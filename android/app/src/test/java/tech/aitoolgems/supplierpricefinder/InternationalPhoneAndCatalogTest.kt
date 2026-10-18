package tech.aitoolgems.supplierpricefinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.aitoolgems.supplierpricefinder.data.local.SeedObservations
import tech.aitoolgems.supplierpricefinder.data.local.SeedSuppliers
import tech.aitoolgems.supplierpricefinder.data.model.AccessType
import tech.aitoolgems.supplierpricefinder.data.model.VerificationStatus

class InternationalPhoneAndCatalogTest {

    @Test
    fun testWorldwideInternationalPhoneRegex() {
        val regex = "^\\+[1-9]\\d{6,14}$".toRegex()

        val validNumbers = listOf(
            "+819012345678", // Japan
            "+818012345678", // Japan mobile
            "+14155552671",  // USA
            "+447911123456", // UK
            "+923001234567", // Pakistan
            "+4915123456789",// Germany
            "+971501234567", // UAE
            "+821012345678", // South Korea
            "+919876543210", // India
            "+61412345678"   // Australia
        )

        for (phone in validNumbers) {
            assertTrue("Expected $phone to be a valid international phone number", regex.matches(phone))
        }

        val invalidNumbers = listOf(
            "03001234567",   // missing leading +
            "+0123456789",   // country code cannot start with 0
            "+123",          // too short (< 7 digits)
            "phone123456",   // non-numeric
            "+12345678901234567" // too long (> 15 digits)
        )

        for (phone in invalidNumbers) {
            assertFalse("Expected $phone to be rejected", regex.matches(phone))
        }
    }

    @Test
    fun testSeedObservationsCatalogIntegrity() {
        val obs = SeedObservations.CANONICAL_OBSERVATIONS
        assertTrue("Expected at least 50 pre-populated canonical quotes, but got ${obs.size}", obs.size >= 50)

        // Check products covered
        val productNames = obs.map { it.productName.lowercase() }
        assertTrue("Must include ChatGPT", productNames.any { it.contains("chatgpt") })
        assertTrue("Must include CapCut", productNames.any { it.contains("capcut") })
        assertTrue("Must include Netflix", productNames.any { it.contains("netflix") })
        assertTrue("Must include VPN", productNames.any { it.contains("vpn") })
        assertTrue("Must include Adobe", productNames.any { it.contains("adobe") })

        // Check prices and currencies
        for (item in obs) {
            assertTrue("Amount must be positive", item.amount > 0.0)
            assertTrue("PKR must be positive", item.amountPkr > 0.0)
            assertEquals("Currency must be USD or USDT", "USD", item.currency)
            assertEquals("Seed observations must be UNVERIFIED until live scan", VerificationStatus.USER_SUPPLIED_UNVERIFIED, item.verificationStatus)
        }
    }
}
