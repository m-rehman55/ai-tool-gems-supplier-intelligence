package tech.aitoolgems.supplierpricefinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.aitoolgems.supplierpricefinder.data.local.SeedSuppliers

class SupplierDirectoryTest {

    @Test
    fun testCanonicalSuppliersDeduplicated() {
        val suppliers = SeedSuppliers.CANONICAL_BOTS

        // Verify distinct primary handles
        val primaryHandles = suppliers.map { it.primaryHandle.lowercase() }
        val uniqueHandles = primaryHandles.toSet()

        assertEquals("Supplier primary handles must be unique without duplicates!", uniqueHandles.size, primaryHandles.size)
    }

    @Test
    fun testReconciledRequiredBotLeads() {
        val handles = SeedSuppliers.CANONICAL_BOTS.map { it.primaryHandle.lowercase() }

        val expectedLeads = listOf(
            "@getdigitaltools_bot",
            "@piggyai799_bot",
            "@sellnitystorebot",
            "@novasubglo_bot",
            "@premikeybot",
            "@aitoolify_bot",
            "@premiumtools_bot",
            "@ai_corn_bot",
            "@fatbunny_hub_bot",
            "@ptstorebd_bot",
            "@verified_streams_bot",
            "@aitoolpre_bi_bot",
            "@spaivault_bot",
            "@prmastbot",
            "@storepremium_bot",
            "@mediahubmarketplace_bot"
        )

        for (expected in expectedLeads) {
            assertTrue("Expected supplier lead '$expected' to be present in directory!", handles.contains(expected.lowercase()))
        }
    }
}
