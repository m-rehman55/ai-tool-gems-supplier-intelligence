import json
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')

with open('scratch/all_channels_deep.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

# Approximate USD to PKR conversion rate
USD_TO_PKR = 280.0

# Bot mappings
bot_map = {
    'digicarttools': '@getdigitaltools_bot (DigiCart)',
    'PiggyAIPremium': '@PiggyAi799_Bot (PiggyAI)',
    'sellnitystoreupdates': '@sellnitystorebot (Sellnity)',
    'verifiedstreams': '@Verified_Streams_bot (Verified Streams)',
    'novasubpre': '@Novasubglo_bot (NovaShop)',
    'hacksxtips': '@aitoolify_bot (AI Toolify)',
    'Airdrop_Crypto_x100': '@aitoolpre_bi_bot (AIToolPre)'
}

raw_catalog = []

for ch, msgs in data.items():
    bot_name = bot_map.get(ch, f"@{ch}")
    for m in msgs:
        # Extract price patterns: $X.XX or X USDT or X$ or Rs. X
        # Look for product titles in lines
        lines = [line.strip() for line in m.split('\n') if line.strip()]
        if not lines:
            continue
        
        # Search for price in message
        price_match = re.search(r'(?:Price|Price\s*:|\$|💵|💰|💲)\s*(?:[:\s]*)(?:\$|USDT)?\s*(\d+(?:\.\d{1,2})?)\s*(?:USDT|\$)?', m, re.IGNORECASE)
        # Also check for sale price pattern: $A -> $B
        sale_match = re.search(r'\$\s*\d+(?:\.\d{1,2})?\s*→\s*\$\s*(\d+(?:\.\d{1,2})?)', m)
        if sale_match:
            price_val = float(sale_match.group(1))
        elif price_match:
            price_val = float(price_match.group(1))
        else:
            price_val = None
        
        # Clean title candidate from first 3 non-empty lines
        title_candidates = []
        for l in lines[:4]:
            # skip warnings, emojis-only, headers
            if any(w in l.lower() for w in ['warning', 'flash sale', 'new in stock', 'back in stock', 'ends in', 'price:', 'discount:']):
                continue
            cleaned = re.sub(r'^[^\w\s]+', '', l).strip()
            if len(cleaned) > 3 and not re.match(r'^\$\d+', cleaned):
                title_candidates.append(cleaned)
                break
        
        prod_title = title_candidates[0] if title_candidates else lines[0]
        # Clean emojis and labels
        prod_title = re.sub(r'[^\x00-\x7F]+', ' ', prod_title).strip()
        prod_title = re.sub(r'\s+', ' ', prod_title)
        
        if price_val is not None and price_val > 0.1:
            raw_catalog.append({
                'channel': ch,
                'bot': bot_name,
                'raw_title': prod_title,
                'price_usd': price_val,
                'price_pkr': round(price_val * USD_TO_PKR),
                'full_text': m[:250]
            })

print(f"Total raw items parsed with prices: {len(raw_catalog)}")

# Group and normalize by standard tool name
def categorize_tool(title):
    t = title.lower()
    if 'chatgpt' in t or 'gpt' in t:
        if 'student' in t or 'edu' in t:
            return 'ChatGPT Plus (Edu / Student)'
        elif 'business' in t or 'team' in t:
            return 'ChatGPT Business / Team Panel'
        elif 'go' in t:
            return 'ChatGPT Go'
        return 'ChatGPT Plus (1 Month Private)'
    elif 'gemini' in t:
        if '18' in t:
            return 'Google Gemini Pro (18 Months)'
        return 'Google Gemini Pro'
    elif 'claude' in t:
        return 'Claude Pro (3.5 Sonnet)'
    elif 'capcut' in t:
        if '6m' in t or '6 month' in t:
            return 'CapCut Pro (6 Months)'
        return 'CapCut Pro (1 Month)'
    elif 'leonardo' in t:
        return 'Leonardo AI (8,500 Credits)'
    elif 'veo' in t:
        return 'Veo 3 Ultra (AI Video)'
    elif 'heygen' in t:
        return 'HeyGen AI Avatar Creator'
    elif 'kling' in t:
        return 'Kling AI Video (1,000 Credits)'
    elif 'krea' in t:
        return 'Krea AI (5,100 Credits)'
    elif 'muse' in t:
        return 'Muse AI (1B - 2B Tokens)'
    elif 'manus' in t:
        return 'Manus Pro AI (1 Year)'
    elif 'openart' in t:
        return 'OpenArt Essential AI'
    elif 'elevenlabs' in t or 'elevanlabs' in t:
        return 'ElevenLabs Voice AI'
    elif 'grok' in t:
        return 'SuperGrok / Grok 2'
    elif 'lovable' in t:
        if 'lite' in t:
            return 'Lovable Lite'
        return 'Lovable Pro (12 Months)'
    elif 'perplexity' in t:
        return 'Perplexity Pro'
    elif 'quillbot' in t:
        return 'QuillBot Premium (1 Year)'
    elif 'ilovepdf' in t:
        return 'iLovePDF Premium (1 Year)'
    elif 'proton' in t:
        if 'lifetime' in t:
            return 'Proton VPN (Lifetime)'
        return 'Proton VPN (1 Year)'
    elif 'duolingo' in t:
        return 'Duolingo Super (12 Months)'
    elif 'coursera' in t:
        return 'Coursera Plus (12 Months)'
    elif 'jetbrains' in t or 'jetbrain' in t:
        return 'JetBrains All IDEs Suite (1 Year)'
    elif 'autodesk' in t:
        return 'Autodesk All Apps AEC (3 Years)'
    elif 'adobe' in t or 'acrobat' in t:
        return 'Adobe Acrobat Pro License'
    elif 'coreldraw' in t or 'corel' in t:
        return 'CorelDRAW Graphics Suite License'
    elif 'zoom' in t:
        return 'Zoom Pro (28 Days)'
    elif 'netflix' in t:
        if '3' in t:
            return 'Netflix 4K UHD (3 Months)'
        return 'Netflix 4K UHD (1 Month)'
    elif 'linkedin' in t:
        return 'LinkedIn Business / Premium (12 Months)'
    elif 'vidiq' in t:
        return 'VidIQ Boost Pro'
    elif 'codex' in t:
        return 'OpenAI Codex API ($100 Allowance)'
    return None

grouped = {}
for item in raw_catalog:
    standard_name = categorize_tool(item['raw_title'])
    if not standard_name:
        standard_name = categorize_tool(item['full_text'])
    
    if standard_name:
        if standard_name not in grouped:
            grouped[standard_name] = []
        grouped[standard_name].append(item)

# For each tool, find the cheapest supplier
comparison_matrix = []

for tool_name, items in sorted(grouped.items()):
    # sort by USD price ascending
    items_sorted = sorted(items, key=lambda x: x['price_usd'])
    cheapest = items_sorted[0]
    
    # gather all unique suppliers and their prices
    suppliers_summary = []
    seen_bots = set()
    for it in items_sorted:
        bot = it['bot']
        if bot not in seen_bots:
            seen_bots.add(bot)
            suppliers_summary.append(f"{bot}: ${it['price_usd']:.2f} (~Rs. {it['price_pkr']})")
    
    comparison_matrix.append({
        'tool': tool_name,
        'cheapest_bot': cheapest['bot'],
        'cheapest_usd': cheapest['price_usd'],
        'cheapest_pkr': cheapest['price_pkr'],
        'all_suppliers': suppliers_summary,
        'other_options_count': len(suppliers_summary)
    })

with open('scratch/full_comparison_matrix.json', 'w', encoding='utf-8') as f:
    json.dump(comparison_matrix, f, ensure_ascii=False, indent=2)

print(f"\nUnique Deduplicated Tools Analyzed: {len(comparison_matrix)}\n")
for c in comparison_matrix:
    print(f"📌 {c['tool']}")
    print(f"   🏆 CHEAPEST WINNER: {c['cheapest_bot']} at ${c['cheapest_usd']:.2f} (Rs. {c['cheapest_pkr']})")
    print(f"   📊 All Sourcing Options: {' | '.join(c['all_suppliers'])}")
    print("-" * 60)
