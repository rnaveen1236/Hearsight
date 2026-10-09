package com.hearsight.sago.router.calls

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.hearsight.sago.router.ActionOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** "Amma-ku call pannu" → alias → Contacts Provider → ACTION_CALL. */
class CallManager(private val context: Context) {

    private val aliases = ContactAliases(context)

    suspend fun call(spokenName: String): ActionOutcome {
        if (spokenName.isBlank()) return ActionOutcome("Who should I call?")
        if (!granted(Manifest.permission.READ_CONTACTS) || !granted(Manifest.permission.CALL_PHONE)) {
            return ActionOutcome("Open the Sago app and allow contacts and phone access.")
        }

        val name = aliases.resolve(spokenName)
        val contact = findContact(name) ?: return ActionOutcome("I couldn't find $spokenName in your contacts.")

        context.startActivity(
            Intent(Intent.ACTION_CALL, Uri.fromParts("tel", contact.number, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return ActionOutcome("Calling $spokenName.", closeOverlay = true)
    }

    private data class Contact(val name: String, val number: String)

    /** Exact name first, then the shortest name containing the spoken one. */
    private suspend fun findContact(name: String): Contact? = withContext(Dispatchers.IO) {
        val matches = mutableListOf<Contact>()
        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.IS_PRIMARY),
            "${Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            "${Phone.IS_PRIMARY} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val display = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                matches += Contact(display, number)
            }
        }
        matches.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: matches.minByOrNull { it.name.length }
    }

    private fun granted(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
