package com.rockbyte.vicu.repo

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.test.AndroidTestCase
import java.io.File
import java.io.IOException
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.rockbyte.vicu.player.PlayerEvent
import com.rockbyte.vicu.player.PlayerRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Real FileProvider access and atomic publication without a MediaStore insertion. */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class WorksStorageTest : AndroidTestCase() {
    private lateinit var store: WorksStorage
    private val outputs = mutableListOf<Uri>()
    private var now = 1_800_000_000_000L

    override fun setUp() {
        super.setUp()
        store = WorksStorage(context, { now })
        store.query() // Finish startup cleanup before creating test outputs.
    }
    override fun tearDown() {
        outputs.forEach { store.delete(it) }
        super.tearDown()
    }
    private fun create(kind: MediaKind = MediaKind.IMAGE, extension: String = "png"): Uri =
        store.create("作品-测试.png", "_crop", extension, kind).also { outputs += it }

    private fun writeImage(uri: Uri) {
        val bitmap = Bitmap.createBitmap(3, 5, Bitmap.Config.ARGB_8888)
        try {
            context.contentResolver.openOutputStream(uri, "wt")!!.use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 95, it))
            }
        } finally { bitmap.recycle() }
    }
    private fun file(uri: Uri): File = File(context.getExternalFilesDir(null),
        uri.pathSegments.joinToString("/"))

    fun testPendingIsInvisibleAndPublishedUriSurvivesRestartAndReediting() {
        val uri = create()
        writeImage(uri)
        assertFalse(store.query().any { it.uri == uri })
        now += 3000
        store.publish(uri)
        val work = store.query().single { it.uri == uri }
        assertEquals(now / 1000, work.dateAdded)
        assertEquals(MediaKind.IMAGE, work.kind)
        assertEquals(0L, work.durationMs)
        assertTrue(work.name.startsWith("作品-测试_crop_"))
        assertEquals(work, WorksStorage(context, { now }).query().single { it.uri == uri })
        val imageStore = ImageCropStorage(context.contentResolver, store) { 128L * 1024 * 1024 }
        val decoded = imageStore.decodeCrop(uri, ImageCropRegion(0, 0, 2, 3), 0)
        val second = imageStore.create(work.name, "_crop", ImageCropFormat.PNG).also { outputs += it }
        try { imageStore.write(second, decoded.bitmap, ImageCropFormat.PNG) }
        finally { decoded.bitmap.recycle() }
        imageStore.publish(second)
        assertTrue(file(uri).exists())
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, second))
        try { assertEquals(2, bitmap.width); assertEquals(3, bitmap.height) } finally { bitmap.recycle() }
    }

    fun testCollisionDeletionAndInterruptedOutputCleanup() {
        val first = create()
        val second = create()
        assertNotSame(first, second)
        assertEquals(first.lastPathSegment, second.lastPathSegment)
        writeImage(first)
        writeImage(second)
        store.publish(first)
        store.delete(second) // Cancellation/failure removes the entire pending output.
        assertFalse(file(second).exists())
        val interrupted = create()
        writeImage(interrupted)
        val restarted = WorksStorage(context, { now })
        assertTrue(restarted.query().any { it.uri == first })
        assertFalse(file(interrupted).exists())
    }

    fun testEmptyAndFailedMetadataPublicationStayInvisible() {
        val empty = create()
        try { store.publish(empty); fail("Empty output must not publish") } catch (_: IOException) { }
        assertFalse(store.query().any { it.uri == empty })
        val failed = create()
        writeImage(failed)
        val blocked = File(file(failed).parentFile, "completed.json")
        assertTrue(blocked.mkdir())
        File(blocked, "blocker").writeText("blocked", Charsets.UTF_8)
        try { store.publish(failed); fail("Metadata failure must propagate") } catch (_: IOException) { }
        assertFalse(store.query().any { it.uri == failed })
    }

    fun testUnknownDurationDoesNotBlockPublicationAndUnavailableStorageFails() {
        val uri = create(MediaKind.AUDIO, "mp3")
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(byteArrayOf(1, 2, 3)) }
        store.publish(uri)
        assertEquals(0L, store.query().single { it.uri == uri }.durationMs)
        val unavailable = WorksStorage(context, { now }, { null })
        try { unavailable.create("test", "", "png", MediaKind.IMAGE); fail("Must not fall back") }
        catch (_: IOException) { }
    }

    fun testEveryOutputAdapterUsesWorksWithoutInsertingSystemMedia() {
        fun mediaIds(collection: Uri): Set<Long> = context.contentResolver.query(collection,
            arrayOf(MediaStore.MediaColumns._ID), null, null, null)!!.use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }
        val collections = listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        val before = collections.map(::mediaIds)
        val adapters = listOf(
            VideoOutputStorage(store).create("fixture", VideoConvertFormat.MP4) to MediaKind.VIDEO,
            AudioOutputStorage(store).create("fixture", AudioExportFormat.MP3) to MediaKind.AUDIO,
            AudioConvertStorage(store).create("fixture", AudioConvertFormat.MP3) to MediaKind.AUDIO,
            AudioTrimStorage(context, store).create("fixture", AudioTrimFormat.MP3) to MediaKind.AUDIO,
            ImageCropStorage(context.contentResolver, store) { 128L * 1024 * 1024 }
                .create("fixture", "_scale", ImageCropFormat.PNG) to MediaKind.IMAGE,
        )
        adapters.forEach { (uri, kind) ->
            outputs += uri
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(byteArrayOf(1, 2, 3)) }
            store.publish(uri)
            assertEquals("${context.packageName}.works", uri.authority)
            assertEquals(kind, store.query().single { it.uri == uri }.kind)
        }
        assertEquals(before, collections.map(::mediaIds))
    }
    fun testVideoWorksCanConvertAgainExtractAudioAndOpenInNativePlayer() = runBlocking {
        val fixture = File.createTempFile("works-video-", ".mp4", context.cacheDir)
        try {
            val generated = FFmpegKit.executeWithArguments(arrayOf("-y", "-v", "error",
                "-f", "lavfi", "-i", "color=c=red:s=64x48:r=10:d=1",
                "-f", "lavfi", "-i", "sine=duration=1", "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-shortest", fixture.absolutePath))
            assertTrue(generated.getOutput(), ReturnCode.isSuccess(generated.getReturnCode()))
            val converter = FFmpegVideoConverter(context)
            val videos = VideoConvertRepository(converter, VideoOutputStorage(store))
            val first = videos.convert(VideoConvertRequest(Uri.fromFile(fixture), "fixture.mp4",
                VideoConvertFormat.MP4, VideoConvertQuality.SMALLEST))
            assertTrue(first.toString(), first is VideoConvertResult.Success)
            val uri = (first as VideoConvertResult.Success).outputUri.also { outputs += it }
            assertTrue(store.query().single { it.uri == uri }.durationMs > 0)
            val second = videos.convert(VideoConvertRequest(uri, "fixture.mp4",
                VideoConvertFormat.MP4, VideoConvertQuality.SMALLEST, videoFilter = "crop=32:32:0:0"))
            assertTrue(second.toString(), second is VideoConvertResult.Success)
            outputs += (second as VideoConvertResult.Success).outputUri
            assertTrue(file(uri).exists())
            val audio = AudioExportRepository(FFmpegAudioEncoder(context), AudioOutputStorage(store))
                .export(AudioExportRequest(uri, "fixture.mp4", AudioExportFormat.MP3, AudioExportQuality.BALANCED)) {}
            assertTrue(audio.toString(), audio is AudioExportResult.Success)
            outputs += (audio as AudioExportResult.Success).outputUri
            val player = PlayerRepository(context)
            try {
                val prepared = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(10000) { player.events.first { it is PlayerEvent.Prepared || it is PlayerEvent.Failed } }
                }
                player.open(uri)
                val event = prepared.await()
                assertTrue(event.toString(), event is PlayerEvent.Prepared)
                assertEquals(64, (event as PlayerEvent.Prepared).width)
                assertEquals(48, event.height)
            } finally { player.release() }
        } finally { fixture.delete() }
    }

    fun testRepositoryRefreshAndPublicationNotifications() = runBlocking {
        val available = java.util.concurrent.atomic.AtomicBoolean(false)
        val storage = WorksStorage(context, { now }, {
            if (available.get()) File(context.getExternalFilesDir(null), "works") else null
        })
        val repo = WorksRepository(storage)
        withTimeout(10000) { repo.library.first { it.failed && !it.loading } }
        available.set(true)
        repo.refresh()
        withTimeout(10000) { repo.library.first { !it.failed && !it.loading } }
        val output = storage.create("notify.png", "", "png", MediaKind.IMAGE).also { outputs += it }
        writeImage(output)
        storage.publish(output)
        withTimeout(10000) { repo.library.first { it.items.any { item -> item.uri == output } } }
        storage.delete(output)
        withTimeout(10000) { repo.library.first { it.items.none { item -> item.uri == output } } }
    }

}
