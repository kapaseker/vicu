package com.rockbyte.vicu.repo

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import android.test.AndroidTestCase
import java.io.File

/** Uses only the platform runner to exercise real decoding, pixel coordinates and publication. */
@Suppress("DEPRECATION")
class ImageCropStorageTest : AndroidTestCase() {
    private val sourceFiles = mutableListOf<File>()
    private val outputs = mutableListOf<Uri>()
    private lateinit var store: ImageCropStorage

    override fun setUp() {
        super.setUp()
        store = ImageCropStorage(context.contentResolver, System::currentTimeMillis) { 512L * 1024 * 1024 }
    }

    override fun tearDown() {
        outputs.forEach { store.delete(it) }
        sourceFiles.forEach { it.delete() }
        super.tearDown()
    }

    private fun source(format: Bitmap.CompressFormat, width: Int = 401, height: Int = 301): File {
        val file = File.createTempFile("crop-test-", ".image", context.cacheDir)
        sourceFiles += file
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) for (x in 0 until width) {
            bitmap.setPixel(x, y, when {
                x < width / 2 && y < height / 2 -> Color.RED
                x >= width / 2 && y < height / 2 -> Color.GREEN
                x < width / 2 -> Color.BLUE
                else -> if (format == Bitmap.CompressFormat.PNG) Color.TRANSPARENT else Color.YELLOW
            })
        }
        file.outputStream().use { assertTrue(bitmap.compress(format, 100, it)) }
        bitmap.recycle()
        return file
    }

    private fun save(file: File, region: ImageCropRegion): Uri {
        val image = store.decodeCrop(Uri.fromFile(file), region, 0)
        val format = imageCropFormat(image.info.mime)
        val output = store.create("device-test.image", "_crop", format)
        outputs += output
        try { store.write(output, image.bitmap, format) } finally { image.bitmap.recycle() }
        store.publish(output)
        return output
    }

    private fun read(uri: Uri): Bitmap = ImageDecoder.decodeBitmap(
        ImageDecoder.createSource(context.contentResolver, uri),
    ) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }

    fun testOddPngCropPreservesPixelsTransparencyAndPublishes() {
        val file = source(Bitmap.CompressFormat.PNG)
        val region = ImageCropRegion(100, 75, 401, 301)
        val result = save(file, region)
        val bitmap = read(result)
        try {
            assertEquals(301, bitmap.width); assertEquals(226, bitmap.height)
            assertEquals(Color.RED, bitmap.getPixel(0, 0))
            assertEquals(Color.GREEN, bitmap.getPixel(300, 0))
            assertEquals(Color.BLUE, bitmap.getPixel(0, 225))
            assertEquals(0, Color.alpha(bitmap.getPixel(300, 225)))
            assertEquals(ColorSpace.get(ColorSpace.Named.SRGB), bitmap.colorSpace)
        } finally { bitmap.recycle() }
        context.contentResolver.query(result, arrayOf(MediaStore.MediaColumns.IS_PENDING,
            MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.MIME_TYPE), null, null, null)!!.use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            assertEquals("Pictures/Vicu/", it.getString(1)); assertEquals("image/png", it.getString(2))
        }
        assertTrue(file.exists())
    }

    fun testJpegFullImageKeepsOddSizeAndDoesNotCopyExif() {
        val file = source(Bitmap.CompressFormat.JPEG)
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_DATETIME, "2020:01:02 03:04:05")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "1/1,2/1,3/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            saveAttributes()
        }
        val result = save(file, ImageCropRegion(0, 0, 401, 301))
        val bitmap = read(result)
        try { assertEquals(401, bitmap.width); assertEquals(301, bitmap.height) } finally { bitmap.recycle() }
        context.contentResolver.openInputStream(result)!!.use {
            val exif = ExifInterface(it)
            assertNull(exif.getAttribute(ExifInterface.TAG_DATETIME))
            assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        }
        assertEquals("image/jpeg", context.contentResolver.getType(result))
    }

    fun testEveryExifOrientationAgreesBetweenPreviewAndCrop() {
        for (orientation in 1..8) {
            val file = source(Bitmap.CompressFormat.JPEG)
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes()
            }
            val preview = store.load(Uri.fromFile(file))
            try {
                val rotated = orientation >= 5
                assertEquals(if (rotated) 301 else 401, preview.info.width)
                assertEquals(if (rotated) 401 else 301, preview.info.height)
                assertEquals(preview.info.width, preview.bitmap.width)
                assertEquals(preview.info.height, preview.bitmap.height)
                val expectedCorner = intArrayOf(Color.RED, Color.GREEN, Color.YELLOW, Color.BLUE,
                    Color.RED, Color.BLUE, Color.YELLOW, Color.GREEN)[orientation - 1]
                val actualCorner = preview.bitmap.getPixel(25, 25)
                for (channel in listOf(Color::red, Color::green, Color::blue)) {
                    assertTrue("Incorrect EXIF orientation $orientation",
                        kotlin.math.abs(channel(expectedCorner) - channel(actualCorner)) <= 5)
                }
                val region = ImageCropRegion(10, 10, 70, 70)
                val cropped = store.decodeCrop(Uri.fromFile(file), region, preview.bitmap.allocationByteCount.toLong())
                try {
                    assertEquals(60, cropped.bitmap.width); assertEquals(60, cropped.bitmap.height)
                    assertEquals(preview.bitmap.getPixel(25, 25), cropped.bitmap.getPixel(15, 15))
                } finally { cropped.bitmap.recycle() }
            } finally { preview.bitmap.recycle() }
        }
    }

    fun testStaticWebpLoadsAndOutputsPng() {
        val file = source(Bitmap.CompressFormat.WEBP_LOSSLESS)
        val preview = store.load(Uri.fromFile(file))
        try { assertEquals("image/webp", preview.info.mime) } finally { preview.bitmap.recycle() }
        val result = save(file, ImageCropRegion(0, 0, 401, 301))
        assertEquals("image/png", context.contentResolver.getType(result))
    }

    fun testAnimationIsRejected() {
        val file = File.createTempFile("animated-", ".gif", context.cacheDir); sourceFiles += file
        // AndroidTestCase receives the target context; the asset belongs to the test APK.
        val testContext = context.createPackageContext("com.rockbyte.vicu.test", 0)
        testContext.assets.open("animated.gif").use { input -> file.outputStream().use(input::copyTo) }
        try { store.load(Uri.fromFile(file)); fail("Animation must be rejected") }
        catch (error: ImageCropException) { assertEquals(ImageCropError.AnimatedUnsupported, error.error) }
    }

    fun testPreviewIsSampledButExportMemoryIncludesOriginal() {
        val file = source(Bitmap.CompressFormat.PNG, 4097, 2049)
        val preview = store.load(Uri.fromFile(file))
        try {
            assertEquals(4097, preview.info.width); assertEquals(2049, preview.info.height)
            assertEquals(2048, preview.bitmap.width)
            val smallBudgetStore = ImageCropStorage(context.contentResolver, System::currentTimeMillis) { 16L * 1024 * 1024 }
            try {
                smallBudgetStore.decodeCrop(Uri.fromFile(file), ImageCropRegion(0, 0, 10, 10), preview.bitmap.allocationByteCount.toLong())
                fail("Even a tiny crop must account for full source decoding")
            } catch (error: ImageCropException) { assertEquals(ImageCropError.ImageTooLarge, error.error) }
        } finally { preview.bitmap.recycle() }
    }
}
