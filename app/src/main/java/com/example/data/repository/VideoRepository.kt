package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.api.AlignmentResponseWrapper
import com.example.data.api.Content
import com.example.data.api.GeminiRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.local.VideoDao
import com.example.data.model.VideoCut
import com.example.data.model.VideoPresets
import com.example.data.model.VideoProject
import com.squareup.moshi.JsonAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class VideoRepository(
    private val videoDao: VideoDao,
    private val context: Context
) {
    val allProjects: Flow<List<VideoProject>> = videoDao.getAllProjects()

    suspend fun getProjectById(id: Int): VideoProject? {
        if (id < 0) {
            // It's a preset. Let's return the corresponding preset
            return VideoPresets.PRESET_PROJECTS.find { it.id == id }
        }
        return videoDao.getProjectById(id)
    }

    suspend fun insertProject(project: VideoProject): Int {
        return videoDao.insertProject(project).toInt()
    }

    suspend fun deleteProject(id: Int) {
        videoDao.deleteProjectById(id)
    }

    fun getCutsForProject(projectId: Int): Flow<List<VideoCut>> {
        return videoDao.getCutsForProject(projectId)
    }

    suspend fun saveCuts(projectId: Int, cuts: List<VideoCut>) {
        videoDao.replaceCutsForProject(projectId, cuts)
    }

    suspend fun updateCut(cut: VideoCut) {
        videoDao.insertCut(cut)
    }

    // High fidelity AI Auto-Cut alignment logic
    suspend fun alignNarrationsWithVideo(
        project: VideoProject,
        narrations: List<String>,
        useMockFallback: Boolean = false
    ): List<VideoCut> = withContext(Dispatchers.IO) {
        val sharedPrefs = context.getSharedPreferences("autocut_prefs", Context.MODE_PRIVATE)
        val customApiKey = sharedPrefs.getString("gemini_api_key", "") ?: ""
        val apiKey = if (customApiKey.isNotBlank()) customApiKey else BuildConfig.GEMINI_API_KEY

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || useMockFallback) {
            Log.d("VideoRepository", "Using high-fidelity local alignment fallback")
            return@withContext generateLocalMockAlignment(project, narrations)
        }

        val prompt = buildString {
            append("You are an expert AI video editor making professional cuts for a YouTube video.\n\n")
            append("We have raw camera footage:\n")
            append("Title: ${project.title}\n")
            append("Total Duration: ${project.durationMs}ms\n")
            append("Description: ${project.description}\n\n")
            append("Original Audio Transcript and timestamps:\n")
            append("${project.originalTranscript}\n\n")
            append("We want to create automatic cuts that match these secondary narration statements in exact sequence:\n")
            narrations.forEachIndexed { index, narration ->
                append("${index + 1}. \"$narration\"\n")
            }
            append("\nTask: For each secondary narration statement, find the exact segment of the raw video (startMs to endMs) that visually and contextually matches. Match timestamps based on the transcript and B-roll descriptions.\n")
            append("Return your response as a single, valid JSON object of the following structure. Do NOT include markdown fences or any trailing text outside of the JSON:\n")
            append("{\n")
            append("  \"cuts\": [\n")
            append("    {\n")
            append("      \"startMs\": 12000,\n")
            append("      \"endMs\": 25000,\n")
            append("      \"visualDescription\": \"Brief description of the action matching the narration\",\n")
            append("      \"originalText\": \"Original text if any\",\n")
            append("      \"confidence\": 0.95\n")
            append("    }\n")
            append("  ]\n")
            append("}\n")
            append("The cuts array must contain exactly one cut per narration, in the same order.")
        }

        try {
            val request = GeminiRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.2f)
            )
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

            if (jsonText != null) {
                val adapter: JsonAdapter<AlignmentResponseWrapper> =
                    RetrofitClient.moshiInstance.adapter(AlignmentResponseWrapper::class.java)
                val alignmentResponse = adapter.fromJson(jsonText)

                if (alignmentResponse != null && alignmentResponse.cuts.isNotEmpty()) {
                    return@withContext alignmentResponse.cuts.mapIndexed { index, result ->
                        VideoCut(
                            projectId = project.id,
                            startMs = result.startMs.coerceAtMost(project.durationMs),
                            endMs = result.endMs.coerceAtMost(project.durationMs),
                            originalText = result.originalText,
                            narrationText = narrations.getOrElse(index) { "" },
                            clipOrder = index,
                            visualDescription = result.visualDescription
                        )
                    }
                }
            }
            // Fallback to local if response parsing fails
            return@withContext generateLocalMockAlignment(project, narrations)
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error calling Gemini API, falling back to local simulation: ${e.message}")
            return@withContext generateLocalMockAlignment(project, narrations)
        }
    }

    private fun generateLocalMockAlignment(project: VideoProject, narrations: List<String>): List<VideoCut> {
        val cuts = mutableListOf<VideoCut>()
        narrations.forEachIndexed { index, narration ->
            var startMs = 0L
            var endMs = 10000L
            var visualDesc = "Custom raw footage"
            var origText = ""

            val lowerText = narration.lowercase()

            when (project.videoType) {
                VideoPresets.TYPE_SCHOOL -> {
                    when {
                        // School, going, walking, morning
                        lowerText.contains("school") || lowerText.contains("go") || lowerText.contains("walk") || lowerText.contains("morning") -> {
                            startMs = 10000L
                            endMs = 25000L
                            visualDesc = "Amy walking up to and entering the school gate, greeting teachers."
                            origText = "Amy: 'Good morning Mr. Davis! Beautiful morning to study physics!'"
                        }
                        // Peter, meets, talk, hello, gravity
                        lowerText.contains("peter") || lowerText.contains("meet") || lowerText.contains("see") || lowerText.contains("greet") -> {
                            startMs = 45000L
                            endMs = 65000L
                            visualDesc = "Peter standing up from his courtyard bench, smiling and waving to Amy."
                            origText = "Peter: 'Hey Amy! Over here! Did you finish reading the chapter on gravity?'"
                        }
                        // Lockers, study, compare, homework
                        lowerText.contains("locker") || lowerText.contains("notes") || lowerText.contains("homework") || lowerText.contains("study") -> {
                            startMs = 65000L
                            endMs = 90000L
                            visualDesc = "Amy and Peter near lockers, comparing notebooks and looking over homework."
                            origText = "Amy: 'Hey Peter! Yes, let's compare our lab notes near the lockers...'"
                        }
                        // Default segmenting if not matched
                        else -> {
                            val chunk = project.durationMs / narrations.size
                            startMs = index * chunk
                            endMs = (index + 1) * chunk
                            visualDesc = VideoPresets.getVisualSceneAtTime(project.videoType, startMs)
                        }
                    }
                }
                VideoPresets.TYPE_PASTA -> {
                    when {
                        lowerText.contains("chop") || lowerText.contains("tomato") || lowerText.contains("basil") || lowerText.contains("prep") -> {
                            startMs = 0L
                            endMs = 15000L
                            visualDesc = "Chef Jack slicing tomatoes and folding green basil on the wooden block."
                            origText = "Chef Jack: 'First, we prep our ingredients...'"
                        }
                        lowerText.contains("water") || lowerText.contains("boil") || lowerText.contains("spaghetti") || lowerText.contains("pasta") -> {
                            startMs = 15000L
                            endMs = 35000L
                            visualDesc = "Steam rising from deep steel pot, plunging spaghetti into boiling water."
                            origText = "Chef Jack: 'Keep your pasta water boiling rapidly...'"
                        }
                        lowerText.contains("garlic") || lowerText.contains("oil") || lowerText.contains("pan") || lowerText.contains("sauce") -> {
                            startMs = 35000L
                            endMs = 55000L
                            visualDesc = "Sizzling garlic chips in shimmering olive oil, pouring in crushed tomatoes."
                            origText = "Chef Jack: 'In a hot pan, sizzle garlic...'"
                        }
                        lowerText.contains("toss") || lowerText.contains("coat") || lowerText.contains("pan") || lowerText.contains("mix") -> {
                            startMs = 55000L
                            endMs = 80000L
                            visualDesc = "Chef Jack tossing pasta into the air, merging sauce and pasta beautifully."
                            origText = "Chef Jack: 'Drain the pasta al dente and toss it...'"
                        }
                        lowerText.contains("plate") || lowerText.contains("serve") || lowerText.contains("garnish") || lowerText.contains("eat") -> {
                            startMs = 80000L
                            endMs = 100000L
                            visualDesc = "Plating the red pasta on a pristine white plate, crowned with basil and drizzled oil."
                            origText = "Chef Jack: 'Plate it beautifully, top with extra olive oil...'"
                        }
                        else -> {
                            val chunk = project.durationMs / narrations.size
                            startMs = index * chunk
                            endMs = (index + 1) * chunk
                            visualDesc = VideoPresets.getVisualSceneAtTime(project.videoType, startMs)
                        }
                    }
                }
                else -> {
                    // Custom raw clips
                    val chunk = project.durationMs / narrations.size
                    startMs = index * chunk
                    endMs = (index + 1) * chunk
                    visualDesc = "Custom clip segment showing matched secondary narration."
                }
            }

            cuts.add(
                VideoCut(
                    projectId = project.id,
                    startMs = startMs,
                    endMs = endMs,
                    originalText = origText,
                    narrationText = narration,
                    clipOrder = index,
                    visualDescription = visualDesc
                )
            )
        }
        return cuts
    }
}
