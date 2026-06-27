package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.local.AppDatabase
import com.example.data.repository.VideoRepository
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ExportScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.VideoEditorViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Core local cache and API layer initialization
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = VideoRepository(database.videoDao(), applicationContext)

        // Retrieve ViewModel scoped to MainActivity
        val viewModel: VideoEditorViewModel by viewModels {
            VideoEditorViewModel.provideFactory(application, repository)
        }

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("home") {
                            HomeScreen(
                                viewModel = viewModel,
                                onNavigateToEditor = {
                                    navController.navigate("editor")
                                }
                            )
                        }
                        composable("editor") {
                            EditorScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.navigateUp()
                                },
                                onNavigateToExport = {
                                    navController.navigate("export")
                                }
                            )
                        }
                        composable("export") {
                            ExportScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.navigateUp()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

