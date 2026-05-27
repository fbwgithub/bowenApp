package com.bowenapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bowenapp.data.model.FileInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMagnetScreen(
    onParse: (String) -> Unit,
    onDownload: (String, List<Int>) -> Unit,
    isParsing: Boolean,
    parsedFiles: List<FileInfo>,
    torrentName: String,
    totalSize: String,
    errorMessage: String?
) {
    var magnetText by remember { mutableStateOf("") }
    var selectedFiles by remember { mutableStateOf(setOf<Int>()) }
    var hasParsed by remember { mutableStateOf(false) }

    // Reset selected when new parse results come
    LaunchedEffect(parsedFiles) {
        if (parsedFiles.isNotEmpty()) {
            hasParsed = true
            selectedFiles = parsedFiles.map { it.index }.toSet()
        }
    }

    // Clear error state after showing
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) hasParsed = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Magnet input
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("磁力链接", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = magnetText,
                    onValueChange = { magnetText = it },
                    placeholder = { Text("magnet:?xt=urn:btih:...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        hasParsed = false
                        onParse(magnetText.trim())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isParsing && magnetText.isNotBlank()
                ) {
                    if (isParsing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("解析中...")
                    } else {
                        Text("解析磁力链接")
                    }
                }
            }
        }

        // Error message
        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    "❌ $errorMessage",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Parse result
        if (hasParsed && torrentName.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("📁 $torrentName", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 14.sp)
                    Text("大小: $totalSize | 文件: ${parsedFiles.size} 个",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // File list + download button
        if (hasParsed && parsedFiles.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))

            Text("选择文件", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

            parsedFiles.forEach { file ->
                val ext = file.path.split(".").lastOrNull()?.lowercase() ?: ""
                val isVideo = ext in listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v")
                val icon = when (ext) {
                    in listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v") -> "🎬"
                    in listOf("mp3", "aac", "wav", "flac") -> "🎵"
                    else -> "📁"
                }
                val isChecked = file.index in selectedFiles

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .toggleable(
                            value = isChecked,
                            onValueChange = {
                                selectedFiles = if (isChecked) selectedFiles - file.index
                                    else selectedFiles + file.index
                            }
                        ),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(icon, fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                file.path.split("/").lastOrNull() ?: file.path,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(file.sizeStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isVideo) {
                            Spacer(Modifier.width(4.dp))
                            FilledTonalButton(
                                onClick = { /* play in parsed state - requires magnet re-parse */ },
                                contentPadding = PaddingValues(6.dp),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("▶", fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = null
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (selectedFiles.isNotEmpty()) {
                        onDownload(magnetText.trim(), selectedFiles.toList())
                        hasParsed = false
                        magnetText = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedFiles.isNotEmpty()
            ) {
                Text("📥 下载选中 (${selectedFiles.size}/${parsedFiles.size})")
            }
        }
    }
}
