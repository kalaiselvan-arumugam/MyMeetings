package com.example.mymeetings.data.parser

import com.example.mymeetings.domain.model.Meeting
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

object RruleEvaluator {

    /**
     * Calculates all occurrence start times (in epoch milliseconds) for a meeting
     * within a specified time window [startMillis, endMillis].
     */
    fun getOccurrences(meeting: Meeting, startMillis: Long, endMillis: Long): List<Long> {
        val occurrences = mutableListOf<Long>()
        val startInstant = Instant.ofEpochMilli(meeting.startTime)
        val zoneId = try {
            ZoneId.of(meeting.timeZone)
        } catch (e: Exception) {
            ZoneId.systemDefault()
        }
        val startDateTime = ZonedDateTime.ofInstant(startInstant, zoneId)
        val rrule = meeting.rrule

        // If there's no recurrence rule, it's a one-time meeting
        if (rrule.isNullOrBlank()) {
            val startVal = meeting.startTime
            if (startVal in startMillis..endMillis && !meeting.exdates.contains(startVal)) {
                occurrences.add(startVal)
            }
            return occurrences
        }

        // Parse RRULE parts
        val rruleMap = parseRrule(rrule)
        val freq = rruleMap["FREQ"] ?: return occurrences
        val interval = rruleMap["INTERVAL"]?.toIntOrNull() ?: 1
        val count = rruleMap["COUNT"]?.toIntOrNull()
        val untilStr = rruleMap["UNTIL"]
        val untilMillis = untilStr?.let { parseUntil(it, zoneId) }

        val byDay = rruleMap["BYDAY"]?.split(",")
        val byMonthDay = rruleMap["BYMONTHDAY"]?.split(",")?.mapNotNull { it.toIntOrNull() }

        // For WEEKLY+BYDAY, we need to track week-offset from start to enforce INTERVAL.
        // We use ISO week number delta: weeks are counted from the start week.
        val startWeekEpochDay = startDateTime.toLocalDate().toEpochDay() / 7

        var current = startDateTime
        var occurrencesCount = 0

        while (true) {
            val currentMillis = current.toInstant().toEpochMilli()

            // If we've passed the end window, we can stop (unless we need to count occurrences from start)
            if (currentMillis > endMillis && count == null) {
                break
            }

            // Check if within UNTIL limit
            if (untilMillis != null && currentMillis > untilMillis) {
                break
            }

            // Check if count limit reached
            if (count != null && occurrencesCount >= count) {
                break
            }

            // Evaluate if current candidate matches byDay or byMonthDay filters
            var matches = true
            if (byDay != null && freq == "WEEKLY") {
                val currentDayStr = current.dayOfWeek.name.take(2) // e.g. "MO", "TU"
                val dayMatches = byDay.contains(currentDayStr)

                // Also check that the current week satisfies the INTERVAL.
                // E.g. INTERVAL=2 means only emit in the same week as start, start+2 weeks, start+4 weeks, etc.
                val currentWeekEpochDay = current.toLocalDate().toEpochDay() / 7
                val weekOffset = (currentWeekEpochDay - startWeekEpochDay).toInt()
                val intervalMatches = weekOffset >= 0 && weekOffset % interval == 0

                matches = dayMatches && intervalMatches
            }
            if (byMonthDay != null) {
                matches = matches && byMonthDay.contains(current.dayOfMonth)
            }

            if (matches) {
                if (currentMillis >= startMillis && currentMillis <= endMillis) {
                    // Check EXDATE
                    val isExcluded = meeting.exdates.any { exdate ->
                        // Match exdate at day precision (or millisecond if exact)
                        val exInstant = Instant.ofEpochMilli(exdate)
                        val exDate = ZonedDateTime.ofInstant(exInstant, zoneId).toLocalDate()
                        exDate == current.toLocalDate()
                    }
                    if (!isExcluded) {
                        occurrences.add(currentMillis)
                    }
                }
                occurrencesCount++
            }

            // Move to next iteration based on FREQ & INTERVAL
            current = when (freq) {
                "DAILY" -> current.plusDays(interval.toLong())
                "WEEKLY" -> {
                    // If BYDAY is specified, we move day by day to check matches against
                    // the byDay list and the interval guard above.
                    if (byDay != null) {
                        current.plusDays(1)
                    } else {
                        current.plusWeeks(interval.toLong())
                    }
                }
                "MONTHLY" -> current.plusMonths(interval.toLong())
                "YEARLY" -> current.plusYears(interval.toLong())
                else -> break
            }

            // Safeguard against infinite loops
            if (occurrencesCount > 5000 || current.year > startDateTime.year + 10) {
                break
            }
        }

        return occurrences
    }

    /**
     * Parses the RRULE string into a key-value map.
     */
    private fun parseRrule(rrule: String): Map<String, String> {
        val cleaned = rrule.trim().uppercase(Locale.ROOT).removePrefix("RRULE:")
        return cleaned.split(";").associate {
            val parts = it.split("=")
            if (parts.size == 2) {
                parts[0] to parts[1]
            } else {
                "" to ""
            }
        }
    }

    /**
     * Parses the UNTIL date-time string from RRULE (e.g. 20260720T120000Z).
     */
    private fun parseUntil(untilStr: String, zoneId: ZoneId): Long? {
        return try {
            val clean = untilStr.replace("Z", "")
            val formatter = if (clean.contains("T")) {
                DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
            } else {
                DateTimeFormatter.ofPattern("yyyyMMdd")
            }
            if (clean.contains("T")) {
                LocalDateTime.parse(clean, formatter).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
            } else {
                LocalDate.parse(clean, formatter).atStartOfDay(zoneId).toInstant().toEpochMilli()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Converts a recurring rule to a human-readable format.
     */
    fun toHumanReadable(rrule: String?): String {
        if (rrule.isNullOrBlank()) return "One-time meeting"
        val rruleMap = parseRrule(rrule)
        val freq = rruleMap["FREQ"] ?: return "Custom recurrence"
        val interval = rruleMap["INTERVAL"]?.toIntOrNull() ?: 1
        val intervalStr = if (interval > 1) "every $interval " else ""

        val freqPart = when (freq) {
            "DAILY" -> if (interval > 1) "${intervalStr}days" else "Daily"
            "WEEKLY" -> {
                val byDay = rruleMap["BYDAY"]
                if (byDay != null) {
                    val days = byDay.split(",").map {
                        when (it) {
                            "MO" -> "Monday"
                            "TU" -> "Tuesday"
                            "WE" -> "Wednesday"
                            "TH" -> "Thursday"
                            "FR" -> "Friday"
                            "SA" -> "Saturday"
                            "SU" -> "Sunday"
                            else -> it
                        }
                    }.joinToString(", ")
                    if (interval > 1) "${intervalStr}weeks on $days" else "Weekly on $days"
                } else {
                    if (interval > 1) "${intervalStr}weeks" else "Weekly"
                }
            }
            "MONTHLY" -> if (interval > 1) "${intervalStr}months" else "Monthly"
            "YEARLY" -> if (interval > 1) "${intervalStr}years" else "Yearly"
            else -> "Custom recurrence"
        }
        return freqPart
    }
}
