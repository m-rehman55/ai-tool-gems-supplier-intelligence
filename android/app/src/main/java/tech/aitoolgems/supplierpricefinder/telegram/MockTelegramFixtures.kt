package tech.aitoolgems.supplierpricefinder.telegram

import tech.aitoolgems.supplierpricefinder.data.model.CrawlerButton
import tech.aitoolgems.supplierpricefinder.data.model.CrawlerButtonType
import tech.aitoolgems.supplierpricefinder.data.model.CrawlerMenu

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
            listOf(BotButton("🛒 Buy Now", "action_checkout_direct")) // Blocked by SafetyBoundary!
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
            listOf(BotButton("💳 Top Up Wallet", "wallet_deposit")) // Blocked!
        )
    )

    val SPAM_REFERRAL_MESSAGE = BotMessage(
        messageId = 301,
        text = "🎁 Earn FREE balance!\nInvite your friends to AI TOOLS and get $0.10 in your wallet for every friend who joins and verifies 💰\nInvite 10 friends = $1 free → use it on ChatGPT, Gemini, Claude & more 🚀\n👉 Tap Refer & Earn in the menu to get your link!",
        buttons = listOf(listOf(BotButton("Refer & Earn", "referral_link")))
    )

    // Complete realistic CrawlerMenu fixtures for end-to-end traversal testing
    val MENU_LANGUAGE_SELECTION = CrawlerMenu(
        chatId = 1001,
        messageId = 501,
        rawText = "Please select your preferred language:\nПожалуйста, выберите язык:",
        buttons = listOf(
            listOf(
                CrawlerButton("🇬🇧 English", CrawlerButtonType.INLINE_CALLBACK, "lang_en"),
                CrawlerButton("🇷🇺 Русский", CrawlerButtonType.INLINE_CALLBACK, "lang_ru")
            )
        )
    )

    val MENU_MEMBERSHIP_GATE = CrawlerMenu(
        chatId = 1001,
        messageId = 502,
        rawText = "⚠️ Access Gate!\nYou must join our official updates channel @digicart_updates before using the shop.",
        buttons = listOf(
            listOf(
                CrawlerButton("📢 Join Channel", CrawlerButtonType.URL_LINK, "", "https://t.me/digicart_updates"),
                CrawlerButton("✅ Joined / Verify", CrawlerButtonType.INLINE_CALLBACK, "check_member")
            )
        )
    )

    val MENU_CATALOG_ENTRY = CrawlerMenu(
        chatId = 1001,
        messageId = 503,
        rawText = "🎉 Welcome to the Wholesale Shop! Choose a category below:",
        buttons = listOf(
            listOf(
                CrawlerButton("📦 All Products", CrawlerButtonType.INLINE_CALLBACK, "cat_all"),
                CrawlerButton("🤖 AI Tools", CrawlerButtonType.INLINE_CALLBACK, "cat_ai")
            ),
            listOf(
                CrawlerButton("💳 Deposit Balance", CrawlerButtonType.INLINE_CALLBACK, "deposit_money"), // BLOCKED
                CrawlerButton("🛒 Checkout Cart", CrawlerButtonType.BUY_TRANSACTION, "") // BLOCKED
            )
        )
    )

    val MENU_MULTI_VARIANT_PRODUCT = CrawlerMenu(
        chatId = 1001,
        messageId = 504,
        rawText = "🤖 ChatGPT Plus (OpenAI Official Account)\n" +
                "Select desired plan duration and access type below:",
        buttons = listOf(
            listOf(
                CrawlerButton("1 Day ($1.00 USD)", CrawlerButtonType.INLINE_CALLBACK, "variant_1d"),
                CrawlerButton("1 Month ($5.00 USD)", CrawlerButtonType.INLINE_CALLBACK, "variant_1m"),
                CrawlerButton("4 Months ($18.00 USD)", CrawlerButtonType.INLINE_CALLBACK, "variant_4m")
            ),
            listOf(
                CrawlerButton("🔙 Back to Catalog", CrawlerButtonType.INLINE_CALLBACK, "back_catalog")
            )
        )
    )

    val MENU_VARIANT_1_DAY_DETAILS = CrawlerMenu(
        chatId = 1001,
        messageId = 505,
        rawText = "ChatGPT Plus — 1 Day Shared Slot (Instant Mail Delivery)\n" +
                "Price: $1.00 USD | Stock: 42 available | Full Warranty (FW)",
        buttons = listOf(
            listOf(CrawlerButton("🔙 Back", CrawlerButtonType.INLINE_CALLBACK, "back_variants"))
        )
    )

    val MENU_VARIANT_1_MONTH_DETAILS = CrawlerMenu(
        chatId = 1001,
        messageId = 506,
        rawText = "ChatGPT Plus — 1 Month Private Account (Personal Email)\n" +
                "Price: $5.00 USD | Stock: 20 available | Full Warranty (FW)",
        buttons = listOf(
            listOf(CrawlerButton("🔙 Back", CrawlerButtonType.INLINE_CALLBACK, "back_variants"))
        )
    )

    val MENU_VARIANT_4_MONTH_DETAILS = CrawlerMenu(
        chatId = 1001,
        messageId = 507,
        rawText = "ChatGPT Plus — 4 Months Private Account (Full Warranty)\n" +
                "Price: $18.00 USD | Stock: 8 available | Full Warranty (FW)",
        buttons = listOf(
            listOf(CrawlerButton("🔙 Back", CrawlerButtonType.INLINE_CALLBACK, "back_variants"))
        )
    )

    val MENU_PAGINATION_PAGE_1 = CrawlerMenu(
        chatId = 1001,
        messageId = 510,
        rawText = "Page 1 of 2:\n1. Canva Pro 1 Year ($2.50 USD)\n2. Netflix Premium 1 Month ($3.00 USD)",
        buttons = listOf(
            listOf(CrawlerButton("Next Page >>", CrawlerButtonType.INLINE_CALLBACK, "page_2"))
        )
    )

    val MENU_PAGINATION_PAGE_2 = CrawlerMenu(
        chatId = 1001,
        messageId = 511,
        rawText = "Page 2 of 2:\n3. Google Gemini 1 Month ($1.50 USD)\n4. Claude Pro 1 Month ($6.00 USD)",
        buttons = listOf(
            listOf(CrawlerButton("<< Prev Page", CrawlerButtonType.INLINE_CALLBACK, "page_1"))
        )
    )

    val MENU_BARE_BUY_AMBIGUOUS = CrawlerMenu(
        chatId = 1002,
        messageId = 601,
        rawText = "Welcome to ToolSpot! Click below:",
        buttons = listOf(
            listOf(CrawlerButton("Buy", CrawlerButtonType.INLINE_CALLBACK, "action_buy"))
        )
    )
}
