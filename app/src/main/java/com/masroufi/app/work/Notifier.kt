package com.masroufi.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.masroufi.app.MainActivity
import com.masroufi.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notifications never show amounts or names on the lock screen: the public version only says there is a
 * Masroufi reminder, and the full text is visible after unlocking the device.
 */
@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT).apply {
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            },
        )
    }

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(id: Int, title: String, body: String) {
        if (!canNotify()) return
        val intent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_private))
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
    }
}
