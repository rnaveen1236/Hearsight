package com.hearsight.sago.router.apps

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.hearsight.sago.router.ActionOutcome

/**
 * Layer 1 (Intent) and layer 2 (deep link) of app control. Layer 3, Accessibility,
 * arrives on Day 3 for things like tapping the first YouTube result.
 */
class AppLauncher(private val context: Context, private val registry: InstalledAppRegistry) {

    suspend fun open(appName: String): ActionOutcome {
        val app = registry.find(appName) ?: return ActionOutcome("I couldn't find $appName on this phone.")
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return ActionOutcome("${app.label} can't be opened directly.")
        start(intent)
        return ActionOutcome("Opening ${app.label}.", closeOverlay = true)
    }

    suspend fun search(appName: String, query: String): ActionOutcome {
        if (query.isBlank()) return open(appName)
        val app = registry.find(appName) ?: return ActionOutcome("I couldn't find $appName on this phone.")

        // Generic search intent first; most apps with a search box accept it.
        val search = Intent(Intent.ACTION_SEARCH)
            .setPackage(app.packageName)
            .putExtra(SearchManager.QUERY, query)
        if (canHandle(search)) {
            start(search)
            return ActionOutcome("Searching ${app.label} for $query.", closeOverlay = true)
        }

        // Known deep links for apps that don't take ACTION_SEARCH.
        deepLink(app.packageName, query)?.let { uri ->
            val view = Intent(Intent.ACTION_VIEW, uri).setPackage(app.packageName)
            if (canHandle(view)) {
                start(view)
                return ActionOutcome("Searching ${app.label} for $query.", closeOverlay = true)
            }
        }
        return open(app.label)
    }

    private fun deepLink(pkg: String, query: String): Uri? = when (pkg) {
        YOUTUBE -> Uri.parse("https://www.youtube.com/results").buildUpon()
            .appendQueryParameter("search_query", query).build()
        SPOTIFY -> Uri.parse("spotify:search:" + Uri.encode(query))
        else -> null
    }

    private fun canHandle(intent: Intent) = intent.resolveActivity(context.packageManager) != null

    private fun start(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object {
        const val YOUTUBE = "com.google.android.youtube"
        const val SPOTIFY = "com.spotify.music"
    }
}
