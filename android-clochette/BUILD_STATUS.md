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

Settings onboarding reorganization:
- Date: 2026-06-14
- Commit tested: `eecda70`
- Change: `MainActivity` now presents first-launch setup in Android order: notifications, overlay/superposition, Usage Access, app permissions, microphone, notification/media access, accessibility, SMS status, and widget.
- Change: diagnostics and Octopus test controls moved below the normal setup and behavior controls.
- Change: behavior settings are grouped separately from voice settings.
- Change: voice sliders now use reusable readable slider rows with visible values.
- Change: choice settings use reusable dropdown rows.
- Change: module display is summarized as detected/invalid/missing instead of a long raw list.
- Validation command: `python android-clochette/tools/validate_persona_assets.py`
- Validation result: success, 25 Clochette persona JSON assets valid, 28 accepted phrase-bank lines found.
- Build command: `cd android-clochette && .\gradlew.bat assembleDebug --stacktrace --no-daemon`
- Build environment note: Android SDK was provided via `ANDROID_HOME=C:\Users\benoi\Documents\Codex\2026-06-10\tu-travailles-sur-le-d-p-2\android-clochette\.android-sdk`.
- Build result: success.
- Debug APK path: `android-clochette/app/build/outputs/apk/debug/app-debug.apk`
- Manual phone checks still required after installing the APK: verify permission buttons open the expected Android screens, sliders save voice values, dropdowns save behavior/voice/IA choices, Observer/Pause still work, overlay buttons still work, and diagnostics are at the bottom.

Closed overlay modes and personality sliders:
- Date: 2026-06-15
- Commit tested: `5e176a2`
- Main code commits included:
  - `c46df69` Add closed overlay modes and personality sliders
  - `5e176a2` Mask collapsed Clochette portrait in bubble
- Files modified:
  - `ClochetteAppearanceSettings.kt`
  - `ClochettePersonalitySettings.kt`
  - `ClochetteOverlayService.kt`
  - `ClochetteProactiveService.kt`
  - `MainActivity.kt`
  - `PhraseBankSelector.kt`
  - `RelationshipModeSettings.kt`
- Change: added closed appearance settings with `En retrait sur le bord` and `Simple point d'appel`.
- Change: closed overlay point uses a 48dp touch area with an 18dp visible dot.
- Change: edge-peek closed portrait is clipped with a rounded bubble-sized mask so the lower portrait no longer spills outside the bubble.
- Change: closed position is persisted after dragging.
- Change: added `Caractère de Clochette` sliders for Bavardage, Initiative, Taquinerie, Douceur, Longueur des phrases, and Curiosité.
- Behavior impact:
  - Bavardage adjusts effective proactive frequency and delay.
  - Initiative adjusts the probability that a proactive tick actually speaks.
  - Taquinerie, Douceur, Curiosité, and Longueur des phrases influence phrase-bank scoring.
  - Longueur des phrases also adjusts overlay bubble width/line count.
  - Curiosité reduces or increases question preference.
- Validation command: `python android-clochette/tools/validate_persona_assets.py`
- Validation result: success, 25 Clochette persona JSON assets valid, 28 accepted phrase-bank lines found.
- Build command: `cd android-clochette && .\gradlew.bat assembleDebug --stacktrace --no-daemon`
- Build environment note: Android SDK was provided via `ANDROID_HOME=C:\Users\benoi\Documents\Codex\2026-06-10\tu-travailles-sur-le-d-p-2\android-clochette\.android-sdk`.
- Build result: success.
- Debug APK path: `android-clochette/app/build/outputs/apk/debug/app-debug.apk`
- Manual phone checks still required after installing the APK: open Clochette, select edge-peek mode, verify tap opens and portrait is masked; select call-dot mode, verify small dot opens Clochette; long press/drag the point; verify Observer/Pause, voice, micro, overlay over other apps, and wake-from-screen-off behavior.

Octopus Blacklace character casting:
- Date: 2026-06-15
- Commit tested: `56cebbd`
- Change: added `CharacterProfile`, `CharacterSettings`, and `CharacterDirector`.
- Characters included:
  - Clochette: host, main character, handles normal system/help/micro context.
  - Natasha: optional commentator, lucid/acerbic Blacklace guest, never insulting.
  - Feuch: optional chaos guest, energetic/action-oriented, never insulting.
- Change: Octopus now calls `CharacterDirector` after phrase generation and before Guardian, so guest phrases still pass Guardian.
- Change: widget, overlay, and remark store share the same `characterId` with the same final phrase.
- Change: overlay reads current character and falls back to existing Clochette assets if guest-specific assets are missing.
- Change: settings include `Personnages Blacklace` with guest enable, Natasha/Feuch toggles, casting mode, frequency, acidity, chaos, host lock, and Octopus choice.
- Cooldowns:
  - global guest cooldown: 90 seconds.
  - per-character cooldown from profile.
  - max guest appearances per hour: 5.
  - guests disabled while proactive mode is Pause.
- Safety:
  - guests comment only observable signals.
  - no permissions added.
  - no SMS permission added.
  - no Android settings screen is opened by a service.
- Validation command: `python android-clochette/tools/validate_persona_assets.py`
- Validation result: success, 25 Clochette persona JSON assets valid, 28 accepted phrase-bank lines found.
- Build command: `cd android-clochette && .\gradlew.bat assembleDebug --stacktrace --no-daemon`
- Build environment note: Android SDK was provided via `ANDROID_HOME=C:\Users\benoi\Documents\Codex\2026-06-10\tu-travailles-sur-le-d-p-2\android-clochette\.android-sdk`.
- Build result: success.
- Debug APK path: `android-clochette/app/build/outputs/apk/debug/app-debug.apk`
- Manual phone checks still required after installing the APK: Clochette only, guests disabled, Natasha enabled, Feuch enabled, casting modes, overlay open/closed, point mode, edge mode, Observer/Pause, micro, voice, and frequency/cooldown behavior over time.
- Current limitation: Natasha and Feuch do not yet have dedicated visual assets in `res/drawable*`; they use Clochette visual fallback while keeping their own character id, line, tone, and diagnostics.

Active Blacklace character selector:
- Date: 2026-06-15
- Commit tested: `16a1d73`
- Change: user can select exactly one active character at a time.
- Characters prepared:
  - `fee_brune`
  - `sofia`
  - `birdy`
  - `audrey`
  - `feunette_verte`
  - `fee_belette`
  - `brumeux`
  - `feuch`
  - `natasha`
- Change: added `assets/characters/<id>/manifest.json` for every character.
- Change: `CharacterProfile` now includes short description, role label, thumbnail, phraseBankId, default personality, enabled, and userSelectable fields.
- Change: settings show character cards with thumbnail, name, description, role, active badge, and `Choisir` button.
- Change: default Octopus character mode is `Personnage verrouillé`; Octopus does not switch characters automatically unless the user selects another mode.
- Modes available:
  - Personnage verrouillé
  - Suggestions de changement
  - Apparitions rares
  - Mode Blacklace vivant
- Change: active character influences phrase tone preference and overlay character id.
- Validation command: `python android-clochette/tools/validate_persona_assets.py`
- Validation result: success, 25 Clochette persona JSON assets valid, 28 accepted phrase-bank lines found.
- Build command: `cd android-clochette && .\gradlew.bat assembleDebug --stacktrace --no-daemon`
- Build environment note: Android SDK was provided via `ANDROID_HOME=C:\Users\benoi\Documents\Codex\2026-06-10\tu-travailles-sur-le-d-p-2\android-clochette\.android-sdk`.
- Build result: success.
- Debug APK path: `android-clochette/app/build/outputs/apk/debug/app-debug.apk`
- Manual phone checks still required after installing the APK: select each character, close/reopen app, verify active character persists, verify overlay open/closed point/edge modes, verify locked mode does not auto-switch, verify rare/living modes can still use cooldown-limited appearances, verify Observer/Pause, voice, micro, and no permission loop.
- Current limitation: character-specific image files referenced in asset manifests are placeholders for future content; runtime uses existing drawable fallbacks until those PNG/WebP files are added.

Blacklace character avatar install:
- Date: 2026-06-15
- Code commit tested after avatar install: `7e30aa7`
- Change: installed five supplied avatar images and connected them to the active character selector and overlay runtime.
- Avatar mapping used:
  - Photo 1: `natasha`
  - Photo 2: `feuch`
  - Photo 3: `birdy`
  - Photo 4: `fee_belette`
  - Photo 5: `fee_brune`
- Runtime drawable assets added:
  - `res/drawable-nodpi/character_natasha_idle.jpg`
  - `res/drawable-nodpi/character_feuch_idle.jpg`
  - `res/drawable-nodpi/character_birdy_idle.jpg`
  - `res/drawable-nodpi/character_fee_belette_idle.jpg`
  - `res/drawable-nodpi/character_fee_brune_idle.jpg`
- Asset library files added under `assets/characters/<id>/` as `idle.jpg`, `talking.jpg`, and `thumbnail.jpg` for the same five characters.
- Remaining fallback characters: `sofia`, `audrey`, `feunette_verte`, and `brumeux` still use the existing Clochette fallback image until dedicated avatars are supplied.
- Validation command: `python android-clochette/tools/validate_persona_assets.py`
- Validation result: success, 25 Clochette persona JSON assets valid, 28 accepted phrase-bank lines found.
- Build command: `cd android-clochette && .\gradlew.bat assembleDebug --stacktrace --no-daemon`
- Build environment note: Android SDK was provided via `ANDROID_HOME=C:\Users\benoi\Documents\Codex\2026-06-10\tu-travailles-sur-le-d-p-2\android-clochette\.android-sdk`.
- Build result: success.
- Debug APK path: `android-clochette/app/build/outputs/apk/debug/app-debug.apk`
- Manual phone checks still required after installing the APK: select each installed character, verify the selector thumbnail changes, verify overlay open/closed point/edge modes use the selected avatar, verify tap/new phrase, long-press/micro, Observer/Pause, voice, and no permission loop.

Second Blacklace avatar batch:
- Date: 2026-06-15
- Commit tested before commit: `bdce5e9`
- Change: installed four additional supplied avatar images.
- Avatar mapping used:
  - Photo 1: `sofia`
  - Photo 2: `feunette_verte`
  - Photo 3: updated `feuch`
  - Photo 4: updated `fee_belette`
- Runtime drawable assets added or updated:
  - `res/drawable-nodpi/character_sofia_idle.jpg`
  - `res/drawable-nodpi/character_feunette_verte_idle.jpg`
  - `res/drawable-nodpi/character_feuch_idle.jpg`
  - `res/drawable-nodpi/character_fee_belette_idle.jpg`
- Asset library files added or updated under `assets/characters/<id>/` as `idle.jpg`, `talking.jpg`, and `thumbnail.jpg`.
- Remaining fallback characters: `audrey` and `brumeux` still use the existing Clochette fallback image until dedicated avatars are supplied.
- Validation command: `python android-clochette/tools/validate_persona_assets.py`
- Validation result: success, 25 Clochette persona JSON assets valid, 28 accepted phrase-bank lines found.
- Build command: `cd android-clochette && .\gradlew.bat assembleDebug --stacktrace --no-daemon`
- Build environment note: Android SDK was provided via `ANDROID_HOME=C:\Users\benoi\Documents\Codex\2026-06-10\tu-travailles-sur-le-d-p-2\android-clochette\.android-sdk`.
- Build result: success.
- Debug APK path: `android-clochette/app/build/outputs/apk/debug/app-debug.apk`
- Manual phone checks still required after installing the APK: select `sofia`, `feunette_verte`, `feuch`, and `fee_belette`; verify thumbnails and overlay avatars update in open, edge, and point modes.
