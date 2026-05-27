package com.bowenapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bowenapp.ui.screens.*
import com.bowenapp.ui.theme.BowenAppTheme
import com.bowenapp.viewmodel.TorrentViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BowenAppTheme {
                val viewModel: TorrentViewModel = viewModel()
                val navController = rememberNavController()
                val torrents by viewModel.torrents.collectAsState()
                val currentTorrent by viewModel.currentTorrent.collectAsState()
                val isParsing by viewModel.isParsing.collectAsState()
                val parsedFiles by viewModel.parsedFiles.collectAsState()
                val parsedName by viewModel.parsedName.collectAsState()
                val parsedSize by viewModel.parsedSize.collectAsState()
                val errorMessage by viewModel.errorMessage.collectAsState()
                val streamUrl by viewModel.streamUrl.collectAsState()

                var pendingMagnet by remember { mutableStateOf("") }

                LaunchedEffect(streamUrl) {
                    if (streamUrl.first.isNotEmpty()) {
                        navController.navigate("player/${streamUrl.first}/${streamUrl.second}")
                    }
                }

                NavHost(navController = navController, startDestination = "torrents") {
                    composable("torrents") {
                        TorrentListScreen(
                            torrents = torrents,
                            onAddClick = { navController.navigate("add") },
                            onTorrentClick = { infoHash ->
                                viewModel.loadTorrentDetail(infoHash)
                                navController.navigate("detail")
                            }
                        )
                    }

                    composable("add") {
                        AddMagnetScreen(
                            onBack = { navController.popBackStack() },
                            onParse = { magnet ->
                                pendingMagnet = magnet
                                viewModel.parseMagnet(magnet)
                            },
                            onDownload = { selectedFiles ->
                                viewModel.downloadSelected(pendingMagnet, selectedFiles)
                                navController.popBackStack()
                            },
                            isParsing = isParsing,
                            parsedFiles = parsedFiles,
                            torrentName = parsedName,
                            totalSize = parsedSize,
                            errorMessage = errorMessage
                        )
                    }

                    composable("detail") {
                        TorrentDetailScreen(
                            torrent = currentTorrent,
                            onBack = { navController.popBackStack() },
                            onPlay = { infoHash, fileIndex ->
                                val filename = currentTorrent?.files?.find { it.index == fileIndex }?.path ?: ""
                                viewModel.playFile(infoHash, fileIndex, filename)
                            },
                            onDownload = { infoHash, fileIndex ->
                                // Trigger download in browser
                            },
                            onPause = { viewModel.pauseTorrent(it) },
                            onResume = { viewModel.resumeTorrent(it) },
                            onRemove = { viewModel.removeTorrent(it); navController.popBackStack() }
                        )
                    }

                    composable("player/{url}/{filename}") { entry ->
                        val url = entry.arguments?.getString("url") ?: ""
                        val filename = entry.arguments?.getString("filename") ?: ""
                        PlayerScreen(
                            streamUrl = url,
                            fileName = filename,
                            onBack = {
                                viewModel.clearStream()
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}
