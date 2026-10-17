import json
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')

with open('scratch/deep_items_dump.json', 'r', encoding='utf-8') as f:
    items = json.load(f)

# Canonical categorization and specification engine
def normalize_item(it):
    raw = (it['raw_title'] + " " + it['full_text']).lower()
    
    # Defaults from extractor
    duration = it['duration']
    warranty = it['warranty']
    access = it['access_type']
    
    # Refine duration & warranty from full text if needed
    if 'lifetime' in raw:
        duration = 'Lifetime'
    elif '3 year' in raw or '3y' in raw:
        duration = '3 Years'
    elif '2 year' in raw or '2y' in raw:
        duration = '2 Years'
    elif '1 year' in raw or '1y' in raw or '12m' in raw or '12 month' in raw:
        duration = '12 Months (1 Year)'
    elif '6 month' in raw or '6m' in raw or '180-day' in raw:
        duration = '6 Months'
    elif '4 month' in raw or '4m' in raw:
        duration = '4 Months'
    elif '3 month' in raw or '3m' in raw:
        duration = '3 Months'
    elif '2 month' in raw or '2m' in raw:
        duration = '2 Months'
    elif '28 day' in raw or '30 day' in raw or '1 month' in raw or '1m' in raw:
        duration = '1 Month'
    elif '9-10 day' in raw or '10 day' in raw:
        duration = '10 Days'
    elif '7 day' in raw:
        duration = '7 Days'

    # Warranty refine
    if 'no warranty' in raw or '(nw)' in raw:
        warranty = 'No Warranty (Replacement not covered)'
    elif 'full warranty' in raw or '(fw)' in raw or 'full war' in raw:
        warranty = 'Full Warranty (100% Replacement Guarantee)'
    elif '25 day' in raw and 'warranty' in raw:
        warranty = '25 Days Replacement Warranty'
    elif '7 day' in raw and 'warranty' in raw:
        warranty = '7 Days Replacement Warranty'
    elif '24h' in raw and 'warranty' in raw:
        warranty = '24 Hours Warranty'
    elif '1 day' in raw and 'warranty' in raw:
        warranty = '1 Day Warranty'
    elif '5h' in raw and 'warranty' in raw:
        warranty = '5 Hours Initial Check Warranty'
    elif warranty == "Standard / Unspecified":
        warranty = 'Standard Working Warranty'

    # Login / Access refine
    if 'promo code' in raw or 'promo' in raw or 'redeem' in raw:
        access = 'Redeem Code / Voucher'
    elif 'invite' in raw or 'link' in raw or 'invitation' in raw:
        access = 'Invite Link / Family Invitation'
    elif 'license key' in raw or 'key' in raw:
        access = 'Official License Key (1 PC)'
    elif 'admin' in raw or 'panel' in raw:
        access = 'Admin Panel / Multi-seat Workspace'
    elif 'format: account|password' in raw or 'email|password' in raw or '2fa' in raw:
        access = 'Private Account (Email + Pass + 2FA Secret)'
    elif 'slot' in raw or 'shared' in raw or 'profile' in raw:
        access = 'Shared Account / Profile Slot'

    # Tool identification
    category = "General"
    tool_name = None

    if 'chatgpt' in raw or 'gpt' in raw:
        category = "AI Assistants"
        if 'student' in raw:
            tool_name = "ChatGPT Plus (Student Verified Plan)"
        elif 'business' in raw or 'panel' in raw or 'codex' in raw:
            tool_name = "ChatGPT Business / Team Panel"
        elif 'go' in raw:
            tool_name = "ChatGPT Go"
        elif 'apple pay' in raw or 'cash' in raw:
            tool_name = "ChatGPT Plus Private (Real Money / Full Warranty)"
        elif 'upi' in raw:
            tool_name = "ChatGPT Plus Private (Fast Delivery UPI)"
        else:
            tool_name = "ChatGPT Plus Private"
    elif 'gemini' in raw:
        category = "AI Assistants"
        tool_name = "Google Gemini Pro (18 Months Invitation)"
    elif 'claude' in raw:
        category = "AI Assistants"
        tool_name = "Claude Pro (3.5 Sonnet Private)"
    elif 'grok' in raw:
        category = "AI Assistants"
        tool_name = "SuperGrok / Grok 2 (X Premium)"
    elif 'perplexity' in raw:
        category = "AI Assistants"
        tool_name = "Perplexity Pro"
    elif 'deepseek' in raw:
        category = "AI Assistants"
        tool_name = "DeepSeek Unlimited / R1"
    elif 'capcut' in raw:
        category = "AI Video"
        if 'team' in raw or '6m' in raw:
            tool_name = "CapCut Pro (Team / Multi-Device)"
        else:
            tool_name = "CapCut Pro (Personal 2-Device)"
    elif 'veo' in raw:
        category = "AI Video"
        tool_name = "Veo 3 Ultra (DeepMind AI Video)"
    elif 'kling' in raw:
        category = "AI Video"
        tool_name = "Kling AI Video (Standard 1,000 Credits)"
    elif 'heygen' in raw:
        category = "AI Video"
        tool_name = "HeyGen AI Avatar (Creator Plan)"
    elif 'runway' in raw:
        category = "AI Video"
        tool_name = "Runway ML Gen-3 Alpha"
    elif 'vidiq' in raw:
        category = "AI Video & YouTube"
        tool_name = "VidIQ Boost (YouTube SEO & Ranking)"
    elif 'leonardo' in raw:
        category = "AI Design"
        tool_name = "Leonardo AI (8,500 Credits Alchemy)"
    elif 'krea' in raw:
        category = "AI Design"
        tool_name = "Krea AI (Basic 5,100 Credits)"
    elif 'openart' in raw:
        category = "AI Design"
        tool_name = "OpenArt Essential AI (4,000 Credits)"
    elif 'muse' in raw:
        category = "AI Video & Design"
        tool_name = "Muse AI (1B - 2B High-Res Tokens)"
    elif 'elevenlabs' in raw or 'elevanlabs' in raw:
        category = "AI Voice"
        tool_name = "ElevenLabs Voice AI (Creator Plan)"
    elif 'lovable' in raw:
        category = "Development"
        if 'lite' in raw:
            tool_name = "Lovable Lite (AI App Generator)"
        else:
            tool_name = "Lovable Pro (AI App Generator)"
    elif 'manus' in raw:
        category = "Development & Agent"
        tool_name = "Manus Pro AI (Autonomous Agent)"
    elif 'jetbrains' in raw or 'jetbrain' in raw:
        category = "Development"
        tool_name = "JetBrains All Products Pack (PyCharm, IntelliJ, WebStorm)"
    elif 'aws' in raw:
        category = "Cloud & Development"
        tool_name = "AWS Cloud Promo Credits (Voucher Code)"
    elif 'duolingo' in raw:
        category = "Education & Languages"
        tool_name = "Duolingo Super (All Languages Unlocked)"
    elif 'coursera' in raw:
        category = "Education & Certifications"
        tool_name = "Coursera Plus (Unlimited Certificates)"
    elif 'ilovepdf' in raw:
        category = "Productivity & Documents"
        tool_name = "iLovePDF Premium (2,000 AI Credits + OCR)"
    elif 'autodesk' in raw:
        category = "Architecture & Engineering"
        tool_name = "Autodesk All Apps AEC (AutoCAD, Revit, 3ds Max)"
    elif 'acrobat' in raw or 'adobe' in raw:
        category = "Design & Software"
        tool_name = "Adobe Acrobat Pro License Key"
    elif 'corel' in raw:
        category = "Design & Vector"
        tool_name = "CorelDRAW Graphics Suite License Key"
    elif 'quillbot' in raw:
        category = "Writing & SEO"
        tool_name = "QuillBot Premium (Paraphraser & Grammar)"
    elif 'proton' in raw:
        category = "VPN & Privacy"
        tool_name = "Proton VPN Plus (Stealth & Secure Core)"
    elif 'netflix' in raw:
        category = "Entertainment"
        tool_name = "Netflix 4K Ultra HD (Private Profile Slot)"
    elif 'zoom' in raw:
        category = "Business & Meetings"
        tool_name = "Zoom Pro (Unlimited 30-Hour Meetings)"
    elif 'linkedin' in raw:
        category = "Business & Jobs"
        tool_name = "LinkedIn Business Premium (InMail + Insights)"
    elif 'telegram' in raw and 'group' in raw:
        category = "Marketing & Outreach"
        tool_name = "Telegram 500+ Active B2B Buy/Sell Groups List"
    
    if tool_name:
        return {
            'tool_name': tool_name,
            'category': category,
            'duration': duration,
            'warranty': warranty,
            'access': access,
            'price_usd': it['price_usd'],
            'price_pkr': it['price_pkr'],
            'bot': it['bot'],
            'channel': it['channel']
        }
    return None

classified = []
for it in items:
    norm = normalize_item(it)
    if norm:
        classified.append(norm)

print(f"Total fully classified tools with deep specs: {len(classified)}")

# Group by (tool_name, duration) to find cheapest bot and all options
grouped_specs = {}
for c in classified:
    key = (c['tool_name'], c['duration'])
    if key not in grouped_specs:
        grouped_specs[key] = []
    grouped_specs[key].append(c)

master_table = []
for (t_name, dur), variants in sorted(grouped_specs.items()):
    # sort by price
    sorted_vars = sorted(variants, key=lambda x: x['price_usd'])
    winner = sorted_vars[0]
    
    # Calculate recommended retail PKR price:
    # Cost + 40-70% margin (minimum Rs. 350 profit)
    cost_pkr = winner['price_pkr']
    if cost_pkr < 500:
        retail_pkr = cost_pkr + 400
    elif cost_pkr < 1500:
        retail_pkr = round((cost_pkr + 650) / 50) * 50
    elif cost_pkr < 4000:
        retail_pkr = round((cost_pkr + 900) / 100) * 100
    else:
        retail_pkr = round((cost_pkr * 1.35) / 100) * 100

    profit_pkr = retail_pkr - cost_pkr

    master_table.append({
        'tool_name': t_name,
        'category': winner['category'],
        'duration': dur,
        'warranty': winner['warranty'],
        'access': winner['access'],
        'winner_bot': winner['bot'],
        'cost_usd': winner['price_usd'],
        'cost_pkr': cost_pkr,
        'retail_pkr': retail_pkr,
        'profit_pkr': profit_pkr,
        'all_bots_count': len(set(v['bot'] for v in variants))
    })

with open('scratch/master_procurement_database.json', 'w', encoding='utf-8') as f:
    json.dump(master_table, f, ensure_ascii=False, indent=2)

print(f"Total unique Master Tool Offerings: {len(master_table)}")
