#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "Strip.h"


Strip* Strip_new(int nGroupId, int nX, int nY, double* dVals)
{
	LinkPt* pBR = LinkPt_new(nX, nY, dVals + 4); // initial construction
	LinkPt* pBL = LinkPt_new(nX, nY, dVals + 6); // only bottom edge

	pBL->m_pPrev = pBR;
	pBR->m_pNext = pBL; // connect edge points

	Strip* pThis = malloc(sizeof(Strip));
	memset(pThis, 0, sizeof(Strip));
	pThis->m_pBR = pBR;

	pThis->m_fVal = dVals[8];
	pThis->m_nGroupId = nGroupId;
	pThis->m_nHead++; // create head strip

//printf("new_strip\n");
	return pThis;
}


void Strip_del(void* pStrip)
{
	if (pStrip == NULL)
		return;

	Strip* pThis = (Strip*)pStrip;
	if (pThis->m_nHead != 0)
	{
		if (pThis->m_nHoleCount > 0)
		{
			LinkPt* pPoly = pThis->m_pTL->m_pPoly;
			while (pPoly != NULL)
			{
				LinkPt* pDel = pPoly;
				pPoly = pDel->m_pPoly;
				LinkPt_del(pDel);
			}
		}
		LinkPt_del(pThis->m_pTL);
	}
	free(pThis);
}


LinkPt* Strip_merge_top_left(Strip* pLeft, LinkPt* pTopBR)
{
	LinkPt* pHole = pTopBR->m_pNext;
	LinkPt* pCellBR = pLeft->m_pBR; // set by initial left search
	LinkPt* pEdge = pCellBR->m_pPrev;

	pTopBR->m_pNext = pCellBR; // connect top-right point
	pCellBR->m_pPrev = pTopBR; // to bottom-right point

	pHole->m_pPrev = pEdge;
	pEdge->m_pNext = pHole;
//printf("merge_top_left\n");
	return pHole;
}
