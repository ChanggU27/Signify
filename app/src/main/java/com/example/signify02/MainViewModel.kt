package com.example.signify02

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.RectF
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import android.speech.tts.TextToSpeech
import androidx.camera.core.Camera
import java.util.Locale
import kotlinx.coroutines.delay
import androidx.core.content.edit
import kotlin.text.compareTo


class MainViewModel(application: Application) : AndroidViewModel(application) {

    // --- Reference to camera ---
    private var camera: Camera? = null


    // ---StateFlow for CameraPermission ---
    private val _hasCameraPermission = MutableStateFlow(checkCameraPermissionStatus(application))
    val hasCameraPermission = _hasCameraPermission.asStateFlow()


    // --- State Flows for Hand Detection ---
    private val _handLandmarkerResult = MutableStateFlow<HandLandmarkerResult?>(null)
    val handLandmarkerResult = _handLandmarkerResult.asStateFlow()
    private val _handBoundingBoxes = MutableStateFlow<List<RectF>>(emptyList())
    val handBoundingBoxes = _handBoundingBoxes.asStateFlow()
    //Landmark button
    private val _showLandmarks = MutableStateFlow(true)
    val showLandmarks = _showLandmarks.asStateFlow()


    // --- State to track camera lens ---
    private val _currentCameraLens = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val currentCameraLens = _currentCameraLens.asStateFlow()


    // --- Camera, Executors, and Analyzers ---
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var handAnalyzer: HandTrackingAnalyzer? = null


    // --- State Flow for Sign Recognition
    private val _predictedSign = MutableStateFlow("")
    var predictedSign = _predictedSign.asStateFlow()
    private val _currentConfidence = MutableStateFlow(0f)
    val currentConfidence = _currentConfidence.asStateFlow()
    private var signAnalyzer: SignDetectionAnalyzer? = null
    // Sign History
    private val _signHistory = MutableStateFlow<List<String>>(emptyList())
    val signHistory = _signHistory.asStateFlow()


    // --- States for Text To Speech ---
    private val _isTextToSpeechEnabled = MutableStateFlow(false)
    var isTextToSpeechEnabled = _isTextToSpeechEnabled.asStateFlow()
    private var tts: TextToSpeech? = null


    // --- Common StateFlows ---
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    // Torch
    private val _isTorchOn = MutableStateFlow(false)
    var isTorchOn = _isTorchOn.asStateFlow()


    // --- Navigation State ---
    private val _showDisplayScreen = MutableStateFlow(false)
    val showDisplaySample = _showDisplayScreen.asStateFlow()
    private val _showAboutScreen = MutableStateFlow(false)
    val showAboutScreen = _showAboutScreen.asStateFlow()
    private val _showExitDialog = MutableStateFlow(false)
    val showExitDialog = _showExitDialog.asStateFlow()
    private val _showPracticeMode = MutableStateFlow(false)
    val showPracticeMode = _showPracticeMode.asStateFlow()


    // --- State Flows for Practice Mode ---
    private val _practiceState = MutableStateFlow(PracticeState.NOT_STARTED)
    val practiceState = _practiceState.asStateFlow()
    private val _currentPracticeLetter = MutableStateFlow<String?>(null)
    val currentPracticeLetter = _currentPracticeLetter.asStateFlow()
    private val _practiceScore = MutableStateFlow(0)
    val practiceScore = _practiceScore.asStateFlow()
    private val _feedback = MutableStateFlow<Feedback?>(null)
    val feedback = _feedback.asStateFlow()
    private val _showHintImage = MutableStateFlow(false)
    val showHintImage = _showHintImage.asStateFlow()
    private val alphabet = ('A'..'Z').toList()

    // --- Add these constants for SharedPreferences ---
    private val PREFS_NAME = "SignifyPrefs"
    private val KEY_SHOW_INITIAL_INFO_DIALOG = "show_initial_info_dialog"

    // --- Add state for the initial info dialog ---
    private val _showInitialInfoDialog = MutableStateFlow(getShowInitialInfoDialogPreference())
    val showInitialInfoDialog = _showInitialInfoDialog.asStateFlow()

    enum class PracticeState {
        NOT_STARTED,
        PRACTICING
    }

    data class Feedback(val isCorrect: Boolean, val timestamp: Long = System.currentTimeMillis())


    //Initialize TTS
    init {
        tts = TextToSpeech(application){ status ->
            if (status == TextToSpeech.SUCCESS){
                tts?.language = Locale.US
            }
        }
    }

    // Camera Permissions
    fun onPermissionResult(isGranted: Boolean) {
        _hasCameraPermission.value = isGranted
    }

    private fun checkCameraPermissionStatus(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun toggleCamera() {
        _currentCameraLens.value = if (_currentCameraLens.value == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    private fun setupSignDetectionCallback() {
        signAnalyzer = SignDetectionAnalyzer(
            context = getApplication(),
            handBoundingBoxesFlow = handBoundingBoxes,
            onSignDetected = { sign, confidence ->
                viewModelScope.launch(Dispatchers.Main) {
                    // If we are in practice mode, handle practice logic
                    if (_practiceState.value == PracticeState.PRACTICING) {
                        if (confidence >= CONFIDENCE_THRESHOLD && sign.equals(_currentPracticeLetter.value, ignoreCase = true)) {
                            _practiceScore.value++
                            _currentPracticeLetter.value = ('A'..'Z').random().toString()
                            triggerFeedback(isCorrect = true)
                        }
                        return@launch // dont update the main prediction while practicing
                    }

                    // Normal sign detection logic
                    if (confidence >= CONFIDENCE_THRESHOLD) {
                        _predictedSign.value = sign
                        _currentConfidence.value = confidence
                    } else {
                        _predictedSign.value = ""
                        _currentConfidence.value = 0f
                    }
                }
            }
        )
    }

    fun setupCameraAndHandTracking(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        viewModelScope.launch {
            try {
                if (signAnalyzer == null) { // this to prevent reinit
                    setupSignDetectionCallback()
                }
                val cameraProvider: ProcessCameraProvider = ProcessCameraProvider.getInstance(context).await(context)
                val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(_currentCameraLens.value)
                    .build()

                // --- Hand Analyzer Setup ---
                handAnalyzer = HandTrackingAnalyzer(context) { result ->
                    // This block runs every time a hand is detected
                    val boundingBoxes = calculateBoundingBoxes(result)
                    viewModelScope.launch(Dispatchers.Main) {
                        _handLandmarkerResult.value = result
                        _handBoundingBoxes.value = boundingBoxes
                    }
                }

                // ImageAnalysis use case for hand tracking
                val handImageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, handAnalyzer!!)
                    }


                // -- Image analysis for sign detection
                val signImageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, signAnalyzer!!)
                    }

                // Unbind all first
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    handImageAnalysis,
                    signImageAnalysis
                )
                _errorMessage.value = null
            } catch (exc: Exception) {
                _errorMessage.value = "Camera Setup Failed: ${exc.localizedMessage}"
            }
        }
    }

    // --- Practice Mode Functions ---
    fun onStartPracticeMode(){
        _showPracticeMode.value = true
        endPractice()
    }

    fun onDismissPracticeMode(){
        _showPracticeMode.value = false
    }

    fun startFlashcardPractice(){
        _practiceState.value = PracticeState.PRACTICING
        _practiceScore.value = 0
        _currentPracticeLetter.value = ('A'.. 'Z').random().toString()
        _showPracticeMode.value = false
    }

    fun endPractice(){
        _practiceState.value = PracticeState.NOT_STARTED
        _currentPracticeLetter.value = null
    }

    private fun triggerFeedback(isCorrect: Boolean){
        viewModelScope.launch{
            _feedback.value = Feedback(isCorrect)
            delay(3000)
            _feedback.value = null
        }
    }

    fun onHintRequested(){
        viewModelScope.launch{
            _showHintImage.value = true
            delay(3000)
            _showHintImage.value = false
        }
    }

    fun onPreviousLetter() {
        val currentLetterChar = _currentPracticeLetter.value?.singleOrNull() ?: return
        val currentIndex = alphabet.indexOf(currentLetterChar)
        if (currentIndex != -1) {
            val previousIndex = (currentIndex - 1 + alphabet.size) % alphabet.size
            _currentPracticeLetter.value = alphabet[previousIndex].toString()
        }
    }

    fun onNextLetter() {
        val currentLetterChar = _currentPracticeLetter.value?.singleOrNull() ?: return
        val currentIndex = alphabet.indexOf(currentLetterChar)
        if (currentIndex != -1) {
            val nextIndex = (currentIndex + 1) % alphabet.size
            _currentPracticeLetter.value = alphabet[nextIndex].toString()
        }
    }


    // --- Recognized Sign Box functions ---
    fun toggleTorch() {
        camera?.cameraControl?.enableTorch(!_isTorchOn.value)
        _isTorchOn.value = !_isTorchOn.value
    }

    fun toggleShowLandmarks() {
        _showLandmarks.value = !_showLandmarks.value
    }

    fun clearHistory() {
        _signHistory.value = emptyList()
    }

    fun toggleTextToSpeech() {
        _isTextToSpeechEnabled.value = !_isTextToSpeechEnabled.value
        if (!_isTextToSpeechEnabled.value) {
            tts?.stop()
        }
    }

    fun appendPredictedSignToHistory() {
        val signToAppend = _predictedSign.value
        if (signToAppend.isNotEmpty()) {
            _signHistory.value = _signHistory.value + signToAppend
            if (_isTextToSpeechEnabled.value) {
                tts?.speak(signToAppend, TextToSpeech.QUEUE_ADD, null, null)
            }
        }
    }


    // --- Kebab Menu Functions ---
    fun onDisplaySample(){
        _showDisplayScreen.value = true
    }

    fun onDismissInfo(){
        _showDisplayScreen.value = false
    }

    fun onDisplayAbout(){
        _showAboutScreen.value = true
    }

    fun onDismissAbout(){
        _showAboutScreen.value = false
    }

    fun onBackPress(){
        when{
            _showAboutScreen.value -> onDismissAbout()
            _showDisplayScreen.value -> onDismissInfo()
            _showPracticeMode.value -> onDismissPracticeMode()
            _practiceState.value == PracticeState.PRACTICING -> endPractice()
            else -> _showExitDialog.value = true
        }
    }

    // --- Dialogs ---
    fun onDismissInitialInfoDialog(doNotShowAgain: Boolean){
        _showInitialInfoDialog.value = false
        if (doNotShowAgain){
            saveShowInitialInfoDialogPreference(false)
        }
    }

    private fun getShowInitialInfoDialogPreference(): Boolean{
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_INITIAL_INFO_DIALOG, true)
    }

    private fun saveShowInitialInfoDialogPreference(show: Boolean){
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_SHOW_INITIAL_INFO_DIALOG, show)}
    }


    fun onDismissExitDialog(){
        _showExitDialog.value = false
    }

    private fun calculateBoundingBoxes(result: HandLandmarkerResult?): List<RectF> {
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
                // Add some padding to the box
                val padding = 0.1f
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