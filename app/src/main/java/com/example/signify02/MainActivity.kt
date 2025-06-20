package com.example.signify02

import android.Manifest
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
import androidx.core.view.WindowCompat
import com.google.accompanist.systemuicontroller.rememberSystemUiController

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

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
                    // We collect all necessary state here at the top level
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
                                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        permissionResultCallback = { isGranted ->
                                            viewModel.updateCameraPermissionStatus(isGranted)
                                            onPermissionResult(isGranted)
                                        }
                                    },
                                    setupCamera = viewModel::setupCameraAndHandTracking
                                )

                                if (practiceState == PracticeState.PRACTICING) {
                                    PracticeHUD(
                                        targetLetter = currentPracticeLetter,
                                        currentScore = practiceScore,
                                        feedback = feedback,
                                        onExit = viewModel::endPractice
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private var permissionResultCallback: ((Boolean) -> Unit)? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            permissionResultCallback?.invoke(isGranted)
            permissionResultCallback = null
        }
}

fun Color.luminance(): Float {
    return (0.2126f * red + 0.7152f * green + 0.0722f * blue)
}