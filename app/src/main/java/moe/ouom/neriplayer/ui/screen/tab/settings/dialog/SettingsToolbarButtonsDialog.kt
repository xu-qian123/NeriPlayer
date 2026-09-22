package moe.ouom.neriplayer.ui.screen.tab.settings.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.data.settings.DEFAULT_NOWPLAYING_TOOLBAR_BUTTONS_CONFIG
import moe.ouom.neriplayer.data.settings.DEFAULT_NOWPLAYING_TOOLBAR_ITEMS
import moe.ouom.neriplayer.data.settings.NowPlayingToolbarButton
import moe.ouom.neriplayer.data.settings.NowPlayingToolbarButtonItem
import moe.ouom.neriplayer.data.settings.decodeToolbarButtons
import moe.ouom.neriplayer.data.settings.encodeToolbarButtons
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsDialog
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsTextButton

@Composable
internal fun SettingsToolbarButtonsDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    config: String,
    onConfigChange: (String) -> Unit
) {
    if (!showDialog) return

    var items by remember(config) {
        mutableStateOf(decodeToolbarButtons(config))
    }

    fun swapItems(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in items.indices || toIndex !in items.indices) return
        val mutable = items.toMutableList()
        val temp = mutable[fromIndex]
        mutable[fromIndex] = mutable[toIndex]
        mutable[toIndex] = temp
        items = mutable
        onConfigChange(encodeToolbarButtons(mutable))
    }

    fun toggleVisibility(index: Int, visible: Boolean) {
        if (index !in items.indices) return
        val mutable = items.toMutableList()
        mutable[index] = mutable[index].copy(visible = visible)
        items = mutable
        onConfigChange(encodeToolbarButtons(mutable))
    }

    MiuixSettingsDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.settings_nowplaying_toolbar_buttons)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.settings_nowplaying_toolbar_buttons_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                items.forEachIndexed { index, item ->
                    ToolbarButtonItemRow(
                        item = item,
                        canMoveUp = index > 0,
                        canMoveDown = index < items.size - 1,
                        onMoveUp = { swapItems(index, index - 1) },
                        onMoveDown = { swapItems(index, index + 1) },
                        onVisibilityChange = { visible -> toggleVisibility(index, visible) }
                    )
                    if (index < items.size - 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        },
        confirmButton = {
            MiuixSettingsTextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            MiuixSettingsTextButton(
                onClick = {
                    items = DEFAULT_NOWPLAYING_TOOLBAR_ITEMS
                    onConfigChange(DEFAULT_NOWPLAYING_TOOLBAR_BUTTONS_CONFIG)
                }
            ) {
                Text(stringResource(R.string.settings_nowplaying_toolbar_buttons_reset))
            }
        }
    )
}

@Composable
private fun ToolbarButtonItemRow(
    item: NowPlayingToolbarButtonItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onVisibilityChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (item.button) {
            NowPlayingToolbarButton.QUEUE -> Icon(
                Icons.AutoMirrored.Outlined.QueueMusic,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            NowPlayingToolbarButton.PLAY_MODE -> Icon(
                Icons.Outlined.Repeat,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            NowPlayingToolbarButton.VOLUME -> Icon(
                Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            NowPlayingToolbarButton.LYRICS -> Icon(
                painter = painterResource(R.drawable.ic_lyrics_24),
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            NowPlayingToolbarButton.MORE -> Icon(
                Icons.Filled.MoreVert,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            NowPlayingToolbarButton.ADD_TO_PLAYLIST -> Icon(
                Icons.AutoMirrored.Outlined.PlaylistAdd,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            NowPlayingToolbarButton.TIMER -> Icon(
                Icons.Outlined.Timer,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = stringResource(item.button.titleRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onMoveUp,
            enabled = canMoveUp,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = "Move up",
                tint = if (canMoveUp) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onMoveDown,
            enabled = canMoveDown,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "Move down",
                tint = if (canMoveDown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Switch(
            checked = item.visible,
            onCheckedChange = onVisibilityChange
        )
    }
}
