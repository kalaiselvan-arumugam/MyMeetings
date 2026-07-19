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

    // -------------------------------------------------------------------------
    // Windows Timezone ID → IANA Timezone ID mapping.
    // Outlook / Exchange ICS files use Windows timezone names (e.g. "Singapore
    // Standard Time") which Java's ZoneId.of() cannot resolve directly.
    // -------------------------------------------------------------------------
    private val WINDOWS_TO_IANA: Map<String, String> = mapOf(
        // Asia-Pacific
        "Singapore Standard Time"           to "Asia/Singapore",
        "India Standard Time"               to "Asia/Kolkata",
        "China Standard Time"               to "Asia/Shanghai",
        "Tokyo Standard Time"               to "Asia/Tokyo",
        "Korea Standard Time"               to "Asia/Seoul",
        "Taipei Standard Time"              to "Asia/Taipei",
        "Hong Kong Standard Time"           to "Asia/Hong_Kong",
        "AUS Eastern Standard Time"         to "Australia/Sydney",
        "E. Australia Standard Time"        to "Australia/Brisbane",
        "W. Australia Standard Time"        to "Australia/Perth",
        "Cen. Australia Standard Time"      to "Australia/Adelaide",
        "New Zealand Standard Time"         to "Pacific/Auckland",
        "SE Asia Standard Time"             to "Asia/Bangkok",
        "Malay Peninsula Standard Time"     to "Asia/Kuala_Lumpur",
        "Indochina Standard Time"           to "Asia/Bangkok",
        "Myanmar Standard Time"             to "Asia/Rangoon",
        "Bangladesh Standard Time"          to "Asia/Dhaka",
        "Sri Lanka Standard Time"           to "Asia/Colombo",
        "Pakistan Standard Time"            to "Asia/Karachi",
        "West Asia Standard Time"           to "Asia/Tashkent",
        "Iran Standard Time"                to "Asia/Tehran",
        "Arabian Standard Time"             to "Asia/Dubai",
        "Arab Standard Time"                to "Asia/Riyadh",
        "Afghanistan Standard Time"         to "Asia/Kabul",
        "Ulaanbaatar Standard Time"         to "Asia/Ulaanbaatar",
        "North Asia East Standard Time"     to "Asia/Irkutsk",
        "Yakutsk Standard Time"             to "Asia/Yakutsk",
        "Vladivostok Standard Time"         to "Asia/Vladivostok",
        "Magadan Standard Time"             to "Asia/Magadan",
        "Kamchatka Standard Time"           to "Asia/Kamchatka",
        // Middle East / Africa
        "Middle East Standard Time"         to "Asia/Beirut",
        "Turkey Standard Time"              to "Europe/Istanbul",
        "Egypt Standard Time"               to "Africa/Cairo",
        "South Africa Standard Time"        to "Africa/Johannesburg",
        "E. Africa Standard Time"           to "Africa/Nairobi",
        "W. Central Africa Standard Time"   to "Africa/Lagos",
        "Morocco Standard Time"             to "Africa/Casablanca",
        // Europe
        "Russian Standard Time"             to "Europe/Moscow",
        "Belarus Standard Time"             to "Europe/Minsk",
        "FLE Standard Time"                 to "Europe/Kiev",
        "GTB Standard Time"                 to "Europe/Bucharest",
        "E. Europe Standard Time"           to "Asia/Nicosia",
        "W. Europe Standard Time"           to "Europe/Berlin",
        "Central Europe Standard Time"      to "Europe/Budapest",
        "Central European Standard Time"    to "Europe/Warsaw",
        "Romance Standard Time"             to "Europe/Paris",
        "GMT Standard Time"                 to "Europe/London",
        "Greenwich Standard Time"           to "Atlantic/Reykjavik",
        // UTC variants
        "UTC"                               to "UTC",
        "UTC-02"                            to "Etc/GMT+2",
        "UTC+12"                            to "Etc/GMT-12",
        // Atlantic / Americas
        "Azores Standard Time"              to "Atlantic/Azores",
        "Cape Verde Standard Time"          to "Atlantic/Cape_Verde",
        "Newfoundland Standard Time"        to "America/St_Johns",
        "E. South America Standard Time"    to "America/Sao_Paulo",
        "SA Eastern Standard Time"          to "America/Fortaleza",
        "Greenland Standard Time"           to "America/Godthab",
        "Atlantic Standard Time"            to "America/Halifax",
        "SA Western Standard Time"          to "America/La_Paz",
        "Central Brazilian Standard Time"   to "America/Cuiaba",
        "SA Pacific Standard Time"          to "America/Bogota",
        "Eastern Standard Time"             to "America/New_York",
        "US Eastern Standard Time"          to "America/Indianapolis",
        "Central Standard Time"             to "America/Chicago",
        "Canada Central Standard Time"      to "America/Regina",
        "Central Standard Time (Mexico)"    to "America/Monterrey",
        "Mountain Standard Time"            to "America/Denver",
        "Mountain Standard Time (Mexico)"   to "America/Chihuahua",
        "US Mountain Standard Time"         to "America/Phoenix",
        "Pacific Standard Time"             to "America/Los_Angeles",
        "Pacific Standard Time (Mexico)"    to "America/Santa_Isabel",
        "Alaskan Standard Time"             to "America/Anchorage",
        "Hawaiian Standard Time"            to "Pacific/Honolulu",
        "Samoa Standard Time"               to "Pacific/Apia",
        // Pacific
        "Fiji Standard Time"                to "Pacific/Fiji",
        "Tonga Standard Time"               to "Pacific/Tongatapu",
    )

    /**
     * Resolves a timezone ID string (IANA or Windows) to a ZoneId.
     * Falls back to systemDefault() if the ID is unknown.
     */
    private fun resolveZoneId(tzid: String?): ZoneId {
        if (tzid.isNullOrBlank()) return ZoneId.systemDefault()
        // Try IANA first (fast path)
        return try {
            ZoneId.of(tzid)
        } catch (e: Exception) {
            // Try Windows → IANA mapping
            val ianaId = WINDOWS_TO_IANA[tzid]
            if (ianaId != null) {
                try { ZoneId.of(ianaId) } catch (e2: Exception) { ZoneId.systemDefault() }
            } else {
                ZoneId.systemDefault()
            }
        }
    }

    /**
     * Resolves a raw timezone ID string to its canonical IANA string.
     * If a Windows ID is given, returns the mapped IANA string.
     */
    private fun resolveIanaId(tzid: String?): String {
        if (tzid.isNullOrBlank()) return ZoneId.systemDefault().id
        return try {
            ZoneId.of(tzid).id   // Already IANA — return canonical form
        } catch (e: Exception) {
            WINDOWS_TO_IANA[tzid] ?: ZoneId.systemDefault().id
        }
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

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
            // Not base64 or failed decompression — ignore
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

    // -------------------------------------------------------------------------
    // Main ICS parser
    // -------------------------------------------------------------------------

    /**
     * Parses standard iCalendar format text into a Meeting domain object.
     */
    fun parseIcs(icsContent: String): Meeting? {
        // Step 1: Unfold long lines (RFC 5545 folds long lines with CRLF + whitespace)
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
        var teamsUrlDirect: String? = null  // From X-MICROSOFT-SKYPETEAMSMEETINGURL

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

            // Split line by first colon only
            val colonIdx = cleanLine.indexOf(':')
            if (colonIdx == -1) continue
            val keyParams = cleanLine.substring(0, colonIdx)
            val value = cleanLine.substring(colonIdx + 1)

            // Extract parameters (e.g. TZID, CN)
            val paramParts = keyParams.split(";")
            val key = paramParts[0].uppercase()
            val params = paramParts.drop(1).associate {
                val kv = it.split("=", limit = 2)
                if (kv.size == 2) kv[0].uppercase() to kv[1] else "" to ""
            }

            when (key) {
                "UID"         -> uid = value
                "SUMMARY"     -> summary = decodeEscapes(value)
                "DESCRIPTION" -> description = decodeEscapes(value)
                "LOCATION"    -> location = decodeEscapes(value)
                "ORGANIZER"   -> {
                    val cn = params["CN"]?.removeSurrounding("\"")
                    organizer = cn ?: value.replace("mailto:", "", ignoreCase = true)
                }
                "DTSTART"     -> {
                    dtstartStr = value
                    dtstartTzid = params["TZID"]
                }
                "DTEND"       -> {
                    dtendStr = value
                    dtendTzid = params["TZID"]
                }
                "RRULE"       -> rrule = value
                "EXDATE"      -> {
                    val exTzid = params["TZID"]
                    value.split(",").forEach { dateStr ->
                        val exMillis = parseDateTime(dateStr, exTzid)
                        if (exMillis > 0L) exdates.add(exMillis)
                    }
                }
                // Microsoft Teams direct URL properties (no regex extraction needed)
                "X-MICROSOFT-SKYPETEAMSMEETINGURL",
                "X-MICROSOFT-ONLINEMEETINGCONFLINK" -> {
                    if (teamsUrlDirect.isNullOrBlank()) {
                        teamsUrlDirect = value.trim()
                    }
                }
            }
        }

        if (summary.isBlank() || dtstartStr.isBlank()) {
            return null // Invalid meeting (no title or start time)
        }

        val startTime = parseDateTime(dtstartStr, dtstartTzid)
        val endTime = if (dtendStr.isNotBlank()) {
            parseDateTime(dtendStr, dtendTzid)
        } else {
            startTime + 3600000L  // Default 1 hour duration
        }

        // Store a canonical IANA timezone ID (not the raw Windows string)
        val timeZone = if (!dtstartTzid.isNullOrBlank()) {
            resolveIanaId(dtstartTzid)
        } else if (dtstartStr.endsWith("Z")) {
            "UTC"
        } else {
            ZoneId.systemDefault().id
        }

        val finalUid = if (uid.isBlank()) UUID.randomUUID().toString() else uid

        // Teams URL: prefer direct property, then regex from description, then location
        val teamsUrl = teamsUrlDirect?.takeIf { it.isNotBlank() }
            ?: description?.let { extractTeamsUrl(it) }
            ?: location?.let { if (it.contains("teams.microsoft.com")) extractTeamsUrl(it) else null }

        // Teams credentials: extract from description text
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Un-escapes iCalendar text escapes: \n, \N, \,, \;, \:, \\.
     * Note: some ICS generators (notably Outlook) escape colons as \: inside
     * property values, which breaks credential extraction if not decoded.
     */
    private fun decodeEscapes(value: String): String {
        return value
            .replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\:", ":")     // Some clients escape colons — must decode before regex extraction
            .replace("\\\\", "\\")
    }

    /**
     * Parses an iCalendar date-time string into epoch milliseconds.
     * Handles: full datetime (yyyyMMdd'T'HHmmss[Z]), all-day (yyyyMMdd),
     * IANA timezone IDs, and Windows timezone IDs via resolveZoneId().
     */
    fun parseDateTime(dateTimeStr: String, tzid: String?): Long {
        val clean = dateTimeStr.trim()
        val zoneId = when {
            !tzid.isNullOrBlank() -> resolveZoneId(tzid)
            clean.endsWith("Z")   -> ZoneId.of("UTC")
            else                  -> ZoneId.systemDefault()
        }

        val cleanTime = clean.removeSuffix("Z")
        return try {
            if (cleanTime.contains("T")) {
                val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
                LocalDateTime.parse(cleanTime, formatter).atZone(zoneId).toInstant().toEpochMilli()
            } else {
                // All-day event
                val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
                LocalDate.parse(cleanTime, formatter).atStartOfDay(zoneId).toInstant().toEpochMilli()
            }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Extracts a Microsoft Teams join URL from text.
     * Handles meetup-join URLs, new Teams /meet/ URLs, Teams Live, and GCC-High variants.
     * Strips trailing angle brackets and parentheses.
     */
    private fun extractTeamsUrl(text: String): String? {
        val regex = Regex(
            "https?://[a-zA-Z0-9.-]*(teams\\.microsoft\\.com|teams\\.live\\.com|" +
            "gov\\.teams\\.microsoft\\.us|dod\\.teams\\.microsoft\\.us)" +
            "/(l/meetup-join|meet|dl/launcher)[^\\s<>\"]*",
            RegexOption.IGNORE_CASE
        )
        val match = regex.find(text) ?: return null
        return match.value.trimEnd('>', ')', '\n', '\r', ' ', ',')
    }

    /**
     * Extracts Meeting ID and Passcode from a Teams description text.
     * Handles both spaced IDs ("291 039 029 031") and compact IDs ("291039029031").
     * Also handles escaped colons (\:) since decodeEscapes() is called before this.
     */
    private fun extractTeamsCredentials(text: String): Pair<String?, String?>? {
        // Meeting ID: 12 digits (may be grouped with spaces/dashes)
        val meetingIdRegex = Regex(
            "Meeting\\s*ID[:\\s]+([0-9][0-9\\s\\-]{6,}[0-9])",
            RegexOption.IGNORE_CASE
        )
        // Passcode / Password: alphanumeric, may include special chars
        val passcodeRegex = Regex(
            "(Passcode|Password)[:\\s]+([a-zA-Z0-9@#\$!%^&*]+)",
            RegexOption.IGNORE_CASE
        )

        val idMatch = meetingIdRegex.find(text)
        val idRaw = idMatch?.groupValues?.get(1)
        val idVal = idRaw?.replace(Regex("[\\s\\-]"), "")?.trim()

        val passMatch = passcodeRegex.find(text)
        val passVal = passMatch?.groupValues?.get(2)?.trim()

        return if (idVal != null && idVal.length >= 9) {
            Pair(idVal, passVal)
        } else {
            null
        }
    }
}
