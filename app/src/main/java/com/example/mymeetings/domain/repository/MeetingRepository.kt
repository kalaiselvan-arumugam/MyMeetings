package com.example.mymeetings.domain.repository

import com.example.mymeetings.domain.model.Meeting
import kotlinx.coroutines.flow.Flow

interface MeetingRepository {
    fun getMeetings(): Flow<List<Meeting>>
    suspend fun getMeetingsList(): List<Meeting>
    suspend fun getMeetingById(id: Long): Meeting?
    suspend fun getMeetingByUid(uid: String): Meeting?
    suspend fun insertMeeting(meeting: Meeting): Long
    suspend fun updateMeeting(meeting: Meeting)
    suspend fun deleteMeeting(meeting: Meeting)
    suspend fun deleteMeetingById(id: Long)
}
