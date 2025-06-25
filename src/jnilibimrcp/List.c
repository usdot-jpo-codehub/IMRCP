#include <stdlib.h>
#include <string.h>
#include "List.h"


List* List_new()
{
	return List_new_capacity(16);
}


List* List_new_capacity(int nCapacity)
{
	List* pThis = malloc(sizeof(List));
	pThis->m_pElems = malloc(nCapacity * sizeof(void*));
	pThis->m_nCapacity = nCapacity;
	pThis->m_nSize = 0;
	return pThis;
}


void List_del(List* pThis, void (*dealloc)(void*))
{
	if (dealloc != NULL)
	{
		void** pElem = pThis->m_pElems;
		while (pThis->m_nSize-- > 0)
			dealloc(*pElem++);
	}
	free(pThis->m_pElems);
	free(pThis);
}


void List_reserve(List* pThis, int nAdditional)
{
	int nDemand = pThis->m_nSize + nAdditional;
	if (nDemand > pThis->m_nCapacity)
	{
		nDemand--;  // find next larger power of 2
		nDemand |= nDemand >> 1;
		nDemand |= nDemand >> 2;
		nDemand |= nDemand >> 4;
		nDemand |= nDemand >> 8;
		nDemand |= nDemand >> 16;
		nDemand++;

		pThis->m_pElems = realloc(pThis->m_pElems, nDemand * sizeof(void*));
		pThis->m_nCapacity = nDemand;
	}
}


void List_append(List* pThis, void* pElem)
{
	List_reserve(pThis, 1);
	pThis->m_pElems[pThis->m_nSize] = pElem;
	pThis->m_nSize++;
}


void List_insert(List* pThis, void* pElem, int nIndex)
{
	if (pThis->m_nSize == nIndex)
		return List_append(pThis, pElem);

	List_reserve(pThis, 1);
	int nMoveCount = pThis->m_nSize - nIndex;

	void** pSrc = pThis->m_pElems + pThis->m_nSize; // add operator handles address width
	void** pDest = pSrc;
	while (nMoveCount-- > 0)
		*pDest-- = *(--pSrc);

	*pSrc = pElem;
	pThis->m_nSize++;
}


int List_search(List* pThis, void* pElem, int (*comp)(void*, void*))
{
	int nLow = 0;
	int nHigh = pThis->m_nSize;

	--nHigh;
	while (nLow <= nHigh)
	{
		int nMid = (nLow + nHigh) >> 1;
		int nComp = comp(pThis->m_pElems[nMid], pElem);

		if (nComp < 0)
			nLow = nMid + 1;
		else if (nComp > 0)
			nHigh = nMid - 1;
		else
			return nMid; // key found
	}
	return -(nLow + 1); // key not found
}
