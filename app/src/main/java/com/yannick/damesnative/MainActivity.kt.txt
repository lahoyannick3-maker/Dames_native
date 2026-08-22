package com.yannick.damesnative

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Gère l'état de la partie (tour, sélection, rafle) et fait le lien entre
 * BoardView (affichage/tactile) et MoteurJeu (règles, en C natif).
 * Aucune règle du jeu n'est ré-implémentée ici : on ne fait que réagir aux
 * réponses du moteur.
 *
 * Étape actuelle : humain contre humain (pass-and-play), 2D. L'IA
 * (wasm_calculerMeilleurCoup côté C, pas encore exposée en JNI) et le rendu
 * 3D viendront dans les étapes suivantes.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var boardView: BoardView

    private var plateau: ByteArray = ByteArray(100)
    private var couleurActuelle: Int = MoteurJeu.NOIR // les noirs commencent, comme côté JS

    private var selection: Pair<Int, Int>? = null
    /** Coups légaux (1er saut) depuis la case sélectionnée. */
    private var coupsDepuisSelection: List<CoupLegal> = emptyList()

    /** Tous les chemins COMPLETS (jusqu'à la fin de la rafle) accessibles
     * depuis la sélection actuelle. Chaque chemin est la liste des cases
     * d'arrivée, saut après saut. Reconstruit en explorant récursivement
     * coupsPour/jouerCoup côté moteur natif (aucune règle réécrite ici,
     * juste de l'exploration). Permet de jouer toute une rafle en un seul
     * toucher sur la case finale, au lieu de devoir taper case par case. */
    private var cheminsDisponibles: List<List<Pair<Int, Int>>> = emptyList()

    private var rafleEnCours = false
    private var pionQuiRafle: Pair<Int, Int>? = null

    /** Règle de la nulle : 25 coups sans prise ni déplacement de pion (non-dame). */
    private var compteurCoupsNuls = 0
    private val seuilNulle = 25

    private val handler = Handler(Looper.getMainLooper())
    /** Délai entre deux sauts d'une même rafle jouée automatiquement, pour
     * que l'enchaînement reste lisible (même valeur que côté JS/3D). */
    private val delaiEntreSauts = 130L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        boardView = BoardView(this)
        setContentView(boardView)

        plateau = MoteurJeu.plateauInitial()
        boardView.plateau = plateau

        boardView.onCaseTouchee = { x, z -> onCaseTouchee(x, z) }

        Toast.makeText(this, "Tour des Noirs", Toast.LENGTH_SHORT).show()
    }

    private fun onCaseTouchee(x: Int, z: Int) {
        // --- Cas 1 et 2 : une pièce est sélectionnée (au repos ou en pleine rafle).
        // On cherche la case touchée dans l'arbre COMPLET des chemins possibles,
        // pas seulement dans le 1er saut : si elle s'y trouve, même plusieurs
        // sauts plus loin, on rejoue automatiquement toute la séquence qui y mène. ---
        val depart = if (rafleEnCours) pionQuiRafle else selection
        if (depart != null) {
            val chemin = cheminsDisponibles.firstOrNull { c -> c.any { it.first == x && it.second == z } }
            if (chemin != null) {
                val idx = chemin.indexOfFirst { it.first == x && it.second == z }
                jouerSequence(depart, chemin.subList(0, idx + 1))
                return
            }
            if (rafleEnCours) {
                // Toucher invalide pendant une rafle : on ignore (on ne peut pas
                // annuler une rafle en cours).
                return
            }
            // Sinon (simple sélection, pas de rafle) : on tombe dans le Cas 3
            // ci-dessous, qui permet de changer de sélection.
        }

        // --- Cas 3 : on touche une pièce de la couleur au trait -> sélection ---
        val valeur = plateau.getOrElse(x * 10 + z) { -1 }.toInt()
        val estBlanc = valeur == 0 || valeur == 1
        val estNoir = valeur == 2 || valeur == 3
        val couleurPiece = when {
            estBlanc -> MoteurJeu.BLANC
            estNoir -> MoteurJeu.NOIR
            else -> -1
        }

        if (couleurPiece == couleurActuelle) {
            // Coups légaux du joueur, prise obligatoire/maximale déjà appliquées par le moteur.
            val tousLesCoups = MoteurJeu.coupsPour(plateau, couleurActuelle)
            val coupsDepuisCettePiece = tousLesCoups.filter { it.x1 == x && it.z1 == z }
            if (coupsDepuisCettePiece.isEmpty()) {
                // Cette pièce existe mais n'a aucun coup légal. Si c'est parce
                // qu'une prise est obligatoire ailleurs sur le plateau, on le
                // signale par un flash rouge (équivalent de surlignerErreur côté JS).
                val priseObligatoire = tousLesCoups.any { it.prise }
                if (priseObligatoire) {
                    boardView.flashErreur(x, z)
                }
                effacerSelection()
                return
            }
            selection = x to z
            coupsDepuisSelection = coupsDepuisCettePiece
            cheminsDisponibles = construireChemins(plateau, x, z, coupsDepuisCettePiece)
            boardView.selection = selection
            boardView.casesSurlignees = cheminsDisponibles.flatten().distinct()
        } else {
            // Case vide ou pièce adverse touchée sans sélection active : désélection.
            effacerSelection()
        }
    }

    private fun effacerSelection() {
        selection = null
        coupsDepuisSelection = emptyList()
        cheminsDisponibles = emptyList()
        boardView.selection = null
        boardView.casesSurlignees = emptyList()
    }

    /** Explore récursivement, via le moteur natif (aucune règle réécrite ici :
     * on ne fait qu'appeler coupsPour/jouerCoup), tous les chemins complets de
     * rafle accessibles depuis (x1, z1) sur [plateauDepart]. Un coup sans
     * prise est un chemin d'un seul saut. Pour un coup de prise, jouerCoup ne
     * modifie pas [plateauDepart] (il renvoie un nouveau plateau) : on peut
     * donc simuler chaque branche sans toucher à l'état réel de la partie,
     * puis continuer récursivement depuis "suite" jusqu'à ce qu'il n'y ait
     * plus de prise possible. */
    private fun construireChemins(
        plateauDepart: ByteArray,
        x1: Int, z1: Int,
        coups: List<CoupLegal>
    ): List<List<Pair<Int, Int>>> {
        val chemins = mutableListOf<List<Pair<Int, Int>>>()
        for (coup in coups) {
            if (!coup.prise) {
                chemins.add(listOf(coup.x2 to coup.z2))
                continue
            }
            val resultat = MoteurJeu.jouerCoup(plateauDepart, x1, z1, coup.x2, coup.z2)
            if (resultat.erreur) continue
            if (resultat.suite.isEmpty()) {
                chemins.add(listOf(coup.x2 to coup.z2))
            } else {
                val suiteCoups = resultat.suite.map { (sx, sz) ->
                    CoupLegal(x1 = coup.x2, z1 = coup.z2, x2 = sx, z2 = sz, prise = true, nbPrises = 0)
                }
                val sousChemins = construireChemins(resultat.plateau, coup.x2, coup.z2, suiteCoups)
                for (sc in sousChemins) chemins.add(listOf(coup.x2 to coup.z2) + sc)
            }
        }
        return chemins
    }

    /** Rejoue automatiquement une séquence de sauts (une rafle entière, ou le
     * bout de chemin menant à la case touchée), un saut réel à la fois via
     * jouer(), avec un léger délai entre chaque pour que l'enchaînement reste
     * visible à l'écran plutôt qu'instantané. */
    private fun jouerSequence(depart: Pair<Int, Int>, sauts: List<Pair<Int, Int>>) {
        if (sauts.isEmpty()) return
        var xActuel = depart.first
        var zActuel = depart.second

        fun etape(i: Int) {
            val (x2, z2) = sauts[i]
            jouer(xActuel, zActuel, x2, z2)
            xActuel = x2; zActuel = z2
            if (i + 1 < sauts.size) {
                handler.postDelayed({ etape(i + 1) }, delaiEntreSauts)
            }
        }
        etape(0)
    }

    private fun jouer(x1: Int, z1: Int, x2: Int, z2: Int) {
        val pieceEtaitDame = plateau.getOrElse(x1 * 10 + z1) { -1 }.toInt().let { it == 1 || it == 3 }

        val resultat = MoteurJeu.jouerCoup(plateau, x1, z1, x2, z2)
        if (resultat.erreur) return // ne devrait pas arriver : on ne propose que des coups déjà validés par le moteur

        plateau = resultat.plateau
        boardView.plateau = plateau

        // Règle de la nulle : reset sur prise ou déplacement de pion (non-dame).
        compteurCoupsNuls = if (resultat.prise || !pieceEtaitDame) 0 else compteurCoupsNuls + 1

        if (resultat.suite.isNotEmpty()) {
            // La rafle continue avec la même pièce, depuis sa nouvelle case.
            rafleEnCours = true
            pionQuiRafle = x2 to z2
            selection = x2 to z2
            coupsDepuisSelection = resultat.suite.map { (sx2, sz2) ->
                CoupLegal(x1 = x2, z1 = z2, x2 = sx2, z2 = sz2, prise = true, nbPrises = 0)
            }
            // Rafraîchit l'arbre des chemins restants depuis la nouvelle case,
            // pour qu'un toucher plus loin dans la rafle continue à fonctionner.
            cheminsDisponibles = construireChemins(plateau, x2, z2, coupsDepuisSelection)
            boardView.selection = selection
            boardView.casesSurlignees = cheminsDisponibles.flatten().distinct()
            return
        }

        // Rafle (ou coup simple) terminée : fin de tour.
        rafleEnCours = false
        pionQuiRafle = null
        effacerSelection()

        if (resultat.devientDame) {
            Toast.makeText(this, "Promotion en dame !", Toast.LENGTH_SHORT).show()
        }

        if (compteurCoupsNuls >= seuilNulle) {
            Toast.makeText(this, "Partie nulle (25 coups sans prise)", Toast.LENGTH_LONG).show()
            return
        }

        couleurActuelle = if (couleurActuelle == MoteurJeu.NOIR) MoteurJeu.BLANC else MoteurJeu.NOIR

        // Fin de partie : plus aucun coup légal pour le joueur au trait.
        if (MoteurJeu.coupsPour(plateau, couleurActuelle).isEmpty()) {
            val gagnant = if (couleurActuelle == MoteurJeu.NOIR) "Blancs" else "Noirs"
            Toast.makeText(this, "$gagnant gagnent !", Toast.LENGTH_LONG).show()
            return
        }

        val nomCouleur = if (couleurActuelle == MoteurJeu.NOIR) "Noirs" else "Blancs"
        Toast.makeText(this, "Tour des $nomCouleur", Toast.LENGTH_SHORT).show()
    }
}
