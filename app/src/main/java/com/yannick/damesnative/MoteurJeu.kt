package com.yannick.damesnative

import org.json.JSONArray
import org.json.JSONObject

/** Un coup légal tel que renvoyé par natif_coupsPour(). */
data class CoupLegal(
    val x1: Int, val z1: Int,
    val x2: Int, val z2: Int,
    val prise: Boolean,
    val nbPrises: Int
)

/** Résultat d'un saut joué via natif_jouerCoup(). */
data class ResultatCoup(
    val plateau: ByteArray,
    val prise: Boolean,
    val px: Int, val pz: Int,
    val devientDame: Boolean,
    /** Sauts encore possibles depuis la nouvelle case si la rafle continue. */
    val suite: List<Pair<Int, Int>>,
    val erreur: Boolean = false
)

/**
 * Pont Kotlin <-> moteur C natif (libdamesnative.so).
 * Source UNIQUE des règles du jeu — utilisée à la fois pour les coups du
 * joueur humain et (via wasm_calculerMeilleurCoup côté C, pas encore
 * exposé ici) pour l'IA. Aucune règle n'est dupliquée en Kotlin.
 */
object MoteurJeu {

    init {
        System.loadLibrary("damesnative")
    }

    private external fun nativePlateauInitial(): ByteArray
    private external fun nativeCoupsPour(plateau: ByteArray, couleur: Int): String
    private external fun nativeJouerCoup(plateau: ByteArray, x1: Int, z1: Int, x2: Int, z2: Int): String

    const val BLANC = 0
    const val NOIR = 1

    fun plateauInitial(): ByteArray = nativePlateauInitial()

    fun coupsPour(plateau: ByteArray, couleur: Int): List<CoupLegal> {
        val json = JSONArray(nativeCoupsPour(plateau, couleur))
        return (0 until json.length()).map { i ->
            val o = json.getJSONObject(i)
            CoupLegal(
                x1 = o.getInt("x1"), z1 = o.getInt("z1"),
                x2 = o.getInt("x2"), z2 = o.getInt("z2"),
                prise = o.getInt("prise") == 1,
                nbPrises = o.getInt("nbPrises")
            )
        }
    }

    fun jouerCoup(plateau: ByteArray, x1: Int, z1: Int, x2: Int, z2: Int): ResultatCoup {
        val o = JSONObject(nativeJouerCoup(plateau, x1, z1, x2, z2))
        if (o.optBoolean("erreur", false)) {
            return ResultatCoup(plateau, prise = false, px = -1, pz = -1, devientDame = false, suite = emptyList(), erreur = true)
        }
        val plateauArr = o.getJSONArray("plateau")
        val nouveauPlateau = ByteArray(100) { i -> plateauArr.getInt(i).toByte() }
        val suiteArr = o.getJSONArray("suite")
        val suite = (0 until suiteArr.length()).map { i ->
            val s = suiteArr.getJSONObject(i)
            s.getInt("x2") to s.getInt("z2")
        }
        return ResultatCoup(
            plateau = nouveauPlateau,
            prise = o.getInt("prise") == 1,
            px = o.getInt("px"), pz = o.getInt("pz"),
            devientDame = o.getInt("devientDame") == 1,
            suite = suite
        )
    }
}
