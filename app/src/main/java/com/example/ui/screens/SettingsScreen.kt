package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.StudioHeader
import com.example.ui.theme.*
import com.example.viewmodel.StudioScreen
import com.example.viewmodel.StudioViewModel

@Composable
fun SettingsScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Obsidian,
        topBar = {
            StudioHeader(
                title = "Settings & XML Spec",
                subtitle = "Standard Anime XML Format v1.0",
                navigationIcon = Icons.Default.ArrowBack,
                onNavigationClick = { viewModel.popBack() }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Info Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = NeonCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ANIME XML SPECIFICATION 1.0",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Anime XML Studio is an offline-capable 2D anime animation engine that compiles XML instructions into synchronized keyframed animation frames and hardware-accelerated MP4 videos.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 18.sp)
                        )
                    }
                }
            }

            // Supported Tags Section
            item {
                Text(
                    text = "Supported XML Elements",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            item {
                SpecCard(
                    tag = "<anime title=\"...\" version=\"1.0\">",
                    desc = "Root document container. Declares document title and format version."
                )
            }

            item {
                SpecCard(
                    tag = "<settings width=\"1920\" height=\"1080\" fps=\"30\" duration=\"10\"/>",
                    desc = "Canvas resolution (width x height), target frames per second, and total duration in seconds."
                )
            }

            item {
                SpecCard(
                    tag = "<assets>\n  <background id=\"bg1\" src=\"...\"/>\n  <character id=\"hero\" src=\"...\"/>\n  <audio id=\"sfx1\" src=\"...\"/>\n</assets>",
                    desc = "Catalog of all image, background, and audio assets referenced by scenes."
                )
            }

            item {
                SpecCard(
                    tag = "<scene id=\"scene1\" start=\"0\" duration=\"5\" background=\"bg1\">",
                    desc = "Scene container with start timestamp, duration, and optional background reference."
                )
            }

            item {
                SpecCard(
                    tag = "<character ref=\"hero\" x=\"500\" y=\"600\" width=\"300\" height=\"500\" layer=\"1\">",
                    desc = "Character instance in scene with position (x, y), size, opacity, rotation, and z-order layer."
                )
            }

            item {
                SpecCard(
                    tag = "<animation type=\"move|scale|rotate|fade|action|expression\">\n  <keyframe time=\"0\" x=\"500\" y=\"600\" easing=\"ease_in_out\"/>\n</animation>",
                    desc = "Keyframe animation tracks. Easing supports 'linear', 'ease_in', 'ease_out', 'ease_in_out', 'step'."
                )
            }

            item {
                SpecCard(
                    tag = "<action name=\"walk|run|jump|idle|attack\"/>",
                    desc = "Procedural kinematic anime character animations."
                )
            }

            item {
                SpecCard(
                    tag = "<expression name=\"happy|angry|shocked|serious|smile\"/>",
                    desc = "Anime facial emotion badges: anger vein (💢), star glint (✨), sweatdrop (💦), eye flare."
                )
            }

            item {
                SpecCard(
                    tag = "<camera type=\"zoom|pan|shake|rotate\">\n  <keyframe time=\"0\" zoom=\"1.0\" x=\"0\" y=\"0\" shake=\"0\"/>\n</camera>",
                    desc = "Cinematic camera movements with dynamic zoom, panning, rotation, and screen shake."
                )
            }

            item {
                SpecCard(
                    tag = "<dialogue character=\"hero\" start=\"2\" duration=\"3\" position=\"bottom\" style=\"anime_box\">",
                    desc = "Anime subtitle dialogue box with speaker badge and typing text effect."
                )
            }

            item {
                SpecCard(
                    tag = "<effect type=\"rain|snow|particles|flash|fade_in|fade_out|glow\" intensity=\"1.0\"/>",
                    desc = "Atmospheric visual overlays and scene transitions."
                )
            }

            item {
                SpecCard(
                    tag = "<audio ref=\"sfx1\" start=\"1.5\" volume=\"1.0\" loop=\"false\"/>",
                    desc = "Synchronized audio sound effect trigger or background music track."
                )
            }

            // About App Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "About Studio",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Anime XML Studio 1.0\nBuilt with Kotlin, Jetpack Compose, Room, MediaCodec, and Android Canvas.\nFully offline and local.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 18.sp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpecCard(tag: String, desc: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = tag,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            )
        }
    }
}
