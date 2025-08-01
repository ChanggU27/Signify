package com.example.signify02

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.signify02.ui.Yrsa

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningHubScreen(
    onDismiss: () -> Unit,
    onStartFlashcards: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Practice Mode", fontFamily = Yrsa) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        // First look of practice mode
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Ready to Practice?", style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Yrsa))
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onStartFlashcards,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xff109e0b))
            ) {
                Text("Start Flashcards", fontFamily = Yrsa)
            }
        }
    }
}

@Composable
fun PracticeHUD(
    modifier: Modifier = Modifier,
    targetLetter: String?,
    currentScore: Int,
    feedback: MainViewModel.Feedback?,
    onExit: () -> Unit,
    showHint: Boolean,
    onHintRequested: () -> Unit,
    onPreviousLetter: () -> Unit,
    onNextLetter: () -> Unit
) {
    // Find the full Sign object from the master list using the ID
    val signToPractice = AllSigns.find { it.id.equals(targetLetter, ignoreCase = true) }

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Score: $currentScore",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Yrsa,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 8f))
            )
            Button(onClick = onHintRequested) {
                Icon(Icons.Default.Lightbulb, "Show Hint", modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Hint", fontFamily = Yrsa)
            }
            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xff960c18), contentColor = Color.White)
            ) {
                Text("End Practice", fontFamily = Yrsa)
            }
        }

        if (signToPractice != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = "Make the sign for:",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 24.sp,
                    fontFamily = Yrsa,
                    style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 10f))
                )

                Text(
                    text = signToPractice.displayName,
                    color = Color.White,
                    fontSize = 110.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = Yrsa,
                    textAlign = TextAlign.Center,
                    style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 10f))
                )
                Spacer(modifier = Modifier.height(185.dp))
            }
        }

        // Previous/Next button
        IconButton(
            onClick = onPreviousLetter,
            modifier = Modifier.align(Alignment.CenterStart).offset(x = 16.dp, y = (30).dp).size(48.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous Letter", tint = Color.White, modifier = Modifier.size(36.dp))
        }
        IconButton(
            onClick = onNextLetter,
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-16).dp, y = (30).dp).size(48.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next Letter", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        // display hint image
        AnimatedVisibility(
            visible = showHint && signToPractice != null,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300, delayMillis = 400)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            if (signToPractice != null) {
                Box(
                    modifier = Modifier.size(150.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = signToPractice.drawableRes),
                        contentDescription = "Hint for ${signToPractice.displayName}",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = feedback != null && feedback.isCorrect,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300, delayMillis = 400)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xff109e0b)).padding(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Text("Correct!", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = Yrsa)
            }
        }
    }
}