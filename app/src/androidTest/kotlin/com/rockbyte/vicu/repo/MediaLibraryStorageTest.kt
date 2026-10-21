package com.rockbyte.vicu.repo

import android.net.Uri
import android.provider.MediaStore
import android.test.AndroidTestCase
import java.io.File

/** GalleryStorage 插入 + MediaLibraryStorage rename 的真实 MediaStore 往返。 */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class MediaLibraryStorageTest : AndroidTestCase() {
    private val storage = MediaLibraryStorage(context)
    private val inserted = mutableListOf<Uri>()

    override fun tearDown() {
        inserted.forEach { context.contentResolver.delete(it, null, null) }
        super.tearDown()
    }

    private fun save(name: String): File {
        val source = File.createTempFile("rename-src", ".png", context.cacheDir)
        source.writeBytes(byteArrayOf(1, 2, 3))
        GalleryStorage(context).save(name, MediaKind.IMAGE, Uri.fromFile(source))
        storage.query(MediaKind.IMAGE).filter { it.name == name }
            .forEach { inserted += it.uri }
        return source
    }

    fun testRenameUpdatesDisplayNameAndLeavesUriStable() {
        val source = save("rename-me.png")
        try {
            val item = storage.query(MediaKind.IMAGE).single { it.name == "rename-me.png" }

            storage.rename(item.uri, "renamed.png")

            val names = storage.query(MediaKind.IMAGE).map { it.name }
            assertFalse("rename-me.png" in names)
            assertTrue("renamed.png" in names)
            // Q+ 稳定 uri：重命名后 uri 不变
            assertTrue(storage.query(MediaKind.IMAGE).single { it.name == "renamed.png" }.uri == item.uri)
        } finally {
            source.delete()
        }
    }
}
