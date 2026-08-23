package com.yannick.damesnative

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Affichage 2D du plateau (10x10) + interaction tactile.
 * Vue "bête" : elle ne connaît aucune règle du jeu, elle se contente
 * d'afficher l'état qu'on lui donne et de remonter les cases touchées
 * via onCaseTouchee. Toute la logique (sélection, coups légaux, rafle)
 * vit dans MainActivity / MoteurJeu (C natif).
 *
 * Convention (identique à moteur.c) : indice plat = x * 10 + z,
 * où x = colonne (0-9), z = ligne (0-9).
 */
class BoardView(context: Context) : View(context) {

    var plateau: ByteArray = ByteArray(100)
        set(value) { field = value; invalidate() }

    /** Case actuellement sélectionnée (x, z), ou null. */
    var selection: Pair<Int, Int>? = null
        set(value) { field = value; invalidate() }

    /** Cases vers lesquelles la pièce sélectionnée peut se déplacer. */
    var casesSurlignees: List<Pair<Int, Int>> = emptyList()
        set(value) { field = value; invalidate() }

    /** Case flashée en rouge (x, z), ou null. Utilisé quand on touche un pion
     * qui n'a aucun coup légal alors qu'une prise est obligatoire ailleurs
     * sur le plateau (équivalent de surlignerErreur côté JS). */
    private var caseErreur: Pair<Int, Int>? = null
        set(value) { field = value; invalidate() }

    var onCaseTouchee: ((x: Int, z: Int) -> Unit)? = null

    private val paintCaseClaire = Paint().apply { color = Color.parseColor("#E8D0AA") }
    private val paintCaseFoncee = Paint().apply { color = Color.parseColor("#8B5A2B") }
    private val paintSelection = Paint().apply { color = Color.parseColor("#4CAF50") }
    private val paintSurbrillance = Paint().apply { color = Color.parseColor("#81C78466") } // semi-transparent
    private val paintErreur = Paint().apply { color = Color.parseColor("#FF3B30") }
    private val paintPionBlanc = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val paintPionNoir = Paint().apply { color = Color.DKGRAY; isAntiAlias = true }
    private val paintDameAnneau = Paint().apply {
        color = Color.parseColor("#FFD700"); style = Paint.Style.STROKE; strokeWidth = 6f; isAntiAlias = true
    }
    // Légère ombre sous la pièce en vol, pour donner une sensation de hauteur
    // pendant le saut de capture (repère visuel simple, pas de vraie 3D ici).
    private val paintOmbre = Paint().apply { color = Color.parseColor("#33000000"); isAntiAlias = true }

    /**
     * Décrit une animation de déplacement en cours (glissement simple, ou
     * saut par-dessus la pièce capturée). Miroir simplifié en 2D de
     * pionEnMouvement / terminerLogiqueCoup côté JS : le plateau logique
     * n'avance qu'à la toute fin de l'animation (voir animerCoup ci-dessous).
     */
    private data class AnimationCoup(
        val xDepart: Int, val zDepart: Int,
        val xArrivee: Int, val zArrivee: Int,
        val valeurPiece: Int,
        val estPrise: Boolean,
        val xPris: Int, val zPris: Int,
        val valeurPiecePrise: Int,
        var progress: Float = 0f
    )

    private var animationActuelle: AnimationCoup? = null
    private var animator: ValueAnimator? = null

    /** Vrai tant qu'une animation de déplacement est en cours. Utilisé pour
     * ignorer les touches pendant qu'une pièce est "en vol" (on ne peut pas
     * changer de sélection ni rejouer par-dessus un mouvement pas terminé). */
    val enAnimation: Boolean get() = animationActuelle != null

    private fun taille(): Float = width.coerceAtMost(height) / 10f

    /** Flashe la case (x, z) en rouge pendant 300 ms, puis l'efface. Appelée
     * quand un pion touché n'a aucun coup légal alors qu'une prise est
     * obligatoire ailleurs sur le plateau. */
    fun flashErreur(x: Int, z: Int) {
        caseErreur = x to z
        postDelayed({ caseErreur = null }, 300)
    }

    /**
     * Anime un déplacement de pièce depuis (x1,z1) vers (x2,z2) — glissement
     * fluide avec accélération/décélération, et en cas de prise, un arc de
     * saut par-dessus la case (xPris,zPris) dont la pièce s'efface
     * progressivement (comme la pièce disparaît une fois "mangée").
     *
     * Le [plateau] affiché pendant l'animation reste celui d'AVANT le coup
     * (moins la pièce en vol, qui est dessinée à part) ; ce n'est qu'à la
     * fin de l'animation que [plateauApres] devient l'état réel affiché,
     * puis [onFin] est appelé — miroir de terminerLogiqueCoup() côté JS, qui
     * n'applique la logique de suite/fin de tour qu'après la fin du
     * mouvement visuel, jamais avant.
     */
    fun animerCoup(
        x1: Int, z1: Int, x2: Int, z2: Int,
        estPrise: Boolean, xPris: Int, zPris: Int,
        plateauApres: ByteArray,
        onFin: () -> Unit
    ) {
        val valeurPiece = plateau.getOrElse(x1 * 10 + z1) { -1 }.toInt()
        val valeurPiecePrise = if (estPrise) plateau.getOrElse(xPris * 10 + zPris) { -1 }.toInt() else -1

        val dx = (x2 - x1).toFloat()
        val dz = (z2 - z1).toFloat()
        val distanceCases = sqrt(dx * dx + dz * dz)
        // Mêmes constantes que côté JS (vitesseUnitesParSeconde / DUREE_MIN /
        // DUREE_MAX dans animer()) : vitesse constante en cases/seconde,
        // bornée pour qu'un saut d'une case ne soit jamais trop brusque, et
        // qu'une longue glissade de dame ne traîne pas trop en longueur.
        val VITESSE_CASES_PAR_SEC = 4.2f
        val DUREE_MIN = 0.16f
        val DUREE_MAX = 0.45f
        val dureeSec = (distanceCases / VITESSE_CASES_PAR_SEC).coerceIn(DUREE_MIN, DUREE_MAX)

        val anim = AnimationCoup(
            xDepart = x1, zDepart = z1, xArrivee = x2, zArrivee = z2,
            valeurPiece = valeurPiece, estPrise = estPrise,
            xPris = xPris, zPris = zPris, valeurPiecePrise = valeurPiecePrise
        )
        animationActuelle = anim

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (dureeSec * 1000).toLong()
            addUpdateListener { va ->
                anim.progress = va.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animationActuelle = null
                    plateau = plateauApres // déclenche invalidate() via le setter
                    onFin()
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = taille()
        val anim = animationActuelle

        for (row in 0 until 10) {      // z
            for (col in 0 until 10) {  // x
                val x = col * t
                val y = row * t

                val estSelectionnee = selection?.let { it.first == col && it.second == row } == true
                val estSurlignee = casesSurlignees.any { it.first == col && it.second == row }
                val estErreur = caseErreur?.let { it.first == col && it.second == row } == true

                val paintCase = when {
                    estErreur -> paintErreur
                    estSelectionnee -> paintSelection
                    (row + col) % 2 == 0 -> paintCaseClaire
                    else -> paintCaseFoncee
                }
                canvas.drawRect(x, y, x + t, y + t, paintCase)
                if (estSurlignee) canvas.drawRect(x, y, x + t, y + t, paintSurbrillance)

                val estDepartAnime = anim != null && anim.xDepart == col && anim.zDepart == row
                val estPriseAnimee = anim != null && anim.estPrise && anim.xPris == col && anim.zPris == row

                when {
                    // La pièce qui part de cette case est en vol : dessinée
                    // séparément par-dessus tout le plateau, plus bas.
                    estDepartAnime -> {}
                    // La pièce capturée s'efface progressivement le temps que
                    // le sauteur passe par-dessus, au lieu de disparaître d'un
                    // coup dès le début du saut.
                    estPriseAnimee -> dessinerPionCapture(canvas, x, y, t, anim!!)
                    else -> {
                        val valeur = plateau.getOrElse(col * 10 + row) { -1 }.toInt()
                        dessinerPion(canvas, x, y, t, valeur)
                    }
                }
            }
        }

        anim?.let { dessinerPionEnVol(canvas, t, it) }
    }

    /** Dessine un pion (et son anneau de dame) au point (x,y) = coin
     * haut-gauche de sa case, avec transparence/échelle/décalage vertical
     * optionnels pour les besoins de l'animation. */
    private fun dessinerPion(
        canvas: Canvas, x: Float, y: Float, t: Float, valeur: Int,
        alpha: Int = 255, echelle: Float = 1f, decalageY: Float = 0f
    ) {
        if (valeur !in 0..3) return
        val estBlanc = valeur == 0 || valeur == 1
        val estDame = valeur == 1 || valeur == 3
        val cx = x + t / 2
        val cy = y + t / 2 + decalageY

        if (decalageY < -0.01f) {
            // Petite ombre au sol pendant le saut, à l'emplacement réel de la
            // case (sans le décalage), pour ancrer visuellement la hauteur.
            canvas.drawOval(
                cx - t * 0.32f, y + t * 0.62f,
                cx + t * 0.32f, y + t * 0.82f,
                paintOmbre
            )
        }

        val paintPion = if (estBlanc) paintPionBlanc else paintPionNoir
        val alphaAvant = paintPion.alpha
        paintPion.alpha = alpha
        canvas.drawCircle(cx, cy, t * 0.4f * echelle, paintPion)
        paintPion.alpha = alphaAvant

        if (estDame) {
            val alphaAnneauAvant = paintDameAnneau.alpha
            paintDameAnneau.alpha = alpha
            canvas.drawCircle(cx, cy, t * 0.26f * echelle, paintDameAnneau)
            paintDameAnneau.alpha = alphaAnneauAvant
        }
    }

    /** Pièce capturée en train de disparaître : reste bien visible pendant le
     * début du saut, puis s'efface (fondu + léger rétrécissement) pile au
     * moment où le sauteur passe au-dessus d'elle. */
    private fun dessinerPionCapture(canvas: Canvas, x: Float, y: Float, t: Float, anim: AnimationCoup) {
        val debut = 0.3f
        val fin = 0.85f
        val p = anim.progress
        val ratio = when {
            p <= debut -> 1f
            p >= fin -> 0f
            else -> 1f - (p - debut) / (fin - debut)
        }
        if (ratio <= 0f) return
        val alpha = (ratio * 255).toInt().coerceIn(0, 255)
        val echelle = 0.55f + 0.45f * ratio
        dessinerPion(canvas, x, y, t, anim.valeurPiecePrise, alpha = alpha, echelle = echelle)
    }

    /** Pièce en cours de déplacement, dessinée par-dessus tout le reste du
     * plateau à sa position interpolée. */
    private fun dessinerPionEnVol(canvas: Canvas, t: Float, anim: AnimationCoup) {
        // Easing asymétrique "élan physique" (identique à tLisse côté JS) :
        // démarrage franc, arrivée plus douce.
        val unMoinsP = 1f - anim.progress
        val tLisse = 1f - unMoinsP * unMoinsP * unMoinsP

        val xDepartPx = anim.xDepart * t
        val zDepartPx = anim.zDepart * t
        val xArriveePx = anim.xArrivee * t
        val zArriveePx = anim.zArrivee * t
        val x = xDepartPx + (xArriveePx - xDepartPx) * tLisse
        val y = zDepartPx + (zArriveePx - zDepartPx) * tLisse

        val decalageY = if (anim.estPrise) {
            // Arc de saut (parabole) par-dessus la pièce capturée.
            -(t * 0.24f) * 4f * tLisse * (1f - tLisse)
        } else {
            // Léger effet de glissement/élévation, comme le sin() côté JS.
            -(t * 0.05f) * sin((tLisse * PI).toFloat())
        }

        dessinerPion(canvas, x, y, t, anim.valeurPiece, decalageY = decalageY)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            // On ignore les touches tant qu'une pièce est en plein
            // déplacement animé : pas de nouvelle sélection ni de coup
            // par-dessus un mouvement pas encore terminé visuellement.
            if (enAnimation) return true

            val t = taille()
            val col = (event.x / t).toInt() // x
            val row = (event.y / t).toInt() // z
            if (col in 0 until 10 && row in 0 until 10) {
                onCaseTouchee?.invoke(col, row)
            }
            return true
        }
        return super.onTouchEvent(event)
    }
}
