package com.rockbyte.vicu.repo

import android.net.Uri
import kotlinx.coroutines.flow.Flow
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

/** MediaStore 聚合查询（domain-facing 数据操作）。 */
interface MediaRepo {
    /** 媒体库条目流；repo 构造后即监听 MediaStore 变更并自动重查，每次重查都发射最新列表。 */
    val library: Flow<List<MediaItem>>

    /** 权限状态变化后由外部触发重查（授权回调后调用）。 */
    fun refresh()
}
