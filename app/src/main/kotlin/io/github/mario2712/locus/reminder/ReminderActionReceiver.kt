package io.github.mario2712.locus.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.mario2712.locus.domain.model.EntrySource
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.usecase.StayCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Aktion „Passt“ aus der Benachrichtigung: trägt den Vorschlag ein, ohne die App zu öffnen. */
class ReminderActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun stayCommands(): StayCommands
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CONFIRM) return
        val date = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse) ?: return
        val type = intent.getStringExtra(EXTRA_TYPE) ?: return
        val place = intent.getStringExtra(EXTRA_PLACE) ?: return

        val commands = EntryPointAccessors.fromApplication(context.applicationContext, ReceiverEntryPoint::class.java)
            .stayCommands()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                commands.checkIn(date, Defaults.ME, type, place, EntrySource.PATTERN)
                NotificationManagerCompat.from(context).cancel(ReminderNotifier.NOTIFICATION_ID)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CONFIRM = "io.github.mario2712.locus.action.CONFIRM_SUGGESTION"
        const val EXTRA_DATE = "date"
        const val EXTRA_TYPE = "type"
        const val EXTRA_PLACE = "place"
    }
}
