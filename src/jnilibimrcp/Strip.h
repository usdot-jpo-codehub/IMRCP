#include "LinkPt.h"


#ifndef _Strip
#define _Strip


typedef struct Strip_t
{
	float m_fVal; // struct position must match tile
	int m_nHead;
	int m_nGroupId;
	int m_nHoleCount;
	LinkPt* m_pTL;
	LinkPt* m_pBR;
}
Strip;


Strip* Strip_new(int, int, int, double*);
void Strip_del(void*);
LinkPt* Strip_merge_top_left(Strip*, LinkPt*);


#endif
