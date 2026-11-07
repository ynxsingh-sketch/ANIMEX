package com.example.renderer

import android.content.Context
import android.graphics.*
import com.example.model.*
import java.io.File
import kotlin.math.*

class AnimeRenderer(private val context: Context) {

    val assetResolver = AssetResolver(context)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val matrix = Matrix()
    private val rectF = RectF()

    // Pre-allocated reusable geometry objects to eliminate GC pauses during playback & video export
    private val colorCache = HashMap<String, Int>()
    private val sharedSrcRect = Rect()
    private val sharedDestRect = RectF()
    private val sharedWrapRect = RectF()
    private val sharedDialogueRect = RectF()
    private val sharedPillRect = RectF()
    private val emotePath = Path()

    private fun getCachedColor(colorStr: String?, defaultColor: Int): Int {
        if (colorStr.isNullOrBlank()) return defaultColor
        return colorCache.getOrPut(colorStr) {
            try {
                Color.parseColor(colorStr)
            } catch (_: Exception) {
                defaultColor
            }
        }
    }

    fun render(
        canvas: Canvas,
        doc: AnimeDoc,
        currentTime: Float,
        targetWidth: Int,
        targetHeight: Int,
        projectDir: File? = null
    ) {
        val totalDur = doc.totalDuration.coerceAtLeast(0.1f)
        val clampedTime = currentTime.coerceIn(0f, totalDur)

        // Find active scene
        val activeScene = doc.scenes.find { clampedTime >= it.start && clampedTime < it.end }
            ?: doc.scenes.lastOrNull()
            ?: return

        val sceneTime = (clampedTime - activeScene.start).coerceAtLeast(0f)

        // Asset map
        val assetMap = doc.assets.associateBy { it.id }

        // 1. Calculate Camera state
        val cameraState = evaluateCamera(activeScene.camera, sceneTime)

        canvas.save()

        // Apply scale to fit coordinate space of project (doc.settings.width x doc.settings.height) to canvas target
        val scaleX = targetWidth.toFloat() / doc.settings.width.toFloat()
        val scaleY = targetHeight.toFloat() / doc.settings.height.toFloat()
        canvas.scale(scaleX, scaleY)

        val docW = doc.settings.width.toFloat()
        val docH = doc.settings.height.toFloat()

        // Apply Camera Transform
        val camX = cameraState.x + (sin(sceneTime * 45f) * cameraState.shake)
        val camY = cameraState.y + (cos(sceneTime * 38f) * cameraState.shake)
        canvas.translate(docW / 2f + camX, docH / 2f + camY)
        canvas.scale(cameraState.zoom, cameraState.zoom)
        canvas.rotate(cameraState.rotation)
        canvas.translate(-docW / 2f, -docH / 2f)

        // 2. Render Backgrounds
        val bgRefs = if (activeScene.backgroundLayers.isNotEmpty()) {
            activeScene.backgroundLayers
        } else if (activeScene.backgroundRef != null) {
            listOf(BackgroundLayerDef(ref = activeScene.backgroundRef))
        } else {
            emptyList()
        }

        if (bgRefs.isEmpty()) {
            // Default dark anime studio backdrop
            paint.shader = null
            paint.color = getCachedColor("#090D14", Color.BLACK)
            canvas.drawRect(0f, 0f, docW, docH, paint)
        } else {
            for (layer in bgRefs) {
                val asset = assetMap[layer.ref]
                val src = asset?.src ?: layer.ref
                val bitmap = assetResolver.loadBitmap(src, projectDir)

                val parallaxOffset = (sceneTime * layer.speed * 30f) % docW
                sharedSrcRect.set(0, 0, bitmap.width, bitmap.height)
                sharedDestRect.set(-parallaxOffset, 0f, docW - parallaxOffset + 5f, docH)

                paint.alpha = (layer.opacity.coerceIn(0f, 1f) * 255).toInt()
                canvas.drawBitmap(bitmap, sharedSrcRect, sharedDestRect, paint)

                // If scrolling parallax, wrap around
                if (parallaxOffset > 0) {
                    sharedWrapRect.set(docW - parallaxOffset, 0f, 2 * docW - parallaxOffset, docH)
                    canvas.drawBitmap(bitmap, sharedSrcRect, sharedWrapRect, paint)
                }
            }
        }

        // 3. Render Characters sorted by layer
        val sortedChars = activeScene.characters.sortedBy { it.layer }
        for (char in sortedChars) {
            if (!char.visible) continue
            renderCharacter(canvas, char, assetMap, sceneTime, docW, docH, projectDir)
        }

        // 4. Render Visual Effects
        for (effect in activeScene.effects) {
            if (sceneTime >= effect.start && sceneTime <= effect.end) {
                val effectProgress = (sceneTime - effect.start) / effect.duration.coerceAtLeast(0.01f)
                renderEffect(canvas, effect, sceneTime, effectProgress, docW, docH)
            }
        }

        // 5. Render Dialogue Subtitles & Anime Speech Boxes
        for (dialogue in activeScene.dialogues) {
            if (sceneTime >= dialogue.start && sceneTime <= dialogue.end) {
                renderDialogue(canvas, dialogue, sceneTime, docW, docH)
            }
        }

        canvas.restore()
    }

    private fun renderCharacter(
        canvas: Canvas,
        char: CharacterInstance,
        assetMap: Map<String, AssetDef>,
        time: Float,
        docW: Float,
        docH: Float,
        projectDir: File?
    ) {
        val asset = assetMap[char.ref]
        val src = asset?.src ?: char.ref
        val bitmap = assetResolver.loadBitmap(src, projectDir)

        var posX = char.x
        var posY = char.y
        var scaleX = 1.0f
        var scaleY = 1.0f
        var rotation = char.rotation
        var opacity = char.opacity
        var actionName: String? = null
        var expressionName: String? = null

        // Apply animated keyframes
        for (anim in char.animations) {
            when (anim.type) {
                AnimationType.MOVE -> {
                    interpolateKeyframes(anim.keyframes, time) { kf1, kf2, t ->
                        val x1 = kf1.x ?: char.x
                        val y1 = kf1.y ?: char.y
                        val x2 = kf2.x ?: x1
                        val y2 = kf2.y ?: y1
                        posX = x1 + (x2 - x1) * t
                        posY = y1 + (y2 - y1) * t
                    }
                }
                AnimationType.SCALE -> {
                    interpolateKeyframes(anim.keyframes, time) { kf1, kf2, t ->
                        val sx1 = kf1.scaleX ?: 1.0f
                        val sy1 = kf1.scaleY ?: 1.0f
                        val sx2 = kf2.scaleX ?: sx1
                        val sy2 = kf2.scaleY ?: sy1
                        scaleX = sx1 + (sx2 - sx1) * t
                        scaleY = sy1 + (sy2 - sy1) * t
                    }
                }
                AnimationType.ROTATE -> {
                    interpolateKeyframes(anim.keyframes, time) { kf1, kf2, t ->
                        val a1 = kf1.angle ?: char.rotation
                        val a2 = kf2.angle ?: a1
                        rotation = a1 + (a2 - a1) * t
                    }
                }
                AnimationType.FADE -> {
                    interpolateKeyframes(anim.keyframes, time) { kf1, kf2, t ->
                        val o1 = kf1.opacity ?: char.opacity
                        val o2 = kf2.opacity ?: o1
                        opacity = o1 + (o2 - o1) * t
                    }
                }
                AnimationType.ACTION -> {
                    actionName = anim.name ?: "idle"
                }
                AnimationType.EXPRESSION -> {
                    expressionName = anim.name ?: "smile"
                }
            }
        }

        // Apply action procedural kinematics
        when (actionName?.lowercase()) {
            "walk" -> {
                posY += sin(time * 10f) * 12f
                rotation += sin(time * 5f) * 3f
            }
            "run" -> {
                posY += abs(sin(time * 14f)) * -20f
                rotation += 6f
            }
            "jump" -> {
                val jumpCycle = (time * 2f) % 2f
                if (jumpCycle < 1.0f) {
                    posY -= sin(jumpCycle * Math.PI.toFloat()) * 90f
                }
            }
            "attack" -> {
                val atkCycle = (time * 3f) % 1f
                if (atkCycle < 0.4f) {
                    posX += (atkCycle / 0.4f) * 80f
                } else {
                    posX += (1f - (atkCycle - 0.4f) / 0.6f) * 80f
                }
            }
            "idle", null -> {
                scaleY *= (1.0f + sin(time * 3.5f) * 0.02f)
                posY += sin(time * 3.5f) * 3f
            }
        }

        if (opacity <= 0.01f) return

        canvas.save()
        canvas.translate(posX, posY)
        canvas.scale(scaleX, scaleY)
        canvas.rotate(rotation)

        // Center alignment
        val w = char.width
        val h = char.height
        rectF.set(-w / 2f, -h / 2f, w / 2f, h / 2f)

        paint.alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
        sharedSrcRect.set(0, 0, bitmap.width, bitmap.height)
        canvas.drawBitmap(bitmap, sharedSrcRect, rectF, paint)

        // Draw Expression Emote Indicator
        if (expressionName != null) {
            renderExpressionEmote(canvas, expressionName, w, h, time)
        }

        canvas.restore()
    }

    private fun renderExpressionEmote(canvas: Canvas, expr: String, w: Float, h: Float, time: Float) {
        val emoteX = w * 0.35f
        val emoteY = -h * 0.45f
        val pulse = 1f + sin(time * 8f) * 0.1f

        paint.style = Paint.Style.FILL
        when (expr.lowercase()) {
            "angry" -> {
                // 💢 Anger vein symbol
                paint.color = getCachedColor("#EF4444", Color.RED)
                val s = 24f * pulse
                canvas.drawRect(emoteX - s, emoteY - 4f, emoteX + s, emoteY + 4f, paint)
                canvas.drawRect(emoteX - 4f, emoteY - s, emoteX + 4f, emoteY + s, paint)
                canvas.drawCircle(emoteX - s * 0.6f, emoteY - s * 0.6f, 6f, paint)
                canvas.drawCircle(emoteX + s * 0.6f, emoteY + s * 0.6f, 6f, paint)
            }
            "happy", "smile" -> {
                // ✨ Sparkle / star flare
                paint.color = getCachedColor("#FBBF24", Color.YELLOW)
                val r = 16f * pulse
                canvas.drawCircle(emoteX, emoteY, r * 0.6f, paint)
                emotePath.reset()
                emotePath.moveTo(emoteX, emoteY - r * 1.5f)
                emotePath.lineTo(emoteX + r * 0.4f, emoteY)
                emotePath.lineTo(emoteX + r * 1.5f, emoteY)
                emotePath.lineTo(emoteX + r * 0.4f, emoteY + r * 0.4f)
                emotePath.lineTo(emoteX, emoteY + r * 1.5f)
                emotePath.lineTo(emoteX - r * 0.4f, emoteY + r * 0.4f)
                emotePath.lineTo(emoteX - r * 1.5f, emoteY)
                emotePath.lineTo(emoteX - r * 0.4f, emoteY)
                emotePath.close()
                canvas.drawPath(emotePath, paint)
            }
            "shocked" -> {
                // 💦 Blue sweatdrop
                paint.color = getCachedColor("#38BDF8", Color.CYAN)
                val r = 18f * pulse
                emotePath.reset()
                emotePath.moveTo(emoteX, emoteY - r * 1.2f)
                emotePath.quadTo(emoteX + r, emoteY, emoteX, emoteY + r)
                emotePath.quadTo(emoteX - r, emoteY, emoteX, emoteY - r * 1.2f)
                emotePath.close()
                canvas.drawPath(emotePath, paint)
            }
            "serious" -> {
                // Glowing cyan eye flare streak
                paint.color = getCachedColor("#00E5FF", Color.CYAN)
                paint.strokeWidth = 5f
                paint.style = Paint.Style.STROKE
                val flareLen = 40f * pulse
                canvas.drawLine(emoteX - flareLen, emoteY, emoteX + flareLen, emoteY, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(emoteX, emoteY, 8f, paint)
            }
        }
    }

    private fun renderEffect(
        canvas: Canvas,
        effect: EffectDef,
        time: Float,
        progress: Float,
        docW: Float,
        docH: Float
    ) {
        paint.shader = null
        when (effect.type.lowercase()) {
            "rain" -> {
                paint.color = getCachedColor(effect.color, Color.parseColor("#B0E0E6"))
                paint.strokeWidth = 3f * effect.intensity
                paint.style = Paint.Style.STROKE
                val numDrops = (120 * effect.intensity).toInt()
                for (i in 0 until numDrops) {
                    val seedX = ((i * 137.5f) % docW)
                    val seedY = ((i * 229.3f) % docH)
                    val dropY = (seedY + time * 1200f) % docH
                    val dropX = (seedX - (time * 250f)) % docW
                    canvas.drawLine(dropX, dropY, dropX - 15f, dropY + 45f, paint)
                }
            }
            "snow" -> {
                paint.color = getCachedColor(effect.color, Color.WHITE)
                paint.style = Paint.Style.FILL
                val numFlakes = (100 * effect.intensity).toInt()
                for (i in 0 until numFlakes) {
                    val seedX = ((i * 163.7f) % docW)
                    val seedY = ((i * 281.9f) % docH)
                    val speed = 80f + (i % 5) * 40f
                    val flakeY = (seedY + time * speed) % docH
                    val sway = sin(time * 2f + i) * 25f
                    val flakeX = (seedX + sway + docW) % docW
                    val radius = (3f + (i % 4) * 2f) * effect.intensity
                    paint.alpha = (140 + (i % 100))
                    canvas.drawCircle(flakeX, flakeY, radius, paint)
                }
            }
            "particles" -> {
                // Rising golden / cyan anime embers
                paint.style = Paint.Style.FILL
                val baseColor = getCachedColor(effect.color, Color.parseColor("#00E5FF"))
                paint.color = baseColor
                val count = (80 * effect.intensity).toInt()
                for (i in 0 until count) {
                    val seedX = ((i * 193.1f) % docW)
                    val seedY = ((i * 311.7f) % docH)
                    val partY = (seedY - time * 150f + docH * 10f) % docH
                    val sway = sin(time * 3f + i * 2f) * 35f
                    val partX = (seedX + sway + docW) % docW
                    val radius = (4f + (i % 5) * 2f)
                    paint.alpha = (180 + (sin(time * 5f + i) * 70).toInt()).coerceIn(40, 255)
                    canvas.drawCircle(partX, partY, radius, paint)
                }
            }
            "flash" -> {
                val flashAlpha = ((1f - progress) * 255 * effect.intensity).toInt().coerceIn(0, 255)
                paint.style = Paint.Style.FILL
                paint.color = getCachedColor(effect.color, Color.WHITE)
                paint.alpha = flashAlpha
                canvas.drawRect(0f, 0f, docW, docH, paint)
            }
            "fade_in" -> {
                val fadeAlpha = ((1f - progress) * 255 * effect.intensity).toInt().coerceIn(0, 255)
                paint.style = Paint.Style.FILL
                paint.color = getCachedColor(effect.color, Color.BLACK)
                paint.alpha = fadeAlpha
                canvas.drawRect(0f, 0f, docW, docH, paint)
            }
            "fade_out" -> {
                val fadeAlpha = (progress * 255 * effect.intensity).toInt().coerceIn(0, 255)
                paint.style = Paint.Style.FILL
                paint.color = getCachedColor(effect.color, Color.BLACK)
                paint.alpha = fadeAlpha
                canvas.drawRect(0f, 0f, docW, docH, paint)
            }
            "glow" -> {
                // Cinematic vignette / atmospheric rim glow
                paint.style = Paint.Style.FILL
                val glowShader = RadialGradient(
                    docW / 2f, docH / 2f, docW * 0.65f,
                    intArrayOf(Color.TRANSPARENT, Color.argb((160 * effect.intensity).toInt(), 0, 0, 0)),
                    floatArrayOf(0.6f, 1.0f),
                    Shader.TileMode.CLAMP
                )
                paint.shader = glowShader
                canvas.drawRect(0f, 0f, docW, docH, paint)
                paint.shader = null
            }
        }
    }

    private fun renderDialogue(
        canvas: Canvas,
        dialogue: DialogueDef,
        time: Float,
        docW: Float,
        docH: Float
    ) {
        val relTime = (time - dialogue.start).coerceAtLeast(0f)
        val charCount = (relTime * 35).toInt().coerceAtMost(dialogue.text.length)
        val visibleText = dialogue.text.take(charCount)

        val boxW = docW * 0.75f
        val boxH = 140f
        val boxX = (docW - boxW) / 2f
        val boxY = if (dialogue.position.equals("top", ignoreCase = true)) 60f else docH - boxH - 60f

        sharedDialogueRect.set(boxX, boxY, boxX + boxW, boxY + boxH)

        // Semi-transparent glassmorphic anime dialogue card
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(225, 13, 17, 26) // Deep studio dark with 88% opacity
        canvas.drawRoundRect(sharedDialogueRect, 20f, 20f, paint)

        // Neon border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = getCachedColor("#38BDF8", Color.CYAN)
        canvas.drawRoundRect(sharedDialogueRect, 20f, 20f, paint)

        // Character Name Tag Pill
        if (dialogue.character.isNotBlank()) {
            val pillW = 200f
            val pillH = 36f
            sharedPillRect.set(boxX + 24f, boxY - 18f, boxX + 24f + pillW, boxY + 18f)
            paint.style = Paint.Style.FILL
            paint.color = getCachedColor("#0284C7", Color.BLUE)
            canvas.drawRoundRect(sharedPillRect, 18f, 18f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.WHITE
            canvas.drawRoundRect(sharedPillRect, 18f, 18f, paint)

            textPaint.color = Color.WHITE
            textPaint.textSize = 22f
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(dialogue.character.uppercase(), sharedPillRect.centerX(), sharedPillRect.centerY() + 8f, textPaint)
        }

        // Dialogue Subtitle Text
        textPaint.color = Color.WHITE
        textPaint.textSize = 34f
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK)
        canvas.drawText(visibleText, boxX + 36f, boxY + 75f, textPaint)
        textPaint.clearShadowLayer()

        // Blinking typing cursor if still typing
        if (charCount < dialogue.text.length && ((time * 4).toInt() % 2 == 0)) {
            val textWidth = textPaint.measureText(visibleText)
            paint.style = Paint.Style.FILL
            paint.color = getCachedColor("#38BDF8", Color.CYAN)
            canvas.drawRect(boxX + 38f + textWidth, boxY + 48f, boxX + 44f + textWidth, boxY + 80f, paint)
        }
    }


    private data class CameraEval(
        val zoom: Float = 1.0f,
        val x: Float = 0f,
        val y: Float = 0f,
        val rotation: Float = 0f,
        val shake: Float = 0f
    )

    private fun evaluateCamera(cameraDef: CameraDef?, time: Float): CameraEval {
        if (cameraDef == null || cameraDef.keyframes.isEmpty()) {
            return CameraEval()
        }
        val kfs = cameraDef.keyframes.sortedBy { it.time }
        if (time <= kfs.first().time) {
            val k = kfs.first()
            return CameraEval(k.zoom, k.x, k.y, k.rotation, k.shake)
        }
        if (time >= kfs.last().time) {
            val k = kfs.last()
            return CameraEval(k.zoom, k.x, k.y, k.rotation, k.shake)
        }

        for (i in 0 until kfs.size - 1) {
            val k1 = kfs[i]
            val k2 = kfs[i + 1]
            if (time >= k1.time && time <= k2.time) {
                val span = (k2.time - k1.time).coerceAtLeast(0.001f)
                val rawT = ((time - k1.time) / span).coerceIn(0f, 1f)
                val t = applyEasing(rawT, k2.easing)
                return CameraEval(
                    zoom = k1.zoom + (k2.zoom - k1.zoom) * t,
                    x = k1.x + (k2.x - k1.x) * t,
                    y = k1.y + (k2.y - k1.y) * t,
                    rotation = k1.rotation + (k2.rotation - k1.rotation) * t,
                    shake = k1.shake + (k2.shake - k1.shake) * t
                )
            }
        }
        return CameraEval()
    }

    private inline fun interpolateKeyframes(
        keyframes: List<KeyframeDef>,
        time: Float,
        apply: (kf1: KeyframeDef, kf2: KeyframeDef, t: Float) -> Unit
    ) {
        if (keyframes.isEmpty()) return
        val sorted = keyframes.sortedBy { it.time }
        if (time <= sorted.first().time) {
            apply(sorted.first(), sorted.first(), 0f)
            return
        }
        if (time >= sorted.last().time) {
            apply(sorted.last(), sorted.last(), 1f)
            return
        }
        for (i in 0 until sorted.size - 1) {
            val k1 = sorted[i]
            val k2 = sorted[i + 1]
            if (time >= k1.time && time <= k2.time) {
                val span = (k2.time - k1.time).coerceAtLeast(0.001f)
                val rawT = ((time - k1.time) / span).coerceIn(0f, 1f)
                val t = applyEasing(rawT, k2.easing)
                apply(k1, k2, t)
                return
            }
        }
    }

    private fun applyEasing(t: Float, easing: Easing): Float {
        return when (easing) {
            Easing.LINEAR -> t
            Easing.EASE_IN -> t * t
            Easing.EASE_OUT -> t * (2f - t)
            Easing.EASE_IN_OUT -> if (t < 0.5f) 2f * t * t else -1f + (4f - 2f * t) * t
            Easing.STEP -> if (t < 1f) 0f else 1f
        }
    }
}
