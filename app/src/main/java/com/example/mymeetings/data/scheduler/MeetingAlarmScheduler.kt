package com.example.mymeetings.data.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.mymeetings.data.parser.RruleEvaluator
import com.example.mymeetings.domain.model.Meeting
import com.example.mymeetings.receiver.AlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeetingAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedules reminders for the next upcoming occurrence of the meeting.
     */
    fun scheduleAlarmsForMeeting(meeting: Meeting) {
        // First cancel any existing alarms for this meeting
        cancelAlarmsForMeeting(meeting)

        if (!meeting.enabled) return

        // Contextual checks for required alarm execution permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.widget.Toast.makeText(context, "Notification permission is required to show meeting alerts. Please enable them.", android.widget.Toast.LENGTH_LONG).show()
                try {
                    val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                android.widget.Toast.makeText(context, "Exact alarm permission is required to schedule meeting alerts. Please grant it.", android.widget.Toast.LENGTH_LONG).show()
                try {
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (ex: Exception) {
                        // Ignore
                    }
                }
            }
        }

        // Calculate next occurrence starting from current time
        val now = System.currentTimeMillis()
        val oneYearFromNow = now + 365L * 24 * 60 * 60 * 1000
        val occurrences = RruleEvaluator.getOccurrences(meeting, now, oneYearFromNow)
        
        // Find the next occurrence where at least one reminder is in the future
        val nextOccurrence = occurrences.firstOrNull { occurrenceTime ->
            meeting.reminderSettings.any { offset ->
                val triggerTime = occurrenceTime - (offset * 60 * 1000)
                triggerTime > now
            }
        } ?: return

        // Schedule alarms for future reminders of the next occurrence
        for (offset in meeting.reminderSettings) {
            val triggerTime = nextOccurrence - (offset * 60 * 1000)
            if (triggerTime <= now) continue // Skip past reminders

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("MEETING_ID", meeting.id)
                putExtra("OCCURRENCE_TIME", nextOccurrence)
                putExtra("OFFSET", offset)
                putExtra("TITLE", meeting.title)
            }

            val requestCode = getRequestCode(meeting.id, offset)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        }
    }

    /**
     * Cancels all scheduled reminders for a meeting across all possible offsets.
     */
    fun cancelAlarmsForMeeting(meeting: Meeting) {
        for (offset in meeting.reminderSettings) {
            val intent = Intent(context, AlarmReceiver::class.java)
            val requestCode = getRequestCode(meeting.id, offset)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    /**
     * Generates a unique request code for each meeting-offset combination.
     */
    private fun getRequestCode(meetingId: Long, offset: Int): Int {
        return (meetingId.hashCode() * 31) + offset
    }
}
