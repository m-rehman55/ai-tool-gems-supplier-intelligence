import json

with open('scratch/telegram_crawl.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

with open('scratch/clean_supplier_messages.txt', 'w', encoding='utf-8') as out:
    for ch, info in data.items():
        msgs = info.get('messages', [])
        if not msgs:
            continue
        out.write(f"\n{'='*70}\n")
        out.write(f"CHANNEL: @{ch} (Total Messages: {len(msgs)})\n")
        out.write(f"{'='*70}\n\n")
        for i, m in enumerate(msgs):
            out.write(f"--- [Message #{i+1}] ---\n")
            out.write(m + "\n\n")

print("Dumped clean messages to scratch/clean_supplier_messages.txt")
