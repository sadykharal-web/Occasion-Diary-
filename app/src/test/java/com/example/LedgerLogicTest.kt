package com.example

import com.example.ai.AiAssistantRepository
import com.example.data.ActivityDirection
import com.example.data.ActivityRecord
import com.example.data.EventEntry
import com.example.data.FamilyIdentityMatcher
import com.example.data.FamilyProfile
import com.example.ui.components.formatNeondraAmount
import com.example.ui.components.formatReminderOffsetLabel
import com.example.ui.screens.UpcomingOccasionItem
import com.example.ui.screens.buildUpcomingOccasionItems
import com.example.ui.screens.formatRelativeDayBadge
import com.example.ui.screens.localDayStartMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun `formatRelativeDayBadge produces clear relative English labels`() {
        val now = 1_764_000_000_000L
        val today = localDayStartMillis(now) + TimeUnit.HOURS.toMillis(10)
        assertEquals("Today", formatRelativeDayBadge(today, now))
        assertEquals("Tomorrow", formatRelativeDayBadge(today + TimeUnit.DAYS.toMillis(1), now))
        assertEquals("In 5 days", formatRelativeDayBadge(today + TimeUnit.DAYS.toMillis(5), now))
        assertEquals("Past", formatRelativeDayBadge(today - TimeUnit.DAYS.toMillis(2), now))
    }

    @Test
    fun `FamilyIdentityMatcher matches exact 3 fields and assigns distinct FN otherwise`() {
        val existingFamilies = listOf(
            FamilyProfile(
                id = "fam_1",
                familyId = "1",
                name = "Ali Raza",
                fatherName = "Muhammad Ramzan",
                address = "Kot Ramzan"
            )
        )

        val sameFamily = FamilyIdentityMatcher.resolveForActivity(
            name = "  ali   raza ",
            fatherName = "muhammad ramzan",
            address = "  kot  ramzan ",
            requestedFn = "",
            existingFamilies = existingFamilies,
            existingActivities = emptyList()
        )
        assertTrue(sameFamily.matchedExisting)
        assertEquals("fam_1", sameFamily.familyProfile.id)
        assertEquals("1", sameFamily.familyProfile.familyId)

        val differentFather = FamilyIdentityMatcher.resolveForActivity(
            name = "Ali Raza",
            fatherName = "Ghulam Rasool",
            address = "Kot Ramzan",
            requestedFn = "",
            existingFamilies = existingFamilies,
            existingActivities = emptyList()
        )
        assertFalse(differentFather.matchedExisting)
        assertNotEquals("fam_1", differentFather.familyProfile.id)
        assertEquals("2", differentFather.familyProfile.familyId)

        val differentAddress = FamilyIdentityMatcher.resolveForActivity(
            name = "Ali Raza",
            fatherName = "Muhammad Ramzan",
            address = "Lahore",
            requestedFn = "1",
            existingFamilies = existingFamilies,
            existingActivities = emptyList()
        )
        assertFalse(differentAddress.matchedExisting)
        assertNotEquals("fam_1", differentAddress.familyProfile.id)
        assertEquals("2", differentAddress.familyProfile.familyId)
    }

    @Test
    fun `offline smart search matches families upcoming occasions and highest In balance`() {
        val repo = AiAssistantRepository()
        val now = System.currentTimeMillis()
        val families = listOf(
            FamilyProfile(id = "f1", familyId = "101", name = "Ali Khan", address = "Lahore"),
            FamilyProfile(id = "f2", familyId = "102", name = "Bilal Ahmed", address = "Karachi")
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
        val repo = AiAssistantRepository()
        val fam1 = FamilyProfile(id = "f1", familyId = "101", name = "Ali Khan")
        val fam2 = FamilyProfile(id = "f2", familyId = "102", name = "Bilal Ahmed")
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

        val followUpUrdu = repo.answerMultiFamilyFollowUpOffline("Which family has highest In?", comparison)
        assertTrue(followUpUrdu.contains("Ali Khan"))
        assertTrue(followUpUrdu.contains("آمدن"))
    }

    @Test
    fun `single family In vs Out comparison and follow-up Urdu answers work accurately`() {
        val repo = AiAssistantRepository()
        val fam = FamilyProfile(id = "f1", familyId = "101", name = "Ali Khan")
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

        val followUpUrdu = repo.answerSingleFamilyFollowUpOffline("How much should I give next?", singleCmp)
        assertTrue(followUpUrdu.contains("18,000"))
        assertTrue(followUpUrdu.contains("روپے"))
        assertTrue(singleCmp.insights.isEmpty())
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
        assertEquals(listOf("r3", "r2"), allInPeriod.map { it.id })

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

    @Test
    fun `default event types contain only the 4 Urdu Neondra events`() {
        assertEquals(
            listOf("شادی", "نکاح", "عقیقہ", "سالگرہ"),
            com.example.data.DropdownCatalog.DEFAULT_EVENT_TYPES
        )
    }

    @Test
    fun `home gift item formatters and append helper build clean Urdu subcategory text`() {
        var items = ""
        items = com.example.ui.screens.appendHomeGiftItem(
            items,
            com.example.ui.screens.formatSuitGiftItem("زنانہ سوٹ", 2)
        )
        items = com.example.ui.screens.appendHomeGiftItem(
            items,
            com.example.ui.screens.formatSuitGiftItem("مردانہ سوٹ", 1)
        )
        items = com.example.ui.screens.appendHomeGiftItem(
            items,
            com.example.ui.screens.formatKgGiftItem("پھل", 3)
        )
        items = com.example.ui.screens.appendHomeGiftItem(
            items,
            com.example.ui.screens.formatKgGiftItem("مٹھائی", 5)
        )
        assertEquals("2 زنانہ سوٹ, 1 مردانہ سوٹ, 3 کلوگرام پھل, 5 کلوگرام مٹھائی", items)
    }

    @Test
    fun `ActivityRecord totalAmount sums نقد ہار سلامی and Neondra accurately`() {
        val record = ActivityRecord(
            id = "a1",
            name = "Asif",
            cashAmount = 5000L,
            haarAmount = 2000L,
            salamiAmount = 1000L,
            neondraAmount = 3000L
        )
        assertEquals(11000L, record.totalAmount)
    }

    @Test
    fun `buildSameDateEventLists groups entries sharing same date and normalized Event Name`() {
        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 10, 9, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val day1Morning = cal.timeInMillis
        cal.set(2026, java.util.Calendar.NOVEMBER, 10, 20, 30, 0)
        val day1Evening = cal.timeInMillis
        cal.set(2026, java.util.Calendar.NOVEMBER, 11, 12, 0, 0)
        val day2Noon = cal.timeInMillis

        val activities = listOf(
            ActivityRecord(
                id = "a1",
                name = "Ali",
                eventType = "شادی",
                eventDetail = "Bilal  Wedding",
                eventDateMillis = day1Morning,
                direction = "IN",
                cashAmount = 2000L,
                neondraAmount = 3000L
            ),
            ActivityRecord(
                id = "a2",
                name = "Usman",
                eventType = "شادی",
                eventDetail = " bilal wedding ",
                eventDateMillis = day1Evening,
                direction = "OUT",
                haarAmount = 1000L,
                salamiAmount = 500L,
                neondraAmount = 1500L
            ),
            ActivityRecord(
                id = "a3",
                name = "Tariq",
                eventType = "شادی",
                eventDetail = "Bilal Wedding",
                eventDateMillis = day2Noon,
                direction = "IN",
                neondraAmount = 4000L
            ),
            ActivityRecord(
                id = "a4",
                name = "No Event Name",
                eventType = "نکاح",
                eventDetail = "   ",
                eventDateMillis = day1Morning,
                direction = "IN",
                neondraAmount = 1000L
            )
        )

        val groups = com.example.ui.screens.buildSameDateEventLists(activities)
        assertEquals(2, groups.size)

        val day2Group = groups[0]
        assertEquals(localDayStartMillis(day2Noon), day2Group.eventDayStartMillis)
        assertEquals(1, day2Group.entries.size)
        assertEquals(4000L, day2Group.totalIn)

        val day1Group = groups[1]
        assertEquals(localDayStartMillis(day1Morning), day1Group.eventDayStartMillis)
        assertEquals(2, day1Group.entries.size)
        assertEquals(5000L, day1Group.totalIn)
        assertEquals(3000L, day1Group.totalOut)
        assertEquals(2000L, day1Group.netBalance)
    }

    @Test
    fun `LocalBackupManager exports valid Excel xlsx and round-trips all sheets cleanly`() {
        val now = 1_764_000_000_000L
        val categories = listOf(
            com.example.data.ActivityCategory(id = "c1", name = "شادی", colorHex = "#D81B60", iconKey = "celebration")
        )
        val families = listOf(
            FamilyProfile(
                id = "f1",
                familyId = "7",
                name = "Rajab Ali",
                fatherName = "Muhammad Ramzan",
                address = "Kot Ramzan",
                phone = "03001234567"
            )
        )
        val activities = listOf(
            ActivityRecord(
                id = "a1",
                familyDocId = "f1",
                familyId = "7",
                name = "Rajab Ali",
                fatherName = "Muhammad Ramzan",
                address = "Kot Ramzan",
                eventType = "شادی",
                eventDetail = "Asif & Bilal Wedding",
                eventDateMillis = now,
                direction = "OUT",
                homeItems = "2 زنانہ سوٹ, 3 KG مٹھائی",
                cashAmount = 5000L,
                haarAmount = 1500L,
                salamiAmount = 1000L,
                neondraAmount = 3000L,
                reminderEnabled = true,
                reminderAtMillis = now + 3600_000L,
                reminderMinutesBefore = 60L,
                calendarSynced = true
            )
        )
        val events = listOf(
            EventEntry(
                id = "e1",
                title = "Family Dinner",
                description = "Night dinner",
                eventDateMillis = now,
                category = "عقیقہ",
                location = "Pearl Hall",
                familyDocId = "f1",
                familyName = "Rajab Ali"
            )
        )

        val xlsxBytes = com.example.drive.LocalBackupManager.serializeBackupXlsxToBytes(
            exportedAtMillis = now,
            categories = categories,
            families = families,
            activities = activities,
            events = events
        )
        // Verify PK zip signature for .xlsx
        assertEquals(0x50.toByte(), xlsxBytes[0])
        assertEquals(0x4B.toByte(), xlsxBytes[1])

        // Verify single-sheet export, no extra/internal columns, and dd-MM-yyyy EventDate
        val expectedDateStr = com.example.ui.components.formatDisplayDate(now)
        assertTrue(expectedDateStr.matches(Regex("\\d{2}-\\d{2}-\\d{4}")))
        val sheetTexts = mutableMapOf<String, String>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(xlsxBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.name.startsWith("xl/worksheets/")) {
                    sheetTexts[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                }
                zis.closeEntry()
            }
        }
        assertEquals(1, sheetTexts.size)
        val sheetXml = sheetTexts.values.first()
        listOf(
            "ID",
            "FamilyDocId",
            "ExportedAtMillis",
            "EventDateMillis",
            "ReminderEnabled",
            "ReminderAtMillis",
            "ReminderMinutesBefore",
            "CalendarSynced"
        ).forEach { forbiddenHeader ->
            assertFalse("Unexpected extra column: $forbiddenHeader", sheetXml.contains(">$forbiddenHeader<"))
        }
        assertTrue(sheetXml.contains(">$expectedDateStr<"))

        val restored = com.example.drive.LocalBackupManager.parseBackupAuto(xlsxBytes)
        assertEquals(1, restored.activities.size)
        val restoredAct = restored.activities.first()
        assertEquals("7", restoredAct.familyId)
        assertEquals("Rajab Ali", restoredAct.name)
        assertEquals("Asif & Bilal Wedding", restoredAct.eventDetail)
        assertEquals(5000L, restoredAct.cashAmount)
        assertEquals(1500L, restoredAct.haarAmount)
        assertEquals(1000L, restoredAct.salamiAmount)
        assertEquals(3000L, restoredAct.neondraAmount)
        assertEquals(10500L, restoredAct.totalAmount)
        assertEquals("2 زنانہ سوٹ, 3 KG مٹھائی", restoredAct.homeItems)
    }

    @Test
    fun `formatDisplayDate uses dd-MM-yyyy and Excel restore parses manual dd-MM-yyyy rows without EventDateMillis`() {
        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 5, 14, 30, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        assertEquals("05-11-2026", com.example.ui.components.formatDisplayDate(cal.timeInMillis))

        // Build a minimal manual Excel sheet without ID, FamilyDocId, ExportedAtMillis, or EventDateMillis
        val sheet1Xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
            <row r="1">
                <c r="A1" t="inlineStr"><is><t>FN</t></is></c>
                <c r="B1" t="inlineStr"><is><t>Name</t></is></c>
                <c r="C1" t="inlineStr"><is><t>FatherName</t></is></c>
                <c r="D1" t="inlineStr"><is><t>Address</t></is></c>
                <c r="E1" t="inlineStr"><is><t>EventType</t></is></c>
                <c r="F1" t="inlineStr"><is><t>Event Name</t></is></c>
                <c r="G1" t="inlineStr"><is><t>EventDate</t></is></c>
                <c r="H1" t="inlineStr"><is><t>Direction</t></is></c>
                <c r="I1" t="inlineStr"><is><t>NeondraAmount</t></is></c>
            </row>
            <row r="2">
                <c r="A2" t="inlineStr"><is><t>15</t></is></c>
                <c r="B2" t="inlineStr"><is><t>Imran</t></is></c>
                <c r="C2" t="inlineStr"><is><t>Aslam</t></is></c>
                <c r="D2" t="inlineStr"><is><t>Lahore</t></is></c>
                <c r="E2" t="inlineStr"><is><t>شادی</t></is></c>
                <c r="F2" t="inlineStr"><is><t>Imran Wedding</t></is></c>
                <c r="G2" t="inlineStr"><is><t>05-11-2026</t></is></c>
                <c r="H2" t="inlineStr"><is><t>IN</t></is></c>
                <c r="I2" t="n"><v>7500</v></c>
            </row>
            </sheetData></worksheet>""".trimIndent()
        val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
            <sheets><sheet name="Ledger_Entries" sheetId="1" r:id="rId1"/></sheets></workbook>""".trimIndent()
        val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""".trimIndent()

        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zip ->
            listOf(
                "xl/workbook.xml" to workbookXml,
                "xl/_rels/workbook.xml.rels" to relsXml,
                "xl/worksheets/sheet1.xml" to sheet1Xml
            ).forEach { (name, content) ->
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }

        val parsed = com.example.drive.LocalBackupManager.parseBackupAuto(baos.toByteArray())
        assertEquals(1, parsed.activities.size)
        val act = parsed.activities.first()
        assertEquals("15", act.familyId)
        assertEquals("Imran", act.name)
        assertEquals("05-11-2026", com.example.ui.components.formatDisplayDate(act.eventDateMillis))
        assertEquals(7500L, act.neondraAmount)
        assertEquals("IN", act.direction)
    }

    @Test
    fun `offline search matches Urdu event types and Urdu gift subcategories across English and Urdu queries`() {
        val repo = AiAssistantRepository()
        val families = listOf(FamilyProfile(id = "f1", familyId = "1", name = "Asif Ali"))
        val activities = listOf(
            ActivityRecord(
                id = "a1",
                familyDocId = "f1",
                familyId = "1",
                name = "Asif Ali",
                eventType = "شادی",
                homeItems = "2 زنانہ سوٹ, 3 KG پھل",
                direction = "OUT",
                cashAmount = 2000L,
                neondraAmount = 3000L
            )
        )
        val urduEventHit = repo.searchFamiliesAndEvents("شادی", families, activities, emptyList()).getOrThrow()
        assertEquals(listOf("a1"), urduEventHit.matchedActivityIds)

        val englishGiftHit = repo.searchFamiliesAndEvents("fruit", families, activities, emptyList()).getOrThrow()
        assertEquals(listOf("a1"), englishGiftHit.matchedActivityIds)
    }

    @Test
    fun `filterLedgerActivities supports multi-select eventTypes and FN EventName EventType sort orders`() {
        val base = 1_764_000_000_000L
        val r1 = ActivityRecord(
            id = "r1",
            familyId = "10",
            name = "Zafar",
            eventType = "شادی",
            eventDetail = "Zain Wedding",
            eventDateMillis = base + 1_000L,
            neondraAmount = 5000L
        )
        val r2 = ActivityRecord(
            id = "r2",
            familyId = "2",
            name = "Bilal",
            eventType = "نکاح",
            eventDetail = "Bilal Nikah",
            eventDateMillis = base + 3_000L,
            neondraAmount = 2000L
        )
        val r3 = ActivityRecord(
            id = "r3",
            familyId = "",
            name = "Asif",
            eventType = "عقیقہ",
            eventDetail = "Aqeeqah Ceremony",
            eventDateMillis = base + 2_000L,
            neondraAmount = 9000L
        )
        val all = listOf(r1, r2, r3)

        val multiFilteredByFn = com.example.ui.screens.filterLedgerActivities(
            activities = all,
            searchQuery = "",
            selectedFilter = com.example.ui.viewmodel.LedgerFilter.ALL,
            selectedAddress = null,
            dateRangeFilter = com.example.ui.viewmodel.DateRangeFilter(),
            selectedEventTypes = setOf("شادی", "عقیقہ"),
            sortOrder = com.example.ui.viewmodel.LedgerSortOrder.FN
        )
        assertEquals(listOf("r1", "r3"), multiFilteredByFn.map { it.id })

        val byFnNumeric = com.example.ui.screens.filterLedgerActivities(
            activities = all,
            searchQuery = "",
            selectedFilter = com.example.ui.viewmodel.LedgerFilter.ALL,
            selectedAddress = null,
            dateRangeFilter = com.example.ui.viewmodel.DateRangeFilter(),
            sortOrder = com.example.ui.viewmodel.LedgerSortOrder.FN
        )
        assertEquals(listOf("r2", "r1", "r3"), byFnNumeric.map { it.id })

        val byEventName = com.example.ui.screens.filterLedgerActivities(
            activities = all,
            searchQuery = "",
            selectedFilter = com.example.ui.viewmodel.LedgerFilter.ALL,
            selectedAddress = null,
            dateRangeFilter = com.example.ui.viewmodel.DateRangeFilter(),
            sortOrder = com.example.ui.viewmodel.LedgerSortOrder.EVENT_NAME
        )
        assertEquals(listOf("r3", "r2", "r1"), byEventName.map { it.id })

        val byEventType = com.example.ui.screens.filterLedgerActivities(
            activities = all,
            searchQuery = "",
            selectedFilter = com.example.ui.viewmodel.LedgerFilter.ALL,
            selectedAddress = null,
            dateRangeFilter = com.example.ui.viewmodel.DateRangeFilter(),
            sortOrder = com.example.ui.viewmodel.LedgerSortOrder.EVENT_TYPE
        )
        assertEquals(listOf("r1", "r3", "r2"), byEventType.map { it.id })

        val byNameAz = com.example.ui.screens.filterLedgerActivities(
            activities = all,
            searchQuery = "",
            selectedFilter = com.example.ui.viewmodel.LedgerFilter.ALL,
            selectedAddress = null,
            dateRangeFilter = com.example.ui.viewmodel.DateRangeFilter(),
            sortOrder = com.example.ui.viewmodel.LedgerSortOrder.NAME_AZ
        )
        assertEquals(listOf("r3", "r2", "r1"), byNameAz.map { it.id })
    }

    @Test
    fun `buildSameDateEventLists respects FN NameAZ EventName and EventType sort orders`() {
        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 12, 10, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val day1 = cal.timeInMillis
        cal.set(2026, java.util.Calendar.NOVEMBER, 14, 10, 0, 0)
        val day2 = cal.timeInMillis

        val activities = listOf(
            ActivityRecord(id = "a1", familyId = "10", name = "Zaid", eventType = "شادی", eventDetail = "Walima", eventDateMillis = day1, direction = "IN", neondraAmount = 1000L),
            ActivityRecord(id = "a2", familyId = "2", name = "Ali", eventType = "نکاح", eventDetail = "Walima", eventDateMillis = day1 + 1000L, direction = "OUT", neondraAmount = 8000L),
            ActivityRecord(id = "a3", familyId = "5", name = "Bilal", eventType = "عقیقہ", eventDetail = "Aqeeqah", eventDateMillis = day2, direction = "IN", neondraAmount = 2000L)
        )

        val byFn = com.example.ui.screens.buildSameDateEventLists(
            activities = activities,
            sortOrder = com.example.ui.viewmodel.LedgerSortOrder.FN
        )
        assertEquals(listOf("Walima", "Aqeeqah"), byFn.map { it.eventName })
        assertEquals(listOf("a2", "a1"), byFn.first().entries.map { it.id })

        val byNameAz = com.example.ui.screens.buildSameDateEventLists(
            activities = activities,
            sortOrder = com.example.ui.viewmodel.LedgerSortOrder.NAME_AZ
        )
        assertEquals(listOf("Walima", "Aqeeqah"), byNameAz.map { it.eventName })
        assertEquals(listOf("a2", "a1"), byNameAz.first().entries.map { it.id })

        val byEventName = com.example.ui.screens.buildSameDateEventLists(
            activities = activities,
            sortOrder = com.example.ui.viewmodel.LedgerSortOrder.EVENT_NAME
        )
        assertEquals(listOf("Aqeeqah", "Walima"), byEventName.map { it.eventName })
        assertEquals(listOf("a2", "a1"), byEventName[1].entries.map { it.id })

        val byEventType = com.example.ui.screens.buildSameDateEventLists(
            activities = activities,
            sortOrder = com.example.ui.viewmodel.LedgerSortOrder.EVENT_TYPE
        )
        assertEquals(listOf("Walima", "Aqeeqah"), byEventType.map { it.eventName })
        assertEquals(listOf("a1", "a2"), byEventType[0].entries.map { it.id })
    }

    @Test
    fun `offline search matches کلوگرام and returns all In Out entries for a single matched family`() {
        val repo = AiAssistantRepository()
        val families = listOf(
            FamilyProfile(id = "f1", familyId = "12", name = "Haji Akram", fatherName = "Haji Yousaf", address = "Lahore"),
            FamilyProfile(id = "f2", familyId = "13", name = "Tariq Mehmood", fatherName = "Nazeer", address = "Multan")
        )
        val activities = listOf(
            ActivityRecord(
                id = "a1",
                familyDocId = "f1",
                familyId = "12",
                name = "Haji Akram",
                fatherName = "Haji Yousaf",
                direction = "IN",
                homeItems = "3 کلوگرام پھل",
                neondraAmount = 4000L
            ),
            ActivityRecord(
                id = "a2",
                familyDocId = "f1",
                familyId = "12",
                name = "Haji Akram",
                fatherName = "Haji Yousaf",
                direction = "OUT",
                neondraAmount = 6000L
            ),
            ActivityRecord(
                id = "a3",
                familyDocId = "f2",
                familyId = "13",
                name = "Tariq Mehmood",
                direction = "IN",
                homeItems = "2 کلوگرام مٹھائی",
                neondraAmount = 3000L
            )
        )

        val singleFamilyByName = repo.searchFamiliesAndEvents("Haji Akram", families, activities, emptyList()).getOrThrow()
        assertEquals(listOf("f1"), singleFamilyByName.matchedFamilyIds)
        assertEquals(setOf("a1", "a2"), singleFamilyByName.matchedActivityIds.toSet())

        val kgSynonymHit = repo.searchFamiliesAndEvents("kg", families, activities, emptyList()).getOrThrow()
        assertEquals(setOf("a1", "a3"), kgSynonymHit.matchedActivityIds.toSet())

        val urduKgHit = repo.searchFamiliesAndEvents("کلوگرام", families, activities, emptyList()).getOrThrow()
        assertEquals(setOf("a1", "a3"), urduKgHit.matchedActivityIds.toSet())
    }

    @Test
    fun `World website URL and Family Events backup filename are configured cleanly`() {
        assertEquals("https://raizan.gamer.free", com.example.ui.screens.WORLD_WEBSITE_URL)
        assertEquals("Family_Events_Backup.xlsx", com.example.drive.LocalBackupManager.DEFAULT_BACKUP_FILE_NAME)
    }
}

