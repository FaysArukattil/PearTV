package com.peartv.app.ui

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.peartv.app.R
import com.peartv.app.data.MajorDebloatOption
import kotlinx.coroutines.delay

private const val LAST_STEP = 6

/**
 * First-run onboarding: Welcome > Features > Files > Usage > Nearby > USB debugging > One-tap clean.
 * [onAuthorizeAdb] connects to the TV's ADB (this triggers the "Allow USB debugging?" prompt).
 * Result: 0 = authorized, 1 = ADB port closed, 2 = refused/failed.
 */
@Composable
fun CleanTvWizardDialog(
    deviceLabel: String = "Android TV",
    majorOptions: List<MajorDebloatOption>,
    onToggleOption: (String) -> Unit,
    onOpenDeveloperOptions: () -> Unit = {},
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    showPermissions: Boolean = false,
    scanDone: Boolean = true,
    bloatLabels: List<String>? = null,
    onAuthorizeAdb: ((Int) -> Unit) -> Unit = { it(2) },
) {
    if (!showPermissions) {
        val focus = rememberAutoFocus()
        Box(
            Modifier.fillMaxSize().background(Color(0xE6000000)).modalTrap().clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.width(900.dp).glassCard().clickable(enabled = false) {}.padding(horizontal = 40.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CleanStep(deviceLabel, majorOptions, onToggleOption, onOpenDeveloperOptions, onConfirm, onDismiss, focus, scanDone, bloatLabels)
            }
        }
        return
    }

    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("leantv", Context.MODE_PRIVATE) }
    // Step is persisted: Android can restart the app when a permission is granted.
    var step by remember { mutableIntStateOf(sp.getInt("wizard_step", 0).coerceIn(0, LAST_STEP)) }
    LaunchedEffect(step) { sp.edit().putInt("wizard_step", step).apply() }
    val next = {
        sp.edit().putBoolean("wizard_pending", false).apply()
        step = (step + 1).coerceAtMost(LAST_STEP)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .modalTrap()
    ) {

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (fadeIn(tween(420, delayMillis = 140)) + slideInHorizontally(tween(460)) { it / 10 }) togetherWith fadeOut(tween(160))
            },
            label = "onboardingStep",
            modifier = Modifier.fillMaxSize()
        ) { s ->
            Box(Modifier.fillMaxSize().padding(horizontal = 110.dp).padding(top = 52.dp, bottom = 72.dp), contentAlignment = Alignment.Center) {
                when (s) {
                    0 -> WelcomePage(next)
                    1 -> FeaturesPage(next)
                    2 -> PermissionPage(filesSpec(), 1, 4, next, onAuthorizeAdb)
                    3 -> PermissionPage(usageSpec(), 2, 4, next, onAuthorizeAdb)
                    4 -> PermissionPage(nearbySpec(), 3, 4, next, onAuthorizeAdb)
                    5 -> PermissionPage(debugSpec(), 4, 4, next, onAuthorizeAdb)
                    else -> Column(
                        Modifier.width(900.dp).glassCard().padding(horizontal = 40.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val focus = rememberAutoFocus()
                        CleanStep(deviceLabel, majorOptions, onToggleOption, onOpenDeveloperOptions, onConfirm, onDismiss, focus, scanDone, bloatLabels)
                    }
                }
            }
        }

        // Brand mark (top-left) on every page after the welcome screen.
        if (step > 0) {
            Row(
                Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LogoMark(34.dp)
                Text("Pear TV", style = Ty.Heading.copy(color = Color(0xCCFFFFFF), fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(LAST_STEP + 1) { i ->
                val w by androidx.compose.animation.core.animateDpAsState(if (i == step) 30.dp else 9.dp, tween(260), label = "dotW")
                Box(
                    Modifier.height(6.dp).width(w).clip(RoundedCornerShape(50))
                        .background(if (i == step) Color.White else if (i < step) Color(0x99FFFFFF) else Color(0x44FFFFFF))
                )
            }
        }
    }
}

// ───────────────────────── helpers ─────────────────────────

@Composable
private fun FadeUp(delayMs: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    Box(modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 44f }) { content() }
}

/** The real app logo (ic_fg on the launcher background colour). */
@Composable
private fun LogoMark(size: Dp) {
    val shape = RoundedCornerShape(size * 0.26f)
    Box(
        Modifier.size(size).clip(shape).background(Color.Black).border(1.dp, Color(0x33FFFFFF), shape),
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(R.drawable.ic_fg), contentDescription = null, modifier = Modifier.requiredSize(size * 1.45f))
    }
}

@Composable
private fun LogoHero(size: Dp = 140.dp) {
    val t = rememberInfiniteTransition(label = "logoPulse")
    val s by t.animateFloat(
        1f, 1.18f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "logoScale"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size * 1.5f)
                .graphicsLayer { scaleX = s; scaleY = s }
                .background(Brush.radialGradient(listOf(Color(0x55FFB347), Color.Transparent)), CircleShape)
        )
        LogoMark(size)
    }
}

@Composable
private fun GradientIcon(icon: ImageVector, size: Int = 120) {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(
        1f, 1.16f,
        infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier.size((size + 36).dp)
                .graphicsLayer { scaleX = s; scaleY = s; alpha = 0.28f }
                .clip(CircleShape).background(Color(0xFF0A84FF))
        )
        Box(
            Modifier.size(size.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF64D2FF)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size((size * 0.5f).dp))
        }
    }
}

@Composable
private fun BigButton(text: String, onClick: () -> Unit, primary: Boolean = true, modifier: Modifier = Modifier) {
    TvButton(text = text, onClick = onClick, primary = primary, modifier = modifier)
}

// ───────────────────────── Page 0: welcome ─────────────────────────

@Composable
private fun WelcomePage(onNext: () -> Unit) {
    val focus = rememberAutoFocus()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FadeUp(0) {
            Image(
                painter = painterResource(R.drawable.banner),
                contentDescription = "Pear TV",
                modifier = Modifier
                    .width(420.dp)
                    .height(236.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        FadeUp(140) {
            Text(
                "A faster, cleaner home for your TV.",
                style = Ty.Dim.copy(
                    fontSize = 28.sp,
                    color = Color(0xCCFFFFFF),
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (-0.5).sp
                ),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(44.dp))
        FadeUp(260) {
            BigButton("Get started", onNext, modifier = Modifier.width(340.dp).focusRequester(focus))
        }
    }
}

// ───────────────────────── Page 1: features ─────────────────────────

private class Feature(val iconRes: Int, val title: String, val sub: String, val c1: Color, val c2: Color)

@Composable
private fun AppleAppIcon(
    iconRes: Int,
    c1: Color,
    c2: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 26.dp
) {
    val squircle = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 8.dp,
                shape = squircle,
                ambientColor = c1.copy(alpha = 0.5f),
                spotColor = c2.copy(alpha = 0.6f)
            )
            .clip(squircle)
            .background(Brush.linearGradient(listOf(c1, c2)))
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color(0x66FFFFFF),
                        Color(0x18FFFFFF)
                    )
                ),
                squircle
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x30FFFFFF),
                            Color(0x05FFFFFF),
                            Color.Transparent
                        )
                    )
                )
        )
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun FeaturesPage(onNext: () -> Unit) {
    val focus = rememberAutoFocus()
    val features = remember {
        listOf(
            Feature(R.drawable.ic_apple_tv_cleaner, "TV cleaner", "Remove bloat & ads", Color(0xFF0A84FF), Color(0xFF5E5CE6)),
            Feature(R.drawable.ic_apple_app_remover, "App remover", "Uninstall anything", Color(0xFFFF375F), Color(0xFFFF2D55)),
            Feature(R.drawable.ic_apple_files, "File manager", "Browse & manage files", Color(0xFF007AFF), Color(0xFF5AC8FA)),
            Feature(R.drawable.ic_apple_storage, "Storage", "Find & clear junk", Color(0xFFAF52DE), Color(0xFF5856D6)),
            Feature(R.drawable.ic_quick_share, "Quick Share", "Phone to TV, fast", Color(0xFF00C7BE), Color(0xFF007AFF)),
            Feature(R.drawable.ic_apple_screensavers, "Screensavers", "Apple TV aerials", Color(0xFF34C759), Color(0xFF00C7BE)),
        )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FadeUp(0) { Text("Everything your TV needs", style = Ty.Title.copy(color = Color.White, fontSize = 40.sp), textAlign = TextAlign.Center) }
        Spacer(Modifier.height(20.dp))
        features.chunked(3).forEachIndexed { r, rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                rowItems.forEachIndexed { c, f ->
                    FadeUp(120 + (r * 3 + c) * 90, Modifier.weight(1f)) { FeatureCard(f) }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Spacer(Modifier.height(6.dp))
        FadeUp(760) { BigButton("Continue", onNext, modifier = Modifier.width(360.dp).focusRequester(focus)) }
    }
}

@Composable
private fun FeatureCard(f: Feature) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .height(134.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x20FFFFFF), Color(0x0CFFFFFF))))
            .border(1.dp, Color(0x26FFFFFF), shape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.Center
    ) {
        AppleAppIcon(iconRes = f.iconRes, c1 = f.c1, c2 = f.c2)
        Spacer(Modifier.height(10.dp))
        Text(f.title, style = Ty.Heading.copy(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(f.sub, style = Ty.Small.copy(fontSize = 13.5.sp, color = Color(0xB3FFFFFF)), maxLines = 1)
    }
}

// ───────────────────────── Permission pages ─────────────────────────

private class PermSpec(
    val icon: ImageVector,
    val title: String,
    val why: String,
    val bullets: List<String>,
    val manualHint: String,
    val granted: (Context) -> Boolean,
    val runtime: Array<String>? = null,
    val intents: (Context) -> List<Intent> = { emptyList() },
    val isAdb: Boolean = false,
)

private fun usageAllowed(ctx: Context): Boolean = try {
    val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) == AppOpsManager.MODE_ALLOWED
} catch (_: Exception) { false }

private fun allGranted(ctx: Context, perms: Array<String>) =
    perms.all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

private fun tryStart(ctx: Context, intents: List<Intent>) {
    for (i in intents) {
        try { ctx.startActivity(i); return } catch (_: Exception) {}
    }
}

private fun findActivity(ctx: Context): Activity? {
    var c: Context? = ctx
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

private fun filesSpec() = PermSpec(
    icon = Icons.Rounded.Folder,
    title = "Files & storage",
    why = "LeanTV has a built-in file manager and storage cleaner. Both need access to your files.",
    bullets = listOf(
        "File manager: browse, copy, move and delete files",
        "Storage manager: find big files and junk, then clear them",
        "Quick Share: save files received from your phone"
    ),
    manualHint = "Turn the switch on for LeanTV, then press Back.",
    granted = { ctx ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else allGranted(ctx, arrayOf("android.permission.WRITE_EXTERNAL_STORAGE"))
    },
    runtime = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
        arrayOf("android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE") else null,
    intents = { ctx ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) listOf(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")),
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        ) else emptyList()
    }
)

private fun usageSpec() = PermSpec(
    icon = Icons.Rounded.BarChart,
    title = "Usage access",
    why = "Lets LeanTV see how big each app really is and how often you use it.",
    bullets = listOf("Accurate app and cache sizes", "Finds apps you never open", "Powers the storage charts"),
    manualHint = "Find LeanTV in the list, turn it on, then press Back.",
    granted = { usageAllowed(it) },
    intents = { listOf(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
)

private fun nearbySpec(): PermSpec {
    val perms: Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT", "android.permission.NEARBY_WIFI_DEVICES"
        )
        Build.VERSION.SDK_INT >= 31 -> arrayOf(
            "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_ADVERTISE", "android.permission.BLUETOOTH_CONNECT"
        )
        else -> arrayOf("android.permission.ACCESS_FINE_LOCATION")
    }
    return PermSpec(
        icon = Icons.Rounded.Bluetooth,
        title = "Nearby devices",
        why = if (Build.VERSION.SDK_INT >= 31) "Quick Share uses Bluetooth and Wi-Fi to find phones on your network."
              else "Android needs the location permission to scan for phones. Your location is never read or stored.",
        bullets = listOf("TV shows up on phones on the same Wi-Fi", "Receive photos, videos and files", "Only active while Quick Share is on"),
        manualHint = "Open Permissions in App Info and allow it for LeanTV.",
        granted = { allGranted(it, perms) },
        runtime = perms
    )
}

private fun debugSpec() = PermSpec(
    icon = Icons.Rounded.Code,
    title = "USB debugging (ADB)",
    why = "Needed to delete system apps and bloat. Everything stays on this TV.",
    bullets = listOf(
        "Delete or disable any app, even preinstalled ones",
        "Run the one-tap TV cleaner",
        "When asked “Allow USB debugging?”, tick Always allow"
    ),
    manualHint = "Turn on USB debugging (if hidden: About, tap Build 7 times), then come back.",
    granted = { ctx -> try { Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 } catch (_: Exception) { false } },
    intents = {
        listOf(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), Intent(Settings.ACTION_DEVICE_INFO_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    },
    isAdb = true
)

@Composable
private fun PermissionPage(
    spec: PermSpec,
    index: Int,
    total: Int,
    onNext: () -> Unit,
    onAuthorizeAdb: ((Int) -> Unit) -> Unit,
) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("leantv", Context.MODE_PRIVATE) }
    val focus = rememberAutoFocus()
    var tick by remember { mutableIntStateOf(0) }
    var requested by remember { mutableStateOf(sp.getBoolean("wizard_pending", false)) }
    var hint by remember { mutableStateOf<String?>(null) }
    var adbOk by remember { mutableStateOf(false) }
    var authorizing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { while (true) { delay(600); tick++ } }
    val enabled = remember(tick) { spec.granted(ctx) }
    val granted = if (spec.isAdb) adbOk else enabled

    LaunchedEffect(granted, requested) {
        if (granted && requested) { delay(650); onNext() }
    }

    fun authorize() {
        if (authorizing) return
        authorizing = true
        hint = "Look for “Allow USB debugging?” on screen, tick Always allow, then press OK."
        onAuthorizeAdb { r ->
            authorizing = false
            when (r) {
                0 -> { adbOk = true; hint = null }
                1 -> hint = "Also turn on Network debugging / ADB over network in Developer options, then press Allow again."
                else -> hint = "Not authorized yet. Press Allow to try again."
            }
        }
    }

    // After the user switches USB debugging on in Settings and comes back, ask for the ADB prompt right away.
    LaunchedEffect(enabled, requested) {
        if (spec.isAdb && enabled && requested && !adbOk) authorize()
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        tick++
        if (res.values.any { !it }) {
            val act = findActivity(ctx)
            val blocked = act != null && res.filter { !it.value }.keys.any { !act.shouldShowRequestPermissionRationale(it) }
            if (blocked) {
                hint = "Android blocked the pop-up. Open Permissions and allow it for LeanTV."
                tryStart(ctx, listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))))
            } else hint = "Not allowed yet. You can allow it any time in App Info."
        }
    }

    fun onContinue() {
        if (granted) { onNext(); return }
        requested = true
        sp.edit().putBoolean("wizard_pending", true).apply()
        if (spec.isAdb) {
            if (enabled) authorize() else { hint = spec.manualHint; tryStart(ctx, spec.intents(ctx)) }
            return
        }
        val rt = spec.runtime
        if (rt != null) launcher.launch(rt) else { hint = spec.manualHint; tryStart(ctx, spec.intents(ctx)) }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(64.dp), verticalAlignment = Alignment.CenterVertically) {
        FadeUp(0) { GradientIcon(spec.icon, 150) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FadeUp(60) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(if (granted) Color(0x3330D158) else Color(0x3364D2FF))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (granted) { Icon(Icons.Rounded.Check, null, tint = Color(0xFF30D158), modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
                    Text(
                        if (granted) "ALLOWED" else "PERMISSION $index OF $total",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                        color = if (granted) Color(0xFF30D158) else Color(0xFF64D2FF)
                    )
                }
            }
            FadeUp(120) { Text(spec.title, style = Ty.Title.copy(color = Color.White, fontSize = 44.sp)) }
            FadeUp(180) { Text(spec.why, style = Ty.Dim.copy(fontSize = 21.sp, color = Color(0xCCFFFFFF))) }
            Spacer(Modifier.height(2.dp))
            spec.bullets.forEachIndexed { i, b ->
                FadeUp(260 + i * 90) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0x3330D158)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, tint = Color(0xFF30D158), modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(b, style = Ty.Small.copy(fontSize = 19.sp, color = Color(0xE6FFFFFF)))
                    }
                }
            }
            if (hint != null) Text(hint!!, style = Ty.Small.copy(fontSize = 17.sp, color = Color(0xFFFFD60A)))
            Spacer(Modifier.height(8.dp))
            FadeUp(560) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    BigButton(
                        if (granted) "Continue" else if (authorizing) "Waiting…" else "Allow",
                        { onContinue() },
                        modifier = Modifier.width(300.dp).focusRequester(focus)
                    )
                    BigButton("Not now", onNext, primary = false, modifier = Modifier.width(200.dp))
                }
            }
            Text("You can change this any time in Settings.", style = Ty.Small.copy(fontSize = 15.sp, color = Color(0x80FFFFFF)))
        }
    }
}

// ───────────────────────── Step 2: one-tap clean ─────────────────────────

@Composable
private fun CleanStep(
    deviceLabel: String,
    majorOptions: List<MajorDebloatOption>,
    onToggleOption: (String) -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    primaryFocus: androidx.compose.ui.focus.FocusRequester,
    scanDone: Boolean,
    bloatLabels: List<String>?,
) {
    val removing = majorOptions.count { it.isSelectedForRemoval }
    // null = caller doesn't scan (old single-step dialog): behave as before.
    val nothingToClean = bloatLabels != null && scanDone && bloatLabels.isEmpty()

    if (nothingToClean) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Color(0x3330D158)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFF30D158), modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Your TV is already clean", style = Ty.Title.copy(color = Color.White, fontSize = 32.sp))
        Spacer(Modifier.height(6.dp))
        Text(
            "No bloat or ad apps were found on $deviceLabel, so there is nothing to remove.",
            style = Ty.Dim.copy(fontSize = 18.sp),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        TvButton(text = "Done", onClick = onSkip, primary = true, modifier = Modifier.fillMaxWidth().focusRequester(primaryFocus))
        return
    }

    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x3364D2FF))
            .padding(horizontal = 14.dp, vertical = 5.dp)
    ) {
        Text(
            "DETECTED: ${deviceLabel.uppercase()}",
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64D2FF), letterSpacing = 1.sp
        )
    }
    Spacer(Modifier.height(10.dp))
    Text("Clean your TV", style = Ty.Title.copy(color = Color.White, fontSize = 32.sp))
    Spacer(Modifier.height(4.dp))
    Text(
        "One tap removes ads, trackers and background junk. Pick what to keep or remove.",
        style = Ty.Dim.copy(fontSize = 18.sp),
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(16.dp))

    if (majorOptions.isNotEmpty()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 270.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(majorOptions, key = { it.id }) { opt ->
                MajorOptionRow(option = opt, onToggle = { onToggleOption(opt.id) })
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    if (bloatLabels != null) {
        val shown = bloatLabels.take(4).joinToString(", ")
        val more = bloatLabels.size - 4
        Text(
            "${bloatLabels.size} installed app(s) will be disabled",
            style = Ty.Heading.copy(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        )
        Text(
            if (bloatLabels.isEmpty()) "Everything is set to Keep." else shown + if (more > 0) " and $more more" else "",
            style = Ty.Small.copy(fontSize = 16.sp, color = Color(0x99FFFFFF)),
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    } else {
        Text(
            if (removing == 0) "Nothing selected to remove" else "$removing selected to remove, ${majorOptions.size - removing} kept",
            style = Ty.Small.copy(fontSize = 16.sp, color = Color(0x99FFFFFF))
        )
    }
    Spacer(Modifier.height(10.dp))

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x14FFFFFF))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("One-time authorization", style = Ty.Heading.copy(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            Text("Turn on USB debugging in Developer Options, then accept the prompt.", style = Ty.Small.copy(fontSize = 15.sp, color = Color(0x99FFFFFF)))
        }
        Spacer(Modifier.width(12.dp))
        DevOptionsChip(onOpenDeveloperOptions)
    }

    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        TvButton(text = "Skip for now", onClick = onSkip, primary = false, modifier = Modifier.weight(1f))
        TvButton(
            text = if (bloatLabels != null && bloatLabels.isNotEmpty()) "Clean ${bloatLabels.size} apps now" else "Clean TV now",
            onClick = onConfirm,
            primary = true,
            enabled = bloatLabels == null || bloatLabels.isNotEmpty(),
            modifier = Modifier.weight(1.6f).focusRequester(primaryFocus)
        )
    }
}

@Composable
private fun DevOptionsChip(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color(0x33FFFFFF))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text("Open Developer Options", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (focused) Color.Black else Color(0xFF64D2FF))
    }
}

@Composable
private fun MajorOptionRow(option: MajorDebloatOption, onToggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val removing = option.isSelectedForRemoval
    val shape = RoundedCornerShape(28.dp)

    val bg by animateColorAsState(
        if (focused) Color(0x40FFFFFF) else if (removing) Color(0x22FF453A) else Color(0x1AFFFFFF),
        tween(140), label = "optBg"
    )
    val ring by animateColorAsState(if (focused) Color.White else Color(0x24FFFFFF), tween(140), label = "optRing")
    val track by animateColorAsState(if (removing) Color(0xFFFF453A) else Color(0xFF30D158), tween(160), label = "optTrack")

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(if (focused) 2.dp else 1.dp, ring, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(option.title, style = Ty.Heading.copy(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            Text(option.description, style = Ty.Small.copy(fontSize = 16.sp, color = Color(0xAAFFFFFF)), maxLines = 2)
        }
        Spacer(Modifier.width(16.dp))
        Text(
            if (removing) "Remove" else "Keep",
            style = Ty.Small.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = track)
        )
        Spacer(Modifier.width(12.dp))
        // Switch: knob on the right = remove, on the left = keep.
        Box(
            Modifier.width(54.dp).height(30.dp).clip(RoundedCornerShape(50)).background(track.copy(alpha = 0.35f)).border(1.5.dp, track, RoundedCornerShape(50)),
            contentAlignment = if (removing) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(Modifier.padding(3.dp).size(22.dp).clip(CircleShape).background(track))
        }
    }
}