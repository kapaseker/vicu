package com.rockbyte.vicu.repo

import android.net.Uri

internal class VideoOutputStorage(private val worksStore: WorksStore) : VideoOutputStore {
    override fun create(inputName: String, format: VideoConvertFormat): Uri =
        worksStore.create(inputName, "", format.extension, MediaKind.VIDEO)
    override fun publish(uri: Uri) = worksStore.publish(uri)
    override fun delete(uri: Uri) = worksStore.delete(uri)
}
