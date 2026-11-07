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
        val doc: AnimeDoc? = try {
            parser.parse(xmlString)
        } catch (e: Exception) {
            issues.add(
                ValidationIssue(
                    level = IssueLevel.WARNING,
                    line = 1,
                    message = "Parsing notice: ${e.message ?: "Recovering using auto-structure"}",
                    suggestion = "Root <anime> or <scene> tags were auto-adjusted."
                )
            )
            null
        }

        if (doc == null) {
            return ValidationResult(
                isValid = false,
                issues = issues,
                doc = null,
                summary = "Could not parse XML into animation scenes."
            )
        }

        // Check settings
        if (doc.settings.width <= 0 || doc.settings.height <= 0) {
            issues.add(
                ValidationIssue(
                    level = IssueLevel.WARNING,
                    line = 2,
                    message = "Invalid resolution: width (${doc.settings.width}) and height (${doc.settings.height}). Defaulting to 1920x1080.",
                    suggestion = "Set width='1920' and height='1080' in <settings>."
                )
            )
        }
        if (doc.settings.fps !in 1..120) {
            issues.add(
                ValidationIssue(
                    level = IssueLevel.INFO,
                    line = 2,
                    message = "FPS (${doc.settings.fps}) is unusual; standard values are 24, 30, or 60.",
                    suggestion = "Set fps='30' for standard anime frame rate."
                )
            )
        }
        if (doc.settings.duration <= 0f) {
            issues.add(
                ValidationIssue(
                    level = IssueLevel.WARNING,
                    line = 2,
                    message = "Total animation duration (${doc.settings.duration}s) was <= 0. Auto-calculated from scenes.",
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
                        level = IssueLevel.INFO,
                        line = 5,
                        message = "Asset has missing or blank 'id' attribute.",
                        suggestion = "Add a unique id attribute like id='hero' or id='bg1'."
                    )
                )
            } else {
                assetMap[asset.id] = asset
            }
        }

        // Check Scenes
        val hasSceneTag = xmlString.contains("<scene", ignoreCase = true)
        if (!hasSceneTag) {
            issues.add(
                ValidationIssue(
                    level = IssueLevel.ERROR,
                    line = 10,
                    message = "No <scene> elements found. At least one scene is required to render animation.",
                    suggestion = "Add a <scene id='scene1' start='0' duration='10'>...</scene> tag."
                )
            )
        }

        val hasHardErrors = issues.any { it.level == IssueLevel.ERROR }
        val warningCount = issues.count { it.level == IssueLevel.WARNING }
        val summary = when {
            doc.scenes.isNotEmpty() && !hasHardErrors ->
                "XML Ready! ${doc.scenes.size} scene(s), ${doc.assets.size} asset(s), duration: ${"%.1f".format(doc.totalDuration)}s"
            doc.scenes.isNotEmpty() && hasHardErrors ->
                "XML parsed with ${issues.count { it.level == IssueLevel.ERROR }} issue(s). Preview will use auto-scenes."
            doc.scenes.isNotEmpty() ->
                "XML parsed with $warningCount notice(s). Ready to preview!"
            else ->
                "XML parsed, but no renderable scenes were found."
        }

        return ValidationResult(
            isValid = !hasHardErrors && doc.scenes.isNotEmpty(),
            issues = issues,
            doc = doc,
            summary = summary
        )
    }
}

