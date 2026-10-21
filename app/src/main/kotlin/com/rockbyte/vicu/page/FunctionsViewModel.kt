package com.rockbyte.vicu.page

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.GalleryRepo
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.repo.MediaRepo
import com.rockbyte.vicu.repo.MediaSource
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.repo.WorksRepo
import com.rockbyte.vicu.ui.component.RenameDialog
import kotlinx.coroutines.launch

/** 功能列表页共用的分享、保存与重命名入口。 */
class FunctionsViewModel(
    private val appContext: Context,
    private val galleryRepo: GalleryRepo,
    private val mediaRepo: MediaRepo,
    private val worksRepo: WorksRepo,
    private val shareLauncher: (Intent) -> Unit = { appContext.startActivity(it) },
) : ViewModel() {

    private var saving = false
    private var renaming = false

    /** 应用 Context 启动外部 Activity 必须带 NEW_TASK；读权限 flag 由 createChooser 迁移到选择器。 */
    fun share(media: SelectedMedia) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = media.kind.shareMimeType
            putExtra(Intent.EXTRA_STREAM, Uri.parse(media.uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, appContext.getString(R.string.share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        shareLauncher(chooser)
    }

    /** 保存到系统相册，结果以 Toast 结束；保存中忽略重复点击。 */
    fun saveToAlbum(media: SelectedMedia) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                galleryRepo.saveToGallery(media)
                Toast.makeText(appContext, R.string.saved_to_album, Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                Toast.makeText(
                    appContext,
                    appContext.getString(R.string.save_failed, error.message ?: ""),
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                saving = false
            }
        }
    }

    /**
     * 重命名为新主名（扩展名取自当前名字且不可改）：SYSTEM 走 MediaStore（uri 稳定），
     * WORKS 更新作品展示名（磁盘文件名不变）。第三方系统文件无写权限时回调
     * [onNeedsPermission] 携带系统授权 IntentSender，用户允许后重试本调用即可；
     * 其余失败以 Toast 结束。改名中忽略重复点击。
     */
    fun rename(
        media: SelectedMedia,
        stem: String,
        onSuccess: (String) -> Unit,
        onNeedsPermission: (IntentSender) -> Unit,
    ) {
        val name = stem.trim()
        if (renaming || name.isEmpty()) return
        renaming = true
        viewModelScope.launch {
            try {
                val extension = media.name.substringAfterLast('.', "")
                val displayName = if (extension.isEmpty()) name else "$name.$extension"
                when (media.source) {
                    MediaSource.WORKS -> worksRepo.rename(Uri.parse(media.uri), displayName)
                    MediaSource.SYSTEM -> mediaRepo.rename(Uri.parse(media.uri), displayName)
                }
                onSuccess(displayName)
            } catch (error: RecoverableSecurityException) {
                onNeedsPermission(error.userAction.actionIntent.intentSender)
            } catch (error: Exception) {
                Toast.makeText(
                    appContext,
                    appContext.getString(R.string.rename_failed, error.message ?: ""),
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                renaming = false
            }
        }
    }
}

/** 功能页标题：当前文本 + 重命名功能按钮的点击回调（点击弹出改名对话框）。 */
internal data class MediaTitle(val text: String, val onClick: () -> Unit)

/**
 * 功能页重命名接线：点击重命名功能按钮弹出 [RenameDialog]（输入预填去掉扩展名的主名）；
 * 确认后经 [FunctionsViewModel.rename] 执行，需要授权时启动系统对话框
 * （RESULT_OK 视为允许并自动重试，取消则 Toast 提示）。标题立即更新为成功后的新名。
 */
@Composable
internal fun rememberMediaTitle(media: SelectedMedia, viewModel: FunctionsViewModel): MediaTitle {
    var title by remember { mutableStateOf(media.name) }
    var showDialog by remember { mutableStateOf(false) }
    var pendingStem by remember { mutableStateOf("") }
    val context = LocalContext.current

    lateinit var request: (String) -> Unit
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            request(pendingStem)
        } else {
            Toast.makeText(context, R.string.rename_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }
    request = { stem ->
        viewModel.rename(
            media = media,
            stem = stem,
            onSuccess = { title = it },
            onNeedsPermission = { sender ->
                pendingStem = stem
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            },
        )
    }
    if (showDialog) {
        RenameDialog(
            initialValue = title.substringBeforeLast('.', title),
            onConfirm = { stem ->
                showDialog = false
                request(stem)
            },
            onDismiss = { showDialog = false },
        )
    }
    return MediaTitle(title) { showDialog = true }
}

internal val MediaKind.shareMimeType: String
    get() = when (this) {
        MediaKind.IMAGE -> "image/*"
        MediaKind.VIDEO -> "video/*"
        MediaKind.AUDIO -> "audio/*"
    }
