package moe.ouom.neriplayer.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class SystemThemeStateTest {

    @Test
    fun `isActualSystemDarkTheme detects dark mode from context configuration`() {
        val mockContext = mock(Context::class.java)
        val mockResources = mock(Resources::class.java)
        val config = Configuration().apply {
            uiMode = Configuration.UI_MODE_NIGHT_YES
        }
        `when`(mockContext.resources).thenReturn(mockResources)
        `when`(mockResources.configuration).thenReturn(config)

        assertTrue(isActualSystemDarkTheme(mockContext))
    }

    @Test
    fun `isActualSystemDarkTheme detects light mode from context configuration`() {
        val mockContext = mock(Context::class.java)
        val mockResources = mock(Resources::class.java)
        val config = Configuration().apply {
            uiMode = Configuration.UI_MODE_NIGHT_NO
        }
        `when`(mockContext.resources).thenReturn(mockResources)
        `when`(mockResources.configuration).thenReturn(config)

        assertFalse(isActualSystemDarkTheme(mockContext))
    }
}
