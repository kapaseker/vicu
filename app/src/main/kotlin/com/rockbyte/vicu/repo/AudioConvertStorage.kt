package com.rockbyte.vicu.repo

import android.net.Uri

internal class AudioConvertStorage(private val worksStore: WorksStore) : AudioConvertStore {
    override fun create(inputName: String, format: AudioConvertFormat): Uri =
        worksStore.create(inputName, "", format.extension, MediaKind.AUDIO)
    override fun publish(uri: Uri) = worksStore.publish(uri)
    override fun delete(uri: Uri) = worksStore.delete(uri)
}
