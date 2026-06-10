package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Nova", appName)
  }

  @Test
  fun `test AgentReach channels are returned correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val channels = com.example.data.AgentReachIntegrationEngine.getChannels(context)
    
    // Check that standard channels exist
    val containsGithub = channels.any { it.name == "github" }
    val containsReddit = channels.any { it.name == "reddit" }
    val containsTwitter = channels.any { it.name == "twitter" }
    
    assertEquals(true, containsGithub)
    assertEquals(true, containsReddit)
    assertEquals(true, containsTwitter)
    
    // Verify total size equals 12 channels
    assertEquals(12, channels.size)
  }

  @Test
  fun `verify public apis catalog has robust standard definitions`() {
    val apisList = com.example.ui.getStaticPublicApisCatalog()
    
    // Check total catalog count
    assertEquals(true, apisList.size >= 25)
    
    // Verify all major categories exist
    val categories = apisList.map { it.category }.toSet()
    assertEquals(true, categories.contains("Animals"))
    assertEquals(true, categories.contains("Anime"))
    assertEquals(true, categories.contains("Finance"))
    assertEquals(true, categories.contains("Development"))
    assertEquals(true, categories.contains("Games"))
    assertEquals(true, categories.contains("Science"))
    assertEquals(true, categories.contains("Weather"))
    
    // Check key APIs for authenticity
    val jikanApi = apisList.find { it.title == "Jikan v4 REST" }
    assertEquals(true, jikanApi != null)
    assertEquals("https://api.jikan.moe/v4", jikanApi?.url)
    assertEquals("None", jikanApi?.auth)
    assertEquals(true, jikanApi?.supportsHttps)
    
    val openWeather = apisList.find { it.title == "OpenWeather Portal" }
    assertEquals(true, openWeather != null)
    assertEquals("apiKey", openWeather?.auth)
    assertEquals(true, openWeather?.supportsHttps)
  }
}
