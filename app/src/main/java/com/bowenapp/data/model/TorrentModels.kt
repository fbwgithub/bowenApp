package com.bowenapp.data.model

import com.google.gson.annotations.SerializedName

data class TorrentInfo(
    @SerializedName("info_hash") val infoHash: String = "",
    val name: String = "",
    @SerializedName("magnet_uri") val magnetUri: String = "",
    @SerializedName("total_size_str") val totalSizeStr: String = "",
    val state: String = "",
    val progress: Double = 0.0,
    @SerializedName("download_rate_str") val downloadRateStr: String = "",
    @SerializedName("upload_rate_str") val uploadRateStr: String = "",
    @SerializedName("num_peers") val numPeers: Int = 0,
    @SerializedName("total_download") val totalDownload: Long = 0,
    @SerializedName("total_upload") val totalUpload: Long = 0,
    @SerializedName("num_seeds") val numSeeds: Int = 0,
    @SerializedName("num_files") val numFiles: Int = 0,
    val files: List<FileInfo> = emptyList()
)

data class FileInfo(
    val index: Int = 0,
    val path: String = "",
    val size: Long = 0,
    @SerializedName("size_str") val sizeStr: String = "",
    val progress: Double = 0.0,
    val offset: Long = 0
)

data class AddTorrentRequest(val magnet: String)

data class AddTorrentResponse(
    val success: Boolean = false,
    val error: String? = null,
    val torrent: TorrentInfo? = null
)

data class ParseMagnetResponse(
    val success: Boolean = false,
    val error: String? = null,
    val name: String = "",
    @SerializedName("total_size_str") val totalSizeStr: String = "",
    val files: List<FileInfo> = emptyList(),
    @SerializedName("info_hash") val infoHash: String = ""
)

data class StatsResponse(
    @SerializedName("total_torrents") val totalTorrents: Int = 0,
    @SerializedName("active_downloads") val activeDownloads: Int = 0,
    @SerializedName("total_download_rate") val totalDownloadRate: String = "",
    @SerializedName("total_upload_rate") val totalUploadRate: String = ""
)
