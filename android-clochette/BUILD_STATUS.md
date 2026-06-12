# Android Clochette Build Status

- Date: 2026-06-12
- Commit tested: working tree after `7fb427c` (`Expose Clochette AI settings and proactive speech`)
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
  - `Diagnostic Clochette vivante` explains why proactive speech is silent or spoken;
  - `Tester parole proactive maintenant` calls `ClochetteProactiveService.ACTION_TEST_INTERVENTION`;
  - first proactive attempt after `Observer` is scheduled after 10 seconds;
  - prototype fast proactive mode is enabled with `DEBUG_FAST_PROACTIVE = true`.

Phone test procedure:
1. Install the debug APK.
2. Allow notifications.
3. Allow overlay/surimpression.
4. Allow Usage Access.
5. Disable battery optimization for Clochette if Android offers it.
6. Open Clochette.
7. Enable `Voix activée`.
8. Enable `Interventions vocales`.
9. Enable `Questions spontanées`.
10. Select relationship mode `Vivante`.
11. Select frequency `bavarde`.
12. Tap `Observer`.
13. Tap `Tester parole proactive maintenant`.
14. Confirm Clochette speaks without tapping the overlay sprite.
15. Wait 10 seconds after `Observer` and check `Diagnostic Clochette vivante` for the proactive tick and decision.
