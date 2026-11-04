package com.jim.realcash.data

import com.jim.realcash.domain.parser.DefaultRules
import com.jim.realcash.domain.parser.ParserRule
import com.jim.realcash.domain.parser.RuleBasedParser
import com.jim.realcash.domain.util.IdGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A rule from an override that was not applied. */
data class SkippedRule(
    /** The rule's id, or `#<index>` when the entry has no readable id. */
    val id: String,
    /** Why it was skipped. */
    val reason: String,
)

/** Outcome of validating a parser-rule override. */
data class RuleOverrideReport(
    /** Whether the override is in effect (stored and its valid rules active). */
    val accepted: Boolean,
    /** Ids of the rules now active from the override (empty when not [accepted]). */
    val appliedRuleIds: List<String>,
    /** Rules of the override that were invalid and left out. */
    val skipped: List<SkippedRule>,
    /** Why the whole override was refused (malformed JSON, no valid rule), or `null`. */
    val error: String? = null,
)

/**
 * The parser rules in effect: the bundled [DefaultRules.rules], unless a valid override is stored.
 *
 * An override is a JSON array of [ParserRule] objects (the same shape remote config will deliver). Each rule is
 * validated on its own, on this device (regex dialects differ between JVM and iOS), and invalid rules are skipped.
 * An override that is not a JSON array, or has no valid rule at all, is refused and the previous rules stay
 * active, so a bad override can never leave the app without rules.
 *
 * [rules] holds the bundled rules until [load] (or [applyOverride] / [clearOverride]) has run.
 */
class ParserRulesRepository internal constructor(
    private val store: SettingsStore,
    private val bundled: List<ParserRule> = DefaultRules.rules,
    private val json: Json = DataJson,
) {
    private val mutex = Mutex()
    private val _rules = MutableStateFlow(bundled)
    private val _lastReport = MutableStateFlow<RuleOverrideReport?>(null)
    private var loaded = false

    /** Rules currently in effect. Never empty as long as the bundled rules are not. */
    val rules: StateFlow<List<ParserRule>> = _rules.asStateFlow()

    /** Report of the override validated most recently (on [load] or [applyOverride]); `null` without override. */
    val lastReport: StateFlow<RuleOverrideReport?> = _lastReport.asStateFlow()

    /** Loads (and re-validates) the stored override if that has not happened yet; returns the active rules. */
    suspend fun load(): List<ParserRule> = mutex.withLock { ensureLoaded() }

    /**
     * Validates [json] and, if it contains at least one valid rule, stores it and activates its valid rules
     * (replacing the bundled ones). Otherwise nothing changes. Returns the validation report either way.
     */
    suspend fun applyOverride(json: String): RuleOverrideReport = mutex.withLock {
        ensureLoaded()
        val (valid, report) = validate(json)
        if (report.accepted) {
            store.put(KEY_OVERRIDE, json)
            _rules.value = valid
        }
        _lastReport.value = report
        report
    }

    /** Removes the stored override and restores the bundled rules. */
    suspend fun clearOverride() = mutex.withLock {
        store.put(KEY_OVERRIDE, null)
        _rules.value = bundled
        _lastReport.value = null
        loaded = true
    }

    /** Must be called with [mutex] held. */
    private suspend fun ensureLoaded(): List<ParserRule> {
        if (!loaded) {
            val stored = store.get(KEY_OVERRIDE)
            if (stored != null) {
                val (valid, report) = validate(stored)
                // A stored override that no longer validates (e.g. regex unsupported on this platform) is ignored.
                _rules.value = if (report.accepted) valid else bundled
                _lastReport.value = report
            }
            loaded = true
        }
        return _rules.value
    }

    private fun validate(text: String): Pair<List<ParserRule>, RuleOverrideReport> {
        val array = try {
            json.parseToJsonElement(text) as? JsonArray
        } catch (e: Exception) {
            null
        } ?: return emptyList<ParserRule>() to refused("override is not a JSON array of rules")

        val valid = mutableListOf<ParserRule>()
        val skipped = mutableListOf<SkippedRule>()
        array.forEachIndexed { index, element ->
            val label = readableId(element) ?: "#$index"
            val rule = try {
                json.decodeFromJsonElement(ParserRule.serializer(), element)
            } catch (e: Exception) {
                skipped += SkippedRule(label, "not a valid rule object: ${e.message?.lineSequence()?.firstOrNull()}")
                return@forEachIndexed
            }
            val problem = problemOf(rule, valid)
            if (problem == null) valid += rule else skipped += SkippedRule(label, problem)
        }
        if (valid.isEmpty()) return emptyList<ParserRule>() to refused("override contains no valid rule", skipped)
        return valid to RuleOverrideReport(accepted = true, appliedRuleIds = valid.map { it.id }, skipped = skipped)
    }

    /** Why [rule] cannot be used, or `null` if it is fine. Regex/confidence checks reuse [RuleBasedParser]. */
    private fun problemOf(rule: ParserRule, accepted: List<ParserRule>): String? {
        if (rule.id.isBlank()) return "blank id"
        if (accepted.any { it.id == rule.id }) return "duplicate id"
        if (rule.amountRegex.isBlank()) return "blank amountRegex"
        return try {
            RuleBasedParser(listOf(rule), NO_IDS)
            null
        } catch (e: Exception) {
            e.message ?: e::class.simpleName ?: "invalid rule"
        }
    }

    private fun readableId(element: JsonElement): String? =
        ((element as? JsonObject)?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun refused(error: String, skipped: List<SkippedRule> = emptyList()) =
        RuleOverrideReport(accepted = false, appliedRuleIds = emptyList(), skipped = skipped, error = error)

    internal companion object {
        /** `app_setting` key holding the raw override JSON. */
        const val KEY_OVERRIDE = "parser.rules_override"

        /** Validation never parses anything, so it never needs real ids. */
        private val NO_IDS = IdGenerator { "" }
    }
}
