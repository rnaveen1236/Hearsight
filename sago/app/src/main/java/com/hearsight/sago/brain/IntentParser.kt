package com.hearsight.sago.brain

import org.json.JSONObject

/** Parses Gemma's reply; anything that isn't valid intent JSON is spoken as-is. */
internal object IntentParser {

    fun parse(raw: String): BrainResult {
        val json = extractJson(raw) ?: return fallback(raw)
        return when (json.optString("type").uppercase()) {
            "ACTION" -> {
                val action = json.optString("action").uppercase()
                if (action.isEmpty()) return fallback(raw)
                val params = json.optJSONObject("parameters")
                val map = buildMap {
                    params?.keys()?.forEach { key -> put(key, params.optString(key)) }
                }
                BrainResult.Action(action, map)
            }
            "ANSWER" -> BrainResult.Answer(json.optString("answer").ifBlank { fallbackText(raw) })
            else -> fallback(raw)
        }
    }

    private fun extractJson(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull()
    }

    private fun fallback(raw: String) = BrainResult.Answer(fallbackText(raw))

    private fun fallbackText(raw: String) =
        raw.replace(Regex("```(json)?"), "").trim().ifBlank { "Sorry, I didn't understand that." }
}
