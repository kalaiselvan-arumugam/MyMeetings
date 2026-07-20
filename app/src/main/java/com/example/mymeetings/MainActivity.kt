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
                    val prefs = remember { getSharedPreferences("mymeetings_settings", android.content.Context.MODE_PRIVATE) }
                    var showSetupWizard by remember { mutableStateOf(!prefs.getBoolean("first_launch_done", false)) }

                    if (isProcessingIntent) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (showSetupWizard) {
                        com.example.mymeetings.ui.permissions.PermissionSetupWizard(
                            onSetupComplete = {
                                prefs.edit().putBoolean("first_launch_done", true).apply()
                                showSetupWizard = false
                            }
                        )
                    } else {
                        MainNavigation()
                    }
                }
            }
        }
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
