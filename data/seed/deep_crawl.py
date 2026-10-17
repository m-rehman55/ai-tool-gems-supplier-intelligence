import urllib.request
import re
import html
import json
import os
import sys

sys.stdout.reconfigure(encoding='utf-8')

channels = [
    'premiumtoolschat',
    'digicarttools',
    'digicartlogs',
    'PiggyAIPremium',
    'piggyai79',
    'sellnitystoreupdates',
    'ptsbd26',
    'verifiedstreams',
    'novasubpre',
    'hacksxtips',
    'toolorax',
    'mmoglobalhub',
    'sealsareana',
    'Airdrop_Crypto_x100',
    'skillpreneures_support',
    'Sharibraza'
]

headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}
results = {}

for ch in channels:
    url = f'https://t.me/s/{ch}'
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, timeout=10) as resp:
            content = resp.read().decode('utf-8', errors='ignore')
            raw_msgs = re.findall(r'<div class="tgme_widget_message_text[^"]*"[^>]*>(.*?)</div>', content, re.DOTALL)
            clean_msgs = []
            for m in raw_msgs:
                clean = re.sub(r'<br\s*/?>', '\n', m)
                clean = re.sub(r'<[^>]+>', '', clean)
                clean = html.unescape(clean).strip()
                if len(clean) > 10:
                    clean_msgs.append(clean)
            if clean_msgs:
                results[ch] = clean_msgs
                print(f"[OK] {ch}: {len(clean_msgs)} messages")
            else:
                print(f"[EMPTY] {ch}: 0 messages")
    except Exception as e:
        print(f"[ERR] {ch}: {e}")

os.makedirs('scratch', exist_ok=True)
with open('scratch/all_channels_deep.json', 'w', encoding='utf-8') as f:
    json.dump(results, f, ensure_ascii=False, indent=2)

print("\nTotal active channels with scraped messages:", len(results))
