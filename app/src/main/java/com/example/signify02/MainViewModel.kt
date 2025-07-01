package com.example.signify02

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
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
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import androidx.core.content.edit


enum class PracticeState {
    NOT_STARTED,
    PRACTICING
}

// Data class to hold the immediate feedback state
data class Feedback(val isCorrect: Boolean, val timestamp: Long = System.currentTimeMillis())

class MainViewModel(application: Application) : AndroidViewModel(application) {
    @SuppressLint("StaticFieldLeak")
    private val context: Context = application.applicationContext

    private val PREFS_NAME = "SignifyPrefs"
    private val KEY_SHOW_INITIAL_INFO_DIALOG = "show_initial_info_dialog"

    // --- State for camera permission ---
    private val _hasCameraPermission = MutableStateFlow(checkCameraPermissionStatus(context))
    val hasCameraPermission: StateFlow<Boolean> = _hasCameraPermission.asStateFlow()

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
    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage
    private val _currentCameraLens = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val currentCameraLens: StateFlow<Int> = _currentCameraLens

    // --- Text-to-Speech State ---
    private val _isTextToSpeechEnabled = MutableStateFlow(false)
    val isTextToSpeechEnabled: StateFlow<Boolean> = _isTextToSpeechEnabled
    private var tts: TextToSpeech? = null

    // --- Navigation and Dialog State ---
    private val _showDisplayScreen = MutableStateFlow(false)
    val showDisplaySample = _showDisplayScreen.asStateFlow()
    private val _showPracticeMode = MutableStateFlow(false)
    val showPracticeMode = _showPracticeMode.asStateFlow()
    private val _showExitDialog = MutableStateFlow(false)
    val showExitDialog = _showExitDialog.asStateFlow()

    // Initial info dialog state, loaded from preferences
    private val _showInitialInfoDialog = MutableStateFlow(getShowInitialInfoDialogPreference())
    val showInitialInfoDialog = _showInitialInfoDialog.asStateFlow()

    // --- About Screen ---
    private val _showAboutScreen = MutableStateFlow(false)
    val showAboutScreen = _showAboutScreen.asStateFlow()

    // --- State Flows for Practice Mode ---
    private val _practiceState = MutableStateFlow(PracticeState.NOT_STARTED)
    val practiceState: StateFlow<PracticeState> = _practiceState.asStateFlow()
    private val _currentPracticeLetter = MutableStateFlow<String?>(null)
    val currentPracticeLetter: StateFlow<String?> = _currentPracticeLetter.asStateFlow()
    private val _practiceScore = MutableStateFlow(0)
    val practiceScore: StateFlow<Int> = _practiceScore.asStateFlow()
    private val _feedback = MutableStateFlow<Feedback?>(null)
    val feedback: StateFlow<Feedback?> = _feedback.asStateFlow()
    private val _showHintImage = MutableStateFlow(false)
    val showHintImage: StateFlow<Boolean> = _showHintImage.asStateFlow()

    private val alphabet = ('A'..'Z').toList()

    // --- Camera, Executors, and Analyzers ---
    private var camera: Camera? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var signAnalyzer: SignDetectionAnalyzer? = null
    private var handAnalyzer: HandTrackingAnalyzer? = null

    init {
        tts = TextToSpeech(getApplication()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
    }

    fun updateCameraPermissionStatus(isGranted: Boolean) {
        _hasCameraPermission.value = isGranted
    }

    fun onDismissInitialInfoDialog(doNotShowAgain: Boolean) {
        _showInitialInfoDialog.value = false
        if (doNotShowAgain) {
            saveShowInitialInfoDialogPreference(false)
        }
    }

    private fun getShowInitialInfoDialogPreference(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_INITIAL_INFO_DIALOG, true)
    }

    private fun saveShowInitialInfoDialogPreference(show: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_SHOW_INITIAL_INFO_DIALOG, show) }
    }

    private fun checkCameraPermissionStatus(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

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

                if (signAnalyzer == null) {
                    setupSignDetectionCallback()
                }

                if (handAnalyzer == null) {
                    handAnalyzer = HandTrackingAnalyzer(getApplication()) { result ->
                        val boundingBoxes = calculateBoundingBoxes(result)
                        viewModelScope.launch(Dispatchers.Main) {
                            _handLandmarkerResult.value = result
                            _handBoundingBoxes.value = boundingBoxes
                        }
                    }
                }

                val signImageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        signAnalyzer?.let { analyzer -> it.setAnalyzer(cameraExecutor, analyzer) }
                    }

                val handImageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        handAnalyzer?.let { analyzer -> it.setAnalyzer(cameraExecutor, analyzer) }
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
                    if (_practiceState.value == PracticeState.PRACTICING) {
                        if (confidence >= CONFIDENCE_THRESHOLD && sign.equals(_currentPracticeLetter.value, ignoreCase = true)) {
                            // Correct sign
                            _practiceScore.value++
                            _currentPracticeLetter.value = ('A'..'Z').random().toString()
                            triggerFeedback(isCorrect = true)
                        }

                        return@launch
                    }

                    // Sign recognition logic
                    if (confidence >= CONFIDENCE_THRESHOLD) {
                        val currentHistory = _signHistory.value
                        if (currentHistory.isEmpty() || currentHistory.last() != sign) {
                            _predicteSign.value = sign
                            _currentConfidence.value = confidence
                            _signHistory.value = currentHistory + sign
                            if (_isTextToSpeechEnabled.value && tts != null && !sign.isBlank()) {
                                tts?.speak(sign, TextToSpeech.QUEUE_ADD, null, null)
                            }
                        }
                    }
                }
            }
        )
    }

    private fun triggerFeedback(isCorrect: Boolean) {
        viewModelScope.launch {
            _feedback.value = Feedback(isCorrect)
            delay(2000)
            _feedback.value = null
        }
    }


    fun toggleTorch() {
        camera?.cameraControl?.enableTorch(!_isTorchOn.value)
        _isTorchOn.value = !_isTorchOn.value
    }

    fun toggleCamera() {
        viewModelScope.launch {
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

    fun startFlashcardPractice() {
        _practiceState.value = PracticeState.PRACTICING
        _practiceScore.value = 0
        _currentPracticeLetter.value = ('A'..'Z').random().toString()
        _showPracticeMode.value = false
    }

    fun endPractice() {
        _practiceState.value = PracticeState.NOT_STARTED
        _currentPracticeLetter.value = null
    }

    fun onDisplaySample() {
        _showDisplayScreen.value = true
    }

    fun onDismissInfo() {
        _showDisplayScreen.value = false
    }

    fun onStartPracticeMode() {
        _showPracticeMode.value = true
        endPractice()
    }

    fun onDismissPracticeMode() {
        _showPracticeMode.value = false
    }


    fun onBackPress() {
        when {
            _showAboutScreen.value -> onDismissAbout()
            _showDisplayScreen.value -> onDismissInfo()
            _showPracticeMode.value -> onDismissPracticeMode()
            _practiceState.value == PracticeState.PRACTICING -> endPractice()
            else -> _showExitDialog.value = true
        }
    }

    fun onDismissExitDialog() {
        _showExitDialog.value = false
    }

    fun onDisplayAbout() {
        _showAboutScreen.value = true
    }

    fun onDismissAbout() {
        _showAboutScreen.value = false
    }

    fun onHintRequested() {
        viewModelScope.launch {
            _showHintImage.value = true
            delay(3000)
            _showHintImage.value = false
        }
    }

    fun onPreviousLetter() {
        viewModelScope.launch(Dispatchers.Default) {
            val currentLetterChar = _currentPracticeLetter.value?.singleOrNull()
            if (currentLetterChar != null) {
                val currentIndex = alphabet.indexOf(currentLetterChar)
                if (currentIndex != -1) {
                    // Calculate previous index, wrapping around from 'A' to 'Z'
                    val previousIndex = (currentIndex - 1 + alphabet.size) % alphabet.size
                    _currentPracticeLetter.value = alphabet[previousIndex].toString()
                    _feedback.value = null
                    _showHintImage.value = false
                }
            }
        }
    }

    fun onNextLetter() {
        viewModelScope.launch(Dispatchers.Default) {
            val currentLetterChar = _currentPracticeLetter.value?.singleOrNull()
            if (currentLetterChar != null) {
                val currentIndex = alphabet.indexOf(currentLetterChar)
                if (currentIndex != -1) {
                    // Calculate next index, wrapping around from 'Z' to 'A'
                    val nextIndex = (currentIndex + 1) % alphabet.size
                    _currentPracticeLetter.value = alphabet[nextIndex].toString()
                    _feedback.value = null
                    _showHintImage.value = false
                }
            }
        }
    }

    private fun calculateBoundingBoxes(result: HandLandmarkerResult?, padding: Float = 0.14f): List<RectF> {
        val boxList = mutableListOf<RectF>()
        result?.landmarks()?.forEach { handLandmarks ->
            if (handLandmarks.isNotEmpty()) {
                var minX = 1.0f
                var minY = 1.0f
                var maxX = 0.0f
                var maxY = 0.0f
                handLandmarks.forEach { landmark ->
                    minX = min(minX, landmark.x())
                    minY = min(minY, landmark.y())
                    maxX = max(maxX, landmark.x())
                    maxY = max(maxY, landmark.y())
                }
                val rect = RectF(
                    max(0f, minX - padding),
                    max(0f, minY - padding),
                    min(1f, maxX + padding),
                    min(1f, maxY + padding)
                )
                boxList.add(rect)
            }
        }
        return boxList
    }

    override fun onCleared() {
        super.onCleared()
        cameraExecutor.shutdown()
        signAnalyzer?.close()
        handAnalyzer?.close()
        tts?.stop()
        tts?.shutdown()
    }
}