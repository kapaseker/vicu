package com.rockbyte.vicu.repo

import android.graphics.Bitmap
import android.net.Uri
import kotlin.math.roundToInt

data class ImageCropInfo(val width: Int, val height: Int, val mime: String)
data class ImageCropPreview(val bitmap: Bitmap, val info: ImageCropInfo)

/** Right/bottom are exclusive, in the direction-corrected original image. */
data class ImageCropRegion(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

enum class ImageCropError { LoadFailed, AnimatedUnsupported, ImageTooLarge, CropFailed, SaveFailed }
class ImageCropException(val error: ImageCropError, cause: Throwable? = null) : Exception(error.name, cause)

interface ImageCropRepo {
    suspend fun load(uri: Uri): Result<ImageCropPreview>
    suspend fun crop(uri: Uri, displayName: String, region: ImageCropRegion, previewBytes: Long): Result<Uri>
}

internal enum class ImageCropFormat(val extension: String, val mime: String) {
    JPEG("jpg", "image/jpeg"), PNG("png", "image/png")
}

internal fun imageCropFormat(mime: String): ImageCropFormat =
    if (mime == "image/jpeg") ImageCropFormat.JPEG else ImageCropFormat.PNG

internal fun imageCropRegion(
    left: Float, top: Float, right: Float, bottom: Float, width: Int, height: Int,
): ImageCropRegion {
    require(width > 0 && height > 0)
    require(listOf(left, top, right, bottom).all { it.isFinite() } && left <= right && top <= bottom)
    val x = (left.coerceIn(0f, 1f) * width).roundToInt().coerceIn(0, width - 1)
    val y = (top.coerceIn(0f, 1f) * height).roundToInt().coerceIn(0, height - 1)
    return ImageCropRegion(x, y,
        (right.coerceIn(0f, 1f) * width).roundToInt().coerceIn(x + 1, width),
        (bottom.coerceIn(0f, 1f) * height).roundToInt().coerceIn(y + 1, height))
}

/** ImageDecoder crop can still decode the whole source. Estimate RGBA8 plus output and preview. */
internal fun imageCropFitsMemory(
    info: ImageCropInfo, region: ImageCropRegion, previewBytes: Long, maxHeapBytes: Long,
): Boolean {
    val budget = minOf(128L * 1024 * 1024, maxHeapBytes / 2)
    if (info.width <= 0 || info.height <= 0 || region.width <= 0 || region.height <= 0 || previewBytes < 0) return false
    val sourcePixels = info.width.toLong() * info.height
    val cropPixels = region.width.toLong() * region.height
    // Compare before multiplying by four, so even absurd image headers cannot overflow.
    if (sourcePixels > budget / 4 || cropPixels > budget / 4 || previewBytes > budget) return false
    return sourcePixels * 4 + cropPixels * 4 + previewBytes <= budget
}
