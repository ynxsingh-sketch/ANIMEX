package com.example.renderer

import android.content.Context
import android.graphics.*
import android.util.LruCache
import com.example.R
import java.io.File
import java.io.InputStream

class AssetResolver(private val context: Context) {

    // 64MB LRU Bitmap cache for silky smooth 60fps preview and high-res export
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    fun loadBitmap(src: String, projectDir: File? = null): Bitmap {
        val cacheKey = "$src:${projectDir?.absolutePath ?: ""}"
        memoryCache.get(cacheKey)?.let { return it }

        var bitmap: Bitmap? = null

        // 1. Check project local directory
        if (projectDir != null && projectDir.exists()) {
            val localFile = File(projectDir, src)
            if (localFile.exists()) {
                bitmap = BitmapFactory.decodeFile(localFile.absolutePath)
            }
        }

        // 2. Check absolute path on disk
        if (bitmap == null && (src.startsWith("/") || src.startsWith("file://"))) {
            val path = src.removePrefix("file://")
            val file = File(path)
            if (file.exists()) {
                bitmap = BitmapFactory.decodeFile(file.absolutePath)
            }
        }

        // 3. Check Android assets
        if (bitmap == null) {
            val assetPaths = listOf(
                src,
                "samples/$src",
                "samples/backgrounds/${File(src).name}",
                "samples/characters/${File(src).name}",
                "samples/characters/$src.png",
                "samples/backgrounds/$src.jpg"
            )
            for (path in assetPaths) {
                try {
                    val stream: InputStream = context.assets.open(path.removePrefix("assets/"))
                    bitmap = BitmapFactory.decodeStream(stream)
                    stream.close()
                    if (bitmap != null) break
                } catch (_: Exception) {}
            }
        }

        // 4. Check drawable resources
        if (bitmap == null) {
            val cleanName = File(src).nameWithoutExtension.lowercase()
                .replace("-", "_")
                .replace(" ", "_")
            val resId = context.resources.getIdentifier(cleanName, "drawable", context.packageName)
            if (resId != 0) {
                try {
                    bitmap = BitmapFactory.decodeResource(context.resources, resId)
                } catch (_: Exception) {}
            }
        }

        // 5. Fallback: generate high-quality anime placeholder card
        if (bitmap == null) {
            bitmap = createPlaceholder(src)
        }

        memoryCache.put(cacheKey, bitmap)
        return bitmap
    }

    private fun createPlaceholder(name: String): Bitmap {
        val isBg = name.contains("bg", ignoreCase = true) || name.contains("back", ignoreCase = true)
        val w = if (isBg) 1280 else 400
        val h = if (isBg) 720 else 700
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())

        // Stylish cyberpunk anime gradient
        val shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(
                if (isBg) Color.parseColor("#0F172A") else Color.parseColor("#1E1B4B"),
                if (isBg) Color.parseColor("#312E81") else Color.parseColor("#4338CA"),
                if (isBg) Color.parseColor("#0284C7") else Color.parseColor("#7C3AED")
            ),
            null,
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawRoundRect(rect, 24f, 24f, paint)

        // Accent border
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        paint.color = Color.parseColor("#38BDF8")
        canvas.drawRoundRect(rect, 24f, 24f, paint)

        // Silhouette icon
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#55FFFFFF")
        if (isBg) {
            // Draw landscape mountain/city shapes
            val path = Path().apply {
                moveTo(0f, h * 0.7f)
                lineTo(w * 0.3f, h * 0.45f)
                lineTo(w * 0.6f, h * 0.6f)
                lineTo(w * 0.85f, h * 0.4f)
                lineTo(w.toFloat(), h * 0.75f)
                lineTo(w.toFloat(), h.toFloat())
                lineTo(0f, h.toFloat())
                close()
            }
            canvas.drawPath(path, paint)
        } else {
            // Draw character anime head & shoulder silhouette
            val headRadius = w * 0.22f
            canvas.drawCircle(w * 0.5f, h * 0.35f, headRadius, paint)
            val bodyRect = RectF(w * 0.2f, h * 0.48f, w * 0.8f, h * 0.95f)
            canvas.drawRoundRect(bodyRect, 30f, 30f, paint)
        }

        // Text label
        paint.color = Color.WHITE
        paint.textSize = if (isBg) 42f else 32f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(name.takeLast(25), w * 0.5f, h * 0.85f, paint)

        return bmp
    }

    fun clearCache() {
        memoryCache.evictAll()
    }
}
