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

md = []
md.append("# AIToolGems — Master Supplier Sourcing & Procurement Database")
md.append("> Complete, deduplicated, verified intelligence on all tools across supplier Telegram bots.\n")
md.append(f"**Total Verified Tool & Plan Offerings:** {len(db)}\n")
md.append("| Tool & Specific Plan | Duration | Warranty | Access & Delivery Type | 🏆 Sasta Supplier (Winner) | Wholesale Cost | Suggested Retail | Net Profit |")
md.append("|---|---|---|---|---|---|---|---|")

for cat, list_items in by_cat.items():
    md.append(f"\n### 📦 Category: {cat}")
    for it in list_items:
        tool_str = f"**{it['tool_name']}**"
        dur_str = it['duration']
        war_str = it['warranty']
        acc_str = it['access']
        bot_str = f"`{it['winner_bot'].split()[0]}`"
        cost_str = f"${it['cost_usd']:.2f} (~Rs. {it['cost_pkr']:,})"
        retail_str = f"**Rs. {it['retail_pkr']:,}**"
        profit_str = f"**+Rs. {it['profit_pkr']:,}**"
        md.append(f"| {tool_str} | {dur_str} | {war_str} | {acc_str} | {bot_str} | {cost_str} | {retail_str} | {profit_str} |")

with open('docs/MASTER_SUPPLIER_PROCUREMENT_CATALOG.md', 'w', encoding='utf-8') as f:
    f.write('\n'.join(md))

print("Saved full report to docs/MASTER_SUPPLIER_PROCUREMENT_CATALOG.md")
