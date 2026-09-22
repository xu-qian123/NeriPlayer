package moe.ouom.neriplayer.data.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.media3.common.Player
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.player.PlayerManager

enum class NowPlayingToolbarButton(
    val id: String,
    val titleRes: Int,
    val defaultVisible: Boolean
) {
    QUEUE("queue", R.string.playlist_queue, true),
    PLAY_MODE("play_mode", R.string.player_playback_mode, true),
    VOLUME("volume", R.string.cd_audio_device, true),
    LYRICS("lyrics", R.string.lyrics_title, true),
    MORE("more", R.string.nowplaying_more_options, true),
    ADD_TO_PLAYLIST("add_to_playlist", R.string.playlist_add_to, false),
    TIMER("timer", R.string.sleep_timer_short, false);

    companion object {
        private val BY_ID = entries.associateBy { it.id }

        fun fromId(id: String): NowPlayingToolbarButton? = BY_ID[id]
    }
}

data class NowPlayingToolbarButtonItem(
    val button: NowPlayingToolbarButton,
    val visible: Boolean
)

val DEFAULT_NOWPLAYING_TOOLBAR_ITEMS: List<NowPlayingToolbarButtonItem> = listOf(
    NowPlayingToolbarButtonItem(NowPlayingToolbarButton.QUEUE, true),
    NowPlayingToolbarButtonItem(NowPlayingToolbarButton.PLAY_MODE, true),
    NowPlayingToolbarButtonItem(NowPlayingToolbarButton.VOLUME, true),
    NowPlayingToolbarButtonItem(NowPlayingToolbarButton.LYRICS, true),
    NowPlayingToolbarButtonItem(NowPlayingToolbarButton.MORE, true),
    NowPlayingToolbarButtonItem(NowPlayingToolbarButton.ADD_TO_PLAYLIST, false),
    NowPlayingToolbarButtonItem(NowPlayingToolbarButton.TIMER, false)
)

const val DEFAULT_NOWPLAYING_TOOLBAR_BUTTONS_CONFIG =
    "queue:1,play_mode:1,volume:1,lyrics:1,more:1,add_to_playlist:0,timer:0"

fun encodeToolbarButtons(items: List<NowPlayingToolbarButtonItem>): String {
    return items.joinToString(",") { "${it.button.id}:${if (it.visible) "1" else "0"}" }
}

fun decodeToolbarButtons(raw: String?): List<NowPlayingToolbarButtonItem> {
    if (raw.isNullOrBlank()) {
        return DEFAULT_NOWPLAYING_TOOLBAR_ITEMS
    }

    val parsedItems = mutableListOf<NowPlayingToolbarButtonItem>()
    val seenButtons = mutableSetOf<NowPlayingToolbarButton>()

    val segments = raw.split(",")
    for (segment in segments) {
        val parts = segment.trim().split(":")
        if (parts.isEmpty()) continue
        val buttonId = parts[0]
        val button = NowPlayingToolbarButton.fromId(buttonId) ?: continue
        if (button in seenButtons) continue

        val visible = if (parts.size > 1) {
            parts[1] == "1" || parts[1].equals("true", ignoreCase = true)
        } else {
            button.defaultVisible
        }

        parsedItems.add(NowPlayingToolbarButtonItem(button, visible))
        seenButtons.add(button)
    }

    // Append any newly added buttons from enum that weren't in saved config
    for (defaultItem in DEFAULT_NOWPLAYING_TOOLBAR_ITEMS) {
        if (defaultItem.button !in seenButtons) {
            parsedItems.add(defaultItem)
            seenButtons.add(defaultItem.button)
        }
    }

    return parsedItems
}

fun List<NowPlayingToolbarButtonItem>.visibleButtons(): List<NowPlayingToolbarButton> {
    val enabled = this.filter { it.visible }.map { it.button }
    return if (enabled.isNotEmpty()) {
        enabled
    } else {
        listOf(
            NowPlayingToolbarButton.QUEUE,
            NowPlayingToolbarButton.LYRICS,
            NowPlayingToolbarButton.MORE
        )
    }
}

enum class CombinedPlaybackMode(
    val titleRes: Int,
    val icon: ImageVector,
    val isActive: Boolean
) {
    REPEAT_ALL(R.string.playlist_mode_repeat_all, Icons.Outlined.Repeat, true),
    REPEAT_ONE(R.string.playlist_mode_repeat_one, Icons.Filled.RepeatOne, true),
    SHUFFLE(R.string.playlist_mode_shuffle, Icons.Outlined.Shuffle, true);

    companion object {
        fun resolve(shuffle: Boolean, repeatMode: Int): CombinedPlaybackMode {
            return when {
                shuffle -> SHUFFLE
                repeatMode == Player.REPEAT_MODE_ONE -> REPEAT_ONE
                else -> REPEAT_ALL
            }
        }
    }

    fun next(): CombinedPlaybackMode {
        return when (this) {
            REPEAT_ALL -> REPEAT_ONE
            REPEAT_ONE -> SHUFFLE
            SHUFFLE -> REPEAT_ALL
        }
    }
}

fun PlayerManager.cycleCombinedPlaybackMode(): CombinedPlaybackMode {
    val current = CombinedPlaybackMode.resolve(
        shuffle = shuffleModeFlow.value,
        repeatMode = repeatModeFlow.value
    )
    val next = current.next()
    when (next) {
        CombinedPlaybackMode.REPEAT_ALL -> setPlaybackMode(Player.REPEAT_MODE_ALL, false)
        CombinedPlaybackMode.REPEAT_ONE -> setPlaybackMode(Player.REPEAT_MODE_ONE, false)
        CombinedPlaybackMode.SHUFFLE -> setPlaybackMode(Player.REPEAT_MODE_ALL, true)
    }
    return next
}
