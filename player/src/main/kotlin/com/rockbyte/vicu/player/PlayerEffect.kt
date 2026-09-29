package com.rockbyte.vicu.player

/**
 * 效果描述（预览与导出共用，WYSIWYG）：
 * - crop 进 libavfilter 滤镜链，播放预览与 `-vf` 导出渲染同一字符串；
 * - trim 不进滤镜链，预览走播放区间（seek clamp + 终点停播），导出走 `-ss/-to`。
 */
sealed interface PlayerEffect {
    data class Crop(val left: Int, val top: Int, val width: Int, val height: Int) : PlayerEffect

    data class Trim(val startMs: Long, val endMs: Long) : PlayerEffect
}

/** YUV420 色度对齐：坐标与尺寸偶数化（向下取整）、起点非负、尺寸至少 2。 */
fun PlayerEffect.Crop.normalized(): PlayerEffect.Crop = PlayerEffect.Crop(
    left = (left.coerceAtLeast(0)) / 2 * 2,
    top = (top.coerceAtLeast(0)) / 2 * 2,
    width = (width.coerceAtLeast(2)) / 2 * 2,
    height = (height.coerceAtLeast(2)) / 2 * 2,
)

/** 预览滤镜链：crop 依列表顺序逗号连接；trim 不进链。空串表示无滤镜（直通）。 */
internal fun List<PlayerEffect>.toPreviewFilterChain(): String =
    filterIsInstance<PlayerEffect.Crop>()
        .joinToString(",") { c ->
            val n = c.normalized()
            "crop=w=${n.width}:h=${n.height}:x=${n.left}:y=${n.top}"
        }

/** 导出参数（输出侧 seek）：trim → `-ss start -to end`（毫秒），crop → `-vf` 滤镜链。 */
internal fun List<PlayerEffect>.toExportArguments(): Array<String> {
    val arguments = mutableListOf<String>()
    effects<PlayerEffect.Trim>().firstOrNull()?.let { trim ->
        arguments += arrayOf("-ss", "${trim.startMs}ms", "-to", "${trim.endMs}ms")
    }
    toPreviewFilterChain().takeIf { it.isNotEmpty() }?.let { chain ->
        arguments += arrayOf("-vf", chain)
    }
    return arguments.toTypedArray()
}

private inline fun <reified T : PlayerEffect> List<PlayerEffect>.effects(): List<T> =
    filterIsInstance<T>()
