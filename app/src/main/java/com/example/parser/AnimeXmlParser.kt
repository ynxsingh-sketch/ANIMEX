package com.example.parser

import android.util.Xml
import com.example.model.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.StringReader

class AnimeXmlParser {

    fun parse(xmlString: String): AnimeDoc {
        if (xmlString.isBlank()) {
            throw XmlPullParserException("XML document is empty")
        }

        val parser = try {
            org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
        } catch (_: Exception) {
            try {
                Class.forName("org.kxml2.io.KXmlParser").getDeclaredConstructor().newInstance() as org.xmlpull.v1.XmlPullParser
            } catch (_: Exception) {
                Xml.newPullParser()
            }
        }
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        } catch (_: Exception) {}

        parser.setInput(StringReader(xmlString))

        var title = "Untitled Anime"
        var version = "1.0"
        var settings = SettingsDef()
        val assets = mutableListOf<AssetDef>()
        val scenes = mutableListOf<SceneDef>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    "anime" -> {
                        title = parser.getAttributeValue(null, "title") ?: title
                        version = parser.getAttributeValue(null, "version") ?: version
                    }
                    "settings" -> {
                        settings = parseSettings(parser)
                    }
                    "assets" -> {
                        // Assets container: parse children inside
                        parseAssets(parser, assets)
                    }
                    "scene" -> {
                        scenes.add(parseScene(parser))
                    }
                }
            }
            eventType = parser.next()
        }

        return AnimeDoc(
            title = title,
            version = version,
            settings = settings,
            assets = assets,
            scenes = scenes
        )
    }

    private fun parseSettings(parser: XmlPullParser): SettingsDef {
        val width = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 1920
        val height = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 1080
        val fps = parser.getAttributeValue(null, "fps")?.toIntOrNull() ?: 30
        val duration = parser.getAttributeValue(null, "duration")?.toFloatOrNull() ?: 10.0f
        return SettingsDef(width = width, height = height, fps = fps, duration = duration)
    }

    private fun parseAssets(parser: XmlPullParser, list: MutableList<AssetDef>) {
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name.equals("assets", ignoreCase = true))) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    "background" -> {
                        val id = parser.getAttributeValue(null, "id") ?: ""
                        val src = parser.getAttributeValue(null, "src") ?: ""
                        val label = parser.getAttributeValue(null, "label") ?: id
                        if (id.isNotBlank()) list.add(AssetDef(id, src, AssetType.BACKGROUND, label))
                    }
                    "character" -> {
                        val id = parser.getAttributeValue(null, "id") ?: ""
                        val src = parser.getAttributeValue(null, "src") ?: ""
                        val label = parser.getAttributeValue(null, "label") ?: id
                        if (id.isNotBlank()) list.add(AssetDef(id, src, AssetType.CHARACTER, label))
                    }
                    "audio" -> {
                        val id = parser.getAttributeValue(null, "id") ?: ""
                        val src = parser.getAttributeValue(null, "src") ?: ""
                        val label = parser.getAttributeValue(null, "label") ?: id
                        if (id.isNotBlank()) list.add(AssetDef(id, src, AssetType.AUDIO, label))
                    }
                }
            }
            eventType = parser.next()
        }
    }

    private fun parseScene(parser: XmlPullParser): SceneDef {
        val id = parser.getAttributeValue(null, "id") ?: "scene_${System.currentTimeMillis()}"
        val start = parser.getAttributeValue(null, "start")?.toFloatOrNull() ?: 0f
        val duration = parser.getAttributeValue(null, "duration")?.toFloatOrNull() ?: 10f
        var bgRef = parser.getAttributeValue(null, "background")

        val bgLayers = mutableListOf<BackgroundLayerDef>()
        val characters = mutableListOf<CharacterInstance>()
        var cameraDef: CameraDef? = null
        val dialogues = mutableListOf<DialogueDef>()
        val effects = mutableListOf<EffectDef>()
        val audioList = mutableListOf<AudioInstance>()

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name.equals("scene", ignoreCase = true))) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    "background" -> {
                        val ref = parser.getAttributeValue(null, "ref")
                            ?: parser.getAttributeValue(null, "src")
                            ?: ""
                        val speed = parser.getAttributeValue(null, "speed")?.toFloatOrNull() ?: 1.0f
                        val opacity = parser.getAttributeValue(null, "opacity")?.toFloatOrNull() ?: 1.0f
                        if (bgRef == null && ref.isNotBlank()) {
                            bgRef = ref
                        }
                        if (ref.isNotBlank()) {
                            bgLayers.add(BackgroundLayerDef(ref, speed, opacity))
                        }
                    }
                    "character" -> {
                        characters.add(parseCharacter(parser))
                    }
                    "camera" -> {
                        cameraDef = parseCamera(parser)
                    }
                    "dialogue" -> {
                        val charRef = parser.getAttributeValue(null, "character") ?: ""
                        val dStart = parser.getAttributeValue(null, "start")?.toFloatOrNull() ?: 0f
                        val dDur = parser.getAttributeValue(null, "duration")?.toFloatOrNull() ?: 3f
                        val pos = parser.getAttributeValue(null, "position") ?: "bottom"
                        val style = parser.getAttributeValue(null, "style") ?: "anime_box"
                        val text = parser.nextText().trim()
                        dialogues.add(DialogueDef(charRef, dStart, dDur, text, pos, style))
                    }
                    "effect" -> {
                        val type = parser.getAttributeValue(null, "type") ?: "particles"
                        val eStart = parser.getAttributeValue(null, "start")?.toFloatOrNull() ?: 0f
                        val eDur = parser.getAttributeValue(null, "duration")?.toFloatOrNull() ?: duration
                        val color = parser.getAttributeValue(null, "color")
                        val intensity = parser.getAttributeValue(null, "intensity")?.toFloatOrNull() ?: 1.0f
                        effects.add(EffectDef(type, eStart, eDur, color, intensity))
                    }
                    "audio" -> {
                        val ref = parser.getAttributeValue(null, "ref") ?: ""
                        val aStart = parser.getAttributeValue(null, "start")?.toFloatOrNull() ?: 0f
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
            ?: ""
        val x = parser.getAttributeValue(null, "x")?.toFloatOrNull() ?: 500f
        val y = parser.getAttributeValue(null, "y")?.toFloatOrNull() ?: 500f
        val width = parser.getAttributeValue(null, "width")?.toFloatOrNull() ?: 300f
        val height = parser.getAttributeValue(null, "height")?.toFloatOrNull() ?: 500f
        val opacity = parser.getAttributeValue(null, "opacity")?.toFloatOrNull() ?: 1.0f
        val rotation = parser.getAttributeValue(null, "rotation")?.toFloatOrNull() ?: 0f
        val layer = parser.getAttributeValue(null, "layer")?.toIntOrNull() ?: 1
        val visible = parser.getAttributeValue(null, "visible")?.toBoolean() ?: true

        val animations = mutableListOf<AnimationDef>()

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name.equals("character", ignoreCase = true))) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    "animation" -> {
                        animations.add(parseAnimation(parser))
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
        val typeStr = parser.getAttributeValue(null, "type")?.uppercase() ?: "MOVE"
        val animType = try {
            AnimationType.valueOf(typeStr)
        } catch (_: Exception) {
            AnimationType.MOVE
        }
        val name = parser.getAttributeValue(null, "name")
        val keyframes = mutableListOf<KeyframeDef>()

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name.equals("animation", ignoreCase = true))) {
            if (eventType == XmlPullParser.START_TAG && parser.name.equals("keyframe", ignoreCase = true)) {
                val time = parser.getAttributeValue(null, "time")?.toFloatOrNull() ?: 0f
                val kx = parser.getAttributeValue(null, "x")?.toFloatOrNull()
                val ky = parser.getAttributeValue(null, "y")?.toFloatOrNull()
                val scaleX = parser.getAttributeValue(null, "scaleX")?.toFloatOrNull()
                    ?: parser.getAttributeValue(null, "scale")?.toFloatOrNull()
                val scaleY = parser.getAttributeValue(null, "scaleY")?.toFloatOrNull()
                    ?: parser.getAttributeValue(null, "scale")?.toFloatOrNull()
                val angle = parser.getAttributeValue(null, "angle")?.toFloatOrNull()
                    ?: parser.getAttributeValue(null, "rotation")?.toFloatOrNull()
                val opacity = parser.getAttributeValue(null, "opacity")?.toFloatOrNull()
                val easingStr = parser.getAttributeValue(null, "easing")?.uppercase() ?: "EASE_IN_OUT"
                val easing = when (easingStr) {
                    "LINEAR" -> Easing.LINEAR
                    "EASE_IN" -> Easing.EASE_IN
                    "EASE_OUT" -> Easing.EASE_OUT
                    "STEP" -> Easing.STEP
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
        while (!(eventType == XmlPullParser.END_TAG && parser.name.equals("camera", ignoreCase = true))) {
            if (eventType == XmlPullParser.START_TAG && parser.name.equals("keyframe", ignoreCase = true)) {
                val time = parser.getAttributeValue(null, "time")?.toFloatOrNull() ?: 0f
                val zoom = parser.getAttributeValue(null, "zoom")?.toFloatOrNull()
                    ?: parser.getAttributeValue(null, "value")?.toFloatOrNull()
                    ?: 1.0f
                val kx = parser.getAttributeValue(null, "x")?.toFloatOrNull() ?: 0f
                val ky = parser.getAttributeValue(null, "y")?.toFloatOrNull() ?: 0f
                val rot = parser.getAttributeValue(null, "rotation")?.toFloatOrNull() ?: 0f
                val shake = parser.getAttributeValue(null, "shake")?.toFloatOrNull() ?: 0f
                val easingStr = parser.getAttributeValue(null, "easing")?.uppercase() ?: "EASE_IN_OUT"
                val easing = when (easingStr) {
                    "LINEAR" -> Easing.LINEAR
                    "EASE_IN" -> Easing.EASE_IN
                    "EASE_OUT" -> Easing.EASE_OUT
                    "STEP" -> Easing.STEP
                    else -> Easing.EASE_IN_OUT
                }
                keyframes.add(CameraKeyframe(time, zoom, kx, ky, rot, shake, easing))
            }
            eventType = parser.next()
        }

        return CameraDef(type, keyframes)
    }
}
