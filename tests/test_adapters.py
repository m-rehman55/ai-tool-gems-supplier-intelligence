"""
Unit tests for public Telegram channel research adapter, HTML parsing fixtures, and caching.
"""

import tempfile
from pathlib import Path
import pytest
from src.adapters.public_telegram import TelegramPublicChannelAdapter

SAMPLE_TELEGRAM_HTML = """
<div class="tgme_widget_message" data-post="sellnitystoreupdates/145">
    <div class="tgme_widget_message_text">
        ⚡ <b>ChatGPT Plus Private Account</b> ⚡<br>
        Duration: 1 Month<br>
        Price: $5.00 USDT<br>
        Warranty: 30 Days Replacement Warranty<br>
        Stock: In Stock
    </div>
    <time datetime="2026-10-15T12:00:00+00:00"></time>
</div>
<!-- /tgme_widget_message -->
<div class="tgme_widget_message" data-post="sellnitystoreupdates/146">
    <div class="tgme_widget_message_text">
        Kling AI 1000 Credits Video Generation<br>
        Flash Sale: $8.00 → $5.02<br>
        Support: @sellnity
    </div>
    <time datetime="2026-10-16T15:30:00+00:00"></time>
</div>
<!-- /tgme_widget_message -->
"""

MALFORMED_HTML = """
<div class="tgme_widget_message" data-post="corrupt/99">
    <div class="tgme_widget_message_text">
        Too short
    </div>
</div>
<!-- /tgme_widget_message -->
<div class="no_message">Random text without data-post</div>
"""

def test_parse_sample_html():
    adapter = TelegramPublicChannelAdapter(cache_dir=Path(tempfile.gettempdir()))
    records = adapter.parse_html_messages(SAMPLE_TELEGRAM_HTML, channel="sellnitystoreupdates")
    
    assert len(records) == 2
    
    # Record 1: ChatGPT Plus
    rec1 = records[0]
    assert rec1["post_id"] == "sellnitystoreupdates/145"
    assert rec1["source_url"] == "https://t.me/sellnitystoreupdates/145"
    assert "ChatGPT Plus" in rec1["clean_text"]
    assert rec1["detected_price"] == 5.0
    assert rec1["published_at"] == "2026-10-15T12:00:00+00:00"
    
    # Record 2: Kling AI sale arrow parsing
    rec2 = records[1]
    assert rec2["post_id"] == "sellnitystoreupdates/146"
    assert rec2["detected_price"] == 5.02
    assert "Kling AI" in rec2["clean_text"]

def test_parse_malformed_html():
    adapter = TelegramPublicChannelAdapter(cache_dir=Path(tempfile.gettempdir()))
    records = adapter.parse_html_messages(MALFORMED_HTML, channel="corrupt")
    # "Too short" should be filtered out (>15 chars threshold), and non-message ignored
    assert len(records) == 0

def test_caching_behavior():
    with tempfile.TemporaryDirectory() as tmpdir:
        cache_path = Path(tmpdir)
        adapter = TelegramPublicChannelAdapter(cache_dir=cache_path)
        
        # Pre-seed cache file
        cached_file = cache_path / "testchannel.html"
        cached_file.write_text(SAMPLE_TELEGRAM_HTML, encoding="utf-8")
        
        # fetch_channel_messages should read from cache without network request
        records = adapter.fetch_channel_messages("testchannel", max_cache_age_hours=24)
        assert len(records) == 2
        assert records[0]["post_id"] == "sellnitystoreupdates/145"
