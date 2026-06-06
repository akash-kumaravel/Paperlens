package com.example.ui.screens

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.OcrViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfUploadScreen(
    viewModel: OcrViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val metadata by viewModel.activePdfMetadata.collectAsState()
    val selectedPages by viewModel.activePdfPagesToOcr.collectAsState()

    var rangeSelectionMode by remember { mutableStateOf("ALL") } // "ALL", "CUSTOM"
    val customPagesChecked = remember { mutableStateListOf<Int>() }

    // Synchronize custom list state with active pages
    LaunchedEffect(metadata) {
        val total = metadata?.pageCount ?: 0
        customPagesChecked.clear()
        customPagesChecked.addAll(0 until total)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PDF OCR Setup",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
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
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            
            // Metadata header card
            Column(modifier = Modifier.weight(1f)) {
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = "PDF document file icon",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = metadata?.fileName ?: "document.pdf",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1
                            )
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Pages: ${metadata?.pageCount ?: 0}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Size: " + (metadata?.fileSize?.let { Formatter.formatFileSize(context, it) } ?: "0 B"),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Page Extraction Range",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Options: Entire pdf or specific pages
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { rangeSelectionMode = "ALL" },
                        shape = RoundedCornerShape(12.dp),
                        border = if (rangeSelectionMode == "ALL") androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        colors = CardDefaults.cardColors(
                            containerColor = if (rangeSelectionMode == "ALL") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.LibraryBooks, contentDescription = "Scan entire workbook", tint = if (rangeSelectionMode == "ALL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("All Pages", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { rangeSelectionMode = "CUSTOM" },
                        shape = RoundedCornerShape(12.dp),
                        border = if (rangeSelectionMode == "CUSTOM") androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        colors = CardDefaults.cardColors(
                            containerColor = if (rangeSelectionMode == "CUSTOM") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FilterList, contentDescription = "Scan specific numbers", tint = if (rangeSelectionMode == "CUSTOM") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Selected Pages", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Custom Selection details
                if (rangeSelectionMode == "CUSTOM") {
                    Text(
                        text = "Select Pages to Extract:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    val totalPages = metadata?.pageCount ?: 0
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.0f)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items((0 until totalPages).toList()) { pageNo ->
                            val isChecked = customPagesChecked.contains(pageNo)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) {
                                            if (customPagesChecked.size > 1) customPagesChecked.remove(pageNo)
                                            else Toast.makeText(context, "At least 1 page must be processed.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            customPagesChecked.add(pageNo)
                                        }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Page ${pageNo + 1}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { _ ->
                                        if (isChecked) {
                                            if (customPagesChecked.size > 1) customPagesChecked.remove(pageNo)
                                        } else {
                                            customPagesChecked.add(pageNo)
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.0f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Everything selected icon indicator",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Entire PDF sequence will be parsed.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Bottom trigger Button
            Button(
                onClick = {
                    val finalPages = if (rangeSelectionMode == "ALL") {
                        val total = metadata?.pageCount ?: 0
                        (0 until total).toList()
                    } else {
                        customPagesChecked.sorted().toList()
                    }
                    
                    if (finalPages.isEmpty()) {
                        Toast.makeText(context, "Please configure at least one active page.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    viewModel.activePdfPagesToOcr.value = finalPages
                    viewModel.startPdfOcr(
                        onComplete = { docId ->
                            onNavigate("results")
                        },
                        onError = { errorText ->
                            Toast.makeText(context, errorText, Toast.LENGTH_LONG).show()
                            onNavigate("home")
                        }
                    )
                    onNavigate("ocr_processing")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = "Ocr scan icon pdf trigger"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Extract PDF Text",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
