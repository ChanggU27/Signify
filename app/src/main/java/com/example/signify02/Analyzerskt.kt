package com.example.signify02

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
//Image analysis
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder

//Mediapipe handlandmarker
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandTrackingAnalyzer(
    private val context: Context,
    private val listener: (HandLandmarkerResult) -> Unit
) : ImageAnalysis.Analyzer {

    private var handLandmarker: HandLandmarker? = null

    //Initialize HandLandmarker
    init {
        setupHandLandmarker()
    }

    // Setup & Configuration
    private fun setupHandLandmarker() {
        try {
            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath(HAND_LANDMARKER_MODEL_FILE)

            val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setResultListener { result, _ ->
                    listener(result)
                }
                .setErrorListener { error ->
                    Log.e(TAG, "MediaPipe HandLandmarker Error: ${error.message}")
                }

            handLandmarker = HandLandmarker.createFromOptions(context, optionsBuilder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Hand Landmarker", e)
        }
    }

    // Image analysis for hand
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val frameTimestamp = imageProxy.imageInfo.timestamp
        // Get the rotation degrees from the imageProxy
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        // Convert the image to a bitmap, then rotate it
        val bitmap = imageProxy.image?.toBitmap()?.rotate(rotationDegrees.toFloat())

        if (bitmap != null) {
            val mpImage = BitmapImageBuilder(bitmap).build()
            handLandmarker?.detectAsync(mpImage, frameTimestamp)
        }

        imageProxy.close()
    }

    //Resource cleanup
    fun close() {
        handLandmarker?.close()
        handLandmarker = null
    }
}