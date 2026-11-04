package com.jim.realcash.data

import com.jim.realcash.domain.parser.DefaultRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ParserRulesRepositoryTest {

    private val override = """
        [
          {"id": "a", "sourceApps": ["x.app"], "amountRegex": "([0-9][0-9,]*)원", "baseConfidence": 1.0},
          {"id": "bad-regex", "sourceApps": ["x.app"], "amountRegex": "([0-9", "baseConfidence": 1.0},
          {"id": "bad-confidence", "sourceApps": ["x.app"], "amountRegex": "([0-9]+)원", "baseConfidence": 1.5},
          {"id": "missing-fields", "sourceApps": ["x.app"]},
          {"id": "a", "sourceApps": ["y.app"], "amountRegex": "([0-9]+)원", "baseConfidence": 1.0},
          {"id": "b", "sourceApps": [], "amountRegex": "₩([0-9,]+)", "merchantRegex": "(.+)에서", "baseConfidence": 0.5,
           "someFutureField": true}
        ]
    """.trimIndent()

    @Test
    fun defaultsWithoutOverride() = runTest {
        val repo = dataOf(inMemoryDriver()).parserRules
        assertEquals(DefaultRules.rules, repo.load())
        assertNull(repo.lastReport.value)
    }

    @Test
    fun invalidRulesAreSkippedOthersApplied() = runTest {
        val driver = inMemoryDriver()
        val repo = dataOf(driver).parserRules
        val report = repo.applyOverride(override)

        assertTrue(report.accepted)
        assertNull(report.error)
        assertEquals(listOf("a", "b"), report.appliedRuleIds)
        assertEquals(listOf("bad-regex", "bad-confidence", "missing-fields", "a"), report.skipped.map { it.id })
        assertTrue("regex" in report.skipped[0].reason, report.skipped[0].reason)
        assertEquals("duplicate id", report.skipped[3].reason)
        assertEquals(listOf("a", "b"), repo.rules.value.map { it.id })

        // Survives a restart (re-validated on load).
        val reloaded = dataOf(driver).parserRules
        assertEquals(listOf("a", "b"), reloaded.load().map { it.id })
        assertEquals(report, reloaded.lastReport.value)

        // Removing the override restores the bundled rules, also after a restart.
        repo.clearOverride()
        assertEquals(DefaultRules.rules, repo.rules.value)
        assertEquals(DefaultRules.rules, dataOf(driver).parserRules.load())
        driver.close()
    }

    @Test
    fun badOverrideNeverLeavesTheAppWithoutRules() = runTest {
        val driver = inMemoryDriver()
        val repo = dataOf(driver).parserRules

        val malformed = repo.applyOverride("{not json")
        assertFalse(malformed.accepted)
        assertNotNull(malformed.error)
        assertEquals(DefaultRules.rules, repo.rules.value)

        val notArray = repo.applyOverride("""{"id": "a"}""")
        assertFalse(notArray.accepted)
        assertEquals(DefaultRules.rules, repo.rules.value)

        val allInvalid = repo.applyOverride(
            """[{"id": "x", "sourceApps": [], "amountRegex": "(", "baseConfidence": 1.0}]""",
        )
        assertFalse(allInvalid.accepted)
        assertEquals(listOf("x"), allInvalid.skipped.map { it.id })
        assertEquals(DefaultRules.rules, repo.rules.value)

        // A refused override does not replace an accepted one.
        assertTrue(repo.applyOverride(override).accepted)
        assertFalse(repo.applyOverride("[]").accepted)
        assertEquals(listOf("a", "b"), repo.rules.value.map { it.id })
        assertEquals(listOf("a", "b"), dataOf(driver).parserRules.load().map { it.id })
        driver.close()
    }
}
