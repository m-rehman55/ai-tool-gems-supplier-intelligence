package tech.aitoolgems.supplierpricefinder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.aitoolgems.supplierpricefinder.data.model.NavigationSafety
import tech.aitoolgems.supplierpricefinder.engine.SafetyBoundary

class SafetyBoundaryTest {

    @Test
    fun testBlockForbiddenPurchaseButtons() {
        val forbiddenTexts = listOf(
            "Buy Now",
            "Purchase Subscription",
            "Checkout",
            "Pay $5.00",
            "Confirm Order",
            "Place Order",
            "Top Up Wallet",
            "Deposit Funds",
            "Generate Invoice",
            "Pay with Crypto"
        )

        for (btn in forbiddenTexts) {
            val safety = SafetyBoundary.checkButtonSafety(btn)
            assertFalse("Expected '$btn' to be BLOCKED, but was SAFE!", safety.isSafe)
            assertTrue("Expected BLOCKED_PURCHASE_RISK", safety == NavigationSafety.BLOCKED_PURCHASE_RISK)
        }
    }

    @Test
    fun testBlockForbiddenCallbacks() {
        val forbiddenCallbacks = listOf(
            "buy_product_123",
            "checkout_order",
            "pay_invoice_99",
            "deposit_usdt"
        )

        for (cb in forbiddenCallbacks) {
            val safety = SafetyBoundary.checkButtonSafety("Click Here", cb)
            assertFalse("Expected callback '$cb' to be BLOCKED!", safety.isSafe)
        }
    }

    @Test
    fun testAllowSafeCatalogNavigation() {
        val safeButtons = listOf(
            "📦 Products",
            "🛍 Shop",
            "Store Menu",
            "AI Tools",
            "Next Page >>",
            "<< Previous",
            "Back to Main Menu",
            "View Details"
        )

        for (btn in safeButtons) {
            val safety = SafetyBoundary.checkButtonSafety(btn)
            assertTrue("Expected safe navigation for '$btn'!", safety.isSafe)
            assertTrue("Expected isCatalogNavigationButton to be true for '$btn'", SafetyBoundary.isCatalogNavigationButton(btn))
        }
    }
}
