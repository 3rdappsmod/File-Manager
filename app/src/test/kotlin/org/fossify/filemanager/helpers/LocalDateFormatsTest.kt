package org.fossify.filemanager.helpers

import java.text.SimpleDateFormat
import java.util.GregorianCalendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalDateFormatsTest {
    @Test
    fun oldMonthFirstAndDayFirstPreferencesAreMigrated() {
        listOf("dd.MM.yyyy", "dd/MM/yyyy", "MM/dd/yyyy", "d MMMM yyyy", "MMMM d yyyy", "MM-dd-yyyy", "dd-MM-yyyy")
            .forEach { assertEquals("yyyy-MM-dd", LocalDateFormats.normalize(it)) }
    }

    @Test
    fun yearFirstChoicesArePreservedAndHaveDistinctOutput() {
        val date = GregorianCalendar(2026, GregorianCalendar.OCTOBER, 5).time
        val labels = LocalDateFormats.patterns.map {
            assertEquals(it, LocalDateFormats.normalize(it))
            SimpleDateFormat(it, Locale.KOREA).format(date)
        }
        assertEquals(listOf("2026-10-05", "2026.10.05", "2026년 10월 5일"), labels)
        assertEquals(labels.size, labels.distinct().size)
    }
}
