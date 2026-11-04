package com.jim.realcash.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WonTest {
    @Test
    fun arithmeticAndComparison() {
        assertEquals(Won(7_000), Won(6_500) + Won(500))
        assertEquals(Won(6_000), Won(6_500) - Won(500))
        assertEquals(Won(30_000), Won(10_000) * 3)
        assertTrue(Won(1_000) > Won(500))
        assertEquals(Won(1_510), listOf(Won(1_000), Won(500), Won(10)).sum())
        assertEquals(Won.ZERO, emptyList<Won>().sum())
    }

    @Test
    fun roundUpToCash() {
        assertEquals(Won(6_500), Won(6_500).roundUpToCash())
        assertEquals(Won(6_510), Won(6_505).roundUpToCash())
        assertEquals(Won(10), Won(1).roundUpToCash())
        assertTrue(Won(6_500).isCashRepresentable)
        assertFalse(Won(6_505).isCashRepresentable)
    }

    @Test
    fun formatting() {
        assertEquals("6,500원", Won(6_500).toString())
        assertEquals("1,234,567원", Won(1_234_567).toString())
        assertEquals("0원", Won.ZERO.toString())
        assertEquals("-500원", Won(-500).toString())
    }

    @Test
    fun denominationLookup() {
        assertEquals(Denomination.entries.sortedByDescending { it.won }, Denomination.descending)
        assertEquals(Denomination.W50000, Denomination.descending.first())
        assertEquals(Denomination.C10, Denomination.descending.last())
        assertEquals(Denomination.C500, Denomination.of(500))
        assertNull(Denomination.of(2_000))
        assertEquals(Denomination.Kind.BILL, Denomination.W1000.kind)
        assertEquals(Denomination.Kind.COIN, Denomination.C500.kind)
        assertEquals(Won(61_500), mapOf(Denomination.W50000 to 1, Denomination.W10000 to 1, Denomination.C500 to 3).total())
    }
}
