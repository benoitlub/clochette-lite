# Clochette Android native

Prototype Android natif separe de la PWA Clochette existante.

Ce dossier ne remplace pas le site web et ne modifie pas `index.html`. Il prepare une app Android Kotlin locale-first, visible et stoppable, avec une bulle en surimpression et des observateurs sobres.

## Ce que fait le prototype

- App Android Kotlin minimale, package `com.feuch.clochette`.
- UI Jetpack Compose pour l'etat, les permissions, le projet courant, l'energie declaree et le Carnet d'indices.
- Service de presence avec notification permanente quand Clochette observe.
- Action `Pause Clochette` dans la notification.
- Service `ClochetteOverlayService` pour afficher une bulle deplacable au-dessus des autres apps.
- Tap sur la bulle : affiche une remarque courte.
- Appui long sur la bulle : ferme la bulle et met Clochette en pause.
- `UsageObserver` lit uniquement des signaux d'usage : package au premier plan, duree approximative et frequence des bascules.
- `SensorObserver` lit sobrement mouvement, orientation, lumiere si disponible et etat d'ecran.
- `ClochetteMemory` stocke localement le Carnet d'indices : evenements observes, interventions, reactions utilisateur, hypotheses et efficacite estimee.
- `ClochetteEngine` produit des remarques locales, courtes, piquantes et non corporate.
- `ProjectKnowledge` embarque les fiches de projets demandees.
- `ClochetteAccessibilityService` existe seulement pour le mode avance "Assistive Clochette", desactive par defaut.

## Ouvrir dans Android Studio

1. Ouvrir Android Studio.
2. Choisir **Open**.
3. Selectionner le dossier `android-clochette/`.
4. Laisser Android Studio synchroniser Gradle.
5. Lancer la configuration `app` sur un appareil ou emulateur Android 8.0+.

Le projet utilise :

- Android Gradle Plugin `8.7.3`
- Kotlin `2.0.21`
- Jetpack Compose avec BOM `2024.12.01`
- `compileSdk 35`
- `minSdk 26`

## Permissions

### Surimpression

Utilisee pour afficher la bulle Clochette au-dessus des autres apps. L'utilisateur doit l'activer explicitement dans les reglages Android.

### Usage Access

Utilisee pour estimer l'app au premier plan et les changements d'application. Le prototype ne lit pas le contenu des apps.

Donnees visees :

- nom de package ;
- duree approximative ;
- changement d'application ;
- frequence des bascules.

### Capteurs

Utilises sobrement pour transformer des capteurs disponibles en signaux simples :

- `walking_possible`
- `phone_still`
- `low_light`
- `screen_active`

Les capteurs sont enregistres avec `SENSOR_DELAY_NORMAL` pour eviter une consommation agressive.

### AccessibilityService

Mode avance "Assistive Clochette", desactive par defaut. Il est declare separement et doit etre active par l'utilisateur dans les reglages d'accessibilite.

Dans ce prototype :

- pas de lecture de contenu de fenetre ;
- pas de gestes automatiques ;
- journalisation limitee aux changements de fenetre/package.

## Limites assumees

- Aucun appel reseau.
- Aucune cle API stockee en dur.
- Pas encore de base Room : le Carnet d'indices utilise `SharedPreferences` et JSON pour rester simple.
- La bulle utilise un `TextView` natif pour rester fiable dans un service de surimpression.
- L'observation d'usage depend de la permission Android `Usage Access`.
- La detection de contexte physique reste approximative.
- La notification Android 13+ demande aussi l'autorisation de notifications.

## Reste a faire

- Remplacer le stockage JSON par Room si le Carnet d'indices grossit.
- Ajouter un vrai ecran detaille du Carnet d'indices.
- Ajouter des controles plus fins pour demarrer/arreter chaque observateur.
- Ajouter des tests unitaires JVM pour `ClochetteEngine` et `ProjectKnowledge`.
- Ajouter une strategie batterie plus stricte si observation longue.
- Definir clairement les capacites du mode "Assistive Clochette" avant toute lecture avancee d'ecran.
