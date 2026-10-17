import json
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')

with open('scratch/all_channels_deep.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

USD_TO_PKR = 280.0

bot_map = {
    'digicarttools': '@getdigitaltools_bot (DigiCart)',
    'PiggyAIPremium': '@PiggyAi799_Bot (PiggyAI)',
    'sellnitystoreupdates': '@sellnitystorebot (Sellnity)',
    'verifiedstreams': '@Verified_Streams_bot (Verified Streams)',
    'novasubpre': '@Novasubglo_bot (NovaShop)',
    'hacksxtips': '@aitoolify_bot (AI Toolify)',
    'Airdrop_Crypto_x100': '@aitoolpre_bi_bot (AIToolPre)'
}

all_items = []

for ch, msgs in data.items():
    bot = bot_map.get(ch, f"@{ch}")
    for idx, m in enumerate(msgs):
        lines = [line.strip() for line in m.split('\n') if line.strip()]
        if not lines:
            continue
        
        # 1. Price extraction
        price_val = None
        sale_m = re.search(r'\$\s*(\d+(?:\.\d{1,2})?)\s*→\s*\$\s*(\d+(?:\.\d{1,2})?)', m)
        if sale_m:
            price_val = float(sale_m.group(2))
        else:
            p_m = re.search(r'(?:Price|Price\s*:|\$|💵|💰|💲)\s*(?:[:\s]*)(?:\$|USDT)?\s*(\d+(?:\.\d{1,2})?)\s*(?:USDT|\$)?', m, re.IGNORECASE)
            if p_m:
                price_val = float(p_m.group(1))
            else:
                usdt_m = re.search(r'(\d+(?:\.\d{1,2})?)\s*(?:USDT|\$)', m)
                if usdt_m:
                    price_val = float(usdt_m.group(1))

        if price_val is None or price_val < 0.2:
            continue

        # 2. Warranty extraction
        warranty = "Standard / Unspecified"
        if re.search(r'no warranty|without warranty|nw\b', m, re.IGNORECASE):
            warranty = "No Warranty (NW)"
        elif re.search(r'full warranty|fw\b', m, re.IGNORECASE):
            warranty = "Full Warranty (FW)"
        elif re.search(r'(\d+)\s*(?:days?|d)\s*warranty', m, re.IGNORECASE):
            w_m = re.search(r'(\d+)\s*(?:days?|d)\s*warranty', m, re.IGNORECASE)
            warranty = f"{w_m.group(1)} Days Warranty"
        elif re.search(r'warranty\s*(\d+)\s*(?:days?|d|h|hours?)', m, re.IGNORECASE):
            w_m = re.search(r'warranty\s*(\d+)\s*(?:days?|d|h|hours?)', m, re.IGNORECASE)
            warranty = f"{w_m.group(1)} Warranty"
        elif re.search(r'(\d+)\s*(?:months?|m)\s*warranty', m, re.IGNORECASE):
            w_m = re.search(r'(\d+)\s*(?:months?|m)\s*warranty', m, re.IGNORECASE)
            warranty = f"{w_m.group(1)} Months Warranty"

        # 3. Duration extraction
        duration = "1 Month"
        if re.search(r'lifetime', m, re.IGNORECASE):
            duration = "Lifetime"
        elif re.search(r'(\d+)\s*(?:years?|y)\b', m, re.IGNORECASE):
            d_m = re.search(r'(\d+)\s*(?:years?|y)\b', m, re.IGNORECASE)
            duration = f"{d_m.group(1)} Year{'s' if int(d_m.group(1))>1 else ''}"
        elif re.search(r'(\d+)\s*(?:months?|m)\b', m, re.IGNORECASE):
            d_m = re.search(r'(\d+)\s*(?:months?|m)\b', m, re.IGNORECASE)
            duration = f"{d_m.group(1)} Months"
        elif re.search(r'(\d+)\s*(?:days?|d)\b', m, re.IGNORECASE):
            d_m = re.search(r'(\d+)\s*(?:days?|d)\b', m, re.IGNORECASE)
            duration = f"{d_m.group(1)} Days"

        # 4. Access / Account type
        access_type = "Private Account"
        if re.search(r'slot|shared|profile', m, re.IGNORECASE):
            access_type = "Shared Profile / Slot"
        elif re.search(r'code|promo|redeem', m, re.IGNORECASE):
            access_type = "Promo / Redeem Code"
        elif re.search(r'key|license key', m, re.IGNORECASE):
            access_type = "Official License Key"
        elif re.search(r'link|invite|invitation', m, re.IGNORECASE):
            access_type = "Direct Claim Link / Invitation"
        elif re.search(r'admin|panel', m, re.IGNORECASE):
            access_type = "Admin Account / Workspace Panel"
        elif re.search(r'api|token', m, re.IGNORECASE):
            access_type = "API Credits / Key"

        # 5. Extract item title
        title_candidates = []
        for l in lines[:4]:
            if any(w in l.lower() for w in ['warning', 'flash sale', 'new in stock', 'back in stock', 'ends in', 'price:', 'discount:', 'join our', 'pinned']):
                continue
            cleaned = re.sub(r'^[^\w\s]+', '', l).strip()
            cleaned = re.sub(r'[^\x00-\x7F]+', ' ', cleaned).strip()
            if len(cleaned) > 3 and not re.match(r'^\$\d+', cleaned):
                title_candidates.append(cleaned)
                break
        
        raw_title = title_candidates[0] if title_candidates else lines[0]
        raw_title = re.sub(r'\s+', ' ', raw_title).strip()

        all_items.append({
            'raw_title': raw_title,
            'duration': duration,
            'warranty': warranty,
            'access_type': access_type,
            'bot': bot,
            'channel': ch,
            'price_usd': price_val,
            'price_pkr': round(price_val * USD_TO_PKR),
            'full_text': m
        })

print(f"Total structured items with prices, warranty & duration: {len(all_items)}")

with open('scratch/deep_items_dump.json', 'w', encoding='utf-8') as f:
    json.dump(all_items, f, ensure_ascii=False, indent=2)
