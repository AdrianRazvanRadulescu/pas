package dev.pas.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The user swiped today's time out of the shade: the worker leaves it out until tomorrow. */
class ScreenTimeDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CoachStore(context).isScreenTimeShadeDismissed = true
    }
}
