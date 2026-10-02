package com.rockbyte.vicu.page.trim

internal enum class TrimInputError { EMPTY, FORMAT, OUT_OF_BOUNDS, ORDER }

internal fun parseTrimTime(text: String): Long? {
    if (!Regex("[0-9]+(\\.[0-9]{1,3})?").matches(text)) return null
    val seconds = text.substringBefore('.').toLongOrNull() ?: return null
    val millis = text.substringAfter('.', "").padEnd(3, '0').toLong()
    if (seconds > (Long.MAX_VALUE - millis) / 1000) return null
    return seconds * 1000 + millis
}

internal fun formatTrimTime(timeMs: Long): String =
    "${timeMs / 1000}.${(timeMs % 1000).toString().padStart(3, '0')}"

internal fun validateTrimInput(start: String, end: String, durationMs: Long): TrimInputError? {
    if (start.isBlank() || end.isBlank()) return TrimInputError.EMPTY
    val startMs = parseTrimTime(start) ?: return TrimInputError.FORMAT
    val endMs = parseTrimTime(end) ?: return TrimInputError.FORMAT
    if (durationMs <= 0 || startMs > durationMs || endMs > durationMs) return TrimInputError.OUT_OF_BOUNDS
    if (startMs >= endMs) return TrimInputError.ORDER
    return null
}
