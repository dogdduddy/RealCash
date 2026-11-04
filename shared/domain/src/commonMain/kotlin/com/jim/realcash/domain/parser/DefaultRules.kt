package com.jim.realcash.domain.parser

/**
 * Built-in parser rules.
 *
 * WARNING: these are PLACEHOLDERS based on guessed notification formats. Real Samsung Wallet / Toss / card-app
 * notification texts have not been collected yet (Phase 0). Every rule must be replaced once samples exist.
 */
object DefaultRules {

    /** Amount written as `6,500원`, `6500원` or `₩6,500`. */
    const val AMOUNT_PATTERN: String = """₩\s?([0-9][0-9,]*)|([0-9][0-9,]*)\s?원"""

    /** Keywords of notifications that move money but are not purchases. */
    val NON_PAYMENT_KEYWORDS: List<String> = listOf("충전", "이체", "송금", "입금", "출금")

    /** Android package of Samsung Wallet. */
    const val SAMSUNG_WALLET: String = "com.samsung.android.spay"

    /** Android package of Toss. */
    const val TOSS: String = "viva.republica.toss"

    /** All built-in rules, most specific first. */
    val rules: List<ParserRule> = listOf(
        // TODO(Phase 0): placeholder. Guessed format: title "결제 완료", text "스타벅스 강남점에서 6,500원 결제".
        //  Replace amount/merchant patterns with ones derived from collected Samsung Wallet notifications.
        ParserRule(
            id = "samsung-wallet-v0",
            sourceApps = listOf(SAMSUNG_WALLET),
            amountRegex = AMOUNT_PATTERN,
            merchantRegex = """(?m)^(.+?)에서\s""",
            ignoreKeywords = NON_PAYMENT_KEYWORDS,
            baseConfidence = 1.0f,
        ),
        // TODO(Phase 0): placeholder. Guessed format: title "토스페이", text "6,500원 결제 | 스타벅스 강남점".
        //  Replace with patterns derived from collected Toss notifications (also check refund wording).
        ParserRule(
            id = "toss-v0",
            sourceApps = listOf(TOSS),
            amountRegex = AMOUNT_PATTERN,
            merchantRegex = """결제\s*[|·]\s*(.+)""",
            ignoreKeywords = NON_PAYMENT_KEYWORDS,
            baseConfidence = 1.0f,
        ),
        // TODO(Phase 0): placeholder. Guessed SMS-like card-app format:
        //  "신한카드(1234)승인 홍*동 6,500원(일시불)11/04 12:34 스타벅스 누적1,234,567원".
        //  The package names below are unverified guesses as well; confirm them on real devices.
        //  baseConfidence < 1 so that, until verified, these never auto-settle on their own.
        ParserRule(
            id = "card-apps-v0",
            sourceApps = listOf(
                "com.shcard.smartpay",
                "com.kbcard.cxh.appcard",
                "com.hyundaicard.appcard",
                "kr.co.samsungcard.mpocket",
            ),
            amountRegex = """승인(?:취소)?\s.*?([0-9][0-9,]*)\s?원""",
            merchantRegex = """(?m)\d{2}/\d{2}\s\d{2}:\d{2}\s(.+?)(?:\s누적|$)""",
            ignoreKeywords = NON_PAYMENT_KEYWORDS,
            baseConfidence = 0.9f,
        ),
    )
}
