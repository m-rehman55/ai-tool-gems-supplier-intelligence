import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

with open('scratch/master_procurement_database.json', 'r', encoding='utf-8') as f:
    db = json.load(f)

by_cat = {}
for item in db:
    c = item['category']
    if c not in by_cat:
        by_cat[c] = []
    by_cat[c].append(item)

for cat, list_items in by_cat.items():
    print(f"\n=======================================================")
    print(f"📦 {cat.upper()} ({len(list_items)} Plans / Tools)")
    print(f"=======================================================")
    for it in list_items:
        print(f"• {it['tool_name']}")
        print(f"  Duration: {it['duration']} | Warranty: {it['warranty']}")
        print(f"  Access/Login: {it['access']}")
        print(f"  🏆 Supplier Bot: {it['winner_bot']}")
        print(f"  💵 Wholesale Cost: ${it['cost_usd']:.2f} (~Rs. {it['cost_pkr']})")
        print(f"  🏷 Suggested Retail: Rs. {it['retail_pkr']} (Profit: +Rs. {it['profit_pkr']})")
        print("-" * 50)
