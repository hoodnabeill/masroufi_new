package com.masroufi.app.feature.profile

import android.content.Context
import android.content.Intent
import android.os.Process
import com.masroufi.app.MainActivity
import com.masroufi.app.R

/** The share card (no amounts, decision 7): the name, the level, and counts only. */
fun profileShareText(context: Context, ui: ProfileUi): String {
    val head = if (ui.name.isBlank()) {
        context.getString(R.string.profile_share_head, ui.level.toString(), ui.levelName)
    } else {
        context.getString(R.string.profile_share_head_named, ui.name.trim(), ui.level.toString(), ui.levelName)
    }
    val stats = context.getString(
        R.string.profile_share_stats, ui.streak.toString(), ui.proverbs.toString(), ui.wonOf.toString(), ui.ofLast.toString(),
    )
    return "$head\n$stats\n${context.getString(R.string.app_name)}"
}

/**
 * After "delete all my data": a fresh launch in a cleared task, then the process ends, so no singleton (pending punches
 * and deletes, the feedback bus, cached repository state) survives into the empty app. Not verified on a device.
 */
fun restartApp(context: Context) {
    val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Process.killProcess(Process.myPid())
}
