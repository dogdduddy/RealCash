package com.jim.realcash.domain.parser

import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.testing.T0
import com.jim.realcash.domain.util.SequentialIdGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * Table tests against [DefaultRules]. The sample strings follow the GUESSED placeholder formats documented on
 * each rule; replace them together with the rules once real notification samples are collected (Phase 0).
 */
class RuleBasedParserTest {
    private val parser = RuleBasedParser(DefaultRules.rules, SequentialIdGenerator("p-"))

    private data class Case(
        val name: String,
        val app: String,
        val title: String?,
        val text: String?,
        val amount: Int?,
        val merchant: String? = null,
        val refund: Boolean = false,
        val confidence: Float? = null,
        val bigText: String? = null,
    )

    private val card = "com.shcard.smartpay"

    private val cases = listOf(
        Case("samsung comma", DefaultRules.SAMSUNG_WALLET, "결제 완료", "스타벅스 강남점에서 6,500원 결제", 6_500, "스타벅스 강남점", confidence = 1.0f),
        Case("samsung no comma", DefaultRules.SAMSUNG_WALLET, "결제 완료", "GS25 역삼점에서 1234원 결제", 1_234, "GS25 역삼점"),
        Case("samsung won sign", DefaultRules.SAMSUNG_WALLET, "결제 완료", "₩12,000 결제되었습니다", 12_000, null, confidence = 0.8f),
        Case("samsung refund", DefaultRules.SAMSUNG_WALLET, "결제 취소", "스타벅스 강남점에서 6,500원 결제 취소", 6_500, "스타벅스 강남점", refund = true),
        Case("samsung top-up ignored", DefaultRules.SAMSUNG_WALLET, "충전 완료", "삼성페이 머니 10,000원 충전", null),
        Case(
            "samsung truncated text, full bigText", DefaultRules.SAMSUNG_WALLET, "결제 완료", "스타벅스 강남점에서 6,5…", 6_500, "스타벅스 강남점",
            bigText = "스타벅스 강남점에서 6,500원 결제",
        ),
        Case("toss payment", DefaultRules.TOSS, "토스페이", "6,500원 결제 | 스타벅스 강남점", 6_500, "스타벅스 강남점", confidence = 1.0f),
        Case("toss refund", DefaultRules.TOSS, "토스페이", "6,500원 환불 완료", 6_500, null, refund = true),
        Case("toss transfer ignored", DefaultRules.TOSS, "송금 완료", "홍길동님에게 30,000원 송금했어요", null),
        Case("toss no amount", DefaultRules.TOSS, "토스", "이번 주 소비 리포트가 도착했어요", null),
        Case(
            "card approval", card, "신한카드", "신한카드(1234)승인 홍*동 6,500원(일시불)11/04 12:34 스타벅스 누적1,234,567원",
            6_500, "스타벅스", confidence = 0.9f,
        ),
        Case("card cancel", card, "신한카드", "신한카드(1234)승인취소 홍*동 6,500원(일시불)11/04 12:34 스타벅스", 6_500, "스타벅스", refund = true),
        Case("unknown app", "com.example.game", "보상", "5,000원 쿠폰이 도착했어요", null),
    )

    @Test
    fun defaultRulesTable() {
        cases.forEach { c ->
            val result = parser.parse(RawNotification(c.app, c.title, c.text, c.bigText, T0))
            if (c.amount == null) {
                assertNull(result, c.name)
                return@forEach
            }
            assertNotNull(result, c.name)
            assertEquals(Won(c.amount), result.amount, c.name)
            assertEquals(c.merchant, result.merchant, c.name)
            assertEquals(c.refund, result.isRefund, c.name)
            assertEquals(c.app, result.sourceApp, c.name)
            assertEquals(T0, result.detectedAt, c.name)
            c.confidence?.let { assertEquals(it, result.confidence, 1e-4f, c.name) }
        }
    }

    @Test
    fun everyDefaultRuleHasAnId() {
        assertTrue(DefaultRules.rules.isNotEmpty())
        assertEquals(DefaultRules.rules.size, DefaultRules.rules.map { it.id }.toSet().size)
    }

    @Test
    fun idsRawTextAndConfidenceScoring() {
        val ids = SequentialIdGenerator("x-")
        val rules = listOf(
            ParserRule(id = "known", sourceApps = listOf("a.app"), amountRegex = "([0-9,]+)원", merchantRegex = "가맹점 (\\S+)", baseConfidence = 0.5f),
            ParserRule(id = "any", sourceApps = emptyList(), amountRegex = "([0-9,]+)원", merchantRegex = "가맹점 (\\S+)", baseConfidence = 1.0f),
        )
        val p = RuleBasedParser(rules, ids)
        val known = p.parse(RawNotification("a.app", "알림", "가맹점 카페 3,000원", null, T0))
        assertNotNull(known)
        assertEquals(PaymentId("x-1"), known.id)
        assertEquals("알림\n가맹점 카페 3,000원", known.rawText)
        assertEquals(1.0f * 0.5f, known.confidence, 1e-4f) // (0.6 + 0.2 + 0.2) × 0.5

        // Wildcard rule: no known-app bonus.
        val other = p.parse(RawNotification("b.app", null, "가맹점 카페 3,000원", null, T0))
        assertNotNull(other)
        assertEquals(0.8f, other.confidence, 1e-4f)
        val noMerchant = p.parse(RawNotification("b.app", null, "3,000원", null, T0))
        assertNotNull(noMerchant)
        assertEquals(0.6f, noMerchant.confidence, 1e-4f)
    }

    @Test
    fun emptyOrZeroAmountsAreIgnored() {
        assertNull(parser.parse(RawNotification(DefaultRules.SAMSUNG_WALLET, null, null, null, T0)))
        assertNull(parser.parse(RawNotification(DefaultRules.SAMSUNG_WALLET, "결제 완료", "0원 결제", null, T0)))
    }

    @Test
    fun invalidRulesAreRejectedAtConstruction() {
        val ids = SequentialIdGenerator()
        assertFailsWith<IllegalArgumentException> {
            RuleBasedParser(listOf(ParserRule(id = "bad", sourceApps = emptyList(), amountRegex = "([0-9", baseConfidence = 1f)), ids)
        }
        assertFailsWith<IllegalArgumentException> {
            RuleBasedParser(listOf(ParserRule(id = "conf", sourceApps = emptyList(), amountRegex = "(\\d+)", baseConfidence = 1.5f)), ids)
        }
    }

    @Test
    fun rulesDeserializeWithDefaults() {
        val json = """{"id":"r1","sourceApps":["a.app"],"amountRegex":"([0-9,]+)원","baseConfidence":0.9}"""
        val rule = Json.decodeFromString(ParserRule.serializer(), json)
        assertEquals(listOf("취소", "승인취소", "환불"), rule.refundKeywords)
        assertEquals(emptyList(), rule.ignoreKeywords)
        assertNull(rule.merchantRegex)
        DefaultRules.rules.forEach { r ->
            assertEquals(r, Json.decodeFromString(ParserRule.serializer(), Json.encodeToString(ParserRule.serializer(), r)))
        }
    }
}
