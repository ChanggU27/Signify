package com.example.signify02

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
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

//TensorflowLite
import kotlinx.coroutines.flow.StateFlow
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

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

    private fun initializeTFLiteInterpreter() {
        try {
            // Load the TFLite file from assets
            val tfliteModel = FileUtil.loadMappedFile(appContext, SIGN_MODEL_FILE)
            val options = Interpreter.Options()
            tflite = Interpreter(tfliteModel, options)

            // Load Labels.txt from assets
            labels = appContext.assets.open(LABELS_FILE)
                .bufferedReader()
                .useLines { lines -> lines.filter { it.isNotBlank() }.toList() }

            //Setup Configuration based on TFLite model
            val inputTensor = tflite!!.getInputTensor(0)
            val inputShape = inputTensor.shape()
            val inputHeight = inputShape[1]
            val inputWidth = inputShape[2]
            inputImageBuffer = TensorImage(inputTensor.dataType())

            // Resize and normalize
            imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(inputHeight, inputWidth, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0f, 255f))
                .build()

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

    // Image analysis for sign
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (!isInitialized || tflite == null) {
            imageProxy.close(); return
        }

        val firstHandBox = handBoundingBoxesFlow.value.firstOrNull()

        if (firstHandBox == null) {
            onSignDetected("", 0f)
            imageProxy.close(); return
        }

        var bitmapToProcess: Bitmap?
        var fullBitmap: Bitmap?


        try {
            // Convert image to bitmap and rotate it
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            fullBitmap = imageProxy.image?.toBitmap()?.rotate(rotationDegrees.toFloat())

            //extract only what is inside the bounding boxes
            if (fullBitmap != null) {
                val croppedBitmap = cropBitmapWithBoundingBox(fullBitmap, firstHandBox)
                if (croppedBitmap != null) {
                    bitmapToProcess = croppedBitmap
                } else {
                    onSignDetected("", 0f); imageProxy.close(); return
                }
            } else {
                onSignDetected("", 0f); imageProxy.close(); return
            }

            //loaed cropped bitmap to input buffer
            inputImageBuffer?.load(bitmapToProcess)
            val processedImage = imageProcessor!!.process(inputImageBuffer)

            //Model inference
            tflite?.run(processedImage.buffer, outputProbabilityBuffer!!.buffer.rewind())

            //Result processing
            val probabilities = probabilityProcessor?.process(outputProbabilityBuffer) ?: outputProbabilityBuffer
            val probabilityArray = probabilities!!.floatArray
            val maxProbabilityIndex = probabilityArray.indices.maxByOrNull { probabilityArray[it] } ?: -1

            // find index with maximum probability from labels
            if (maxProbabilityIndex != -1 && maxProbabilityIndex < labels.size) {
                val confidence = probabilityArray[maxProbabilityIndex]
                val detectedSign = labels[maxProbabilityIndex]
                // send result back to mainviewmodel
                onSignDetected(detectedSign, confidence)
            } else {
                onSignDetected("", 0f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during sign detection analysis", e)
            onSignDetected("", 0f)
        } finally {
            imageProxy.close()
        }
    }

    // Resource cleanup
    fun close() {
        tflite?.close()
        tflite = null
        isInitialized = false
        Log.d(TAG, "SignDetectionAnalyzer closed.")
    }
}
