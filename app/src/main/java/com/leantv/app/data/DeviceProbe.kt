package com.leantv.app.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock

object DeviceProbe {

    fun read(ctx: Context): DeviceProfile {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val stat = StatFs(Environment.getDataDirectory().path)
        val dm = ctx.resources.displayMetrics
        val gb = 1024.0 * 1024.0 * 1024.0

        return DeviceProfile(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            android = Build.VERSION.RELEASE.orEmpty(),
            sdk = Build.VERSION.SDK_INT,
            patch = Build.VERSION.SECURITY_PATCH.orEmpty(),
            ramTotalMb = mi.totalMem / (1024 * 1024),
            ramFreeMb = mi.availMem / (1024 * 1024),
            storageTotalGb = stat.totalBytes / gb,
            storageFreeGb = stat.availableBytes / gb,
            cores = Runtime.getRuntime().availableProcessors(),
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            resolution = "${dm.widthPixels} x ${dm.heightPixels}",
            uptimeHours = SystemClock.elapsedRealtime() / 3_600_000L,
            family = family(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty(), Build.MODEL.orEmpty()),
        )
    }

    private fun family(manufacturer: String, brand: String, model: String): TvFamily {
        val s = "$manufacturer $brand $model".lowercase()
        return when {
            "xiaomi" in s || "redmi" in s || model.startsWith("MiTV", ignoreCase = true) -> TvFamily.XIAOMI
            "sony" in s -> TvFamily.SONY
            "tcl" in s -> TvFamily.TCL
            "hisense" in s -> TvFamily.HISENSE
            "philips" in s || "tpv" in s -> TvFamily.PHILIPS
            "oneplus" in s -> TvFamily.ONEPLUS
            "realme" in s -> TvFamily.REALME
            "nvidia" in s -> TvFamily.NVIDIA
            "amazon" in s -> TvFamily.FIRE_TV
            else -> TvFamily.GENERIC
        }
    }
}
