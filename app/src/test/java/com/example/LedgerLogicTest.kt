package com.example

import com.example.data.ActivityDirection
import com.example.data.EventEntry
import com.example.ui.components.formatNeondraAmount
import com.example.ui.components.formatReminderOffsetLabel
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
