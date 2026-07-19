package com.example.mymeetings.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.mymeetings.data.scheduler.MeetingAlarmScheduler
import com.example.mymeetings.domain.repository.MeetingRepository
import com.example.mymeetings.receiver.AlarmReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class AlarmService : Service() {

    @Inject
    lateinit var repository: MeetingRepository

    @Inject
    lateinit var alarmScheduler: MeetingAlarmScheduler

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var notificationManager: NotificationManager? = null

    companion object {
        const val ACTION_TRIGGER = "com.example.mymeetings.action.TRIGGER_ALARM"
        const val ACTION_JOIN = "com.example.mymeetings.action.JOIN_MEETING"
        const val ACTION_SNOOZE = "com.example.mymeetings.action.SNOOZE_MEETING"
        const val ACTION_DISMISS = "com.example.mymeetings.action.DISMISS_MEETING"

        private const val CHANNEL_ID = "meeting_alarms_channel"
        private const val NOTIFICATION_ID_BASE = 2000

        // In-memory static reference to stop sound reliably
        private var activeRingtone: Ringtone? = null
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_TRIGGER
        val meetingId = intent?.getLongExtra("MEETING_ID", -1L) ?: -1L
        val occurrenceTime = intent?.getLongExtra("OCCURRENCE_TIME", 0L) ?: 0L
        val offset = intent?.getIntExtra("OFFSET", 0) ?: 0
        val title = intent?.getStringExtra("TITLE") ?: "Meeting Reminder"

        if (meetingId == -1L) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_TRIGGER -> handleTrigger(meetingId, occurrenceTime, offset, title)
            ACTION_JOIN -> handleJoin(meetingId, occurrenceTime)
            ACTION_SNOOZE -> handleSnooze(meetingId, occurrenceTime, offset, title)
            ACTION_DISMISS -> handleDismiss(meetingId)
        }

        return START_NOT_STICKY
    }

    private fun handleTrigger(meetingId: Long, occurrenceTime: Long, offset: Int, title: String) {
        // Play Alarm Sound
        playAlarmSound()

        // Build notification
        val notification = buildAlarmNotification(meetingId, occurrenceTime, offset, title)

        // Run as foreground service to ensure it rings reliably in background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID_BASE + meetingId.toInt(), notification)
        } else {
            startForeground(NOTIFICATION_ID_BASE + meetingId.toInt(), notification)
        }
    }

    private fun handleJoin(meetingId: Long, occurrenceTime: Long) {
        stopAlarmSound()
        
        serviceScope.launch {
            val meeting = repository.getMeetingById(meetingId)
            if (meeting != null) {
                // Update history stats
                val updated = meeting.copy(
                    timesJoined = meeting.timesJoined + 1,
                    lastNotificationTime = System.currentTimeMillis()
                )
                repository.updateMeeting(updated)

                // Launch deep link if available
                val url = meeting.meetingUrl
                if (!url.isNullOrBlank()) {
                    try {
                        val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(launchIntent)
                    } catch (e: Exception) {
                        Toast.makeText(applicationContext, "Microsoft Teams is not installed. Copying link.", Toast.LENGTH_LONG).show()
                        copyToClipboard(meeting.title, url)
                    }
                } else {
                    Toast.makeText(applicationContext, "No URL found. Copying credentials.", Toast.LENGTH_SHORT).show()
                    val copyText = "Meeting ID: ${meeting.meetingId}\nPasscode: ${meeting.passcode}"
                    copyToClipboard(meeting.title, copyText)
                }

                // Reschedule for next occurrence
                alarmScheduler.scheduleAlarmsForMeeting(updated)
            }
            stopForeground(true)
            stopSelf()
        }
    }

    private fun handleSnooze(meetingId: Long, occurrenceTime: Long, offset: Int, title: String) {
        stopAlarmSound()

        val snoozeMinutes = getSnoozeDurationFromPrefs()
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val triggerTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000)

        // Setup intent to trigger AlarmReceiver again in 5/10 mins
        val intent = Intent(applicationContext, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER
            putExtra("MEETING_ID", meetingId)
            putExtra("OCCURRENCE_TIME", occurrenceTime)
            putExtra("OFFSET", offset)
            putExtra("TITLE", "$title (Snoozed)")
        }

        // Use a unique request code for snooze alarms
        val requestCode = (meetingId.hashCode() * 31) + 9999
        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }

        Toast.makeText(applicationContext, "Snoozed for $snoozeMinutes minutes", Toast.LENGTH_SHORT).show()

        stopForeground(true)
        stopSelf()
    }

    private fun handleDismiss(meetingId: Long) {
        stopAlarmSound()

        serviceScope.launch {
            val meeting = repository.getMeetingById(meetingId)
            if (meeting != null) {
                // Update history stats
                val updated = meeting.copy(
                    timesDismissed = meeting.timesDismissed + 1,
                    lastNotificationTime = System.currentTimeMillis()
                )
                repository.updateMeeting(updated)

                // Reschedule for next occurrence
                alarmScheduler.scheduleAlarmsForMeeting(updated)
            }
            stopForeground(true)
            stopSelf()
        }
    }

    private fun playAlarmSound() {
        stopAlarmSound() // Ensure no overlapping sounds

        val uriString = getRingtoneUriFromPrefs()
        val uri = if (uriString.isNullOrEmpty()) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        } else {
            Uri.parse(uriString)
        }

        try {
            val ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.isLooping = true
            }
            ringtone.play()
            activeRingtone = ringtone
        } catch (e: Exception) {
            // Fallback to notification sound
            try {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(applicationContext, fallbackUri)
                ringtone.play()
                activeRingtone = ringtone
            } catch (ex: Exception) {
                // Audio unavailable
            }
        }
    }

    private fun stopAlarmSound() {
        try {
            activeRingtone?.stop()
            activeRingtone = null
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun buildAlarmNotification(meetingId: Long, occurrenceTime: Long, offset: Int, title: String): Notification {
        val formatTime = DateTimeFormatter.ofPattern("hh:mm a")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(occurrenceTime))

        val text = if (offset == 0) "Meeting is starting now! ($formatTime)" else "Starting in $offset minutes ($formatTime)"

        // Notification Action PendingIntents
        val joinIntent = PendingIntent.getBroadcast(
            this,
            (meetingId.hashCode() * 31) + 101,
            Intent(this, AlarmReceiver::class.java).apply {
                action = ACTION_JOIN
                putExtra("MEETING_ID", meetingId)
                putExtra("OCCURRENCE_TIME", occurrenceTime)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = PendingIntent.getBroadcast(
            this,
            (meetingId.hashCode() * 31) + 102,
            Intent(this, AlarmReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra("MEETING_ID", meetingId)
                putExtra("OCCURRENCE_TIME", occurrenceTime)
                putExtra("OFFSET", offset)
                putExtra("TITLE", title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = PendingIntent.getBroadcast(
            this,
            (meetingId.hashCode() * 31) + 103,
            Intent(this, AlarmReceiver::class.java).apply {
                action = ACTION_DISMISS
                putExtra("MEETING_ID", meetingId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_call, "Join Teams", joinIntent)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze", snoozeIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Meeting Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority alarms for online meetings"
                enableVibration(true)
                // Set audio attributes for alarm sound
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                setSound(null, audioAttributes) // Sound is managed manually by Ringtone/MediaPlayer
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    private fun getRingtoneUriFromPrefs(): String? {
        val prefs = getSharedPreferences("mymeetings_settings", Context.MODE_PRIVATE)
        return prefs.getString("alarm_ringtone_uri", null)
    }

    private fun getSnoozeDurationFromPrefs(): Int {
        val prefs = getSharedPreferences("mymeetings_settings", Context.MODE_PRIVATE)
        return prefs.getInt("snooze_duration_minutes", 5)
    }

    override fun onDestroy() {
        stopAlarmSound()
        serviceJob.cancel()
        super.onDestroy()
    }
}
