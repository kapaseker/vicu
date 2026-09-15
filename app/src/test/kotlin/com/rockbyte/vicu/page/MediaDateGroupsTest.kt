package com.rockbyte.vicu.page

import android.net.Uri
import com.rockbyte.vicu.repo.MediaItem
import com.rockbyte.vicu.repo.MediaKind
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class MediaDateGroupsTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun groupsMixedMediaByLocalDateAndPreservesRepositoryOrder() {
        val newest = item("new.jpg", MediaKind.IMAGE, epochSecond(2026, 9, 16, 23, 30))
        val sameDay = item("same-day.mp3", MediaKind.AUDIO, epochSecond(2026, 9, 16, 0, 0))
        val older = item("old.mp4", MediaKind.VIDEO, epochSecond(2026, 9, 15, 23, 59))

        val groups = groupMediaByDate(listOf(newest, sameDay, older), zone)

        assertEquals(listOf(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 15)), groups.map { it.date })
        assertEquals(listOf(newest, sameDay), groups.first().items)
        assertEquals(listOf(older), groups.last().items)
    }

    @Test
    fun returnsNoGroupsForEmptyLibrary() {
        assertEquals(emptyList<MediaDateGroup>(), groupMediaByDate(emptyList(), zone))
    }

    @Test
    fun keepsNewYearsEveAndNewYearsDayInSeparateGroups() {
        val newYear = item("new-year.jpg", MediaKind.IMAGE, epochSecond(2026, 1, 1, 0, 0))
        val oldYear = item("old-year.jpg", MediaKind.IMAGE, epochSecond(2025, 12, 31, 23, 59))

        val groups = groupMediaByDate(listOf(newYear, oldYear), zone)

        assertEquals(listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2025, 12, 31)), groups.map { it.date })
    }

    @Test
    fun formatsTodayYesterdayCurrentYearAndPreviousYear() {
        val today = LocalDate.of(2026, 9, 16)

        assertEquals(MediaDateLabel.Today, mediaDateLabel(today, today))
        assertEquals(MediaDateLabel.Yesterday, mediaDateLabel(today.minusDays(1), today))
        assertEquals(MediaDateLabel.MonthDay(9, 13), mediaDateLabel(LocalDate.of(2026, 9, 13), today))
        assertEquals(MediaDateLabel.YearMonthDay(2025, 9, 13), mediaDateLabel(LocalDate.of(2025, 9, 13), today))
    }

    private fun item(name: String, kind: MediaKind, dateAdded: Long) =
        MediaItem(mock(Uri::class.java), name, kind, dateAdded)

    private fun epochSecond(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toEpochSecond()
}
