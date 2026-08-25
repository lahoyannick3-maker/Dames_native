package com.yannick.damesnative

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Faux champ d'initiales : miroir du `<input readonly inputmode="none">`
 * côté JS. Ce n'est volontairement PAS un EditText — jamais de clavier
 * système Android — pour la même raison que côté JS : le clavier système
 * glisse toujours depuis le bas et ne peut pas pivoter, alors que la carte
 * du Joueur 2 est tournée à 180° en cours de partie (origine "jeu", pas
 * encore câblée ici). Dessine son propre texte centré + curseur clignotant,
 * et détecte :
 *  - un tap → repositionne le curseur à l'endroit touché (miroir de
 *    positionCurseurDepuisClic / ouvrirClavierInitiales) ;
 *  - un appui long (500 ms, annulé au-delà de 12dp de déplacement) →
 *    déclenche la bulle Copier/Coller (miroir de demarrerAppuiLongInitiales).
 */
class ChampInitialesVue(context: Context) : View(context) {

    var valeur: String = ""
        set(v) { field = v; invalidate() }
    var curseurPosition: Int = 0
        set(v) { field = v; invalidate() }
    var placeholder: String = ""

    private var saisieActive = false
    private var curseurVisible = false

    /** (position tapée, x écran, y écran) */
    var onTap: ((Int, Float, Float) -> Unit)? = null
    /** (x écran, y écran) */
    var onAppuiLong: ((Float, Float) -> Unit)? = null

    private val densite = context.resources.displayMetrics.density
    private val espacementLettresPx = 2f * densite
    private val seuilDeplacementPx = 12f * densite

    private val paintTexte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textSize = 15f * densite
        textAlign = Paint.Align.LEFT
    }
    private val paintPlaceholder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x59FFFFFF.toInt() // rgba(255,255,255,0.35)
        textSize = 13f * densite
        textAlign = Paint.Align.CENTER
    }
    private val paintCaret = Paint().apply {
        color = Color.WHITE
        strokeWidth = 1.5f * densite
    }

    private var xDepart = 0f
    private var yDepart = 0f
    private var appuiLongEnCours: Runnable? = null

    private val runnableClignotement = object : Runnable {
        override fun run() {
            if (saisieActive) {
                curseurVisible = !curseurVisible
                invalidate()
                postDelayed(this, 500)
            }
        }
    }

    /** Active/désactive l'affichage du curseur clignotant (miroir de
     * mettreAJourCurseurInitiales(joueur, actif)). */
    fun activerSaisie(actif: Boolean) {
        saisieActive = actif
        removeCallbacks(runnableClignotement)
        if (actif) {
            curseurVisible = true
            postDelayed(runnableClignotement, 500)
        } else {
            curseurVisible = false
        }
        invalidate()
    }

    private fun largeurTexte(v: String): Float =
        if (v.isEmpty()) 0f else paintTexte.measureText(v) + max(0, v.length - 1) * espacementLettresPx

    private fun decalage(v: String, position: Int): Float {
        if (position <= 0) return 0f
        val sous = v.substring(0, position)
        return paintTexte.measureText(sous) + max(0, position - 1) * espacementLettresPx
    }

    /** Miroir de positionCurseurDepuisClic côté JS. */
    private fun positionDepuisX(x: Float): Int {
        if (valeur.isEmpty()) return 0
        val x0 = width / 2f - largeurTexte(valeur) / 2f
        var meilleurePosition = 0
        var meilleureDistance = Float.MAX_VALUE
        for (k in 0..valeur.length) {
            val d = abs((x - x0) - decalage(valeur, k))
            if (d < meilleureDistance) {
                meilleureDistance = d
                meilleurePosition = k
            }
        }
        return meilleurePosition
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                xDepart = event.x
                yDepart = event.y
                val rawX = event.rawX
                val rawY = event.rawY
                val r = Runnable { onAppuiLong?.invoke(rawX, rawY) }
                appuiLongEnCours = r
                postDelayed(r, 500)
                onTap?.invoke(positionDepuisX(event.x), rawX, rawY)
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot((event.x - xDepart).toDouble(), (event.y - yDepart).toDouble()) > seuilDeplacementPx) {
                    appuiLongEnCours?.let { removeCallbacks(it) }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                appuiLongEnCours?.let { removeCallbacks(it) }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (valeur.isEmpty() && !saisieActive) {
            canvas.drawText(placeholder, width / 2f, height / 2f - (paintPlaceholder.ascent() + paintPlaceholder.descent()) / 2f, paintPlaceholder)
            return
        }
        val x0 = width / 2f - largeurTexte(valeur) / 2f
        val yBase = height / 2f - (paintTexte.ascent() + paintTexte.descent()) / 2f
        var x = x0
        for (c in valeur) {
            val s = c.toString()
            canvas.drawText(s, x, yBase, paintTexte)
            x += paintTexte.measureText(s) + espacementLettresPx
        }
        if (saisieActive && curseurVisible) {
            val xCaret = x0 + decalage(valeur, curseurPosition)
            canvas.drawLine(xCaret, height * 0.2f, xCaret, height * 0.8f, paintCaret)
        }
    }
}

/**
 * Panneau clavier virtuel (lettres A-Z + effacer), miroir de
 * #clavierVirtuelInitiales côté JS : posé au-dessus du reste de l'écran,
 * indépendant de la carte des paramètres, coulisse depuis le bas.
 * La variante tournée à 180° pour le Joueur 2 en cours de partie
 * (.clavier-j2 côté JS) n'est pas câblée ici : elle viendra avec l'origine
 * "jeu" de l'écran Paramètres, pas encore portée.
 */
class ClavierVirtuelInitiales(context: Context) : LinearLayout(context) {

    var onLettre: ((Char) -> Unit)? = null
    var onEffacer: (() -> Unit)? = null
    var onFermer: (() -> Unit)? = null

    private val titreVue: TextView
    private val densite = context.resources.displayMetrics.density
    private fun dp(v: Int) = (v * densite).toInt()

    init {
        orientation = VERTICAL
        visibility = View.GONE
        background = GradientDrawable().apply {
            setColor(0xFA11121E.toInt()) // rgba(17,18,30,0.98)
            setStroke(dp(1), 0x1FFFFFFF) // rgba(255,255,255,0.12)
        }
        setPadding(dp(8), dp(8), dp(8), dp(10))

        val entete = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titreVue = TextView(context).apply {
            text = "Initiales"
            setTextColor(0xB8FFFFFF.toInt()) // rgba(255,255,255,0.72)
            textSize = 11.5f
            setTypeface(typeface, Typeface.BOLD)
        }
        entete.addView(titreVue, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        entete.addView(TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x14FFFFFF) // rgba(255,255,255,0.08)
            }
            isClickable = true
            setOnClickListener { onFermer?.invoke() }
        }, LayoutParams(dp(30), dp(30)))
        addView(entete, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(6)
        })

        addView(construireGrille(context), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** Miroir de l'ouverture du panneau (transform: translateY(105%) → 0). */
    fun ouvrir(titre: String) {
        titreVue.text = titre
        if (visibility != View.VISIBLE) {
            visibility = View.VISIBLE
            translationY = 600f * densite
            post { animate().translationY(0f).setDuration(220).start() }
        }
    }

    fun fermer() {
        if (visibility != View.VISIBLE) return
        animate().translationY(height.toFloat().let { if (it > 0f) it else 600f * densite }).setDuration(220).withEndAction {
            visibility = View.GONE
            translationY = 0f
        }.start()
    }

    private fun construireGrille(context: Context): LinearLayout {
        val lettres = ('A'..'Z').toList()
        val gapDp = 6
        val conteneur = LinearLayout(context).apply { orientation = VERTICAL }
        val lignesLettres = listOf(
            lettres.subList(0, 7),
            lettres.subList(7, 14),
            lettres.subList(14, 21)
        )
        lignesLettres.forEachIndexed { i, ligneLettres ->
            conteneur.addView(construireLigneTouches(context, ligneLettres, false), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                if (i > 0) topMargin = dp(gapDp)
            })
        }
        conteneur.addView(construireLigneTouches(context, lettres.subList(21, 26), true), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(gapDp)
        })
        return conteneur
    }

    /** Une lettre = 1 "part" de largeur ; le bouton effacer = 2 parts
     * (grid-column: span 2 côté CSS). Le total de parts par ligne vaut
     * toujours 7 (7 lettres, ou 5 lettres + effacer(2)), donc les cellules
     * restent alignées d'une ligne à l'autre malgré le span. */
    private fun construireLigneTouches(context: Context, lettresLigne: List<Char>, avecEffacer: Boolean): LinearLayout {
        val gapDp = 6
        val ligne = LinearLayout(context).apply { orientation = HORIZONTAL }
        lettresLigne.forEachIndexed { i, lettre ->
            ligne.addView(construireToucheLettre(context, lettre), LayoutParams(0, dp(48), 1f).apply {
                if (i > 0) marginStart = dp(gapDp)
            })
        }
        if (avecEffacer) {
            ligne.addView(construireToucheEffacer(context), LayoutParams(0, dp(48), 2f).apply {
                marginStart = dp(gapDp)
            })
        }
        return ligne
    }

    private fun construireToucheLettre(context: Context, lettre: Char): TextView {
        val fondNormal = GradientDrawable().apply {
            cornerRadius = dp(9).toFloat()
            setColor(0x14FFFFFF) // rgba(255,255,255,0.08)
            setStroke(dp(1), 0x1AFFFFFF) // rgba(255,255,255,0.1)
        }
        val fondActif = GradientDrawable().apply {
            cornerRadius = dp(9).toFloat()
            setColor(0x47E94560) // rgba(233,69,96,0.28)
            setStroke(dp(1), 0xCCE94560.toInt()) // rgba(233,69,96,0.8)
        }
        return TextView(context).apply {
            text = lettre.toString()
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = fondNormal
            isClickable = true
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> background = fondActif
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> background = fondNormal
                }
                false
            }
            setOnClickListener { onLettre?.invoke(lettre) }
        }
    }

    private fun construireToucheEffacer(context: Context): TextView {
        return TextView(context).apply {
            text = "⌫"
            setTextColor(Color.WHITE)
            textSize = 17f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(0x1FFFFFFF) // rgba(255,255,255,0.12)
            }
            isClickable = true
            setOnClickListener { onEffacer?.invoke() }
        }
    }
}

/**
 * Bulle flottante "Copier / Coller" façon clavier Android natif, affichée
 * lors d'un appui long sur le champ d'initiales — miroir de
 * creerBulleCopierCollerInitiales / afficherBulleCopierCollerInitiales
 * côté JS. Positionnée en coordonnées LOCALES à son parent (déjà converties
 * depuis rawX/rawY par l'appelant).
 */
class BulleCopierColler(context: Context) : LinearLayout(context) {

    var onCopier: (() -> Unit)? = null
    var onColler: (() -> Unit)? = null

    private val boutonCopier: TextView
    private val densite = context.resources.displayMetrics.density
    private fun dp(v: Int) = (v * densite).toInt()

    init {
        orientation = HORIZONTAL
        visibility = View.GONE
        background = GradientDrawable().apply {
            setColor(0xFF16213E.toInt())
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), 0x8CE94560.toInt()) // rgba(233,69,96,0.55)
        }
        setPadding(dp(4), dp(4), dp(4), dp(4))
        elevation = dp(8).toFloat()

        boutonCopier = construireBouton(context, "Copier") { onCopier?.invoke(); masquer() }
        val boutonColler = construireBouton(context, "Coller") { onColler?.invoke(); masquer() }
        addView(boutonCopier)
        addView(boutonColler, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(2) })
    }

    private fun construireBouton(context: Context, texte: String, action: () -> Unit): TextView {
        return TextView(context).apply {
            text = texte
            setTextColor(Color.WHITE)
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            isClickable = true
            setOnClickListener { if (isEnabled) action() }
        }
    }

    /** x, y = coordonnées LOCALES au parent de la bulle (déjà converties
     * depuis rawX/rawY). largeurParentPx sert au recadrage sur les bords. */
    fun afficher(x: Float, y: Float, copierActif: Boolean, largeurParentPx: Int) {
        boutonCopier.isEnabled = copierActif
        boutonCopier.setTextColor(if (copierActif) Color.WHITE else 0x4DFFFFFF.toInt())
        visibility = View.VISIBLE
        post {
            var left = x - width / 2f
            val top = max(dp(48).toFloat(), y - dp(16) - height)
            if (left < dp(4)) left = dp(4).toFloat()
            if (left + width > largeurParentPx - dp(4)) left = (largeurParentPx - dp(4) - width).toFloat()
            translationX = left
            translationY = top
        }
    }

    fun masquer() {
        visibility = View.GONE
    }
}
