package com.bowenapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bowenapp.data.model.FileInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMagnetScreen(
    onBack: () -> Unit,
    onParse: (String) -> Unit,
    onDownload: (List<Int>) -> Unit,
    isParsing: Boolean,
    parsedFiles: List<FileInfo>,
    torrentName: String,
    totalSize: String,
    errorMessage: String?
) {
    var magnetUrl by remember { mutableStateOf("") }
    var selectedFiles by remember { mutableStateOf<Set<Int>>(emptySet()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加磁力链接") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← 返回") }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = magnetUrl,
                onValueChange = { magnetUrl = it },
                label = { Text("磁力链接") },
                placeholder = { Text("magnet:?xt=urn:btih:...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { onParse(magnetUrl) },
                enabled = magnetUrl.startsWith("magnet:") && !isParsing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isParsing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isParsing) "解析中..." else "解析磁力链接")
            }

            errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (parsedFiles.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("📁 $torrentName", style = MaterialTheme.typography.titleSmall)
                        Text("大小: $totalSize | 文件数: ${parsedFiles.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Select All / Deselect All
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("选择文件", style = MaterialTheme.typography.titleSmall)
                    Row {
                        TextButton(onClick = {
                            selectedFiles = parsedFiles.map { it.index }.toSet()
                        }) { Text("全选") }
                        TextButton(onClick = { selectedFiles = emptySet() }) {
                            Text("取消全选") }
                    }
                }

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(parsedFiles, key = { it.index }) { file ->
                        val isSelected = file.index in selectedFiles
                        val ext = file.path.split(".").lastOrNull() ?: ""
                        val icon = when (ext.lowercase()) {
                            in listOf("mp4","mkv","avi","mov","wmv","flv","webm","m4v") -> "🎬"
                            in listOf("mp3","aac","wav","flac","ogg","m4a") -> "🎵"
                            in listOf("jpg","jpeg","png","gif","webp") -> "🖼️"
                            in listOf("zip","rar","tar","gz","7z") -> "📦"
                            in listOf("pdf","txt","doc","docx") -> "📄"
                            else -> "📁"
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = isSelected,
                                    onValueChange = {
                                        selectedFiles = if (it) selectedFiles + file.index
                                        else selectedFiles - file.index
                                    }
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(icon, fontSize = 20.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        file.path.split("/").lastOrNull() ?: file.path,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        file.sizeStr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { onDownload(selectedFiles.toList()) },
                    enabled = selectedFiles.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("下载选中文件 (${selectedFiles.size}/${parsedFiles.size})")
                }
            }
        }
    }
}
