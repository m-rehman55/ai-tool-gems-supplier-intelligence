import uiautomation as auto
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

root = auto.GetRootControl()
print("Scanning top-level desktop windows...")
found = False
for child in root.GetChildren():
    n = child.Name or ""
    c = child.ClassName or ""
    if "telegram" in n.lower() or "telegram" in c.lower() or child.ProcessId == 23248:
        print(f"FOUND: Name='{n}', Class='{c}', PID={child.ProcessId}, Rect={child.BoundingRectangle}")
        found = True

if not found:
    print("No window found matching Telegram or PID 23248. Listing all visible windows:")
    for child in root.GetChildren():
        if child.Name:
            print(f" - {child.Name} (PID={child.ProcessId}, Class={child.ClassName})")
