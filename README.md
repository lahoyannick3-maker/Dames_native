# Dames Native — Squelette Android/NDK

Squelette minimal pour valider la chaîne complète **C (moteur) → .so (NDK) → JNI → Kotlin → écran**,
sans WebView ni WASM, buildé entièrement via GitHub Actions (pas besoin de PC).

## Ce que fait ce squelette pour l'instant

- Un `moteur.c` **stub** (factice) qui génère juste la position de départ du plateau (10x10).
- Un pont JNI (`native-lib.cpp`) qui expose `moteur_version()` et `plateau_initial()` à Kotlin.
- Une `MainActivity` qui charge la lib native et affiche le plateau via un `Canvas` 2D basique (`BoardView`).
- Une CI GitHub Actions qui compile le tout (Gradle + NDK) et produit un APK debug téléchargeable.

## Comment l'utiliser depuis ton téléphone

1. Crée un nouveau dépôt GitHub (ex: `dames-native`).
2. Pousse tout le contenu de ce dossier à la racine du dépôt (via l'app GitHub, ou `git` en ligne de commande si tu as un client Git sur ton téléphone).
3. Le push sur `main` déclenche automatiquement le workflow `.github/workflows/build.yml`.
4. Va dans l'onglet **Actions** du dépôt → le job "Build APK" tourne → à la fin, un artifact `dames-native-debug` contenant `app-debug.apk` est disponible en téléchargement.
5. Télécharge et installe l'APK sur ton ZTE Blade pour valider que ça tourne.

## Prochaine étape : brancher ton vrai moteur

Une fois que tu vois le plateau stub s'afficher sur ton téléphone :

1. Remplace `app/src/main/cpp/moteur.c` par ton vrai `moteur.c` (ou `moteur_no_tt.c`).
2. Mets à jour `app/src/main/cpp/moteur.h` avec les vraies signatures de fonctions (celles utilisées par ton système `CASE_CAPTUREE`/`BLOQUE`, la prise maximale, le minimax, etc.).
3. Adapte `native-lib.cpp` pour exposer les fonctions dont tu as réellement besoin (ex: `jouer_coup()`, `calculer_coup_ia()`, `plateau_courant()`...).
4. Adapte `MainActivity.kt` en conséquence (déclarations `external fun`).

## Après ça : le rendu 3D

`BoardView.kt` est volontairement basique (Canvas 2D) pour isoler le test JNI de tout le reste.
L'étape suivante consistera à remplacer cette vue par un rendu OpenGL ES (recommandé vu les
contraintes matérielles : 2GB RAM, GPU Mali bas de gamme) pour se rapprocher du rendu Three.js actuel.

## Notes techniques

- `minSdk 24`, `abiFilters 'arm64-v8a', 'armeabi-v7a'` — couvre la quasi-totalité des appareils Android récents dont ton ZTE Blade.
- Pas de wrapper Gradle committé (impossible à générer sans PC) — la CI installe Gradle directement via `gradle/actions/setup-gradle`.
- Le NDK est installé explicitement dans le workflow (version `26.1.10909125`, stable et bien supportée).
