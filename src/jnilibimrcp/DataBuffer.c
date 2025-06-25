#include <stdlib.h>
#include <string.h>
#include "DataBuffer.h"


const int CHUNK_SIZE = 65536;


DataBuffer* DataBuffer_new()
{
	DataBuffer* pThis = malloc(sizeof(DataBuffer));
	pThis->m_nCapacity = CHUNK_SIZE;
	pThis->m_nSize = 0;
	pThis->m_pBuf = malloc(CHUNK_SIZE);
	return pThis;
}


void DataBuffer_del(void* pDataBuffer)
{
	if (pDataBuffer == NULL)
		return;

	DataBuffer* pThis = (DataBuffer*)pDataBuffer;
	free(pThis->m_pBuf);
	free(pThis);
}


static void write_bytes(DataBuffer* pThis, void* pData, int nLen)
{
	int nDemand = pThis->m_nSize + nLen;
	if (pThis->m_nCapacity < nDemand)
	{
		int nNewCap = CHUNK_SIZE * (1 + nDemand / CHUNK_SIZE);
		pThis->m_pBuf = realloc(pThis->m_pBuf, nNewCap);
		pThis->m_nCapacity = nNewCap;
	}
	memcpy(pThis->m_pBuf + pThis->m_nSize, pData, nLen);
	pThis->m_nSize += nLen;
}


void DataBuffer_write_short(DataBuffer* pThis, int nVal)
{
	int nBuf = (nVal & 0x000000FF) << 8 | (nVal & 0x0000FF00) >> 8;
	write_bytes(pThis, &nBuf, 2);
}
