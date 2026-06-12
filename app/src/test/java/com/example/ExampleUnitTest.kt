package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testContactSanitizerAndScoring() {
    val sanitizedContact = ContactResolver.sanitizeName("Nazeer ❤️")
    val sanitizedQuery = ContactResolver.sanitizeName("Nazeer")
    assertEquals("nazeer", sanitizedContact)
    assertEquals("nazeer", sanitizedQuery)

    val score = ContactResolver.calculateMatchScore("Nazeer ❤️", "Nazeer")
    assertEquals(1.0f, score, 0.001f)

    // Guarantees "hi" does not match "Mohit"
    val hiScore = ContactResolver.calculateMatchScore("Mohit", "hi")
    assertTrue(hiScore < 0.35f)

    // Guarantees "naz" or "naze" is a strong match for Nazeer
    val nazScore = ContactResolver.calculateMatchScore("Nazeer", "naz")
    assertTrue(nazScore >= 0.80f)

    val moScore = ContactResolver.calculateMatchScore("Mohit", "mo")
    assertTrue(moScore >= 0.80f)
  }

  @Test
  fun testMessageAndContactParsing() {
    // Pattern 1: send {message} to {contact}
    val res1 = ContactResolver.parseMessageAndContact("send hi to nazeer")
    assertEquals("nazeer", res1.first.lowercase())
    assertEquals("hi", res1.second)

    val res2 = ContactResolver.parseMessageAndContact("send hello bro to bittu")
    assertEquals("bittu", res2.first.lowercase())
    assertEquals("hello bro", res2.second)

    // Pattern 2: message {contact} {message}
    val res3 = ContactResolver.parseMessageAndContact("message bittu hi there")
    assertEquals("bittu", res3.first.lowercase())
    assertEquals("hi there", res3.second)

    // Pattern 3: send {contact} {message}
    val res4 = ContactResolver.parseMessageAndContact("send nazeer good morning")
    assertEquals("nazeer", res4.first.lowercase())
    assertEquals("good morning", res4.second)

    // User reported patterns
    val res5 = ContactResolver.parseMessageAndContact("message hi to nazeer")
    assertEquals("nazeer", res5.first.lowercase())
    assertEquals("hi", res5.second)

    val res6 = ContactResolver.parseMessageAndContact("message nazeer hi")
    assertEquals("nazeer", res6.first.lowercase())
    assertEquals("hi", res6.second)
  }

  @Test
  fun testYouTubeQueryParsing() {
    val query1 = ContactResolver.parseYouTubeQuery("open yt and play arz kiya")
    assertEquals("arz kiya", query1)

    val query2 = ContactResolver.parseYouTubeQuery("open youtube and play believer")
    assertEquals("believer", query2)

    val query3 = ContactResolver.parseYouTubeQuery("play hanuman chalisa on youtube")
    assertEquals("hanuman chalisa", query3)
  }

  @Test
  fun testConversationMemoryResolution() {
    com.example.data.ConversationMemoryResolver.resetContext()

    // 1. Test "Call him again" with lastPerson available
    com.example.data.ConversationMemoryResolver.updateContext(person = "Nazeer")
    val res1 = com.example.data.ConversationMemoryResolver.resolve("Call him again")
    assertTrue(res1 is com.example.data.ConversationMemoryResolver.Resolution.Resolved)
    assertEquals("Call Nazeer", (res1 as com.example.data.ConversationMemoryResolver.Resolution.Resolved).query)

    // 2. Test "What is the score?" -> "Now?"
    com.example.data.ConversationMemoryResolver.resetContext()
    com.example.data.ConversationMemoryResolver.updateContext(topic = "cricket score")
    val res2 = com.example.data.ConversationMemoryResolver.resolve("Now?")
    assertTrue(res2 is com.example.data.ConversationMemoryResolver.Resolution.Resolved)
    assertEquals("refresh cricket score", (res2 as com.example.data.ConversationMemoryResolver.Resolution.Resolved).query)

    // 3. Test "Search Hyderabad weather" -> "Save it"
    com.example.data.ConversationMemoryResolver.resetContext()
    com.example.data.ConversationMemoryResolver.updateContext(topic = "Hyderabad weather")
    val res3 = com.example.data.ConversationMemoryResolver.resolve("Save it")
    assertTrue(res3 is com.example.data.ConversationMemoryResolver.Resolution.Resolved)
    assertEquals("hyderabad weather save to notes", (res3 as com.example.data.ConversationMemoryResolver.Resolution.Resolved).query)

    // 4. Test "Open YouTube" -> "Close it"
    com.example.data.ConversationMemoryResolver.resetContext()
    com.example.data.ConversationMemoryResolver.updateContext(app = "YouTube")
    val res4 = com.example.data.ConversationMemoryResolver.resolve("Close it")
    assertTrue(res4 is com.example.data.ConversationMemoryResolver.Resolution.Resolved)
    assertEquals("close YouTube", (res4 as com.example.data.ConversationMemoryResolver.Resolution.Resolved).query)

    // 5. Test Low Confidence (Ambiguous context) -> Call him with no context
    com.example.data.ConversationMemoryResolver.resetContext()
    val res5 = com.example.data.ConversationMemoryResolver.resolve("Call him")
    assertTrue(res5 is com.example.data.ConversationMemoryResolver.Resolution.Ambiguous)
    assertEquals("Who would you like to call?", (res5 as com.example.data.ConversationMemoryResolver.Resolution.Ambiguous).prompt)
  }

  @Test
  fun testPlannerEngine() {
    // 1. Weather and notes plan
    val plan1 = com.example.data.PlannerEngine.createPlan("Find Hyderabad weather and save it to notes")
    assertEquals("Find Hyderabad weather and save it to notes", plan1.goal)
    assertEquals(6, plan1.steps.size)
    assertEquals("Search weather", plan1.steps[0].description)
    assertEquals("Extract result", plan1.steps[1].description)
    assertEquals("Open notes", plan1.steps[2].description)
    assertEquals("Verify note exists", plan1.steps[5].description)

    // 2. Call and speaker plan
    val plan2 = com.example.data.PlannerEngine.createPlan("Call Nazeer and enable speaker")
    assertEquals("Call Nazeer and enable speaker", plan2.goal)
    assertEquals(5, plan2.steps.size)
    assertEquals("Resolve contact", plan2.steps[0].description)
    assertEquals("Place call", plan2.steps[1].description)
    assertEquals("Wait for connected state", plan2.steps[2].description)
    assertEquals("Enable speaker", plan2.steps[3].description)
    assertEquals("Verify speaker enabled", plan2.steps[4].description)

    // 3. Open Blinkit and add milk plan
    val plan3 = com.example.data.PlannerEngine.createPlan("Open Blinkit and add milk")
    assertEquals("Open Blinkit and add milk", plan3.goal)
    assertEquals(5, plan3.steps.size)
    assertEquals("Open Blinkit", plan3.steps[0].description)
    assertEquals("Search milk", plan3.steps[1].description)
    assertEquals("Add to cart", plan3.steps[3].description)
    assertEquals("Verify cart updated", plan3.steps[4].description)

    // 4. Search free AI APIs and save to notes plan
    val plan4 = com.example.data.PlannerEngine.createPlan("Search free AI APIs and save to notes")
    assertEquals("Search free AI APIs and save to notes", plan4.goal)
    assertEquals(6, plan4.steps.size)
    assertEquals("Browser search", plan4.steps[0].description)
    assertEquals("Extract results", plan4.steps[1].description)
    assertEquals("Format summary", plan4.steps[2].description)
    assertEquals("Create note", plan4.steps[3].description)
    assertEquals("Save", plan4.steps[4].description)
    assertEquals("Verify", plan4.steps[5].description)
  }
}
