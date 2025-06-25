#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "LinkPt.h"


LinkPt* LinkPt_new(int nX, int nY, double* dPt)
{
	LinkPt* pThis = malloc(sizeof(LinkPt));
	memset(pThis, 0, sizeof(LinkPt));
	memcpy(pThis, dPt, 16);
	pThis->m_nY = nY;
	pThis->m_nX = nX;
//	printf("%ld %.1f %.1f\n", (long)pThis, dX, dY);
	return pThis;
}


void LinkPt_del(void* pThis)
{
	if (pThis == NULL)
		return;

	LinkPt* pPt = (LinkPt*)pThis;
	do
	{
		LinkPt* pDel = pPt;
		pPt = pDel->m_pNext;
		free(pDel);
	}
	while (pPt != pThis && pPt != NULL);
}
