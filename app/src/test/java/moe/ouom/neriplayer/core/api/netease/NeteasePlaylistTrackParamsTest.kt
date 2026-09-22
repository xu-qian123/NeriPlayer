package moe.ouom.neriplayer.core.api.netease

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NeteasePlaylistTrackParamsTest {
    @Test
    fun `playlist add params include batch fields used by netease manipulate tracks`() {
        val params = buildNeteasePlaylistAddTracksParams(
            playlistId = 88L,
            songIds = listOf(1L, 2L, 2L, 0L, -1L, 3L)
        )

        assertEquals("add", params["op"])
        assertEquals("88", params["pid"])
        assertEquals("88", params["id"])
        assertEquals("1,2,3", params["tracks"])
        assertEquals("[1,2,3]", params["trackIds"])
        assertEquals("true", params["imme"])
    }

    @Test
    fun `playlist add params reject empty positive song ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildNeteasePlaylistAddTracksParams(
                playlistId = 88L,
                songIds = listOf(0L, -1L)
            )
        }
    }

    @Test
    fun `playlist delete params include batch fields used by netease manipulate tracks`() {
        val params = buildNeteasePlaylistDeleteTracksParams(
            playlistId = 99L,
            songIds = listOf(4L, 5L, 5L, 0L, -2L, 6L)
        )

        assertEquals("del", params["op"])
        assertEquals("99", params["pid"])
        assertEquals("99", params["id"])
        assertEquals("4,5,6", params["tracks"])
        assertEquals("[4,5,6]", params["trackIds"])
    }

    @Test
    fun `playlist delete params reject empty positive song ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildNeteasePlaylistDeleteTracksParams(
                playlistId = 99L,
                songIds = listOf(0L, -1L)
            )
        }
    }
}
