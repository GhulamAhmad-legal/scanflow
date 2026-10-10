package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watermark.WatermarkConfig
import com.example.watermark.WatermarkLayout
import com.example.watermark.WatermarkType

@Composable
fun WatermarkModal(
    initialConfig: WatermarkConfig,
    onDismiss: () -> Unit,
    onSave: (WatermarkConfig) -> Unit
) {
    var enabled by remember { mutableStateOf(initialConfig.enabled) }
    var type by remember { mutableStateOf(initialConfig.type) }
    var text by remember { mutableStateOf(initialConfig.text) }
    var opacity by remember { mutableFloatStateOf(initialConfig.opacity) }
    var rotation by remember { mutableFloatStateOf(initialConfig.rotationDegrees) }
    var layout by remember { mutableStateOf(initialConfig.layout) }
    var colorHex by remember { mutableStateOf(initialConfig.colorHex) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            imageUri = uri
            type = WatermarkType.IMAGE
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BrandingWatermark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Watermark Settings")
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // Enable toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Apply Watermark", fontWeight = FontWeight.Bold)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                if (enabled) {
                    Spacer(Modifier.height(8.dp))

                    // Text vs Image toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = type == WatermarkType.TEXT,
                            onClick = { type = WatermarkType.TEXT },
                            label = { Text("Text") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = type == WatermarkType.IMAGE,
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            label = { Text(if (imageUri != null) "Logo Selected" else "Pick Logo") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    if (type == WatermarkType.TEXT) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Watermark Text") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Layout: Single vs Tiled
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = layout == WatermarkLayout.SINGLE_CENTER,
                            onClick = { layout = WatermarkLayout.SINGLE_CENTER },
                            label = { Text("Single Center") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = layout == WatermarkLayout.TILED,
                            onClick = { layout = WatermarkLayout.TILED },
                            label = { Text("Tiled Diagonal") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Opacity Slider
                    Text("Opacity: ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        valueRange = 0.1f..0.8f
                    )

                    // Rotation Slider
                    Text("Rotation: ${rotation.toInt()}°", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = rotation,
                        onValueChange = { rotation = it },
                        valueRange = -90f..90f
                    )

                    // Live Mini Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray.copy(alpha = opacity),
                            modifier = Modifier.rotate(rotation)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        WatermarkConfig(
                            enabled = enabled,
                            type = type,
                            text = text,
                            imagePath = imageUri?.toString(),
                            opacity = opacity,
                            rotationDegrees = rotation,
                            layout = layout,
                            colorHex = colorHex
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
