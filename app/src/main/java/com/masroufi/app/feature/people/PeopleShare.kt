package com.masroufi.app.feature.people

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.masroufi.app.R
import com.masroufi.app.core.formatPounds

/** The polite message (decision 6): plain, naming the person and the debt, nothing else. */
fun reminderText(context: Context, name: String, owed: Long): String =
    context.getString(R.string.people_remind_text, name, formatPounds(owed))

fun reminderIntent(context: Context, name: String, owed: Long): Intent {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, reminderText(context, name, owed))
    return Intent.createChooser(send, context.getString(R.string.people_act_remind))
}

/**
 * Opens the system share sheet with the reminder; [onReturned] runs when the sheet closes (Android does not say whether
 * the user sent anything, so the feedback line says it for them).
 */
@Composable
fun rememberReminder(onReturned: () -> Unit): (name: String, owed: Long) -> Unit {
    val context = LocalContext.current
    val returned = rememberUpdatedState(onReturned)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { returned.value() }
    return { name, owed ->
        try {
            launcher.launch(reminderIntent(context, name, owed))
        } catch (e: android.content.ActivityNotFoundException) {
            // no app can share text: nothing to send
        }
    }
}
