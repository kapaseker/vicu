package com.rockbyte.vicu.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaLibraryStorageDateAddedTest {

    @Test
    fun secondsValuePassesThrough() {
        assertEquals(1753846610L, 1753846610L.normalizeDateAdded())
    }

    @Test
    fun millisecondsValueIsNormalizedToSeconds() {
        // 设备上厂商 App 误写毫秒的真实样本：CutSame-1734330302349.mp4
        assertEquals(1734330305L, 1734330305148L.normalizeDateAdded())
    }

    @Test
    fun ceilingBoundaryStaysSeconds() {
        assertEquals(DATE_ADDED_SECONDS_CEILING, DATE_ADDED_SECONDS_CEILING.normalizeDateAdded())
    }
}
