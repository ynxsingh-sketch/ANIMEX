package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AssetType
import com.example.renderer.AssetResolver
import com.example.ui.components.StudioHeader
import com.example.ui.theme.*
import com.example.viewmodel.StudioScreen
import com.example.viewmodel.StudioViewModel
import java.io.File
import java.io.FileOutputStream

data class AssetItem(
    val id: String,
    val src: String,
    val type: AssetType,
    val label: String,
    val isBundled: Boolean = true
)

@Composable
fun AssetManagerScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "Characters", "Backgrounds", "Audio")

    val assetResolver = remember { AssetResolver(context) }

    // Bundled + Imported project assets
    val bundledAssets = remember {
        listOf(
            AssetItem("bg_cyber", "bg_cyber_tokyo.jpg", AssetType.BACKGROUND, "Cyber Tokyo Alley"),
            AssetItem("bg_shrine", "bg_cherry_temple.jpg", AssetType.BACKGROUND, "Cherry Blossom Temple"),
            AssetItem("hero_ren", "char_hero_ren.png", AssetType.CHARACTER, "Ren (Protagonist)"),
            AssetItem("ayame_ninja", "char_ayame_ninja.png", AssetType.CHARACTER, "Ayame (Shinobi)"),
            AssetItem("bgm_cyber", "bgm_cyber_battle.wav", AssetType.AUDIO, "Cyber Battle BGM"),
            AssetItem("sfx_slash", "sfx_slash.wav", AssetType.AUDIO, "Sword Slash SFX"),
            AssetItem("sfx_laser", "sfx_laser.wav", AssetType.AUDIO, "Laser Strike SFX"),
            AssetItem("sfx_energy", "sfx_energy_up.wav", AssetType.AUDIO, "Energy Charge SFX"),
            AssetItem("sfx_impact", "sfx_impact.wav", AssetType.AUDIO, "Heavy Impact SFX")
        )
    }

    val customAssets = remember { mutableStateListOf<AssetItem>() }
    val allAssets = remember(customAssets.size) { bundledAssets + customAssets }

    val filteredAssets = remember(selectedTab, allAssets) {
        when (selectedTab) {
            1 -> allAssets.filter { it.type == AssetType.CHARACTER }
            2 -> allAssets.filter { it.type == AssetType.BACKGROUND }
            3 -> allAssets.filter { it.type == AssetType.AUDIO }
            else -> allAssets
        }
    }

    // SAF File Pickers for importing assets
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val fileName = "custom_img_${System.currentTimeMillis()}.png"
            val destFile = File(context.filesDir, fileName)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val newId = "custom_${customAssets.size + 1}"
                val isBg = destFile.name.contains("bg", ignoreCase = true)
                customAssets.add(
                    AssetItem(
                        id = newId,
                        src = destFile.absolutePath,
                        type = if (isBg) AssetType.BACKGROUND else AssetType.CHARACTER,
                        label = "Imported Image ($newId)",
                        isBundled = false
                    )
                )
                Toast.makeText(context, "Image asset imported!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val fileName = "custom_aud_${System.currentTimeMillis()}.wav"
            val destFile = File(context.filesDir, fileName)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val newId = "audio_${customAssets.size + 1}"
                customAssets.add(
                    AssetItem(
                        id = newId,
                        src = destFile.absolutePath,
                        type = AssetType.AUDIO,
                        label = "Imported Audio ($newId)",
                        isBundled = false
                    )
                )
                Toast.makeText(context, "Audio asset imported!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Audio import failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Obsidian,
        topBar = {
            StudioHeader(
                title = "Asset Manager",
                subtitle = "${allAssets.size} Assets Available",
                navigationIcon = Icons.Default.ArrowBack,
                onNavigationClick = { viewModel.popBack() },
                actions = {
                    IconButton(onClick = { viewModel.navigateTo(StudioScreen.EDITOR) }) {
                        Icon(Icons.Default.Code, contentDescription = "Editor", tint = TextPrimary)
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                SmallFloatingActionButton(
                    onClick = { audioPicker.launch("audio/*") },
                    containerColor = NeonPurple,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.AudioFile, contentDescription = "Import Audio")
                }

                ExtendedFloatingActionButton(
                    onClick = { imagePicker.launch("image/*") },
                    containerColor = NeonCyan,
                    contentColor = StudioBlack,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("import_asset_fab")
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Image", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Category Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = StudioBlack,
                contentColor = NeonCyan,
                divider = { HorizontalDivider(color = BorderDark) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = (selectedTab == index),
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) NeonCyan else TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            // Assets Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredAssets, key = { it.id }) { asset ->
                    AssetCard(
                        asset = asset,
                        resolver = assetResolver,
                        onCopyXml = {
                            val tag = when (asset.type) {
                                AssetType.BACKGROUND -> "<background id=\"${asset.id}\" src=\"${asset.src}\"/>"
                                AssetType.CHARACTER -> "<character id=\"${asset.id}\" src=\"${asset.src}\"/>"
                                AssetType.AUDIO -> "<audio id=\"${asset.id}\" src=\"${asset.src}\"/>"
                            }
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clip.setPrimaryClip(ClipData.newPlainText("XML Tag", tag))
                            Toast.makeText(context, "Copied XML tag: $tag", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // Space for FAB
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
fun AssetCard(
    asset: AssetItem,
    resolver: AssetResolver,
    onCopyXml: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Visual Preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (asset.type == AssetType.AUDIO) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = NeonPurple.copy(alpha = 0.2f),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = NeonPurple,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Audio Track", color = TextSecondary, fontSize = 11.sp)
                    }
                } else {
                    val bitmap = remember(asset.src) {
                        try {
                            resolver.loadBitmap(asset.src)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = asset.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.BrokenImage, contentDescription = null, tint = TextMuted)
                    }
                }

                // Asset Type Pill
                val typeColor = when (asset.type) {
                    AssetType.BACKGROUND -> ElectricCyan
                    AssetType.CHARACTER -> NeonPurple
                    AssetType.AUDIO -> SunsetGold
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, typeColor),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = asset.type.name,
                        color = typeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Card Body
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = asset.label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "ID: ${asset.id}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = NeonCyan,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                FilledTonalButton(
                    onClick = onCopyXml,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SurfaceElevated,
                        contentColor = TextPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Tag", fontSize = 11.sp)
                }
            }
        }
    }
}
