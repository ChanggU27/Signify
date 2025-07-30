package com.example.signify02

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.example.signify02.ui.Yrsa
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.core.content.res.ResourcesCompat

val HAND_CONNECTIONS = listOf(
    Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4),         // Thumb
    Pair(5, 6), Pair(6, 7), Pair(7, 8),                     // Index finger
    Pair(9, 10), Pair(10, 11), Pair(11, 12),                // Middle finger
    Pair(13, 14), Pair(14, 15), Pair(15, 16),               // Ring finger
    Pair(17, 18), Pair(18, 19), Pair(19, 20),               // Pinky
    Pair(0, 5), Pair(5, 9), Pair(9, 13), Pair(13, 17), Pair(0, 17) // Palm
)

val SignDrawables = mapOf(
    'A' to R.drawable.a_test, 'B' to R.drawable.b_test, 'C' to R.drawable.c_test,
    'D' to R.drawable.d_test, 'E' to R.drawable.e_test, 'F' to R.drawable.f_test,
    'G' to R.drawable.g_test, 'H' to R.drawable.h_test, 'I' to R.drawable.i_test,
    'J' to R.drawable.j_test, 'K' to R.drawable.k_test, 'L' to R.drawable.l_test,
    'M' to R.drawable.m_test, 'N' to R.drawable.n_test, 'O' to R.drawable.o_test,
    'P' to R.drawable.p_test, 'Q' to R.drawable.q_test, 'R' to R.drawable.r_test,
    'S' to R.drawable.s_test, 'T' to R.drawable.t_test, 'U' to R.drawable.u_test,
    'V' to R.drawable.v_test, 'W' to R.drawable.w_test, 'X' to R.drawable.x_test,
    'Y' to R.drawable.y_test, 'Z' to R.drawable.z_test, '_' to R.drawable.space_test
)

@Composable
fun SignifyCameraScreen(
    hasCameraPermission: Boolean,
    requestCameraPermission: (onPermissionResult: (Boolean) -> Unit) -> Unit,
    lifecycleOwner: LifecycleOwner,
    setupCamera: (Context, LifecycleOwner, Preview.SurfaceProvider) -> Unit,
    handBoundingBoxes: List<RectF>,
    landmarkResult: HandLandmarkerResult?,
    currentCameraLens: Int,
    onToggleCamera: () -> Unit,
    predictedSign: String,
    predictedSignConfidence: Float,
    signHistory: List<String>,
    isTorchOn: Boolean,
    showLandmarks: Boolean,
    errorMessage: String?,
    onToggleTorch: () -> Unit,
    onToggleShowLandmarks: () -> Unit,
    onAppendSignToHistory: () -> Unit,
    onClearHistory: () -> Unit,
    onDisplaySample: () -> Unit,
    onDisplayAbout: () -> Unit,
    onStartPracticeMode: () -> Unit,
    practiceState: MainViewModel.PracticeState,
    currentPracticeLetter: String?,
    practiceScore: Int,
    feedback: MainViewModel.Feedback?,
    showHintImage: Boolean,
    onEndPractice: () -> Unit,
    onHintRequested: () -> Unit,
    onPreviousLetter: () -> Unit,
    onNextLetter: () -> Unit,
    onSpeakSignHistory: () -> Unit
) {
    val context = LocalContext.current
    var previewView: PreviewView? by remember { mutableStateOf(null) }

    LaunchedEffect(previewView, currentCameraLens) {
        if (hasCameraPermission && previewView != null) {
            setupCamera(context, lifecycleOwner, previewView!!.surfaceProvider)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)),
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }.also {
                            previewView = it
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                HandDetectionOverlay(
                    normalizedBoundingBoxes = handBoundingBoxes,
                    landmarkResult = landmarkResult,
                    currentCameraLens = currentCameraLens,
                    modifier = Modifier.fillMaxSize(),
                    predictedSign = predictedSign,
                    predictedSignConfidence = predictedSignConfidence,
                    showLandmarks = showLandmarks
                )

                if (practiceState == MainViewModel.PracticeState.PRACTICING) {
                    PracticeHUD(
                        targetLetter = currentPracticeLetter,
                        currentScore = practiceScore,
                        feedback = feedback,
                        onExit = onEndPractice,
                        showHint = showHintImage,
                        onHintRequested = onHintRequested,
                        onPreviousLetter = onPreviousLetter,
                        onNextLetter = onNextLetter
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box{
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
                            onDismissRequest = { menuExpanded = false}
                        ) {
                            DropdownMenuItem(
                                text = {Text("ASL Alphabet Samples", fontFamily = Yrsa)},
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
                                text = {Text("About", fontFamily = Yrsa)},
                                onClick = {
                                    onDisplayAbout()
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                    Text(
                        text = if (practiceState == MainViewModel.PracticeState.PRACTICING) "ASL Practice" else "Signify",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = Yrsa,
                        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(4f, 4f), 8f))
                    )
                    IconButton(onClick = onToggleCamera) {
                        Icon(
                            imageVector = Icons.Filled.Cameraswitch,
                            contentDescription = "Switch Camera",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                errorMessage?.let{ message ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                            .padding(8.dp)
                            .align(Alignment.BottomCenter)
                    ){
                        Text(text = message, color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.align(Alignment.Center))
                    }
                }
            } else {
                PermissionRequestUI(onRequestPermission = { requestCameraPermission {} })
            }
        }

        if(hasCameraPermission){
            RecognizedSignBox(
                modifier = Modifier.navigationBarsPadding(),
                signHistory = signHistory,
                isTorchOn = isTorchOn,
                showLandmarks = showLandmarks,
                onToggleTorch = onToggleTorch,
                onToggleShowLandmarks = onToggleShowLandmarks,
                onAppendSignToHistory = onAppendSignToHistory,
                onClearHistory = onClearHistory,
                onSpeakSignHistory = onSpeakSignHistory
            )
        }
    }
}


//Camera permission UI
@Composable
fun PermissionRequestUI(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera permission is required for Signify to function.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Button(onClick = onRequestPermission) {
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
    onToggleTorch: () -> Unit,
    onToggleShowLandmarks: () -> Unit,
    onAppendSignToHistory: () -> Unit,
    onClearHistory: () -> Unit,
    onSpeakSignHistory: () -> Unit
){
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)){
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
        ){
            IconButton(
                onClick = onToggleTorch,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (isTorchOn) MaterialTheme.colorScheme.secondary else Color.Transparent)
            ){
                Icon(
                    imageVector = if (isTorchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                    contentDescription = "Toggle Flashlight",
                    tint = if (isTorchOn) MaterialTheme.colorScheme.onSecondary else LocalContentColor.current
                )
            }
            IconButton(
                onClick = onToggleShowLandmarks, // Corrected typo
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (showLandmarks) MaterialTheme.colorScheme.secondary else Color.Transparent)
            ){
                Icon(
                    imageVector = if (showLandmarks) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = "Toggle Landmark Visibility",
                    tint = if (showLandmarks) MaterialTheme.colorScheme.onSecondary else LocalContentColor.current
                )
            }
            IconButton(onClick = onSpeakSignHistory,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color.Yellow)) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = "Speak Sign History",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onAppendSignToHistory,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
            ){
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add to Sign History",
                    tint = Color.White
                )
            }
            AnimatedVisibility(visible = signHistory.isNotEmpty()) {
                IconButton(
                    onClick = onClearHistory,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.error)
                ){
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "Clear History",
                        tint = Color.White
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ){
            Text(
                text = "Sign History: ${signHistory.joinToString(" ")}",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Black,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
fun HandDetectionOverlay(
    normalizedBoundingBoxes: List<RectF>,
    landmarkResult: HandLandmarkerResult?,
    modifier: Modifier = Modifier,
    currentCameraLens: Int,
    predictedSign: String,
    predictedSignConfidence: Float,
    showLandmarks: Boolean
) {
    val context = LocalContext.current
    val yrsaTypeface = remember(context) {
        try { ResourcesCompat.getFont(context, R.font.yrsa_variablefont_wght) } catch (_: Exception) { Typeface.DEFAULT }
    }

    val textBackgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    val lowConfidenceColor = MaterialTheme.colorScheme.error
    val highConfidenceColor = Color.White
    val outlineColor = if (predictedSignConfidence < 0.60f) lowConfidenceColor else highConfidenceColor

    Canvas(modifier = modifier) {
        if (currentCameraLens == CameraSelector.LENS_FACING_FRONT) {
            scale(scaleX = -1f, scaleY = 1f, pivot = center) {
                drawHandDetectionVisuals(
                    normalizedBoundingBoxes,
                    landmarkResult,
                    size.width,
                    size.height,
                    outlineColor = outlineColor,
                    showLandmarks = showLandmarks
                )
            }
        } else {
            drawHandDetectionVisuals(
                normalizedBoundingBoxes,
                landmarkResult,
                size.width,
                size.height,
                outlineColor = outlineColor,
                showLandmarks = showLandmarks
            )
        }

        // tooltip drawing logic
        val firstHandBox = normalizedBoundingBoxes.firstOrNull()
        if (predictedSign.isNotEmpty() && firstHandBox != null) {
            val tooltipText = "Sign: $predictedSign (${(predictedSignConfidence * 100).toInt()})%"
            val textPaint = Paint().apply {
                color = Color.White.toArgb()
                textSize = 40f
                textAlign = Paint.Align.CENTER
                typeface = yrsaTypeface ?: Typeface.DEFAULT_BOLD
            }
            val textWidth = textPaint.measureText(tooltipText)
            val textHeight = textPaint.descent() - textPaint.ascent()
            var finalTooltipX = if (currentCameraLens == CameraSelector.LENS_FACING_FRONT) {
                (1f - firstHandBox.centerX()) * size.width
            } else {
                firstHandBox.centerX() * size.width
            }
            var finalTooltipY = (firstHandBox.top * size.height) - textHeight / 2 - 30f
            finalTooltipX = finalTooltipX.coerceIn(textWidth / 2 + 20f, size.width - textWidth / 2 - 20f)
            finalTooltipY = finalTooltipY.coerceIn(textHeight + 20f, size.height - 20f)

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


// darken overlay around the bounding box, draw hand bounding box, draw hand landmarks (skeletal like)
private fun DrawScope.drawHandDetectionVisuals(
    normalizedBoundingBoxes: List<RectF>,
    landmarkResult: HandLandmarkerResult?,
    canvasWidth: Float,
    canvasHeight: Float,
    outlineColor: Color,
    showLandmarks: Boolean
) {
    val overlayColor: Color = Color.Black.copy(alpha = 0.6f)
    val firstHandBox = normalizedBoundingBoxes.firstOrNull()

    // Background Dimming Effect
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

    // Bounding Box Outline with Confidence Color
    if (firstHandBox != null) {
        drawRect(
            color = outlineColor,
            topLeft = Offset(firstHandBox.left * canvasWidth, firstHandBox.top * canvasHeight),
            size = ComposeSize((firstHandBox.right - firstHandBox.left) * canvasWidth, (firstHandBox.bottom - firstHandBox.top) * canvasHeight),
            style = Stroke(width = 6f)
        )
    }

    // Landmarks and Connections
    if (showLandmarks && landmarkResult != null) {
        landmarkResult.landmarks().forEach { handLandmarks ->
            HAND_CONNECTIONS.forEach {
                val start = handLandmarks[it.first]
                val end = handLandmarks[it.second]
                drawLine(
                    color = Color.White,
                    start = Offset(start.x() * canvasWidth, start.y() * canvasHeight),
                    end = Offset(end.x() * canvasWidth, end.y() * canvasHeight),
                    strokeWidth = 4f
                )
            }
            handLandmarks.forEach { landmark ->
                drawCircle(color = Color.Red, radius = 6f, center = Offset(landmark.x() * canvasWidth, landmark.y() * canvasHeight))
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySampleScreen(
    onDismiss: () -> Unit
) {

    val signs = ('A'..'Z').toList() + '_'

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ASL Alphabet Samples", fontFamily = Yrsa) },
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
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.Center
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
                    }
                    Text(text = if (sign == '_') "Space" else sign.toString())
                }
            }
        }
    }
}

@Composable
fun SignifyDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmButtonText: String,
    onConfirm: () -> Unit,
    dismissButtonText: String? = null,
    onDismiss: (() -> Unit)? = null,
    showCheckbox: Boolean = false,
    checkboxChecked: Boolean = false,
    onCheckboxCheckedChange: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit
){
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
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = Yrsa, fontWeight = FontWeight.Bold, fontSize = 24.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                content()

                Spacer(modifier = Modifier.height(16.dp))

                if (showCheckbox && onCheckboxCheckedChange != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Checkbox(
                            checked = checkboxChecked,
                            onCheckedChange = onCheckboxCheckedChange
                        )
                        Text(
                            text = "Do not show again",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Yrsa)
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
        confirmButtonText = "Yes",
        onConfirm = onConfirm,
        dismissButtonText = "No",
        onDismiss = onDismiss
    ) {

        Text(
            text = "Are you sure you want to exit the app?",
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Yrsa),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun InitialInfoDialog(
    onDismiss: (Boolean) -> Unit
) {
    var doNotShowAgainChecked by remember { mutableStateOf(false) }

    SignifyDialog(
        onDismissRequest = { onDismiss(doNotShowAgainChecked) },
        title = "How to Use",
        confirmButtonText = "Got it!",
        onConfirm = { onDismiss(doNotShowAgainChecked) },
        showCheckbox = true,
        checkboxChecked = doNotShowAgainChecked,
        onCheckboxCheckedChange = { doNotShowAgainChecked = it }
    ) {
        Spacer(modifier = Modifier.height(30.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhotoCamera, contentDescription = "Camera Icon", modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Point the camera at a hand to detect and translate ASL (Alphabet) in real-time.", fontFamily = Yrsa)
            }
            Spacer(modifier = Modifier.height(25.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = "Add Icon", tint = Color.White, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary), )
                Spacer(modifier = Modifier.width(16.dp))
                Text("Press the '+' button to add letters to your sign history.", fontFamily = Yrsa)
            }
            Spacer(modifier = Modifier.height(25.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SpaceBar, contentDescription = "Spacebar Icon", modifier = Modifier.size(40.dp) )
                Spacer(modifier = Modifier.width(16.dp))
                Text("Make the 'space' sign to speak the word and clear the history.", fontFamily = Yrsa)
            }
        }
    }
}