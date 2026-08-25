package com.yannick.damesnative

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
    private lateinit var menuPrincipal: MenuPrincipalView
    private lateinit var parametresView: ParametresView

    /** Mode de partie choisi au menu : "humain", "faible", "moyen", "normal"
     * ou "expert" (miroir de la variable modeJeu côté JS). L'IA elle-même
     * (JNI + coroutines) n'est pas encore branchée : les modes IA lancent
     * pour l'instant une partie humain contre humain, en attendant. */
    private var modeJeu: String = "humain"

    private var plateau: ByteArray = ByteArray(100)
    private var couleurActuelle: Int = MoteurJeu.BLANC // les blancs commencent par défaut, comme côté JS (joueurActuel = 'blanc')

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

    /** Nombre de tentatives consécutives de jouer un pion sans coup légal
     * alors qu'une prise est obligatoire ailleurs. Au-delà du seuil, on
     * affiche un message explicite (miroir de compteurErreurs /
     * SEUIL_ERREUR_PRISE côté JS). Remise à 0 dès qu'une sélection réussit
     * ou que le message est fermé. */
    private var compteurErreurs = 0
    private val seuilErreurPrise = 6

    /** Règle de la nulle : 25 coups sans prise ni déplacement de pion (non-dame). */
    private var compteurCoupsNuls = 0
    private val seuilNulle = 25

    private val handler = Handler(Looper.getMainLooper())
    /** Petite pause de lisibilité entre deux sauts d'une même rafle jouée
     * automatiquement. Avant l'animation réelle du pion (glissement + saut),
     * ce délai devait à lui seul faire tout le travail visuel (400 ms, sinon
     * l'enchaînement paraissait instantané). Maintenant que animerCoup() sur
     * BoardView anime vraiment chaque saut (~160-450 ms selon la distance),
     * on n'a plus besoin que d'une courte respiration entre deux sauts pour
     * que la chaîne reste lisible à l'œil, plutôt que de tout miser sur ce
     * délai (miroir du petit gap de 130 ms entre captures côté JS).
     */
    private val pauseEntreSauts = 180L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Racine commune : le plateau (et bientôt l'écran d'accueil) en dessous,
        // le splash par-dessus le temps de son animation — miroir du z-index
        // 9999 de #splashScreen côté JS, qui recouvre #menuPrincipal au démarrage.
        val racine = FrameLayout(this)

        boardView = BoardView(this)
        // TODO(UI) : le plateau est en plein écran ici. Côté JS (index.html),
        // le plateau est CENTRÉ à l'écran pour laisser la place aux boutons
        // quitter/nouvelle partie et aux avatars. À corriger à l'étape "UI de
        // la partie" (avec le clavier initiales, les modales et les confettis) :
        // remplacer par un layout qui centre boardView et réserve l'espace
        // autour, au lieu de boardView seul en plein écran.
        // Caché tant qu'aucune partie n'est lancée (miroir de
        // renderer.domElement.style.display = 'none' au menu côté JS).
        boardView.visibility = android.view.View.GONE
        racine.addView(boardView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        plateau = MoteurJeu.plateauInitial()
        boardView.plateau = plateau
        boardView.onCaseTouchee = { x, z -> onCaseTouchee(x, z) }

        menuPrincipal = MenuPrincipalView(this)
        menuPrincipal.onModeChoisi = { mode -> demarrerJeu(mode) }
        menuPrincipal.onParametres = { parametresView.ouvrir() }
        racine.addView(menuPrincipal, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Par-dessus le menu : caché tant qu'on n'a pas appuyé sur "⚙ Paramètres"
        // (miroir de #ecranParametres, invisible/opacity 0 par défaut côté JS).
        parametresView = ParametresView(this)
        parametresView.onFermer = { /* déjà masqué par ParametresView elle-même ; rien à faire de plus ici */ }
        racine.addView(parametresView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val splash = SplashView(this)
        racine.addView(splash, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        setContentView(racine)

        splash.demarrer {
            // Écran suivant révélé pendant que le splash termine son fondu :
            // le menu principal, déjà en dessous, apparaît simplement en
            // devenant visible (miroir de menu-visible ajouté côté JS).
        }
    }

    /** Lance une partie depuis le menu (miroir de demarrerJeu(mode) côté JS). */
    private fun demarrerJeu(mode: String) {
        modeJeu = mode

        if (mode != "humain") {
            // L'IA (JNI + coroutines) n'est pas encore branchée : on lance
            // quand même une partie jouable, en pass-and-play, plutôt que de
            // bloquer le bouton. À remplacer dès l'étape "IA" de la roadmap.
            Toast.makeText(this, "IA pas encore branchée — mode 2 joueurs en attendant", Toast.LENGTH_SHORT).show()
        }

        annulerTimersEnAttente()
        plateau = MoteurJeu.plateauInitial()
        couleurActuelle = MoteurJeu.BLANC
        compteurErreurs = 0
        compteurCoupsNuls = 0
        rafleEnCours = false
        pionQuiRafle = null
        effacerSelection()
        boardView.plateau = plateau

        menuPrincipal.visibility = android.view.View.GONE
        boardView.visibility = android.view.View.VISIBLE
    }

    /** Retour au menu (miroir de retourMenu() côté JS). Déclenché pour
     * l'instant par le bouton retour matériel/geste Android : le vrai bouton
     * "quitter" à l'écran, comme côté JS, viendra avec l'étape "UI de
     * partie" (plateau centré, boutons quitter/nouvelle partie). */
    private fun retourMenu() {
        annulerTimersEnAttente()
        modeJeu = "humain"
        effacerSelection()
        rafleEnCours = false
        pionQuiRafle = null

        boardView.visibility = android.view.View.GONE
        menuPrincipal.visibility = android.view.View.VISIBLE
    }

    override fun onBackPressed() {
        if (parametresView.visibility == android.view.View.VISIBLE) {
            // Miroir de clicRetourParametres() côté JS : d'abord remonter du
            // sous-écran (avatars/règles) au hub, puis seulement fermer.
            parametresView.retourMateriel()
        } else if (boardView.visibility == android.view.View.VISIBLE) {
            retourMenu()
        } else {
            super.onBackPressed()
        }
    }

    /** Annule toute étape de rafle automatique encore programmée (jouerSequence
     * via handler.postDelayed), pour ne pas laisser un ancien coup se jouer
     * tout seul après un retour au menu ou un nouveau lancement de partie
     * (miroir de annulerTousLesTimersJeu() côté JS). */
    private fun annulerTimersEnAttente() {
        handler.removeCallbacksAndMessages(null)
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
                // Cette pièce existe mais n'a aucun coup légal — que ce soit
                // parce qu'elle est bloquée (entourée, coincée en bord de
                // plateau...) ou parce qu'une prise est obligatoire ailleurs.
                // Dans les deux cas on le signale par un flash rouge
                // (équivalent de surlignerErreur côté JS, appelé dès que
                // coups.length === 0, indépendamment de la prise obligatoire).
                boardView.flashErreur(x, z)

                // La prise obligatoire, elle, déclenche EN PLUS un compteur :
                // au bout de 6 tentatives sur un pion sans prise possible, un
                // message explicite s'affiche (miroir exact du JS : le flash
                // rouge seul ne suffit pas toujours à comprendre pourquoi le
                // pion ne bouge pas).
                val priseObligatoire = tousLesCoups.any { it.prise }
                if (priseObligatoire) {
                    compteurErreurs++
                    if (compteurErreurs >= seuilErreurPrise) {
                        afficherMessagePriseObligatoire()
                    }
                } else {
                    compteurErreurs = 0
                }
                effacerSelection()
                return
            }
            compteurErreurs = 0
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

    /** Message "Prise obligatoire" affiché après plusieurs tentatives infructueuses
     * sur un pion sans coup légal alors qu'une capture est obligatoire ailleurs.
     * Miroir de positionnerMessageObligatoire()/le div #messageObligatoire côté JS.
     * NOTE : côté JS, ce message tourne à 180° quand c'est au tour des Noirs en
     * pass-and-play (positionnerMessageObligatoire). Cette rotation n'est pas
     * encore portée ici — elle fait partie de l'étape "porter l'UI" prévue plus
     * tard, en même temps que le reste de l'UI orientée-joueur (badges, modales). */
    private fun afficherMessagePriseObligatoire() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Prise obligatoire")
            .setMessage("La règle impose que vous devez capturer un pion adverse.")
            .setPositiveButton("Compris") { dialog, _ ->
                compteurErreurs = 0
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
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
     * jouer(). Chaque étape n'est lancée qu'une fois l'animation du saut
     * précédent VRAIMENT terminée (callback apresAnimation de jouer()), avec
     * une courte pause de lisibilité entre les deux, au lieu d'un délai fixe
     * qui ne tenait pas compte du temps réel de l'animation. */
    private fun jouerSequence(depart: Pair<Int, Int>, sauts: List<Pair<Int, Int>>) {
        if (sauts.isEmpty()) return
        var xActuel = depart.first
        var zActuel = depart.second

        fun etape(i: Int) {
            val (x2, z2) = sauts[i]
            jouer(xActuel, zActuel, x2, z2) {
                xActuel = x2; zActuel = z2
                if (i + 1 < sauts.size) {
                    handler.postDelayed({ etape(i + 1) }, pauseEntreSauts)
                }
            }
        }
        etape(0)
    }

    /** Joue un saut (x1,z1) -> (x2,z2) déjà validé par le moteur, en
     * l'animant réellement sur BoardView (glissement, ou saut par-dessus la
     * pièce capturée qui s'efface progressivement). Toute la logique qui
     * dépendait auparavant du résultat immédiat de jouerCoup (règle de la
     * nulle, suite de rafle, promotion, fin de tour/partie) est maintenant
     * appliquée dans le callback de fin d'animation, pour qu'elle ne
     * s'exécute jamais avant que le joueur ait vu le coup se jouer à
     * l'écran — miroir de terminerLogiqueCoup() côté JS. [apresAnimation]
     * est appelé une fois tout ce traitement terminé, pour que jouerSequence
     * puisse enchaîner sur le saut suivant au bon moment. */
    private fun jouer(x1: Int, z1: Int, x2: Int, z2: Int, apresAnimation: () -> Unit = {}) {
        val pieceEtaitDame = plateau.getOrElse(x1 * 10 + z1) { -1 }.toInt().let { it == 1 || it == 3 }

        val resultat = MoteurJeu.jouerCoup(plateau, x1, z1, x2, z2)
        if (resultat.erreur) { // ne devrait pas arriver : on ne propose que des coups déjà validés par le moteur
            apresAnimation()
            return
        }

        boardView.animerCoup(
            x1, z1, x2, z2,
            estPrise = resultat.prise, xPris = resultat.px, zPris = resultat.pz,
            plateauApres = resultat.plateau
        ) {
            plateau = resultat.plateau

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
                apresAnimation()
                return@animerCoup
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
                apresAnimation()
                return@animerCoup
            }

            couleurActuelle = if (couleurActuelle == MoteurJeu.NOIR) MoteurJeu.BLANC else MoteurJeu.NOIR

            // Fin de partie : plus aucun coup légal pour le joueur au trait.
            if (MoteurJeu.coupsPour(plateau, couleurActuelle).isEmpty()) {
                val gagnant = if (couleurActuelle == MoteurJeu.NOIR) "Blancs" else "Noirs"
                Toast.makeText(this, "$gagnant gagnent !", Toast.LENGTH_LONG).show()
                apresAnimation()
                return@animerCoup
            }

            val nomCouleur = if (couleurActuelle == MoteurJeu.NOIR) "Noirs" else "Blancs"
            Toast.makeText(this, "Tour des $nomCouleur", Toast.LENGTH_SHORT).show()
            apresAnimation()
        }
    }
}
