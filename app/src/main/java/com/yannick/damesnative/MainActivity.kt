package com.yannick.damesnative

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        // Charge libdamesnative.so (compilée depuis moteur.c + native-lib.cpp)
        init {
            System.loadLibrary("damesnative")
        }
    }

    // --- Déclarations des fonctions natives (JNI) ---
    external fun nativeGetVersion(): String
    external fun nativeGetPlateauInitial(): IntArray

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Appel du moteur C pour récupérer le plateau initial (10x10 = 100 cases)
        val plateau = nativeGetPlateauInitial()

        // Affichage 2D basique pour valider la chaîne JNI -> Kotlin -> écran
        val boardView = BoardView(this, plateau)
        setContentView(boardView)

        Toast.makeText(this, "Moteur chargé : " + nativeGetVersion(), Toast.LENGTH_LONG).show()
    }
}
