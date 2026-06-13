# Notion Phrase Banks

Notion est prévu comme source éditoriale, pas comme dépendance runtime.

Flux cible :

```text
Notion -> export JSON -> personas/clochette/phrase_banks/ -> APK -> Octopus
```

L'application Android doit continuer à fonctionner localement si Notion est absent.

## Colonnes Notion Recommandées

- `entryId` : identifiant stable, par exemple `focus_next_action_001`
- `bankId` : `natural`, `teasing`, `soft`, `badass`, `focus`, `fatigue`, `creative`, `micro_questions`, `silence_responses`
- `tone` : ton principal
- `trigger` : `manual_tap`, `proactive_tick`, `proactive_test`, `safe_voice_test`, `overlay_reply`, `voice_transcription`, `now_playing_detected`, `gateway_test`
- `line` : phrase française courte
- `status` : `accepted`, `draft`, `rejected`
- `context` : optionnel, app/contexte ciblé
- `mode` : optionnel, relation/mode vocal
- `maxWords` : optionnel, 25 par défaut
- `notes` : optionnel, non exporté dans l'APK

## Statuts

- `accepted` : exportable vers JSON
- `draft` : gardé côté Notion
- `rejected` : jamais exporté

## Format JSON Cible

Chaque banque vit dans :

```text
app/src/main/assets/personas/clochette/phrase_banks/{bankId}.json
```

Exemple :

```json
{
  "id": "focus",
  "tone": "focus",
  "entries": [
    {
      "id": "focus_next_action_001",
      "triggers": ["proactive_tick", "manual_tap"],
      "line": "Hypothèse : le prochain geste est petit. Trop petit pour faire peur. Parfait."
    }
  ]
}
```

## Correspondance Octopus

Octopus remonte dans son diagnostic :

- `bankId`
- `entryId`
- `tone`
- `trigger`
- `source`

Ce diagnostic doit permettre de retrouver la ligne Notion originale.

## Enrichir Les Banques Sans Kotlin

1. Ajouter ou modifier une ligne dans Notion.
2. Passer son statut à `accepted`.
3. Exporter le JSON de la banque.
4. Remplacer le fichier correspondant dans `phrase_banks/`.
5. Lancer :

```powershell
python android-clochette/tools/validate_persona_assets.py
cd android-clochette
./gradlew assembleDebug --stacktrace --no-daemon
```

Pas besoin de modifier Kotlin si le format reste stable.
