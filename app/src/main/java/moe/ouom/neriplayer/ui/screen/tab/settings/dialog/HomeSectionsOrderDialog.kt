package moe.ouom.neriplayer.ui.screen.tab.settings.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.data.settings.DefaultNeteaseHomeSections
import moe.ouom.neriplayer.data.settings.NeteaseHomeSectionId
import moe.ouom.neriplayer.data.settings.encodeNeteaseHomeSectionOrder
import moe.ouom.neriplayer.data.settings.generated.AutoSettingsRepository
import moe.ouom.neriplayer.data.settings.parseNeteaseHomeSectionOrder
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsDialog
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsTextButton

/**
 * 首页板块排序弹窗：支持上移、下移调整歌曲板块渲染顺序，并可一键恢复默认。
 */
@Composable
fun HomeSectionsOrderDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    autoSettingsRepository: AutoSettingsRepository,
    scope: CoroutineScope
) {
    if (!showDialog) return

    val savedOrderRaw by autoSettingsRepository.homeSectionsOrderFlow.collectAsState(initial = null)
    var sections by remember(savedOrderRaw) {
        mutableStateOf(parseNeteaseHomeSectionOrder(savedOrderRaw))
    }

    fun persist(next: List<NeteaseHomeSectionId>) {
        sections = next
        scope.launch {
            autoSettingsRepository.setHomeSectionsOrder(encodeNeteaseHomeSectionOrder(next))
        }
    }

    fun move(item: NeteaseHomeSectionId, delta: Int) {
        val currentIndex = sections.indexOf(item)
        val targetIndex = (currentIndex + delta).coerceIn(0, sections.lastIndex)
        if (targetIndex == currentIndex) return
        persist(sections.toMutableList().apply {
            removeAt(currentIndex)
            add(targetIndex, item)
        })
    }

    MiuixSettingsDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            MiuixSettingsTextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_done))
            }
        },
        dismissButton = {
            MiuixSettingsTextButton(
                onClick = { persist(DefaultNeteaseHomeSections) }
            ) {
                Text(stringResource(R.string.settings_home_sections_order_reset))
            }
        },
        title = { Text(stringResource(R.string.settings_home_sections_order)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                sections.forEachIndexed { index, section ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Sort,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(section.titleRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        )
                        IconButton(
                            enabled = index > 0,
                            onClick = { move(section, -1) }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowUp,
                                contentDescription = "Move up",
                                tint = if (index > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                        IconButton(
                            enabled = index < sections.lastIndex,
                            onClick = { move(section, 1) }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "Move down",
                                tint = if (index < sections.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }
                }
            }
        }
    )
}
