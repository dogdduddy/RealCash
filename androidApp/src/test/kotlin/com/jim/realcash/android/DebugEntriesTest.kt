package com.jim.realcash.android

import com.jim.realcash.android.debug.DebugStrings
import com.jim.realcash.android.debug.androidDebugEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DebugEntriesTest {
    @Test
    fun debugBuildsListBothScreens() {
        assertEquals(listOf(DebugStrings.ACCESS_TITLE, DebugStrings.SAMPLES_TITLE), androidDebugEntries(debug = true).map { it.title })
    }

    @Test
    fun releaseBuildsListNothing() {
        assertTrue(androidDebugEntries(debug = false).isEmpty())
    }
}
