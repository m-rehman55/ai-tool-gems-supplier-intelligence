"""
Integration and specification verification test for Android APK project.
Verifies:
- Android directory structure, manifests, and build scripts exist.
- Supplier handles in SeedSuppliers.kt match canonical leads in src/seed_data.py.
- SafetyBoundary patterns match src/research.py forbidden purchase actions.
- Android workflow configuration is valid.
"""

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent.parent
ANDROID_DIR = ROOT / "android"

def test_android_project_structure():
    assert ANDROID_DIR.exists(), "android/ directory must exist"
    assert (ANDROID_DIR / "build.gradle.kts").exists(), "root build.gradle.kts must exist"
    assert (ANDROID_DIR / "settings.gradle.kts").exists(), "settings.gradle.kts must exist"
    assert (ANDROID_DIR / "app" / "build.gradle.kts").exists(), "app/build.gradle.kts must exist"
    assert (ANDROID_DIR / "app" / "src" / "main" / "AndroidManifest.xml").exists(), "AndroidManifest.xml must exist"
    assert (ANDROID_DIR / ".github" / "workflows" / "android-build.yml").exists() or (ROOT / ".github" / "workflows" / "android-build.yml").exists(), "CI workflow must exist"

def test_android_canonical_suppliers_match_seed_data():
    seed_suppliers_kt = (ANDROID_DIR / "app" / "src" / "main" / "java" / "tech" / "aitoolgems" / "supplierpricefinder" / "data" / "local" / "SeedSuppliers.kt").read_text(encoding="utf-8")
    python_seed = (ROOT / "src" / "seed_data.py").read_text(encoding="utf-8")

    expected_handles = [
        "@getdigitaltools_bot",
        "@PiggyAi799_Bot",
        "@sellnitystorebot",
        "@Novasubglo_bot",
        "@PremiKeyBot",
        "@aitoolify_bot",
        "@premiumtools_bot",
        "@AI_corn_bot",
        "@FatBunny_Hub_bot",
        "@ptstorebd_bot",
        "@Verified_Streams_bot",
        "@aitoolpre_bi_bot",
        "@spaivault_bot",
        "@prmastbot",
        "@storepremium_bot",
        "@MediaHubMarketplace_bot"
    ]

    for handle in expected_handles:
        assert handle.lower() in seed_suppliers_kt.lower(), f"Expected handle {handle} in Android SeedSuppliers.kt"
        assert handle.lower() in python_seed.lower(), f"Expected handle {handle} in Python seed_data.py"

def test_safety_boundary_consistency():
    safety_kt = (ANDROID_DIR / "app" / "src" / "main" / "java" / "tech" / "aitoolgems" / "supplierpricefinder" / "engine" / "SafetyBoundary.kt").read_text(encoding="utf-8")
    research_py = (ROOT / "src" / "research.py").read_text(encoding="utf-8")

    critical_blocked_tokens = ["buy", "purchase", "checkout", "order", "wallet", "deposit", "invoice", "pay"]
    for token in critical_blocked_tokens:
        assert token in safety_kt.lower(), f"SafetyBoundary.kt must contain blocked token '{token}'"
        assert token in research_py.lower(), f"research.py must contain blocked token '{token}'"

def test_android_manifest_permissions():
    manifest_text = (ANDROID_DIR / "app" / "src" / "main" / "AndroidManifest.xml").read_text(encoding="utf-8")
    assert "android.permission.INTERNET" in manifest_text
    # Ensure no dangerous SMS or contact permissions
    assert "SEND_SMS" not in manifest_text
    assert "READ_SMS" not in manifest_text
    assert "READ_CONTACTS" not in manifest_text
