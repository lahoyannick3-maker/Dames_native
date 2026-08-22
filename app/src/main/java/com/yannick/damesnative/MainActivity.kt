package com.yannick.damesnative

import android.os.Bundle
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

    private var rafleEnCours = false
    private var pionQuiRafle: Pair<Int, Int>? = null

    /** Règle de la nulle : 25 coups sans prise ni déplacement de pion (non-dame). */
    private var compteurCoupsNuls = 0
    private val seuilNulle = 25

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
        // --- Cas 1 : une rafle est en cours, seule la pièce qui rafle peut continuer ---
        if (rafleEnCours) {
            val destinationValide = coupsDepuisSelection.any { it.x2 == x && it.z2 == z }
            if (destinationValide) {
                jouer(pionQuiRafle!!.first, pionQuiRafle!!.second, x, z)
            }
            // Sinon : on ignore le toucher (on ne peut pas annuler une rafle en cours).
            return
        }

        // --- Cas 2 : une case est déjà sélectionnée et on touche une destination valide ---
        val sel = selection
        if (sel != null) {
            val destinationValide = coupsDepuisSelection.any { it.x2 == x && it.z2 == z }
            if (destinationValide) {
                jouer(sel.first, sel.second, x, z)
                return
            }
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
                // Cette pièce existe mais n'a aucun coup légal (souvent : une prise est
                // obligatoire ailleurs sur le plateau). On ne sélectionne pas.
                selection = null
                coupsDepuisSelection = emptyList()
                boardView.selection = null
                boardView.casesSurlignees = emptyList()
                return
            }
            selection = x to z
            coupsDepuisSelection = coupsDepuisCettePiece
            boardView.selection = selection
            boardView.casesSurlignees = coupsDepuisCettePiece.map { it.x2 to it.z2 }
        } else {
            // Case vide ou pièce adverse touchée sans sélection active : désélection.
            selection = null
            coupsDepuisSelection = emptyList()
            boardView.selection = null
            boardView.casesSurlignees = emptyList()
        }
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
            boardView.selection = selection
            boardView.casesSurlignees = resultat.suite
            return
        }

        // Rafle (ou coup simple) terminée : fin de tour.
        rafleEnCours = false
        pionQuiRafle = null
        selection = null
        coupsDepuisSelection = emptyList()
        boardView.selection = null
        boardView.casesSurlignees = emptyList()

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
