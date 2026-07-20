package com.example.mymeetings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import com.example.mymeetings.data.parser.IcsParser
import com.example.mymeetings.domain.repository.MeetingRepository
import com.example.mymeetings.theme.MyMeetingsTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var repository: MeetingRepository

    companion object {
        // Shared navigation target between MainActivity creation and MainNavigation
        var initialDestination: NavKey = Main
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var isProcessingIntent by mutableStateOf(false)

        val intentAction = intent?.action
        val intentData = intent?.data
        val intentType = intent?.type

        // 1. Check if launched from Quick Settings Tile (ACTION_SCAN_QR)
        if (intentAction == "com.example.mymeetings.action.SCAN_QR") {
            initialDestination = Scanner
        }
        // 2. Check if launched by sharing or viewing an ICS file
        else if (intentAction == Intent.ACTION_VIEW && intentData != null) {
            isProcessingIntent = true
            lifecycleScope.launch {
                val icsContent = readTextFromUri(intentData)
                importSharedIcs(icsContent) {
                    isProcessingIntent = false
                }
            }
        } else if (intentAction == Intent.ACTION_SEND && intentType != null) {
            isProcessingIntent = true
            lifecycleScope.launch {
                val icsContent = if (intentType == "text/plain" || intentType == "text/calendar") {
                    intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                } else {
                    val streamUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (streamUri != null) readTextFromUri(streamUri) else ""
                }
                importSharedIcs(icsContent) {
                    isProcessingIntent = false
                }
            }
        }

        setContent {
            MyMeetingsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isProcessingIntent) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        MainNavigation()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val prefs = getSharedPreferences("mymeetings_settings", Context.MODE_PRIVATE)
        val firstLaunchDone = prefs.getBoolean("first_launch_done", false)
        if (firstLaunchDone) return

        // 1. Post Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
                return
            }
        }

        // 2. Battery Optimization exemption dialog (Android 6.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                }
                return
            }
        }

        // 3. Draw Over Other Apps Overlay (Android 6.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                return
            }
        }

        // 4. Exact Alarms (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
                return
            }
        }

        // 5. Bypass Do Not Disturb (Android 6.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                startActivity(intent)
                return
            }
        }

        // Once all permissions have been requested/approved sequentially, mark first launch as completed
        prefs.edit().putBoolean("first_launch_done", true).apply()
    }

    /**
     * Parses the shared ICS text, checks for duplicates, saves to Room,
     * and sets the initial navigation screen to the meeting details.
     */
    private suspend fun importSharedIcs(icsContent: String, onComplete: () -> Unit) {
        if (icsContent.isNotBlank()) {
            val parsed = IcsParser.parseIcs(icsContent)
            if (parsed != null) {
                val existing = repository.getMeetingByUid(parsed.uid)
                val id = if (existing != null) {
                    // Update/overwrite existing
                    repository.updateMeeting(parsed.copy(id = existing.id))
                    existing.id
                } else {
                    // Save as new
                    repository.insertMeeting(parsed)
                }
                initialDestination = Details(id)
                Toast.makeText(this, "Event imported successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to parse shared meeting info", Toast.LENGTH_SHORT).show()
            }
        }
        onComplete()
    }

    private fun readTextFromUri(uri: Uri): String {
        val stringBuilder = StringBuilder()
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line).append("\n")
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return stringBuilder.toString()
    }
}
