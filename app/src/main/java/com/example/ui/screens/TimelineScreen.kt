package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AnimeDoc
import com.example.ui.components.StudioHeader
import com.example.ui.theme.*
import com.example.viewmodel.StudioScreen
import com.example.viewmodel.StudioViewModel
import kotlin.math.roundToInt

@Composable
fun TimelineScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val parsedDoc by viewModel.parsedDoc.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()

    val doc = parsedDoc
    val totalDur = doc?.totalDuration ?: 10.0f
    val pixelsPerSecond = 80.dp
    val timelineWidth = (totalDur * 80).dp.coerceAtLeast(360.dp)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Obsidian,
        topBar = {
            StudioHeader(
                title = "Visual Timeline",
                subtitle = "${currentProject?.title ?: "Project"} • ${formatTimecode(currentTime)}",
                navigationIcon = Icons.Default.ArrowBack,
                onNavigationClick = { viewModel.popBack() },
                actions = {
                    IconButton(
                        onClick = { viewModel.togglePlay() },
                        modifier = Modifier.testTag("timeline_play_toggle")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = NeonCyan
                        )
                    }
                    IconButton(onClick = { viewModel.navigateTo(StudioScreen.EDITOR) }) {
                        Icon(Icons.Default.Code, contentDescription = "Edit XML", tint = TextPrimary)
                    }
                }
            )
        }
    ) { paddingValues ->
        if (doc == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No anime document loaded", color = TextSecondary)
            }
        } else {
            val horizontalScroll = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Header Inspector Bar
                Surface(
                    color = StudioBlack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "PLAYHEAD: ",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                            )
                            Text(
                                text = formatTimecode(currentTime),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TrackLegend(label = "Scene", color = Color(0xFF1E293B))
                            TrackLegend(label = "Char", color = NeonCyan)
                            TrackLegend(label = "Camera", color = NeonPurple)
                            TrackLegend(label = "Dialogue", color = SunsetGold)
                            TrackLegend(label = "FX", color = AnimeMagenta)
                        }
                    }
                }

                // Scrollable Multi-Track Timeline Board
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScroll)
                ) {
                    Column(
                        modifier = Modifier
                            .width(timelineWidth + 120.dp)
                            .fillMaxHeight()
                            .padding(start = 100.dp, end = 20.dp)
                            .pointerInput(totalDur) {
                                detectTapGestures { offset ->
                                    val tapSeconds = (offset.x / 80.dp.toPx()).coerceIn(0f, totalDur)
                                    viewModel.seekTo(tapSeconds)
                                }
                            }
                    ) {
                        // 1. Time Ruler
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(SurfaceDark)
                        ) {
                            val secondsCount = totalDur.toInt() + 1
                            for (sec in 0..secondsCount) {
                                Box(
                                    modifier = Modifier
                                        .width(pixelsPerSecond)
                                        .fillMaxHeight()
                                ) {
                                    Text(
                                        text = "${sec}s",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        ),
                                        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                                    )
                                    // Tick mark
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(8.dp)
                                            .background(BorderDark)
                                            .align(Alignment.BottomStart)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Track 1: Scenes
                            item {
                                TrackRow(title = "Scenes") {
                                    for (scene in doc.scenes) {
                                        val startOffset = (scene.start * 80).dp
                                        val blockWidth = (scene.duration * 80).dp.coerceAtLeast(30.dp)
                                        TrackBlock(
                                            offset = startOffset,
                                            width = blockWidth,
                                            label = scene.id,
                                            color = Color(0xFF1E293B),
                                            borderColor = ElectricCyan
                                        )
                                    }
                                }
                            }

                            // Track 2: Characters
                            val allChars = doc.scenes.flatMap { it.characters }.distinctBy { it.ref }
                            for (ch in allChars) {
                                item {
                                    TrackRow(title = ch.ref) {
                                        for (scene in doc.scenes) {
                                            val cInst = scene.characters.find { it.ref == ch.ref }
                                            if (cInst != null) {
                                                val startOffset = (scene.start * 80).dp
                                                val blockWidth = (scene.duration * 80).dp
                                                TrackBlock(
                                                    offset = startOffset,
                                                    width = blockWidth,
                                                    label = "${ch.ref} (Layer ${cInst.layer})",
                                                    color = Color(0xFF0F3B4E),
                                                    borderColor = NeonCyan
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Track 3: Camera
                            item {
                                TrackRow(title = "Camera") {
                                    for (scene in doc.scenes) {
                                        val cam = scene.camera
                                        if (cam != null && cam.keyframes.isNotEmpty()) {
                                            val startOffset = (scene.start * 80).dp
                                            val blockWidth = (scene.duration * 80).dp
                                            TrackBlock(
                                                offset = startOffset,
                                                width = blockWidth,
                                                label = "Cam: ${cam.type} (${cam.keyframes.size} kfs)",
                                                color = Color(0xFF381A51),
                                                borderColor = NeonPurple
                                            )
                                        }
                                    }
                                }
                            }

                            // Track 4: Dialogue
                            item {
                                TrackRow(title = "Dialogue") {
                                    for (scene in doc.scenes) {
                                        for (d in scene.dialogues) {
                                            val startOffset = ((scene.start + d.start) * 80).dp
                                            val blockWidth = (d.duration * 80).dp
                                            TrackBlock(
                                                offset = startOffset,
                                                width = blockWidth,
                                                label = "${d.character}: \"${d.text.take(15)}...\"",
                                                color = Color(0xFF452B0F),
                                                borderColor = SunsetGold
                                            )
                                        }
                                    }
                                }
                            }

                            // Track 5: Visual Effects
                            item {
                                TrackRow(title = "Effects") {
                                    for (scene in doc.scenes) {
                                        for (eff in scene.effects) {
                                            val startOffset = ((scene.start + eff.start) * 80).dp
                                            val blockWidth = (eff.duration * 80).dp
                                            TrackBlock(
                                                offset = startOffset,
                                                width = blockWidth,
                                                label = eff.type.uppercase(),
                                                color = Color(0xFF4B112D),
                                                borderColor = AnimeMagenta
                                            )
                                        }
                                    }
                                }
                            }

                            // Track 6: Audio
                            item {
                                TrackRow(title = "Audio") {
                                    for (scene in doc.scenes) {
                                        for (aud in scene.audio) {
                                            val startOffset = ((scene.start + aud.start) * 80).dp
                                            val blockWidth = ((aud.duration ?: 3.0f) * 80).dp
                                            TrackBlock(
                                                offset = startOffset,
                                                width = blockWidth,
                                                label = "♫ ${aud.ref}",
                                                color = Color(0xFF133E2B),
                                                borderColor = MintSuccess
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Vertical Red Playhead Indicator
                    val playheadX = (currentTime * 80).dp + 100.dp
                    Box(
                        modifier = Modifier
                            .offset(x = playheadX)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(CrimsonAlert)
                    ) {
                        // Playhead handle
                        Surface(
                            shape = CircleShape,
                            color = CrimsonAlert,
                            modifier = Modifier
                                .size(10.dp)
                                .offset(x = (-4).dp, y = 10.dp)
                        ) {}
                    }

                    // Left static track header labels
                    Column(
                        modifier = Modifier
                            .width(100.dp)
                            .fillMaxHeight()
                            .background(StudioBlack)
                            .padding(top = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TrackLabel("Scenes")
                        for (ch in doc.scenes.flatMap { it.characters }.distinctBy { it.ref }) {
                            TrackLabel(ch.ref)
                        }
                        TrackLabel("Camera")
                        TrackLabel("Dialogue")
                        TrackLabel("Effects")
                        TrackLabel("Audio")
                    }
                }
            }
        }
    }
}

@Composable
fun TrackRow(
    title: String,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(SurfaceElevated.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .border(0.5.dp, BorderDark, RoundedCornerShape(6.dp))
    ) {
        content()
    }
}

@Composable
fun TrackBlock(
    offset: androidx.compose.ui.unit.Dp,
    width: androidx.compose.ui.unit.Dp,
    label: String,
    color: Color,
    borderColor: Color
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .offset(x = offset)
            .width(width)
            .height(34.dp)
            .padding(2.dp)
    ) {
        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 6.dp)) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TrackLabel(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TrackLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = label, color = TextSecondary, fontSize = 9.sp)
    }
}
