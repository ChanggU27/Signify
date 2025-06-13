package com.example.signify02

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

// ====================================================================================
// --- Activity (Entry Point) ---
// ====================================================================================
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            Signify02Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Collect necessary states from ViewModel
                    val predictedSign by viewModel.predictedSign.collectAsState()
                    val currentConfidence by viewModel.currentConfidence.collectAsState()
                    val signHistory by viewModel.signHistory.collectAsState()
                    val errorMessage by viewModel.errorMessage.collectAsState()
                    val handBoundingBoxes by viewModel.handBoundingBoxes.collectAsState()
                    val showLandmarks by viewModel.showLandmarks.collectAsState()
                    val landmarkResult by viewModel.handLandmarkerResult.collectAsState()
                    val isTextToSpeechEnabled by viewModel.isTextToSpeechEnabled.collectAsState()
                    val currentCameraLens by viewModel.currentCameraLens.collectAsState()
                    val isTorchOn by viewModel.isTorchOn.collectAsState() // Re-collected

                    ASLCameraScreen(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
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
                        onShowInfo = viewModel::onShowInfo,
                        requestCameraPermission = { onPermissionResult ->
                            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                            permissionResultCallback = onPermissionResult
                        },
                        setupCamera = viewModel::setupCameraAndHandTracking
                    )
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