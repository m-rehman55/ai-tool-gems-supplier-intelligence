import json
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')

with open('scratch/full_comparison_matrix.json', 'r', encoding='utf-8') as f:
    matrix = json.load(f)

with open('app.js', 'r', encoding='utf-8') as f:
    app_js = f.read()

store_products = re.findall(r"name:'([^']+)'", app_js)
print(f"Total current tools on our website: {len(store_products)}")

# Current store tools normalized
store_tools_clean = [
    'chatgpt', 'gemini', 'veo', 'leonardo', 'elevenlabs', 'canva', 'figma',
    'capcut', 'adobe', 'lovable', 'gamma', 'replit', 'n8n', 'notion', 'nordvpn',
    'surfshark', 'youtube', 'netflix', 'linkedin', 'windows', 'claude',
    'midjourney', 'cursor', 'perplexity', 'deepseek', 'grok', 'runway',
    'heygen', 'grammarly', 'quillbot', 'semrush', 'allinone'
]

not_in_store = []
in_store = []

for item in matrix:
    t_name = item['tool']
    t_low = t_name.lower()
    
    # Check if any store tool matches
    matched = any(st in t_low for st in store_tools_clean)
    if matched:
        in_store.append(item)
    else:
        not_in_store.append(item)

print(f"Total unique tools extracted from Telegram bots: {len(matrix)}")
print(f"Tools already on website: {len(in_store)}")
print(f"Total tools NOT on our website: {len(not_in_store)}\n")

print("="*60)
print(f"LIST OF {len(not_in_store)} TOOLS NOT ON OUR WEBSITE:")
print("="*60)
for i, t in enumerate(not_in_store, 1):
    print(f"{i}. {t['tool']}")
    print(f"   Cheapest Supplier: {t['cheapest_bot']}")
    print(f"   Cost: ${t['cheapest_usd']} (approx Rs. {t['cheapest_pkr']} PKR)")
    print("-" * 50)
