package com.example.mymeetings.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.example.mymeetings.domain.model.Meeting

@Entity(
    tableName = "meetings",
    indices = [
        Index(value = ["uid"], unique = true),
        Index(value = ["startTime"]),
        Index(value = ["enabled"])
    ]
)
data class MeetingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uid: String,
    val title: String,
    val description: String?,
    val organizer: String?,
    val startTime: Long,
    val endTime: Long,
    val timeZone: String,
    val meetingUrl: String?,
    val meetingId: String?,
    val passcode: String?,
    val rrule: String?,
    val exdates: List<Long>,
    val enabled: Boolean,
    val reminderSettings: List<Int>,
    val guestName: String?,
    val color: Int?,
    val tags: List<String>,
    val notes: String?,
    
    // Stats & History
    val creationDate: Long,
    val lastModified: Long,
    val importedFrom: String,
    val lastNotificationTime: Long?,
    val lastOpenedTime: Long?,
    val timesJoined: Int,
    val timesDismissed: Int,
    val isMissed: Boolean
) {
    fun toDomain(): Meeting = Meeting(
        id = id,
        uid = uid,
        title = title,
        description = description,
        organizer = organizer,
        startTime = startTime,
        endTime = endTime,
        timeZone = timeZone,
        meetingUrl = meetingUrl,
        meetingId = meetingId,
        passcode = passcode,
        rrule = rrule,
        exdates = exdates,
        enabled = enabled,
        reminderSettings = reminderSettings,
        guestName = guestName,
        color = color,
        tags = tags,
        notes = notes,
        creationDate = creationDate,
        lastModified = lastModified,
        importedFrom = importedFrom,
        lastNotificationTime = lastNotificationTime,
        lastOpenedTime = lastOpenedTime,
        timesJoined = timesJoined,
        timesDismissed = timesDismissed,
        isMissed = isMissed
    )

    companion object {
        fun fromDomain(m: Meeting): MeetingEntity = MeetingEntity(
            id = m.id,
            uid = m.uid,
            title = m.title,
            description = m.description,
            organizer = m.organizer,
            startTime = m.startTime,
            endTime = m.endTime,
            timeZone = m.timeZone,
            meetingUrl = m.meetingUrl,
            meetingId = m.meetingId,
            passcode = m.passcode,
            rrule = m.rrule,
            exdates = m.exdates,
            enabled = m.enabled,
            reminderSettings = m.reminderSettings,
            guestName = m.guestName,
            color = m.color,
            tags = m.tags,
            notes = m.notes,
            creationDate = m.creationDate,
            lastModified = m.lastModified,
            importedFrom = m.importedFrom,
            lastNotificationTime = m.lastNotificationTime,
            lastOpenedTime = m.lastOpenedTime,
            timesJoined = m.timesJoined,
            timesDismissed = m.timesDismissed,
            isMissed = m.isMissed
        )
    }
}
