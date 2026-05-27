package com.bowenapp.data.api

import com.bowenapp.data.model.*
import retrofit2.http.*

interface TorrentApi {

    @GET("api/torrents")
    suspend fun listTorrents(): List<TorrentInfo>

    @GET("api/torrents/{infoHash}")
    suspend fun getTorrent(@Path("infoHash") infoHash: String): TorrentInfo

    @POST("api/torrents")
    suspend fun addTorrent(@Body request: AddTorrentRequest): AddTorrentResponse

    @HTTP(method = "DELETE", path = "api/torrents/{infoHash}")
    suspend fun removeTorrent(
        @Path("infoHash") infoHash: String,
        @Query("delete_files") deleteFiles: Boolean = false
    ): Map<String, Any>

    @POST("api/torrents/{infoHash}/pause")
    suspend fun pauseTorrent(@Path("infoHash") infoHash: String): Map<String, Any>

    @POST("api/torrents/{infoHash}/resume")
    suspend fun resumeTorrent(@Path("infoHash") infoHash: String): Map<String, Any>

    @POST("api/magnet/parse")
    suspend fun parseMagnet(@Body request: AddTorrentRequest): ParseMagnetResponse

    @POST("api/magnet/download")
    suspend fun downloadSelected(@Body request: SelectedDownloadRequest): AddTorrentResponse

    @GET("api/stats")
    suspend fun getStats(): StatsResponse
}

data class SelectedDownloadRequest(
    val magnet: String,
    val selectedFiles: List<Int>
)
