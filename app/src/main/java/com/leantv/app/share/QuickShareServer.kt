package com.leantv.app.share

import android.content.Context
import android.media.MediaScannerConnection
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class QuickShareServer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onRequestConsent: suspend (sender: String, fileName: String, fileSize: Long) -> Boolean,
    private val onTransferProgress: (fraction: Float, bytesPerSec: Long) -> Unit,
    private val onTransferComplete: (File) -> Unit,
    private val onItemReceived: (SharedItem) -> Unit,
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val approvedSessions = ConcurrentHashMap<String, Long>() // token -> expiration time

    val port: Int = 8765

    companion object {
        private const val TAG = "QuickShareServer"
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (!host.startsWith("127.")) return host
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun start(): Boolean {
        if (serverJob != null && serverJob?.isActive == true) return true

        return try {
            val socket = ServerSocket(port)
            serverSocket = socket
            serverJob = scope.launch(Dispatchers.IO) {
                while (isActive && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        launch(Dispatchers.IO) {
                            handleClient(client)
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }

            Log.i(TAG, "QuickShareServer started on port $port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start QuickShareServer on port $port", e)
            false
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
        serverSocket = null
        serverJob = null
        approvedSessions.clear()
        Log.i(TAG, "QuickShareServer stopped")
    }

    private suspend fun handleClient(client: Socket) {
        try {
            client.use { s ->
                val input = s.getInputStream()
                val output = s.getOutputStream()
                val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0]
                val uri = parts[1]

                // Read HTTP headers
                val headers = mutableMapOf<String, String>()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.isEmpty()) break
                    val colonIdx = line!!.indexOf(':')
                    if (colonIdx > 0) {
                        val key = line!!.substring(0, colonIdx).trim().lowercase()
                        val value = line!!.substring(colonIdx + 1).trim()
                        headers[key] = value
                    }
                }

                when {
                    method == "GET" && (uri == "/" || uri.startsWith("/?")) -> {
                        serveWebPage(output)
                    }
                    method == "POST" && uri.startsWith("/api/request-transfer") -> {
                        handleRequestTransferPost(headers, reader, output)
                    }
                    method == "POST" && uri.startsWith("/api/upload") -> {
                        handleUploadPost(uri, headers, input, output)
                    }
                    method == "POST" && uri.startsWith("/api/message") -> {
                        handleMessagePost(headers, reader, output)
                    }
                    method == "OPTIONS" -> {
                        sendOptionsResponse(output)
                    }
                    else -> {
                        sendResponse(output, 404, "text/plain", "Not Found")
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun handleRequestTransferPost(
        headers: Map<String, String>,
        reader: BufferedReader,
        output: OutputStream
    ) {
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (contentLength > 0) {
            val buffer = CharArray(contentLength)
            var totalRead = 0
            while (totalRead < contentLength) {
                val r = reader.read(buffer, totalRead, contentLength - totalRead)
                if (r == -1) break
                totalRead += r
            }
            String(buffer, 0, totalRead)
        } else ""

        var fileName = "file"
        var fileSize = 0L
        var senderName = detectDeviceName(headers["user-agent"])

        try {
            val orgJson = org.json.JSONObject(body)
            if (orgJson.has("name")) fileName = orgJson.getString("name")
            if (orgJson.has("size")) fileSize = orgJson.getLong("size")
            if (orgJson.has("sender") && orgJson.getString("sender").isNotBlank()) {
                senderName = orgJson.getString("sender")
            }
        } catch (_: Exception) {}

        // Request user consent on the TV screen (pops up Accept/Decline dialog)
        val approved = onRequestConsent(senderName, fileName, fileSize)

        if (approved) {
            val token = UUID.randomUUID().toString()
            approvedSessions[token] = System.currentTimeMillis() + 10 * 60 * 1000 // 10 min validity
            sendResponse(output, 200, "application/json", "{\"approved\":true,\"token\":\"$token\"}")
        } else {
            sendResponse(output, 403, "application/json", "{\"approved\":false,\"error\":\"Transfer declined on TV\"}")
        }
    }

    private suspend fun handleUploadPost(
        uri: String,
        headers: Map<String, String>,
        input: InputStream,
        output: OutputStream
    ) {
        // Extract token from query or header
        val token = uri.substringAfter("token=", "").substringBefore("&").ifEmpty {
            headers["x-transfer-token"] ?: ""
        }

        val rawFilename = headers["x-filename"] ?: "upload_${System.currentTimeMillis()}"
        val filename = try {
            URLDecoder.decode(rawFilename, "UTF-8").replace("[/\\\\?%*:|\"<>]".toRegex(), "_")
        } catch (_: Exception) {
            "upload_${System.currentTimeMillis()}"
        }

        val contentLength = headers["content-length"]?.toLongOrNull() ?: -1L
        val sender = headers["x-sender"] ?: detectDeviceName(headers["user-agent"])

        // If not already approved via /api/request-transfer, ask consent now
        val isPreApproved = token.isNotBlank() && (approvedSessions.remove(token) ?: 0L) > System.currentTimeMillis()
        if (!isPreApproved) {
            val approved = onRequestConsent(sender, filename, if (contentLength > 0) contentLength else 0L)
            if (!approved) {
                sendResponse(output, 403, "application/json", "{\"error\":\"Transfer declined on TV\"}")
                return
            }
        }

        val downloadsDir = TvFileDestinationFactory.getDownloadDir(context)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        // Resolve non-conflicting file
        var destFile = File(downloadsDir, filename)
        if (destFile.exists()) {
            val nameWithoutExt = filename.substringBeforeLast('.', filename)
            val ext = if (filename.contains('.')) ".${filename.substringAfterLast('.')}" else ""
            var counter = 1
            while (destFile.exists()) {
                destFile = File(downloadsDir, "${nameWithoutExt}_$counter$ext")
                counter++
            }
        }

        var totalBytesRead = 0L
        val startTime = System.currentTimeMillis()
        var lastReportTime = startTime
        var lastReportBytes = 0L

        try {
            BufferedOutputStream(FileOutputStream(destFile)).use { fileOut ->
                val buffer = ByteArray(64 * 1024)
                var bytesToRead = contentLength

                while (bytesToRead != 0L) {
                    val readSize = if (bytesToRead > 0) minOf(buffer.size.toLong(), bytesToRead).toInt() else buffer.size
                    val r = input.read(buffer, 0, readSize)
                    if (r == -1) break
                    fileOut.write(buffer, 0, r)
                    totalBytesRead += r
                    if (bytesToRead > 0) bytesToRead -= r

                    val now = System.currentTimeMillis()
                    if (now - lastReportTime >= 250) {
                        val durationSec = (now - lastReportTime) / 1000.0
                        val bytesSince = totalBytesRead - lastReportBytes
                        val speedBps = if (durationSec > 0) (bytesSince / durationSec).toLong() else 0L
                        val fraction = if (contentLength > 0) (totalBytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f) else 0f
                        onTransferProgress(fraction, speedBps)
                        lastReportTime = now
                        lastReportBytes = totalBytesRead
                    }
                }
                fileOut.flush()
            }

            // Ensure media scanner discovers new file
            try {
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
            } catch (_: Exception) {}

            onTransferComplete(destFile)
            onItemReceived(
                SharedItem.SharedFile(
                    name = destFile.name,
                    path = destFile.absolutePath,
                    sizeBytes = destFile.length(),
                    timestamp = System.currentTimeMillis()
                )
            )

            sendResponse(output, 200, "application/json", "{\"status\":\"ok\",\"file\":\"${destFile.name}\"}")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing uploaded file", e)
            sendResponse(output, 500, "application/json", "{\"error\":\"File write error: ${e.message}\"}")
        }
    }

    private fun handleMessagePost(headers: Map<String, String>, reader: BufferedReader, output: OutputStream) {
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (contentLength > 0) {
            val buffer = CharArray(contentLength)
            var totalRead = 0
            while (totalRead < contentLength) {
                val r = reader.read(buffer, totalRead, contentLength - totalRead)
                if (r == -1) break
                totalRead += r
            }
            String(buffer, 0, totalRead)
        } else ""

        var rawText = body
        if (body.startsWith("text=")) {
            rawText = try { URLDecoder.decode(body.substring(5), "UTF-8") } catch (_: Exception) { body.substring(5) }
        }

        if (rawText.isNotBlank()) {
            val isUrl = rawText.startsWith("http://", ignoreCase = true) || rawText.startsWith("https://", ignoreCase = true)
            onItemReceived(SharedItem.SharedText(rawText, isUrl, System.currentTimeMillis()))
            sendResponse(output, 200, "application/json", "{\"status\":\"ok\"}")
        } else {
            sendResponse(output, 400, "application/json", "{\"error\":\"Empty message\"}")
        }
    }

    private fun detectDeviceName(userAgent: String?): String {
        val ua = userAgent?.lowercase() ?: return "Apple / Web Device"
        return when {
            ua.contains("iphone") -> "iPhone"
            ua.contains("ipad") -> "iPad"
            ua.contains("macintosh") || ua.contains("mac os") -> "Mac"
            ua.contains("android") -> "Android Phone"
            ua.contains("windows") -> "Windows PC"
            else -> "Apple / Web Device"
        }
    }

    private fun sendOptionsResponse(output: OutputStream) {
        val header = "HTTP/1.1 200 OK\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
            "Access-Control-Allow-Headers: Content-Type, X-Filename, X-Sender, X-Transfer-Token\r\n" +
            "Content-Length: 0\r\n" +
            "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    private fun sendResponse(output: OutputStream, statusCode: Int, contentType: String, content: String) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 $statusCode OK\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
            "Access-Control-Allow-Headers: Content-Type, X-Filename, X-Sender, X-Transfer-Token\r\n" +
            "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun serveWebPage(output: OutputStream) {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <title>LeanTV Quick Share</title>
                <style>
                    :root {
                        --bg: #000000;
                        --card: #1C1C1E;
                        --card-sub: #2C2C2E;
                        --accent: #0A84FF;
                        --accent-grad: linear-gradient(135deg, #0A84FF 0%, #0066CC 100%);
                        --mint: #30D158;
                        --coral: #FF453A;
                        --text: #FFFFFF;
                        --dim: #8E8E93;
                        --line: rgba(255, 255, 255, 0.12);
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; -webkit-tap-highlight-color: transparent; }
                    body {
                        background: var(--bg);
                        color: var(--text);
                        font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Helvetica Neue", sans-serif;
                        padding: 24px 16px;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        min-height: 100vh;
                    }
                    .wrap { width: 100%; max-width: 440px; display: flex; flex-direction: column; gap: 20px; }
                    header { text-align: center; margin-top: 12px; margin-bottom: 8px; }
                    .logo-icon {
                        width: 64px; height: 64px; border-radius: 20px;
                        background: var(--accent-grad);
                        display: inline-flex; align-items: center; justify-content: center;
                        box-shadow: 0 8px 24px rgba(10, 132, 255, 0.35);
                        margin-bottom: 14px;
                    }
                    .logo-icon svg { width: 34px; height: 34px; fill: white; }
                    h1 { font-size: 26px; font-weight: 700; letter-spacing: -0.4px; }
                    p.sub { font-size: 14px; color: var(--dim); margin-top: 4px; }

                    .card {
                        background: var(--card);
                        border-radius: 24px;
                        padding: 22px;
                        border: 1px solid var(--line);
                        display: flex;
                        flex-direction: column;
                        gap: 16px;
                    }
                    .card h2 { font-size: 17px; font-weight: 600; color: var(--text); }

                    .drop-zone {
                        border: 2px dashed rgba(255, 255, 255, 0.2);
                        border-radius: 18px;
                        padding: 28px 16px;
                        text-align: center;
                        cursor: pointer;
                        background: rgba(255, 255, 255, 0.03);
                        transition: all 0.2s ease;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        gap: 10px;
                    }
                    .drop-zone:hover, .drop-zone:active {
                        border-color: var(--accent);
                        background: rgba(10, 132, 255, 0.08);
                    }
                    .drop-icon {
                        width: 44px; height: 44px; border-radius: 50%;
                        background: rgba(10, 132, 255, 0.15);
                        display: flex; align-items: center; justify-content: center;
                    }
                    .drop-icon svg { width: 22px; height: 22px; fill: var(--accent); }
                    .drop-title { font-size: 16px; font-weight: 600; color: var(--text); }
                    .drop-hint { font-size: 13px; color: var(--dim); }
                    #fileInput { display: none; }

                    .btn {
                        background: var(--accent-grad);
                        color: white;
                        border: none;
                        border-radius: 14px;
                        padding: 16px;
                        font-size: 16px;
                        font-weight: 600;
                        cursor: pointer;
                        text-align: center;
                        width: 100%;
                        transition: opacity 0.15s;
                    }
                    .btn:active { opacity: 0.85; }
                    .btn:disabled { opacity: 0.4; cursor: not-allowed; }

                    .progress-wrap {
                        display: none;
                        flex-direction: column;
                        gap: 8px;
                        margin-top: 4px;
                    }
                    .progress-label {
                        display: flex; justify-content: space-between;
                        font-size: 13px; font-weight: 500; color: var(--dim);
                    }
                    .progress-bar {
                        width: 100%; height: 8px; background: var(--card-sub);
                        border-radius: 4px; overflow: hidden;
                    }
                    .progress-fill {
                        width: 0%; height: 100%;
                        background: var(--mint);
                        transition: width 0.15s;
                    }

                    .status-box {
                        display: none;
                        padding: 14px;
                        border-radius: 14px;
                        font-size: 14px;
                        font-weight: 500;
                        text-align: center;
                    }
                    .status-box.ok { background: rgba(48, 209, 88, 0.15); color: var(--mint); display: block; }
                    .status-box.err { background: rgba(255, 69, 58, 0.15); color: var(--coral); display: block; }
                    .status-box.info { background: rgba(10, 132, 255, 0.15); color: var(--accent); display: block; }

                    textarea {
                        width: 100%; background: var(--card-sub);
                        border: 1px solid var(--line);
                        border-radius: 14px; color: var(--text);
                        padding: 12px 14px; font-size: 15px; outline: none;
                        resize: vertical; min-height: 70px;
                        font-family: inherit;
                    }
                    textarea:focus { border-color: var(--accent); }

                    .tabs {
                        display: flex; background: var(--card-sub);
                        border-radius: 12px; padding: 4px; gap: 4px;
                    }
                    .tab-btn {
                        flex: 1; padding: 8px; border: none; background: transparent;
                        color: var(--dim); font-size: 14px; font-weight: 600;
                        border-radius: 8px; cursor: pointer;
                    }
                    .tab-btn.active {
                        background: var(--card); color: var(--text);
                        box-shadow: 0 2px 6px rgba(0,0,0,0.3);
                    }
                </style>
            </head>
            <body>
                <div class="wrap">
                    <header>
                        <div class="logo-icon">
                            <svg viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 14v-4H8l4-4 4 4h-3v4h-2z"/></svg>
                        </div>
                        <h1>LeanTV Quick Share</h1>
                        <p class="sub">Send files, photos, videos or notes to your TV</p>
                    </header>

                    <div class="tabs">
                        <button class="tab-btn active" onclick="setTab('files')">Files & Photos</button>
                        <button class="tab-btn" onclick="setTab('notes')">Link or Note</button>
                    </div>

                    <!-- Files Tab -->
                    <div id="tabFiles" class="card">
                        <h2>Select Files or Photos</h2>
                        <div class="drop-zone" onclick="document.getElementById('fileInput').click()">
                            <div class="drop-icon">
                                <svg viewBox="0 0 24 24"><path d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM14 13v4h-4v-4H7l5-5 5 5h-3z"/></svg>
                            </div>
                            <div class="drop-title" id="fileTitle">Choose Photos or Files</div>
                            <div class="drop-hint" id="fileHint">Camera roll, videos, documents, or APKs</div>
                            <input type="file" id="fileInput" onchange="onFilePicked(this)" multiple />
                        </div>

                        <div class="progress-wrap" id="progWrap">
                            <div class="progress-label">
                                <span id="progStatus">Connecting to TV...</span>
                                <span id="progPct">0%</span>
                            </div>
                            <div class="progress-bar"><div class="progress-fill" id="progFill"></div></div>
                        </div>

                        <div id="fileStatusBox" class="status-box"></div>

                        <button id="sendBtn" class="btn" onclick="startUpload()" style="display: none;">
                            Send to TV
                        </button>
                    </div>

                    <!-- Notes Tab -->
                    <div id="tabNotes" class="card" style="display: none;">
                        <h2>Send Text Note or Web Link</h2>
                        <textarea id="noteInput" placeholder="Paste YouTube link, stream URL, or note..."></textarea>
                        <div id="noteStatusBox" class="status-box"></div>
                        <button class="btn" onclick="sendNote()">Send to TV</button>
                    </div>
                </div>

                <script>
                    function setTab(tab) {
                        const tabFiles = document.getElementById('tabFiles');
                        const tabNotes = document.getElementById('tabNotes');
                        const btns = document.querySelectorAll('.tab-btn');
                        if (tab === 'files') {
                            tabFiles.style.display = 'flex';
                            tabNotes.style.display = 'none';
                            btns[0].classList.add('active');
                            btns[1].classList.remove('active');
                        } else {
                            tabFiles.style.display = 'none';
                            tabNotes.style.display = 'flex';
                            btns[0].classList.remove('active');
                            btns[1].classList.add('active');
                        }
                    }

                    let pickedFiles = [];
                    function onFilePicked(input) {
                        if (!input.files || input.files.length === 0) return;
                        pickedFiles = Array.from(input.files);
                        const count = pickedFiles.length;
                        const totalBytes = pickedFiles.reduce((acc, f) => acc + f.size, 0);
                        const mb = (totalBytes / (1024 * 1024)).toFixed(1);

                        document.getElementById('fileTitle').innerText = count === 1 ? pickedFiles[0].name : count + ' files selected';
                        document.getElementById('fileHint').innerText = mb + ' MB total';
                        document.getElementById('sendBtn').style.display = 'block';
                        hideStatus('fileStatusBox');
                    }

                    function showStatus(id, type, msg) {
                        const box = document.getElementById(id);
                        box.className = 'status-box ' + type;
                        box.innerText = msg;
                    }
                    function hideStatus(id) {
                        const box = document.getElementById(id);
                        box.className = 'status-box';
                        box.style.display = 'none';
                    }

                    function getSenderName() {
                        const ua = navigator.userAgent;
                        if (/iPhone/i.test(ua)) return 'iPhone';
                        if (/iPad/i.test(ua)) return 'iPad';
                        if (/Macintosh/i.test(ua)) return 'Mac';
                        if (/Android/i.test(ua)) return 'Android Device';
                        if (/Windows/i.test(ua)) return 'Windows PC';
                        return 'Apple / Web Device';
                    }

                    async function startUpload() {
                        if (!pickedFiles || pickedFiles.length === 0) return;
                        const sendBtn = document.getElementById('sendBtn');
                        const progWrap = document.getElementById('progWrap');
                        const progStatus = document.getElementById('progStatus');
                        const progPct = document.getElementById('progPct');
                        const progFill = document.getElementById('progFill');

                        sendBtn.disabled = true;
                        progWrap.style.display = 'flex';

                        for (let i = 0; i < pickedFiles.length; i++) {
                            const file = pickedFiles[i];
                            progStatus.innerText = 'Waiting for TV approval...';
                            progPct.innerText = '';
                            progFill.style.width = '0%';
                            showStatus('fileStatusBox', 'info', 'Please tap "Accept" on your TV remote...');

                            // Step 1: Request Consent on TV screen
                            let token = null;
                            try {
                                const reqRes = await fetch('/api/request-transfer', {
                                    method: 'POST',
                                    headers: { 'Content-Type': 'application/json' },
                                    body: JSON.stringify({
                                        name: file.name,
                                        size: file.size,
                                        sender: getSenderName()
                                    })
                                });
                                const reqData = await reqRes.json();
                                if (!reqRes.ok || !reqData.approved) {
                                    showStatus('fileStatusBox', 'err', reqData.error || 'Transfer declined on TV.');
                                    progWrap.style.display = 'none';
                                    sendBtn.disabled = false;
                                    return;
                                }
                                token = reqData.token;
                            } catch (e) {
                                showStatus('fileStatusBox', 'err', 'Could not reach TV. Check Wi-Fi.');
                                progWrap.style.display = 'none';
                                sendBtn.disabled = false;
                                return;
                            }

                            // Step 2: Upload file stream
                            progStatus.innerText = 'Sending ' + file.name + '...';
                            hideStatus('fileStatusBox');

                            await new Promise((resolve, reject) => {
                                const xhr = new XMLHttpRequest();
                                xhr.open('POST', '/api/upload?token=' + encodeURIComponent(token), true);
                                xhr.setRequestHeader('X-Filename', encodeURIComponent(file.name));
                                xhr.setRequestHeader('X-Sender', getSenderName());

                                xhr.upload.onprogress = (e) => {
                                    if (e.lengthComputable) {
                                        const pct = Math.round((e.loaded / e.total) * 100);
                                        progFill.style.width = pct + '%';
                                        progPct.innerText = pct + '%';
                                    }
                                };

                                xhr.onload = () => {
                                    if (xhr.status === 200) {
                                        resolve();
                                    } else {
                                        reject(new Error('Upload failed'));
                                    }
                                };

                                xhr.onerror = () => reject(new Error('Network error'));
                                xhr.send(file);
                            });
                        }

                        progFill.style.width = '100%';
                        progPct.innerText = '100%';
                        progStatus.innerText = 'Complete!';
                        showStatus('fileStatusBox', 'ok', '✓ Saved to TV (LeanTvQuickshare)!');
                        sendBtn.style.display = 'none';
                        sendBtn.disabled = false;
                        document.getElementById('fileTitle').innerText = 'Choose Photos or Files';
                        document.getElementById('fileHint').innerText = 'Camera roll, videos, documents, or APKs';
                        document.getElementById('fileInput').value = '';
                        pickedFiles = [];
                    }

                    async function sendNote() {
                        const text = document.getElementById('noteInput').value.trim();
                        if (!text) {
                            showStatus('noteStatusBox', 'err', 'Please enter a note or link.');
                            return;
                        }
                        try {
                            const res = await fetch('/api/message', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                                body: 'text=' + encodeURIComponent(text)
                            });
                            if (res.ok) {
                                showStatus('noteStatusBox', 'ok', '✓ Sent successfully to TV screen!');
                                document.getElementById('noteInput').value = '';
                            } else {
                                showStatus('noteStatusBox', 'err', 'Failed to send note to TV.');
                            }
                        } catch (e) {
                            showStatus('noteStatusBox', 'err', 'Could not reach TV. Check Wi-Fi.');
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        sendResponse(output, 200, "text/html; charset=UTF-8", html)
    }
}
