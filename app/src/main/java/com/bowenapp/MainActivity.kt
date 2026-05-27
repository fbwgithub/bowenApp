package com.bowenapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bowenapp.data.ServerConfig
import com.bowenapp.ui.screens.*
import com.bowenapp.ui.theme.BowenAppTheme
import com.bowenapp.viewmodel.TorrentViewModel

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val viewModel: TorrentViewModel = viewModel()
    var selectedTab by remember { mutableStateOf(Tab.TASKS) }
    var showDetail by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }

    val torrents by viewModel.torrents.collectAsState()
    val currentTorrent by viewModel.currentTorrent.collectAsState()
    val isParsing by viewModel.isParsing.collectAsState()
    val parsedFiles by viewModel.parsedFiles.collectAsState()
    val parsedName by viewModel.parsedName.collectAsState()
    val parsedSize by viewModel.totalParsedSize.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val streamUrl by viewModel.streamUrl.collectAsState()
    val stats by viewModel.stats.collectAsState()

    // Auto-show player when stream URL is ready
    LaunchedEffect(streamUrl) {
        if (streamUrl.first.isNotEmpty()) {
            showPlayer = true
        }
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
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                Tab.TASKS -> TorrentListScreen(
                    torrents = torrents,
                    onTorrentClick = { hash ->
                        viewModel.loadTorrentDetail(hash)
                        showDetail = true
                    }
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

    // Detail overlay
    if (showDetail && currentTorrent != null) {
        TorrentDetailScreen(
            torrent = currentTorrent!!,
            onBack = { showDetail = false },
            onPlay = { hash, idx, name -> viewModel.playFile(hash, idx, name) },
            onPause = { viewModel.pauseTorrent(it) },
            onResume = { viewModel.resumeTorrent(it) },
            onRemove = { viewModel.removeTorrent(it); showDetail = false }
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
