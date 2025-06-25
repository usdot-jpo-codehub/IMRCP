/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.store;

import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.Mercator;
import imrcp.system.Arrays;
import imrcp.system.Id;
import imrcp.system.Introsort;
import imrcp.system.ObsType;
import imrcp.system.ResourceRecord;
import imrcp.system.Util;
import imrcp.system.XzBuffer;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.zip.GZIPInputStream;

/**
 *
 * @author Federal Highway Administration
 */
public class TileFileReader 
{
	public final static byte VARIES = 0;
	public final static byte UBYTE = 1;
	public final static byte SBYTE = 2;
	public final static byte QUARTERFLOAT = 3;
	public final static byte USHORT = 4;
	public final static byte SSHORT = 5;
	public final static byte HALFFLOAT = 6;
	public final static byte UINT = 7;
	public final static byte SINT = 8;
	public final static byte SINGLEFLOAT = 9;
	public final static byte ULONG = 10;
	public final static byte SLONG = 11;
	public final static byte DOUBLEFLOAT = 12;
	public final static byte ULONGLONG = 13;
	public final static byte SLONGLONG = 14;
	public final static byte QUADFLOAT = 15;
	
	public static void parseFile(Path oFile, ObsList oObsList, int nQueryObsType, int nLon1, int nLat1, int nLon2, int nLat2, long lQueryStart, long lQueryEnd, long lRefTime, ResourceRecord oRR)
		throws IOException
	{
		if (Files.size(oFile) == 0)
			return;
		oObsList.m_bHasData = true;
		if (nLon1 > nLon2)
		{
			nLon1 ^= nLon2;
			nLon2 ^= nLon1;
			nLon1 ^= nLon2;
		}
		if (nLat1 > nLat2)
		{
			nLat1 ^= nLat2;
			nLat2 ^= nLat1;
			nLat1 ^= nLat2;
		}
		double dLon1 = GeoUtil.fromIntDeg(nLon1);
		double dLat1 = GeoUtil.fromIntDeg(nLat1);
		double dLon2 = GeoUtil.fromIntDeg(nLon2);
		double dLat2 = GeoUtil.fromIntDeg(nLat2);
		
		try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(Files.newInputStream(oFile))))
		{
			TileFileHeader oHeader = new TileFileHeader(oIn, oRR.getContribId());
			if (!GeoUtil.boundingBoxesIntersect(nLon1, nLat1, nLon2, nLat2, oHeader.m_nMinLon, oHeader.m_nMinLat, oHeader.m_nMaxLon, oHeader.m_nMaxLat))
				return;
			ArrayList<long[]> oIndex = new ArrayList(oHeader.m_nTileCount);
			long lFilePos = oHeader.m_nBytesRead + oHeader.m_nTileCount * 8;
			Mercator oM = new Mercator((int)Math.pow(2, oRR.getTileSize()) - 1);
			ArrayList<long[]> oTilesToLoad = new ArrayList();
			int[] nTiles = new int[2];
			oM.lonLatToTile(dLon1, dLat2, oHeader.m_nZoom, nTiles);
			int nStartX = nTiles[0];
			int nStartY = nTiles[1];
			oM.lonLatToTile(dLon2, dLat1, oHeader.m_nZoom, nTiles);
			int nEndX = nTiles[0];
			int nEndY = nTiles[1];
			for (int nX = nStartX; nX <= nEndX; nX++)
			{
				for (int nY = nStartY; nY <= nEndY; nY++)
				{
					oTilesToLoad.add(new long[]{nX, nY});
				}
			}
			Comparator<long[]> oTileComp = (long[] o1, long[] o2) ->
			{
				int nRet = Long.compare(o1[0], o2[0]);
				if (nRet == 0)
					nRet = Long.compare(o1[1], o2[1]);

				return nRet;
			};
			
			Introsort.usort(oTilesToLoad, oTileComp);
			for (int nIndex = 0; nIndex < oHeader.m_nTileCount; nIndex++)
			{
				long[] lEntry = new long[]{oIn.readShort(), oIn.readShort(), oIn.readInt(), lFilePos};
				if (Collections.binarySearch(oTilesToLoad, lEntry, oTileComp) >= 0)
					oIndex.add(lEntry);
				lFilePos += lEntry[2];
			}
			
			lFilePos = oHeader.m_nBytesRead + oHeader.m_nTileCount * 8;
			

			for (long[] lIndex : oIndex)
			{
				long lBytesToSkip = lIndex[3] - lFilePos;
				oIn.skipBytes((int)lBytesToSkip);
				readTile(oIn, lIndex, oHeader, oObsList, oM, nLon1, nLat1, nLon2, nLat2, nQueryObsType, lQueryStart, lQueryEnd, lRefTime, oRR);
				lFilePos = lIndex[3] + lIndex[2];
			}
		}
	}
	
	
	public static void readTile(DataInputStream oIn, long[] lIndex, TileFileHeader oHeader, ObsList oObsSet, Mercator oM, int nLon1, int nLat1, int nLon2, int nLat2, int nQueryObsType, long lQueryStart, long lQueryEnd, long lRefTime, ResourceRecord oRR)
		throws IOException
	{
		int nTileX = (int)lIndex[0];
		int nTileY = (int)lIndex[1];
		int nLength = (int)lIndex[2];
		double[] dLonLat = new double[2];
		byte[] yBuf = new byte[nLength];
		oIn.read(yBuf, 0, nLength);
		if (nLength == 0)
			return;
		
		boolean bGzip = yBuf.length > 1 && yBuf[0] == 31 && yBuf[1] == -117;
		boolean bXz = yBuf.length > 6 && yBuf[0] == -3 && yBuf[5] == 0; 
		
		if (bGzip || bXz)
		{
			if (bGzip)
			{
				InputStream oInComp = new GZIPInputStream(new ByteArrayInputStream(yBuf));
				ByteArrayOutputStream oBuf = new ByteArrayOutputStream(yBuf.length * 2);
				int nByte;
				while ((nByte = oInComp.read()) >= 0)
					oBuf.write(nByte);

				oBuf.flush();
				yBuf = oBuf.toByteArray();
			}
			else
			{
				yBuf = XzBuffer.decompress(yBuf);
			}
			
		}
		
		int nMask = oM.getTileSize();
		int nCount = 0;
		try (DataInputStream oData = new DataInputStream(new ByteArrayInputStream(yBuf)))
		{
			ArrayList<int[]> oGeometries = new ArrayList();
			while (oData.available() > 0)
			{
				++nCount;
				int nObsFlags = 0;
				int nGeoType = oHeader.m_yGeometryType;
				if (oHeader.m_bObsFlags)
				{
					nObsFlags = oData.readByte();
					nGeoType = Util.getLowerNybble(nObsFlags, false); // ls nybble is geo type
					nObsFlags = Util.getUpperNybble(nObsFlags, false); // ms nybble is obs flags (bridge, mobile, event, reserved)
				}
				int nObstype = oHeader.m_nObsTypeId;
				if (nObstype == ObsType.VARIES)
					nObstype = oData.readInt();
				
				int nSize = oHeader.m_yIdSize;
				if (nSize == -1)
					nSize = oIn.readByte();
				byte[] yId = new byte[nSize];
				oIn.read(yId, 0, nSize);
				Id oId = Id.NULLID;
				String[] sStrings = null;
				if (oHeader.m_oStringTable != null)
				{
					int nFlags = oData.readByte();
					int nFlag = 8;
					sStrings = new String[nFlag];
					while (nFlag-- > 0)
					{
						if (((nFlags >> nFlag) & 1) == 1)
						{
							sStrings[nFlag] = oHeader.m_oStringTable[oData.readInt()];
						}
					}
				}
				
				if (nGeoType == Obs.POINT)
				{
					int[] nPoint = imrcp.system.Arrays.newIntArray(2);
					int nTixelX = oData.readUnsignedShort();
					int nTixelY = oData.readUnsignedShort();
					oM.tilePixelsTolonLat(nTixelX, nTixelY, nTileX, nTileY, oHeader.m_nZoom, dLonLat);
					nPoint = imrcp.system.Arrays.add(nPoint, GeoUtil.toIntDeg(dLonLat[0]), GeoUtil.toIntDeg(dLonLat[1]));
					oGeometries.add(nPoint);
				}
				if (nGeoType == Obs.LINESTRING)
				{
					int nPointCount = oData.readShort();
					if (nPointCount == 1)
					{
						nGeoType = Obs.POINT;
						
						int[] nPoint = Arrays.newIntArray(2);
						nPoint = imrcp.system.Arrays.add(nPoint, GeoUtil.toIntDeg(dLonLat[0]), GeoUtil.toIntDeg(dLonLat[1]));
						oGeometries.add(nPoint);
					}
					else
					{
						int nTixelX = oData.readUnsignedShort();
						int nTixelY = oData.readUnsignedShort();
						oM.tilePixelsTolonLat(nTixelX, nTixelY, nTileX, nTileY, oHeader.m_nZoom, dLonLat);
						int[] nLine = Arrays.newIntArray(nPointCount * 2 + 4);
						nLine = imrcp.system.Arrays.add(nLine, new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE});
						nLine = imrcp.system.Arrays.addAndUpdate(nLine, GeoUtil.toIntDeg(dLonLat[0]), GeoUtil.toIntDeg(dLonLat[1]), 1);
						while (--nPointCount > 0)
						{
							int nOffset = oData.readUnsignedShort();
							nTixelX += nOffset;
							nTixelX = nTixelX & nMask;

							nOffset = oData.readUnsignedShort();
							nTixelY += nOffset;
							nTixelY = nTixelY & nMask;

							oM.tilePixelsTolonLat(nTixelX, nTixelY, nTileX, nTileY, oHeader.m_nZoom, dLonLat);
							nLine = imrcp.system.Arrays.addAndUpdate(nLine, GeoUtil.toIntDeg(dLonLat[0]), GeoUtil.toIntDeg(dLonLat[1]), 2);
						}

						oGeometries.add(nLine);
					}
				}
				if (nGeoType == Obs.POLYGON)
				{
					int nRingCount = oData.readShort();
					int[] nGeoArray = Arrays.newIntArray();
					nGeoArray = Arrays.add(nGeoArray, nRingCount);
					for (int nRingIndex = 0; nRingIndex < nRingCount; nRingIndex++)
					{
						int nPointCount = oData.readUnsignedShort();
						if (nPointCount == 0)
						{
							--nGeoArray[1];
							continue;
						}
						nGeoArray = Arrays.ensureCapacity(nGeoArray, nPointCount * 2 + 5); // 4 spots for bb and 1 for point count
						nGeoArray = Arrays.add(nGeoArray, nPointCount);
						int nBBPos = nGeoArray[0];
						nGeoArray = Arrays.add(nGeoArray, new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE});
						int nTixelX = oData.readUnsignedShort();
						int nTixelY = oData.readUnsignedShort();
						oM.tilePixelsTolonLat(nTixelX, nTixelY, nTileX, nTileY, oHeader.m_nZoom, dLonLat);

						
						if (nRingIndex == 0) // outer ring
						{
							nGeoArray = Arrays.addAndUpdate(nGeoArray, GeoUtil.toIntDeg(dLonLat[0]), GeoUtil.toIntDeg(dLonLat[1]), nBBPos);
							while(--nPointCount > 0)
							{
								int nOffset = oData.readUnsignedShort();
								nTixelX += nOffset;
								nTixelX = nTixelX & nMask;

								nOffset = oData.readUnsignedShort();
								nTixelY += nOffset;
								nTixelY = nTixelY & nMask;

								oM.tilePixelsTolonLat(nTixelX, nTixelY, nTileX, nTileY, oHeader.m_nZoom, dLonLat);
								nGeoArray = imrcp.system.Arrays.addAndUpdate(nGeoArray, GeoUtil.toIntDeg(dLonLat[0]), GeoUtil.toIntDeg(dLonLat[1]), nBBPos);
							}
						}
						else // hole
						{
							int nPos = nGeoArray[0] + nPointCount * 2;
							nGeoArray[0] = nPos;
							nGeoArray[--nPos] = GeoUtil.toIntDeg(dLonLat[1]);
							nGeoArray[--nPos] = GeoUtil.toIntDeg(dLonLat[0]);
							while(--nPointCount > 0)
							{
								int nOffset = oData.readUnsignedShort();
								nTixelX += nOffset;
								nTixelX = nTixelX & nMask;

								nOffset = oData.readUnsignedShort();
								nTixelY += nOffset;
								nTixelY = nTixelY & nMask;

								oM.tilePixelsTolonLat(nTixelX, nTixelY, nTileX, nTileY, oHeader.m_nZoom, dLonLat);
								nGeoArray[--nPos] = GeoUtil.toIntDeg(dLonLat[1]);
								nGeoArray[--nPos] = GeoUtil.toIntDeg(dLonLat[0]);
								Arrays.updateBb(nGeoArray, nGeoArray[nPos], nGeoArray[nPos + 1], nBBPos);
							}
						}
					}
					if (nGeoArray[1] > 0) // don't add invalid geometries
						oGeometries.add(nGeoArray);
				}
				double[] dVals = oHeader.m_oValueReader.readValues(oData, oHeader.m_lStartTimes.length - 1);

				long lRecv = oHeader.m_lRecv;
				long lStart = Long.MIN_VALUE;
				long lEnd = Long.MIN_VALUE;
				if (oHeader.m_bObsHaveReceived)
					lRecv = oHeader.m_lRecv + (oData.readInt() * 1000L);
				if (oHeader.m_bObsHaveStart)
					lStart = oHeader.m_lRecv + (oData.readInt() * 1000L);
				if (oHeader.m_bObsHaveEnd)
				{
					int nEndOffset = oData.readInt();
					if (nEndOffset == Integer.MAX_VALUE)
						lEnd = Long.MAX_VALUE;
					else
						lEnd = oHeader.m_lRecv + (nEndOffset * 1000L);
				}
				
				for (int nValIndex = 0; nValIndex < dVals.length; nValIndex++)
				{
					for (int[] oGeo : oGeometries)
					{
						Obs oObs = new Obs();
						oObs.m_dValue = dVals[nValIndex];
						oObs.m_oGeoArray = oGeo;
						oObs.m_yGeoType = (byte)nGeoType;
						oObs.m_oObjId = oId;
						if (oHeader.m_bObsHaveStart)
							oObs.m_lObsTime1 = lStart;
						else
							oObs.m_lObsTime1 = oHeader.m_lStartTimes[nValIndex];
						if (oHeader.m_bObsHaveEnd)
							oObs.m_lObsTime2 = lEnd;
						else
							oObs.m_lObsTime2 = oHeader.m_lStartTimes[nValIndex + 1];
						oObs.m_lTimeRecv = lRecv;
						oObs.m_nContribId = oRR.getContribId();
						oObs.m_nObsTypeId = nObstype;
						oObs.m_sStrings = sStrings;
						if (sStrings != null && oHeader.m_yAssociateObj == Id.SENSOR)
							oObs.m_oObjId = new Id(Id.SENSOR, sStrings);
						oObs.m_nObsFlag = nObsFlags;
						if (oObs.matches(nQueryObsType, lQueryStart, lQueryEnd, lRefTime, nLat1, nLat2, nLon1, nLon2))
							oObsSet.add(oObs);
					}
				}
				oGeometries.clear();
			}
		}
		catch (EOFException oEof) // can ignore eof exception, happens when the compressed byte fill up the buffer exactly
		{
			oEof.printStackTrace();
		}
	}
	
	
	private static class TileFileHeader
	{
		byte m_yVer;
		int m_nMinLon;
		int m_nMinLat;
		int m_nMaxLon;
		int m_nMaxLat;
		int m_nObsTypeId;
		boolean m_bObsFlags;
		ValueReader m_oValueReader;
		byte m_yGeometryType;
		byte m_yIdSize;
		byte m_yAssociateObj;
		boolean m_bObsHaveReceived;
		boolean m_bObsHaveStart;
		boolean m_bObsHaveEnd;
		long m_lRecv;
		long m_lEnd;
		long[] m_lStartTimes;
		String[] m_oStringTable;
		int m_nZoom;
		int m_nTileSize;
		int m_nTileCount;
		int m_nBytesRead;
		
		TileFileHeader(DataInputStream oIn, int nContribId)
			throws IOException
		{
			m_yVer = oIn.readByte();
			m_nMinLon = oIn.readInt();
			m_nMinLat = oIn.readInt();
			m_nMaxLon = oIn.readInt();
			m_nMaxLat = oIn.readInt();
			m_nObsTypeId = oIn.readInt();
			int nFlags = oIn.readByte();
			m_bObsFlags = Util.getUpperNybble(nFlags, false) == 1;
			int nValueType = Util.getLowerNybble(nFlags, false);
			m_oValueReader = newValueReader(nValueType);
			if (m_oValueReader == null)
				throw new IOException("Value type " + nValueType + " not implemented");
			m_yGeometryType = oIn.readByte();
			m_yIdSize = oIn.readByte();
			int nObjAndTimes = oIn.readByte();
			m_yAssociateObj = (byte)Util.getUpperNybble(nObjAndTimes, true);
			m_bObsHaveReceived = ((nObjAndTimes >> 2) & 0x1) == 1;
			m_bObsHaveStart = ((nObjAndTimes >> 1) & 0x1) == 1;
			m_bObsHaveEnd = (nObjAndTimes & 0x1) == 1;
			m_lRecv = oIn.readLong();
			int nEndOffset = oIn.readInt();
			if (nEndOffset == Integer.MAX_VALUE)
				m_lEnd = Long.MAX_VALUE;
			else
				m_lEnd = m_lRecv + (nEndOffset * 1000L);
			int nStartTimes = oIn.readByte();
			if (nStartTimes < 0)
			{
				int nSecond = oIn.readByte();
				nStartTimes <<= 8;
				nStartTimes += nSecond;
				nStartTimes = -nStartTimes;
			}
			m_lStartTimes = new long[nStartTimes + 1];
			m_lStartTimes[0] = m_lRecv + (oIn.readInt() * 1000L);
			for (int nIndex = 1; nIndex < nStartTimes; nIndex++)
			{
				m_lStartTimes[nIndex] = m_lStartTimes[nIndex - 1] + (oIn.readInt() * 1000L);
			}
			m_lStartTimes[nStartTimes] = m_lEnd;
			int nStringTableLength = oIn.readInt();
			if (nStringTableLength > 0)
			{
				int nNumStrings = oIn.readInt();
				byte[] yBuf = new byte[nStringTableLength];
				oIn.read(yBuf, 0, nStringTableLength);
				if (yBuf.length > 6 && yBuf[0] == -3 && yBuf[5] == 0)
					yBuf = XzBuffer.decompress(yBuf);

				try (DataInputStream oData = new DataInputStream(new ByteArrayInputStream(yBuf)))
				{
					m_oStringTable = new String[nNumStrings];
					for (int nIndex = 0; nIndex < nNumStrings; nIndex++)
						m_oStringTable[nIndex] = oData.readUTF();
				}
			}
			
			m_nZoom = oIn.readByte();
			m_nTileSize = oIn.readByte();
			m_nTileCount = oIn.readInt();
			m_nBytesRead = 48 + ((m_lStartTimes.length - 1) * 4) + nStringTableLength;
		}
	}
	
	
	private static ValueReader newValueReader(int nType)	
	{
		switch (nType)
		{
			case SBYTE:
				return new SignedByteReader();
			case UBYTE:
				return new UnsignedByteReader();
			case SSHORT:
				return new SignedShortReader();
			case USHORT:
				return new UnsignedShortReader();
			case HALFFLOAT:
				return new HalfPrecisionReader();
			case SINGLEFLOAT:
				return new FloatReader();
			default:
				return null;
		}
	}
	
	private static abstract class ValueReader
	{
		abstract double[] readValues(DataInputStream oIn, int nValues) throws IOException;
	}

	private static class SignedByteReader extends ValueReader
	{
		@Override
		double[] readValues(DataInputStream oIn, int nValues)
			throws IOException
		{
			double[] dVals = new double[nValues];
			for (int nIndex = 0; nIndex < nValues; nIndex++)
				dVals[nIndex] = oIn.readByte();
			
			return dVals;
		}
	}
	
	
	private static class UnsignedByteReader extends ValueReader
	{
		@Override
		double[] readValues(DataInputStream oIn, int nValues)
			throws IOException
		{
			double[] dVals = new double[nValues];
			for (int nIndex = 0; nIndex < nValues; nIndex++)
				dVals[nIndex] = oIn.readUnsignedByte();
			
			return dVals;
		}
	}
	
	
	private static class HalfPrecisionReader extends ValueReader
	{
		@Override
		double[] readValues(DataInputStream oIn, int nValues) throws IOException
		{
			double[] dVals = new double[nValues];
			for (int nIndex = 0; nIndex < nValues; nIndex++)
				dVals[nIndex] = Util.fromHpfp(oIn.readShort());
			
			return dVals;
		}
	}
	
	
	private static class SignedShortReader extends ValueReader
	{
		@Override
		double[] readValues(DataInputStream oIn, int nValues) throws IOException
		{
			double[] dVals = new double[nValues];
			for (int nIndex = 0; nIndex < nValues; nIndex++)
				dVals[nIndex] = oIn.readShort();
			
			return dVals;
		}
	}
	
	
	private static class UnsignedShortReader extends ValueReader
	{
		@Override
		double[] readValues(DataInputStream oIn, int nValues) throws IOException
		{
			double[] dVals = new double[nValues];
			for (int nIndex = 0; nIndex < nValues; nIndex++)
				dVals[nIndex] = oIn.readUnsignedShort();
			
			return dVals;
		}
	}
	
	
	private static class FloatReader extends ValueReader
	{
		@Override
		double[] readValues(DataInputStream oIn, int nValues) throws IOException
		{
			double[] dVals = new double[nValues];
			for (int nIndex = 0; nIndex< nValues; nIndex++)
				dVals[nIndex] = oIn.readFloat();
			
			return dVals;
		}
	}
}
