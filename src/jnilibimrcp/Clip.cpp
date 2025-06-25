#include <vector>
#include <boost/geometry.hpp>
#include <boost/geometry/geometries/geometries.hpp>
#include "Clip.h"


typedef boost::geometry::model::d2::point_xy<double> Point2D;
typedef boost::geometry::model::polygon<Point2D, true, false> LinkPoly; // clockwise open polygon


long Clip_make_box(double dMinX, double dMinY, double dMaxX, double dMaxY)
{
	LinkPoly* pPoly = new LinkPoly();
	std::vector<Point2D>* pRing = &pPoly->outer();
	pRing->reserve(4);
	pRing->push_back(Point2D(dMinX, dMaxY));
	pRing->push_back(Point2D(dMaxX, dMaxY));
	pRing->push_back(Point2D(dMaxX, dMinY));
	pRing->push_back(Point2D(dMinX, dMinY));
	return (long)pPoly;
}


void Clip_free_polygon(long lPolyRef)
{
	delete (LinkPoly*)lPolyRef;
}


int Clip_intersection(long lClipRef, LinkPt* pStart, int nLen)
{
	LinkPoly oSubj;;
	std::vector<Point2D>* pRing = &oSubj.outer();
	pRing->reserve(nLen);

	LinkPt* pPt = pStart;
	do
	{
		pRing->push_back(Point2D(pPt->m_dX, pPt->m_dY));
		pPt = pPt->m_pNext;
	}
	while (pPt != pStart);

	std::vector<LinkPoly> oResult;
	boost::geometry::intersection(oSubj, *((LinkPoly*)lClipRef), oResult);

	if (oResult.size() > 0)
	{
		std::vector<Point2D>* pRing = &oResult[0].outer(); // always a single polygon without holes?
		int nNewLen = pRing->size();
		if (nNewLen > nLen)
		{
			LinkPt* pPt = pStart->m_pPrev; // modify at list end
			while (nLen++ < nNewLen)
			{
				LinkPt* pNewPt = LinkPt_new(0, 0, (double*)pPt); // convenient dummy coordinates
				pNewPt->m_pNext = pPt->m_pNext; // insert new point
				pNewPt->m_pPrev = pPt; // between existing points
				pPt->m_pNext = pNewPt;
				pPt = pNewPt;
			}
		}

		for (int nPos = 0; nPos < nNewLen; nPos++)
		{
			Point2D oPt = (*pRing)[nPos];
			pStart->m_dX = oPt.x();
			pStart->m_dY = oPt.y();
			pStart = pStart->m_pNext;
		}
		return nNewLen;
	}
	return nLen; // no changes
}
