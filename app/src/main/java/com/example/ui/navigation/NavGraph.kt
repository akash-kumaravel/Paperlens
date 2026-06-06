package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.ui.screens.*
import com.example.viewmodel.OcrViewModel

@Composable
fun MainNavGraph(
    navController: NavHostController,
    viewModel: OcrViewModel
) {
    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen(onNavigate = { target ->
                navController.navigate(target) {
                    popUpTo("splash") { inclusive = true }
                }
            })
        }
        
        composable("onboarding") {
            OnboardingScreen(onNavigate = { target ->
                navController.navigate(target) {
                    popUpTo("onboarding") { inclusive = true }
                }
            })
        }
        
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        
        composable("book_creator") {
            BookCreatorScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        
        composable("camera_scan") {
            CameraScanScreen(
                viewModel = viewModel,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo("camera_scan") { inclusive = true }
                    }
                }
            )
        }
        
        composable("image_preview") {
            ImagePreviewScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        
        composable("pdf_upload") {
            PdfUploadScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        
        composable("ocr_processing") {
            OcrProcessingScreen(
                viewModel = viewModel
            )
        }
        
        composable("results") {
            ResultsScreen(
                viewModel = viewModel,
                onNavigate = { route -> 
                    navController.navigate(route) {
                        popUpTo("results") { inclusive = true }
                    }
                }
            )
        }
        
        composable("history") {
            HistoryScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) }
            )
        }
    }
}
