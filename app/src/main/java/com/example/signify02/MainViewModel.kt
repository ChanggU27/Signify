package com.example.signify02

import android.app.Application
import android.content.Context
import android.graphics.RectF
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

// ====================================================================================
// --- ViewModel (State Management & Logic) ---
// ====================================================================================
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // --- State Flows for Sign Recognition ---
    private val _predicteSign = MutableStateFlow("")
    val predictedSign: StateFlow<String> = _predicteSign
    private val _currentConfidence = MutableStateFlow(0f)
    val currentConfidence: StateFlow<Float> = _currentConfidence
    private val _signHistory = MutableStateFlow<List<String>>(emptyList())
    val signHistory: StateFlow<List<String>> = _signHistory

    // --- State Flows for Hand Detection ---
    private val _handLandmarkerResult = MutableStateFlow<HandLandmarkerResult?>(null)
    val handLandmarkerResult: StateFlow<HandLandmarkerResult?> = _handLandmarkerResult
    private val _handBoundingBoxes = MutableStateFlow<List<RectF>>(emptyList())
    val handBoundingBoxes: StateFlow<List<RectF>> = _handBoundingBoxes
    private val _showLandmarks = MutableStateFlow(false)
    val showLandmarks: StateFlow<Boolean> = _showLandmarks

    // --- Common State Flows ---
    private val _isTorchOn = MutableStateFlow(false) // Re-added
    val isTorchOn: StateFlow<Boolean> = _isTorchOn // Re-added
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage
    // Camera lens state
    private val _currentCameraLens = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val currentCameraLens: StateFlow<Int> = _currentCameraLens

    // --- Text-to-Speech State ---
    private val _isTextToSpeechEnabled = MutableStateFlow(false)
    val isTextToSpeechEnabled: StateFlow<Boolean> = _isTextToSpeechEnabled
    private var tts: TextToSpeech? = null


    // --- Camera, Executors, and Analyzers ---
    private var camera: Camera? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var signAnalyzer: SignDetectionAnalyzer? = null
    private var handAnalyzer: HandTrackingAnalyzer? = null

    init {
        // Initialize TextToSpeech
        tts = TextToSpeech(getApplication()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS Language not supported.")
                    _errorMessage.value = "Text-to-speech language not supported."
                }
            } else {
                Log.e(TAG, "TTS Initialization failed.")
                _errorMessage.value = "Text-to-speech initialization failed."
            }
        }
    }

    /**
     * Initializes the camera, binds use cases (Preview, TWO ImageAnalysis streams),
     * and starts the camera feed. Called from the UI layer.
     */
    fun setupCameraAndHandTracking(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        viewModelScope.launch {
            try {
                val cameraProvider: ProcessCameraProvider = ProcessCameraProvider.getInstance(getApplication()).await(context)

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = surfaceProvider
                }

                val cameraSelector = CameraSelector.Builder().requireLensFacing(_currentCameraLens.value).build()

                // --- Initialize Sign Detection Analyzer ---
                // Only re-initialize if null
                if (signAnalyzer == null) {
                    setupSignDetectionCallback()
                }

                // --- Initialize Hand Tracking Analyzer ---
                if (handAnalyzer == null) {
                    handAnalyzer = HandTrackingAnalyzer(getApplication()) { result ->
                        // Hand detection result callback
                        val boundingBoxes = calculateBoundingBoxes(result)
                        viewModelScope.launch(Dispatchers.Main) {
                            _handLandmarkerResult.value = result
                            _handBoundingBoxes.value = boundingBoxes
                        }
                    }
                }

                // --- Build Sign Detection ImageAnalysis Use Case ---
                val signImageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        signAnalyzer?.let { analyzer ->
                            it.setAnalyzer(cameraExecutor, analyzer)
                        } ?: Log.e(TAG, "SignAnalyzer was null during ImageAnalysis setup")
                    }

                // --- Build Hand Tracking ImageAnalysis Use Case ---
                val handImageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480 ))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        handAnalyzer?.let { analyzer ->
                            it.setAnalyzer(cameraExecutor, analyzer)
                        } ?: Log.e(TAG, "HandAnalyzer was null during ImageAnalysis setup")
                    }

                cameraProvider.unbindAll()

                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    signImageAnalysis,
                    handImageAnalysis
                )
                _errorMessage.value = null
                Log.d(TAG, "Camera setup successful with Preview, Sign Analysis, and Hand Analysis using lens: ${_currentCameraLens.value}")

            } catch (exc: Exception) {
                val errorMsg = "Camera setup failed: ${exc.localizedMessage}"
                Log.e(TAG, errorMsg, exc)
                _errorMessage.value = errorMsg
            }
        }
    }

    private fun setupSignDetectionCallback() {
        signAnalyzer = SignDetectionAnalyzer(
            context = getApplication(),
            handBoundingBoxesFlow = handBoundingBoxes,
            onSignDetected = { sign, confidence ->
                viewModelScope.launch(Dispatchers.Main) {
                    if (confidence >= CONFIDENCE_THRESHOLD) {
                        val _predicteSignValue = _predicteSign.value
                        val currentHistory = _signHistory.value

                        _predicteSign.value = sign
                        _currentConfidence.value = confidence

                        if (currentHistory.isEmpty() || currentHistory.last() != sign) {
                            if (sign != _predicteSignValue || currentHistory.isEmpty()) {
                                _signHistory.value = currentHistory + sign
                                if (_isTextToSpeechEnabled.value && tts != null && !sign.isBlank()) {
                                    tts?.speak(sign, TextToSpeech.QUEUE_FLUSH, null, null)
                                }
                            }
                        }
                    } else {
                        //Empty Logic, good as is, no need to add anything.
                    }
                }
            }
        )
    }

    fun toggleTorch() {
        val future = camera?.cameraControl?.enableTorch(!_isTorchOn.value)
        future?.addListener({
            viewModelScope.launch(Dispatchers.Main) {
                _isTorchOn.value = !_isTorchOn.value
            }
        }, ContextCompat.getMainExecutor(getApplication()))
            ?: Log.w(TAG, "Camera or CameraControl not available to toggle torch.")
    }

    fun toggleCamera() {
        viewModelScope.launch(Dispatchers.Main) {
            _currentCameraLens.value = if (_currentCameraLens.value == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.Main) {
            _predicteSign.value = ""
            _currentConfidence.value = 0f
            _signHistory.value = emptyList()
        }
    }

    fun toggleShowLandmarks() {
        _showLandmarks.value = !_showLandmarks.value
    }

    fun toggleTextToSpeech() {
        _isTextToSpeechEnabled.value = !_isTextToSpeechEnabled.value
        if (!_isTextToSpeechEnabled.value) {
            tts?.stop()
        }
    }

    fun onShowInfo() { // Renamed from onShowSignGallery

    }

    private fun calculateBoundingBoxes(result: HandLandmarkerResult?, padding: Float = 0.05f): List<RectF> {
        val boxList = mutableListOf<RectF>()

        if (result == null || result.landmarks().isNullOrEmpty()) {
            return boxList
        }

        result.landmarks().forEach { handLandmarks ->
            // Ensure the list of landmarks for a hand is not empty
            if (handLandmarks.isNullOrEmpty()) return@forEach

            var minX = 1.0f
            var minY = 1.0f
            var maxX = 0.0f
            var maxY = 0.0f

            handLandmarks.forEach { landmark: NormalizedLandmark ->
                minX = min(minX, landmark.x())
                minY = min(minY, landmark.y())
                maxX = max(maxX, landmark.x())
                maxY = max(maxY, landmark.y())
            }

            // Apply padding and clamp to [0, 1] range
            val paddedMinX = max(0f, minX - padding)
            val paddedMinY = max(0f, minY - padding)
            val paddedMaxX = min(1f, maxX + padding)
            val paddedMaxY = min(1f, maxY + padding)

            // Create RectF
            val rect = RectF(paddedMinX, paddedMinY, paddedMaxX, paddedMaxY)
            boxList.add(rect)
        }
        return boxList
    }

    /** Releases resources when the ViewModel is destroyed. */
    override fun onCleared() {
        super.onCleared()
        cameraExecutor.shutdown()
        signAnalyzer?.close()
        handAnalyzer?.close()
        tts?.stop()
        tts?.shutdown()
        Log.d(TAG, "ViewModel cleared, resources released.")
    }
}