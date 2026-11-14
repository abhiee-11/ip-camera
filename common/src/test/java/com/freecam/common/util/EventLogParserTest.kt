package com.freecam.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventLogParserTest {

    @Test
    fun emptyOrMissingFileYieldsEmptyList() {
        assertTrue(EventLogParser.parseEventLog(null).isEmpty())
        assertTrue(EventLogParser.parseEventLog("").isEmpty())
        assertTrue(EventLogParser.parseEventLog("not json").isEmpty())
        assertTrue(EventLogParser.parseEventLog("[]").isEmpty())
    }

    @Test
    fun parsesAndSortsNewestFirst() {
        val content = """[{"id":"a","ts":100,"severity":"low"},{"id":"b","ts":200,"severity":"high"}]"""
        val events = EventLogParser.parseEventLog(content)
        assertEquals(listOf("b", "a"), events.map { it.id })
        assertEquals("high", events.first().severity)
    }

    @Test
    fun toleratesEntriesMissingFields() {
        val events = EventLogParser.parseEventLog("""[{"ts":5}]""")
        assertEquals(1, events.size)
        assertEquals(5, events.first().ts)
        assertTrue(events.first().id.isNotEmpty())
    }

    @Test
    fun ignoresUnknownFields() {
        val events = EventLogParser.parseEventLog(
            """[{"id":"c","ts":7,"severity":"low","future_field":123}]"""
        )
        assertEquals(1, events.size)
        assertEquals("c", events.first().id)
    }
}
