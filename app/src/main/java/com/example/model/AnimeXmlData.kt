package com.example.model

enum class AssetType {
    BACKGROUND,
    CHARACTER,
    AUDIO
}

enum class AnimationType {
    MOVE,
    SCALE,
    ROTATE,
    FADE,
    ACTION,
    EXPRESSION
}

enum class Easing {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    STEP
}

data class KeyframeDef(
    val time: Float,
    val x: Float? = null,
    val y: Float? = null,
    val scaleX: Float? = null,
    val scaleY: Float? = null,
    val angle: Float? = null,
    val opacity: Float? = null,
    val easing: Easing = Easing.EASE_IN_OUT
)

data class AnimationDef(
    val type: AnimationType,
    val name: String? = null, // for action (walk, jump, idle) or expression (smile, angry, serious)
    val keyframes: List<KeyframeDef> = emptyList()
)

data class CharacterInstance(
    val ref: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 300f,
    val height: Float = 500f,
    val opacity: Float = 1.0f,
    val rotation: Float = 0.0f,
    val layer: Int = 1,
    val visible: Boolean = true,
    val animations: List<AnimationDef> = emptyList()
)

data class CameraKeyframe(
    val time: Float,
    val zoom: Float = 1.0f,
    val x: Float = 0f,
    val y: Float = 0f,
    val rotation: Float = 0.0f,
    val shake: Float = 0.0f,
    val easing: Easing = Easing.EASE_IN_OUT
)

data class CameraDef(
    val type: String = "general",
    val keyframes: List<CameraKeyframe> = emptyList()
)

data class DialogueDef(
    val character: String,
    val start: Float,
    val duration: Float,
    val text: String,
    val position: String = "bottom", // bottom, top, bubble
    val style: String = "anime_box"   // cinematic, anime_box, manga_bubble
) {
    val end: Float get() = start + duration
}

data class EffectDef(
    val type: String, // rain, snow, particles, flash, fade_in, fade_out, glow
    val start: Float,
    val duration: Float,
    val color: String? = null,
    val intensity: Float = 1.0f
) {
    val end: Float get() = start + duration
}

data class AudioInstance(
    val ref: String,
    val start: Float,
    val duration: Float? = null,
    val volume: Float = 1.0f,
    val loop: Boolean = false
)

data class BackgroundLayerDef(
    val ref: String,
    val speed: Float = 1.0f,
    val opacity: Float = 1.0f
)

data class SceneDef(
    val id: String,
    val start: Float,
    val duration: Float,
    val backgroundRef: String? = null,
    val backgroundLayers: List<BackgroundLayerDef> = emptyList(),
    val characters: List<CharacterInstance> = emptyList(),
    val camera: CameraDef? = null,
    val dialogues: List<DialogueDef> = emptyList(),
    val effects: List<EffectDef> = emptyList(),
    val audio: List<AudioInstance> = emptyList()
) {
    val end: Float get() = start + duration
}

data class AssetDef(
    val id: String,
    val src: String,
    val type: AssetType,
    val label: String = ""
)

data class SettingsDef(
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    val duration: Float = 10.0f
) {
    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height.toFloat() else 16f / 9f
}

data class AnimeDoc(
    val title: String = "Untitled Anime",
    val version: String = "1.0",
    val settings: SettingsDef = SettingsDef(),
    val assets: List<AssetDef> = emptyList(),
    val scenes: List<SceneDef> = emptyList()
) {
    val totalDuration: Float
        get() = if (settings.duration > 0f) settings.duration
                else scenes.maxOfOrNull { it.end } ?: 10.0f
}
