package com.peartv.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.util.LruCache
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.peartv.app.files.FileManagerHelper
import com.peartv.app.storage.StorageFileItem
import com.peartv.app.storage.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private enum class FileSort(val label: String) {
    LARGEST("Largest first"),
    NEWEST("Newest first"),
    NAME("Name A-Z")
}

private fun catAccent(c: StorageCategory): Color = when (c) {
    StorageCategory.VIDEOS -> Color(0xFF8B5CF6)
    StorageCategory.IMAGES -> Color(0xFFEC4899)
    StorageCategory.AUDIO -> Color(0xFFF59E0B)
    StorageCategory.APKS -> Color(0xFF06B6D4)
    else -> Color.White
}

private fun catIcon(c: StorageCategory) = when (c) {
    StorageCategory.VIDEOS -> Icons.Rounded.Movie
    StorageCategory.IMAGES -> Icons.Rounded.Image
    StorageCategory.AUDIO -> Icons.Rounded.MusicNote
    StorageCategory.APKS -> Icons.Rounded.Android
    else -> Icons.Rounded.Description
}

private fun openLabel(c: StorageCategory) = when (c) {
    StorageCategory.IMAGES -> "View Full Screen"
    StorageCategory.VIDEOS -> "Play Video"
    StorageCategory.AUDIO -> "Play Audio"
    StorageCategory.APKS -> "Install APK"
    else -> "Open"
}

private fun Modifier.dpad(handler: (Int) -> Boolean): Modifier = this.onPreviewKeyEvent { ev ->
    ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && handler(ev.nativeKeyEvent.keyCode)
}

private fun fmtDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private fun fmtDate(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

// ─────────────────────────────────────────────────────────────────────────────
// Thumbnails / metadata (all decoding happens off the main thread, 2 at a time)
// ─────────────────────────────────────────────────────────────────────────────

private fun decodeImage(file: File, maxPx: Int): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, o)
    if (o.outWidth <= 0 || o.outHeight <= 0) return null
    var s = 1
    while (o.outWidth / (s * 2) >= maxPx && o.outHeight / (s * 2) >= maxPx) s *= 2
    val b = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
    val rot = runCatching {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            6 -> 90
            3 -> 180
            8 -> 270
            else -> 0
        }
    }.getOrDefault(0)
    return if (rot == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
}

private fun scaleDown(b: Bitmap, maxPx: Int): Bitmap {
    val m = maxOf(b.width, b.height)
    if (m <= maxPx) return b
    val f = maxPx.toFloat() / m
    return Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
}

private object Thumbs {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val gate = Semaphore(2)

    suspend fun get(ctx: Context, file: File, cat: StorageCategory, px: Int): Bitmap? {
        val key = "${file.path}@$px@${file.lastModified()}"
        cache.get(key)?.let { return it }
        val bmp = gate.withPermit {
            withContext(Dispatchers.IO) { runCatching { decode(ctx, file, cat, px) }.getOrNull() }
        }
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    private fun decode(ctx: Context, file: File, cat: StorageCategory, px: Int): Bitmap? = when (cat) {
        StorageCategory.IMAGES -> decodeImage(file, px)
        StorageCategory.VIDEOS -> {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(file.path)
                r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { scaleDown(it, px) }
            } finally {
                runCatching { r.release() }
            }
        }
        StorageCategory.AUDIO -> {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(file.path)
                r.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.let { scaleDown(it, px) }
            } finally {
                runCatching { r.release() }
            }
        }
        StorageCategory.APKS -> {
            val pm = ctx.packageManager
            val info = pm.getPackageArchiveInfo(file.path, 0)
            val ai = info?.applicationInfo
            if (ai != null) {
                ai.sourceDir = file.path
                ai.publicSourceDir = file.path
                ai.loadIcon(pm).toBitmap(px.coerceAtMost(256), px.coerceAtMost(256))
            } else null
        }
        else -> null
    }
}

private fun readMeta(ctx: Context, file: File, cat: StorageCategory): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    when (cat) {
        StorageCategory.IMAGES -> {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, o)
            if (o.outWidth > 0) out.add("Resolution" to "${o.outWidth} x ${o.outHeight}")
        }
        StorageCategory.VIDEOS, StorageCategory.AUDIO -> {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(file.path)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { out.add("Duration" to fmtDuration(it)) }
                if (cat == StorageCategory.VIDEOS) {
                    val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    if (w != null && h != null) out.add("Resolution" to "$w x $h")
                } else {
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { out.add("Title" to it) }
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { out.add("Artist" to it) }
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { out.add("Album" to it) }
                }
            } finally {
                runCatching { r.release() }
            }
        }
        StorageCategory.APKS -> {
            val pm = ctx.packageManager
            val info = pm.getPackageArchiveInfo(file.path, 0)
            if (info != null) {
                info.applicationInfo?.let { ai ->
                    ai.sourceDir = file.path
                    ai.publicSourceDir = file.path
                    out.add("App" to ai.loadLabel(pm).toString())
                }
                out.add("Package" to info.packageName)
                info.versionName?.let { out.add("Version" to it) }
                val installed = runCatching { pm.getPackageInfo(info.packageName, 0) }.getOrNull() != null
                out.add("Status" to if (installed) "Already installed" else "Not installed")
            }
        }
        else -> {}
    }
    return out
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Videos / Photos / Audio / APK browser, built for a TV remote.
 *
 *  OK on a file      = tick / untick it (cursor then moves down, so you can tick quickly)
 *  RIGHT on a file   = jump into the side panel (View/Play/Install, Select, Delete)
 *  LEFT / BACK       = return to the same file in the list
 *  Top bar           = Select all, Sort, Delete selected
 *  Deleting always needs a second OK press.
 *  Photos open in a full-screen viewer: LEFT/RIGHT = previous/next, OK = show/hide info, BACK = close.
 */
@Composable
fun StorageFilesView(
    category: StorageCategory,
    title: String,
    files: List<StorageFileItem>,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val accent = catAccent(category)
    val listState = rememberLazyListState()

    var items by remember { mutableStateOf(files) }
    var sort by remember { mutableStateOf(FileSort.LARGEST) }
    val checked = remember { mutableStateMapOf<String, Boolean>() }
    var focusedPath by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmTargets by remember { mutableStateOf<List<StorageFileItem>?>(null) }
    var inPanel by remember { mutableStateOf(false) }
    var viewerPath by remember { mutableStateOf<String?>(null) }

    val sorted = remember(items, sort) {
        when (sort) {
            FileSort.LARGEST -> items.sortedByDescending { it.size }
            FileSort.NEWEST -> items.sortedByDescending { it.lastModified }
            FileSort.NAME -> items.sortedBy { it.name.lowercase() }
        }
    }
    val sortedNow by rememberUpdatedState(sorted)
    val current: StorageFileItem? = sorted.firstOrNull { it.path == focusedPath } ?: sorted.firstOrNull()
    val checkedCount = checked.size
    val checkedBytes = remember(items, checkedCount) { items.filter { it.path in checked }.sumOf { it.size } }
    val totalBytes = remember(items) { items.sumOf { it.size } }

    // focus requesters
    val backF = remember { FocusRequester() }
    val selAllF = remember { FocusRequester() }
    val sortF = remember { FocusRequester() }
    val delSelF = remember { FocusRequester() }
    val actionF = remember { List(3) { FocusRequester() } }
    val viewerF = remember { FocusRequester() }
    val itemF = remember { HashMap<String, FocusRequester>() }
    fun itemReq(path: String) = itemF.getOrPut(path) { FocusRequester() }

    fun focusList(index: Int) {
        scope.launch {
            val l = sortedNow
            if (l.isEmpty()) {
                runCatching { backF.requestFocus() }
                return@launch
            }
            val i = index.coerceIn(0, l.lastIndex)
            runCatching { listState.scrollToItem(i) }
            delay(60)
            val l2 = sortedNow
            if (l2.isNotEmpty()) runCatching { itemReq(l2[i.coerceAtMost(l2.lastIndex)].path).requestFocus() }
        }
    }

    fun focusCurrent() {
        focusList(sortedNow.indexOfFirst { it.path == current?.path }.coerceAtLeast(0))
    }

    LaunchedEffect(Unit) {
        delay(150)
        focusList(0)
    }
    LaunchedEffect(status) {
        if (status != null) {
            delay(4500)
            status = null
        }
    }

    fun toggle(path: String) {
        if (checked.remove(path) == null) checked[path] = true
    }

    StorageBackHandler {
        when {
            viewerPath != null -> {
                val p = viewerPath
                viewerPath = null
                focusList(sortedNow.indexOfFirst { it.path == p }.coerceAtLeast(0))
            }
            inPanel -> focusCurrent()
            else -> onBack()
        }
        true
    }

    fun deleteFiles(targets: List<StorageFileItem>) {
        if (busy || targets.isEmpty()) return
        scope.launch {
            busy = true
            val idx = sortedNow.indexOfFirst { it.path == current?.path }.coerceAtLeast(0)
            var ok = 0
            var bytes = 0L
            val gone = HashSet<String>()
            for (t in targets) {
                if (StorageHelper.deleteFile(ctx, t.file)) {
                    ok++
                    bytes += t.size
                    gone.add(t.path)
                }
            }
            items = items.filter { it.path !in gone }
            gone.forEach { checked.remove(it) }
            status = if (ok == targets.size) "✓ Deleted $ok file(s) • freed ${StorageHelper.formatBytes(bytes)}"
            else "Deleted $ok of ${targets.size} file(s) - some could not be removed"
            busy = false
            delay(100)
            focusList(idx)
        }
    }

    fun openItem(it: StorageFileItem) {
        if (category == StorageCategory.IMAGES) {
            viewerPath = it.path
            scope.launch {
                delay(80)
                runCatching { viewerF.requestFocus() }
            }
        } else if (!FileManagerHelper.openFile(ctx, it.file)) {
            status = "No app on this TV can open ${it.name}"
        }
    }

    // preview
    var previewBmp by remember { mutableStateOf<Bitmap?>(null) }
    var previewMeta by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(current?.path) {
        previewBmp = null
        previewMeta = emptyList()
        val f = current ?: return@LaunchedEffect
        delay(200) // don't decode while the cursor is flying through the list
        previewBmp = Thumbs.get(ctx, f.file, category, 720)
        previewMeta = withContext(Dispatchers.IO) { runCatching { readMeta(ctx, f.file, category) }.getOrDefault(emptyList()) }
    }

    // ══════════════════════════ UI ══════════════════════════
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ---- header ----
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Focusable(
                    onClick = onBack,
                    modifier = Modifier.focusRequester(backF).dpad { k ->
                        when (k) {
                            KeyEvent.KEYCODE_DPAD_RIGHT -> { runCatching { selAllF.requestFocus() }; true }
                            KeyEvent.KEYCODE_DPAD_DOWN -> { if (sortedNow.isNotEmpty()) focusCurrent(); true }
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> true
                            else -> false
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    idle = Color(0x22FFFFFF)
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Storage", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "${items.size} files • ${StorageHelper.formatBytes(totalBytes)}" +
                            if (checkedCount > 0) " • $checkedCount selected (${StorageHelper.formatBytes(checkedBytes)})" else "",
                        fontSize = 14.sp, color = Color(0xAAFFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val allChecked = items.isNotEmpty() && checkedCount == items.size
                    TopChip(
                        text = if (allChecked) "Clear selection" else "Select all",
                        modifier = Modifier.focusRequester(selAllF).dpad { k ->
                            when (k) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> { runCatching { backF.requestFocus() }; true }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> { runCatching { sortF.requestFocus() }; true }
                                KeyEvent.KEYCODE_DPAD_DOWN -> { if (sortedNow.isNotEmpty()) focusCurrent(); true }
                                KeyEvent.KEYCODE_DPAD_UP -> true
                                else -> false
                            }
                        },
                        onClick = {
                            if (allChecked) checked.clear() else items.forEach { checked[it.path] = true }
                        }
                    )
                    TopChip(
                        text = "Sort: ${sort.label}",
                        modifier = Modifier.focusRequester(sortF).dpad { k ->
                            when (k) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> { runCatching { selAllF.requestFocus() }; true }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> { if (checkedCount > 0) runCatching { delSelF.requestFocus() }; true }
                                KeyEvent.KEYCODE_DPAD_DOWN -> { if (sortedNow.isNotEmpty()) focusCurrent(); true }
                                KeyEvent.KEYCODE_DPAD_UP -> true
                                else -> false
                            }
                        },
                        onClick = { sort = FileSort.entries[(sort.ordinal + 1) % FileSort.entries.size] }
                    )
                    if (checkedCount > 0) {
                        TopChip(
                            text = "Delete selected ($checkedCount • ${StorageHelper.formatBytes(checkedBytes)})",
                            danger = true,
                            modifier = Modifier.focusRequester(delSelF).dpad { k ->
                                when (k) {
                                    KeyEvent.KEYCODE_DPAD_LEFT -> { runCatching { sortF.requestFocus() }; true }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> { if (sortedNow.isNotEmpty()) focusCurrent(); true }
                                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP -> true
                                    else -> false
                                }
                            },
                            onClick = { confirmTargets = items.filter { it.path in checked } }
                        )
                    }
                }
            }

            status?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x334CAF50))
                        .border(1.dp, Color(0x664CAF50), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text(msg, color = Color(0xFF30D158), fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }

            Spacer(Modifier.height(12.dp))

            if (items.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Color(0x0EFFFFFF)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF30D158), modifier = Modifier.size(54.dp))
                        Text("No $title found", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("Nothing to clean here.", fontSize = 15.sp, color = Color(0x88FFFFFF))
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    // ---- LEFT: file list ----
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(0.55f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(Color(0x0EFFFFFF)).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        itemsIndexed(sorted, key = { _, it -> it.path }) { index, item ->
                            val isChecked = item.path in checked
                            var thumb by remember(item.path) { mutableStateOf<Bitmap?>(null) }
                            LaunchedEffect(item.path) { thumb = Thumbs.get(ctx, item.file, category, 112) }

                            Focusable(
                                onClick = {
                                    toggle(item.path)
                                    focusManager.moveFocus(FocusDirection.Down)
                                },
                                shape = RoundedCornerShape(14.dp),
                                idle = if (isChecked) accent.copy(alpha = 0.22f) else Color(0x12FFFFFF),
                                scaleOnFocus = 1.02f,
                                modifier = Modifier.focusRequester(itemReq(item.path)).dpad { k ->
                                    when (k) {
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> { runCatching { actionF[0].requestFocus() }; true }
                                        KeyEvent.KEYCODE_DPAD_LEFT -> true
                                        KeyEvent.KEYCODE_DPAD_UP -> if (index == 0) { runCatching { selAllF.requestFocus() }; true } else false
                                        KeyEvent.KEYCODE_DPAD_DOWN -> index == sortedNow.lastIndex
                                        else -> false
                                    }
                                }
                            ) { focused ->
                                LaunchedEffect(focused) { if (focused) focusedPath = item.path }
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (isChecked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isChecked) accent else Color(0x66FFFFFF),
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Box(
                                        Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val t = thumb
                                        if (t != null) {
                                            Image(t.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        } else {
                                            Icon(catIcon(category), null, tint = accent, modifier = Modifier.size(26.dp))
                                        }
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(fmtDate(item.lastModified), color = Color(0x88FFFFFF), fontSize = 13.sp, maxLines = 1)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x22FFFFFF)).padding(horizontal = 12.dp, vertical = 5.dp)) {
                                        Text(StorageHelper.formatBytes(item.size), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // ---- RIGHT: preview + actions ----
                    Column(
                        Modifier.weight(0.45f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(Color(0x16FFFFFF))
                            .onFocusChanged { inPanel = it.hasFocus }
                            .verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val cur = current
                        if (cur == null) {
                            Text("Nothing selected", color = Color(0x66FFFFFF), fontSize = 18.sp)
                        } else {
                            Box(
                                Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x33000000)),
                                contentAlignment = Alignment.Center
                            ) {
                                val b = previewBmp
                                if (b != null) {
                                    Image(
                                        b.asImageBitmap(), null, Modifier.fillMaxSize().padding(if (category == StorageCategory.APKS) 36.dp else 0.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    if (category == StorageCategory.VIDEOS) {
                                        Icon(Icons.Rounded.PlayCircle, null, tint = Color(0xCCFFFFFF), modifier = Modifier.size(54.dp))
                                    }
                                } else {
                                    Icon(catIcon(category), null, tint = accent.copy(alpha = 0.8f), modifier = Modifier.size(64.dp))
                                }
                            }
                            Text(cur.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(cur.path, color = Color(0x77FFFFFF), fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                InfoRow("Size", StorageHelper.formatBytes(cur.size))
                                InfoRow("Modified", fmtDate(cur.lastModified))
                                previewMeta.forEach { (k, v) -> InfoRow(k, v) }
                            }

                            val acts = listOf(
                                Triple("open", openLabel(category), true),
                                Triple("sel", if (cur.path in checked) "Unselect" else "Select", false),
                                Triple("del", "Delete this file", false),
                            )
                            acts.forEachIndexed { i, (id, label, primary) ->
                                TvButton(
                                    text = if (busy) "Working..." else label,
                                    primary = primary,
                                    onClick = {
                                        if (!busy) {
                                            when (id) {
                                                "open" -> openItem(cur)
                                                "sel" -> toggle(cur.path)
                                                "del" -> confirmTargets = listOf(cur)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().focusRequester(actionF[i])
                                        .dpad { k ->
                                            when (k) {
                                                KeyEvent.KEYCODE_DPAD_LEFT -> { focusCurrent(); true }
                                                KeyEvent.KEYCODE_DPAD_UP -> {
                                                    if (i > 0) runCatching { actionF[i - 1].requestFocus() } else runCatching { selAllF.requestFocus() }
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_DOWN -> { if (i < acts.lastIndex) runCatching { actionF[i + 1].requestFocus() }; true }
                                                KeyEvent.KEYCODE_DPAD_RIGHT -> true
                                                else -> false
                                            }
                                        }
                                )
                            }
                            Text(
                                "OK on a file = tick it • → = view / delete",
                                color = Color(0x66FFFFFF), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // ══════════ full-screen photo viewer ══════════
        val vp = viewerPath
        if (vp != null) {
            val vIndex = sorted.indexOfFirst { it.path == vp }
            val vItem = sorted.getOrNull(vIndex)
            if (vItem == null) {
                viewerPath = null
            } else {
                var vBmp by remember(vp) { mutableStateOf<Bitmap?>(null) }
                var showInfo by remember { mutableStateOf(true) }
                LaunchedEffect(vp) {
                    vBmp = withContext(Dispatchers.IO) { runCatching { decodeImage(vItem.file, 2000) }.getOrNull() }
                }
                Box(
                    Modifier.fillMaxSize().background(Color.Black)
                        .focusRequester(viewerF)
                        .dpad { k ->
                            when (k) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> { sorted.getOrNull(vIndex - 1)?.let { viewerPath = it.path }; true }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> { sorted.getOrNull(vIndex + 1)?.let { viewerPath = it.path }; true }
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { showInfo = !showInfo; true }
                                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> true
                                else -> false
                            }
                        }
                        .focusable(),
                    contentAlignment = Alignment.Center
                ) {
                    val b = vBmp
                    if (b != null) Image(b.asImageBitmap(), vItem.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else Text("Loading...", color = Color(0x99FFFFFF), fontSize = 18.sp)

                    if (showInfo) {
                        Column(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xAA000000)).padding(horizontal = 40.dp, vertical = 16.dp)
                        ) {
                            Text(vItem.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${vIndex + 1} / ${sorted.size} • ${StorageHelper.formatBytes(vItem.size)} • ${fmtDate(vItem.lastModified)}",
                                color = Color(0xCCFFFFFF), fontSize = 15.sp
                            )
                            Text("◀ ▶ browse • OK hide info • BACK close", color = Color(0x88FFFFFF), fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        confirmTargets?.let { t ->
            ConfirmDialog(
                title = if (t.size == 1) "Delete ${t[0].name}?" else "Delete ${t.size} files?",
                message = "${StorageHelper.formatBytes(t.sumOf { it.size })} will be permanently removed. This cannot be undone.",
                confirmText = "Delete",
                onConfirm = {
                    confirmTargets = null
                    deleteFiles(t)
                },
                onDismiss = {
                    confirmTargets = null
                    focusCurrent()
                }
            )
        }
    }
}

@Composable
private fun TopChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) {
    Focusable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        idle = if (danger) Color(0x33FF453A) else Color(0x22FFFFFF),
        scaleOnFocus = 1.05f
    ) {
        Text(
            text,
            color = if (danger) Color(0xFFFF8A80) else Color.White,
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = Color(0x99FFFFFF), fontSize = 14.sp)
        Spacer(Modifier.width(12.dp))
        Text(v, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    }
}