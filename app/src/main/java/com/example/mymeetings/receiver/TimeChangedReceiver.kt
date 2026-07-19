package com.example.mymeetings.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.mymeetings.data.scheduler.MeetingAlarmScheduler
import com.example.mymeetings.domain.repository.MeetingRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TimeChangedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: MeetingRepository

    @Inject
    lateinit var alarmScheduler: MeetingAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val meetings = repository.getMeetingsList()
                    for (meeting in meetings) {
                        if (meeting.enabled) {
                            alarmScheduler.scheduleAlarmsForMeeting(meeting)
                        }
                    }
                } catch (e: Exception) {
                    // Graceful handling
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
