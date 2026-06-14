# Android Clochette Build Status

- Date: 2026-06-13
- Commit tested: `d65b4c6`
- Validation command: `python android-clochette/tools/validate_persona_assets.py`
- Validation result: success, 25 Clochette persona JSON assets valid, 28 accepted phrase-bank lines found
- Build command: `cd android-clochette && .\gradlew.bat assembleDebug --stacktrace --no-daemon`
- Build result: success
- Debug APK path: `android-clochette/app/build/outputs/apk/debug/app-debug.apk`

Latest overlay microphone correction:
- Date: 2026-06-14
- Change: replaced fragile press-and-hold microphone capture with tap-to-start / tap-to-stop timed capture.
- Initial capture: 15 seconds maximum.
- Extra capture: tap Clochette after a first result to continue for 20 seconds.
- UI: compact mic-only overlay, Clochette portrait remains the control, countdown and partial transcript stay in the mini capsule above.
- Permission behavior: missing microphone permission opens Android app settings instead of `VoiceReplyActivity`.
- Reuse fix: every voice capture now creates a fresh `SpeechRecognizer`, increments a `voiceSessionId`, clears stale partial text, cancels timers, and ignores callbacks from older sessions.
- Error handling: `no_match`, `speech_timeout`, `recognizer_busy`, `client`, `audio`, and permission errors return the overlay to a tappable stable state.
- Follow-up fix: tapping the Clochette portrait now starts voice capture directly instead of generating a new remark first.
- Follow-up fix: after `stopListening()`, a 2.5-second fallback processes the current partial transcript if Android never sends `onResults` or `onError`.
- Commands run:
  - `python android-clochette/tools/validate_persona_assets.py`
  - `cd android-clochette && .\gradlew.bat assembleDebug --stacktrace --no-daemon`
- Result: success.

Current correction:
- `OctopusCore.intervene(...)` is now the required path for visible Clochette interventions.
- Widget tap, overlay tap/reply, proactive ticks/tests, safe voice test, MainActivity test buttons, and VoiceReplyActivity transcription now route through Octopus.
- `ProactiveInterventionRunner` was removed to avoid a second intervention pipeline.
- Widget, overlay, voice, memory and diagnostics use the same `OctopusDecision.finalLine`.
- Diagnostics include trigger, source, provider, Guardian reason, voice status, overlay state, micro status, appearance, phrase bank id, phrase entry id and tone.
- Local phrase banks under `personas/clochette/phrase_banks/` are the first local source before context lines and local fallback.
- Gateway/Mistral/Gemini remain optional and are only used when explicitly configured or tested.

Bypass audit:
- Direct phrase decisions in `ClochetteWidget`, `ClochetteOverlayService`, `ClochetteProactiveService`, `MainActivity`, and `VoiceReplyActivity` were migrated to Octopus.
- `ContextRemarkEngine` and `ClochetteEngine` are still available only as local fallback helpers owned by Octopus.
- `ClochetteVoice.speakAfterRemark(...)` remains for legacy callers, but proactive/visible intervention speech now uses the Octopus decision path.
- Permission screens, service start/stop, pause, and raw settings display stay outside Octopus because they do not decide a phrase.

Phone test procedure:
1. Install the debug APK.
2. Allow notifications, overlay/surimpression, Usage Access, and microphone when Android asks.
3. Open Clochette.
4. Enable `Voix activée`, `Interventions vocales`, and `Questions spontanées`.
5. Select relationship mode `Vivante` and frequency `Bavarde`.
6. Tap `Observer`.
7. Tap `Tester Octopus local`.
8. Confirm the diagnostic shows `source`, `bank`, `entry`, `tone`, `guardian`, `voice`, `provider`, and `appearance`.
9. Tap `Forcer phrase sûre parlée`.
10. Confirm Clochette speaks without tapping the overlay sprite.
11. Tap the widget and confirm widget and overlay show the same phrase.
12. Hold the overlay reply button, speak, release, and confirm transcription appears in the overlay.
13. Tap `Copier diagnostic` and verify the copied text explains the last decision.

Expected APK behavior:
- Clochette works locally without a gateway.
- Phrase banks drive local interventions when possible.
- Empty Gateway URL shows local fallback behavior instead of blocking the app.
- No API key is stored in the repository or APK.

Overlay tap refresh and collapsed portrait fix:
- Date: 2026-06-14
- Commit tested: `2a1cc07`
- Change: open and collapsed Clochette are tappable again.
- Open portrait tap: calls `OctopusCore.intervene(...)` with `manual_tap` and refreshes the overlay line.
- Open bubble tap: calls the same Octopus path and does not drag the overlay.
- Portrait long press: opens the overlay microphone path.
- Collapsed medallion tap: expands Clochette and requests a fresh Octopus line immediately.
- Collapsed medallion long press: opens the overlay microphone path.
- Drag behavior: movement is handled only from the Clochette portrait/medallion, not from the text bubble.
- Collapsed visual: the medallion has a circular base, portrait uses `CENTER_INSIDE`, and the portrait can overflow above the circle by 14dp so the head is not cropped.
- Validation command: `python android-clochette/tools/validate_persona_assets.py`
- Validation result: success, 25 Clochette persona JSON assets valid, 28 accepted phrase-bank lines found.
- Build command: `cd android-clochette && .\gradlew.bat assembleDebug --stacktrace --no-daemon`
- Build environment note: Android SDK was provided via `ANDROID_HOME=C:\Users\benoi\Documents\Codex\2026-06-10\tu-travailles-sur-le-d-p-2\android-clochette\.android-sdk`.
- Build result: success.
- Debug APK path: `android-clochette/app/build/outputs/apk/debug/app-debug.apk`
- Manual phone checks still required after installing the APK: tap open portrait, tap open bubble, collapse then tap medallion, long press medallion, long press open portrait, drag portrait, verify bubble text/voice/widget all show the same Octopus decision.

Overlay long-press mic guard:
- Date: 2026-06-14
- Commit tested: working tree after `2e9972a`; final commit contains the same source change plus this note.
- Change: long press on the Clochette portrait/medallion starts the overlay microphone immediately, and the following finger release is ignored once so it does not stop the 15-second capture by accident.
- Tap behavior preserved: normal tap on portrait/medallion or bubble still calls `OctopusCore.intervene(...)` through the existing manual tap path.
- Drag behavior preserved: moving the portrait/medallion cancels the pending long press and only drags Clochette; the bubble still does not drag the overlay.
- Validation command: `python android-clochette/tools/validate_persona_assets.py`
- Validation result: success, 25 Clochette persona JSON assets valid, 28 accepted phrase-bank lines found.
- Build command: `cd android-clochette && .\gradlew.bat assembleDebug --stacktrace --no-daemon`
- Build environment note: Android SDK was provided via `ANDROID_HOME=C:\Users\benoi\Documents\Codex\2026-06-10\tu-travailles-sur-le-d-p-2\android-clochette\.android-sdk`.
- Build result: success.
- Debug APK path: `android-clochette/app/build/outputs/apk/debug/app-debug.apk`
