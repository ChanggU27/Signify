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

    val bitmapWidth = width + rowPadding / pixelStride

    if (bitmapWidth <= 0 || height <= 0) {
        Log.e("ImageToBitmap", "Invalid dimensions for bitmap creation: $bitmapWidth x $height")
        return null
    }

    val bitmap = createBitmap(bitmapWidth, height)
    buffer.rewind()
    bitmap.copyPixelsFromBuffer(buffer)

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

fun Bitmap.rotate(degrees: Float): Bitmap {
    if (degrees == 0f) return this
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}