package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.DateRange
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
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDateRangePickerState
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
import com.example.data.ActivityDirection
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
import com.example.ui.theme.InBadgeFgDark
import com.example.ui.theme.InBadgeFgLight
import com.example.ui.theme.JakartaSansFamily
import com.example.ui.theme.OutBadgeFgDark
import com.example.ui.theme.OutBadgeFgLight
import com.example.ui.viewmodel.DateRangeFilter
import com.example.ui.viewmodel.LedgerDashboardData
import com.example.ui.viewmodel.LedgerFilter
import java.util.Calendar
import java.util.TimeZone
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

fun localDayEndMillis(millis: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }
    return cal.timeInMillis
}

fun utcPickerMillisToLocalDayStart(utcMillis: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcMillis
    }
    return Calendar.getInstance().apply {
        set(
            utcCal.get(Calendar.YEAR),
            utcCal.get(Calendar.MONTH),
            utcCal.get(Calendar.DAY_OF_MONTH),
            0,
            0,
            0
        )
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

fun localDayStartToUtcPickerMillis(localMillis: Long): Long {
    val localCal = Calendar.getInstance().apply {
        timeInMillis = localMillis
    }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(
            localCal.get(Calendar.YEAR),
            localCal.get(Calendar.MONTH),
            localCal.get(Calendar.DAY_OF_MONTH),
            0,
            0,
            0
        )
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

fun filterLedgerActivities(
    activities: List<ActivityRecord>,
    searchQuery: String,
    selectedFilter: LedgerFilter,
    selectedAddress: String?,
    selectedCategoryFilter: String?,
    dateRangeFilter: DateRangeFilter
): List<ActivityRecord> {
    val q = searchQuery.trim()
    val rangeStart = dateRangeFilter.startDateMillis?.let(::localDayStartMillis)
    val rangeEnd = (dateRangeFilter.endDateMillis ?: dateRangeFilter.startDateMillis)?.let(::localDayEndMillis)

    return activities.filter { act ->
        val matchesDirection = when (selectedFilter) {
            LedgerFilter.ALL -> true
            LedgerFilter.IN -> act.parsedDirection == ActivityDirection.IN
            LedgerFilter.OUT -> act.parsedDirection == ActivityDirection.OUT
            LedgerFilter.REMINDERS -> act.reminderEnabled
        }
        val matchesAddress = selectedAddress == null ||
            act.address.equals(selectedAddress, ignoreCase = true)
        val matchesCategory = selectedCategoryFilter == null ||
            act.eventType.equals(selectedCategoryFilter, ignoreCase = true)
        val matchesDateRange = (rangeStart == null || act.eventDateMillis >= rangeStart) &&
            (rangeEnd == null || act.eventDateMillis <= rangeEnd)
        val matchesSearch = q.isEmpty() ||
            act.name.contains(q, ignoreCase = true) ||
            act.familyId.contains(q, ignoreCase = true) ||
            act.fatherName.contains(q, ignoreCase = true) ||
            act.address.contains(q, ignoreCase = true) ||
            act.eventType.contains(q, ignoreCase = true) ||
            act.eventDetail.contains(q, ignoreCase = true) ||
            act.homeItems.contains(q, ignoreCase = true)
        matchesDirection && matchesAddress && matchesCategory && matchesDateRange && matchesSearch
    }
}

fun formatRelativeDayBadge(
    eventDateMillis: Long,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val todayStart = localDayStartMillis(nowMillis)
    val eventStart = localDayStartMillis(eventDateMillis)
    val deltaDays = TimeUnit.MILLISECONDS.toDays(eventStart - todayStart)
    return when {
        deltaDays < 0L -> "گزشتہ"
        deltaDays == 0L -> "آج"
        deltaDays == 1L -> "کل"
        deltaDays <= 365L -> "$deltaDays دن بعد"
        else -> "${deltaDays / 30} ماہ بعد"
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
    dateRangeFilter: DateRangeFilter = DateRangeFilter(),
    onDateRangeChange: (startMillis: Long?, endMillis: Long?, presetLabel: String?) -> Unit = { _, _, _ -> },
    onClearDateRange: () -> Unit = {},
    onManageCategoriesClick: () -> Unit,
    onNewEventClick: () -> Unit,
    onEventClick: (EventEntry) -> Unit,
    onActivityClick: (ActivityRecord) -> Unit,
    onEditActivityClick: (ActivityRecord) -> Unit = onActivityClick,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAllSavedEvents by remember { mutableStateOf(false) }
    var showDateRangeDialog by remember { mutableStateOf(false) }

    if (showDateRangeDialog) {
        LedgerDateRangePickerDialog(
            initialFilter = dateRangeFilter,
            onDismiss = { showDateRangeDialog = false },
            onClear = {
                onClearDateRange()
                showDateRangeDialog = false
            },
            onApply = { startMillis, endMillis ->
                onDateRangeChange(startMillis, endMillis, null)
                showDateRangeDialog = false
            }
        )
    }

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
                    contentDescription = "نیا اندراج شامل کریں"
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
                        selectedCategoryFilter,
                        dateRangeFilter
                    ) {
                        filterLedgerActivities(
                            activities = data.activities,
                            searchQuery = searchQuery,
                            selectedFilter = selectedFilter,
                            selectedAddress = selectedAddress,
                            selectedCategoryFilter = selectedCategoryFilter,
                            dateRangeFilter = dateRangeFilter
                        )
                    }

                    val periodBaseActivities = remember(
                        data.activities,
                        searchQuery,
                        selectedAddress,
                        selectedCategoryFilter,
                        dateRangeFilter
                    ) {
                        filterLedgerActivities(
                            activities = data.activities,
                            searchQuery = searchQuery,
                            selectedFilter = LedgerFilter.ALL,
                            selectedAddress = selectedAddress,
                            selectedCategoryFilter = selectedCategoryFilter,
                            dateRangeFilter = dateRangeFilter
                        )
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
                                placeholder = { Text("تقریب، نام، فیملی نمبر، پتہ یا قسم تلاش کریں...") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { onSearchQueryChange("") }) {
                                            Icon(Icons.Rounded.Clear, contentDescription = "تلاش صاف کریں")
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
                                                text = "آنے والی تقریبات (${upcomingOccasions.size})",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "ترمیم کے لیے کارڈ پر ٹیپ یا سوائپ کریں",
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
                                        label = { Text("نئی تقریب") },
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
                                        label = { Text("آنے والی") },
                                        modifier = Modifier.testTag("filter_upcoming_only_chip")
                                    )
                                    FilterChip(
                                        selected = showAllSavedEvents,
                                        onClick = { showAllSavedEvents = true },
                                        label = { Text("تمام محفوظ (${data.events.size})") },
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
                                            editLabel = "تقریب میں ترمیم",
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
                                            editLabel = "اندراج میں ترمیم",
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
                                    text = "کھاتہ اندراجات (${filteredList.size})",
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

                                DateRangeFilterBar(
                                    dateRangeFilter = dateRangeFilter,
                                    periodActivities = periodBaseActivities,
                                    onOpenPicker = { showDateRangeDialog = true },
                                    onApplyPreset = onDateRangeChange,
                                    onClearDateRange = onClearDateRange
                                )

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
                                        label = { Text("اقسام") },
                                        modifier = Modifier.testTag("manage_categories_chip")
                                    )
                                    FilterChip(
                                        selected = selectedCategoryFilter == null,
                                        onClick = { onCategoryFilterChange(null) },
                                        label = { Text("تمام اقسام") }
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
                                            label = { Text("تمام پتے") }
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
                                    editLabel = "ترمیم کریں",
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
    val isUrgent = label == "آج" || label == "کل"
    val isPast = label == "گزشتہ"
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
                        label = event.category.ifBlank { "تقریب" },
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
                        contentDescription = "تقریب میں ترمیم",
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
                        contentDescription = "تقریب میں ترمیم",
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
                text = "ابھی کوئی آنے والی تقریب نہیں",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (hasSavedEvents) {
                    "تمام محفوظ تقریبات گزر چکی ہیں۔ اوپر \"تمام محفوظ\" دبائیں یا نئی تقریب شامل کریں۔"
                } else {
                    "فون میں آف لائن محفوظ رکھنے کے لیے شادی، ولیمہ، سالگرہ یا خاندانی دعوت شامل کریں۔"
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
                    Text("تقریب شامل کریں")
                }
                if (hasSavedEvents) {
                    AssistChip(
                        onClick = onShowAllSaved,
                        label = { Text("گزشتہ دیکھیں") }
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
                            if (record.fatherName.isNotBlank()) add("ولدیت: ${record.fatherName}")
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
                        ReminderStatusBadge(label = "یاددہانی")
                    }
                }

                Text(
                    text = "رقم: ${formatNeondraAmount(record.neondraAmount)}",
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
                text = if (hasAnyData) "کوئی مماثل اندراج نہیں ملا" else "ابھی تک کوئی کھاتہ اندراج نہیں",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (hasAnyData) {
                    "فلٹرز یا تلاش کے الفاظ تبدیل کر کے دیکھیں۔"
                } else {
                    "اپنی پہلی تقریب، تحفہ یا نیوندرا درج کرنے کے لیے نیچے (+) بٹن دبائیں۔ خاندان کے نام، فیملی نمبر اور پتے خودکار محفوظ ہو جائیں گے۔"
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
                            text = "پہلا اندراج شامل کریں",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateRangeFilterBar(
    dateRangeFilter: DateRangeFilter,
    periodActivities: List<ActivityRecord>,
    onOpenPicker: () -> Unit,
    onApplyPreset: (startMillis: Long?, endMillis: Long?, presetLabel: String?) -> Unit,
    onClearDateRange: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val inColor = if (isDark) InBadgeFgDark else InBadgeFgLight
    val outColor = if (isDark) OutBadgeFgDark else OutBadgeFgLight

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = onOpenPicker,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.DateRange,
                        contentDescription = "تاریخ منتخب کریں",
                        tint = if (dateRangeFilter.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(
                        text = if (dateRangeFilter.isActive && dateRangeFilter.presetLabel == null) {
                            formatDateRangeLabel(dateRangeFilter)
                        } else {
                            "تاریخ منتخب کریں"
                        },
                        fontWeight = if (dateRangeFilter.isActive && dateRangeFilter.presetLabel == null) FontWeight.Bold else FontWeight.Medium
                    )
                },
                modifier = Modifier.testTag("date_range_picker_button")
            )

            FilterChip(
                selected = !dateRangeFilter.isActive,
                onClick = onClearDateRange,
                label = { Text("تمام اوقات") },
                modifier = Modifier.testTag("date_range_all_time_chip")
            )

            FilterChip(
                selected = dateRangeFilter.presetLabel == "آج",
                onClick = {
                    if (dateRangeFilter.presetLabel == "آج") {
                        onClearDateRange()
                    } else {
                        val todayStart = localDayStartMillis(System.currentTimeMillis())
                        onApplyPreset(todayStart, localDayEndMillis(todayStart), "آج")
                    }
                },
                label = { Text("آج") },
                modifier = Modifier.testTag("date_range_preset_today")
            )

            FilterChip(
                selected = dateRangeFilter.presetLabel == "گزشتہ 7 دن",
                onClick = {
                    if (dateRangeFilter.presetLabel == "گزشتہ 7 دن") {
                        onClearDateRange()
                    } else {
                        val todayEnd = localDayEndMillis(System.currentTimeMillis())
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = localDayStartMillis(System.currentTimeMillis())
                            add(Calendar.DAY_OF_YEAR, -6)
                        }
                        onApplyPreset(cal.timeInMillis, todayEnd, "گزشتہ 7 دن")
                    }
                },
                label = { Text("گزشتہ 7 دن") },
                modifier = Modifier.testTag("date_range_preset_7d")
            )

            FilterChip(
                selected = dateRangeFilter.presetLabel == "اس ماہ",
                onClick = {
                    if (dateRangeFilter.presetLabel == "اس ماہ") {
                        onClearDateRange()
                    } else {
                        val now = System.currentTimeMillis()
                        val monthStart = Calendar.getInstance().apply {
                            timeInMillis = localDayStartMillis(now)
                            set(Calendar.DAY_OF_MONTH, 1)
                        }.timeInMillis
                        onApplyPreset(monthStart, localDayEndMillis(now), "اس ماہ")
                    }
                },
                label = { Text("اس ماہ") },
                modifier = Modifier.testTag("date_range_preset_month")
            )

            FilterChip(
                selected = dateRangeFilter.presetLabel == "گزشتہ 30 دن",
                onClick = {
                    if (dateRangeFilter.presetLabel == "گزشتہ 30 دن") {
                        onClearDateRange()
                    } else {
                        val todayEnd = localDayEndMillis(System.currentTimeMillis())
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = localDayStartMillis(System.currentTimeMillis())
                            add(Calendar.DAY_OF_YEAR, -29)
                        }
                        onApplyPreset(cal.timeInMillis, todayEnd, "گزشتہ 30 دن")
                    }
                },
                label = { Text("گزشتہ 30 دن") },
                modifier = Modifier.testTag("date_range_preset_30d")
            )

            FilterChip(
                selected = dateRangeFilter.presetLabel == "اس سال",
                onClick = {
                    if (dateRangeFilter.presetLabel == "اس سال") {
                        onClearDateRange()
                    } else {
                        val now = System.currentTimeMillis()
                        val yearStart = Calendar.getInstance().apply {
                            timeInMillis = localDayStartMillis(now)
                            set(Calendar.MONTH, Calendar.JANUARY)
                            set(Calendar.DAY_OF_MONTH, 1)
                        }.timeInMillis
                        onApplyPreset(yearStart, localDayEndMillis(now), "اس سال")
                    }
                },
                label = { Text("اس سال") },
                modifier = Modifier.testTag("date_range_preset_year")
            )
        }

        if (dateRangeFilter.isActive) {
            val periodIn = periodActivities
                .filter { it.parsedDirection == ActivityDirection.IN }
                .sumOf { it.neondraAmount }
            val periodOut = periodActivities
                .filter { it.parsedDirection == ActivityDirection.OUT }
                .sumOf { it.neondraAmount }
            val periodNet = periodIn - periodOut
            val netPrefix = if (periodNet >= 0) "+" else ""

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_date_range_summary")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = onOpenPicker)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = formatDateRangeLabel(dateRangeFilter),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onClearDateRange,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("date_range_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "تاریخ فلٹر صاف کریں",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "آمدن: ${formatNeondraAmount(periodIn)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = inColor
                        )
                        Text(
                            text = "خرچ: ${formatNeondraAmount(periodOut)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = outColor
                        )
                        Text(
                            text = "خالص: $netPrefix${formatNeondraAmount(periodNet)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

private fun formatDateRangeLabel(filter: DateRangeFilter): String {
    val start = filter.startDateMillis
    val end = filter.endDateMillis
    val rangeText = when {
        start != null && end != null -> {
            val s = formatDisplayDate(start)
            val e = formatDisplayDate(end)
            if (s == e) s else "$s – $e"
        }
        start != null -> "${formatDisplayDate(start)} سے"
        end != null -> "${formatDisplayDate(end)} تک"
        else -> "تمام اوقات"
    }
    return if (!filter.presetLabel.isNullOrBlank()) {
        "${filter.presetLabel} ($rangeText)"
    } else {
        rangeText
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerDateRangePickerDialog(
    initialFilter: DateRangeFilter,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onApply: (startMillis: Long?, endMillis: Long?) -> Unit
) {
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialFilter.startDateMillis?.let(::localDayStartToUtcPickerMillis),
        initialSelectedEndDateMillis = (initialFilter.endDateMillis ?: initialFilter.startDateMillis)
            ?.let(::localDayStartToUtcPickerMillis)
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val startLocal = pickerState.selectedStartDateMillis?.let(::utcPickerMillisToLocalDayStart)
                    val rawEndUtc = pickerState.selectedEndDateMillis ?: pickerState.selectedStartDateMillis
                    val endLocal = rawEndUtc?.let { localDayEndMillis(utcPickerMillisToLocalDayStart(it)) }
                    onApply(startLocal, endLocal)
                },
                enabled = pickerState.selectedStartDateMillis != null,
                modifier = Modifier.testTag("date_range_apply_button")
            ) {
                Text("لاگو کریں", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (initialFilter.isActive || pickerState.selectedStartDateMillis != null) {
                    TextButton(onClick = onClear) {
                        Text("صاف کریں")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("منسوخ کریں")
                }
            }
        }
    ) {
        DateRangePicker(
            state = pickerState,
            title = {
                Text(
                    text = "تاریخ کے مطابق آمدن اور خرچ فلٹر کریں",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)
                )
            },
            showModeToggle = true,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
        )
    }
}

