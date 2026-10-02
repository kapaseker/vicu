package com.rockbyte.vicu.repo

import org.junit.Assert.*
import org.junit.Test

class ImageCropGeometryTest {
    @Test
    fun fullImagePreservesOddDimensions() {
        assertEquals(ImageCropRegion(0, 0, 1921, 1081), imageCropRegion(0f, 0f, 1f, 1f, 1921, 1081))
    }

    @Test
    fun edgesRoundIndependentlyAndTinyRegionsStayNonempty() {
        assertEquals(ImageCropRegion(2, 1, 5, 4), imageCropRegion(.25f, .25f, .75f, .75f, 7, 5))
        assertEquals(ImageCropRegion(0, 0, 1, 1), imageCropRegion(.9f, .9f, 1f, 1f, 1, 1))
        assertEquals(ImageCropRegion(0, 0, 7, 5), imageCropRegion(-1f, -1f, 2f, 2f, 7, 5))
    }

    @Test
    fun invalidDimensionsAndNonfiniteBoundsAreRejected() {
        for (bounds in listOf(floatArrayOf(0f, 0f, 1f, 1f), floatArrayOf(Float.NaN, 0f, 1f, 1f))) {
            assertThrows(IllegalArgumentException::class.java) {
                imageCropRegion(bounds[0], bounds[1], bounds[2], bounds[3], 0, 5)
            }
        }
        assertThrows(IllegalArgumentException::class.java) { imageCropRegion(Float.NaN, 0f, 1f, 1f, 7, 5) }
        assertThrows(IllegalArgumentException::class.java) { imageCropRegion(.8f, 0f, .2f, 1f, 7, 5) }
    }

    @Test
    fun formatUsesDecodedMimeRatherThanFilename() {
        assertEquals(ImageCropFormat.JPEG, imageCropFormat("image/jpeg"))
        for (mime in listOf("image/png", "image/webp", "image/heif")) {
            assertEquals(ImageCropFormat.PNG, imageCropFormat(mime))
        }
    }

    @Test
    fun memoryIncludesFullSourceCropAndPreviewAndAcceptsExactBudget() {
        val info = ImageCropInfo(4000, 3000, "image/jpeg")
        val region = ImageCropRegion(0, 0, 2000, 1500)
        val bytes = (12_000_000L + 3_000_000L) * 4 + 2048L * 1536 * 4
        assertTrue(imageCropFitsMemory(info, region, 2048L * 1536 * 4, bytes * 2))
        assertFalse(imageCropFitsMemory(info, region, 2048L * 1536 * 4, (bytes - 1) * 2))
        assertFalse(imageCropFitsMemory(ImageCropInfo(10000, 10000, "image/png"),
            ImageCropRegion(0, 0, 1, 1), 0, Long.MAX_VALUE))
    }
}
