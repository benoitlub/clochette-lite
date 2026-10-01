# Clochette — signature Android stable

Les builds de test ordinaires restent des APK debug.

Pour publier un APK installable par-dessus la version précédente sans effacer les données, lancer manuellement le workflow **Android Clochette Debug**. Le job `release` utilise une clé privée stable stockée uniquement dans GitHub Actions Secrets.

Secrets requis :

- `CLOCHETTE_KEYSTORE_B64` — contenu Base64 du fichier JKS
- `CLOCHETTE_KEYSTORE_PASSWORD`
- `CLOCHETTE_KEY_ALIAS`
- `CLOCHETTE_KEY_PASSWORD`

Création initiale de la clé (une seule fois, à conserver aussi dans un coffre privé) :

```bash
keytool -genkeypair -v -keystore clochette-release.jks -alias clochette -keyalg RSA -keysize 4096 -validity 10000
base64 -w 0 clochette-release.jks
```

Ne jamais committer le fichier `.jks`, sa version Base64 ou ses mots de passe.

La première migration depuis un APK debug signé avec une autre clé peut nécessiter une désinstallation. Après installation du premier APK Release signé avec cette clé, les Releases suivantes utilisent la même identité de signature et peuvent mettre l'application à jour en conservant ses données, sous réserve des règles normales de mise à jour Android.
