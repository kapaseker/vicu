package com.rockbyte.vicu.page

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rockbyte.vicu.repo.MediaItem
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.repo.MediaRepo
import com.rockbyte.vicu.repo.SelectedMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MediaPickerUiState(
    val loading: Boolean = true,
    val items: List<MediaItem> = emptyList(),
    val hasAccess: Boolean? = null,
    val permissionsToRequest: List<String> = emptyList(),
)

/**
 * 媒体选择页：按 [bind] 传入的媒体类型过滤 [MediaRepo] 聚合库并处理权限。
 * 选中结果经 [selected] 回传——koinViewModel 作用域为 Activity，调用页与本页共享同一实例；
 * 调用页消费后须 [consume] 清空，避免跨页面脏状态。
 */
class MediaPickerViewModel(
    private val appContext: Context,
    private val mediaRepo: MediaRepo,
) : ViewModel() {

    private var kinds: Set<MediaKind> = emptySet()
    private var items: List<MediaItem> = emptyList()

    val uiState: StateFlow<MediaPickerUiState>
        field = MutableStateFlow(MediaPickerUiState())

    val selected: StateFlow<SelectedMedia?>
        field = MutableStateFlow<SelectedMedia?>(null)

    init {
        viewModelScope.launch {
            // repo 每次重查（含授权后 refresh）都会发射，这里统一过滤并重算权限状态
            mediaRepo.library.collect { mediaItems ->
                items = mediaItems
                publishState()
            }
        }
    }

    /** 绑定支持的媒体类型（幂等）；未绑定前保持 loading，避免空权限集误判为无权限。 */
    fun bind(kinds: Set<MediaKind>) {
        if (this.kinds == kinds) return
        this.kinds = kinds
        publishState()
    }

    /** 选中条目并映射为回传结果。 */
    fun confirm(item: MediaItem) {
        selected.value = SelectedMedia(item.uri.toString(), item.name, item.kind)
    }

    /** 调用页消费选中结果后清空。 */
    fun consume() {
        selected.value = null
    }

    /** 权限授权回调后触发 repo 重查。 */
    fun refresh() = mediaRepo.refresh()

    private fun publishState() {
        if (kinds.isEmpty()) return
        val permissions = requiredMediaPermissions()
        uiState.value = MediaPickerUiState(
            loading = false,
            items = items.filter { it.kind in kinds },
            hasAccess = permissions.any {
                appContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            },
            permissionsToRequest = permissions,
        )
    }

    private fun requiredMediaPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            kinds.map { mediaPermissionFor(it, Build.VERSION.SDK_INT) }
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
}
