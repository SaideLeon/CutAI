package com.example.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.VideoCut
import com.example.data.model.VideoPresets
import com.example.data.model.VideoProject
import com.example.data.repository.VideoRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VideoEditorViewModel(
    application: Application,
    private val repository: VideoRepository
) : AndroidViewModel(application) {

    // Projects list
    val projects: StateFlow<List<VideoProject>> = repository.allProjects
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current active project
    private val _currentProjectId = MutableStateFlow<Int?>(null)
    val currentProjectId = _currentProjectId.asStateFlow()

    private val _currentProject = MutableStateFlow<VideoProject?>(null)
    val currentProject = _currentProject.asStateFlow()

    // Current active cuts
    private val _currentCuts = MutableStateFlow<List<VideoCut>>(emptyList())
    val currentCuts = _currentCuts.asStateFlow()

    // Manual API Key State Flow
    private val _manualApiKey = MutableStateFlow("")
    val manualApiKey = _manualApiKey.asStateFlow()

    // State of AI operations
    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing = _isTranscribing.asStateFlow()

    private val _isAutoCutting = MutableStateFlow(false)
    val isAutoCutting = _isAutoCutting.asStateFlow()

    private val _showApiWarning = MutableStateFlow(false)
    val showApiWarning = _showApiWarning.asStateFlow()

    // Player Playback States
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentTimeMs = MutableStateFlow(0L)
    val currentTimeMs = _currentTimeMs.asStateFlow()

    // Timeline Edit Selection
    private val _selectedCutId = MutableStateFlow<Int?>(null)
    val selectedCutId = _selectedCutId.asStateFlow()

    // Secondary Audio Draft Narrations
    val draftNarrations = mutableStateListOf<String>()

    init {
        // Load manual API key from SharedPreferences
        val sharedPrefs = application.getSharedPreferences("autocut_prefs", android.content.Context.MODE_PRIVATE)
        _manualApiKey.value = sharedPrefs.getString("gemini_api_key", "") ?: ""

        // Observe current active cuts dynamically
        _currentProjectId
            .filterNotNull()
            .flatMapLatest { id ->
                repository.getCutsForProject(id)
            }
            .onEach { cuts ->
                _currentCuts.value = cuts
            }
            .launchIn(viewModelScope)
    }

    fun saveManualApiKey(key: String) {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("autocut_prefs", android.content.Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("gemini_api_key", key.trim()).apply()
        _manualApiKey.value = key.trim()
    }

    fun selectProject(projectId: Int) {
        _currentProjectId.value = projectId
        _currentTimeMs.value = 0L
        _isPlaying.value = false
        draftNarrations.clear()

        viewModelScope.launch {
            val project = repository.getProjectById(projectId)
            _currentProject.value = project

            // Pre-populate some drafts if there are no cuts yet
            if (project != null) {
                if (project.videoType == VideoPresets.TYPE_SCHOOL) {
                    draftNarrations.addAll(listOf(
                        "Amy goes to school, excited for a new day.",
                        "Peter meets Amy in the central courtyard to talk about homework."
                    ))
                } else if (project.videoType == VideoPresets.TYPE_PASTA) {
                    draftNarrations.addAll(listOf(
                        "First, Chef Jack chops garden tomatoes.",
                        "Then, he boils the spaghetti noodles.",
                        "He sizzles fresh garlic in olive oil.",
                        "Next, he tosses the spaghetti directly in the skillet.",
                        "Finally, the plate is beautifully served with fresh basil."
                    ))
                } else {
                    // For custom projects, provide a couple of starter narrations if empty
                    draftNarrations.addAll(listOf(
                        "Here is the exciting start of our custom footage.",
                        "And here is the conclusion of our custom video cut."
                    ))
                }
            }
        }
    }

    fun createProject(
        title: String,
        type: String,
        customPath: String? = null,
        durationMs: Long = 60000L,
        description: String = "",
        transcript: String = ""
    ) {
        viewModelScope.launch {
            val desc = if (type == "custom") {
                description
            } else if (type == VideoPresets.TYPE_SCHOOL) {
                VideoPresets.PRESET_PROJECTS[0].description
            } else {
                VideoPresets.PRESET_PROJECTS[1].description
            }

            val originalTranscript = if (type == "custom") {
                transcript
            } else if (type == VideoPresets.TYPE_SCHOOL) {
                VideoPresets.PRESET_PROJECTS[0].originalTranscript
            } else {
                VideoPresets.PRESET_PROJECTS[1].originalTranscript
            }

            val duration = if (type == "custom") {
                durationMs
            } else if (type == VideoPresets.TYPE_SCHOOL) {
                VideoPresets.PRESET_PROJECTS[0].durationMs
            } else {
                VideoPresets.PRESET_PROJECTS[1].durationMs
            }

            val project = VideoProject(
                title = title,
                videoType = type,
                customVideoPath = customPath,
                durationMs = duration,
                description = desc,
                originalTranscript = originalTranscript
            )
            val newId = repository.insertProject(project)
            selectProject(newId)
        }
    }

    fun deleteProject(projectId: Int) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            if (_currentProjectId.value == projectId) {
                _currentProjectId.value = null
                _currentProject.value = null
                _currentCuts.value = emptyList()
            }
        }
    }

    fun addDraftNarration(text: String) {
        if (text.isNotBlank()) {
            draftNarrations.add(text)
        }
    }

    fun removeDraftNarration(index: Int) {
        if (index in draftNarrations.indices) {
            draftNarrations.removeAt(index)
        }
    }

    fun updateDraftNarration(index: Int, text: String) {
        if (index in draftNarrations.indices) {
            draftNarrations[index] = text
        }
    }

    // Runs audio extraction & transcription simulation
    fun transcribeVideo() {
        viewModelScope.launch {
            _isTranscribing.value = true
            kotlinx.coroutines.delay(2000) // Realistic processing delay
            _isTranscribing.value = false
        }
    }

    // Core AI alignment trigger
    fun generateAutoCuts(forceMock: Boolean = false) {
        val project = _currentProject.value ?: return
        if (draftNarrations.isEmpty()) return

        viewModelScope.launch {
            _isAutoCutting.value = true
            
            val manualKey = _manualApiKey.value
            val apiKey = if (manualKey.isNotBlank()) manualKey else com.example.BuildConfig.GEMINI_API_KEY
            _showApiWarning.value = apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY"

            val cuts = repository.alignNarrationsWithVideo(project, draftNarrations.toList(), forceMock)
            repository.saveCuts(project.id, cuts)

            _isAutoCutting.value = false
        }
    }

    // Timeline operations
    fun selectCut(cutId: Int?) {
        _selectedCutId.value = cutId
        val cut = _currentCuts.value.find { it.id == cutId }
        if (cut != null) {
            seekTo(cut.startMs)
        }
    }

    fun updateCutDuration(cutId: Int, startMs: Long, endMs: Long) {
        viewModelScope.launch {
            val cut = _currentCuts.value.find { it.id == cutId } ?: return@launch
            val updated = cut.copy(startMs = startMs, endMs = endMs)
            repository.updateCut(updated)
        }
    }

    fun deleteCut(cutId: Int) {
        viewModelScope.launch {
            val updatedCuts = _currentCuts.value.filter { it.id != cutId }
                .mapIndexed { index, cut -> cut.copy(clipOrder = index) }
            _currentProjectId.value?.let { projId ->
                repository.saveCuts(projId, updatedCuts)
            }
            if (_selectedCutId.value == cutId) {
                _selectedCutId.value = null
            }
        }
    }

    fun dismissApiWarning() {
        _showApiWarning.value = false
    }

    // Playback control
    fun togglePlay() {
        _isPlaying.value = !_isPlaying.value
    }

    fun pause() {
        _isPlaying.value = false
    }

    fun seekTo(timeMs: Long) {
        val duration = _currentProject.value?.durationMs ?: 0L
        _currentTimeMs.value = timeMs.coerceIn(0L, duration)
    }

    fun tick(deltaMs: Long) {
        if (!_isPlaying.value) return
        val current = _currentTimeMs.value
        val duration = _currentProject.value?.durationMs ?: 0L

        if (current >= duration) {
            _currentTimeMs.value = 0L
            _isPlaying.value = false
        } else {
            _currentTimeMs.value = (current + deltaMs).coerceAtMost(duration)
        }
    }

    // Factory helper
    companion object {
        fun provideFactory(
            application: Application,
            repository: VideoRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VideoEditorViewModel(application, repository) as T
            }
        }
    }
}
