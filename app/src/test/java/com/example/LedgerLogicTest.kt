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
    fun `formatReminderOffsetLabel returns Urdu labels`() {
        assertEquals("تقریب کے وقت", formatReminderOffsetLabel(0L))
        assertEquals("1 گھنٹہ پہلے", formatReminderOffsetLabel(60L))
        assertEquals("1 دن پہلے", formatReminderOffsetLabel(1440L))
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
        assertEquals("آج", formatRelativeDayBadge(today, now))
        assertEquals("کل", formatRelativeDayBadge(today + TimeUnit.DAYS.toMillis(1), now))
        assertEquals("5 دن بعد", formatRelativeDayBadge(today + TimeUnit.DAYS.toMillis(5), now))
        assertEquals("گزشتہ", formatRelativeDayBadge(today - TimeUnit.DAYS.toMillis(2), now))
    }

    @Test
    fun `offline smart search matches families upcoming occasions and highest In balance`() {
        val repo = com.example.ai.AiAssistantRepository()
        val now = System.currentTimeMillis()
        val families = listOf(
            com.example.data.FamilyProfile(id = "f1", familyId = "101", name = "Ali Khan", address = "Lahore"),
            com.example.data.FamilyProfile(id = "f2", familyId = "102", name = "Bilal Ahmed", address = "Karachi")
        )
        val activities = listOf(
            ActivityRecord(id = "a1", familyDocId = "f1", familyId = "101", name = "Ali Khan", direction = "IN", neondraAmount = 15000L, eventType = "Wedding", eventDateMillis = now),
            ActivityRecord(id = "a2", familyDocId = "f2", familyId = "102", name = "Bilal Ahmed", direction = "OUT", neondraAmount = 8000L, eventType = "Walima", eventDateMillis = now)
        )
        val events = listOf(
            EventEntry(id = "e1", title = "Ali Wedding", eventDateMillis = now + TimeUnit.DAYS.toMillis(3), category = "Wedding"),
            EventEntry(id = "e2", title = "Past Function", eventDateMillis = now - TimeUnit.DAYS.toMillis(5), category = "Dinner")
        )

        val highestInResult = repo.searchFamiliesAndEvents("Who has the highest In balance?", families, activities, events).getOrThrow()
        assertEquals(listOf("f1"), highestInResult.matchedFamilyIds)

        val highestInUrduResult = repo.searchFamiliesAndEvents("سب سے زیادہ آمدن کس خاندان کی ہے؟", families, activities, events).getOrThrow()
        assertEquals(listOf("f1"), highestInUrduResult.matchedFamilyIds)

        val upcomingResult = repo.searchFamiliesAndEvents("upcoming events", families, activities, events).getOrThrow()
        assertEquals(listOf("e1"), upcomingResult.matchedEventIds)

        val upcomingUrduResult = repo.searchFamiliesAndEvents("آنے والی تقریبات", families, activities, events).getOrThrow()
        assertEquals(listOf("e1"), upcomingUrduResult.matchedEventIds)
    }

    @Test
    fun `offline balance comparison calculates exact In Out and Net totals`() {
        val repo = com.example.ai.AiAssistantRepository()
        val fam1 = com.example.data.FamilyProfile(id = "f1", familyId = "101", name = "Ali Khan")
        val fam2 = com.example.data.FamilyProfile(id = "f2", familyId = "102", name = "Bilal Ahmed")
        val activities = listOf(
            ActivityRecord(id = "a1", familyDocId = "f1", name = "Ali Khan", direction = "IN", neondraAmount = 20000L),
            ActivityRecord(id = "a2", familyDocId = "f1", name = "Ali Khan", direction = "OUT", neondraAmount = 5000L),
            ActivityRecord(id = "a3", familyDocId = "f2", name = "Bilal Ahmed", direction = "OUT", neondraAmount = 10000L)
        )

        val comparison = repo.compareFamilyBalances(listOf(fam1, fam2), activities, emptyList()).getOrThrow()
        assertEquals("Ali Khan", comparison.higherNetFamilyName)
        val aliStats = comparison.familyBreakdowns.first { it.familyDocId == "f1" }
        assertEquals(20000L, aliStats.totalIn)
        assertEquals(5000L, aliStats.totalOut)
        assertEquals(15000L, aliStats.netBalance)
    }

    @Test
    fun `single family In vs Out comparison separates In and Out entries accurately`() {
        val repo = com.example.ai.AiAssistantRepository()
        val fam = com.example.data.FamilyProfile(id = "f1", familyId = "101", name = "Ali Khan")
        val activities = listOf(
            ActivityRecord(id = "a1", familyDocId = "f1", name = "Ali Khan", direction = "IN", neondraAmount = 25000L, eventType = "Wedding"),
            ActivityRecord(id = "a2", familyDocId = "f1", name = "Ali Khan", direction = "IN", neondraAmount = 5000L, eventType = "Dinner"),
            ActivityRecord(id = "a3", familyDocId = "f1", name = "Ali Khan", direction = "OUT", neondraAmount = 12000L, eventType = "Wedding")
        )

        val singleCmp = repo.compareWithinSingleFamilyLocal(fam, activities, emptyList())
        assertEquals(30000L, singleCmp.totalIn)
        assertEquals(12000L, singleCmp.totalOut)
        assertEquals(18000L, singleCmp.netBalance)
        assertEquals(2, singleCmp.inEntries.size)
        assertEquals(1, singleCmp.outEntries.size)
        val weddingBreakdown = singleCmp.categoryBreakdowns.first { it.categoryName == "Wedding" }
        assertEquals(25000L, weddingBreakdown.totalIn)
        assertEquals(12000L, weddingBreakdown.totalOut)
        assertEquals(13000L, weddingBreakdown.netBalance)
    }

    @Test
    fun `filterLedgerActivities filters In and Out entries by inclusive date range`() {
        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 1, 9, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nov1Morning = cal.timeInMillis
        cal.set(2026, java.util.Calendar.NOVEMBER, 10, 14, 30, 0)
        val nov10Afternoon = cal.timeInMillis
        cal.set(2026, java.util.Calendar.NOVEMBER, 15, 23, 50, 0)
        val nov15LateNight = cal.timeInMillis
        cal.set(2026, java.util.Calendar.NOVEMBER, 20, 8, 0, 0)
        val nov20Morning = cal.timeInMillis

        val records = listOf(
            ActivityRecord(id = "r1", name = "A", direction = "IN", neondraAmount = 1000L, eventDateMillis = nov1Morning),
            ActivityRecord(id = "r2", name = "B", direction = "IN", neondraAmount = 2000L, eventDateMillis = nov10Afternoon),
            ActivityRecord(id = "r3", name = "C", direction = "OUT", neondraAmount = 3000L, eventDateMillis = nov15LateNight),
            ActivityRecord(id = "r4", name = "D", direction = "OUT", neondraAmount = 4000L, eventDateMillis = nov20Morning)
        )

        cal.set(2026, java.util.Calendar.NOVEMBER, 10, 0, 0, 0)
        val rangeStart = cal.timeInMillis
        cal.set(2026, java.util.Calendar.NOVEMBER, 15, 0, 0, 0)
        val rangeEnd = cal.timeInMillis

        val rangeFilter = com.example.ui.viewmodel.DateRangeFilter(
            startDateMillis = rangeStart,
            endDateMillis = rangeEnd
        )

        val allInPeriod = com.example.ui.screens.filterLedgerActivities(
            activities = records,
            searchQuery = "",
            selectedFilter = com.example.ui.viewmodel.LedgerFilter.ALL,
            selectedAddress = null,
            selectedCategoryFilter = null,
            dateRangeFilter = rangeFilter
        )
        assertEquals(listOf("r2", "r3"), allInPeriod.map { it.id })

        val inOnlyInPeriod = com.example.ui.screens.filterLedgerActivities(
            activities = records,
            searchQuery = "",
            selectedFilter = com.example.ui.viewmodel.LedgerFilter.IN,
            selectedAddress = null,
            selectedCategoryFilter = null,
            dateRangeFilter = rangeFilter
        )
        assertEquals(listOf("r2"), inOnlyInPeriod.map { it.id })

        val outOnlyInPeriod = com.example.ui.screens.filterLedgerActivities(
            activities = records,
            searchQuery = "",
            selectedFilter = com.example.ui.viewmodel.LedgerFilter.OUT,
            selectedAddress = null,
            selectedCategoryFilter = null,
            dateRangeFilter = rangeFilter
        )
        assertEquals(listOf("r3"), outOnlyInPeriod.map { it.id })
    }

    @Test
    fun `utcPickerMillisToLocalDayStart round-trips local date cleanly`() {
        val sampleLocalStart = localDayStartMillis(1_764_000_000_000L)
        val utcMillis = com.example.ui.screens.localDayStartToUtcPickerMillis(sampleLocalStart)
        val backToLocal = com.example.ui.screens.utcPickerMillisToLocalDayStart(utcMillis)
        assertEquals(sampleLocalStart, backToLocal)
    }
}

