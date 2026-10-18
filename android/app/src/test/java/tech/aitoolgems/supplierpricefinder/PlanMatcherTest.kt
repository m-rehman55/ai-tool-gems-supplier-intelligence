package tech.aitoolgems.supplierpricefinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.aitoolgems.supplierpricefinder.data.model.AccessType
import tech.aitoolgems.supplierpricefinder.engine.PlanMatcher

class PlanMatcherTest {

    @Test
    fun testParseDuration() {
        assertEquals("1 Month", PlanMatcher.parseDuration("ChatGPT Plus 1 Month Private"))
        assertEquals("12 Months", PlanMatcher.parseDuration("Perplexity Pro 12 Months"))
        assertEquals("1 Year", PlanMatcher.parseDuration("Coursera Plus 1 Year"))
        assertEquals("2 Years", PlanMatcher.parseDuration("ChatGPT Edu 2 Years"))
        assertEquals("Lifetime", PlanMatcher.parseDuration("Proton VPN Lifetime"))
        assertEquals("28 Days", PlanMatcher.parseDuration("Zoom Pro 28 Days"))
    }

    @Test
    fun testParseAccessType() {
        assertEquals(AccessType.PRIVATE, PlanMatcher.parseAccessType("ChatGPT Plus 1 Month Private Account"))
        assertEquals(AccessType.SHARED, PlanMatcher.parseAccessType("Netflix 4K UHD 1 Profile Slot Shared"))
        assertEquals(AccessType.EDU, PlanMatcher.parseAccessType("JetBrains All IDEs Student Edu Email"))
        assertEquals(AccessType.TEAM, PlanMatcher.parseAccessType("ChatGPT Business Team Admin Panel"))
        assertEquals(AccessType.KEY, PlanMatcher.parseAccessType("Windows 11 Pro Retail License Key"))
    }

    @Test
    fun testMatchingKeyUniqueness() {
        val privateKey = PlanMatcher.generateMatchingKey("ChatGPT", "ChatGPT Plus", "1 Month", AccessType.PRIVATE)
        val sharedKey = PlanMatcher.generateMatchingKey("ChatGPT", "ChatGPT Plus", "1 Month", AccessType.SHARED)
        val eduKey = PlanMatcher.generateMatchingKey("ChatGPT", "ChatGPT Plus", "1 Month", AccessType.EDU)
        val annualKey = PlanMatcher.generateMatchingKey("ChatGPT", "ChatGPT Plus", "12 Months", AccessType.PRIVATE)

        assertNotEquals(privateKey, sharedKey)
        assertNotEquals(privateKey, eduKey)
        assertNotEquals(privateKey, annualKey)
    }

    @Test
    fun testArePlansComparable() {
        // Equal
        val (match1, _) = PlanMatcher.arePlansComparable("1 Month", AccessType.PRIVATE, "1 Month", AccessType.PRIVATE)
        assertTrue(match1)

        // Different durations
        val (match2, reason2) = PlanMatcher.arePlansComparable("1 Month", AccessType.PRIVATE, "12 Months", AccessType.PRIVATE)
        assertFalse(match2)
        assertTrue(reason2.contains("Different durations"))

        // Different access models (Private vs Shared)
        val (match3, reason3) = PlanMatcher.arePlansComparable("1 Month", AccessType.PRIVATE, "1 Month", AccessType.SHARED)
        assertFalse(match3)
        assertTrue(reason3.contains("Different account types"))
    }
}
