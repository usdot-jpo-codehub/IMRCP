/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.forecast.mlp;

import imrcp.collect.NHC;
import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.Mercator;
import imrcp.geosrv.Network;
import imrcp.geosrv.WayNetworks;
import imrcp.geosrv.osm.OsmWay;
import imrcp.store.Obs;
import imrcp.store.ObsList;
import imrcp.store.TileObsView;
import imrcp.system.Arrays;
import imrcp.system.CsvReader;
import imrcp.system.Directory;
import imrcp.system.FileUtil;
import imrcp.system.FilenameFormatter;
import imrcp.system.Id;
import imrcp.system.Introsort;
import imrcp.system.MathUtil;
import imrcp.system.ObsType;
import imrcp.system.ResourceRecord;
import imrcp.system.Scheduling;
import imrcp.collect.TileFileWriter;
import imrcp.system.Units;
import imrcp.system.Util;
import imrcp.system.XzBuffer;
import imrcp.system.dbf.DbfResultSet;
import imrcp.system.shp.ShpReader;
import imrcp.web.NetworkGeneration;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Federal Highway Administration
 */
public class MLPHurricane extends MLP
{
	private final String m_sLinkSpeedPast = "link_speed_past7d.csv";
	private final String m_sOneshot = "hos_input.csv";
	private final String m_sOneshotMinMax = "dummy_input_oneshot.csv";
	private final String m_sLinkSpeedNext = "link_speed_next7d.csv";
	private final String m_sLinkSpeedNextMinMax = "dummy_input_lstm.csv";
	private final String m_sOnlineOutput = "hur_output.csv";
	private final String m_sOneshotOutput = "hos_output.csv";
	private String m_sTrainingDir;
	private String m_sDirFormatString;
	private static WayNetworks g_oWayNetworks;
	private static TileObsView g_oOv;
	public static String STATUSLOG = "status.log";
	private Process m_oTrainingProcess = null;
	private String m_sNetworkTraining = null;
	private final Object HURLOCK = new Object();
	
	/**
	 * A rough estimate of the coast line used to determine when landfall 
	 * happens
	 */
	private final double[] m_dCoastLine = new double[]{-97.1836293,27.5947176,-96.9851421,27.8959487,-96.5378880,28.2554942,
		-95.4662076,28.8118550,-94.7655095,29.2723850,-94.7124878,29.3353212,-94.6860702,29.4059219,-94.0173520,29.6624647,-93.8447573,29.6639951,
		-93.7619823,29.7206023,-93.2353925,29.7649478,-92.7915777,29.6349141,-92.6524743,29.5908297,-92.3215409,29.5246281,-92.2522758,29.5357888,
		-92.1368340,29.5833935,-92.0524405,29.5779252,-92.0096842,29.5526366,-91.8437900,29.4744995,-91.7659736,29.4804549,-91.6931480,29.5554194,
		-91.5403823,29.5280930,-91.3405020,29.4485561,-91.3419297,29.2930307,-91.2848210,29.2443715,-91.0592418,29.1745860,-90.8408011,29.1446633,
		-90.6480594,29.2020074,-90.5852398,29.2929440,-90.4681671,29.2854728,-90.2782807,29.2493543,-90.2640036,29.0798011,-90.1926177,29.0810488,
		-89.8942249,29.2792464,-89.4958920,29.2530913,-89.4130844,28.9262144,-89.1318242,28.9849289,-88.9807647,29.1773387,-89.0550059,29.2558420,
		-89.2163379,29.3753502,-89.4262123,29.4151552,-89.4247846,29.6536571,-89.2203503,29.8253632,-89.1603862,30.0641284,-89.1703802,30.3220395,
		-88.9084479,30.3826008,-88.7057121,30.3333227,-88.4958378,30.3111394,-88.1574689,30.3037438,-88.0189804,30.2087848,-87.5506893,30.2408592,
		-86.8994420,30.3627083,-86.4882596,30.3737948,-86.2716506,30.3488598,-85.7528762,30.1237185,-85.3981792,29.8917873};
	
	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_sTrainingDir = oBlockConfig.getString("mlptraining");
		if (!m_sTrainingDir.endsWith("/"))
			m_sTrainingDir += "/";
		m_sTrainingDir = m_sArchPath + m_sTrainingDir;
		m_sDirFormatString = m_sTempPath + "mlp/%s/%s/";		
	}
	
	
	@Override
	public boolean start()
		throws Exception
	{
//		Scheduling.getInstance().scheduleOnce(this, 1000);
		m_nSchedId = Scheduling.getInstance().createSched(this, m_nOffset, m_nPeriod);
		return true;
	}

	
	@Override
	public void execute()
	{
		synchronized (HURLOCK)
		{
			if (m_oTrainingProcess != null)
			{
				if (!m_oTrainingProcess.isAlive())
				{
					m_sNetworkTraining = null;
					m_oTrainingProcess = null;
				}
			}
		}
		int nPeriodMillis = m_nPeriod * 1000;
		long lNow = System.currentTimeMillis() / nPeriodMillis * nPeriodMillis; // floor to nearest period
//		long lNow = 1630040400000L; // ida for test
		TileObsView oOv = (TileObsView)Directory.getInstance().lookup("ObsView");

		Comparator<Obs> oConeComp = (Obs o1, Obs o2) -> // compare cones by reference time and storm number and forecast number
		{
			int nReturn = Long.compare(o2.m_lTimeRecv, o1.m_lTimeRecv);
			if (nReturn == 0)
				nReturn = (o1.m_sStrings[0] + o1.m_sStrings[1]).compareTo(o2.m_sStrings[0] + o2.m_sStrings[1]);
			return nReturn;
		};
		
		ObsList oCones = oOv.getData(ObsType.TRSCNE, lNow, lNow + 60000, 0, 650000000, -1790000000, 0, lNow); // get all possible cones
		int nConeIndex = oCones.size();
		while (nConeIndex-- > 0)
		{
			Obs oCone = oCones.get(nConeIndex);
			if (lNow - oCone.m_lTimeRecv > 21600000) // ignore forecast older than 6 hours
				oCones.remove(nConeIndex);
		}
			
		if (oCones.isEmpty())
			return;
		Introsort.usort(oCones, oConeComp);
		WayNetworks oNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
		ArrayList<MLPHurricaneSession> oSessions = new ArrayList();
		for (Network oNetwork : oNetworks.getNetworks())
		{
			try
			{
				String sNetworkDir = oNetworks.getNetworkDir(oNetwork.m_sNetworkId);
				JSONObject oStatus = oNetworks.getHurricaneModelStatus(oNetwork);
				
				sNetworkDir = getModelDir(sNetworkDir, oStatus.getString("model"), true);
				if (!hurricaneModelFilesExist(sNetworkDir)) // model files do not exist so don't run the traffic model
					continue; 

				int[] nNetworkPoly = oNetwork.getGeometry();
				String[] sStorm = null;
				for (Obs oCone : oCones)
				{
					if (oCone.spatialMatch(nNetworkPoly))
					{
						sStorm = oCone.m_sStrings;
						break;
					}
				}
				if (sStorm == null)
					continue;
				m_oLogger.debug(String.format("network %s - storm %s %s", oNetwork.m_sNetworkId, sStorm[0], sStorm[1]));
				int[] nCone = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
				Obs oCone = null;
				for (Obs oTemp : oCones)
				{
					if (oTemp.m_sStrings[0].compareTo(sStorm[0]) == 0 && oTemp.m_sStrings[1].compareTo(sStorm[1]) == 0)
					{
						oCone = oTemp;
						if (oTemp.m_oGeoArray[3] < nCone[0])
							nCone[0] = oTemp.m_oGeoArray[3];
						if (oTemp.m_oGeoArray[4] < nCone[1])
							nCone[1] = oTemp.m_oGeoArray[4];
						if (oTemp.m_oGeoArray[5] > nCone[2])
							nCone[2] = oTemp.m_oGeoArray[5];
						if (oTemp.m_oGeoArray[6] > nCone[3])
							nCone[3] = oTemp.m_oGeoArray[6];
					}
				}

				ObsList oCats = oOv.getData(ObsType.TRSCAT, oCone.m_lObsTime1, oCone.m_lObsTime2, nCone[1], nCone[3], nCone[0], nCone[2], oCone.m_lTimeRecv);
				int nIndex = oCats.size();
				while (nIndex-- > 0)
				{
					Obs oTemp = oCats.get(nIndex);
					if (oCone.m_lTimeRecv != oTemp.m_lTimeRecv || oCone.m_sStrings[0].compareTo(oTemp.m_sStrings[0]) != 0 ||
						oCone.m_sStrings[1].compareTo(oTemp.m_sStrings[1]) != 0 ||
						oCone.m_sStrings[2].compareTo(oTemp.m_sStrings[2]) != 0)
						oCats.remove(nIndex);
				}
				Introsort.usort(oCats, Obs.g_oCompObsByTime);
				ObsList oGstWnds = oOv.getData(ObsType.GSTWND, oCone.m_lObsTime1, oCone.m_lObsTime2, nCone[1], nCone[3], nCone[0], nCone[2], oCone.m_lTimeRecv);
				nIndex = oGstWnds.size();
				while (nIndex-- > 0)
				{
					Obs oTemp = oGstWnds.get(nIndex);
					if (oCone.m_lTimeRecv != oTemp.m_lTimeRecv || oTemp.m_sStrings[0].compareTo(oTemp.m_sStrings[0]) != 0 ||
						oCone.m_sStrings[1].compareTo(oTemp.m_sStrings[1]) != 0 ||
						oCone.m_sStrings[2].compareTo(oTemp.m_sStrings[2]) != 0)
						oGstWnds.remove(nIndex);
				}
				Introsort.usort(oGstWnds, Obs.g_oCompObsByTime);
				m_oLogger.debug("Determining landfall");
				double[] dLandfall = new double[]{Double.NaN, Double.NaN};
				long lLandfall = Long.MIN_VALUE;
				for (nIndex = 0; nIndex < oCats.size() - 1;)
				{
					Obs oCat1 = oCats.get(nIndex);
					Obs oCat2 = oCats.get(++nIndex);
					
					double dX1 = GeoUtil.fromIntDeg(oCat1.m_oGeoArray[1]);
					double dY1 = GeoUtil.fromIntDeg(oCat1.m_oGeoArray[2]);
					double dX2 = GeoUtil.fromIntDeg(oCat2.m_oGeoArray[1]);
					double dY2 = GeoUtil.fromIntDeg(oCat2.m_oGeoArray[2]);
					
					double dMinX = Math.min(dX1, dX2);
					double dMinY = Math.min(dY1, dY2);
					double dMaxX = Math.max(dX1, dX2);
					double dMaxY = Math.max(dY1, dY2);
					for (int nCoastIndex = 0; nCoastIndex < m_dCoastLine.length - 2; nCoastIndex += 2) // see if the hurricane track segment intersects with any of the line segments of the coastline
					{
						double dCx1 = m_dCoastLine[nCoastIndex];
						double dCy1 = m_dCoastLine[nCoastIndex + 1];
						double dCx2 = m_dCoastLine[nCoastIndex + 2];
						double dCy2 = m_dCoastLine[nCoastIndex + 3];

						if (GeoUtil.boundingBoxesIntersect(dMinX, dMinY, dMaxX, dMaxY, 
							Math.min(dCx1, dCx2), Math.min(dCy1, dCy2), Math.max(dCx1, dCx2), Math.max(dCy1, dCy2)))
							GeoUtil.getIntersection(dX1, dY1, dX2, dY2, dCx1, dCy1, dCx2, dCy2, dLandfall); // getIntersection fills in the first two position of dLandfall with the lon and lat if the two line segments insect, otherwise they get set to NaN


						if (Double.isFinite(dLandfall[0])) // intersection was found
						{
							double dTotalDistance = GeoUtil.distance(dX1, dY1, dX2, dY2);
							double dDistToLandfall = GeoUtil.distance(dX1, dY1, dLandfall[0], dLandfall[1]);
							double dRatio = dDistToLandfall / dTotalDistance;
							long lTimeDiff = oCat2.m_lObsTime1 - oCat1.m_lObsTime1;
							lLandfall = oCat1.m_lObsTime1 + (long)(lTimeDiff * dRatio) / 3600000 * 3600000;
							break;
						}
					}
				}
				m_oLogger.debug(String.format("Landfall: %.7f,%.7f", dLandfall[0], dLandfall[1]));
				if (Double.isNaN(dLandfall[0]) || lLandfall < lNow) // hurricane is not projected to make landfall or landfall has already occured
					continue;
				
				MLPHurricaneSession oSess = new MLPHurricaneSession(oNetwork, lNow, sNetworkDir, oNetwork.m_oNetworkWays, oCones.get(0), oCats, oGstWnds, dLandfall, lLandfall);
				oSessions.add(oSess);
			}
			catch (Exception oEx)
			{
				m_oLogger.error(oEx, oEx);
			}
		}
		if (oSessions.isEmpty())
			return;
		g_oWayNetworks = oNetworks;
		g_oOv = oOv;
		try
		{
			Scheduling.processCallables(oSessions, m_nPythonProcesses);
			ArrayList<MLPHurricaneSession> oOneshotSessions = new ArrayList(oSessions.size());
			for (MLPHurricaneSession oSession : oSessions)
			{
				if (oSession.m_nExit == 0)
					oOneshotSessions.add(new MLPHurricaneSession(oSession));
			}
			Scheduling.processCallables(oOneshotSessions, m_nPythonProcesses);
			createHurricaneFiles(oSessions);
			createHurricaneFiles(oOneshotSessions);
			try
			{
				for (MLPHurricaneSession oSess : oSessions)
				{
					for (Path oFile : Files.walk(Paths.get(oSess.m_sDirectory), FileVisitOption.FOLLOW_LINKS).sorted(Comparator.reverseOrder()).collect(Collectors.toList())) // delete all associated files
					{
						Files.delete(oFile);
					}
				}
			}
			catch (IOException oIOEx)
			{
				m_oLogger.error(oIOEx, oIOEx);
			}
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
	
	
	private void createHurricaneFiles(ArrayList<MLPHurricaneSession> oSessions)
	{
		try
		{
			m_oLogger.debug("create files");
			long lFileTime = oSessions.get(0).m_lRuntime;
			int nContrib = Integer.valueOf("mlphur", 36);
			String sOutput = m_sOnlineOutput;
			long lDiff = 3600000;
			if (oSessions.get(0).m_sFunction.compareTo("hos") == 0)
			{
				nContrib = Integer.valueOf("mlphos", 36);
				sOutput = m_sOneshotOutput;
				lDiff = 300000;
			}
			long[] lTimes;
			if (lDiff == 3600000)
			{
				lTimes = new long[6];
			}
			else
			{
				lTimes = new long[2016];
			}
			java.util.Arrays.fill(lTimes, lDiff);
			lTimes[0] = lFileTime;
			WayNetworks oWays = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
			ArrayList<MLPHurricaneTile> oTiles = new ArrayList();
			ResourceRecord oRR = Directory.getResource(nContrib, ObsType.SPDLNK);
			
			if (oRR == null)
				throw new Exception("No valid resource record configured");
			
			long lStartTime = lFileTime;
			long lEndTime = lStartTime + oRR.getRange();
			int[] nTile = new int[2];
			int nPPT = (int)Math.pow(2, oRR.getTileSize()) - 1;
			
			Mercator oM = new Mercator(nPPT);
			int[] nBB = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
			Units.UnitConv oConv = Units.getInstance().getConversion(oRR.getSrcUnits(), ObsType.getUnits(ObsType.SPDLNK, true));
			
			for (MLPHurricaneSession oSess : oSessions)
			{
				TreeMap<Id, double[]> oPreds = new TreeMap(Id.COMPARATOR);
				Path oPath = Paths.get(oSess.m_sDirectory + sOutput);
				if (!Files.exists(oPath))
					continue;
				try (CsvReader oIn = new CsvReader(Files.newInputStream(oPath)))
				{
					int nCols;
					while ((nCols = oIn.readLine()) > 0)
					{
						Id oId = new Id(oIn.parseString(0));
						OsmWay oWay = oWays.getWayById(oId);
						if (oWay == null)
							continue;
						if (oWay.m_nMinLon < nBB[0])
							nBB[0] = oWay.m_nMinLon;
						if (oWay.m_nMinLat < nBB[1])
							nBB[1] = oWay.m_nMinLat;
						if (oWay.m_nMaxLon > nBB[2])
							nBB[2] = oWay.m_nMaxLon;
						if (oWay.m_nMaxLat > nBB[3])
							nBB[3] = oWay.m_nMaxLat;
						
						double[] dTileInfo = new double[nCols + 1]; // number of columns is the number of predictions + 1, we need number of predictions + 2
						java.util.Arrays.fill(dTileInfo, Double.NaN);
						dTileInfo[0] = GeoUtil.fromIntDeg(oWay.m_nMidLon);
						dTileInfo[1] = GeoUtil.fromIntDeg(oWay.m_nMidLat);
						for (int nCol = 1; nCol < nCols; nCol++)
						{
							dTileInfo[nCol + 1] = nearest(oConv.convert(oIn.parseDouble(nCol)), oRR.getRound());
						}
						oPreds.put(oId, dTileInfo);
					}
				}
				catch (Exception oEx)
				{
					m_oLogger.error(oEx, oEx);
				}
				m_oLogger.info(String.format("%s predictions for %s - %d", Integer.toString(nContrib, 36), oSess.m_oNetwork.m_sNetworkId, oPreds.size()));
				int nExtended = extendPredictions(oPreds, oSess.m_oSegments, oWays, m_dExtendDistTol, nBB);
				m_oLogger.info(String.format("Extended %s predictions for %s - %d", Integer.toString(nContrib, 36), oSess.m_oNetwork.m_sNetworkId, nExtended));
				for (double[] dTileInfo : oPreds.values())
				{
					oM.lonLatToTile(dTileInfo[0], dTileInfo[1], oRR.getZoom(), nTile);
					MLPHurricaneTile oTile = new MLPHurricaneTile(nTile[0], nTile[1]);
					int nIndex = Collections.binarySearch(oTiles, oTile);
					if (nIndex < 0)
					{
						nIndex = ~nIndex;
						oTiles.add(nIndex, oTile);
					}

					oTile = oTiles.get(nIndex);

					oTile.m_oTileInfos.add(dTileInfo);
				}
				
			}
			if (oTiles.isEmpty())
				return;

			
			for (MLPHurricaneTile oTile : oTiles)
			{
				oTile.m_oM = oM;
				oTile.m_oRR = oRR;
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
				oOut.writeByte(Util.combineNybbles(Id.SEGMENT, 0b0000)); // associate with obj and timestamp flag. the lower bits are all 1 since recv, start, and end time are written per obs 
				oOut.writeLong(lFileTime);
				oOut.writeInt((int)((lEndTime - lFileTime)) / 1000); // end time offset from received time in seconds
				if (lTimes.length > Byte.MAX_VALUE)
					oOut.writeShort(-lTimes.length);
				else
					oOut.writeByte(lTimes.length);
				oOut.writeInt((int)((lTimes[0] - lFileTime) / 1000)); // start time offset from the received time for the first value
				for (int nIndex = 1; nIndex < lTimes.length; nIndex++) // the rest are offset from the previous value
					oOut.writeInt((int)(lTimes[nIndex] / 1000));
				oOut.writeInt(0); // no string pool
				oOut.writeByte(oRR.getZoom()); // tile zoom level
				oOut.writeByte(oRR.getTileSize());
				oOut.writeInt(oTiles.size());

				for (MLPHurricaneTile oTile : oTiles) // finish writing tile metadata
				{
					oOut.writeShort(oTile.m_nX);
					oOut.writeShort(oTile.m_nY);
					oOut.writeInt(oTile.m_yTileData.length);
				}

				for (MLPHurricaneTile oTile : oTiles)
				{
					oOut.write(oTile.m_yTileData);
				}
			}
			
			
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
		m_oLogger.debug("done");
	}

	private void interpolate(ArrayList<LinkSpeedPastRecord> oAllSpeeds, int nIndex)
	{
		ArrayList<LinkSpeedPastRecord> oInterpolates = new ArrayList(m_nInterpolateLimit);
		LinkSpeedPastRecord oPrev = null;
		int nPrevIndex = -1;
		while (nIndex < oAllSpeeds.size())
		{
			LinkSpeedPastRecord oCur = oAllSpeeds.get(nIndex++);
			if (!Double.isNaN(oCur.m_dSpeed))
			{
				oPrev = oCur;
				nPrevIndex = nIndex - 1;
				continue;
			}
			
			oInterpolates.clear();
			if (oPrev != null) 
			{
				oInterpolates.add(oPrev); // start with the last non nan speed
				oInterpolates.add(oCur);
				if (nIndex < oAllSpeeds.size())
					oCur = oAllSpeeds.get(nIndex++);
				while (Double.isNaN(oCur.m_dSpeed) && oInterpolates.size() < m_nInterpolateLimit && nIndex < oAllSpeeds.size())
				{
					oInterpolates.add(oCur);
					oCur = oAllSpeeds.get(nIndex++);
				}
				if (!Double.isNaN(oCur.m_dSpeed))
				{
					oInterpolates.add(oCur);
				}
				else if (nIndex == oAllSpeeds.size()) // the last speed record is NaN
				{
					if (nIndex - nPrevIndex < m_nInterpolateLimit - 2)
					{
						while (nIndex-- > nPrevIndex)
							oAllSpeeds.get(nIndex).m_dSpeed = oPrev.m_dSpeed;
					}
				}
				else
					return; // not enough valids speed to interpolate
				
				int nSteps = oInterpolates.size() - 2;
				double dRange = oInterpolates.get(nSteps + 1).m_dSpeed - oInterpolates.get(0).m_dSpeed;
				double dStep = dRange / nSteps;
				double dPrevVal = oInterpolates.get(0).m_dSpeed;
				for (int nIntIndex = 1; nIntIndex < nSteps + 1; nIntIndex++)
				{
					dPrevVal += dStep;
					oInterpolates.get(nIntIndex).m_dSpeed = dPrevVal;
				}
				
				oPrev = oCur;
				nPrevIndex = nIndex - 1;
			}
			else // first speed is NaN so look ahead to try and find a non NaN value
			{
				for (int nLookAhead = nIndex; nLookAhead < m_nInterpolateLimit - 2; nLookAhead++)
				{
					LinkSpeedPastRecord oRec = oAllSpeeds.get(nLookAhead);
					if (!Double.isNaN(oRec.m_dSpeed))
					{
						while (nLookAhead-- > nIndex - 1)
							oAllSpeeds.get(nLookAhead).m_dSpeed = oRec.m_dSpeed;
						
						break;
					}
				}
			}
		}
	}
	
	
	public boolean isTraining()
	{
		synchronized (HURLOCK)
		{
			return m_sNetworkTraining != null;
		}
	}
	
	
	public void checkProcess()
	{
		synchronized (HURLOCK)
		{
			if (m_oTrainingProcess == null)
				return;
			BufferedReader oIn = new BufferedReader(new InputStreamReader(m_oTrainingProcess.getInputStream()));
			WayNetworks oWays = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
			String sStatusLog = oWays.getNetworkDir(m_sNetworkTraining) + STATUSLOG;
			try
			{
				while (oIn.ready())
				{
					String sOutput = oIn.readLine();
					if (sOutput.endsWith("stop"))
					{
						m_oTrainingProcess = null;
						m_sNetworkTraining = null;
						return;
					}
					if (sOutput.endsWith("!@#"))
					{
						
						sOutput = sOutput.substring(0, sOutput.length() - 3);
						statuslog(sOutput.substring(sOutput.lastIndexOf(" - ") + " - ".length()), sStatusLog);
					}
					m_oLogger.info(sOutput);
				}
			}
			catch (IOException oEx)
			{
				m_oLogger.error(oEx, oEx);
			}
			if (m_oTrainingProcess.isAlive())
				Scheduling.getInstance().scheduleOnce(() -> checkProcess(), 60000);
			else
			{
				Network oNetwork = oWays.getNetwork(m_sNetworkTraining);
				JSONObject oStatus = oWays.getHurricaneModelStatus(oNetwork);
				String sCurrentModel = oStatus.getString("model");
				String sNextModel = sCurrentModel.contains("model_a") ? "model_b/" : "model_a/";
				String sNetworkDir = oWays.getNetworkDir(oNetwork.m_sNetworkId);
				String sModelDir = sNetworkDir + sNextModel;
				oNetwork.removeStatus(Network.TRAINING);
				if (MLP.hurricaneModelFilesExist(sModelDir))
				{
					oWays.writeHurricaneModelStatus(oNetwork, sNextModel, WayNetworks.HURMODEL_TRAINED);
					oNetwork.m_sMsg = "";
				}
				else
				{
					oWays.writeHurricaneModelStatus(oNetwork, sCurrentModel, WayNetworks.HURMODEL_ERROR);
					oNetwork.addStatus(Network.ERROR);
				}
				try
				{
					oWays.writeNetworkFile();
				}
				catch (IOException oEx)
				{
					m_oLogger.error(oEx, oEx);
				}
				m_oTrainingProcess = null;
				m_sNetworkTraining = null;
			}
		}
	}
	
	
	public void train(Network oNetwork)
	{
		synchronized (HURLOCK)
		{
			m_sNetworkTraining = oNetwork.m_sNetworkId;
		}
		String sBaseDir = m_sTrainingDir + oNetwork.m_sNetworkId + "/";
		m_oLogger.info("Training " + oNetwork.m_sNetworkId);
		Path oBaseDir = Paths.get(sBaseDir);
		WayNetworks oWays = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
		JSONObject oStatus = oWays.getHurricaneModelStatus(oNetwork);
		String sStatusLog = oWays.getNetworkDir(oNetwork.m_sNetworkId) + STATUSLOG;
		
		try
		{
			Files.deleteIfExists(Paths.get(sStatusLog));
			Files.createDirectories(oBaseDir, FileUtil.DIRPERS);
			SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
			oSdf.setTimeZone(TimeZone.getTimeZone("UTC"));
			statuslog("Started to find traffic speeds", sStatusLog);
			long lTrainingStart = findTrainingStart();
			m_oLogger.debug(String.format("Training start - %s", oSdf.format(lTrainingStart)));
			statuslog(String.format("Finished finding traffic speeds - earliest time: %s", oSdf.format(lTrainingStart)), sStatusLog);
			statuslog("Started to find hurricanes", sStatusLog);
			ArrayList<HurricaneForecastList> oHurricanesToTrain = findHurricanes(oNetwork, lTrainingStart);
			statuslog(String.format("Finished finding hurricanes - storms found: %d", oHurricanesToTrain.size()), sStatusLog);
			for (HurricaneForecastList oStorm : oHurricanesToTrain)
			{
				m_oLogger.debug(String.format("%s - %s", oStorm.m_sStormId, oSdf.format(oStorm.m_lLandfallTime)));
			}
			
			JSONObject oMetadata = new JSONObject();
			JSONObject oStats = new JSONObject();
			if (oHurricanesToTrain.isEmpty())
			{
				oNetwork.removeStatus(Network.TRAINING);
				synchronized (HURLOCK)
				{
					m_sNetworkTraining = null;
				}
				m_oLogger.info("Failed to train " + oNetwork.m_sNetworkId + " no hurricanes intersected the network when there was traffic data");
				oWays.writeHurricaneModelStatus(oNetwork, oStatus.getString("model"), WayNetworks.HURMODEL_NOTTRAINED);
				return;
			}
			String sMetadataFile = createMetadata(oMetadata, oNetwork, sBaseDir, sStatusLog, oStatus, oHurricanesToTrain);
			for (HurricaneForecastList oStorm : oHurricanesToTrain)
			{
				statuslog(String.format("Creating files for storm %s", oStorm.m_sStormId), sStatusLog);
				createSpeedFiles(oNetwork, oStorm, sBaseDir, sStatusLog);
				JSONObject oStormStats = new JSONObject();
				for (Map.Entry<String, double[]> oEntry : oStorm.m_oStats.entrySet())
				{
					JSONArray oArr = new JSONArray();
					oArr.put(oEntry.getValue()[0]);
					oArr.put(oEntry.getValue()[1]);
					oStormStats.put(oEntry.getKey(), oArr);
				}
				oStats.put(oStorm.m_sStormId, oStormStats);
			}
			oMetadata.put("stats", oStats);
			try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(Paths.get(sMetadataFile), FileUtil.WRITE, FileUtil.FILEPERS), StandardCharsets.UTF_8)))
			{
				oOut.write(oMetadata.toString(3));
			}
			statuslog("Finished creating input files for MLP", sStatusLog);
			ProcessBuilder oPB = new ProcessBuilder(m_sPythonCmd, m_sPythonScript, m_sPythonLogLevel, "trainhur", sMetadataFile, "", "", "0", "");
			oPB.redirectErrorStream(true);
			oPB.directory(new File(String.format("%smlp/", MLPHurricane.this.m_sTempPath)));
			synchronized (HURLOCK)
			{
				m_oTrainingProcess = oPB.start();
				m_sNetworkTraining = oNetwork.m_sNetworkId;
			}
			m_oLogger.info("Finished creating input files for training. Request sent to MLP");
			statuslog("Request sent to MLP", sStatusLog);
			Scheduling.getInstance().scheduleOnce(() -> checkProcess(), 60000);
		}
		catch (Exception oEx)
		{
			m_oLogger.info("Failed to train " + oNetwork.m_sNetworkId);
			statuslog("Error occured", sStatusLog);
			oWays.writeHurricaneModelStatus(oNetwork, oStatus.getString("model"), WayNetworks.HURMODEL_ERROR);
			m_oLogger.error(oEx, oEx);
			synchronized (HURLOCK)
			{
				m_sNetworkTraining = null;
				if (m_oTrainingProcess != null)
				{
					m_oTrainingProcess.destroy();
					m_oTrainingProcess = null;
				}
			}
		}
	}
	
	
	private static long findTrainingStart()
		throws IOException
	{
		ArrayList<ResourceRecord> oRRs = Directory.getResourcesByObsType(ObsType.SPDLNK);
		long lStart = Long.MAX_VALUE;
		for (ResourceRecord oRR : oRRs)
		{
			if (!MLP.isMLPContrib(oRR.getContribId()))
			{
				FilenameFormatter oArchFf = new FilenameFormatter(oRR.getArchiveFf());
				long lEarliest = findEarliestFile(oArchFf, oRR);
				if (lEarliest < lStart)
					lStart = lEarliest;
				FilenameFormatter oTileFf = new FilenameFormatter(oRR.getTiledFf());
				lEarliest = findEarliestFile(oTileFf, oRR);
				if (lEarliest < lStart)
					lStart = lEarliest;
			}
		}
		
		return lStart / 86400000 * 86400000;
	}
	
	
	private static long findEarliestFile(FilenameFormatter oFf, ResourceRecord oRR)
		throws IOException
	{
		String sBaseDir = oFf.getPattern().substring(0, oFf.getPattern().indexOf("/%r")).replace("%S", Integer.toString(oRR.getObsTypeId(), 36));
		String sFileExt = oFf.getExtension();
		Path oDir = Paths.get(sBaseDir);
		List<Path> oFiles = Files.walk(oDir, FileVisitOption.FOLLOW_LINKS).filter(Files::isRegularFile).filter(oPath -> oPath.toString().endsWith(sFileExt)).collect(Collectors.toList());
		List<Path> oProcess = new ArrayList(oFiles.size());
		long[] lTimes = new long[3];
		int nIndex = oFiles.size();
		while (nIndex-- > 0)
		{
			try
			{
				Path oFile = oFiles.get(nIndex);
				oFf.parse(oFile.toString(), lTimes);
				oProcess.add(oFile);
			}
			catch (Exception oEx) // have the wrong file name format so skip
			{
			}
		}
		if (oProcess.isEmpty())
			return Long.MAX_VALUE;
		Introsort.usort(oProcess, TileObsView.PATHREFCOMP);
		
		oFf.parse(oProcess.get(oProcess.size() - 1).toString(), lTimes);
		return Math.min(lTimes[FilenameFormatter.VALID], lTimes[FilenameFormatter.START]);
	}
	
	private ArrayList<HurricaneForecastList> findHurricanes(Network oNetwork, long lTrainingStart)
	{
		ArrayList<HurricaneForecastList> oStormsToTrain = new ArrayList();
		m_oLogger.debug("Getting US Border");
		Path oShpPath = Paths.get(NetworkGeneration.getUSBorderShp());
		Path oDbfPath = Paths.get(NetworkGeneration.getUSBorderShp().replace(".shp", ".dbf"));
		
		ArrayList<int[]> oBorders = new ArrayList();
		try (DbfResultSet oDbf = new DbfResultSet(new BufferedInputStream(Files.newInputStream(oDbfPath)));
			 ShpReader oShp = new ShpReader(new BufferedInputStream(Files.newInputStream(oShpPath))))
		{
			while (oDbf.next()) // for each record in the .dbf
			{
				oShp.readPolygon(oBorders);
			}
		}
		catch (Exception oEx)
		{
			m_oLogger.error("Failed to get US Border, cannot determine hurricane landfall");
			m_oLogger.error(oEx, oEx);
			return oStormsToTrain;
		}
		int[] nCentroid = GeoUtil.centroid(oNetwork.getGeometry());
		double[] dCentroid = new double[]{GeoUtil.fromIntDeg(nCentroid[0]), GeoUtil.fromIntDeg(nCentroid[0])};
		m_oLogger.debug("Searching for hurricanes");
		long lNow = System.currentTimeMillis();
//		long lCurTime = lTrainingStart;
//		TileFileWriter oNhc = (TileFileWriter)Directory.getInstance().lookup("NHC");
//		ArrayList<ResourceRecord> oRRs = Directory.getResourcesByContrib(Integer.valueOf("nhc", 36));
//		while (lCurTime < lNow)
//		{
//			OneTimeReentrantLock oLock = oNhc.queueRequest(oRRs, lCurTime, lCurTime, lCurTime);
//			try
//			{
//				if (oLock.tryLock(Long.MAX_VALUE, TimeUnit.SECONDS))
//					oLock.unlock();
//			}
//			catch (InterruptedException oEx)
//			{
//			}
//			lCurTime += 86400000;
//		}
		int[] nNHC = new int[]{Integer.valueOf("nhc", 36), Integer.MIN_VALUE};
		TileObsView oOv = (TileObsView)Directory.getInstance().lookup("ObsView");
		ObsList oHurData = oOv.getData(ObsType.TRSCAT, lTrainingStart, lNow, 0, 650000000, -1790000000, 0, lNow, nNHC); // get all possible cones
		ArrayList<HurricaneForecastList> oUniqueStorms = new ArrayList();
		HurricaneForecastList oHFLSearch = new HurricaneForecastList();
		HurricaneForecast oHFSearch = new HurricaneForecast();

		for (Obs oHur : oHurData)
		{
			oHFLSearch.m_sStormId = oHur.m_sStrings[0];
			int nIndex = Collections.binarySearch(oUniqueStorms, oHFLSearch);
			if (nIndex < 0)
			{
				nIndex = ~nIndex;
				oUniqueStorms.add(nIndex, new HurricaneForecastList(oHur.m_sStrings[0]));
			}
			HurricaneForecastList oStorm = oUniqueStorms.get(nIndex);
			oHFSearch.m_sForecastId = oHur.m_sStrings[1];
			oHFSearch.m_lRecvTime = oHur.m_lTimeRecv;
			nIndex = Collections.binarySearch(oStorm, oHFSearch);
			if (nIndex < 0)
			{
				nIndex = ~nIndex;
				oStorm.add(nIndex, new HurricaneForecast(oHur.m_lTimeRecv, oHur.m_sStrings[1]));
			}
			HurricaneForecast oForecast = oStorm.get(nIndex);
			oForecast.m_oCats.add(oHur);
		}

		SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		oSdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		
		for (HurricaneForecastList oStorm : oUniqueStorms)
		{
			m_oLogger.debug(oStorm.m_sStormId);
			oStorm.m_lLandfallTime = Long.MIN_VALUE;
			boolean bInNetwork = false;
			boolean bPreviousOnLand = false;
			Landfall oLf = new Landfall();
			ArrayList<Landfall> oLfs = new ArrayList();
			for (int nIndex = 0; nIndex < oStorm.size(); nIndex++)
			{
				boolean bCurrentOnLand = false;
				HurricaneForecast oForecast = oStorm.get(nIndex);
				Introsort.usort(oForecast.m_oCats, Obs.g_oCompObsByTime);
				Obs oCat = oForecast.m_oCats.get(0);
				int[] nPt = oCat.m_oGeoArray;
				if (!bInNetwork && GeoUtil.isInsideRingAndHoles(oNetwork.getGeometry(), Obs.POINT, nPt))
					bInNetwork = true;
				for (int[] nBorder : oBorders)
				{
					if (GeoUtil.isInsideRingAndHoles(nBorder, Obs.POINT, nPt))
					{
						bCurrentOnLand = true;
						if (!bPreviousOnLand)
						{
							oLf.m_lTime = oCat.m_lObsTime1;
							oLf.m_dLon = GeoUtil.fromIntDeg(oCat.m_oGeoArray[1]);
							oLf.m_dLat = GeoUtil.fromIntDeg(oCat.m_oGeoArray[2]);
							oLf.m_nZone = dCentroid[0] > oLf.m_dLon ? 0 : 1;
							oLf.m_dDistToNetwork = GeoUtil.distanceFromLatLon(dCentroid[1], dCentroid[0], oLf.m_dLat, oLf.m_dLon);
						}
						break;
					}
				}
				
				int nCat = 0;
				ObsList oWinds = oOv.getData(ObsType.GSTWND, oCat.m_lObsTime1, oCat.m_lObsTime1 + 1, oCat.m_oGeoArray[2], oCat.m_oGeoArray[2] + 1, oCat.m_oGeoArray[1], oCat.m_oGeoArray[1] + 1, oCat.m_lTimeRecv, nNHC);
				for (Obs oWind : oWinds)
				{
					if (oWind.m_sStrings[0].compareTo(oCat.m_sStrings[0]) != 0 || oWind.m_sStrings[1].compareTo(oCat.m_sStrings[1]) != 0) // skip values that are not the same storm and forecast
						continue;
					int nSSHWS = NHC.getSSHWS(oWind.m_dValue, false);
					if (nSSHWS > oStorm.m_nHurCat)
						oStorm.m_nHurCat = nSSHWS;
					nCat = nSSHWS;
				}
				if (nCat > 0 && !bPreviousOnLand && bCurrentOnLand)
				{
					oLfs.add(new Landfall(oLf));
			}
				bPreviousOnLand = bCurrentOnLand;
			}
			if (!oLfs.isEmpty() && bInNetwork && oStorm.m_nHurCat > 0) // train for storms that make landfall, intersect the network, and are hurricanes
			{
				m_oLogger.debug(String.format("%s has %d landfalls", oStorm.m_sStormId, oLfs.size()));
				Introsort.usort(oLfs, (Landfall o1, Landfall o2) -> Double.compare(o1.m_dDistToNetwork, o2.m_dDistToNetwork));
				oLf = oLfs.get(0); // get closest to network
				oStorm.m_dLandfallLat = oLf.m_dLat;
				oStorm.m_dLandfallLon = oLf.m_dLon;
				oStorm.m_lLandfallTime = oLf.m_lTime;
				oStorm.m_nLfZone = oLf.m_nZone;
				oStormsToTrain.add(oStorm);
			}
		}

		m_oLogger.debug("Done searching for hurricanes");
		return oStormsToTrain;
	}

	private String createMetadata(JSONObject oJson, Network oNetwork, String sBaseDir, String sStatusLog, JSONObject oStatus, ArrayList<HurricaneForecastList> oHurricanes)
		throws IOException
	{
		Path oPath = Paths.get(sBaseDir + "linkid_coord_imrcp.csv");
		TreeSet<String> oUniqueRoads = new TreeSet();
		Pattern oHighwayPattern = Pattern.compile("^(I|US) [0-9]{1,4}$");
		SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		oSdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oPath, FileUtil.WRITE, FileUtil.FILEPERS), StandardCharsets.UTF_8)))
		{
			int nSeqId = 1;
			oOut.append("osm_id,imrcpid,name,lon,lat\n");
			for (OsmWay oWay : oNetwork.m_oNetworkWays)
			{
				if (oWay.m_sName != null)
				{
					String[] sNames = oWay.m_sName.replace(',', ';').split(";");
					for (String sName : sNames)
					{
						String sTrimmed = sName.trim();
						if (oHighwayPattern.matcher(sTrimmed).matches())
							oUniqueRoads.add(sTrimmed);
					}
				}
				oOut.append(String.format("%d,%s,%s,%.7f,%.7f\n", nSeqId++, oWay.m_oId.toString(), oWay.m_sName != null ? oWay.m_sName.replace(',', ';') : "", GeoUtil.fromIntDeg(oWay.m_nMidLon), GeoUtil.fromIntDeg(oWay.m_nMidLat)));
			}
		}
		WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
		JSONArray oFreewayName = new JSONArray();
		oUniqueRoads.forEach(sRoad -> oFreewayName.put(sRoad));
		String sCurrentModel = oStatus.getString("model");
		String sNextModel = sCurrentModel.contains("model_a") ? "model_b/" : "model_a/";
		oJson.put("networkdir", oWayNetworks.getNetworkDir(oNetwork.m_sNetworkId) + sNextModel);
		oJson.put("statuslog", sStatusLog);
		oJson.put("basedir", sBaseDir);
		oJson.put("freeway_name", oFreewayName);
		oJson.put("file_pattern_prefix", "mlpspeeds_");
		oJson.put("linkid", oPath.toString());
		JSONArray oFolderPath = new JSONArray();
		JSONArray oLfTime = new JSONArray();
		JSONArray oCategory = new JSONArray();
		JSONArray oLfZone = new JSONArray();
		JSONArray oLfCoord = new JSONArray();
		JSONArray oHurricane = new JSONArray();
		for (HurricaneForecastList oStorm : oHurricanes)
		{
			oFolderPath.put(String.format("%sdata_14days_%s", sBaseDir, oStorm.m_sStormId));
			oLfTime.put(oSdf.format(oStorm.m_lLandfallTime));
			oCategory.put(oStorm.m_nHurCat);
			oLfZone.put(oStorm.m_nLfZone);
			JSONArray oCoords = new JSONArray();
			oCoords.put(oStorm.m_dLandfallLat);
			oCoords.put(oStorm.m_dLandfallLon);
			oLfCoord.put(oCoords);
			oHurricane.put(oStorm.m_sStormId);
		}
		oJson.put("folder_path", oFolderPath);
		oJson.put("lf_time", oLfTime);
		oJson.put("category", oCategory);
		oJson.put("lf_zone", oLfZone);
		oJson.put("lf_coord", oLfCoord);
		oJson.put("hurricane", oHurricane);
		
		return sBaseDir + "hurricane_data.json";
	}

	private void createSpeedFiles(Network oNetwork, HurricaneForecastList oStorm, String sBaseDir, String sStatusLog)
		throws IOException
	{
		ArrayList<ResourceRecord> oRRs = Directory.getResourcesByObsType(ObsType.SPDLNK);
		ArrayList<Integer> oContribSources = new ArrayList(oRRs.size() * 2);
		int nMaxZoom = Integer.MIN_VALUE;
		int nTileSize = 16;
		for (ResourceRecord oRR : oRRs)
		{
			if (!MLP.isMLPContrib(oRR.getContribId()))
			{
				oContribSources.add(oRR.getContribId());
				oContribSources.add(oRR.getSourceId());
				if (oRR.getZoom() > nMaxZoom)
				{
					nMaxZoom = oRR.getZoom();
					nTileSize = oRR.getTileSize();
				}
			}
		}
		int[] nContribSources = new int[oContribSources.size()];
		for (int nIndex = 0; nIndex < nContribSources.length; nIndex++)
			nContribSources[nIndex] = oContribSources.get(nIndex);
		
		int nPPT = (int)Math.pow(2, nTileSize) - 1;
		Mercator oM = new Mercator(nPPT);
		int nTol = (int)Math.round(oM.RES[nMaxZoom] * 100); // meters per pixel * 100 
		WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
		SimpleDateFormat oDaySdf = new SimpleDateFormat("yyyyMMdd");
		oDaySdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		oSdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		HashMap<String, Calendar> oCals = new HashMap();
		ArrayList<SpeedRecord> oSpeedRecs = new ArrayList();
		int[] nNetworkBb = oNetwork.getBoundingBox();
		int[] nTiles = new int[2];
		Comparator<int[]> oTileComp = (int[] o1, int[] o2) ->
		{
			int nRet = o1[0] - o2[0];
			if (nRet == 0)
				nRet = o1[1] - o2[1];
			
			return nRet;
		};
		TreeMap<int[], ObsList> oObsByTile = new TreeMap(oTileComp);

		for (OsmWay oWay : oNetwork.m_oNetworkWays)
		{
			SpeedRecord oRec = new SpeedRecord();
			oRec.m_sId = oWay.m_oId.toString();
			oRec.m_nDir = oWay.getDirection();
			oRec.m_nLanes = oWayNetworks.getLanes(oWay.m_oId);
			if (oRec.m_nLanes < 0)
				oRec.m_nLanes = 2;
			TimeZone oTz = oWayNetworks.getTimeZone(oWay.m_nMidLon, oWay.m_nMidLat);
			if (!oCals.containsKey(oTz.getID()))
			{
				Calendar oCal = new GregorianCalendar(oTz);
				oCals.put(oTz.getID(), oCal);
			}
			oM.lonLatToTile(GeoUtil.fromIntDeg(oWay.m_nMidLon), GeoUtil.fromIntDeg(oWay.m_nMidLat), nMaxZoom, nTiles);
			int nMidX = nTiles[0];
			int nMidY = nTiles[1];
			if (!oObsByTile.containsKey(nTiles))
				oObsByTile.put(new int[]{nTiles[0], nTiles[1]}, new ObsList());
			oRec.m_oTileObsLists.add(oObsByTile.get(nTiles));
			
			oRec.m_oCal = oCals.get(oTz.getID());
			oRec.m_nQueryGeo = GeoUtil.getBoundingPolygon(oWay.m_nMidLon - nTol, oWay.m_nMidLat - nTol, oWay.m_nMidLon + nTol, oWay.m_nMidLat + nTol);
			oM.lonLatToTile(GeoUtil.fromIntDeg(oWay.m_nMidLon - nTol), GeoUtil.fromIntDeg(oWay.m_nMidLat + nTol), nMaxZoom, nTiles);
			int nStartX = nTiles[0];
			int nStartY = nTiles[1];
			oM.lonLatToTile(GeoUtil.fromIntDeg(oWay.m_nMidLon + nTol), GeoUtil.fromIntDeg(oWay.m_nMidLat - nTol), nMaxZoom, nTiles);
			int nEndX = nTiles[0];
			int nEndY = nTiles[1];
			for (int nX = nStartX; nX <= nEndX; nX++)
			{
				nTiles[0] = nX;
				for (int nY = nStartY; nY <= nEndY; nY++)
				{
					if (nX == nMidX && nY == nMidY)
						continue;
					nTiles[1] = nY;
					if (!oObsByTile.containsKey(nTiles))
						oObsByTile.put(new int[]{nTiles[0], nTiles[1]}, new ObsList());
					oRec.m_oTileObsLists.add(oObsByTile.get(nTiles));
				}
			}
			
			oSpeedRecs.add(oRec);
		}

		TileObsView oOV = (TileObsView)Directory.getInstance().lookup("ObsView");
		Units oUnits = Units.getInstance();
		Units.UnitConv oSpdlnkConv = oUnits.getConversion(ObsType.getUnits(ObsType.SPDLNK, true), "mph");
		
		long lStartTime = (oStorm.m_lLandfallTime / 86400000) * 86400000 - (86400000 * 10); // floor to the nearest utc day and then go 10 days back
		long lCurTime = lStartTime;
		long lSevenDayCutoff = lCurTime + 86400000 * 7;
		long lEndTime = lCurTime + 86400000 * 14; // 14 day storm period
		long lRefTime = lEndTime + 86400000;
		Calendar oCal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		int nFails = 0;
		while (lCurTime < lEndTime)
		{
			oCal.setTimeInMillis(lCurTime);
			if (lCurTime % 86400000 == 0)
			{
				m_oLogger.info("Processing " + oDaySdf.format(lCurTime));
				statuslog(String.format("Processing %s for storm %s", oDaySdf.format(lCurTime), oStorm.m_sStormId), sStatusLog);
			}
			boolean bAddForStats = lCurTime < lSevenDayCutoff;
			long lQueryEnd = lCurTime + 300000; // speeds are saved in 5 minute time ranges
			for (Map.Entry<String, Calendar> oEntry : oCals.entrySet())
				oEntry.getValue().setTimeInMillis(lCurTime);

			ObsList oSpds = oOV.getData(ObsType.SPDLNK, lCurTime, lQueryEnd,  nNetworkBb[1],  nNetworkBb[3],  nNetworkBb[0],  nNetworkBb[2], lRefTime, nContribSources);
			for (ObsList oList : oObsByTile.values())
				oList.clear();
			for (Obs oObs : oSpds)
			{
				oM.lonLatToTile(GeoUtil.fromIntDeg(oObs.m_oGeoArray[1]), GeoUtil.fromIntDeg(oObs.m_oGeoArray[2]), nMaxZoom, nTiles);
				oObsByTile.get(nTiles).add(oObs);
			}

			for (SpeedRecord oRec : oSpeedRecs)
			{
				int nList = 0;
				oRec.m_dSpeed = Double.NaN;
				while (Double.isNaN(oRec.m_dSpeed) && nList < oRec.m_oTileObsLists.size())
				{
					oRec.m_dSpeed = MLP.getValue(oRec.m_oTileObsLists.get(nList++), oRec.m_nQueryGeo);
				}

				oRec.m_dSpeed = oSpdlnkConv.convert(oRec.m_dSpeed);
				if (bAddForStats)
					oRec.m_dSpeedsForStats = Arrays.add(oRec.m_dSpeedsForStats, oRec.m_dSpeed);
				oRec.m_dAllSpeeds = Arrays.add(oRec.m_dAllSpeeds, oRec.m_dSpeed);
				if (!Double.isNaN(oRec.m_dSpeed))
					++oRec.m_nCount;
			}

			lCurTime = lQueryEnd; 
		}

		oStorm.m_oStats = new HashMap();
		double[] dMean = new double[1];
		int nRecIndex = oSpeedRecs.size();


		while (nRecIndex-- > 0)
		{
			SpeedRecord oRec = oSpeedRecs.get(nRecIndex);
			if (oRec.m_nCount == 0)
			{
				oSpeedRecs.remove(nRecIndex);
				continue;
			}


			if (MathUtil.interpolate(oRec.m_dSpeedsForStats, 72) && MathUtil.interpolate(oRec.m_dAllSpeeds, 72))
			{
				dMean[0] = Double.NaN;
				double dStd = MathUtil.standardDeviation(oRec.m_dSpeedsForStats, dMean);
				if (Double.isNaN(dStd) || Double.isNaN(dMean[0]))
				{
					oSpeedRecs.remove(nRecIndex);
					nFails++;
					continue;
				}
				oStorm.m_oStats.put(oRec.m_sId + oStorm.m_sStormId, new double[]{dMean[0], dStd});
			}
			else // too much missing values so flag to not write data
			{
				oSpeedRecs.remove(nRecIndex);
				++nFails;
			}
		}
		
		m_oLogger.info("Fails = " + nFails);
		lCurTime = lStartTime;
		int nSpeedIndex = 1;
		

		while (lCurTime < lEndTime)
		{
			long lNextDay = lCurTime + 86400000;
			Path oFile = Paths.get(String.format("%sdata_14days_%s/mlpspeeds_%s.csv", sBaseDir, oStorm.m_sStormId, oDaySdf.format(lCurTime)));
			Files.createDirectories(oFile.getParent(), FileUtil.DIRPERS);
			m_oLogger.debug("Opening " + oFile.toString());
			try (Writer oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oFile, FileUtil.WRITE, FileUtil.FILEPERS), StandardCharsets.UTF_8)))
			{
				oOut.append("Timestamp,Id,Direction,DayOfWeek,Lanes,Speed\n");
				while (lCurTime < lNextDay)
				{
					oCal.setTimeInMillis(lCurTime);
					int nDoW = MLP.getDayOfWeek(oCal);
					for (SpeedRecord oRec : oSpeedRecs)
					{
						oRec.m_dSpeed = oRec.m_dAllSpeeds[nSpeedIndex];
						oRec.m_lTimestamp = lCurTime;
						oRec.m_nDayOfWeek = nDoW;
						oRec.write(oOut, oSdf);
					}
					lCurTime += 300000;
					++nSpeedIndex;
				}
			}
		}
	}
	
	
	private void statuslog(String sMsg, String sFilename)
	{
		try
		{
			Path oPath = Paths.get(sFilename);
			SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
			oSdf.setTimeZone(TimeZone.getTimeZone("UTC"));
			try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oPath, FileUtil.APPENDTO, FileUtil.FILEPERS), StandardCharsets.UTF_8)))
			{
				oOut.append(oSdf.format(System.currentTimeMillis())).append(" - ").append(sMsg).append('\n');
			}
		}
		catch (IOException oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
	
	private class MLPHurricaneTile implements Comparable<MLPHurricaneTile>, Callable<MLPHurricaneTile>
	{
		private int m_nX;
		private int m_nY;
		private Mercator m_oM;
		private ResourceRecord m_oRR;
		private ArrayList<double[]> m_oTileInfos = new ArrayList();
		byte[] m_yTileData;
		MLPHurricaneTile(int nX, int nY)
		{
			m_nX = nX;
			m_nY = nY;
		}
		
		
		
		@Override
		public int compareTo(MLPHurricaneTile o)
		{
			int nRet = m_nX - o.m_nX;
			if (nRet == 0)
				nRet = m_nY - o.m_nY;
			
			return nRet;
		}

		@Override
		public MLPHurricaneTile call() throws Exception
		{
			double[] dPixels = new double[2];
			ByteArrayOutputStream oRawStream = new ByteArrayOutputStream(8192);
			DataOutputStream oRawDataStream = new DataOutputStream(oRawStream);

			int nZoom = m_oRR.getZoom();
			for (double[] oInfo : m_oTileInfos)
			{
				m_oM.lonLatToTilePixels(oInfo[0], oInfo[1], m_nX, m_nY, nZoom, dPixels);
				oRawDataStream.writeShort((int)dPixels[0]);
				oRawDataStream.writeShort((int)dPixels[1]);
				TileFileWriter.ValueWriter oVW = TileFileWriter.newValueWriter(m_oRR.getValueType());
				for (int nTimeIndex = 2; nTimeIndex < oInfo.length; nTimeIndex++)
				{
					oVW.writeValue(oRawDataStream, oInfo[nTimeIndex]);
				}
			}

			m_yTileData = XzBuffer.compress(oRawStream.toByteArray());
			
			return this;
		}
		
	}
	
	private class MLPHurricaneSession implements Callable<MLPHurricaneSession>
	{
		private ArrayList<OsmWay> m_oSegments;
		private Network m_oNetwork;
		private long m_lRuntime;
		private Obs m_oCone;
		private ObsList m_oCats;
		private ObsList m_oGstWnds;
		private double[] m_dLandfall;
		private long m_lLandfall;
		private String m_sDirectory;
		private String m_sFunction = "hur";
		private String m_sNetworkDir;
		private int m_nExit = 1;
		
		
		MLPHurricaneSession(MLPHurricaneSession oSess)
		{
			m_sFunction = "hos";
			m_oSegments = oSess.m_oSegments;
			m_oNetwork = oSess.m_oNetwork;
			m_lRuntime = oSess.m_lRuntime;
			m_sNetworkDir = oSess.m_sNetworkDir;
			m_oCone = oSess.m_oCone;
			m_oCats = oSess.m_oCats;
			m_oGstWnds = oSess.m_oGstWnds;
			m_dLandfall = oSess.m_dLandfall;
			m_lLandfall = oSess.m_lLandfall;
			m_sDirectory = oSess.m_sDirectory;
		}
		
		MLPHurricaneSession(Network oNetwork, long lRuntime, String sNetworkDir, ArrayList<OsmWay> oSegments, Obs oCone, ObsList oCats, ObsList oGstWnds, double[] dLandfall, long lLandfall)
		{
			m_oSegments = oSegments;
			m_oNetwork = oNetwork;
			m_lRuntime = lRuntime;
			m_sNetworkDir = sNetworkDir;
			m_oCone = oCone;
			m_oCats = oCats;
			m_oGstWnds = oGstWnds;
			m_dLandfall = dLandfall;
			m_lLandfall = lLandfall;
		}

		@Override
		public MLPHurricaneSession call() throws Exception
		{
			if (!createInputFiles())
				return this;
			SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			oSdf.setTimeZone(Directory.m_oUTC);
			ProcessBuilder oPB = new ProcessBuilder(m_sPythonCmd, m_sPythonScript, m_sPythonLogLevel, m_sFunction, m_sDirectory, oSdf.format(m_lRuntime - 300000), m_sNetworkDir, "0", "");
			oPB.redirectErrorStream(true);
			oPB.directory(new File(String.format("%smlp/", MLPHurricane.this.m_sTempPath)));
			Process oProc = oPB.start();
			try (BufferedReader oStdInput = new BufferedReader(new InputStreamReader(oProc.getInputStream())))
			{
				String sOutput;
				while ((sOutput = oStdInput.readLine()) != null)
				{
					m_oLogger.info(sOutput);
				}
			}
			oProc.waitFor(m_nPeriod, TimeUnit.SECONDS);
			oProc.destroy();
			try
			{
				m_nExit = oProc.exitValue();
			}
			catch (IllegalThreadStateException oEx)
			{
				m_nExit = 1;
				oProc.destroyForcibly();
			}
			return this;
		}
		
		private boolean createInputFiles()
		{
			try
			{
				WayNetworks oWayNetworks = g_oWayNetworks;
				TileObsView oOv = g_oOv;
				ArrayList<LinkSpeedPastRecord> oPastRecords = new ArrayList();
				long lEarliest = m_lLandfall - 864000000; // 10 days before landfall. we need 7 days past speed data from the first "impact" day which is 3 days prior to landfall 
				long lStartQuery = lEarliest;
				long lEndQuery = lEarliest + 604800000 + 3600000; // need 7 days of speeds for the oneshot input. Add 1 hour because the interpolate function needs the endpoint to get the last values
				
				long lStartOfPast = lStartQuery;
				long lEndOfPast = lEndQuery;
				long lStartOfNext = m_lRuntime - 86700000; // need the past 24 hours. Go an extra 5 minutes because the current speed isn't available right when the function is called
				long lEndOfNext = m_lRuntime;
				
				if (lEndOfNext > lEndQuery)
					lEndQuery = lEndOfNext;
				
				String sSessionDir = String.format(m_sDirFormatString, m_oNetwork.m_sNetworkId, Long.toString(m_lRuntime));
				m_sDirectory = sSessionDir;
				Files.createDirectories(Paths.get(m_sDirectory), FileUtil.DIRPERS);
				Path oLinkSpeedPastPath = Paths.get(sSessionDir + m_sLinkSpeedPast);
				Path oOneshotPath = Paths.get(sSessionDir + m_sOneshot);
				Path oOneshotMinMaxPath = Paths.get(sSessionDir + m_sOneshotMinMax);
				Path oLinkSpeedNextPath = Paths.get(sSessionDir + m_sLinkSpeedNext);
				Path oLinkSpeedNextMinMaxPath = Paths.get(sSessionDir + m_sLinkSpeedNextMinMax);
				if (Files.exists(oLinkSpeedPastPath) && Files.exists(oOneshotPath) && Files.exists(oOneshotMinMaxPath)
					&& Files.exists(oLinkSpeedNextPath) && Files.exists(oLinkSpeedNextMinMaxPath))
					return true;
				SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				oSdf.setTimeZone(Directory.m_oUTC);
				m_oLogger.debug(String.format("Start query: %s", oSdf.format(lStartQuery)));
				m_oLogger.debug(String.format("End query: %s", oSdf.format(lEndQuery)));
				int[] nGeo = m_oNetwork.getGeometry();
				ArrayList<ResourceRecord> oRRs = Directory.getResourcesByObsType(ObsType.SPDLNK);
				ArrayList<Integer> oContribSources = new ArrayList(oRRs.size() * 2);
				int nOneshot = Integer.valueOf("MLPOS", 36);
				for (ResourceRecord oRR : oRRs)
				{
					if (oRR.getContribId() == nOneshot || !isMLPContrib(oRR.getContribId()))
					{
						oContribSources.add(oRR.getContribId());
						oContribSources.add(oRR.getSourceId());
					}
				}
				int[] nContribSources = new int[oContribSources.size()];
				for (int nIndex = 0; nIndex < nContribSources.length; nIndex++)
					nContribSources[nIndex] = oContribSources.get(nIndex);


				Comparator<int[]> oComp = (int[] o1, int[] o2) ->
				{
					int nRet = Integer.compare(o1[1], o2[1]);
					if (nRet == 0)
						nRet = Integer.compare(o1[2], o2[2]);

					return nRet;
				};
				TreeMap<int[], ObsList> oObsByLoc = new TreeMap(oComp);
				m_oLogger.debug("Getting speeds");
				while (lStartQuery < lEndQuery)
				{
					ObsList oSpds = oOv.getData(ObsType.SPDLNK, lStartQuery, lStartQuery + 300000, nGeo[4], nGeo[6], nGeo[5], nGeo[7], m_lRuntime, nContribSources);
					lStartQuery += 300000;
					for (Obs oObs : oSpds)
					{
						if (oObs.m_yGeoType != Obs.POINT)
							continue;
						
						oObs.m_lObsTime1 = lStartQuery;
						if (!oObsByLoc.containsKey(oObs.m_oGeoArray))
							oObsByLoc.put(oObs.m_oGeoArray, new ObsList());

						oObsByLoc.get(oObs.m_oGeoArray).add(oObs);
					}
				}
				m_oLogger.debug("Got speeds");
				Units oUnits = Units.getInstance();
				Units.UnitConv oConv = oUnits.getConversion(ObsType.getUnits(ObsType.SPDLNK, true), "mph");
				int nHurCat = 0;
				for (Obs oGstWnd : m_oGstWnds)
				{
					int nCat = NHC.getSSHWS(oGstWnd.m_dValue, false);
					if (nCat > nHurCat)
						nHurCat = nCat;
				}
				if (nHurCat > 4)
					nHurCat = 4;
				int nLandfallLon = GeoUtil.toIntDeg(m_dLandfall[0]);
				m_oLogger.debug("Associating speeds to ways");
				for (Map.Entry<int[], ObsList> oEntry : oObsByLoc.entrySet())
				{
					int nLon = oEntry.getKey()[1];
					int nLat = oEntry.getKey()[2];
					OsmWay oWay = oWayNetworks.getWay(100, nLon, nLat);
					if (oWay == null || Collections.binarySearch(m_oSegments, oWay, OsmWay.WAYBYTEID)< 0) // skip locations that do no have an associated way or that are not in the network
						continue;

					for (Obs oObs : oEntry.getValue())
					{
						ResourceRecord oTemp = Directory.getResource(oObs.m_nContribId, oObs.m_nObsTypeId);
						oPastRecords.add(new LinkSpeedPastRecord(oWay.m_oId, oObs.m_lObsTime1 / 300000 * 300000, oConv.convert(oObs.m_dValue), nHurCat, nLon > nLandfallLon ? 0 : 1, oTemp.getPreference()));
					}
				}
				m_oLogger.debug("PastRecords: " + oPastRecords.size());


				if (oPastRecords.isEmpty())
					return false;

				Introsort.usort(oPastRecords);
				ArrayList<LinkSpeedPastRecord> oTempPast = new ArrayList(oPastRecords.size());
				LinkSpeedPastRecord oCur = oPastRecords.get(0);
				for (int nPastIndex = 1; nPastIndex < oPastRecords.size(); nPastIndex++)
				{
					LinkSpeedPastRecord oCmp = oPastRecords.get(nPastIndex);
					if (oCur.m_lTimestamp == oCmp.m_lTimestamp && Id.COMPARATOR.compare(oCur.m_oId, oCmp.m_oId) == 0)
					{
						if (oCmp.m_yPref <= oCur.m_yPref)
							oCur = oCmp;
					}
					else
					{
						oTempPast.add(oCur);
						oCur = oCmp;
					}
				}
				oTempPast.add(oCur);
				oPastRecords = oTempPast;
				double[] dSpeeds = Arrays.newDoubleArray(2016);
				double[] dMean = new double[1];
				ArrayList<OneshotRecord> oOneshotRecords = new ArrayList();
				ArrayList<LinkSpeedNextRecord> oLinkSpeedNextRecords = new ArrayList();
				ArrayList<LinkSpeedPastRecord> oAllSpeeds = new ArrayList();
				GregorianCalendar oCal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
				LinkSpeedPastRecord oPrev = new LinkSpeedPastRecord();
				oPrev.m_oId = Id.NULLID;
				dSpeeds = Arrays.add(dSpeeds, oPrev.m_dSpeed);
				int nLimit = oPastRecords.size();
				int nLast = nLimit - 1;
				int nFirstId = 0;
				long lExpectedTime = lEarliest;
				m_oLogger.debug("Processing records");
				for (int nIndex = 0; nIndex < nLimit; nIndex++)
				{
					oCur = oPastRecords.get(nIndex);
					if (oCur.m_oId.compareTo(oPrev.m_oId) != 0 || nIndex == nLast) // different id or last element in list
					{
						if (nIndex > 0) // dont do this the first time through the loop
						{
							OsmWay oWay = oWayNetworks.getWayById(oPrev.m_oId);
							dMean[0] = Double.NaN; // reset mean array
							dSpeeds = Arrays.add(dSpeeds, oPrev.m_dSpeed);
							double dStd = MathUtil.standardDeviation(dSpeeds, dMean);
							int nLanes = oWayNetworks.getLanes(oWay.m_oId);
							if (nLanes > 6)
								nLanes = 6;
							if (nLanes < 0)
								nLanes = 2;
							int nDirection = oWay.getDirection();
							double dDistanceToLandfall = GeoUtil.distanceFromLatLon(m_dLandfall[1], m_dLandfall[0], GeoUtil.fromIntDeg(oWay.m_nMidLat), GeoUtil.fromIntDeg(oWay.m_nMidLon));
							oOneshotRecords.add(new OneshotRecord(oWay, dSpeeds, dMean[0], dStd, oPrev.m_nHurricaneCategory, oPrev.m_nLandfallLocation, nDirection, nLanes, dDistanceToLandfall));
							dSpeeds[0] = 1; // reset speed array
							while (lExpectedTime < m_lRuntime)
							{
								if (lExpectedTime == oPrev.m_lTimestamp)
									oAllSpeeds.add(oPrev);
								else
									oAllSpeeds.add(new LinkSpeedPastRecord(oPrev.m_oId, lExpectedTime, Double.NaN, oPrev.m_nHurricaneCategory, oPrev.m_nLandfallLocation));
								lExpectedTime += 300000;
							}
							interpolate(oAllSpeeds, nFirstId);
							for (int nSpeedIndex = nFirstId; nSpeedIndex < oAllSpeeds.size(); nSpeedIndex++)
							{
								LinkSpeedPastRecord oRec = oAllSpeeds.get(nSpeedIndex);
								oLinkSpeedNextRecords.add(new LinkSpeedNextRecord(oRec.m_lTimestamp, oCal, m_lLandfall, oWay, nDirection, nLanes, dDistanceToLandfall, oRec.m_dSpeed, dMean[0], dStd, oPrev.m_nHurricaneCategory, oPrev.m_nLandfallLocation));
							}
							lExpectedTime = lEarliest;
							nFirstId = oAllSpeeds.size();
							if (nIndex != nLast)
							{
								while (oCur.m_lTimestamp != lExpectedTime && lExpectedTime < m_lRuntime)
								{
									oAllSpeeds.add(new LinkSpeedPastRecord(oCur.m_oId, lExpectedTime, Double.NaN, oPrev.m_nHurricaneCategory, oPrev.m_nLandfallLocation));
									lExpectedTime += 300000;
								}
							}
						}
					}
					else
					{
						while (oPrev.m_lTimestamp > lExpectedTime)
						{
							oAllSpeeds.add(new LinkSpeedPastRecord(oCur.m_oId, lExpectedTime, Double.NaN, oPrev.m_nHurricaneCategory, oPrev.m_nLandfallLocation));
							lExpectedTime += 300000;
						}

						if (oCur.m_lTimestamp == oPrev.m_lTimestamp)
						{
							oCur.m_dSpeed = (oPrev.m_dSpeed + oCur.m_dSpeed) / 2;
						}
						else
						{
							dSpeeds = Arrays.add(dSpeeds, oPrev.m_dSpeed);
							oAllSpeeds.add(oPrev);
							lExpectedTime += 300000;
						}
					}
					oPrev = oCur;
				}
				m_oLogger.debug("Processed records");
				m_oLogger.debug("Writing files");
				try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oLinkSpeedPastPath, FileUtil.WRITE, FileUtil.FILEPERS), StandardCharsets.UTF_8)))
				{
					oOut.append(LinkSpeedPastRecord.HEADER); // write header
					for (LinkSpeedPastRecord oRec : oAllSpeeds)
					{
						if (oRec.m_lTimestamp >= lStartOfPast && oRec.m_lTimestamp < lEndOfPast)
							oRec.write(oOut, oSdf);
					}
				}


				OneshotRecord oMins = new OneshotRecord(true);
				OneshotRecord oMaxs = new OneshotRecord(false);
				try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oOneshotPath, FileUtil.WRITE, FileUtil.FILEPERS), StandardCharsets.UTF_8)))
				{
					oOut.append(OneshotRecord.HEADER);
					for (OneshotRecord oRec : oOneshotRecords)
					{
						if (oRec.m_dDistanceToLandfall < oMins.m_dDistanceToLandfall)
							oMins.m_dDistanceToLandfall = oRec.m_dDistanceToLandfall;
						if (oRec.m_dDistanceToLandfall > oMaxs.m_dDistanceToLandfall)
							oMaxs.m_dDistanceToLandfall = oRec.m_dDistanceToLandfall;
						if (oRec.m_dSpeedMean < oMins.m_dSpeedMean)
							oMins.m_dSpeedMean = oRec.m_dSpeedMean;
						if (oRec.m_dSpeedMean > oMaxs.m_dSpeedMean)
							oMaxs.m_dSpeedMean = oRec.m_dSpeedMean;
						if (oRec.m_dSpeedStd < oMins.m_dSpeedStd)
							oMins.m_dSpeedStd = oRec.m_dSpeedStd;
						if (oRec.m_dSpeedStd > oMaxs.m_dSpeedStd)
							oMaxs.m_dSpeedStd = oRec.m_dSpeedStd;
						if (oRec.m_oWay.m_nMidLon < oMins.m_oWay.m_nMidLon)
							oMins.m_oWay.m_nMidLon = oRec.m_oWay.m_nMidLon;
						if (oRec.m_oWay.m_nMidLat < oMins.m_oWay.m_nMidLat)
							oMins.m_oWay.m_nMidLat = oRec.m_oWay.m_nMidLat;
						if (oRec.m_oWay.m_nMidLon > oMaxs.m_oWay.m_nMidLon)
							oMaxs.m_oWay.m_nMidLon = oRec.m_oWay.m_nMidLon;
						if (oRec.m_oWay.m_nMidLat > oMaxs.m_oWay.m_nMidLat)
							oMaxs.m_oWay.m_nMidLat = oRec.m_oWay.m_nMidLat;
						
						int nCongestedIndex = 0;
						for (int nIndex = -14; nIndex < 14; nIndex++)
						{
							oRec.m_nCongestedToWrite = oRec.m_nCongested[nCongestedIndex++];
							oRec.m_nTimeToLandfall = nIndex;
							oCal.setTimeInMillis(m_lLandfall + nIndex * 21600000);
							oRec.m_nTimeOfDay = oCal.get(Calendar.HOUR_OF_DAY) / 6 + 1;
							oRec.write(oOut);
						}
					}
				}

				try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oOneshotMinMaxPath, FileUtil.WRITE, FileUtil.FILEPERS), StandardCharsets.UTF_8)))
				{
					oOut.append(OneshotRecord.HEADER);
					oMins.m_nLanes = 1;
					oMaxs.m_nLanes = 2;
					oMins.m_nHurricaneCategory = 1;
					oMaxs.m_nHurricaneCategory = 2;
					oMins.write(oOut);
					oMaxs.write(oOut);
					oMins.m_nLanes = 3;
					oMaxs.m_nLanes = 4;
					oMins.m_nTimeOfDay = 3;
					oMaxs.m_nDirection = 3;
					oMaxs.m_nTimeOfDay = 4;
					oMins.m_nDirection = 4;
					oMins.m_nHurricaneCategory = 3;
					oMaxs.m_nHurricaneCategory = 4;
					oMins.write(oOut);
					oMaxs.write(oOut);
					oMins.m_nLanes = 5;
					oMaxs.m_nLanes = 6;
					oMins.write(oOut);
					oMaxs.write(oOut);
				}

				LinkSpeedNextRecord oMin = new LinkSpeedNextRecord(true);
				LinkSpeedNextRecord oMax = new LinkSpeedNextRecord(false);
				try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oLinkSpeedNextPath, FileUtil.WRITE, FileUtil.FILEPERS), StandardCharsets.UTF_8)))
				{
					oOut.append(LinkSpeedNextRecord.HEADER);
					int nMinLon = Integer.MAX_VALUE;
					int nMaxLon = Integer.MIN_VALUE;
					int nMinLat = Integer.MAX_VALUE;
					int nMaxLat = Integer.MIN_VALUE;

					for (LinkSpeedNextRecord oRec : oLinkSpeedNextRecords)
					{
						if (oRec.m_lTime < lStartOfNext || oRec.m_lTime > lEndOfNext)
							continue;
						oRec.write(oOut, oSdf);
						if (oRec.m_dDistanceToLandfall < oMin.m_dDistanceToLandfall)
							oMin.m_dDistanceToLandfall = oRec.m_dDistanceToLandfall;
						if (oRec.m_dDistanceToLandfall > oMax.m_dDistanceToLandfall)
							oMax.m_dDistanceToLandfall = oRec.m_dDistanceToLandfall;
						if (oRec.m_dSpeedMean < oMin.m_dSpeedMean)
							oMin.m_dSpeedMean = oRec.m_dSpeedMean;
						if (oRec.m_dSpeedMean > oMax.m_dSpeedMean)
							oMax.m_dSpeedMean = oRec.m_dSpeedMean;
						if (oRec.m_dSpeedStd < oMin.m_dSpeedStd)
							oMin.m_dSpeedStd = oRec.m_dSpeedStd;
						if (oRec.m_dSpeedStd > oMax.m_dSpeedStd)
							oMax.m_dSpeedStd = oRec.m_dSpeedStd;
		//				if (oRec.m_dSpeed < oMin.m_dSpeed)
		//					oMin.m_dSpeed = oRec.m_dSpeed;
		//				if (oRec.m_dSpeed > oMax.m_dSpeed)
		//					oMax.m_dSpeed = oRec.m_dSpeed;
						if (oRec.m_nTimeToLandfall < oMin.m_nTimeToLandfall)
							oMin.m_nTimeToLandfall = oRec.m_nTimeToLandfall;
						if (oRec.m_nTimeToLandfall > oMax.m_nTimeToLandfall)
							oMax.m_nTimeToLandfall = oRec.m_nTimeToLandfall;
						if (oRec.m_oWay.m_nMidLon < nMinLon)
							nMinLon = oRec.m_oWay.m_nMidLon;
						if (oRec.m_oWay.m_nMidLat < nMinLat)
							nMinLat = oRec.m_oWay.m_nMidLat;
						if (oRec.m_oWay.m_nMidLon > nMaxLon)
							nMaxLon = oRec.m_oWay.m_nMidLon;
						if (oRec.m_oWay.m_nMidLat > nMaxLat)
							nMaxLat = oRec.m_oWay.m_nMidLat;
						if (oRec.m_lTime < oMin.m_lTime)
							oMin.m_lTime = oRec.m_lTime;
						if (oRec.m_lTime > oMax.m_lTime)
							oMax.m_lTime = oRec.m_lTime;
					}
					oMin.m_oWay.m_nMidLon = nMinLon;
					oMin.m_oWay.m_nMidLat = nMinLat;
					oMax.m_oWay.m_nMidLon = nMaxLon;
					oMax.m_oWay.m_nMidLat = nMaxLat;
				}

				try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oLinkSpeedNextMinMaxPath, FileUtil.WRITE, FileUtil.FILEPERS), StandardCharsets.UTF_8)))
				{
					oOut.append(LinkSpeedNextRecord.HEADER);
					oMin.m_nLanes = 1;
					oMax.m_nLanes = 2;
					oMin.m_dSpeed = 0;
					oMax.m_dSpeed = 90;
					oMin.write(oOut, oSdf);
					oMax.write(oOut, oSdf);
					oMin.m_nDirection = 3;
					oMax.m_nDirection = 4;
					oMin.m_nHurricaneCategory = 3;
					oMax.m_nHurricaneCategory = 4;
					oMin.m_nLanes = 3;
					oMax.m_nLanes = 4;

					oMin.write(oOut, oSdf);
					oMax.write(oOut, oSdf);
					oMin.m_nLanes = 5;
					oMax.m_nLanes = 6;

					oMin.write(oOut, oSdf);
					oMax.write(oOut, oSdf);
				}
				m_oLogger.debug("Wrote files");
				return true;
			}
			catch (Exception oEx)
			{
				m_oLogger.error(oEx, oEx);
				return false;
			}
		}
	}
	
	private static class SpeedRecord
	{
		Calendar m_oCal;
		long m_lTimestamp;
		String m_sId;
		int m_nDir;
		int m_nDayOfWeek;
		int m_nLanes;
		double m_dSpeed;
		int[] m_nQueryGeo;
		double[] m_dSpeedsForStats = Arrays.newDoubleArray(2016);
		double[] m_dAllSpeeds = Arrays.newDoubleArray(4032);
		int m_nCount = 0;
		ArrayList<ObsList> m_oTileObsLists = new ArrayList();
		
		
		private void write(Writer oOut, SimpleDateFormat oSdf)
			throws IOException
		{
			oOut.append(String.format("%s,%s,%d,%d,%d,%.2f\n", oSdf.format(m_lTimestamp), m_sId, m_nDir, m_nDayOfWeek, m_nLanes, m_dSpeed));
		}
	}
	
	
	private static class Landfall
	{
		long m_lTime;
		int m_nHurCat;
		double m_dLon;
		double m_dLat;
		int m_nZone;
		double m_dDistToNetwork;
		
		Landfall()
		{
		}
		
		
		Landfall(Landfall oLf)
		{
			m_lTime = oLf.m_lTime;
			m_nHurCat = oLf.m_nHurCat;
			m_dLon = oLf.m_dLon;
			m_dLat = oLf.m_dLat;
			m_nZone = oLf.m_nZone;
			m_dDistToNetwork = oLf.m_dDistToNetwork;
		}
		
		
		Landfall(long lTime, int nHurCat, double dLon, double dLat, int nZone, double dDistToNetwork)
		{
			m_lTime = lTime;
			m_nHurCat = nHurCat;
			m_dLon = dLon;
			m_dLat = dLat;
			m_nZone = nZone;
			m_dDistToNetwork = dDistToNetwork;
		}
	}
	
	
	private static class HurricaneForecastList extends ArrayList<HurricaneForecast> implements Comparable<HurricaneForecastList>
	{
		String m_sStormId;
		long m_lLandfallTime;
		int m_nHurCat = 0;
		int m_nLfZone;
		double m_dLandfallLon;
		double m_dLandfallLat;
		HashMap<String, double[]> m_oStats;

		HurricaneForecastList()
		{
			
		}
		
		HurricaneForecastList(String sStormId)
		{
			m_sStormId = sStormId;
		}
		
		@Override
		public int compareTo(HurricaneForecastList o)
		{
			return m_sStormId.compareTo(o.m_sStormId);
		}
	}
	
	
	private static class HurricaneForecast implements Comparable<HurricaneForecast>
	{
		String m_sForecastId;
		long m_lRecvTime;
		boolean m_bLandfall;
		ObsList m_oCones;
		ObsList m_oCats = new ObsList();
		ObsList m_oGusts;
		ObsList m_oPressures;

		HurricaneForecast()
		{
			
		}
		
		
		HurricaneForecast(long lRecvTime, String sForecastId)
		{
			m_lRecvTime = lRecvTime;
			m_sForecastId = sForecastId;
		}
		@Override
		public int compareTo(HurricaneForecast o)
		{
			int nRet = Long.compare(m_lRecvTime, o.m_lRecvTime);;
			if (nRet == 0)
				nRet = m_sForecastId.compareTo(o.m_sForecastId);
			return nRet;
		}
	}
}
