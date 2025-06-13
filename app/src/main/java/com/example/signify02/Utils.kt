package com.example.signify02

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.media.Image
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import java.nio.ByteBuffer
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max
import kotlin.math.min
import androidx.core.graphics.createBitmap

// ====================================================================================
// --- Helper Functions & Extensions ---
// ====================================================================================

/**
 * Extension function to convert CameraX's ListenableFuture to a Kotlin Coroutine suspend function.
 */
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

/**
 * Helper function to convert an Image (expecting RGBA_8888 format) to a Bitmap.
 */
@SuppressLint("UnsafeOptInUsageError")
fun Image.toBitmap(): Bitmap? {

    val planes = this.planes
    if (planes.isEmpty()) {
        Log.e("ImageToBitmap", "Image has no planes.")
        return null
    }

    val buffer: ByteBuffer = planes[0].buffer
    val pixelStride: Int = planes[0].pixelStride
    val rowStride: Int = planes[0].rowStride
    val rowPadding = rowStride - pixelStride * width

    // Create bitmap config based on image properties
    val bitmapWidth = width + rowPadding / pixelStride

    // Ensure dimensions are valid
    if (bitmapWidth <= 0 || height <= 0) {
        Log.e("ImageToBitmap", "Invalid dimensions for bitmap creation: $bitmapWidth x $height")
        return null
    }

    val bitmap = createBitmap(bitmapWidth, height)

    buffer.rewind()
    bitmap.copyPixelsFromBuffer(buffer)

    // Crop if padding exists
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


/**
 * Helper function to rotate a Bitmap by a specified number of degrees.
 */
fun Bitmap.rotate(degrees: Float): Bitmap {
    if (degrees == 0f) return this // No rotation needed
    val matrix = Matrix().apply { postRotate(degrees) }
    // Create a new bitmap rotated using the matrix
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/**
 * Helper function to crop a Bitmap based on a normalized bounding box.
 */
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
    val paddedMinX = max(0f, normalizedRect.left - padding)
    val paddedMinY = max(0f, normalizedRect.top - padding)
    val paddedMaxX = min(1f, normalizedRect.right + padding)
    val paddedMaxY = min(1f, normalizedRect.bottom + padding)

    // Convert normalized coordinates to pixel coordinates
    val cropX = (paddedMinX * imgWidth).toInt().coerceIn(0, imgWidth - 1)
    val cropY = (paddedMinY * imgHeight).toInt().coerceIn(0, imgHeight - 1)

    // Calculate width/height, ensuring they are at least 1 and don't exceed bounds
    val cropWidth = ((paddedMaxX - paddedMinX) * imgWidth).toInt().coerceAtLeast(1)
        .coerceAtMost(imgWidth - cropX)
    val cropHeight = ((paddedMaxY - paddedMinY) * imgHeight).toInt().coerceAtLeast(1)
        .coerceAtMost(imgHeight - cropY)

    // Basic check for valid dimensions after coercion
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
        Log.e("CropError", "Error during Bitmap.createBitmap with coords ($cropX, $cropY, $cropWidth, $cropHeight) from source ${imgWidth}x${imgHeight}", e)
        null
    }
}
