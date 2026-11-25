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
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.example.ui.components.formatNeondraAmount
import com.example.ui.theme.InBadgeFgDark
import com.example.ui.theme.InBadgeFgLight
import com.example.ui.theme.JakartaSansFamily
import com.example.ui.theme.NotoArabicFamily
import com.example.ui.theme.OutBadgeFgDark
import com.example.ui.theme.OutBadgeFgLight
import com.example.ui.viewmodel.DateRangeFilter
import com.example.ui.viewmodel.LedgerDashboardData
import com.example.ui.viewmodel.LedgerFilter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class SameDateEventGroup(
    val groupKey: String,
    val eventName: String,
    val eventDayStartMillis: Long,
    val eventTypes: List<String>,
    val entries: List<ActivityRecord>,
    val totalIn: Long,
    val totalOut: Long
) {
    val netBalance: Long get() = totalIn - totalOut
}

fun buildSameDateEventLists(activities: List<ActivityRecord>): List<SameDateEventGroup> {
    return activities
        .filter { it.eventDetail.trim().isNotEmpty() }
        .groupBy { act ->
            val dayStart = localDayStartMillis(act.eventDateMillis)
            val normalizedName = act.eventDetail.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ENGLISH)
            "${dayStart}__$normalizedName"
        }
        .map { (key, groupEntries) ->
            val sortedEntries = groupEntries.sortedByDescending { it.eventDateMillis }
            val displayName = sortedEntries.first().eventDetail.trim().replace(Regex("\\s+"), " ")
            val dayStart = localDayStartMillis(sortedEntries.first().eventDateMillis)
            val eventTypes = sortedEntries.map { it.eventType.trim() }.filter { it.isNotEmpty() }.distinct()
            val totalIn = sortedEntries.filter { it.parsedDirection == ActivityDirection.IN }.sumOf { it.totalAmount }
            val totalOut = sortedEntries.filter { it.parsedDirection == ActivityDirection.OUT }.sumOf { it.totalAmount }
            SameDateEventGroup(
                groupKey = key,
                eventName = displayName,
                eventDayStartMillis = dayStart,
                eventTypes = eventTypes,
                entries = sortedEntries,
                totalIn = totalIn,
                totalOut = totalOut
            )
        }
        .sortedWith(
            compareByDescending<SameDateEventGroup> { it.eventDayStartMillis }
                .thenByDescending { it.entries.size }
                .thenBy { it.eventName }
        )
}

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

@OptIn(ExperimentalMaterial3Api::class)
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
    onQuickNeondraClick: () -> Unit = {},
    onActivityClick: (ActivityRecord) -> Unit,
    onEditActivityClick: (ActivityRecord) -> Unit = onActivityClick,
    onAddToEventList: (eventName: String, eventDateMillis: Long, eventType: String) -> Unit = { _, _, _ -> },
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDateRangeDialog by remember { mutableStateOf(false) }
    var showEventListsMode by remember { mutableStateOf(false) }

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
                    contentDescription = "Add Ledger Entry"
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

                    val sameDateEventLists = remember(filteredList) {
                        buildSameDateEventLists(filteredList)
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

                        item(key = "quick_actions_row") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = onQuickNeondraClick,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("ledger_quick_neondra_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AccountBalanceWallet,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Neondra", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = onAddClick,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("ledger_new_entry_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Full Entry", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        item(key = "search_bar") {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = { Text("Search name, FN, father, address, event name or event type...") },
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

                        item(key = "ledger_view_mode_toggle") {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = !showEventListsMode,
                                    onClick = { showEventListsMode = false },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                    modifier = Modifier.testTag("ledger_mode_all_entries")
                                ) {
                                    Text("All Entries (${filteredList.size})")
                                }
                                SegmentedButton(
                                    selected = showEventListsMode,
                                    onClick = { showEventListsMode = true },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    modifier = Modifier.testTag("ledger_mode_event_lists")
                                ) {
                                    Text("Event Lists (${sameDateEventLists.size})")
                                }
                            }
                        }

                        item(key = "ledger_filters_header") {
                            Column(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (showEventListsMode) {
                                        "Same-Date Event Lists (${sameDateEventLists.size})"
                                    } else {
                                        "Ledger Entries (${filteredList.size})"
                                    },
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
                                        label = { Text("Event Types") },
                                        modifier = Modifier.testTag("manage_categories_chip")
                                    )
                                    FilterChip(
                                        selected = selectedCategoryFilter == null,
                                        onClick = { onCategoryFilterChange(null) },
                                        label = { Text("All Event Types") }
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

                        if (showEventListsMode) {
                            if (sameDateEventLists.isEmpty()) {
                                item(key = "event_lists_empty") {
                                    EmptyEventListsCard(onAddClick = onAddClick)
                                }
                            } else {
                                items(
                                    items = sameDateEventLists,
                                    key = { "event_group_${it.groupKey}" }
                                ) { group ->
                                    SameDateEventGroupCard(
                                        group = group,
                                        catalog = data.catalog,
                                        onEntryClick = onActivityClick,
                                        onAddToEventClick = {
                                            onAddToEventList(
                                                group.eventName,
                                                group.eventDayStartMillis,
                                                group.eventTypes.firstOrNull().orEmpty()
                                            )
                                        }
                                    )
                                }
                            }
                        } else if (filteredList.isEmpty()) {
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
                                    editLabel = "Edit",
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
                        label = event.category.ifBlank { "Event" },
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
                        contentDescription = "Edit Event",
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
                        text = formatDisplayDate(event.eventDateMillis),
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
                        contentDescription = "Edit Entry",
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
                        text = formatDisplayDate(record.eventDateMillis),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = JakartaSansFamily
                    )
                }
                Text(
                    text = formatNeondraAmount(record.totalAmount),
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
                    "All saved events are in the past. Tap \"All Saved\" above or add a new event."
                } else {
                    "Add a wedding, dinner, birthday, or family gathering to keep it saved offline on your device."
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
                    Text("Add Event")
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

            val hasMoneyBreakdown = record.cashAmount > 0L || record.haarAmount > 0L || record.salamiAmount > 0L
            if (hasMoneyBreakdown) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (record.cashAmount > 0L) {
                        MoneyTagChip(label = "نقد", amount = record.cashAmount)
                    }
                    if (record.haarAmount > 0L) {
                        MoneyTagChip(label = "ہار", amount = record.haarAmount)
                    }
                    if (record.salamiAmount > 0L) {
                        MoneyTagChip(label = "سلامی", amount = record.salamiAmount)
                    }
                    if (record.neondraAmount > 0L) {
                        MoneyTagChip(label = "Neondra", amount = record.neondraAmount)
                    }
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
                    text = "Amount: ${formatNeondraAmount(record.totalAmount)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MoneyTagChip(label: String, amount: Long) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    ) {
        Text(
            text = "$label ${formatNeondraAmount(amount)}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = NotoArabicFamily,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SameDateEventGroupCard(
    group: SameDateEventGroup,
    catalog: com.example.data.DropdownCatalog,
    onEntryClick: (ActivityRecord) -> Unit,
    onAddToEventClick: () -> Unit
) {
    var expanded by remember(group.groupKey) { mutableStateOf(true) }
    val isDark = isSystemInDarkTheme()
    val inColor = if (isDark) InBadgeFgDark else InBadgeFgLight
    val outColor = if (isDark) OutBadgeFgDark else OutBadgeFgLight
    val netPrefix = if (group.netBalance >= 0L) "+" else ""

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("event_list_card_${group.groupKey}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = group.eventName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = formatDisplayDate(group.eventDayStartMillis),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = JakartaSansFamily
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${group.entries.size} ${if (group.entries.size == 1) "entry" else "entries"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse event list" else "Expand event list"
                    )
                }
            }

            if (group.eventTypes.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    group.eventTypes.forEach { evType ->
                        val (catColor, catIcon) = catalog.resolveCategoryVisual(evType)
                        CategoryBadge(label = evType, color = catColor, icon = catIcon)
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "In: ${formatNeondraAmount(group.totalIn)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = inColor
                    )
                    Text(
                        text = "Out: ${formatNeondraAmount(group.totalOut)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = outColor
                    )
                    Text(
                        text = "Net: $netPrefix${formatNeondraAmount(group.netBalance)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                group.entries.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onEntryClick(entry) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = buildString {
                                    if (entry.familyId.isNotBlank()) append("FN ${entry.familyId} • ")
                                    append(entry.name)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val sub = buildList {
                                if (entry.fatherName.isNotBlank()) add("Father: ${entry.fatherName}")
                                if (entry.address.isNotBlank()) add(entry.address)
                            }.joinToString(" • ")
                            if (sub.isNotEmpty()) {
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DirectionPill(direction = entry.parsedDirection)
                            Text(
                                text = formatNeondraAmount(entry.totalAmount),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            FilledTonalButton(
                onClick = onAddToEventClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("add_to_event_list_${group.groupKey}")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Entry to This Event", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EmptyEventListsCard(onAddClick: () -> Unit) {
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
                imageVector = Icons.Rounded.Event,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = "No Same-Date Event Lists Yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Entries that share the same Event Name on the same Event Date are automatically grouped together into an Event List.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onAddClick, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Full Entry")
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
                text = if (hasAnyData) "No matching entries found" else "No ledger entries yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (hasAnyData) {
                    "Try adjusting your filters or search query."
                } else {
                    "Tap the (+) button below to record your first entry or Neondra. Families sharing Name, Father's Name & Address are grouped automatically under one unique FN."
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
                        contentDescription = "Pick Dates",
                        tint = if (dateRangeFilter.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(
                        text = if (dateRangeFilter.isActive && dateRangeFilter.presetLabel == null) {
                            formatDateRangeLabel(dateRangeFilter)
                        } else {
                            "Pick Dates"
                        },
                        fontWeight = if (dateRangeFilter.isActive && dateRangeFilter.presetLabel == null) FontWeight.Bold else FontWeight.Medium
                    )
                },
                modifier = Modifier.testTag("date_range_picker_button")
            )

            FilterChip(
                selected = !dateRangeFilter.isActive,
                onClick = onClearDateRange,
                label = { Text("All Time") },
                modifier = Modifier.testTag("date_range_all_time_chip")
            )

            FilterChip(
                selected = dateRangeFilter.presetLabel == "Today",
                onClick = {
                    if (dateRangeFilter.presetLabel == "Today") {
                        onClearDateRange()
                    } else {
                        val todayStart = localDayStartMillis(System.currentTimeMillis())
                        onApplyPreset(todayStart, localDayEndMillis(todayStart), "Today")
                    }
                },
                label = { Text("Today") },
                modifier = Modifier.testTag("date_range_preset_today")
            )

            FilterChip(
                selected = dateRangeFilter.presetLabel == "Last 7 Days",
                onClick = {
                    if (dateRangeFilter.presetLabel == "Last 7 Days") {
                        onClearDateRange()
                    } else {
                        val todayEnd = localDayEndMillis(System.currentTimeMillis())
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = localDayStartMillis(System.currentTimeMillis())
                            add(Calendar.DAY_OF_YEAR, -6)
                        }
                        onApplyPreset(cal.timeInMillis, todayEnd, "Last 7 Days")
                    }
                },
                label = { Text("Last 7 Days") },
                modifier = Modifier.testTag("date_range_preset_7d")
            )

            FilterChip(
                selected = dateRangeFilter.presetLabel == "This Month",
                onClick = {
                    if (dateRangeFilter.presetLabel == "This Month") {
                        onClearDateRange()
                    } else {
                        val now = System.currentTimeMillis()
                        val monthStart = Calendar.getInstance().apply {
                            timeInMillis = localDayStartMillis(now)
                            set(Calendar.DAY_OF_MONTH, 1)
                        }.timeInMillis
                        onApplyPreset(monthStart, localDayEndMillis(now), "This Month")
                    }
                },
                label = { Text("This Month") },
                modifier = Modifier.testTag("date_range_preset_month")
            )

            FilterChip(
                selected = dateRangeFilter.presetLabel == "Last 30 Days",
                onClick = {
                    if (dateRangeFilter.presetLabel == "Last 30 Days") {
                        onClearDateRange()
                    } else {
                        val todayEnd = localDayEndMillis(System.currentTimeMillis())
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = localDayStartMillis(System.currentTimeMillis())
                            add(Calendar.DAY_OF_YEAR, -29)
                        }
                        onApplyPreset(cal.timeInMillis, todayEnd, "Last 30 Days")
                    }
                },
                label = { Text("Last 30 Days") },
                modifier = Modifier.testTag("date_range_preset_30d")
            )

            FilterChip(
                selected = dateRangeFilter.presetLabel == "This Year",
                onClick = {
                    if (dateRangeFilter.presetLabel == "This Year") {
                        onClearDateRange()
                    } else {
                        val now = System.currentTimeMillis()
                        val yearStart = Calendar.getInstance().apply {
                            timeInMillis = localDayStartMillis(now)
                            set(Calendar.MONTH, Calendar.JANUARY)
                            set(Calendar.DAY_OF_MONTH, 1)
                        }.timeInMillis
                        onApplyPreset(yearStart, localDayEndMillis(now), "This Year")
                    }
                },
                label = { Text("This Year") },
                modifier = Modifier.testTag("date_range_preset_year")
            )
        }

        if (dateRangeFilter.isActive) {
            val periodIn = periodActivities
                .filter { it.parsedDirection == ActivityDirection.IN }
                .sumOf { it.totalAmount }
            val periodOut = periodActivities
                .filter { it.parsedDirection == ActivityDirection.OUT }
                .sumOf { it.totalAmount }
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
                                contentDescription = "Clear date filter",
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
                            text = "In: ${formatNeondraAmount(periodIn)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = inColor
                        )
                        Text(
                            text = "Out: ${formatNeondraAmount(periodOut)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = outColor
                        )
                        Text(
                            text = "Net: $netPrefix${formatNeondraAmount(periodNet)}",
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
        start != null -> "From ${formatDisplayDate(start)}"
        end != null -> "Until ${formatDisplayDate(end)}"
        else -> "All Time"
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
                Text("Apply", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (initialFilter.isActive || pickerState.selectedStartDateMillis != null) {
                    TextButton(onClick = onClear) {
                        Text("Clear")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    ) {
        DateRangePicker(
            state = pickerState,
            title = {
                Text(
                    text = "Filter In & Out Entries by Date Range",
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
