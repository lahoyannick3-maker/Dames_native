package com.yannick.damesnative

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
 *    d'initiales avec clavier virtuel dédié, grille de couleurs (7 col.) ;
 *  - sous-écran Règles du jeu : "Qui commence ?" (Blancs/Noirs).
 *
 * Persistance : SharedPreferences "dame_prefs", équivalent natif du
 * localStorage utilisé côté JS (mêmes données : profil de chaque joueur,
 * couleur de début de partie).
 *
 * Clavier des initiales (voir ClavierInitiales.kt) : champ non éditable au
 * sens Android (ChampInitialesVue, PAS un EditText), clavier virtuel A-Z
 * qui coulisse depuis le bas, bulle Copier/Coller sur appui long — miroir
 * fidèle de #clavierVirtuelInitiales côté JS. Le clavier système Android
 * n'est jamais utilisé ici : il ne pourrait pas pivoter avec la carte du
 * Joueur 2 quand elle est retournée à 180° en cours de partie.
 *
 * PÉRIMÈTRE de cette étape : seule l'origine "menu" (ouvrirParametres(),
 * les deux profils visibles, jamais de carte tournée) est portée. La
 * variante "jeu" — un seul avatar visible, carte ET clavier retournés à
 * 180° pour le Joueur 2, appelée par ouvrirParametresJoueur() côté JS —
 * viendra avec l'étape "UI de partie", quand les badges joueurs en jeu
 * existeront côté natif.
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

    // ---- État du clavier virtuel des initiales (miroir des variables
    // module-scope côté JS : joueurClavierInitialesActif, curseurPositionInitiales) ----
    private var joueurClavierActif: String? = null
    private var curseurPosJ1 = 0
    private var curseurPosJ2 = 0
    private var champActif: ChampInitialesVue? = null
    private var apercuActif: TextView? = null

    private val fondRadial = GradientDrawable().apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT
        colors = intArrayOf(COULEUR_FOND_1, COULEUR_FOND_2, COULEUR_FOND_3)
        setGradientCenter(0.5f, 0.2f)
    }

    private lateinit var titreVue: TextView
    private lateinit var conteneurContenu: LinearLayout
    private lateinit var defilement: ScrollView
    private lateinit var clavierInitiales: ClavierVirtuelInitiales
    private lateinit var bulleCopierColler: BulleCopierColler

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

        // Clavier + bulle : ajoutés en dernier pour rester au-dessus de la
        // carte, indépendants d'elle (comme #clavierVirtuelInitiales et
        // #bulleCopierCollerInitiales, hors de #parametresCard côté JS).
        clavierInitiales = ClavierVirtuelInitiales(context).apply {
            onLettre = { lettre -> saisirLettreClavier(lettre) }
            onEffacer = { effacerDerniereInitiale() }
            onFermer = { fermerClavierVirtuel() }
        }
        addView(clavierInitiales, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))

        bulleCopierColler = BulleCopierColler(context).apply {
            onCopier = { copierInitiales() }
            onColler = { collerInitiales() }
        }
        addView(bulleCopierColler, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
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

    /** Un tap n'importe où en dehors de la bulle Copier/Coller la referme
     * (miroir du listener document-level touchstart côté JS). */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && bulleCopierColler.visibility == View.VISIBLE) {
            val loc = IntArray(2)
            bulleCopierColler.getLocationOnScreen(loc)
            val dansLaBulle = ev.rawX >= loc[0] && ev.rawX <= loc[0] + bulleCopierColler.width &&
                ev.rawY >= loc[1] && ev.rawY <= loc[1] + bulleCopierColler.height
            if (!dansLaBulle) bulleCopierColler.masquer()
        }
        return super.dispatchTouchEvent(ev)
    }

    /** Ouvre l'écran depuis le menu (miroir de ouvrirParametres() côté JS). */
    fun ouvrir() {
        fermerClavierVirtuel()
        sousEcran = null
        rafraichir()
        defilement.scrollTo(0, 0)
        visibility = View.VISIBLE
    }

    /** À appeler depuis le bouton retour matériel quand cet écran est visible
     * (miroir de clicRetourParametres() côté JS). Consomme toujours l'événement. */
    fun retourMateriel() = clicRetour()

    /** Miroir exact de clicRetourParametres() : le clavier se ferme en
     * premier s'il est ouvert, puis seulement le sous-écran, puis l'écran. */
    private fun clicRetour() {
        if (clavierInitiales.visibility == View.VISIBLE) {
            fermerClavierVirtuel()
            return
        }
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
        // Sécurité : une reconstruction complète du sous-écran (changement
        // d'emoji/couleur, bascule de sous-écran...) détacherait le champ
        // actuellement lié au clavier. On referme proprement avant.
        fermerClavierVirtuel()

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

        val largeurContenuDp2 = contenuLargeurDp()
        val tailleCouleurDp = (largeurContenuDp2 - 6 * 8) / 7f
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

    private fun profilPour(joueur: String): Profil = if (joueur == "j1") profilJ1 else profilJ2

    private fun curseurPosPour(joueur: String): Int = if (joueur == "j1") curseurPosJ1 else curseurPosJ2

    private fun definirCurseurPosPour(joueur: String, position: Int) {
        if (joueur == "j1") curseurPosJ1 = position else curseurPosJ2 = position
    }

    private fun changerTypeProfil(joueur: String, type: String) {
        val profil = profilPour(joueur)
        profil.type = type
        if (type == "emoji" && profil.value !in EMOJIS_DISPONIBLES) profil.value = EMOJIS_DISPONIBLES[0]
        if (type == "initiales") profil.value = profil.value.uppercase().filter { it in 'A'..'Z' }.take(2)
        sauvegarderProfil(joueur, profil)
        rafraichir()
    }

    // ---------- Champ d'initiales + clavier virtuel ----------

    private fun construireChampInitiales(context: Context, joueur: String, profil: Profil, apercu: TextView): LinearLayout {
        lateinit var champ: ChampInitialesVue
        champ = ChampInitialesVue(context).apply {
            valeur = profil.value
            placeholder = if (joueur == "j1") "Ex : JY" else "Ex : YL"
            background = GradientDrawable().apply {
                setColor(COULEUR_TOGGLE_FOND)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), COULEUR_TOGGLE_BORD)
            }
            onTap = { position, xEcran, yEcran -> ouvrirClavierPourChamp(joueur, champ, apercu, position) }
            onAppuiLong = { xEcran, yEcran -> afficherBulleCopierColler(joueur, xEcran, yEcran) }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(champ, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))
        }
    }

    /** Miroir de ouvrirClavierInitiales(joueur, evt) côté JS. */
    private fun ouvrirClavierPourChamp(joueur: String, champ: ChampInitialesVue, apercu: TextView, positionTap: Int) {
        if (joueurClavierActif != null && joueurClavierActif != joueur) {
            champActif?.activerSaisie(false)
        }
        joueurClavierActif = joueur
        champActif = champ
        apercuActif = apercu

        val profil = profilPour(joueur)
        val position = positionTap.coerceIn(0, profil.value.length)
        definirCurseurPosPour(joueur, position)

        champ.valeur = profil.value
        champ.curseurPosition = position
        champ.activerSaisie(true)

        clavierInitiales.ouvrir(if (joueur == "j2") "Initiales — Joueur 2" else "Initiales — Joueur 1")
    }

    /** Miroir de fermerClavierInitiales() côté JS. */
    private fun fermerClavierVirtuel() {
        clavierInitiales.fermer()
        champActif?.activerSaisie(false)
        champActif = null
        apercuActif = null
        joueurClavierActif = null
        bulleCopierColler.masquer()
    }

    /** Miroir de mettreAJourInitialesDepuisClavier(joueur) côté JS : mise à
     * jour locale du champ + de l'aperçu, sans reconstruire tout l'écran
     * (pour ne pas fermer le clavier qu'on est en train d'utiliser). */
    private fun rafraichirApercuInitiales(joueur: String) {
        val profil = profilPour(joueur)
        profil.type = "initiales"
        profil.value = profil.value.uppercase().take(2)

        val position = curseurPosPour(joueur).coerceIn(0, profil.value.length)
        definirCurseurPosPour(joueur, position)

        champActif?.valeur = profil.value
        champActif?.curseurPosition = position

        apercuActif?.text = profil.value
        apercuActif?.textSize = 15f

        sauvegarderProfil(joueur, profil)
    }

    /** Miroir de saisirLettreClavierInitiales(lettre) côté JS. */
    private fun saisirLettreClavier(lettre: Char) {
        val joueur = joueurClavierActif ?: return
        val profil = profilPour(joueur)
        val valeur = profil.value.uppercase()
        if (valeur.length >= 2) return

        var pos = curseurPosPour(joueur).coerceIn(0, valeur.length)
        profil.value = valeur.substring(0, pos) + lettre + valeur.substring(pos)
        pos += 1
        definirCurseurPosPour(joueur, pos)
        rafraichirApercuInitiales(joueur)
    }

    /** Miroir de effacerDerniereInitiale() côté JS (efface avant le curseur). */
    private fun effacerDerniereInitiale() {
        val joueur = joueurClavierActif ?: return
        val profil = profilPour(joueur)
        val valeur = profil.value
        val pos = curseurPosPour(joueur).coerceIn(0, valeur.length)
        if (pos == 0) return

        profil.value = valeur.substring(0, pos - 1) + valeur.substring(pos)
        definirCurseurPosPour(joueur, pos - 1)
        rafraichirApercuInitiales(joueur)
    }

    /** Miroir de copierInitiales() côté JS — plus simple en natif : pas
     * besoin de plugin, l'API ClipboardManager d'Android suffit. */
    private fun copierInitiales() {
        val joueur = joueurClavierActif ?: return
        val profil = profilPour(joueur)
        if (profil.value.isEmpty()) return
        val gestionnaire = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        gestionnaire.setPrimaryClip(ClipData.newPlainText("initiales", profil.value))
    }

    /** Miroir de collerInitiales() côté JS. */
    private fun collerInitiales() {
        val joueur = joueurClavierActif ?: return
        val gestionnaire = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val item = gestionnaire.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0) ?: return
        val texte = item.coerceToText(context)?.toString() ?: return
        val lettres = texte.uppercase().filter { it in 'A'..'Z' }
        if (lettres.isEmpty()) return

        val profil = profilPour(joueur)
        val valeur = profil.value
        val pos = curseurPosPour(joueur).coerceIn(0, valeur.length)
        val nouvelleValeur = (valeur.substring(0, pos) + lettres + valeur.substring(pos)).take(2)
        profil.value = nouvelleValeur
        definirCurseurPosPour(joueur, (pos + lettres.length).coerceAtMost(nouvelleValeur.length))
        rafraichirApercuInitiales(joueur)
    }

    /** Miroir de afficherBulleCopierCollerInitiales(joueur, x, y) côté JS. */
    private fun afficherBulleCopierColler(joueur: String, xEcran: Float, yEcran: Float) {
        val profil = profilPour(joueur)
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val xParent = xEcran - loc[0]
        val yParent = yEcran - loc[1]
        bulleCopierColler.afficher(xParent, yParent, profil.value.isNotEmpty(), width)
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
     * + gap côté CSS, sans dépendre de weights Android. */
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
