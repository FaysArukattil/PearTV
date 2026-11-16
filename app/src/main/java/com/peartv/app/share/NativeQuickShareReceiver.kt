package com.peartv.app.share

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import com.peartv.app.HomeActivity
import com.peartv.app.R
import dev.bluehouse.bada.discovery.Discovery
import dev.bluehouse.bada.discovery.ble.BleQuickShareAdvertiser
import dev.bluehouse.bada.discovery.medium.MediumRegistries
import dev.bluehouse.bada.protocol.connection.InboundConnection
import dev.bluehouse.bada.protocol.connection.InboundConnectionState
import dev.bluehouse.bada.protocol.connection.TransferItem
import dev.bluehouse.bada.protocol.endpoint.DeviceType
import dev.bluehouse.bada.protocol.endpoint.EndpointInfo
import dev.bluehouse.bada.protocol.server.TcpReceiverServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import android.util.Log
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.SecureRandom

class NativeQuickShareReceiver : Service() {

    // ─── Binder ───────────────────────────────────────────────────────────────

    inner class LocalBinder : Binder() {
        val service: NativeQuickShareReceiver get() = this@NativeQuickShareReceiver
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder {
        startReceiving()
        return binder
    }

    // ─── State ────────────────────────────────────────────────────────────────

    private val _state = MutableStateFlow(NativeQuickShareState())
    val state: StateFlow<NativeQuickShareState> = _state.asStateFlow()

    // ─── Coroutine scope ──────────────────────────────────────────────────────

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ─── Protocol objects ─────────────────────────────────────────────────────

    private var tcpServer: TcpReceiverServer? = null
    private var advertiseHandle: dev.bluehouse.bada.discovery.AdvertiseHandle? = null
    private var bleAdvertiser: BleQuickShareAdvertiser? = null
    private var airDropServer: AirDropServer? = null
    private var discovery: Discovery? = null
    private var multicastLock: android.net.wifi.WifiManager.MulticastLock? = null

    /** 4-byte alphanumeric endpoint id drawn from ENDPOINT_ID_ALPHABET so BLE and mDNS correlate to the same peer. */
    private var endpointId: ByteArray = generateEndpointId()

    private fun generateEndpointId(): ByteArray {
        val alphabet = dev.bluehouse.bada.discovery.QuickShareMdns.ENDPOINT_ID_ALPHABET
        val rng = SecureRandom()
        return ByteArray(4) { alphabet[rng.nextInt(alphabet.length)].code.toByte() }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopReceiving()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildRunningNotification())
        startReceiving()
        return START_STICKY
    }

    override fun onDestroy() {
        stopReceiving()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ─── Core start / stop ────────────────────────────────────────────────────

    private val committedPayloadFiles = java.util.concurrent.ConcurrentHashMap<Long, File>()
    @Volatile private var isStarting = false

    fun startReceiving() {
        if (_state.value.isAdvertising || isStarting) return
        isStarting = true

        stopReceivingInternal()

        endpointId = generateEndpointId()
        val currentEpId = endpointId

        val ctx = applicationContext
        val wifi = ctx.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        try {
            multicastLock?.release()
        } catch (_: Exception) {}
        multicastLock = wifi?.createMulticastLock("LeanTvQuickShare")?.apply {
            setReferenceCounted(false)
            acquire()
        }

        // Exclude BLE_L2CAP because TV Bluetooth controllers stall on L2CAP CoC channels (causing a 30s hang)
        val providers = MediumRegistries.defaultProviders(ctx).filterNot {
            it.medium == dev.bluehouse.bada.protocol.medium.Medium.BLE_L2CAP
        }
        val mediumRegistry = dev.bluehouse.bada.protocol.medium.MediumRegistry(providers = providers)

        val server = TcpReceiverServer(
            parentScope = serviceScope,
            factoryProvider = {
                TvFileDestinationFactory(
                    context = ctx,
                    onFileCommitted = { file, payloadId ->
                        committedPayloadFiles[payloadId] = file
                        Log.i(TAG, "Quick Share file committed: ${file.absolutePath} for payload $payloadId")
                    }
                )
            },
            mediumRegistry = mediumRegistry,
            logger = { Log.i(TAG, "BadaCore: $it") }
        )
        tcpServer = server

        val friendlyName = try {
            android.provider.Settings.Global.getString(ctx.contentResolver, android.provider.Settings.Global.DEVICE_NAME)
                ?: android.provider.Settings.Secure.getString(ctx.contentResolver, "bluetooth_name")
                ?: android.os.Build.MODEL
        } catch (_: Exception) {
            android.os.Build.MODEL
        }

        val endpointInfo = EndpointInfo(
            version = 1,
            hidden = false,
            deviceType = DeviceType.LAPTOP, // "Laptop" shows reliably in all pickers without NFC/touch requirement
            reserved = false,
            metadata = ByteArray(EndpointInfo.METADATA_LEN).also { SecureRandom().nextBytes(it) },
            deviceName = friendlyName.take(EndpointInfo.MAX_NAME_LEN),
        )

        serviceScope.launch(Dispatchers.IO) {
            try {
                val port = server.start()

                // 1. Immediately start BLE pulse advertiser in LOW_LATENCY mode (parallel, no blocking)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    tryStartBleAdvertise(ctx, endpointInfo, currentEpId)
                }

                // 2. mDNS advertisement – returns a closeable handle
                try {
                    val disc = Discovery(ctx, instanceEndpointIdProvider = { currentEpId })
                    discovery = disc
                    val handle = disc.advertise(endpointInfo, port)
                    advertiseHandle = handle
                    Log.i(TAG, "Quick Share mDNS advertising started on port $port")
                } catch (e: Exception) {
                    Log.w(TAG, "mDNS advertise failed (non-fatal): ${e.message}")
                }

                // 3. Apple AirDrop compatibility service (BLE pulse + mDNS _airdrop._tcp + TLS 8770)
                try {
                    val airdrop = AirDropServer(
                        context = ctx,
                        scope = serviceScope,
                        deviceName = friendlyName,
                        onRequestConsent = { sender, files ->
                            val consentDeferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
                            _state.update {
                                it.copy(
                                    incomingTransfer = IncomingTransfer(
                                        senderName = sender,
                                        pin = "",
                                        files = files,
                                        phase = TransferPhase.WAITING_CONSENT,
                                        onConsent = { approved ->
                                            consentDeferred.complete(approved)
                                        }
                                    )
                                )
                            }
                            consentDeferred.await()
                        },
                        onTransferProgress = { frac, bps ->
                            _state.update { s ->
                                s.copy(
                                    incomingTransfer = s.incomingTransfer?.copy(
                                        progressFraction = frac,
                                        bytesPerSecond = bps
                                    )
                                )
                            }
                        },
                        onTransferComplete = { files ->
                            _state.update { s ->
                                s.copy(
                                    incomingTransfer = s.incomingTransfer?.copy(
                                        phase = TransferPhase.COMPLETED,
                                        progressFraction = 1f
                                    )
                                )
                            }
                            files.firstOrNull()?.let { f ->
                                showFileReceivedNotification(
                                    ReceivedFile(
                                        name = f.name,
                                        path = f.absolutePath,
                                        sizeBytes = f.length(),
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    )
                    airDropServer = airdrop
                    airdrop.start()
                    Log.i(TAG, "AirDrop compatibility service started")
                } catch (e: Exception) {
                    Log.w(TAG, "AirDrop server start failed (non-fatal): ${e.message}")
                }

                _state.update { it.copy(isAdvertising = true) }
                isStarting = false

                // Collect new connections
                launch {
                    server.activeConnections.collect { conn ->
                        observeConnection(conn)
                    }
                }

                // Collect completed connections (for file-received notifications)
                launch {
                    server.results.collect { completion ->
                        handleCompletion(completion)
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to start Quick Share receiver", e)
                isStarting = false
                _state.update { it.copy(isAdvertising = false) }
            }
        }
    }

    private fun stopReceivingInternal() {
        try {
            multicastLock?.release()
        } catch (_: Exception) {}
        multicastLock = null

        advertiseHandle?.close()
        advertiseHandle = null

        bleAdvertiser?.stop()
        bleAdvertiser = null

        airDropServer?.stop()
        airDropServer = null

        discovery = null

        val serverToStop = tcpServer
        tcpServer = null
        if (serverToStop != null) {
            try {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    serverToStop.stop()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping tcpServer: ${e.message}")
            }
        }

        _state.update { NativeQuickShareState(isAdvertising = false) }
    }

    fun stopReceiving() {
        isStarting = false
        stopReceivingInternal()
    }

    // ─── Connection observation ───────────────────────────────────────────────

    private fun observeConnection(conn: InboundConnection) {
        serviceScope.launch {
            conn.state.collect { connState ->
                when (connState) {
                    is InboundConnectionState.WaitingForUserConsent -> {
                        val meta = connState.metadata
                        val files = meta.items.mapNotNull { it.toFileInfo() }
                        _state.update {
                            it.copy(
                                incomingTransfer = IncomingTransfer(
                                    senderName = meta.sourceDeviceName,
                                    pin = meta.pin,
                                    files = files,
                                    phase = TransferPhase.WAITING_CONSENT,
                                    connection = conn,
                                )
                            )
                        }
                    }

                    is InboundConnectionState.Receiving -> {
                        val progress = connState.progress
                        val fraction = if (progress.totalSize > 0)
                            (progress.bytesTransferred.toFloat() / progress.totalSize.toFloat()).coerceIn(0f, 1f)
                        else 0f
                        _state.update { s ->
                            s.copy(
                                incomingTransfer = s.incomingTransfer?.copy(
                                    phase = TransferPhase.RECEIVING,
                                    progressFraction = fraction,
                                    bytesPerSecond = progress.bytesPerSecond,
                                )
                            )
                        }
                    }

                    is InboundConnectionState.Completed -> {
                        val ctx = applicationContext
                        val received = connState.items.mapNotNull { item ->
                            when (item) {
                                is dev.bluehouse.bada.protocol.connection.ReceivedItem.File -> {
                                    val committed = committedPayloadFiles.remove(item.payloadId)
                                        ?: File(TvFileDestinationFactory.getDownloadDir(ctx), item.header.fileName.ifEmpty { "file_${item.payloadId}" })
                                    ReceivedFile(
                                        name = committed.name,
                                        path = committed.absolutePath,
                                        sizeBytes = if (committed.exists()) committed.length() else item.bytesWritten,
                                        timestamp = System.currentTimeMillis(),
                                    )
                                }
                                else -> null
                            }
                        }
                        _state.update { s ->
                            s.copy(
                                incomingTransfer = s.incomingTransfer?.copy(phase = TransferPhase.COMPLETED),
                                lastReceivedFiles = received + s.lastReceivedFiles,
                            )
                        }
                        // Show notification for each received file
                        received.forEach { showFileReceivedNotification(it) }
                        // Auto-clear the consent dialog after 2 s
                        launch {
                            kotlinx.coroutines.delay(2_000)
                            _state.update { it.copy(incomingTransfer = null) }
                        }
                    }

                    is InboundConnectionState.Rejected -> {
                        _state.update { s ->
                            s.copy(incomingTransfer = s.incomingTransfer?.copy(phase = TransferPhase.REJECTED))
                        }
                        launch {
                            kotlinx.coroutines.delay(1_500)
                            _state.update { it.copy(incomingTransfer = null) }
                        }
                    }

                    is InboundConnectionState.Cancelled,
                    is InboundConnectionState.Failed -> {
                        _state.update { s ->
                            s.copy(incomingTransfer = s.incomingTransfer?.copy(phase = TransferPhase.FAILED))
                        }
                        launch {
                            kotlinx.coroutines.delay(1_500)
                            _state.update { it.copy(incomingTransfer = null) }
                        }
                    }

                    else -> { /* Idle / Handshaking / Negotiating — no UI change */ }
                }
            }
        }
    }

    private fun handleCompletion(completion: dev.bluehouse.bada.protocol.server.InboundConnectionCompletion) {
        // Results are already handled per-connection via observeConnection; this is a belt-and-braces
        android.util.Log.i(TAG, "Transfer #${completion.connectionId} finished: ${completion.result::class.simpleName}")
    }

    // ─── User consent (called from ViewModel) ────────────────────────────────

    fun submitConsent(accepted: Boolean) {
        val transfer = _state.value.incomingTransfer
        if (transfer?.connection != null) {
            transfer.connection.submitUserConsent(accepted)
        } else {
            transfer?.onConsent?.invoke(accepted)
        }
        if (accepted) {
            _state.update { s ->
                s.copy(incomingTransfer = s.incomingTransfer?.copy(phase = TransferPhase.RECEIVING))
            }
        } else {
            _state.update { s ->
                s.copy(incomingTransfer = s.incomingTransfer?.copy(phase = TransferPhase.REJECTED))
            }
        }
    }

    // ─── BLE advertising (API ≥ 26) ──────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    private fun tryStartBleAdvertise(ctx: android.content.Context, endpointInfo: EndpointInfo, epId: ByteArray) {
        try {
            val adv = BleQuickShareAdvertiser(ctx)
            adv.setAdvertiseMode(android.bluetooth.le.AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            bleAdvertiser = adv
            adv.start(endpointInfo, epId)
            Log.i(TAG, "Quick Share BLE advertiser started in LOW_LATENCY mode (endpoint=${String(epId)})")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "BLE advertise failed (non-fatal): ${e.message}")
        }
    }

    // ─── Notifications ────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_RUNNING, "Quick Share Receiver", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "TV is discoverable via Quick Share" }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_RECEIVED, "Quick Share – File Received", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun buildRunningNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_RUNNING)
                .setContentTitle("Quick Share active")
                .setContentText("Your TV is visible in nearby Quick Share pickers")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(tapIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
                .setContentTitle("Quick Share active")
                .setContentText("Your TV is visible in nearby Quick Share pickers")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(tapIntent)
                .setOngoing(true)
                .build()
        }
    }

    private fun showFileReceivedNotification(file: ReceivedFile) {
        val nm = getSystemService(NotificationManager::class.java)
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_RECEIVED)
                .setContentTitle("📥 ${file.name}")
                .setContentText("Saved to Downloads (${formatBytes(file.sizeBytes)})")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
                .setContentTitle("📥 ${file.name}")
                .setContentText("Saved to Downloads (${formatBytes(file.sizeBytes)})")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .build()
        }
        nm.notify(NOTIF_FILE_BASE + (file.timestamp % 1000).toInt(), notif)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024f)} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024f * 1024))} MB"
        else -> "${"%.2f".format(bytes / (1024f * 1024 * 1024))} GB"
    }

    companion object {
        const val ACTION_START = "com.peartv.app.share.ACTION_START"
        const val ACTION_STOP = "com.peartv.app.share.ACTION_STOP"
        private const val TAG = "NativeQSReceiver"
        private const val CHANNEL_RUNNING = "qs_receiver_running"
        private const val CHANNEL_RECEIVED = "qs_receiver_received"
        private const val NOTIF_ID = 9001
        private const val NOTIF_FILE_BASE = 9100
    }
}
