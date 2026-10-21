package com.rockbyte.vicu.repo

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class WorksLibraryState(
    val items: List<MediaItem> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
)

interface WorksRepo {
    val library: StateFlow<WorksLibraryState>
    fun refresh()

    /**
     * 重命名作品为仅展示用的新名字（不含路径语义，磁盘文件名不变）。
     * 作品 uri 内嵌文件名，真实改名会使既有页面与路由中的 uri 全部失效，
     * 故展示名与文件名解耦：只更新 completed.json 的 displayName 并广播变更。
     */
    suspend fun rename(uri: Uri, displayName: String)
}

internal interface WorksStore {
    val changes: Flow<Unit>
    fun create(inputName: String, suffix: String, extension: String, kind: MediaKind): Uri
    fun publish(uri: Uri)
    fun delete(uri: Uri)
    fun rename(uri: Uri, displayName: String)
    fun query(): List<MediaItem>
}
