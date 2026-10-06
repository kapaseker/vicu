package com.rockbyte.vicu.page

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.MediaItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

internal data class MediaDateGroup(val date: LocalDate, val items: List<MediaItem>)

internal fun groupMediaByDate(
    items: List<MediaItem>,
    zoneId: ZoneId,
): List<MediaDateGroup> = items
    .groupBy { Instant.ofEpochSecond(it.dateAdded).atZone(zoneId).toLocalDate() }
    .map { (date, dateItems) -> MediaDateGroup(date, dateItems) }

internal sealed interface MediaDateLabel {
    data object Today : MediaDateLabel
    data object Yesterday : MediaDateLabel
    data class MonthDay(val month: Int, val day: Int) : MediaDateLabel
    data class YearMonthDay(val year: Int, val month: Int, val day: Int) : MediaDateLabel
}

internal fun mediaDateLabel(date: LocalDate, today: LocalDate): MediaDateLabel = when {
    date == today -> MediaDateLabel.Today
    date == today.minusDays(1) -> MediaDateLabel.Yesterday
    date.year == today.year -> MediaDateLabel.MonthDay(date.monthValue, date.dayOfMonth)
    else -> MediaDateLabel.YearMonthDay(date.year, date.monthValue, date.dayOfMonth)
}

@Composable
internal fun MediaDateGroup.label(today: LocalDate): String = when (val label = mediaDateLabel(date, today)) {
    MediaDateLabel.Today -> stringResource(R.string.media_date_today)
    MediaDateLabel.Yesterday -> stringResource(R.string.media_date_yesterday)
    is MediaDateLabel.MonthDay -> stringResource(R.string.media_date_month_day, label.month, label.day)
    is MediaDateLabel.YearMonthDay -> stringResource(
        R.string.media_date_year_month_day,
        label.year,
        label.month,
        label.day,
    )
}

/** 超过 1 小时按 h:mm:ss，否则 m:ss（与播放页时钟格式一致）。 */
internal fun formatMediaClock(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
