package com.bowenapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.bowenapp.data.ServerConfig

@Composable
fun PlayerScreen(
    streamUrl: String,
    fileName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val baseUrl = ServerConfig.getBaseUrl().removeSuffix("/")

    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(context) {
        val fullUrl = if (streamUrl.startsWith("http")) streamUrl else "$baseUrl$streamUrl"
        val exoPlayer = ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(fullUrl)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
        player = exoPlayer
        onDispose { exoPlayer.release() }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A2E))
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            TextButton(onClick = {
                player?.release()
                onBack()
            }) { Text("← 返回", color = Color.White) }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            player?.let {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = it
                            useController = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }
                )
            }
        }
    }
}
