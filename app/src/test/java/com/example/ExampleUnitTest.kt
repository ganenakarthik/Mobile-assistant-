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
}
