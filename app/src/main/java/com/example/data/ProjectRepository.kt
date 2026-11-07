package com.example.data

import android.content.Context
import com.example.database.AnimeDatabase
import com.example.database.AnimeProject
import com.example.model.*
import com.example.parser.AnimeXmlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ProjectRepository(private val context: Context) {

    private val db = AnimeDatabase.getInstance(context)
    private val projectDao = db.projectDao()
    private val parser = AnimeXmlParser()

    val allProjects: Flow<List<AnimeProject>> = projectDao.getAllProjects()

    suspend fun getProjectById(id: Long): AnimeProject? = withContext(Dispatchers.IO) {
        projectDao.getProjectById(id)
    }

    suspend fun saveProject(project: AnimeProject): Long = withContext(Dispatchers.IO) {
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        if (project.id == 0L) {
            projectDao.insert(updated)
        } else {
            projectDao.update(updated)
            project.id
        }
    }

    suspend fun deleteProject(project: AnimeProject) = withContext(Dispatchers.IO) {
        projectDao.delete(project)
        getProjectDir(project.id).deleteRecursively()
    }

    suspend fun duplicateProject(project: AnimeProject): Long = withContext(Dispatchers.IO) {
        val duplicated = project.copy(
            id = 0,
            title = "${project.title} (Copy)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val newId = projectDao.insert(duplicated)
        // Copy assets folder if present
        val srcDir = getProjectDir(project.id)
        val dstDir = getProjectDir(newId)
        if (srcDir.exists()) {
            srcDir.copyRecursively(dstDir, overwrite = true)
        }
        newId
    }

    fun getProjectDir(projectId: Long): File {
        val dir = File(context.filesDir, "projects/$projectId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun seedSampleProjectsIfEmpty() = withContext(Dispatchers.IO) {
        val count = projectDao.getCount()
        if (count == 0) {
            val samples = getSampleProjects()
            for (sample in samples) {
                projectDao.insert(sample)
            }
        }
    }

    fun getSampleProjects(): List<AnimeProject> {
        return listOf(
            AnimeProject(
                title = "Cyberpunk Showdown",
                description = "2-scene anime action with Ren & Ayame, neon city, rain, camera zoom, and audio SFX.",
                width = 1920,
                height = 1080,
                fps = 30,
                duration = 9.0f,
                xmlContent = """
<anime version="1.0" title="Cyberpunk Showdown">
    <settings width="1920" height="1080" fps="30" duration="9.0"/>
    
    <assets>
        <background id="bg_cyber" src="bg_cyber_tokyo.jpg" label="Cyber Tokyo Night"/>
        <character id="hero_ren" src="char_hero_ren.png" label="Ren (Protagonist)"/>
        <character id="ayame_ninja" src="char_ayame_ninja.png" label="Ayame (Shinobi)"/>
        <audio id="bgm_cyber" src="bgm_cyber_battle.wav" type="bgm"/>
        <audio id="sfx_slash" src="sfx_slash.wav" type="sfx"/>
        <audio id="sfx_laser" src="sfx_laser.wav" type="sfx"/>
    </assets>

    <!-- Scene 1: Ayame arrives in the rainy cyber alley -->
    <scene id="scene1" start="0.0" duration="4.5" background="bg_cyber">
        <audio ref="bgm_cyber" start="0.0" volume="0.8" loop="true"/>
        <effect type="rain" start="0.0" duration="4.5" intensity="1.0" color="#38BDF8"/>
        
        <character ref="ayame_ninja" x="1400" y="650" width="420" height="700" layer="2">
            <animation type="move">
                <keyframe time="0.0" x="1700" y="650" easing="ease_out"/>
                <keyframe time="2.0" x="1350" y="650" easing="ease_in_out"/>
            </animation>
            <animation type="action" name="walk"/>
            <animation type="expression" name="serious"/>
        </character>

        <camera type="zoom">
            <keyframe time="0.0" zoom="1.0" x="0" y="0" shake="0"/>
            <keyframe time="2.5" zoom="1.25" x="200" y="-50" shake="3"/>
        </camera>

        <dialogue character="Ayame" start="1.5" duration="2.8" position="bottom" style="anime_box">
            Ren, the perimeter sensors have been breached!
        </dialogue>
    </scene>

    <!-- Scene 2: Ren steps forward with dramatic eye flare -->
    <scene id="scene2" start="4.5" duration="4.5" background="bg_cyber">
        <effect type="particles" start="0.0" duration="4.5" intensity="1.2" color="#00E5FF"/>
        <effect type="flash" start="0.0" duration="0.4" intensity="0.8" color="#FFFFFF"/>
        <audio ref="sfx_laser" start="0.1" volume="1.0"/>

        <character ref="ayame_ninja" x="600" y="650" width="380" height="650" layer="1">
            <animation type="action" name="idle"/>
        </character>

        <character ref="hero_ren" x="1250" y="620" width="460" height="760" layer="2">
            <animation type="move">
                <keyframe time="0.0" x="1500" y="620" easing="ease_out"/>
                <keyframe time="1.5" x="1200" y="620" easing="ease_in_out"/>
            </animation>
            <animation type="action" name="attack"/>
            <animation type="expression" name="serious"/>
        </character>

        <camera type="shake">
            <keyframe time="0.0" zoom="1.3" x="100" y="-30" shake="8"/>
            <keyframe time="1.0" zoom="1.3" x="100" y="-30" shake="2"/>
            <keyframe time="4.0" zoom="1.1" x="0" y="0" shake="0"/>
        </camera>

        <dialogue character="Ren" start="1.0" duration="3.2" position="bottom" style="anime_box">
            I will never give up! Let's show them our true power!
        </dialogue>
    </scene>
</anime>
                """.trimIndent()
            ),
            AnimeProject(
                title = "Cherry Blossom Shrine",
                description = "Tranquil anime scene at a Shinto courtyard with falling sakura petals and peaceful dialogue.",
                width = 1920,
                height = 1080,
                fps = 30,
                duration = 8.0f,
                xmlContent = """
<anime version="1.0" title="Cherry Blossom Shrine">
    <settings width="1920" height="1080" fps="30" duration="8.0"/>
    
    <assets>
        <background id="bg_shrine" src="bg_cherry_temple.jpg" label="Cherry Blossom Temple"/>
        <character id="ayame" src="char_ayame_ninja.png" label="Ayame"/>
        <audio id="sfx_energy" src="sfx_energy_up.wav" type="sfx"/>
    </assets>

    <scene id="scene1" start="0.0" duration="8.0" background="bg_shrine">
        <effect type="snow" start="0.0" duration="8.0" intensity="1.0" color="#FFB7C5"/>
        <effect type="glow" start="0.0" duration="8.0" intensity="0.5"/>
        <audio ref="sfx_energy" start="0.5" volume="0.8"/>

        <character ref="ayame" x="960" y="640" width="400" height="680" layer="1">
            <animation type="scale">
                <keyframe time="0.0" scale="0.95"/>
                <keyframe time="4.0" scale="1.05"/>
            </animation>
            <animation type="action" name="idle"/>
            <animation type="expression" name="happy"/>
        </character>

        <camera type="pan">
            <keyframe time="0.0" zoom="1.0" x="-100" y="0"/>
            <keyframe time="6.0" zoom="1.15" x="50" y="-40"/>
        </camera>

        <dialogue character="Ayame" start="2.0" duration="4.5" position="bottom" style="anime_box">
            Even under the heaviest storm, the sakura petals find their way to bloom.
        </dialogue>
    </scene>
</anime>
                """.trimIndent()
            ),
            AnimeProject(
                title = "Heroic Charge",
                description = "Fast-paced single character sprint with camera follow, motion particles, and sword strike.",
                width = 1920,
                height = 1080,
                fps = 30,
                duration = 6.0f,
                xmlContent = """
<anime version="1.0" title="Heroic Charge">
    <settings width="1920" height="1080" fps="30" duration="6.0"/>
    
    <assets>
        <background id="bg_cyber" src="bg_cyber_tokyo.jpg"/>
        <character id="hero_ren" src="char_hero_ren.png"/>
        <audio id="sfx_slash" src="sfx_slash.wav"/>
    </assets>

    <scene id="scene1" start="0.0" duration="6.0" background="bg_cyber">
        <effect type="particles" start="0.0" duration="6.0" intensity="1.5" color="#00E5FF"/>
        <audio ref="sfx_slash" start="2.5" volume="1.0"/>

        <character ref="hero_ren" x="300" y="640" width="440" height="740" layer="1">
            <animation type="move">
                <keyframe time="0.0" x="200" y="640" easing="linear"/>
                <keyframe time="2.4" x="960" y="640" easing="ease_in"/>
                <keyframe time="3.0" x="1100" y="580" easing="ease_out"/>
                <keyframe time="6.0" x="1700" y="640" easing="ease_in_out"/>
            </animation>
            <animation type="action" name="run"/>
            <animation type="expression" name="serious"/>
        </character>

        <camera type="zoom">
            <keyframe time="0.0" zoom="1.0" x="0" y="0" shake="0"/>
            <keyframe time="2.5" zoom="1.4" x="150" y="-40" shake="10"/>
            <keyframe time="3.5" zoom="1.1" x="0" y="0" shake="1"/>
        </camera>

        <dialogue character="Ren" start="1.0" duration="3.0" position="bottom" style="anime_box">
            Nothing can stand in our path now!
        </dialogue>
    </scene>
</anime>
                """.trimIndent()
            )
        )
    }
}
