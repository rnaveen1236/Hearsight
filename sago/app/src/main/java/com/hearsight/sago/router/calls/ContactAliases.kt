package com.hearsight.sago.router.calls

import android.content.Context

/** Spoken nickname → contact name, e.g. "amma" → "Lakshmi". Edited on the setup screen. */
class ContactAliases(context: Context) {

    private val prefs = context.getSharedPreferences("contact_aliases", Context.MODE_PRIVATE)

    fun resolve(spoken: String): String = prefs.getString(key(spoken), null) ?: spoken

    fun put(alias: String, contactName: String) {
        prefs.edit().putString(key(alias), contactName.trim()).apply()
    }

    fun all(): Map<String, String> = prefs.all.mapValues { it.value.toString() }

    private fun key(alias: String) = alias.trim().lowercase()
}
