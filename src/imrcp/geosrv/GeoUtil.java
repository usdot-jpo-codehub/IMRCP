package imrcp.geosrv;

import imrcp.geosrv.osm.OsmNode;
import imrcp.geosrv.osm.OsmWay;
import imrcp.store.Obs;
import imrcp.system.Arrays;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import org.apache.logging.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * This class contains computational geometry and related methods.
 * @author aaron.cherney
 */
public abstract class GeoUtil
{
	static
	{
		System.loadLibrary("imrcp");
	}


	public static native long makePolygon(int[] nPolygon);


	public static native int clipPolygon(long[] lResultClipSubjRefs);
	
	
	public static native int[] popResult(long lResultRef);


	public static native void freePolygon(long lPolygonRef);
	public final static StringBuilder DEBUG = new StringBuilder();
	public static String[] COLORS = new String[]{"black", "blue", "red", "green", "pink", "yellow"};
	public static int COLOR = 0;
	
	/**
	 * Approximate radius of the earth in km
	 */
	public static final double EARTH_RADIUS_KM = 6371;

	
	/**
	 * Pi divided by 180
	 */
	public static final double PIOVER180 = Math.PI / 180;

	
	/**
	 * Fills the given array with the point the two line segments intersect. If
	 * the two line segments do not intersect, the array is filled with 
	 * {@code Double.NaN} 
	 * 
	 * @param dPx x coordinate of the initial point of the first segment
	 * @param dPy y coordinate of the initial point of the first segment
	 * @param dEnd1x x coordinate of the terminal point of the first segment
	 * @param dEnd1y y coordinate of the terminal point of the first segment
	 * @param dQx x coordinate of the initial point of the second segment
	 * @param dQy y coordinate of the initial point of the second segment
	 * @param dEnd2x x coordinate of the terminal point of the second segment
	 * @param dEnd2y y coordinate of the terminal point of the second segment
	 * @param dInter array to get filled with the coordinates of the intersection
	 * point
	 */
	public static void getIntersection(double dPx, double dPy, double dEnd1x, double dEnd1y, double dQx, double dQy, double dEnd2x, double dEnd2y, double[] dInter)
	{
		dInter[0] = Double.NaN;
		dInter[1] = Double.NaN;
		double dDeltaQPx = dQx - dPx;
		double dDeltaQPy = dQy - dPy;
		double dRx = dEnd1x - dPx;
		double dRy = dEnd1y - dPy;
		double dSx = dEnd2x - dQx;
		double dSy = dEnd2y - dQy;
		double dRCrossS = cross(dRx, dRy, dSx, dSy);
		if (dRCrossS == 0) // the lines are collinear, parallel, or overlap so there is no single point of intersection
			return;
		double dT = cross(dDeltaQPx, dDeltaQPy, dSx, dSy) / dRCrossS;
		if (dT < 0 || dT > 1) // lines do not intersect
			return;
		double dU = cross(dDeltaQPx, dDeltaQPy, dRx, dRy) / dRCrossS;
		if (dU < 0 || dU > 1) // lines do not intersect
			return;
		dInter[0] = dPx + dT * dRx;
		dInter[1] = dPy + dT * dRy;
	}

	
	/**
	 * Gets the magnitude of the vector that is the cross product between two vectors
	 * with their z component set to zero.
	 * 
	 * @param dVx x component of vector 1
	 * @param dVy y component of vector 1
	 * @param dWx x component of vector 2
	 * @param dWy y component of vector 2
	 * @return the magnitude of the vector that is the cross product between two 
	 * vectors with their z component set to zero.
	 */
	public static double cross(double dVx, double dVy, double dWx, double dWy)
	{
		return dVx * dWy - dVy * dWx;
	}

	
	/**
	 * Floors the given integer to the given precision.
	 * @param nValue the value to floor
	 * @param nPrecision the precision to used to floor
	 * @return the floored value of the integer based on the precision
	 */
	public static int floor(int nValue, int nPrecision)
	{
		// this integer flooring method returns the next smallest integer
		int nFlooredValue = nValue / nPrecision * nPrecision;

		// correct for negative numbers 
		// ensure the value was not previously floored or this will return the wrong result
		if (nValue < 0 && nFlooredValue != nValue)
			nFlooredValue -= nPrecision;

		return nFlooredValue;
	}

	
	/**
	 * Determines if the given point is inside the given bounding box with the
	 * given tolerance.
	 * 
	 * @param nX x coordinate of the point
	 * @param nY y coordinate of the point
	 * @param nT top bound of the bounding box
	 * @param nR right bound of the bounding box
	 * @param nB bottom bound of the bounding box
	 * @param nL left bound of the bounding box
	 * @param nTol tolerance to expand the bounding box by
	 * @return true if the point is inside (or on the edge) of the bounding box
	 * expanded by the tolerance, otherwise false.
	 */
	public static boolean isInside(int nX, int nY, int nL, int nB, int nR, int nT, int nTol)      
	{
		if (nR < nL) // swap the left and right bounds as needed
		{
			nR ^= nL;
			nL ^= nR;
			nR ^= nL;
		}

		if (nT < nB) // swap the top and bottom bounds as needed
		{
			nT ^= nB;
			nB ^= nT;
			nT ^= nB;
		}

		// expand the bounds by the tolerance
		return (nX >= nL - nTol && nX <= nR + nTol
		   && nY >= nB - nTol && nY <= nT + nTol);
	}
	

	/**
	 * Calculates the right hand rule value of the point in regards to the given
	 * directed line segment.
	 * 
	 * @param dX x coordinate of the point
	 * @param dY y coordinate of the point 
	 * @param dX1 x coordinate of the initial point of the line segment
	 * @param dY1 y coordinate of the initial point of the line segment
	 * @param dX2 x coordinate of the terminal point of the line segment
	 * @param dY2 y coordinate of the terminal point of the line segment.
	 * @return -1, 0, or 1. -1 means the point is on the right side of the line
	 * segment, 0 means the point is on the line defined by the line segment, and
	 * 1 means the point is on the left side of the line segment
	 */
	public static int rightHand(double dX, double dY, double dX1, double dY1, double dX2, double dY2)
	{
		double dXp = dX1 - dX;
		double dXd = dX2 - dX1;

		double dYp = dY1 - dY;
		double dYd = dY2 - dY1;

		double dVal = (dXd * dYp) - (dYd * dXp);
		if (dVal > 0)
			return 1;
		else if (dVal < 0)
			return -1;
		
		return 0;
	}

	
	/**
	 * Determines if the given point is inside the given bounding box with the
	 * given tolerance.
	 * 
	 * @param dX x coordinate of the point
	 * @param dY y coordinate of the point
	 * @param dT top bound of the bounding box
	 * @param dR right bound of the bounding box
	 * @param dB bottom bound of the bounding box
	 * @param dL left bound of the bounding box
	 * @param dTol tolerance to expand the bounding box by
	 * @return true if the point is inside (or on the edge) of the bounding box
	 * expanded by the tolerance, otherwise false.
	 */
	public static boolean isInside(double dX, double dY, double dL, double dB, double dR, double dT, double dTol)
	{
		return (dX >= dL - dTol && dX <= dR + dTol
		   && dY >= dB - dTol && dY <= dT + dTol);
	}

	
	/**
	 * Gets the squared perpendicular distance from the given point to the given 
	 * directed line segment. Wrapper for the method that accepts doubles:
	 * {@link #getPerpDist(double, double, double, double, double, double, imrcp.geosrv.WaySnapInfo)}.
	 * Useful intermediate parameters get stored in the given WaySnapInfo
	 * 
	 * @param nX x coordinate of the point
	 * @param nY y coordinate of the point
	 * @param nX1 x coordinate of the initial point of the line segment
	 * @param nY1 y coordinate of the initial point of the line segment
	 * @param nX2 x coordinate of the terminal point of the line segment
	 * @param nY2 y coordinate of the terminal point of the line segment.
	 * @param oSnap object that stores some useful intermediate parameters of the
	 * perpendicular distance algorithm
	 * @return The perpendicular distance from the point to the line segment. If
	 * the point cannot be snapped to the line segment {@code Integer.MIN_VALUE}
	 * is returned
	 */
	public static int getPerpDist(int nX, int nY,
	   int nX1, int nY1, int nX2, int nY2, WaySnapInfo oSnap)
	{
		double dDist = getPerpDist((double)nX, (double)nY,
		   (double)nX1, (double)nY1, (double)nX2, (double)nY2, oSnap);

		if (Double.isNaN(dDist) || dDist > Integer.MAX_VALUE)
			return Integer.MIN_VALUE;

		oSnap.m_nSqDist = (int)dDist;
		return (int)dDist;
	}
	
	
	/**
	 * Gets the squared perpendicular distance from the given point to the given 
	 * directed line segment. Wrapper for the method that accepts doubles:
	 * {@link #getPerpDist(double, double, double, double, double, double)}
	 * 
	 * @param nX x coordinate of the point
	 * @param nY y coordinate of the point
	 * @param nX1 x coordinate of the initial point of the line segment
	 * @param nY1 y coordinate of the initial point of the line segment
	 * @param nX2 x coordinate of the terminal point of the line segment
	 * @param nY2 y coordinate of the terminal point of the line segment.
	 * @return The perpendicular distance from the point to the line segment. If
	 * the point cannot be projected on to the line segment {@code Integer.MIN_VALUE}
	 * is returned
	 */
	public static int getPerpDist(int nX, int nY,
	   int nX1, int nY1, int nX2, int nY2)
	{
		double dDist = getPerpDist((double)nX, (double)nY,
		   (double)nX1, (double)nY1, (double)nX2, (double)nY2);

		if (Double.isNaN(dDist) || dDist > Integer.MAX_VALUE)
			return Integer.MIN_VALUE;

		return (int)dDist;
	}

	
	/**
	 * Gets the squared perpendicular distance from the given point to the given 
	 * directed line segment by attempting to project the point onto the line segment.
	 * Useful intermediate parameters get stored in the given WaySnapInfo. 
	 * 
	 * @param dX x coordinate of the point
	 * @param dY y coordinate of the point
	 * @param dX1 x coordinate of the initial point of the line segment
	 * @param dY1 y coordinate of the initial point of the line segment
	 * @param dX2 x coordinate of the terminal point of the line segment
	 * @param dY2 y coordinate of the terminal point of the line segment.
	 * @param oSnap object that stores some useful intermediate parameters of the
	 * perpendicular distance algorithm
	 * @return The perpendicular distance from the point to the line segment. If
	 * the point cannot be projected on to the line segment {@code Double.NaN}
	 * is returned
	 */
	public static double getPerpDist(double dX, double dY,
	   double dX1, double dY1, double dX2, double dY2, WaySnapInfo oSnap)
	{
		double dXd = dX2 - dX1;
		double dYd = dY2 - dY1;
		double dXp = dX - dX1;
		double dYp = dY - dY1;

		if (dXd == 0 && dYd == 0) // line segment is a point
			return dXp * dXp + dYp * dYp; // squared dist between the points

		double dU = dXp * dXd + dYp * dYd;
		double dV = dXd * dXd + dYd * dYd;

		if (dU < 0 || dU > dV) // nearest point is not on the line
		{
			oSnap.m_dProjSide = dU;
			return Double.NaN;
		}

		oSnap.m_nRightHandRule = (int)((dXd * dYp) - (dYd * dXp));

		// find the perpendicular intersection of the point on the line
		dXp = dX1 + (dU * dXd / dV);
		dYp = dY1 + (dU * dYd / dV);
		oSnap.m_nLonIntersect = (int)Math.round(dXp);
		oSnap.m_nLatIntersect = (int)Math.round(dYp);

		dXd = dX - dXp; // calculate the squared distance
		dYd = dY - dYp; // between the point and the intersection
		return dXd * dXd + dYd * dYd;
	}
	
	
	/**
	 * Gets the squared perpendicular distance from the given point to the given 
	 * directed line segment by attempting to project the point onto the line segment.
	 * 
	 * @param dX x coordinate of the point
	 * @param dY y coordinate of the point
	 * @param dX1 x coordinate of the initial point of the line segment
	 * @param dY1 y coordinate of the initial point of the line segment
	 * @param dX2 x coordinate of the terminal point of the line segment
	 * @param dY2 y coordinate of the terminal point of the line segment.
	 *
	 * @return The perpendicular distance from the point to the line segment. If
	 * the point cannot be projected on to the line segment {@code Double.NaN}
	 * is returned
	 */
	public static double getPerpDist(double dX, double dY, double dX1, double dY1, double dX2, double dY2)
	{
		double dXd = dX2 - dX1;
		double dYd = dY2 - dY1;
		double dXp = dX - dX1;
		double dYp = dY - dY1;

		if (dXd == 0 && dYd == 0) // line segment is a point
			return dXp * dXp + dYp * dYp; // squared dist between the points

		double dU = dXp * dXd + dYp * dYd;
		double dV = dXd * dXd + dYd * dYd;

		if (dU < 0 || dU > dV) // nearest point is not on the line
		{
			return Double.NaN;
		}

		// find the perpendicular intersection of the point on the line
		dXp = dX1 + (dU * dXd / dV);
		dYp = dY1 + (dU * dYd / dV);

		dXd = dX - dXp; // calculate the squared distance
		dYd = dY - dYp; // between the point and the intersection
		return dXd * dXd + dYd * dYd;
	}
	
	
	/**
	 * Gets the squared perpendicular distance from the given point to the given 
	 * directed line segment by attempting to project the point onto the line segment.
	 * The projected point gets stored in dSnap.
	 * 
	 * @param dX x coordinate of the point
	 * @param dY y coordinate of the point
	 * @param dX1 x coordinate of the initial point of the line segment
	 * @param dY1 y coordinate of the initial point of the line segment
	 * @param dX2 x coordinate of the terminal point of the line segment
	 * @param dY2 y coordinate of the terminal point of the line segment.
	 * @param dSnap array that gets filled with the projected point on the line
	 * segment in the format [projected x coordinate, projected y coordinate]. 
	 * If the point cannot be projected both coordinates are set to  {@code Double.NaN}
	 * @return The perpendicular distance from the point to the line segment. If
	 * the point cannot be projected on to the line segment {@code Double.NaN}
	 * is returned
	 */
	public static double snap(double dX, double dY,
	   double dX1, double dY1, double dX2, double dY2, double[] dSnap)
	{
		double dXd = dX2 - dX1;
		double dYd = dY2 - dY1;
		double dXp = dX - dX1;
		double dYp = dY - dY1;
		dSnap[0] = dSnap[1] = Double.NaN;
		if (dXd == 0 && dYd == 0) // line segment is a point
			return dXp * dXp + dYp * dYp; // squared dist between the points

		double dU = dXp * dXd + dYp * dYd;
		double dV = dXd * dXd + dYd * dYd;

		if (dU < 0 || dU > dV) // nearest point is not on the line
		{
			return Double.NaN;
		}


		// find the perpendicular intersection of the point on the line
		dXp = dX1 + (dU * dXd / dV);
		dYp = dY1 + (dU * dYd / dV);
		dSnap[0] = dXp;
		dSnap[1] = dYp;

		dXd = dX - dXp; // calculate the squared distance
		dYd = dY - dYp; // between the point and the intersection
		return dXd * dXd + dYd * dYd;
	}

	
	/**
	 * Converts the given decimal degree coordinate to an integer scaled to 7
	 * decimal places. This is used to store geo-coordinates as integers.
	 * 
	 * @param dValue Decimal degree coordinate to convert
	 * @return the decimal degree coordinate as an integer scaled to 7 decimal places
	 */
	public static int toIntDeg(double dValue)
	{
		return ((int)Math.round((dValue + 0.00000005) * 10000000.0));
	}

	
	/**
	 * Converts the given integer decimal degree scaled to 7 decimal places to a
	 * double representation in decimal degrees.
	 * 
	 * @param nValue integer decimal degree scaled to 7 decimal places
	 * @return Decimal degrees value of the scaled integer value
	 */
	public static double fromIntDeg(int nValue)
	{
		return fromIntDeg(nValue, 10000000);
	}
	
	
	/**
	 * Converts the given integer decimal degree scaled to the given power of 10
	 * to a double representation in decimal degrees
	 * 
	 * @param nValue integer decimal degree scaled to the power of 10
	 * @param nScale the power of 10
	 * @return Decimal degrees value of the scaled integer value
	 */
	public static double fromIntDeg(int nValue, int nScale)
	{
		return (((double)nValue) / (1.0 * nScale));
	}
	
	
	/**
	 * Determines if the given point is inside the given closed polygon defined 
	 * by the array. This is an implementation of the Ray Casting Algorithm.
	 * 
	 * @param nPolyPoints closed polygon in format [y0, x0, y1, x1, ... yn, xn, y0, x0]
	 * @param nX x coordinate of the point
	 * @param nY y coordinate of the point
	 * @return true if the point is inside the polygon, otherwise false
	 */
	public static boolean isInsidePolygon(int[] nPolyPoints, double nX, double nY)
	{
		int nCount = 0;
		SegIterator oSegIt = new SegIterator(nPolyPoints);
		while (oSegIt.hasNext())
		{
			int[] oLine = oSegIt.next();
			int nX1 = oLine[1];
			int nX2 = oLine[3];
			int nY1 = oLine[0];
			int nY2 = oLine[2];

			if ((nY1 < nY && nY2 >= nY || nY2 < nY && nY1 >= nY)
			   && (nX1 <= nX || nX2 <= nX)
			   && (nX1 + (nY - nY1) / (nY2 - nY1) * (nX2 - nX1) < nX))
				++nCount;
		}
		return (nCount & 1) != 0;
	}
	
	
	/**
	 * Determines if the given point is inside the given multipolygon defined
	 * by the ArrayList of polygon rings.
	 * 
	 * @param nRings list containing any outer and inner rings defining a 
	 * multipolygon object. Rings are defined by a growable array which has a flexible
	 * format but should be something like [insertion point, hole flag, min x, min y, max x, max y, x0, y0, x1, y1, ... xn, yn, x0, y0].
	 * In that case nHole would be 1 and nBoundsStart would be 2 and nRingStart would be 6.
	 * @param nRingStart the index in the ring arrays that the coordinates start at
	 * @param nHole the index in the ring arrays that the hole flag is located
	 * @param nBoundsStart the index in the ring arrays that the bounding box starts at
	 * @param nX x coordinate of the point
	 * @param nY y coordinate of the point
	 * @return true if the point is inside the multipolygon, otherwise false.
	 */
	public static boolean isInsideMultiPolygon(ArrayList<int[]> nRings, int nRingStart, int nHole, int nBoundsStart, double nX, double nY)
	{
		if (nRings.size() == 1)
			return isInsidePolygon(nRings.get(0), nX, nY, nRingStart, nBoundsStart);
		
		boolean bInsideExterior = false;
		for (int[] nRing : nRings)
		{
			boolean bHole = nRing[nHole] == 1;
			if (bInsideExterior && !bHole) // if the point is inside an exterior ring and the current ring is not a hole then there cannot be another hole inside the exterior ring the point is inside so early out
				return true;
			boolean bInside = isInsidePolygon(nRing, nX, nY, nRingStart, nBoundsStart);
			if (!bHole && bInside)
				bInsideExterior = true;
			
			if (bHole && bInside) // if the point is inside of a hole then it is not "inside" the polygon
				return false;
		}
		return bInsideExterior;
	}
	
	
	/**
	 * Determines if the given polyline is inside or intersects the given multipolygon.
	 * 
	 * @param nRings list containing any outer and inner rings defining a 
	 * multipolygon object. Rings are defined by a growable array which has a flexible
	 * format but should be something like [insertion point, hole flag, min x, min y, max x, max y, x0, y0, x1, y1, ... xn, yn, x0, y0].
	 * In that case nHole would be 1 and nBoundsStart would be 2 and nRingStart would be 6.
	 * @param nRingStart the index in the ring arrays that the coordinates start at
	 * @param nHole the index in the ring arrays that the hole flag is located
	 * @param nBoundsStart the index in the ring arrays that the bounding box starts at
	 * @param nPolyBounds bounding box of the entire multipolygon [min x, min y, max x, max y]
	 * @param nPolyLine growable array defining the polyline to test which has a flexible
	 * format but should be something line [insertion point, min x, min y, max x, max y, x0, y0, x1, y1, ... xn, yn].
	 * In that case nLineStart would be 5 and nLineBoundsStart would be 1
	 * @param nLineStart the index in the polyline array that the coordinates start at
	 * @param nLineBoundsStart the index in the polyline array that the bounding box starts at
	 * @return true if the polyline is inside or intersect the multipolygon
	 */
	public static boolean isInsideMultiPolygon(ArrayList<int[]> nRings, int nRingStart, int nHole, int nBoundsStart,  int[] nPolyBounds, int[] nPolyLine, int nLineStart, int nLineBoundsStart)
	{
		if (!boundingBoxesIntersect(nPolyBounds[0], nPolyBounds[1], nPolyBounds[2], nPolyBounds[3], nPolyLine[nLineBoundsStart], nPolyLine[nLineBoundsStart + 1], nPolyLine[nLineBoundsStart + 2], nPolyLine[nLineBoundsStart + 3]))
			return false;
		Iterator<int[]> oLineIt = Arrays.iterator(nPolyLine, new int[2], nLineStart, 2);
		while (oLineIt.hasNext()) // first iterate through all the points to see if any are inside
		{
			int[] nPt = oLineIt.next();
			if (isInside(nPt[0], nPt[1], nPolyBounds[0], nPolyBounds[1], nPolyBounds[2], nPolyBounds[3], 0)) // quick bounding box test
			{
				if (isInsideMultiPolygon(nRings, nRingStart, nHole, nBoundsStart, nPt[0], nPt[1])) // early out if a point is inside, meaning the polyline is at least partially inside
					return true;
			}
		}
		
		double[] dIntersection = new double[2];
		
		for (int[] nRing : nRings) // if no point was inside, check if any line segment intersects an edge of the polygons
		{
			if (nRing[nHole] == 1 ||  // can skip holes, only test exterior rings
				!boundingBoxesIntersect(nPolyLine[nLineBoundsStart], nPolyLine[nLineBoundsStart + 1], nPolyLine[nLineBoundsStart + 2], nPolyLine[nLineBoundsStart + 3],
									    nRing[nBoundsStart], nRing[nBoundsStart + 1], nRing[nBoundsStart + 2], nRing[nBoundsStart + 3])) // and polygons that aren't close to the line
				continue;
			
			oLineIt = Arrays.iterator(nPolyLine, new int[4], nLineStart, 2); 
			while (oLineIt.hasNext())
			{
				int[] nSeg = oLineIt.next();
				int nX1 = nSeg[0];
				int nY1 = nSeg[1];
				int nX2 = nSeg[2];
				int nY2 = nSeg[3];
				
				if (nX2 < nX1) // swap the left and right bounds as needed
				{
					nX2 ^= nX1;
					nX1 ^= nX2;
					nX2 ^= nX1;
				}
				
				if (nY2 < nY1) // swap the top and bottom bounds as needed
				{
					nY2 ^= nY1;
					nY1 ^= nY2;
					nY2 ^= nY1;
				}
				
				if (boundingBoxesIntersect(nX1, nY1, nX2, nY2, nRing[nBoundsStart], nRing[nBoundsStart + 1], nRing[nBoundsStart + 2], nRing[nBoundsStart + 3])) // if the line segment is close to the polygon, check if it intersects any edge
				{
					Iterator<int[]> oPolyIt = Arrays.iterator(nRing, new int[4], nRingStart, 2);
					while (oPolyIt.hasNext())
					{
						int[] nEdge = oPolyIt.next();
						getIntersection(nSeg[0], nSeg[1], nSeg[2], nSeg[3], nEdge[0], nEdge[1], nEdge[2], nEdge[3], dIntersection);
						if (Double.isFinite(dIntersection[0]))
							return true;
					}
				}
			}
		}
		
		return false;
	}
	
	
	/**
	 * Determines if the given roadway segment is inside or intersects the given 
	 * multipolygon.
	 * 
	 * @param nRings list containing any outer and inner rings defining a 
	 * multipolygon object. Rings are defined by a growable array which has a flexible
	 * format but should be something like [insertion point, hole flag, min x, min y, max x, max y, x0, y0, x1, y1, ... xn, yn, x0, y0].
	 * In that case nHole would be 1 and nBoundsStart would be 2 and nRingStart would be 6.
	 * @param nRingStart the index in the ring arrays that the coordinates start at
	 * @param nHole the index in the ring arrays that the hole flag is located
	 * @param nBoundsStart the index in the ring arrays that the bounding box starts at
	 * @param nPolyBounds bounding box of the entire multipolygon [min x, min y, max x, max y]
	 * @param oWay the roadway segment to test
	 * @return true if the roadway segment is inside or intersect the multipolygon
	 */
	public static boolean isInsideMultiPolygon(ArrayList<int[]> nRings, int nRingStart, int nHole, int nBoundsStart, int[] nPolyBounds, OsmWay oWay)
	{
		int[] nLine = Arrays.newIntArray(oWay.m_oNodes.size() * 2 + 5);
		nLine = Arrays.add(nLine, new int[]{oWay.m_nMinLon, oWay.m_nMinLat, oWay.m_nMaxLon, oWay.m_nMaxLat});
		for (OsmNode oNode : oWay.m_oNodes)
		{
			nLine = Arrays.add(nLine, oNode.m_nLon, oNode.m_nLat);
		}
		
		return isInsideMultiPolygon(nRings, nRingStart, nHole, nBoundsStart, nPolyBounds, nLine, 5, 1);
	}
	
	
	/**
	 * Wrapper for {@link #isInsidePolygon(int[], double, double, int, int)} for
	 * a polygon array that does not have the bounding box include so 0 is passed
	 * for nBoundsStart.
	 * 
	 * @param nPoly growable array defining the  closed polygon which has a flexible
	 * format but should be something like [insertion point, x0, y0, x1, y1, ... xn, yn, x0, y0]
	 * @param nX x coordinate of the point
	 * @param nY y coordinate of the point
	 * @param nStart index in the polygon array that the coordinates start at
	 * @return true if the point is inside the polygon, otherwise false
	 */
	public static boolean isInsidePolygon(int[] nPoly, double nX, double nY, int nStart)
	{
		return isInsidePolygon(nPoly, nX, nY, nStart, 0);
	}
	
	
	/**
	 * Determines if the given point is inside the given closed polygon defined 
	 * by the array. This is an implementation of the Ray Casting Algorithm.
	 * 
	 * @param nPoly growable array defining the  closed polygon which has a flexible
	 * format but should be something like [insertion point, x0, y0, x1, y1, ... xn, yn, x0, y0]
	 * @param nX x coordinate of the point
	 * @param nY y coordinate of the point
	 * @param nStart index in the polygon array that the coordinates start at
	 * @param nBoundsStart if the growable array contains the bounding box this 
	 * is the index in the array that the bounding box starts at, otherwise should
	 * be 0
	 * @return true if the point is inside the polygon, otherwise false
	 */
	public static boolean isInsidePolygon(int[] nPoly, double nX, double nY, int nStart, int nBoundsStart)
	{
		if (nBoundsStart > 0) // if the bounding box is included in the polygon array
		{
			if (!isInside(nX, nY, nPoly[nBoundsStart], nPoly[nBoundsStart + 1], nPoly[nBoundsStart + 2], nPoly[nBoundsStart + 3], 0)) // early out bounding box test
				return false;
		}
		
		int nCount = 0;
		int[] nSeg = new int[4];
		Iterator<int[]> oIt = Arrays.iterator(nPoly, nSeg, nStart, 2);
		while (oIt.hasNext())
		{
			oIt.next();
			int nX1 = nSeg[0];
			int nY1 = nSeg[1];
			int nX2 = nSeg[2];
			int nY2 = nSeg[3];
			if ((nY1 < nY && nY2 >= nY || nY2 < nY && nY1 >= nY)
			   && (nX1 <= nX || nX2 <= nX)
			   && (nX1 + (nY - nY1) / (nY2 - nY1) * (nX2 - nX1) < nX))
				++nCount;
		}
		return (nCount & 1) != 0;
	}
	
	
	/**
	 * Determines if the two bounding boxes intersect.
	 * 
	 * @param dXmin1 min x of the first bounding box
	 * @param dYmin1 min y of the first bounding box
	 * @param dXmax1 max x of the first bounding box
	 * @param dYmax1 max y of the first bounding box
	 * @param dXmin2 min x of the second bounding box
	 * @param dYmin2 min y of the second bounding box
	 * @param dXmax2 max x of the second bounding box
	 * @param dYmax2 max y of the second bounding box
	 * @return true if the two bounding boxes intersect, otherwise false
	 */
	public static boolean boundingBoxesIntersect(double dXmin1, double dYmin1, double dXmax1, double dYmax1, double dXmin2, double dYmin2, double dXmax2, double dYmax2)
	{
		return dYmax1 >= dYmin2 && dYmin1 <= dYmax2 && dXmax1 >= dXmin2 && dXmin1 <= dXmax2;
	}
	
	
	/**
	 * Determines if the two bounding boxes intersect.
	 * 
	 * @param nXmin1 min x of the first bounding box
	 * @param nYmin1 min y of the first bounding box
	 * @param nXmax1 max x of the first bounding box
	 * @param nYmax1 max y of the first bounding box
	 * @param nXmin2 min x of the second bounding box
	 * @param nYmin2 min y of the second bounding box
	 * @param nXmax2 max x of the second bounding box
	 * @param nYmax2 max y of the second bounding box
	 * @return true if the two bounding boxes intersect, otherwise false
	 */
	public static boolean boundingBoxesIntersect(int nXmin1, int nYmin1, int nXmax1, int nYmax1, int nXmin2, int nYmin2, int nXmax2, int nYmax2)
	{
		return nYmax1 >= nYmin2 && nYmin1 <= nYmax2 && nXmax1 >= nXmin2 && nXmin1 <= nXmax2;
	}
		
	
	/**
	 * Compares the two doubles with the given tolerance.
	 * 
	 * @param d1 first double
	 * @param d2 second double
	 * @param dTol tolerance
	 * @return 0 if the doubles are within the tolerance of get other, -1 if the 
	 * second double is more than the tolerance greater than the first double, 1
	 * if the first double is more than the tolerance greater than the second double.
	 */
	public static int compareTol(double d1, double d2, double dTol)
	{
		if (d2 > d1)
		{
			if (d2 - d1 > dTol)
				return -1;
		}
		else if (d1 - d2 > dTol)		
			return 1;
		
		return 0;
	}
	
	
	/**
	 * Adjusts the latitude to decimal degrees if it is outside of the range of
	 * {@literal -90 <= dLat <= 90}
	 * 
	 * @param dLat latitude to adust if needed
	 * @return latitude in decimal degrees
	 */
	public static double adjustLat(double dLat)
	{
		if (dLat > 90.0)
			return dLat - 180.0;
		if (dLat < -90.0)
			return dLat + 180.0;
		
		return dLat;
	}
	
	
	/**
	 * Adjusts the longitude to decimal degrees if it is outside of the range of
	 * {@literal -180 < dLon <= 180}
	 * 
	 * @param dLon longitude to adust if needed
	 * @return longitude in decimal degrees
	 */
	public static double adjustLon(double dLon)
	{
		if (dLon > 180)
			return dLon - 360.0;
		if (dLon <= -180)
			return dLon + 360.0;
		
		return dLon;
	}
	
	
	/**
	 * Gets the distance between the 2 geo-coordinates in km using the Haversine
	 * formula.
	 * 
	 * @param dLat1 latitude in decimal degrees of the first point
	 * @param dLon1 longitude in decimal degrees of the first point
	 * @param dLat2 latitude in decimal degrees of the second point
	 * @param dLon2 longitude in decimal degrees of the second point
	 * @return distance in km between the 2 geo-coordinates.
	 */
	public static double distanceFromLatLon(double dLat1, double dLon1, double dLat2, double dLon2)
	{
		double dLat = (dLat2 - dLat1) * PIOVER180;
		double dLon = (dLon2 - dLon1) * PIOVER180;
		double dA = Math.sin(dLat / 2) * Math.sin(dLat /2) + Math.cos(dLat1 * PIOVER180) * Math.cos(dLat2 * PIOVER180) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
		return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(dA));
	}
	
	
	public static void getPoint(double dLon, double dLat, double dAngle, double dDistance, double[] dPoint)
	{
		double dBearing = Math.PI / 2 - dAngle; // clockwise from north
		if (dBearing < 0)
			dBearing += Math.PI * 2;
		double dAngDist = dDistance / EARTH_RADIUS_KM;
		double dLat1 = dLat * PIOVER180;
		double dLon1 = dLon * PIOVER180;
		double dLat2 = Math.asin(Math.sin(dLat1) * Math.cos(dAngDist) + Math.cos(dLat1) * Math.sin(dAngDist) * Math.cos(dBearing));
		double dLon2 = dLon1 + Math.atan2(Math.sin(dBearing) * Math.sin(dAngDist) * Math.cos(dLat1), Math.cos(dAngDist) - Math.sin(dLat1) * Math.sin(dLat2));
		dPoint[0] = dLon2 * 180 / Math.PI;
		dPoint[1] = dLat2 * 180 / Math.PI;
	
	}
	
	
	/**
	 * Determines the measure of the angle defined by the 3 points in radians. 
	 * The second point is the vertex of the angle.
	 * 
	 * @param dX1 x coordinate of point 1
	 * @param dY1 y coordinate of point 1
	 * @param dX2 x coordinate of the point 2(vertex)
	 * @param dY2 y coordinate of the point 2(vertex)
	 * @param dX3 x coordinate of point 3
	 * @param dY3 y coordinate of point 3
	 * @return Measure of the angle defined by the 3 points in radians
	 */
	public static double angle(double dX1, double dY1, double dX2, double dY2, double dX3, double dY3)
	{
		double dUi = dX1 - dX2;
		double dUj = dY1 - dY2;
		double dVi = dX3 - dX2;
		double dVj = dY3 - dY2;
		double dDot = dUi * dVi + dUj * dVj;
		double dLenU = Math.sqrt(dUi * dUi + dUj * dUj);
		double dLenV = Math.sqrt(dVi * dVi + dVj * dVj);
		if (dLenU == 0 || dLenV == 0) // prevent division by zero
			return Double.NaN;
		double dValue = dDot / (dLenU * dLenV);
		dValue = round(dValue, 12); // round to help prevent value outside of the domain of arccos
		if (dValue > 1 || dValue < -1) // prevent domain error for arcos
			return Double.NaN;
		
		return Math.acos(dValue); // return value in radians
	}
	
	
	/**
	 * Rounds the given value to the nearest given amount of decimal places.
	 * @param dVal value to round
	 * @param nPlaces number of decimal places to round to
	 * @return the rounded value
	 */
	public static double round(double dVal, int nPlaces)
	{
		BigDecimal dBd = new BigDecimal(Double.toString(dVal));
		dBd = dBd.setScale(nPlaces, RoundingMode.HALF_UP);
		return dBd.doubleValue();
	}
	
	
	/**
	 * Gets the squared distance between the given two points
	 * 
	 * @param dXi x coordinate of the first point
	 * @param dYi y coordinate of the first point
	 * @param dXj x coordinate of the second point
	 * @param dYj y coordinate of the second point
	 * @return The squared distance between the points.
	 */
	public static double sqDist(double dXi, double dYi, double dXj, double dYj)
	{
		double dXd = dXj - dXi;
		double dYd = dYj - dYi;
		return dXd * dXd + dYd * dYd;
	}
	
	
	/**
	 * Gets the distance between the given two points 
	 * 
	 * @param dXi x coordinate of the first point
	 * @param dYi y coordinate of the first point
	 * @param dXj x coordinate of the second point
	 * @param dYj y coordinate of the second point
	 * @return The distance between the points.
	 */
	public static double distance(double dXi, double dYi, double dXj, double dYj)
	{
		return Math.sqrt(sqDist(dXi, dYi, dXj, dYj));
	}
	
	
	/**
	 * Determines the measure of the angle defined by using the first point as
	 * the vertex of the angle, the second point as a point on the terminal side
	 * of the angle and a point one unit to the right on the vertex as a point on
	 * the initial side of the angle.
	 * 
	 * @param dX1 x coordinate of point 1 (vertex)
	 * @param dY1 y coordinate of point 1 (vertex)
	 * @param dX2 x coordinate of the point 2 (terminal side of angle)
	 * @param dY2 y coordinate of the point 2 (terminal side of angle

	 * @return Measure of the angle defined using the first point as the vertex 
	 * of the angle, the second point as a point on the terminal side of the 
	 * angle and a point one unit to the right on the vertex as a point on
	 * the initial side of the angle.
	 */
	public static double angle(double dX1, double dY1, double dX2, double dY2)
	{
		return angle(dX1 + 1, dY1, dX1, dY1, dX2, dY2);
	}
	
	
	/**
	 * Determines the heading of the directed line segment defined by the given
	 * 2 points.
	 * 
	 * @param dX1 x coordinate of the initial point
	 * @param dY1 y coordinate of the initial point
	 * @param dX2 x coordinate of the terminal point
	 * @param dY2 y coordinate of the terminal point
	 * @return The heading of the directed line segment in radians. Range is {@literal 0 <= rad < 2pi}
	 */
	public static double heading(double dX1, double dY1, double dX2, double dY2)
	{
		double dRads = angle(dX1, dY1, dX2, dY2);
		if (dY1 > dY2)
			dRads = 2 * Math.PI - dRads;
		
		return dRads;
	}
	

	/**
	 * Determines the magnitude of the difference between the two headings.
	 * @param dHdg1 first heading in radians
	 * @param dHdg2 second heading in radians
	 * @return the magnitude of the difference between the two headings in radians
	 */
	public static double hdgDiff(double dHdg1, double dHdg2)
	{

		int nQuad1 = GeoUtil.quad(dHdg1);
		int nQuad2 = GeoUtil.quad(dHdg2);
		if ((nQuad1 == 0 || nQuad1 == 1) && nQuad2 == 4)
			dHdg2 -= (2 * Math.PI);
		if (nQuad1 == 4 && (nQuad2 == 0 || nQuad2 == 1))
			dHdg2 += (2 * Math.PI);
		return Math.abs(dHdg2 - dHdg1);
		
	}
	
	
	/**
	 * Determines the quadrant the given angle, in radians, is in.
	 * @param dAngle angle in radians, {@literal 0 <= angle}
	 * @return the quadrant the angle is in. If the angle is on the x or y axis
	 * other values are returned namely:
	 * 3pi/2 = -3
	 * pi = -2
	 * pi/2 = -1
	 * 0 = 0
	 */
	public static int quad(double dAngle)
	{
		double d2Pi = Math.PI * 2;
		double d3Pi2 = Math.PI * 3 / 2;
		double d1Pi2 = Math.PI / 2;
		while (dAngle >= d2Pi)
			dAngle -= d2Pi;
		if (dAngle > d3Pi2)
			return 4;
		else if (dAngle == d3Pi2)
			return -3;
		else if (dAngle > Math.PI)
			return 3;
		else if (dAngle == Math.PI)
			return -2;
		else if (dAngle > d1Pi2)
			return 2;
		else if (dAngle == d1Pi2)
			return - 1;
		else if (dAngle > 0)
			return 1;
		
		return 0;
	}
	
	
	public static boolean collinear(double dX1, double dY1, double dX2, double dY2, double dX3, double dY3, double dTol)
	{
		return Math.abs(dX1 * (dY2 - dY3) + dX2 * (dY3 - dY1) + dX3 * (dY1 - dY2)) < dTol;
	}
	
	
	public static boolean collinear(double dX1, double dY1, double dX2, double dY2, double dX3, double dY3)
	{
		return collinear(dX1, dY1, dX2, dY2, dX3, dY3, 0.0000001);
	}
	
	
	public static boolean isClockwise(int[] nPoly, int nStart)
	{
		return getDoubleSignedArea(nPoly, nStart) > 0;
	}
	
	public static double getDoubleSignedArea(int[] nPoly, int nPolyPos)
	{
		int nNumPoints = nPoly[nPolyPos];
		nPolyPos += 5; // skip number of points and bounding box
		int nFirstIndex = nPolyPos;
		int nPolyEnd = nPolyPos + nNumPoints * 2 - 2;
		
		long lWinding = 0;
		while (nPolyPos < nPolyEnd)
		{
			long nX1 = nPoly[nPolyPos];
			long nY1 = nPoly[nPolyPos + 1];
			long nX2 = nPoly[nPolyPos + 2];
			long nY2 = nPoly[nPolyPos + 3];
			lWinding += (nX2 - nX1) * (nY2 + nY1);
			
			nPolyPos += 2;
		}
		long nX1 = nPoly[nPolyPos];
		long nY1 = nPoly[nPolyPos + 1];
		long nX2 = nPoly[nFirstIndex];
		long nY2 = nPoly[nFirstIndex + 1];
		lWinding += (nX2 - nX1) * (nY2 + nY1);
		
		return lWinding;
	}

	
	public static boolean isPointInsidePolygon(int[] nPoly, int nPolyOffset, double nX, double nY)
	{
		int nNumPoints = nPoly[nPolyOffset++];
		if (!GeoUtil.isInside(nX, nY, nPoly[nPolyOffset++], nPoly[nPolyOffset++], nPoly[nPolyOffset++], nPoly[nPolyOffset++], 0)) // not in bounding box, early out
			return false;
		
		int nPolyEnd = nPolyOffset + nNumPoints * 2 - 2;
		int nCount = 0;
		int nFirstX = nPoly[nPolyOffset];
		int nFirstY = nPoly[nPolyOffset + 1];
		while (nPolyOffset < nPolyEnd)
		{
			int nX1 = nPoly[nPolyOffset];
			int nY1 = nPoly[nPolyOffset + 1];
			int nX2 = nPoly[nPolyOffset + 2];
			int nY2 = nPoly[nPolyOffset + 3];
			if ((nY1 < nY && nY2 >= nY || nY2 < nY && nY1 >= nY)
			   && (nX1 <= nX || nX2 <= nX)
			   && (nX1 + (nY - nY1) / (nY2 - nY1) * (nX2 - nX1) < nX))
				++nCount;
			
			nPolyOffset += 2;
		}
		
		int nX1 = nPoly[nPolyOffset];
		int nY1 = nPoly[nPolyOffset + 1];
		int nX2 = nFirstX;
		int nY2 = nFirstY;
		if ((nY1 < nY && nY2 >= nY || nY2 < nY && nY1 >= nY)
			   && (nX1 <= nX || nX2 <= nX)
			   && (nX1 + (nY - nY1) / (nY2 - nY1) * (nX2 - nX1) < nX))
				++nCount;
		return (nCount & 1) != 0;
		
	}
	
	
	public static boolean isPointInsideRingAndHoles(int[] nPoly, double nX, double nY)
	{
		int nPolyPos = 2; // start at point count of outer ring
		if (GeoUtil.isPointInsidePolygon(nPoly, nPolyPos, nX, nY))
		{
			if (nPoly[1] > 1) // need to check if inside holes
			{
				for (int nRingIndex = 1; nRingIndex < nPoly[1]; nRingIndex++) // holes
				{
					nPolyPos += nPoly[nPolyPos] * 2 + 5; // move position to the start of the first ring
					if (isPointInsidePolygon(nPoly, nPolyPos, nX, nY)) // inside ring but inside a hole so not in the polygon
						return false;
				}
			}
			
			return true;
		}
		
		return false;
	}
	
	public static boolean isInsideRingAndHoles(int[] nPoly, byte yGeoType, int[] nGeo)
	{
		switch (yGeoType)
		{
			case Obs.POINT:
			{
				return isPointInsideRingAndHoles(nPoly, nGeo[1], nGeo[2]);
			}
			case Obs.LINESTRING:
			{
				if (boundingBoxesIntersect(nPoly[3], nPoly[4], nPoly[5], nPoly[6], nGeo[1], nGeo[2], nGeo[3], nGeo[4])) // if the outer ring and linestring bounding boxes intersect
				{
					Iterator<int[]> oIt = Arrays.iterator(nGeo, new int[2], 5, 2);
					while (oIt.hasNext()) // first check if any point is inside polygon
					{
						int[] nPt = oIt.next();
						if (isPointInsideRingAndHoles(nPoly, nPt[0], nPt[1])) // early out if any point is inside the polygon
							return true;
					}
					
					oIt = Arrays.iterator(nGeo, new int[4], 5, 2); // second check if any line segment of the linestring intersects an edge of the outer ring
					int nOuterRingPoints = nPoly[2];
					int nOuterRingStart = 7; // first point of the outer ring [insertion point, num rings, num points, bb0, bb1, bb2, bb3, x0, y0,...]
					int nOuterRingEnd = nOuterRingStart + nOuterRingPoints * 2 - 2;
					double[] dInter = new double[2];
					while (oIt.hasNext())
					{
						int[] nSeg = oIt.next();
						int nX1 = nSeg[0];
						int nY1 = nSeg[1];
						int nX2 = nSeg[2];
						int nY2 = nSeg[3];
						if (boundingBoxesIntersect(nPoly[3], nPoly[4], nPoly[5], nPoly[6], Math.min(nX1, nX2), Math.min(nY1, nY2), Math.max(nX1, nX2), Math.max(nY1, nY2)))
						{
							for (int nRingIndex = nOuterRingStart; nRingIndex < nOuterRingEnd; nRingIndex += 2)
							{
								int nPolyX1 = nPoly[nRingIndex];
								int nPolyY1 = nPoly[nRingIndex + 1];
								int nPolyX2 = nPoly[nRingIndex + 2];
								int nPolyY2 = nPoly[nRingIndex + 3];
								getIntersection(nX1, nY1, nX2, nY2, nPolyX1, nPolyY1, nPolyX2, nPolyY2, dInter);
								if (Double.isFinite(dInter[0]))
									return true;
							}
							int nPolyX1 = nPoly[nOuterRingEnd];
							int nPolyY1 = nPoly[nOuterRingEnd + 1];
							int nPolyX2 = nPoly[7];
							int nPolyY2 = nPoly[8];
							getIntersection(nX1, nY1, nX2, nY2, nPolyX1, nPolyY1, nPolyX2, nPolyY2, dInter);
							if (Double.isFinite(dInter[0]))
								return true;
						}
					}
				}
				break;
			}
			case Obs.POLYGON:
			{
				if (boundingBoxesIntersect(nPoly[3], nPoly[4], nPoly[5], nPoly[6], nGeo[3], nGeo[4], nGeo[5], nGeo[6]))
				{
					int nGeoOuterRingPoints = nGeo[2];
					int nGeoOuterRingStart = 7; // first point of the outer ring [insertion point, num rings, num points, bb0, bb1, bb2, bb3, x0, y0,...]
					int nGeoOuterRingEnd = nGeoOuterRingStart + nGeoOuterRingPoints * 2;
					for (int nGeoIndex = nGeoOuterRingStart; nGeoIndex < nGeoOuterRingEnd;)
					{
						int nX = nGeo[nGeoIndex++];
						int nY = nGeo[nGeoIndex++];
						if (isPointInsideRingAndHoles(nPoly, nX, nY)) // early out if any point is inside the polygon
							return true;
					}
					nGeoOuterRingEnd -= 2;
					int nOuterRingPoints = nPoly[2];
					int nOuterRingStart = 7; // first point of the outer ring [insertion point, num rings, num points, bb0, bb1, bb2, bb3, x0, y0,...]
					int nOuterRingEnd = nOuterRingStart + nOuterRingPoints * 2 - 2;
					
					double[] dInter = new double[2];
					for (int nGeoIndex = nGeoOuterRingStart; nGeoIndex < nGeoOuterRingEnd; nGeoIndex += 2)
					{
						int nX1 = nGeo[nGeoIndex];
						int nY1 = nGeo[nGeoIndex + 1];
						int nX2 = nGeo[nGeoIndex + 2];
						int nY2 = nGeo[nGeoIndex + 3];
						if (boundingBoxesIntersect(nPoly[3], nPoly[4], nPoly[5], nPoly[6], Math.min(nX1, nX2), Math.min(nY1, nY2), Math.max(nX1, nX2), Math.max(nY1, nY2)))
						{
							for (int nRingIndex = nOuterRingStart; nRingIndex < nOuterRingEnd; nRingIndex += 2)
							{
								int nPolyX1 = nPoly[nRingIndex];
								int nPolyY1 = nPoly[nRingIndex + 1];
								int nPolyX2 = nPoly[nRingIndex + 2];
								int nPolyY2 = nPoly[nRingIndex + 3];
								getIntersection(nX1, nY1, nX2, nY2, nPolyX1, nPolyY1, nPolyX2, nPolyY2, dInter);
								if (Double.isFinite(dInter[0]))
									return true;
							}
							
							int nPolyX1 = nPoly[nOuterRingEnd - 2];
							int nPolyY1 = nPoly[nOuterRingEnd - 1];
							int nPolyX2 = nPoly[7];
							int nPolyY2 = nPoly[8];
							getIntersection(nX1, nY1, nX2, nY2, nPolyX1, nPolyY1, nPolyX2, nPolyY2, dInter);
							if (Double.isFinite(dInter[0]))
								return true;
						}
					}
					int nX1 = nGeo[nGeoOuterRingEnd];
					int nY1 = nGeo[nGeoOuterRingEnd + 1];
					int nX2 = nGeo[7];
					int nY2 = nGeo[8];
					if (boundingBoxesIntersect(nPoly[3], nPoly[4], nPoly[5], nPoly[6], Math.min(nX1, nX2), Math.min(nY1, nY2), Math.max(nX1, nX2), Math.max(nY1, nY2)))
					{
						for (int nRingIndex = nOuterRingStart; nRingIndex < nOuterRingEnd; nRingIndex += 2)
						{
							int nPolyX1 = nPoly[nRingIndex];
							int nPolyY1 = nPoly[nRingIndex + 1];
							int nPolyX2 = nPoly[nRingIndex + 2];
							int nPolyY2 = nPoly[nRingIndex + 3];
							getIntersection(nX1, nY1, nX2, nY2, nPolyX1, nPolyY1, nPolyX2, nPolyY2, dInter);
							if (Double.isFinite(dInter[0]))
								return true;
						}

						int nPolyX1 = nPoly[nOuterRingEnd - 2];
						int nPolyY1 = nPoly[nOuterRingEnd - 1];
						int nPolyX2 = nPoly[7];
						int nPolyY2 = nPoly[8];
						getIntersection(nX1, nY1, nX2, nY2, nPolyX1, nPolyY1, nPolyX2, nPolyY2, dInter);
						if (Double.isFinite(dInter[0]))
							return true;
					}
				}
				
				break;
			}
		}
		
		return false;
	}
	
	public static int[] getBoundingPolygon(int nStartLon, int nStartLat, int nEndLon, int nEndLat)
	{
		int[] nQueryRing = Arrays.newIntArray(15);
		nQueryRing = Arrays.add(nQueryRing, 1); // one outer ring
		nQueryRing = Arrays.add(nQueryRing, 4); // 4 points
		nQueryRing = Arrays.add(nQueryRing, nStartLon, nStartLat); // add bounding box
		nQueryRing = Arrays.add(nQueryRing, nEndLon, nEndLat);
		nQueryRing = Arrays.add(nQueryRing, nStartLon, nEndLat); // add top left
		nQueryRing = Arrays.add(nQueryRing, nEndLon, nEndLat); // add top right
		nQueryRing = Arrays.add(nQueryRing, nEndLon, nStartLat); // add bottom right
		nQueryRing = Arrays.add(nQueryRing, nStartLon, nStartLat); // add bottom right
		return nQueryRing;
	}
	
	
	public static void reverseRing(int[] nPoly, int nPolyPos)
	{
		int nNumPoints = nPoly[nPolyPos];
		nPolyPos += 5; // skip number of points and bounding box
		int nFirstIndex = nPolyPos;
		int nPolyEnd = nPolyPos + nNumPoints * 2;
		int[] nTemp = new int[nNumPoints * 2];
		int nIndex = nTemp.length - 2;
		while (nPolyPos < nPolyEnd)
		{
			nTemp[nIndex] = nPoly[nPolyPos++];
			nTemp[nIndex + 1] = nPoly[nPolyPos++];
			nIndex -= 2;
		}
		
		System.arraycopy(nTemp, 0, nPoly, nFirstIndex, nTemp.length);
	}
	
	public static void polygonGeoJson(StringBuilder sBuf, int[] nGeo, String sColor, double dOpacity)
	{
		if (sBuf == null)
			return;
		sBuf.append("{\"type\":\"Feature\",\"properties\":{\"opacity\":").append(dOpacity).append(",\"color\":\"").append(sColor).append("\"},");
		sBuf.append("\"geometry\":{\"type\":\"Polygon\",\"coordinates\":");
		sBuf.append('[');
		int nRings = nGeo[1];
		int nPos = 2;
		for (int nRing = 0; nRing < nRings; nRing++)
		{
			sBuf.append('[');
			int nPoints = nGeo[nPos];
			nPos += 5;
			int nFirstPoint = nPos;
			for (int nPoint = 0; nPoint < nPoints; nPoint++)
				sBuf.append(String.format("[%2.7f,%2.7f],", fromIntDeg(nGeo[nPos++]), fromIntDeg(nGeo[nPos++])));
			sBuf.append(String.format("[%2.7f,%2.7f],", fromIntDeg(nGeo[nFirstPoint]), fromIntDeg(nGeo[nFirstPoint + 1])));
			sBuf.setLength(sBuf.length() - 1);
			sBuf.append(']').append(',');
		}
		sBuf.setLength(sBuf.length() - 1);
		sBuf.append("]}");
		sBuf.append("},");
	}
	
	public static void resetDebug()
	{
		DEBUG.setLength(0);
		DEBUG.append("{\"type\":\"FeatureCollection\",\"features\":[");
	}
	
	
	public static void debug(int[] nGeo)
	{
		synchronized (DEBUG)
		{
			GeoUtil.polygonGeoJson(DEBUG, nGeo, COLORS[COLOR++ % COLORS.length], 0.4);
		}
	}
	
	public static void endDebug()
	{
		if (DEBUG.indexOf("\"Feature\"") >= 0)
			DEBUG.setLength(DEBUG.length() - 1);
		DEBUG.append("]}");
	}
	
	public static void getPolygons(ArrayList<int[]> oOuters, ArrayList<int[]> oHoles)
	{
		for (int[] nHole : oHoles)
		{
			Iterator<int[]> oIt = Arrays.iterator(nHole, new int[2], 7, 2);
			boolean bInside = false;
			for (int nOuterIndex = 0; nOuterIndex < oOuters.size(); nOuterIndex++)
			{
				int[] nOuter = oOuters.get(nOuterIndex);
				while (!bInside && oIt.hasNext())
				{
					int[] nPt = oIt.next();
					bInside = isPointInsideRingAndHoles(nOuter, nPt[0], nPt[1]);
				}
				if (bInside)
				{
					nOuter = Arrays.ensureCapacity(nOuter, nHole[0]);
					oOuters.set(nOuterIndex, nOuter); // reference could have changed
					System.arraycopy(nHole, 2, nOuter, nOuter[0], nHole[0] - 2); // don't copy ring count or insertion point
					nOuter[0] += nHole[0] - 2;
					++nOuter[1]; // increment ring count
					break;
				}
			}
		}
	}
	
	
	public static int[] centroid(int[] nPolygon)
	{
		double[] dCentroid = new double[]{0.0, 0.0};
		double dSignedArea = 0.0;
		double dX0 = 0.0;
		double dY0 = 0.0;
		double dX1 = 0.0;
		double dY1 = 0.0;
		double dA = 0.0;
		
		int nPoints = nPolygon[2];
		int nPos = 7;
		int nEnd = nPos + nPoints * 2 - 2;
		while (nPos < nEnd)
		{
			dX0 = nPolygon[nPos++];
			dY0 = nPolygon[nPos++];
			dX1 = nPolygon[nPos];
			dY1 = nPolygon[nPos + 1];
			dA = dX0 * dY1 - dX1 * dY0;
			dSignedArea += dA;
			dCentroid[0] += (dX0 + dX1) * dA;
			dCentroid[1] += (dY0 + dY1) * dA;
		}
		
		dX0 = nPolygon[nPos++];
		dY0 = nPolygon[nPos];
		dX1 = nPolygon[7];
		dY1 = nPolygon[8];
		dA = dX0 * dY1 - dX1 * dY0;
		dSignedArea += dA;
		dCentroid[0] += (dX0 + dX1) * dA;
		dCentroid[1] += (dY0 + dY1) * dA;
		
		dSignedArea *= 0.5;
		
		dCentroid[0] /= (6.0 * dSignedArea);
		dCentroid[1] /= (6.0 * dSignedArea);
		
		return new int[]{(int)Math.round(dCentroid[0]), (int)Math.round(dCentroid[1])};
	}

	public static ArrayList<int[]> parseGeojsonGeometry(JSONObject oGeometry, ArrayList<int[]> oOuters, ArrayList<int[]> oHoles)
	{
		ArrayList<int[]> oGeos = new ArrayList(1);
		String sType = oGeometry.getString("type");
		JSONArray oCoords = oGeometry.getJSONArray("coordinates");
		if (sType.compareTo("Point") == 0)
		{
			int[] nGeo = Arrays.newIntArray(2);
			nGeo = Arrays.add(nGeo, toIntDeg(oCoords.getDouble(0)));
			nGeo = Arrays.add(nGeo, toIntDeg(oCoords.getDouble(1)));
			oGeos.add(nGeo);
		}
		else if (sType.compareTo("MultiPoint") == 0)
		{
			for (int nIndex = 0; nIndex < oCoords.length(); nIndex++)
			{
				int[] nGeo = Arrays.newIntArray(2);
				JSONArray oCoord = oCoords.getJSONArray(nIndex);
				nGeo = Arrays.add(nGeo, toIntDeg(oCoord.getDouble(0)));
				nGeo = Arrays.add(nGeo, toIntDeg(oCoord.getDouble(1)));
				oGeos.add(nGeo);
			}
		}
		else if (sType.compareTo("LineString") == 0)
		{
			int[] nGeo = Arrays.newIntArray(oCoords.length() * 2);
			for (int nIndex = 0; nIndex < oCoords.length(); nIndex++)
			{
				JSONArray oCoord = oCoords.getJSONArray(nIndex);
				nGeo = Arrays.add(nGeo, toIntDeg(oCoord.getDouble(0)));
				nGeo = Arrays.add(nGeo, toIntDeg(oCoord.getDouble(1)));
			}
			oGeos.add(nGeo);
		}
		else if (sType.compareTo("MultiLineString") == 0)
		{
			for (int nLineIndex = 0; nLineIndex < oCoords.length(); nLineIndex++)
			{
				JSONArray oLine = oCoords.getJSONArray(nLineIndex);
				int[] nGeo = Arrays.newIntArray(oLine.length() * 2);
				for (int nIndex = 0; nIndex < oLine.length(); nIndex++)
				{
					JSONArray oCoord = oCoords.getJSONArray(nIndex);
					nGeo = Arrays.add(nGeo, toIntDeg(oCoord.getDouble(0)));
					nGeo = Arrays.add(nGeo, toIntDeg(oCoord.getDouble(1)));
				}
				oGeos.add(nGeo);
			}
		}
		else if (sType.compareTo("Polygon") == 0)
		{
			oGeos.add(parseGeojsonPolygon(oCoords, oOuters, oHoles));
		}
		else if (sType.compareTo("MultiPolygon") == 0)
		{
			for (int nPolyIndex = 0; nPolyIndex < oCoords.length(); nPolyIndex++)
			{
				oGeos.add(parseGeojsonPolygon(oCoords.getJSONArray(nPolyIndex), oOuters, oHoles));
			}
		}
		
		return oGeos;
	}
	
	
	public static int[] parseGeojsonPolygon(JSONArray oPolygonCoords, ArrayList<int[]> oOuters, ArrayList<int[]> oHoles)
	{
		oOuters.clear();
		oHoles.clear();
		
		for (int nRingIndex = 0; nRingIndex < oPolygonCoords.length(); nRingIndex++)
		{
			JSONArray oRing = oPolygonCoords.getJSONArray(nRingIndex);
			int[] nRing = Arrays.newIntArray(oRing.length() * 2 + 7);
			int nPointCountIndex = 2;
			int nBbIndex = 3;
			int nPrevX = Integer.MIN_VALUE;
			int nPrevY = Integer.MIN_VALUE;
			nRing = Arrays.add(nRing, 1); // each array will represent 1 ring
			nRing = Arrays.add(nRing, 0); // starts with zero points
			nRing = Arrays.add(nRing, new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE});
			
			// geojson polygons are supposed to have closed rings and counterclockwise exterior rings and clockwise holes, our geometry structure wants the opposite of that (open rings and exteriors rings are clockwise)
			int nCoordIndex = 0;
			int nEnd = oRing.length();
			int nStep = 1;
			if (nRingIndex == 0)
			{
				nCoordIndex = oRing.length() - 1;
				nEnd = -1;
				nStep = -1;
			}
			while (nCoordIndex != nEnd)
			{
				JSONArray oCoord = oRing.getJSONArray(nCoordIndex);
				int nX = toIntDeg(oCoord.getDouble(0));
				int nY = toIntDeg(oCoord.getDouble(1));
				if (nX != nPrevX || nY != nPrevY)
				{
					nRing = Arrays.addAndUpdate(nRing, nX, nY, nBbIndex);
					++nRing[nPointCountIndex];
					nPrevX = nX;
					nPrevY = nY;
				}
				nCoordIndex += nStep;
			}
			
			if (nRing[nRing[0] - 2] == nRing[nPointCountIndex + 5] && nRing[nRing[0] - 1] == nRing[nPointCountIndex + 6]) // if the polygon is closed (it should be), remove the last point
			{
				nRing[nPointCountIndex] -= 1;
				nRing[0] -= 2;
			}
			
			if (nRingIndex == 0)
			{
				if (!isClockwise(nRing, 2))
					reverseRing(nRing, 2);
				oOuters.add(nRing);
			}
			else
			{
				if (isClockwise(nRing, 2))
					reverseRing(nRing, 2);
				oHoles.add(nRing);
			}
		}
		
		getPolygons(oOuters, oHoles);
		
		return oOuters.get(0); // should only be a single polygon
	}
	
	
	public static boolean quickIsSamePolygon(int[] o1, int[] o2)
	{
		return o1[0] == o2[0] && o1[1] == o2[1] && o1[2] == o2[2] && 
			   o1[3] == o2[3] && o1[4] == o2[4] && o1[5] == o2[5] && 
			   o1[6] == o2[6];
	}
}
