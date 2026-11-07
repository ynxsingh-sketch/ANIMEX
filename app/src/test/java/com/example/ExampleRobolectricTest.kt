package com.example

import com.example.database.AnimeProject
import com.example.model.SettingsDef
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleRobolectricTest {

  @Test
  fun testAnimeProjectCreation() {
    val project = AnimeProject(
      id = 1L,
      title = "Anime XML Studio",
      xmlContent = "<anime/>",
      width = 1920,
      height = 1080,
      fps = 30,
      duration = 10.0f
    )
    assertEquals("Anime XML Studio", project.title)
    assertEquals(1920, project.width)
    assertEquals(1080, project.height)
    assertEquals(16f / 9f, SettingsDef(1920, 1080).aspectRatio, 0.01f)
  }
}
