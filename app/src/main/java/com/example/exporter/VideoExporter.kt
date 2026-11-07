package com.example.exporter

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.model.*
import com.example.renderer.AnimeRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class VideoExporter(private val context: Context) {

    private val renderer = AnimeRenderer(context)

    private val _exportProgress = MutableStateFlow(ExportProgress())
    val exportProgress: StateFlow<ExportProgress> = _exportProgress.asStateFlow()

    @Volatile
    private var isCancelled = false

    fun cancelExport() {
        isCancelled = true
    }

    suspend fun exportVideo(
        doc: AnimeDoc,
        config: ExportConfig,
        projectDir: File? = null
    ): ExportProgress = withContext(Dispatchers.Default) {
        isCancelled = false

        val width = config.resolution.width
        val height = config.resolution.height
        val fps = config.fps.fps
        val duration = doc.totalDuration.coerceAtLeast(1f)
        val totalFrames = (duration * fps).toInt().coerceAtLeast(1)

        val tempOutputFile = File(context.cacheDir, "anime_export_${System.currentTimeMillis()}.mp4")

        _exportProgress.value = ExportProgress(
            step = ExportStep.PREPARING,
            currentFrame = 0,
            totalFrames = totalFrames,
            progress = 0f,
            message = "Initializing H.264 video encoder..."
        )

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var frameBitmap: Bitmap? = null

        try {
            // Find supported color format
            val mimeType = MediaFormat.MIMETYPE_VIDEO_AVC
            val colorFormat = chooseColorFormat(mimeType)

            val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                setInteger(MediaFormat.KEY_BIT_RATE, config.resolution.defaultBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createEncoderByType(mimeType)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(tempOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var muxerStarted = false

            frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frameBitmap)
            val pixels = IntArray(width * height)
            val yuvFrameBuffer = ByteArray(width * height * 3 / 2)

            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = 10_000L

            var framesSubmitted = 0
            var outputEosReached = false

            _exportProgress.value = ExportProgress(
                step = ExportStep.RENDERING_VIDEO,
                currentFrame = 0,
                totalFrames = totalFrames,
                progress = 0f,
                message = "Rendering animation frames..."
            )

            while (!outputEosReached) {
                if (isCancelled) {
                    _exportProgress.value = ExportProgress(
                        step = ExportStep.CANCELLED,
                        message = "Export was cancelled by user."
                    )
                    tempOutputFile.delete()
                    return@withContext _exportProgress.value
                }

                // 1. Submit input frame to encoder if still frames left
                if (framesSubmitted < totalFrames) {
                    val inputBufferIndex = encoder.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()

                            val timeSec = framesSubmitted.toFloat() / fps.toFloat()
                            val ptsUs = (framesSubmitted.toLong() * 1_000_000L) / fps.toLong()

                            // Render frame onto canvas
                            canvas.drawColor(android.graphics.Color.BLACK)
                            renderer.render(canvas, doc, timeSec, width, height, projectDir)

                            // Read pixels and convert to NV12 / YUV420
                            frameBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                            encodeYUV420(pixels, yuvFrameBuffer, width, height, colorFormat)

                            inputBuffer.put(yuvFrameBuffer)

                            val isLastFrame = (framesSubmitted == totalFrames - 1)
                            val flags = if (isLastFrame) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0

                            encoder.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                yuvFrameBuffer.size,
                                ptsUs,
                                flags
                            )

                            framesSubmitted++

                            val prog = framesSubmitted.toFloat() / totalFrames.toFloat()
                            _exportProgress.value = ExportProgress(
                                step = ExportStep.RENDERING_VIDEO,
                                currentFrame = framesSubmitted,
                                totalFrames = totalFrames,
                                progress = prog,
                                message = "Rendering frame $framesSubmitted / $totalFrames (${(prog * 100).toInt()}%)"
                            )
                        }
                    }
                }

                // 2. Drain output from encoder
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (muxerStarted) {
                            throw RuntimeException("Output format changed after muxer started")
                        }
                        val newFormat = encoder.outputFormat
                        videoTrackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null && muxerStarted) {
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size != 0) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
                            }
                        }
                        encoder.releaseOutputBuffer(outputBufferIndex, false)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputEosReached = true
                        }
                    }
                }
            }

            // Stop and release encoder & muxer
            try {
                encoder.stop()
                encoder.release()
                encoder = null
            } catch (_: Exception) {}

            try {
                if (muxerStarted) {
                    muxer.stop()
                }
                muxer.release()
                muxer = null
            } catch (_: Exception) {}

            // Save to MediaStore (Movies/AnimeStudio)
            _exportProgress.value = ExportProgress(
                step = ExportStep.FINALIZING,
                currentFrame = totalFrames,
                totalFrames = totalFrames,
                progress = 1.0f,
                message = "Saving MP4 video to device..."
            )

            val baseName = if (config.customFileName.isNotBlank()) config.customFileName
            else "${doc.title.replace(" ", "_")}_${System.currentTimeMillis()}"
            val finalFileName = "$baseName.mp4"

            val savedUri = saveToMediaStore(tempOutputFile, finalFileName)

            val result = ExportProgress(
                step = ExportStep.COMPLETED,
                currentFrame = totalFrames,
                totalFrames = totalFrames,
                progress = 1.0f,
                message = "Export complete! Saved to Movies/AnimeStudio.",
                outputUri = savedUri,
                outputFilePath = tempOutputFile.absolutePath
            )
            _exportProgress.value = result
            return@withContext result

        } catch (e: Exception) {
            val errorMsg = "Video export failed: ${e.localizedMessage ?: e.message}"
            val result = ExportProgress(
                step = ExportStep.ERROR,
                error = errorMsg,
                message = errorMsg
            )
            _exportProgress.value = result
            tempOutputFile.delete()
            return@withContext result
        } finally {
            try {
                encoder?.release()
                muxer?.release()
                frameBitmap?.recycle()
            } catch (_: Exception) {}
        }
    }

    private fun chooseColorFormat(mimeType: String): Int {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            val types = info.supportedTypes
            for (type in types) {
                if (type.equals(mimeType, ignoreCase = true)) {
                    val caps = info.getCapabilitiesForType(type)
                    for (format in caps.colorFormats) {
                        if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                            return format // NV12 - standard on mobile
                        }
                    }
                    for (format in caps.colorFormats) {
                        if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                            return format // I420
                        }
                    }
                }
            }
        }
        return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
    }

    private fun encodeYUV420(
        argb: IntArray,
        yuv: ByteArray,
        width: Int,
        height: Int,
        colorFormat: Int
    ) {
        val isSemiPlanar = (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
        val frameSize = width * height
        var yIndex = 0
        var uIndex = frameSize
        var vIndex = frameSize + frameSize / 4

        var a: Int
        var r: Int
        var g: Int
        var b: Int
        var y: Int
        var u: Int
        var v: Int
        var index = 0

        for (j in 0 until height) {
            for (i in 0 until width) {
                a = (argb[index] and -0x1000000) shr 24
                r = (argb[index] and 0x00FF0000) shr 16
                g = (argb[index] and 0x0000FF00) shr 8
                b = (argb[index] and 0x000000FF)

                y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                if (j % 2 == 0 && index % 2 == 0) {
                    if (isSemiPlanar) {
                        // NV12: Interleaved U and V
                        yuv[uIndex++] = u.coerceIn(0, 255).toByte()
                        yuv[uIndex++] = v.coerceIn(0, 255).toByte()
                    } else {
                        // I420: Planar U then V
                        yuv[uIndex++] = u.coerceIn(0, 255).toByte()
                        yuv[vIndex++] = v.coerceIn(0, 255).toByte()
                    }
                }
                index++
            }
        }
    }

    private fun saveToMediaStore(sourceFile: File, displayName: String): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/AnimeStudio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val videoUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

        try {
            resolver.openOutputStream(videoUri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(videoUri, contentValues, null, null)
            }
            return videoUri
        } catch (e: Exception) {
            resolver.delete(videoUri, null, null)
            return null
        }
    }
}
