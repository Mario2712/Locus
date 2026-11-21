package io.github.mario2712.locus.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mario2712.locus.MainActivity
import io.github.mario2712.locus.R
import javax.inject.Inject

class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(content: ReminderContent) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when (content) {
            is ReminderContent.OnTrip -> context.getString(R.string.reminder_text_trip, content.baseName)
            is ReminderContent.Empty -> content.label?.let { context.getString(R.string.reminder_text_suggestion, it) }
                ?: context.getString(R.string.reminder_text)
        }
        val title = when (content) {
            is ReminderContent.OnTrip -> context.getString(R.string.reminder_title_trip, content.baseName)
            is ReminderContent.Empty -> context.getString(R.string.reminder_title)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setAutoCancel(true)

        val actionIntent = when {
            content is ReminderContent.Empty && content.suggestion != null ->
                ReminderActionReceiver.ACTION_CONFIRM to R.string.reminder_action_confirm
            content is ReminderContent.OnTrip && content.canReturnHome ->
                ReminderActionReceiver.ACTION_RETURN_HOME to R.string.reminder_action_return_home
            else -> null
        }
        if (actionIntent != null) {
            val pendingAction = PendingIntent.getBroadcast(
                context, 1,
                Intent(context, ReminderActionReceiver::class.java).apply {
                    action = actionIntent.first
                    putExtra(ReminderActionReceiver.EXTRA_DATE, content.date.toString())
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, context.getString(actionIntent.second), pendingAction)
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    companion object {
        const val CHANNEL_ID = "daily_reminder"
        const val NOTIFICATION_ID = 1001
    }
}
