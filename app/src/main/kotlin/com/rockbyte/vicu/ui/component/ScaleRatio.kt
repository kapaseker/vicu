package com.rockbyte.vicu.ui.component

import androidx.annotation.StringRes
import androidx.compose.runtime.saveable.listSaver
import com.rockbyte.vicu.R
import kotlin.math.sqrt

/** 预制输出比例。声明顺序即展示顺序；[ratio] 为输出宽高比（宽/高），null 表示跟随源比例。 */
internal enum class ScaleRatioPreset(
    @StringRes val forwardLabel: Int,
    @StringRes val backwardLabel: Int,
    val ratio: Float?,
    val flippable: Boolean,
) {
    Original(R.string.image_scale_ratio_original, R.string.image_scale_ratio_original, null, false),
    Square(R.string.image_scale_ratio_1_1, R.string.image_scale_ratio_1_1, 1f, false),
    ThreeTwo(R.string.image_scale_ratio_3_2, R.string.image_scale_ratio_2_3, 1.5f, true),
    FourThree(R.string.image_scale_ratio_4_3, R.string.image_scale_ratio_3_4, 4f / 3f, true),
    SixteenNine(R.string.image_scale_ratio_16_9, R.string.image_scale_ratio_9_16, 16f / 9f, true),
    TwentyOneNine(R.string.image_scale_ratio_21_9, R.string.image_scale_ratio_9_21, 21f / 9f, true),
    TwoOne(R.string.image_scale_ratio_2_1, R.string.image_scale_ratio_1_2, 2f, true),
    SqrtTwo(R.string.image_scale_ratio_sqrt2_1, R.string.image_scale_ratio_1_sqrt2, sqrt(2f), true),
}

/** 当前方向下的输出宽高比；原比例跟随 [sourceAspect]。 */
internal fun ScaleRatioPreset.outputRatio(flipped: Boolean, sourceAspect: Float): Float =
    ratio?.let { if (flipped) 1f / it else it } ?: sourceAspect

/** 当前方向下的展示文案。 */
internal fun ScaleRatioPreset.labelRes(flipped: Boolean): Int =
    if (flipped) backwardLabel else forwardLabel

/**
 * 比例选择的状态转移：返回 (新选中项, 新翻转记忆)。
 * 点非当前项 → 选中并沿用其记忆方向；点当前可翻转项 → 翻转并写回记忆；其余（原比例、1:1）无操作。
 */
internal fun nextRatioSelection(
    current: ScaleRatioPreset,
    flipped: Set<ScaleRatioPreset>,
    clicked: ScaleRatioPreset,
): Pair<ScaleRatioPreset, Set<ScaleRatioPreset>> = when {
    clicked != current -> clicked to flipped
    clicked.flippable && clicked in flipped -> clicked to (flipped - clicked)
    clicked.flippable -> clicked to (flipped + clicked)
    else -> current to flipped
}

/** 各预设的翻转记忆持久化。 */
internal val scaleRatioFlippedSaver = listSaver<Set<ScaleRatioPreset>, Int>(
    save = { presets -> presets.map(ScaleRatioPreset::ordinal) },
    restore = { ordinals -> ordinals.map { ScaleRatioPreset.entries[it] }.toSet() },
)
