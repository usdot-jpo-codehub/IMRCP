#include <math.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "XzBuffer.h"
#include "Hpfp.h"
#include "Strip.h"
#include "Tile.h"


static void Tile_write_ring(Tile* pThis, Mercator* pM, LinkPt* pStart, int nLen, bool bIsHole, DataBuffer* pOut, FILE* pFile)
{
//	fprintf(pFile, "%d,", nLen);
	int nPts[nLen << 1]; // array for transforming points
	int* pPtIter = nPts;
	LinkPt* pPt = pStart;
	if (bIsHole)
	{
		int nX = pPt->m_nX, nY = pPt->m_nY;
		LinkPt* pPtTL = pPt;
		do
		{
			if (pPt->m_nY < nY) // check for top-most row
			{
				nY = pPt->m_nY; // set new vertical
				nX = pPt->m_nX; // reset horizontal
				pPtTL = pPt;
			}
			else if (pPt->m_nY == nY) // in top-most row
			{
				if (pPt->m_nX < nX) // check for left-most cell
				{
					nX = pPt->m_nX; // set new horizontal
					pPtTL = pPt;
				}
				else if (pPt->m_nX == nX && pPt->m_dX < pPtTL->m_dX)
					pPtTL = pPt; // set left-most point of top-left cell
			}
			pPt = pPt->m_pNext;
		}
		while (pPt != pStart);

		pPt = pPtTL; // holes are not clipped
		do
		{
//			fprintf(pFile, "%f,%f,", pPt->m_dX, pPt->m_dY);
			Mercator_lon_lat_to_tile_pixels(pM, pPt->m_dX, pPt->m_dY, pThis->m_nX, pThis->m_nY, pThis->m_nZoom, pPtIter);
			pPtIter += 2;
			pPt = pPt->m_pPrev; // reverse point order
		}
		while (pPt != pPtTL);
	}
	else
	{
		int nPos = nLen; // use size to iterate instead of start point
		while (nPos-- > 0) // there may be leftover points from clipping
		{
//			fprintf(pFile, "%f,%f,", pPt->m_dX, pPt->m_dY);
			Mercator_lon_lat_to_tile_pixels(pM, pPt->m_dX, pPt->m_dY, pThis->m_nX, pThis->m_nY, pThis->m_nZoom, pPtIter);
			pPtIter += 2;
			pPt = pPt->m_pNext;
		}
	}

	pPtIter -= 2; // iterator is at end of array
	int* pPtPrev = pPtIter - 2;
	int nPos = nLen; // reset index tracking
	while (--nPos > 0) // calculate point offsets
	{
		*pPtIter = *pPtIter - *pPtPrev; // save X offset
		*(pPtIter + 1) = *(pPtIter + 1) - *(pPtPrev + 1); // save Y offset
		pPtIter = pPtPrev;
		pPtPrev -= 2;
	}

	DataBuffer_write_short(pOut, nLen);
	pPtIter = nPts; // write geometry to buffer
	while (nLen-- > 0)
	{
//		fprintf(pFile, "%d,", *pPtIter);
		DataBuffer_write_short(pOut, *pPtIter++);
//		fprintf(pFile, "%d,", *pPtIter);
		DataBuffer_write_short(pOut, *pPtIter++);
	}
}


Tile* Tile_new(int nX, int nY, int nZoom, Mercator* pM)
{
	Tile* pThis = malloc(sizeof(Tile));
//	for (int nPos = 0; nPos < 1000; nPos++)
//		pThis->m_nHoleHist[nPos] = pThis->m_nPolyHist[nPos] = 0;

	pThis->m_fVal = -9999.0;
//	pThis->m_nCount = 0; // debugging counter
	pThis->m_nX = nX;
	pThis->m_nY = nY;
	pThis->m_nZoom = nZoom;
	pThis->m_nCurrRow = pThis->m_nCurrMin = pThis->m_nCurrMax = -1;
	Mercator_lon_lat_bounds(pM, nX, nY, nZoom, pThis->m_dBounds);
	pThis->m_pStripList = List_new();
	pThis->m_pCurrRow = List_new();
	pThis->m_pPrevRow = List_new();
	pThis->m_pOut = DataBuffer_new();
	return pThis;
}


void Tile_del(void* pTile)
{
//	printf("Tile_del\n");
	if (pTile == NULL)
		return;

	Tile* pThis = (Tile*)pTile;
	List_del(pThis->m_pPrevRow, NULL);
	List_del(pThis->m_pCurrRow, NULL);
	List_del(pThis->m_pStripList, Strip_del);
	DataBuffer_del(pThis->m_pOut);
	free(pThis);
}


void Tile_add_cell(Tile* pThis, int nCellX, int nCellY, double* dVals)
{
	bool bOutside = true;
	int nSize = 4;
	double* pBounds = pThis->m_dBounds; // verify cell is in tile
	double* pValIter = dVals;
	while (bOutside && nSize-- > 0)
	{
		double dX = *pValIter++;
		double dY = *pValIter++;
		bOutside = dX >= pBounds[2] || dY >= pBounds[3] || dX <= pBounds[0] || dY <= pBounds[1];
	}

	if (bOutside) // cell bounds touched tile
		return; // but no points are inside

	Strip *pCell, *pLeft, *pTop;
	pCell = pLeft = pTop = NULL;
	float fVal = (float)dVals[8]; // convenience cast
	List* pStrips = pThis->m_pStripList; // get chains from pair
	if (nCellY == pThis->m_nCurrRow)
	{
		Strip** pStripIter = (Strip**)pStrips->m_pElems + pStrips->m_nSize - 1; // start at last element
		Strip* pTest = *pStripIter; // first check for matching left
		if (pThis->m_nCurrMax == nCellX && pTest->m_fVal == fVal)
		{
			pCell = pLeft = pTest;
			LinkPt* pTopBL = pLeft->m_pBR->m_pPrev;
			LinkPt* pTopBR = pTopBL->m_pPrev; // check top corner
			if (pTopBR->m_dX == dVals[2] && pTopBR->m_dY == dVals[3])
				pTop = pLeft; // skips further top processing

			memcpy(pTopBL, dVals + 4, 16); // update upper inside corner
			pTopBL->m_nY = nCellY; // update cell tracking
			pTopBL->m_nX = nCellX;
			pLeft->m_pBR = pTopBL; // update left bottom-right point
		}
		else
		{
			pCell = Strip_new(pStrips->m_nSize, nCellX, nCellY, dVals);
			List_append(pStrips, pCell); // add incomplete cell
		}
	}
	else
	{
		if (nCellY - pThis->m_nCurrRow == 1) // cell y should be greater
		{
			List* pTempList = pThis->m_pPrevRow; // swap row lists
			pThis->m_pPrevRow = pThis->m_pCurrRow;
			pThis->m_pCurrRow = pTempList;
			pThis->m_nPrevMin = pThis->m_nCurrMin;
			pThis->m_nPrevMax = pThis->m_nCurrMax;
		}
		else // clear prev row no need to swap
		{
			pThis->m_pPrevRow->m_nSize = 0;
			pThis->m_nPrevMin = pThis->m_nPrevMax = -1;
		}
		pThis->m_pCurrRow->m_nSize = 0; // new cell conditions
		pThis->m_nCurrMin = pThis->m_nCurrMax = nCellX; // setup for range fill later
		pThis->m_nCurrRow = nCellY;

		pCell = Strip_new(pStrips->m_nSize, nCellX, nCellY, dVals);
		List_append(pStrips, pCell); // add incomplete cell
	}

	if (pTop == NULL) // skipped when inside corner
	{
		LinkPt* pTopBR; // search for cell above
		if (nCellX < pThis->m_nPrevMax && nCellX >= pThis->m_nPrevMin) // range check handles shifts and gaps
		{
			Strip* pTest = (Strip*)pThis->m_pPrevRow->m_pElems[nCellX - pThis->m_nPrevMin];
			if (pTest->m_fVal == fVal)  // value match check
			{
				double dBotTR = dVals[2];
				pTopBR = pTest->m_pBR;
				while (pTopBR->m_dX > dBotTR && pTopBR->m_pNext != pTopBR) // infinite loop check
					pTopBR = pTopBR->m_pNext;

				if (pTopBR->m_dX == dBotTR && pTopBR->m_dY == dVals[3])
					pTop = pTest;
			}
		}

		if (pTop == NULL) // no cell above found
		{
			LinkPt* pTL = LinkPt_new(nCellX, nCellY, dVals); // finish constructing
			LinkPt* pTR = LinkPt_new(nCellX, nCellY, dVals + 2); // cell top edge
			LinkPt* pBR = pCell->m_pBR;
			LinkPt* pBL = pBR->m_pNext;

			if (pLeft == NULL) // stand-alone cell
			{
				pCell->m_pTL = pBL->m_pNext = pTL;
				pTL->m_pPrev = pBL;
			}
			else
			{
				LinkPt* pBotTL = pBR->m_pPrev; // left makes bottom-right triangle
				pBotTL->m_pNext = pTL;
				pTL->m_pPrev = pBotTL;
			}

			pBR->m_pPrev = pTR;
			pTR->m_pNext = pBR;
			pTR->m_pPrev = pTL;
			pTL->m_pNext = pTR;
		}
		else
		{
			if (pLeft == NULL) // merge top only
			{
				LinkPt* pTopBL = pTopBR->m_pNext;
				LinkPt* pCellBR = pCell->m_pBR;
				LinkPt* pCellBL = pCellBR->m_pNext;

				pTopBL->m_pPrev = pCellBL; // connect top edge points
				pCellBL->m_pNext = pTopBL;
				pCellBR->m_pPrev = pTopBR;
				pTopBR->m_pNext = pCellBR;

				pCell->m_nGroupId = pTop->m_nGroupId; // merge to lower group
				pCell->m_nHead = 0; // no longer a head strip
			}
			else
			{
				LinkPt* pHole = Strip_merge_top_left(pLeft, pTopBR);
				if (pLeft->m_nGroupId == pTop->m_nGroupId) // inside point is a hole
				{
					Strip* pToStrip = pStrips->m_pElems[pLeft->m_nGroupId]; // group ids are the same
					pHole->m_pPoly = pToStrip->m_pTL->m_pPoly;
					pToStrip->m_pTL->m_pPoly = pHole;
					pToStrip->m_nHoleCount++;
				}
				else
				{
					int nFromGroup, nToGroup;
					if (pLeft->m_nGroupId < pTop->m_nGroupId)
					{
						nFromGroup = pTop->m_nGroupId;
						nToGroup= pLeft->m_nGroupId;
					}
					else
					{
						nToGroup = pTop->m_nGroupId;
						nFromGroup= pLeft->m_nGroupId;
					}

					Strip* pToStrip = pStrips->m_pElems[nToGroup];
					Strip* pFromStrip = pStrips->m_pElems[nFromGroup];
					if (pFromStrip->m_nHoleCount > 0) // move existing holes to lower group id
					{
						LinkPt* pFromHole = pFromStrip->m_pTL->m_pPoly;
						LinkPt* pEndHole = pFromHole;
						while (pEndHole->m_pPoly != NULL)
							pEndHole = pEndHole->m_pPoly;

						pEndHole->m_pPoly = pToStrip->m_pTL->m_pPoly;
						pToStrip->m_pTL->m_pPoly = pFromHole;
						pToStrip->m_nHoleCount += pFromStrip->m_nHoleCount;
						pFromStrip->m_nHoleCount = 0;
						pFromStrip->m_pTL->m_pPoly = NULL;
					}

					nSize = pStrips->m_nSize;
					Strip** pStripIter = (Strip**)pStrips->m_pElems + --nSize; // start at last element
					while (nSize >= nFromGroup && nSize-- > 0) // group ids are the original insertion index
					{
						Strip* pStrip = *pStripIter;
						if (pStrip->m_nGroupId == nFromGroup)
						{
							pStrip->m_nGroupId = nToGroup;
							pStrip->m_nHead = 0; // no longer a head strip
						}
						--pStripIter;
					}
				}
			}
		}
	}

	int nFill = nCellX - pThis->m_nCurrMax;
	while (nFill-- > 0) // insert placeholder for gaps
		List_append(pThis->m_pCurrRow, pThis);

	List_append(pThis->m_pCurrRow, pCell);
	pThis->m_nCurrMax = ++nCellX;

//printf("\n");
//	Tile_print_strips(pThis);
}


static void Tile_simplibounds(Tile* pThis, LinkPt* pStart, ClipInfo* pClipInfo)
{
	LinkPt* pPrevPt = pStart; // presumes at least 3 points
	LinkPt* pCurrPt = pPrevPt->m_pNext;
	LinkPt* pNextPt = pCurrPt->m_pNext;

	int nLen = 1; // account for first point
	double dMinX, dMinY, dMaxX, dMaxY;
	dMinX = dMaxX = pStart->m_dX;
	dMinY = dMaxY = pStart->m_dY;

	do
	{
		double dCross = (pCurrPt->m_dX - pPrevPt->m_dX) * (pNextPt->m_dY - pCurrPt->m_dY) -
			(pNextPt->m_dX - pCurrPt->m_dX) * (pCurrPt->m_dY - pPrevPt->m_dY);

		if (fabs(dCross) < 0.0000005) // point is collinear
		{
			free(pCurrPt); // free unused point
			pPrevPt->m_pNext = pNextPt; // re-link next and prev points
			pNextPt->m_pPrev = pPrevPt;
		}
		else
		{
			if (pCurrPt->m_dX < dMinX)
				dMinX = pCurrPt->m_dX;

			if (pCurrPt->m_dX > dMaxX)
				dMaxX = pCurrPt->m_dX;

			if (pCurrPt->m_dY < dMinY)
				dMinY = pCurrPt->m_dY;

			if (pCurrPt->m_dY > dMaxY)
				dMaxY = pCurrPt->m_dY;

			pPrevPt = pCurrPt;
			++nLen;
		}
		pCurrPt = pNextPt;
		pNextPt = pNextPt->m_pNext;
	}
	while (pCurrPt != pStart); // ensure keeping top-left point

	double* pBounds = (double*)pClipInfo;
	*pBounds++ = dMinX;
	*pBounds++ = dMinY;
	*pBounds++ = dMaxX;
	*pBounds = dMaxY;
	pClipInfo->m_nLen = nLen;
	pClipInfo->m_pPoly = pStart; // initially keep polygon
}


int Tile_call(Tile* pThis, Mercator* pM, int nLevel, FILE* pFile)
{
	double* dBounds = pThis->m_dBounds;
	double dMinX = *dBounds++; // make local value copies
	double dMinY = *dBounds++;
	double dMaxX = *dBounds++;
	double dMaxY = *dBounds;
	long lClipRef = Clip_make_box(dMinX, dMinY, dMaxX, dMaxY);

	DataBuffer* pOut = pThis->m_pOut;
	Strip** pStripIter = (Strip**)pThis->m_pStripList->m_pElems;
	int nSize = pThis->m_pStripList->m_nSize;
	while (nSize-- > 0)
	{
		Strip* pOuter = *pStripIter++; // only write root polygons
		if (pOuter->m_nHead != 0)
		{
//			printf("%d\n", nSize);
			int nRec = pOuter->m_nHoleCount + 1;
			int nRingCount = nRec;
			ClipInfo oClipInfo[nRingCount];

			ClipInfo* pInfoIter = oClipInfo; // populate list with simplified rings
			Tile_simplibounds(pThis, pOuter->m_pTL, pInfoIter);
			if (--nRec > 0)
			{
				LinkPt* pHole = pOuter->m_pTL->m_pPoly;
				while (pHole != NULL)
				{
					Tile_simplibounds(pThis, pHole, ++pInfoIter);
					pHole = pHole->m_pPoly;
				}
			}

			pInfoIter = oClipInfo; // reset clip info iterator
			dBounds = (double*)pInfoIter; // pointer overlays struct double elements
			if (*dBounds++ < dMinX || *dBounds++ < dMinY || *dBounds++ > dMaxX || *dBounds > dMaxY)
				pInfoIter->m_nLen = Clip_intersection(lClipRef, pInfoIter->m_pPoly, pInfoIter->m_nLen);

			nRec = 0; // reset record index
//			fprintf(pFile, "%d,", nRingCount);
			DataBuffer_write_short(pOut, nRingCount); // start polygon record
			while (nRec < nRingCount) // finish writing remaiing rings
			{
				Tile_write_ring(pThis, pM, pInfoIter->m_pPoly, pInfoIter->m_nLen, nRec++ > 0, pOut, pFile);
				++pInfoIter;
			}
//			fprintf(pFile, "%d\n", Hpfp_to_hpfp(pOuter->m_fVal));
			DataBuffer_write_short(pOut, Hpfp_to_hpfp(pOuter->m_fVal)); // finish polygon record
		}
	}
	Clip_free_polygon(lClipRef);

	XzBuffer* pXz = XzBuffer_new(nLevel);
	XzBuffer_proc(pXz, pOut->m_nSize, pOut->m_pBuf);
	if (pXz->count < pOut->m_nSize)
	{
		pOut->m_nSize = pXz->count; // truncate buffer
		XzBuffer_copy(pXz, pOut->m_nSize, pOut->m_pBuf);
	}
	XzBuffer_del(pXz);
	return pOut->m_nSize;
}


int Tile_comp(void* pLhs, void* pRhs)
{
	if (((Tile*)pLhs)->m_nY == ((Tile*)pRhs)->m_nY)
		return ((Tile*)pLhs)->m_nX - ((Tile*)pRhs)->m_nX;

	return ((Tile*)pLhs)->m_nY - ((Tile*)pRhs)->m_nY;
}
