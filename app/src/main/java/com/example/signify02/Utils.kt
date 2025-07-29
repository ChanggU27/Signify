package com.example.signify02

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.google.common.util.concurrent.ListenableFuture
import java.nio.ByteBuffer
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import android.graphics.RectF

// Convert ListenableFuture to suspendable fun
suspend fun <T> ListenableFuture<T>.await(context: Context): T =
    suspendCoroutine { continuation: Continuation<T> ->
        addListener({
            try {
                continuation.resume(get())
            } catch (e: Throwable) {
                continuation.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

// Convert Image to android bitmap
@SuppressLint("UnsafeOptInUsageError")
fun Image.toBitmap(): Bitmap? {
    //Get Image planes
    val planes = this.planes
    if (planes.isEmpty()) {
        Log.e("ImageToBitmap", "Image has no planes.")
        return null
    }

    //Buffer info
    val buffer: ByteBuffer = planes[0].buffer
    val pixelStride: Int = planes[0].pixelStride
    val rowStride: Int = planes[0].rowStride
    val rowPadding = rowStride - pixelStride * width

    val bitmapWidth = width + rowPadding / pixelStride

    if (bitmapWidth <= 0 || height <= 0) {
        Log.e("ImageToBitmap", "Invalid dimensions for bitmap creation: $bitmapWidth x $height")
        return null
    }

    //Create bitmap and apply padding
    val bitmap = createBitmap(bitmapWidth, height)
    buffer.rewind()
    bitmap.copyPixelsFromBuffer(buffer)

    //handle padding and crop if necessary
    if (rowPadding > 0) {
        if (width <= 0 || height <= 0) {
            Log.e("ImageToBitmap", "Invalid dimensions for cropping: $width x $height")
            bitmap.recycle()
            return null
        }
        return try {
            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            bitmap.recycle()
            croppedBitmap
        } catch (e: IllegalArgumentException) {
            Log.e("ImageToBitmap", "Error cropping bitmap", e)
            bitmap.recycle()
            null
        }
    }

    return bitmap
}

// Rotate the given bitmap if necessary
fun Bitmap.rotate(degrees: Float): Bitmap {
    if (degrees == 0f) return this
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

fun cropBitmapWithBoundingBox(
    sourceBitmap: Bitmap,
    normalizedRect: RectF?,
    padding: Float = 0.05f
): Bitmap? {
    if (normalizedRect == null) return null

    val imgHeight = sourceBitmap.height
    val imgWidth = sourceBitmap.width

    if (imgWidth <= 0 || imgHeight <= 0) {
        Log.e("CropError", "Source bitmap has invalid dimensions: ${imgWidth}x${imgHeight}")
        return null
    }

    // Apply padding and clamp to [0, 1]
    val paddedMinX = (normalizedRect.left - padding).coerceAtLeast(0f)
    val paddedMinY = (normalizedRect.top - padding).coerceAtLeast(0f)
    val paddedMaxX = (normalizedRect.right + padding).coerceAtMost(1f)
    val paddedMaxY = (normalizedRect.bottom + padding).coerceAtMost(1f)


    //convert the normalized coordinates to pixel coordinates
    val cropX = (paddedMinX * imgWidth).toInt().coerceIn(0, imgWidth - 1)
    val cropY = (paddedMinY * imgHeight).toInt().coerceIn(0, imgHeight - 1)

    // Calculate width/height
    val cropWidth = ((paddedMaxX - paddedMinX) * imgWidth).toInt().coerceAtLeast(1)
        .coerceAtMost(imgWidth - cropX)
    val cropHeight = ((paddedMaxY - paddedMinY) * imgHeight).toInt().coerceAtLeast(1)
        .coerceAtMost(imgHeight - cropY)

    // check for valid dimensions after coercion
    if (cropWidth <= 0 || cropHeight <= 0) {
        Log.e("CropError", "Invalid crop dimensions after calculation: W=$cropWidth, H=$cropHeight")
        return null
    }

    return try {
        Bitmap.createBitmap(
            sourceBitmap,
            cropX,
            cropY,
            cropWidth,
            cropHeight
        )
    } catch (e: IllegalArgumentException) {
        Log.e("CropError", "Error during Bitmap.createBitmap", e)
        null
    }
}

