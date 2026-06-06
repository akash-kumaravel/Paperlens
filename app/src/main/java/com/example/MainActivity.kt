package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.ui.navigation.MainNavGraph
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.OcrViewModel

import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    
    private val ocrViewModel: OcrViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Let the application content draw fully edge-to-edge
        enableEdgeToEdge()
        
        setContent {
            val appThemeSetting by ocrViewModel.appTheme.collectAsState()
            
            // Choose the active dark theme mode dynamically based on setting!
            val useDarkTheme = when (appThemeSetting) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme() // "System Default"
            }

            MyApplicationTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val navController = rememberNavController()
                    MainNavGraph(
                        navController = navController,
                        viewModel = ocrViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

