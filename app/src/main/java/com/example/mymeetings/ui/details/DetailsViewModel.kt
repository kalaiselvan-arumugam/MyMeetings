package com.example.mymeetings.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymeetings.domain.model.Meeting
import com.example.mymeetings.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val repository: MeetingRepository
) : ViewModel() {

    private val _meeting = MutableStateFlow<Meeting?>(null)
    val meeting: StateFlow<Meeting?> = _meeting.asStateFlow()

    fun loadMeeting(id: Long) {
        viewModelScope.launch {
            val result = repository.getMeetingById(id)
            _meeting.value = result
        }
    }

    fun deleteMeeting(onFinished: () -> Unit) {
        val current = _meeting.value ?: return
        viewModelScope.launch {
            repository.deleteMeeting(current)
            onFinished()
        }
    }

    fun incrementJoinStats() {
        val current = _meeting.value ?: return
        viewModelScope.launch {
            val updated = current.copy(
                timesJoined = current.timesJoined + 1,
                lastOpenedTime = System.currentTimeMillis()
            )
            repository.updateMeeting(updated)
            _meeting.value = updated
        }
    }
}
