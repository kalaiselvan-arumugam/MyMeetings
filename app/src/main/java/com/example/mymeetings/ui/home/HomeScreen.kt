package com.example.mymeetings.ui.home

import android.text.format.DateUtils
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.layout
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mymeetings.data.parser.RruleEvaluator
import com.example.mymeetings.domain.model.Meeting
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToScanner: () -> Unit,
    onNavigateToDetails: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedTag by viewModel.selectedTag.collectAsStateWithLifecycle()
    val selectedRecurrence by viewModel.selectedRecurrence.collectAsStateWithLifecycle()

    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("MyMeetings", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToScanner,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = FloatingCornerShape()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Scan QR Code")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan QR", fontWeight = FontWeight.Medium)
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Search Input Row
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search meetings by title, host, tags...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            imageVector = if (showFilters) Icons.Default.KeyboardArrowUp else Icons.Default.List,
                            contentDescription = "Toggle Filters"
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Filtering Options Row
            AnimatedVisibility(visible = showFilters) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    // Recurrence Type Filter Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        FilterChip(
                            selected = selectedRecurrence == null,
                            onClick = { viewModel.selectRecurrenceFilter(null) },
                            label = { Text("All") }
                        )
                        FilterChip(
                            selected = selectedRecurrence == "one-time",
                            onClick = { viewModel.selectRecurrenceFilter("one-time") },
                            label = { Text("One-time") }
                        )
                        FilterChip(
                            selected = selectedRecurrence == "recurring",
                            onClick = { viewModel.selectRecurrenceFilter("recurring") },
                            label = { Text("Recurring") }
                        )
                    }

                    if (uiState is HomeUiState.Success) {
                        val tags = (uiState as HomeUiState.Success).availableTags
                        if (tags.isNotEmpty()) {
                            Text(
                                "Tags",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item {
                                    InputChip(
                                        selected = selectedTag == null,
                                        onClick = { viewModel.selectTag(null) },
                                        label = { Text("Any Tag") }
                                    )
                                }
                                items(tags) { tag ->
                                    InputChip(
                                        selected = selectedTag == tag,
                                        onClick = { viewModel.selectTag(if (selectedTag == tag) null else tag) },
                                        label = { Text(tag) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Main List Content
            when (val state = uiState) {
                HomeUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is HomeUiState.Success -> {
                    if (state.meetings.isEmpty()) {
                        EmptyStateView()
                    } else {
                        MeetingGroupedList(
                            meetings = state.meetings,
                            onMeetingClick = onNavigateToDetails,
                            onToggleEnabled = { viewModel.toggleMeetingEnabled(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No Meetings Scheduled",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Scan a QR code from a meeting invite or open an iCalendar (.ics) file to start receiving offline notifications and quick links.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun MeetingGroupedList(
    meetings: List<Meeting>,
    onMeetingClick: (Long) -> Unit,
    onToggleEnabled: (Meeting) -> Unit
) {
    val now = System.currentTimeMillis()
    val zoneId = ZoneId.systemDefault()
    val startOfToday = LocalDate.now().atStartOfDay(zoneId).toInstant().toEpochMilli()
    val startOfTomorrow = LocalDate.now().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val startOfNextDay = LocalDate.now().plusDays(2).atStartOfDay(zoneId).toInstant().toEpochMilli()

    // Categorize meetings dynamically by their next occurrence
    val grouped = meetings.groupBy { meeting ->
        val nextOccurrence = getNextOccurrenceTime(meeting)
        when {
            nextOccurrence < startOfToday -> "Past / Completed"
            nextOccurrence in startOfToday until startOfTomorrow -> "Today"
            nextOccurrence in startOfTomorrow until startOfNextDay -> "Tomorrow"
            else -> "Upcoming"
        }
    }.toSortedMap(compareBy {
        when (it) {
            "Today" -> 1
            "Tomorrow" -> 2
            "Upcoming" -> 3
            "Past / Completed" -> 4
            else -> 5
        }
    })

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        grouped.forEach { (category, categoryMeetings) ->
            item {
                Text(
                    text = category,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(categoryMeetings, key = { it.uid }) { meeting ->
                MeetingItemCard(
                    meeting = meeting,
                    onMeetingClick = onMeetingClick,
                    onToggleEnabled = onToggleEnabled
                )
            }
        }
    }
}

@Composable
fun MeetingItemCard(
    meeting: Meeting,
    onMeetingClick: (Long) -> Unit,
    onToggleEnabled: (Meeting) -> Unit
) {
    val nextOccurrence = getNextOccurrenceTime(meeting)
    
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
        .withZone(ZoneId.systemDefault())
    val formatStartTime = timeFormatter.format(Instant.ofEpochMilli(nextOccurrence))
    val formatEndTime = timeFormatter.format(Instant.ofEpochMilli(nextOccurrence + (meeting.endTime - meeting.startTime)))

    val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM dd")
        .withZone(ZoneId.systemDefault())
    val formatStartDate = dateFormatter.format(Instant.ofEpochMilli(nextOccurrence))

    val colorAccent = meeting.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onMeetingClick(meeting.id) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Color tag bar on the left
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (meeting.enabled) colorAccent else Color.Gray)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title and Enabled Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = meeting.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = meeting.enabled,
                        onCheckedChange = { onToggleEnabled(meeting) },
                        modifier = Modifier.scale(0.7f)
                    )
                }

                // Date & Time
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow, // Time icon representation
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$formatStartDate • $formatStartTime - $formatEndTime",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Recurrence Rule String
                if (!meeting.rrule.isNullOrBlank()) {
                    Text(
                        text = RruleEvaluator.toHumanReadable(meeting.rrule),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Organizer details
                meeting.organizer?.let {
                    Text(
                        text = "Organizer: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Tags chips
                if (meeting.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        meeting.tags.take(3).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Float custom shape helper
@Composable
fun FloatingCornerShape() = RoundedCornerShape(16.dp)

fun getNextOccurrenceTime(meeting: Meeting): Long {
    val now = System.currentTimeMillis()
    val endOfTime = now + 365L * 24 * 60 * 60 * 1000
    val occurrences = RruleEvaluator.getOccurrences(meeting, now, endOfTime)
    return occurrences.firstOrNull() ?: meeting.startTime
}

// Extension modifier to scale switch
fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout((placeable.width * scale).toInt(), (placeable.height * scale).toInt()) {
            placeable.placeRelative(
                ((placeable.width * scale - placeable.width) / 2).toInt(),
                ((placeable.height * scale - placeable.height) / 2).toInt()
            )
        }
    }
)
