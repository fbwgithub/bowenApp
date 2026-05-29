package com.bowenapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bowenapp.data.ServerConfig
import com.bowenapp.ui.screens.*
import com.bowenapp.ui.theme.BowenAppTheme
import com.bowenapp.viewmodel.TorrentViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServerConfig.init(applicationContext)

        setContent {
            BowenAppTheme {
                MainApp()
            }
        }
    }
}

enum class Tab(val label: String, val icon: ImageVector) {
    TASKS("任务", Icons.Default.Download),
    ADD("添加", Icons.Default.Add),
    SETTINGS("设置", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainApp() {
    val viewModel: TorrentViewModel = viewModel()
    val pagerState = rememberPagerState(pageCount = { Tab.entries.size })
    val scope = rememberCoroutineScope()
    var showDetail by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }

    // Delete confirmation state
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var deleteFilesChecked by remember { mutableStateOf(false) }

    val torrents by viewModel.torrents.collectAsState()
    val currentTorrent by viewModel.currentTorrent.collectAsState()
    val isParsing by viewModel.isParsing.collectAsState()
    val parsedFiles by viewModel.parsedFiles.collectAsState()
    val parsedName by viewModel.parsedName.collectAsState()
    val parsedSize by viewModel.totalParsedSize.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val streamUrl by viewModel.streamUrl.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    // Show player when stream URL is ready
    LaunchedEffect(streamUrl) {
        if (streamUrl.first.isNotEmpty()) {
            showPlayer = true
        }
    }

    // Reset checkbox when dialog opens
    LaunchedEffect(deleteTarget) {
        deleteFilesChecked = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚡ 磁力下载") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { page ->
            when (Tab.entries[page]) {
                Tab.TASKS -> TorrentListScreen(
                    torrents = torrents,
                    onTorrentClick = { hash ->
                        viewModel.loadTorrentDetail(hash)
                        showDetail = true
                    },
                    onPause = { viewModel.pauseTorrent(it) },
                    onResume = { viewModel.resumeTorrent(it) },
                    onDelete = { deleteTarget = it },
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshTorrents() }
                )
                Tab.ADD -> AddMagnetScreen(
                    onParse = { magnet -> viewModel.parseMagnet(magnet) },
                    onDownload = { magnet, files -> viewModel.downloadSelected(magnet, files) },
                    isParsing = isParsing,
                    parsedFiles = parsedFiles,
                    torrentName = parsedName,
                    totalSize = parsedSize,
                    errorMessage = errorMessage
                )
                Tab.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    stats = stats
                )
            }
        }
    }

    // Delete confirmation dialog
    deleteTarget?.let { hash ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("🗑 删除任务") },
            text = {
                Column {
                    Text("确定要删除这个下载任务吗？")
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { deleteFilesChecked = !deleteFilesChecked }
                    ) {
                        Checkbox(
                            checked = deleteFilesChecked,
                            onCheckedChange = { deleteFilesChecked = it }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "同时删除已下载的文件",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeTorrent(hash, deleteFilesChecked)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    // Detail overlay
    if (showDetail && currentTorrent != null) {
        TorrentDetailScreen(
            torrent = currentTorrent!!,
            onBack = { showDetail = false },
            onPlay = { hash, idx, name -> viewModel.playFile(hash, idx, name) },
            onPause = { viewModel.pauseTorrent(it) },
            onResume = { viewModel.resumeTorrent(it) },
            onRemove = { hash ->
                deleteTarget = hash
                showDetail = false
            }
        )
    }

    // Player overlay
    if (showPlayer && streamUrl.first.isNotEmpty()) {
        PlayerScreen(
            streamUrl = streamUrl.first,
            fileName = streamUrl.second,
            onBack = {
                viewModel.clearStream()
                showPlayer = false
            }
        )
    }
}
