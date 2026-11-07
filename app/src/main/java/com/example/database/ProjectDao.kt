package com.example.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<AnimeProject>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Long): AnimeProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: AnimeProject): Long

    @Update
    suspend fun update(project: AnimeProject)

    @Delete
    suspend fun delete(project: AnimeProject)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun getCount(): Int
}
