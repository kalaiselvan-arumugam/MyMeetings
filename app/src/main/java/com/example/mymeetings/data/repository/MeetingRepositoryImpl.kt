package com.example.mymeetings.data.repository

import com.example.mymeetings.data.local.MeetingDao
import com.example.mymeetings.data.local.MeetingEntity
import com.example.mymeetings.data.scheduler.MeetingAlarmScheduler
import com.example.mymeetings.domain.model.Meeting
import com.example.mymeetings.domain.repository.MeetingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeetingRepositoryImpl @Inject constructor(
    private val meetingDao: MeetingDao,
    private val alarmScheduler: MeetingAlarmScheduler
) : MeetingRepository {

    override fun getMeetings(): Flow<List<Meeting>> {
        return meetingDao.getMeetingsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getMeetingsList(): List<Meeting> = withContext(Dispatchers.IO) {
        meetingDao.getMeetingsList().map { it.toDomain() }
    }

    override suspend fun getMeetingById(id: Long): Meeting? = withContext(Dispatchers.IO) {
        meetingDao.getMeetingById(id)?.toDomain()
    }

    override suspend fun getMeetingByUid(uid: String): Meeting? = withContext(Dispatchers.IO) {
        meetingDao.getMeetingByUid(uid)?.toDomain()
    }

    override suspend fun insertMeeting(meeting: Meeting): Long = withContext(Dispatchers.IO) {
        val entity = MeetingEntity.fromDomain(meeting)
        val id = meetingDao.insert(entity)
        
        // Re-schedule alarms with the generated autoincrement ID
        val savedMeeting = meeting.copy(id = id)
        alarmScheduler.scheduleAlarmsForMeeting(savedMeeting)
        id
    }

    override suspend fun updateMeeting(meeting: Meeting) = withContext(Dispatchers.IO) {
        val entity = MeetingEntity.fromDomain(meeting)
        meetingDao.update(entity)
        alarmScheduler.scheduleAlarmsForMeeting(meeting)
    }

    override suspend fun deleteMeeting(meeting: Meeting) = withContext(Dispatchers.IO) {
        alarmScheduler.cancelAlarmsForMeeting(meeting)
        meetingDao.delete(MeetingEntity.fromDomain(meeting))
        Unit
    }

    override suspend fun deleteMeetingById(id: Long) = withContext(Dispatchers.IO) {
        val meeting = getMeetingById(id)
        if (meeting != null) {
            deleteMeeting(meeting)
        }
        Unit
    }
}
