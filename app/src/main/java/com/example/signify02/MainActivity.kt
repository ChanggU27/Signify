package com.example.signify02

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.signify02.ui.SignifyTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    //Ask for camera permission
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            viewModel.onPermissionResult(isGranted)
        }


    // Launcher for photos and video permission
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestMediaPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->

        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        installSplashScreen()

        requestMediaPermissions()

        scheduleNotificationWorker()

        setContent {
            SignifyTheme(dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    //Navigation States
                    val showDisplaySample by viewModel.showDisplaySample.collectAsState()
                    val showAboutScreen by viewModel.showAboutScreen.collectAsState()
                    val showPracticeMode by viewModel.showPracticeMode.collectAsState()

                    // dialog states
                    val showExitDialog by viewModel.showExitDialog.collectAsState()
                    val showInitialInfoDialog by viewModel.showInitialInfoDialog.collectAsState()
                    val hasCameraPermission by viewModel.hasCameraPermission.collectAsState()

                    //Settings states
                    val showSettingsScreen by viewModel.showSettingsScreen.collectAsState()
                    val isAutoAppendEnabled by viewModel.isAutoAppendEnabled.collectAsState()
                    val showLandmarks by viewModel.showLandmarks.collectAsState()


                    // This logic will display the dialogs as overlays
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

                    BackHandler(enabled = true){
                        viewModel.onBackPress()
                    }

                    when {
                        showAboutScreen -> {
                            AboutScreen(onDismiss = viewModel::onDismissAbout)
                        }
                        showDisplaySample -> {
                            DisplaySampleScreen(onDismiss = viewModel::onDismissInfo)
                        }
                        showPracticeMode -> {
                            LearningHubScreen(
                                onDismiss = viewModel::onDismissPracticeMode,
                                onStartFlashcards = viewModel::startFlashcardPractice
                            )
                        }
                        showSettingsScreen -> {
                            SettingsScreen(
                                onDismiss = viewModel::onDismissSettings,
                                showLandmarks = showLandmarks,
                                onToggleShowLandmarks = viewModel::toggleShowLandmarks,
                                isAutoAppendEnabled = isAutoAppendEnabled,
                                onToggleAutoAppend = viewModel::toggleAutoAppend
                            )
                        }
                        else -> {
                            //StateFlow for camera permission
                            val lifecycleOwner = LocalLifecycleOwner.current

                            // StateFlow for mediapipe handlandmark
                            val handBoundingBoxes by viewModel.handBoundingBoxes.collectAsState()
                            val landmarkResult by viewModel.handLandmarkerResult.collectAsState()
                            val currentCameraLens by viewModel.currentCameraLens.collectAsState()

                            // StateFlow for signs
                            val predictedSign by viewModel.predictedSign.collectAsState()
                            val currentConfidence by viewModel.currentConfidence.collectAsState()

                            // StateFlows
                            val signHistory by viewModel.signHistory.collectAsState()
                            val isTorchOn by viewModel.isTorchOn.collectAsState()
                            val showLandmarks by viewModel.showLandmarks.collectAsState()
                            val errorMessage by viewModel.errorMessage.collectAsState()

                            // States for Practice Mode
                            val practiceState by viewModel.practiceState.collectAsState()
                            val currentPracticeLetter by viewModel.currentPracticeLetter.collectAsState()
                            val practiceScore by viewModel.practiceScore.collectAsState()
                            val feedback by viewModel.feedback.collectAsState()
                            val showHintImage by viewModel.showHintImage.collectAsState()

                            SignifyCameraScreen(
                                hasCameraPermission = hasCameraPermission,
                                requestCameraPermission = { onResult ->
                                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                                lifecycleOwner = lifecycleOwner,
                                setupCamera = viewModel::setupCameraAndHandTracking,
                                handBoundingBoxes = handBoundingBoxes,
                                landmarkResult = landmarkResult,
                                currentCameraLens = currentCameraLens,
                                onToggleCamera = viewModel::toggleCamera,
                                predictedSign = predictedSign,
                                predictedSignConfidence = currentConfidence,
                                signHistory = signHistory,
                                isTorchOn = isTorchOn,
                                showLandmarks = showLandmarks,
                                errorMessage = errorMessage,
                                onToggleTorch = viewModel::toggleTorch,
                                onAppendSignToHistory = viewModel::appendPredictedSignToHistory,
                                onClearHistory = viewModel::clearHistory,
                                onDisplaySample = viewModel::onDisplaySample,
                                onDisplayAbout = viewModel::onDisplayAbout,
                                onStartPracticeMode = viewModel::onStartPracticeMode,
                                practiceState = practiceState,
                                currentPracticeLetter = currentPracticeLetter,
                                practiceScore = practiceScore,
                                feedback = feedback,
                                showHintImage = showHintImage,
                                onEndPractice = viewModel::endPractice,
                                onHintRequested = viewModel::onHintRequested,
                                onPreviousLetter = viewModel::onPreviousLetter,
                                onNextLetter = viewModel::onNextLetter,
                                onSpeakSignHistory = viewModel::speakSignHistory,
                                onSetTtsLanguage = viewModel::setTtsLanguage,
                                onDeleteLastSign = viewModel::deleteLastSignFromHistory,
                                onDisplaySettings = viewModel::onDisplaySettings
                            )
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestMediaPermissions() {
        // list for all the permissions required
        val permissionsToRequest = mutableListOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS
        )

        // filter out the permissions thats been granted
        val permissionsNotGranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsNotGranted.isNotEmpty()) {
            requestMediaPermissionsLauncher.launch(permissionsNotGranted)
        }
    }

    // Sched notif every 8 hours
    private fun scheduleNotificationWorker() {
        val notificationWorkRequest =
            PeriodicWorkRequestBuilder<NotificationWorker>(8, TimeUnit.HOURS)
                .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "SignifyReminderNotification",
            ExistingPeriodicWorkPolicy.KEEP,
            notificationWorkRequest
        )
    }
}