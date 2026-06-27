package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VideoCut
import com.example.data.model.VideoPresets
import com.example.data.model.VideoProject
import com.example.ui.viewmodel.VideoEditorViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: VideoEditorViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToExport: () -> Unit
) {
    val project by viewModel.currentProject.collectAsState()
    val cuts by viewModel.currentCuts.collectAsState()
    val isTranscribing by viewModel.isTranscribing.collectAsState()
    val isAutoCutting by viewModel.isAutoCutting.collectAsState()
    val showApiWarning by viewModel.showApiWarning.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentTimeMs by viewModel.currentTimeMs.collectAsState()
    val selectedCutId by viewModel.selectedCutId.collectAsState()

    val manualApiKey by viewModel.manualApiKey.collectAsState()
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var tempApiKey by remember(manualApiKey) { mutableStateOf(manualApiKey) }

    var activeTab by remember { mutableStateOf(0) } // 0: Original Audio, 1: Secondary Narration
    var newNarrationText by remember { mutableStateOf("") }

    // Simulation of continuous playback ticking
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                delay(100)
                viewModel.tick(100)
            }
        }
    }

    if (project == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentProj = project!!

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            currentProj.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Raw Footage • ${(currentProj.durationMs / 1000)}s",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showApiKeyDialog = true },
                        modifier = Modifier.testTag("configure_api_key_editor_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "Configure API Key",
                            tint = if (manualApiKey.isNotBlank()) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Button(
                        onClick = onNavigateToExport,
                        enabled = cuts.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("assemble_export_button")
                    ) {
                        Icon(Icons.Default.MovieFilter, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Assemble", fontWeight = FontWeight.Bold)
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
        ) {
            // Screen split: Player on top, Controls in the middle, Timeline at the bottom
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Live Video Player Canvas
                VideoPlayerWidget(
                    project = currentProj,
                    currentTimeMs = currentTimeMs,
                    isPlaying = isPlaying,
                    onPlayToggle = { viewModel.togglePlay() },
                    onSeek = { viewModel.seekTo(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Control Tabs: Original Audio VS Secondary Narration Script
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    TabRow(
                        selectedTabIndex = activeTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            modifier = Modifier.testTag("original_tab")
                        ) {
                            Box(modifier = Modifier.padding(vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Hearing, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Raw Audio", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            modifier = Modifier.testTag("secondary_tab")
                        ) {
                            Box(modifier = Modifier.padding(vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("AI Narrator Script", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .padding(16.dp)
                    ) {
                        if (activeTab == 0) {
                            // Original Audio transcript tab
                            OriginalAudioPanel(
                                project = currentProj,
                                isTranscribing = isTranscribing,
                                onTranscribe = { viewModel.transcribeVideo() }
                            )
                        } else {
                            // Secondary audio narration script alignment tab
                            SecondaryNarrationPanel(
                                draftNarrations = viewModel.draftNarrations,
                                isAutoCutting = isAutoCutting,
                                newText = newNarrationText,
                                onTextChange = { newNarrationText = it },
                                onAdd = {
                                    viewModel.addDraftNarration(newNarrationText)
                                    newNarrationText = ""
                                },
                                onRemove = { viewModel.removeDraftNarration(it) },
                                onAutoCut = { viewModel.generateAutoCuts() }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 3. Multi-track Timeline Visualizer (Fixed at the bottom)
            TimelineWidget(
                project = currentProj,
                cuts = cuts,
                currentTimeMs = currentTimeMs,
                selectedCutId = selectedCutId,
                onSelectCut = { viewModel.selectCut(it) },
                onSeek = { viewModel.seekTo(it) }
            )
        }
    }

    // AI Processing Overlay Dialogs
    if (isAutoCutting) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Gemini Compiling Cuts...")
                }
            },
            text = {
                Column {
                    Text("Gemini 3.5 Flash is analyzing your raw footage transcript and matching timestamps with secondary narration scripts.")
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        )
    }

    // API Key Notice
    if (showApiWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissApiWarning() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("API Simulation Active")
                }
            },
            text = {
                Text(
                    "No custom GEMINI_API_KEY found in project secrets. " +
                    "To demonstrate robust features seamlessly, AI AutoCut is executing our high-fidelity, contextual video scene matching engine offline. " +
                    "To run real live Gemini multimodal analysis, enter your key manually below or in the AI Studio Secrets panel!"
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissApiWarning() }) {
                    Text("Got it")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissApiWarning()
                    showApiKeyDialog = true
                }) {
                    Text("Configurar Chave")
                }
            }
        )
    }

    // Manual API Key config dialog inside editor screen
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Configurar Chave API")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Insira sua chave de API do Gemini para realizar análise de vídeo real e auto-cuts. Sua chave será salva localmente no dispositivo.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tempApiKey,
                        onValueChange = { tempApiKey = it },
                        label = { Text("Chave API do Gemini") },
                        placeholder = { Text("AIzaSy...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (tempApiKey.isNotBlank() && tempApiKey.startsWith("AIzaSy")) {
                        Text(
                            "✓ Chave no formato correto",
                            color = Color(0xFF2E7D32),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveManualApiKey(tempApiKey)
                        showApiKeyDialog = false
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun VideoPlayerWidget(
    project: VideoProject,
    currentTimeMs: Long,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val durationS = project.durationMs / 1000
    val currentS = currentTimeMs / 1000

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(12.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column {
            // Dynamic Animated Canvas Scene
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFF111111)),
                contentAlignment = Alignment.BottomCenter
            ) {
                VideoSceneCanvas(
                    projectType = project.videoType,
                    timeMs = currentTimeMs,
                    isPlaying = isPlaying
                )

                // Subtitle overlays
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Scene: ${VideoPresets.getVisualSceneAtTime(project.videoType, currentTimeMs, project.description)}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Player Progress Seekbar & Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Slider(
                    value = currentTimeMs.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..project.durationMs.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_slider"),
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.DarkGray,
                        thumbColor = MaterialTheme.colorScheme.primary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("%02d:%02d", currentS / 60, currentS % 60),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    IconButton(
                        onClick = onPlayToggle,
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .testTag("play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = String.format("%02d:%02d", durationS / 60, durationS % 60),
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun VideoSceneCanvas(
    projectType: String,
    timeMs: Long,
    isPlaying: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "player_animation")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_motion"
    )

    val bounceScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Background gradients based on time / scenes
        val brush = when (projectType) {
            VideoPresets.TYPE_SCHOOL -> {
                if (timeMs < 25000) {
                    // Early morning yellow skies
                    Brush.verticalGradient(colors = listOf(Color(0xFFFFB300), Color(0xFF1976D2)))
                } else if (timeMs < 65000) {
                    // Midday bright garden green sky
                    Brush.verticalGradient(colors = listOf(Color(0xFF2196F3), Color(0xFF4CAF50)))
                } else {
                    // Interior metallic lockers colors
                    Brush.verticalGradient(colors = listOf(Color(0xFF37474F), Color(0xFF78909C)))
                }
            }
            VideoPresets.TYPE_PASTA -> {
                if (timeMs < 15000) {
                    // Chopping board warm wood look
                    Brush.verticalGradient(colors = listOf(Color(0xFF8D6E63), Color(0xFF4E342E)))
                } else if (timeMs < 55000) {
                    // Sizzling pan fire orange/grey
                    Brush.verticalGradient(colors = listOf(Color(0xFFFF5722), Color(0xFF263238)))
                } else {
                    // Fine plating white/green/red
                    Brush.verticalGradient(colors = listOf(Color(0xFFFAFAFA), Color(0xFFD32F2F)))
                }
            }
            else -> Brush.verticalGradient(colors = listOf(Color.DarkGray, Color.Black))
        }

        drawRect(brush = brush, size = size)

        // Draw animated contextual motifs inside the video feed
        when (projectType) {
            VideoPresets.TYPE_SCHOOL -> {
                if (timeMs < 25000) {
                    // School gate and Sun
                    drawCircle(Color.Yellow, radius = 25.dp.toPx() * bounceScale, center = Offset(width * 0.8f, height * 0.3f))
                    // Sidewalk line
                    drawLine(Color.White, start = Offset(0f, height * 0.8f), end = Offset(width, height * 0.8f), strokeWidth = 3.dp.toPx())
                    // Draw Amy (stylized backpack bubble)
                    drawCircle(Color(0xFF0D47A1), radius = 18.dp.toPx(), center = Offset(width * 0.3f, height * 0.7f))
                    drawCircle(Color(0xFFF57C00), radius = 10.dp.toPx(), center = Offset(width * 0.3f, height * 0.58f)) // Head
                } else if (timeMs < 65000) {
                    // Peter sitting on courtyard bench
                    // Tree trunk
                    drawRect(Color(0xFF5D4037), topLeft = Offset(width * 0.15f, height * 0.3f), size = Size(20.dp.toPx(), height * 0.5f))
                    // Leaves
                    drawCircle(Color(0xFF2E7D32), radius = 45.dp.toPx() * bounceScale, center = Offset(width * 0.17f, height * 0.3f))
                    // Bench and Peter / Amy
                    drawLine(Color(0xFF4E342E), start = Offset(width * 0.4f, height * 0.75f), end = Offset(width * 0.8f, height * 0.75f), strokeWidth = 8.dp.toPx())
                    // Peter (Blue circle sitting)
                    drawCircle(Color(0xFF0277BD), radius = 15.dp.toPx(), center = Offset(width * 0.5f, height * 0.7f))
                } else {
                    // Hallway Locker grid drawing
                    for (i in 0..5) {
                        val startX = width * 0.15f + i * (width * 0.12f)
                        drawRoundRect(
                            color = Color(0xFFB0BEC5),
                            topLeft = Offset(startX, height * 0.2f),
                            size = Size(30.dp.toPx(), height * 0.6f),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        // Locker handles
                        drawRect(Color.DarkGray, topLeft = Offset(startX + 25.dp.toPx(), height * 0.5f), size = Size(3.dp.toPx(), 10.dp.toPx()))
                    }
                }
            }
            VideoPresets.TYPE_PASTA -> {
                if (timeMs < 15000) {
                    // Knife and tomatoes
                    drawCircle(Color(0xFFD32F2F), radius = 22.dp.toPx() * bounceScale, center = Offset(width * 0.3f, height * 0.5f))
                    drawCircle(Color(0xFFD32F2F), radius = 18.dp.toPx(), center = Offset(width * 0.45f, height * 0.55f))
                    // Knife slash
                    drawLine(Color.LightGray, start = Offset(width * 0.6f, height * 0.2f), end = Offset(width * 0.75f, height * 0.7f), strokeWidth = 6.dp.toPx())
                } else if (timeMs < 55000) {
                    // Steam bubbles
                    for (i in 0..6) {
                        val bubbleX = (width * 0.2f + i * (width * 0.12f) + waveOffset) % (width * 0.6f) + width * 0.2f
                        val bubbleY = height * 0.7f - (i * 12.dp.toPx() + waveOffset * 0.5f) % (height * 0.5f)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.4f),
                            radius = (6.dp.toPx() + i % 3),
                            center = Offset(bubbleX, bubbleY)
                        )
                    }
                    // Cooking pot frame
                    drawRoundRect(
                        color = Color.Gray,
                        topLeft = Offset(width * 0.25f, height * 0.4f),
                        size = Size(width * 0.5f, height * 0.45f),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                        style = Stroke(width = 3.dp.toPx())
                    )
                } else {
                    // Red spaghetti loops
                    drawCircle(Color(0xFFFF9800), radius = 35.dp.toPx() * bounceScale, center = Offset(width * 0.5f, height * 0.5f), style = Stroke(width = 8.dp.toPx()))
                    drawCircle(Color(0xFFFF5722), radius = 25.dp.toPx(), center = Offset(width * 0.5f, height * 0.5f), style = Stroke(width = 6.dp.toPx()))
                    // Basil crown
                    drawCircle(Color(0xFF4CAF50), radius = 10.dp.toPx(), center = Offset(width * 0.5f, height * 0.4f))
                }
            }
        }
    }
}

@Composable
fun OriginalAudioPanel(
    project: VideoProject,
    isTranscribing: Boolean,
    onTranscribe: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Original Footage Subtitles & Transcript",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isTranscribing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Extracting AAC Audio...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = project.originalTranscript,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onTranscribe,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("extract_transcribe_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Default.Hearing, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Extract Audio & Transcribe with Gemini")
        }
    }
}

@Composable
fun SecondaryNarrationPanel(
    draftNarrations: List<String>,
    isAutoCutting: Boolean,
    newText: String,
    onTextChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onAutoCut: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newText,
                onValueChange = onTextChange,
                placeholder = { Text("Write narration, e.g. Amy goes to school") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("narration_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onAdd,
                enabled = newText.isNotBlank(),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .testTag("add_narration_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Narration", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            if (draftNarrations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Add script statements describing what happens next.",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(draftNarrations) { index, statement ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statement,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { onRemove(index) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onAutoCut,
            enabled = draftNarrations.isNotEmpty() && !isAutoCutting,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("autocut_button")
                .drawBehind {
                    // subtle glow border for extra polished look
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Auto-Cut Video with AI", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TimelineWidget(
    project: VideoProject,
    cuts: List<VideoCut>,
    currentTimeMs: Long,
    selectedCutId: Int?,
    onSelectCut: (Int) -> Unit,
    onSeek: (Long) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(145.dp)
            .shadow(16.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            // Track Headers & Timeline Indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Timeline Tracks",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${cuts.size} clips aligned",
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Scrollable Timeline Rows representing Tracks
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Track 1: Original Footage Segments
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(360.dp)
                                .fillMaxHeight(0.4f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF263238), Color(0xFF455A64))
                                    )
                                )
                                .padding(4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text("RAW FOOTAGE TRACK", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Track 2: Compiled AI Cuts
                    if (cuts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .width(360.dp)
                                .weight(1f)
                                .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No cuts generated. Write secondary narration and press 'Auto-Cut Video'",
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            cuts.forEach { cut ->
                                val duration = cut.endMs - cut.startMs
                                val widthDp = (duration / 150).coerceIn(100L, 250L).toInt().dp
                                val isSelected = cut.id == selectedCutId

                                Box(
                                    modifier = Modifier
                                        .width(widthDp)
                                        .fillMaxHeight(0.9f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                                            else MaterialTheme.colorScheme.surface
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onSelectCut(cut.id) }
                                ) {
                                    Row(modifier = Modifier.fillMaxSize()) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .width(4.dp)
                                                    .fillMaxHeight()
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                        Column(modifier = Modifier.padding(8.dp).weight(1f)) {
                                            Text(
                                                text = "Cut ${cut.clipOrder + 1}: ${cut.narrationText}",
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = String.format("%02ds - %02ds", cut.startMs / 1000, cut.endMs / 1000),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace
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
    }
}
