#include <stdbool.h>


#ifndef _LinkPt
#define _LinkPt


#ifdef __cplusplus
extern "C"
{
#endif


typedef struct LinkPt_t
{
	double m_dX;				// horizontal ordinate (do NOT move this)
	double m_dY;				// vertical ordinate (do NOT move this)
	int m_nX;					// tracks cell X and Y
	int m_nY;					// where point belongs
	struct LinkPt_t* m_pPrev;	// pointer to previous point in this chain
	struct LinkPt_t* m_pNext;	// pointer to next point in this chain
	struct LinkPt_t* m_pPoly;	// pointer to the next polygon
//	struct LinkPt_t* m_pNeigh;	// the coresponding intersection point
//	bool m_bDummy;				// placeholder byte for 8-byte address alignment
//	bool m_bIntersect;			// 1 if an intersection point, 0 otherwise
//	bool m_bEntry;				// 1 if an entry point, 0 otherwise
//	bool m_bVisited;			// 1 if the node has been visited, 0 otherwise
//	float m_fAlpha;				// intersection point placemet
}
LinkPt;


LinkPt* LinkPt_new(int, int, double*);
void LinkPt_del(void*);


#ifdef __cplusplus
}
#endif
#endif
