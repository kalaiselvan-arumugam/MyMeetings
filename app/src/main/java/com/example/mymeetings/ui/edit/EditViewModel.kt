package com.example.mymeetings.ui.edit

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
class EditViewModel @Inject constructor(
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

    fun updateMeeting(updated: Meeting, onFinished: () -> Unit) {
        viewModelScope.launch {
            repository.updateMeeting(updated)
            onFinished()
        }
    }
}
