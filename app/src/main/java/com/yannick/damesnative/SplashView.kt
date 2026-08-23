package com.yannick.damesnative

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Écran de démarrage, portage fidèle de #splashScreen / .splash-logo /
 * .splash-loader dans index.html :
 *  - logo "DAME GAME PLAY" en 3 mots colorés (orange/blanc/vert) qui
 *    apparaissent en cascade (fondu + léger glissement vers le haut,
 *    délais 0.2s / 0.5s / 0.8s, durée 0.6s chacun) ;
 *  - une fine barre de chargement qui se remplit en 1.8s (dégradé
 *    orange -> vert, courbe cubic-bezier(0.4,0,0.2,1)) ;
 *  - à 2.2s, fondu de sortie du splash sur 0.8s, l'écran suivant étant
 *    révélé une fois ce fondu presque terminé (2.2s + 0.75s), pour éviter
 *    deux animations floues simultanées sur un appareil d'entrée de gamme.
 * Ceci est le miroir exact des délais de gererSplashScreen() côté JS.
 *
 * NOTE fidélité : Android n'a pas d'équivalent direct des unités CSS
 * (vw, px de letter-spacing indépendants du zoom). Les tailles/espacements
 * ci-dessous sont donc des équivalents visuels raisonnables plutôt qu'une
 * conversion pixel-perfect ; les COULEURS et le TIMING, eux, sont exacts.
 */
class SplashView(context: Context) : FrameLayout(context) {

    companion object {
        private val COULEUR_FOND = 0xFF0F0C29.toInt()
        private val COULEUR_MOT1 = 0xFFFF8C00.toInt() // DAME (orange)
        private val COULEUR_MOT2 = 0xFFFFFFFF.toInt() // GAME (blanc)
        private val COULEUR_MOT3 = 0xFF008000.toInt() // PLAY (vert)
        private val COULEUR_LOADER_FOND = 0x1AFFFFFF // rgba(255,255,255,0.1)

        private const val DELAI_AVANT_FONDU = 2200L
        private const val DUREE_FONDU_SPLASH = 800L
        private const val DELAI_AVANT_SUITE = 750L // depuis le DÉBUT du fondu, pas depuis la fin

        private const val DUREE_MOT = 600L
        private val DELAIS_MOTS = longArrayOf(200L, 500L, 800L)

        private const val DUREE_LOADER = 1800L
        private const val LARGEUR_LOADER_DP = 150
        private const val HAUTEUR_LOADER_DP = 3
    }

    private val easeStandard = PathInterpolator(0.25f, 0.1f, 0.25f, 1f) // "ease" CSS par défaut
    private val easeLoader = PathInterpolator(0.4f, 0f, 0.2f, 1f) // cubic-bezier(0.4,0,0.2,1) du CSS

    private val mots: List<TextView>
    private val loaderBarre: View
    private var animateurLoader: ValueAnimator? = null

    init {
        setBackgroundColor(COULEUR_FOND)
        // Bloque les touches sous le splash tant qu'il est affiché, comme le
        // z-index 9999 côté web (rien en dessous n'est cliquable).
        isClickable = true
        isFocusable = true

        val conteneur = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val largeurEcranDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val grandEcran = largeurEcranDp >= 600f
        val tailleLogoSp = if (grandEcran) 56f else (largeurEcranDp * 0.08f).coerceIn(28f, 56f)
        val espacementLettresEm = if (grandEcran) 0.07f else 2f / tailleLogoSp

        val logoLigne = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val textesEtCouleurs = listOf("DAME" to COULEUR_MOT1, "GAME" to COULEUR_MOT2, "PLAY" to COULEUR_MOT3)
        mots = textesEtCouleurs.map { (texte, couleur) ->
            TextView(context).apply {
                text = texte
                setTextColor(couleur)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                textSize = tailleLogoSp
                letterSpacing = espacementLettresEm
                alpha = 0f
                translationY = dp(10)
            }
        }
        mots.forEachIndexed { i, mot ->
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (i > 0) lp.marginStart = dp(8).toInt()
            logoLigne.addView(mot, lp)
        }

        conteneur.addView(
            logoLigne,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(30).toInt()
            }
        )

        val loaderFond = View(context).apply {
            background = pilule(COULEUR_LOADER_FOND)
        }
        loaderBarre = View(context).apply {
            background = degradeLoader()
        }
        val loaderConteneur = FrameLayout(context)
        loaderConteneur.addView(loaderFond, LayoutParams(dp(LARGEUR_LOADER_DP).toInt(), dp(HAUTEUR_LOADER_DP).toInt()))
        loaderConteneur.addView(loaderBarre, LayoutParams(0, dp(HAUTEUR_LOADER_DP).toInt()))

        conteneur.addView(
            loaderConteneur,
            LinearLayout.LayoutParams(dp(LARGEUR_LOADER_DP).toInt(), dp(HAUTEUR_LOADER_DP).toInt())
        )

        addView(conteneur, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    private fun dp(valeur: Int): Float = valeur * resources.displayMetrics.density

    private fun pilule(couleur: Int): GradientDrawable = GradientDrawable().apply {
        setColor(couleur)
        cornerRadius = dp(10)
    }

    private fun degradeLoader(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(COULEUR_MOT1, COULEUR_MOT3) // #FF8C00 -> #008000, comme le linear-gradient CSS
    ).apply {
        cornerRadius = dp(10)
    }

    /**
     * Lance la séquence complète (cascade du logo, remplissage de la barre,
     * puis fondu de sortie) et appelle [surEcranSuivant] au moment où l'écran
     * suivant doit apparaître — c'est-à-dire pendant que le splash termine
     * encore son fondu, exactement comme côté JS (menu-visible ajouté avant
     * la fin de la transition CSS du splash).
     */
    fun demarrer(surEcranSuivant: () -> Unit) {
        mots.forEachIndexed { i, mot ->
            mot.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(DELAIS_MOTS[i])
                .setDuration(DUREE_MOT)
                .setInterpolator(easeStandard)
                .start()
        }

        loaderBarre.post {
            val largeurCible = dp(LARGEUR_LOADER_DP).toInt()
            val anim = ValueAnimator.ofInt(0, largeurCible)
            anim.duration = DUREE_LOADER
            anim.interpolator = easeLoader
            anim.addUpdateListener { va ->
                val lp = loaderBarre.layoutParams
                lp.width = va.animatedValue as Int
                loaderBarre.layoutParams = lp
            }
            animateurLoader = anim
            anim.start()
        }

        postDelayed({
            animate()
                .alpha(0f)
                .setDuration(DUREE_FONDU_SPLASH)
                .withEndAction { visibility = View.GONE }
                .start()

            postDelayed({ surEcranSuivant() }, DELAI_AVANT_SUITE)
        }, DELAI_AVANT_FONDU)
    }
}
