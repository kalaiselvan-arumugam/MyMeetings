package com.example.mymeetings.ui.details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mymeetings.data.parser.RruleEvaluator
import com.example.mymeetings.domain.model.Meeting
import com.example.mymeetings.ui.home.getNextOccurrenceTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    meetingId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    viewModel: DetailsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val meetingState by viewModel.meeting.collectAsStateWithLifecycle()

    LaunchedEffect(meetingId) {
        viewModel.loadMeeting(meetingId)
    }

    val meeting = meetingState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meeting Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (meeting != null) {
                        IconButton(onClick = { onNavigateToEdit(meeting.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = {
                            viewModel.deleteMeeting {
                                Toast.makeText(context, "Meeting deleted", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        if (meeting == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                // Color Tag Title Bar
                val accentColor = meeting.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = meeting.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (!meeting.rrule.isNullOrBlank()) {
                            Text(
                                text = RruleEvaluator.toHumanReadable(meeting.rrule),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = "One-time meeting",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Date Time Box Card
                val nextOccurrence = getNextOccurrenceTime(meeting)
                val duration = meeting.endTime - meeting.startTime
                val nextEnd = nextOccurrence + duration

                val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy")
                    .withZone(ZoneId.systemDefault())
                val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
                    .withZone(ZoneId.systemDefault())

                val formatNextDate = dateFormatter.format(Instant.ofEpochMilli(nextOccurrence))
                val formatNextStart = timeFormatter.format(Instant.ofEpochMilli(nextOccurrence))
                val formatNextEnd = timeFormatter.format(Instant.ofEpochMilli(nextEnd))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Next Occurrence",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatNextDate,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$formatNextStart - $formatNextEnd (${meeting.timeZone})",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                // Main Action Buttons (Join, Share)
                Button(
                    onClick = {
                        viewModel.incrementJoinStats()
                        val url = meeting.meetingUrl
                        if (!url.isNullOrBlank()) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Teams app unavailable. Opening in browser.", Toast.LENGTH_SHORT).show()
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(browserIntent)
                            }
                        } else {
                            Toast.makeText(context, "No Join URL available. Use Meeting ID.", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = !meeting.meetingUrl.isNullOrBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text("Join Microsoft Teams", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val url = meeting.meetingUrl
                            if (!url.isNullOrBlank()) {
                                copyToClipboard(context, "Meeting Link", url)
                                Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !meeting.meetingUrl.isNullOrBlank(),
                        modifier = Modifier.weight(1.0f)
                    ) {
                        Text("Copy Link")
                    }
                    OutlinedButton(
                        onClick = {
                            val shareText = buildShareText(meeting)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Meeting"))
                        },
                        modifier = Modifier.weight(1.0f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share")
                    }
                }

                // Meeting Credentials Section (URL + ID + Passcode)
                if (!meeting.meetingId.isNullOrBlank() || !meeting.meetingUrl.isNullOrBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Microsoft Teams Credentials",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Join URL row
                            if (!meeting.meetingUrl.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Join URL", style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            meeting.meetingUrl,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(onClick = {
                                        copyToClipboard(context, "Join URL", meeting.meetingUrl)
                                        Toast.makeText(context, "URL copied!", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Text("Copy")
                                    }
                                }
                            }

                            // Meeting ID row
                            if (!meeting.meetingId.isNullOrBlank()) {
                                if (!meeting.meetingUrl.isNullOrBlank()) {
                                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Meeting ID", style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            meeting.meetingId,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    TextButton(onClick = {
                                        copyToClipboard(context, "Meeting ID", meeting.meetingId)
                                        Toast.makeText(context, "ID copied!", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Text("Copy")
                                    }
                                }
                                Divider(modifier = Modifier.padding(vertical = 8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Passcode", style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            meeting.passcode ?: "-",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    TextButton(onClick = {
                                        meeting.passcode?.let {
                                            copyToClipboard(context, "Passcode", it)
                                            Toast.makeText(context, "Passcode copied!", Toast.LENGTH_SHORT).show()
                                        }
                                    }, enabled = !meeting.passcode.isNullOrBlank()) {
                                        Text("Copy")
                                    }
                                }
                            }
                        }
                    }
                }

                // Preferred Guest Name Card
                if (!meeting.guestName.isNullOrBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Preferred Guest Name", style = MaterialTheme.typography.labelMedium)
                                Text(meeting.guestName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = {
                                copyToClipboard(context, "Guest Name", meeting.guestName)
                                Toast.makeText(context, "Guest Name copied!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Copy Guest Name") // Uses standard share icon for action representation
                            }
                        }
                    }
                }

                // Description and Notes Section
                if (!meeting.description.isNullOrBlank() || !meeting.notes.isNullOrBlank()) {
                    Text(
                        text = "Description & Notes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                    meeting.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    meeting.notes?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                // Organizer details
                meeting.organizer?.let {
                    Text(
                        text = "Host / Organizer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Tags Details
                if (meeting.tags.isNotEmpty()) {
                    Text(
                        text = "Tags",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        meeting.tags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = tag,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // History Statistics Panel
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Import & Usage History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        val importFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a")
                            .withZone(ZoneId.systemDefault())
                        val eventDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM dd yyyy, hh:mm a")
                            .withZone(ZoneId.systemDefault())
                        val importDate = importFormatter.format(Instant.ofEpochMilli(meeting.creationDate))

                        StatRow(label = "Event Start", value = eventDateFormatter.format(Instant.ofEpochMilli(meeting.startTime)))
                        StatRow(label = "Event End", value = eventDateFormatter.format(Instant.ofEpochMilli(meeting.endTime)))
                        StatRow(label = "Import Date", value = importDate)
                        StatRow(label = "Imported From", value = meeting.importedFrom)
                        StatRow(label = "Times Joined", value = meeting.timesJoined.toString())
                        StatRow(label = "Times Dismissed", value = meeting.timesDismissed.toString())
                        StatRow(
                            label = "Last Reminder Notification", 
                            value = meeting.lastNotificationTime?.let { importFormatter.format(Instant.ofEpochMilli(it)) } ?: "Never Fired"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
}

private fun buildShareText(meeting: Meeting): String {
    val builder = java.lang.StringBuilder()
    builder.append("Meeting: ${meeting.title}\n")
    meeting.organizer?.let { builder.append("Organizer: $it\n") }
    
    val timeFormatter = DateTimeFormatter.ofPattern("EEE, MMM dd, h:mm a").withZone(ZoneId.systemDefault())
    builder.append("Start: ${timeFormatter.format(Instant.ofEpochMilli(meeting.startTime))}\n")
    
    if (!meeting.meetingUrl.isNullOrBlank()) {
        builder.append("Join URL: ${meeting.meetingUrl}\n")
    }
    if (!meeting.meetingId.isNullOrBlank()) {
        builder.append("Meeting ID: ${meeting.meetingId}\n")
        builder.append("Passcode: ${meeting.passcode ?: ""}\n")
    }
    return builder.toString()
}
