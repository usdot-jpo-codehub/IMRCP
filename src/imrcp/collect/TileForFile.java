/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.collect;

import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.Mercator;
import imrcp.store.Obs;
import imrcp.store.ObsList;
import imrcp.system.Arrays;
import imrcp.system.ResourceRecord;
import imrcp.system.XzBuffer;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Callable;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Federal Highway Administration
 */
public class TileForFile implements Callable, Comparable<TileForFile>
{
	public int m_nX;
	public int m_nY;
	public long m_lFileRecv;
	public ArrayList<String> m_oSP;
	public Mercator m_oM;
	public ResourceRecord m_oRR;
	public byte[] m_yTileData;
	public int m_nStringFlag;
	public boolean m_bWriteObsFlag = false;
	public boolean m_bWriteObsType = false;
	public boolean m_bWriteRecv = false;
	public boolean m_bWriteStart = false;
	public boolean m_bWriteEnd = false;
	public ObsList m_oObsList = new ObsList();
	public Logger m_oLogger = null;
	
	public TileForFile()
	{
	}
	
	
	public TileForFile(int nX, int nY)
	{
		m_nX = nX;
		m_nY = nY;
	}
	
	public TileForFile(int nX, int nY, Mercator oM, ResourceRecord oRR, long lFileRecv, int nStringFlag, Logger oLogger)
	{
		this(nX, nY);
		m_oM = oM;
		m_oRR = oRR;
		m_lFileRecv = lFileRecv;
		m_nStringFlag = nStringFlag;
		m_oLogger = oLogger;
	}
	
	@Override
	public Object call() throws Exception
	{
		ByteArrayOutputStream oRawBytes = new ByteArrayOutputStream(8192);
		DataOutputStream oRawOut = new DataOutputStream(oRawBytes);
		TileFileWriter.ValueWriter oVW = TileFileWriter.newValueWriter(m_oRR.getValueType());
		ArrayList<int[]> oPolygons = new ArrayList();
		double[] dPt = new double[2];
		double[] dBounds = new double[4];
		m_oM.lonLatBounds(m_nX, m_nY, m_oRR.getZoom(), dBounds);
		int[] nTile = GeoUtil.getBoundingPolygon(GeoUtil.toIntDeg(dBounds[0]), GeoUtil.toIntDeg(dBounds[1]), GeoUtil.toIntDeg(dBounds[2]), GeoUtil.toIntDeg(dBounds[3]));
		double[] dPixels = new double[2];
		int[] nPt = new int[2];
		long lTilePolyRef = 0L;
		try
		{
			for (Obs oObs : m_oObsList)
			{
				if (oObs.m_yGeoType == Obs.POINT || oObs.m_yGeoType == Obs.LINESTRING)
				{
					if (m_bWriteObsFlag)
						oRawOut.writeByte(oObs.m_nObsFlag);
					if (m_bWriteObsType)
						oRawOut.writeInt(oObs.m_nObsTypeId);
					if (m_oSP != null)
					{
						if (m_nStringFlag >= 0)
							oRawOut.writeByte(m_nStringFlag);
						else
						{
							int nStringFlag = 0;
							for (int nIndex = 0; nIndex < 8; nIndex++)
							{
								if (oObs.m_sStrings[nIndex] != null)
									nStringFlag = Obs.addFlag(nStringFlag, nIndex);
							}
							oRawOut.writeByte(nStringFlag);
						}
						oObs.writeStrings(oRawOut, m_oSP);
					}
					Iterator<int[]> oIt = Arrays.iterator(oObs.m_oGeoArray, nPt, 1, 2);
					if (oObs.m_yGeoType == Obs.LINESTRING)
						oRawOut.writeShort((short)((Arrays.size(oObs.m_oGeoArray) - 4) / 2));
					while (oIt.hasNext())
					{
						oIt.next();
						m_oM.lonLatToTilePixels(GeoUtil.fromIntDeg(oObs.m_oGeoArray[1]), GeoUtil.fromIntDeg(oObs.m_oGeoArray[2]), m_nX, m_nY, m_oRR.getZoom(), dPixels);
						oRawOut.writeShort((int)dPixels[0]);
						oRawOut.writeShort((int)dPixels[1]);
					}
					
					oVW.writeValue(oRawOut, TileFileWriter.nearest(oObs.m_dValue, m_oRR.getRound()));
					if (m_bWriteRecv)
						oRawOut.writeInt((int)((oObs.m_lTimeRecv - m_lFileRecv) / 1000));
					if (m_bWriteStart)
						oRawOut.writeInt((int)((oObs.m_lObsTime1 - m_lFileRecv) / 1000));
					if (m_bWriteEnd)
						oRawOut.writeInt((int)((oObs.m_lObsTime2 - m_lFileRecv) / 1000));
				}
				else if (oObs.m_yGeoType == Obs.POLYGON)
				{
					oPolygons.clear();
					if (oObs.m_oGeoArray[3] < nTile[3] || oObs.m_oGeoArray[5] > nTile[5] ||
						oObs.m_oGeoArray[4] < nTile[4] || oObs.m_oGeoArray[6] > nTile[6]) // only clip if polygon is outside of tile bounds
					{
						if (lTilePolyRef == 0L)
							lTilePolyRef = GeoUtil.makePolygon(nTile);
						long lObsPoly = GeoUtil.makePolygon(oObs.m_oGeoArray);
						long[] lClipRef = new long[]{0L, lTilePolyRef, lObsPoly};
						try
						{
							int nResults = GeoUtil.clipPolygon(lClipRef);
							while (nResults-- > 0)
							{
								int[] nGeo = GeoUtil.popResult(lClipRef[0]);
								oPolygons.add(nGeo);
							}
						}
						finally
						{
							GeoUtil.freePolygon(lObsPoly);
						}
					}
					else
					{
						oPolygons.add(oObs.m_oGeoArray);
					}	
				

					for (int[] nPolygon : oPolygons)
					{
						int nRingCount = nPolygon[1];
						int nPos = 2;
						int nFirstRingPointCount = nPolygon[nPos];
						if (nFirstRingPointCount > 65535) // spec is for unsigned short
							continue;
						if (m_bWriteObsFlag)
							oRawOut.writeByte(oObs.m_nObsFlag);
						if (m_bWriteObsType)
							oRawOut.writeInt(oObs.m_nObsTypeId);
						for (int nRing = 0; nRing < nRingCount; nRing++)
						{
							int nPoints = nPolygon[nPos];
							if (nRing > 0)
								GeoUtil.reverseRing(nPolygon, nPos);
							nPos += 5 + nPoints * 2;
						}

						if (m_oSP != null)
						{
							oRawOut.writeByte(m_nStringFlag);
							Obs.writeStrings(oObs.m_sStrings, oRawOut, m_oSP);
						}


						ArrayList<int[]> nRings = deltaTixels(nPolygon, dPt, m_oM, m_nX, m_nY, m_oRR.getZoom());
						oRawOut.writeShort(nRings.size()); // write total ring count

						for (int[] nRing : nRings)
						{
							oRawOut.writeShort(nRing.length / 2);
							for (int nIndex = 0; nIndex < nRing.length; nIndex++)
								oRawOut.writeShort(nRing[nIndex]);
						}

						oVW.writeValue(oRawOut, TileFileWriter.nearest(oObs.m_dValue, m_oRR.getRound()));
						if (m_bWriteRecv)
							oRawOut.writeInt((int)((oObs.m_lTimeRecv - m_lFileRecv) / 1000));
						if (m_bWriteStart)
							oRawOut.writeInt((int)((oObs.m_lObsTime1 - m_lFileRecv) / 1000));
						if (m_bWriteEnd)
							oRawOut.writeInt((int)((oObs.m_lObsTime2 - m_lFileRecv) / 1000));
					}
				}
				
			}
			oRawOut.flush();
		}
		catch (Exception oEx)
		{
			if (m_oLogger != null)
				m_oLogger.error(oEx, oEx);
		}
		finally
		{
			GeoUtil.freePolygon(lTilePolyRef);
		}
		
		if (oRawBytes.size() > 0)
			m_yTileData = XzBuffer.compress(oRawBytes.toByteArray());
		
		return null;
	}
	
	
	public final static ArrayList<int[]> deltaTixels(int[] nPolygon, double[] dPixels, Mercator oM, int nX, int nY, int nZoom)
	{
		int nNumRings = nPolygon[1];
		ArrayList<int[]> oRings = new ArrayList(nNumRings);
		int nPolyPos = 2;
		for (int nRingIndex = 0; nRingIndex < nNumRings; nRingIndex++)
		{
			int nNumPoints = nPolygon[nPolyPos];
			if (nNumPoints > 65535)
				continue;
			int[] dTixels = new int[nNumPoints * 2];
			int nTixelPos = 0;
			nPolyPos += 5; // skip number of points and bounding box
			int nEnd = nPolyPos + nNumPoints * 2;
			while (nPolyPos < nEnd)
			{
				oM.lonLatToTilePixels(GeoUtil.fromIntDeg(nPolygon[nPolyPos++]), GeoUtil.fromIntDeg(nPolygon[nPolyPos++]), nX, nY, nZoom, dPixels);
				dTixels[nTixelPos++] = (int)dPixels[0];
				dTixels[nTixelPos++] = (int)dPixels[1];
			}
			oRings.add(dTixels);
		}
		
		for (int[] nRing : oRings)
		{
			int nIndex = nRing.length - 2;
			int nCurX = nRing[nIndex];
			int nCurY = nRing[nIndex + 1];
			while (nIndex > 0)
			{
				int nPrevX = nRing[nIndex - 2];
				int nPrevY = nRing[nIndex - 1];
				nRing[nIndex] = nCurX - nPrevX;
				nRing[nIndex + 1] = nCurY - nPrevY;
				nIndex -= 2;
				nCurX = nPrevX;
				nCurY = nPrevY;
			}
		}
		
		return oRings;
	}
	
	
	@Override
	public int compareTo(TileForFile o)
	{
		int nRet = m_nX - o.m_nX;
		if (nRet == 0)
			nRet = m_nY - o.m_nY;

		return nRet;
	}
}
