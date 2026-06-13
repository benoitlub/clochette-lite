# Android Clochette Build Status

- Date: 2026-06-13
- Commit tested: working tree for `Fix proactive Guardian blocking and voice diagnostics`
- Persona asset validation: `python android-clochette/tools/validate_persona_assets.py`
- Build command: `cd android-clochette && ./gradlew assembleDebug --stacktrace --no-daemon`
- Result: success
- Debug APK: `android-clochette/app/build/outputs/apk/debug/app-debug.apk`

Observed bug fixed:
- The widget could show a generated proactive phrase while the overlay showed `Je garde celle-là pour plus tard.`
- Diagnostics showed `source : guardian_fallback`, `voix : skipped_guardian`, `provider : aucun`.
- The proactive phrase, overlay, widget, memory and voice were not using one single result.

Correction:
- Proactive interventions now pass through `ProactiveInterventionRunner`.
- The runner returns one result: original line, final line, source, Guardian reason, voice status, mic decision and provider.
- Widget and overlay now receive the same `finalLine`.
- The safe test path uses `local_proactive_test`, `guardian : approved`, `voix : spoken`.
- Overlay diagnostics include source, voice status, Guardian decision and provider.
- If Guardian blocks or voice is skipped, the overlay diagnostic bubble remains visible for 60 seconds.
- The overlay includes a visible `Micro` button.
- The `Micro` button now opens a compact voice reply panel inside the overlay instead of navigating to the full `VoiceReplyActivity` page when microphone permission is already granted.
- Proactive questions that open the mic now ask the overlay to open its mic panel.
- The sprite can be dragged even when the text bubble is hidden.
- At rest, the overlay folds into a small portrait bubble instead of leaving the full Clochette silhouette on screen.
- Clochette expands to the full silhouette only while a phrase, diagnostic, drag interaction, or voice reply panel is visible.
- If the microphone times out without a reply, the overlay shows a neutral local line and does not reopen the microphone automatically.
- Prototype fast proactive mode is enabled. In `BAVARDE`, the next attempts are roughly 35 to 60 seconds apart after the first 10-second attempt.
- Local proactive questions are varied, and local `anti_repeat` blocks can be softened as `approved_repeat_softened` so Clochette does not become mute just because she asked a nearby question recently.

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
16. Confirm overlay debug shows `source : local_proactive_test`, `voix : spoken`, `guardian : approved`.
17. Tap `Tester parole proactive maintenant`.
18. Confirm it no longer lands on `guardian_fallback` by default.
19. Hide the bubble, then drag Clochette by the sprite.
20. Tap `Micro` in the overlay and confirm the reply UI stays in the overlay instead of opening a full page.
21. If microphone permission is missing, confirm Android is sent to the visible permission path instead of opening a hidden microphone.
22. Wait for inactivity and confirm Clochette folds into a small portrait bubble.
23. Trigger a phrase or tap the portrait and confirm Clochette expands again.

Notion/API status:
- Notion sync and external AI providers are still not active in this APK.
- Current priority is local proactive speech and visible diagnostics.
- No API key is stored in the repository or APK.
