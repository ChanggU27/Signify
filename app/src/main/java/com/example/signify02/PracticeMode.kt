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
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Lightbulb

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
    onExit: () -> Unit,
    showHint: Boolean,
    onHintRequested: () -> Unit,
    onPreviousLetter: () -> Unit,
    onNextLetter: () -> Unit
) {
    val currentHintDrawable = targetLetter?.uppercase()?.let { SignDrawables[it.single()] }

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

            //Hint Button
            Button(
                onClick = onHintRequested,
                modifier = Modifier
                    .wrapContentSize()
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = "Show Hint", modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Hint", fontFamily = Yrsa)
            }

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

        // Center display - Target Letter and Hint Button
        targetLetter?.let {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-120).dp)
            ) {
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
                Spacer(modifier = Modifier.height(185.dp))
            }
        }

        // Previous Letter Button
        IconButton(
            onClick = onPreviousLetter,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 16.dp, y = (-50).dp)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous Letter",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        // Next Letter Button
        IconButton(
            onClick = onNextLetter,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-16).dp, y = (-50).dp)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next Random Letter",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        // Hint Image Display
        AnimatedVisibility(
            visible = showHint && currentHintDrawable != null,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300, delayMillis = 400)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            if (currentHintDrawable != null) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = currentHintDrawable),
                        contentDescription = "Hint for letter $targetLetter",
                        modifier = Modifier.fillMaxSize()
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
                        text = if (feedback.isCorrect) "Correct!" else "Try again!",
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