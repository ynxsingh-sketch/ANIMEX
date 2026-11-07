package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.IssueLevel
import com.example.parser.XmlSyntaxHighlighter
import com.example.ui.components.StudioHeader
import com.example.ui.theme.*
import com.example.viewmodel.StudioScreen
import com.example.viewmodel.StudioViewModel

@Composable
fun XmlEditorScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val xmlCode by viewModel.xmlCode.collectAsState()
    val validationResult by viewModel.validationResult.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()

    var showSnippetsMenu by remember { mutableStateOf(false) }
    var showValidationDetails by remember { mutableStateOf(false) }

    val lines = remember(xmlCode) { xmlCode.lines() }
    val lineCount = lines.size.coerceAtLeast(1)

    // Compute error line if any
    val errorLine = validationResult?.issues?.find { it.level == IssueLevel.ERROR }?.line

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Obsidian,
        topBar = {
            StudioHeader(
                title = currentProject?.title ?: "XML Editor",
                subtitle = "Anime XML Format v1.0",
                navigationIcon = Icons.Default.ArrowBack,
                onNavigationClick = { viewModel.popBack() },
                actions = {
                    IconButton(
                        onClick = { viewModel.undo() },
                        modifier = Modifier.testTag("undo_button")
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo", tint = TextPrimary)
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        modifier = Modifier.testTag("redo_button")
                    ) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo", tint = TextPrimary)
                    }
                    IconButton(
                        onClick = { viewModel.saveCurrentProject() },
                        modifier = Modifier.testTag("save_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save", tint = NeonCyan)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = StudioBlack,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column {
                    // Validation Status Bar
                    val res = validationResult
                    val barColor = when {
                        res == null -> SurfaceElevated
                        res.isValid -> MintSuccess.copy(alpha = 0.2f)
                        else -> CrimsonAlert.copy(alpha = 0.2f)
                    }
                    val textColor = when {
                        res == null -> TextSecondary
                        res.isValid -> MintSuccess
                        else -> CrimsonAlert
                    }
                    val icon = when {
                        res == null -> Icons.Default.Info
                        res.isValid -> Icons.Default.CheckCircle
                        else -> Icons.Default.Warning
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(barColor)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = res?.summary ?: "Click VALIDATE XML to inspect instructions",
                            style = MaterialTheme.typography.bodySmall.copy(color = textColor),
                            modifier = Modifier.weight(1f)
                        )
                        if (res != null && res.issues.isNotEmpty()) {
                            TextButton(
                                onClick = { showValidationDetails = !showValidationDetails },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    if (showValidationDetails) "Hide" else "Issues (${res.issues.size})",
                                    color = textColor,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Action Buttons Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clip.primaryClip?.getItemAt(0)?.text?.let { text ->
                                    val pasted = text.toString().trim()
                                    if (pasted.isNotBlank()) {
                                        viewModel.updateXmlCode(pasted)
                                        viewModel.generatePreview()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple),
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("paste_and_preview_button")
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PASTE & RUN", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.validateXml() },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                            modifier = Modifier
                                .weight(0.9f)
                                .testTag("validate_xml_button")
                        ) {
                            Icon(Icons.Default.Rule, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("VALIDATE", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Button(
                            onClick = { viewModel.generatePreview() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = StudioBlack),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("generate_preview_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PREVIEW", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Quick Snippets / Edit Tools Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(SurfaceDark)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clipboard actions
                SnippetChip("Paste", Icons.Default.ContentPaste) {
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clip.primaryClip?.getItemAt(0)?.text?.let { text ->
                        viewModel.updateXmlCode(text.toString())
                        viewModel.validateXml()
                    }
                }
                SnippetChip("Copy", Icons.Default.ContentCopy) {
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clip.setPrimaryClip(ClipData.newPlainText("Anime XML", xmlCode))
                }
                SnippetChip("Clear", Icons.Default.Clear) {
                    viewModel.updateXmlCode("")
                }

                VerticalDivider(modifier = Modifier.height(20.dp), color = BorderDark)

                // Quick Insert Snippets
                SnippetChip("+ Character", Icons.Default.Person) {
                    viewModel.insertSnippet(
                        """
        <character ref="hero_ren" x="960" y="650" width="400" height="700">
            <animation type="action" name="idle"/>
        </character>
                        """.trimIndent()
                    )
                }
                SnippetChip("+ Move Anim", Icons.Default.OpenWith) {
                    viewModel.insertSnippet(
                        """
        <animation type="move">
            <keyframe time="0.0" x="500" y="600" easing="ease_in_out"/>
            <keyframe time="2.5" x="1200" y="600" easing="ease_in_out"/>
        </animation>
                        """.trimIndent()
                    )
                }
                SnippetChip("+ Camera Zoom", Icons.Default.ZoomIn) {
                    viewModel.insertSnippet(
                        """
    <camera type="zoom">
        <keyframe time="0.0" zoom="1.0" x="0" y="0" shake="0"/>
        <keyframe time="2.0" zoom="1.4" x="100" y="-30" shake="5"/>
    </camera>
                        """.trimIndent()
                    )
                }
                SnippetChip("+ Dialogue", Icons.Default.ChatBubble) {
                    viewModel.insertSnippet(
                        """
    <dialogue character="Ren" start="1.0" duration="3.0" position="bottom" style="anime_box">
        This is our anime moment!
    </dialogue>
                        """.trimIndent()
                    )
                }
                SnippetChip("+ Effect", Icons.Default.AutoAwesome) {
                    viewModel.insertSnippet(
                        """
    <effect type="particles" start="0.0" duration="5.0" intensity="1.0" color="#00E5FF"/>
                        """.trimIndent()
                    )
                }
            }

            // Issue details expander if opened
            if (showValidationDetails && validationResult != null) {
                Surface(
                    color = SurfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(validationResult!!.issues) { issue ->
                            val color = if (issue.level == IssueLevel.ERROR) CrimsonAlert else SunsetGold
                            Row(verticalAlignment = Alignment.Top) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = color.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = "Line ${issue.line}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = color,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = issue.message,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    if (issue.suggestion != null) {
                                        Text(
                                            text = "💡 ${issue.suggestion}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Code Editor Canvas with Line Numbers
            val verticalScroll = rememberScrollState()
            val horizontalScroll = rememberScrollState()

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Obsidian)
                    .verticalScroll(verticalScroll)
            ) {
                // Fast, single-text rendering for line numbers (zero lag on long XML)
                val lineNumbersText = remember(lineCount) {
                    (1..lineCount).joinToString("\n")
                }

                Box(
                    modifier = Modifier
                        .background(StudioBlack)
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .widthIn(min = 36.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Text(
                        text = lineNumbersText,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = LineNumberColor,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 20.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    )
                }

                // Vertical separator
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(BorderDark)
                )

                // Editable text field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScroll)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = xmlCode,
                        onValueChange = { viewModel.updateXmlCode(it) },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TextPrimary,
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(NeonCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("xml_code_editor_field")
                    )
                }
            }
        }
    }
}

@Composable
fun SnippetChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}
