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
 * File: moe.ouom.neriplayer.data.lyrics/CustomSongLyricsRepositoryTest
 * Created: 2026/9/22
 */

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.model.displayName
import moe.ouom.neriplayer.data.model.displayArtist
import moe.ouom.neriplayer.data.model.stableKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CustomSongLyricsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testStorageFile: File
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val testSong = SongItem(
        id = 123456L,
        name = "测试在线歌曲",
        artist = "测试歌手",
        album = "netease",
        albumId = 0L,
        durationMs = 180000L,
        coverUrl = null
    )

    @Before
    fun setup() {
        testStorageFile = tempFolder.newFile("test_custom_lyrics.json")
    }

    @Test
    fun `save and get custom lyrics successfully`() = runTest(testDispatcher) {
        val repo = CustomSongLyricsRepository(
            storageFile = testStorageFile,
            scope = testScope
        )

        assertNull(repo.getCustomLyrics(testSong.stableKey()))
        assertFalse(repo.hasCustomLyrics(testSong.stableKey()))

        val customLrc = "[00:01.00]我的自定义歌词"
        val customTrans = "[00:01.00]My custom translation"
        repo.saveCustomLyrics(testSong, customLrc, customTrans)
        repo.persistSync()

        val saved = repo.getCustomLyrics(testSong.stableKey())
        assertNotNull(saved)
        assertEquals(customLrc, saved?.lyric)
        assertEquals(customTrans, saved?.translatedLyric)
        assertTrue(repo.hasCustomLyrics(testSong.stableKey()))
    }

    @Test
    fun `clear custom lyrics successfully`() = runTest(testDispatcher) {
        val repo = CustomSongLyricsRepository(
            storageFile = testStorageFile,
            scope = testScope
        )

        repo.saveCustomLyrics(testSong, "[00:01.00]Lyric", null)
        repo.persistSync()
        assertTrue(repo.hasCustomLyrics(testSong.stableKey()))

        repo.clearCustomLyrics(testSong)
        repo.persistSync()
        assertNull(repo.getCustomLyrics(testSong.stableKey()))
        assertFalse(repo.hasCustomLyrics(testSong.stableKey()))
    }

    @Test
    fun `save and get custom title and artist successfully`() = runTest(testDispatcher) {
        val repo = CustomSongLyricsRepository(
            storageFile = testStorageFile,
            scope = testScope
        )

        repo.saveCustomMetadata(
            song = testSong,
            customName = "自定义好听歌名",
            customArtist = "真实艺术家"
        )
        repo.persistSync()

        val entry = repo.getCustomLyrics(testSong.stableKey())
        assertNotNull(entry)
        assertEquals("自定义好听歌名", entry?.customName)
        assertEquals("真实艺术家", entry?.customArtist)

        // 水合测试
        val hydrated = repo.hydrateSong(testSong)
        assertNotNull(hydrated)
        assertEquals("自定义好听歌名", hydrated?.displayName())
        assertEquals("真实艺术家", hydrated?.displayArtist())
    }

    @Test
    fun `save lyric offset and hydrate song successfully`() = runTest(testDispatcher) {
        val repo = CustomSongLyricsRepository(
            storageFile = testStorageFile,
            scope = testScope
        )

        repo.saveUserLyricOffset(testSong, 750L)
        repo.persistSync()

        val entry = repo.getCustomLyrics(testSong.stableKey())
        assertNotNull(entry)
        assertEquals(750L, entry?.userLyricOffsetMs)

        val hydrated = repo.hydrateSong(testSong)
        assertEquals(750L, hydrated?.userLyricOffsetMs)
    }

    @Test
    fun `retaining remaining fields when one aspect is cleared`() = runTest(testDispatcher) {
        val repo = CustomSongLyricsRepository(
            storageFile = testStorageFile,
            scope = testScope
        )

        // 同时保存歌词和自定义元数据
        repo.saveCustomLyrics(testSong, "[00:01.00]Lyric", null)
        repo.saveCustomMetadata(testSong, "新标题", "新歌手")
        repo.persistSync()

        // 只清除歌词
        repo.clearCustomLyrics(testSong)
        repo.persistSync()

        val entry = repo.getCustomLyrics(testSong.stableKey())
        assertNotNull(entry)
        assertNull(entry?.lyric)
        assertEquals("新标题", entry?.customName)
        assertEquals("新歌手", entry?.customArtist)

        // 再清除元数据
        repo.clearCustomMetadata(testSong)
        repo.persistSync()

        assertNull(repo.getCustomLyrics(testSong.stableKey()))
        assertFalse(repo.hasCustomLyrics(testSong.stableKey()))
    }

    @Test
    fun `reload from disk recovers previously saved entries including metadata`() = runTest(testDispatcher) {
        val repo1 = CustomSongLyricsRepository(
            storageFile = testStorageFile,
            scope = testScope
        )
        val customLrc = "[00:05.00]持续存在的内容"
        repo1.saveCustomLyrics(testSong, customLrc, null)
        repo1.saveCustomMetadata(testSong, "持久化标题", "持久化艺术家")
        repo1.saveUserLyricOffset(testSong, -300L)
        repo1.persistSync()

        // 模拟 App 重新启动构造新实例从磁盘重新加载
        val repo2 = CustomSongLyricsRepository(
            storageFile = testStorageFile,
            scope = testScope
        )
        val entry = repo2.getCustomLyrics(testSong.stableKey())
        assertNotNull(entry)
        assertEquals(customLrc, entry?.lyric)
        assertEquals("持久化标题", entry?.customName)
        assertEquals("持久化艺术家", entry?.customArtist)
        assertEquals(-300L, entry?.userLyricOffsetMs)

        val hydrated = repo2.hydrateSong(testSong)
        assertEquals("持久化标题", hydrated?.displayName())
        assertEquals("持久化艺术家", hydrated?.displayArtist())
        assertEquals(customLrc, hydrated?.matchedLyric)
        assertEquals(-300L, hydrated?.userLyricOffsetMs)
    }
}
