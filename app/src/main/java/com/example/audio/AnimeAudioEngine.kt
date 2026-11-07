package com.example.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.example.model.AnimeDoc
import java.io.File

class AnimeAudioEngine(private val context: Context) {

    private val soundPool: SoundPool
    private val soundMap = mutableMapOf<String, Int>()
    private var mediaPlayer: MediaPlayer? = null
    private var currentBgmRef: String? = null
    private var isMuted = false
    private val triggeredSfx = mutableSetOf<String>()

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(attributes)
            .build()
    }

    fun prepareAssets(doc: AnimeDoc, projectDir: File? = null) {
        soundMap.clear()
        triggeredSfx.clear()

        for (asset in doc.assets) {
            val src = asset.src
            val soundId = loadSound(src, projectDir)
            if (soundId != 0) {
                soundMap[asset.id] = soundId
            }
        }
    }

    private fun loadSound(src: String, projectDir: File?): Int {
        // 1. Try local project file
        if (projectDir != null && projectDir.exists()) {
            val file = File(projectDir, src)
            if (file.exists()) {
                return soundPool.load(file.absolutePath, 1)
            }
        }
        // 2. Try absolute path
        val directFile = File(src.removePrefix("file://"))
        if (directFile.exists()) {
            return soundPool.load(directFile.absolutePath, 1)
        }
        // 3. Try app assets
        val candidatePaths = listOf(
            src,
            "samples/audio/${File(src).name}",
            "samples/audio/$src.wav",
            "samples/audio/$src.mp3"
        )
        for (path in candidatePaths) {
            try {
                val afd: AssetFileDescriptor = context.assets.openFd(path.removePrefix("assets/"))
                val id = soundPool.load(afd, 1)
                return id
            } catch (_: Exception) {}
        }
        return 0
    }

    fun syncTime(doc: AnimeDoc, currentTime: Float, isPlaying: Boolean) {
        if (isMuted || !isPlaying) {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
            return
        }

        val clampedTime = currentTime.coerceIn(0f, doc.totalDuration)
        val activeScene = doc.scenes.find { clampedTime >= it.start && clampedTime < it.end }
            ?: return
        val sceneTime = clampedTime - activeScene.start

        // Check scene audio items
        for ((idx, audioItem) in activeScene.audio.withIndex()) {
            val triggerKey = "${activeScene.id}:$idx:${audioItem.ref}"
            if (sceneTime >= audioItem.start && sceneTime < (audioItem.start + 0.25f)) {
                if (!triggeredSfx.contains(triggerKey)) {
                    triggeredSfx.add(triggerKey)
                    val soundId = soundMap[audioItem.ref]
                    if (soundId != null && soundId != 0) {
                        soundPool.play(soundId, audioItem.volume, audioItem.volume, 1, if (audioItem.loop) -1 else 0, 1.0f)
                    }
                }
            } else if (sceneTime < audioItem.start) {
                triggeredSfx.remove(triggerKey)
            }
        }
    }

    fun onSeek() {
        triggeredSfx.clear()
        mediaPlayer?.pause()
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        if (muted) {
            mediaPlayer?.pause()
            soundPool.autoPause()
        } else {
            soundPool.autoResume()
        }
    }

    fun release() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
            soundPool.release()
            soundMap.clear()
        } catch (_: Exception) {}
    }
}
