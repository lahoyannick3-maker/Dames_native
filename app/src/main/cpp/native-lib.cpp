#include <jni.h>

extern "C" {
#include "moteur.h"
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_yannick_damesnative_MainActivity_nativeGetVersion(JNIEnv* env, jobject /* this */) {
    return env->NewStringUTF(moteur_version());
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_yannick_damesnative_MainActivity_nativeGetPlateauInitial(JNIEnv* env, jobject /* this */) {
    int plateau[100];
    plateau_initial(plateau);

    jintArray resultat = env->NewIntArray(100);
    env->SetIntArrayRegion(resultat, 0, 100, plateau);
    return resultat;
}
