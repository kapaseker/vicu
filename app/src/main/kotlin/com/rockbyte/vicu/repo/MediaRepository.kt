package com.rockbyte.vicu.repo

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * [MediaRepo] 实现：构造后即监听 MediaStore 变更，通过 [MediaLibraryStore] 重查并聚合。
 */
internal class MediaRepository(
    private val resolver: ContentResolver,
    private val mediaStore: MediaLibraryStore,
) : MediaRepo {

    // ponytail: repo 由 Koin 以 single 持有，进程级生命周期，scope 无需显式 cancel
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // replay=1 而非 StateFlow：StateFlow 会去重，授权后重查结果不变时（如该类型无媒体）
    // 下游收不到发射，loading 会卡死；SharedFlow 保证每次重查都发射
    private val _library = MutableSharedFlow<List<MediaItem>>(replay = 1)
    override val library: SharedFlow<List<MediaItem>> = _library

    init {
        scope.launch { requery() }
        scope.launch {
            observeMediaStoreChanges().collectLatest { requery() }
        }
    }

    override fun refresh() {
        scope.launch { requery() }
    }

    private suspend fun requery() {
        // SecurityException 捕获是查询容错而非权限判断：未授权的类型查询自然失败返回空
        _library.emit(
            MediaKind.entries.flatMap { kind ->
                try {
                    mediaStore.query(kind)
                } catch (_: SecurityException) {
                    emptyList()
                }
            }.sortedByDescending(MediaItem::dateAdded)
        )
    }

    private fun observeMediaStoreChanges(): Flow<Unit> = callbackFlow {
        // trySend 线程安全；不传 Handler 则回调可能来自 binder 线程，无需切线程
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }
        for (kind in MediaKind.entries) {
            resolver.registerContentObserver(kind.collection, true, observer)
        }
        awaitClose { resolver.unregisterContentObserver(observer) }
    }
}

/** MediaStore 本地存储查询。 */
internal interface MediaLibraryStore {
    fun query(kind: MediaKind): List<MediaItem>
}

private val MediaKind.collection: Uri
    get() = when (this) {
        MediaKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        MediaKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }
