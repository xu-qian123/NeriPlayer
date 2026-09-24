package moe.ouom.neriplayer.core.roaming

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.api.netease.NeteaseClient
import moe.ouom.neriplayer.core.logging.NPLogger
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.data.auth.netease.NeteaseCookieRepository
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.model.stableKey
import moe.ouom.neriplayer.ui.viewmodel.tab.parseNeteaseHomeSongs
import org.json.JSONObject
import java.time.LocalDate

/**
 * 网易云音乐「私人漫游」管理器
 *
 * 负责私人漫游无限流推荐的拉取、首播初始化以及待播队列的自动预取与追加，
 * 实现沉浸式的无限推荐流体验。
 */
class NeteasePrivateRoamingManager(
    private val application: Application,
    private val neteaseClient: NeteaseClient,
    private val cookieRepo: NeteaseCookieRepository
) {
    companion object {
        private const val TAG = "NERI-Roaming"
        private const val PREFETCH_REMAINING_THRESHOLD = 2
        private const val PREF_NAME = "netease_private_roaming"
        private const val KEY_ROAMING_DATE = "roaming_date"
        private const val KEY_DAILY_PREFETCHED_SONG = "daily_prefetched_song"
        private const val KEY_LAST_PLAYED_SONG = "last_played_song"
    }

    private val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val fetchMutex = Mutex()

    private val _isLoadingFlow = MutableStateFlow(false)
    val isLoadingFlow: StateFlow<Boolean> = _isLoadingFlow.asStateFlow()

    private val _dailyPrefetchedSongFlow = MutableStateFlow<SongItem?>(null)
    val dailyPrefetchedSongFlow: StateFlow<SongItem?> = _dailyPrefetchedSongFlow.asStateFlow()

    private val _lastPlayedSongFlow = MutableStateFlow<SongItem?>(null)
    val lastPlayedSongFlow: StateFlow<SongItem?> = _lastPlayedSongFlow.asStateFlow()

    init {
        val today = getTodayDateString()
        val savedDate = prefs.getString(KEY_ROAMING_DATE, null)
        if (savedDate == today) {
            _dailyPrefetchedSongFlow.value = parseSongFromJson(prefs.getString(KEY_DAILY_PREFETCHED_SONG, null))
            _lastPlayedSongFlow.value = parseSongFromJson(prefs.getString(KEY_LAST_PLAYED_SONG, null))
        } else {
            prefs.edit()
                .putString(KEY_ROAMING_DATE, today)
                .remove(KEY_DAILY_PREFETCHED_SONG)
                .remove(KEY_LAST_PLAYED_SONG)
                .apply()
        }

        scope.launch {
            PlayerManager.currentSongFlow.collect { song ->
                if (PlayerManager.isRoamingMode && song != null) {
                    recordLastPlayedSong(song)
                    checkPrefetchUpcomingSongs()
                }
            }
        }

        if (hasLogin()) {
            prefetchDailyRoamingIfNeeded()
        }
    }

    fun hasLogin(): Boolean = !cookieRepo.getCookiesOnce()["MUSIC_U"].isNullOrBlank()

    /**
     * 检查当前漫游队列剩余曲目，当剩余 <= 2 首时静默预取下一批歌曲
     */
    fun checkPrefetchUpcomingSongs() {
        if (!PlayerManager.isRoamingMode) return
        val currentQueue = PlayerManager.currentQueueFlow.value
        val currentIndex = PlayerManager.currentQueueIndex
        val remaining = currentQueue.size - 1 - currentIndex
        if (remaining <= PREFETCH_REMAINING_THRESHOLD && !_isLoadingFlow.value) {
            scope.launch {
                fetchAndAppendBatch(reason = "prefetch_threshold_reached")
            }
        }
    }

    /**
     * 当队列播放到最后一首（切歌或曲目播放结束）时触发拉取续播
     */
    fun onQueueEndReached() {
        if (!PlayerManager.isRoamingMode) return
        scope.launch {
            fetchAndAppendBatch(reason = "queue_end_reached", advanceTrack = true)
        }
    }

    private suspend fun fetchAndAppendBatch(
        reason: String,
        advanceTrack: Boolean = false
    ) {
        if (!hasLogin()) {
            NPLogger.w(TAG, "Cannot fetch roaming songs: user not logged in ($reason)")
            return
        }
        if (!fetchMutex.tryLock()) {
            NPLogger.d(TAG, "Already fetching roaming songs ($reason), skipping duplicate request")
            return
        }
        try {
            _isLoadingFlow.value = true
            NPLogger.d(TAG, "Fetching next batch of roaming songs ($reason)...")
            val songs = requestFmSongsBatch()
            if (songs.isNotEmpty()) {
                val currentQueue = PlayerManager.currentQueueFlow.value
                val existingKeys = currentQueue.mapTo(HashSet<String>(currentQueue.size)) { it.stableKey() }
                val newSongs = songs.filterNot { existingKeys.contains(it.stableKey()) }
                if (newSongs.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        PlayerManager.appendSongsToQueue(newSongs)
                        if (advanceTrack && PlayerManager.currentQueueIndex < PlayerManager.currentQueueFlow.value.lastIndex) {
                            PlayerManager.next()
                        }
                    }
                    NPLogger.d(TAG, "Appended ${newSongs.size} new roaming songs to queue")
                } else {
                    NPLogger.w(TAG, "Roaming batch returned songs that are already in queue, trying one more")
                    val secondBatch = requestFmSongsBatch().filterNot { existingKeys.contains(it.stableKey()) }
                    if (secondBatch.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            PlayerManager.appendSongsToQueue(secondBatch)
                            if (advanceTrack && PlayerManager.currentQueueIndex < PlayerManager.currentQueueFlow.value.lastIndex) {
                                PlayerManager.next()
                            }
                        }
                    }
                }
            } else {
                NPLogger.w(TAG, "No songs returned from personal FM radio API")
            }
        } catch (e: Exception) {
            NPLogger.e(TAG, "Failed to fetch roaming songs ($reason)", e)
        } finally {
            _isLoadingFlow.value = false
            fetchMutex.unlock()
        }
    }

    private suspend fun requestFmSongsBatch(): List<SongItem> {
        return withContext(Dispatchers.IO) {
            try {
                val raw = neteaseClient.getPersonalFmSongs()
                parseNeteaseHomeSongs(raw)
            } catch (e: Exception) {
                NPLogger.e(TAG, "requestFmSongsBatch failed", e)
                emptyList()
            }
        }
    }

    private fun getTodayDateString(): String = LocalDate.now().toString()

    private fun SongItem.toJsonString(): String {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("artist", artist)
            put("album", album)
            put("albumId", albumId)
            put("durationMs", durationMs)
            if (coverUrl != null) put("coverUrl", coverUrl)
            if (mediaUri != null) put("mediaUri", mediaUri)
        }.toString()
    }

    private fun parseSongFromJson(json: String?): SongItem? {
        if (json.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(json)
            val id = obj.optLong("id", 0L)
            if (id <= 0L) return null
            SongItem(
                id = id,
                name = obj.optString("name", ""),
                artist = obj.optString("artist", ""),
                album = obj.optString("album", ""),
                albumId = obj.optLong("albumId", 0L),
                durationMs = obj.optLong("durationMs", 0L),
                coverUrl = obj.optString("coverUrl").takeIf { it.isNotBlank() },
                mediaUri = obj.optString("mediaUri", "netease:$id").takeIf { it.isNotBlank() } ?: "netease:$id"
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun recordLastPlayedSong(song: SongItem) {
        val today = getTodayDateString()
        _lastPlayedSongFlow.value = song
        prefs.edit()
            .putString(KEY_ROAMING_DATE, today)
            .putString(KEY_LAST_PLAYED_SONG, song.toJsonString())
            .apply()
        NPLogger.d(TAG, "Recorded last played roaming song: ${song.name}")
    }

    /**
     * 每日静默预取当天的第 1 首漫游歌曲
     */
    fun prefetchDailyRoamingIfNeeded() {
        if (!hasLogin()) return
        val today = getTodayDateString()
        val savedDate = prefs.getString(KEY_ROAMING_DATE, null)
        if (savedDate == today && _dailyPrefetchedSongFlow.value != null) {
            return
        }
        scope.launch {
            try {
                val songs = requestFmSongsBatch()
                val first = songs.firstOrNull() ?: return@launch
                withContext(Dispatchers.Main) {
                    val currentToday = getTodayDateString()
                    val currentSavedDate = prefs.getString(KEY_ROAMING_DATE, null)
                    val editor = prefs.edit().putString(KEY_ROAMING_DATE, currentToday)
                    if (currentSavedDate != currentToday) {
                        _lastPlayedSongFlow.value = null
                        editor.remove(KEY_LAST_PLAYED_SONG)
                    }
                    editor.putString(KEY_DAILY_PREFETCHED_SONG, first.toJsonString()).apply()
                    _dailyPrefetchedSongFlow.value = first
                }
                NPLogger.d(TAG, "Prefetched daily roaming song: ${first.name} for $today")
            } catch (e: Exception) {
                NPLogger.e(TAG, "Failed to prefetch daily roaming song", e)
            }
        }
    }

    /**
     * 启动私人漫游模式或切换播放状态
     */
    fun startRoaming(
        onFailure: (String) -> Unit = {}
    ) {
        if (!hasLogin()) {
            onFailure(application.getString(R.string.home_roaming_need_login))
            return
        }

        // 若当前已经是漫游模式且已有歌曲，点击则执行播放/暂停切换
        if (PlayerManager.isRoamingMode && PlayerManager.hasItems()) {
            PlayerManager.togglePlayPause()
            return
        }

        scope.launch {
            fetchMutex.withLock {
                _isLoadingFlow.value = true
                try {
                    NPLogger.d(TAG, "Starting roaming session...")
                    val today = getTodayDateString()
                    val savedDate = prefs.getString(KEY_ROAMING_DATE, null)
                    if (savedDate != today) {
                        _dailyPrefetchedSongFlow.value = null
                        _lastPlayedSongFlow.value = null
                        prefs.edit()
                            .putString(KEY_ROAMING_DATE, today)
                            .remove(KEY_DAILY_PREFETCHED_SONG)
                            .remove(KEY_LAST_PLAYED_SONG)
                            .apply()
                    }

                    // 优先断点续播当天最后播放的歌曲；其次当天首次预取的歌曲
                    val resumeSong = _lastPlayedSongFlow.value ?: _dailyPrefetchedSongFlow.value

                    if (resumeSong != null) {
                        withContext(Dispatchers.Main) {
                            PlayerManager.playRoaming(listOf(resumeSong), startIndex = 0)
                        }
                        NPLogger.d(TAG, "Roaming resumed with song: ${resumeSong.name}")

                        // 异步拉取后续漫游批次追加到队列末尾
                        val batch1 = try {
                            requestFmSongsBatch().filterNot { it.stableKey() == resumeSong.stableKey() }
                        } catch (_: Exception) {
                            emptyList()
                        }
                        val batch2 = try {
                            requestFmSongsBatch().filterNot { it.stableKey() == resumeSong.stableKey() }
                        } catch (_: Exception) {
                            emptyList()
                        }
                        val appendList = (batch1 + batch2).distinctBy { it.stableKey() }
                        if (appendList.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                if (PlayerManager.isRoamingMode) {
                                    PlayerManager.appendSongsToQueue(appendList)
                                }
                            }
                            NPLogger.d(TAG, "Appended ${appendList.size} upcoming roaming songs after resume song")
                        }
                    } else {
                        val batch1 = requestFmSongsBatch()
                        if (batch1.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                onFailure(application.getString(R.string.home_roaming_fetch_failed))
                            }
                            return@withLock
                        }

                        val batch2 = try {
                            requestFmSongsBatch()
                        } catch (_: Exception) {
                            emptyList()
                        }
                        val initialList = (batch1 + batch2).distinctBy { it.stableKey() }

                        val firstSong = initialList.first()
                        withContext(Dispatchers.Main) {
                            _dailyPrefetchedSongFlow.value = firstSong
                            prefs.edit()
                                .putString(KEY_ROAMING_DATE, today)
                                .putString(KEY_DAILY_PREFETCHED_SONG, firstSong.toJsonString())
                                .apply()
                            PlayerManager.playRoaming(initialList, startIndex = 0)
                        }
                        NPLogger.d(TAG, "Roaming started with fresh batch of ${initialList.size} tracks")
                    }
                } catch (e: Exception) {
                    NPLogger.e(TAG, "Failed to start roaming", e)
                    withContext(Dispatchers.Main) {
                        onFailure(application.getString(R.string.home_roaming_fetch_failed))
                    }
                } finally {
                    _isLoadingFlow.value = false
                }
            }
        }
    }
}
