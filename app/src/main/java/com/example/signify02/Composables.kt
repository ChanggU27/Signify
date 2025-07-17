package com.example.signify02

import android.content.Context
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.core.content.res.ResourcesCompat
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.window.Dialog

// ====================================================================================
// --- Composable UI Layer ---
// ====================================================================================

val HAND_CONNECTIONS = listOf(
    Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4),         // Thumb
    Pair(5, 6), Pair(6, 7), Pair(7, 8),                     // Index finger
    Pair(9, 10), Pair(10, 11), Pair(11, 12),                // Middle finger
    Pair(13, 14), Pair(14, 15), Pair(15, 16),               // Ring finger
    Pair(17, 18), Pair(18, 19), Pair(19, 20),               // Pinky
    Pair(0, 5), Pair(5, 9), Pair(9, 13), Pair(13, 17), Pair(0, 17) // Palm
)

val SignDrawables = mapOf(
    'A' to R.drawable.a_test,
    'B' to R.drawable.b_test,
    'C' to R.drawable.c_test,
    'D' to R.drawable.d_test,
    'E' to R.drawable.e_test,
    'F' to R.drawable.f_test,
    'G' to R.drawable.g_test,
    'H' to R.drawable.h_test,
    'I' to R.drawable.i_test,
    'J' to R.drawable.j_test,
    'K' to R.drawable.k_test,
    'L' to R.drawable.l_test,
    'M' to R.drawable.m_test,
    'N' to R.drawable.n_test,
    'O' to R.drawable.o_test,
    'P' to R.drawable.p_test,
    'Q' to R.drawable.q_test,
    'R' to R.drawable.r_test,
    'S' to R.drawable.s_test,
    'T' to R.drawable.t_test,
    'U' to R.drawable.u_test,
    'V' to R.drawable.v_test,
    'W' to R.drawable.w_test,
    'X' to R.drawable.x_test,
    'Y' to R.drawable.y_test,
    'Z' to R.drawable.z_test,
)

@Composable
fun SignifyCameraScreen(
    modifier: Modifier = Modifier,
    //Camera Permission
    hasCameraPermission: Boolean,
    // Sign Recognition State
    predictedSign: String,
    currentConfidence: Float,
    signHistory: List<String>,
    // Hand Detection State
    handBoundingBoxes: List<RectF>,
    // Common State & Callbacks
    isTorchOn: Boolean,
    currentCameraLens: Int,
    errorMessage: String?,
    onToggleTorch: () -> Unit,
    onClearHistory: () -> Unit,
    onToggleCamera: () -> Unit,
    requestCameraPermission: (onPermissionResult: (Boolean) -> Unit) -> Unit,
    setupCamera: (Context, LifecycleOwner, Preview.SurfaceProvider) -> Unit,
    // ViewModel needed by child composables
    showLandmarks: Boolean,
    landmarkResult: HandLandmarkerResult?,
    onToggleShowLandmarks: () -> Unit,
    // TTS parameters
    isTextToSpeechEnabled: Boolean,
    onToggleTextToSpeech: () -> Unit,
    // Gallery button callback
    onDisplaySample: () -> Unit,
    // Practice Mode
    onStartPracticeMode: () -> Unit,
    // About Screen
    onDisplayAbout: () -> Unit,
    practiceState: PracticeState,
    // Append to sign history button
    onAppendSignToHistory: () -> Unit

) {

    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var showPermissionRationale by remember { mutableStateOf(!hasCameraPermission) }
    var previewView: PreviewView? by remember { mutableStateOf(null) }

    LaunchedEffect(hasCameraPermission, previewView, currentCameraLens) {
        if (hasCameraPermission && previewView != null) {
            Log.d(TAG, "Permission granted and PreviewView ready, setting up camera.")
            setupCamera(context, lifecycleOwner, previewView!!.surfaceProvider)
        } else {
            Log.d(TAG, "Conditions not met for camera setup: hasPermission=$hasCameraPermission, previewViewIsNull=${previewView == null}")
        }

    }


// Main layout column
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val cameraPreviewShape = RoundedCornerShape(
            bottomStart = 24.dp,
            bottomEnd = 24.dp
        )

        Box(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .clip(cameraPreviewShape)
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }.also {
                            previewView = it
                            Log.d(TAG, "PreviewView created and assigned.")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                HandDetectionOverlay(
                    normalizedBoundingBoxes = handBoundingBoxes,
                    predictedSign = predictedSign,
                    predictedSignConfidence = currentConfidence,
                    showLandmarks = showLandmarks,
                    landmarkResult = landmarkResult,
                    currentCameraLens = currentCameraLens,
                    modifier = Modifier.fillMaxSize()
                )

                // Top bar content
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 34.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Kebab Menu
                    Box {
                        var menuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("ASL Alphabet Samples", fontFamily = Yrsa) },
                                onClick = {
                                    onDisplaySample()
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Practice Mode", fontFamily = Yrsa) },
                                onClick = {
                                    onStartPracticeMode()
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("About", fontFamily = Yrsa) },
                                onClick = {
                                    onDisplayAbout()
                                    menuExpanded = false
                                }
                            )
                        }
                    }

                    // Center: Title
                    Text(
                        text = if (practiceState == PracticeState.PRACTICING) "ASL Practice" else "Signify",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = Yrsa,
                        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(4f, 4f), 8f))
                    )

                    // Right: Camera Switch
                    IconButton(
                        onClick = onToggleCamera,
                        modifier = Modifier
                            .size(36.dp)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Cameraswitch,
                            contentDescription = if (currentCameraLens == CameraSelector.LENS_FACING_BACK) "Switch to Front Camera" else "Switch to Back Camera",
                            tint = Color.White
                        )
                    }
                }
            } else {
                // Show the permission request UI
                PermissionRequestUI(
                    showRationale = showPermissionRationale,
                    onRequestPermission = {
                        requestCameraPermission { isGranted ->

                            showPermissionRationale = !isGranted
                            Log.d(TAG, "Permission result: $isGranted")
                        }
                    }
                )
            }

            // Display error messages at the bottom of the preview area
            errorMessage?.let { message ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                        .padding(8.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        if (hasCameraPermission){
            RecognizedSignBox(
                modifier = Modifier.navigationBarsPadding(),
                signHistory = signHistory,
                isTorchOn = isTorchOn,
                showLandmarks = showLandmarks,
                onToggleShowLandmarks = onToggleShowLandmarks,
                onClearHistory = onClearHistory,
                onToggleTorch = onToggleTorch,
                isTextToSpeechEnabled = isTextToSpeechEnabled,
                onToggleTextToSpeech = onToggleTextToSpeech,
                onAppendSignToHistory = onAppendSignToHistory
            )
        }
    }
}

@Composable
fun PermissionRequestUI(
    showRationale: Boolean,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (showRationale) "Camera permission is required for Signify to function." else "Camera permission is required for Signify to function.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Grant Permission")
        }
    }
}



@Composable
fun RecognizedSignBox(
    modifier: Modifier = Modifier,
    signHistory: List<String>,
    isTorchOn: Boolean,
    showLandmarks: Boolean,
    onToggleShowLandmarks: () -> Unit,
    onClearHistory: () -> Unit,
    onToggleTorch: () -> Unit,
    isTextToSpeechEnabled: Boolean,
    onToggleTextToSpeech: () -> Unit,
    onAppendSignToHistory: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(0.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Transparent)
            .padding(16.dp)

    ) {
        Column {
            // Top row for title and action icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,

                ) {

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    IconButton(
                        onClick = onToggleTorch,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isTorchOn) MaterialTheme.colorScheme.secondary else Color.Transparent) // Changed to MaterialTheme.colorScheme.secondary
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                            contentDescription = "Toggle Flashlight",
                            tint = if (isTorchOn) MaterialTheme.colorScheme.onSecondary else LocalContentColor.current // Changed to MaterialTheme.colorScheme.onSecondary
                        )
                    }

                    // Landmark Toggle Button
                    IconButton(
                        onClick = onToggleShowLandmarks,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (showLandmarks) MaterialTheme.colorScheme.secondary else Color.Transparent) // Changed to MaterialTheme.colorScheme.secondary
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = if (showLandmarks) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Toggle Landmark Visibility",
                            tint = if (showLandmarks) MaterialTheme.colorScheme.onSecondary else LocalContentColor.current // Changed to MaterialTheme.colorScheme.onSecondary
                        )
                    }

                    // Text-to-Speech Toggle Button
                    IconButton(
                        onClick = onToggleTextToSpeech,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isTextToSpeechEnabled) MaterialTheme.colorScheme.secondary else Color.Transparent) // Changed to MaterialTheme.colorScheme.secondary
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isTextToSpeechEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Toggle Text-to-Speech",
                            tint = if (isTextToSpeechEnabled) MaterialTheme.colorScheme.onSecondary else LocalContentColor.current // Changed to MaterialTheme.colorScheme.onSecondary
                        )
                    }

                    // Add to History Button
                    IconButton(
                        onClick = onAppendSignToHistory,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add to History",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    // Clear History Button
                    AnimatedVisibility(visible = signHistory.isNotEmpty()) {
                        IconButton(
                            onClick = onClearHistory,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.error)
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear History",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(
                        1.dp,
                        Color.Black,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Sign History: ${signHistory.joinToString(" ")}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun HandDetectionOverlay(
    normalizedBoundingBoxes: List<RectF>,
    predictedSign: String,
    predictedSignConfidence: Float,
    showLandmarks: Boolean,
    landmarkResult: HandLandmarkerResult?,
    currentCameraLens: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val yrsaTypeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.yrsa_variablefont_wght)
    }

    val landmarkColor = Color.Red
    val connectionColor = Color.White
    val textColor = Color.White
    val textBackgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    val lowConfidenceColor = MaterialTheme.colorScheme.error
    val highConfidenceColor = Color.White

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val firstHandBox = normalizedBoundingBoxes.firstOrNull()

        if (currentCameraLens == CameraSelector.LENS_FACING_FRONT) {
            scale(scaleX = -1f, scaleY = 1f, pivot = Offset(canvasWidth / 2f, canvasHeight / 2f)) {
                drawHandDetectionVisuals(
                    normalizedBoundingBoxes, predictedSignConfidence, showLandmarks,
                    landmarkResult, canvasWidth, canvasHeight, landmarkColor,
                    connectionColor, lowConfidenceColor, highConfidenceColor
                )
            }
        } else {
            drawHandDetectionVisuals(
                normalizedBoundingBoxes, predictedSignConfidence, showLandmarks,
                landmarkResult, canvasWidth, canvasHeight, landmarkColor,
                connectionColor, lowConfidenceColor, highConfidenceColor
            )
        }

        if (predictedSign.isNotEmpty() && predictedSignConfidence > 0 && firstHandBox != null) {
            val tooltipText = "Sign: $predictedSign (${(predictedSignConfidence * 100).toInt()}%)"
            val textPaint = Paint().apply {
                color = textColor.toArgb()
                textSize = 40f
                typeface = yrsaTypeface ?: Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            val textWidth = textPaint.measureText(tooltipText)
            val textHeight = textPaint.descent() - textPaint.ascent()
            var finalTooltipX = if (currentCameraLens == CameraSelector.LENS_FACING_FRONT) {
                (1f - firstHandBox.centerX()) * canvasWidth
            } else {
                firstHandBox.centerX() * canvasWidth
            }
            var finalTooltipY = (firstHandBox.top * canvasHeight) - textHeight / 2 - 30f
            finalTooltipX = finalTooltipX.coerceIn(textWidth / 2 + 20f, canvasWidth - textWidth / 2 - 20f)
            finalTooltipY = finalTooltipY.coerceIn(textHeight + 20f, canvasHeight - 20f)

            drawContext.canvas.nativeCanvas.drawRoundRect(
                finalTooltipX - textWidth / 2 - 20f,
                finalTooltipY - textHeight - 10f,
                finalTooltipX + textWidth / 2 + 20f,
                finalTooltipY + 25f,
                20f, 20f,
                Paint().apply {
                    color = textBackgroundColor.toArgb()
                    style = Paint.Style.FILL
                }
            )
            drawContext.canvas.nativeCanvas.drawText(tooltipText, finalTooltipX, finalTooltipY, textPaint)
        }
    }
}

private fun DrawScope.drawHandDetectionVisuals(
    normalizedBoundingBoxes: List<RectF>,
    predictedSignConfidence: Float,
    showLandmarks: Boolean,
    landmarkResult: HandLandmarkerResult?,
    canvasWidth: Float,
    canvasHeight: Float,
    landmarkColor: Color,
    connectionColor: Color,
    lowConfidenceColor: Color,
    highConfidenceColor: Color
) {
    val overlayColor: Color = Color.Black.copy(alpha = 0.6f)
    val firstHandBox = normalizedBoundingBoxes.firstOrNull()

    // Dim the background
    if (firstHandBox != null) {
        val left = (firstHandBox.left * canvasWidth).coerceIn(0f, canvasWidth)
        val top = (firstHandBox.top * canvasHeight).coerceIn(0f, canvasHeight)
        val right = (firstHandBox.right * canvasWidth).coerceIn(left, canvasWidth)
        val bottom = (firstHandBox.bottom * canvasHeight).coerceIn(top, canvasHeight)
        drawRect(color = overlayColor, topLeft = Offset(0f, 0f), size = ComposeSize(canvasWidth, top), style = Fill)
        drawRect(color = overlayColor, topLeft = Offset(0f, bottom), size = ComposeSize(canvasWidth, canvasHeight - bottom), style = Fill)
        drawRect(color = overlayColor, topLeft = Offset(0f, top), size = ComposeSize(left, bottom - top), style = Fill)
        drawRect(color = overlayColor, topLeft = Offset(right, top), size = ComposeSize(canvasWidth - right, bottom - top), style = Fill)
    }

    // Draw Bounding Box Outline
    if (firstHandBox != null) {
        // Use the color passed in as a parameter
        val outlineColor = if (predictedSignConfidence < 0.70f) lowConfidenceColor else highConfidenceColor
        normalizedBoundingBoxes.forEach { box ->
            drawRect(
                color = outlineColor,
                topLeft = Offset(box.left * canvasWidth, box.top * canvasHeight),
                size = ComposeSize((box.right - box.left) * canvasWidth, (box.bottom - box.top) * canvasHeight),
                style = Stroke(width = 6f)
            )
        }
    }

    // Draw Hand Landmarks and Connections
    if (showLandmarks && landmarkResult != null) {
        landmarkResult.landmarks().forEach { handLandmarks ->
            HAND_CONNECTIONS.forEach {
                val start = handLandmarks[it.first]
                val end = handLandmarks[it.second]
                drawLine(
                    color = connectionColor,
                    start = Offset(start.x() * canvasWidth, start.y() * canvasHeight),
                    end = Offset(end.x() * canvasWidth, end.y() * canvasHeight),
                    strokeWidth = 4f
                )
            }
            handLandmarks.forEach { landmark ->
                drawCircle(
                    color = landmarkColor,
                    radius = 6f,
                    center = Offset(landmark.x() * canvasWidth, landmark.y() * canvasHeight)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySampleScreen(
    onDismiss: () -> Unit
) {
    BackHandler(enabled = true) {
        onDismiss()
    }

    val signs = ('A'..'Z').toList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ASL Alphabet Samples") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 128.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(signs) { sign ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(8.dp)
                ) {
                    val resourceId = SignDrawables[sign]
                    if (resourceId != null) {
                        Image(
                            painter = painterResource(id = resourceId),
                            contentDescription = "Sign for letter $sign",
                            modifier = Modifier.size(100.dp)
                        )
                    } else {
                        Text(text = "No image for $sign")
                        Log.e(TAG, "Drawable for sign '${sign}' not found in map.")
                    }
                    Text(text = sign.toString())
                }
            }
        }
    }
}

@Composable
fun SignifyDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String,
    confirmButtonText: String,
    onConfirm: () -> Unit,
    dismissButtonText: String? = null,
    onDismiss: (() -> Unit)? = null,
    showCheckbox: Boolean = false,
    checkboxChecked: Boolean = false,
    onCheckboxCheckedChange: ((Boolean) -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = Yrsa),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Yrsa),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(5.dp))

                // Checkbox for "Do not show again"
                if (showCheckbox && onCheckboxCheckedChange != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Checkbox(
                            checked = checkboxChecked,
                            onCheckedChange = onCheckboxCheckedChange
                        )
                        Text(
                            text = "Do not show again",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Yrsa),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (dismissButtonText != null && onDismiss != null) {
                        TextButton(onClick = onDismiss) {
                            Text(dismissButtonText, fontFamily = Yrsa)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(onClick = onConfirm) {
                        Text(confirmButtonText, fontFamily = Yrsa)
                    }
                }
            }
        }
    }
}


@Composable
fun ExitConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    SignifyDialog(
        onDismissRequest = onDismiss,
        title = "Exit Signify?",
        text = "Are you sure you want to exit the app?",
        confirmButtonText = "Yes",
        onConfirm = onConfirm,
        dismissButtonText = "No",
        onDismiss = onDismiss
    )
}

@Composable
fun InitialInfoDialog(
    onDismiss: (Boolean) -> Unit
) {
    var doNotShowAgainChecked by remember { mutableStateOf(false) }

    SignifyDialog(
        onDismissRequest = { onDismiss(doNotShowAgainChecked) },
        title = "REMINDER",
        text = "To achieve the most accurate results, please use a plain, well-lit background.",
        confirmButtonText = "Got it!",
        onConfirm = { onDismiss(doNotShowAgainChecked) },
        showCheckbox = true,
        checkboxChecked = doNotShowAgainChecked,
        onCheckboxCheckedChange = { doNotShowAgainChecked = it }
    )
}