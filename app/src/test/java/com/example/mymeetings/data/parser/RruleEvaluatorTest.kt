package com.example.mymeetings.data.parser

import com.example.mymeetings.domain.model.Meeting
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class RruleEvaluatorTest {

    @Test
    fun testOneTimeEvent() {
        val startEpoch = LocalDateTime.of(2026, 7, 19, 10, 0)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        val endEpoch = LocalDateTime.of(2026, 7, 19, 11, 0)
            .toInstant(ZoneOffset.UTC).toEpochMilli()

        val meeting = Meeting(
            id = 0L,
            uid = "test-uid-1",
            title = "One-Time Sync",
            startTime = startEpoch,
            endTime = endEpoch,
            timeZone = "UTC",
            rrule = null,
            exdates = emptyList()
        )

        // Request occurrence within current day
        val now = startEpoch - 1000 // 1 second before
        val limit = startEpoch + 3600_000 * 24 // 1 day after
        val occurrences = RruleEvaluator.getOccurrences(meeting, now, limit)

        assertEquals(1, occurrences.size)
        assertEquals(startEpoch, occurrences[0])
    }

    @Test
    fun testWeeklyEventWithExdates() {
        val startEpoch = LocalDateTime.of(2026, 7, 19, 10, 0) // Sunday
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        val endEpoch = LocalDateTime.of(2026, 7, 19, 11, 0)
            .toInstant(ZoneOffset.UTC).toEpochMilli()

        // Exclude the second occurrence (July 26)
        val exdateEpoch = LocalDateTime.of(2026, 7, 26, 10, 0)
            .toInstant(ZoneOffset.UTC).toEpochMilli()

        val meeting = Meeting(
            id = 0L,
            uid = "test-uid-weekly",
            title = "Weekly Standup",
            startTime = startEpoch,
            endTime = endEpoch,
            timeZone = "UTC",
            rrule = "FREQ=WEEKLY;BYDAY=SU",
            exdates = listOf(exdateEpoch)
        )

        // Evaluate occurrences over 2 weeks (14 days)
        val now = startEpoch - 1000
        val limit = startEpoch + (3600_000L * 24 * 7 * 2) // 2 weeks
        val occurrences = RruleEvaluator.getOccurrences(meeting, now, limit)

        println("OCCURRENCES DETECTED: " + occurrences.map { java.time.Instant.ofEpochMilli(it).toString() })
        val expectedThirdWeek = LocalDateTime.of(2026, 8, 2, 10, 0)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        println("EXPECTED THIRD WEEK: " + java.time.Instant.ofEpochMilli(expectedThirdWeek).toString())

        // We expect 2 occurrences: July 19 (start) and Aug 2 (week 3). July 26 is excluded!
        assertEquals(2, occurrences.size)
        assertEquals(startEpoch, occurrences[0])
        assertEquals(expectedThirdWeek, occurrences[1])
    }
}
