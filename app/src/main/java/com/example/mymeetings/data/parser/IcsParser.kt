package com.example.mymeetings.data.parser

import com.example.mymeetings.domain.model.Meeting
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPInputStream

object IcsParser {

    /**
     * Entry point to parse, decode, and extract a Meeting object from any raw QR string.
     * Automatically handles plain text ICS, Base64-encoded, and Gzip-compressed formats.
     */
    fun parseQrPayload(payload: String): Meeting? {
        val trimmed = payload.trim()

        // 1. Check if raw plain text ICS
        if (trimmed.contains("BEGIN:VCALENDAR") || trimmed.contains("BEGIN:VEVENT")) {
            return parseIcs(trimmed)
        }

        // 2. Try Base64 decoding
        try {
            val decodedBytes = Base64.getDecoder().decode(trimmed)
            
            // Check if bytes are Gzip compressed (Gzip magic numbers: 0x1F, 0x8B)
            val isGzipped = decodedBytes.size >= 2 && 
                            decodedBytes[0] == 0x1f.toByte() && 
                            decodedBytes[1] == 0x8b.toByte()
            
            val plainText = if (isGzipped) {
                decompressGzip(decodedBytes)
            } else {
                String(decodedBytes, Charsets.UTF_8)
            }

            if (plainText.contains("BEGIN:VCALENDAR") || plainText.contains("BEGIN:VEVENT")) {
                return parseIcs(plainText)
            }
        } catch (e: Exception) {
            // Not base64 or failed decompression
        }

        return null
    }

    /**
     * Decompresses Gzip-compressed byte array to plain text string.
     */
    private fun decompressGzip(compressed: ByteArray): String {
        val gis = GZIPInputStream(ByteArrayInputStream(compressed))
        val buffer = ByteArray(1024)
        val out = ByteArrayOutputStream()
        var len: Int
        while (gis.read(buffer).also { len = it } > 0) {
            out.write(buffer, 0, len)
        }
        gis.close()
        return out.toString(Charsets.UTF_8.name())
    }

    /**
     * Parses standard iCalendar format text into a Meeting domain object.
     */
    fun parseIcs(icsContent: String): Meeting? {
        // Step 1: Unfold long lines (RFC 5545 splits long lines with CRLF + space)
        val unfolded = icsContent
            .replace("\r\n ", "")
            .replace("\r\n\t", "")
            .replace("\n ", "")
            .replace("\n\t", "")

        val lines = unfolded.split("\r\n", "\n")
        var inEvent = false

        // Parsed key-values
        var uid = ""
        var summary = ""
        var description: String? = null
        var dtstartStr = ""
        var dtstartTzid: String? = null
        var dtendStr = ""
        var dtendTzid: String? = null
        var organizer: String? = null
        var rrule: String? = null
        val exdates = mutableListOf<Long>()
        var location: String? = null

        for (line in lines) {
            val cleanLine = line.trim()
            if (cleanLine.equals("BEGIN:VEVENT", ignoreCase = true)) {
                inEvent = true
                continue
            }
            if (cleanLine.equals("END:VEVENT", ignoreCase = true)) {
                break
            }
            if (!inEvent) continue

            // Split line by first colon
            val colonIdx = cleanLine.indexOf(':')
            if (colonIdx == -1) continue
            val keyParams = cleanLine.substring(0, colonIdx)
            val value = cleanLine.substring(colonIdx + 1)

            // Extract parameters
            val paramParts = keyParams.split(";")
            val key = paramParts[0].uppercase()
            val params = paramParts.drop(1).associate {
                val kv = it.split("=")
                if (kv.size == 2) kv[0].uppercase() to kv[1] else "" to ""
            }

            when (key) {
                "UID" -> uid = value
                "SUMMARY" -> summary = decodeEscapes(value)
                "DESCRIPTION" -> description = decodeEscapes(value)
                "LOCATION" -> location = decodeEscapes(value)
                "ORGANIZER" -> {
                    val cn = params["CN"]?.removeSurrounding("\"")
                    organizer = cn ?: value.replace("mailto:", "", ignoreCase = true)
                }
                "DTSTART" -> {
                    dtstartStr = value
                    dtstartTzid = params["TZID"]
                }
                "DTEND" -> {
                    dtendStr = value
                    dtendTzid = params["TZID"]
                }
                "RRULE" -> rrule = value
                "EXDATE" -> {
                    val exTzid = params["TZID"]
                    value.split(",").forEach { dateStr ->
                        val exMillis = parseDateTime(dateStr, exTzid)
                        if (exMillis > 0L) {
                            exdates.add(exMillis)
                        }
                    }
                }
            }
        }

        if (summary.isBlank() || dtstartStr.isBlank()) {
            return null // Invalid meeting (no title or start time)
        }

        val startTime = parseDateTime(dtstartStr, dtstartTzid)
        val endTime = if (dtendStr.isNotBlank()) parseDateTime(dtendStr, dtendTzid) else startTime + 3600000L // Default 1 hour duration
        val timeZone = dtstartTzid ?: (if (dtstartStr.endsWith("Z")) "UTC" else ZoneId.systemDefault().id)
        val finalUid = if (uid.isBlank()) UUID.randomUUID().toString() else uid

        // Extract Teams meeting elements
        val teamsUrl = description?.let { extractTeamsUrl(it) } ?: location?.let { if (it.contains("teams.microsoft.com")) extractTeamsUrl(it) else null }
        val teamsCreds = description?.let { extractTeamsCredentials(it) }

        return Meeting(
            uid = finalUid,
            title = summary,
            description = description,
            organizer = organizer,
            startTime = startTime,
            endTime = endTime,
            timeZone = timeZone,
            meetingUrl = teamsUrl,
            meetingId = teamsCreds?.first,
            passcode = teamsCreds?.second,
            rrule = rrule,
            exdates = exdates,
            notes = location?.let { "Location: $it" }
        )
    }

    /**
     * Un-escapes character formatting like "\,", "\;", and "\n".
     */
    private fun decodeEscapes(value: String): String {
        return value
            .replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
    }

    /**
     * Parses standard iCalendar format date-time string into milliseconds since epoch.
     */
    fun parseDateTime(dateTimeStr: String, tzid: String?): Long {
        val clean = dateTimeStr.trim()
        val zoneId = if (!tzid.isNullOrBlank()) {
            try { ZoneId.of(tzid) } catch (e: Exception) { ZoneId.systemDefault() }
        } else if (clean.endsWith("Z")) {
            ZoneId.of("UTC")
        } else {
            ZoneId.systemDefault()
        }

        val cleanTime = clean.replace("Z", "")
        return try {
            if (cleanTime.contains("T")) {
                val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
                LocalDateTime.parse(cleanTime, formatter).atZone(zoneId).toInstant().toEpochMilli()
            } else {
                // All-day event (LocalDate)
                val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
                LocalDate.parse(cleanTime, formatter).atStartOfDay(zoneId).toInstant().toEpochMilli()
            }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Extracts Microsoft Teams join URL from text description.
     */
    private fun extractTeamsUrl(text: String): String? {
        val regex = Regex("https?://[a-zA-Z0-9.-]*teams\\.microsoft\\.com/l/meetup-join/\\S+")
        val match = regex.find(text)
        return match?.value?.removeSuffix(">")?.removeSuffix(")")
    }

    /**
     * Extracts Meeting ID (12 digits, grouped or continuous) and Passcode from DESCRIPTION text.
     */
    private fun extractTeamsCredentials(text: String): Pair<String?, String?>? {
        // Match Meeting ID (typically 12 digits, e.g. "291 039 029 031" or "291-039-029-031")
        val meetingIdRegex = Regex("Meeting\\s*ID:?\\s*([0-9\\s\\-]+)", RegexOption.IGNORE_CASE)
        // Match Passcode (alphanumeric/special)
        val passcodeRegex = Regex("Passcode:?\\s*([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE)

        val idMatch = meetingIdRegex.find(text)
        val idVal = idMatch?.groupValues?.get(1)?.replace(Regex("[\\s\\-]"), "")?.trim()

        val passMatch = passcodeRegex.find(text)
        val passVal = passMatch?.groupValues?.get(1)?.trim()

        if (idVal != null && idVal.length >= 9) { // Safety length check
            return Pair(idVal, passVal)
        }
        return null
    }
}
