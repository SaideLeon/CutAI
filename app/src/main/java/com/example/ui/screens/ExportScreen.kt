package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VideoCut
import com.example.data.model.VideoProject
import com.example.ui.viewmodel.VideoEditorViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: VideoEditorViewModel,
    onNavigateBack: () -> Unit
) {
    val project by viewModel.currentProject.collectAsState()
    val cuts by viewModel.currentCuts.collectAsState()

    if (project == null || cuts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No compiled project tracks loaded.")
        }
        return
    }

    val currentProj = project!!

    // Playback timeline in Export Screen is the assembled cut sequence!
    var isPlaying by remember { mutableStateOf(false) }
    var currentClipIndex by remember { mutableStateOf(0) }
    var currentClipTimeMs by remember { mutableStateOf(cuts.firstOrNull()?.startMs ?: 0L) }
    var showExportProgress by remember { mutableStateOf(false) }
    var exportCompleted by remember { mutableStateOf(false) }
    var exportLogLine by remember { mutableStateOf("") }
    var exportStep by remember { mutableStateOf(0) }

    // Suggested YouTube details
    var ytTitle by remember { mutableStateOf("") }
    var ytDescription by remember { mutableStateOf("") }
    var isGeneratingYtMeta by remember { mutableStateOf(false) }

    // Generate YouTube Title and Description simulation using Gemini
    LaunchedEffect(currentProj) {
        isGeneratingYtMeta = true
        delay(1200)
        if (currentProj.videoType == "preset_school") {
            ytTitle = "How Amy & Peter Excelled in Physics: Locker Study Tutorial!"
            ytDescription = "A sequenced, explanatory overview detailing Amy's morning walk, Peter's courtyard gravity review, and locker note coordination.\n\n" +
                    "Timestamps:\n" +
                    "00:00 - Introduction\n" +
                    "00:05 - Courtyard prep\n" +
                    "00:15 - Hallway study session\n\n" +
                    "Subscribe for more high school study guides! #Physics #StudyHack #Tutorial"
        } else {
            ytTitle = "The Absolute Best Spaghetti Pomodoro Recipe by Chef Jack!"
            ytDescription = "Follow Chef Jack's classic culinary masterclass. From precision tomato chopping to boiling spaghetti water and making the garlic-basil simmer.\n\n" +
                    "Chapters:\n" +
                    "00:00 - Tomato & Basil Prep\n" +
                    "00:10 - Boiling Pasta\n" +
                    "00:20 - Red Tomato Sauce Base\n" +
                    "00:35 - skillet tossing\n" +
                    "00:50 - Final plating\n\n" +
                    "Like, comment, and share! #Spaghetti #Cooking #Recipe #ChefJack"
        }
        isGeneratingYtMeta = false
    }

    // Playback engine for assembled timeline sequence
    LaunchedEffect(isPlaying, currentClipIndex) {
        if (isPlaying) {
            val activeCut = cuts.getOrNull(currentClipIndex) ?: return@LaunchedEffect
            while (true) {
                delay(100)
                currentClipTimeMs += 100
                if (currentClipTimeMs >= activeCut.endMs) {
                    // Switch to next clip in sequence
                    if (currentClipIndex + 1 < cuts.size) {
                        currentClipIndex++
                        currentClipTimeMs = cuts[currentClipIndex].startMs
                    } else {
                        // End of final clip
                        isPlaying = false
                        currentClipIndex = 0
                        currentClipTimeMs = cuts.firstOrNull()?.startMs ?: 0L
                        break
                    }
                }
            }
        }
    }

    // Render flow simulation
    LaunchedEffect(showExportProgress) {
        if (showExportProgress) {
            exportCompleted = false
            exportStep = 1
            exportLogLine = "Preparing render pipeline..."
            delay(1200)
            exportStep = 2
            exportLogLine = "Splitting and cutting 1080p raw frames..."
            delay(1500)
            exportStep = 3
            exportLogLine = "Merging secondary audio narrative voiceover track..."
            delay(1800)
            exportStep = 4
            exportLogLine = "Multiplexing and encoding final YouTube H.264 stream..."
            delay(1500)
            exportStep = 5
            exportLogLine = "Optimizing audio compression level..."
            delay(1000)
            exportCompleted = true
        }
    }

    val currentCut = cuts.getOrNull(currentClipIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assemble Sequence", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Compiled Video Monitor
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentCut != null) {
                        VideoSceneCanvas(
                            projectType = currentProj.videoType,
                            timeMs = currentClipTimeMs,
                            isPlaying = isPlaying
                        )

                        // Subtitle Overlay (Narration statement)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "🗣️ NARRATION (Audio 2 Overlay)",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentCut.narrationText,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Play/Pause Floating Overlay Trigger
                        IconButton(
                            onClick = { isPlaying = !isPlaying },
                            modifier = Modifier
                                .size(54.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Clip Index Tag
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "CUT ${currentClipIndex + 1}/${cuts.size}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text("Loading sequence...", color = Color.Gray)
                    }
                }
            }

            // 2. Export Button
            Button(
                onClick = { showExportProgress = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("export_youtube_button"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Export and Render MP4 for YouTube", fontWeight = FontWeight.Bold)
            }

            // 3. YouTube Publishing Suite
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Recommend,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Gemini Suggested Meta tags",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (isGeneratingYtMeta) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            "YouTube Title",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(ytTitle, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "YouTube Description",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(ytDescription, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
    }

    // Export Progress modal
    if (showExportProgress) {
        AlertDialog(
            onDismissRequest = { if (exportCompleted) showExportProgress = false },
            confirmButton = {
                if (exportCompleted) {
                    Button(
                        onClick = { showExportProgress = false },
                        modifier = Modifier.testTag("dismiss_export")
                    ) {
                        Text("Finish")
                    }
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!exportCompleted) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Rendering Cut Video")
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Render Succeeded!")
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (exportCompleted) "Your high-definition explanatory YouTube cut is fully rendered and optimized with custom voiceover alignment!"
                        else "Running automated timeline compiler:"
                    )

                    LinearProgressIndicator(
                        progress = { if (exportCompleted) 1.0f else (exportStep / 5.0f) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = ">>> $exportLogLine",
                            color = Color(0xFF00FF00),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        )
    }
}
