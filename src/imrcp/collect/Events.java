/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.collect;

import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.Mercator;
import imrcp.store.Obs;
import imrcp.system.Arrays;
import imrcp.system.CsvReader;
import imrcp.system.Directory;
import imrcp.system.FilenameFormatter;
import imrcp.system.ObsType;
import imrcp.system.ResourceRecord;
import imrcp.system.Scheduling;
import imrcp.system.StringPool;
import imrcp.system.Text;
import imrcp.system.Util;
import imrcp.system.XzBuffer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

/**
 *
 * @author Federal Highway Administration
 */
public class Events extends Collector
{
	private static final String[] TYPES = new String[]{"workzone", "incident", "speed-change"};
	public static final int ALLLANES = 99;
	public static final int HALFLANES = 98;
	
	@Override
	public boolean start() throws Exception
	{
		m_nSchedId = Scheduling.getInstance().createSched(this, m_nOffset, m_nPeriod);
		return true;
	}
	
	@Override
	public void execute()
	{
		try
		{
			ArrayList<ResourceRecord> oRRs = Directory.getResourcesByContribSource(m_nContribId, m_nSourceId);
			long lNow = System.currentTimeMillis();
			lNow = lNow / 60000 * 60000;
			processRealTime(oRRs, lNow, lNow, lNow);
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
	
	
	@Override
	protected void createFiles(TileFileInfo oInfo, TreeSet<Path> oArchiveFiles)
	{
		try
		{
			ArrayList<ResourceRecord> oRRs = oInfo.m_oRRs;
			m_oLogger.debug("create files");
			int[] nTile = new int[2];
			int nPPT = (int)Math.pow(2, oRRs.get(0).getTileSize()) - 1;
			
			Mercator oM = new Mercator(nPPT);
			SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
			oSdf.setTimeZone(Directory.m_oUTC);
			double dIncident = ObsType.lookup(ObsType.EVT, "incident");
			double dWorkzone = ObsType.lookup(ObsType.EVT, "workzone");
			for (Path oPath : oArchiveFiles)
			{
				boolean bGzip;
				try (InputStream oIn = Files.newInputStream(oPath))
				{
					int y1 = oIn.read();
					int y2 = oIn.read();
					bGzip = y1 == 31 && y2 == 139; // read unsigned bytes so -117 = 139
				}
				
				ArrayList<EventRecord>[] oEvents = new ArrayList[]{new ArrayList(), new ArrayList()};
				int nEvt = 0;
				int nVsl = 1;
				
				try (CsvReader oIn = new CsvReader(new BufferedInputStream((bGzip ? new GZIPInputStream(Files.newInputStream(oPath)) : Files.newInputStream(oPath))), 9, '|'))
				{
					oIn.readLine(); // version string
					String sVersion = oIn.parseString(0);
					oIn.readLine(); // skip header
					StringBuilder sLocBuf = new StringBuilder();
					int nLine = 2;
					int nColumn = 0;
				
					while (oIn.readLine() > 0)
					{
						++nLine;
						try
						{
							nColumn = 0;
							if (oIn.isNull(0))
								throw new ParseException("Event Id is required", nLine);
							String sEventId = oIn.parseString(0);
							++nColumn;
							if (oIn.isNull(1))
								throw new ParseException("Event Type is required", nLine);
							String sEventType = oIn.parseString(1).toLowerCase();
							boolean bValidType = false;
							for (String sType : TYPES)
							{
								if (sType.compareTo(sEventType) == 0)
								{
									bValidType = true;
									break;
								}
							}
							if (!bValidType)
								throw new IOException("Invalid event type: " + sEventType);
							++nColumn;
							String sDesc = oIn.isNull(2) ? null : oIn.parseString(1);
							++nColumn;
							long lStart = oSdf.parse(oIn.parseString(3)).getTime();
							++nColumn;
							long lEnd = oIn.isNull(4) ? Long.MAX_VALUE : oSdf.parse(oIn.parseString(4)).getTime();
							++nColumn;
							long lUpdate = oSdf.parse(oIn.parseString(5)).getTime();
							++nColumn;
							int nLanes = oIn.isNull(6) ? HALFLANES : oIn.parseInt(6);
							++nColumn;
							double dSpeedLimit = oIn.isNull(7) ? Double.NaN : oIn.parseDouble(7);
							++nColumn;
							oIn.parseString(sLocBuf, 8);
							int nStart = 0;
							
							double[] dCoords = Arrays.newDoubleArray();
							while ((nStart = sLocBuf.indexOf("[", nStart)) >= 0)
							{
								++nStart; // skip '['
								int nEnd = sLocBuf.indexOf(",", nStart);
								double dLon = Text.parseDouble(sLocBuf.subSequence(nStart, nEnd));
								nStart = nEnd + 1; // skip ','
								double dLat = Text.parseDouble(sLocBuf.subSequence(nStart, sLocBuf.indexOf("]", nStart)));
								dCoords = Arrays.add(dCoords, dLon, dLat);
							}
							
							if (dCoords[0] == 3) // only one point
							{
								EventRecord oRec = new EventRecord(sEventId, sEventType, sDesc, lStart, lEnd, lUpdate, nLanes, dSpeedLimit, GeoUtil.toIntDeg(dCoords[1]), GeoUtil.toIntDeg(dCoords[2]), "u");
								oRec.m_sDir = "u";
								if (sEventType.compareTo("speed-change") == 0)
								{
									oEvents[nVsl].add(oRec);
									oRec.m_dValue = oRec.m_dSpeedLimit;
								}
								else
								{
									if (sEventType.compareTo("incident") == 0)
										oRec.m_dValue = dIncident;
									else
										oRec.m_dValue = dWorkzone;
									oEvents[nEvt].add(oRec);
								}
							}
							else
							{
								double[] dSeg = new double[4];
								Iterator<double[]> oIt = Arrays.iterator(dCoords, dSeg, 1, 2);
								String sDir = "u";
								while (oIt.hasNext())
								{
									oIt.next();
									double dHdg = GeoUtil.heading(dSeg[0], dSeg[1], dSeg[2], dSeg[3]);
									sDir = getDirection(dHdg);
									EventRecord oRec = new EventRecord(sEventId, sEventType, sDesc, lStart, lEnd, lUpdate, nLanes, dSpeedLimit, GeoUtil.toIntDeg(dSeg[0]), GeoUtil.toIntDeg(dSeg[1]), sDir);
									if (sEventType.compareTo("speed-change") == 0)
									{
										oEvents[nVsl].add(oRec);
										oRec.m_dValue = oRec.m_dSpeedLimit;
									}
									else
									{
										if (sEventType.compareTo("incident") == 0)
											oRec.m_dValue = dIncident;
										else
											oRec.m_dValue = dWorkzone;
										oEvents[nEvt].add(oRec);
									}	
								}
								
								EventRecord oRec = new EventRecord(sEventId, sEventType, sDesc, lStart, lEnd, lUpdate, nLanes, dSpeedLimit, GeoUtil.toIntDeg(dSeg[2]), GeoUtil.toIntDeg(dSeg[3]), sDir);
								if (sEventType.compareTo("speed-change") == 0)
								{
									oEvents[nVsl].add(oRec);
									oRec.m_dValue = oRec.m_dSpeedLimit;
								}
								else
								{
									if (sEventType.compareTo("incident") == 0)
										oRec.m_dValue = dIncident;
									else
										oRec.m_dValue = dWorkzone;
									oEvents[nEvt].add(oRec);
								}	
							}
						}
						catch (Exception oEx)
						{
							m_oLogger.error(String.format("Error parsing line %d column %d", nLine, nColumn));
							m_oLogger.error(oEx, oEx);
						}
					}
				}
				
				for (int nTypeIndex = 0; nTypeIndex < oEvents.length; nTypeIndex++)
				{
					int nObstype = nTypeIndex == nEvt ? ObsType.EVT : ObsType.VSLLNK;
					ArrayList<EventRecord> oRecords = oEvents[nTypeIndex];
					StringPool oSP = new StringPool();
					int[] nBB = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
					long lStartTime = Long.MAX_VALUE;
					long lEndTime = Long.MIN_VALUE;
					long lFileTime = oInfo.m_lRef;
					ResourceRecord oRR = null;
					for (ResourceRecord oTemp : oRRs)
					{
						if (oTemp.getObsTypeId() == nObstype)
						{
							oRR = oTemp;
							break;
						}
					}
					if (oRR == null)
						continue;
						
					ArrayList<TileForFile> oTiles = new ArrayList();
					for (EventRecord oRec : oRecords)
					{
						double dVal = oRec.m_dValue;
						if (!Double.isFinite(dVal) || dVal < 0.0) // ignore missing or invalid values
							continue;

						if (oRec.m_lStart < lStartTime)
							lStartTime = oRec.m_lStart;
						if (oRec.m_lEnd > lEndTime)
							lEndTime = oRec.m_lEnd;

						if (oRec.m_nLon < nBB[0])
							nBB[0] = oRec.m_nLon;
						if (oRec.m_nLat < nBB[1])
							nBB[1] = oRec.m_nLat;
						if (oRec.m_nLon > nBB[2])
							nBB[2] = oRec.m_nLon;
						if (oRec.m_nLat > nBB[3])
							nBB[3] = oRec.m_nLat;

						oM.lonLatToTile(GeoUtil.fromIntDeg(oRec.m_nLon), GeoUtil.fromIntDeg(oRec.m_nLat), oRR.getZoom(), nTile);
						TileForFile oTile = new TileForFile(nTile[0], nTile[1]);
						int nIndex = Collections.binarySearch(oTiles, oTile);
						if (nIndex < 0)
						{
							nIndex = ~nIndex;
							oTiles.add(nIndex, oTile);
						}

						Obs oObs = new Obs();
						oObs.m_lObsTime1 = oRec.m_lStart;
						oObs.m_lObsTime2 = oRec.m_lEnd;
						oObs.m_lTimeRecv = oRec.m_lStart;
						oObs.m_dValue = dVal;
						oObs.m_yGeoType = Obs.POINT;
						oObs.m_oGeoArray = Obs.createPoint(oRec.m_nLon, oRec.m_nLat);
						oObs.m_sStrings = new String[]{oRec.m_sEventId, oRec.m_sEventType, oRec.m_sDesc, Integer.toString(oRec.m_nLanesAffected), oRec.m_sDir, null, null, null};
						for (int nSIndex = 0; nSIndex < m_nStrings; nSIndex++)
						{
							String sStr = oObs.m_sStrings[nSIndex];
							if (sStr != null)
								oSP.intern(sStr);
						}

						oTiles.get(nIndex).m_oObsList.add(oObs);
					}
					if (oTiles.isEmpty())
						continue;

					int nStringFlag = 0;
					for (int nString = 0; nString < m_nStrings; nString++)
						nStringFlag = Obs.addFlag(nStringFlag, nString);

					ArrayList<String> oSPList = oSP.toList();
					for (TileForFile oTile : oTiles)
					{
						oTile.m_oSP = oSPList;
						oTile.m_oM = oM;
						oTile.m_oRR = oRR;
						oTile.m_lFileRecv = lFileTime;
						oTile.m_nStringFlag = nStringFlag;
						oTile.m_bWriteObsFlag = false;
						oTile.m_bWriteRecv = true;
						oTile.m_bWriteStart = true;
						oTile.m_bWriteEnd = true;
						oTile.m_bWriteObsType = false;
					}
					Scheduling.processCallables(oTiles, m_nThreads);
					
					FilenameFormatter oFF = new FilenameFormatter(oRR.getTiledFf());
					Path oTiledFile = oRR.getFilename(lFileTime, lStartTime, lEndTime, oFF);
					Files.createDirectories(oTiledFile.getParent());
					try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(oTiledFile))))
					{
						oOut.writeByte(1); // version
						oOut.writeInt(nBB[0]); // bounds min x
						oOut.writeInt(nBB[1]); // bounds min y
						oOut.writeInt(nBB[2]); // bounds max x
						oOut.writeInt(nBB[3]); // bounds max y
						oOut.writeInt(oRR.getObsTypeId()); // obsversation type
						oOut.writeByte(Util.combineNybbles(0, oRR.getValueType())); // obs flag not present. value type
						oOut.writeByte(Obs.POINT);
						oOut.writeByte(0); // id format: -1=variable, 0=null, 16=uuid, 32=32-bytes
						oOut.writeByte(Util.combineNybbles(0, 0b0111)); // associate with obj and timestamp flag. the lower bits are all 1 since recv, start, and end time are written per obs 
						oOut.writeLong(lFileTime);
						oOut.writeInt((int)((lEndTime - lFileTime)) / 1000); // end time offset from received time in seconds
						oOut.writeByte(1); // only file start time
						oOut.writeInt((int)((lStartTime - lFileTime)) / 1000); // start time offset from received time in seconds

						ByteArrayOutputStream oRawBytes = new ByteArrayOutputStream(8192);
						DataOutputStream oRawOut = new DataOutputStream(oRawBytes);
						for (String sStr : oSPList)
						{
							oRawOut.writeUTF(sStr);
						}
						oRawOut.flush();

						byte[] yCompressed = XzBuffer.compress(oRawBytes.toByteArray());
						oOut.writeInt(yCompressed.length);  // compressed string pool length
						oOut.writeInt(oSPList.size());
						oOut.write(yCompressed);

						oOut.writeByte(oRR.getZoom()); // tile zoom level
						oOut.writeByte(oRR.getTileSize());
						oOut.writeInt(oTiles.size());

						for (TileForFile oTile : oTiles) // finish writing tile metadata
						{
							oOut.writeShort(oTile.m_nX);
							oOut.writeShort(oTile.m_nY);
							oOut.writeInt(oTile.m_yTileData.length);
						}

						for (TileForFile oTile : oTiles)
						{
							oOut.write(oTile.m_yTileData);
						}
					}
				}
			}
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
	
	
	private String getDirection(double dHdg)
	{
		if (dHdg > Math.PI * 15 / 8)
			return "e";
		if (dHdg > Math.PI * 13 / 8)
			return "se";
		if (dHdg > Math.PI * 11 / 8)
			return "s";
		if (dHdg > Math.PI * 9 / 8)
			return "sw";
		if (dHdg > Math.PI * 7 / 8)
			return "w";
		if (dHdg > Math.PI * 5 / 8)
			return "nw";
		if (dHdg > Math.PI * 3 / 8)
			return "n";
		if (dHdg > Math.PI / 8)
			return "ne";
		
		return "e";
	}
	
	private class EventRecord
	{
		String m_sEventId;
		String m_sEventType;
		String m_sDesc;
		long m_lStart;
		long m_lEnd;
		long m_lUpdate;
		int m_nLanesAffected;
		double m_dSpeedLimit;
		int m_nLon;
		int m_nLat;
		double m_dValue;
		String m_sDir;
		
		EventRecord(String sExtId, String sEventType, String sDesc, long lStart, long lEnd, long lUpdate, int nLanes, double dSpeedLimit, int nLon, int nLat, String sDir)
		{
			m_sEventId = sExtId;
			m_sEventType = sEventType;
			m_sDesc = sDesc;
			m_lStart = lStart;
			m_lEnd = lEnd;
			m_lUpdate = lUpdate;
			m_nLanesAffected = nLanes;
			m_dSpeedLimit = dSpeedLimit;
			m_nLon = nLon;
			m_nLat = nLat;
			m_sDir = sDir;
		}
	}
}
