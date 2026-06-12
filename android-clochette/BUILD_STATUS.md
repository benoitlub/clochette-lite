# Android Clochette Build Status

- Date: 2026-06-12
- Commit tested: working tree after `d624227` (`Add AI gateway settings and fallback`)
- Persona asset validation: `python android-clochette/tools/validate_persona_assets.py`
- Build command: `cd android-clochette && ./gradlew assembleDebug --stacktrace`
- Result: success
- Expected debug APK: `android-clochette/app/build/outputs/apk/debug/app-debug.apk`

Notes:
- `ContextRemarkEngine` loads both `personas/clochette/context_lines.json` and `personas/clochette/app_context_lines.json`.
- Generated lines pass through `withVisibleFrenchAccents()`.
- GitHub Actions validates persona JSON assets before `assembleDebug`.
- Overlay text wraps up to six lines with dynamic width and source debug.
- AI gateway is optional, has no embedded API key, and falls back locally.
- Now Playing uses Android notification listener metadata only when the user grants permission.
- Visible in this APK:
  - top-level `Contrôle vivant` panel with phrase source, AI provider/status, and last action;
  - `Tester intervention vivante` button speaks immediately through proactive voice;
  - `IA de Clochette` panel shows `auto`, `mistral`, `gemini`, `local` and fallback status;
  - overlay debug line shows source, provider, and last action;
  - local natural fallback is used when no gateway URL is configured.
