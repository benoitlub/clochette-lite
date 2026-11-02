# Clochette Lite

Présence cognitive légère du Feuch Institut : une petite présence piquante et
maternelle qui aide Benoît à protéger son élan.

## Deux applications distinctes

| Version | Sources | Installation |
|---|---|---|
| Web / PWA | `index.html`, scripts et styles à la racine | Navigateur, puis ajout à l'écran d'accueil |
| Android native | `android-clochette/` | APK de développement ou Android Studio |

Ajouter le site à l'écran d'accueil installe la PWA, pas l'application native.
Les deux versions n'ont pas les mêmes possibilités ni les mêmes permissions.

## Version web

Le site contient une boussole du moment, un timer adaptatif, des interventions
courtes et un carnet enregistré localement dans le navigateur.

Adresse attendue :
https://benoitlub.github.io/clochette-lite/.

Dans Chrome Android, ouvrir le site puis utiliser « Ajouter à l'écran d'accueil »
ou l'option d'installation proposée par le navigateur.
Pour l'hébergement Pages historique : branche `main`, dossier racine.

Les phrases locales permettent un fonctionnement sans modèle distant.
La passerelle facultative est documentée dans
[clochette-gateway/README.md](clochette-gateway/README.md).
Sa présence dans le dépôt ne prouve pas qu'un serveur distant est configuré.

## Version Android native

Le dossier [android-clochette](android-clochette/README.md) contient l'application
Kotlin : présence en notification, surimpression déplaçable, personnages, mémoire
locale et interactions vocales. Les observations dépendent des permissions
accordées et des capteurs disponibles.

### Obtenir l'APK de développement

1. Ouvrir [Actions — Android Clochette Debug](https://github.com/benoitlub/clochette-lite/actions/workflows/android-clochette-debug.yml).
2. Choisir un run réussi du commit voulu.
3. Télécharger l'artefact `clochette-debug-apk` et extraire l'APK.
4. Installer sur un appareil de test ; Android peut demander l'autorisation
   d'installer une application depuis cette source.

Il s'agit d'un APK debug, pas d'une version publiée sur une boutique.
Un build réussi n'atteste pas les gestes, le microphone ou l'autonomie sur chaque
téléphone.

### Construire depuis les sources

Ouvrir `android-clochette/` dans Android Studio, ou, avec Java et le SDK Android
configurés :

```bash
python3 android-clochette/tools/validate_persona_assets.py
cd android-clochette
./gradlew assembleDebug
```

Sur Windows : `gradlew.bat assembleDebug`.
Sortie : `android-clochette/app/build/outputs/apk/debug/app-debug.apk`.

Les détails d'intégration IA sont dans
[AI_GATEWAY.md](android-clochette/AI_GATEWAY.md) et les vérifications historiques
dans [BUILD_STATUS.md](android-clochette/BUILD_STATUS.md). Ce dernier document
ne vaut pas validation d'une version ultérieure.

## Principe

Clochette distingue ce qu'elle sait, ce qu'elle suppose et ce qu'elle imagine.
Les accès aux usages, notifications, capteurs, surimpression et microphone doivent
être compréhensibles et contrôlables depuis l'application et Android.

Une remarque mémorable vaut mieux qu'un conseil banal.
