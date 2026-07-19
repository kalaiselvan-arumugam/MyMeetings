package com.example.mymeetings.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {

    @Query("SELECT * FROM meetings ORDER BY startTime ASC")
    fun getMeetingsFlow(): Flow<List<MeetingEntity>>

    @Query("SELECT * FROM meetings ORDER BY startTime ASC")
    fun getMeetingsList(): List<MeetingEntity>

    @Query("SELECT * FROM meetings WHERE id = :id")
    fun getMeetingById(id: Long): MeetingEntity?

    @Query("SELECT * FROM meetings WHERE uid = :uid")
    fun getMeetingByUid(uid: String): MeetingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(meeting: MeetingEntity): Long

    @Update
    fun update(meeting: MeetingEntity): Int

    @Delete
    fun delete(meeting: MeetingEntity): Int

    @Query("DELETE FROM meetings WHERE id = :id")
    fun deleteById(id: Long): Int
}
