package moe.ouom.neriplayer.core.roaming

import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.model.stableKey
import moe.ouom.neriplayer.ui.viewmodel.tab.NeteaseHomeSongSource
import moe.ouom.neriplayer.ui.viewmodel.tab.availableNeteaseHomeSongSources
import moe.ouom.neriplayer.ui.viewmodel.tab.parseNeteaseHomeSongs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteasePrivateRoamingTest {

    @Test
    fun privateFmSongSource_doesNotRequireLoginToRenderCard() {
        val sources = listOf(
            NeteaseHomeSongSource.PERSONAL_RADAR,
            NeteaseHomeSongSource.DAILY_RECOMMEND,
            NeteaseHomeSongSource.PRIVATE_FM
        )
        // Even when hasLogin = false, PRIVATE_FM card is available so it can guide the user
        val availableWhenLoggedOut = availableNeteaseHomeSongSources(sources, hasLogin = false)
        assertTrue(availableWhenLoggedOut.contains(NeteaseHomeSongSource.PRIVATE_FM))
        assertFalse(availableWhenLoggedOut.contains(NeteaseHomeSongSource.DAILY_RECOMMEND))

        // When logged in, both are available
        val availableWhenLoggedIn = availableNeteaseHomeSongSources(sources, hasLogin = true)
        assertTrue(availableWhenLoggedIn.contains(NeteaseHomeSongSource.PRIVATE_FM))
        assertTrue(availableWhenLoggedIn.contains(NeteaseHomeSongSource.DAILY_RECOMMEND))
    }

    @Test
    fun parsePersonalFmJson_extractsTracksCorrectly() {
        val sampleFmJson = """
            {
                "code": 200,
                "data": [
                    {
                        "id": 123456,
                        "name": "Roaming Track 1",
                        "artists": [{"id": 1, "name": "Artist 1"}],
                        "album": {"id": 10, "name": "Album 1", "picUrl": "https://p1.music.126.net/abc.jpg"},
                        "duration": 210000
                    },
                    {
                        "id": 234567,
                        "name": "Roaming Track 2",
                        "artists": [{"id": 2, "name": "Artist 2"}],
                        "album": {"id": 20, "name": "Album 2", "picUrl": "https://p1.music.126.net/def.jpg"},
                        "duration": 180000
                    }
                ]
            }
        """.trimIndent()

        val parsed = parseNeteaseHomeSongs(sampleFmJson)
        assertEquals(2, parsed.size)
        assertEquals(123456L, parsed[0].id)
        assertEquals("Roaming Track 1", parsed[0].name)
        assertEquals("Artist 1", parsed[0].artist)
        assertEquals(234567L, parsed[1].id)
        assertEquals("Roaming Track 2", parsed[1].name)
        assertEquals("Artist 2", parsed[1].artist)
    }

    @Test
    fun roamingPrefetchThreshold_triggersWhenTwoOrFewerTracksRemain() {
        fun shouldPrefetch(queueSize: Int, currentIndex: Int): Boolean {
            val remaining = queueSize - 1 - currentIndex
            return remaining <= 2
        }

        // 6 tracks in queue, at index 0 -> remaining = 5 -> no prefetch
        assertFalse(shouldPrefetch(queueSize = 6, currentIndex = 0))

        // 6 tracks in queue, at index 3 -> remaining = 2 -> prefetch!
        assertTrue(shouldPrefetch(queueSize = 6, currentIndex = 3))

        // 6 tracks in queue, at index 4 -> remaining = 1 -> prefetch!
        assertTrue(shouldPrefetch(queueSize = 6, currentIndex = 4))

        // 6 tracks in queue, at index 5 (last track) -> remaining = 0 -> prefetch!
        assertTrue(shouldPrefetch(queueSize = 6, currentIndex = 5))
    }

    @Test
    fun deduplicateRoamingSongs_preservesOrderWithoutDuplicateKeys() {
        val existingQueue = listOf(
            SongItem(id = 101L, name = "Song 1", artist = "Artist A", album = "Album A", albumId = 1L, durationMs = 180000L, coverUrl = null),
            SongItem(id = 102L, name = "Song 2", artist = "Artist B", album = "Album B", albumId = 2L, durationMs = 180000L, coverUrl = null)
        )
        val newBatch = listOf(
            SongItem(id = 102L, name = "Song 2 (dup)", artist = "Artist B", album = "Album B", albumId = 2L, durationMs = 180000L, coverUrl = null),
            SongItem(id = 103L, name = "Song 3", artist = "Artist C", album = "Album C", albumId = 3L, durationMs = 180000L, coverUrl = null),
            SongItem(id = 104L, name = "Song 4", artist = "Artist D", album = "Album D", albumId = 4L, durationMs = 180000L, coverUrl = null)
        )

        val existingKeys = existingQueue.mapTo(HashSet<String>()) { it.stableKey() }
        val toAppend = newBatch.filterNot { existingKeys.contains(it.stableKey()) }

        assertEquals(2, toAppend.size)
        assertEquals(103L, toAppend[0].id)
        assertEquals(104L, toAppend[1].id)
    }
}
