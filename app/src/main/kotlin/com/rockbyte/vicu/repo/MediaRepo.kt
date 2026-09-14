package com.rockbyte.vicu.repo

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.serialization.Serializable

/** 首页媒体库条目。 */
data class MediaItem(
    val uri: Uri,
    val name: String,
    val kind: MediaKind,
    val dateAdded: Long,
)

@Serializable
enum class MediaKind { IMAGE, VIDEO, AUDIO }

@Serializable
data class SelectedMedia(
    val uri: String,
    val name: String,
    val kind: MediaKind,
)

data class MediaLibrary(
    val items: List<MediaItem>,
    val hasAccess: Boolean,
    val permissionsToRequest: List<String>,
)

/** MediaStore 聚合查询（domain-facing 数据操作）。 */
interface MediaRepo {
    /** 返回当前可读集合及请求媒体访问所需的权限。 */
    suspend fun loadLibrary(): MediaLibrary

    /** 读取媒体预览；媒体不可读或预览失败时返回 null。 */
    suspend fun loadThumbnail(uri: Uri, width: Int, height: Int): Bitmap?
}
