#include "moteur.h"

// --- STUB DE VALIDATION ---
// Ceci N'EST PAS ton vrai moteur.c. C'est un stub minimal pour valider
// que la chaîne complète fonctionne : C -> .so (NDK) -> JNI -> Kotlin -> écran.
//
// Une fois que tu vois le plateau s'afficher sur ton téléphone, remplace
// ce fichier par ton vrai moteur.c (ou moteur_no_tt.c), et adapte
// native-lib.cpp pour appeler tes vraies fonctions (minimax, prise maximale,
// CASE_CAPTUREE/BLOQUE, etc.)

const char* moteur_version() {
    return "Stub moteur C v0.1 - a remplacer par le vrai moteur.c";
}

void plateau_initial(int* plateau) {
    // 0 = case vide, 1 = pion joueur 1 (bas), 2 = pion joueur 2 (haut)
    for (int i = 0; i < 100; i++) {
        plateau[i] = 0;
    }

    // Joueur 2 : 4 rangées du haut, cases foncées uniquement
    for (int row = 0; row < 4; row++) {
        for (int col = 0; col < 10; col++) {
            if ((row + col) % 2 == 1) {
                plateau[row * 10 + col] = 2;
            }
        }
    }

    // Joueur 1 : 4 rangées du bas, cases foncées uniquement
    for (int row = 6; row < 10; row++) {
        for (int col = 0; col < 10; col++) {
            if ((row + col) % 2 == 1) {
                plateau[row * 10 + col] = 1;
            }
        }
    }
}
