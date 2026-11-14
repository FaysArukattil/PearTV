package com.leantv.app.share

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.media.MediaScannerConnection
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket

/**
 * Native Apple AirDrop receiver implementation for Android TV.
 * Advertises Apple BLE presence, registers mDNS _airdrop._tcp, and serves
 * AirDrop HTTP endpoints (/Discover, /Ask, /Upload) over TLS on port 8770.
 */
class AirDropServer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val deviceName: String = Build.MODEL,
    private val onRequestConsent: suspend (sender: String, files: List<TransferFileInfo>) -> Boolean,
    private val onTransferProgress: (fraction: Float, bytesPerSec: Long) -> Unit,
    private val onTransferComplete: (List<File>) -> Unit,
) {
    companion object {
        private const val TAG = "AirDropServer"
        const val AIRDROP_PORT = 8770
        const val APPLE_MANUFACTURER_ID = 0x004C
    }

    private var serverSocket: SSLServerSocket? = null
    private var serverJob: Job? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var bleCallback: AdvertiseCallback? = null
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null

    val isRunning: Boolean get() = serverSocket != null && !serverSocket!!.isClosed

    fun start(): Boolean {
        if (isRunning) return true

        return try {
            val sslContext = initSslContext()
            val socket = sslContext.serverSocketFactory.createServerSocket(AIRDROP_PORT) as SSLServerSocket
            socket.needClientAuth = false
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

            // Register mDNS
            registerMdns()

            // Start Apple BLE AirDrop pulse
            startBlePulse()

            Log.i(TAG, "AirDropServer started on port $AIRDROP_PORT")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AirDropServer on port $AIRDROP_PORT", e)
            false
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null

        stopBlePulse()
        unregisterMdns()

        Log.i(TAG, "AirDropServer stopped")
    }

    private fun initSslContext(): SSLContext {
        val ks = KeyStore.getInstance("PKCS12")
        context.assets.open("airdrop.p12").use { input ->
            ks.load(input, "leantv".toCharArray())
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, "leantv".toCharArray())

        val sslCtx = SSLContext.getInstance("TLS")
        sslCtx.init(kmf.keyManagers, null, null)
        return sslCtx
    }

    private fun registerMdns() {
        try {
            val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            val randomId = (0..0xFFFFFFFFFFFFL).random().toString(16).padStart(12, '0')
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = randomId
                serviceType = "_airdrop._tcp"
                port = AIRDROP_PORT
                setAttribute("flags", "1019")
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                    Log.i(TAG, "AirDrop mDNS registered: ${NsdServiceInfo.serviceName}._airdrop._tcp")
                }
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "AirDrop mDNS registration failed: $errorCode")
                }
                override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                    Log.i(TAG, "AirDrop mDNS unregistered")
                }
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "AirDrop mDNS unregistration failed: $errorCode")
                }
            }
            nsdRegistrationListener = listener
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "Error registering AirDrop mDNS: ${e.message}")
        }
    }

    private fun unregisterMdns() {
        val listener = nsdRegistrationListener ?: return
        nsdRegistrationListener = null
        try {
            val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager.unregisterService(listener)
        } catch (_: Exception) {}
    }

    private fun startBlePulse() {
        try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
            val adapter = bm.adapter ?: return
            if (!adapter.isEnabled) return
            val advertiser = adapter.bluetoothLeAdvertiser ?: return
            bleAdvertiser = advertiser

            // Standard AirDrop presence beacon payload:
            // Type: 0x05 (AirDrop)
            // Length: 0x12 (18 bytes payload)
            // 8 prefix zeroes
            // Version: 0x01 (adVr 1)
            // 4 Contact Hashes (2 bytes each): e.g. 0x94C3, 0x3DCF, 0xE91F, 0xE441
            // Suffix: 0x00
            val airdropPayload = byteArrayOf(
                0x05, 0x12,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x01,
                0x94.toByte(), 0xC3.toByte(),
                0x3D.toByte(), 0xCF.toByte(),
                0xE9.toByte(), 0x1F.toByte(),
                0xE4.toByte(), 0x41.toByte(),
                0x00
            )

            val data = AdvertiseData.Builder()
                .addManufacturerData(APPLE_MANUFACTURER_ID, airdropPayload)
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .build()

            val scanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build()

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build()

            val cb = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.i(TAG, "AirDrop Apple BLE advertiser started successfully")
                }
                override fun onStartFailure(errorCode: Int) {
                    Log.w(TAG, "AirDrop Apple BLE advertise failed: $errorCode")
                }
            }
            bleCallback = cb
            advertiser.startAdvertising(settings, data, scanResponse, cb)
        } catch (e: Exception) {
            Log.w(TAG, "Error starting AirDrop BLE pulse: ${e.message}")
        }
    }

    private fun stopBlePulse() {
        val advertiser = bleAdvertiser ?: return
        val cb = bleCallback ?: return
        bleAdvertiser = null
        bleCallback = null
        try {
            advertiser.stopAdvertising(cb)
        } catch (_: Exception) {}
    }

    // ─── HTTP Connection Handler ─────────────────────────────────────────────

    private fun readHttpLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) {
                return if (sb.isEmpty()) null else sb.toString()
            }
            if (b == '\n'.code) {
                if (sb.endsWith('\r')) sb.deleteCharAt(sb.length - 1)
                return sb.toString()
            }
            sb.append(b.toChar())
        }
    }

    private suspend fun handleClient(client: Socket) {
        try {
            client.soTimeout = 60000
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())

            while (!client.isClosed) {
                val requestLine = readHttpLine(input) ?: break
                if (requestLine.isBlank()) continue
                val parts = requestLine.split(" ")
                if (parts.size < 2) break
                val method = parts[0]
                val path = parts[1]

                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readHttpLine(input) ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val key = line.substring(0, colon).trim().lowercase()
                        val value = line.substring(colon + 1).trim()
                        headers[key] = value
                    }
                }

                Log.i(TAG, "AirDrop request: $method $path")

                var closeAfter = headers["connection"]?.equals("close", ignoreCase = true) == true

                when (path) {
                    "/Discover" -> {
                        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                        if (contentLength > 0) {
                            skipBytes(input, contentLength.toLong())
                        }
                        handleDiscover(output)
                    }
                    "/Ask" -> {
                        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                        val reqData = if (contentLength > 0) readBytes(input, contentLength) else ByteArray(0)
                        handleAsk(reqData, output)
                    }
                    "/Upload" -> {
                        val isChunked = headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true
                        val expectContinue = headers["expect"]?.contains("100-continue", ignoreCase = true) == true
                        if (expectContinue) {
                            output.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.US_ASCII))
                            output.flush()
                        }
                        handleUpload(input, isChunked, output)
                        closeAfter = true
                    }
                    else -> {
                        val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        output.write(notFound.toByteArray(Charsets.US_ASCII))
                        output.flush()
                        closeAfter = true
                    }
                }

                if (closeAfter) break
            }
        } catch (e: Exception) {
            Log.w(TAG, "AirDrop client connection error: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun handleDiscover(output: OutputStream) {
        val mediaCaps = """{"Version": 1}""".toByteArray(Charsets.UTF_8)
        val body = BinaryPlist.createDict(
            "ReceiverComputerName" to deviceName,
            "ReceiverModelName" to "MacBook Pro",
            "ReceiverMediaCapabilities" to mediaCaps
        )
        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/x-bplist\r\n" +
                "Content-Length: ${body.size}\r\n\r\n"
        output.write(response.toByteArray(Charsets.US_ASCII))
        output.write(body)
        output.flush()
        Log.i(TAG, "Responded to /Discover with name '$deviceName', model 'MacBook Pro'")
    }

    private suspend fun handleAsk(reqData: ByteArray, output: OutputStream) {
        val strings = BinaryPlist.extractStrings(reqData)
        val sender = strings["SenderComputerName"] ?: "Apple Device"
        val fileName = strings["FileName"] ?: "AirDrop Transfer"

        val files = listOf(
            TransferFileInfo(name = fileName, sizeBytes = 0L, mimeType = null)
        )

        val approved = onRequestConsent(sender, files)
        if (approved) {
            val body = BinaryPlist.createDict(
                "ReceiverComputerName" to deviceName,
                "ReceiverModelName" to "MacBook Pro"
            )
            val response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/x-bplist\r\n" +
                    "Content-Length: ${body.size}\r\n\r\n"
            output.write(response.toByteArray(Charsets.US_ASCII))
            output.write(body)
            output.flush()
            Log.i(TAG, "AirDrop transfer approved for '$sender'")
        } else {
            val response = "HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
            output.write(response.toByteArray(Charsets.US_ASCII))
            output.flush()
            Log.i(TAG, "AirDrop transfer declined for '$sender'")
        }
    }

    private fun handleUpload(rawInput: InputStream, isChunked: Boolean, output: OutputStream) {
        val downloadDir = File("/storage/emulated/0/Quickshare").apply { mkdirs() }
        val stream = if (isChunked) ChunkedInputStream(rawInput) else rawInput

        val extractedFiles = CpioExtractor.extract(stream, downloadDir) { frac, bps ->
            onTransferProgress(frac, bps)
        }

        // Trigger media scanner on newly saved files
        if (extractedFiles.isNotEmpty()) {
            val paths = extractedFiles.map { it.absolutePath }.toTypedArray()
            MediaScannerConnection.scanFile(context, paths, null, null)
            onTransferComplete(extractedFiles)
        }

        val response = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        output.write(response.toByteArray(Charsets.US_ASCII))
        output.flush()
        Log.i(TAG, "AirDrop /Upload complete: ${extractedFiles.size} file(s) saved")
    }

    private fun skipBytes(input: InputStream, count: Long) {
        var remaining = count
        val buf = ByteArray(1024)
        while (remaining > 0) {
            val r = input.read(buf, 0, remaining.coerceAtMost(buf.size.toLong()).toInt())
            if (r < 0) break
            remaining -= r
        }
    }

    private fun readBytes(input: InputStream, count: Int): ByteArray {
        val buf = ByteArray(count)
        var total = 0
        while (total < count) {
            val r = input.read(buf, total, count - total)
            if (r < 0) break
            total += r
        }
        return buf
    }
}

/**
 * Transparent HTTP chunked transfer-encoding reader stream.
 */
class ChunkedInputStream(private val raw: InputStream) : InputStream() {
    private var currentChunkRemaining = 0
    private var isDone = false

    override fun read(): Int {
        val b = ByteArray(1)
        val n = read(b, 0, 1)
        return if (n > 0) b[0].toInt() and 0xFF else -1
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (isDone) return -1
        if (currentChunkRemaining == 0) {
            readNextChunk()
            if (isDone) return -1
        }
        val toRead = len.coerceAtMost(currentChunkRemaining)
        val n = raw.read(b, off, toRead)
        if (n > 0) {
            currentChunkRemaining -= n
            if (currentChunkRemaining == 0) {
                // Read trailing \r\n
                raw.read()
                raw.read()
            }
        }
        return n
    }

    private fun readNextChunk() {
        val line = readAsciiLine()
        val chunkSizeHex = line.trim().split(';')[0]
        val size = chunkSizeHex.toIntOrNull(16) ?: 0
        if (size <= 0) {
            isDone = true
            currentChunkRemaining = 0
            readAsciiLine()
        } else {
            currentChunkRemaining = size
        }
    }

    private fun readAsciiLine(): String {
        val sb = StringBuilder()
        while (true) {
            val c = raw.read()
            if (c < 0 || c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
        }
        return sb.toString()
    }
}
