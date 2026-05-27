package com.bowenapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

@Composable
fun TorrentProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "progress"
    )
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = animatedProgress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun StatusBadge(state: String) {
    val color = when (state) {
        "downloading" -> MaterialTheme.colorScheme.primary
        "downloading_metadata" -> MaterialTheme.colorScheme.secondary
        "finished", "seeding" -> Color(0xFF66BB6A)
        "paused" -> Color(0xFFFFA726)
        "checking" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = when (state) {
        "downloading" -> "下载中"
        "downloading_metadata" -> "获取元数据"
        "finished" -> "已完成"
        "seeding" -> "做种中"
        "paused" -> "已暂停"
        "checking" -> "检查中"
        "error" -> "错误"
        else -> state
    }
    Surface(
        shape = RoundedCornerShape(20),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}


@Composable
fun FileIcon(ext: String) {
    val icon = when (ext.lowercase()) {
        in listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v") -> "🎬"
        in listOf("mp3", "aac", "wav", "flac", "ogg", "m4a") -> "🎵"
        in listOf("jpg", "jpeg", "png", "gif", "webp") -> "🖼️"
        in listOf("zip", "rar", "tar", "gz", "7z") -> "📦"
        in listOf("pdf", "txt", "doc", "docx") -> "📄"
        else -> "📁"
    }
    Text(text = icon, fontSize = 24.sp)
}

@Composable
fun FileProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(80.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = (progress / 100f).coerceIn(0f, 1f))
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}
