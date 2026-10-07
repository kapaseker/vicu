package com.rockbyte.vicu.repo

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import android.net.Uri

internal class AudioTrimStorage(
    context: Context,
    private val worksStore: WorksStore,
) : AudioTrimStore {
    private val resolver = context.contentResolver
    private val cacheDir = context.cacheDir
    override fun create(inputName: String, format: AudioTrimFormat): Uri =
        worksStore.create(inputName, "", format.extension, MediaKind.AUDIO)
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
    override fun publish(uri: Uri) = worksStore.publish(uri)
    override fun delete(uri: Uri) = worksStore.delete(uri)
}
