"""
Autonomous Telethon Bot Crawler for Supplier Price Intelligence.
Connects via user session to Telegram MTProto API.
Visits supplier bots, sends /open or /start, clicks inline menu buttons (e.g. Products / Shop),
and extracts product titles, prices, stock, and warranty without human intervention.
"""

import os
import sys
import asyncio
import re
import hashlib
from datetime import datetime, timezone
from pathlib import Path

# Add project root to sys.path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.database import get_db, init_db
from src.models import (
    Supplier, Product, ProductPlan, PriceObservation, PriceHistory,
    AccessType, VerificationStatus, RiskLevel
)
from src.matching import parse_duration, parse_access_type, generate_matching_key
from src.excel import export_master_catalog

# Target bot handles to explore
TARGET_BOTS = [
    "@ptstorebd_bot",
    "@PremiKeyBot",
    "@PiggyAi799_Bot",
    "@getdigitaltools_bot",
    "@sellnitystorebot",
    "@Novasubglo_bot",
    "@aitoolify_bot",
    "@Verified_Streams_bot"
]

async def crawl_bot_shop(client, bot_handle: str):
    """Interacts with a supplier bot, opens menu, and clicks Products button."""
    from telethon import functions, types
    
    print(f"\n[BOT RADAR] Opening conversation with {bot_handle}...")
    entity = await client.get_entity(bot_handle)
    
    # 1. Send /open or /start command
    print(f"  -> Sending /open command to {bot_handle}...")
    sent_msg = await client.send_message(entity, "/open")
    await asyncio.sleep(2.5)
    
    # Get latest bot response
    messages = await client.get_messages(entity, limit=3)
    if not messages:
        return
        
    latest_msg = messages[0]
    
    # If bot didn't recognize /open, try /start
    if not latest_msg.reply_markup and not latest_msg.buttons:
        print(f"  -> /open had no buttons, sending /start to {bot_handle}...")
        await client.send_message(entity, "/start")
        await asyncio.sleep(2.5)
        messages = await client.get_messages(entity, limit=3)
        latest_msg = messages[0]

    # Inspect inline keyboard buttons
    if latest_msg.buttons:
        print(f"  -> Found inline menu with {len(latest_msg.buttons)} button rows:")
        products_btn = None
        for row in latest_msg.buttons:
            for btn in row:
                btn_text = btn.text.strip()
                print(f"     [BUTTON] {btn_text}")
                if any(k in btn_text.lower() for k in ["product", "shop", "store", "catalog", "buy"]):
                    products_btn = btn
                    
        # Click the Products button if found
        if products_btn:
            print(f"  -> Clicking '{products_btn.text}' button...")
            try:
                await products_btn.click()
                await asyncio.sleep(3.0)
                
                # Fetch updated catalog view
                catalog_msgs = await client.get_messages(entity, limit=2)
                catalog_msg = catalog_msgs[0]
                
                if catalog_msg.buttons:
                    print(f"  -> Successfully opened Products catalog ({len(catalog_msg.buttons)} items):")
                    extracted_items = []
                    for row in catalog_msg.buttons:
                        for item_btn in row:
                            item_text = item_btn.text.strip()
                            print(f"     📦 {item_text}")
                            extracted_items.append(item_text)
                    return extracted_items
            except Exception as e:
                print(f"  [!] Button click error: {e}")
                
    return []

async def main():
    api_id = os.getenv("TELEGRAM_API_ID")
    api_hash = os.getenv("TELEGRAM_API_HASH")
    
    if not api_id or not api_hash:
        print("\n=======================================================")
        print("⚠️  TELEGRAM API CREDENTIALS NOT FOUND IN ENVIRONMENT")
        print("=======================================================")
        print("To enable 100% autonomous bot-shop exploration:")
        print("1. Go to https://my.telegram.org and log in with your phone number.")
        print("2. Click 'API development tools' and copy your App api_id & api_hash.")
        print("3. Add them to your .env file:")
        print("   TELEGRAM_API_ID=your_api_id")
        print("   TELEGRAM_API_HASH=your_api_hash")
        print("   TELEGRAM_PHONE=+92300xxxxxxx")
        print("=======================================================\n")
        return

    from telethon import TelegramClient
    session_path = Path(__file__).resolve().parent.parent / "data" / "telethon_session"
    client = TelegramClient(str(session_path), int(api_id), api_hash)
    
    await client.start()
    print("✅ Successfully authorized local Telegram MTProto session!")
    
    for bot in TARGET_BOTS:
        try:
            items = await crawl_bot_shop(client, bot)
            await asyncio.sleep(2.0)
        except Exception as e:
            print(f"  [!] Failed exploring {bot}: {e}")
            
    await client.disconnect()

if __name__ == "__main__":
    asyncio.run(main())
