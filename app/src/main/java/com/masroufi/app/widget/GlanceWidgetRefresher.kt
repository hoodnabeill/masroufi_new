package com.masroufi.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** Redraws every placed widget (a no-op when none is on the home screen). */
class GlanceWidgetRefresher(private val context: Context) : WidgetRefresher {
    override suspend fun update() {
        TicketWidget().updateAll(context)
        MatchWidget().updateAll(context)
        WideWidget().updateAll(context)
    }
}
