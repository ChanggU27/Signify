package com.example.signify02

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions as MpBaseOptions // Alias BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.flow.StateFlow
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

// ====================================================================================
// --- Sign Detection Analyzer (TFLite) ---
// ====================================================================================
/**
 * Performs sign language detection using a TFLite model.
 * Crops the input based on hand bounding boxes received from another analyzer.
 */
class SignDetectionAnalyzer(
    context: Context,
    private val handBoundingBoxesFlow: StateFlow<List<RectF>>,
    private val onSignDetected: (String, Float) -> Unit
) : ImageAnalysis.Analyzer {

    private var tflite: Interpreter? = null
    private var inputImageBuffer: TensorImage? = null
    private var outputProbabilityBuffer: TensorBuffer? = null
    private var probabilityProcessor: TensorProcessor? = null
    private var imageProcessor: ImageProcessor? = null
    private var labels: List<String> = emptyList()
    private var isInitialized = false
    private val appContext = context.applicationContext

    init {
        try {
            initializeTFLiteInterpreter()
            isInitialized = true
            Log.d(TAG, "SignDetectionAnalyzer initialized.")
        } catch (e: Exception) {
            Log.e(TAG, "SignDetectionAnalyzer init failed", e)
        }
    }

    /** Loads the TFLite model, labels, and configures TF Lite Support helpers. */
    private fun initializeTFLiteInterpreter() {
        try {
            val tfliteModel = FileUtil.loadMappedFile(appContext, SIGN_MODEL_FILE)
            val options = Interpreter.Options()
            tflite = Interpreter(tfliteModel, options)

            labels = appContext.assets.open(LABELS_FILE)
                .bufferedReader()
                .useLines { lines -> lines.filter { it.isNotBlank() }.toList() }

            // Configure input buffer based on model signature
            val inputTensor = tflite!!.getInputTensor(0)
            val inputShape = inputTensor.shape()
            val inputHeight = inputShape[1]
            val inputWidth = inputShape[2]
            inputImageBuffer = TensorImage(inputTensor.dataType())

            // Define preprocessing steps (must match model training)
            imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(inputHeight, inputWidth, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0f, 255f))
                .build()

            // Configure output buffer
            val outputTensor = tflite!!.getOutputTensor(0)
            outputProbabilityBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), outputTensor.dataType())

            probabilityProcessor = TensorProcessor.Builder().build()

            Log.i(TAG, "Sign TFLite interpreter initialized. Input: ${inputShape.contentToString()}, Output: ${outputTensor.shape().contentToString()}")

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Sign TFLite interpreter", e)
            close()
            throw e
        }
    }

    @SuppressLint("UnsafeOptInUsageError") // For imageProxy.image access
    override fun analyze(imageProxy: ImageProxy) {
        if (!isInitialized || tflite == null) {
            imageProxy.close(); return
        }

        val firstHandBox = handBoundingBoxesFlow.value.firstOrNull()

        if (firstHandBox == null) {
            onSignDetected("", 0f)
            imageProxy.close(); return
        }

        var bitmapToProcess: Bitmap? = null
        var fullBitmap: Bitmap? = null

        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            fullBitmap = imageProxy.image?.toBitmap()?.rotate(rotationDegrees.toFloat())

            if (fullBitmap != null) {
                // Crop the image to the detected hand region before analysis
                val croppedBitmap = cropBitmapWithBoundingBox(fullBitmap, firstHandBox)
                if (croppedBitmap != null) {
                    bitmapToProcess = croppedBitmap // Use the cropped version
                } else {
                    Log.w(TAG, "Sign Analyzer: Cropping failed.")
                    onSignDetected("", 0f); imageProxy.close(); return // Skip if crop fails
                }
            } else {
                Log.w(TAG, "Sign Analyzer: Failed to get bitmap.")
                onSignDetected("", 0f); imageProxy.close(); return // Skip if bitmap fails
            }

            // --- Run inference on the cropped image ---
            if (bitmapToProcess != null) {
                inputImageBuffer?.load(bitmapToProcess)
                val processedImage = imageProcessor!!.process(inputImageBuffer)
                tflite?.run(processedImage.buffer, outputProbabilityBuffer!!.buffer.rewind())

                // --- Post-process output ---
                val probabilities = probabilityProcessor?.process(outputProbabilityBuffer) ?: outputProbabilityBuffer
                val probabilityArray = probabilities!!.floatArray
                val maxProbabilityIndex = probabilityArray.indices.maxByOrNull { probabilityArray[it] } ?: -1

                // --- Report result ---
                if (maxProbabilityIndex != -1 && maxProbabilityIndex < labels.size) {
                    val confidence = probabilityArray[maxProbabilityIndex]
                    val detectedSign = labels[maxProbabilityIndex]
                    onSignDetected(detectedSign, confidence)
                } else {
                    onSignDetected("", 0f)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during sign detection analysis", e)
            onSignDetected("", 0f)
        } finally {
            imageProxy.close()
        }
    }

    /** Closes the TFLite interpreter to release native resources. */
    fun close() {
        tflite?.close()
        tflite = null
        isInitialized = false
        Log.d(TAG, "SignDetectionAnalyzer closed.")
    }
}


// ====================================================================================
// --- Hand Tracking Analyzer (MediaPipe) ---
// ====================================================================================

/**
 * Performs hand landmark detection using the MediaPipe HandLandmarker Task.
 * Runs in LIVE_STREAM mode, providing results asynchronously via a listener.
 */
class HandTrackingAnalyzer(
    private val context: Context,
    private val listener: (HandLandmarkerResult) -> Unit
) : ImageAnalysis.Analyzer {

    private var handLandmarker: HandLandmarker? = null
    private var isInitialized = false

    init {
        try {
            setupHandLandmarker()
            isInitialized = true
            Log.d(TAG, "HandTrackingAnalyzer initialized.")
        } catch (e: Exception) {
            Log.e(TAG, "HandTrackingAnalyzer init failed", e)
        }
    }

    /** Configures and creates the MediaPipe HandLandmarker instance. */
    private fun setupHandLandmarker() {
        try {
            // Basic options point to the model file
            val baseOptionsBuilder = MpBaseOptions.builder()
                .setModelAssetPath(HAND_LANDMARKER_MODEL_FILE)

            // Specific options for HandLandmarker
            val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(RunningMode.LIVE_STREAM) // Essential for processing camera frames
                .setNumHands(1) // Max hands to detect
                .setMinHandDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setResultListener { result: HandLandmarkerResult, _: MPImage -> // Called with results
                    listener(result) // Pass results back to ViewModel
                }
                .setErrorListener { error: RuntimeException -> // Called on internal errors
                    Log.e(TAG, "MediaPipe HandLandmarker Error: ${error.message}")
                }

            handLandmarker = HandLandmarker.createFromOptions(context, optionsBuilder.build())
            Log.i(TAG, "Hand Landmarker initialized.")

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Hand Landmarker", e)
            close()
            throw e
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (!isInitialized || handLandmarker == null) {
            imageProxy.close(); return
        }

        val frameTimestamp = imageProxy.imageInfo.timestamp
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        // Convert frame to Bitmap
        val bitmap = imageProxy.image?.toBitmap()?.rotate(rotationDegrees.toFloat())

        if (bitmap == null) {
            Log.w(TAG, "Hand Analyzer: Bitmap conversion failed.")
            imageProxy.close(); return
        }

        try {
            // Convert Bitmap to MediaPipe's MPImage format
            val mpImage = BitmapImageBuilder(bitmap).build()

            // Run detection asynchronously. Results arrive at the ResultListener.
            handLandmarker?.detectAsync(mpImage, frameTimestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Error during hand detection analysis", e)
        } finally {
            imageProxy.close()
        }
    }

    /** Closes the HandLandmarker instance to release native resources. */
    fun close() {
        handLandmarker?.close()
        handLandmarker = null
        isInitialized = false
        Log.d(TAG, "HandTrackingAnalyzer closed.")
    }
}
