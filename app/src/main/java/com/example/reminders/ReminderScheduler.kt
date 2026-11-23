package com.example.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.example.data.ActivityRecord
import java.text.NumberFormat
import java.util.Locale

object ReminderScheduler {

    fun scheduleReminder(context: Context, record: ActivityRecord) {
        if (!record.reminderEnabled || record.reminderAtMillis <= System.currentTimeMillis()) {
            cancelReminder(context, record.id)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = buildReminderIntent(context, record)
        val pending = PendingIntent.getBroadcast(
            context,
            record.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            record.reminderAtMillis,
            pending
        )
    }

    fun cancelReminder(context: Context, activityId: String) {
        if (activityId.isBlank()) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            activityId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            alarmManager.cancel(pending)
            pending.cancel()
        }
    }

    fun showImmediatePreviewNotification(context: Context, record: ActivityRecord) {
        val intent = buildReminderIntent(context, record)
        ReminderReceiver().onReceive(context, intent)
    }

    private fun buildReminderIntent(context: Context, record: ActivityRecord): Intent {
        val formattedNeondra = NumberFormat.getNumberInstance(Locale.US).format(record.neondraAmount)
        val title = "${record.eventType}: ${record.name}" +
            if (record.familyId.isNotBlank()) " (FN ${record.familyId})" else ""
        val details = buildList {
            if (record.eventDetail.isNotBlank()) add(record.eventDetail)
            if (record.address.isNotBlank()) add("پتہ: ${record.address}")
            add("${record.parsedDirection.shortLabel} • نیوندرا: $formattedNeondra")
            if (record.homeItems.isNotBlank()) add(record.homeItems)
        }.joinToString("\n")

        return Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_ACTIVITY_ID, record.id)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_BODY, details)
        }
    }
}

object CalendarIntentHelper {
    fun buildInsertCalendarIntent(record: ActivityRecord): Intent {
        val formattedNeondra = NumberFormat.getNumberInstance(Locale.US).format(record.neondraAmount)
        val title = "${record.eventType} - ${record.name}" +
            if (record.familyId.isNotBlank()) " (FN ${record.familyId})" else ""
        val description = buildString {
            if (record.familyId.isNotBlank()) appendLine("FN: ${record.familyId}")
            if (record.fatherName.isNotBlank()) appendLine("Father: ${record.fatherName}")
            if (record.address.isNotBlank()) appendLine("Address: ${record.address}")
            if (record.eventDetail.isNotBlank()) appendLine("E-Detail: ${record.eventDetail}")
            appendLine("In/Out: ${record.parsedDirection.shortLabel}")
            if (record.homeItems.isNotBlank()) appendLine("Home: ${record.homeItems}")
            appendLine("Neondra: $formattedNeondra")
        }.trim()

        val beginMillis = record.eventDateMillis
        val endMillis = beginMillis + 60 * 60 * 1000L

        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            if (record.address.isNotBlank()) {
                putExtra(CalendarContract.Events.EVENT_LOCATION, record.address)
            }
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
        }
    }
}
