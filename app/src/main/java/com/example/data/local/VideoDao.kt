package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.VideoProject
import com.example.data.model.VideoCut
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM video_projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<VideoProject>>

    @Query("SELECT * FROM video_projects WHERE id = :id")
    suspend fun getProjectById(id: Int): VideoProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: VideoProject): Long

    @Query("DELETE FROM video_projects WHERE id = :id")
    suspend fun deleteProjectById(id: Int)

    @Query("SELECT * FROM video_cuts WHERE projectId = :projectId ORDER BY clipOrder ASC")
    fun getCutsForProject(projectId: Int): Flow<List<VideoCut>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCut(cut: VideoCut): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCuts(cuts: List<VideoCut>)

    @Query("DELETE FROM video_cuts WHERE projectId = :projectId")
    suspend fun deleteCutsForProject(projectId: Int)

    @Transaction
    suspend fun replaceCutsForProject(projectId: Int, cuts: List<VideoCut>) {
        deleteCutsForProject(projectId)
        insertCuts(cuts)
    }
}
