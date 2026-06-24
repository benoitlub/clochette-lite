# Android Clochette Build Status

## Version 38 - Safe automatic microphone after TTS

- Date: 2026-06-24
- Commit tested: `677e6d3`
- Version: `versionCode 38`, `versionName 0.1.38`
- Supersedes version 37 behavior: `AUTO_AFTER_TTS` is enabled again, with strict TTS and recognizer guards.
- Files modified:
  - `app/build.gradle.kts`
  - `ClochetteOverlayService.kt`
  - `OctopusCore.kt`
  - `VoiceInteractionController.kt`
- Automatic reply flow:
  1. Octopus marks a real question with `shouldOpenMic`.
  2. TTS is queued and reports real `onStart` / `onDone`.
  3. Overlay waits for the matching `onDone`.
  4. Overlay waits another 650 ms.
  5. A single `AUTO_AFTER_TTS` session is created for 15 seconds.
  6. `À toi — j’écoute… 15 s` appears only after Android calls `onReadyForSpeech`.
- Guards:
  - `startListening()` is rejected while TTS is queued/speaking.
  - `AUTO_AFTER_TTS` is correlated to one question and consumed once.
  - No automatic restart after silence or recognizer error.
  - Missing TTS completion callback falls back to the manual reply prompt; it never starts an unsafe microphone session.
  - SpeechRecognizer is cancelled/destroyed and stale callbacks are invalidated between sessions.
  - Avatar tap/drag remains independent from the microphone during listening and after no-speech.
- Silence behavior:
  - Returns to `IDLE`.
  - Shows `Je n’ai rien entendu. Touche le micro pour réessayer.`
  - Keeps bubble readable and avatar draggable.
  - Only the explicit microphone button can retry that same interaction.
- Validation:
  - `python android-clochette/tools/validate_persona_assets.py`
  - Result: success; 25 persona JSON assets valid, 28 accepted phrase-bank lines, 9 character manifests.
- Build:
  - `cd android-clochette && .\gradlew.bat assembleDebug --stacktrace --no-daemon`
  - Result: `BUILD SUCCESSFUL`
  - APK: `android-clochette/app/build/outputs/apk/debug/app-debug.apk`
- Test matrix:
  - A. No microphone during TTS: state/source guards verified in code; phone audio test pending.
  - B. Automatic microphone after real TTS `onDone` + 650 ms: code path verified; phone timing test pending.
  - C. User transcription: recognizer readiness and partial/final callbacks verified in code; phone speech test pending.
  - D. Silence message and IDLE recovery: verified in code; phone UI test pending.
  - E. Readable/draggable overlay after silence without loop: verified in gesture/state code; phone drag test pending.
  - F. Manual microphone retry: explicit `MICRO_BUTTON` path verified; phone retry test pending.

## Version 37 - TTS / microphone state machine

- Date: 2026-06-24
- Commit tested: `b185d6b`
- Version: `versionCode 37`, `versionName 0.1.37`
- Files modified:
  - `app/build.gradle.kts`
  - `ClochetteOverlayService.kt`
  - `ClochetteVoice.kt`
  - `OctopusCore.kt`
  - `VoiceInteractionController.kt`
  - `VoiceReplyActivity.kt`
- Main corrections:
  - TTS state now uses the real `UtteranceProgressListener` callbacks (`onStart`, `onDone`, `onError`).
  - Microphone start is rejected while TTS is queued/speaking and for 650 ms after the real TTS `onDone`.
  - `AUTO_AFTER_TTS disabled by default`.
  - Octopus questions create `reply_prompt`; `shouldOpenMic` remains `false`.
  - After a question, overlay displays: `À toi — touche le micro pour répondre.`
  - Manual listening can start only from `MICRO_BUTTON`; non-manual sources are rejected unless explicit debug mode is enabled.
  - `J’écoute…` appears only after Android calls `onReadyForSpeech`.
  - SpeechRecognizer is cancelled, destroyed, callbacks cleared, and recreated between sessions.
  - Silence exits mic-only mode, returns to `IDLE`, restores readable/draggable overlay, and displays: `Je n’ai rien entendu. Touche le micro pour réessayer.`
  - Avatar touch is independent from microphone control: tap toggles/readability, drag always moves, long press only reports movement help.
  - Debug overlay reports voice/TTS/recognizer states, trigger source, touch target, drag/expand flags, no-speech reason, TTS completion delay, and current TTS activity.
- Validation:
  - `python android-clochette/tools/validate_persona_assets.py`
  - Result: success; 25 persona JSON assets valid, 28 accepted phrase-bank lines, 9 character manifests.
- Build:
  - `cd android-clochette && .\gradlew.bat assembleDebug --stacktrace --no-daemon`
  - Result: `BUILD SUCCESSFUL`
  - APK: `android-clochette/app/build/outputs/apk/debug/app-debug.apk`
- Test matrix:
  - A. Question/TTS/no auto-micro: code path verified; physical phone confirmation pending.
  - B. Explicit micro button/readiness/transcription: code path verified; physical phone confirmation pending.
  - C. Silence/readable IDLE/drag enabled: code path verified; physical phone confirmation pending.
  - D. Drag and avatar tap while listening: gesture separation verified in code; physical phone confirmation pending.
  - E. Recovery after microphone error: recognizer reset and readable error state verified in code; physical phone confirmation pending.
  - F. Anti-auto-transcription: TTS guard and manual-only source verified in code; loudspeaker phone test pending.

Reduced overlay tap/micro separation fix:
- Date: 2026-06-17
- Commit tested: working tree after `966dbb5`; final commit contains the same source changes.
- Summary:
  - Reduced avatar/portrait/pastille tap no longer starts microphone capture.
  - Reduced avatar tap records `AVATAR_REDUCED_TAP`, expands the overlay, and shows the bubble/last line.
  - Bubble tap records `BUBBLE_TAP` and keeps the bubble visible instead of starting the microphone.
  - Micro capture can start only from `MICRO_BUTTON`, `PROACTIVE_REPLY_REQUEST`, or `AUTO_PROMPT`.
  - The microphone badge records `MICRO_BUTTON` and is the explicit manual path for listening.
  - Avatar long press records `AVATAR_LONG_PRESS` and shows the helper text; it does not start the microphone.
  - Avatar drag records `AVATAR_DRAG` and only moves the overlay.
  - After no speech, Clochette exits mic-only mode, shows “Je n’ai rien entendu...” in the normal bubble, records `lastNoSpeechAt`, and returns voice state to `IDLE`.
  - Overlay diagnostic now includes `lastTouchTarget`, `lastVoiceTriggerSource`, `overlayMode`, `voiceState`, `lastNoSpeechAt`, and `canExpand`.
- Files modified:
  - `ClochetteOverlayService.kt`
  - `VoiceInteractionController.kt`
- Validation command: `python android-clochette/tools/validate_persona_assets.py`
- Validation result: success, 25 Clochette persona JSON assets valid, 28 accepted phrase-bank lines found, 9 character asset manifests validated.
- Build command: `cd android-clochette && .\gradlew.bat assembleDebug --stacktrace --no-daemon`
- Build result: success.
- Debug APK path: `android-clochette/app/build/outputs/apk/debug/app-debug.apk`
- Manual tests to run on phone:
  - A. Clochette réduite → tap avatar: expected overlay expands, bubble visible, no microphone.
  - B. Clochette réduite → tap microphone badge: expected 15-second listening/transcription.
  - C. Micro silence: expected “Je n’ai rien entendu...” in the bubble and voice state back to IDLE.
  - D. After no speech → tap avatar: expected opens bubble only, no microphone restart.
  - E. After no speech → tap microphone badge: expected clean new listening session.
  - F. Drag reduced avatar: expected movement only, no microphone, no accidental open.
  - G. Long press reduced avatar: expected helper/tooltip only, no microphone.

Voice state, permissions, and conversational context stabilization:
- Date: 2026-06-16
- Commit tested: working tree after `992a118`; final commit below contains the same source changes.
- Summary:
  - Added a central `VoiceInteractionController` with states `IDLE`, `SPEAKING`, `LISTENING`, `TRANSCRIBING`, `THINKING`, and `COOLDOWN`.
  - TTS now stops before microphone listening, and proactive speech is skipped while the microphone is listening/transcribing.
  - TTS returns to `IDLE` after an estimated speech duration instead of leaving Clochette stuck in `SPEAKING`.
  - Overlay gestures are separated: portrait/bubble are for opening or requesting a line; the microphone badge is the explicit listen control.
  - Overlay micro permission no longer opens Android app settings repeatedly after the first denial.
  - Added `PermissionStateManager` to remember explicit permission prompts and avoid loops.
  - Added `ConversationContextStore` so user transcription stores intent, mood, energy, tags, active character, last avatar line, and the last 10 interactions.
  - User replies now affect Octopus phrase selection and character response style instead of using a generic random reply.
  - Phrase-bank scoring now accounts for active character, conversation mood/intent/tags, recent repetition rejection, and personality sliders.
  - Diagnostics now expose user intent, mood, energy, selected tags, score/reason, and rejected recent lines.
- Validation command: `python android-clochette/tools/validate_persona_assets.py`
- Validation result: success, 25 Clochette persona JSON assets valid, 28 accepted phrase-bank lines found, 9 character asset manifests validated.
- Build command: `cd android-clochette && .\gradlew.bat assembleDebug --stacktrace --no-daemon`
- Build environment note: Android SDK was provided via `ANDROID_HOME=C:\Users\benoi\Documents\Codex\2026-06-10\tu-travailles-sur-le-d-p-2\android-clochette\.android-sdk`.
- Build result: success.
- Debug APK path: `android-clochette/app/build/outputs/apk/debug/app-debug.apk`
- Phone checks still required:
  1. Open each character and verify the avatar remains readable on bright/dark backgrounds.
  2. Tap portrait/bubble for a line; tap microphone badge for listening.
  3. Verify Clochette does not speak while listening and does not transcribe her own TTS.
  4. Speak phrases such as "j'ai la flemme" or "je suis crevé" and confirm the next line changes with character style.
  5. Confirm diagnostics show intent, mood, tags, reason, phrase bank, Guardian, provider, and voice state.
  6. Confirm permission buttons do not reopen Android settings automatically in a loop.

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

Character avatar mapping and alpha fix:
- Date: 2026-06-15
- Commit tested before commit: `d9c6306`
- Change: replaced character JPG assets with PNG assets carrying a real alpha channel.
- Change: removed tracked JPG avatar files so checkerboard backgrounds cannot be selected by manifests.
- Change: every `assets/characters/<id>/manifest.json` now points to `idle.png`, `talking.png`, and `thumbnail.png`.
- Change: `CharacterRegistry` now maps every character ID directly to its matching drawable resource; no index/order-based mapping is used.
- Runtime validation added:
  - logs `Character asset mismatch: expected <id> but got <manifest id>` if a manifest ID is wrong.
  - logs missing manifest fields/files.
  - logs non-alpha JPG/JPEG references as invalid avatar assets.
- Build validation added in `tools/validate_persona_assets.py`:
  - validates all 9 character manifests.
  - verifies `manifest.id` exactly matches the character folder/id.
  - verifies `idle` and `thumbnail` exist.
  - rejects JPG/JPEG avatar references.
  - verifies PNG/WebP assets have an alpha channel.
  - detects obvious checkerboard backgrounds on opaque corner samples.
- Corrected explicit mapping:
  - `fee_brune`: Clochette / fée brune gothic model.
  - `sofia`: brunette portrait.
  - `birdy`: purple fairy with megaphone.
  - `audrey`: blue-haired character with cigarette.
  - `feunette_verte`: small green creature.
  - `fee_belette`: dark-haired cartoon fairy.
  - `brumeux`: dark man with glasses.
  - `feuch`: orange/eye-themed supplied avatar available in the asset set.
  - `natasha`: blue-haired Natasha asset.
- Validation command: `python android-clochette/tools/validate_persona_assets.py`
- Validation result: success, 25 Clochette persona JSON assets valid, 28 accepted phrase-bank lines found, 9 character asset manifests validated.
- Build command: `cd android-clochette && .\gradlew.bat assembleDebug --stacktrace --no-daemon`
- Build environment note: Android SDK was provided via `ANDROID_HOME=C:\Users\benoi\Documents\Codex\2026-06-10\tu-travailles-sur-le-d-p-2\android-clochette\.android-sdk`.
- Build result: success.
- Debug APK path: `android-clochette/app/build/outputs/apk/debug/app-debug.apk`
- Manual phone checks still required after installing the APK: select all 9 characters one by one and verify name, thumbnail, open overlay avatar, edge mode avatar, point mode access, tap/new phrase, long-press/micro, Observer/Pause, and no visible checkerboard on light or dark backgrounds.

Character asset validation CI fix:
- Date: 2026-06-15
- Commit tested before commit: `ab040b1`
- Problem found after push: `validate_persona_assets.py` used Pillow, but GitHub Actions does not install Python dependencies.
- Change: replaced Pillow usage with standard-library PNG/WebP alpha-header checks.
- Validation command: `python android-clochette/tools/validate_persona_assets.py`
- Validation result: success, 25 Clochette persona JSON assets valid, 28 accepted phrase-bank lines found, 9 character asset manifests validated.
- Build command: `cd android-clochette && .\gradlew.bat assembleDebug --stacktrace --no-daemon`
- Build result: success.
