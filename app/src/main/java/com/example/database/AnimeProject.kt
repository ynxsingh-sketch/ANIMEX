package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class AnimeProject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val xmlContent: String,
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    val duration: Float = 10.0f,
    val thumbnailPath: String? = null,
    val lastExportPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
