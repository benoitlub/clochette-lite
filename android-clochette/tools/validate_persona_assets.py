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
]


def main() -> int:
    errors: list[str] = []
    for relative in REQUIRED_JSON:
        path = ASSETS / relative
        if not path.exists():
            errors.append(f"missing: {relative}")
            continue
        try:
            json.loads(path.read_text(encoding="utf-8-sig"))
        except json.JSONDecodeError as exc:
            errors.append(f"invalid json: {relative}:{exc.lineno}:{exc.colno}: {exc.msg}")

    if errors:
        print("Clochette persona asset validation failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    print(f"OK: {len(REQUIRED_JSON)} Clochette persona JSON assets are present and valid.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
