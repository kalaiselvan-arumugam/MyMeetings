package com.example.mymeetings.ui.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mymeetings.data.parser.IcsParser
import com.example.mymeetings.domain.model.Meeting
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (Long) -> Unit,
    viewModel: ScannerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scannerState by viewModel.scannerState.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Camera permission is required to scan QR codes.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Camera control states
    var camera: Camera? by remember { mutableStateOf(null) }
    var isTorchOn by remember { mutableStateOf(false) }

    // Gallery QR code image picker launcher
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val image = InputImage.fromFilePath(context, uri)
                val scanner = BarcodeScanning.getClient()
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val qrContent = barcodes.firstOrNull()?.rawValue
                        if (!qrContent.isNullOrBlank()) {
                            val parsed = IcsParser.parseIcs(qrContent)
                            if (parsed != null) {
                                viewModel.processScannedMeeting(parsed) { id ->
                                    onNavigateToDetails(id)
                                }
                            } else {
                                viewModel.setInvalidQrError()
                            }
                        } else {
                            Toast.makeText(context, "No QR code found in selected image.", Toast.LENGTH_LONG).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Failed to scan image.", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // File picker launcher fallback
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val icsContent = readTextFromUri(context, uri)
            val parsed = IcsParser.parseIcs(icsContent)
            if (parsed != null) {
                viewModel.processScannedMeeting(parsed) { id ->
                    onNavigateToDetails(id)
                }
            } else {
                viewModel.setInvalidQrError()
            }
        }
    }

    // Handle back button while resetting state
    val handleBack: () -> Unit = {
        viewModel.resetState()
        onNavigateBack()
    }

    androidx.activity.compose.BackHandler {
        handleBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().apply {
                                setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            val analyzerExecutor = Executors.newSingleThreadExecutor()
                            imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    val scanner = BarcodeScanning.getClient()

                                    scanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            val barcode = barcodes.firstOrNull()
                                            val rawValue = barcode?.rawValue
                                            if (rawValue != null) {
                                                // Run on UI Thread
                                                val parsed = IcsParser.parseQrPayload(rawValue)
                                                if (parsed != null) {
                                                    // Stop analyzer temporarily
                                                    imageAnalysis.clearAnalyzer()
                                                    viewModel.processScannedMeeting(parsed) { id ->
                                                        onNavigateToDetails(id)
                                                    }
                                                }
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            try {
                                cameraProvider.unbindAll()
                                camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (exc: Exception) {
                                Toast.makeText(ctx, "Failed to initialize camera preview.", Toast.LENGTH_SHORT).show()
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Infinite transitions for scanning scanner animation effects
                val infiniteTransition = rememberInfiniteTransition(label = "scan")
                
                // Pulsing target border visibility alpha
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )

                // Laser horizontal line vertical position sweeping progress
                val laserProgress by infiniteTransition.animateFloat(
                    initialValue = 0.05f,
                    targetValue = 0.95f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2500, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "laser"
                )

                // Semi-transparent black background mask with 100% transparent cutout scan target box
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                ) {
                    // 1. Draw the dark screen overlay
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val windowSize = 260.dp.toPx()
                        val left = (width - windowSize) / 2
                        val top = (height - windowSize) / 2

                        // Fill overall screen black with 70% opacity
                        drawRect(color = Color.Black.copy(alpha = 0.7f))

                        // Draw clear rounded cutout window
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = Offset(left, top),
                            size = Size(windowSize, windowSize),
                            cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                            blendMode = BlendMode.Clear
                        )
                    }

                    // 2. Draw pulsing corner borders and moving red laser line
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val windowSize = 260.dp.toPx()
                        val left = (width - windowSize) / 2
                        val top = (height - windowSize) / 2
                        val right = left + windowSize
                        val bottom = top + windowSize
                        val cornerLen = 24.dp.toPx()
                        val stroke = 3.dp.toPx()

                        // Use our primary theme color for corner highlights
                        val accentColor = Color(0xFF6200EE).copy(alpha = pulseAlpha)

                        // Top Left Corner
                        drawLine(accentColor, Offset(left, top), Offset(left + cornerLen, top), stroke)
                        drawLine(accentColor, Offset(left, top), Offset(left, top + cornerLen), stroke)

                        // Top Right Corner
                        drawLine(accentColor, Offset(right, top), Offset(right - cornerLen, top), stroke)
                        drawLine(accentColor, Offset(right, top), Offset(right, top + cornerLen), stroke)

                        // Bottom Left Corner
                        drawLine(accentColor, Offset(left, bottom), Offset(left + cornerLen, bottom), stroke)
                        drawLine(accentColor, Offset(left, bottom), Offset(left, bottom - cornerLen), stroke)

                        // Bottom Right Corner
                        drawLine(accentColor, Offset(right, bottom), Offset(right - cornerLen, bottom), stroke)
                        drawLine(accentColor, Offset(right, bottom), Offset(right, bottom - cornerLen), stroke)

                        // Draw horizontal red laser scanning sweep line
                        val laserY = top + laserProgress * windowSize
                        drawLine(
                            color = Color.Red,
                            start = Offset(left + 8.dp.toPx(), laserY),
                            end = Offset(right - 8.dp.toPx(), laserY),
                            strokeWidth = 3.dp.toPx()
                        )
                    }
                }

                // Bottom Side-by-Side Control Buttons Row (Mockup aligned)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 64.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val flashAvailable = camera?.cameraInfo?.hasFlashUnit() == true
                    
                    // Left Control Button: Torch Toggle
                    Button(
                        onClick = {
                            if (flashAvailable) {
                                isTorchOn = !isTorchOn
                                camera?.cameraControl?.enableTorch(isTorchOn)
                            } else {
                                Toast.makeText(context, "Flashlight unavailable.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTorchOn) MaterialTheme.colorScheme.primary else Color.DarkGray.copy(alpha = 0.8f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isTorchOn) "Torch ON" else "Torch OFF", maxLines = 1)
                    }

                    // Right Control Button: Upload QR from Gallery
                    Button(
                        onClick = { galleryPickerLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.DarkGray.copy(alpha = 0.8f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload QR", maxLines = 1)
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Camera permission is required.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // UI states overlay: Processing, Success, Dialogs, etc.
            when (val state = scannerState) {
                ScannerUiState.Processing -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is ScannerUiState.DuplicateFound -> {
                    DuplicateResolverDialog(
                        scanned = state.scannedMeeting,
                        existing = state.existingMeeting,
                        onOverwrite = {
                            viewModel.overwriteExisting(state.scannedMeeting, state.existingMeeting.id) { id ->
                                onNavigateToDetails(id)
                            }
                        },
                        onKeepBoth = {
                            viewModel.keepBoth(state.scannedMeeting) { id ->
                                onNavigateToDetails(id)
                            }
                        },
                        onCancel = {
                            viewModel.resetState()
                            // Re-triggers preview binding by resetting state
                        }
                    )
                }
                is ScannerUiState.Error -> {
                    AlertDialog(
                        onDismissRequest = { viewModel.resetState() },
                        title = { Text("Import Error") },
                        text = { Text(state.message) },
                        confirmButton = {
                            TextButton(onClick = { viewModel.resetState() }) {
                                Text("Try Again")
                            }
                        }
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
fun DuplicateResolverDialog(
    scanned: Meeting,
    existing: Meeting,
    onOverwrite: () -> Unit,
    onKeepBoth: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Duplicate Meeting Detected") },
        text = {
            Text("A meeting with the title \"${existing.title}\" is already imported in your calendar.\n\nWhat would you like to do?")
        },
        confirmButton = {
            TextButton(onClick = onOverwrite) {
                Text("Replace")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onKeepBoth) {
                    Text("Keep Both")
                }
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    )
}

/**
 * Utility to read file stream text contents from Uri safely.
 */
private fun readTextFromUri(context: Context, uri: Uri): String {
    val stringBuilder = StringBuilder()
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line).append("\n")
                }
            }
        }
    } catch (e: Exception) {
        // Log or handle
    }
    return stringBuilder.toString()
}
