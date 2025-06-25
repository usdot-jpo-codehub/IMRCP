#include <limits>
#include <vector>
#include <stdio.h>
#include <boost/geometry.hpp>
#include <boost/geometry/geometries/geometries.hpp>
#include "imrcp_geosrv_GeoUtil.h"


const double SCALE = 10000000.0;
typedef boost::geometry::model::d2::point_xy<double> Point2D;
typedef boost::geometry::model::box<Point2D> Box2D;
typedef boost::geometry::model::polygon<Point2D, true, false> Polygon2D;

typedef struct Bounds_t
{
	int m_nMinX;
	int m_nMinY;
	int m_nMaxX;
	int m_nMaxY;
	Box2D* m_pBox;
}
Bounds;


int* make_ring(std::vector<Point2D>* pRing, int* pIntIter)
{
	int nPointCount = *pIntIter++; // ring point count
	pRing->reserve(nPointCount);
	pIntIter += 4; // skip bounding box
	while (nPointCount-- > 0)
	{
		double dX = *pIntIter++;
		double dY = *pIntIter++;
		pRing->push_back(Point2D(dX / SCALE, dY / SCALE));
	}
	return pIntIter; // update calling pointer position
}


int* save_ring(std::vector<Point2D>* pRing, int* pIntIter)
{
	int nPointCount = pRing->size();
	*pIntIter++ = nPointCount; // set point count
	int* pBounds = pIntIter; // set bounding box later
	pIntIter += 4; // skip ahead

	int nMinX, nMinY, nMaxX, nMaxY;
	nMinX = nMinY  = std::numeric_limits<int>::max();
	nMaxX = nMaxY  = std::numeric_limits<int>::min();

	for (int nPos = 0; nPos < nPointCount; nPos++)
	{
		Point2D* pPt = &((*pRing)[nPos]);
		int nX = (int)(pPt->x() * SCALE);
		int nY = (int)(pPt->y() * SCALE);

		if (nX < nMinX)
			nMinX = nX;

		if (nX > nMaxX)
			nMaxX = nX;

		if (nY < nMinY)
			nMinY = nY;

		if (nY > nMaxY)
			nMaxY = nY;

		*pIntIter++ = nX;
		*pIntIter++ = nY;
	}

	*pBounds++ = nMinX;
	*pBounds++ = nMinY;
	*pBounds++ = nMaxX;
	*pBounds = nMaxY;
	return pIntIter;
}


JNIEXPORT jlong JNICALL Java_imrcp_geosrv_GeoUtil_makeBox(JNIEnv* pEnv, jclass pClass, jintArray nBox)
{
	jint* pBox = pEnv->GetIntArrayElements(nBox, NULL);

	jint* pBoxIter = pBox + 3;
	int nMinX = *pBoxIter++;
	int nMinY = *pBoxIter++;
	int nMaxX = *pBoxIter++;
	int nMaxY = *pBoxIter;

	Bounds* pBounds = new Bounds();
	pBounds->m_nMinX = nMinX;
	pBounds->m_nMinY = nMinY;
	pBounds->m_nMaxX = nMaxX;
	pBounds->m_nMaxY = nMaxY;
	pBounds->m_pBox = new Box2D(Point2D(nMinX, nMinY), Point2D(nMaxX, nMaxY));

	pEnv->ReleaseIntArrayElements(nBox, pBox, JNI_ABORT); // abort and release
	return (jlong)pBounds;
}


JNIEXPORT jlong JNICALL Java_imrcp_geosrv_GeoUtil_makePolygon(JNIEnv* pEnv, jclass pClass, jintArray nPolygon)
{
	jint* pPolygon = pEnv->GetIntArrayElements(nPolygon, NULL);

	int* pIntIter = pPolygon + 1; // skip insertion index
	int nHoleCount = *pIntIter++; // initially total ring count

	Polygon2D* pPoly = new Polygon2D();
	pIntIter = make_ring(&(pPoly->outer()), pIntIter);
	if (--nHoleCount > 0) // pre-decrement skips outer ring
	{
		pPoly->inners().resize(nHoleCount); // allocate hole point vectors
		while (nHoleCount-- > 0)
			pIntIter = make_ring(&(pPoly->inners()[nHoleCount]), pIntIter);
	}
	pEnv->ReleaseIntArrayElements(nPolygon, pPolygon, JNI_ABORT); // abort and release
	return (jlong)pPoly;
}


JNIEXPORT jint JNICALL Java_imrcp_geosrv_GeoUtil_clipBox(JNIEnv* pEnv, jclass pClass, jintArray nSubjPoly, jlongArray lResultBoxRefs)
{
	jlong* pRefs = pEnv->GetLongArrayElements(lResultBoxRefs, NULL);

	if (pRefs[0] == 0L)
		pRefs[0] = (jlong)new std::vector<Polygon2D>();

	std::vector<Polygon2D>* pResult = (std::vector<Polygon2D>*)pRefs[0];
	Bounds* pBounds = (Bounds*)pRefs[1];
	Box2D* pClip = pBounds->m_pBox;

	jint* pSubjPoly = pEnv->GetIntArrayElements(nSubjPoly, NULL);
	int* pIntIter = pSubjPoly + 1; // skip insertion index
	int nHoleCount = *pIntIter++; // initially total ring count

	Polygon2D* pSubj = new Polygon2D();;
	pIntIter = make_ring(&(pSubj->outer()), pIntIter);
	if (--nHoleCount > 0) // pre-decrement skips outer ring
	{
		pSubj->inners().reserve(nHoleCount); // reserve maximum hole capacity
		while (nHoleCount-- > 0) // exclude holes outside clip bounds
		{
			int* pBoxIter = pIntIter + 3;
			if (*pBoxIter++ > pBounds->m_nMaxX || *pBoxIter++ > pBounds->m_nMaxY || 
				*pBoxIter++ < pBounds->m_nMinX || *pBoxIter++ < pBounds->m_nMinY)
			{
				pIntIter += (*pIntIter << 1) + 1;
				continue; // skip excluded hole geometry
			}
			pSubj->inners().resize(pSubj->inners().size() + 1); // incrementally increase hole vector
			pIntIter = make_ring(&(pSubj->inners().back()), pIntIter);
		}
	}
	pEnv->ReleaseIntArrayElements(nSubjPoly, pSubjPoly, JNI_ABORT); // abort and release

	boost::geometry::intersection(*pSubj, *pClip, *pResult);
	pEnv->ReleaseLongArrayElements(lResultBoxRefs, pRefs, 0); // copy back and release
	delete pSubj; // only needed for each call
	return pResult->size();
}


JNIEXPORT jint JNICALL Java_imrcp_geosrv_GeoUtil_clipPolygon(JNIEnv* pEnv, jclass pClass, jlongArray lResultClipSubjRefs)
{
	jint nSize = 0;
	jlong* pRefs = pEnv->GetLongArrayElements(lResultClipSubjRefs, NULL);
	if (pRefs[1] != 0L && pRefs[2] != 0L)
	{
		if (pRefs[0] == 0L)
			pRefs[0] = (jlong)new std::vector<Polygon2D>();

		std::vector<Polygon2D>* pResult = (std::vector<Polygon2D>*)pRefs[0];
		Polygon2D* pClip = (Polygon2D*)pRefs[1];
		Polygon2D* pSubj = (Polygon2D*)pRefs[2];
		boost::geometry::intersection(*pSubj, *pClip, *pResult);

		if (pResult->empty())
			delete pResult;
		else
			nSize = pResult->size();
	}
	pEnv->ReleaseLongArrayElements(lResultClipSubjRefs, pRefs, 0); // copy back and release
	return nSize;
}


JNIEXPORT jintArray JNICALL Java_imrcp_geosrv_GeoUtil_popResult(JNIEnv* pEnv, jclass pClass, jlong lResultRef)
{
	if (lResultRef == 0L)
		return NULL;

	jintArray pPoints = NULL;
	std::vector<Polygon2D>* pResult = (std::vector<Polygon2D>*)lResultRef;
	if (!pResult->empty())
	{
		Polygon2D* pPoly = &pResult->back();
		std::vector<Point2D>* pOuter = &pPoly->outer();

		int nTotalPoints = pOuter->size();
		int nHoleCount = pPoly->inners().size();
		int nRingCount = nHoleCount + 1;
		while (nHoleCount-- > 0)
			nTotalPoints += pPoly->inners()[nHoleCount].size();

		int nSize = 2 + (5 * nRingCount) + (nTotalPoints << 1); // array size insert + count + 5 * count + total points * 2
		jint oPoints[nSize]; // local stack allocation automatically freed
		jint* pIntIter = oPoints;
		*pIntIter++ = nSize; // offset value
		*pIntIter++ = nRingCount--;

		pIntIter = save_ring(pOuter, pIntIter);
		while (nRingCount-- > 0)
			pIntIter = save_ring(&(pPoly->inners()[nRingCount]), pIntIter);

		pPoints = pEnv->NewIntArray(nSize);
		pEnv->SetIntArrayRegion(pPoints, 0, nSize, oPoints);

		pResult->pop_back();
	}

	if (pResult->empty()) // release when done
		delete pResult;

	return pPoints;
}


JNIEXPORT void JNICALL Java_imrcp_geosrv_GeoUtil_freeBox(JNIEnv* pEnv, jclass pClass, jlong lBoxRef)
{
	if (lBoxRef == 0L)
		return;

	Bounds* pBounds = (Bounds*)lBoxRef;
	delete pBounds->m_pBox;
	delete pBounds;
}


JNIEXPORT void JNICALL Java_imrcp_geosrv_GeoUtil_freePolygon(JNIEnv* pEnv, jclass pClass, jlong lPolygonRef)
{
	if (lPolygonRef != 0L)
		delete (Polygon2D*)lPolygonRef;
}
