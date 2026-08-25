package com.yannick.damesnative

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.hypot
import kotlin.math.min

/**
 * Écran Paramètres, portage fidèle de #ecranParametres dans index.html :
 *  - carte glassmorphism sur le même fond radial sombre que le menu ;
 *  - un "hub" d'accueil à deux options tactiles (Avatars / Règles du jeu),
 *    sans montrer les avatars tant que l'utilisateur n'a pas choisi une
 *    catégorie (ouvrirParametres() côté JS) ;
 *  - sous-écran Avatars : bloc Joueur 1 et bloc Joueur 2, chacun avec
 *    aperçu, bascule Emoji/Initiales, grille d'émojis (6 colonnes), champ
 *    d'initiales, grille de couleurs (7 colonnes) ;
 *  - sous-écran Règles du jeu : "Qui commence ?" (Blancs/Noirs).
 *
 * Persistance : SharedPreferences "dame_prefs", équivalent natif du
 * localStorage utilisé côté JS (mêmes données : profil de chaque joueur,
 * couleur de début de partie).
 *
 * DÉVIATION assumée par rapport au JS : le champ d'initiales utilise ici un
 * vrai EditText + clavier système Android, au lieu du clavier virtuel
 * dessiné à la main (curseur customisé, bulle copier/coller) construit
 * côté JS. Ce clavier virtuel n'existait que pour contourner un problème
 * propre aux WebView (le clavier natif Android perturbait le viewport de
 * la page) — ce problème n'existe pas ici puisqu'on est en Android natif,
 * où EditText + IME système est le comportement standard et correct.
 *
 * PÉRIMÈTRE de cette étape : seule l'origine "menu" (ouvrirParametres(),
 * les deux profils visibles) est portée. La variante "jeu" — un seul
 * avatar visible, carte retournée à 180° pour le Joueur 2, appelée par
 * ouvrirParametresJoueur() côté JS — viendra avec l'étape "UI de partie",
 * quand les badges joueurs en jeu existeront côté natif.
 */
class ParametresView(context: Context) : FrameLayout(context) {

    companion object {
        private val COULEUR_FOND_1 = 0xFF1E2640.toInt()
        private val COULEUR_FOND_2 = 0xFF0C0F17.toInt()
        private val COULEUR_FOND_3 = 0xFF05060A.toInt()

        private const val COULEUR_CARTE_FOND = 0x8C141223.toInt() // rgba(20,18,35,0.55)
        private const val COULEUR_CARTE_BORD = 0x1FFFFFFF // rgba(255,255,255,0.12)

        private const val COULEUR_BTN_RETOUR_FOND = 0x0FFFFFFF // rgba(255,255,255,0.06)
        private const val COULEUR_BTN_RETOUR_BORD = 0x26FFFFFF // rgba(255,255,255,0.15)

        private const val COULEUR_HUB_FOND = 0x0AFFFFFF // rgba(255,255,255,0.04)
        private const val COULEUR_HUB_BORD = 0x1FFFFFFF // rgba(255,255,255,0.12)
        private val COULEUR_HUB_SOUS = 0x80FFFFFF.toInt() // rgba(255,255,255,0.5)
        private const val COULEUR_HUB_CHEVRON = 0x59FFFFFF // rgba(255,255,255,0.35)

        private const val COULEUR_BLOC_FOND = 0x05FFFFFF // rgba(255,255,255,0.02)
        private const val COULEUR_BLOC_BORD = 0x1AFFFFFF // rgba(255,255,255,0.1)
        private const val COULEUR_AVATAR_BORD = 0x33FFFFFF // rgba(255,255,255,0.2)

        private const val COULEUR_TOGGLE_FOND = 0x0DFFFFFF // rgba(255,255,255,0.05)
        private const val COULEUR_TOGGLE_BORD = 0x26FFFFFF // rgba(255,255,255,0.15)
        private val COULEUR_TOGGLE_TEXTE = 0xB2FFFFFF.toInt() // rgba(255,255,255,0.7)

        private const val COULEUR_ACTIF_ROUGE_BG = 0x40E94560 // rgba(233,69,96,0.25)
        private val COULEUR_ACTIF_ROUGE_BORD = 0xFFE94560.toInt() // #e94560

        private const val COULEUR_ACTIF_BLEU_BG = 0x474E73EB // rgba(78,115,235,0.28)
        private val COULEUR_ACTIF_BLEU_BORD = 0xFF4364F7.toInt() // #4364f7
        private const val COULEUR_REGLE_BLOC_FOND = 0x0D4E73EB // rgba(78,115,235,0.05)
        private const val COULEUR_REGLE_BLOC_BORD = 0x404E73EB // rgba(78,115,235,0.25)

        private const val COULEUR_EMOJI_FOND = 0x0AFFFFFF // rgba(255,255,255,0.04)
        private const val COULEUR_EMOJI_BORD = 0x1AFFFFFF // rgba(255,255,255,0.1)

        private const val COULEUR_PLACEHOLDER = 0x59FFFFFF // rgba(255,255,255,0.35)
        private const val COULEUR_NOTE = 0x73FFFFFF // rgba(255,255,255,0.45)

        private const val LARGEUR_CARTE_MAX_DP = 420

        private val EMOJIS_DISPONIBLES = listOf(
            "😀", "😎", "🙂", "🤠", "🥳", "😺", "🐶", "🐱",
            "🐼", "🦊", "🐸", "🦁", "🐵", "🐧", "🦉", "🐢",
            "🐲", "🦄", "👾", "🤖", "👑", "🎯", "🔥", "⚡",
            "🍀", "⭐", "🎮", "⚔️"
        )

        private val PALETTE_COULEURS = intArrayOf(
            0xFFE94560.toInt(), 0xFFF97316.toInt(), 0xFFF59E0B.toInt(), 0xFF10B981.toInt(),
            0xFF0EA5E9.toInt(), 0xFF4364F7.toInt(), 0xFF8B5CF6.toInt(), 0xFFEC4899.toInt(),
            0xFF64748B.toInt(), 0xFF1E293B.toInt()
        )

        private val COULEUR_DEFAUT_J1 = 0xFF0EA5E9.toInt()
        private val COULEUR_DEFAUT_J2 = 0xFF1E293B.toInt()
    }

    /** Miroir du profil { type, value, couleur } côté JS. */
    private data class Profil(var type: String, var value: String, var couleur: Int)

    /** Appelé quand l'écran se ferme complètement (miroir de fermerParametres() côté JS). */
    var onFermer: (() -> Unit)? = null

    private val prefs = context.getSharedPreferences("dame_prefs", Context.MODE_PRIVATE)

    private var profilJ1 = chargerProfil("j1", "emoji", "👤", COULEUR_DEFAUT_J1)
    private var profilJ2 = chargerProfil("j2", "emoji", "👤", COULEUR_DEFAUT_J2)
    private var couleurDebutPartie = prefs.getString("regle_debut", "blanc") ?: "blanc"

    /** null = hub d'accueil ; "avatars" ou "regles" = sous-écran actif. */
    private var sousEcran: String? = null

    private val fondRadial = GradientDrawable().apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT
        colors = intArrayOf(COULEUR_FOND_1, COULEUR_FOND_2, COULEUR_FOND_3)
        setGradientCenter(0.5f, 0.2f)
    }

    private lateinit var titreVue: TextView
    private lateinit var conteneurContenu: LinearLayout
    private lateinit var defilement: ScrollView

    init {
        visibility = View.GONE
        background = fondRadial

        defilement = ScrollView(context).apply { isFillViewport = true }
        val enveloppe = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(24), dp(12), dp(24))
        }
        enveloppe.addView(construireCarte(context))
        defilement.addView(
            enveloppe,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        )
        addView(defilement, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
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

    /** Ouvre l'écran depuis le menu (miroir de ouvrirParametres() côté JS). */
    fun ouvrir() {
        sousEcran = null
        rafraichir()
        defilement.scrollTo(0, 0)
        visibility = View.VISIBLE
    }

    /** À appeler depuis le bouton retour matériel quand cet écran est visible
     * (miroir de clicRetourParametres() côté JS : d'abord remonter au hub,
     * puis seulement fermer). Consomme toujours l'événement. */
    fun retourMateriel() = clicRetour()

    private fun clicRetour() {
        if (sousEcran != null) {
            sousEcran = null
            rafraichir()
        } else {
            visibility = View.GONE
            onFermer?.invoke()
        }
    }

    // ---------- Construction de la carte ----------

    private fun construireCarte(context: Context): LinearLayout {
        val largeurEcranDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val largeurCarteDp = min(largeurEcranDp - 24f, LARGEUR_CARTE_MAX_DP.toFloat())

        val carte = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(COULEUR_CARTE_FOND)
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), COULEUR_CARTE_BORD)
            }
            setPadding(dp(20), dp(24), dp(20), dp(26))
        }

        val entete = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titreVue = TextView(context).apply {
            text = "Paramètres"
            setTextColor(Color.WHITE)
            textSize = 17.5f
            setTypeface(typeface, Typeface.BOLD)
        }
        entete.addView(titreVue, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        entete.addView(construireBoutonRetour(context), LinearLayout.LayoutParams(dp(40), dp(40)))
        carte.addView(entete, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(20)
        })

        conteneurContenu = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        carte.addView(conteneurContenu, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(carte, LinearLayout.LayoutParams(dp(largeurCarteDp.toInt()), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun construireBoutonRetour(context: Context): View {
        return TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 15.5f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(COULEUR_BTN_RETOUR_FOND)
                setStroke(dp(1), COULEUR_BTN_RETOUR_BORD)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { clicRetour() }
        }
    }

    private fun rafraichir() {
        titreVue.text = when (sousEcran) {
            "avatars" -> "Avatars"
            "regles" -> "Règles du jeu"
            else -> "Paramètres"
        }
        conteneurContenu.removeAllViews()
        val contenu = when (sousEcran) {
            "avatars" -> construireAvatars(context)
            "regles" -> construireRegles(context)
            else -> construireHub(context)
        }
        conteneurContenu.addView(contenu, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        defilement.scrollTo(0, 0)
    }

    // ---------- Hub d'accueil ----------

    private fun construireHub(context: Context): LinearLayout {
        val hub = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        hub.addView(
            construireOptionHub(context, "🎭", "Avatars", "Emoji, initiales, couleurs") {
                sousEcran = "avatars"; rafraichir()
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        hub.addView(
            construireOptionHub(context, "📜", "Règles du jeu", "Qui commence la partie") {
                sousEcran = "regles"; rafraichir()
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
        )
        return hub
    }

    private fun construireOptionHub(context: Context, icone: String, titre: String, sousTitre: String, action: () -> Unit): LinearLayout {
        val option = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                setColor(COULEUR_HUB_FOND)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), COULEUR_HUB_BORD)
            }
            setPadding(dp(16), dp(18), dp(16), dp(18))
            setOnClickListener { action() }
        }
        option.addView(TextView(context).apply {
            text = icone
            textSize = 21f
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val texte = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texte.addView(TextView(context).apply {
            text = titre
            setTextColor(Color.WHITE)
            textSize = 14.3f
            setTypeface(typeface, Typeface.BOLD)
        })
        texte.addView(TextView(context).apply {
            text = sousTitre
            setTextColor(COULEUR_HUB_SOUS)
            textSize = 11.2f
        })
        option.addView(texte, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(14)
        })

        option.addView(TextView(context).apply {
            text = "›"
            setTextColor(COULEUR_HUB_CHEVRON)
            textSize = 19f
        })
        return option
    }

    // ---------- Sous-écran Avatars ----------

    private fun construireAvatars(context: Context): LinearLayout {
        val conteneur = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        conteneur.addView(
            construireProfilBloc(context, "j1", profilJ1, "Joueur 1 (Blancs)"),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        conteneur.addView(
            construireProfilBloc(context, "j2", profilJ2, "Joueur 2 (Noirs)"),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(18)
            }
        )
        conteneur.addView(TextView(context).apply {
            text = "Joueur 2 sert uniquement en mode 2 joueurs. Contre l'IA, tu joues toujours avec l'avatar Joueur 1."
            setTextColor(COULEUR_NOTE)
            textSize = 10.4f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
        })
        return conteneur
    }

    private fun construireProfilBloc(context: Context, joueur: String, profil: Profil, titreBloc: String): LinearLayout {
        val bloc = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(COULEUR_BLOC_FOND)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), COULEUR_BLOC_BORD)
            }
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        bloc.addView(TextView(context).apply {
            text = titreBloc
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        })

        val apercu = TextView(context).apply {
            text = profil.value
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = if (profil.type == "initiales") 15f else 21f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(profil.couleur)
                setStroke(dp(1), COULEUR_AVATAR_BORD)
            }
        }

        val ligneApercu = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        ligneApercu.addView(apercu, LinearLayout.LayoutParams(dp(56), dp(56)))
        ligneApercu.addView(
            creerToggleSegmente(
                context,
                listOf("Emoji" to "emoji", "Initiales" to "initiales"),
                profil.type,
                COULEUR_ACTIF_ROUGE_BG, COULEUR_ACTIF_ROUGE_BORD
            ) { nouveauType -> changerTypeProfil(joueur, nouveauType) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) }
        )
        bloc.addView(ligneApercu, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14)
        })

        if (profil.type == "emoji") {
            val largeurContenuDp = contenuLargeurDp()
            val tailleCelluleDp = (largeurContenuDp - 5 * 6) / 6f
            val grille = grilleFixe(context, EMOJIS_DISPONIBLES.size, 6, tailleCelluleDp, 6) { idx ->
                val emoji = EMOJIS_DISPONIBLES[idx]
                val actif = profil.value == emoji
                TextView(context).apply {
                    text = emoji
                    gravity = Gravity.CENTER
                    textSize = 14.5f
                    background = GradientDrawable().apply {
                        cornerRadius = dp(10).toFloat()
                        if (actif) {
                            setColor(COULEUR_ACTIF_ROUGE_BG)
                            setStroke(dp(1), COULEUR_ACTIF_ROUGE_BORD)
                        } else {
                            setColor(COULEUR_EMOJI_FOND)
                            setStroke(dp(1), COULEUR_EMOJI_BORD)
                        }
                    }
                    isClickable = true
                    setOnClickListener {
                        profil.value = emoji
                        profil.type = "emoji"
                        sauvegarderProfil(joueur, profil)
                        rafraichir()
                    }
                }
            }
            bloc.addView(grille, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            })
        } else {
            bloc.addView(
                construireChampInitiales(context, joueur, profil, apercu),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(14)
                }
            )
        }

        val largeurContenuDp = contenuLargeurDp()
        val tailleCouleurDp = (largeurContenuDp - 6 * 8) / 7f
        val grilleCouleur = grilleFixe(context, PALETTE_COULEURS.size, 7, tailleCouleurDp, 8) { idx ->
            val couleur = PALETTE_COULEURS[idx]
            val actif = profil.couleur == couleur
            View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(couleur)
                    setStroke(dp(if (actif) 3 else 2), if (actif) Color.WHITE else Color.TRANSPARENT)
                }
                isClickable = true
                setOnClickListener {
                    profil.couleur = couleur
                    sauvegarderProfil(joueur, profil)
                    rafraichir()
                }
            }
        }
        bloc.addView(grilleCouleur, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        return bloc
    }

    /** Largeur intérieure de la carte (hors padding 20dp de chaque côté),
     * utilisée pour calculer une taille de cellule fixe reproduisant les
     * grilles CSS (repeat(N, 1fr) + gap) sans dépendre de weights Android. */
    private fun contenuLargeurDp(): Float {
        val largeurEcranDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val largeurCarteDp = min(largeurEcranDp - 24f, LARGEUR_CARTE_MAX_DP.toFloat())
        return largeurCarteDp - 40f
    }

    private fun changerTypeProfil(joueur: String, type: String) {
        val profil = if (joueur == "j1") profilJ1 else profilJ2
        profil.type = type
        if (type == "emoji" && profil.value !in EMOJIS_DISPONIBLES) profil.value = EMOJIS_DISPONIBLES[0]
        if (type == "initiales") profil.value = profil.value.uppercase().filter { it in 'A'..'Z' }.take(2)
        sauvegarderProfil(joueur, profil)
        rafraichir()
    }

    private fun construireChampInitiales(context: Context, joueur: String, profil: Profil, apercu: TextView): LinearLayout {
        val champ = EditText(context).apply {
            setText(profil.value)
            hint = if (joueur == "j1") "Ex : JY" else "Ex : YL"
            setHintTextColor(COULEUR_PLACEHOLDER)
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            textSize = 15f
            letterSpacing = 0.12f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(FiltreLettresMajuscules(), InputFilter.LengthFilter(2))
            background = GradientDrawable().apply {
                setColor(COULEUR_TOGGLE_FOND)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), COULEUR_TOGGLE_BORD)
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        // Mise à jour locale (pas de rafraichir() complet ici, pour ne pas
        // perdre le focus/curseur à chaque frappe) : miroir de
        // mettreAJourInitialesDepuisClavier() côté JS.
        champ.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                profil.type = "initiales"
                profil.value = (s?.toString() ?: "")
                sauvegarderProfil(joueur, profil)
                apercu.text = profil.value
                apercu.textSize = 15f
            }
        })

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(champ, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    /** N'accepte que des lettres, converties en majuscules (miroir du
     * nettoyage A-Z appliqué côté JS aussi bien à la saisie qu'au collage). */
    private class FiltreLettresMajuscules : InputFilter {
        override fun filter(source: CharSequence, start: Int, end: Int, dest: android.text.Spanned?, dstart: Int, dend: Int): CharSequence {
            val resultat = StringBuilder()
            for (i in start until end) {
                val c = source[i]
                if (c.isLetter()) resultat.append(c.uppercaseChar())
            }
            return resultat
        }
    }

    // ---------- Sous-écran Règles du jeu ----------

    private fun construireRegles(context: Context): LinearLayout {
        val bloc = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(COULEUR_REGLE_BLOC_FOND)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), COULEUR_REGLE_BLOC_BORD)
            }
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val texte = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texte.addView(TextView(context).apply {
            text = "Qui commence ?"
            setTextColor(Color.WHITE)
            textSize = 12.6f
            setTypeface(typeface, Typeface.BOLD)
        })
        texte.addView(TextView(context).apply {
            text = "S'applique à la prochaine partie"
            setTextColor(COULEUR_HUB_SOUS)
            textSize = 10.9f
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2)
        })
        bloc.addView(texte, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val toggle = creerToggleSegmente(
            context,
            listOf("⚪ Blancs" to "blanc", "⚫ Noirs" to "noir"),
            couleurDebutPartie,
            COULEUR_ACTIF_BLEU_BG, COULEUR_ACTIF_BLEU_BORD
        ) { valeur ->
            couleurDebutPartie = valeur
            prefs.edit().putString("regle_debut", couleurDebutPartie).apply()
            rafraichir()
        }
        bloc.addView(toggle, LinearLayout.LayoutParams(dp(160), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(14)
        })

        val conteneur = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        conteneur.addView(bloc, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return conteneur
    }

    // ---------- Aides génériques ----------

    /** Contrôle segmenté à 2 (ou plus) options, miroir de .type-toggle /
     * .type-actif côté CSS. Réutilisé pour Emoji/Initiales (accent rouge)
     * et Blancs/Noirs (accent bleu). */
    private fun creerToggleSegmente(
        context: Context,
        options: List<Pair<String, String>>,
        valeurActuelle: String,
        couleurActiveBg: Int, couleurActiveBordure: Int,
        onSelect: (String) -> Unit
    ): LinearLayout {
        val conteneur = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        options.forEachIndexed { i, (label, valeur) ->
            val actif = valeur == valeurActuelle
            val bouton = TextView(context).apply {
                text = label
                textSize = 11.8f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setTextColor(if (actif) Color.WHITE else COULEUR_TOGGLE_TEXTE)
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    if (actif) {
                        setColor(couleurActiveBg)
                        setStroke(dp(1), couleurActiveBordure)
                    } else {
                        setColor(COULEUR_TOGGLE_FOND)
                        setStroke(dp(1), COULEUR_TOGGLE_BORD)
                    }
                }
                isClickable = true
                setOnClickListener { onSelect(valeur) }
            }
            conteneur.addView(bouton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i > 0) marginStart = dp(8)
            })
        }
        return conteneur
    }

    /** Grille à cellules carrées de taille fixe (dp), en lignes de
     * [colonnes] éléments — miroir de grid-template-columns: repeat(N, 1fr)
     * + gap côté CSS, sans dépendre de weights Android (qui ne donnent pas
     * des cellules carrées de façon fiable). */
    private fun grilleFixe(
        context: Context, count: Int, colonnes: Int, celluleDp: Float, gapDp: Int,
        builder: (Int) -> View
    ): LinearLayout {
        val conteneur = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val nbLignes = (count + colonnes - 1) / colonnes
        for (ligneIdx in 0 until nbLignes) {
            val ligne = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (col in 0 until colonnes) {
                val idx = ligneIdx * colonnes + col
                if (idx >= count) break
                val vue = builder(idx)
                ligne.addView(vue, LinearLayout.LayoutParams(dp(celluleDp), dp(celluleDp)).apply {
                    if (col > 0) marginStart = dp(gapDp)
                })
            }
            conteneur.addView(ligne, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                if (ligneIdx > 0) topMargin = dp(gapDp)
            })
        }
        return conteneur
    }

    // ---------- Persistance (SharedPreferences = équivalent natif du localStorage) ----------

    private fun chargerProfil(joueur: String, typeDefaut: String, valeurDefaut: String, couleurDefaut: Int): Profil {
        val type = prefs.getString("profil_${joueur}_type", typeDefaut) ?: typeDefaut
        val value = prefs.getString("profil_${joueur}_value", valeurDefaut) ?: valeurDefaut
        val couleur = prefs.getInt("profil_${joueur}_couleur", couleurDefaut)
        return Profil(type, value, couleur)
    }

    private fun sauvegarderProfil(joueur: String, profil: Profil) {
        prefs.edit()
            .putString("profil_${joueur}_type", profil.type)
            .putString("profil_${joueur}_value", profil.value)
            .putInt("profil_${joueur}_couleur", profil.couleur)
            .apply()
    }

    private fun dp(valeur: Int): Int = (valeur * resources.displayMetrics.density).toInt()
    private fun dp(valeur: Float): Int = (valeur * resources.displayMetrics.density).toInt()
}
