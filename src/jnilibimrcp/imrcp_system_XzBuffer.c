#include <stdlib.h>
#include <string.h>
#include "imrcp_system_XzBuffer.h"
#include "XzBuffer.h"


JNIEXPORT jlong JNICALL Java_imrcp_system_XzBuffer_init(JNIEnv* pEnv, jclass pClass, 
	jint nLevel, jint nChunkSize, jint nDefaultElems)
{
	return (jlong)XzBuffer_init(nLevel, nChunkSize, nDefaultElems);
}


JNIEXPORT jint JNICALL Java_imrcp_system_XzBuffer_proc(JNIEnv* pEnv, jclass pClass, 
	jlong lXzBufferRef, jbyteArray yInBuf)
{
	XzBuffer* pThis = (XzBuffer*)lXzBufferRef;
	jbyte* pInBuf = (*pEnv)->GetByteArrayElements(pEnv, yInBuf, NULL);
	jint nCount = XzBuffer_proc(pThis, (*pEnv)->GetArrayLength(pEnv, yInBuf), pInBuf);
	(*pEnv)->ReleaseByteArrayElements(pEnv, yInBuf, pInBuf, JNI_ABORT); // abort and release
	return nCount;
}


JNIEXPORT void JNICALL Java_imrcp_system_XzBuffer_free(JNIEnv* pEnv, jclass pClass, 
	jlong lXzBufferRef, jbyteArray yOutBuf)
{
	XzBuffer* pThis = (XzBuffer*)lXzBufferRef;
	jbyte* pOutBuf = (*pEnv)->GetByteArrayElements(pEnv, yOutBuf, NULL);
	XzBuffer_copy(pThis, pThis->count, pOutBuf);
	(*pEnv)->ReleaseByteArrayElements(pEnv, yOutBuf, pOutBuf, 0); // copy back and release
	XzBuffer_del(pThis);
}
