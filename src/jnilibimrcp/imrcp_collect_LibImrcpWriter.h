/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class imrcp_collect_LibImrcpWriter */

#ifndef _Included_imrcp_collect_LibImrcpWriter
#define _Included_imrcp_collect_LibImrcpWriter
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     imrcp_collect_LibImrcpWriter
 * Method:    init
 * Signature: (II)J
 */
JNIEXPORT jlong JNICALL Java_imrcp_collect_LibImrcpWriter_init
  (JNIEnv *, jclass, jint, jint);

/*
 * Class:     imrcp_collect_LibImrcpWriter
 * Method:    addCell
 * Signature: (JII[D)I
 */
JNIEXPORT jint JNICALL Java_imrcp_collect_LibImrcpWriter_addCell
  (JNIEnv *, jclass, jlong, jint, jint, jdoubleArray);

/*
 * Class:     imrcp_collect_LibImrcpWriter
 * Method:    process
 * Signature: (J[III)V
 */
JNIEXPORT void JNICALL Java_imrcp_collect_LibImrcpWriter_process
  (JNIEnv *, jclass, jlong, jintArray, jint, jint);

/*
 * Class:     imrcp_collect_LibImrcpWriter
 * Method:    getData
 * Signature: (J[BI)V
 */
JNIEXPORT void JNICALL Java_imrcp_collect_LibImrcpWriter_getData
  (JNIEnv *, jclass, jlong, jbyteArray, jint);

/*
 * Class:     imrcp_collect_LibImrcpWriter
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_imrcp_collect_LibImrcpWriter_free
  (JNIEnv *, jclass, jlong);

#ifdef __cplusplus
}
#endif
#endif
