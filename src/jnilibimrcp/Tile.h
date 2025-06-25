#include <stdio.h>
#include "Clip.h"
#include "DataBuffer.h"
#include "List.h"
#include "Mercator.h"


#ifndef _Tile
#define _Tile


typedef struct Tile_t
{
	float m_fVal; // filler value for cell comparison
	int m_nX;
	int m_nY;
	int m_nZoom;
	int m_nCurrMin;
	int m_nCurrMax;
	int m_nCurrRow;
	int m_nPrevMin;
	int m_nPrevMax;
	int m_nPrevCol;
	double m_dBounds[4];
	List* m_pCurrRow;
	List* m_pPrevRow;
	List* m_pStripList;
	DataBuffer* m_pOut;
}
Tile;


Tile* Tile_new(int, int, int, Mercator*);
void Tile_del(void*);
void Tile_add_cell(Tile*, int, int, double*);
int Tile_call(Tile*, Mercator*, int, FILE*);
int Tile_comp(void*, void*);


#endif
