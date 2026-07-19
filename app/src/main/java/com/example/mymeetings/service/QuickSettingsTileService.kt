package com.example.mymeetings.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.example.mymeetings.MainActivity

class QuickSettingsTileService : TileService() {

    /**
     * Called when the user taps the quick settings tile.
     * Launches the MainActivity, routing directly to the QR Scanner screen.
     */
    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "com.example.mymeetings.action.SCAN_QR"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
