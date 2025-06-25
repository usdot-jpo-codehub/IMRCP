#include <stdlib.h>
#include <string.h>
#include "XzBuffer.h"


XzBuffer* XzBuffer_init(int nLevel, int nChunkSize, int nDefaultElems)
{
	XzBuffer* pThis = malloc(sizeof(XzBuffer)); // XzBuffer extends lzma_stream
	memset(pThis, 0, sizeof(XzBuffer)); // initialize dynamic structure

	lzma_stream* pLzmaStream = (lzma_stream*)pThis;
	lzma_ret ret = -1;
	if (nLevel > 0) // compression level 0 indicates decompress mode
	{
		if (nLevel == 9)
			nLevel |= LZMA_PRESET_EXTREME;

		ret = lzma_easy_encoder(pLzmaStream, nLevel, LZMA_CHECK_CRC64);
	}
	else
		ret = lzma_stream_decoder(pLzmaStream, UINT64_MAX, LZMA_CONCATENATED);

	if (ret != LZMA_OK)
	{
		free(pThis); // release allocated structure
		return NULL;
	}

	if (nChunkSize > 0) // chunk size of 0 indicates streaming mode
	{
		pThis->elems = malloc(nDefaultElems * sizeof(void*));
		pThis->capacity = nDefaultElems;
		pThis->chunksize = nChunkSize;
		pThis->count = 0;
		pThis->size = 1;
		pThis->elems[0] = malloc(nChunkSize); // allocate first chunk
	}
	return pThis;
}


XzBuffer* XzBuffer_new(int nLevel)
{
	return XzBuffer_init(nLevel, 65536, 4); // initial capacity 256KiB
}


XzBuffer* XzBuffer_new_stream(int nLevel)
{
	return XzBuffer_init(nLevel, 0, 0); // no buffer in stream mode
}


void XzBuffer_del(void* pXzBuffer)
{
	if (pXzBuffer == NULL)
		return;

	XzBuffer* pThis = (XzBuffer*)pXzBuffer;
	char** pElems = (char**)pThis->elems;
	while (pThis->size-- > 0) // free allocated chunks
		free(*pElems++);

	free(pThis->elems);
	lzma_end((lzma_stream*)pThis); // release stream resources
	free(pThis);
}


int XzBuffer_proc(XzBuffer* pThis, int nSize, void* pInBuf)
{
	lzma_stream* pLzmaStream = (lzma_stream*)pThis;

	pLzmaStream->next_in = pInBuf;
	pLzmaStream->avail_in = nSize;
	pLzmaStream->next_out = pThis->elems[0];
	pLzmaStream->avail_out = pThis->chunksize;

	lzma_action action = LZMA_RUN;
	lzma_ret ret = LZMA_OK;
	while (ret == LZMA_OK)
	{
		if (pLzmaStream->avail_in == 0)
			action = LZMA_FINISH;

		ret = lzma_code(pLzmaStream, action);
		if (pLzmaStream->avail_out == 0 || ret == LZMA_STREAM_END)
		{
			pThis->count += pThis->chunksize - pLzmaStream->avail_out;
			if (ret != LZMA_STREAM_END)
			{
				if (pThis->size == pThis->capacity)
				{
					int nCapacity = 3 * pThis->capacity / 2;
					pThis->elems = realloc(pThis->elems, nCapacity * sizeof(void*));
					pThis->capacity = nCapacity;
				}
				pThis->elems[pThis->size++] = pLzmaStream->next_out = malloc(pThis->chunksize); // reset output buffer
				pLzmaStream->avail_out = pThis->chunksize;
			}
		}
	}
	if (ret != LZMA_OK && ret != LZMA_STREAM_END)
		return ret;

	return pThis->count; // output buffer size
}


void XzBuffer_copy(XzBuffer* pThis, int nOutSize, void* pOutBuf)
{
	char** pElems = (char**)pThis->elems;
	if (pElems == NULL || pThis->count == 0 || pOutBuf == NULL || nOutSize == 0)
		return;

	if (nOutSize > pThis->count)
		nOutSize = pThis->count;

	while (nOutSize > 0) // copy available bytes to output buffer
	{
		int nCount = pThis->chunksize;
		if (nCount > nOutSize)
			nCount = nOutSize;

		memcpy(pOutBuf, *pElems, nCount);
		nOutSize -= nCount;
		pOutBuf += nCount;
		++pElems;
	}
}
