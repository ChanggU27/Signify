package com.example.signify02

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp



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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Ready to Practice?",
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Yrsa),
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Button(
                onClick = onStartFlashcards,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xff109e0b),
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ))
             {
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
    feedback: Feedback?,
    onExit: () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Top Banner for instructions and score
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 195.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Score Display
            Text(
                text = "Score: $currentScore",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Yrsa,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 8f))
            )

            // Exit Button
            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xff960c18),
                    contentColor = Color.White
                )
            ) {
                Text("End Practice", fontFamily = Yrsa)
            }
        }

        // Center display
        targetLetter?.let {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-120).dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Make the sign for:",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 24.sp,
                        fontFamily = Yrsa,
                        style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 10f))
                    )
                    Text(
                        text = it,
                        color = Color.White,
                        fontSize = 140.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = Yrsa,
                        style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 10f))
                    )
                }
            }
        }

        // Feedback overlay
        AnimatedVisibility(
            visible = feedback != null,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300, delayMillis = 400)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            if (feedback != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xff109e0b))
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "Correct!",
                        color = Color.White,

                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Yrsa
                    )
                }
            }
        }
    }
}