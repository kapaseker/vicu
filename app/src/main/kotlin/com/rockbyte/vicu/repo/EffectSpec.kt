package com.rockbyte.vicu.repo

/**
 * 效果描述（预览与导出共用，WYSIWYG）：
 * - crop 进 libavfilter 滤镜链，播放预览与 `-vf` 导出渲染同一字符串；
 * - trim 不进滤镜链，预览走播放区间（seek clamp + 终点停播），导出走 `-ss/-to`。
 */
sealed interface EffectSpec {
    data class Crop(val left: Int, val top: Int, val width: Int, val height: Int) : EffectSpec

    data class Trim(val startMs: Long, val endMs: Long) : EffectSpec
}

/** YUV420 色度对齐：坐标与尺寸偶数化（向下取整）、起点非负、尺寸至少 2。 */
internal fun EffectSpec.Crop.normalized(): EffectSpec.Crop = EffectSpec.Crop(
    left = (left.coerceAtLeast(0)) / 2 * 2,
    top = (top.coerceAtLeast(0)) / 2 * 2,
    width = (width.coerceAtLeast(2)) / 2 * 2,
    height = (height.coerceAtLeast(2)) / 2 * 2,
)

/** 预览滤镜链：crop 依列表顺序逗号连接；trim 不进链。空串表示无滤镜（直通）。 */
internal fun List<EffectSpec>.toPreviewFilterChain(): String =
    filterIsInstance<EffectSpec.Crop>()
        .joinToString(",") { c ->
            val n = c.normalized()
            "crop=w=${n.width}:h=${n.height}:x=${n.left}:y=${n.top}"
        }

/** 导出参数（输出侧 seek）：trim → `-ss start -to end`（毫秒），crop → `-vf` 滤镜链。 */
internal fun List<EffectSpec>.toExportArguments(): Array<String> {
    val arguments = mutableListOf<String>()
    effects<EffectSpec.Trim>().firstOrNull()?.let { trim ->
        arguments += arrayOf("-ss", "${trim.startMs}ms", "-to", "${trim.endMs}ms")
    }
    toPreviewFilterChain().takeIf { it.isNotEmpty() }?.let { chain ->
        arguments += arrayOf("-vf", chain)
    }
    return arguments.toTypedArray()
}

private inline fun <reified T : EffectSpec> List<EffectSpec>.effects(): List<T> =
    filterIsInstance<T>()
