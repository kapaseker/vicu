package com.rockbyte.vicu.repo

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class ImageCropRepository(private val store: ImageCropStore) : ImageCropRepo {
    override suspend fun load(uri: Uri): Result<ImageCropPreview> {
        var preview: ImageCropPreview? = null
        return try {
            withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                preview = store.load(uri)
                Result.success(checkNotNull(preview))
            }
        } catch (error: Exception) {
            preview?.bitmap?.recycle()
            if (error is CancellationException) throw error
            Result.failure(error.asImageCropException(ImageCropError.LoadFailed))
        }
    }

    override suspend fun crop(
        uri: Uri, displayName: String, region: ImageCropRegion, previewBytes: Long,
    ): Result<Uri> {
        var output: Uri? = null
        var failureType = ImageCropError.CropFailed
        return try {
            withContext(Dispatchers.IO) {
                require(region.left >= 0 && region.top >= 0 && region.right > region.left && region.bottom > region.top)
                currentCoroutineContext().ensureActive()
                val image = store.decodeCrop(uri, region, previewBytes)
                try {
                    currentCoroutineContext().ensureActive()
                    val format = imageCropFormat(image.info.mime)
                    failureType = ImageCropError.SaveFailed
                    val destination = store.create(displayName, format)
                    output = destination
                    currentCoroutineContext().ensureActive()
                    store.write(destination, image.bitmap, format)
                    currentCoroutineContext().ensureActive()
                    store.publish(destination)
                    Result.success(destination)
                } finally {
                    image.bitmap.recycle()
                }
            }
        } catch (error: Exception) {
            output?.let { destination ->
                withContext(NonCancellable + Dispatchers.IO) {
                    try { store.delete(destination) } catch (cleanupError: Exception) {
                        if (cleanupError !== error) error.addSuppressed(cleanupError)
                    }
                }
            }
            if (error is CancellationException) throw error
            Result.failure(error.asImageCropException(failureType))
        }
    }
}

private fun Exception.asImageCropException(fallback: ImageCropError): ImageCropException =
    this as? ImageCropException ?: ImageCropException(fallback, this)

internal interface ImageCropStore {
    fun load(uri: Uri): ImageCropPreview
    fun decodeCrop(uri: Uri, region: ImageCropRegion, previewBytes: Long): ImageCropPreview
    fun create(displayName: String, format: ImageCropFormat): Uri
    fun write(uri: Uri, bitmap: Bitmap, format: ImageCropFormat)
    fun publish(uri: Uri)
    fun delete(uri: Uri)
}
