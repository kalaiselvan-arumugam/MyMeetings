package com.example.mymeetings.data.parser

import org.junit.Assert.*
import org.junit.Test

class IcsParserTest {

    @Test
    fun testParseSimpleIcs() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Example Corp//MyMeetings//EN
            BEGIN:VEVENT
            UID:meeting-12345
            DTSTART:20260719T100000Z
            DTEND:20260719T110000Z
            SUMMARY:Project Sync Meeting
            LOCATION:Microsoft Teams Meeting
            DESCRIPTION:Join meeting here: https://teams.microsoft.com/l/meetup-join/19%3ameeting_xyz%40thread.v2/0?context=%7b%22Tid%22%3a%22abc%22%2c%22Oid%22%3a%22def%22%7d\nMeeting ID: 123 456 789 012\nPasscode: abcDEF
            RRULE:FREQ=WEEKLY;BYDAY=SU
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val meeting = IcsParser.parseIcs(ics)
        assertNotNull(meeting)
        assertEquals("meeting-12345", meeting?.uid)
        assertEquals("Project Sync Meeting", meeting?.title)
        assertEquals("https://teams.microsoft.com/l/meetup-join/19%3ameeting_xyz%40thread.v2/0?context=%7b%22Tid%22%3a%22abc%22%2c%22Oid%22%3a%22def%22%7d", meeting?.meetingUrl)
        assertEquals("123456789012", meeting?.meetingId)
        assertEquals("abcDEF", meeting?.passcode)
        assertEquals("FREQ=WEEKLY;BYDAY=SU", meeting?.rrule)
    }

    @Test
    fun testParseLineFoldedIcs() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:folded-123
            SUMMARY:This is a very
              long title
            DTSTART:20260719T100000Z
            DTEND:20260719T110000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val meeting = IcsParser.parseIcs(ics)
        assertNotNull(meeting)
        assertEquals("folded-123", meeting?.uid)
        assertEquals("This is a very long title", meeting?.title)
    }
}
