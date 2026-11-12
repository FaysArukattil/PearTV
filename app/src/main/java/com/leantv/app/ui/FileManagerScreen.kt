package com.leantv.app.ui

import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Description
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leantv.app.files.FileItem
import com.leantv.app.files.FileManagerHelper
import com.leantv.app.files.StorageDrive
import com.leantv.app.storage.StorageHelper
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FileManagerScreen(
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var drives by remember { mutableStateOf<List<StorageDrive>>(emptyList()) }
    var currentDir by remember { mutableStateOf<File?>(null) }
    var currentFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var activeCategory by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    var selectedFile by remember { mutableStateOf<FileItem?>(null) }
    var showRenameDialog by remember { mutableStateOf<FileItem?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf<FileItem?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val hasPerm = remember { FileManagerHelper.hasStoragePermission(ctx) }

    fun loadDir(dir: File) {
        scope.launch {
            isLoading = true
            currentDir = dir
            activeCategory = null
            currentFiles = FileManagerHelper.listDirectory(dir)
            isLoading = false
        }
    }

    fun loadCategory(key: String) {
        scope.launch {
            isLoading = true
            activeCategory = key
            currentDir = null
            currentFiles = FileManagerHelper.scanCategoryFiles(key)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        val detected = FileManagerHelper.detectStorageDrives(ctx)
        drives = detected
        if (detected.isNotEmpty()) {
            loadDir(detected.first().path)
        }
    }

    // Intercept Back button: navigate up directory, or close file manager
    BackHandler {
        val cur = currentDir
        if (activeCategory != null) {
            val root = drives.firstOrNull()?.path
            if (root != null) loadDir(root) else onClose()
        } else if (cur != null && drives.none { it.path.path == cur.path }) {
            val parent = cur.parentFile
            if (parent != null && parent.canRead()) {
                loadDir(parent)
            } else {
                onClose()
            }
        } else {
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SAMSUNG MY FILES",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFF9F0A),
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "TV File Manager",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!hasPerm) {
                        FileGlassPill(
                            text = "Grant Storage Access",
                            accent = Color(0xFFFF9F0A),
                            onClick = { FileManagerHelper.requestStoragePermission(ctx) }
                        )
                    }
                    FileGlassPill(
                        text = "Close",
                        accent = Color.White,
                        onClick = onClose
                    )
                }
            }

            statusMessage?.let { msg ->
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x33FFA726))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(text = msg, color = Color(0xFFFFD60A), fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(18.dp))

            // Categories Row (Samsung style)
            val categories = listOf(
                Pair("downloads", "Downloads"),
                Pair("videos", "Videos"),
                Pair("apks", "APK Files"),
                Pair("audio", "Audio"),
                Pair("images", "Images"),
                Pair("documents", "Documents")
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(categories) { (key, label) ->
                    CategoryPill(
                        label = label,
                        isSelected = activeCategory == key,
                        onClick = { loadCategory(key) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Storage Drives Row
            if (drives.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(drives) { drive ->
                        DriveCard(
                            drive = drive,
                            isSelected = currentDir?.path == drive.path.path,
                            onClick = { loadDir(drive.path) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Path Breadcrumb
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x14FFFFFF))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (activeCategory != null) "Category: ${activeCategory?.replaceFirstChar { it.uppercase() }}"
                    else currentDir?.path ?: "Storage Root",
                    color = Color(0xBBFFFFFF),
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(12.dp))

            // File & Directory List
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Scanning storage...", color = Color(0x88FFFFFF), fontSize = 19.sp)
                }
            } else if (currentFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("This folder is empty.", color = Color(0x66FFFFFF), fontSize = 19.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x0EFFFFFF))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(currentFiles, key = { it.file.absolutePath }) { item ->
                        FileRow(
                            item = item,
                            isSelected = selectedFile?.file?.absolutePath == item.file.absolutePath,
                            onItemClick = {
                                if (item.isDirectory) {
                                    loadDir(item.file)
                                } else if (item.extension == "apk") {
                                    val success = FileManagerHelper.installApk(ctx, item.file)
                                    if (!success) statusMessage = "Unable to install APK"
                                } else {
                                    val opened = FileManagerHelper.openMediaFile(ctx, item.file)
                                    if (!opened) statusMessage = "No viewer found for ${item.name}"
                                }
                            },
                            onToggleSelect = {
                                selectedFile = if (selectedFile?.file?.absolutePath == item.file.absolutePath) null else item
                            },
                            onDelete = { showDeleteDialog = item },
                            onRename = {
                                showRenameDialog = item
                                renameInput = item.name
                            }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    showDeleteDialog?.let { fileToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete File?", color = Color.White) },
            text = { Text("Are you sure you want to permanently delete '${fileToDelete.name}'?", color = Color(0xCCFFFFFF)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val ok = FileManagerHelper.deleteFile(fileToDelete.file)
                        showDeleteDialog = null
                        if (ok) {
                            statusMessage = "Deleted ${fileToDelete.name}"
                            currentDir?.let { loadDir(it) } ?: activeCategory?.let { loadCategory(it) }
                        } else {
                            statusMessage = "Failed to delete file"
                        }
                    }
                }) {
                    Text("Delete", color = Color(0xFFFF453A))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF232326)
        )
    }

    // Rename Dialog
    showRenameDialog?.let { fileToRename ->
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename File", color = Color.White) },
            text = {
                Column {
                    Text("Enter new filename:", color = Color(0xCCFFFFFF), fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0x22FFFFFF),
                            unfocusedContainerColor = Color(0x11FFFFFF)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val ok = FileManagerHelper.renameFile(fileToRename.file, renameInput.trim())
                        showRenameDialog = null
                        if (ok) {
                            statusMessage = "Renamed to ${renameInput.trim()}"
                            currentDir?.let { loadDir(it) } ?: activeCategory?.let { loadCategory(it) }
                        } else {
                            statusMessage = "Failed to rename file"
                        }
                    }
                }) {
                    Text("Save", color = Color(0xFF64D2FF))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF232326)
        )
    }
}

@Composable
private fun CategoryPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .scale(if (isFocused) 1.05f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) Color.White
                else if (isSelected) Color(0x44FFA726)
                else Color(0x1AFFFFFF)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else if (isSelected) Color(0xFFFF9F0A) else Color(0x22FFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = if (isFocused) Color.Black else if (isSelected) Color(0xFFFF9F0A) else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DriveCard(
    drive: StorageDrive,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .scale(if (isFocused) 1.03f else 1f)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isFocused) Color(0x38FFFFFF)
                else if (isSelected) Color(0x29FFFFFF)
                else Color(0x14FFFFFF)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x22FFFFFF),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (drive.isRemovable) Icons.Rounded.Usb else Icons.Rounded.Storage,
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(text = drive.name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${StorageHelper.formatBytes(drive.freeBytes)} free of ${StorageHelper.formatBytes(drive.totalBytes)}",
                color = Color(0x88FFFFFF),
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun FileRow(
    item: FileItem,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault()) }

    val fileIcon = when {
        item.isDirectory -> Icons.Rounded.Folder
        item.extension == "apk" -> Icons.Rounded.Android
        item.mimeType.startsWith("video/") -> Icons.Rounded.Movie
        item.mimeType.startsWith("audio/") -> Icons.Rounded.MusicNote
        item.mimeType.startsWith("image/") -> Icons.Rounded.Image
        else -> Icons.Rounded.Description
    }
    val fileTint = when {
        item.isDirectory -> Color(0xFF64D2FF)
        item.extension == "apk" -> Color(0xFF30D158)
        item.mimeType.startsWith("video/") -> Color(0xFFBF5AF2)
        item.mimeType.startsWith("audio/") -> Color(0xFFFF9F0A)
        item.mimeType.startsWith("image/") -> Color(0xFFFF453A)
        else -> Color(0xFF8E8E93)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (isFocused) 1.01f else 1f)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isFocused) Color(0x3DFFFFFF)
                else if (isSelected) Color(0x24FFFFFF)
                else Color(0x0CFFFFFF)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x16FFFFFF),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onItemClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(fileIcon, contentDescription = null, tint = fileTint, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = item.name,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${dateFormat.format(Date(item.lastModified))} • ${if (item.isDirectory) "Folder" else StorageHelper.formatBytes(item.sizeBytes)}",
                        color = Color(0x88FFFFFF),
                        fontSize = 15.sp
                    )
                }
            }

            // Quick type badge
            if (item.extension.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x22FFFFFF))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = item.extension.uppercase(),
                        color = Color(0xDDFFFFFF),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Inline Action bar when focused or selected
        if (isFocused || isSelected) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FileActionPill(
                    text = if (item.isDirectory) "Open" else if (item.extension == "apk") "Install" else "Play / View",
                    onClick = onItemClick
                )
                FileActionPill(text = "Rename", onClick = onRename)
                FileActionPill(text = "Delete", onClick = onDelete)
            }
        }
    }
}

@Composable
private fun FileActionPill(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isFocused) Color.White else Color(0x26FFFFFF))
            .border(1.dp, if (isFocused) Color.White else Color(0x33FFFFFF), RoundedCornerShape(6.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FileGlassPill(
    text: String,
    accent: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .scale(if (isFocused) 1.05f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) accent else Color(0x1FFFFFFF))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x33FFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (isFocused && accent == Color.White) Color.Black else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
