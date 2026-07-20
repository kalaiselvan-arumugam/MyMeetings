package com.example.mymeetings.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymeetings.domain.model.Meeting
import com.example.mymeetings.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: MeetingRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("mymeetings_settings", Context.MODE_PRIVATE)

    private val _defaultGuestName = MutableStateFlow(prefs.getString("default_guest_name", "") ?: "")
    val defaultGuestName: StateFlow<String> = _defaultGuestName.asStateFlow()

    private val _alarmRingtoneUri = MutableStateFlow(prefs.getString("alarm_ringtone_uri", "") ?: "")
    val alarmRingtoneUri: StateFlow<String> = _alarmRingtoneUri.asStateFlow()

    private val _silentModeBypass = MutableStateFlow(prefs.getBoolean("silent_mode_bypass", false))
    val silentModeBypass: StateFlow<Boolean> = _silentModeBypass.asStateFlow()

    private val _alarmVibrationEnabled = MutableStateFlow(prefs.getBoolean("alarm_vibration_enabled", true))
    val alarmVibrationEnabled: StateFlow<Boolean> = _alarmVibrationEnabled.asStateFlow()

    private val _alarmVibrationPattern = MutableStateFlow(prefs.getString("alarm_vibration_pattern", "Default") ?: "Default")
    val alarmVibrationPattern: StateFlow<String> = _alarmVibrationPattern.asStateFlow()

    fun updateDefaultGuestName(name: String) {
        _defaultGuestName.value = name
        prefs.edit().putString("default_guest_name", name).apply()
    }

    fun updateAlarmRingtoneUri(uri: String) {
        _alarmRingtoneUri.value = uri
        prefs.edit().putString("alarm_ringtone_uri", uri).apply()
    }

    fun updateSilentModeBypass(enabled: Boolean) {
        _silentModeBypass.value = enabled
        prefs.edit().putBoolean("silent_mode_bypass", enabled).apply()
    }

    fun updateAlarmVibrationEnabled(enabled: Boolean) {
        _alarmVibrationEnabled.value = enabled
        prefs.edit().putBoolean("alarm_vibration_enabled", enabled).apply()
    }

    fun updateAlarmVibrationPattern(pattern: String) {
        _alarmVibrationPattern.value = pattern
        prefs.edit().putString("alarm_vibration_pattern", pattern).apply()
    }

    /**
     * Serializes all meeting records from the database to a backup JSON string.
     */
    fun exportBackup(onExported: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val meetings = repository.getMeetingsList()
                val json = Json.encodeToString(meetings)
                onExported(json)
            } catch (e: Exception) {
                // Ignore or callback with empty
            }
        }
    }

    /**
     * Parses and imports meeting records from a backup JSON string, checking for duplicates.
     */
    fun importBackup(json: String, onImported: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val meetings = Json.decodeFromString<List<Meeting>>(json)
                for (meeting in meetings) {
                    val existing = repository.getMeetingByUid(meeting.uid)
                    if (existing == null) {
                        repository.insertMeeting(meeting.copy(id = 0L)) // Insert as new
                    } else {
                        repository.updateMeeting(meeting.copy(id = existing.id)) // Overwrite
                    }
                }
                onImported(true)
            } catch (e: Exception) {
                onImported(false)
            }
        }
    }
}
