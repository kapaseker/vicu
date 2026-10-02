package com.rockbyte.vicu.ui.component

import androidx.compose.ui.unit.Dp

internal fun videoPreviewWidth(availableWidth: Dp, availableHeight: Dp, aspectRatio: Float): Dp =
    minOf(availableWidth, availableHeight * 0.55f * aspectRatio)
