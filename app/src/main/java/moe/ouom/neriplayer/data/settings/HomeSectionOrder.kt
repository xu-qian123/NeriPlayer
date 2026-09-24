package moe.ouom.neriplayer.data.settings

import androidx.annotation.StringRes
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.ui.viewmodel.tab.NeteaseHomeSongSource

/**
 * 首页板块排序
 *
 * 用户可在 设置→个性化→首页板块排序 里调整各歌曲板块的显示顺序。
 * 顺序以逗号分隔的 id.name 列表持久化到 DataStore（经 AutoSettingsSchema 自动生成），
 * 缺失/非法的名字回退到默认顺序，保证旧版本数据或脏数据不会让首页丢板块。
 */
enum class NeteaseHomeSectionId(@StringRes val titleRes: Int) {
    FEATURED_CARDS(R.string.home_section_featured_cards),
    TOP_SOARING(R.string.recommend_trending),
    PERSONALIZED_NEW_SONGS(R.string.home_netease_new_songs),
    TOP_HOT(R.string.home_netease_hot_rank),
    TOP_NEW(R.string.home_netease_new_rank);

    val songSource: NeteaseHomeSongSource?
        get() = when (this) {
            FEATURED_CARDS -> null
            TOP_SOARING -> NeteaseHomeSongSource.TOP_SOARING
            PERSONALIZED_NEW_SONGS -> NeteaseHomeSongSource.PERSONALIZED_NEW_SONGS
            TOP_HOT -> NeteaseHomeSongSource.TOP_HOT
            TOP_NEW -> NeteaseHomeSongSource.TOP_NEW
        }
}

/** 默认顺序：专属推荐卡片 → 飙升榜 → 新歌 → 热歌 → 新歌榜 */
val DefaultNeteaseHomeSections: List<NeteaseHomeSectionId> = listOf(
    NeteaseHomeSectionId.FEATURED_CARDS,
    NeteaseHomeSectionId.TOP_SOARING,
    NeteaseHomeSectionId.PERSONALIZED_NEW_SONGS,
    NeteaseHomeSectionId.TOP_HOT,
    NeteaseHomeSectionId.TOP_NEW
)

fun encodeNeteaseHomeSectionOrder(order: List<NeteaseHomeSectionId>): String {
    return order.joinToString(separator = ",") { it.name }
}

fun parseNeteaseHomeSectionOrder(raw: String?): List<NeteaseHomeSectionId> {
    if (raw.isNullOrBlank()) return DefaultNeteaseHomeSections
    val parsed = raw.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { name ->
            when (name) {
                "FEATURED_CARDS" -> NeteaseHomeSectionId.FEATURED_CARDS
                "PERSONAL_RADAR", "DAILY_RECOMMEND", "PRIVATE_FM" -> NeteaseHomeSectionId.FEATURED_CARDS
                else -> runCatching { NeteaseHomeSectionId.valueOf(name) }.getOrNull()
            }
        }
        .distinct()
    // 丢弃未知 id 后按默认顺序补齐缺失项，保证任何持久化值都能还原出完整列表
    val missing = DefaultNeteaseHomeSections.filterNot { id -> id in parsed }
    return parsed + missing
}

fun NeteaseHomeSongSource.toHomeSectionId(): NeteaseHomeSectionId {
    return when (this) {
        NeteaseHomeSongSource.TOP_SOARING -> NeteaseHomeSectionId.TOP_SOARING
        NeteaseHomeSongSource.PERSONALIZED_NEW_SONGS -> NeteaseHomeSectionId.PERSONALIZED_NEW_SONGS
        NeteaseHomeSongSource.TOP_HOT -> NeteaseHomeSectionId.TOP_HOT
        NeteaseHomeSongSource.TOP_NEW -> NeteaseHomeSectionId.TOP_NEW
        NeteaseHomeSongSource.PERSONAL_RADAR,
        NeteaseHomeSongSource.DAILY_RECOMMEND,
        NeteaseHomeSongSource.PRIVATE_FM -> NeteaseHomeSectionId.FEATURED_CARDS
    }
}
