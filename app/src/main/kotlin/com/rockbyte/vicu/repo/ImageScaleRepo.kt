package com.rockbyte.vicu.repo

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

interface ImageScaleRepo {
    suspend fun load(uri: Uri): Result<ImageCropPreview>
    suspend fun scale(uri: Uri, displayName: String, width: Int, height: Int, previewBytes: Long): Result<Uri>
}

internal class ImageScaleRepository(private val store: ImageCropStore) : ImageScaleRepo {
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
            Result.failure(error.asImageScaleException(ImageCropError.LoadFailed))
        }
    }

    override suspend fun scale(
        uri: Uri, displayName: String, width: Int, height: Int, previewBytes: Long,
    ): Result<Uri> {
        var output: Uri? = null
        var failureType = ImageCropError.CropFailed
        return try {
            withContext(Dispatchers.IO) {
                require(width > 0 && height > 0)
                currentCoroutineContext().ensureActive()
                val image = store.decodeScaled(uri, width, height, previewBytes)
                try {
                    currentCoroutineContext().ensureActive()
                    val format = imageCropFormat(image.info.mime)
                    failureType = ImageCropError.SaveFailed
                    val destination = store.create(displayName, "_scale", format)
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
            Result.failure(error.asImageScaleException(failureType))
        }
    }
}

private fun Exception.asImageScaleException(fallback: ImageCropError): ImageCropException =
    this as? ImageCropException ?: ImageCropException(fallback, this)
