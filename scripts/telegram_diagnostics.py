"""Secret-safe, non-authenticating diagnostics for local Telegram access.

This script intentionally never calls Telegram authorization, prompts for a
phone/code/password, sends a message, or prints any credential value.
"""

import importlib.util
import json
import os
import socket
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
SESSION_BASENAME = ROOT / "data" / "telethon_session"
ENV_FILE = ROOT / ".env"


def load_local_env() -> None:
    """Load only Telegram settings from the private local .env file."""
    if not ENV_FILE.exists():
        return
    allowed = {"TELEGRAM_API_ID", "TELEGRAM_API_HASH", "ALLOW_TELETHON_USER_SESSION"}
    for raw_line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        if key in allowed and not os.getenv(key):
            os.environ[key] = value.strip().strip('"').strip("'")


def env_state(name: str) -> str:
    """Report only whether an expected setting is absent or present."""
    return "configured" if os.getenv(name) else "missing"


def tcp_probe(host: str = "149.154.167.50", port: int = 443, timeout: float = 3.0) -> str:
    """A single harmless TCP reachability probe; never retries or authenticates."""
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return "reachable"
    except socket.timeout:
        return "timeout"
    except OSError as error:
        return f"unreachable:{error.__class__.__name__}"


def main() -> int:
    load_local_env()
    session_files = sorted(SESSION_BASENAME.parent.glob(f"{SESSION_BASENAME.name}*"))
    report = {
        "telegram_connection_verified": False,
        "telethon_library": "installed" if importlib.util.find_spec("telethon") else "missing",
        "telegram_api_id": env_state("TELEGRAM_API_ID"),
        "telegram_api_hash": env_state("TELEGRAM_API_HASH"),
        "user_session_file": "present" if any(path.suffix == ".session" for path in session_files) else "missing",
        "session_file_count": len(session_files),
        "network_probe": tcp_probe(),
        "safe_next_action": "Configure your own API credentials in local .env and authorize only through a trusted local prompt." if not all(os.getenv(key) for key in ("TELEGRAM_API_ID", "TELEGRAM_API_HASH")) else "Credentials are configured; verify the session using the approved local smoke-test flow.",
    }
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
