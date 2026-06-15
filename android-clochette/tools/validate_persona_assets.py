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
    try:
        from PIL import Image
    except ImportError:
        errors.append("Pillow is required to validate character avatar alpha channels")
        return 0

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
            try:
                image = Image.open(asset_path)
                has_alpha = image.mode in {"RGBA", "LA"} or "transparency" in image.info
                if not has_alpha:
                    errors.append(f"invalid avatar asset: no alpha channel for characterId={character_id} file={file_name}")
                else:
                    alpha = image.convert("RGBA").getchannel("A")
                    minimum, maximum = alpha.getextrema()
                    if minimum == 255 and maximum == 255:
                        errors.append(f"invalid avatar asset: opaque alpha for characterId={character_id} file={file_name}")
                if looks_like_checkerboard(image):
                    errors.append(f"Invalid avatar asset: checkerboard background detected for characterId={character_id}")
            except Exception as exc:
                errors.append(f"invalid character image: characters/{character_id}/{file_name}: {exc}")
    return loaded


def looks_like_checkerboard(image) -> bool:
    rgba = image.convert("RGBA")
    rgb = rgba.convert("RGB")
    width, height = rgba.size
    if width < 12 or height < 12:
        return False
    samples = []
    for x, y in (
        (2, 2),
        (width - 3, 2),
        (2, height - 3),
        (width - 3, height - 3),
        (width // 2, 2),
        (width // 2, height - 3),
    ):
        if rgba.getpixel((x, y))[3] < 24:
            continue
        r, g, b = rgb.getpixel((x, y))
        if max(r, g, b) - min(r, g, b) < 18 and min(r, g, b) > 190:
            samples.append((r, g, b))
    return len(samples) >= 4


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
