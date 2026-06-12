# Codex map — Clochette Android

Ce fichier est la carte de repérage pour travailler sur `android-clochette/` sans perdre du temps à chercher les fichiers.

## Règle principale

Ne pas modifier la PWA sauf demande explicite.

Fichiers à ne pas toucher dans ce chantier Android :

- `index.html`
- `app.js`
- `sw.js`
- `manifest.webmanifest`
- tout fichier racine lié à la PWA

Travailler principalement dans :

- `android-clochette/`
- `.github/workflows/` uniquement pour les builds APK

## Build

Depuis la racine du repo :

```bash
cd android-clochette
chmod +x ./gradlew
./gradlew assembleDebug
```

APK attendu :

```text
android-clochette/app/build/outputs/apk/debug/app-debug.apk
```

Workflow GitHub Actions attendu :

```text
.github/workflows/android-clochette-debug.yml
```

Artifact attendu :

```text
clochette-debug-apk
```

## Code Kotlin principal

Chemin package :

```text
android-clochette/app/src/main/java/com/feuch/clochette/
```

Fichiers importants :

- `MainActivity.kt` : écran principal, tableau de bord, boutons.
- `ClochetteEngine.kt` : génération locale de remarques.
- `ClochetteMemory.kt` : mémoire locale simple.
- `ProjectKnowledge.kt` : contexte projet.
- `UsageObserver.kt` : observation des apps / usages, si disponible.
- `SensorObserver.kt` : mouvement / capteurs.
- `ClochetteVoice.kt` : synthèse vocale Android.
- `ClochettePresenceService.kt` : présence en arrière-plan.
- `ClochetteOverlayService.kt` : Clochette flottante.
- `ClochetteAccessibilityService.kt` : mode avancé, facultatif.
- `ClochetteWidget.kt` : widget écran d'accueil.
- `PersonaModuleLoader.kt` : chargement défensif des modules JSON.
- `DashboardPanels.kt` : panneaux Compose séparés.
- `ConnectionSettings.kt` : réglages connexions/habitudes.

## Assets persona

Persona principal :

```text
android-clochette/app/src/main/assets/personas/clochette.json
```

Modules persona :

```text
android-clochette/app/src/main/assets/personas/clochette/
```

Modules actuels :

- `interaction.json` : micro court, modes manuel/compagne/vivante.
- `sensor_profiles.json` : promenade, création, fatigue, trajet, etc.
- `memory_rules.json` : règles de mémoire courte et oubli.
- `ai_gateway.json` : stratégie Mistral → Gemini → OpenAI → Ollama.
- `notion_sync.json` : lien Notion et politique de synchro.
- `dreams.json` : rêves, moments privés, génération occasionnelle.
- `app_context_lines.json` : remarques par app et durée.
- `octopus_core.json` : architecture interne modulaire.

Si un module manque ou est invalide, l'app ne doit pas crasher.

## Architecture interne : Octopus Core

Référence :

```text
assets/personas/clochette/octopus_core.json
```

Bras internes :

- `Observer` : apps, durée, batterie, heure, mouvement.
- `Compagnon` : bulle, voix, micro 15 secondes.
- `Archiviste` : souvenirs courts.
- `Rêveur` : phrases candidates et mini-bilans.
- `Bibliothécaire` : JSON, Notion, contenus adoptés.
- `Diplomate IA` : gateway IA et choix fournisseur.
- `Gardien` : anti-absurde, anti-répétition, anti-intrusion.
- `Caméléon` : personas Clochette, Natasha, Pattou, Aloisia.

Le terme `Octopus Core` est interne. Ne pas l'exposer en grand public sauf debug.

## Priorité actuelle

Priorité fonctionnelle :

1. Clochette parle avec des remarques liées au contexte réel.
2. Usage Access si possible : app courante + durée approximative.
3. Micro après question en mode vivant.
4. Anti-phrases abstraites.
5. Onboarding guidé des réglages.
6. Mémoire courte locale.
7. Plus tard seulement : Mistral / Gemini / Notion sync / rêves automatiques.

## Style de Clochette

Elle est :

- espiègle ;
- tendre ;
- intrusive avec de bonnes intentions ;
- un peu badass ;
- prudente ;
- jamais méchante.

Elle dit plutôt :

- « Je peux me tromper... »
- « J'ai l'impression que... »
- « Je remarque juste que... »
- « Je soupçonne que... »

Elle ne doit pas produire de phrases mystiques abstraites sans rapport avec le contexte.

À éviter :

- « Le plan n'est pas l'entrée de service. »
- « Le vide répond au vide. »
- « Les chemins sont des portes. »

## Bons exemples

ChatGPT long :

> Trois heures avec ChatGPT… je vais finir par demander un badge de participante.

Codex long :

> Deux heures avec Codex. À ce niveau, ce n'est plus du debug, c'est une relation karmique.

GitHub :

> Si ce build passe, je lui offre une madeleine virtuelle.

Pinterest :

> Pinterest prolonge. Je déclare officiellement une tempête d'inspiration classée rose pastel.

Nuit :

> Tu sais qu'il existe une légende étrange appelée sommeil ?

## Permissions Android à diagnostiquer

Important : certaines ROM Android / Xiaomi / HyperOS peuvent bloquer ou cacher des autorisations sensibles pour un APK debug.

À vérifier dans `AndroidManifest.xml` :

- overlay ;
- notifications ;
- microphone ;
- foreground service si nécessaire ;
- widget receiver ;
- Usage Access avec `android.permission.PACKAGE_USAGE_STATS`.

Ne jamais lire le contenu des apps. Usage Access doit servir uniquement à :

- package name ;
- nom app ;
- durée approximative ;
- changements d'apps.

## Quand tu modifies

Toujours résumer :

- fichiers changés ;
- ce qui est activé ;
- ce qui n'est pas activé ;
- commande de build ;
- résultat du build ;
- commit.
