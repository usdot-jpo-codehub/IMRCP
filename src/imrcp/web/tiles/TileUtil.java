/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.web.tiles;

import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.Mercator;
import imrcp.system.Arrays;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import vector_tile.VectorTile;

/**
 * Contains methods to aid in the creation of Mapbox Vector Tiles(MVT). See the
 * Mapbox Vector Tile Specification at https://github.com/mapbox/vector-tile-spec/tree/master/2.1
 * @author aaron.cherney
 */
public abstract class TileUtil
{
	/**
	 * Command id for MoveTo in MVT
	 */
	public final static int MOVETO = 1;

	
	/**
	 * Command id for LineTo in MVT
	 */
	public final static int LINETO = 2;

	
	/**
	 * Command id for ClosePath in MVT
	 */
	public final static int CLOSEPATH = 7;
	
	
	/**
	 * Calculates the CommandInteger which indicates the command to be executed
	 * in the geometry encoding of a MVT based off the given command id and 
	 * command count.
	 * @param nId the command ID. 1 = MoveTo, 2 = LineTo, 7 = ClosePath
	 * @param nCount The command count which indicates the number of times the
	 * command will be executed.
	 * @return The CommandInteger
	 */
	public static int command(int nId, int nCount)
	{
		return (nId & 0x7) | (nCount << 3);
	}
	
	
	/**
	 * Calculates the ParameterInteger of the given value. A ParameterInteger is
	 * a zigzag encoded so that small negative nad positive values are both encoded
	 * as small integers.
	 * @param dValue the value to encoded
	 * @return the ParameterInteger of the value
	 */
	public static int parameter(double dValue)
	{
		int nVal = (int)Math.round(dValue);
		return (nVal << 1) ^ (nVal >> 31);
	}
	
	
	/**
	 * Gets the position of the given value as a number between 0 and the extent
	 * based off of the minimum and maximum. This function is used to determine
	 * where the points of geometries are located inside of a MVT.
	 * 
	 * @param dVal coordinate to get the position of
	 * @param dMin minimum bound
	 * @param dMax maximum bound
	 * @param dExtent total number of positions inside of the tile to use
	 * @param bInvert use true for y axis, false for x axis
	 * @return Position inside the tile of the coordinate. {@code 0 <= pos < extent}
	 */
	public static int getPos(double dVal, double dMin, double dMax, double dExtent, boolean bInvert)
	{
//		if (bInvert)
//			return (int)Math.round((dMax - dVal) * dExtent / (dMax - dMin));
//		
//		return (int)Math.round((dVal - dMin) * dExtent / (dMax - dMin));
		if (bInvert)
			return (int)((dMax - dVal) * dExtent / (dMax - dMin));
		
		return (int)((dVal - dMin) * dExtent / (dMax - dMin));
	}
	
	
	/**
	 * Adds the polygon defined by the given Area as a Feature to the FeatureBuilder.
	 * 
	 * @param oFeatureBuilder Feature Builder for the VectorTile
	 * @param nCur reusable array that contains the position of the cursor which
	 * is the current position in tile coordinates
	 * @param dMercBounds bounds in mercator meters of the MVT. [min x, min y, max x, max y]
	 * @param nExtent number of positions to use along each axis inside the tile
	 * @param oPoly Polygon with coordinates in mercator meters
	 * @param nPointBuffer reusable growable array that stores the tile coordinates
	 * that correspond to the points of the polygon
	 */
	public static void addPolygon1(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dMercBounds, int nExtent, Area oPoly, int[] nPointBuffer)
	{		
		double[] dCoords = new double[2];
		double[] dPrev = new double[2];
		int nPosX;
		int nPosY;
		PathIterator oIt = oPoly.getPathIterator(null);
		nPointBuffer[0] = 1;
		
		ArrayList<Point2D.Double> oRing = new ArrayList();
		ArrayList<ArrayList<Point2D.Double>> oRings = new ArrayList();
		while (!oIt.isDone()) // create polygons and holes from clipped area
		{
			int nSegType = oIt.currentSegment(dCoords);
			if (nSegType == PathIterator.SEG_MOVETO || nSegType == PathIterator.SEG_LINETO)
			{
				oRing.add(new Point2D.Double(dCoords[0], dCoords[1]));
			}
			else if (nSegType == PathIterator.SEG_CLOSE)
			{
//				Point2D.Double oFirst = oRing.get(0);
//				Point2D.Double oLast = oRing.get(oRing.size() - 1);
//				if (oFirst.x == oLast.x && oFirst.y == oLast.y)
//					oRing.remove(oRing.size() - 1);
				oRings.add(oRing);
				oRing = new ArrayList();
			}
			oIt.next();
		}

		int nIndex = oRings.size();
		if (nIndex > 1)
			System.out.print("");
		while (nIndex-- > 0)
		{
			oRing = oRings.get(nIndex);
			Point2D.Double oPrev = oRing.get(0);
			nPosX = getPos(oPrev.x, dMercBounds[0], dMercBounds[2], nExtent, false);
			nPosY = getPos(oPrev.y, dMercBounds[1], dMercBounds[3], nExtent, true);
			nPointBuffer = Arrays.add(nPointBuffer, nPosX, nPosY);
			for (int nPointIndex = 1; nPointIndex < oRing.size(); nPointIndex++)
			{
				Point2D.Double oPt = oRing.get(nPointIndex);
				if (oPt.x == oPrev.x && oPt.y == oPrev.y)
					continue;
				nPosX = getPos(oPt.x, dMercBounds[0], dMercBounds[2], nExtent, false);
				nPosY = getPos(oPt.y, dMercBounds[1], dMercBounds[3], nExtent, true);
				nPointBuffer = Arrays.add(nPointBuffer, nPosX, nPosY);
				oPrev = oPt;
			}
			writePointBuffer(oFeatureBuilder, nPointBuffer, nCur, true); // write the geometry commands to the tile
			nPointBuffer[0] = 1; // reset insertion point to 1
		}
	}
	
	
	public static void addPolygon(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dMercBounds, int nExtent, int[] nPoly, int[] nPointBuffer)
	{
		int nRings = nPoly[1];
		int nPolyPos = 2;
		int nPosX;
		int nPosY;
		nPointBuffer[0] = 1;
		for (int nRingIndex = 0; nRingIndex < nRings; nRingIndex++)
		{
			int nNumPoints = nPoly[nPolyPos];
			nPolyPos += 5; // skip bounding box
			int nFirstPoint = nPolyPos;
			int nEnd = nPolyPos + nNumPoints * 2;
			while (nPolyPos < nEnd)
			{
				nPosX = getPos(Mercator.lonToMeters(GeoUtil.fromIntDeg(nPoly[nPolyPos++])), dMercBounds[0], dMercBounds[2], nExtent, false);
				nPosY = getPos(Mercator.latToMeters(GeoUtil.fromIntDeg(nPoly[nPolyPos++])), dMercBounds[1], dMercBounds[3], nExtent, true);
				nPointBuffer = Arrays.add(nPointBuffer, nPosX, nPosY); // add tile coordinates to the array
			}
			nPosX = getPos(Mercator.lonToMeters(GeoUtil.fromIntDeg(nPoly[nFirstPoint++])), dMercBounds[0], dMercBounds[2], nExtent, false);
			nPosY = getPos(Mercator.latToMeters(GeoUtil.fromIntDeg(nPoly[nFirstPoint])), dMercBounds[1], dMercBounds[3], nExtent, true);
			nPointBuffer = Arrays.add(nPointBuffer, nPosX, nPosY); // add tile coordinates to the array
			writePointBuffer(oFeatureBuilder, nPointBuffer, nCur, true); // write the geometry commands to the tile
			nPointBuffer[0] = 1;
		}
	}
	
	
	public static void addPolygon(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dMercBounds, int nExtent, Area oPoly, int[] nPointBuffer)
	{		
		double[] dCoords = new double[2];
		double[] dPrev = new double[2];
		double[] dFirst = new double[2];
		double[] dTemp;
		int nPosX;
		int nPosY;
		PathIterator oIt = oPoly.getPathIterator(null);
		BitSet oHoles = new BitSet();
		nPointBuffer[0] = 1;
		
		double dWinding = 0;
		int nBitIndex = 0;
		while (!oIt.isDone()) // first determine which parts of multi-path are polygons and holes
		{
			switch (oIt.currentSegment(dCoords)) // fills dCoords with the coordinates of the next point
			{
				case PathIterator.SEG_MOVETO:
				{
					dWinding = 0; // init winding number
					System.arraycopy(dCoords, 0, dFirst, 0, 2); // copy point into dFirst
					System.arraycopy(dCoords, 0, dPrev, 0, 2); // copy poitn into dPrev
					break;
				}
				case PathIterator.SEG_LINETO:
				{
					dWinding += ((dCoords[0] - dPrev[0]) * (dCoords[1] + dPrev[1]));
					System.arraycopy(dCoords, 0, dPrev, 0, 2);
					break;
				}
				case PathIterator.SEG_CLOSE:
				{
					dWinding += ((dFirst[0] - dCoords[0]) * (dFirst[1] + dCoords[1]));
					oHoles.set(nBitIndex++, dWinding < 0); // negative winding number is hole
					break;
				}
			}
			oIt.next();
		}
		
		
		nBitIndex = 0;
		dPrev[0] = -1;
		dPrev[1] = -1;
		oIt = oPoly.getPathIterator(null);

		while (!oIt.isDone()) // write polygons
		{
			if (oHoles.get(nBitIndex++)) // skip holes
			{
				while (oIt.currentSegment(dCoords) != PathIterator.SEG_CLOSE)
					oIt.next();
			}
			else
			{
				while (oIt.currentSegment(dCoords) != PathIterator.SEG_CLOSE)
				{
					nPosX = getPos(dCoords[0], dMercBounds[0], dMercBounds[2], nExtent, false);
					nPosY = getPos(dCoords[1], dMercBounds[1], dMercBounds[3], nExtent, true);
					if (dCoords[0] != dPrev[0] || dCoords[1] != dPrev[1]) // ignore repeated points
					{
						nPointBuffer = Arrays.add(nPointBuffer, nPosX, nPosY); // add tile coordinates to the array
						dTemp = dCoords;
						dCoords = dPrev;
						dPrev = dTemp;
					}
					oIt.next();
				}
				writePointBuffer(oFeatureBuilder, nPointBuffer, nCur, true); // write the geometry commands to the tile
				nPointBuffer[0] = 1; // reset insertion point to 1
				dPrev[0] = -1;
				dPrev[1] = -1;
				
			}
			oIt.next();
		}
		
		nBitIndex = 0;
		dPrev[0] = -1;
		dPrev[1] = -1;
		oIt = oPoly.getPathIterator(null);

		while (!oIt.isDone()) // write holes
		{
			if (!oHoles.get(nBitIndex++)) // skip polygons
			{
				while (oIt.currentSegment(dCoords) != PathIterator.SEG_CLOSE)
					oIt.next();
			}
			else
			{
				while (oIt.currentSegment(dCoords) != PathIterator.SEG_CLOSE)
				{
					nPosX = getPos(dCoords[0], dMercBounds[0], dMercBounds[2], nExtent, false);
					nPosY = getPos(dCoords[1], dMercBounds[1], dMercBounds[3], nExtent, true);
					if (dCoords[0] != dPrev[0] || dCoords[1] != dPrev[1]) // ignore repeated points
					{
						nPointBuffer = Arrays.add(nPointBuffer, nPosX, nPosY); // add tile coordinates to the array
						dTemp = dCoords;
						dCoords = dPrev;
						dPrev = dTemp;
					}
					oIt.next();
				}
				writePointBuffer(oFeatureBuilder, nPointBuffer, nCur, true); // write the geometry commands to the tile
				nPointBuffer[0] = 1;
				dPrev[0] = -1;
				dPrev[1] = -1;
			}
			oIt.next();
		}
	}
	
	
	/**
	 * Adds the linestring defined by the given double array as a Feature to the 
	 * FeatureBuilder.
	 * 
	 * @param oFeatureBuilder Feature Builder for the VectorTile
	 * @param nCur reusable array that contains the position of the cursor which
	 * is the current position in tile coordinates
	 * @param dMercBounds bounds in mercator meters of the MVT. [min x, min y, max x, max y]
	 * @param nExtent number of positions to use along each axis inside the tile
	 * @param dLine array that defines the linestring. [group value, id, value index for highway, value index for bridge, x0, y0, x1, y1... xn, yn]
	 * @param nPointBuffer reusable growable array that stores the tile coordinates
	 * that correspond to the points of the linestring
	 * @param nTags not implemented
	 */
	public static void addLinestring(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dMercBounds, int nExtent, double[] dLine, int[] nPointBuffer, int... nTags)
	{
		int nPosX;
		int nPosY;
		double[] dCoords = new double[2];
		double[] dPrev = new double[2];
		double[] dTemp;
		nPointBuffer[0] = 1;
		for (int i = 6; i < dLine.length;)
		{
			dCoords[0] = dLine[i++];
			dCoords[1] = dLine[i++];
			nPosX = getPos(Mercator.lonToMeters(dCoords[0]), dMercBounds[0], dMercBounds[2], nExtent, false);
			nPosY = getPos(Mercator.latToMeters(dCoords[1]), dMercBounds[1], dMercBounds[3], nExtent, true);
			if (dCoords[0] != dPrev[0] || dCoords[1] != dPrev[1])
			{
				nPointBuffer = Arrays.add(nPointBuffer, nPosX, nPosY);
				dTemp = dCoords;
				dCoords = dPrev;
				dPrev = dTemp;
			}
		}
		writePointBuffer(oFeatureBuilder, nPointBuffer, nCur, false);
		oFeatureBuilder.setId((long)dLine[1]);
		oFeatureBuilder.addTags(0);
		oFeatureBuilder.addTags((int)dLine[2]);
		oFeatureBuilder.addTags(1);
		oFeatureBuilder.addTags((int)dLine[3]);
		oFeatureBuilder.addTags(2);
		oFeatureBuilder.addTags((int)dLine[4]);
		oFeatureBuilder.addTags(3);
		oFeatureBuilder.addTags((int)dLine[5]);
		oFeatureBuilder.setType(VectorTile.Tile.GeomType.LINESTRING);
	}
	
	
	public static void addPolyOutline(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dMercBounds, int nExtent, int[] nPoly, int[] nPointBuffer)
	{
		int nRings = nPoly[1];
		int nPolyPos = 2;
		int nPosX;
		int nPosY;
		nPointBuffer[0] = 1;
		for (int nRingIndex = 0; nRingIndex < nRings; nRingIndex++)
		{
			int nNumPoints = nPoly[nPolyPos];
			nPolyPos += 5; // skip bounding box
			int nFirstPoint = nPolyPos;
			int nEnd = nPolyPos + nNumPoints * 2;
			while (nPolyPos < nEnd)
			{
				nPosX = getPos(Mercator.lonToMeters(GeoUtil.fromIntDeg(nPoly[nPolyPos++])), dMercBounds[0], dMercBounds[2], nExtent, false);
				nPosY = getPos(Mercator.latToMeters(GeoUtil.fromIntDeg(nPoly[nPolyPos++])), dMercBounds[1], dMercBounds[3], nExtent, true);
				nPointBuffer = Arrays.add(nPointBuffer, nPosX, nPosY); // add tile coordinates to the array
			}
			nPosX = getPos(Mercator.lonToMeters(GeoUtil.fromIntDeg(nPoly[nFirstPoint++])), dMercBounds[0], dMercBounds[2], nExtent, false);
			nPosY = getPos(Mercator.latToMeters(GeoUtil.fromIntDeg(nPoly[nFirstPoint])), dMercBounds[1], dMercBounds[3], nExtent, true);
			nPointBuffer = Arrays.add(nPointBuffer, nPosX, nPosY); // add tile coordinates to the array
			writePointBuffer(oFeatureBuilder, nPointBuffer, nCur, false); // write the geometry commands to the tile
			nPointBuffer[0] = 1;
		}
	}
	
	
	/**
	 * Adds the point defined by the given longitude and latitude as a Feature to the 
	 * FeatureBuilder.
	 * 
	 * @param oFeatureBuilder Feature Builder for the VectorTile
	 * @param nCur reusable array that contains the position of the cursor which
	 * is the current position in tile coordinates
	 * @param dMercBounds bounds in mercator meters of the MVT. [min x, min y, max x, max y]
	 * @param nExtent number of positions to use along each axis inside the tile
	 * @param dLon longitude of the point in decimal degrees
	 * @param dLat latitude of the point in decimal degrees
	 */
	public static void addPointToFeature(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dMercBounds, int nExtent, double dLon, double dLat)
	{
		int nPosX = getPos(Mercator.lonToMeters(dLon), dMercBounds[0], dMercBounds[2], nExtent, false);
		int nPosY = getPos(Mercator.latToMeters(dLat), dMercBounds[1], dMercBounds[3], nExtent, true);
		
		oFeatureBuilder.addGeometry(command(MOVETO, 1));
		int nDeltaX = nPosX - nCur[0];
		int nDeltaY = nPosY - nCur[1];
		oFeatureBuilder.addGeometry(parameter(nDeltaX));
		oFeatureBuilder.addGeometry(parameter(nDeltaY));
		nCur[0] += nDeltaX;
		nCur[1] += nDeltaY;
	}
	
	
	/**
	 * Writes the tile coordinates in the point buffer to the FeatureBuilder
	 * 
	 * @param oFeatureBuilder Feature Builder for the VectorTile
	 * @param nPointBuffer reusable growable array that stores the tile coordinates
	 * that correspond to the points of the feature
	 * @param nCur reusable array that contains the position of the cursor which
	 * is the current position in tile coordinates
	 * @param bClose flag indicating if the feature needs to be closed. Should be
	 * true for polygons, otherwise false
	 */
	public static void writePointBuffer(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nPointBuffer, int[] nCur, boolean bClose)
	{
		int nStart = 1;
		int nBound = (int)(nPointBuffer[0]);
		int nInc = 2;
		
		oFeatureBuilder.addGeometry(command(MOVETO, 1)); // move to the first point
		int i = nStart;
		int nPosX = nPointBuffer[i];
		int nPosY = nPointBuffer[i + 1];
		int nDeltaX = nPosX - nCur[0]; // cursor should start at [0, 0]
		int nDeltaY = nPosY - nCur[1];
		oFeatureBuilder.addGeometry(parameter(nDeltaX)); // always write the deltas from the cursor tile coordinates = cursor coordinate + delta
		oFeatureBuilder.addGeometry(parameter(nDeltaY));
		nCur[0] += nDeltaX;
		nCur[1] += nDeltaY;
		i += nInc;
		oFeatureBuilder.addGeometry(command(LINETO, nPointBuffer[0] / 2 - 1)); // calculate the number of line to commands that will be executed
		for (; i != nBound; i += nInc)
		{
			nPosX = nPointBuffer[i];
			nPosY = nPointBuffer[i + 1];
			nDeltaX = nPosX - nCur[0];
			nDeltaY = nPosY - nCur[1];
			oFeatureBuilder.addGeometry(parameter(nDeltaX));
			oFeatureBuilder.addGeometry(parameter(nDeltaY));
			nCur[0] += nDeltaX;
			nCur[1] += nDeltaY;
		}
		if (bClose)
			oFeatureBuilder.addGeometry(command(CLOSEPATH, 1));
	}
	
	
	/**
	 * Creates a Path object from the given polygon defined as an exterior ring.
	 * @param dRing array that defines the polygon [group value, x0, y0, x1, y1, ... xn, yn]
	 * 
	 * @return Path2D.Double object that represents the polygon
	 */
	static Path2D.Double getPath(double[] dRing)
	{
		Path2D.Double oPath = new Path2D.Double();
		oPath.moveTo(dRing[1], dRing[2]); // start at 1 because the group value is in index 0
		for (int i = 3; i < dRing.length;)
			oPath.lineTo(dRing[i++], dRing[i++]);
		oPath.closePath();
		
		return oPath;
	}
	
	
	/**
	 * Wrapper for {@link #newAddLinestring(vector_tile.VectorTile.Tile.Feature.Builder, int[], double[], int, int, double[], int[])}
	 * with a start position of 1.
	 * 
	 * @param oFeatureBuilder Feature Builder for the VectorTile
	 * @param nCur reusable array that contains the position of the cursor which
	 * is the current position in tile coordinates
	 * @param dBounds bounds in mercator meters of the MVT. [min x, min y, max x, max y]
	 * @param nExtent number of positions to use along each axis inside the tile
	 * @param dLine growable array that defines the linestring. [group value, id, value index for highway, value index for bridge, x0, y0, x1, y1... xn, yn]
	 * @param nPointBuffer reusable growable array that stores the tile coordinates
	 * that correspond to the points of the linestring
	 * @return reference to the array that describes nPointBuffer (the reference
	 * could possibly change if more space needed to be allocated to add the points)
	 */
	public static int[] newAddLinestring(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dBounds, int nExtent, double[] dLine, int[] nPointBuffer)
	{
		return newAddLinestring(oFeatureBuilder, nCur, dBounds, nExtent, 1, dLine, nPointBuffer);
	}
	
	
	/**
	 * Adds the linestring defined by the given double array as a Feature to the 
	 * FeatureBuilder.
	 * 
	 * @param oFeatureBuilder Feature Builder for the VectorTile
	 * @param nCur reusable array that contains the position of the cursor which
	 * is the current position in tile coordinates
	 * @param dBounds bounds in mercator meters of the MVT. [min x, min y, max x, max y]
	 * @param nExtent number of positions to use along each axis inside the tile
	 * @param nStartPos the position in the growable array that the coordinates 
	 * of the linestring start
	 * @param dLine growable array that defines the linestring. [group value, id, value index for highway, value index for bridge, x0, y0, x1, y1... xn, yn]
	 * @param nPointBuffer reusable growable array that stores the tile coordinates
	 * that correspond to the points of the linestring
	 * @return reference to the array that describes nPointBuffer (the reference
	 * could possibly change if more space needed to be allocated to add the points)
	 */
	public static int[] newAddLinestring(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dBounds, int nExtent, int nStartPos, double[] dLine, int[] nPointBuffer)
	{
		int nPosX;
		int nPosY;
		double[] dCoords = new double[2];
		nPointBuffer[0] = 1;
		Iterator<double[]> oIt = Arrays.iterator(dLine, dCoords, nStartPos, 2);
		while (oIt.hasNext())
		{
			oIt.next();
			nPosX = getPos(Mercator.lonToMeters(dCoords[0]), dBounds[0], dBounds[2], nExtent, false);
			nPosY = getPos(Mercator.latToMeters(dCoords[1]), dBounds[1], dBounds[3], nExtent, true);

			nPointBuffer = Arrays.add(nPointBuffer, nPosX, nPosY);
		}
		newWritePointBuffer(oFeatureBuilder, nPointBuffer, nCur, false);
		
		return nPointBuffer;
	}
	
	
	/**
	 * Writes the tile coordinates in the point buffer to the FeatureBuilder. This
	 * differs from {@link #writePointBuffer(vector_tile.VectorTile.Tile.Feature.Builder, int[], int[], boolean)}
	 * because it accumulates the deltas of the coordinate in a buffer before
	 * writign them to the Feature Builder in attempt to avoid invalid geometries
	 * 
	 * @param oFeatureBuilder Feature Builder for the VectorTile
	 * @param nPointBuffer reusable growable array that stores the tile coordinates
	 * that correspond to the points of the feature
	 * @param nCur reusable array that contains the position of the cursor which
	 * is the current position in tile coordinates
	 * @param bClose flag indicating if the feature needs to be closed. Should be
	 * true for polygons, otherwise false
	 */
	public static void newWritePointBuffer(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nPointBuffer, int[] nCur, boolean bClose)
	{
		Iterator<int[]> oIt = Arrays.iterator(nPointBuffer, new int[2], 1, 2);
		int nInitCurX = nCur[0];
		int nInitCurY = nCur[1];
		if (oIt.hasNext())
		{
			int[] nPos = oIt.next();

			int nMoveX = nPos[0] - nCur[0];
			int nMoveY = nPos[1] - nCur[1];	
			int nDeltaX = nMoveX;
			int nDeltaY = nMoveY;
			nCur[0] += nDeltaX;
			nCur[1] += nDeltaY;
			int[] nDeltas = Arrays.newIntArray(Arrays.size(nPointBuffer));
			while (oIt.hasNext())
			{
				nPos = oIt.next();
				nDeltaX = nPos[0] - nCur[0];
				nDeltaY = nPos[1] - nCur[1];
				if (nDeltaX != 0 || nDeltaY != 0)
					nDeltas = Arrays.add(nDeltas, nDeltaX, nDeltaY);
				nCur[0] += nDeltaX;
				nCur[1] += nDeltaY;
			}
			if (nDeltas[0] / 2 == 0) // there are no points
			{
				nCur[0] = nInitCurX; // re initialize the cursor
				nCur[1] = nInitCurY; 
				return; // and don't add the invalid geometry to the tile
			}
			oFeatureBuilder.addGeometry(command(MOVETO, 1));
			oFeatureBuilder.addGeometry(parameter(nMoveX));
			oFeatureBuilder.addGeometry(parameter(nMoveY));
			oFeatureBuilder.addGeometry(command(LINETO, nDeltas[0] / 2));
			Iterator<int[]> oDeltaIt = Arrays.iterator(nDeltas, new int[2], 1, 2);
			while (oDeltaIt.hasNext())
			{
				int[] nDelta = oDeltaIt.next();
				oFeatureBuilder.addGeometry(parameter(nDelta[0]));
				oFeatureBuilder.addGeometry(parameter(nDelta[1]));
			}
			if (bClose)
				oFeatureBuilder.addGeometry(command(CLOSEPATH, 1));
		}
	}
}
