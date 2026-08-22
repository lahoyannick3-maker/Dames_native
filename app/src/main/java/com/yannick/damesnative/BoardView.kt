package com.yannick.damesnative

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

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

    private fun taille(): Float = width.coerceAtMost(height) / 10f

    /** Flashe la case (x, z) en rouge pendant 300 ms, puis l'efface. Appelée
     * quand un pion touché n'a aucun coup légal alors qu'une prise est
     * obligatoire ailleurs sur le plateau. */
    fun flashErreur(x: Int, z: Int) {
        caseErreur = x to z
        postDelayed({ caseErreur = null }, 300)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = taille()

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

                // encodage plat (moteur.c) : -1 vide, 0 pion blanc, 1 dame blanche,
                // 2 pion noir, 3 dame noire, 4 bloquée (temporaire, rafle en cours)
                val valeur = plateau.getOrElse(col * 10 + row) { -1 }.toInt()
                if (valeur in 0..3) {
                    val estBlanc = valeur == 0 || valeur == 1
                    val estDame = valeur == 1 || valeur == 3
                    val paintPion = if (estBlanc) paintPionBlanc else paintPionNoir
                    val cx = x + t / 2; val cy = y + t / 2
                    canvas.drawCircle(cx, cy, t * 0.4f, paintPion)
                    if (estDame) canvas.drawCircle(cx, cy, t * 0.26f, paintDameAnneau)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
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
