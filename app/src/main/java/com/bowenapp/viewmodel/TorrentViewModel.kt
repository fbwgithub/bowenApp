package com.bowenapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bowenapp.data.api.TorrentApi
import com.bowenapp.data.api.SelectedDownloadRequest
import com.bowenapp.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class TorrentViewModel : ViewModel() {

    private val api: TorrentApi

    private val _torrents = MutableStateFlow<List<TorrentInfo>>(emptyList())
    val torrents: StateFlow<List<TorrentInfo>> = _torrents.asStateFlow()

    private val _currentTorrent = MutableStateFlow<TorrentInfo?>(null)
    val currentTorrent: StateFlow<TorrentInfo?> = _currentTorrent.asStateFlow()

    private val _isParsing = MutableStateFlow(false)
    val isParsing: StateFlow<Boolean> = _isParsing.asStateFlow()

    private val _parsedFiles = MutableStateFlow<List<FileInfo>>(emptyList())
    val parsedFiles: StateFlow<List<FileInfo>> = _parsedFiles.asStateFlow()

    private val _parsedName = MutableStateFlow("")
    val parsedName: StateFlow<String> = _parsedName.asStateFlow()

    private val _parsedSize = MutableStateFlow("")
    val parsedSize: StateFlow<String> = _parsedSize.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _streamUrl = MutableStateFlow(Pair("", ""))
    val streamUrl: StateFlow<Pair<String, String>> = _streamUrl.asStateFlow()

    init {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        api = Retrofit.Builder()
            .baseUrl("http://127.0.0.1:5000/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TorrentApi::class.java)

        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                try {
                    _torrents.value = api.listTorrents()
                    _currentTorrent.value?.let { current ->
                        _currentTorrent.value = api.getTorrent(current.infoHash)
                    }
                } catch (_: Exception) {}
                delay(2000)
            }
        }
    }

    fun parseMagnet(magnet: String) {
        viewModelScope.launch {
            _isParsing.value = true
            _errorMessage.value = null
            _parsedFiles.value = emptyList()
            try {
                val resp = api.parseMagnet(AddTorrentRequest(magnet))
                if (resp.success) {
                    _parsedFiles.value = resp.files
                    _parsedName.value = resp.name
                    _parsedSize.value = resp.totalSizeStr
                } else {
                    _errorMessage.value = resp.error ?: "解析失败"
                }
            } catch (e: Exception) {
                _errorMessage.value = "请求失败: ${e.message}"
            } finally {
                _isParsing.value = false
            }
        }
    }

    fun downloadSelected(magnet: String, selectedFiles: List<Int>) {
        viewModelScope.launch {
            try {
                val resp = api.downloadSelected(SelectedDownloadRequest(magnet, selectedFiles))
                if (resp.success) {
                    _parsedFiles.value = emptyList()
                    _parsedName.value = ""
                    _parsedSize.value = ""
                } else {
                    _errorMessage.value = resp.error ?: "下载失败"
                }
            } catch (e: Exception) {
                _errorMessage.value = "下载失败: ${e.message}"
            }
        }
    }

    fun loadTorrentDetail(infoHash: String) {
        viewModelScope.launch {
            try { _currentTorrent.value = api.getTorrent(infoHash) }
            catch (e: Exception) { _errorMessage.value = "加载失败: ${e.message}" }
        }
    }

    fun pauseTorrent(infoHash: String) {
        viewModelScope.launch { try { api.pauseTorrent(infoHash) } catch (_: Exception) {} }
    }

    fun resumeTorrent(infoHash: String) {
        viewModelScope.launch { try { api.resumeTorrent(infoHash) } catch (_: Exception) {} }
    }

    fun removeTorrent(infoHash: String) {
        viewModelScope.launch {
            try { api.removeTorrent(infoHash); _currentTorrent.value = null } catch (_: Exception) {}
        }
    }

    fun playFile(infoHash: String, fileIndex: Int, filename: String) {
        _streamUrl.value = Pair("/api/stream/$infoHash/$fileIndex", filename)
    }

    fun clearStream() { _streamUrl.value = Pair("", "") }
    fun clearError() { _errorMessage.value = null }
}
