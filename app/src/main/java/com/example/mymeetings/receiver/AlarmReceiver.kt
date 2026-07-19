package com.example.mymeetings.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.mymeetings.service.AlarmService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: AlarmService.ACTION_TRIGGER
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            this.action = action
            putExtras(intent)
        }
        
        // Start service to play sound and manage notification
        try {
            context.startService(serviceIntent)
        } catch (e: Exception) {
            // On Android 12+, background service start might fail if app is in background.
            // However, starting a service from a pending intent is permitted if it is an alarm trigger.
            // Fallback to startForegroundService if needed.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
