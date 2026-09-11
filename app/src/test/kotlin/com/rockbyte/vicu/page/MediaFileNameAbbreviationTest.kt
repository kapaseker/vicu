package com.rockbyte.vicu.page

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFileNameAbbreviationTest {

    @Test
    fun stemWithinTenCharactersKeptIntact() {
        assertEquals("clip.mp4", abbreviateMediaFileName("clip.mp4"))
        assertEquals("abcdefghij.mp4", abbreviateMediaFileName("abcdefghij.mp4"))
    }

    @Test
    fun stemOverTenCharactersCollapsesMiddle() {
        assertEquals("abcde***ghijk.mp4", abbreviateMediaFileName("abcdefghijk.mp4"))
        assertEquals("abcde***klmno.mp4", abbreviateMediaFileName("abcdefghijklmno.mp4"))
    }

    @Test
    fun extensionExcludedFromCount() {
        // 名称恰 10 字符 + 长后缀：不触发省略
        assertEquals("abcdefghij.mp4", abbreviateMediaFileName("abcdefghij.mp4"))
    }

    @Test
    fun nameWithoutExtensionAlsoAbbreviated() {
        assertEquals("abcde***ghijk", abbreviateMediaFileName("abcdefghijk"))
    }
}
