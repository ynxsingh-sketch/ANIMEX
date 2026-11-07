package com.example.validator

import android.util.Xml
import com.example.model.*
import com.example.parser.AnimeXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.StringReader

class AnimeXmlValidator {

    private val parser = AnimeXmlParser()

    fun validate(xmlString: String): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        if (xmlString.isBlank()) {
            issues.add(
                ValidationIssue(
                    level = IssueLevel.ERROR,
                    line = 1,
                    message = "XML code is empty. Paste or load anime XML code to begin.",
                    suggestion = "Use a sample template or insert the standard <anime> root tag."
                )
            )
            return ValidationResult(isValid = false, issues = issues, summary = "Empty XML document")
        }

        // 1. Syntax & Structural Check
        val syntaxParser = try {
            org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
        } catch (_: Exception) {
            try {
                Class.forName("org.kxml2.io.KXmlParser").getDeclaredConstructor().newInstance() as org.xmlpull.v1.XmlPullParser
            } catch (_: Exception) {
                Xml.newPullParser()
            }
        }
        try {
            syntaxParser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            syntaxParser.setInput(StringReader(xmlString))
            var eventType = syntaxParser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                eventType = syntaxParser.next()
            }
        } catch (e: XmlPullParserException) {
            val line = e.lineNumber.coerceAtLeast(1)
            val col = e.columnNumber.coerceAtLeast(1)
            issues.add(
                ValidationIssue(
                    level = IssueLevel.ERROR,
                    line = line,
                    column = col,
                    message = "XML Syntax Error: ${e.message?.substringBefore(" (position:") ?: "Malformed XML"}",
                    suggestion = "Check for unclosed tags, missing attribute quotes, or mismatched brackets."
                )
            )
            return ValidationResult(
                isValid = false,
                issues = issues,
                summary = "Failed XML syntax validation at line $line"
            )
        } catch (e: Exception) {
            issues.add(
                ValidationIssue(
                    level = IssueLevel.ERROR,
                    line = 1,
                    message = "Parsing Error: ${e.localizedMessage ?: "Unknown parse error"}"
                )
            )
            return ValidationResult(isValid = false, issues = issues, summary = "Parsing error")
        }

        // 2. Semantic AST Validation
        val doc: AnimeDoc
        try {
            doc = parser.parse(xmlString)
        } catch (e: Exception) {
            issues.add(
                ValidationIssue(
                    level = IssueLevel.ERROR,
                    line = 1,
                    message = "Failed to construct anime scene graph: ${e.message}",
                    suggestion = "Ensure root tag is <anime> and contains valid <settings>, <assets>, and <scene> blocks."
                )
            )
            return ValidationResult(isValid = false, issues = issues, summary = "Semantic parse failed")
        }

        // Check settings
        if (doc.settings.width <= 0 || doc.settings.height <= 0) {
            issues.add(
                ValidationIssue(
                    level = IssueLevel.ERROR,
                    line = 2,
                    message = "Invalid resolution: width (${doc.settings.width}) and height (${doc.settings.height}) must be > 0.",
                    suggestion = "Set width='1920' and height='1080' in <settings>."
                )
            )
        }
        if (doc.settings.fps !in 1..120) {
            issues.add(
                ValidationIssue(
                    level = IssueLevel.WARNING,
                    line = 2,
                    message = "FPS (${doc.settings.fps}) is unusual; standard values are 24, 30, or 60.",
                    suggestion = "Set fps='30' for standard anime frame rate."
                )
            )
        }
        if (doc.settings.duration <= 0f) {
            issues.add(
                ValidationIssue(
                    level = IssueLevel.ERROR,
                    line = 2,
                    message = "Total animation duration (${doc.settings.duration}s) must be greater than 0.",
                    suggestion = "Set duration='10' in <settings>."
                )
            )
        }

        // Check Assets
        val assetMap = mutableMapOf<String, AssetDef>()
        for (asset in doc.assets) {
            if (asset.id.isBlank()) {
                issues.add(
                    ValidationIssue(
                        level = IssueLevel.ERROR,
                        line = 5,
                        message = "Asset has missing or blank 'id' attribute.",
                        suggestion = "Add a unique id attribute like id='hero' or id='bg1'."
                    )
                )
            } else if (assetMap.containsKey(asset.id)) {
                issues.add(
                    ValidationIssue(
                        level = IssueLevel.ERROR,
                        line = 5,
                        message = "Duplicate asset id detected: '${asset.id}'.",
                        suggestion = "Asset IDs must be unique across all backgrounds, characters, and audio."
                    )
                )
            } else {
                assetMap[asset.id] = asset
            }

            if (asset.src.isBlank()) {
                issues.add(
                    ValidationIssue(
                        level = IssueLevel.WARNING,
                        line = 5,
                        message = "Asset '${asset.id}' has empty 'src' path.",
                        suggestion = "Provide a valid image or audio path, or reference a bundled sample."
                    )
                )
            }
        }

        // Check Scenes
        if (doc.scenes.isEmpty()) {
            issues.add(
                ValidationIssue(
                    level = IssueLevel.ERROR,
                    line = 10,
                    message = "No <scene> elements found. At least one scene is required to render animation.",
                    suggestion = "Add a <scene id='scene1' start='0' duration='10'>...</scene> tag."
                )
            )
        }

        val sceneIds = mutableSetOf<String>()
        for ((sIndex, scene) in doc.scenes.withIndex()) {
            if (scene.id.isBlank()) {
                issues.add(
                    ValidationIssue(
                        level = IssueLevel.WARNING,
                        line = 10 + sIndex * 5,
                        message = "Scene at index $sIndex has blank id.",
                        suggestion = "Add an id attribute: id='scene${sIndex + 1}'"
                    )
                )
            } else if (!sceneIds.add(scene.id)) {
                issues.add(
                    ValidationIssue(
                        level = IssueLevel.WARNING,
                        line = 10 + sIndex * 5,
                        message = "Duplicate scene id '${scene.id}'.",
                        suggestion = "Ensure each scene has a distinct identifier."
                    )
                )
            }

            if (scene.duration <= 0f) {
                issues.add(
                    ValidationIssue(
                        level = IssueLevel.ERROR,
                        line = 10 + sIndex * 5,
                        message = "Scene '${scene.id}' duration (${scene.duration}s) must be > 0.",
                        suggestion = "Specify duration='5' or similar positive number."
                    )
                )
            }

            // Check scene background
            scene.backgroundRef?.let { bgRef ->
                if (!assetMap.containsKey(bgRef)) {
                    issues.add(
                        ValidationIssue(
                            level = IssueLevel.WARNING,
                            line = 10 + sIndex * 5,
                            message = "Scene '${scene.id}' references unknown background '$bgRef'.",
                            suggestion = "Define <background id='$bgRef' src='...'/> inside <assets>."
                        )
                    )
                }
            }

            // Check scene characters
            for (char in scene.characters) {
                if (!assetMap.containsKey(char.ref)) {
                    issues.add(
                        ValidationIssue(
                            level = IssueLevel.WARNING,
                            line = 12 + sIndex * 5,
                            message = "Character in scene '${scene.id}' references undefined asset '${char.ref}'.",
                            suggestion = "Define <character id='${char.ref}' src='...'/> in <assets>."
                        )
                    )
                }

                // Check keyframes monotonicity
                for (anim in char.animations) {
                    var lastTime = -1f
                    for (kf in anim.keyframes) {
                        if (kf.time < lastTime) {
                            issues.add(
                                ValidationIssue(
                                    level = IssueLevel.ERROR,
                                    line = 15 + sIndex * 5,
                                    message = "Keyframe time (${kf.time}s) in character '${char.ref}' is out of order (previous was ${lastTime}s).",
                                    suggestion = "Ensure keyframe times are ordered chronologically."
                                )
                            )
                        }
                        if (kf.time > scene.duration) {
                            issues.add(
                                ValidationIssue(
                                    level = IssueLevel.WARNING,
                                    line = 15 + sIndex * 5,
                                    message = "Keyframe time (${kf.time}s) exceeds scene duration (${scene.duration}s).",
                                    suggestion = "Adjust keyframe time or extend scene duration."
                                )
                            )
                        }
                        lastTime = kf.time
                    }
                }
            }

            // Check dialogues
            for (dialogue in scene.dialogues) {
                if (dialogue.character.isNotBlank() && !assetMap.containsKey(dialogue.character)) {
                    issues.add(
                        ValidationIssue(
                            level = IssueLevel.INFO,
                            line = 18 + sIndex * 5,
                            message = "Dialogue references character '${dialogue.character}' which is not in <assets>.",
                            suggestion = "You can add <character id='${dialogue.character}' .../> for speaker portrait."
                        )
                    )
                }
                if (dialogue.text.isBlank()) {
                    issues.add(
                        ValidationIssue(
                            level = IssueLevel.WARNING,
                            line = 18 + sIndex * 5,
                            message = "Empty dialogue content in scene '${scene.id}'.",
                            suggestion = "Provide dialogue text inside <dialogue>...</dialogue>."
                        )
                    )
                }
            }

            // Check audio
            for (aud in scene.audio) {
                if (!assetMap.containsKey(aud.ref)) {
                    issues.add(
                        ValidationIssue(
                            level = IssueLevel.WARNING,
                            line = 20 + sIndex * 5,
                            message = "Audio in scene '${scene.id}' references undefined asset '${aud.ref}'.",
                            suggestion = "Define <audio id='${aud.ref}' src='...'/> in <assets>."
                        )
                    )
                }
            }
        }

        val hasErrors = issues.any { it.level == IssueLevel.ERROR }
        val summary = if (!hasErrors) {
            "XML is fully valid! ${doc.scenes.size} scene(s), ${doc.assets.size} asset(s), duration: ${doc.totalDuration}s"
        } else {
            "Found ${issues.count { it.level == IssueLevel.ERROR }} error(s) and ${issues.count { it.level == IssueLevel.WARNING }} warning(s)."
        }

        return ValidationResult(
            isValid = !hasErrors,
            issues = issues,
            doc = if (!hasErrors) doc else null,
            summary = summary
        )
    }
}
