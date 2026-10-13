package com.rockbyte.vicu.repo

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [GalleryRepo] 实现：IO 线程上把作品复制进系统相册。 */
internal class GalleryRepository(private val store: GalleryStore) : GalleryRepo {
    override suspend fun saveToGallery(media: SelectedMedia) = withContext(Dispatchers.IO) {
        store.save(media.name, media.kind, Uri.parse(media.uri))
    }
}
