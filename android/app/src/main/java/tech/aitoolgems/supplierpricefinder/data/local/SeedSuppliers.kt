package tech.aitoolgems.supplierpricefinder.data.local

import tech.aitoolgems.supplierpricefinder.data.model.Supplier

object SeedSuppliers {
    /**
     * Deduplicated canonical supplier directory reconciling all 16+ bot leads
     * specified in the Master Implementation Prompt Phase 7.
     */
    val CANONICAL_BOTS = listOf(
        Supplier(
            id = 1,
            canonicalName = "DigiCart Tools",
            primaryHandle = "@getdigitaltools_bot",
            allHandles = "@getdigitaltools_bot, @getdigicart, @digicarttools",
            entityType = "BOT",
            publicUrl = "https://t.me/s/digicarttools",
            supportedCurrencies = "USD,USDT",
            reliabilityScore = 85f,
            notes = "Automated shop bot with fast restock announcements."
        ),
        Supplier(
            id = 2,
            canonicalName = "PiggyAI Premium",
            primaryHandle = "@PiggyAi799_Bot",
            allHandles = "@PiggyAi799_Bot, @PiggyAIPremium, @khaidayne",
            entityType = "BOT",
            publicUrl = "https://t.me/s/PiggyAIPremium",
            supportedCurrencies = "USD,USDT",
            reliabilityScore = 80f,
            notes = "Global USDT shop bot with wide AI tool catalog."
        ),
        Supplier(
            id = 3,
            canonicalName = "Sellnity Store",
            primaryHandle = "@sellnitystorebot",
            allHandles = "@sellnitystorebot, @sellnity, @sellnitystoreupdates",
            entityType = "BOT",
            publicUrl = "https://t.me/s/sellnitystoreupdates",
            supportedCurrencies = "USD,USDT",
            reliabilityScore = 88f,
            notes = "Creative and video tools, flash sales with limited timers."
        ),
        Supplier(
            id = 4,
            canonicalName = "NovaShop Premium",
            primaryHandle = "@Novasubglo_bot",
            allHandles = "@Novasubglo_bot, @novasubpre, @chupiuoi1610",
            entityType = "BOT",
            publicUrl = "https://t.me/s/novasubpre",
            supportedCurrencies = "USD,USDT",
            reliabilityScore = 75f,
            notes = "Leonardo AI, Grok, and ChatGPT Plus subscriptions."
        ),
        Supplier(
            id = 5,
            canonicalName = "PremiKey Bot",
            primaryHandle = "@PremiKeyBot",
            allHandles = "@PremiKeyBot",
            entityType = "BOT",
            publicUrl = "https://t.me/PremiKeyBot",
            supportedCurrencies = "USD,USDT",
            reliabilityScore = 70f,
            notes = "Interactive key delivery bot."
        ),
        Supplier(
            id = 6,
            canonicalName = "AI Toolify",
            primaryHandle = "@aitoolify_bot",
            allHandles = "@aitoolify_bot, @hacksxtips",
            entityType = "BOT",
            publicUrl = "https://t.me/s/hacksxtips",
            supportedCurrencies = "USD,USDT",
            reliabilityScore = 65f,
            notes = "Offers Gemini Pro, AI subscriptions, and referral credits."
        ),
        Supplier(
            id = 7,
            canonicalName = "Premium Tools Bot",
            primaryHandle = "@premiumtools_bot",
            allHandles = "@premiumtools_bot, @linkedinpremiumtop",
            entityType = "BOT",
            supportedCurrencies = "USD,USDT",
            reliabilityScore = 65f,
            notes = "Interactive bot requiring start command."
        ),
        Supplier(
            id = 8,
            canonicalName = "AI Corn Bot",
            primaryHandle = "@AI_corn_bot",
            allHandles = "@AI_corn_bot",
            entityType = "BOT",
            supportedCurrencies = "USD,USDT",
            reliabilityScore = 60f,
            notes = "Interactive AI tools menu bot."
        ),
        Supplier(
            id = 9,
            canonicalName = "FatBunny Hub",
            primaryHandle = "@FatBunny_Hub_bot",
            allHandles = "@FatBunny_Hub_bot",
            entityType = "BOT",
            supportedCurrencies = "USD,USDT",
            reliabilityScore = 65f,
            notes = "Software licenses and streaming bots."
        ),
        Supplier(
            id = 10,
            canonicalName = "PT Store BD",
            primaryHandle = "@ptstorebd_bot",
            allHandles = "@ptstorebd_bot, @ptsbd26",
            entityType = "BOT",
            supportedCurrencies = "USD,BDT,USDT",
            reliabilityScore = 65f,
            notes = "South Asian digital services bot."
        ),
        Supplier(
            id = 11,
            canonicalName = "Verified Streams",
            primaryHandle = "@Verified_Streams_bot",
            allHandles = "@Verified_Streams_bot, @verifiedstreams",
            entityType = "BOT",
            supportedCurrencies = "USD,USDT",
            reliabilityScore = 70f,
            notes = "Gemini and media accounts bot."
        ),
        Supplier(
            id = 12,
            canonicalName = "AI Tool Pre Bot",
            primaryHandle = "@aitoolpre_bi_bot",
            allHandles = "@aitoolpre_bi_bot",
            entityType = "BOT",
            supportedCurrencies = "USD,USDT",
            reliabilityScore = 55f,
            notes = "Catalog bot with 3-day warranty tiers."
        ),
        Supplier(
            id = 13,
            canonicalName = "SP AI Vault",
            primaryHandle = "@spaivault_bot",
            allHandles = "@spaivault_bot, @skillpreneures_support",
            entityType = "BOT",
            supportedCurrencies = "USD,USDT",
            reliabilityScore = 60f,
            notes = "Vault bot requiring membership or /start."
        ),
        Supplier(
            id = 14,
            canonicalName = "Premium Asset Store",
            primaryHandle = "@prmastbot",
            allHandles = "@prmastbot, @premiumassetstore",
            entityType = "BOT",
            supportedCurrencies = "USD,USDT",
            reliabilityScore = 65f,
            notes = "Digital asset licenses and premium keys."
        ),
        Supplier(
            id = 15,
            canonicalName = "Store Premium Bot",
            primaryHandle = "@storepremium_bot",
            allHandles = "@storepremium_bot",
            entityType = "BOT",
            supportedCurrencies = "USD,USDT",
            reliabilityScore = 60f,
            notes = "Premium subscription store bot."
        ),
        Supplier(
            id = 16,
            canonicalName = "MediaHub Marketplace",
            primaryHandle = "@MediaHubMarketplace_bot",
            allHandles = "@MediaHubMarketplace_bot, @MediaHubDigitalMarketplace",
            entityType = "BOT",
            supportedCurrencies = "USD,USDT",
            reliabilityScore = 65f,
            notes = "Marketplace bot for digital and AI tools."
        )
    )
}
