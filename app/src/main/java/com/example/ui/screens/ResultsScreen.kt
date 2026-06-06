package com.example.ui.screens

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.example.data.AppDatabase
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.OcrViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    viewModel: OcrViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentDocText by viewModel.ocrResultText.collectAsState()
    val activeDocTitle by viewModel.documentTitle.collectAsState()
    val activeDocument by viewModel.activeDocument.collectAsState()

    var isEditingTitle by remember { mutableStateOf(false) }
    var tempTitle by remember { mutableStateOf(activeDocTitle) }

    // Search features inside OCR Text
    var showSearchOverlay by remember { mutableStateOf(false) }
    var searchInsideQuery by remember { mutableStateOf("") }

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun handleCopy() {
        val clip = ClipData.newPlainText("Paper Lens Result", currentDocText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun handleShareText() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, activeDocTitle)
            putExtra(Intent.EXTRA_TEXT, currentDocText)
        }
        context.startActivity(Intent.createChooser(intent, "Share Scanned Text via:"))
    }

    fun handleDocTitleChange() {
        if (tempTitle.isNotBlank()) {
            viewModel.updateActiveDocumentTitle(tempTitle)
            isEditingTitle = false
        } else {
            Toast.makeText(context, "Title cannot be empty.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isEditingTitle) {
                        TextField(
                            value = tempTitle,
                            onValueChange = { tempTitle = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { handleDocTitleChange() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                tempTitle = activeDocTitle
                                isEditingTitle = true
                            }
                        ) {
                            Text(
                                text = activeDocTitle,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1.0f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit title pencil icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onNavigate("home") }) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Navigate to home dashboard"
                        )
                    }
                },
                actions = {
                    // Top toolbar search and select buttons
                    IconButton(onClick = { showSearchOverlay = !showSearchOverlay }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search inside output text"
                        )
                    }
                    IconButton(onClick = {
                        handleCopy()
                        Toast.makeText(context, "All text selected and copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = "Select all text and copy"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // Mobile sticky bottom action bar (Grid format for neat single-screen touch accessibility)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomBarActionButton(
                            title = "DOCX",
                            icon = Icons.Default.Description,
                            onClick = {
                                viewModel.exportResultDocx(context, activeDocTitle, currentDocText,
                                    onComplete = { file ->
                                        viewModel.shareFile(context, file, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                                        Toast.makeText(context, "Exported DOCX successfully!", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, "DOCX compilation error: $err", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        )

                        BottomBarActionButton(
                            title = "PDF",
                            icon = Icons.Default.PictureAsPdf,
                            onClick = {
                                viewModel.exportResultPdf(context, activeDocTitle, currentDocText,
                                    onComplete = { file ->
                                        viewModel.shareFile(context, file, "application/pdf")
                                        Toast.makeText(context, "Exported Searchable PDF successfully!", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, "PDF compilation error: $err", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        )

                        BottomBarActionButton(
                            title = "Share",
                            icon = Icons.Default.Share,
                            onClick = { handleShareText() }
                        )

                        BottomBarActionButton(
                            title = "Copy",
                            icon = Icons.Default.ContentCopy,
                            onClick = { handleCopy() }
                        )

                        BottomBarActionButton(
                            title = "Delete",
                            icon = Icons.Outlined.Delete,
                            tint = MaterialTheme.colorScheme.error,
                            onClick = {
                                activeDocument?.let { doc ->
                                    viewModel.deleteDocument(doc)
                                    Toast.makeText(context, "Document deleted", Toast.LENGTH_SHORT).show()
                                    onNavigate("home")
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            
            // Search Query overlay
            AnimatedVisibility(
                visible = showSearchOverlay,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val matchesCount = remember(searchInsideQuery, currentDocText) {
                    if (searchInsideQuery.isEmpty()) 0 else {
                        var count = 0
                        var idx = currentDocText.indexOf(searchInsideQuery, ignoreCase = true)
                        while (idx != -1) {
                            count++
                            idx = currentDocText.indexOf(searchInsideQuery, idx + searchInsideQuery.length, ignoreCase = true)
                        }
                        count
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = searchInsideQuery,
                            onValueChange = { searchInsideQuery = it },
                            placeholder = { Text("Find in text...", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1.0f),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search Icon in search bar",
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )

                        if (searchInsideQuery.isNotEmpty()) {
                            val matchHintText = if (matchesCount == 1) "1 match" else "$matchesCount matches"
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (matchesCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text(
                                    text = matchHintText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (matchesCount > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        IconButton(onClick = {
                            searchInsideQuery = ""
                            showSearchOverlay = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search finder")
                        }
                    }
                }
            }

            // Undo / Redo Toolbar Panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = viewModel.canUndo()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Undo,
                            contentDescription = "Undo button action text edit",
                            tint = if (viewModel.canUndo()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = viewModel.canRedo()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Redo,
                            contentDescription = "Redo button action text edit",
                            tint = if (viewModel.canRedo()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
                
                // Status local indicators
                Text(
                    text = "Saved Automatically",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                )
            }

            // Editable rich text content box
            OutlinedTextField(
                value = currentDocText,
                onValueChange = { viewModel.setOcrResultText(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                visualTransformation = remember(searchInsideQuery) {
                    SearchHighlightTransformation(searchInsideQuery, Color(0xFFFFEB3B))
                }
            )

            // Save confirmation action triggers
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    // Update document content manually in local cache DB
                    activeDocument?.let { currentDoc ->
                        val text = currentDocText
                        val title = activeDocTitle
                        coroutineScope.launch {
                            val updated = currentDoc.copy(name = title, ocrText = text)
                            AppDatabase.getDatabase(context).ocrDocumentDao().updateDocument(updated)
                            Toast.makeText(context, "Document Saved", Toast.LENGTH_SHORT).show()
                            onNavigate("home")
                        }
                    } ?: onNavigate("home")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = "Save and finish button trigger")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save & Close Scanner", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BottomBarActionButton(
    title: String,
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint
        )
    }
}

class SearchHighlightTransformation(
    private val query: String,
    private val highlightColor: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (query.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val builder = AnnotatedString.Builder(text)
        val textString = text.text
        var index = textString.indexOf(query, ignoreCase = true)
        while (index != -1) {
            builder.addStyle(
                style = SpanStyle(
                    background = highlightColor,
                    color = Color.Black
                ),
                start = index,
                end = index + query.length
            )
            index = textString.indexOf(query, index + query.length, ignoreCase = true)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
