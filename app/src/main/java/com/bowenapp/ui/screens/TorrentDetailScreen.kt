package com.bowenapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bowenapp.data.model.TorrentInfo
import com.bowenapp.data.model.FileInfo
import com.bowenapp.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentDetailScreen(
    torrent: TorrentInfo?,
    onBack: () -> Unit,
    onPlay: (String, Int) -> Unit,
    onDownload: (String, Int) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = torrent?.name ?: "详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
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
        if (torrent == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Info card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusBadge(torrent.state)
                            Text(
                                "${(torrent.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        TorrentProgressBar(
                            progress = torrent.progress.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        val items = listOf(
                            "总大小" to torrent.totalSizeStr,
                            "已下载" to formatBytes(torrent.totalDownload),
                            "下载速度" to torrent.downloadRateStr,
                            "上传速度" to torrent.uploadRateStr,
                            "Peers" to "${torrent.numPeers}",
                            "Seeds" to "${torrent.numSeeds}"
                        )
                        Column {
                            items.forEach { (label, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall)
                                    Text(value, color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            // Action buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (torrent.state == "paused") {
                        Button(
                            onClick = { onResume(torrent.infoHash) },
                            modifier = Modifier.weight(1f)
                        ) { Text("▶ 继续") }
                    } else {
                        OutlinedButton(
                            onClick = { onPause(torrent.infoHash) },
                            modifier = Modifier.weight(1f)
                        ) { Text("⏸ 暂停") }
                    }
                    OutlinedButton(
                        onClick = { onRemove(torrent.infoHash) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("🗑 删除") }
                }
            }

            // Files header
            item {
                Text(
                    "文件列表 (${torrent.files.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // File items
            items(torrent.files, key = { "${torrent.infoHash}_${it.index}" }) { file ->
                FileItemRow(
                    file = file,
                    isVideo = isVideoFile(file.path),
                    onPlay = { onPlay(torrent.infoHash, file.index) },
                    onDownload = { onDownload(torrent.infoHash, file.index) }
                )
            }
        }
    }
}

@Composable
private fun FileItemRow(
    file: FileInfo,
    isVideo: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    val ext = file.path.split(".").lastOrNull() ?: ""
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileIcon(ext)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.path.split("/").lastOrNull() ?: file.path,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(file.sizeStr, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FileProgressBar(progress = file.progress.toFloat())
                    Spacer(Modifier.width(8.dp))
                    Text("${file.progress.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (isVideo) {
                FilledTonalButton(onClick = onPlay, contentPadding = PaddingValues(8.dp)) {
                    Text("▶", fontSize = MaterialTheme.typography.bodySmall.fontSize)
                }
                Spacer(Modifier.width(4.dp))
            }
            OutlinedButton(onClick = onDownload, contentPadding = PaddingValues(8.dp)) {
                Text("💾", fontSize = MaterialTheme.typography.bodySmall.fontSize)
            }
        }
    }
}

private fun isVideoFile(path: String): Boolean {
    val ext = path.split(".").lastOrNull()?.lowercase() ?: return false
    return ext in listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v")
}

private fun formatBytes(bytes: Long): String {
    if (bytes == 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var i = 0
    var size = bytes.toDouble()
    while (size >= 1024 && i < units.size - 1) {
        size /= 1024.0
        i++
    }
    return "%.2f %s".format(size, units[i])
}
