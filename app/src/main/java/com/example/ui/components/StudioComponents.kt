package com.example.ui.components

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ExportProgress
import com.example.model.ExportStep
import com.example.ui.theme.*

@Composable
fun StudioHeader(
    title: String,
    subtitle: String? = null,
    navigationIcon: ImageVector? = null,
    onNavigationClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        color = StudioBlack,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navigationIcon != null && onNavigationClick != null) {
                IconButton(
                    onClick = onNavigationClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("nav_back_button")
                ) {
                    Icon(
                        imageVector = navigationIcon,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = NeonCyan,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        }
    }
}

@Composable
fun ExportProgressModal(
    progress: ExportProgress,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    if (progress.step != ExportStep.IDLE) {
        AlertDialog(
            onDismissRequest = {
                if (progress.step == ExportStep.COMPLETED || progress.step == ExportStep.ERROR) {
                    onDismiss()
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = SurfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (progress.step) {
                        ExportStep.COMPLETED -> Icons.Default.CheckCircle
                        ExportStep.ERROR -> Icons.Default.Error
                        ExportStep.CANCELLED -> Icons.Default.Cancel
                        else -> Icons.Default.Videocam
                    }
                    val iconColor = when (progress.step) {
                        ExportStep.COMPLETED -> MintSuccess
                        ExportStep.ERROR -> CrimsonAlert
                        ExportStep.CANCELLED -> TextMuted
                        else -> NeonCyan
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when (progress.step) {
                            ExportStep.COMPLETED -> "Export Complete!"
                            ExportStep.ERROR -> "Export Failed"
                            ExportStep.CANCELLED -> "Export Cancelled"
                            ExportStep.FINALIZING -> "Saving Video..."
                            else -> "Exporting MP4 Video"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    if (progress.step == ExportStep.RENDERING_VIDEO || progress.step == ExportStep.PREPARING) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { progress.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.size(96.dp),
                                strokeWidth = 8.dp,
                                color = NeonCyan,
                                trackColor = BorderDark
                            )
                            Text(
                                text = "${(progress.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = progress.message,
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    } else if (progress.step == ExportStep.COMPLETED) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MintSuccess.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Your MP4 anime video is ready!",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Saved in device Movies/AnimeStudio directory.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = progress.message,
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                    }
                }
            },
            confirmButton = {
                if (progress.step == ExportStep.COMPLETED) {
                    Row {
                        if (progress.outputUri != null) {
                            Button(
                                onClick = {
                                    val playIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(progress.outputUri, "video/mp4")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(playIntent, "Play Video"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = StudioBlack),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("play_video_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play Video", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "video/mp4"
                                        putExtra(Intent.EXTRA_STREAM, progress.outputUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Anime Video"))
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("share_video_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                } else if (progress.step == ExportStep.ERROR || progress.step == ExportStep.CANCELLED) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = TextPrimary)
                    }
                }
            },
            dismissButton = {
                if (progress.step == ExportStep.RENDERING_VIDEO || progress.step == ExportStep.PREPARING) {
                    OutlinedButton(
                        onClick = onCancel,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonAlert),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel Export", color = CrimsonAlert)
                    }
                }
            }
        )
    }
}
