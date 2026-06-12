# Clochette AI Gateway

Clochette ne stocke aucune clé Mistral, Gemini ou autre fournisseur dans l'APK.
L'app Android peut appeler une gateway privée configurée par l'utilisateur, puis revenir au moteur local si la gateway est absente ou en erreur.

## Configuration Android

Dans l'app, section `IA de Clochette` :

- `IA distante activée` : active ou coupe les appels réseau.
- `Gateway URL` : URL de base de la gateway, par exemple `https://example.test/clochette`.
- `Provider préféré` : `auto`, `mistral`, `gemini` ou `local`.
- `Style` : `naturel`, `espiegle` ou `feuch`.

L'app appelle :

```text
POST {gatewayUrl}/generate-remark
```

Timeout Android : 8 secondes.

## Payload envoyé

Clochette envoie uniquement des signaux autorisés et résumés :

```json
{
  "personaId": "clochette",
  "relationshipMode": "alive",
  "preferredProvider": "auto",
  "styleLevel": "naturel",
  "foregroundApp": "Spotify",
  "durationMinutes": 12,
  "appSwitchCount": 3,
  "sensorSummary": "movement=still battery=72",
  "energy": "moyenne",
  "recentMemorySummary": "résumé local court",
  "nowPlaying": {
    "appName": "Spotify",
    "title": "Titre si Android l'expose",
    "artist": "Artiste si Android l'expose"
  },
  "userLastReply": null,
  "language": "fr-FR"
}
```

Le contenu privé des autres apps n'est pas lu.

## Réponse attendue

```json
{
  "line": "Tu es sur Spotify depuis un moment. Je reste discrète.",
  "shouldSpeak": true,
  "shouldOpenMic": false,
  "listenSeconds": 15,
  "providerUsed": "mistral",
  "source": "ai_gateway",
  "cooldownMinutes": 8
}
```

La phrase reçue passe ensuite par `GuardianRulesLoader` et `withVisibleFrenchAccents()`.

## Fallback local

Si la gateway est désactivée, vide, lente ou en erreur :

- l'app ne crashe pas ;
- `ContextRemarkEngine` puis `ClochetteEngine` répondent localement ;
- le provider affiché devient `fallback` ou `local` ;
- aucune clé n'est requise côté Android.
