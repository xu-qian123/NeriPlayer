package moe.ouom.neriplayer.data.settings

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
            NeteaseHomeSectionId.PERSONAL_RADAR,
            NeteaseHomeSectionId.DAILY_RECOMMEND,
            NeteaseHomeSectionId.PRIVATE_FM,
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
    fun orderNeteaseHomeSectionsRespectsCustomOrder() {
        data class Section(val id: NeteaseHomeSectionId, val name: String)

        val radar = listOf(
            Section(NeteaseHomeSectionId.PERSONAL_RADAR, "Radar"),
            Section(NeteaseHomeSectionId.DAILY_RECOMMEND, "Daily")
        )
        val trending = listOf(
            Section(NeteaseHomeSectionId.TOP_SOARING, "Soaring"),
            Section(NeteaseHomeSectionId.TOP_HOT, "Hot")
        )

        val customOrder = listOf(
            NeteaseHomeSectionId.TOP_HOT,
            NeteaseHomeSectionId.PERSONAL_RADAR,
            NeteaseHomeSectionId.TOP_SOARING,
            NeteaseHomeSectionId.DAILY_RECOMMEND,
            NeteaseHomeSectionId.PRIVATE_FM,
            NeteaseHomeSectionId.PERSONALIZED_NEW_SONGS,
            NeteaseHomeSectionId.TOP_NEW
        )

        val result = orderNeteaseHomeSections(radar, trending, customOrder) { it.id }
        assertEquals(
            listOf("Hot", "Radar", "Soaring", "Daily"),
            result.map { it.name }
        )
    }
}
