package com.leantv.app.monitor

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import java.util.Locale

data class RamSnapshot(
    val totalBytes: Long,
    val availBytes: Long,
    val usedBytes: Long,
    val percentUsed: Int,
    val isLowMem: Boolean,
) {
    val totalMb: Long get() = totalBytes / (1024 * 1024)
    val usedMb: Long get() = usedBytes / (1024 * 1024)
    val availMb: Long get() = availBytes / (1024 * 1024)

    val formattedTotal: String
        get() = String.format(Locale.US, "%.1f GB", totalBytes / (1024f * 1024f * 1024f))
    val formattedUsed: String
        get() = String.format(Locale.US, "%.1f GB", usedBytes / (1024f * 1024f * 1024f))
    val formattedAvail: String
        get() = String.format(Locale.US, "%.1f GB", availBytes / (1024f * 1024f * 1024f))
}

data class CpuSnapshot(
    val percent: Int?,
    val cores: Int,
    val statusText: String,
)

data class RunningAppItem(
    val pid: Int,
    val packageName: String,
    val appName: String,
    val pssMb: Float,
    val isSystemApp: Boolean,
)

class SystemMonitor(private val context: Context) {

    private val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val pm = context.packageManager

    private var lastTotalJiffies: Long = 0
    private var lastIdleJiffies: Long = 0

    suspend fun sampleRam(): RamSnapshot = withContext(Dispatchers.IO) {
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val total = memInfo.totalMem
        val avail = memInfo.availMem
        val used = (total - avail).coerceAtLeast(0)
        val percent = if (total > 0) ((used.toDouble() / total.toDouble()) * 100).toInt() else 0
        RamSnapshot(
            totalBytes = total,
            availBytes = avail,
            usedBytes = used,
            percentUsed = percent.coerceIn(0, 100),
            isLowMem = memInfo.lowMemory,
        )
    }

    suspend fun sampleCpu(): CpuSnapshot = withContext(Dispatchers.IO) {
        val cores = Runtime.getRuntime().availableProcessors()

        // Reading /proc/stat is restricted by SELinux on Android 10+ (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val estimatedThreads = Thread.activeCount()
            return@withContext CpuSnapshot(
                percent = null,
                cores = cores,
                statusText = "$cores cores active ($estimatedThreads thread units)"
            )
        }

        try {
            RandomAccessFile("/proc/stat", "r").use { reader ->
                val line = reader.readLine() ?: return@withContext CpuSnapshot(null, cores, "$cores cores")
                val tokens = line.split("\\s+".toRegex())
                if (tokens.size >= 5 && tokens[0] == "cpu") {
                    val user = tokens[1].toLong()
                    val nice = tokens[2].toLong()
                    val system = tokens[3].toLong()
                    val idle = tokens[4].toLong()
                    val iowait = if (tokens.size > 5) tokens[5].toLong() else 0
                    val irq = if (tokens.size > 6) tokens[6].toLong() else 0
                    val softirq = if (tokens.size > 7) tokens[7].toLong() else 0

                    val total = user + nice + system + idle + iowait + irq + softirq
                    val totalDelta = total - lastTotalJiffies
                    val idleDelta = idle - lastIdleJiffies

                    lastTotalJiffies = total
                    lastIdleJiffies = idle

                    if (totalDelta > 0) {
                        val cpuPercent = (100 * (totalDelta - idleDelta) / totalDelta).toInt().coerceIn(0, 100)
                        return@withContext CpuSnapshot(cpuPercent, cores, "$cpuPercent% Load • $cores Cores")
                    }
                }
            }
        } catch (_: Exception) {
            // Graceful fallback: do not crash
        }

        CpuSnapshot(percent = null, cores = cores, statusText = "$cores Cores")
    }

    suspend fun sampleRunningApps(): List<RunningAppItem> = withContext(Dispatchers.IO) {
        val processes = am.runningAppProcesses ?: return@withContext emptyList()
        val myUid = Process.myUid()
        val myPkg = context.packageName

        val result = mutableListOf<RunningAppItem>()

        for (proc in processes) {
            val pkg = proc.pkgList?.firstOrNull() ?: proc.processName.split(":").first()
            if (pkg == myPkg) continue // Don't kill self

            var isSystem = false
            val appLabel = try {
                val ai = pm.getApplicationInfo(pkg, 0)
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                pm.getApplicationLabel(ai).toString().ifBlank { pkg }
            } catch (_: PackageManager.NameNotFoundException) {
                pkg
            }

            var pssMb = 0f
            try {
                val mem = am.getProcessMemoryInfo(intArrayOf(proc.pid))
                if (mem.isNotEmpty()) {
                    pssMb = mem[0].totalPss / 1024f
                }
            } catch (_: Exception) {
                // Ignore memory info query failure
            }

            result.add(
                RunningAppItem(
                    pid = proc.pid,
                    packageName = pkg,
                    appName = appLabel,
                    pssMb = pssMb,
                    isSystemApp = isSystem,
                )
            )
        }

        // Deduplicate by package name keeping the highest memory instance, then sort by memory desc
        result.groupBy { it.packageName }
            .map { (_, items) -> items.maxByOrNull { it.pssMb } ?: items.first() }
            .sortedByDescending { it.pssMb }
    }

    fun killBackgroundProcess(packageName: String) {
        if (packageName == context.packageName) return
        try {
            am.killBackgroundProcesses(packageName)
        } catch (_: Exception) {
            // Ignore
        }
    }

    fun killAllBackgroundProcesses(packages: List<String>) {
        val myPkg = context.packageName
        for (pkg in packages) {
            if (pkg != myPkg) {
                try {
                    am.killBackgroundProcesses(pkg)
                } catch (_: Exception) {
                    // Ignore
                }
            }
        }
    }
}

