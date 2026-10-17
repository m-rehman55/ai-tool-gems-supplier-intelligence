import urllib.request
import re
import html
import json
import os

channels = [
    'premiumtoolschat',
    'digicarttools',
    'PiggyAIPremium',
    'piggyai79', 
    'sellnitystoreupdates',
    'ptsbd26',
    'verifiedstreams',
    'novasubpre', 
    'hacksxtips',
    'toolorax',
    'mmoglobalhub'
]

results = {}
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}

for ch in channels:
    url = f'https://t.me/s/{ch}'
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, timeout=12) as resp:
            content = resp.read().decode('utf-8', errors='ignore')
            
            title_m = re.search(r'<div class="tgme_channel_info_title"[^>]*><span[^>]*>(.*?)</span>', content, re.DOTALL)
            title = html.unescape(title_m.group(1)).strip() if title_m else ch
            
            raw_msgs = re.findall(r'<div class="tgme_widget_message_text[^"]*"[^>]*>(.*?)</div>', content, re.DOTALL)
            clean_msgs = []
            for m in raw_msgs:
                clean = re.sub(r'<br\s*/?>', '\n', m)
                clean = re.sub(r'<[^>]+>', '', clean)
                clean = html.unescape(clean).strip()
                if len(clean) > 20:
                    clean_msgs.append(clean)
            
            results[ch] = {
                'title': title,
                'msg_count': len(clean_msgs),
                'messages': clean_msgs
            }
            print(f"[OK] {ch}: '{title}' - Found {len(clean_msgs)} messages")
    except Exception as e:
        print(f"[ERR] {ch}: {e}")

os.makedirs('scratch', exist_ok=True)
with open('scratch/telegram_crawl.json', 'w', encoding='utf-8') as f:
    json.dump(results, f, ensure_ascii=False, indent=2)

print("\nDone! Output saved to scratch/telegram_crawl.json")
