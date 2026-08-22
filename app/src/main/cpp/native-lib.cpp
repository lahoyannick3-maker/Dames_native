#include <jni.h>
#include <stdint.h>

// Déclarations des fonctions exposées par moteur.c (voir le bloc
// "PONT NATIF — RÈGLES DU JEU" dans ce fichier). Pas de moteur.h séparé :
// moteur.c est autonome, ces 3 fonctions sont ses seuls points d'entrée
// utiles côté humain (natif_jouerCoup / natif_coupsPour / natif_plateauInitial).
extern "C" {
    void natif_plateauInitial(int8_t *outFlat);
    char *natif_coupsPour(int8_t *flat, int couleur);
    char *natif_jouerCoup(int8_t *flat, int x1, int z1, int x2, int z2);
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_yannick_damesnative_MoteurJeu_nativePlateauInitial(JNIEnv *env, jobject /* this */) {
    int8_t plateau[100];
    natif_plateauInitial(plateau);

    jbyteArray resultat = env->NewByteArray(100);
    env->SetByteArrayRegion(resultat, 0, 100, plateau);
    return resultat;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_yannick_damesnative_MoteurJeu_nativeCoupsPour(JNIEnv *env, jobject /* this */,
                                                        jbyteArray plateau, jint couleur) {
    jbyte *flat = env->GetByteArrayElements(plateau, nullptr);
    char *json = natif_coupsPour(reinterpret_cast<int8_t *>(flat), couleur);
    jstring resultat = env->NewStringUTF(json);
    env->ReleaseByteArrayElements(plateau, flat, JNI_ABORT); // lecture seule, pas de copie retour
    return resultat;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_yannick_damesnative_MoteurJeu_nativeJouerCoup(JNIEnv *env, jobject /* this */,
                                                        jbyteArray plateau,
                                                        jint x1, jint z1, jint x2, jint z2) {
    jbyte *flat = env->GetByteArrayElements(plateau, nullptr);
    char *json = natif_jouerCoup(reinterpret_cast<int8_t *>(flat), x1, z1, x2, z2);
    jstring resultat = env->NewStringUTF(json);
    env->ReleaseByteArrayElements(plateau, flat, JNI_ABORT);
    return resultat;
}
