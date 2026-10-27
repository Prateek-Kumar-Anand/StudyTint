#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "clab_proc.h"

/* Streaming interface used by CEngine / CRun in CLab.kt. */

JNIEXPORT jlong JNICALL
Java_com_student_smartstudy_CEngine_start(JNIEnv *env, jobject thiz, jbyteArray jsrc, jstring jdir, jint timeoutMs, jint mode)
{
    jsize srcLen = (*env)->GetArrayLength(env, jsrc);
    char *src = (char *)malloc((size_t)srcLen + 1);
    const char *dirChars = (*env)->GetStringUTFChars(env, jdir, NULL);
    char *dir = strdup(dirChars ? dirChars : "/");
    ClabProc *p;
    (void)thiz;

    if (dirChars) (*env)->ReleaseStringUTFChars(env, jdir, dirChars);
    if (!src || !dir) { free(src); free(dir); return 0; }
    (*env)->GetByteArrayRegion(env, jsrc, 0, srcLen, (jbyte *)src);
    src[srcLen] = 0;
    p = clab_proc_start(src, dir, timeoutMs, (int)mode);
    free(src);
    free(dir);
    return (jlong)(intptr_t)p;
}

/* Returns [flags, output bytes...]; flags: 1 = waiting for input, 2 = finished. */
JNIEXPORT jbyteArray JNICALL
Java_com_student_smartstudy_CEngine_poll(JNIEnv *env, jobject thiz, jlong handle, jint waitMs)
{
    ClabProc *p = (ClabProc *)(intptr_t)handle;
    char buf[16384];
    int flags = CLAB_FINISHED, n = 0;
    jbyteArray result;
    (void)thiz;

    if (p) n = clab_proc_poll(p, buf, (int)sizeof buf, (int)waitMs, &flags);
    result = (*env)->NewByteArray(env, 1 + n);
    if (result) {
        jbyte f = (jbyte)flags;
        (*env)->SetByteArrayRegion(env, result, 0, 1, &f);
        if (n > 0) (*env)->SetByteArrayRegion(env, result, 1, n, (jbyte *)buf);
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_student_smartstudy_CEngine_write(JNIEnv *env, jobject thiz, jlong handle, jbyteArray jdata)
{
    ClabProc *p = (ClabProc *)(intptr_t)handle;
    jsize len = (*env)->GetArrayLength(env, jdata);
    char *data;
    (void)thiz;
    if (!p || len <= 0) return;
    data = (char *)malloc((size_t)len);
    if (!data) return;
    (*env)->GetByteArrayRegion(env, jdata, 0, len, (jbyte *)data);
    clab_proc_write(p, data, (int)len);
    free(data);
}

JNIEXPORT void JNICALL
Java_com_student_smartstudy_CEngine_closeInput(JNIEnv *env, jobject thiz, jlong handle)
{
    (void)env; (void)thiz;
    clab_proc_close_input((ClabProc *)(intptr_t)handle);
}

JNIEXPORT void JNICALL
Java_com_student_smartstudy_CEngine_kill(JNIEnv *env, jobject thiz, jlong handle)
{
    (void)env; (void)thiz;
    clab_proc_kill((ClabProc *)(intptr_t)handle);
}

JNIEXPORT jstring JNICALL
Java_com_student_smartstudy_CEngine_finish(JNIEnv *env, jobject thiz, jlong handle)
{
    char status[160];
    (void)thiz;
    clab_proc_finish((ClabProc *)(intptr_t)handle, status, sizeof status);
    return (*env)->NewStringUTF(env, status);
}
