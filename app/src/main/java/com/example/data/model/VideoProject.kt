package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "video_projects")
data class VideoProject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val videoType: String, // "preset_school", "preset_pasta", "custom"
    val customVideoPath: String? = null,
    val durationMs: Long,
    val description: String,
    val originalTranscript: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "video_cuts",
    foreignKeys = [
        ForeignKey(
            entity = VideoProject::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class VideoCut(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: Int,
    val startMs: Long,
    val endMs: Long,
    val originalText: String,
    val narrationText: String,
    val narrationAudioPath: String? = null,
    val clipOrder: Int,
    val visualDescription: String,
    val isEnabled: Boolean = true
)

// High fidelity preset configurations for seamless user testing
object VideoPresets {
    const val TYPE_SCHOOL = "preset_school"
    const val TYPE_PASTA = "preset_pasta"

    val PRESET_PROJECTS = listOf(
        VideoProject(
            id = -1,
            title = "Amy & Peter: School Days (Raw)",
            videoType = TYPE_SCHOOL,
            durationMs = 90000,
            description = "Raw camera footage of high school hallways, entrance gates, central courtyard benches, and student lockers.",
            originalTranscript = "00:00 - 00:12: [Lofi background music in Amy's headphones]\n" +
                    "00:12 - 00:25: Amy: 'Good morning Mr. Davis! Beautiful morning to study physics!'\n" +
                    "00:25 - 00:45: [School hallway bell rings. Distant chatter of classrooms.]\n" +
                    "00:45 - 00:65: Peter: 'Hey Amy! Over here! Did you finish reading the chapter on gravity?'\n" +
                    "00:65 - 00:90: Amy: 'Hey Peter! Yes, let's compare our lab notes near the hallway lockers before third period.'",
            createdAt = System.currentTimeMillis() - 86400000
        ),
        VideoProject(
            id = -2,
            title = "Spaghetti Pomodoro Masterclass (Raw)",
            videoType = TYPE_PASTA,
            durationMs = 100000,
            description = "Raw camera footage of kitchen B-roll, ingredients prep, and cooking steps for Chef Jack's signature recipe.",
            originalTranscript = "00:00 - 00:15: Chef Jack: 'First, we prep our ingredients: sweet cherry tomatoes and fresh garden basil, sliced super thin.'\n" +
                    "00:15 - 00:35: Chef Jack: 'Keep your pasta water boiling rapidly. Salt it heavily, then submerge the spaghetti.'\n" +
                    "00:35 - 00:55: Chef Jack: 'In a hot pan, sizzle garlic in olive oil, then pour in our hand-crushed tomatoes for a slow simmer.'\n" +
                    "00:55 - 01:20: Chef Jack: 'Drain the pasta al dente and toss it directly into the skillet. Toss it high to emulsify that sauce!'\n" +
                    "01:20 - 01:40: Chef Jack: 'Plate it beautifully, top with extra olive oil and a fresh crown of basil. Buon appetito!'",
            createdAt = System.currentTimeMillis() - 43200000
        )
    )

    fun getVisualSceneAtTime(type: String, timeMs: Long, customDescription: String? = null): String {
        return when (type) {
            TYPE_SCHOOL -> {
                when {
                    timeMs < 12000 -> "Amy walking down the sidewalk, wearing headphones, listening to music with a blue backpack."
                    timeMs < 25000 -> "Amy entering the school gates, smiling and waving to teachers."
                    timeMs < 45000 -> "Peter sitting alone on a courtyard bench under a large tree, reading a book."
                    timeMs < 65000 -> "Peter stands up and waves eagerly, greeting Amy as she approaches."
                    else -> "Amy and Peter talking and laughing next to metal lockers in the hallway."
                }
            }
            TYPE_PASTA -> {
                when {
                    timeMs < 15000 -> "Chef Jack chopping tomatoes and basil on a wooden cutting board in a bright kitchen."
                    timeMs < 35000 -> "A large stainless pot of boiling salted water with steam, placing spaghetti noodles inside."
                    timeMs < 55000 -> "Golden olive oil sizzling with chopped garlic in a pan, pouring in rich tomato sauce."
                    timeMs < 80000 -> "Chef Jack tosses spaghetti high into the air inside the pan to coat it with red sauce."
                    else -> "A visual of spaghetti on a pristine white plate, garnished with oil and fresh basil leaves."
                }
            }
            else -> customDescription ?: "Custom raw camera footage."
        }
    }
}
