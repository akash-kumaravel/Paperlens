package com.example.ui.screens

import android.app.AlertDialog
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.OcrDocument
import com.example.viewmodel.OcrViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: OcrViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val documents by viewModel.documents.collectAsState()
    val typeFilter by viewModel.selectedDocTypeFilter.collectAsState()

    var showRenameDialog by remember { mutableStateOf<OcrDocument?>(null) }
    var renameValue by remember { mutableStateOf("") }

    var expandedMenuDoc by remember { mutableStateOf<OcrDocument?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Scanned Archives",
                        fontSize = 20.sp,
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Search Bar Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search document name or extracted words...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search filter icon") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search input")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Horizontal Filter Tabs: All, Images, PDF
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipTab(
                    label = "All",
                    selected = typeFilter == null,
                    onClick = { viewModel.updateDocTypeFilter(null) }
                )
                FilterChipTab(
                    label = "Camera",
                    selected = typeFilter == "CAMERA",
                    onClick = { viewModel.updateDocTypeFilter("CAMERA") }
                )
                FilterChipTab(
                    label = "Images",
                    selected = typeFilter == "IMAGE",
                    onClick = { viewModel.updateDocTypeFilter("IMAGE") }
                )
                FilterChipTab(
                    label = "PDFs",
                    selected = typeFilter == "PDF",
                    onClick = { viewModel.updateDocTypeFilter("PDF") }
                )
            }

            // Results lists
            if (documents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.0f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Empty scanning items history dashboard decoration illustrations",
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No Matching Scans found" else "Archives Empty",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.0f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(documents) { document ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1.0f)
                                        .clickable {
                                            viewModel.selectDocument(document)
                                            onNavigate("results")
                                        }
                                        .padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Custom visual indicators
                                    val icon = when (document.sourceType) {
                                        "PDF" -> Icons.Default.PictureAsPdf
                                        "IMAGE" -> Icons.Default.InsertPhoto
                                        else -> Icons.Default.Description
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = "Source Type Indicator icon",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column {
                                        Text(
                                            text = document.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 1
                                        )
                                        val textSnip = document.ocrText.take(40).replace("\n", " ") + "..."
                                        Text(
                                            text = textSnip,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1
                                        )
                                    }
                                }

                                // Interactive options trigger menu dot
                                Box {
                                    IconButton(onClick = { expandedMenuDoc = document }) {
                                        Icon(
                                            imageVector = Icons.Outlined.MoreVert,
                                            contentDescription = "More custom row actions trigger button"
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = expandedMenuDoc == document,
                                        onDismissRequest = { expandedMenuDoc = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Open document") },
                                            leadingIcon = { Icon(Icons.Default.Launch, contentDescription = "Open doc") },
                                            onClick = {
                                                expandedMenuDoc = null
                                                viewModel.selectDocument(document)
                                                onNavigate("results")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Rename file") },
                                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = "Rename doc") },
                                            onClick = {
                                                expandedMenuDoc = null
                                                renameValue = document.name
                                                showRenameDialog = document
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Share extracted text") },
                                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = "Share raw doc content text") },
                                            onClick = {
                                                expandedMenuDoc = null
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, document.ocrText)
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Share Scanned Text via:"))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete document", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = "Delete doc item", tint = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                expandedMenuDoc = null
                                                viewModel.deleteDocument(document)
                                                Toast.makeText(context, "Archive item deleted successfully", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Interactive Rename Dialog overlay
    if (showRenameDialog != null) {
        val documentToRename = showRenameDialog!!
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename Archived Document", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameValue.isNotBlank()) {
                            coroutineScope.launch {
                                val updated = documentToRename.copy(name = renameValue)
                                com.example.data.AppDatabase.getDatabase(context).ocrDocumentDao().updateDocument(updated)
                                Toast.makeText(context, "Successfully renamed document", Toast.LENGTH_SHORT).show()
                                showRenameDialog = null
                            }
                        } else {
                            Toast.makeText(context, "Title cannot be blank.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Rename", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChipTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            selectedLabelColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(10.dp)
    )
}
