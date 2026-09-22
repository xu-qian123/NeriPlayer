package moe.ouom.neriplayer.data.lyrics

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2026 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * File: moe.ouom.neriplayer.data.lyrics/CustomSongLyricsRepository
 * Created: 2026/9/22
 */

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.core.api.search.MusicPlatform
import moe.ouom.neriplayer.core.logging.NPLogger
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.model.stableKey
import moe.ouom.neriplayer.util.io.writeTextAtomically
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "CustomSongLyricsRepo"

data class CustomSongLyricEntry(
    val stableKey: String,
    val lyric: String? = null,
    val translatedLyric: String? = null,
    val customName: String? = null,
    val customArtist: String? = null,
    val userLyricOffsetMs: Long? = null,
    val matchedLyricSource: MusicPlatform? = null,
    val matchedSongId: String? = null,
    val modifiedAt: Long = System.currentTimeMillis()
) {
    fun isEmpty(): Boolean {
        return lyric == null &&
            translatedLyric == null &&
            customName.isNullOrBlank() &&
            customArtist.isNullOrBlank() &&
            userLyricOffsetMs == null &&
            matchedLyricSource == null &&
            matchedSongId.isNullOrBlank()
    }
}

class CustomSongLyricsRepository internal constructor(
    private val storageFile: File,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val gson = Gson()
    private val cache = ConcurrentHashMap<String, CustomSongLyricEntry>()
    private val persistenceMutex = Mutex()

    init {
        loadFromDisk()
    }

    internal fun loadFromDisk() {
        try {
            if (storageFile.exists()) {
                val json = storageFile.readText(Charsets.UTF_8)
                val type = object : TypeToken<Map<String, CustomSongLyricEntry>>() {}.type
                val map: Map<String, CustomSongLyricEntry>? = gson.fromJson(json, type)
                if (!map.isNullOrEmpty()) {
                    cache.clear()
                    cache.putAll(map)
                    NPLogger.d(TAG, "Loaded ${cache.size} custom entries from ${storageFile.name}")
                }
            }
        } catch (e: Exception) {
            NPLogger.e(TAG, "Failed to load custom lyrics from disk", e)
        }
    }

    fun getCustomLyrics(stableKey: String): CustomSongLyricEntry? {
        if (stableKey.isBlank()) return null
        return cache[stableKey]
    }

    fun hasCustomLyrics(stableKey: String): Boolean {
        if (stableKey.isBlank()) return false
        val entry = cache[stableKey] ?: return false
        return !entry.isEmpty()
    }

    suspend fun saveCustomLyrics(
        song: SongItem,
        lyric: String?,
        translatedLyric: String?,
        matchedSource: MusicPlatform? = null,
        matchedSongId: String? = null
    ) {
        val key = song.stableKey()
        if (key.isBlank()) return
        val current = cache[key]
        val updatedEntry = (current ?: CustomSongLyricEntry(stableKey = key)).copy(
            lyric = lyric,
            translatedLyric = translatedLyric,
            matchedLyricSource = matchedSource ?: current?.matchedLyricSource ?: song.matchedLyricSource,
            matchedSongId = matchedSongId ?: current?.matchedSongId ?: song.matchedSongId,
            modifiedAt = System.currentTimeMillis()
        )
        if (updatedEntry.isEmpty()) {
            cache.remove(key)
        } else {
            cache[key] = updatedEntry
        }
        NPLogger.d(TAG, "Saved custom lyrics for $key (lyricLen=${lyric?.length ?: 0}, transLen=${translatedLyric?.length ?: 0})")
        persistAsync()
    }

    suspend fun saveCustomMetadata(
        song: SongItem,
        customName: String?,
        customArtist: String?,
        restoreName: Boolean = false,
        restoreArtist: Boolean = false
    ) {
        val key = song.stableKey()
        if (key.isBlank()) return
        val current = cache[key]
        val finalName = if (restoreName) null else (customName?.trim()?.takeIf { it.isNotBlank() } ?: current?.customName)
        val finalArtist = if (restoreArtist) null else (customArtist?.trim()?.takeIf { it.isNotBlank() } ?: current?.customArtist)

        val updatedEntry = (current ?: CustomSongLyricEntry(stableKey = key)).copy(
            customName = finalName,
            customArtist = finalArtist,
            modifiedAt = System.currentTimeMillis()
        )
        if (updatedEntry.isEmpty()) {
            cache.remove(key)
            NPLogger.d(TAG, "Removed custom entry because all fields are empty: $key")
        } else {
            cache[key] = updatedEntry
            NPLogger.d(TAG, "Saved custom metadata for $key: name='$finalName', artist='$finalArtist'")
        }
        persistAsync()
    }

    suspend fun saveUserLyricOffset(
        song: SongItem,
        offsetMs: Long
    ) {
        val key = song.stableKey()
        if (key.isBlank()) return
        val current = cache[key]
        val updatedEntry = (current ?: CustomSongLyricEntry(stableKey = key)).copy(
            userLyricOffsetMs = if (offsetMs == 0L) null else offsetMs,
            modifiedAt = System.currentTimeMillis()
        )
        if (updatedEntry.isEmpty()) {
            cache.remove(key)
        } else {
            cache[key] = updatedEntry
        }
        NPLogger.d(TAG, "Saved custom lyric offset for $key: offsetMs=$offsetMs")
        persistAsync()
    }

    suspend fun clearCustomLyrics(song: SongItem) {
        clearCustomLyrics(song.stableKey())
    }

    suspend fun clearCustomLyrics(stableKey: String) {
        if (stableKey.isBlank()) return
        val current = cache[stableKey] ?: return
        val updatedEntry = current.copy(
            lyric = null,
            translatedLyric = null,
            matchedLyricSource = null,
            matchedSongId = null,
            modifiedAt = System.currentTimeMillis()
        )
        if (updatedEntry.isEmpty()) {
            cache.remove(stableKey)
            NPLogger.d(TAG, "Cleared custom lyrics and removed entry for $stableKey")
        } else {
            cache[stableKey] = updatedEntry
            NPLogger.d(TAG, "Cleared custom lyrics but retained other metadata for $stableKey")
        }
        persistAsync()
    }

    suspend fun clearCustomMetadata(song: SongItem) {
        clearCustomMetadata(song.stableKey())
    }

    suspend fun clearCustomMetadata(stableKey: String) {
        if (stableKey.isBlank()) return
        val current = cache[stableKey] ?: return
        val updatedEntry = current.copy(
            customName = null,
            customArtist = null,
            modifiedAt = System.currentTimeMillis()
        )
        if (updatedEntry.isEmpty()) {
            cache.remove(stableKey)
            NPLogger.d(TAG, "Cleared custom metadata and removed entry for $stableKey")
        } else {
            cache[stableKey] = updatedEntry
            NPLogger.d(TAG, "Cleared custom metadata but retained other fields for $stableKey")
        }
        persistAsync()
    }

    fun hydrateSong(song: SongItem?): SongItem? {
        if (song == null) return null
        val key = song.stableKey()
        if (key.isBlank()) return song
        val entry = cache[key] ?: return song

        var updated = song
        if (!entry.customName.isNullOrBlank()) {
            updated = updated.copy(customName = entry.customName)
        }
        if (!entry.customArtist.isNullOrBlank()) {
            updated = updated.copy(customArtist = entry.customArtist)
        }
        if (entry.lyric != null && updated.matchedLyric == null) {
            updated = updated.copy(matchedLyric = entry.lyric)
        }
        if (entry.translatedLyric != null && updated.matchedTranslatedLyric == null) {
            updated = updated.copy(matchedTranslatedLyric = entry.translatedLyric)
        }
        if (entry.userLyricOffsetMs != null && updated.userLyricOffsetMs == 0L) {
            updated = updated.copy(userLyricOffsetMs = entry.userLyricOffsetMs)
        }
        if (entry.matchedLyricSource != null && updated.matchedLyricSource == null) {
            updated = updated.copy(
                matchedLyricSource = entry.matchedLyricSource,
                matchedSongId = entry.matchedSongId ?: updated.matchedSongId
            )
        }
        return updated
    }

    internal suspend fun persistSync() = withContext(Dispatchers.IO) {
        persistenceMutex.withLock {
            writeSnapshotToDisk()
        }
    }

    private fun persistAsync() {
        scope.launch(Dispatchers.IO) {
            persistenceMutex.withLock {
                writeSnapshotToDisk()
            }
        }
    }

    private fun writeSnapshotToDisk() {
        try {
            val snapshot = HashMap(cache)
            val json = gson.toJson(snapshot)
            storageFile.writeTextAtomically(json)
            NPLogger.d(TAG, "Successfully persisted ${snapshot.size} custom entries")
        } catch (e: Exception) {
            NPLogger.e(TAG, "Failed to persist custom entries to disk", e)
        }
    }

    companion object {
        @Volatile
        private var instance: CustomSongLyricsRepository? = null

        fun getInstance(context: Context): CustomSongLyricsRepository {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val appContext = context.applicationContext ?: context
                    val dir = File(appContext.filesDir, "custom_lyrics")
                    if (!dir.exists()) {
                        dir.mkdirs()
                    }
                    val file = File(dir, "custom_song_lyrics.json")
                    CustomSongLyricsRepository(file).also { instance = it }
                }
            }
        }
    }
}
