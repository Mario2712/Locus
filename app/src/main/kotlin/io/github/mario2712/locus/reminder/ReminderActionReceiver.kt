package io.github.mario2712.locus.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Aktionen aus der Benachrichtigung: Vorschlag übernehmen oder Heimreise eintragen. */
class ReminderActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun reminderActions(): ReminderActions
    }

    override fun onReceive(context: Context, intent: Intent) {
        val date = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse) ?: return
        val action = intent.action
        if (action != ACTION_CONFIRM && action != ACTION_RETURN_HOME) return

        val actions = EntryPointAccessors.fromApplication(context.applicationContext, ReceiverEntryPoint::class.java)
            .reminderActions()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (action == ACTION_CONFIRM) actions.confirm(date) else actions.returnHome(date)
                NotificationManagerCompat.from(context).cancel(ReminderNotifier.NOTIFICATION_ID)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CONFIRM = "io.github.mario2712.locus.action.CONFIRM_SUGGESTION"
        const val ACTION_RETURN_HOME = "io.github.mario2712.locus.action.RETURN_HOME"
        const val EXTRA_DATE = "date"
    }
}
