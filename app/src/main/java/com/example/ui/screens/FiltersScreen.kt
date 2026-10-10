package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.FilterType
import com.example.ui.Screen
import com.example.ui.ScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FilterOption(val type: FilterType, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersScreen(
    viewModel: ScanViewModel,
    pageId: Long,
    croppedBitmap: Bitmap?,
    documentId: Long
) {
    val coroutineScope = rememberCoroutineScope()
    var baseCroppedBitmap by remember { mutableStateOf(croppedBitmap ?: viewModel.activeCroppedBitmap) }

    LaunchedEffect(pageId) {
        if (baseCroppedBitmap == null) {
            val page = viewModel.repository.getPageById(pageId)
            if (page != null) {
                baseCroppedBitmap = viewModel.fileManager.loadBitmap(page.croppedPath)
                viewModel.activeCroppedBitmap = baseCroppedBitmap
            }
        }
    }

    if (baseCroppedBitmap == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val sourceBmp = baseCroppedBitmap!!

    var selectedFilter by remember { mutableStateOf(FilterType.MAGIC_COLOR) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var sharpness by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableIntStateOf(0) }

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessingPreview by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Live preview generator on downscaled copy
    val updatePreview: () -> Unit = {
        coroutineScope.launch {
            isProcessingPreview = true
            val smallBmp = withContext(Dispatchers.Default) {
                val scale = 600f / maxOf(sourceBmp.width, sourceBmp.height)
                if (scale < 1.0f) {
                    Bitmap.createScaledBitmap(
                        sourceBmp,
                        (sourceBmp.width * scale).toInt(),
                        (sourceBmp.height * scale).toInt(),
                        true
                    )
                } else {
                    sourceBmp
                }
            }

            val processed = viewModel.filters.applyFilter(
                source = smallBmp,
                filterType = selectedFilter,
                brightness = brightness,
                contrast = contrast,
                sharpness = sharpness,
                rotation = rotation
            )
            previewBitmap = processed
            isProcessingPreview = false
        }
    }

    LaunchedEffect(selectedFilter, brightness, contrast, sharpness, rotation) {
        updatePreview()
    }

    val filterOptions = listOf(
        FilterOption(FilterType.ORIGINAL, "Original"),
        FilterOption(FilterType.MAGIC_COLOR, "Magic Color"),
        FilterOption(FilterType.BLACK_WHITE, "B & W"),
        FilterOption(FilterType.GRAYSCALE, "Grayscale"),
        FilterOption(FilterType.LIGHTEN, "Lighten")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Filters & Adjust", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Rotate Left (90° CCW)
                    IconButton(onClick = { rotation = (rotation - 90 + 360) % 360 }) {
                        Icon(Icons.Default.RotateLeft, contentDescription = "Rotate Left")
                    }
                    // Rotate Right (90° CW)
                    IconButton(onClick = { rotation = (rotation + 90) % 360 }) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate Right")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    // Filter Chips Row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(filterOptions) { opt ->
                            FilterChip(
                                selected = selectedFilter == opt.type,
                                onClick = { selectedFilter = opt.type },
                                label = { Text(opt.label) },
                                leadingIcon = {
                                    if (selectedFilter == opt.type) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Sliders Accordion: Brightness, Contrast, Sharpness
                    var showSliders by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showSliders = !showSliders }) {
                            Icon(
                                if (showSliders) Icons.Default.ExpandLess else Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (showSliders) "Hide Adjustments" else "Fine Tune (Sliders)")
                        }

                        Button(
                            onClick = {
                                isSaving = true
                                viewModel.saveFilters(
                                    pageId = pageId,
                                    filterType = selectedFilter,
                                    brightness = brightness,
                                    contrast = contrast,
                                    sharpness = sharpness,
                                    rotation = rotation
                                ) {
                                    isSaving = false
                                    viewModel.navigateTo(Screen.PageGrid(documentId))
                                }
                            },
                            enabled = !isSaving,
                            modifier = Modifier.testTag("save_filters_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Done")
                        }
                    }

                    if (showSliders) {
                        Spacer(Modifier.height(8.dp))
                        // Brightness slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BrightnessMedium, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Brightness", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(70.dp))
                            Slider(
                                value = brightness,
                                onValueChange = { brightness = it },
                                valueRange = -60f..60f,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Contrast slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Contrast, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Contrast", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(70.dp))
                            Slider(
                                value = contrast,
                                onValueChange = { contrast = it },
                                valueRange = 0.6f..1.8f,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Sharpness slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Details, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sharpness", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(70.dp))
                            Slider(
                                value = sharpness,
                                onValueChange = { sharpness = it },
                                valueRange = 0f..1.5f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F172A))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val bmp = previewBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Filter Preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            if (isProcessingPreview) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Rendering...", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
    }
}
