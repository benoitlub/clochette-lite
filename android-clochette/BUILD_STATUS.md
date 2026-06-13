# Android Clochette Build Status

- Date: 2026-06-13
- Previous build command: `cd android-clochette && ./gradlew assembleDebug --stacktrace --no-daemon`
- Previous result: success before phrase-bank wiring
- Latest change: Notion-ready phrase banks + Octopus local selection
- Latest validation to run: `python android-clochette/tools/validate_persona_assets.py`
- Latest build to run: `cd android-clochette && ./gradlew assembleDebug --stacktrace --no-daemon`
- Debug APK path: `android-clochette/app/build/outputs/apk/debug/app-debug.apk`

Observed bug fixed earlier:
- The widget could show a generated proactive phrase while the overlay showed `Je garde celle-là pour plus tard.`
- Diagnostics showed `source : guardian_fallback`, `voix : skipped_guardian`, `provider : aucun`.
- The proactive phrase, overlay, widget, memory and voice were not using one single result.

Correction already present:
- Proactive interventions pass through `ProactiveInterventionRunner`.
- The runner returns one result: original line, final line, source, Guardian reason, voice status, mic decision and provider.
- Widget and overlay receive the same `finalLine`.
- The safe test path uses `local_proactive_test`, `guardian : approved`, `voix : spoken`.
- Overlay diagnostics include source, voice status, Guardian decision and provider.
- If Guardian blocks or voice is skipped, the overlay diagnostic bubble remains visible for 60 seconds.
- The overlay includes a visible `Micro` button.
- The `Micro` button opens a compact voice reply panel inside the overlay instead of navigating to the full `VoiceReplyActivity` page when microphone permission is already granted.
- Proactive questions that open the mic ask the overlay to open its mic panel.
- The sprite can be dragged even when the text bubble is hidden.
- At rest, the overlay folds into a small portrait bubble instead of leaving the full Clochette silhouette on screen.
- Clochette expands to the full silhouette only while a phrase, diagnostic, drag interaction, or voice reply panel is visible.
- If the microphone times out without a reply, the overlay shows a neutral local line and does not reopen the microphone automatically.
- `OctopusCore` can produce a single `OctopusDecision` used for line, source, provider, Guardian, voice, overlay, micro and diagnostic.
- `Octopus / diagnostic` is visible in MainActivity with copyable diagnostic text.
- `Relais API Clochette` is visible with Gateway URL, provider choice, style choice, health test, generation test, last status, latency, raw response and error.
- Gateway calls use `/api/health` and `/api/generate-remark`.
- `clochette-gateway/` contains a server scaffold with no real API key committed.
- Prototype fast proactive mode is enabled. In `BAVARDE`, the next attempts are roughly 35 to 60 seconds apart after the first 10-second attempt.
- Local proactive questions can soften local `anti_repeat` blocks as `approved_repeat_softened`.

Phrase bank wiring:
- Added non-empty phrase banks under `personas/clochette/phrase_banks/`:
  - `natural.json`
  - `teasing.json`
  - `soft.json`
  - `badass.json`
  - `focus.json`
  - `fatigue.json`
  - `creative.json`
  - `micro_questions.json`
  - `silence_responses.json`
- `validate_persona_assets.py` now checks these banks and fails if a bank has no accepted line.
- `PersonaModuleLoader` now lists phrase banks in the module diagnostics.
- `PhraseBankSelector` loads accepted local lines, scores them by trigger, context, relationship mode, tone and mic intent.
- `ProactiveInterventionRunner` asks `PhraseBankSelector` before falling back to hardcoded local proactive lines.
- `OctopusCore` asks `PhraseBankSelector` before falling back to `ContextRemarkEngine` or local hardcoded lines.
- Octopus diagnostics enrich `Source phrase` with `bank=...`, `id=...`, and `tone=...` when a phrase bank entry is used.

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
11. Select frequency `Bavarde`.
12. Tap `Observer`.
13. Tap `Forcer phrase sûre parlée`.
14. Confirm Clochette speaks without tapping the overlay sprite.
15. Confirm overlay and widget show the same phrase.
16. Confirm overlay debug shows `source`, `voix`, `guardian`, and `provider`.
17. Tap `Tester parole proactive maintenant`.
18. Confirm it no longer lands on `guardian_fallback` by default.
19. Tap `Tester Octopus local`.
20. Confirm the Octopus panel shows a phrase source containing `bank=...`, `id=...`, and `tone=...`.
21. Tap `Copier diagnostic` and paste it into a note/chat if needed.
22. Leave Gateway URL empty, tap `Tester le relais`, and confirm the app shows fallback local instead of crashing.

Notion/API status:
- Notion sync and external AI providers are still not required for local Clochette behavior.
- Phrase banks are ready to be generated from Notion exports.
- External providers remain routed through `clochette-gateway/`; keys stay server-side.
- No API key is stored in the repository or APK.
