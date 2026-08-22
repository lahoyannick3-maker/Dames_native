# Dames Native — v2 : vrai moteur branché

Le vrai `moteur.c` (minimax, Zobrist, table de transposition — celui utilisé
en WASM) est maintenant branché en natif via NDK/JNI. Le jeu est jouable en
humain contre humain (pass-and-play), en 2D, avec toutes les règles gérées
par le C : prise obligatoire, prise maximale, rafles multiples, promotion.

## Ce qui a changé depuis le squelette v1

- `moteur.c` : ton vrai fichier, avec 3 fonctions ajoutées à la fin
  (`natif_plateauInitial`, `natif_coupsPour`, `natif_jouerCoup`). Aucune
  ligne existante modifiée — l'IA (`wasm_calculerMeilleurCoup`) est
  intacte mais pas encore branchée côté Kotlin (prochaine étape).
- `native-lib.cpp` : pont JNI vers ces 3 fonctions.
- `MoteurJeu.kt` : wrapper Kotlin (types, parsing JSON).
- `MainActivity.kt` : gestion complète du tour de jeu (sélection, rafles,
  promotion, victoire, nulle) — zéro règle dupliquée, tout passe par le C.
- `BoardView.kt` : affichage + détection tactile (case sélectionnée,
  destinations surlignées).
- `gradle.properties` : ajouté (fix de la première erreur de build).

## Comment tester

1. Remplace le contenu de ton dépôt GitHub par ce dossier.
2. Push sur `main` → onglet Actions → récupère l'APK dans l'artifact.
3. Installe sur ton téléphone. Tu dois pouvoir :
   - toucher un pion → ses destinations légales se surlignent
   - toucher une destination → le pion se déplace
   - si une prise est obligatoire quelque part, seules les pièces
     pouvant capturer se sélectionnent
   - une rafle multiple s'enchaîne automatiquement (tu ne touches que
     les destinations successives)
   - un pion arrivé au bout du plateau devient dame (anneau doré)
   - la partie se termine (Toast) si un camp est bloqué

## Prochaines étapes

1. **Brancher l'IA** : exposer `wasm_calculerMeilleurCoup` en JNI, appeler
   depuis un thread Kotlin (Coroutine) pour ne pas bloquer l'UI pendant le
   calcul — équivalent du Web Worker JS actuel.
2. **UI** : clavier virtuel initiales, modales de confirmation, écran de
   victoire avec confettis (portage direct depuis `index.html`).
3. **Rendu 3D** : remplacer `BoardView` (Canvas 2D) par OpenGL ES.
