#include <stdio.h>
#include "List.h"
#include "Mercator.h"


#ifndef _TileWriter
#define _TileWriter


typedef struct TileWriter_t
{
	int m_nZoom;
	Mercator* m_pM;
	List* m_pTileList;
}
TileWriter;


TileWriter* TileWriter_new(int, int);
void TileWriter_del(void*);
int TileWriter_add_cell(TileWriter*, int, int, double*);
void TileWriter_proc(TileWriter*, FILE*);


#endif
