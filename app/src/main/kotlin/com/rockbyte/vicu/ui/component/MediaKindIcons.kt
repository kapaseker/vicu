package com.rockbyte.vicu.ui.component

import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.MediaKind

/** 媒体类型 → 展示图标（首页 grid 与功能列表页共用）。 */
internal val MediaKind.iconRes: Int
    get() = when (this) {
        MediaKind.IMAGE -> R.drawable.ic_pic
        MediaKind.VIDEO -> R.drawable.ic_video
        MediaKind.AUDIO -> R.drawable.ic_music
    }
