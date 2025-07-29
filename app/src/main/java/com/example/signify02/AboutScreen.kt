package com.example.signify02

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.signify02.ui.Yrsa

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onDismiss: () -> Unit
){
    val context = LocalContext.current
    val versionName = remember{
        try{
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) { "N/A"}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Signify", fontFamily = Yrsa) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ){ padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ){
            Spacer(modifier = Modifier.height(24.dp))
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "App Icon",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Signify",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Version: $versionName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Signify is a real-time American Sign Language (ASL) alphabet detection app designed to bridge communication gaps.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))
            InfoSection(
                icon = Icons.Filled.Code,
                title = "Developers",
                content = "Castillon, Karl Emerson\nManghi, Darius-Xavier\nMojagan, Clark Adrian\nVilla, John Jacob T."
            )
            Spacer(modifier = Modifier.height(60.dp))
            InfoSection(
                icon = Icons.Filled.LaptopMac,
                title = "Technology",
                content = "App: Kotlin & Jetpack Compose\nAI Model Training: Python & TensorFlow\nAI Models: TensorFlow Lite & MediaPipe"
            )
        }
    }
}

@Composable
private fun InfoSection(icon: ImageVector, title: String, content: String){
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Row(verticalAlignment = Alignment.CenterVertically){
            Icon(
                imageVector = icon,
                contentDescription = "$title Icon",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}