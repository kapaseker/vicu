package com.rockbyte.vicu.repo

import android.net.Uri

/** 首页媒体库条目。 */
data class MediaItem(
    val uri: Uri,
    val name: String,
    val kind: MediaKind,
    val dateAdded: Long,
)

enum class MediaKind { IMAGE, VIDEO, AUDIO }

/** MediaStore 聚合查询（domain-facing 数据操作）。 */
interface MediaRepo {
    /** 聚合当前用户的图片/视频/音频，按添加时间倒序；未授权的集合返回为空。 */
    suspend fun queryAllMedia(): List<MediaItem>
}
