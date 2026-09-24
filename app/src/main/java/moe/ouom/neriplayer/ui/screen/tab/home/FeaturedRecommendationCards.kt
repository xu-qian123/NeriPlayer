package moe.ouom.neriplayer.ui.screen.tab.home

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2026 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 */

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.ui.util.rememberSongDisplayCoverUrl
import moe.ouom.neriplayer.util.media.fastScrollableImageRequest
import java.time.LocalDate

private val FeaturedCardWidth = 120.dp
private val FeaturedCardHeight = 174.dp
private val FeaturedCardShape = RoundedCornerShape(16.dp)
private val FeaturedCardBlurHeight = 52.dp

/**
 * 首页三大专属卡片横向滑选行 (每日推荐、私人雷达、私人漫游)
 *
 * 紧凑原生卡片，封面取歌单/流第 1 首歌曲，下半部采用真实高斯模糊。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeaturedRecommendationRow(
    dailySongs: List<SongItem>,
    radarSongs: List<SongItem>,
    hasLogin: Boolean,
    onDailyRecommendClick: () -> Unit,
    onPersonalRadarClick: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    offlineMode: Boolean,
    modifier: Modifier = Modifier
) {
    val overscrollOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    val current = overscrollOffset.value
                    if (current > 0f && available.x < 0f) {
                        // 左边缘拉伸后，反向向左滑释放拉伸
                        val consumed = available.x.coerceAtLeast(-current)
                        scope.launch { overscrollOffset.snapTo(current + consumed) }
                        if (current + consumed <= 0.5f) hasTriggeredHaptic = false
                        return Offset(consumed, 0f)
                    } else if (current < 0f && available.x > 0f) {
                        // 右边缘拉伸后，反向向右滑释放拉伸
                        val consumed = available.x.coerceAtMost(-current)
                        scope.launch { overscrollOffset.snapTo(current + consumed) }
                        if (current + consumed >= -0.5f) hasTriggeredHaptic = false
                        return Offset(consumed, 0f)
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.x != 0f) {
                    val current = overscrollOffset.value
                    val maxStretch = 150f
                    val progress = (abs(current) / maxStretch).coerceIn(0f, 1f)
                    // 渐进式橡皮筋阻尼：越往外拉阻力越大
                    val damping = 0.40f * (1f - progress * 0.70f)
                    val delta = available.x * damping
                    val newOffset = (current + delta).coerceIn(-maxStretch, maxStretch)

                    if (abs(current) < 1f && abs(newOffset) >= 1f && !hasTriggeredHaptic) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        hasTriggeredHaptic = true
                    }

                    scope.launch { overscrollOffset.snapTo(newOffset) }
                    return Offset(available.x, 0f)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (abs(overscrollOffset.value) > 0.5f) {
                    hasTriggeredHaptic = false
                    scope.launch {
                        overscrollOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = 320f
                            )
                        )
                    }
                    return Velocity(available.x, 0f)
                }
                hasTriggeredHaptic = false
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                hasTriggeredHaptic = false
                if (abs(available.x) > 120f && abs(overscrollOffset.value) < 1f) {
                    // 惯性滑行撞墙果冻微冲回弹
                    val target = (available.x * 0.032f).coerceIn(-90f, 90f)
                    scope.launch {
                        overscrollOffset.animateTo(
                            targetValue = target,
                            animationSpec = tween(durationMillis = 60, easing = LinearOutSlowInEasing)
                        )
                        overscrollOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = 320f
                            )
                        )
                    }
                } else if (abs(overscrollOffset.value) > 0.5f) {
                    scope.launch {
                        overscrollOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = 320f
                            )
                        )
                    }
                }
                return Velocity.Zero
            }
        }
    }

    val cardModifier = Modifier.graphicsLayer {
        translationX = overscrollOffset.value
    }

    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        LazyRow(
            modifier = modifier
                .fillMaxWidth()
                .clipToBounds()
                .nestedScroll(nestedScrollConnection),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "featured:daily_recommend") {
                DailyRecommendCard(
                    firstSong = dailySongs.firstOrNull(),
                    hasLogin = hasLogin,
                    onClick = onDailyRecommendClick,
                    onShowSnackbar = onShowSnackbar,
                    offlineMode = offlineMode,
                    modifier = cardModifier
                )
            }
            item(key = "featured:personal_radar") {
                PersonalRadarCard(
                    firstSong = radarSongs.firstOrNull(),
                    onClick = onPersonalRadarClick,
                    offlineMode = offlineMode,
                    modifier = cardModifier
                )
            }
            item(key = "featured:private_fm") {
                PrivateRoamingFeaturedCard(
                    hasLogin = hasLogin,
                    onShowSnackbar = onShowSnackbar,
                    offlineMode = offlineMode,
                    modifier = cardModifier
                )
            }
        }
    }
}

/**
 * 通用双层高斯模糊底色卡片基座
 */
@Composable
private fun FeaturedBlurCard(
    coverUrl: String?,
    subtitleText: String,
    topBadge: @Composable () -> Unit,
    actionButton: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    offlineMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    Surface(
        modifier = modifier
            .size(width = FeaturedCardWidth, height = FeaturedCardHeight)
            .clip(FeaturedCardShape)
            .clickable(onClick = onClick),
        shape = FeaturedCardShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. 底层封面通底
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = fastScrollableImageRequest(
                        context = context,
                        data = coverUrl,
                        sizePx = 384,
                        offlineMode = offlineMode
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            }

            // 2. 上部微暗影渐变保护 (确保白字标签清晰)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.55f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // 3. 左上角标签
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 8.dp, start = 8.dp)
            ) {
                topBadge()
            }

            // 4. 浮动操作按钮 (可选，如漫游播放控制)
            if (actionButton != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = FeaturedCardBlurHeight + 8.dp, end = 8.dp)
                ) {
                    actionButton()
                }
            }

            // 5. 下半部：真实高斯模糊毛玻璃层 (模糊下半部封面并透出色调)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FeaturedCardBlurHeight)
                    .align(Alignment.BottomCenter)
                    .clipToBounds()
            ) {
                // 模糊的底层封面副本
                if (!coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = fastScrollableImageRequest(
                            context = context,
                            data = coverUrl,
                            sizePx = 128,
                            offlineMode = offlineMode
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(radius = 20.dp)
                    )
                }

                // 微暗色保护衬底
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isDark) {
                                Color.Black.copy(alpha = 0.42f)
                            } else {
                                Color.Black.copy(alpha = 0.35f)
                            }
                        )
                )

                // 推荐语文字
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 13.5.sp
                        ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 每日推荐卡片
 */
@Composable
private fun DailyRecommendCard(
    firstSong: SongItem?,
    hasLogin: Boolean,
    onClick: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    offlineMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coverUrl = rememberSongDisplayCoverUrl(firstSong)
    val dayOfMonth = remember { LocalDate.now().dayOfMonth.toString() }

    val subtitle = when {
        firstSong != null && firstSong.name.isNotBlank() -> "每日推荐 | 从「${firstSong.name}」听起"
        !hasLogin -> stringResource(R.string.home_login_required_hint)
        else -> stringResource(R.string.home_netease_daily_songs)
    }

    FeaturedBlurCard(
        coverUrl = coverUrl,
        subtitleText = subtitle,
        topBadge = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayOfMonth,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = Color.White
                    )
                }
                Text(
                    text = stringResource(R.string.home_netease_daily_songs),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
        },
        onClick = {
            if (!hasLogin && firstSong == null) {
                onShowSnackbar(context.getString(R.string.home_login_required))
            } else {
                onClick()
            }
        },
        offlineMode = offlineMode,
        modifier = modifier
    )
}

/**
 * 私人雷达卡片
 */
@Composable
private fun PersonalRadarCard(
    firstSong: SongItem?,
    onClick: () -> Unit,
    offlineMode: Boolean,
    modifier: Modifier = Modifier
) {
    val coverUrl = rememberSongDisplayCoverUrl(firstSong)

    val subtitle = if (firstSong != null && firstSong.name.isNotBlank()) {
        "今天《${firstSong.name}》爱不释耳"
    } else {
        stringResource(R.string.recommend_radar)
    }

    FeaturedBlurCard(
        coverUrl = coverUrl,
        subtitleText = subtitle,
        topBadge = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Radar,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = stringResource(R.string.recommend_radar),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
        },
        onClick = onClick,
        offlineMode = offlineMode,
        modifier = modifier
    )
}

/**
 * 私人漫游卡片 (唯一直接启播歌曲与播放/暂停控制)
 */
@Composable
private fun PrivateRoamingFeaturedCard(
    hasLogin: Boolean,
    onShowSnackbar: (String) -> Unit,
    offlineMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val roamingManager = remember { AppContainer.neteaseRoamingManager }

    val isRoamingMode by PlayerManager.isRoamingModeFlow.collectAsStateWithLifecycle()
    val isPlaying by PlayerManager.isPlayingFlow.collectAsStateWithLifecycle()
    val isLoading by roamingManager.isLoadingFlow.collectAsStateWithLifecycle()
    val dailyPrefetchedSong by roamingManager.dailyPrefetchedSongFlow.collectAsStateWithLifecycle()

    val isRoamingPlaying = isRoamingMode && isPlaying

    // 卡片封面永远只展示当天第一次预取的信息
    val coverUrl = rememberSongDisplayCoverUrl(dailyPrefetchedSong)

    fun handleRoamingToggle() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (offlineMode) {
            onShowSnackbar(context.getString(R.string.home_offline_no_continue))
            return
        }
        if (!hasLogin) {
            onShowSnackbar(context.getString(R.string.home_roaming_need_login))
            return
        }
        roamingManager.startRoaming(onFailure = onShowSnackbar)
    }

    // 文案联动永远只展示当天第一次预取的信息
    val subtitle = when {
        !hasLogin -> stringResource(R.string.home_roaming_need_login_hint)
        isLoading && dailyPrefetchedSong == null -> stringResource(R.string.home_roaming_loading)
        dailyPrefetchedSong != null && !dailyPrefetchedSong?.name.isNullOrBlank() -> {
            "从「${dailyPrefetchedSong?.name}」开启无限漫游"
        }
        else -> "从「漫游推荐」开启无限漫游"
    }

    FeaturedBlurCard(
        coverUrl = coverUrl,
        subtitleText = subtitle,
        topBadge = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AllInclusive,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = stringResource(R.string.home_netease_private_fm),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
        },
        actionButton = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f))
                    .clickable(onClick = ::handleRoamingToggle),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading && !PlayerManager.hasItems()) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = if (isRoamingPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        },
        onClick = ::handleRoamingToggle,
        offlineMode = offlineMode,
        modifier = modifier
    )
}
