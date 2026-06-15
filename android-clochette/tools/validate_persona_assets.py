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

EXPECTED_CHARACTERS = [
    "fee_brune",
    "sofia",
    "birdy",
    "audrey",
    "feunette_verte",
    "fee_belette",
    "brumeux",
    "feuch",
    "natasha",
]

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


def validate_character_assets(errors: list[str]) -> int:
    loaded = 0
    for character_id in EXPECTED_CHARACTERS:
        relative = f"characters/{character_id}/manifest.json"
        manifest_path = ASSETS / relative
        manifest = load_json(manifest_path, relative, errors)
        if not isinstance(manifest, dict):
            continue
        loaded += 1
        manifest_id = manifest.get("id")
        if manifest_id != character_id:
            errors.append(f"Character asset mismatch: expected {character_id} but got {manifest_id}")

        for field in ("idle", "thumbnail"):
            file_name = str(manifest.get(field, "")).strip()
            if not file_name:
                errors.append(f"missing character asset field: {relative}:{field}")
                continue
            asset_path = manifest_path.parent / file_name
            if not asset_path.exists():
                errors.append(f"missing character asset file: characters/{character_id}/{file_name}")
                continue
            if asset_path.suffix.lower() not in {".png", ".webp"}:
                errors.append(f"invalid avatar asset format: characters/{character_id}/{file_name} must be PNG or WebP with alpha")
                continue
            if not file_has_alpha(asset_path):
                errors.append(f"invalid avatar asset: no alpha channel for characterId={character_id} file={file_name}")
    return loaded


def file_has_alpha(path: Path) -> bool:
    data = path.read_bytes()
    suffix = path.suffix.lower()
    if suffix == ".png":
        # PNG color type: 4 = grayscale+alpha, 6 = truecolor+alpha.
        if len(data) < 26 or data[:8] != b"\x89PNG\r\n\x1a\n":
            return False
        return data[25] in {4, 6}
    if suffix == ".webp":
        # WebP extended header stores alpha in VP8X feature flags.
        if len(data) < 21 or data[:4] != b"RIFF" or data[8:12] != b"WEBP":
            return False
        offset = 12
        while offset + 8 <= len(data):
            chunk = data[offset:offset + 4]
            size = int.from_bytes(data[offset + 4:offset + 8], "little")
            payload_start = offset + 8
            if chunk == b"VP8X" and payload_start < len(data):
                return bool(data[payload_start] & 0b00010000)
            offset = payload_start + size + (size % 2)
    return False


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

    character_assets = validate_character_assets(errors)

    if errors:
        print("Clochette persona asset validation failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    print(
        f"OK: {loaded} Clochette persona JSON assets are present and valid; "
        f"{accepted_total} accepted phrase-bank lines found; "
        f"{character_assets} character asset manifests validated."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
