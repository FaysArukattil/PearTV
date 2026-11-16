package com.peartv.app.data

import java.text.DateFormat
import java.util.Date
import java.util.Locale

enum class AppTypeFilter(val label: String) {
    ALL("All Apps"),
    USER("User Apps"),
    SYSTEM("System Apps"),
}

enum class AppSortOrder(val label: String) {
    NAME("Name (A-Z)"),
    SIZE("Size (Largest)"),
    DATE("Install Date (Newest)"),
}

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val isUpdatedSystemApp: Boolean,
    val installTimeMillis: Long,
    val lastUpdateTimeMillis: Long,
    val sizeBytes: Long,
    val launchIntentAvailable: Boolean,
) {
    val canUninstall: Boolean
        get() = !isSystemApp || isUpdatedSystemApp

    val formattedSize: String
        get() = when {
            sizeBytes <= 0 -> "Unknown size"
            sizeBytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", sizeBytes / 1024f)
            sizeBytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", sizeBytes / (1024f * 1024f))
            else -> String.format(Locale.US, "%.2f GB", sizeBytes / (1024f * 1024f * 1024f))
        }

    val formattedInstallDate: String
        get() = if (installTimeMillis > 0) {
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(installTimeMillis))
        } else {
            "Pre-installed"
        }
}

