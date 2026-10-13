package com.rockbyte.vicu.repo

import android.net.Uri

internal interface GalleryStore {
    /** 把 source 复制进系统相册对应集合；失败抛异常，成功返回即已发布。 */
    fun save(name: String, kind: MediaKind, source: Uri)
}

/** 保存作品到系统相册（domain-facing 数据操作）。 */
interface GalleryRepo {
    /** 复制（不移动）作品到系统相册，按类型分流集合与目录。 */
    suspend fun saveToGallery(media: SelectedMedia)
}
