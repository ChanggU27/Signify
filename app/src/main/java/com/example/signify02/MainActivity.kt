
package com.example.signify02

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.core.view.WindowCompat
import com.google.accompanist.systemuicontroller.rememberSystemUiController

// ====================================================================================
// --- Activity (Entry Point) ---
// ====================================================================================
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind system bars (status bar and navigation bar)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        super.onCreate(savedInstanceState)

        setContent {
            val systemUiController = rememberSystemUiController()
            val useDarkIcons = MaterialTheme.colorScheme.surface.luminance() > 0.5f

            SideEffect {
                systemUiController.setStatusBarColor(
                    color = Color.Transparent,
                    darkIcons = useDarkIcons
                )
                systemUiController.setNavigationBarColor(
                    color = Color.Transparent,
                    darkIcons = useDarkIcons
                )
            }

            Signify02Theme(dynamicColor = false) {
                // A surface container using BG color from theme.kt
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val showExitDialog by viewModel.showExitDialog.collectAsState()
                    val showInitialInfoDialog by viewModel.showInitialInfoDialog.collectAsState()

                    if (showInitialInfoDialog) {
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

                    // Collect necessary states from ViewModel
                    val displaySample by viewModel.showDisplaySample.collectAsState()
                    val hasCameraPermission by viewModel.hasCameraPermission.collectAsState()

                    if (displaySample) {
                        DisplaySampleScreen(onDismiss = viewModel::onDismissInfo)
                    } else {
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

                        SignifyCameraScreen(
                            modifier = Modifier.fillMaxSize(),
                            // Pass hasCameraPermission from ViewModel
                            hasCameraPermission = hasCameraPermission,
                            // Sign Recognition State
                            predictedSign = predictedSign,
                            currentConfidence = currentConfidence,
                            signHistory = signHistory,
                            // Hand Detection State
                            handBoundingBoxes = handBoundingBoxes,
                            showLandmarks = showLandmarks,
                            landmarkResult = landmarkResult,
                            // Common State & Callbacks
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
                            onBackPress = viewModel::onBackPress,
                            requestCameraPermission = { onPermissionResult ->
                                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                                // Update ViewModel's permission state after result
                                permissionResultCallback = { isGranted ->
                                    viewModel.updateCameraPermissionStatus(isGranted) // UPDATE HERE
                                    onPermissionResult(isGranted)
                                }
                            },
                            setupCamera = viewModel::setupCameraAndHandTracking
                        )
                    }
                }
            }
        }
    }

    // --- Permission Handling ---
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