package moe.ouom.neriplayer.data.settings

import moe.ouom.neriplayer.ui.viewmodel.tab.NeteaseHomeSongSource
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSectionOrderTest {

    @Test
    fun parseNullOrBlankReturnsDefaultOrder() {
        assertEquals(DefaultNeteaseHomeSections, parseNeteaseHomeSectionOrder(null))
        assertEquals(DefaultNeteaseHomeSections, parseNeteaseHomeSectionOrder(""))
        assertEquals(DefaultNeteaseHomeSections, parseNeteaseHomeSectionOrder("   "))
    }

    @Test
    fun encodeAndParseRoundTrip() {
        val custom = listOf(
            NeteaseHomeSectionId.TOP_HOT,
            NeteaseHomeSectionId.TOP_SOARING,
            NeteaseHomeSectionId.FEATURED_CARDS,
            NeteaseHomeSectionId.PERSONALIZED_NEW_SONGS,
            NeteaseHomeSectionId.TOP_NEW
        )
        val encoded = encodeNeteaseHomeSectionOrder(custom)
        val parsed = parseNeteaseHomeSectionOrder(encoded)
        assertEquals(custom, parsed)
    }

    @Test
    fun parseAppendsMissingSections() {
        val partial = "TOP_HOT,TOP_SOARING"
        val parsed = parseNeteaseHomeSectionOrder(partial)
        assertEquals(NeteaseHomeSectionId.TOP_HOT, parsed[0])
        assertEquals(NeteaseHomeSectionId.TOP_SOARING, parsed[1])
        assertEquals(DefaultNeteaseHomeSections.size, parsed.size)
        // All default sections should be present
        assertEquals(DefaultNeteaseHomeSections.toSet(), parsed.toSet())
    }

    @Test
    fun parseMapsLegacySectionIdsToFeaturedCards() {
        val legacy = "PERSONAL_RADAR,DAILY_RECOMMEND,PRIVATE_FM,TOP_HOT"
        val parsed = parseNeteaseHomeSectionOrder(legacy)
        assertEquals(NeteaseHomeSectionId.FEATURED_CARDS, parsed[0])
        assertEquals(NeteaseHomeSectionId.TOP_HOT, parsed[1])
        assertEquals(DefaultNeteaseHomeSections.size, parsed.size)
        assertEquals(DefaultNeteaseHomeSections.toSet(), parsed.toSet())
    }

    @Test
    fun songSourceToHomeSectionIdMapsCorrectly() {
        assertEquals(NeteaseHomeSectionId.TOP_SOARING, NeteaseHomeSongSource.TOP_SOARING.toHomeSectionId())
        assertEquals(NeteaseHomeSectionId.PERSONALIZED_NEW_SONGS, NeteaseHomeSongSource.PERSONALIZED_NEW_SONGS.toHomeSectionId())
        assertEquals(NeteaseHomeSectionId.TOP_HOT, NeteaseHomeSongSource.TOP_HOT.toHomeSectionId())
        assertEquals(NeteaseHomeSectionId.TOP_NEW, NeteaseHomeSongSource.TOP_NEW.toHomeSectionId())
        assertEquals(NeteaseHomeSectionId.FEATURED_CARDS, NeteaseHomeSongSource.PERSONAL_RADAR.toHomeSectionId())
        assertEquals(NeteaseHomeSectionId.FEATURED_CARDS, NeteaseHomeSongSource.DAILY_RECOMMEND.toHomeSectionId())
        assertEquals(NeteaseHomeSectionId.FEATURED_CARDS, NeteaseHomeSongSource.PRIVATE_FM.toHomeSectionId())
    }
}
