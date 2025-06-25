#include <string.h>
#include "imrcp_collect_LibImrcpWriter.h"
#include "Tile.h"
#include "TileWriter.h"


JNIEXPORT jlong JNICALL Java_imrcp_collect_LibImrcpWriter_init
	(JNIEnv* pEnv, jclass pClass, jint nPPT, jint nZoom)
{
	return (jlong)TileWriter_new(nPPT, nZoom);
}


JNIEXPORT jint JNICALL Java_imrcp_collect_LibImrcpWriter_addCell
	(JNIEnv* pEnv, jclass pClass, jlong lTileWriterRef, jint nCellX, jint nCellY, jdoubleArray dCell)
{
	jdouble* dVals = (*pEnv)->GetDoubleArrayElements(pEnv, dCell, NULL);
	TileWriter* pThis = (TileWriter*)lTileWriterRef;
	int nSize = TileWriter_add_cell(pThis, nCellX, nCellY, dVals);
	(*pEnv)->ReleaseDoubleArrayElements(pEnv, dCell, dVals, JNI_ABORT); // abort and release
	return nSize;
}


JNIEXPORT void JNICALL Java_imrcp_collect_LibImrcpWriter_process
	(JNIEnv* pEnv, jclass pClass, jlong lTileWriterRef, jintArray nTileInfo, jint nLevel, jint nIndex)
{
	TileWriter* pThis = (TileWriter*)lTileWriterRef;
	Tile* pTile = pThis->m_pTileList->m_pElems[nIndex];

	jint* pTileInfo = (*pEnv)->GetIntArrayElements(pEnv, nTileInfo, NULL);
	memcpy(pTileInfo, &pTile->m_nX, sizeof(long));
	pTileInfo[2] = Tile_call(pTile, pThis->m_pM, nLevel, NULL); // finalize clipping and compress results
	(*pEnv)->ReleaseIntArrayElements(pEnv, nTileInfo, pTileInfo, 0); // copy back and release
}


JNIEXPORT void JNICALL Java_imrcp_collect_LibImrcpWriter_getData
	(JNIEnv* pEnv, jclass pClass, jlong lTileWriterRef, jbyteArray yOutBuf, jint nIndex)
{
	Tile* pTile = ((TileWriter*)lTileWriterRef)->m_pTileList->m_pElems[nIndex];
	jbyte* pOutBuf = (*pEnv)->GetByteArrayElements(pEnv, yOutBuf, NULL);
	memcpy(pOutBuf, pTile->m_pOut->m_pBuf, pTile->m_pOut->m_nSize); // output buffer at least this size
	(*pEnv)->ReleaseByteArrayElements(pEnv, yOutBuf, pOutBuf, 0); // copy back and release
}


JNIEXPORT void JNICALL Java_imrcp_collect_LibImrcpWriter_free
	(JNIEnv* pEnv, jclass pClass, jlong lTileWriterRef)
{
	TileWriter_del((TileWriter*)lTileWriterRef);
}
