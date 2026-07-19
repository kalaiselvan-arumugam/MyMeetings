package com.example.mymeetings.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymeetings.domain.model.Meeting
import com.example.mymeetings.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MeetingRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val _selectedRecurrence = MutableStateFlow<String?>(null) // "one-time", "recurring", or null
    val selectedRecurrence: StateFlow<String?> = _selectedRecurrence.asStateFlow()

    // Combined UI state
    val uiState: StateFlow<HomeUiState> = combine(
        repository.getMeetings(),
        _searchQuery,
        _selectedTag,
        _selectedRecurrence
    ) { meetings, query, tag, recurrenceFilter ->
        val filtered = meetings.filter { meeting ->
            // Search query filter
            val matchesQuery = query.isBlank() || 
                meeting.title.contains(query, ignoreCase = true) ||
                meeting.organizer?.contains(query, ignoreCase = true) == true ||
                meeting.description?.contains(query, ignoreCase = true) == true ||
                meeting.tags.any { it.contains(query, ignoreCase = true) }

            // Tag filter
            val matchesTag = tag == null || meeting.tags.contains(tag)

            // Recurrence filter
            val isRecurring = !meeting.rrule.isNullOrBlank()
            val matchesRecurrence = recurrenceFilter == null || 
                (recurrenceFilter == "recurring" && isRecurring) ||
                (recurrenceFilter == "one-time" && !isRecurring)

            matchesQuery && matchesTag && matchesRecurrence
        }

        val allTags = meetings.flatMap { it.tags }.distinct().sorted()

        HomeUiState.Success(
            meetings = filtered,
            availableTags = allTags
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState.Loading
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectTag(tag: String?) {
        _selectedTag.value = tag
    }

    fun selectRecurrenceFilter(filter: String?) {
        _selectedRecurrence.value = filter
    }

    fun deleteMeeting(meeting: Meeting) {
        viewModelScope.launch {
            repository.deleteMeeting(meeting)
        }
    }

    fun toggleMeetingEnabled(meeting: Meeting) {
        viewModelScope.launch {
            repository.updateMeeting(meeting.copy(enabled = !meeting.enabled))
        }
    }
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
        val meetings: List<Meeting>,
        val availableTags: List<String>
    ) : HomeUiState
}
