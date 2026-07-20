package com.example.mymeetings.ui.settings

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val defaultGuestName by viewModel.defaultGuestName.collectAsStateWithLifecycle()
    val ringtoneUri by viewModel.alarmRingtoneUri.collectAsStateWithLifecycle()
    val silentModeBypass by viewModel.silentModeBypass.collectAsStateWithLifecycle()
    val alarmVibrationEnabled by viewModel.alarmVibrationEnabled.collectAsStateWithLifecycle()
    val alarmVibrationPattern by viewModel.alarmVibrationPattern.collectAsStateWithLifecycle()

    var showVibrationPatternMenu by remember { mutableStateOf(false) }

    // Ringtone Picker Launcher
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                viewModel.updateAlarmRingtoneUri(uri.toString())
                Toast.makeText(context, "Alarm ringtone updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // JSON file picker launcher for RESTORE
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val json = readTextFromUri(context, uri)
            viewModel.importBackup(json) { success ->
                if (success) {
                    Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Failed to restore backup. Invalid file format.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category: Personalization
            Text("Personalization", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = defaultGuestName,
                onValueChange = { viewModel.updateDefaultGuestName(it) },
                label = { Text("Default Guest Name") },
                placeholder = { Text("Used for auto-copying guest credentials") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Divider()

            // Category: Alarm Options
            Text("Alarms & Notification Sounds", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            
            ListItem(
                headlineContent = { Text("Alarm Ringtone") },
                supportingContent = { Text(getRingtoneTitle(context, ringtoneUri)) },
                trailingContent = { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.clickable {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                        if (ringtoneUri.isNotBlank()) {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(ringtoneUri))
                        }
                    }
                    ringtonePickerLauncher.launch(intent)
                }
            )

            // Silent Mode Bypass Switch
            ListItem(
                headlineContent = { Text("Bypass Silent / DND Mode") },
                supportingContent = { Text("Play meeting alert ringtones even when phone is set to silent or Do Not Disturb.") },
                trailingContent = {
                    Switch(
                        checked = silentModeBypass,
                        onCheckedChange = { viewModel.updateSilentModeBypass(it) }
                    )
                }
            )

            // Alarm Vibration Switch
            ListItem(
                headlineContent = { Text("Alarm Vibration") },
                supportingContent = { Text("Vibrate phone during meeting alerts.") },
                trailingContent = {
                    Switch(
                        checked = alarmVibrationEnabled,
                        onCheckedChange = { viewModel.updateAlarmVibrationEnabled(it) }
                    )
                }
            )

            // Vibration Pattern Selection (visible if vibration is enabled)
            if (alarmVibrationEnabled) {
                Box {
                    ListItem(
                        headlineContent = { Text("Vibration Pattern") },
                        supportingContent = { Text(alarmVibrationPattern) },
                        trailingContent = { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null) },
                        modifier = Modifier.clickable { showVibrationPatternMenu = true }
                    )
                    DropdownMenu(
                        expanded = showVibrationPatternMenu,
                        onDismissRequest = { showVibrationPatternMenu = false }
                    ) {
                        listOf("Default", "Heartbeat", "Fast").forEach { pattern ->
                            DropdownMenuItem(
                                text = { Text(pattern) },
                                onClick = {
                                    viewModel.updateAlarmVibrationPattern(pattern)
                                    showVibrationPatternMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Divider()

            // Category: Backups
            Text("Data Backup & Recovery", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            
            ListItem(
                headlineContent = { Text("Export Backup") },
                supportingContent = { Text("Save meeting entries to a JSON text backup file") },
                trailingContent = { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.clickable {
                    viewModel.exportBackup { json ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_TEXT, json)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Save Backup"))
                    }
                }
            )

            ListItem(
                headlineContent = { Text("Import Backup") },
                supportingContent = { Text("Restore meetings from an exported JSON file") },
                trailingContent = { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.clickable {
                    importLauncher.launch("*/*")
                }
            )

            Divider()

            // Category: Privacy
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "All meeting data, calendar files, and QR scanning operations remain strictly local on your device. No cloud integrations or analytics trackers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true
    }
}

private fun getRingtoneTitle(context: Context, uriString: String?): String {
    if (uriString.isNullOrBlank()) return "Default alarm sound"
    return try {
        val uri = Uri.parse(uriString)
        RingtoneManager.getRingtone(context, uri).getTitle(context) ?: "Custom alarm sound"
    } catch (e: Exception) {
        "Custom alarm sound"
    }
}

private fun readTextFromUri(context: Context, uri: Uri): String {
    val stringBuilder = StringBuilder()
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
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
