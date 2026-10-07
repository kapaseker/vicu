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
}

internal interface WorksStore {
    val changes: Flow<Unit>
    fun create(inputName: String, suffix: String, extension: String, kind: MediaKind): Uri
    fun publish(uri: Uri)
    fun delete(uri: Uri)
    fun query(): List<MediaItem>
}
