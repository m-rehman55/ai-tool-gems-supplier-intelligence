"""Create or verify an owner-authorized local Telegram session.

This script prompts only in the local terminal for the account phone number,
Telegram login code, and (if enabled) the owner's two-step password. It never
prints secrets, reads supplier messages, sends messages, or clicks bot buttons.
"""

import asyncio
import os
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
ENV_FILE = ROOT / ".env"
SESSION_PATH = ROOT / "data" / "telethon_session"


def load_local_env() -> None:
    if not ENV_FILE.exists():
        return
    allowed = {"TELEGRAM_API_ID", "TELEGRAM_API_HASH", "ALLOW_TELETHON_USER_SESSION"}
    for raw_line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        if key in allowed and not os.getenv(key):
            os.environ[key] = value.strip().strip('"').strip("'")


async def main() -> int:
    load_local_env()
    if os.getenv("ALLOW_TELETHON_USER_SESSION") != "1":
        print("Authorization remains disabled. Set ALLOW_TELETHON_USER_SESSION=1 in the private .env file.")
        return 1

    api_id = os.getenv("TELEGRAM_API_ID")
    api_hash = os.getenv("TELEGRAM_API_HASH")
    if not api_id or not api_hash:
        print("Telegram API credentials are missing from the private .env file.")
        return 1

    try:
        from telethon import TelegramClient
    except ImportError:
        print("Telethon is not installed in this Python environment.")
        return 1

    client = TelegramClient(str(SESSION_PATH), int(api_id), api_hash)
    try:
        try:
            await client.start()
        except ConnectionError:
            print("Telegram connection timed out before authorization. Check that this Python environment is allowed outbound TCP 443, then retry once on a normal network.")
            return 2
        if await client.is_user_authorized():
            print("Local Telegram session is authorized. No messages were read or sent.")
            return 0
        print("Telegram did not authorize the local session.")
        return 1
    finally:
        await client.disconnect()


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
