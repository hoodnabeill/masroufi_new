package com.masroufi.app.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.masroufi.app.R

/** A person picked from the phone's contacts: their name and one number. */
data class PickedContact(val name: String, val phone: String)

/**
 * Opens the system contact picker for a phone number. The picker grants the app that one number only, so no
 * READ_CONTACTS permission is needed and the address book is never read.
 */
private object PickPhone : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit) = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

@Composable
fun rememberContactPicker(onPicked: (PickedContact) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(PickPhone) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val columns = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        context.contentResolver.query(uri, columns, null, null, null)?.use { c ->
            if (c.moveToFirst()) onPicked(PickedContact(c.getString(0).orEmpty(), c.getString(1).orEmpty()))
        }
    }
    return {
        try {
            launcher.launch(Unit)
        } catch (e: ActivityNotFoundException) {
            // No contacts app: the number can still be typed.
        }
    }
}

object Phones {
    /** Digits for wa.me in international form; Egyptian local numbers (01xxxxxxxxx) get the 20 country code. */
    fun international(phone: String): String {
        val digits = phone.filter { it.isDigit() || it == '+' }.let { com.masroufi.app.core.Money.normalizeDigits(it) }
        return when {
            digits.startsWith("+") -> digits.drop(1)
            digits.startsWith("00") -> digits.drop(2)
            digits.startsWith("01") && digits.length == 11 -> "20" + digits.drop(1)
            else -> digits
        }.filter(Char::isDigit)
    }

    /** WhatsApp with [text] ready, to [phone] when known, otherwise the user picks the chat. Nothing is sent by itself. */
    fun whatsapp(context: Context, phone: String?, text: String = ""): Boolean {
        val to = phone?.let(::international)?.takeIf { it.length >= 8 }.orEmpty()
        val uri = Uri.parse("https://wa.me/$to" + if (text.isNotEmpty()) "?text=" + Uri.encode(text) else "")
        return start(context, Intent(Intent.ACTION_VIEW, uri))
    }

    /** The dialer with the number filled in; the user presses call (no CALL_PHONE permission). */
    fun call(context: Context, phone: String): Boolean = start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/** Call and WhatsApp side by side, for a person with a saved number. */
@Composable
fun ContactActions(phone: String, modifier: Modifier = Modifier, message: String = "") {
    val context = LocalContext.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassSecondaryButton(onClick = { Phones.call(context, phone) }, modifier = Modifier.weight(1f)) {
            Icon(MIcons.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.contact_call))
        }
        GlassButton(onClick = { Phones.whatsapp(context, phone, message) }, modifier = Modifier.weight(1f)) {
            Icon(MIcons.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.contact_whatsapp))
        }
    }
}
