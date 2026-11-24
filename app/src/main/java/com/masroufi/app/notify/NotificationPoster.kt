package com.masroufi.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.masroufi.app.MainActivity
import com.masroufi.app.R
import com.masroufi.app.core.inAppLanguage
import com.masroufi.app.domain.NotifKind
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

const val EXTRA_NOTIF_KIND = "notifKind"
const val EXTRA_NOTIF_ARG = "notifArg"

/** The channel's name (what the system settings list) for each kind. */
@StringRes
fun channelName(kind: NotifKind): Int = when (kind) {
    NotifKind.EVENING -> R.string.notif_ch_evening
    NotifKind.BILL -> R.string.notif_ch_bill
    NotifKind.GAMEYA_DUE -> R.string.notif_ch_gameya_due
    NotifKind.TRIAL_SUB -> R.string.notif_ch_trial_sub
    NotifKind.TRIAL_PLUS -> R.string.notif_ch_trial_plus
    NotifKind.LOW_BALANCE -> R.string.notif_ch_low
    NotifKind.STREAK -> R.string.notif_ch_streak
    NotifKind.GAMEYA_TURN -> R.string.notif_ch_gameya_turn
    NotifKind.OCCASION -> R.string.notif_ch_occasion
    NotifKind.WISH -> R.string.notif_ch_wish
    NotifKind.MORNING -> R.string.notif_ch_morning
}

/** The notification's one line; [alt] is the variant without an amount or second sentence. */
@StringRes
fun noticeText(kind: NotifKind, alt: Boolean): Int = when (kind) {
    NotifKind.EVENING -> if (alt) R.string.notif_evening_over else R.string.notif_evening
    NotifKind.BILL -> R.string.notif_bill
    NotifKind.GAMEYA_DUE -> R.string.notif_gameya_due
    NotifKind.TRIAL_SUB -> R.string.notif_trial_sub
    NotifKind.TRIAL_PLUS -> R.string.notif_trial_plus
    NotifKind.LOW_BALANCE -> R.string.notif_low
    NotifKind.STREAK -> R.string.notif_streak
    NotifKind.GAMEYA_TURN -> R.string.notif_gameya_turn
    NotifKind.OCCASION -> if (alt) R.string.notif_occasion_plain else R.string.notif_occasion
    NotifKind.WISH -> R.string.notif_wish
    NotifKind.MORNING -> R.string.notif_morning
}

fun channelId(kind: NotifKind) = "notif_${kind.name.lowercase()}"

/** Posts notices through the system: one channel per kind, a tap opens [MainActivity] with the target in the intent. */
@Singleton
class NotificationPoster @Inject constructor(@ApplicationContext private val app: Context) : NoticeSink {
    /** The app's language even before Android 13 hands it to the application context. */
    private fun localized(): Context = app.inAppLanguage()

    /** Creates (or renames, after a language switch) the channels; safe to call on every start. */
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ctx = localized()
        val manager = app.getSystemService(NotificationManager::class.java)
        NotifKind.entries.forEach {
            manager.createNotificationChannel(NotificationChannel(channelId(it), ctx.getString(channelName(it)), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    override fun canPost(): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(app).areNotificationsEnabled()

    @SuppressLint("MissingPermission") // canPost() checks it
    override fun post(notice: Notice): Boolean {
        if (!canPost()) return false
        ensureChannels()
        val ctx = localized()
        val tap = Intent(app, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_NOTIF_KIND, notice.kind.name)
            .also { i -> notice.arg?.let { i.putExtra(EXTRA_NOTIF_ARG, it) } }
        val id = notice.kind.ordinal + 1
        val text = ctx.getString(noticeText(notice.kind, notice.alt), *notice.args.toTypedArray())
        val n = NotificationCompat.Builder(app, channelId(notice.kind))
            .setSmallIcon(R.drawable.ic_stat_ticket)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(PendingIntent.getActivity(app, id, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        return try {
            NotificationManagerCompat.from(app).notify(id, n)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NotifyModule {
    @Binds abstract fun sink(poster: NotificationPoster): NoticeSink
}
