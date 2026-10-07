package com.rockbyte.vicu.repo

import android.net.Uri

internal class AudioOutputStorage(private val worksStore: WorksStore) : AudioOutputStore {
    override fun create(inputName: String, format: AudioExportFormat): Uri =
        worksStore.create(inputName, "", format.extension, MediaKind.AUDIO)
    override fun publish(uri: Uri) = worksStore.publish(uri)
    override fun delete(uri: Uri) = worksStore.delete(uri)
}
