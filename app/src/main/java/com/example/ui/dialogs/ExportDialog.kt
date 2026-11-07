package com.example.ui.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ExportConfig
import com.example.model.VideoFps
import com.example.model.VideoResolution
import com.example.ui.theme.*

@Composable
fun ExportDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onStartExport: (ExportConfig) -> Unit
) {
    var selectedResolution by remember { mutableStateOf(VideoResolution.RES_720P) }
    var selectedFps by remember { mutableStateOf(VideoFps.FPS_30) }
    var customFileName by remember { mutableStateOf(initialTitle.replace(" ", "_")) }
    var includeAudio by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Movie, contentDescription = null, tint = NeonCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export MP4 Video", fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Resolution Selector
                Text(
                    text = "Resolution:",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(6.dp))
                VideoResolution.values().forEach { res ->
                    val isSelected = (selectedResolution == res)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedResolution = res }
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedResolution = res },
                            colors = RadioButtonDefaults.colors(selectedColor = NeonCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = res.label,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Frame Rate Selector
                Text(
                    text = "Frame Rate:",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VideoFps.values().forEach { fpsOption ->
                        val isSelected = (selectedFps == fpsOption)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) NeonPurple else SurfaceDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) NeonPurple else BorderDark),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedFps = fpsOption }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    text = "${fpsOption.fps} FPS",
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Output File Name
                OutlinedTextField(
                    value = customFileName,
                    onValueChange = { customFileName = it },
                    label = { Text("Output File Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_file_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onStartExport(
                        ExportConfig(
                            resolution = selectedResolution,
                            fps = selectedFps,
                            includeAudio = includeAudio,
                            customFileName = customFileName
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = StudioBlack),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_export_button")
            ) {
                Text("Start Export", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
