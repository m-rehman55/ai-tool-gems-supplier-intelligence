package tech.aitoolgems.supplierpricefinder.telegram

data class BotButton(val text: String, val callbackData: String)

data class BotMessage(
    val messageId: Long,
    val text: String,
    val buttons: List<List<BotButton>> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

object MockTelegramFixtures {

    val DIGICART_SHOP_MENU = BotMessage(
        messageId = 101,
        text = "👋 Welcome to DigiCart Tools!\nSelect an option below to browse active stock:",
        buttons = listOf(
            listOf(BotButton("📦 Products", "cat_products"), BotButton("ℹ️ Support", "menu_support")),
            listOf(BotButton("🛒 Buy Now", "action_checkout_direct")) // Will be blocked by SafetyBoundary!
        )
    )

    val DIGICART_PRODUCTS_LIST = BotMessage(
        messageId = 102,
        text = "🔥 Available Tools Catalog:\n\n" +
                "1. ChatGPT Plus (1 Month Private) - Full Warranty (FW)\n" +
                "   Price: $4.80 USDT | Stock: 35 available\n\n" +
                "2. ChatGPT Plus Edu / K12 (2 Years)\n" +
                "   Price: $4.63 USDT | Stock: 10 available\n\n" +
                "3. Google Gemini Pro (18M Private)\n" +
                "   Price: $0.45 USDT | Full Warranty (FW)\n\n" +
                "4. JetBrains All IDEs (1 Year Edu)\n" +
                "   Price: $3.70 USDT | Stock: 5 available",
        buttons = listOf(
            listOf(BotButton("ChatGPT Plus (1M)", "item_chatgpt_1m")),
            listOf(BotButton("Gemini Pro", "item_gemini_pro")),
            listOf(BotButton("Next Page >>", "page_2"))
        )
    )

    val PIGGYAI_SHOP_MENU = BotMessage(
        messageId = 201,
        text = "🐷 PiggyAI Premium Wholesale Shop:\n" +
                "• ChatGPT Plus (Private 1M): $7.62 USDT (1 Day Warranty)\n" +
                "• CapCut Pro (1 Month): $1.27 USDT\n" +
                "• Netflix 4K UHD 3M: $3.24 USDT (Shared Profile)",
        buttons = listOf(
            listOf(BotButton("🛍 Open Shop", "open_shop_catalog")),
            listOf(BotButton("💳 Top Up Wallet", "wallet_deposit")) // Will be blocked!
        )
    )

    val SPAM_REFERRAL_MESSAGE = BotMessage(
        messageId = 301,
        text = "🎁 Earn FREE balance!\nInvite your friends to AI TOOLS and get $0.10 in your wallet for every friend who joins and verifies 💰\nInvite 10 friends = $1 free → use it on ChatGPT, Gemini, Claude & more 🚀\n👉 Tap Refer & Earn in the menu to get your link!",
        buttons = listOf(listOf(BotButton("Refer & Earn", "referral_link")))
    )
}
