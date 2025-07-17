// Signify/app/src/main/java/com/example/signify02/MainActivity.kt

package com.example.signify02

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    // ActivityResultLauncher for CAMERA permission
    private var cameraPermissionResultCallback: ((Boolean) -> Unit)? = null
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            cameraPermissionResultCallback?.invoke(isGranted)
            cameraPermissionResultCallback = null
        }

    // ActivityResultLauncher for POST_NOTIFICATIONS permission
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                scheduleAslReminder()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        checkAndRequestNotificationPermission()

        setContent {
            val systemUiController = rememberSystemUiController()
            val useDarkIcons = MaterialTheme.colorScheme.surface.luminance() > 0.5f

            SideEffect {
                systemUiController.setStatusBarColor(color = Color.Transparent, darkIcons = useDarkIcons)
                systemUiController.setNavigationBarColor(color = Color.Transparent, darkIcons = useDarkIcons)
            }

            Signify02Theme(dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BackHandler(enabled = true) {
                        viewModel.onBackPress()
                    }

                    // --- STATE COLLECTION ---
                    val showExitDialog by viewModel.showExitDialog.collectAsState()
                    val showInitialInfoDialog by viewModel.showInitialInfoDialog.collectAsState()
                    val hasCameraPermission by viewModel.hasCameraPermission.collectAsState()
                    val displaySample by viewModel.showDisplaySample.collectAsState()
                    val showPracticeMode by viewModel.showPracticeMode.collectAsState()
                    val showAboutScreen by viewModel.showAboutScreen.collectAsState()

                    // --- DIALOG LOGIC ---
                    if (showInitialInfoDialog && hasCameraPermission) {
                        InitialInfoDialog(onDismiss = viewModel::onDismissInitialInfoDialog)
                    }

                    if (showExitDialog) {
                        ExitConfirmationDialog(
                            onConfirm = {
                                viewModel.onDismissExitDialog()
                                finish()
                            },
                            onDismiss = viewModel::onDismissExitDialog
                        )
                    }

                    // --- SCREEN NAVIGATION LOGIC ---
                    when {
                        showAboutScreen -> {
                            AboutScreen(onDismiss = viewModel::onDismissAbout)
                        }
                        displaySample -> {
                            DisplaySampleScreen(onDismiss = viewModel::onDismissInfo)
                        }
                        showPracticeMode -> {
                            LearningHubScreen(
                                onDismiss = viewModel::onDismissPracticeMode,
                                onStartFlashcards = viewModel::startFlashcardPractice
                            )
                        }
                        else -> {
                            val predictedSign by viewModel.predictedSign.collectAsState()
                            val currentConfidence by viewModel.currentConfidence.collectAsState()
                            val signHistory by viewModel.signHistory.collectAsState()
                            val errorMessage by viewModel.errorMessage.collectAsState()
                            val handBoundingBoxes by viewModel.handBoundingBoxes.collectAsState()
                            val showLandmarks by viewModel.showLandmarks.collectAsState()
                            val landmarkResult by viewModel.handLandmarkerResult.collectAsState()
                            val isTextToSpeechEnabled by viewModel.isTextToSpeechEnabled.collectAsState()
                            val currentCameraLens by viewModel.currentCameraLens.collectAsState()
                            val isTorchOn by viewModel.isTorchOn.collectAsState()
                            val practiceState by viewModel.practiceState.collectAsState()
                            val currentPracticeLetter by viewModel.currentPracticeLetter.collectAsState()
                            val practiceScore by viewModel.practiceScore.collectAsState()
                            val feedback by viewModel.feedback.collectAsState()
                            val showHintImage by viewModel.showHintImage.collectAsState()

                            Box(modifier = Modifier.fillMaxSize()) {
                                SignifyCameraScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    hasCameraPermission = hasCameraPermission,
                                    predictedSign = predictedSign,
                                    currentConfidence = currentConfidence,
                                    signHistory = signHistory,
                                    handBoundingBoxes = handBoundingBoxes,
                                    showLandmarks = showLandmarks,
                                    landmarkResult = landmarkResult,
                                    currentCameraLens = currentCameraLens,
                                    isTorchOn = isTorchOn,
                                    errorMessage = errorMessage,
                                    onClearHistory = viewModel::clearHistory,
                                    onToggleShowLandmarks = viewModel::toggleShowLandmarks,
                                    isTextToSpeechEnabled = isTextToSpeechEnabled,
                                    onToggleTextToSpeech = viewModel::toggleTextToSpeech,
                                    onToggleCamera = viewModel::toggleCamera,
                                    onToggleTorch = viewModel::toggleTorch,
                                    onDisplaySample = viewModel::onDisplaySample,
                                    onStartPracticeMode = viewModel::onStartPracticeMode,
                                    onDisplayAbout = viewModel::onDisplayAbout,
                                    requestCameraPermission = { onPermissionResult ->
                                        cameraPermissionResultCallback = { isGranted ->
                                            viewModel.updateCameraPermissionStatus(isGranted)
                                            onPermissionResult(isGranted)
                                        }
                                        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    },
                                    setupCamera = viewModel::setupCameraAndHandTracking,
                                    practiceState = practiceState
                                )

                                if (practiceState == PracticeState.PRACTICING) {
                                    PracticeHUD(
                                        targetLetter = currentPracticeLetter,
                                        currentScore = practiceScore,
                                        feedback = feedback,
                                        onExit = viewModel::endPractice,
                                        showHint = showHintImage,
                                        onHintRequested = viewModel::onHintRequested,
                                        onPreviousLetter = viewModel::onPreviousLetter,
                                        onNextLetter = viewModel::onNextLetter
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    scheduleAslReminder()
                }
                else -> {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            scheduleAslReminder()
        }
    }

    private fun scheduleAslReminder() {
        val workTag = "asl_reminder_work"

        val repeatingRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
            7,
            TimeUnit.HOURS
        )
            .setInitialDelay(2, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            workTag,
            ExistingPeriodicWorkPolicy.KEEP,
            repeatingRequest
        )
    }
}