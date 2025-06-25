#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include "Tile.h"
#include "TileWriter.h"


TileWriter* TileWriter_new(int nPPT, int nZoom)
{
	TileWriter* pThis = malloc(sizeof(TileWriter));
	pThis->m_nZoom = nZoom;
	pThis->m_pM = Mercator_new_size(nPPT);
	pThis->m_pTileList = List_new();
	return pThis;
}


void TileWriter_del(void* pTileWriter)
{
	TileWriter* pThis = (TileWriter*)pTileWriter;
	List_del(pThis->m_pTileList, Tile_del);
	free(pThis->m_pM);
	free(pThis);
}


int TileWriter_add_cell(TileWriter* pThis, int nX, int nY, double* dVals)
{
	int nZoom = pThis->m_nZoom;
	int nTile[2];
	Tile oTile;
	Tile* pTile;

//	double dMinX = Math_Min_flt64(dVals[0], dVals[6]);
//	double dMinY = Math_Min_flt64(dVals[5], dVals[7]);
//	double dMaxX = Math_Max_flt64(dVals[2], dVals[4]);
//	double dMaxY = Math_Max_flt64(dVals[1], dVals[3]);

	double dMinX = fmin(dVals[0], dVals[6]);
	double dMinY = fmin(dVals[5], dVals[7]);
	double dMaxX = fmax(dVals[2], dVals[4]);
	double dMaxY = fmax(dVals[1], dVals[3]);

	Mercator_lon_lat_to_tile(pThis->m_pM, dMinX, dMaxY, nZoom, nTile);
	int nStartX = nTile[0]; // does this handle lambert conformal?
	int nStartY = nTile[1];

	Mercator_lon_lat_to_tile(pThis->m_pM, dMaxX, dMinY, nZoom, nTile);
	int nEndX = nTile[0];
	int nEndY = nTile[1];

	for (oTile.m_nY = nStartY; oTile.m_nY <= nEndY; oTile.m_nY++)
	{
//		if (oTile.m_nY != 45) continue;
		for (oTile.m_nX = nStartX; oTile.m_nX <= nEndX; oTile.m_nX++)
		{
//			if (oTile.m_nX != 28) continue;
			int nIndex = List_search(pThis->m_pTileList, &oTile, Tile_comp);
			if (nIndex < 0)
			{
				pTile = Tile_new(oTile.m_nX, oTile.m_nY, nZoom, pThis->m_pM);
				List_insert(pThis->m_pTileList, pTile, ~nIndex); // append new tile to list
			}
			else
				pTile = pThis->m_pTileList->m_pElems[nIndex];

			Tile_add_cell(pTile, nX, nY, dVals);
		}
	}
	return pThis->m_pTileList->m_nSize;
}


void TileWriter_proc(TileWriter* pThis, FILE* pFile)
{
	Tile** pTileIter = (Tile**)pThis->m_pTileList->m_pElems;
	int nCount = pThis->m_pTileList->m_nSize;
	while (nCount-- > 0)
	{
		Tile* pTile = *pTileIter++;
//		fprintf(pFile, "%d,%d\n", pTile->m_nX, pTile->m_nY); // debug tile x and y
		Tile_call(pTile, pThis->m_pM, 9, pFile);
		fwrite(pTile->m_pOut->m_pBuf, pTile->m_pOut->m_nSize, 1, pFile);
	}
}
