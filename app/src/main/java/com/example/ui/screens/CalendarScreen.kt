package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.ActivityDirection
import com.example.data.ActivityRecord
import com.example.data.DropdownCatalog
import com.example.data.EventEntry
import com.example.data.UiState
import com.example.reminders.CalendarIntentHelper
import com.example.ui.components.CategoryBadge
import com.example.ui.components.DirectionPill
import com.example.ui.components.formatDisplayDate
import com.example.ui.components.formatDisplayTime
import com.example.ui.components.formatNeondraAmount
import com.example.ui.theme.InBadgeFgLight
import com.example.ui.theme.JakartaSansFamily
import com.example.ui.theme.OutBadgeFgLight
import com.example.ui.theme.ReminderAmberFgLight
import com.example.ui.viewmodel.LedgerDashboardData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val DAY_LABELS = listOf("اتوار", "پیر", "منگل", "بدھ", "جمعرات", "جمعہ", "ہفتہ")
private val URDU_MONTHS = listOf(
    "جنوری", "فروری", "مارچ", "اپریل", "مئی", "جون",
    "جولائی", "اگست", "ستمبر", "اکتوبر", "نومبر", "دسمبر"
)

@Composable
fun CalendarScreen(
    state: UiState<LedgerDashboardData>,
    onActivityClick: (ActivityRecord) -> Unit,
    onAddOnDate: (Long) -> Unit,
    onNewEventOnDate: (Long) -> Unit,
    onEditEvent: (EventEntry) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onCalendarSynced: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val todayCal = remember { Calendar.getInstance() }
    var visibleYear by remember { mutableIntStateOf(todayCal.get(Calendar.YEAR)) }
    var visibleMonth by remember { mutableIntStateOf(todayCal.get(Calendar.MONTH)) }
    var selectedDayStartMillis by remember {
        mutableLongStateOf(dayStartMillis(System.currentTimeMillis()))
    }

    val dashboardData = (state as? UiState.Success)?.data
    val activities = dashboardData?.activities ?: emptyList()
    val events = dashboardData?.events ?: emptyList()
    val catalog = dashboardData?.catalog ?: DropdownCatalog()

    val activitiesByDay = remember(activities) {
        activities.groupBy { dayStartMillis(it.eventDateMillis) }
    }
    val eventsByDay = remember(events) {
        events.groupBy { dayStartMillis(it.eventDateMillis) }
    }

    val selectedDayActivities = remember(activitiesByDay, selectedDayStartMillis) {
        activitiesByDay[selectedDayStartMillis].orEmpty().sortedBy { it.eventDateMillis }
    }
    val selectedDayStandaloneEvents = remember(eventsByDay, selectedDayStartMillis) {
        eventsByDay[selectedDayStartMillis].orEmpty().sortedBy { it.eventDateMillis }
    }

    val monthTitle = remember(visibleYear, visibleMonth) {
        val monthName = URDU_MONTHS.getOrElse(visibleMonth) { "" }
        "$monthName $visibleYear"
    }

    val monthCells = remember(visibleYear, visibleMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, visibleYear)
            set(Calendar.MONTH, visibleMonth)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val firstDayOffset = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        buildList<Long?> {
            repeat(firstDayOffset) { add(null) }
            for (day in 1..daysInMonth) {
                cal.set(Calendar.DAY_OF_MONTH, day)
                add(cal.timeInMillis)
            }
            while (size % 7 != 0) add(null)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (visibleMonth == 0) {
                                        visibleMonth = 11
                                        visibleYear -= 1
                                    } else {
                                        visibleMonth -= 1
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "پچھلا مہینہ")
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = monthTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "آج پر جائیں",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        val now = Calendar.getInstance()
                                        visibleYear = now.get(Calendar.YEAR)
                                        visibleMonth = now.get(Calendar.MONTH)
                                        selectedDayStartMillis = dayStartMillis(now.timeInMillis)
                                    }
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (visibleMonth == 11) {
                                        visibleMonth = 0
                                        visibleYear += 1
                                    } else {
                                        visibleMonth += 1
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "اگلا مہینہ")
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            DAY_LABELS.forEach { dayName ->
                                Text(
                                    text = dayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        monthCells.chunked(7).forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                week.forEach { dayMillis ->
                                    if (dayMillis == null) {
                                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                    } else {
                                        val dayNum = remember(dayMillis) {
                                            Calendar.getInstance().apply { timeInMillis = dayMillis }
                                                .get(Calendar.DAY_OF_MONTH)
                                        }
                                        val dayActs = activitiesByDay[dayMillis].orEmpty()
                                        val dayStandalone = eventsByDay[dayMillis].orEmpty()
                                        val isSelected = dayMillis == selectedDayStartMillis
                                        val isToday = dayMillis == dayStartMillis(System.currentTimeMillis())

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    when {
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                        else -> MaterialTheme.colorScheme.surface
                                                    }
                                                )
                                                .then(
                                                    if (isToday && !isSelected) {
                                                        Modifier.border(
                                                            1.dp,
                                                            MaterialTheme.colorScheme.primary,
                                                            RoundedCornerShape(12.dp)
                                                        )
                                                    } else Modifier
                                                )
                                                .clickable { selectedDayStartMillis = dayMillis },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = "$dayNum",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontFamily = JakartaSansFamily,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                                if (dayActs.isNotEmpty() || dayStandalone.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        if (dayStandalone.isNotEmpty()) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(5.dp)
                                                                    .clip(CircleShape)
                                                                    .background(
                                                                        if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                                        else MaterialTheme.colorScheme.primary
                                                                    )
                                                            )
                                                        }
                                                        if (dayActs.any { it.parsedDirection == ActivityDirection.IN }) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(5.dp)
                                                                    .clip(CircleShape)
                                                                    .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else InBadgeFgLight)
                                                            )
                                                        }
                                                        if (dayActs.any { it.parsedDirection == ActivityDirection.OUT }) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(5.dp)
                                                                    .clip(CircleShape)
                                                                    .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else OutBadgeFgLight)
                                                            )
                                                        }
                                                        if (dayActs.any { it.reminderEnabled }) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(5.dp)
                                                                    .clip(CircleShape)
                                                                    .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else ReminderAmberFgLight)
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
                    }
                }
            }

            item {
                val noonSelectedMillis = remember(selectedDayStartMillis) {
                    Calendar.getInstance().apply {
                        timeInMillis = selectedDayStartMillis
                        set(Calendar.HOUR_OF_DAY, 12)
                        set(Calendar.MINUTE, 0)
                    }.timeInMillis
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text(
                            text = formatDisplayDate(selectedDayStartMillis),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = JakartaSansFamily,
                            fontWeight = FontWeight.Bold
                        )
                        val totalItems = selectedDayStandaloneEvents.size + selectedDayActivities.size
                        Text(
                            text = "اس دن $totalItems اندراجات / تقریبات طے ہیں",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onNewEventOnDate(noonSelectedMillis) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("calendar_new_event_button")
                        ) {
                            Icon(Icons.Rounded.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("نئی تقریب")
                        }

                        OutlinedButton(
                            onClick = { onAddOnDate(noonSelectedMillis) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("calendar_new_entry_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("نیا کھاتہ")
                        }
                    }
                }
            }

            if (selectedDayStandaloneEvents.isEmpty() && selectedDayActivities.isEmpty()) {
                item {
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
                                .padding(24.dp),
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
                                text = "اس تاریخ پر کوئی تقریب یا کھاتہ اندراج موجود نہیں",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                if (selectedDayStandaloneEvents.isNotEmpty()) {
                    item {
                        Text(
                            text = "تقریبات (${selectedDayStandaloneEvents.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(selectedDayStandaloneEvents, key = { "evt_${it.id}" }) { evt ->
                        val (catColor, catIcon) = catalog.resolveCategoryVisual(evt.category)
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditEvent(evt) }
                                .testTag("event_card_${evt.id}")
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
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        CategoryBadge(
                                            label = evt.category.ifBlank { "تقریب" },
                                            color = catColor,
                                            icon = catIcon
                                        )
                                        Text(
                                            text = evt.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = formatDisplayTime(evt.eventDateMillis),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = { onEditEvent(evt) }) {
                                            Icon(Icons.Rounded.Edit, contentDescription = "تقریب میں ترمیم")
                                        }
                                        IconButton(onClick = { onDeleteEvent(evt.id) }) {
                                            Icon(
                                                Icons.Rounded.DeleteOutline,
                                                contentDescription = "تقریب حذف کریں",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                                if (evt.description.isNotBlank()) {
                                    Text(
                                        text = evt.description,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                if (evt.location.isNotBlank() || evt.familyName.isNotBlank()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (evt.location.isNotBlank()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(evt.location, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        if (evt.familyName.isNotBlank()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Rounded.Groups, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(evt.familyName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedDayActivities.isNotEmpty()) {
                    item {
                        Text(
                            text = "کھاتہ اندراجات (${selectedDayActivities.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(selectedDayActivities, key = { "act_${it.id}" }) { event ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onActivityClick(event) }
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
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (event.eventType.isNotBlank()) {
                                            val (catColor, catIcon) = catalog.resolveCategoryVisual(event.eventType)
                                            CategoryBadge(
                                                label = event.eventType,
                                                color = catColor,
                                                icon = catIcon
                                            )
                                        }
                                        Text(
                                            text = event.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${formatDisplayTime(event.eventDateMillis)} • FN ${event.familyId.ifBlank { "—" }} • ${event.address}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    DirectionPill(direction = event.parsedDirection)
                                }
                                if (event.eventDetail.isNotBlank()) {
                                    Text(text = event.eventDetail, style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "رقم: ${formatNeondraAmount(event.neondraAmount)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (event.reminderEnabled) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.NotificationsActive,
                                                    contentDescription = "یاددہانی فعال",
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(6.dp).size(16.dp)
                                                )
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(CalendarIntentHelper.buildInsertCalendarIntent(event))
                                                }
                                                onCalendarSynced(event.id)
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("کیلنڈر", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (events.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "تمام محفوظ تقریبات (${events.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(events, key = { "all_evt_${it.id}" }) { evt ->
                    val (catColor, catIcon) = catalog.resolveCategoryVisual(evt.category)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedDayStartMillis = dayStartMillis(evt.eventDateMillis)
                                onEditEvent(evt)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CategoryBadge(
                                        label = evt.category.ifBlank { "تقریب" },
                                        color = catColor,
                                        icon = catIcon
                                    )
                                    Text(
                                        text = formatDisplayDate(evt.eventDateMillis),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = evt.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (evt.description.isNotBlank()) {
                                    Text(
                                        text = evt.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                            IconButton(onClick = { onDeleteEvent(evt.id) }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "تقریب حذف کریں",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun dayStartMillis(timeMillis: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}
