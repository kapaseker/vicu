package com.rockbyte.vicu.repo

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlin.math.roundToInt

internal class ImageCropStorage(
    private val resolver: ContentResolver,
    private val currentTimeMillis: () -> Long,
    private val maxHeapBytes: () -> Long,
) : ImageCropStore {
    override fun load(uri: Uri): ImageCropPreview = decode(uri, null, 0)

    override fun decodeCrop(uri: Uri, region: ImageCropRegion, previewBytes: Long): ImageCropPreview =
        decode(uri, region, previewBytes)

    private fun decode(uri: Uri, region: ImageCropRegion?, previewBytes: Long): ImageCropPreview {
        lateinit var sourceInfo: ImageCropInfo
        val bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                if (info.isAnimated) throw ImageCropException(ImageCropError.AnimatedUnsupported)
                // ImageDecoder applies EXIF orientation/mirroring to both header size and decoded pixels.
                sourceInfo = ImageCropInfo(info.size.width, info.size.height, info.mimeType)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                if (region == null) {
                    val scale = minOf(1.0, 2048.0 / maxOf(sourceInfo.width, sourceInfo.height))
                    decoder.setTargetSize(
                        (sourceInfo.width * scale).roundToInt().coerceAtLeast(1),
                        (sourceInfo.height * scale).roundToInt().coerceAtLeast(1),
                    )
                } else {
                    require(region.left >= 0 && region.top >= 0 && region.right <= sourceInfo.width &&
                        region.bottom <= sourceInfo.height && region.width > 0 && region.height > 0)
                    if (!imageCropFitsMemory(sourceInfo, region, previewBytes, maxHeapBytes())) {
                        throw ImageCropException(ImageCropError.ImageTooLarge)
                    }
                    decoder.crop = Rect(region.left, region.top, region.right, region.bottom)
                }
            }
        } catch (error: OutOfMemoryError) {
            throw ImageCropException(ImageCropError.ImageTooLarge, error)
        }
        // Force ordinary RGBA8 pixels; drop any HDR gainmap before encoding.
        if (android.os.Build.VERSION.SDK_INT >= 34) bitmap.setGainmap(null)
        if (bitmap.config == Bitmap.Config.ARGB_8888) return ImageCropPreview(bitmap, sourceInfo)
        try {
            return ImageCropPreview(checkNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)), sourceInfo)
        } catch (error: OutOfMemoryError) {
            throw ImageCropException(ImageCropError.ImageTooLarge, error)
        } finally {
            bitmap.recycle()
        }
    }

    override fun create(displayName: String, format: ImageCropFormat): Uri {
        val stem = displayName.substringBeforeLast('.', displayName)
            .replace(Regex("[^\\p{L}\\p{N}._-]"), "_").trim('_').ifBlank { "image" }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${stem}_crop_${currentTimeMillis()}.${format.extension}")
            put(MediaStore.MediaColumns.MIME_TYPE, format.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Vicu")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return checkNotNull(resolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values,
        )) { "Image output creation failed" }
    }

    override fun write(uri: Uri, bitmap: Bitmap, format: ImageCropFormat) {
        checkNotNull(resolver.openOutputStream(uri, "w")).use { output ->
            val compression = if (format == ImageCropFormat.JPEG) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
            try {
                check(bitmap.compress(compression, 95, output)) { "Image encoding failed" }
            } catch (error: OutOfMemoryError) {
                throw ImageCropException(ImageCropError.ImageTooLarge, error)
            }
        }
    }

    override fun publish(uri: Uri) {
        check(resolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null) > 0) { "Image output publication failed" }
    }

    override fun delete(uri: Uri) {
        resolver.delete(uri, null, null)
    }
}
