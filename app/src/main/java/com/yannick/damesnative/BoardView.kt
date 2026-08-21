package com.yannick.damesnative

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * Affichage 2D minimal du plateau de dames (10x10 cases).
 * Sert uniquement à valider la chaîne JNI -> Kotlin -> écran.
 * Le rendu 3D (Three.js -> OpenGL ES/Filament) viendra dans une étape ultérieure.
 */
class BoardView(context: Context, private val plateau: IntArray) : View(context) {

    private val paintCaseClaire = Paint().apply { color = Color.parseColor("#E8D0AA") }
    private val paintCaseFoncee = Paint().apply { color = Color.parseColor("#8B5A2B") }
    private val paintPionJ1 = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val paintPionJ2 = Paint().apply { color = Color.BLACK; isAntiAlias = true }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val taille = width.coerceAtMost(height) / 10f

        for (row in 0 until 10) {
            for (col in 0 until 10) {
                val x = col * taille
                val y = row * taille

                val paintCase = if ((row + col) % 2 == 0) paintCaseClaire else paintCaseFoncee
                canvas.drawRect(x, y, x + taille, y + taille, paintCase)

                val valeur = plateau.getOrElse(row * 10 + col) { 0 }
                if (valeur != 0) {
                    val paintPion = if (valeur == 1) paintPionJ1 else paintPionJ2
                    canvas.drawCircle(x + taille / 2, y + taille / 2, taille * 0.4f, paintPion)
                }
            }
        }
    }
}
