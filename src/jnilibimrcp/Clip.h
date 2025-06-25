#include "LinkPt.h"


#ifndef _Clip
#define _Clip


typedef struct ClipInfo_t
{
	double m_dMinX;     // bounding corners of polygon
	double m_dMinY;
	double m_dMaxX;
	double m_dMaxY;
	int m_nLen;         // number of points in polygon
	LinkPt* m_pPoly;    // polygon falls outside clip region
}
ClipInfo;


#ifdef __cplusplus
extern "C"
{
#endif


long Clip_make_box(double, double, double, double);
void Clip_free_polygon(long);
int Clip_intersection(long, LinkPt*, int);


#ifdef __cplusplus
}
#endif
#endif
