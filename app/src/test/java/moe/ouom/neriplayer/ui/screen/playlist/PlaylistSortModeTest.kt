package moe.ouom.neriplayer.ui.screen.playlist

import moe.ouom.neriplayer.data.model.SongItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistSortModeTest {

    private fun dummySong(id: Long, name: String, artist: String, album: String, addedAt: Long = 0L, albumId: Long = 0L) =
        SongItem(
            id = id,
            name = name,
            artist = artist,
            album = album,
            albumId = albumId,
            durationMs = 180000L,
            coverUrl = null,
            addedAt = addedAt
        )

    @Test
    fun defaultModePreservesOriginalOrder() {
        val songs = listOf(
            dummySong(1, "Zebra", "Artist B", "Album C"),
            dummySong(2, "Apple", "Artist A", "Album A")
        )
        val sorted = songs.applyPlaylistSort(PlaylistSortMode.DEFAULT)
        assertEquals(songs, sorted)
    }

    @Test
    fun sortByNameOrdersAlphabetically() {
        val songs = listOf(
            dummySong(1, "Zebra", "Artist B", "Album C"),
            dummySong(2, "Apple", "Artist A", "Album A"),
            dummySong(3, "Banana", "Artist C", "Album B")
        )
        val sorted = songs.applyPlaylistSort(PlaylistSortMode.BY_NAME)
        assertEquals(listOf("Apple", "Banana", "Zebra"), sorted.map { it.name })
    }

    @Test
    fun sortByArtistOrdersAlphabetically() {
        val songs = listOf(
            dummySong(1, "Song 1", "Charlie", "Album C"),
            dummySong(2, "Song 2", "Alice", "Album A"),
            dummySong(3, "Song 3", "Bob", "Album B")
        )
        val sorted = songs.applyPlaylistSort(PlaylistSortMode.BY_ARTIST)
        assertEquals(listOf("Alice", "Bob", "Charlie"), sorted.map { it.artist })
    }

    @Test
    fun sortByAddedAtOldestAndNewest() {
        val songs = listOf(
            dummySong(1, "Old", "A", "A", addedAt = 100L),
            dummySong(2, "New", "A", "A", addedAt = 300L),
            dummySong(3, "Mid", "A", "A", addedAt = 200L)
        )
        val oldest = songs.applyPlaylistSort(PlaylistSortMode.OLDEST_FIRST)
        assertEquals(listOf(1L, 3L, 2L), oldest.map { it.id })

        val newest = songs.applyPlaylistSort(PlaylistSortMode.NEWEST_FIRST)
        assertEquals(listOf(2L, 3L, 1L), newest.map { it.id })
    }
}
