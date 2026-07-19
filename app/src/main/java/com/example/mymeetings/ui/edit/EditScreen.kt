package com.example.mymeetings.ui.edit

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    meetingId: Long,
    onNavigateBack: () -> Unit,
    viewModel: EditViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val meetingState by viewModel.meeting.collectAsStateWithLifecycle()

    LaunchedEffect(meetingId) {
        viewModel.loadMeeting(meetingId)
    }

    val meeting = meetingState

    if (meeting == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        // Form states
        var title by remember { mutableStateOf(meeting.title) }
        var guestName by remember { mutableStateOf(meeting.guestName ?: "") }
        var notes by remember { mutableStateOf(meeting.notes ?: "") }
        var tagsStr by remember { mutableStateOf(meeting.tags.joinToString(", ")) }
        var selectedColor by remember { mutableStateOf(meeting.color ?: CuratedColors[0].toArgb()) }
        
        // Reminder settings states
        val activeOffsets = remember { mutableStateListOf<Int>().apply { addAll(meeting.reminderSettings) } }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Edit Meeting", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (title.isBlank()) {
                                Toast.makeText(context, "Title cannot be blank.", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }

                            // Convert tags comma string to list
                            val tagsList = tagsStr.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }

                            val updated = meeting.copy(
                                title = title,
                                guestName = guestName.ifBlank { null },
                                notes = notes.ifBlank { null },
                                tags = tagsList,
                                color = selectedColor,
                                reminderSettings = activeOffsets.sorted(),
                                lastModified = System.currentTimeMillis()
                            )

                            viewModel.updateMeeting(updated) {
                                Toast.makeText(context, "Changes saved successfully", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            }
                        }) {
                            Icon(Icons.Default.Done, contentDescription = "Save")
                        }
                    }
                )
            },
            modifier = modifier.fillMaxSize()
        ) { padding ->
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title Field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Meeting Title *") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Guest Name
                OutlinedTextField(
                    value = guestName,
                    onValueChange = { guestName = it },
                    label = { Text("Preferred Guest Name") },
                    placeholder = { Text("e.g. John Doe (Guest)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Tags (Comma Separated)
                OutlinedTextField(
                    value = tagsStr,
                    onValueChange = { tagsStr = it },
                    label = { Text("Tags (comma separated)") },
                    placeholder = { Text("e.g. Work, Weekly, Teams") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Notes Field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    placeholder = { Text("Add custom details, URLs, or passcodes...") },
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Color Picker Grid
                Text("Select Category Color", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                ColorSelectorGrid(
                    selectedColor = selectedColor,
                    onColorSelected = { selectedColor = it }
                )

                // Reminder Timings Checkboxes
                Text("Reminder Offsets", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ReminderOptions.forEach { (minutes, label) ->
                            val isChecked = activeOffsets.contains(minutes)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) activeOffsets.remove(minutes) else activeOffsets.add(minutes)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked == true) activeOffsets.add(minutes) else activeOffsets.remove(minutes)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColorSelectorGrid(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CuratedColors.forEach { color ->
            val argb = color.toArgb()
            val isSelected = argb == selectedColor
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.LightGray,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(argb) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Done,
                        contentDescription = "Selected",
                        tint = if (color == Color.White) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// Visual category accent colors
private val CuratedColors = listOf(
    Color(0xFF6200EE), // Purple
    Color(0xFF03DAC6), // Teal
    Color(0xFF3700B3), // Navy Blue
    Color(0xFFFF0266), // Soft Pink / Red
    Color(0xFFFFC107), // Amber / Yellow
    Color(0xFF4CAF50), // Green
    Color(0xFFE91E63)  // Deep Rose
)

private val ReminderOptions = listOf(
    0 to "At start of meeting",
    1 to "1 minute before",
    5 to "5 minutes before",
    10 to "10 minutes before",
    15 to "15 minutes before",
    30 to "30 minutes before"
)
