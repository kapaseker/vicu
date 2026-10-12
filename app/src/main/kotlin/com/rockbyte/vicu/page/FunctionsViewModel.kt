package com.rockbyte.vicu.page

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.repo.SelectedMedia

/** 功能列表页共用的分享入口：把当前媒体交给系统分享面板。 */
class FunctionsViewModel(
    private val appContext: Context,
    private val shareLauncher: (Intent) -> Unit = { appContext.startActivity(it) },
) : ViewModel() {

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
}

internal val MediaKind.shareMimeType: String
    get() = when (this) {
        MediaKind.IMAGE -> "image/*"
        MediaKind.VIDEO -> "video/*"
        MediaKind.AUDIO -> "audio/*"
    }