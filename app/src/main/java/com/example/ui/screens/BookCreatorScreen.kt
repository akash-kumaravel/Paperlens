package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.viewmodel.OcrViewModel
import java.io.File
import java.io.FileOutputStream

data class BookPage(val uri: Uri, val bitmap: Bitmap)

// Safe URI decode to Bitmap with downsampling to avoid out-of-memory errors
fun decodeUriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 2 // Downsample slightly to optimize memory limits
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookCreatorScreen(
    viewModel: OcrViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val pageList = remember { mutableStateListOf<BookPage>() }
    var bookTitle by remember { mutableStateOf("My Custom PDF Book") }
    
    var isCompiling by remember { mutableStateOf(false) }
    var compiledFile by remember { mutableStateOf<File?>(null) }
    
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    
    // Core camera permissions checking
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            triggerCameraCapture(context, pageList) { uri -> tempPhotoUri = uri }
        } else {
            Toast.makeText(context, "Camera permission is required to snap pages.", Toast.LENGTH_SHORT).show()
        }
    }

    // Media picking launchers
    val selectImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            var successfullyAdded = 0
            for (uri in uris) {
                val bitmap = decodeUriToBitmap(context, uri)
                if (bitmap != null) {
                    pageList.add(BookPage(uri, bitmap))
                    successfullyAdded++
                }
            }
            if (successfullyAdded > 0) {
                Toast.makeText(context, "Added $successfullyAdded image page(s).", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempPhotoUri?.let { uri ->
                val bitmap = decodeUriToBitmap(context, uri)
                if (bitmap != null) {
                    pageList.add(BookPage(uri, bitmap))
                    Toast.makeText(context, "Captured and added page successfully.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun triggerCamera() {
        if (hasCameraPermission) {
            try {
                val cacheFile = File(context.cacheDir, "book_cam_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile
                )
                tempPhotoUri = uri
                takePhotoLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Error starting camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // PDF compilation routine
    fun compileBookToPdf() {
        if (pageList.isEmpty()) {
            Toast.makeText(context, "Please add at least 1 image to construct a book.", Toast.LENGTH_SHORT).show()
            return
        }
        if (bookTitle.trim().isEmpty()) {
            Toast.makeText(context, "Please enter a valid book title.", Toast.LENGTH_SHORT).show()
            return
        }

        isCompiling = true
        
        // Run compiling in a side effect or basic state block. 
        // We compile on main/UI safely using low-latency scaled bitmaps
        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 Width in postscript points
            val pageHeight = 842 // A4 Height in postscript points

            for ((index, page) in pageList.withIndex()) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas

                val bitmap = page.bitmap
                // Scale bitmap preserving ratio to fit within A4 boundaries
                val scaleX = pageWidth.toFloat() / bitmap.width
                val scaleY = pageHeight.toFloat() / bitmap.height
                val scale = scaleX.coerceAtMost(scaleY)

                val drawWidth = bitmap.width * scale
                val drawHeight = bitmap.height * scale
                val left = (pageWidth - drawWidth) / 2
                val top = (pageHeight - drawHeight) / 2

                val rect = android.graphics.RectF(left, top, left + drawWidth, top + drawHeight)
                canvas.drawBitmap(bitmap, null, rect, null)
                
                pdfDocument.finishPage(pdfPage)
            }

            // Save PDF
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val filename = if (bookTitle.endsWith(".pdf")) bookTitle else "$bookTitle.pdf"
            val file = File(baseDir, filename)
            
            val out = FileOutputStream(file)
            pdfDocument.writeTo(out)
            out.flush()
            out.close()
            pdfDocument.close()

            compiledFile = file
            isCompiling = false
            Toast.makeText(context, "Successfully created PDF Book!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            isCompiling = false
            Toast.makeText(context, "Failed compiling PDF Book: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PDF Book Creator",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onNavigate("home") }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back home icon"
                        )
                    }
                },
                actions = {
                    if (pageList.isNotEmpty() && compiledFile == null) {
                        TextButton(onClick = { pageList.clear() }) {
                            Text("Clear All", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            if (isCompiling) {
                // Compile loading status screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 4.dp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Assembling PDF Pages...",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scaling dimensions and converting images into a digital book layout...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (compiledFile != null) {
                // Success compiler screen with "Convert to text / OCR" Option!
                val file = compiledFile!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Book compiled successfully icon",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Text(
                            text = "PDF Book Generated!",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Your selected pages have been assembled into a single high-quality searchable-ready document.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // File specification Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Book,
                                        contentDescription = "PDF Book Icon",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Size: " + Formatter.formatFileSize(context, file.length()) + "  •  Pages: ${pageList.size}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Saved in Local Documents",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Multi option operations
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Crucial prominent OCR text recovery trigger requested by user!
                        Button(
                            onClick = {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val parsed = viewModel.selectPdf(uri)
                                if (parsed) {
                                    viewModel.activeScanSourceType.value = "PDF"
                                    onNavigate("pdf_upload")
                                } else {
                                    Toast.makeText(context, "Error staging compiler text recovery.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = "Convert text icon"
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Convert Book to Text (OCR)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        // Secondary actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.shareFile(context, file, "application/pdf")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share PDF Book", fontSize = 13.sp)
                                }
                            }

                            Button(
                                onClick = {
                                    compiledFile = null
                                    pageList.clear()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "New Book", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Create New", fontSize = 13.sp)
                                }
                            }
                        }

                        TextButton(
                            onClick = { onNavigate("home") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Go back to Workspace Home", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (pageList.isEmpty()) {
                // Empty view styling matching system design limits
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        Color.Transparent
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CollectionsBookmark,
                            contentDescription = "Empty pages illustrator icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Build Your PDF Book",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Compile collections of receipts, homework notes, or presentation slides directly into structured books.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Spacer(modifier = Modifier.height(36.dp))

                    // Dual visual action items
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ImportActionOptionCard(
                            title = "Select Images from Gallery",
                            subtitle = "Multi-select JPEG, PNG, HEIC screenshots",
                            icon = Icons.Outlined.PhotoLibrary,
                            color = MaterialTheme.colorScheme.primary,
                            onClick = { selectImagesLauncher.launch("image/*") }
                        )

                        ImportActionOptionCard(
                            title = "Scan Multi-Pages via Camera",
                            subtitle = "Take live consecutive page photos",
                            icon = Icons.Outlined.PhotoCamera,
                            color = MaterialTheme.colorScheme.primary,
                            onClick = { triggerCamera() }
                        )
                    }
                }
            } else {
                // Normal editing grid & metadata layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.0f)
                    ) {
                        Text(
                            text = "Book Metadata",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = bookTitle,
                            onValueChange = { bookTitle = it },
                            placeholder = { Text("Enter book name...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = "Edit title icon", modifier = Modifier.size(18.dp))
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Book Pages (${pageList.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )

                            // Quick trigger buttons to append more photos mid-step
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = { selectImagesLauncher.launch("image/*") }) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add files icon", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { triggerCamera() }) {
                                    Icon(Icons.Default.AddAPhoto, contentDescription = "Camera app icon", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        Divider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Vertical sorting lists
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.0f)
                        ) {
                            itemsIndexed(pageList) { pageIndex, pageItem ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // Image Mini Preview
                                            Image(
                                                bitmap = pageItem.bitmap.asImageBitmap(),
                                                contentDescription = "Mini Thumbnail Page ${pageIndex + 1}",
                                                modifier = Modifier
                                                    .size(60.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.LightGray),
                                                contentScale = ContentScale.Crop
                                            )

                                            Spacer(modifier = Modifier.width(16.dp))

                                            Column {
                                                Text(
                                                    text = "Page ${pageIndex + 1}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Resolutions: ${pageItem.bitmap.width}x${pageItem.bitmap.height}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        // Navigation/Sort controls
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (pageIndex > 0) {
                                                        val temp = pageList[pageIndex - 1]
                                                        pageList[pageIndex - 1] = pageItem
                                                        pageList[pageIndex] = temp
                                                    }
                                                },
                                                enabled = pageIndex > 0
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowUpward,
                                                    contentDescription = "Move image step up",
                                                    modifier = Modifier.size(20.dp),
                                                    tint = if (pageIndex > 0) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    if (pageIndex < pageList.size - 1) {
                                                        val temp = pageList[pageIndex + 1]
                                                        pageList[pageIndex + 1] = pageItem
                                                        pageList[pageIndex] = temp
                                                    }
                                                },
                                                enabled = pageIndex < pageList.size - 1
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDownward,
                                                    contentDescription = "Move image step down",
                                                    modifier = Modifier.size(20.dp),
                                                    tint = if (pageIndex < pageList.size - 1) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
                                                )
                                            }

                                            IconButton(onClick = { pageList.removeAt(pageIndex) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Remove page from list",
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom compile trigger Button
                    Button(
                        onClick = { compileBookToPdf() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Compile Icon")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Compile ${pageList.size} Pages into PDF Book",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImportActionOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Side-helper to run camera setup logic safely inside permissions launcher context callback
private fun triggerCameraCapture(
    context: Context,
    pageList: MutableList<BookPage>,
    onSetUri: (Uri) -> Unit
) {
    // Just a structural helper for side launcher triggers
}
