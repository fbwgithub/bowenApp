package com.bowenapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bowenapp.data.ServerConfig
import com.bowenapp.data.model.StatsResponse
import com.bowenapp.viewmodel.TorrentViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: TorrentViewModel,
    stats: StatsResponse
) {
    var currentIp by remember { mutableStateOf(ServerConfig.getBaseIp()) }
    var editIp by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Server Settings
        Text("⚙ 服务器设置", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("当前服务器", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(currentIp, fontSize = 16.sp, fontWeight = FontWeight.Medium)

                if (isEditing) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editIp,
                        onValueChange = { editIp = it },
                        label = { Text("服务器地址") },
                        placeholder = { Text("192.168.1.12:5000") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { isEditing = false; editIp = "" }) {
                            Text("取消")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val input = editIp.trim()
                            if (input.isNotBlank()) {
                                val url = if (input.startsWith("http://") || input.startsWith("https://"))
                                    input else "http://$input"
                                viewModel.updateBaseUrl(url)
                                currentIp = ServerConfig.getBaseIp()
                                isEditing = false
                                editIp = ""
                                statusMsg = "✅ 已更新"
                            }
                        }) {
                            Text("保存")
                        }
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            editIp = currentIp
                            isEditing = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("修改服务器地址")
                    }
                }

                if (statusMsg.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(statusMsg, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Stats
        Text("📊 实时统计", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                StatRow("下载任务", "${stats.totalTorrents}")
                StatRow("活跃任务", "${stats.activeDownloads}")
                StatRow("下载速度", stats.totalDownloadRate)
                StatRow("上传速度", stats.totalUploadRate)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Tips
        Text("📌 提示", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                listOf(
                    "支持磁力链接和 .torrent 文件",
                    "解析后可选择指定文件下载",
                    "视频文件支持在线播放",
                    "修改服务器地址后会自动重连"
                ).forEach {
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("• ", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
