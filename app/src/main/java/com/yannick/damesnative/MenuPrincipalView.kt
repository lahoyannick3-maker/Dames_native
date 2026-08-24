package com.yannick.damesnative

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.hypot
import kotlin.math.min

/**
 * Écran d'accueil, portage fidèle de #menuPrincipal dans index.html :
 *  - carte centrée (glassmorphism : fond semi-transparent, bord clair fin,
 *    coins très arrondis) sur fond radial sombre ;
 *  - titre "DAME GAME PLAY" tricolore + sous-titre ;
 *  - bouton "Deux joueurs" (humain) puis séparateur "Jouer contre l'IA"
 *    puis les 4 boutons de difficulté (Faible/Moyen/Normal/Expert) ;
 *  - lien "⚙ Paramètres" en pastille discrète.
 *
 * NOTE fidélité : pas de vrai flou (`backdrop-filter: blur`) ici — Android
 * ne l'offre nativement qu'à partir de l'API 31, et l'appareil cible (Go
 * Edition, 2 Go RAM) n'a pas intérêt à payer ce coût. Un fond semi-transparent
 * uni donne un résultat visuellement très proche. Le dégradé radial du fond
 * (`radial-gradient(circle at 50% 20%, ...)`) est approché avec
 * GradientDrawable en RADIAL, centré aux mêmes coordonnées relatives.
 *
 * Callbacks à brancher depuis MainActivity :
 *  - [onModeChoisi] reçoit "humain", "faible", "moyen", "normal" ou "expert"
 *    (mêmes valeurs que l'argument de demarrerJeu(mode) côté JS).
 *  - [onParametres] correspond à ouvrirParametres() côté JS — pas encore
 *    porté (prochaine étape UI), donc pour l'instant MainActivity peut n'y
 *    mettre qu'un simple message d'attente.
 */
class MenuPrincipalView(context: Context) : FrameLayout(context) {

    companion object {
        private val COULEUR_FOND_1 = 0xFF1E2640.toInt()
        private val COULEUR_FOND_2 = 0xFF0C0F17.toInt()
        private val COULEUR_FOND_3 = 0xFF05060A.toInt()

        private val COULEUR_MOT1 = 0xFFFF8C00.toInt() // DAME (orange)
        private val COULEUR_MOT2 = 0xFFFFFFFF.toInt() // GAME (blanc)
        private val COULEUR_MOT3 = 0xFF008000.toInt() // PLAY (vert)

        private const val COULEUR_CARTE_FOND = 0x8C141223 // rgba(20,18,35,0.55)
        private const val COULEUR_CARTE_BORD = 0x1FFFFFFF // rgba(255,255,255,0.12)
        private const val COULEUR_SOUS_TITRE = 0xA6FFFFFF.toInt() // rgba(255,255,255,0.65)
        private const val COULEUR_SEPARATEUR_TEXTE = 0x66FFFFFF // rgba(255,255,255,0.4)
        private const val COULEUR_SEPARATEUR_LIGNE = 0x26FFFFFF // rgba(255,255,255,0.15)
        private const val COULEUR_ICONE_FOND = 0x26FFFFFF // rgba(255,255,255,0.15)
        private const val COULEUR_PARAM_FOND = 0x0DFFFFFF // rgba(255,255,255,0.05)
        private const val COULEUR_PARAM_BORD = 0x14FFFFFF // rgba(255,255,255,0.08)
        private const val COULEUR_PARAM_TEXTE = 0xB2FFFFFF.toInt() // rgba(255,255,255,0.7)

        private const val LARGEUR_CARTE_MAX_DP = 390
    }

    /** Reçoit "humain", "faible", "moyen", "normal" ou "expert". */
    var onModeChoisi: ((String) -> Unit)? = null
    var onParametres: (() -> Unit)? = null

    private val fondRadial = GradientDrawable().apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT
        colors = intArrayOf(COULEUR_FOND_1, COULEUR_FOND_2, COULEUR_FOND_3)
        setGradientCenter(0.5f, 0.2f)
    }

    init {
        background = fondRadial

        val defilement = ScrollView(context).apply { isFillViewport = true }

        val conteneur = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(20), dp(12), dp(20))
        }

        conteneur.addView(construireCarte(context))
        defilement.addView(
            conteneur,
            ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.MATCH_PARENT)
        )

        addView(defilement, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Le rayon du dégradé radial doit couvrir tout l'écran depuis son
        // centre (50%, 20%) : on prend la plus grande distance possible
        // jusqu'à un coin, comme le ferait `circle` en CSS.
        if (w > 0 && h > 0) {
            val cx = w * 0.5f
            val cy = h * 0.2f
            val rayon = maxOf(
                hypot(cx.toDouble(), cy.toDouble()),
                hypot((w - cx).toDouble(), cy.toDouble()),
                hypot(cx.toDouble(), (h - cy).toDouble()),
                hypot((w - cx).toDouble(), (h - cy).toDouble())
            ).toFloat()
            fondRadial.gradientRadius = rayon
        }
    }

    private fun construireCarte(context: Context): LinearLayout {
        val largeurEcranDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val largeurCarteDp = min(largeurEcranDp - 24f, LARGEUR_CARTE_MAX_DP.toFloat())

        val carte = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(COULEUR_CARTE_FOND)
                cornerRadius = dp(32).toFloat()
                setStroke(dp(1), COULEUR_CARTE_BORD)
            }
            setPadding(dp(22), dp(32), dp(22), dp(32))
        }

        carte.addView(construireTitre(context))
        carte.addView(construireSousTitre(context).apply {
            (layoutParams as? LinearLayout.LayoutParams)
        })

        carte.addView(
            creerBoutonMenu(context, "Deux joueurs", "👥", 0xFF0052D4.toInt(), 0xFF4364F7.toInt()) {
                onModeChoisi?.invoke("humain")
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        carte.addView(
            construireSeparateur(context, "Jouer contre l'IA"),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(20); bottomMargin = dp(14)
            }
        )

        val grilleIA = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val boutonsIA = listOf(
            Triple("IA Faible", "🤖", 0xFF059669.toInt() to 0xFF10B981.toInt()),
            Triple("IA Moyen", "🧠", 0xFFD97706.toInt() to 0xFFF59E0B.toInt()),
            Triple("IA Normal", "🔥", 0xFFEA580C.toInt() to 0xFFF97316.toInt()),
            Triple("IA Expert", "👑", 0xFFDC2626.toInt() to 0xFFEF4444.toInt())
        )
        val modesIA = listOf("faible", "moyen", "normal", "expert")
        boutonsIA.forEachIndexed { i, (texte, icone, couleurs) ->
            val (c1, c2) = couleurs
            val bouton = creerBoutonMenu(context, texte, icone, c1, c2) { onModeChoisi?.invoke(modesIA[i]) }
            grilleIA.addView(
                bouton,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    if (i > 0) topMargin = dp(10)
                }
            )
        }
        carte.addView(grilleIA)

        carte.addView(
            construirePastilleParametres(context),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(24)
            }
        )

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(carte, LinearLayout.LayoutParams(dp(largeurCarteDp.toInt()), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    /** Titre "DAME GAME PLAY" tricolore, une seule ligne, taille fluide
     * (miroir de clamp(1.35rem, 6.8vw, 2.5rem) côté CSS). */
    private fun construireTitre(context: Context): TextView {
        val largeurEcranDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val tailleSp = (largeurEcranDp * 0.068f).coerceIn(21.6f, 40f)

        val texte = "DAME GAME PLAY"
        val spannable = SpannableString(texte)
        fun colorer(mot: String, couleur: Int) {
            val debut = texte.indexOf(mot)
            spannable.setSpan(ForegroundColorSpan(couleur), debut, debut + mot.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        colorer("DAME", COULEUR_MOT1)
        colorer("GAME", COULEUR_MOT2)
        colorer("PLAY", COULEUR_MOT3)
        spannable.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, texte.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        return TextView(context).apply {
            text = spannable
            textSize = tailleSp
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
        }
    }

    private fun construireSousTitre(context: Context): TextView {
        val largeurEcranDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val tailleSp = (largeurEcranDp * 0.032f).coerceIn(11.2f, 13.6f)
        return TextView(context).apply {
            text = "LE JEU DE DAMES MODERNE"
            setTextColor(COULEUR_SOUS_TITRE)
            textSize = tailleSp
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            letterSpacing = 0.04f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(24))
        }
    }

    /** Un bouton du menu (humain ou IA) : badge icône + libellé, sur fond
     * dégradé (approximation du linear-gradient(135deg, c1, c2) CSS). */
    private fun creerBoutonMenu(
        context: Context, texte: String, icone: String,
        couleur1: Int, couleur2: Int, action: () -> Unit
    ): LinearLayout {
        val bouton = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(couleur1, couleur2)).apply {
                cornerRadius = dp(20).toFloat()
            }
            setPadding(dp(15), dp(12), dp(15), dp(12))
        }

        val badge = TextView(context).apply {
            text = icone
            textSize = 17f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(COULEUR_ICONE_FOND)
                cornerRadius = dp(12).toFloat()
            }
        }
        bouton.addView(badge, LinearLayout.LayoutParams(dp(34), dp(34)))

        val label = TextView(context).apply {
            text = texte
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        bouton.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(14)
        })

        // Léger effet d'appui, miroir de .btn-menu:active { transform: scale(0.98) }.
        bouton.setOnClickListener {
            it.animate().scaleX(0.98f).scaleY(0.98f).setDuration(60)
                .withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(80)
                        .setInterpolator(OvershootInterpolator()).start()
                }.start()
            action()
        }

        return bouton
    }

    /** Ligne "—— Jouer contre l'IA ——", miroir de .menu-separator. */
    private fun construireSeparateur(context: Context, texte: String): LinearLayout {
        fun ligne() = View(context).apply {
            setBackgroundColor(COULEUR_SEPARATEUR_LIGNE)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ligne(), LinearLayout.LayoutParams(0, dp(1), 1f))
            addView(TextView(context).apply {
                text = texte.uppercase()
                setTextColor(COULEUR_SEPARATEUR_TEXTE)
                textSize = 11.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                letterSpacing = 0.12f
                setPadding(dp(12), 0, dp(12), 0)
            })
            addView(ligne(), LinearLayout.LayoutParams(0, dp(1), 1f))
        }
    }

    /** Pastille "⚙ Paramètres", miroir de .menu-settings. */
    private fun construirePastilleParametres(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                setColor(COULEUR_PARAM_FOND)
                cornerRadius = dp(100).toFloat()
                setStroke(dp(1), COULEUR_PARAM_BORD)
            }
            setPadding(dp(22), dp(10), dp(22), dp(10))
            addView(TextView(context).apply {
                text = "⚙"
                setTextColor(COULEUR_PARAM_TEXTE)
                textSize = 14f
            })
            addView(TextView(context).apply {
                text = "  Paramètres"
                setTextColor(COULEUR_PARAM_TEXTE)
                textSize = 13.6f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            setOnClickListener { onParametres?.invoke() }
        }
    }

    private fun dp(valeur: Int): Int = (valeur * resources.displayMetrics.density).toInt()
}
