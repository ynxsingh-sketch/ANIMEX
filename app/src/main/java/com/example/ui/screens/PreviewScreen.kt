package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AnimeDoc
import com.example.renderer.AnimeRenderer
import com.example.ui.components.StudioHeader
import com.example.ui.theme.*
import com.example.viewmodel.StudioScreen
import com.example.viewmodel.StudioViewModel

@Composable
fun PreviewScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val parsedDoc by viewModel.parsedDoc.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val isLooping by viewModel.isLooping.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()

    val renderer = remember { AnimeRenderer(context) }
    var isFullscreen by remember { mutableStateOf(false) }

    val doc = parsedDoc

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Obsidian,
        topBar = {
            if (!isFullscreen) {
                StudioHeader(
                    title = currentProject?.title ?: doc?.title ?: "Anime Preview",
                    subtitle = doc?.let { "${it.settings.width}x${it.settings.height} • ${it.settings.fps} FPS" },
                    navigationIcon = Icons.Default.ArrowBack,
                    onNavigationClick = { viewModel.popBack() },
                    actions = {
                        IconButton(
                            onClick = { viewModel.navigateTo(StudioScreen.EDITOR) },
                            modifier = Modifier.testTag("nav_to_editor_button")
                        ) {
                            Icon(Icons.Default.Code, contentDescription = "Edit XML", tint = TextPrimary)
                        }
                        IconButton(
                            onClick = { viewModel.navigateTo(StudioScreen.TIMELINE) },
                            modifier = Modifier.testTag("nav_to_timeline_button")
                        ) {
                            Icon(Icons.Default.Timeline, contentDescription = "Timeline", tint = TextPrimary)
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        if (doc == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = SunsetGold,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No Animation Loaded", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(onClick = { viewModel.navigateTo(StudioScreen.EDITOR) }) {
                        Text("Open XML Editor")
                    }
                }
            }
        } else {
            val totalDur = doc.totalDuration.coerceAtLeast(0.1f)
            val currentFrame = (currentTime * doc.settings.fps).toInt()
            val totalFrames = (totalDur * doc.settings.fps).toInt()
            val activeSceneIndex = doc.scenes.indexOfFirst { currentTime >= it.start && currentTime < it.end }
                .let { if (it >= 0) it + 1 else doc.scenes.size }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isFullscreen) PaddingValues(0.dp) else paddingValues)
            ) {
                // Video Preview Canvas Player Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (isFullscreen) 1f else 0.55f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val aspectRatio = doc.settings.aspectRatio
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspectRatio)
                            .background(Color.Black)
                            .testTag("anime_preview_canvas_container")
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width.toInt().coerceAtLeast(1)
                            val h = size.height.toInt().coerceAtLeast(1)
                            renderer.render(
                                canvas = drawContext.canvas.nativeCanvas,
                                doc = doc,
                                currentTime = currentTime,
                                targetWidth = w,
                                targetHeight = h
                            )
                        }

                        // Fullscreen Toggle in corner
                        IconButton(
                            onClick = { isFullscreen = !isFullscreen },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color(0x88000000), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.White
                            )
                        }

                        // Scene & Resolution Overlay badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xAA0D111A),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(if (isPlaying) MintSuccess else SunsetGold, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Scene $activeSceneIndex/${doc.scenes.size} • Frame $currentFrame/$totalFrames",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Controls Section (hidden in full-screen)
                if (!isFullscreen) {
                    Column(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxWidth()
                            .background(StudioBlack)
                            .padding(16.dp)
                    ) {
                        // Timecodes and Frame Counter
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTimecode(currentTime),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            )
                            Text(
                                text = "Duration: ${formatTimecode(totalDur)}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Scrubbing Slider
                        Slider(
                            value = currentTime.coerceIn(0f, totalDur),
                            onValueChange = { viewModel.seekTo(it) },
                            valueRange = 0f..totalDur,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = NeonCyan,
                                inactiveTrackColor = BorderDark
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("playback_scrubber_slider")
                        )

                        // Transport Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Restart
                            IconButton(onClick = { viewModel.restart() }) {
                                Icon(Icons.Default.Replay, contentDescription = "Restart", tint = TextPrimary)
                            }

                            // Step -1 Frame
                            IconButton(onClick = { viewModel.stepFrame(-1) }) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "-1 Frame", tint = TextPrimary)
                            }

                            // Play / Pause Major Action
                            FilledIconButton(
                                onClick = { viewModel.togglePlay() },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = NeonCyan,
                                    contentColor = StudioBlack
                                ),
                                modifier = Modifier
                                    .size(56.dp)
                                    .testTag("play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // Step +1 Frame
                            IconButton(onClick = { viewModel.stepFrame(1) }) {
                                Icon(Icons.Default.SkipNext, contentDescription = "+1 Frame", tint = TextPrimary)
                            }

                            // Loop Toggle
                            IconButton(onClick = { viewModel.toggleLooping() }) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = "Loop",
                                    tint = if (isLooping) NeonCyan else TextMuted
                                )
                            }

                            // Mute Toggle
                            IconButton(onClick = { viewModel.toggleMute() }) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "Mute",
                                    tint = if (isMuted) CrimsonAlert else TextPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Playback Speed Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
                            speeds.forEach { speed ->
                                val isSelected = (playbackSpeed == speed)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) NeonPurple else SurfaceElevated,
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.setPlaybackSpeed(speed) }
                                ) {
                                    Text(
                                        text = "${speed}x",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isSelected) Color.White else TextSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // EXPORT VIDEO ACTION BUTTON
                        Button(
                            onClick = { viewModel.openExportDialog() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonPurple,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("open_export_dialog_button")
                        ) {
                            Icon(Icons.Default.MovieCreation, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("EXPORT MP4 VIDEO", fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                        }
                    }
                }
            }
        }
    }
}

fun formatTimecode(seconds: Float): String {
    val totalSec = seconds.toInt()
    val min = totalSec / 60
    val sec = totalSec % 60
    val millis = ((seconds - totalSec) * 100).toInt()
    return "%02d:%02d.%02d".format(min, sec, millis)
}
