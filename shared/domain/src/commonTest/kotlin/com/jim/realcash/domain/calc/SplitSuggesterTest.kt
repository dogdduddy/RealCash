package com.jim.realcash.domain.calc

import com.jim.realcash.domain.model.Denomination.C500
import com.jim.realcash.domain.model.Denomination.W1000
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.Denomination.W5000
import com.jim.realcash.domain.model.Denomination.W50000
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.total
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SplitSuggesterTest {
    @Test
    fun largestFirstIsAtmStyle() {
        assertEquals(mapOf(W50000 to 2), SplitSuggester.suggest(Won(100_000), SplitStyle.LARGEST_FIRST))
    }

    @Test
    fun balancedFavoursTenThousandBills() {
        assertEquals(mapOf(W50000 to 1, W10000 to 5), SplitSuggester.suggest(Won(100_000), SplitStyle.BALANCED))
        assertEquals(mapOf(W10000 to 3), SplitSuggester.suggest(Won(30_000), SplitStyle.BALANCED))
        assertEquals(mapOf(W10000 to 7), SplitSuggester.suggest(Won(70_000), SplitStyle.BALANCED))
        assertEquals(mapOf(W50000 to 3, W10000 to 5), SplitSuggester.suggest(Won(200_000), SplitStyle.BALANCED))
        assertEquals(
            mapOf(W10000 to 3, W5000 to 1, W1000 to 2, C500 to 1),
            SplitSuggester.suggest(Won(37_500), SplitStyle.BALANCED),
        )
    }

    @Test
    fun suggestionsAlwaysSumToAmountAndValidate() {
        val random = Random(7)
        repeat(200) {
            val amount = Won(random.nextInt(0, 50_000) * 10)
            SplitStyle.entries.forEach { style ->
                val split = SplitSuggester.suggest(amount, style)
                assertEquals(amount, split.total(), "$style for $amount")
                assertTrue(SplitSuggester.validate(amount, split), "$style for $amount")
            }
        }
    }

    @Test
    fun validateRejectsWrongSplits() {
        assertFalse(SplitSuggester.validate(Won(100_000), mapOf(W50000 to 1)))
        assertFalse(SplitSuggester.validate(Won(100_000), mapOf(W50000 to 3, W10000 to -5)))
        assertFalse(SplitSuggester.validate(Won(-10_000), mapOf(W10000 to -1)))
        assertTrue(SplitSuggester.validate(Won(100_000), mapOf(W50000 to 1, W10000 to 5, W5000 to 0)))
        assertTrue(SplitSuggester.validate(Won(0), emptyMap()))
    }

    @Test
    fun rejectsAmountsThatAreNotMultiplesOfTen() {
        SplitStyle.entries.forEach { style ->
            assertFailsWith<IllegalArgumentException> { SplitSuggester.suggest(Won(10_005), style) }
        }
    }
}
