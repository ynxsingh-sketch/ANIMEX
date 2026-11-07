package com.example.model

import android.net.Uri

enum class VideoResolution(val label: String, val width: Int, val height: Int, val defaultBitrate: Int) {
    RES_1080P("1080p FHD (1920x1080)", 1920, 1080, 8_000_000),
    RES_720P("720p HD (1280x720)", 1280, 720, 4_000_000),
    RES_480P("480p SD (854x480)", 854, 480, 2_000_000)
}

enum class VideoFps(val label: String, val fps: Int) {
    FPS_24("24 FPS (Cinematic)", 24),
    FPS_30("30 FPS (Standard Anime)", 30),
    FPS_60("60 FPS (Smooth Motion)", 60)
}

data class ExportConfig(
    val resolution: VideoResolution = VideoResolution.RES_720P,
    val fps: VideoFps = VideoFps.FPS_30,
    val includeAudio: Boolean = true,
    val customFileName: String = ""
)

enum class ExportStep {
    IDLE,
    PREPARING,
    RENDERING_VIDEO,
    PROCESSING_AUDIO,
    FINALIZING,
    COMPLETED,
    CANCELLED,
    ERROR
}

data class ExportProgress(
    val step: ExportStep = ExportStep.IDLE,
    val currentFrame: Int = 0,
    val totalFrames: Int = 0,
    val progress: Float = 0f, // 0.0 to 1.0
    val message: String = "",
    val outputUri: Uri? = null,
    val outputFilePath: String? = null,
    val error: String? = null
)
