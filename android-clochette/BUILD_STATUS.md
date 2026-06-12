# Android Clochette Build Status

- Date: 2026-06-12
- Commit tested: `3caba2e` (`Merge Clochette context phrase libraries`)
- Persona asset validation: `python android-clochette/tools/validate_persona_assets.py`
- Build command: `cd android-clochette && ./gradlew assembleDebug --stacktrace`
- Result: success
- Expected debug APK: `android-clochette/app/build/outputs/apk/debug/app-debug.apk`

Notes:
- `ContextRemarkEngine` loads both `personas/clochette/context_lines.json` and `personas/clochette/app_context_lines.json`.
- Generated lines pass through `withVisibleFrenchAccents()`.
- GitHub Actions validates persona JSON assets before `assembleDebug`.
