package com.example.signify02

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.RectF
import android.speech.tts.TextToSpeech
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// PREFS constants
private const val PREFS_NAME = "SignifyPrefs"
private const val KEY_SHOW_INITIAL_INFO_DIALOG = "show_initial_info_dialog"
private const val KEY_TTS_LANGUAGE = "tts_language"
private const val KEY_TTS_COUNTRY = "tts_country"
private const val KEY_SHOW_LANDMARKS = "show_landmarks"
private const val KEY_AUTO_APPEND = "auto_append"

class MainViewModel(application: Application) : AndroidViewModel(application) {


    //core component and analyzers
    private var camera: Camera? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var handAnalyzer: HandTrackingAnalyzer? = null
    private var landmarkInterpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private var alphabet: List<String> = emptyList()
    private var tts: TextToSpeech? = null

    // auto appending of letter
    private var autoAppendJob: Job?= null
    private var lastAppendedSign: String?= null
    // track sign, how long its being held
    private var currentlyTimingSign: String? = null
    //track the space, how long its being held
    private var autoSpeakJob: Job? = null


    // --- Camera & AI State ---
    val hasCameraPermission = MutableStateFlow(checkCameraPermissionStatus(application))
    val handLandmarkerResult = MutableStateFlow<HandLandmarkerResult?>(null)
    val handBoundingBoxes = MutableStateFlow<List<RectF>>(emptyList())
    val showLandmarks = MutableStateFlow(getPreference(KEY_SHOW_LANDMARKS, true))
    val currentCameraLens = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val predictedSign = MutableStateFlow("")
    val currentConfidence = MutableStateFlow(0f)
    val errorMessage = MutableStateFlow<String?>(null)

    // --- UI Controls State ---
    val signHistory = MutableStateFlow<List<String>>(emptyList())
    val isTorchOn = MutableStateFlow(false)
    val isAutoAppendEnabled = MutableStateFlow(getPreference(KEY_AUTO_APPEND, true))

    // --- Navigation & Dialog State ---
    val showDisplaySample = MutableStateFlow(false)
    val showAboutScreen = MutableStateFlow(false)
    val showExitDialog = MutableStateFlow(false)
    val showPracticeMode = MutableStateFlow(false)
    val showInitialInfoDialog = MutableStateFlow(getShowInitialInfoDialogPreference())
    val showSettingsScreen = MutableStateFlow(false)

    // --- Practice Mode State ---
    val practiceState = MutableStateFlow(PracticeState.NOT_STARTED)
    val currentPracticeLetter = MutableStateFlow<String?>(null)
    val practiceScore = MutableStateFlow(0)
    val feedback = MutableStateFlow<Feedback?>(null)
    val showHintImage = MutableStateFlow(false)

    // --- TTS Locale ---
    private val _ttsLocale = MutableStateFlow(getTtsLanguagePreference())



    enum class PracticeState { NOT_STARTED, PRACTICING }
    data class Feedback(val isCorrect: Boolean, val timestamp: Long = System.currentTimeMillis())


    //initializationm
    init {
        _ttsLocale.value = getTtsLanguagePreference()
        initializeTextToSpeech(application)
        initializeLandmarkModel(application)
    }


    private fun initializeTextToSpeech(application: Application) {
        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // whatever was saved in the preference, load it at start
                tts?.language = _ttsLocale.value
            }
        }
    }

    private fun initializeLandmarkModel(application: Application) {
        try {
            val model = FileUtil.loadMappedFile(application, "asl_gesture_landmark.tflite")
            landmarkInterpreter = Interpreter(model, Interpreter.Options())
            labels = application.assets.open("asl_gesture_labels.txt").bufferedReader().readLines().filter { it.isNotBlank() }
            alphabet = labels
        } catch (_: Exception) {
            errorMessage.value = "AI model or labels failed to load."
        }
    }


    //public UI handlers
    fun onPermissionResult(isGranted: Boolean) {
        hasCameraPermission.value = isGranted
    }
    fun onDisplaySettings() {
        showSettingsScreen.value = true
    }
    fun onDismissSettings() {
        showSettingsScreen.value = false
    }

    fun toggleCamera() {
        currentCameraLens.value = if (currentCameraLens.value == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    fun toggleTorch() {
        camera?.cameraControl?.enableTorch(!isTorchOn.value)
        isTorchOn.value = !isTorchOn.value
    }

    //Settings functions
    fun toggleShowLandmarks(isEnabled: Boolean) {
        showLandmarks.value = isEnabled
        savePreference(KEY_SHOW_LANDMARKS, isEnabled)
    }

    fun toggleAutoAppend(isEnabled: Boolean) {
        isAutoAppendEnabled.value = isEnabled
        savePreference(KEY_AUTO_APPEND, isEnabled)
    }

    fun appendPredictedSignToHistory() {
        val signToAppend = predictedSign.value
        if (signToAppend.isNotEmpty()) {

            if (signToAppend.equals("space", ignoreCase = true)) {

                if (signHistory.value.isEmpty() || signHistory.value.last() != " ") {
                    signHistory.value = signHistory.value + " "
                    lastAppendedSign = " "
                }
            } else {

                signHistory.value = signHistory.value + signToAppend
                lastAppendedSign = signToAppend
            }
        }
    }

    fun clearHistory() {
        signHistory.value = emptyList()
        lastAppendedSign = null
    }

    fun speakSignHistory() {
        if (signHistory.value.isNotEmpty()) {
            val currentWord = signHistory.value.joinToString("")
            tts?.speak(currentWord, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    // handle language change
    fun setTtsLanguage(locale: Locale) {
        _ttsLocale.value = locale
        tts?.language = locale
        saveTtsLanguagePreference(locale)
    }


    fun onDisplaySample(){
        showDisplaySample.value = true
    }
    fun onDismissInfo() {
        showDisplaySample.value = false
    }
    fun onDisplayAbout() {
        showAboutScreen.value = true
    }
    fun onDismissAbout() {
        showAboutScreen.value = false
    }
    fun onDismissExitDialog(){
        showExitDialog.value = false
    }
    fun onDismissInitialInfoDialog(doNotShowAgain: Boolean) {
        showInitialInfoDialog.value = false
        if (doNotShowAgain) saveShowInitialInfoDialogPreference(false)
    }

    fun onBackPress() {
        when {
            showSettingsScreen.value -> onDismissSettings()
            showAboutScreen.value -> onDismissAbout()
            showDisplaySample.value -> onDismissInfo()
            showPracticeMode.value -> onDismissPracticeMode()
            practiceState.value == PracticeState.PRACTICING -> endPractice()
            else -> showExitDialog.value = true
        }
    }


    // Practice Mode Functions
    fun onStartPracticeMode() {
        showPracticeMode.value = true
        endPractice()
    }

    fun onDismissPracticeMode() {
        showPracticeMode.value = false
    }

    fun startFlashcardPractice() {
        practiceState.value = PracticeState.PRACTICING
        practiceScore.value = 0
        if (alphabet.isNotEmpty()) currentPracticeLetter.value = alphabet.random()
        showPracticeMode.value = false
    }

    fun endPractice() {
        practiceState.value = PracticeState.NOT_STARTED
        currentPracticeLetter.value = null
    }

    fun onHintRequested() {
        viewModelScope.launch {
            showHintImage.value = true
            delay(3000)
            showHintImage.value = false
        }
    }

    fun onPreviousLetter() {
        val currentIndex = alphabet.indexOf(currentPracticeLetter.value)
        if (currentIndex != -1) {
            val previousIndex = (currentIndex - 1 + alphabet.size) % alphabet.size
            currentPracticeLetter.value = alphabet[previousIndex]
        }
    }

    fun onNextLetter() {
        val currentIndex = alphabet.indexOf(currentPracticeLetter.value)
        if (currentIndex != -1) {
            val nextIndex = (currentIndex + 1) % alphabet.size
            currentPracticeLetter.value = alphabet[nextIndex]
        }
    }

    private fun triggerFeedback(isCorrect: Boolean) {
        viewModelScope.launch {
            feedback.value = Feedback(isCorrect)
            delay(2000)
            feedback.value = null
        }
    }


    // AI and Camera Logic
    fun setupCameraAndHandTracking(context: Context, lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        viewModelScope.launch {
            try {
                val cameraProvider = ProcessCameraProvider.getInstance(context).await(context)
                val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
                val cameraSelector = CameraSelector.Builder().requireLensFacing(currentCameraLens.value).build()

                handAnalyzer = HandTrackingAnalyzer(context) { result ->
                    handLandmarkerResult.value = result
                    handBoundingBoxes.value = calculateBoundingBoxes(result)
                    if (result.landmarks().isNotEmpty()) {
                        val normalizedLandmarks = normalizeLandmarks(result.landmarks().first())
                        runLandmarkInference(normalizedLandmarks)
                    } else {
                        predictedSign.value = ""
                        currentConfidence.value = 0f
                        autoAppendJob?.cancel()
                        currentlyTimingSign = null
                        lastAppendedSign = null
                    }
                }

                val handImageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { it.setAnalyzer(cameraExecutor, handAnalyzer!!) }

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, handImageAnalysis)
                errorMessage.value = null
            } catch (exc: Exception) {
                errorMessage.value = "Camera Setup Failed: ${exc.localizedMessage}"
            }
        }
    }

    private fun runLandmarkInference(normalizedLandmarks: FloatArray) {
        if (landmarkInterpreter == null || labels.isEmpty()) return

        val inputBuffer = ByteBuffer.allocateDirect(4 * 42).apply {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().put(normalizedLandmarks)
        }
        val outputProbabilities = Array(1) { FloatArray(labels.size) }
        landmarkInterpreter?.run(inputBuffer, outputProbabilities)

        val maxConfidence = outputProbabilities[0].maxOrNull() ?: 0f
        if (maxConfidence >= 0.6f) {
            val maxIndex = outputProbabilities[0].indexOfFirst { it == maxConfidence }
            val sign = labels.getOrElse(maxIndex) { "" }

            predictedSign.value = sign
            currentConfidence.value = maxConfidence

            if (!sign.equals("space", ignoreCase = true)) {
                autoSpeakJob?.cancel()
            }

            if (practiceState.value == PracticeState.PRACTICING) {
                if (sign.equals(currentPracticeLetter.value, ignoreCase = true)) {
                    practiceScore.value++
                    if (alphabet.isNotEmpty()) currentPracticeLetter.value = alphabet.random()
                    triggerFeedback(true)
                }
            } else {
                handleAutoAppend(sign)
            }
        } else {
            predictedSign.value = ""
            currentConfidence.value = 0f
            autoAppendJob?.cancel()
            currentlyTimingSign = null
            lastAppendedSign = null
        }
    }

    private fun handleAutoAppend(currentSign: String){
        //auto speak timer for space
        if (currentSign.equals("space", ignoreCase = true)) {
            autoAppendJob?.cancel()

            // only start a new timer if one isn't already running
            if (autoSpeakJob == null || autoSpeakJob?.isActive == false) {
                autoSpeakJob = viewModelScope.launch {
                    delay(1500)
                    speakSignHistory()
                    clearHistory()

                    //clear after
                    predictedSign.value = ""
                    currentConfidence.value = 0f
                }
            }
            return
        }

        if (!isAutoAppendEnabled.value) return
        if (currentSign == currentlyTimingSign || currentSign == lastAppendedSign) {
            return
        }

        autoAppendJob?.cancel()
        currentlyTimingSign = currentSign

        // regular letter, append after .5s of being held
        autoAppendJob = viewModelScope.launch{
            delay(500)
            if (predictedSign.value == currentSign){
                signHistory.value = signHistory.value + currentSign
                lastAppendedSign = currentSign
            }
            currentlyTimingSign = null
        }
    }


    // Private helpers
    private fun normalizeLandmarks(landmarks: List<NormalizedLandmark>): FloatArray {
        val normalizedCoords = FloatArray(42)
        if (landmarks.isEmpty()) return normalizedCoords

        val wrist = landmarks[0]
        val translatedLandmarks = landmarks.map { Pair(it.x() - wrist.x(), it.y() - wrist.y()) }

        var maxDistance = 0f
        for (coord in translatedLandmarks) {
            val distance = sqrt(coord.first * coord.first + coord.second * coord.second)
            if (distance > maxDistance) maxDistance = distance
        }

        if (maxDistance > 0) {
            translatedLandmarks.forEachIndexed { index, pair ->
                normalizedCoords[index * 2] = pair.first / maxDistance
                normalizedCoords[index * 2 + 1] = pair.second / maxDistance
            }
        }
        return normalizedCoords
    }

    private fun calculateBoundingBoxes(result: HandLandmarkerResult?): List<RectF> {
        val boxList = mutableListOf<RectF>()
        result?.landmarks()?.forEach { handLandmarks ->
            if (handLandmarks.isNotEmpty()) {
                var minX = 1f; var minY = 1f; var maxX = 0f; var maxY = 0f
                handLandmarks.forEach { landmark ->
                    minX = min(minX, landmark.x())
                    minY = min(minY, landmark.y())
                    maxX = max(maxX, landmark.x())
                    maxY = max(maxY, landmark.y())
                }
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

    private fun checkCameraPermissionStatus(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun savePreference(key: String, value: Boolean) {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(key, value) }
    }

    private fun getPreference(key: String, defaultValue: Boolean): Boolean {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(key, defaultValue)
    }



    private fun getShowInitialInfoDialogPreference(): Boolean {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_INITIAL_INFO_DIALOG, true)
    }

    private fun saveShowInitialInfoDialogPreference(show: Boolean) {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_SHOW_INITIAL_INFO_DIALOG, show) }
    }

    private fun saveTtsLanguagePreference(locale: Locale) {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_TTS_LANGUAGE, locale.language)
            putString(KEY_TTS_COUNTRY, locale.country)
        }
    }

    private fun getTtsLanguagePreference(): Locale {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val language = prefs.getString(KEY_TTS_LANGUAGE, Locale.US.language) ?: Locale.US.language
        val country = prefs.getString(KEY_TTS_COUNTRY, Locale.US.country) ?: Locale.US.country
        return Locale(language, country)
    }

    fun deleteLastSignFromHistory() {
        if (signHistory.value.isNotEmpty()) {
            signHistory.value = signHistory.value.dropLast(1)
            lastAppendedSign = signHistory.value.lastOrNull()
        }
    }


    //Life cycle
    override fun onCleared() {
        super.onCleared()
        cameraExecutor.shutdown()
        landmarkInterpreter?.close()
        handAnalyzer?.close()
        tts?.stop()
        tts?.shutdown()
    }

}