package com.example.mymeetings.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymeetings.domain.model.Meeting
import com.example.mymeetings.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val repository: MeetingRepository
) : ViewModel() {

    private val _scannerState = MutableStateFlow<ScannerUiState>(ScannerUiState.Idle)
    val scannerState: StateFlow<ScannerUiState> = _scannerState.asStateFlow()

    fun resetState() {
        _scannerState.value = ScannerUiState.Idle
    }

    /**
     * Checks if a meeting with the same UID already exists.
     * If it exists, transitions state to DuplicateFound to trigger the UI dialog.
     * Otherwise, saves the meeting directly.
     */
    fun processScannedMeeting(meeting: Meeting, onFinished: (Long) -> Unit) {
        viewModelScope.launch {
            _scannerState.value = ScannerUiState.Processing
            val existing = repository.getMeetingByUid(meeting.uid)
            if (existing != null) {
                _scannerState.value = ScannerUiState.DuplicateFound(meeting, existing)
            } else {
                saveMeeting(meeting, onFinished)
            }
        }
    }

    fun saveMeeting(meeting: Meeting, onFinished: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.insertMeeting(meeting)
            _scannerState.value = ScannerUiState.Success(id)
            onFinished(id)
        }
    }

    fun overwriteExisting(scanned: Meeting, existingId: Long, onFinished: (Long) -> Unit) {
        viewModelScope.launch {
            // Keep the database autoincrement ID of the existing row
            val toSave = scanned.copy(id = existingId)
            repository.updateMeeting(toSave)
            _scannerState.value = ScannerUiState.Success(existingId)
            onFinished(existingId)
        }
    }

    fun keepBoth(scanned: Meeting, onFinished: (Long) -> Unit) {
        viewModelScope.launch {
            // Generate a fresh UID and reset ID
            val toSave = scanned.copy(
                id = 0L,
                uid = UUID.randomUUID().toString(),
                title = "${scanned.title} (Copy)"
            )
            val id = repository.insertMeeting(toSave)
            _scannerState.value = ScannerUiState.Success(id)
            onFinished(id)
        }
    }

    fun setInvalidQrError() {
        _scannerState.value = ScannerUiState.Error("Invalid QR code or ICS file format.")
    }
}

sealed interface ScannerUiState {
    data object Idle : ScannerUiState
    data object Processing : ScannerUiState
    data class DuplicateFound(val scannedMeeting: Meeting, val existingMeeting: Meeting) : ScannerUiState
    data class Success(val meetingId: Long) : ScannerUiState
    data class Error(val message: String) : ScannerUiState
}
