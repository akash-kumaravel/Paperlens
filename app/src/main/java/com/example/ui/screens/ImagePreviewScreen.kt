package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Matrix
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.OcrViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePreviewScreen(
    viewModel: OcrViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val currentBitmap by viewModel.activeBitmap.collectAsState()
    val original by viewModel.originalBitmap.collectAsState()
    
    var rotationValue by remember { mutableStateOf(0f) }
    var contrastSlider by remember { mutableStateOf(1.0f) } // 1.0f normal contrast, 1.5f high contrast text pop
    var activeModeTab by remember { mutableStateOf("Enhance") } // "Crop", "Rotate", "Enhance"

    // Help rotate bitmap programmatically
    fun applyRotation() {
        val target = currentBitmap ?: return
        val matrix = Matrix()
        matrix.postRotate(90f)
        val rotated = Bitmap.createBitmap(target, 0, 0, target.width, target.height, matrix, true)
        viewModel.activeBitmap.value = rotated
        rotationValue = (rotationValue + 90f) % 360f
    }

    // Help crop/resize bitmap programmatically
    fun applySimulatedCrop() {
        val target = currentBitmap ?: return
        // Crop out 5% padding around canvas for simple neat alignment layout
        val paddingX = (target.width * 0.05).toInt()
        val paddingY = (target.height * 0.05).toInt()
        val cropped = Bitmap.createBitmap(
            target,
            paddingX,
            paddingY,
            target.width - (paddingX * 2),
            target.height - (paddingY * 2)
        )
        viewModel.activeBitmap.value = cropped
        Toast.makeText(context, "Completed document border crop.", Toast.LENGTH_SHORT).show()
    }

    // Enhance text contrast dynamically
    fun applyContrastMultiplier() {
        val source = original ?: return
        val bmp = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint()
        val matrix = android.graphics.ColorMatrix(floatArrayOf(
            contrastSlider, 0f, 0f, 0f, 0f,
            0f, contrastSlider, 0f, 0f, 0f,
            0f, 0f, contrastSlider, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        
        // Re-apply current rotation if any
        if (rotationValue > 0f) {
            val rotMatrix = Matrix()
            rotMatrix.postRotate(rotationValue)
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, rotMatrix, true)
            viewModel.activeBitmap.value = rotated
        } else {
            viewModel.activeBitmap.value = bmp
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Document Tuning",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onNavigate("camera_scan") }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // Document preview bounding card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentBitmap != null) {
                        Image(
                            bitmap = currentBitmap!!.asImageBitmap(),
                            contentDescription = "Active scanning document file preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }

            // Tuning Control Panels
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                
                // Adjustment Tabs Selection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TabToggleButton(
                        title = "Crop",
                        icon = Icons.Outlined.Crop,
                        isActive = activeModeTab == "Crop",
                        onClick = { activeModeTab = "Crop" }
                    )
                    TabToggleButton(
                        title = "Rotate",
                        icon = Icons.Outlined.RotateRight,
                        isActive = activeModeTab == "Rotate",
                        onClick = { activeModeTab = "Rotate" }
                    )
                    TabToggleButton(
                        title = "Enhance",
                        icon = Icons.Default.AutoFixHigh,
                        isActive = activeModeTab == "Enhance",
                        onClick = { activeModeTab = "Enhance" }
                    )
                }

                // Selected Tab Parameter slider or control trigger
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (activeModeTab) {
                        "Crop" -> {
                            Button(
                                onClick = { applySimulatedCrop() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Crop, contentDescription = "Simulated Crop trigger")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Auto Detect Crop Borders")
                            }
                        }
                        "Rotate" -> {
                            Button(
                                onClick = { applyRotation() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.RotateRight, contentDescription = "Simulated rotation")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Rotate 90° Clockwise")
                            }
                        }
                        "Enhance" -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BrightnessMedium,
                                    contentDescription = "Enhance level slider",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Contrast",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = contrastSlider,
                                    onValueChange = {
                                        contrastSlider = it
                                        applyContrastMultiplier()
                                    },
                                    valueRange = 0.5f..2.0f,
                                    modifier = Modifier.weight(1.0f)
                                )
                                Text(
                                    text = String.format("%.1fx", contrastSlider),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(36.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Continue Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = { onNavigate("camera_scan") },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Retake")
                }
                
                Button(
                    onClick = {
                        val bitmap = currentBitmap
                        if (bitmap != null) {
                            val source = viewModel.activeScanSourceType.value
                            viewModel.startImageOcr(
                                bitmap = bitmap,
                                sourceType = source,
                                onComplete = { docId ->
                                    onNavigate("results")
                                },
                                onError = { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    onNavigate("home")
                                }
                            )
                            onNavigate("ocr_processing")
                        } else {
                            Toast.makeText(context, "No bitmap to scan.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = "Run OCR Scan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TabToggleButton(
    title: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
