package com.example.ui.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterHeightTest {

    @Test
    fun matchesHeightRange_includesSelectedBounds() {
        assertTrue(matchesHeightRange("5'6''", "5'6''", "5'10''"))
        assertTrue(matchesHeightRange("5'10''", "5'6''", "5'10''"))
    }

    @Test
    fun matchesHeightRange_excludesHeightsOutsideSelectedRange() {
        assertFalse(matchesHeightRange("5'5''", "5'6''", "5'10''"))
        assertFalse(matchesHeightRange("5'11''", "5'6''", "5'10''"))
    }

    @Test
    fun matchesHeightRange_supportsStoredHeightFormatsAndUnknownHeights() {
        assertTrue(matchesHeightRange("5'08\" (173 cm)", "5'8''", "5'10''"))
        assertTrue(matchesHeightRange("", "5'8''", "5'10''"))
    }

    @Test
    fun matchesOppositeGender_handlesEnglishBengaliAndUnknownValues() {
        assertTrue(matchesOppositeGender("Bride", "Groom"))
        assertTrue(matchesOppositeGender("কনে", "পুরুষ"))
        assertTrue(matchesOppositeGender("Female", "Male"))
        assertFalse(matchesOppositeGender("Female", "Bride"))
        assertTrue(matchesOppositeGender("", "Male"))
        assertTrue(matchesOppositeGender("Female", ""))
    }
}
