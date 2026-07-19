package com.example.mymeetings.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Meeting(
    val id: Long = 0L,
    val uid: String,
    val title: String,
    val description: String? = null,
    val organizer: String? = null,
    val startTime: Long, // Epoch milliseconds
    val endTime: Long, // Epoch milliseconds
    val timeZone: String, // e.g. "UTC"
    val meetingUrl: String? = null,
    val meetingId: String? = null,
    val passcode: String? = null,
    val rrule: String? = null,
    val exdates: List<Long> = emptyList(), // Exclusion dates in milliseconds
    val enabled: Boolean = true,
    val reminderSettings: List<Int> = listOf(30, 15, 10, 5, 1, 0), // Default reminder offsets in minutes
    val guestName: String? = null,
    val color: Int? = null,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
    
    // History & Usage Statistics
    val creationDate: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val importedFrom: String = "QR", // "QR", "FILE", "SHARE"
    val lastNotificationTime: Long? = null,
    val lastOpenedTime: Long? = null,
    val timesJoined: Int = 0,
    val timesDismissed: Int = 0,
    val isMissed: Boolean = false
)
