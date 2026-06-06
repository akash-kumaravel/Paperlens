package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.OcrViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: OcrViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val currentTheme by viewModel.appTheme.collectAsState()
    val currentEngine by viewModel.ocrEngineType.collectAsState()
    val currentLang by viewModel.ocrLanguage.collectAsState()
    val currentQuality by viewModel.recognitionQuality.collectAsState()

    var showEngineDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Scanner Configuration",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onNavigate("home") }) {
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
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            // Section 1: Visual Theme controls
            SettingsSectionHeader(title = "Appearance")
            SettingsRowItem(
                title = "App theme",
                subtitle = currentTheme,
                icon = Icons.Default.Brightness4,
                onClick = { showThemeDialog = true }
            )

            // Section 2: Local OCR Engine controls
            SettingsSectionHeader(title = "OCR Recognition Settings")
            SettingsRowItem(
                title = "Active Local Engine",
                subtitle = currentEngine,
                icon = Icons.Default.Memory,
                onClick = { showEngineDialog = true }
            )
            SettingsRowItem(
                title = "Recognition Quality",
                subtitle = currentQuality,
                icon = Icons.Default.HighQuality,
                onClick = {
                    val next = if (currentQuality == "High Quality") "Standard Quality" else "High Quality"
                    viewModel.recognitionQuality.value = next
                    Toast.makeText(context, "Scanning resolution updated to $next", Toast.LENGTH_SHORT).show()
                }
            )
            SettingsRowItem(
                title = "Language Model",
                subtitle = currentLang,
                icon = Icons.Default.Translate,
                onClick = {
                    val next = if (currentLang == "Latin / Multilingual") "Universal / All Character sets" else "Latin / Multilingual"
                    viewModel.ocrLanguage.value = next
                    Toast.makeText(context, "Language weights updated to $next", Toast.LENGTH_SHORT).show()
                }
            )

            // Section 3: Secure Local Storage metadata
            SettingsSectionHeader(title = "Storage & Assets Configuration")
            SettingsRowItem(
                title = "Physical Storage Location",
                subtitle = "Subdirectory: /Android/data/${context.packageName}/Files",
                icon = Icons.Default.Folder,
                arrowIcon = false,
                onClick = {}
            )
            SettingsRowItem(
                title = "Secure Local Sandbox",
                subtitle = "100% private. Files never leave your physical device.",
                icon = Icons.Default.Security,
                arrowIcon = false,
                onClick = {}
            )

            // Section 4: Regulatory declarations
            SettingsSectionHeader(title = "About scanner")
            SettingsRowItem(
                title = "Application Version",
                subtitle = "v1.0 (Production Stable Release)",
                icon = Icons.Default.Info,
                arrowIcon = false,
                onClick = {}
            )
            SettingsRowItem(
                title = "Privacy Policy & Statements",
                subtitle = "Files are protected locally. No trackers, absolute on-device anonymity.",
                icon = Icons.Default.VerifiedUser,
                arrowIcon = false,
                onClick = {
                    Toast.makeText(context, "Paper Lens: Privacy first. Zero collection.", Toast.LENGTH_LONG).show()
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Interactive Theme Custom Selection Dialog
    if (showThemeDialog) {
        val themes = listOf("Light", "Dark", "System Default")
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Select Appearance Theme", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    themes.forEach { themeName ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateTheme(themeName)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = themeName, fontSize = 15.sp)
                            if (currentTheme == themeName) {
                                Icon(Icons.Default.Done, contentDescription = "Active selection", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Interactive Engine Selection Dialog
    if (showEngineDialog) {
        val engines = listOf("Local Standard Engine", "PaddleOCR 3.x Mobile")
        AlertDialog(
            onDismissRequest = { showEngineDialog = false },
            title = { Text("Select Local Recognition Engine", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    engines.forEach { engineName ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateEngine(engineName)
                                    showEngineDialog = false
                                    Toast.makeText(context, "Engine switched to $engineName", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = engineName, fontSize = 15.sp)
                            if (currentEngine == engineName) {
                                Icon(Icons.Default.Done, contentDescription = "Active selection", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
fun SettingsRowItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    arrowIcon: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1.0f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (arrowIcon) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Expand parameter selections",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
