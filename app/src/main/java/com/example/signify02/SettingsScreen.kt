package com.example.signify02

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.signify02.ui.Yrsa

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onDismiss: () -> Unit,
    showLandmarks: Boolean,
    onToggleShowLandmarks: (Boolean) -> Unit,
    isAutoAppendEnabled: Boolean,
    onToggleAutoAppend: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontFamily = Yrsa) },
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
                .padding(16.dp)
        ) {
            SettingSwitch(
                icon = Icons.Default.Visibility,
                title = "Show Hand Landmarks",
                description = "Display the skeleton overlay on detected hand.",
                isChecked = showLandmarks,
                onCheckedChange = onToggleShowLandmarks
            )
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            SettingSwitch(
                icon = Icons.Default.TouchApp,
                title = "Enable Auto-Append",
                description = "Automatically add a sign to the history after holding it for 0.5 second .",
                isChecked = isAutoAppendEnabled,
                onCheckedChange = onToggleAutoAppend
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    icon: ImageVector,
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Yrsa))
            Text(text = description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = isChecked, onCheckedChange = onCheckedChange)
    }
}