package com.leantv.app.share

import android.content.Context
import android.os.Environment
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

class QuickShareServer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onItemReceived: (SharedItem) -> Unit,
    private val onProgressUpdate: (String?) -> Unit,
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    var currentPin: String = generatePin()
        private set

    val port: Int = 8765

    private fun generatePin(): String = (1000..9999).random().toString()

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
        currentPin = generatePin()

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
            true
        } catch (_: Exception) {
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
        onProgressUpdate(null)
    }

    private fun handleClient(client: Socket) {
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

                // Read headers
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
                    method == "POST" && uri.startsWith("/api/message") -> {
                        handleMessagePost(headers, reader, output)
                    }
                    method == "POST" && uri.startsWith("/api/upload") -> {
                        handleUploadPost(headers, input, output)
                    }
                    else -> {
                        sendResponse(output, 404, "text/plain", "Not Found")
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun serveWebPage(output: OutputStream) {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
                <title>LeanTV Quick Share</title>
                <style>
                    :root {
                        --bg: #07131B;
                        --card: #132A38;
                        --card-hi: #1F4257;
                        --amber: #FFC15E;
                        --mint: #5FD6A4;
                        --coral: #FF7A6B;
                        --text: #F2F8F7;
                        --dim: #A9C6CA;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
                    body { background: var(--bg); color: var(--text); padding: 20px; display: flex; flex-direction: column; align-items: center; min-height: 100vh; }
                    .container { width: 100%; max-width: 480px; display: flex; flex-direction: column; gap: 20px; }
                    header { text-align: center; margin-bottom: 8px; }
                    h1 { color: var(--amber); font-size: 28px; font-weight: 700; margin-bottom: 6px; }
                    p.sub { color: var(--dim); font-size: 15px; }
                    .card { background: var(--card); border-radius: 18px; padding: 20px; border: 1px solid rgba(255,255,255,0.08); display: flex; flex-direction: column; gap: 14px; }
                    h2 { font-size: 18px; font-weight: 600; color: var(--text); }
                    label { font-size: 13px; color: var(--dim); }
                    input[type="text"], textarea {
                        width: 100%; background: #0A1B24; border: 1px solid rgba(255,255,255,0.12);
                        border-radius: 12px; color: var(--text); padding: 12px 14px; font-size: 16px; outline: none;
                    }
                    input[type="text"]:focus, textarea:focus { border-color: var(--amber); }
                    textarea { resize: vertical; min-height: 80px; }
                    .btn {
                        background: var(--amber); color: #2B1A00; border: none; border-radius: 12px;
                        padding: 14px 20px; font-size: 16px; font-weight: 600; cursor: pointer; text-align: center;
                        transition: opacity 0.15s;
                    }
                    .btn:active { opacity: 0.85; }
                    .file-drop {
                        border: 2px dashed rgba(255,255,255,0.2); border-radius: 14px; padding: 24px;
                        text-align: center; cursor: pointer; background: rgba(255,255,255,0.02);
                    }
                    .file-drop:hover { border-color: var(--amber); }
                    #fileInput { display: none; }
                    .progress-bar { width: 100%; height: 8px; background: #0A1B24; border-radius: 4px; overflow: hidden; display: none; margin-top: 10px; }
                    .progress-fill { width: 0%; height: 100%; background: var(--mint); transition: width 0.2s; }
                    .status { font-size: 14px; text-align: center; padding: 10px; border-radius: 10px; display: none; }
                    .status.ok { background: rgba(95,214,164,0.15); color: var(--mint); display: block; }
                    .status.err { background: rgba(255,122,107,0.15); color: var(--coral); display: block; }
                </style>
            </head>
            <body>
                <div class="container">
                    <header>
                        <h1>LeanTV Quick Share</h1>
                        <p class="sub">Send files, text notes, and links directly to your TV screen</p>
                    </header>

                    <div class="card">
                        <h2>1. Enter TV Security PIN</h2>
                        <label for="pinInput">Enter the 4-digit PIN displayed on your TV screen:</label>
                        <input type="text" id="pinInput" maxlength="4" placeholder="e.g. 1234" autocomplete="off" inputmode="numeric" />
                    </div>

                    <div class="card">
                        <h2>2. Send Text or URL to TV</h2>
                        <textarea id="textInput" placeholder="Paste YouTube link, stream URL, or text note..."></textarea>
                        <button class="btn" onclick="sendText()">Send to TV</button>
                        <div id="textStatus" class="status"></div>
                    </div>

                    <div class="card">
                        <h2>3. Send Files (Photos, APKs, Videos)</h2>
                        <div class="file-drop" onclick="document.getElementById('fileInput').click()">
                            <p id="fileNameLabel" style="color: var(--dim);">Tap to choose a file</p>
                            <input type="file" id="fileInput" onchange="fileSelected(this)" />
                        </div>
                        <div class="progress-bar" id="progressBar"><div class="progress-fill" id="progressFill"></div></div>
                        <button class="btn" id="uploadBtn" onclick="uploadFile()" style="display: none;">Upload to TV</button>
                        <div id="fileStatus" class="status"></div>
                    </div>
                </div>

                <script>
                    function getPin() { return document.getElementById('pinInput').value.trim(); }

                    async function sendText() {
                        const pin = getPin();
                        const text = document.getElementById('textInput').value.trim();
                        const status = document.getElementById('textStatus');
                        if (!pin) { status.className = 'status err'; status.innerText = 'Please enter the 4-digit PIN from your TV screen.'; return; }
                        if (!text) { status.className = 'status err'; status.innerText = 'Please enter some text or a link to send.'; return; }

                        try {
                            const res = await fetch('/api/message', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-LeanTV-PIN': pin },
                                body: 'text=' + encodeURIComponent(text)
                            });
                            const data = await res.json();
                            if (res.ok) {
                                status.className = 'status ok'; status.innerText = 'Sent successfully to TV!';
                                document.getElementById('textInput').value = '';
                            } else {
                                status.className = 'status err'; status.innerText = data.error || 'Failed to send.';
                            }
                        } catch (e) {
                            status.className = 'status err'; status.innerText = 'Network error reaching TV.';
                        }
                    }

                    let selectedFile = null;
                    function fileSelected(input) {
                        if (input.files && input.files[0]) {
                            selectedFile = input.files[0];
                            document.getElementById('fileNameLabel').innerText = selectedFile.name + ' (' + (selectedFile.size / 1024 / 1024).toFixed(1) + ' MB)';
                            document.getElementById('uploadBtn').style.display = 'block';
                        }
                    }

                    function uploadFile() {
                        const pin = getPin();
                        const status = document.getElementById('fileStatus');
                        if (!pin) { status.className = 'status err'; status.innerText = 'Please enter the 4-digit PIN from your TV screen.'; return; }
                        if (!selectedFile) return;

                        const bar = document.getElementById('progressBar');
                        const fill = document.getElementById('progressFill');
                        bar.style.display = 'block'; fill.style.width = '0%';
                        status.className = 'status'; status.style.display = 'none';

                        const xhr = new XMLHttpRequest();
                        xhr.open('POST', '/api/upload', true);
                        xhr.setRequestHeader('X-LeanTV-PIN', pin);
                        xhr.setRequestHeader('X-Filename', encodeURIComponent(selectedFile.name));

                        xhr.upload.onprogress = (e) => {
                            if (e.lengthComputable) {
                                const pct = Math.round((e.loaded / e.total) * 100);
                                fill.style.width = pct + '%';
                            }
                        };

                        xhr.onload = () => {
                            if (xhr.status === 200) {
                                status.className = 'status ok'; status.innerText = 'File saved to TV (Downloads/LeanTV/)!';
                                document.getElementById('uploadBtn').style.display = 'none';
                                document.getElementById('fileNameLabel').innerText = 'Tap to choose another file';
                                selectedFile = null;
                            } else {
                                status.className = 'status err'; status.innerText = 'Upload rejected. Check PIN.';
                            }
                            bar.style.display = 'none';
                        };

                        xhr.onerror = () => {
                            status.className = 'status err'; status.innerText = 'Connection to TV failed.';
                            bar.style.display = 'none';
                        };

                        xhr.send(selectedFile);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        sendResponse(output, 200, "text/html; charset=UTF-8", html)
    }

    private fun handleMessagePost(headers: Map<String, String>, reader: BufferedReader, output: OutputStream) {
        val pin = headers["x-leantv-pin"] ?: ""
        if (pin != currentPin) {
            sendResponse(output, 403, "application/json", "{\"error\":\"Invalid Security PIN\"}")
            return
        }

        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val charBuffer = CharArray(contentLength)
        var totalRead = 0
        while (totalRead < contentLength) {
            val r = reader.read(charBuffer, totalRead, contentLength - totalRead)
            if (r == -1) break
            totalRead += r
        }
        val body = String(charBuffer, 0, totalRead)

        var rawText = body
        if (body.startsWith("text=")) {
            rawText = URLDecoder.decode(body.substring(5), "UTF-8")
        }

        if (rawText.isNotBlank()) {
            val isUrl = rawText.startsWith("http://", ignoreCase = true) || rawText.startsWith("https://", ignoreCase = true)
            onItemReceived(SharedItem.SharedText(rawText, isUrl, System.currentTimeMillis()))
            sendResponse(output, 200, "application/json", "{\"status\":\"ok\"}")
        } else {
            sendResponse(output, 400, "application/json", "{\"error\":\"Empty payload\"}")
        }
    }

    private fun handleUploadPost(headers: Map<String, String>, input: InputStream, output: OutputStream) {
        val pin = headers["x-leantv-pin"] ?: ""
        if (pin != currentPin) {
            sendResponse(output, 403, "application/json", "{\"error\":\"Invalid Security PIN\"}")
            return
        }

        val rawFilename = headers["x-filename"] ?: "upload_${System.currentTimeMillis()}"
        val filename = try {
            URLDecoder.decode(rawFilename, "UTF-8").replace("[/\\\\?%*:|\"<>]".toRegex(), "_")
        } catch (_: Exception) {
            "upload_${System.currentTimeMillis()}"
        }

        val contentLength = headers["content-length"]?.toLongOrNull() ?: -1L

        val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "LeanTV")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val destFile = File(downloadsDir, filename)
        var totalBytesRead = 0L

        try {
            onProgressUpdate("Receiving $filename...")
            BufferedOutputStream(FileOutputStream(destFile)).use { fileOut ->
                val buffer = ByteArray(32 * 1024)
                var bytesToRead = contentLength

                while (bytesToRead != 0L) {
                    val readSize = if (bytesToRead > 0) minOf(buffer.size.toLong(), bytesToRead).toInt() else buffer.size
                    val r = input.read(buffer, 0, readSize)
                    if (r == -1) break
                    fileOut.write(buffer, 0, r)
                    totalBytesRead += r
                    if (bytesToRead > 0) bytesToRead -= r

                    if (contentLength > 0) {
                        val pct = ((totalBytesRead * 100) / contentLength).toInt()
                        onProgressUpdate("Receiving $filename: $pct%")
                    }
                }
                fileOut.flush()
            }

            onProgressUpdate(null)
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
            onProgressUpdate(null)
            sendResponse(output, 500, "application/json", "{\"error\":\"File write error: ${e.message}\"}")
        }
    }

    private fun sendResponse(output: OutputStream, statusCode: Int, contentType: String, content: String) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 $statusCode OK\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }
}

