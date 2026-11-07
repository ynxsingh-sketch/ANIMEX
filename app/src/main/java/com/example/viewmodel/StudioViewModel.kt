package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AnimeAudioEngine
import com.example.data.ProjectRepository
import com.example.database.AnimeProject
import com.example.exporter.VideoExporter
import com.example.model.*
import com.example.parser.AnimeXmlParser
import com.example.validator.AnimeXmlValidator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

enum class StudioScreen {
    HOME,
    EDITOR,
    PREVIEW,
    TIMELINE,
    ASSETS,
    SETTINGS
}

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(application)
    private val parser = AnimeXmlParser()
    private val validator = AnimeXmlValidator()
    private val exporter = VideoExporter(application)
    val audioEngine = AnimeAudioEngine(application)

    val allProjects: StateFlow<List<AnimeProject>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentScreen = MutableStateFlow(StudioScreen.HOME)
    val currentScreen: StateFlow<StudioScreen> = _currentScreen.asStateFlow()

    private val _currentProject = MutableStateFlow<AnimeProject?>(null)
    val currentProject: StateFlow<AnimeProject?> = _currentProject.asStateFlow()

    private val _xmlCode = MutableStateFlow("")
    val xmlCode: StateFlow<String> = _xmlCode.asStateFlow()

    private val _validationResult = MutableStateFlow<ValidationResult?>(null)
    val validationResult: StateFlow<ValidationResult?> = _validationResult.asStateFlow()

    private val _parsedDoc = MutableStateFlow<AnimeDoc?>(null)
    val parsedDoc: StateFlow<AnimeDoc?> = _parsedDoc.asStateFlow()

    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTime = MutableStateFlow(0f)
    val currentTime: StateFlow<Float> = _currentTime.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isLooping = MutableStateFlow(true)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    // Export state
    val exportProgress: StateFlow<ExportProgress> = exporter.exportProgress

    private val _isExportDialogOpen = MutableStateFlow(false)
    val isExportDialogOpen: StateFlow<Boolean> = _isExportDialogOpen.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var playbackJob: Job? = null

    // Undo / Redo history
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()

    // Navigation back stack for step-by-step back traversal
    private val navBackStack = mutableListOf<StudioScreen>()

    init {
        viewModelScope.launch {
            repository.seedSampleProjectsIfEmpty()
        }
    }

    fun navigateTo(screen: StudioScreen, addToStack: Boolean = true) {
        if (screen != StudioScreen.PREVIEW && _isPlaying.value) {
            pause()
        }
        if (addToStack && _currentScreen.value != screen) {
            navBackStack.add(_currentScreen.value)
        }
        _currentScreen.value = screen
    }

    fun popBack(): Boolean {
        if (_isPlaying.value) {
            pause()
        }
        if (navBackStack.isNotEmpty()) {
            val previous = navBackStack.removeAt(navBackStack.size - 1)
            _currentScreen.value = previous
            return true
        } else if (_currentScreen.value != StudioScreen.HOME) {
            _currentScreen.value = StudioScreen.HOME
            return true
        }
        return false
    }

    fun canGoBack(): Boolean {
        return navBackStack.isNotEmpty() || _currentScreen.value != StudioScreen.HOME
    }

    fun openProject(project: AnimeProject) {
        if (_currentScreen.value != StudioScreen.PREVIEW) {
            navBackStack.add(_currentScreen.value)
        }
        _currentProject.value = project
        _xmlCode.value = project.xmlContent
        undoStack.clear()
        redoStack.clear()
        undoStack.add(project.xmlContent)

        validateXml()
        val doc = _validationResult.value?.doc ?: try { parser.parse(project.xmlContent) } catch (_: Exception) { null }
        if (doc != null) {
            _parsedDoc.value = doc
            audioEngine.prepareAssets(doc, repository.getProjectDir(project.id))
        }
        _currentTime.value = 0f
        _currentScreen.value = StudioScreen.PREVIEW
    }

    fun importAndPreviewXml(rawXml: String, title: String = "Imported Anime Project") {
        val cleanXml = rawXml.trim()
        if (cleanXml.isBlank()) {
            _statusMessage.value = "Pasted XML is empty."
            return
        }

        viewModelScope.launch {
            val parsedDoc = try {
                parser.parse(cleanXml)
            } catch (e: Exception) {
                null
            }

            val newProj = AnimeProject(
                title = title.ifBlank { "Custom Animation" },
                description = "Rendered from custom XML",
                width = parsedDoc?.settings?.width ?: 1920,
                height = parsedDoc?.settings?.height ?: 1080,
                fps = parsedDoc?.settings?.fps ?: 30,
                duration = parsedDoc?.totalDuration ?: 10f,
                xmlContent = cleanXml
            )

            val id = repository.saveProject(newProj)
            val saved = repository.getProjectById(id) ?: newProj.copy(id = id)
            
            _currentProject.value = saved
            _xmlCode.value = cleanXml
            undoStack.clear()
            redoStack.clear()
            undoStack.add(cleanXml)

            validateXml()
            val doc = _validationResult.value?.doc ?: parsedDoc
            if (doc != null) {
                _parsedDoc.value = doc
                audioEngine.prepareAssets(doc, repository.getProjectDir(saved.id))
            }
            _currentTime.value = 0f
            navBackStack.add(_currentScreen.value)
            _currentScreen.value = StudioScreen.PREVIEW
            play()
            _statusMessage.value = "Custom XML loaded and playing!"
        }
    }

    fun createNewProject(title: String, sampleIndex: Int = 0) {
        viewModelScope.launch {
            val samples = repository.getSampleProjects()
            val template = if (sampleIndex in samples.indices) samples[sampleIndex] else samples.first()
            val newProj = AnimeProject(
                title = title.ifBlank { "New Anime Project" },
                description = "Created in Anime XML Studio",
                width = template.width,
                height = template.height,
                fps = template.fps,
                duration = template.duration,
                xmlContent = template.xmlContent
            )
            val id = repository.saveProject(newProj)
            val saved = repository.getProjectById(id) ?: newProj.copy(id = id)
            openProject(saved)
        }
    }

    fun updateXmlCode(newXml: String, recordHistory: Boolean = true) {
        if (recordHistory && newXml != _xmlCode.value) {
            if (undoStack.size > 50) undoStack.removeAt(0)
            undoStack.add(_xmlCode.value)
            redoStack.clear()
        }
        _xmlCode.value = newXml
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(_xmlCode.value)
            _xmlCode.value = prev
            validateXml()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(_xmlCode.value)
            _xmlCode.value = next
            validateXml()
        }
    }

    fun validateXml(): Boolean {
        val result = validator.validate(_xmlCode.value)
        _validationResult.value = result
        val doc = result.doc ?: try { parser.parse(_xmlCode.value) } catch (_: Exception) { null }
        if (doc != null) {
            _parsedDoc.value = doc
            val current = _currentProject.value
            if (current != null) {
                audioEngine.prepareAssets(doc, repository.getProjectDir(current.id))
            }
            return true
        }
        return false
    }

    fun generatePreview() {
        validateXml()
        val doc = _parsedDoc.value ?: try { parser.parse(_xmlCode.value) } catch (e: Exception) { null }
        if (doc != null) {
            _parsedDoc.value = doc
            _currentTime.value = 0f
            saveCurrentProject()
            navigateTo(StudioScreen.PREVIEW)
            play()
            _statusMessage.value = "Preview generated (${doc.scenes.size} scene(s))!"
        } else {
            _statusMessage.value = "Could not parse XML. Please check the XML format."
        }
    }

    fun saveCurrentProject() {
        val project = _currentProject.value ?: return
        viewModelScope.launch {
            val doc = _parsedDoc.value
            val updated = project.copy(
                xmlContent = _xmlCode.value,
                width = doc?.settings?.width ?: project.width,
                height = doc?.settings?.height ?: project.height,
                fps = doc?.settings?.fps ?: project.fps,
                duration = doc?.totalDuration ?: project.duration
            )
            repository.saveProject(updated)
            _currentProject.value = updated
            _statusMessage.value = "Project saved."
        }
    }

    fun deleteProject(project: AnimeProject) {
        viewModelScope.launch {
            repository.deleteProject(project)
            if (_currentProject.value?.id == project.id) {
                _currentProject.value = null
                _currentScreen.value = StudioScreen.HOME
            }
        }
    }

    fun duplicateProject(project: AnimeProject) {
        viewModelScope.launch {
            val newId = repository.duplicateProject(project)
            val newProj = repository.getProjectById(newId)
            if (newProj != null) {
                _statusMessage.value = "Project duplicated as '${newProj.title}'"
            }
        }
    }

    // Playback Controls
    fun togglePlay() {
        if (_isPlaying.value) pause() else play()
    }

    fun play() {
        val doc = _parsedDoc.value ?: return
        _isPlaying.value = true
        playbackJob?.cancel()

        playbackJob = viewModelScope.launch {
            val frameDelay = 1000L / doc.settings.fps.coerceAtLeast(1)
            var lastTime = System.currentTimeMillis()

            while (_isPlaying.value) {
                val now = System.currentTimeMillis()
                val delta = (now - lastTime) / 1000f * _playbackSpeed.value
                lastTime = now

                var nextTime = _currentTime.value + delta
                val totalDur = doc.totalDuration

                if (nextTime >= totalDur) {
                    if (_isLooping.value) {
                        nextTime = 0f
                        audioEngine.onSeek()
                    } else {
                        nextTime = totalDur
                        _isPlaying.value = false
                    }
                }

                _currentTime.value = nextTime
                audioEngine.syncTime(doc, nextTime, _isPlaying.value)

                delay(frameDelay.coerceAtLeast(10))
            }
        }
    }

    fun pause() {
        _isPlaying.value = false
        playbackJob?.cancel()
        _parsedDoc.value?.let { audioEngine.syncTime(it, _currentTime.value, false) }
    }

    fun seekTo(timeSec: Float) {
        val doc = _parsedDoc.value ?: return
        val clamped = timeSec.coerceIn(0f, doc.totalDuration)
        _currentTime.value = clamped
        audioEngine.onSeek()
        audioEngine.syncTime(doc, clamped, _isPlaying.value)
    }

    fun stepFrame(delta: Int) {
        val doc = _parsedDoc.value ?: return
        pause()
        val dt = delta.toFloat() / doc.settings.fps.toFloat()
        seekTo(_currentTime.value + dt)
    }

    fun restart() {
        seekTo(0f)
        play()
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
    }

    fun toggleLooping() {
        _isLooping.value = !_isLooping.value
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        audioEngine.setMuted(_isMuted.value)
    }

    // Snippet insertions
    fun insertSnippet(snippet: String) {
        val current = _xmlCode.value
        val updated = "$current\n$snippet\n"
        updateXmlCode(updated)
        validateXml()
    }

    fun openExportDialog() {
        _isExportDialogOpen.value = true
    }

    fun closeExportDialog() {
        _isExportDialogOpen.value = false
    }

    fun startExport(config: ExportConfig) {
        val doc = _parsedDoc.value ?: return
        closeExportDialog()

        viewModelScope.launch {
            val projDir = _currentProject.value?.let { repository.getProjectDir(it.id) }
            val result = exporter.exportVideo(doc, config, projDir)
            if (result.step == ExportStep.COMPLETED) {
                _statusMessage.value = "Video successfully exported to device!"
                val proj = _currentProject.value
                if (proj != null && result.outputFilePath != null) {
                    repository.saveProject(proj.copy(lastExportPath = result.outputFilePath))
                }
            } else if (result.step == ExportStep.ERROR) {
                _statusMessage.value = result.error ?: "Export failed"
            }
        }
    }

    fun cancelExport() {
        exporter.cancelExport()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
    }
}
