package com.bowenapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bowenapp.data.model.FileInfo
import com.bowenapp.data.model.TorrentInfo
import com.bowenapp.ui.components.FileProgressBar
import com.bowenapp.ui.components.TorrentProgressBar

@Composable
fun TorrentDetailScreen(
    torrent: TorrentInfo,
    onBack: () -> Unit,
    onPlay: (String, Int, String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("← 返回", fontSize = 16.sp) }
                Spacer(Modifier.width(8.dp))
                Text(
                    torrent.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            // Stats grid
            val pct = (torrent.progress * 100).toInt()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("状态", torrent.state)
                        StatItem("进度", "$pct%")
                        StatItem("大小", torrent.totalSizeStr)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("Peers", "${torrent.numPeers}")
                        StatItem("下载", torrent.downloadRateStr)
                        StatItem("上传", torrent.uploadRateStr)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            TorrentProgressBar(progress = torrent.progress.toFloat())
            Spacer(Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (torrent.state == "paused") {
                    Button(onClick = { onResume(torrent.infoHash) }, modifier = Modifier.weight(1f)) {
                        Text("▶ 继续")
                    }
                } else {
                    OutlinedButton(onClick = { onPause(torrent.infoHash) }, modifier = Modifier.weight(1f)) {
                        Text("⏸ 暂停")
                    }
                }
                Button(
                    onClick = { onRemove(torrent.infoHash) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("🗑 删除")
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("📁 文件 (${torrent.files.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(8.dp))

            torrent.files.forEach { file ->
                val isVideo = isVideoFile(file.path)
                FileItemRow(
                    file = file,
                    isVideo = isVideo,
                    onPlay = if (isVideo) {{ onPlay(torrent.infoHash, file.index, file.path) }} else null
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FileItemRow(
    file: FileInfo,
    isVideo: Boolean,
    onPlay: (() -> Unit)?
) {
    val ext = file.path.split(".").lastOrNull()?.lowercase() ?: ""
    val icon = when (ext) {
        in listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v") -> "🎬"
        in listOf("mp3", "aac", "wav", "flac") -> "🎵"
        else -> "📁"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
            if (isVideo && onPlay != null) {
                FilledTonalButton(onClick = onPlay, contentPadding = PaddingValues(8.dp), modifier = Modifier.size(36.dp)) {
                    Text("▶", fontSize = 12.sp)
                }
                Spacer(Modifier.width(4.dp))
            }
            OutlinedButton(onClick = { }, contentPadding = PaddingValues(8.dp), modifier = Modifier.size(36.dp)) {
                Text("💾", fontSize = 12.sp)
            }
        }
    }
}

private fun isVideoFile(path: String): Boolean {
    val ext = path.split(".").lastOrNull()?.lowercase() ?: return false
    return ext in listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v")
}
