package com.rockbyte.vicu.repo

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

internal class AudioTrimStorage(
    context: Context,
    private val currentTimeMillis: () -> Long,
) : AudioTrimStore {
    private val resolver = context.contentResolver
    private val cacheDir = context.cacheDir
    override fun create(inputName: String, format: AudioTrimFormat): Uri {
        val baseName = inputName.substringBeforeLast('.', inputName)
            .replace(Regex("[^\\p{L}\\p{N}._-]"), "_").trim('_').ifBlank { "audio" }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${baseName}_${currentTimeMillis()}.${format.extension}")
            put(MediaStore.MediaColumns.MIME_TYPE, format.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/FFmpegKitNext")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return checkNotNull(resolver.insert(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values,
        )) { "Output creation failed" }
    }
    override fun finalizeFlac(uri: Uri, packets: List<AudioPacketInfo>) {
        val temporary = File.createTempFile("audio-trim-flac-", ".flac", cacheDir)
        try {
            checkNotNull(resolver.openFileDescriptor(uri, "r")).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { source ->
                    FileOutputStream(temporary).channel.use { destination ->
                        normalizeFlacStream(source, packets, destination)
                    }
                }
            }
            checkNotNull(resolver.openOutputStream(uri, "wt")).use { destination ->
                temporary.inputStream().use { it.copyTo(destination) }
            }
        } finally { temporary.delete() }
    }
    override fun publish(uri: Uri) {
        check(resolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null) > 0) { "Output publication failed" }
    }
    override fun delete(uri: Uri) {
        resolver.delete(uri, null, null)
    }
}
