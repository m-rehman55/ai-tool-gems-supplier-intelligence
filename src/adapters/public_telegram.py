"""
Public Telegram Channel Research Adapter.
Fetches public channel web previews (https://t.me/s/<channel>) without login or session tokens.
Includes disk caching, backoff, and robust HTML parsing.
"""

import re
import html
import time
import json
import hashlib
from datetime import datetime, timezone
from pathlib import Path
from typing import List, Dict, Any, Optional
import urllib.request

from src.config import settings
from src.matching import parse_duration, parse_access_type

class TelegramPublicChannelAdapter:
    def __init__(self, cache_dir: Optional[Path] = None):
        self.cache_dir = cache_dir or (settings.database_path.parent / "cache" / "telegram")
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self.headers = {"User-Agent": settings.user_agent}
        self.min_delay_seconds = 1.0
        self.last_request_time = 0.0

    def _rate_limit(self) -> None:
        elapsed = time.time() - self.last_request_time
        if elapsed < self.min_delay_seconds:
            time.sleep(self.min_delay_seconds - elapsed)
        self.last_request_time = time.time()

    def fetch_channel_messages(self, channel_handle: str, max_cache_age_hours: int = 4) -> List[Dict[str, Any]]:
        """
        Fetches public messages from https://t.me/s/<channel_handle>.
        Returns list of parsed message dictionaries.
        """
        clean_handle = channel_handle.strip().lstrip("@").replace("https://t.me/s/", "").replace("https://t.me/", "")
        cache_file = self.cache_dir / f"{clean_handle}.html"
        
        content = ""
        is_cached = False
        
        # Check cache
        if cache_file.exists():
            mtime = datetime.fromtimestamp(cache_file.stat().st_mtime, tz=timezone.utc)
            age_hours = (datetime.now(timezone.utc) - mtime).total_seconds() / 3600
            if age_hours < max_cache_age_hours:
                with open(cache_file, "r", encoding="utf-8", errors="ignore") as f:
                    content = f.read()
                is_cached = True

        if not content:
            self._rate_limit()
            url = f"https://t.me/s/{clean_handle}"
            req = urllib.request.Request(url, headers=self.headers)
            try:
                with urllib.request.urlopen(req, timeout=12) as resp:
                    content = resp.read().decode("utf-8", errors="ignore")
                with open(cache_file, "w", encoding="utf-8") as f:
                    f.write(content)
            except Exception as e:
                # If network fails but stale cache exists, use it
                if cache_file.exists():
                    with open(cache_file, "r", encoding="utf-8", errors="ignore") as f:
                        content = f.read()
                else:
                    return []

        return self.parse_html_messages(content, clean_handle)

    def parse_html_messages(self, html_content: str, channel: str) -> List[Dict[str, Any]]:
        """Extracts structured text snippets, message IDs, and timestamps."""
        # Find all message blocks
        msg_blocks = re.findall(
            r'<div class="tgme_widget_message\b[^>]*data-post="([^"]+)"[^>]*>(.*?)</div>\s*<!-- /tgme_widget_message -->',
            html_content,
            re.DOTALL
        )
        
        parsed_records = []
        for post_id, block in msg_blocks:
            # Extract text
            text_m = re.search(r'<div class="tgme_widget_message_text[^"]*"[^>]*>(.*?)</div>', block, re.DOTALL)
            if not text_m:
                continue
                
            raw_html = text_m.group(1)
            clean_text = re.sub(r'<br\s*/?>', '\n', raw_html)
            clean_text = re.sub(r'<[^>]+>', '', clean_text)
            clean_text = html.unescape(clean_text).strip()
            
            if len(clean_text) < 15:
                continue

            # Extract post date
            time_m = re.search(r'<time datetime="([^"]+)"', block)
            pub_date = time_m.group(1) if time_m else datetime.now(timezone.utc).isoformat()
            
            source_url = f"https://t.me/{post_id}"
            
            # Price parsing
            price_val = None
            sale_m = re.search(r'\$\s*(\d+(?:\.\d{1,2})?)\s*→\s*\$\s*(\d+(?:\.\d{1,2})?)', clean_text)
            if sale_m:
                price_val = float(sale_m.group(2))
            else:
                p_m = re.search(r'(?:Price|Price\s*:|\$|💵|💰|💲)\s*(?:[:\s]*)(?:\$|USDT)?\s*(\d+(?:\.\d{1,2})?)\s*(?:USDT|\$)?', clean_text, re.IGNORECASE)
                if p_m:
                    price_val = float(p_m.group(1))

            parsed_records.append({
                "channel": channel,
                "post_id": post_id,
                "source_url": source_url,
                "published_at": pub_date,
                "clean_text": clean_text,
                "detected_price": price_val,
                "content_hash": hashlib.sha256(clean_text.encode("utf-8")).hexdigest()
            })
            
        return parsed_records
