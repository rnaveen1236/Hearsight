package com.hearsight.sago.router.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(val label: String, val packageName: String)

/**
 * Every launchable app on the phone, read live from PackageManager so newly
 * installed apps are found without any code change.
 */
class InstalledAppRegistry(private val context: Context) {

    suspend fun all(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
            .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
    }

    /** Best fuzzy match for a spoken app name ("vatsapp" → WhatsApp), or null. */
    suspend fun find(spokenName: String): InstalledApp? {
        val target = normalize(spokenName)
        if (target.isEmpty()) return null
        val apps = all()
        apps.firstOrNull { normalize(it.label) == target }?.let { return it }
        apps.filter { normalize(it.label).startsWith(target) || target.startsWith(normalize(it.label)) }
            .minByOrNull { it.label.length }?.let { return it }
        return apps
            .map { it to similarity(normalize(it.label), target) }
            .filter { it.second >= MIN_SIMILARITY }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun similarity(a: String, b: String): Double {
        val longest = maxOf(a.length, b.length)
        return if (longest == 0) 1.0 else 1.0 - levenshtein(a, b).toDouble() / longest
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

    private companion object {
        const val MIN_SIMILARITY = 0.6
    }
}
