package com.lynk.dvrprobe

import org.junit.Assert.*
import org.junit.Test

class AppThemeModeTest {
    @Test fun followsSystemNightChanges() {
        assertTrue(AppThemeMode.SYSTEM.isLight(systemNight = false))
        assertFalse(AppThemeMode.SYSTEM.isLight(systemNight = true))
    }

    @Test fun explicitChoicesIgnoreSystemNightChanges() {
        for (systemNight in listOf(false, true)) {
            assertTrue(AppThemeMode.LIGHT.isLight(systemNight))
            assertFalse(AppThemeMode.DARK.isLight(systemNight))
        }
    }

    @Test fun cyclesThroughAllModesAndRestoresSelection() {
        val light = AppThemeMode.SYSTEM.next()
        assertEquals(AppThemeMode.LIGHT, light)
        assertEquals(light, AppThemeMode.fromStored(light.name))
        assertEquals(AppThemeMode.DARK, light.next())
        assertEquals(AppThemeMode.SYSTEM, light.next().next())
    }

    @Test fun missingOrInvalidPreferenceFollowsSystem() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStored(null))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStored("invalid"))
    }
}
