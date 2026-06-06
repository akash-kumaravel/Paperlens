package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: androidx.compose.ui.graphics.Color
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val pages = listOf(
        OnboardingPage(
            title = "Scan Images Instantly",
            description = "Aim, point and capture. Our advanced OCR engine detects documents, crops edges instantly, and extracts text locally in milliseconds.",
            icon = Icons.Default.DocumentScanner,
            accentColor = MaterialTheme.colorScheme.primary
        ),
        OnboardingPage(
            title = "Extract Text From PDFs",
            description = "Need to compile books, assignments or contracts? Upload full multi-page PDFs to run text recognition page-by-page entirely on-device.",
            icon = Icons.Default.PictureAsPdf,
            accentColor = MaterialTheme.colorScheme.primary
        ),
        OnboardingPage(
            title = "Export To Word And PDF",
            description = "Instantly generate searchable PDF archives and editable Microsoft Word DOCX formats. No OCR server, no cloud data leaks, absolutely 100% private.",
            icon = Icons.Default.Description,
            accentColor = MaterialTheme.colorScheme.primary
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })

    fun completeOnboarding() {
        val prefs = context.getSharedPreferences("bg_ocr_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_done", true).apply()
        onNavigate("home")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Skip Button (Top Right)
        if (pagerState.currentPage < pages.size - 1) {
            TextButton(
                onClick = { completeOnboarding() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = "Skip",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Pager Content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
        ) { pageIndex ->
            val page = pages[pageIndex]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Large Accent Icon Representing Feature
                Surface(
                    modifier = Modifier.size(160.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = page.accentColor.copy(alpha = 0.1f),
                    shadowElevation = 0.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = page.title,
                            tint = page.accentColor,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = page.title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = page.description,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Slide Indicators (Pips)
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                for (i in 0 until pages.size) {
                    val isActive = pagerState.currentPage == i
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = if (isActive) 24.dp else 8.dp, height = 8.dp)
                            .background(
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                    )
                }
            }

            // Next / Get Started Action Button
            val isLastPage = pagerState.currentPage == pages.size - 1
            Button(
                onClick = {
                    if (isLastPage) {
                        completeOnboarding()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
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
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isLastPage) "Get Started" else "Next",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isLastPage) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next icon",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
