package com.example.mymeetings.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [MeetingEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class MeetingDatabase : RoomDatabase() {
    abstract fun meetingDao(): MeetingDao
}
