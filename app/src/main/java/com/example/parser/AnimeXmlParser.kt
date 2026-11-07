package com.example.parser

import android.util.Xml
import com.example.model.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.StringReader

class AnimeXmlParser {

    fun parse(rawXml: String): AnimeDoc {
        val xmlString = sanitizeXmlInput(rawXml)
        if (xmlString.isBlank()) {
            return createDefaultAnimeDoc("Empty XML")
        }

        return try {
            parseWithXmlPull(xmlString)
        } catch (e: Exception) {
            // Fault-tolerant fallback: parse with regex extractor so even imperfect XML works!
            parseWithRegexFallback(xmlString)
        }
    }

    private fun sanitizeXmlInput(raw: String): String {
        var clean = raw.trim()
        // Strip markdown code fence if user pasted from markdown
        if (clean.startsWith("```")) {
            clean = clean.substringAfter("\n")
            if (clean.endsWith("```")) {
                clean = clean.substringBeforeLast("```")
            }
            clean = clean.trim()
        }

        // If no root tag, wrap automatically
        val lower = clean.lowercase()
        if (!lower.contains("<anime") && !lower.contains("<project") && !lower.contains("<animation")) {
            clean = "<anime version=\"1.0\" title=\"Custom Anime Animation\">\n" +
                    "  <settings width=\"1920\" height=\"1080\" fps=\"30\" duration=\"10\"/>\n" +
                    (if (!lower.contains("<scene")) "  <scene id=\"scene1\" start=\"0\" duration=\"10\">\n$clean\n  </scene>" else clean) +
                    "\n</anime>"
        } else if (!lower.contains("<scene") && lower.contains("<character")) {
            // Has root anime but no scene: wrap inner characters in scene
            clean = clean.replace(Regex("<assets>[\\s\\S]*?</assets>", RegexOption.IGNORE_CASE)) { it.value }
        }

        return clean
    }

    private fun parseWithXmlPull(xmlString: String): AnimeDoc {
        val parser = try {
            org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
        } catch (_: Exception) {
            try {
                Class.forName("org.kxml2.io.KXmlParser").getDeclaredConstructor().newInstance() as XmlPullParser
            } catch (_: Exception) {
                Xml.newPullParser()
            }
        }
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        } catch (_: Exception) {}

        parser.setInput(StringReader(xmlString))

        var title = "Custom Anime Project"
        var version = "1.0"
        var settings = SettingsDef()
        val assets = mutableListOf<AssetDef>()
        val scenes = mutableListOf<SceneDef>()
        val orphanCharacters = mutableListOf<CharacterInstance>()
        var globalBackgroundRef: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val tag = parser.name.lowercase()
                when (tag) {
                    "anime", "project", "animation_project", "studio", "root" -> {
                        title = parser.getAttributeValue(null, "title")
                            ?: parser.getAttributeValue(null, "name")
                            ?: title
                        version = parser.getAttributeValue(null, "version") ?: version
                    }
                    "settings", "config" -> {
                        settings = parseSettings(parser)
                    }
                    "assets", "resources" -> {
                        parseAssets(parser, assets)
                    }
                    "scene", "shot", "sequence" -> {
                        scenes.add(parseScene(parser))
                    }
                    "character", "actor", "sprite", "char" -> {
                        // Character outside scene
                        orphanCharacters.add(parseCharacter(parser))
                    }
                    "background", "bg", "backdrop" -> {
                        val ref = parser.getAttributeValue(null, "ref")
                            ?: parser.getAttributeValue(null, "src")
                            ?: parser.getAttributeValue(null, "id")
                        if (ref != null) globalBackgroundRef = ref
                    }
                }
            }
            eventType = parser.next()
        }

        // If there were characters outside any scene, or no scenes found:
        if (scenes.isEmpty() || orphanCharacters.isNotEmpty()) {
            val maxDur = maxOf(settings.duration, 10f)
            val defaultScene = SceneDef(
                id = "scene_main",
                start = 0f,
                duration = maxDur,
                backgroundRef = globalBackgroundRef ?: assets.find { it.type == AssetType.BACKGROUND }?.id ?: "bg_cyber",
                characters = orphanCharacters
            )
            if (scenes.isEmpty()) {
                scenes.add(defaultScene)
            } else if (orphanCharacters.isNotEmpty()) {
                val first = scenes.first()
                scenes[0] = first.copy(characters = first.characters + orphanCharacters)
            }
        }

        // Auto-register any character or background from scenes not explicitly defined in assets
        autoRegisterMissingAssets(scenes, assets)

        // Ensure calculated duration is valid
        val finalDuration = if (settings.duration > 0f) settings.duration
        else (scenes.maxOfOrNull { it.end } ?: 10f).coerceAtLeast(5f)

        return AnimeDoc(
            title = title,
            version = version,
            settings = settings.copy(duration = finalDuration),
            assets = assets,
            scenes = scenes
        )
    }

    private fun parseSettings(parser: XmlPullParser): SettingsDef {
        val width = parseDimension(parser.getAttributeValue(null, "width"), 1920f).toInt()
        val height = parseDimension(parser.getAttributeValue(null, "height"), 1080f).toInt()
        val fps = (parser.getAttributeValue(null, "fps")?.toIntOrNull() ?: 30).coerceIn(12, 120)
        val duration = parseTimeAttr(parser.getAttributeValue(null, "duration"), 10.0f)
        return SettingsDef(width = width, height = height, fps = fps, duration = duration)
    }

    private fun parseAssets(parser: XmlPullParser, list: MutableList<AssetDef>) {
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && (parser.name.equals("assets", ignoreCase = true) || parser.name.equals("resources", ignoreCase = true)))) {
            if (eventType == XmlPullParser.START_TAG) {
                val tag = parser.name.lowercase()
                val id = parser.getAttributeValue(null, "id")
                    ?: parser.getAttributeValue(null, "name")
                    ?: "asset_${list.size + 1}"
                val src = parser.getAttributeValue(null, "src")
                    ?: parser.getAttributeValue(null, "url")
                    ?: parser.getAttributeValue(null, "path")
                    ?: id
                val label = parser.getAttributeValue(null, "label") ?: id

                when (tag) {
                    "background", "bg", "backdrop" -> {
                        list.add(AssetDef(id, src, AssetType.BACKGROUND, label))
                    }
                    "character", "actor", "sprite", "char" -> {
                        list.add(AssetDef(id, src, AssetType.CHARACTER, label))
                    }
                    "audio", "sound", "music", "sfx", "bgm" -> {
                        list.add(AssetDef(id, src, AssetType.AUDIO, label))
                    }
                }
            }
            eventType = parser.next()
        }
    }

    private fun parseScene(parser: XmlPullParser): SceneDef {
        val id = parser.getAttributeValue(null, "id") ?: "scene_${System.currentTimeMillis() % 10000}"
        val start = parseTimeAttr(parser.getAttributeValue(null, "start"), 0f)
        val duration = parseTimeAttr(parser.getAttributeValue(null, "duration"), 10f)
        var bgRef = parser.getAttributeValue(null, "background")
            ?: parser.getAttributeValue(null, "bg")

        val bgLayers = mutableListOf<BackgroundLayerDef>()
        val characters = mutableListOf<CharacterInstance>()
        var cameraDef: CameraDef? = null
        val dialogues = mutableListOf<DialogueDef>()
        val effects = mutableListOf<EffectDef>()
        val audioList = mutableListOf<AudioInstance>()

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && (parser.name.equals("scene", ignoreCase = true) || parser.name.equals("shot", ignoreCase = true)))) {
            if (eventType == XmlPullParser.START_TAG) {
                val tag = parser.name.lowercase()
                when (tag) {
                    "background", "bg", "backdrop" -> {
                        val ref = parser.getAttributeValue(null, "ref")
                            ?: parser.getAttributeValue(null, "src")
                            ?: parser.getAttributeValue(null, "id")
                            ?: ""
                        val speed = parser.getAttributeValue(null, "speed")?.toFloatOrNull() ?: 1.0f
                        val opacity = parser.getAttributeValue(null, "opacity")?.toFloatOrNull() ?: 1.0f
                        if (bgRef == null && ref.isNotBlank()) bgRef = ref
                        if (ref.isNotBlank()) bgLayers.add(BackgroundLayerDef(ref, speed, opacity))
                    }
                    "character", "actor", "sprite", "char" -> {
                        characters.add(parseCharacter(parser))
                    }
                    "camera", "cam", "view" -> {
                        cameraDef = parseCamera(parser)
                    }
                    "dialogue", "dialog", "subtitle", "text", "caption" -> {
                        val charRef = parser.getAttributeValue(null, "character")
                            ?: parser.getAttributeValue(null, "char")
                            ?: parser.getAttributeValue(null, "speaker")
                            ?: ""
                        val dStart = parseTimeAttr(parser.getAttributeValue(null, "start"), 0f)
                        val dDur = parseTimeAttr(parser.getAttributeValue(null, "duration"), 3.5f)
                        val pos = parser.getAttributeValue(null, "position") ?: "bottom"
                        val style = parser.getAttributeValue(null, "style") ?: "anime_box"
                        val text = parser.nextText().trim()
                        dialogues.add(DialogueDef(charRef, dStart, dDur, text, pos, style))
                    }
                    "effect", "fx", "particles", "weather" -> {
                        val type = parser.getAttributeValue(null, "type")
                            ?: parser.getAttributeValue(null, "name")
                            ?: "particles"
                        val eStart = parseTimeAttr(parser.getAttributeValue(null, "start"), 0f)
                        val eDur = parseTimeAttr(parser.getAttributeValue(null, "duration"), duration)
                        val color = parser.getAttributeValue(null, "color")
                        val intensity = parser.getAttributeValue(null, "intensity")?.toFloatOrNull() ?: 1.0f
                        effects.add(EffectDef(type, eStart, eDur, color, intensity))
                    }
                    "audio", "sound", "sfx", "bgm", "music" -> {
                        val ref = parser.getAttributeValue(null, "ref")
                            ?: parser.getAttributeValue(null, "src")
                            ?: parser.getAttributeValue(null, "id")
                            ?: ""
                        val aStart = parseTimeAttr(parser.getAttributeValue(null, "start"), 0f)
                        val aDur = parser.getAttributeValue(null, "duration")?.toFloatOrNull()
                        val vol = parser.getAttributeValue(null, "volume")?.toFloatOrNull() ?: 1.0f
                        val loop = parser.getAttributeValue(null, "loop")?.toBoolean() ?: false
                        if (ref.isNotBlank()) {
                            audioList.add(AudioInstance(ref, aStart, aDur, vol, loop))
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return SceneDef(
            id = id,
            start = start,
            duration = duration,
            backgroundRef = bgRef,
            backgroundLayers = bgLayers,
            characters = characters,
            camera = cameraDef,
            dialogues = dialogues,
            effects = effects,
            audio = audioList
        )
    }

    private fun parseCharacter(parser: XmlPullParser): CharacterInstance {
        val ref = parser.getAttributeValue(null, "ref")
            ?: parser.getAttributeValue(null, "id")
            ?: parser.getAttributeValue(null, "name")
            ?: parser.getAttributeValue(null, "src")
            ?: "hero"
        val x = parseDimension(parser.getAttributeValue(null, "x") ?: parser.getAttributeValue(null, "left"), 960f)
        val y = parseDimension(parser.getAttributeValue(null, "y") ?: parser.getAttributeValue(null, "top"), 650f)
        val width = parseDimension(parser.getAttributeValue(null, "width") ?: parser.getAttributeValue(null, "w"), 400f)
        val height = parseDimension(parser.getAttributeValue(null, "height") ?: parser.getAttributeValue(null, "h"), 700f)
        val opacity = parser.getAttributeValue(null, "opacity")?.toFloatOrNull()
            ?: parser.getAttributeValue(null, "alpha")?.toFloatOrNull()
            ?: 1.0f
        val rotation = parser.getAttributeValue(null, "rotation")?.toFloatOrNull()
            ?: parser.getAttributeValue(null, "angle")?.toFloatOrNull()
            ?: 0f
        val layer = parser.getAttributeValue(null, "layer")?.toIntOrNull()
            ?: parser.getAttributeValue(null, "z")?.toIntOrNull()
            ?: 1
        val visible = parser.getAttributeValue(null, "visible")?.toBoolean() ?: true

        val animations = mutableListOf<AnimationDef>()

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && (parser.name.equals("character", ignoreCase = true) || parser.name.equals("actor", ignoreCase = true) || parser.name.equals("sprite", ignoreCase = true)))) {
            if (eventType == XmlPullParser.START_TAG) {
                val tag = parser.name.lowercase()
                when (tag) {
                    "animation", "anim", "motion" -> {
                        animations.add(parseAnimation(parser))
                    }
                    "action" -> {
                        val actName = parser.getAttributeValue(null, "name")
                            ?: parser.getAttributeValue(null, "type")
                            ?: "idle"
                        animations.add(AnimationDef(AnimationType.ACTION, actName, emptyList()))
                    }
                    "expression", "emotion", "face" -> {
                        val exprName = parser.getAttributeValue(null, "name")
                            ?: parser.getAttributeValue(null, "type")
                            ?: "smile"
                        animations.add(AnimationDef(AnimationType.EXPRESSION, exprName, emptyList()))
                    }
                }
            }
            eventType = parser.next()
        }

        return CharacterInstance(
            ref = ref,
            x = x,
            y = y,
            width = width,
            height = height,
            opacity = opacity,
            rotation = rotation,
            layer = layer,
            visible = visible,
            animations = animations
        )
    }

    private fun parseAnimation(parser: XmlPullParser): AnimationDef {
        val typeStr = (parser.getAttributeValue(null, "type") ?: parser.getAttributeValue(null, "name") ?: "MOVE").uppercase()
        val animType = when {
            typeStr.contains("MOVE") || typeStr.contains("POS") -> AnimationType.MOVE
            typeStr.contains("SCALE") || typeStr.contains("ZOOM") -> AnimationType.SCALE
            typeStr.contains("ROTAT") || typeStr.contains("ANGLE") -> AnimationType.ROTATE
            typeStr.contains("FADE") || typeStr.contains("ALPHA") || typeStr.contains("OPACITY") -> AnimationType.FADE
            typeStr.contains("ACTION") || typeStr.contains("ACT") -> AnimationType.ACTION
            typeStr.contains("EXPR") || typeStr.contains("EMOTE") -> AnimationType.EXPRESSION
            else -> AnimationType.MOVE
        }
        val name = parser.getAttributeValue(null, "name") ?: parser.getAttributeValue(null, "action")
        val keyframes = mutableListOf<KeyframeDef>()

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && (parser.name.equals("animation", ignoreCase = true) || parser.name.equals("anim", ignoreCase = true)))) {
            if (eventType == XmlPullParser.START_TAG && (parser.name.equals("keyframe", ignoreCase = true) || parser.name.equals("frame", ignoreCase = true) || parser.name.equals("kf", ignoreCase = true))) {
                val time = parseTimeAttr(parser.getAttributeValue(null, "time"), 0f)
                val kx = parser.getAttributeValue(null, "x")?.toFloatOrNull()
                    ?: parser.getAttributeValue(null, "left")?.toFloatOrNull()
                val ky = parser.getAttributeValue(null, "y")?.toFloatOrNull()
                    ?: parser.getAttributeValue(null, "top")?.toFloatOrNull()
                val scaleX = parser.getAttributeValue(null, "scaleX")?.toFloatOrNull()
                    ?: parser.getAttributeValue(null, "scale")?.toFloatOrNull()
                val scaleY = parser.getAttributeValue(null, "scaleY")?.toFloatOrNull()
                    ?: parser.getAttributeValue(null, "scale")?.toFloatOrNull()
                val angle = parser.getAttributeValue(null, "angle")?.toFloatOrNull()
                    ?: parser.getAttributeValue(null, "rotation")?.toFloatOrNull()
                val opacity = parser.getAttributeValue(null, "opacity")?.toFloatOrNull()
                    ?: parser.getAttributeValue(null, "alpha")?.toFloatOrNull()
                val easingStr = parser.getAttributeValue(null, "easing")?.uppercase() ?: "EASE_IN_OUT"
                val easing = when {
                    easingStr.contains("LINEAR") -> Easing.LINEAR
                    easingStr.contains("IN_OUT") -> Easing.EASE_IN_OUT
                    easingStr.contains("IN") -> Easing.EASE_IN
                    easingStr.contains("OUT") -> Easing.EASE_OUT
                    easingStr.contains("STEP") -> Easing.STEP
                    else -> Easing.EASE_IN_OUT
                }
                keyframes.add(KeyframeDef(time, kx, ky, scaleX, scaleY, angle, opacity, easing))
            }
            eventType = parser.next()
        }

        return AnimationDef(animType, name, keyframes)
    }

    private fun parseCamera(parser: XmlPullParser): CameraDef {
        val type = parser.getAttributeValue(null, "type") ?: "zoom"
        val keyframes = mutableListOf<CameraKeyframe>()

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && (parser.name.equals("camera", ignoreCase = true) || parser.name.equals("cam", ignoreCase = true)))) {
            if (eventType == XmlPullParser.START_TAG && (parser.name.equals("keyframe", ignoreCase = true) || parser.name.equals("frame", ignoreCase = true) || parser.name.equals("kf", ignoreCase = true))) {
                val time = parseTimeAttr(parser.getAttributeValue(null, "time"), 0f)
                val zoom = parser.getAttributeValue(null, "zoom")?.toFloatOrNull()
                    ?: parser.getAttributeValue(null, "value")?.toFloatOrNull()
                    ?: parser.getAttributeValue(null, "scale")?.toFloatOrNull()
                    ?: 1.0f
                val kx = parser.getAttributeValue(null, "x")?.toFloatOrNull() ?: 0f
                val ky = parser.getAttributeValue(null, "y")?.toFloatOrNull() ?: 0f
                val rot = parser.getAttributeValue(null, "rotation")?.toFloatOrNull() ?: 0f
                val shake = parser.getAttributeValue(null, "shake")?.toFloatOrNull() ?: 0f
                val easingStr = parser.getAttributeValue(null, "easing")?.uppercase() ?: "EASE_IN_OUT"
                val easing = when {
                    easingStr.contains("LINEAR") -> Easing.LINEAR
                    easingStr.contains("IN_OUT") -> Easing.EASE_IN_OUT
                    easingStr.contains("IN") -> Easing.EASE_IN
                    easingStr.contains("OUT") -> Easing.EASE_OUT
                    else -> Easing.EASE_IN_OUT
                }
                keyframes.add(CameraKeyframe(time, zoom, kx, ky, rot, shake, easing))
            }
            eventType = parser.next()
        }

        return CameraDef(type, keyframes)
    }

    private fun autoRegisterMissingAssets(scenes: List<SceneDef>, assets: MutableList<AssetDef>) {
        val assetIds = assets.map { it.id }.toMutableSet()
        for (scene in scenes) {
            scene.backgroundRef?.let { bgId ->
                if (!assetIds.contains(bgId)) {
                    assets.add(AssetDef(bgId, bgId, AssetType.BACKGROUND, bgId))
                    assetIds.add(bgId)
                }
            }
            for (layer in scene.backgroundLayers) {
                if (!assetIds.contains(layer.ref)) {
                    assets.add(AssetDef(layer.ref, layer.ref, AssetType.BACKGROUND, layer.ref))
                    assetIds.add(layer.ref)
                }
            }
            for (char in scene.characters) {
                if (!assetIds.contains(char.ref)) {
                    assets.add(AssetDef(char.ref, char.ref, AssetType.CHARACTER, char.ref))
                    assetIds.add(char.ref)
                }
            }
            for (aud in scene.audio) {
                if (!assetIds.contains(aud.ref)) {
                    assets.add(AssetDef(aud.ref, aud.ref, AssetType.AUDIO, aud.ref))
                    assetIds.add(aud.ref)
                }
            }
        }
    }

    private fun parseWithRegexFallback(xml: String): AnimeDoc {
        val title = Regex("<anime[^>]*title=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.get(1) ?: "Imported Anime"
        val width = Regex("width=[\"'](\\d+)[\"']").find(xml)?.groupValues?.get(1)?.toIntOrNull() ?: 1920
        val height = Regex("height=[\"'](\\d+)[\"']").find(xml)?.groupValues?.get(1)?.toIntOrNull() ?: 1080
        val fps = Regex("fps=[\"'](\\d+)[\"']").find(xml)?.groupValues?.get(1)?.toIntOrNull() ?: 30
        val duration = Regex("duration=[\"']([0-9.]+)[\"']").find(xml)?.groupValues?.get(1)?.toFloatOrNull() ?: 10f

        val assets = mutableListOf<AssetDef>()
        val charMatches = Regex("<character[^>]*id=[\"']([^\"']+)[\"'][^>]*src=[\"']([^\"']+)[\"']").findAll(xml)
        for (m in charMatches) {
            assets.add(AssetDef(m.groupValues[1], m.groupValues[2], AssetType.CHARACTER, m.groupValues[1]))
        }

        val characters = mutableListOf<CharacterInstance>()
        val inlineChars = Regex("<character[^>]*ref=[\"']([^\"']+)[\"']").findAll(xml)
        for (m in inlineChars) {
            characters.add(CharacterInstance(ref = m.groupValues[1], x = 960f, y = 650f))
        }

        if (characters.isEmpty()) {
            characters.add(CharacterInstance(ref = "hero", x = 960f, y = 650f))
        }

        val scene = SceneDef(
            id = "scene_fallback",
            start = 0f,
            duration = duration,
            backgroundRef = "bg_cyber",
            characters = characters
        )

        return AnimeDoc(
            title = title,
            version = "1.0",
            settings = SettingsDef(width = width, height = height, fps = fps, duration = duration),
            assets = assets,
            scenes = listOf(scene)
        )
    }

    private fun createDefaultAnimeDoc(title: String): AnimeDoc {
        return AnimeDoc(
            title = title,
            version = "1.0",
            settings = SettingsDef(1920, 1080, 30, 10f),
            assets = listOf(
                AssetDef("bg_cyber", "bg_cyber_tokyo.jpg", AssetType.BACKGROUND, "Cyber Tokyo"),
                AssetDef("hero_ren", "char_hero_ren.png", AssetType.CHARACTER, "Hero Ren")
            ),
            scenes = listOf(
                SceneDef(
                    id = "scene1",
                    start = 0f,
                    duration = 10f,
                    backgroundRef = "bg_cyber",
                    characters = listOf(CharacterInstance("hero_ren", 960f, 650f, 400f, 700f))
                )
            )
        )
    }

    private fun parseTimeAttr(raw: String?, defaultVal: Float): Float {
        if (raw == null) return defaultVal
        val clean = raw.trim().lowercase()
        return try {
            when {
                clean.endsWith("ms") -> clean.removeSuffix("ms").trim().toFloat() / 1000f
                clean.endsWith("s") -> clean.removeSuffix("s").trim().toFloat()
                clean.endsWith("sec") -> clean.removeSuffix("sec").trim().toFloat()
                else -> clean.toFloat()
            }
        } catch (_: Exception) {
            defaultVal
        }
    }

    private fun parseDimension(raw: String?, defaultVal: Float): Float {
        if (raw == null) return defaultVal
        val clean = raw.trim().lowercase().removeSuffix("px").removeSuffix("dp").trim()
        return clean.toFloatOrNull() ?: defaultVal
    }
}
