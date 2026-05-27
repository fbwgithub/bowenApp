package com.bowenapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bowenapp.data.ServerConfig
import com.bowenapp.data.api.TorrentApi
import com.bowenapp.data.api.SelectedDownloadRequest
import com.bowenapp.data.api.PlayMagnetRequest
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

    private var _api: TorrentApi? = null
    private val api: TorrentApi get() {
        if (_api == null) _api = createApi()
        return _api!!
    }

    private fun createApi(): TorrentApi {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(ServerConfig.getBaseUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TorrentApi::class.java)
    }

    fun updateBaseUrl(newUrl: String) {
        ServerConfig.setBaseUrl(newUrl)
        _api = null // will recreate on next access
        startPolling()
    }

    private var pollingJob: kotlinx.coroutines.Job? = null

    fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                try {
                    _torrents.value = api.listTorrents()
                    _currentTorrent.value?.let { current ->
                        _currentTorrent.value = api.getTorrent(current.infoHash)
                    }
                } catch (_: Exception) {}
                delay(3000)
            }
        }
    }

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

    private val _totalParsedSize = MutableStateFlow("")
    val totalParsedSize: StateFlow<String> = _totalParsedSize.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _streamUrl = MutableStateFlow(Pair("", ""))
    val streamUrl: StateFlow<Pair<String, String>> = _streamUrl.asStateFlow()

    private val _stats = MutableStateFlow(StatsResponse())
    val stats: StateFlow<StatsResponse> = _stats.asStateFlow()

    init {
        startPolling()
        // Also poll stats
        viewModelScope.launch {
            while (true) {
                try { _stats.value = api.getStats() } catch (_: Exception) {}
                delay(5000)
            }
        }
    }

    fun clearStream() { _streamUrl.value = Pair("", "") }

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
                    _totalParsedSize.value = resp.totalSizeStr
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
                    _totalParsedSize.value = ""
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
        viewModelScope.launch {
            try {
                val magnet = _currentTorrent.value?.magnetUri ?: return@launch
                val resp = api.playMagnet(PlayMagnetRequest(magnet, fileIndex))
                if (resp.success) {
                    _streamUrl.value = Pair(resp.streamUrl, filename)
                } else {
                    _errorMessage.value = resp.error ?: "播放失败"
                }
            } catch (e: Exception) {
                _errorMessage.value = "播放失败: ${e.message}"
            }
        }
    }
}
