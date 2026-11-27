package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActivityDirection
import com.example.data.ActivityRecord
import com.example.data.DropdownCatalog
import com.example.data.EventEntry
import com.example.ui.components.CategoryBadge
import com.example.ui.components.DirectionPill
import com.example.ui.components.ReminderStatusBadge
import com.example.ui.components.formatDisplayDate
import com.example.ui.components.formatNeondraAmount
import com.example.ui.theme.InBadgeFgDark
import com.example.ui.theme.InBadgeFgLight
import com.example.ui.theme.JakartaSansFamily
import com.example.ui.theme.LocalAppDarkTheme
import com.example.ui.theme.NotoArabicFamily
import com.example.ui.theme.OutBadgeFgDark
import com.example.ui.theme.OutBadgeFgLight
import java.util.Locale

data class EventDirectoryGroup(
    val key: String,
    val title: String,
    val dayStartMillis: Long,
    val eventTypes: List<String>,
    val entries: List<ActivityRecord>,
    val matchingEventIds: List<String> = emptyList(),
    val summary: FamilyFinancialSummary
)

fun buildEventDirectoryGroups(
    activities: List<ActivityRecord>,
    events: List<EventEntry> = emptyList()
): List<EventDirectoryGroup> {
    val eventsBySignature = events
        .filter { it.title.trim().isNotEmpty() }
        .groupBy { evt ->
            val dayStart = localDayStartMillis(evt.eventDateMillis)
            val cleanTitle = evt.title.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ENGLISH)
            "${dayStart}__$cleanTitle"
        }

    val activityGrouped = activities.groupBy { act ->
        val dayStart = localDayStartMillis(act.eventDateMillis)
        val rawDetail = act.eventDetail.trim().replace(Regex("\\s+"), " ")
        val rawType = act.eventType.trim().replace(Regex("\\s+"), " ")
        if (rawDetail.isNotEmpty()) {
            "${dayStart}__name__${rawDetail.lowercase(Locale.ENGLISH)}"
        } else {
            "${dayStart}__type__${rawType.ifEmpty { "event" }.lowercase(Locale.ENGLISH)}"
        }
    }

    val result = mutableListOf<EventDirectoryGroup>()
    val coveredTitleDateKeys = mutableSetOf<String>()

    activityGrouped.forEach { (groupKey, groupEntries) ->
        val sortedEntries = groupEntries.sortedWith(
            compareBy<ActivityRecord> { it.familyId.trim().isEmpty() }
                .thenBy { it.familyId.trim().toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.familyId.trim().lowercase(Locale.ENGLISH) }
                .thenByDescending { it.eventDateMillis }
        )
        val first = sortedEntries.first()
        val dayStart = localDayStartMillis(first.eventDateMillis)
        val detailCandidates = sortedEntries
            .map { it.eventDetail.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() }
        val title = detailCandidates.firstOrNull { cand -> cand.any { it.isUpperCase() } }
            ?: detailCandidates.firstOrNull()
            ?: first.eventType.trim().ifBlank { "Event" }
        val signature = "${dayStart}__${title.lowercase(Locale.ENGLISH)}"
        coveredTitleDateKeys.add(signature)
        val eventTypes = sortedEntries
            .map { it.eventType.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val matchedEvents = eventsBySignature[signature].orEmpty().map { it.id }

        result.add(
            EventDirectoryGroup(
                key = groupKey,
                title = title,
                dayStartMillis = dayStart,
                eventTypes = eventTypes,
                entries = sortedEntries,
                matchingEventIds = matchedEvents,
                summary = computeFamilyFinancialSummary(sortedEntries)
            )
        )
    }

    events.forEach { evt ->
        val cleanTitle = evt.title.trim().replace(Regex("\\s+"), " ")
        if (cleanTitle.isNotEmpty()) {
            val dayStart = localDayStartMillis(evt.eventDateMillis)
            val signature = "${dayStart}__${cleanTitle.lowercase(Locale.ENGLISH)}"
            if (coveredTitleDateKeys.add(signature)) {
                result.add(
                    EventDirectoryGroup(
                        key = "event_${evt.id}_${dayStart}",
                        title = cleanTitle,
                        dayStartMillis = dayStart,
                        eventTypes = emptyList(),
                        entries = emptyList(),
                        matchingEventIds = listOf(evt.id),
                        summary = computeFamilyFinancialSummary(emptyList())
                    )
                )
            }
        }
    }

    return result.sortedWith(
        compareByDescending<EventDirectoryGroup> { it.dayStartMillis }
            .thenBy { it.title.lowercase(Locale.ENGLISH) }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EventDirectoryCard(
    group: EventDirectoryGroup,
    catalog: DropdownCatalog,
    isDark: Boolean,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onQuickNeondraClick: () -> Unit,
    onNewEntryClick: () -> Unit
) {
    val inColor = if (isDark) InBadgeFgDark else InBadgeFgLight
    val outColor = if (isDark) OutBadgeFgDark else OutBadgeFgLight
    val cashAccent = if (isDark) Color(0xFF34D399) else Color(0xFF047857)
    val haarAccent = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
    val salamiAccent = if (isDark) Color(0xFFC084FC) else Color(0xFF7E22CE)
    val neondraAccent = if (isDark) Color(0xFF38BDF8) else Color(0xFF0369A1)
    val homeGiftAccent = if (isDark) Color(0xFFFB7185) else Color(0xFFBE123C)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("event_directory_card_${group.key}")
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
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EventNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = NotoArabicFamily,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = formatDisplayDate(group.dayStartMillis),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = JakartaSansFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.testTag("edit_event_${group.key}")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit Event")
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.testTag("delete_event_${group.key}")
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete Event",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (group.eventTypes.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    group.eventTypes.forEach { evType ->
                        val (catColor, catIcon) = catalog.resolveCategoryVisual(evType)
                        CategoryBadge(label = evType, color = catColor, icon = catIcon)
                    }
                }
            }

            // Overall In / Out Summary Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = inColor.copy(alpha = if (isDark) 0.16f else 0.11f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text("Total In", style = MaterialTheme.typography.labelSmall, color = inColor)
                        Text(
                            text = formatNeondraAmount(group.summary.totalIn),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaSansFamily,
                            color = inColor
                        )
                    }
                }
                Surface(
                    color = outColor.copy(alpha = if (isDark) 0.16f else 0.11f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text("Total Out", style = MaterialTheme.typography.labelSmall, color = outColor)
                        Text(
                            text = formatNeondraAmount(group.summary.totalOut),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaSansFamily,
                            color = outColor
                        )
                    }
                }
            }

            // Category Breakdown (نقد, ہار, سلامی, نیوندرا, ہوم گفٹ) In & Out
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FamilyCategoryInOutChip(
                    label = "نقد",
                    inText = formatNeondraAmount(group.summary.cashIn),
                    outText = formatNeondraAmount(group.summary.cashOut),
                    accent = cashAccent,
                    isDark = isDark
                )
                FamilyCategoryInOutChip(
                    label = "ہار",
                    inText = formatNeondraAmount(group.summary.haarIn),
                    outText = formatNeondraAmount(group.summary.haarOut),
                    accent = haarAccent,
                    isDark = isDark
                )
                FamilyCategoryInOutChip(
                    label = "سلامی",
                    inText = formatNeondraAmount(group.summary.salamiIn),
                    outText = formatNeondraAmount(group.summary.salamiOut),
                    accent = salamiAccent,
                    isDark = isDark
                )
                FamilyCategoryInOutChip(
                    label = "نیوندرا",
                    inText = formatNeondraAmount(group.summary.neondraIn),
                    outText = formatNeondraAmount(group.summary.neondraOut),
                    accent = neondraAccent,
                    isDark = isDark
                )
                FamilyCategoryInOutChip(
                    label = "ہوم گفٹ",
                    inText = "${group.summary.homeGiftInCount}",
                    outText = "${group.summary.homeGiftOutCount}",
                    accent = homeGiftAccent,
                    isDark = isDark
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDark) 0.55f else 0.72f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .clickable(onClick = onCardClick)
                        .testTag("event_entries_pill_${group.key}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "${group.entries.size} ${if (group.entries.size == 1) "entry" else "entries"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            softWrap = false
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "View Event Entries",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onQuickNeondraClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("event_neondra_button_${group.key}")
                    ) {
                        Icon(
                            Icons.Rounded.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "نیوندرا",
                            fontFamily = NotoArabicFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    OutlinedButton(
                        onClick = onNewEntryClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("event_new_entry_button_${group.key}")
                    ) {
                        Icon(Icons.Rounded.PostAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "New Entry",
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EventEntriesBottomSheet(
    group: EventDirectoryGroup,
    catalog: DropdownCatalog,
    onDismiss: () -> Unit,
    onQuickNeondra: () -> Unit,
    onNewEntry: () -> Unit,
    onEntryClick: (ActivityRecord) -> Unit
) {
    val isDark = LocalAppDarkTheme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val inColor = if (isDark) InBadgeFgDark else InBadgeFgLight
    val outColor = if (isDark) OutBadgeFgDark else OutBadgeFgLight
    val summary = remember(group.entries) { group.summary }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EventNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = NotoArabicFamily,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatDisplayDate(group.dayStartMillis),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JakartaSansFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = inColor.copy(alpha = if (isDark) 0.16f else 0.11f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Total In", style = MaterialTheme.typography.labelSmall, color = inColor)
                        Text(
                            text = formatNeondraAmount(summary.totalIn),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = inColor
                        )
                    }
                }
                Surface(
                    color = outColor.copy(alpha = if (isDark) 0.16f else 0.11f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Total Out", style = MaterialTheme.typography.labelSmall, color = outColor)
                        Text(
                            text = formatNeondraAmount(summary.totalOut),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = outColor
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Entries (${group.entries.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onQuickNeondra,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "نیوندرا",
                            fontFamily = NotoArabicFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(
                        onClick = onNewEntry,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.PostAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("New Entry", fontSize = 12.sp)
                    }
                }
            }

            if (group.entries.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No entries recorded for this event yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(group.entries, key = { it.id }) { record ->
                        val (catColor, catIcon) = catalog.resolveCategoryVisual(record.eventType)
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.42f else 0.45f)
                            ),
                            border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEntryClick(record) }
                                .testTag("event_sheet_entry_${record.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
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
                                        if (record.familyId.isNotBlank()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "FN ${record.familyId}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontFamily = JakartaSansFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = record.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val meta = buildList {
                                                if (record.fatherName.isNotBlank()) add("ولد: ${record.fatherName}")
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

                                CategoryBadge(label = record.eventType, color = catColor, icon = catIcon)

                                if (record.homeItems.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CardGiftcard,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "تحائف: ${record.homeItems}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = NotoArabicFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                val moneyParts = buildList {
                                    if (record.cashAmount > 0L) add("نقد: ${formatNeondraAmount(record.cashAmount)}")
                                    if (record.haarAmount > 0L) add("ہار: ${formatNeondraAmount(record.haarAmount)}")
                                    if (record.salamiAmount > 0L) add("سلامی: ${formatNeondraAmount(record.salamiAmount)}")
                                    if (record.neondraAmount > 0L) add("نیوندرا: ${formatNeondraAmount(record.neondraAmount)}")
                                }
                                if (moneyParts.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        moneyParts.forEach { part ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = part,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = NotoArabicFamily,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (record.reminderEnabled) {
                                        ReminderStatusBadge()
                                    } else {
                                        Spacer(modifier = Modifier.width(1.dp))
                                    }
                                    Text(
                                        text = "Total: ${formatNeondraAmount(record.totalAmount)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = JakartaSansFamily,
                                        color = if (record.parsedDirection == ActivityDirection.IN) inColor else outColor
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
internal fun EventGroupEditorDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Event Name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        error = null
                    },
                    label = { Text("Event Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_event_name_input")
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = title.trim().replace(Regex("\\s+"), " ")
                    if (trimmed.isEmpty()) {
                        error = "Event name is required."
                        return@TextButton
                    }
                    onConfirm(trimmed)
                },
                modifier = Modifier.testTag("confirm_edit_event_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
