package com.example

import com.example.data.ActivityDirection
import com.example.data.ActivityRecord
import com.example.data.EventEntry
import com.example.ui.components.formatNeondraAmount
import com.example.ui.components.formatReminderOffsetLabel
import com.example.ui.screens.UpcomingOccasionItem
import com.example.ui.screens.buildUpcomingOccasionItems
import com.example.ui.screens.formatRelativeDayBadge
import com.example.ui.screens.localDayStartMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class LedgerLogicTest {

    @Test
    fun `ActivityDirection fromWire handles valid and unknown values safely`() {
        assertEquals(ActivityDirection.IN, ActivityDirection.fromWire("IN"))
        assertEquals(ActivityDirection.IN, ActivityDirection.fromWire("in"))
        assertEquals(ActivityDirection.OUT, ActivityDirection.fromWire("OUT"))
        assertEquals(ActivityDirection.OUT, ActivityDirection.fromWire("unknown_value"))
        assertEquals(ActivityDirection.OUT, ActivityDirection.fromWire(null))
    }

    @Test
    fun `formatNeondraAmount formats numbers with commas`() {
        assertEquals("5,000", formatNeondraAmount(5000L))
        assertEquals("20,000", formatNeondraAmount(20000L))
        assertEquals("0", formatNeondraAmount(0L))
    }

    @Test
    fun `formatReminderOffsetLabel returns English labels`() {
        assertEquals("At event time", formatReminderOffsetLabel(0L))
        assertEquals("1 hour before", formatReminderOffsetLabel(60L))
        assertEquals("1 day before", formatReminderOffsetLabel(1440L))
    }

    @Test
    fun `EventEntry stores title date and description defaults properly`() {
        val event = EventEntry(
            id = "evt_1",
            userId = "user_1",
            title = "Family Dinner",
            description = "Annual family dinner at Pearl Hall",
            eventDateMillis = 1764000000000L,
            category = "Dinner"
        )
        assertEquals("Family Dinner", event.title)
        assertEquals("Annual family dinner at Pearl Hall", event.description)
        assertEquals(1764000000000L, event.eventDateMillis)
        assertEquals("Dinner", event.category)
    }

    @Test
    fun `buildUpcomingOccasionItems sorts upcoming occasions ascending and respects toggle`() {
        val now = 1_764_000_000_000L
        val todayNoon = localDayStartMillis(now) + TimeUnit.HOURS.toMillis(12)
        val tomorrow = todayNoon + TimeUnit.DAYS.toMillis(1)
        val nextWeek = todayNoon + TimeUnit.DAYS.toMillis(7)
        val lastWeek = todayNoon - TimeUnit.DAYS.toMillis(7)

        val events = listOf(
            EventEntry(id = "e_next", title = "Next Week Event", eventDateMillis = nextWeek, category = "Wedding"),
            EventEntry(id = "e_past", title = "Past Event", eventDateMillis = lastWeek, category = "Wedding"),
            EventEntry(id = "e_tomorrow", title = "Tomorrow Event", eventDateMillis = tomorrow, category = "Dinner")
        )
        val activities = listOf(
            ActivityRecord(id = "a_today", name = "Ali Khan", eventType = "Walima", eventDateMillis = todayNoon),
            ActivityRecord(id = "a_past", name = "Old Entry", eventType = "Wedding", eventDateMillis = lastWeek)
        )

        val upcomingOnly = buildUpcomingOccasionItems(
            events = events,
            activities = activities,
            showAllSavedEvents = false,
            searchQuery = "",
            selectedCategoryFilter = null,
            nowMillis = now
        )
        assertEquals(3, upcomingOnly.size)
        assertTrue(upcomingOnly[0] is UpcomingOccasionItem.Activity)
        assertEquals("event_e_tomorrow", upcomingOnly[1].uniqueKey)
        assertEquals("event_e_next", upcomingOnly[2].uniqueKey)

        val allSaved = buildUpcomingOccasionItems(
            events = events,
            activities = activities,
            showAllSavedEvents = true,
            searchQuery = "",
            selectedCategoryFilter = null,
            nowMillis = now
        )
        assertEquals(4, allSaved.size)
        assertEquals("event_e_past", allSaved.first().uniqueKey)
    }

    @Test
    fun `formatRelativeDayBadge produces clear relative labels`() {
        val now = 1_764_000_000_000L
        val today = localDayStartMillis(now) + TimeUnit.HOURS.toMillis(10)
        assertEquals("Today", formatRelativeDayBadge(today, now))
        assertEquals("Tomorrow", formatRelativeDayBadge(today + TimeUnit.DAYS.toMillis(1), now))
        assertEquals("In 5 days", formatRelativeDayBadge(today + TimeUnit.DAYS.toMillis(5), now))
        assertEquals("Past", formatRelativeDayBadge(today - TimeUnit.DAYS.toMillis(2), now))
    }
}
