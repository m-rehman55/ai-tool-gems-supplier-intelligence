import json
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')

with open('scratch/telegram_crawl.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

known_keywords = {
    'chatgpt': 'ChatGPT Plus / Team',
    'gpt': 'ChatGPT / GPT-4o',
    'claude': 'Claude Pro / Sonnet',
    'gemini': 'Google Gemini Advanced',
    'midjourney': 'Midjourney Pro / Standard',
    'cursor': 'Cursor AI Pro',
    'windsurf': 'Windsurf AI (Codeium)',
    'perplexity': 'Perplexity Pro',
    'canva': 'Canva Pro',
    'capcut': 'CapCut Pro',
    'elevenlabs': 'ElevenLabs Voice AI',
    'leonardo': 'Leonardo AI',
    'adobe': 'Adobe Creative Cloud',
    'freepik': 'Freepik Premium',
    'envato': 'Envato Elements',
    'grammarly': 'Grammarly Premium',
    'quillbot': 'QuillBot Premium',
    'turnitin': 'Turnitin Similarity / Instructor',
    'semrush': 'SEMrush Pro / Guru',
    'ahrefs': 'Ahrefs Standard',
    'crunchyroll': 'Crunchyroll Fan / Mega Fan',
    'spotify': 'Spotify Premium',
    'netflix': 'Netflix 4K UHD',
    'youtube': 'YouTube Premium',
    'prime': 'Amazon Prime Video',
    'nordvpn': 'NordVPN',
    'surfshark': 'Surfshark VPN',
    'github copilot': 'GitHub Copilot',
    'copilot': 'Microsoft Copilot Pro',
    'figma': 'Figma Pro',
    'notion': 'Notion Business / Plus',
    'runway': 'Runway ML Gen-3',
    'heygen': 'HeyGen AI Avatar',
    'suno': 'Suno AI Music',
    'udio': 'Udio AI Music',
    'invideo': 'InVideo AI Studio',
    'fliki': 'Fliki AI Video',
    'replit': 'Replit Core',
    'v0': 'v0 by Vercel',
    'bolt': 'Bolt.new Pro',
    'lovable': 'Lovable Pro',
    'deepseek': 'DeepSeek Unlimited / R1',
    'grok': 'SuperGrok / Grok 2',
    'tradingview': 'TradingView Essential / Plus',
    'chegg': 'Chegg Study Pack'
}

detected_catalog = {}

for ch, info in data.items():
    msgs = info.get('messages', [])
    for m in msgs:
        m_lower = m.lower()
        for kw, tool_name in known_keywords.items():
            # match word boundaries or simple substring
            if re.search(r'\b' + re.escape(kw) + r'\b', m_lower):
                if tool_name not in detected_catalog:
                    detected_catalog[tool_name] = []
                detected_catalog[tool_name].append({
                    'channel': ch,
                    'text': m[:350]
                })

# Deduplicate channels per tool
summary = {}
for tool, occurrences in detected_catalog.items():
    channels_seen = sorted(list(set(o['channel'] for o in occurrences)))
    summary[tool] = {
        'channels': channels_seen,
        'count': len(occurrences),
        'sample': occurrences[0]['text']
    }

# Load current website products
with open('app.js', 'r', encoding='utf-8') as f:
    app_js = f.read()

# Parse current tools in store
current_store_tools = []
for line in app_js.splitlines():
    m = re.search(r"name:'([^']+)'", line)
    if m:
        current_store_tools.append(m.group(1))

print(f"Total current store tools: {len(current_store_tools)}")
print(f"Total tools detected from Telegram suppliers: {len(summary)}\n")

already_in_store = {}
missing_opportunities = {}

for tool, details in summary.items():
    # check if any current store tool matches
    matched = any(
        tool.lower().split()[0] in curr.lower() 
        for curr in current_store_tools
    )
    if matched:
        already_in_store[tool] = details
    else:
        missing_opportunities[tool] = details

out = {
    'current_store_tools': current_store_tools,
    'already_in_store': already_in_store,
    'missing_opportunities': missing_opportunities
}

with open('scratch/tools_comparison.json', 'w', encoding='utf-8') as f:
    json.dump(out, f, ensure_ascii=False, indent=2)

print("=== 1. TOOLS ALREADY IN STORE (Suppliers Found) ===")
for t, d in sorted(already_in_store.items()):
    print(f"- {t}: Available in @{', @'.join(d['channels'])}")

print("\n=== 2. HIGH-DEMAND OPPORTUNITY TOOLS (MISSING FROM STORE!) ===")
for t, d in sorted(missing_opportunities.items()):
    print(f"- {t}: Available in @{', @'.join(d['channels'])}")
