#!/usr/bin/env python3
"""Validate Clochette persona JSON assets before Android builds.

Run from the repository root:
    python android-clochette/tools/validate_persona_assets.py
"""

from __future__ import annotations

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "android-clochette" / "app" / "src" / "main" / "assets"

REQUIRED_JSON = [
    "personas/clochette.json",
    "personas/shared_library_model.json",
    "personas/clochette/interaction.json",
    "personas/clochette/sensor_profiles.json",
    "personas/clochette/memory_rules.json",
    "personas/clochette/ai_gateway.json",
    "personas/clochette/notion_sync.json",
    "personas/clochette/dreams.json",
    "personas/clochette/context_lines.json",
    "personas/clochette/app_context_lines.json",
    "personas/clochette/octopus_core.json",
    "personas/clochette/guardian_rules.json",
    "personas/clochette/relationship_modes.json",
    "personas/clochette/library_schema.json",
    "personas/clochette/persona_traits.json",
    "personas/clochette/appearance_library.json",
]

REQUIRED_PHRASE_BANKS = [
    "personas/clochette/phrase_banks/natural.json",
    "personas/clochette/phrase_banks/teasing.json",
    "personas/clochette/phrase_banks/soft.json",
    "personas/clochette/phrase_banks/badass.json",
    "personas/clochette/phrase_banks/focus.json",
    "personas/clochette/phrase_banks/fatigue.json",
    "personas/clochette/phrase_banks/creative.json",
    "personas/clochette/phrase_banks/micro_questions.json",
    "personas/clochette/phrase_banks/silence_responses.json",
]


def load_json(path: Path, relative: str, errors: list[str]) -> dict | list | None:
    if not path.exists():
        errors.append(f"missing: {relative}")
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except json.JSONDecodeError as exc:
        errors.append(f"invalid json: {relative}:{exc.lineno}:{exc.colno}: {exc.msg}")
        return None


def accepted_entries(data: dict | list | None) -> list[dict]:
    if isinstance(data, list):
        entries = data
    elif isinstance(data, dict):
        entries = data.get("entries", [])
    else:
        entries = []
    return [
        item for item in entries
        if isinstance(item, dict)
        and item.get("status") == "accepted"
        and str(item.get("line", "")).strip()
    ]


def main() -> int:
    errors: list[str] = []
    loaded = 0
    accepted_total = 0

    for relative in REQUIRED_JSON:
        data = load_json(ASSETS / relative, relative, errors)
        if data is not None:
            loaded += 1

    for relative in REQUIRED_PHRASE_BANKS:
        data = load_json(ASSETS / relative, relative, errors)
        if data is None:
            continue
        loaded += 1
        accepted = accepted_entries(data)
        accepted_total += len(accepted)
        if not accepted:
            errors.append(f"empty phrase bank: {relative} has no accepted line")

    if errors:
        print("Clochette persona asset validation failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    print(
        f"OK: {loaded} Clochette persona JSON assets are present and valid; "
        f"{accepted_total} accepted phrase-bank lines found."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
