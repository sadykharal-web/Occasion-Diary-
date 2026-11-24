package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ActivityRecord
import com.example.data.EventEntry
import com.example.data.UiState
import com.example.ui.components.CategoryBadge
import com.example.ui.components.DirectionPill
import com.example.ui.components.HeroLedgerSummaryCard
import com.example.ui.components.ReminderStatusBadge
import com.example.ui.components.formatDisplayDate
import com.example.ui.components.formatDisplayTime
import com.example.ui.components.formatNeondraAmount
import com.example.ui.theme.JakartaSansFamily
import com.example.ui.viewmodel.LedgerDashboardData
import com.example.ui.viewmodel.LedgerFilter
import java.util.Calendar
import java.util.concurrent.TimeUnit

sealed interface UpcomingOccasionItem {
    val uniqueKey: String
    val eventDateMillis: Long

    data class Event(val entry: EventEntry) : UpcomingOccasionItem {
        override val uniqueKey: String = "event_${entry.id}"
        override val eventDateMillis: Long = entry.eventDateMillis
    }

    data class Activity(val record: ActivityRecord) : UpcomingOccasionItem {
        override val uniqueKey: String = "act_${record.id}"
        override val eventDateMillis: Long = record.eventDateMillis
    }
}

fun localDayStartMillis(millis: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

fun formatRelativeDayBadge(
    eventDateMillis: Long,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val todayStart = localDayStartMillis(nowMillis)
    val eventStart = localDayStartMillis(eventDateMillis)
    val deltaDays = TimeUnit.MILLISECONDS.toDays(eventStart - todayStart)
    return when {
        deltaDays < 0L -> "Past"
        deltaDays == 0L -> "Today"
        deltaDays == 1L -> "Tomorrow"
        deltaDays <= 365L -> "In $deltaDays days"
        else -> "In ${deltaDays / 30} mo"
    }
}

fun buildUpcomingOccasionItems(
    events: List<EventEntry>,
    activities: List<ActivityRecord>,
    showAllSavedEvents: Boolean,
    searchQuery: String,
    selectedCategoryFilter: String?,
    nowMillis: Long = System.currentTimeMillis()
): List<UpcomingOccasionItem> {
    val todayStart = localDayStartMillis(nowMillis)
    val q = searchQuery.trim()

    val eventItems = events
        .filter { evt ->
            val matchesTime = showAllSavedEvents || evt.eventDateMillis >= todayStart
            val matchesCategory = selectedCategoryFilter == null ||
                evt.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesSearch = q.isEmpty() ||
                evt.title.contains(q, ignoreCase = true) ||
                evt.description.contains(q, ignoreCase = true) ||
                evt.category.contains(q, ignoreCase = true) ||
                evt.location.contains(q, ignoreCase = true) ||
                evt.familyName.contains(q, ignoreCase = true)
            matchesTime && matchesCategory && matchesSearch
        }
        .map { UpcomingOccasionItem.Event(it) }

    val activityItems = activities
        .filter { act ->
            val matchesTime = act.eventDateMillis >= todayStart
            val matchesCategory = selectedCategoryFilter == null ||
                act.eventType.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesSearch = q.isEmpty() ||
                act.name.contains(q, ignoreCase = true) ||
                act.eventType.contains(q, ignoreCase = true) ||
                act.eventDetail.contains(q, ignoreCase = true) ||
                act.address.contains(q, ignoreCase = true)
            matchesTime && matchesCategory && matchesSearch
        }
        .map { UpcomingOccasionItem.Activity(it) }

    return (eventItems + activityItems).sortedBy { it.eventDateMillis }
}

@Composable
fun LedgerScreen(
    state: UiState<LedgerDashboardData>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: LedgerFilter,
    onFilterChange: (LedgerFilter) -> Unit,
    selectedAddress: String?,
    onAddressFilterChange: (String?) -> Unit,
    selectedCategoryFilter: String?,
    onCategoryFilterChange: (String?) -> Unit,
    onManageCategoriesClick: () -> Unit,
    onNewEventClick: () -> Unit,
    onEventClick: (EventEntry) -> Unit,
    onActivityClick: (ActivityRecord) -> Unit,
    onEditActivityClick: (ActivityRecord) -> Unit = onActivityClick,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAllSavedEvents by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_activity_fab")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add New Entry"
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (state) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is UiState.Success -> {
                    val data = state.data
                    val nowMillis = remember(data.events, data.activities) { System.currentTimeMillis() }

                    val upcomingOccasions = remember(
                        data.events,
                        data.activities,
                        showAllSavedEvents,
                        searchQuery,
                        selectedCategoryFilter,
                        nowMillis
                    ) {
                        buildUpcomingOccasionItems(
                            events = data.events,
                            activities = data.activities,
                            showAllSavedEvents = showAllSavedEvents,
                            searchQuery = searchQuery,
                            selectedCategoryFilter = selectedCategoryFilter,
                            nowMillis = nowMillis
                        )
                    }

                    val filteredList = remember(
                        data.activities,
                        searchQuery,
                        selectedFilter,
                        selectedAddress,
                        selectedCategoryFilter
                    ) {
                        val q = searchQuery.trim()
                        data.activities.filter { act ->
                            val matchesDirection = when (selectedFilter) {
                                LedgerFilter.ALL -> true
                                LedgerFilter.IN -> act.parsedDirection.wireValue == "IN"
                                LedgerFilter.OUT -> act.parsedDirection.wireValue == "OUT"
                                LedgerFilter.REMINDERS -> act.reminderEnabled
                            }
                            val matchesAddress = selectedAddress == null ||
                                act.address.equals(selectedAddress, ignoreCase = true)
                            val matchesCategory = selectedCategoryFilter == null ||
                                act.eventType.equals(selectedCategoryFilter, ignoreCase = true)
                            val matchesSearch = q.isEmpty() ||
                                act.name.contains(q, ignoreCase = true) ||
                                act.familyId.contains(q, ignoreCase = true) ||
                                act.fatherName.contains(q, ignoreCase = true) ||
                                act.address.contains(q, ignoreCase = true) ||
                                act.eventType.contains(q, ignoreCase = true) ||
                                act.eventDetail.contains(q, ignoreCase = true) ||
                                act.homeItems.contains(q, ignoreCase = true)
                            matchesDirection && matchesAddress && matchesCategory && matchesSearch
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxSize()
                            .testTag("main_occasions_lazy_column"),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "hero_summary") {
                            HeroLedgerSummaryCard(summary = data.summary)
                        }

                        item(key = "search_bar") {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = { Text("Search occasions, name, FN, address, or category...") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { onSearchQueryChange("") }) {
                                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ledger_search_input")
                            )
                        }

                        item(key = "upcoming_occasions_header") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.EventAvailable,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column {
                                            Text(
                                                text = "Upcoming Occasions (${upcomingOccasions.size})",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Tap or swipe right on a card to edit in Firestore",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    AssistChip(
                                        onClick = onNewEventClick,
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.Event,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text("New Occasion") },
                                        modifier = Modifier.testTag("ledger_new_event_chip")
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = !showAllSavedEvents,
                                        onClick = { showAllSavedEvents = false },
                                        label = { Text("Upcoming") },
                                        modifier = Modifier.testTag("filter_upcoming_only_chip")
                                    )
                                    FilterChip(
                                        selected = showAllSavedEvents,
                                        onClick = { showAllSavedEvents = true },
                                        label = { Text("All Saved (${data.events.size})") },
                                        modifier = Modifier.testTag("filter_all_saved_events_chip")
                                    )
                                }
                            }
                        }

                        if (upcomingOccasions.isEmpty()) {
                            item(key = "upcoming_occasions_empty") {
                                EmptyUpcomingOccasionsCard(
                                    hasSavedEvents = data.events.isNotEmpty(),
                                    onNewEventClick = onNewEventClick,
                                    onShowAllSaved = { showAllSavedEvents = true }
                                )
                            }
                        } else {
                            items(
                                items = upcomingOccasions,
                                key = { "upcoming_${it.uniqueKey}" }
                            ) { item ->
                                val badgeLabel = formatRelativeDayBadge(item.eventDateMillis, nowMillis)
                                when (item) {
                                    is UpcomingOccasionItem.Event -> {
                                        val (evtColor, evtIcon) = data.catalog.resolveCategoryVisual(item.entry.category)
                                        SwipeToEditContainer(
                                            editLabel = "Edit Occasion",
                                            onEdit = { onEventClick(item.entry) }
                                        ) {
                                            UpcomingEventOccasionCard(
                                                event = item.entry,
                                                categoryColor = evtColor,
                                                categoryIcon = evtIcon,
                                                relativeBadge = badgeLabel,
                                                onClick = { onEventClick(item.entry) },
                                                onEditClick = { onEventClick(item.entry) }
                                            )
                                        }
                                    }

                                    is UpcomingOccasionItem.Activity -> {
                                        val (actColor, actIcon) = data.catalog.resolveCategoryVisual(item.record.eventType)
                                        SwipeToEditContainer(
                                            editLabel = "Edit Occasion",
                                            onEdit = { onEditActivityClick(item.record) }
                                        ) {
                                            UpcomingActivityOccasionCard(
                                                record = item.record,
                                                categoryColor = actColor,
                                                categoryIcon = actIcon,
                                                relativeBadge = badgeLabel,
                                                onClick = { onActivityClick(item.record) },
                                                onEditClick = { onEditActivityClick(item.record) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "ledger_filters_header") {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Ledger Entries (${filteredList.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LedgerFilter.entries.forEach { filter ->
                                        FilterChip(
                                            selected = selectedFilter == filter,
                                            onClick = { onFilterChange(filter) },
                                            label = { Text(filter.label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AssistChip(
                                        onClick = onManageCategoriesClick,
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.Tune,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text("Categories") },
                                        modifier = Modifier.testTag("manage_categories_chip")
                                    )
                                    FilterChip(
                                        selected = selectedCategoryFilter == null,
                                        onClick = { onCategoryFilterChange(null) },
                                        label = { Text("All Categories") }
                                    )
                                    data.catalog.eventTypes.forEach { catName ->
                                        val (catColor, catIcon) = data.catalog.resolveCategoryVisual(catName)
                                        FilterChip(
                                            selected = selectedCategoryFilter == catName,
                                            onClick = {
                                                onCategoryFilterChange(
                                                    if (selectedCategoryFilter == catName) null else catName
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = catIcon,
                                                    contentDescription = null,
                                                    tint = catColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = { Text(catName) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = catColor.copy(alpha = 0.16f),
                                                selectedLabelColor = catColor
                                            )
                                        )
                                    }
                                }

                                if (data.catalog.addresses.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        FilterChip(
                                            selected = selectedAddress == null,
                                            onClick = { onAddressFilterChange(null) },
                                            label = { Text("All Addresses") }
                                        )
                                        data.catalog.addresses.forEach { addr ->
                                            FilterChip(
                                                selected = selectedAddress == addr,
                                                onClick = {
                                                    onAddressFilterChange(if (selectedAddress == addr) null else addr)
                                                },
                                                label = { Text(addr) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (filteredList.isEmpty()) {
                            item(key = "ledger_empty") {
                                EmptyLedgerState(
                                    hasAnyData = data.activities.isNotEmpty(),
                                    onAddClick = onAddClick
                                )
                            }
                        } else {
                            items(
                                items = filteredList,
                                key = { "ledger_${it.id}" }
                            ) { activity ->
                                val (catColor, catIcon) = data.catalog.resolveCategoryVisual(activity.eventType)
                                SwipeToEditContainer(
                                    editLabel = "Edit Entry",
                                    onEdit = { onEditActivityClick(activity) }
                                ) {
                                    ActivitySummaryCard(
                                        record = activity,
                                        categoryColor = catColor,
                                        categoryIcon = catIcon,
                                        onClick = { onActivityClick(activity) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToEditContainer(
    editLabel: String,
    onEdit: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                onEdit()
            }
            // Return false so the card snaps back smoothly after launching edit.
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = editLabel,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = editLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    ) {
        content()
    }
}

@Composable
private fun RelativeDayPill(
    label: String,
    modifier: Modifier = Modifier
) {
    val isUrgent = label == "Today" || label == "Tomorrow"
    val isPast = label == "Past"
    val containerColor = when {
        isUrgent -> MaterialTheme.colorScheme.tertiaryContainer
        isPast -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when {
        isUrgent -> MaterialTheme.colorScheme.onTertiaryContainer
        isPast -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun UpcomingEventOccasionCard(
    event: EventEntry,
    categoryColor: Color,
    categoryIcon: ImageVector,
    relativeBadge: String,
    onClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("upcoming_event_card_${event.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    RelativeDayPill(label = relativeBadge)
                    CategoryBadge(
                        label = event.category.ifBlank { "Occasion" },
                        color = categoryColor,
                        icon = categoryIcon
                    )
                }
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("edit_upcoming_event_${event.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit occasion",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (event.description.isNotBlank()) {
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${formatDisplayDate(event.eventDateMillis)} • ${formatDisplayTime(event.eventDateMillis)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = JakartaSansFamily
                    )
                }
            }

            if (event.location.isNotBlank() || event.familyName.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (event.location.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = event.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (event.familyName.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = event.familyName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingActivityOccasionCard(
    record: ActivityRecord,
    categoryColor: Color,
    categoryIcon: ImageVector,
    relativeBadge: String,
    onClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("upcoming_activity_card_${record.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    RelativeDayPill(label = relativeBadge)
                    CategoryBadge(
                        label = record.eventType,
                        color = categoryColor,
                        icon = categoryIcon
                    )
                    DirectionPill(direction = record.parsedDirection)
                }
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("edit_upcoming_activity_${record.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit ledger occasion",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = if (record.eventDetail.isNotBlank()) {
                    "${record.name} — ${record.eventDetail}"
                } else {
                    record.name
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${formatDisplayDate(record.eventDateMillis)} • ${formatDisplayTime(record.eventDateMillis)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = JakartaSansFamily
                    )
                }
                Text(
                    text = formatNeondraAmount(record.neondraAmount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmptyUpcomingOccasionsCard(
    hasSavedEvents: Boolean,
    onNewEventClick: () -> Unit,
    onShowAllSaved: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.EventAvailable,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "No upcoming occasions yet",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (hasSavedEvents) {
                    "All saved events are in the past. Tap \"All Saved\" above or create a new upcoming occasion."
                } else {
                    "Add a wedding, birthday, walima, or family gathering to track it here and sync with Firestore."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onNewEventClick,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("empty_upcoming_add_event_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Occasion")
                }
                if (hasSavedEvents) {
                    AssistChip(
                        onClick = onShowAllSaved,
                        label = { Text("View Past") }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivitySummaryCard(
    record: ActivityRecord,
    categoryColor: Color,
    categoryIcon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("activity_card_${record.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (record.familyId.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "FN ${record.familyId}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontFamily = JakartaSansFamily,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = record.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val meta = buildList {
                            if (record.fatherName.isNotBlank()) add("Father: ${record.fatherName}")
                            if (record.address.isNotBlank()) add(record.address)
                        }.joinToString(" • ")
                        if (meta.isNotEmpty()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                DirectionPill(direction = record.parsedDirection)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryBadge(
                    label = record.eventType,
                    color = categoryColor,
                    icon = categoryIcon
                )
                if (record.eventDetail.isNotBlank()) {
                    Text(
                        text = record.eventDetail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (record.homeItems.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = record.homeItems,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = formatDisplayDate(record.eventDateMillis),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = JakartaSansFamily
                    )
                    if (record.reminderEnabled) {
                        ReminderStatusBadge(label = "Reminder")
                    }
                }

                Text(
                    text = "Amount: ${formatNeondraAmount(record.neondraAmount)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmptyLedgerState(
    hasAnyData: Boolean,
    onAddClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (hasAnyData) Icons.Rounded.Search else Icons.AutoMirrored.Rounded.EventNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = if (hasAnyData) "No matching entries found" else "No Ledger Entries Yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (hasAnyData) {
                    "Try adjusting your filters or search terms."
                } else {
                    "Tap the (+) button below to record your first occasion, gift, or transaction. Family names, FN IDs, and addresses sync automatically into dropdowns."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!hasAnyData) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.clickable(onClick = onAddClick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Add First Entry",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}
