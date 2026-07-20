package com.example.mymeetings.ui.permissions

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun PermissionSetupWizard(
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Key states
    var isNotificationGranted by remember { mutableStateOf(checkNotificationPermission(context)) }
    var isOverlayGranted by remember { mutableStateOf(checkOverlayPermission(context)) }
    var isBatteryOptimizationIgnored by remember { mutableStateOf(checkBatteryOptimization(context)) }
    var isExactAlarmGranted by remember { mutableStateOf(checkExactAlarmPermission(context)) }
    var isDndPolicyGranted by remember { mutableStateOf(checkDndPolicyPermission(context)) }

    // Launchers
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isNotificationGranted = granted
    }

    // Refresh states on resume/launch
    LaunchedEffect(Unit) {
        isNotificationGranted = checkNotificationPermission(context)
        isOverlayGranted = checkOverlayPermission(context)
        isBatteryOptimizationIgnored = checkBatteryOptimization(context)
        isExactAlarmGranted = checkExactAlarmPermission(context)
        isDndPolicyGranted = checkDndPolicyPermission(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Welcome to MyMeetings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "To guarantee you never miss a calendar invite, please complete the permission setup below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))

            // 1. Post Notifications (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionCard(
                    title = "Post Notifications",
                    description = "Required to show incoming meeting heads-up alerts and quick-actions.",
                    isGranted = isNotificationGranted,
                    onRequest = {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 2. Exact Alarms (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PermissionCard(
                    title = "Exact Alarms",
                    description = "Enables alarms to fire precisely when the meeting starts.",
                    isGranted = isExactAlarmGranted,
                    onRequest = {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 3. Draw Over Other Apps (Overlay)
            PermissionCard(
                title = "Draw Over Other Apps",
                description = "Required to display full-screen call-like alerts over your screen when meetings start.",
                isGranted = isOverlayGranted,
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 4. Disable Battery Optimizations
            PermissionCard(
                title = "Disable Battery Optimization",
                description = "Prevents Android from freezing background processes, ensuring meeting alarms fire reliably.",
                isGranted = isBatteryOptimizationIgnored,
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 5. Bypass Do Not Disturb (Notification Policy Access)
            PermissionCard(
                title = "Do Not Disturb Override",
                description = "Allows the app to play alarm sounds even when the phone is on Silent or Do Not Disturb.",
                isGranted = isDndPolicyGranted,
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        context.startActivity(intent)
                    }
                }
            )
        }

        // Action Footer
        Button(
            onClick = onSetupComplete,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Proceed to Application", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(16.dp))
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = Color(0xFF22C55E),
                    modifier = Modifier.size(32.dp)
                )
            } else {
                IconButton(onClick = onRequest) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Action Required",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun checkOverlayPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }
}

private fun checkBatteryOptimization(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true
    }
}

private fun checkExactAlarmPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.canScheduleExactAlarms()
    } else {
        true
    }
}

private fun checkDndPolicyPermission(context: Context): Boolean {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        nm.isNotificationPolicyAccessGranted
    } else {
        true
    }
}
