# Encoding notes — Clochette Android

Ce fichier évite les corrections d'accents trop larges qui cassent le code.

## Problème observé

Certains textes Android peuvent contenir du mojibake ou des accents mal encodés.

Exemples typiques :

- `detecte` au lieu de `détecté` dans l'interface.
- phrases françaises sans accents dans les assets JSON.
- chaînes visibles mélangées à des identifiants Kotlin.

## Règle importante

Ne jamais faire de remplacement global non ciblé sur tout le projet.

À éviter :

```text
replace all detected -> détecté
replace all active -> activé
replace all observe -> observé
```

Pourquoi : cela peut casser des identifiants internes comme :

```kotlin
val detected = ...
fun observeUsage()
data class DetectedModule(...)
```

## Méthode sûre

Corriger uniquement :

1. Les chaînes visibles par l'utilisateur.
2. Les valeurs texte dans les JSON de persona.
3. Les `Text(...)` ou `stringResource(...)` destinés à l'UI.
4. Les messages de boutons, titres, sous-titres, cartes, toasts.

Ne pas modifier :

- noms de variables ;
- noms de fonctions ;
- noms de classes ;
- IDs internes ;
- clés JSON utilisées par le code ;
- package names ;
- valeurs `id` des modules.

## Rechercher les textes visibles

Commandes utiles depuis `android-clochette/` :

```bash
grep -RIn "Text(\|Button\|Toast\|title\|description\|line\|question" app/src/main | head -200
```

Pour trouver les chaînes ASCII suspectes :

```bash
grep -RIn "detecte\|active\|desactive\|repondre\|reglages\|ecoute\|pret\|journee\|idee\|duree" app/src/main app/src/main/assets | head -200
```

## Build après chaque petite passe

Toujours compiler après une correction d'encodage :

```bash
./gradlew assembleDebug
```

## Résumé attendu

Quand tu corriges les textes, indique :

- fichiers modifiés ;
- types de chaînes corrigées ;
- confirmation qu'aucun identifiant Kotlin n'a été renommé ;
- résultat du build.
