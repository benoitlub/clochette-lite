# Octopus Usage

Octopus est le centre de décision obligatoire de Clochette.

Toute intervention visible qui produit une phrase ou une réaction doit passer par :

```kotlin
OctopusCore.intervene(context, trigger)
```

Les exceptions acceptées sont techniques : chargement initial de l'overlay, demandes de permissions Android, pause/stop de service, affichage brut des réglages.

## Flux décisionnel

1. Le composant appelle Octopus avec un trigger stable.
2. Octopus choisit une phrase locale via `PhraseBankSelector`.
3. Si aucune banque ne convient, Octopus peut utiliser `context_lines` / `app_context_lines`.
4. Si le fallback local est nécessaire, Octopus le marque explicitement.
5. La gateway n'est tentée que si l'IA distante est activée et configurée.
6. Guardian valide ou remplace avec une raison claire.
7. Octopus produit une seule `OctopusDecision`.
8. Widget, overlay, voix, mémoire et diagnostic utilisent cette même décision.

## Triggers

- `manual_tap`
- `proactive_tick`
- `proactive_test`
- `safe_voice_test`
- `overlay_reply`
- `voice_transcription`
- `now_playing_detected`
- `gateway_test`

Ne pas inventer un nouveau trigger sans l'ajouter dans `OctopusCore` et cette documentation.

## Sources De Phrases

Ordre local attendu :

1. `personas/clochette/phrase_banks/`
2. `context_lines.json` / `app_context_lines.json`
3. `ClochetteEngine` comme fallback legacy contrôlé
4. Gateway uniquement si activée
5. Fallback local de sécurité

Chaque phrase issue d'une banque doit remonter :

- `bankId`
- `entryId`
- `tone`
- `trigger`
- `source`

Exemple :

```text
source=local_proactive
bank=focus
entry=focus_next_action_001
tone=focus
guardian=approved
voice=spoken
```

## Guardian

Guardian intervient après le choix de phrase.

Raisons attendues :

- `approved`
- `anti_repeat`
- `approved_repeat_softened`
- `blocked_night`
- `blocked_user_declined`
- `blocked_too_intrusive`
- `guardian_fallback`
- `approved_test_bypass_...`

Si Guardian bloque, Octopus doit laisser une trace visible dans le diagnostic. La bulle ne doit pas disparaître immédiatement.

## Micro

Le chemin manuel doit être unique : maintenir/parler dans l'overlay, avec transcription visible.

Pour une question proactive :

- `OctopusDecision.shouldOpenMic = true`
- ouverture visible dans l'overlay
- `listenSeconds <= 15`
- silence ou erreur => banque `silence_responses`
- pas de relance automatique en boucle

## Overlay, Widget, Voix

Après une `OctopusDecision` :

- Widget affiche `finalLine`
- Overlay affiche `finalLine`
- Voix parle `finalLine`
- Mémoire enregistre `finalLine`
- Diagnostic décrit la même décision

Il ne doit plus y avoir de widget = phrase A, overlay = phrase B, voix = phrase C.

## Apparence

Octopus remonte aussi un état d'apparence :

- `collapsed_portrait`
- `expanded`
- `expanded_idle`
- `expanded_micro`

Même si l'image n'est pas encore chargée dynamiquement, le diagnostic doit indiquer l'apparence choisie.

## Gateway

La gateway est optionnelle.

Si l'URL est vide :

```text
IA distante non configurée · banques locales actives
```

Si elle est activée :

- tester `/api/health`
- générer via `/api/generate-remark`
- timeout court
- fallback local propre
- aucune clé API dans Android
- aucune clé API dans GitHub

## Notion

Notion est la source éditoriale future :

```text
Notion -> export JSON -> phrase_banks -> APK -> Octopus
```

Voir aussi `NOTION_PHRASE_BANKS.md`.

## Chemins Parallèles Audités

À migrer ou garder uniquement comme legacy non appelé :

- `ClochetteOverlayService.speakNextLine()` appelait `ClochetteEngine` directement.
- `ClochetteOverlayService.finishOverlayReply()` répondait localement hors Octopus.
- `ClochetteWidget.onReceive()` choisissait phrase + Guardian + voix hors Octopus.
- `ClochetteProactiveService` appelait `ProactiveInterventionRunner`.
- `MainActivity.generateLine()` / `acceptLine()` généraient hors Octopus.
- `VoiceReplyActivity.replyWithAi()` traite encore la sous-page legacy quand Android y envoie l'utilisateur pour permission.

Objectif de cette passe : overlay, widget, proactif et boutons de test visibles passent par Octopus. Les fonctions legacy restantes doivent être non utilisées ou explicitement documentées.

## Procédure Test Téléphone

1. Installer l'APK debug.
2. Autoriser notifications, overlay, Usage Access, micro si demandé.
3. Ouvrir Clochette.
4. Appuyer `Tester Octopus local`.
5. Vérifier diagnostic : source, bank, entry, tone, guardian.
6. Appuyer `Forcer phrase sûre parlée`.
7. Vérifier `voice=spoken`.
8. Appuyer sur le widget.
9. Vérifier que widget et overlay affichent la même phrase.
10. Appuyer `Tester micro transcription`.
11. Parler, vérifier la transcription et la réponse dans l'overlay.
12. Copier diagnostic et vérifier qu'il suffit à comprendre la décision.
