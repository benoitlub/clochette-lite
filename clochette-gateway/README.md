# Clochette Gateway

Relais serveur minimal pour Clochette Android.

Les clés Mistral/Gemini ne doivent jamais être stockées dans l'APK ni dans GitHub. Elles restent côté serveur, sous forme de variables d'environnement.

## Variables

Copier `.env.example` vers `.env` localement si besoin, sans committer `.env`.

```bash
MISTRAL_API_KEY=
GEMINI_API_KEY=
```

## Endpoints

`GET /api/health`

```json
{
  "ok": true,
  "service": "clochette-gateway"
}
```

`POST /api/generate-remark`

Retourne toujours un JSON valide. Si Mistral et Gemini sont absents ou échouent, le relais retourne une phrase locale.

```json
{
  "line": "Je suis là. Tu veux que je reste discrète ou que je t’aide à reprendre le fil ?",
  "shouldSpeak": true,
  "shouldOpenMic": false,
  "listenSeconds": 15,
  "providerUsed": "local",
  "source": "ai_gateway",
  "cooldownMinutes": 8
}
```

## Test local

```bash
npm install
npm run check
npm start
```

Ouvrir ensuite la page locale du serveur. Elle affiche l'état des providers sans afficher les clés.
