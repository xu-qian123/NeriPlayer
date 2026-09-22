package moe.ouom.neriplayer.data.settings

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingToolbarPreferencesTest {

    @Test
    fun combinedPlaybackModeCyclesThroughThreeModesWithoutOrder() {
        val allModes = CombinedPlaybackMode.values()
        assertEquals(3, allModes.size)
        assertTrue(allModes.contains(CombinedPlaybackMode.REPEAT_ALL))
        assertTrue(allModes.contains(CombinedPlaybackMode.REPEAT_ONE))
        assertTrue(allModes.contains(CombinedPlaybackMode.SHUFFLE))

        assertEquals(CombinedPlaybackMode.REPEAT_ONE, CombinedPlaybackMode.REPEAT_ALL.next())
        assertEquals(CombinedPlaybackMode.SHUFFLE, CombinedPlaybackMode.REPEAT_ONE.next())
        assertEquals(CombinedPlaybackMode.REPEAT_ALL, CombinedPlaybackMode.SHUFFLE.next())
    }

    @Test
    fun combinedPlaybackModeResolvesCorrectly() {
        // When shuffle is enabled, always resolves to SHUFFLE
        assertEquals(
            CombinedPlaybackMode.SHUFFLE,
            CombinedPlaybackMode.resolve(shuffle = true, repeatMode = Player.REPEAT_MODE_ALL)
        )
        assertEquals(
            CombinedPlaybackMode.SHUFFLE,
            CombinedPlaybackMode.resolve(shuffle = true, repeatMode = Player.REPEAT_MODE_ONE)
        )
        assertEquals(
            CombinedPlaybackMode.SHUFFLE,
            CombinedPlaybackMode.resolve(shuffle = true, repeatMode = Player.REPEAT_MODE_OFF)
        )

        // When shuffle is disabled and repeatMode is ONE
        assertEquals(
            CombinedPlaybackMode.REPEAT_ONE,
            CombinedPlaybackMode.resolve(shuffle = false, repeatMode = Player.REPEAT_MODE_ONE)
        )

        // When shuffle is disabled and repeatMode is ALL or OFF, falls back to REPEAT_ALL
        assertEquals(
            CombinedPlaybackMode.REPEAT_ALL,
            CombinedPlaybackMode.resolve(shuffle = false, repeatMode = Player.REPEAT_MODE_ALL)
        )
        assertEquals(
            CombinedPlaybackMode.REPEAT_ALL,
            CombinedPlaybackMode.resolve(shuffle = false, repeatMode = Player.REPEAT_MODE_OFF)
        )
    }

    @Test
    fun defaultVisibleButtonsProvideFallbacks() {
        val emptyList = emptyList<NowPlayingToolbarButtonItem>()
        val defaultVisible = emptyList.visibleButtons()
        assertEquals(3, defaultVisible.size)
        assertTrue(defaultVisible.contains(NowPlayingToolbarButton.QUEUE))
        assertTrue(defaultVisible.contains(NowPlayingToolbarButton.LYRICS))
        assertTrue(defaultVisible.contains(NowPlayingToolbarButton.MORE))

        val customList = listOf(
            NowPlayingToolbarButtonItem(NowPlayingToolbarButton.PLAY_MODE, visible = true),
            NowPlayingToolbarButtonItem(NowPlayingToolbarButton.TIMER, visible = false)
        )
        val customVisible = customList.visibleButtons()
        assertEquals(1, customVisible.size)
        assertEquals(NowPlayingToolbarButton.PLAY_MODE, customVisible.first())
    }
}
