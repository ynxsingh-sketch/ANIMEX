package com.example

import com.example.model.AnimationType
import com.example.model.AssetType
import com.example.parser.AnimeXmlParser
import com.example.validator.AnimeXmlValidator
import org.junit.Assert.*
import org.junit.Test

class AnimeXmlEngineTest {

    private val sampleXml = """
        <anime version="1.0" title="Battle of Neo Tokyo">
            <settings width="1920" height="1080" fps="30" duration="10"/>
            <assets>
                <background id="bg1" src="assets/bg.png"/>
                <character id="hero" src="assets/hero.png"/>
                <character id="villain" src="assets/villain.png"/>
                <audio id="sfx1" src="assets/slash.wav"/>
            </assets>
            <scene id="scene1" start="0" duration="5" background="bg1">
                <character ref="hero" x="500" y="600" width="300" height="500" layer="1">
                    <animation type="move">
                        <keyframe time="0" x="500" y="600"/>
                        <keyframe time="3" x="900" y="600"/>
                    </animation>
                    <animation type="action" name="walk"/>
                    <animation type="expression" name="serious"/>
                </character>
                <camera type="zoom">
                    <keyframe time="0" zoom="1.0"/>
                    <keyframe time="4" zoom="1.5" shake="5"/>
                </camera>
                <dialogue character="hero" start="2" duration="2">
                    Hold the line!
                </dialogue>
                <audio ref="sfx1" start="2.5" volume="1.0"/>
            </scene>
            <scene id="scene2" start="5" duration="5" background="bg1">
                <character ref="villain" x="800" y="600" width="350" height="550" layer="2">
                    <animation type="action" name="attack"/>
                </character>
            </scene>
        </anime>
    """.trimIndent()

    @Test
    fun testParseXmlDocumentStructure() {
        val parser = AnimeXmlParser()
        val doc = parser.parse(sampleXml)

        assertEquals("Battle of Neo Tokyo", doc.title)
        assertEquals("1.0", doc.version)
        assertEquals(1920, doc.settings.width)
        assertEquals(1080, doc.settings.height)
        assertEquals(30, doc.settings.fps)
        assertEquals(10.0f, doc.settings.duration)

        // Assets
        assertEquals(4, doc.assets.size)
        assertEquals(AssetType.BACKGROUND, doc.assets[0].type)
        assertEquals("bg1", doc.assets[0].id)
        assertEquals("hero", doc.assets[1].id)

        // Scenes
        assertEquals(2, doc.scenes.size)
        val s1 = doc.scenes[0]
        assertEquals("scene1", s1.id)
        assertEquals(0f, s1.start)
        assertEquals(5f, s1.duration)
        assertEquals(1, s1.characters.size)

        // Character animations
        val hero = s1.characters[0]
        assertEquals("hero", hero.ref)
        assertEquals(3, hero.animations.size)
        assertEquals(AnimationType.MOVE, hero.animations[0].type)
        assertEquals(2, hero.animations[0].keyframes.size)
        assertEquals(500f, hero.animations[0].keyframes[0].x)
        assertEquals(900f, hero.animations[0].keyframes[1].x)

        // Camera
        assertNotNull(s1.camera)
        assertEquals(2, s1.camera!!.keyframes.size)
        assertEquals(1.0f, s1.camera!!.keyframes[0].zoom)
        assertEquals(1.5f, s1.camera!!.keyframes[1].zoom)

        // Dialogue
        assertEquals(1, s1.dialogues.size)
        assertEquals("hero", s1.dialogues[0].character)
        assertEquals("Hold the line!", s1.dialogues[0].text)

        // Audio
        assertEquals(1, s1.audio.size)
        assertEquals("sfx1", s1.audio[0].ref)
    }

    @Test
    fun testValidatorValidXml() {
        val validator = AnimeXmlValidator()
        val result = validator.validate(sampleXml)

        assertTrue("Expected valid XML result", result.isValid)
        assertEquals(0, result.errorCount)
        assertNotNull(result.doc)
    }

    @Test
    fun testValidatorMissingSceneReportsError() {
        val invalidXml = """
            <anime version="1.0" title="Empty Anime">
                <settings width="1920" height="1080" fps="30" duration="10"/>
                <assets/>
            </anime>
        """.trimIndent()

        val validator = AnimeXmlValidator()
        val result = validator.validate(invalidXml)

        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.message.contains("No <scene> elements found") })
    }

    @Test
    fun testValidatorMalformedXmlReportsLineNumber() {
        val brokenXml = """
            <anime version="1.0" title="Broken">
                <settings width="1920" height="1080"
                <scene id="s1">
            </anime>
        """.trimIndent()

        val validator = AnimeXmlValidator()
        val result = validator.validate(brokenXml)

        assertFalse(result.isValid)
        assertTrue(result.issues.isNotEmpty())
        assertTrue("Error line should be detected", result.issues[0].line >= 2)
    }
}
