package com.rockbyte.vicu.page

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.GalleryRepo
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.repo.SelectedMedia
import kotlinx.coroutines.launch

/** 功能列表页共用的分享与保存入口：分享交给系统面板，保存复制进系统相册。 */
class FunctionsViewModel(
    private val appContext: Context,
    private val galleryRepo: GalleryRepo,
    private val shareLauncher: (Intent) -> Unit = { appContext.startActivity(it) },
) : ViewModel() {

    private var saving = false

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
}

internal val MediaKind.shareMimeType: String
    get() = when (this) {
        MediaKind.IMAGE -> "image/*"
        MediaKind.VIDEO -> "video/*"
        MediaKind.AUDIO -> "audio/*"
    }