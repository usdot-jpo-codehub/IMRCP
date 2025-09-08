/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.forecast.mlp;


import imrcp.collect.Events;
import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.Mercator;
import imrcp.geosrv.Network;
import imrcp.geosrv.WayNetworks;
import imrcp.geosrv.WaySnapInfo;
import imrcp.geosrv.osm.OsmNode;
import imrcp.geosrv.osm.OsmWay;
import imrcp.store.Obs;
import imrcp.store.ObsList;
import imrcp.store.TileObsView;
import imrcp.system.CsvReader;
import imrcp.system.Directory;
import imrcp.system.FileUtil;
import imrcp.system.FilenameFormatter;
import imrcp.system.Id;
import imrcp.system.Introsort;
import imrcp.system.ObsType;
import imrcp.system.ResourceRecord;
import imrcp.system.Scheduling;
import imrcp.collect.TileFileInfo;
import imrcp.collect.TileFileWriter;
import imrcp.system.Units;
import imrcp.system.Util;
import imrcp.system.XzBuffer;
import imrcp.web.Scenario;
import imrcp.web.SegmentGroup;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.json.JSONObject;

/**
 *
 * @author Federal Highway Administration
 */
public class MLP extends TileFileWriter
{
	private String m_sRTInputFf = "mlp/%s_%s/%d_%d/mlp_input.csv";
	private String m_sLtsInputFf = "%d_%d/mlp_lts_input.csv";
	private String m_sOneshotFf = "%d_%d/mlp_oneshot_input.csv";
	private String m_sExtendedLongTsFf = "mlp/lts/%d_%d/mlp_lts_output.csv";
	protected String m_sPythonScript;
	protected String m_sPythonCmd;
	protected String m_sPythonLogLevel;
	protected int m_nPythonProcesses;
	protected int m_nInterpolateLimit;
	protected double m_dExtendDistTol;
	
	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_dExtendDistTol = oBlockConfig.optDouble("disttol", 480000);
		m_nInterpolateLimit = oBlockConfig.optInt("interpolate", 72);
		m_sPythonCmd = oBlockConfig.optString("python", "python3");
		m_sPythonScript = oBlockConfig.getString("script");
		m_nPythonProcesses = oBlockConfig.optInt("processes", 4);
		if (m_nPythonProcesses < 1)
			m_nPythonProcesses = 1;
		m_sPythonLogLevel = oBlockConfig.optString("loglevel", "info");
	}
	
	
	@Override
	public boolean start()
		throws Exception
	{
		m_nSchedId = Scheduling.getInstance().createSched(this, m_nOffset, m_nPeriod);
		return true;
	}
	
	
	@Override
	public void execute()
	{
		try
		{
			int nPeriodMillis = m_nPeriod * 1000;
			long lNow = System.currentTimeMillis() / nPeriodMillis * nPeriodMillis;
			WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
			ArrayList<MLPSession> oSessions = getSessions(lNow, "rt", oWayNetworks, null);

			if (oSessions.isEmpty())
				return;
			for (MLPSession oSess : oSessions)
				oSess.m_lRefTime = oSess.m_lRuntime;
			m_oLogger.info(String.format("Processing %d tiles", oSessions.size()));
			Scheduling.processCallables(oSessions, m_nPythonProcesses);			
			createFiles(oSessions);
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
	
	
	protected ArrayList<MLPSession> getSessions(long lRuntime, String sFunction, WayNetworks oWayNetworks, Scenario oScenario)
	{
		ArrayList<MLPSession> oSessions = new ArrayList();
		int nContribId = Integer.valueOf("MLP" + "rt", 36); // long ts and oneshot for scenarios do not write tiles files so use the real time resource record since the others don't exist
		if (sFunction.compareTo("exos") == 0) // extended oneshot, use its resource record
			nContribId = Integer.valueOf("MLPOS", 36);
		ResourceRecord oRR = Directory.getResource(nContribId, ObsType.SPDLNK);
		int nPPT = (int)Math.pow(2, oRR.getTileSize()) - 1;
		Mercator oM = new Mercator(nPPT);
		int nTol = (int)Math.round(oM.RES[oRR.getZoom()] * 100); // meters per pixel * 100 
		int[] nTile = new int[2];
		MLPSession oSearch = new MLPSession();
		ArrayList<Network> oNetworks;
		if (oScenario == null)
			oNetworks = oWayNetworks.getNetworks();
		else
		{
			oNetworks = new ArrayList(1);
			oNetworks.add(oWayNetworks.getNetwork(oScenario.m_sNetwork));
		}
		for (Network oNetwork : oNetworks)
		{
			if (!oNetwork.m_bCanRunTraffic)
				continue;
			String sNetworkDir = String.format(oWayNetworks.getDataPath() + WayNetworks.NETWORKFF, oNetwork.m_sNetworkId, "");
			sNetworkDir = sNetworkDir.substring(0, sNetworkDir.lastIndexOf("/") + 1); // include the slash
			sNetworkDir = getModelDir(sNetworkDir, "", false);
			
			if (!Files.exists(Paths.get(sNetworkDir + "decision_tree.pickle")) || !Files.exists(Paths.get(sNetworkDir + "mlp_python_data.pkl"))) // model files do not exist so don't run the traffic model
				continue;
			int[] nBb = oNetwork.getBoundingBox();
			ArrayList<OsmWay> oNetworkSegments = new ArrayList();
			oWayNetworks.getWays(oNetworkSegments, 0, nBb[0], nBb[1], nBb[2], nBb[3]);
			for (OsmWay oWay : oNetworkSegments)
			{
				if (!oNetwork.wayInside(oWay))
					continue;
				
				SegmentGroup oGroup = null;
				if (oScenario != null)
				{
					for (SegmentGroup oTempGroup : oScenario.m_oGroups)
					{
						if (Arrays.binarySearch(oTempGroup.m_oSegments, oWay.m_oId, Id.COMPARATOR) >= 0)
						{
							oGroup = oTempGroup;
							break;
						}
					}
					if (oGroup == null)
						continue;
				}
				oM.lonLatToTile(GeoUtil.fromIntDeg(oWay.m_nMidLon), GeoUtil.fromIntDeg(oWay.m_nMidLat), oRR.getZoom(), nTile);
				oSearch.m_nX = nTile[0];
				oSearch.m_nY = nTile[1];
				int nIndex = Collections.binarySearch(oSessions, oSearch);
				if (nIndex < 0)
				{
					nIndex = ~nIndex;
					MLPSession oSess = new MLPSession(nTile[0], nTile[1], lRuntime, sFunction, oWayNetworks, oM, oRR, sNetworkDir);
					oSessions.add(nIndex, oSess);
				}
				ArrayList<OsmWay> oDownstreams = new ArrayList();
				for (OsmWay oDown : oWay.m_oNodes.get(oWay.m_oNodes.size() - 1).m_oRefs)
				{
					String sHighway = oDown.get("highway");
					if (sHighway == null || sHighway.contains("link") || OsmWay.WAYBYTEID.compare(oWay, oDown) == 0)
						continue;
					oDownstreams.add(oDown);
					if (oDown.m_bBridge)
					{
						for (OsmWay oSecond : oDown.m_oNodes.get(oDown.m_oNodes.size() - 1).m_oRefs)
						{
							sHighway = oSecond.get("highway");
							if (sHighway == null || sHighway.contains("link"))
								continue;
							oDownstreams.add(oSecond);
						}
					}
				}
				WayNetworks.WayMetadata oWayMetadata = oWayNetworks.getMetadata(oWay.m_oId);
				String sId = oWay.m_oId.toString();
				int nDirection = oWay.getDirection();
				int nCurve = oWay.getCurve();
				int nOffRamps;
				if (oWay.containsKey("egress"))
					nOffRamps = Integer.parseInt(oWay.get("egress"));
				else
					nOffRamps = 0;
				int nOnRamps;
				if (oWay.containsKey("ingress"))
					nOnRamps = Integer.parseInt(oWay.get("ingress"));
				else
					nOnRamps = 0;
				int nLanes;
				int nSpdLimit;
				if (oScenario != null && oScenario.m_oUserDefinedMetadata.containsKey(oWay.m_oId))
				{
					int[] nMetadata = oScenario.m_oUserDefinedMetadata.get(oWay.m_oId);
					nLanes = nMetadata[0];
					nSpdLimit = nMetadata[1];
				}
				else
				{
					nLanes = oWayMetadata.m_nLanes;
					nSpdLimit = oWayMetadata.m_nSpdLimit;
				}
				if (nLanes < 0)
					nLanes = 2;
				
				String sSpdLimit = nSpdLimit < 0 ? "65" : Integer.toString(oWayMetadata.m_nSpdLimit);
				MLPSessionRecord oRec = new MLPSessionRecord(oWay, oDownstreams, GeoUtil.getBoundingPolygon(oWay.m_nMidLon - nTol, oWay.m_nMidLat - nTol, oWay.m_nMidLon + nTol, oWay.m_nMidLat + nTol), oGroup, new MLPMetadata(sId, sSpdLimit, oWayMetadata.m_nHOV, nDirection, nCurve, nOffRamps, nOnRamps, oWayMetadata.m_nPavementCondition, oWay.m_sName, nLanes));
				ArrayList<MLPSessionRecord> oRecList = oSessions.get(nIndex).m_oSessionRecords;
				int nRecSearch = Collections.binarySearch(oRecList, oRec);
				if (nRecSearch < 0) // segment can be part of two networks so need only add once
					oRecList.add(~nRecSearch, oRec);
			}
		}
		int nIndex = oSessions.size();
		while (nIndex-- > 0)
		{
			if (oSessions.get(nIndex).m_oSessionRecords.isEmpty())
				oSessions.remove(nIndex);
		}
		
		return oSessions;
	}
	
	
	public boolean queueOneshot(Scenario oScenario, String sBaseDir)
	{
		try
		{
			WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
			long lNow = System.currentTimeMillis();
			lNow = (lNow / 3600000) * 3600000;
			int nHourDiff = (int)Duration.between(Instant.ofEpochMilli(lNow), Instant.ofEpochMilli(oScenario.m_lStartTime)).toHours();
			int nForecastOffset;
			if (nHourDiff <= 0)
				nForecastOffset = 0;
			else
				nForecastOffset = nHourDiff;

			long lLongTsUpdateStart = oScenario.m_lStartTime - (nForecastOffset * 3600000);
			long lRefTime = oScenario.m_lStartTime > lNow ? lNow : oScenario.m_lStartTime;

			ArrayList<MLPSession> oSessions = getSessions(lLongTsUpdateStart + 3600000, "os", oWayNetworks, oScenario);
			m_oLogger.debug("Sessions: " + oSessions.size());
			if (oSessions.isEmpty())
				return false;		

			ArrayList<MLPSession> oLongTs = new ArrayList(oSessions.size());

			for (int nIndex = 0; nIndex < oSessions.size(); nIndex++)
			{
				MLPSession oOneshotSess = oSessions.get(nIndex);
				oOneshotSess.m_oScenario = oScenario;
				oOneshotSess.m_sDirectory = sBaseDir + oScenario.m_sId + "/";
				Path oLtsOutputDir = Paths.get(String.format(oOneshotSess.m_sDirectory + m_sLtsInputFf, oOneshotSess.m_nX, oOneshotSess.m_nY));
				oOneshotSess.m_sExtendedLtsDirectory = oLtsOutputDir.getParent().toString() + "/";
				oOneshotSess.m_nForecastOffset = nForecastOffset;
				oOneshotSess.m_lRefTime = lRefTime;
				oOneshotSess.createOneshotInputFiles();
				MLPSession oLts = new MLPSession(oOneshotSess.m_nX, oOneshotSess.m_nY, lLongTsUpdateStart, "lts", oWayNetworks, oOneshotSess.m_oM, oOneshotSess.m_oRR, oOneshotSess.m_sNetworkDir);
				oLts.m_oSessionRecords = oOneshotSess.m_oSessionRecords;
				oLts.m_sDirectory = sBaseDir + oScenario.m_sId + "/";
				oLts.m_oScenario = oScenario;
				oLts.m_nForecastOffset = nForecastOffset;
				oLts.m_lRefTime = lRefTime;
				oLongTs.add(oLts);
			}
			Scheduling.getInstance().execute(() -> executeOneshot(oSessions, oLongTs, oScenario.m_sId));

			return true;
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
			return false;
		}
	}
	
	
	private void executeOneshot(ArrayList<MLPSession> oOneshotSessions, ArrayList<MLPSession> oLtsSessions, String sScenarioId)
	{
		try
		{
			m_oLogger.debug("starting lts");
			Scheduling.processCallables(oLtsSessions, m_nPythonProcesses / 2);
			m_oLogger.debug("starting os");
			Scheduling.processCallables(oOneshotSessions, m_nPythonProcesses / 2);
			notify("mlp", sScenarioId);
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
	
	
	
	public void longTsUpdate()
	{
		try
		{
			WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
			GregorianCalendar oCal = new GregorianCalendar(Directory.m_oUTC);
			while(oCal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY)
				oCal.add(Calendar.DAY_OF_WEEK, -1);
			oCal.set(Calendar.HOUR_OF_DAY, 0);
			oCal.set(Calendar.MINUTE, 0);
			oCal.set(Calendar.SECOND, 0);
			oCal.set(Calendar.MILLISECOND, 0);
			long lStartOfMonday = oCal.getTimeInMillis();
			long lNextMonday = lStartOfMonday + 604800000;
			long lCurTime = lStartOfMonday;
			ArrayList<Network> oNetworks = oWayNetworks.getNetworks();

			for (Network oNetwork : oNetworks)
			{
				int[] nBb = oNetwork.getBoundingBox();
				ArrayList<OsmWay> oWaysInNetwork = new ArrayList(oNetwork.m_oNetworkWays.size());
				oWayNetworks.getWays(oWaysInNetwork, 0, nBb[0], nBb[1], nBb[2], nBb[3]);
				int nWayIndex = oWaysInNetwork.size();
				while (nWayIndex-- > 0)
				{
					OsmWay oWay = oWaysInNetwork.get(nWayIndex);
					if (!oNetwork.wayInside(oWay))
						oWaysInNetwork.remove(nWayIndex);
				}
			}
			while (lCurTime < lNextMonday)
			{
				lCurTime += 300000;
			}
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
	
	
	protected void createFiles(ArrayList<MLPSession> oSessions)
	{
		try
		{
			m_oLogger.debug("create files");
			long lFileTime = oSessions.get(0).m_lRefTime;
			long lStartTime = oSessions.get(0).m_lRuntime;
			int nContrib = Integer.valueOf("mlprt", 36);
			String sOutput = "mlp_output.csv";
			long lDiff = 900000;
			if (oSessions.get(0).m_sFunction.compareTo("os") == 0)
			{
				nContrib = Integer.valueOf("mlpos", 36);
				sOutput = "mlp_oneshot_output.csv";
				lDiff = 3600000;
				lStartTime -= 300000;
			}
			long[] lTimes;
			if (lDiff == 900000)
				lTimes = new long[8];
			else
				lTimes = new long[169];

			java.util.Arrays.fill(lTimes, lDiff);
			lTimes[0] = lStartTime;
			WayNetworks oWays = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
			ArrayList<MLPTile> oTiles = new ArrayList();
			ResourceRecord oRR = Directory.getResource(nContrib, ObsType.SPDLNK);
			
			if (oRR == null)
				throw new Exception("No valid resource record configured");
			
			
			long lEndTime = lStartTime + oRR.getRange();
			int[] nTile = new int[2];
			int nPPT = (int)Math.pow(2, oRR.getTileSize()) - 1;
			
			Mercator oM = new Mercator(nPPT);
			int[] nBB = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
			Units.UnitConv oConv = Units.getInstance().getConversion(oRR.getSrcUnits(), ObsType.getUnits(ObsType.SPDLNK, true));
			
			TreeMap<Id, double[]> oPreds = new TreeMap(Id.COMPARATOR);
			ArrayList<OsmWay> oSegments = new ArrayList();
			int nSessions  = 0;
			for (MLPSession oSess : oSessions)
			{
				if (oSess.m_nExit != 0) // ignore sessions that did not exit gracefully from python
					continue;
				++nSessions;
				try (CsvReader oIn = new CsvReader(Files.newInputStream(Paths.get(oSess.m_sDirectory + sOutput))))
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
				oSegments.ensureCapacity(oSess.m_oSessionRecords.size());
				for (MLPSessionRecord oRec : oSess.m_oSessionRecords)
					oSegments.add(oRec.m_oWay);
			}
			m_oLogger.info(String.format("%d processed tiles", nSessions));
			m_oLogger.info(String.format("%s predictions - %d", Integer.toString(nContrib, 36),  oPreds.size()));
			int nExtended = extendPredictions(oPreds, oSegments, oWays, m_dExtendDistTol, nBB);
			m_oLogger.info(String.format("Extended %s predictions - %d", Integer.toString(nContrib, 36), nExtended));
			for (double[] dTileInfo : oPreds.values())
			{
				oM.lonLatToTile(dTileInfo[0], dTileInfo[1], oRR.getZoom(), nTile);
				MLPTile oTile = new MLPTile(nTile[0], nTile[1]);
				int nIndex = Collections.binarySearch(oTiles, oTile);
				if (nIndex < 0)
				{
					nIndex = ~nIndex;
					oTiles.add(nIndex, oTile);
				}

				oTile = oTiles.get(nIndex);

				oTile.m_oTileInfos.add(dTileInfo);
			}
				
			if (oTiles.isEmpty())
				return;
			

			for (MLPTile oTile : oTiles)
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

				for (MLPTile oTile : oTiles) // finish writing tile metadata
				{
					oOut.writeShort(oTile.m_nX);
					oOut.writeShort(oTile.m_nY);
					oOut.writeInt(oTile.m_yTileData.length);
				}

				for (MLPTile oTile : oTiles)
				{
					oOut.write(oTile.m_yTileData);
				}
			}
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
		finally
		{
			try
			{
				for (MLPSession oSess : oSessions)
				{
					for (Path oFile : Files.walk(Paths.get(oSess.m_sDirectory), FileVisitOption.FOLLOW_LINKS).sorted(Comparator.reverseOrder()).collect(Collectors.toList())) // delete all associated files
					{
						Files.delete(oFile);
					}
				}
				Files.delete(Paths.get(oSessions.get(0).m_sDirectory).getParent());
			}
			catch (IOException oIOEx)
			{
				m_oLogger.error(oIOEx, oIOEx);
			}
		}
		m_oLogger.debug("done");
	}
	
	
	@Override
	protected void createFiles(TileFileInfo oInfo, TreeSet<Path> oArchiveFiles)
	{
	}
	
	
	public static String getModelDir(String sNetworkDir, String sSubDir, boolean bHurricaneModel)
	{
		if (bHurricaneModel)
		{
			String sDirFullPath = sNetworkDir + sSubDir;
			boolean bExists = hurricaneModelFilesExist(sDirFullPath);
			if (bExists)
			{
				return sDirFullPath;
			}
		}
		else
		{
			if (Files.exists(Paths.get(sNetworkDir + "decision_tree.pickle")) && Files.exists(Paths.get(sNetworkDir + "mlp_python_data.pkl")))
			{
				return sNetworkDir;
			}
		}
		return sNetworkDir.substring(0, sNetworkDir.lastIndexOf("/", sNetworkDir.length() - 2) + 1); // -2 to skip the ending "/" and then + 1 to include the slash
	}

	public static int getVisibility(double dVisInFt)
	{
		if (dVisInFt > 3300)
		{
			return 1;
		}
		else
		{
			if (dVisInFt >= 330)
			{
				return 2;
			}
			else
			{
				return 3;
			}
		}
	}

	/**
	 * Gets data about upstream, downstream, and segment incidents as well as if
	 * there roadwork on the segment.
	 *
	 * @return [onlink incident, downstream incident, onlink workzone, downstream workzone, onlink lanes closed, downstream lanes closed]
	 */
	public static int[] getIncidentData(ObsList oEvents, OsmWay oWay, ArrayList<OsmWay> oDownstream, long lTimestamp)
	{
		int[] nReturn = new int[]{0, 0, 0, 0, 0, 0};
		double dIncident = ObsType.lookup(ObsType.EVT, "incident");
		double dFloodedRoad = ObsType.lookup(ObsType.EVT, "flooded-road");
		for (int nIndex = 0; nIndex < oEvents.size(); nIndex++)
		{
			Obs oEvent = oEvents.get(nIndex);
			if (oEvent.m_lObsTime1 >= lTimestamp + 300000 || oEvent.m_lObsTime2 < lTimestamp)
			{
				continue;
			}
			int nLanes = Integer.parseInt(oEvent.m_sStrings[3]);
			if (oEvent.m_dValue == dIncident || oEvent.m_dValue == dFloodedRoad)
			{
				for (OsmWay oDown : oDownstream)
				{
					if (Id.COMPARATOR.compare(oDown.m_oId, oEvent.m_oObjId) == 0)
					{
						nReturn[1] = 1;
						if (oEvent.m_dValue == dIncident)
						{
							if (nLanes > nReturn[5])
							{
								nReturn[5] = nLanes;
							}
						}
						else
						{
							// flooded road
							nReturn[5] = Events.ALLLANES;
						}
						break;
					}
					if (nReturn[1] == 1)
					{
						break;
					}
				}
				if (Id.COMPARATOR.compare(oWay.m_oId, oEvent.m_oObjId) == 0)
				{
					nReturn[0] = 1;
					if (oEvent.m_dValue == dIncident)
					{
						if (nLanes > nReturn[4])
						{
							nReturn[4] = nLanes;
						}
					}
					else
					{
						// flooded road
						nReturn[4] = Events.ALLLANES;
					}
				}
			}
			else // workzone
			{
				for (OsmWay oDown : oDownstream)
				{
					if (Id.COMPARATOR.compare(oDown.m_oId, oEvent.m_oObjId) == 0)
					{
						nReturn[3] = 1;
						if (nLanes > nReturn[5])
						{
							nReturn[5] = nLanes;
						}
						break;
					}
					if (nReturn[3] == 1)
					{
						break;
					}
				}
				if (Id.COMPARATOR.compare(oWay.m_oId, oEvent.m_oObjId) == 0)
				{
					nReturn[2] = 1;
					if (nLanes > nReturn[4])
					{
						nReturn[4] += nLanes;
					}
				}
			}
		}
		return nReturn;
	}

	/**
	 * Returns the day of week category for Bayes for the given time
	 *
	 * @param lTime timestamp of the observation in milliseconds
	 * @return 1 if a weekend, 2 if a weekday
	 */
	public static int getDayOfWeek(Calendar oCal)
	{
		int nDayOfWeek = oCal.get(Calendar.DAY_OF_WEEK);
		if (nDayOfWeek == Calendar.SATURDAY || nDayOfWeek == Calendar.SUNDAY)
		{
			return 1; // 1-WEEKEND
		}
		return 2; // 2-WEEKDAY
	}

	/**
	 * Returns the time of day category for Bayes for the given time
	 *
	 * @param lTime timestamp of the observation in milliseconds
	 * @return	1 = morning 2 = AM peak 3 = Off peak 4 = PM peak 5 = night
	 */
	public static int getTimeOfDay(Calendar oCal)
	{
		int nTimeOfDay = oCal.get(Calendar.HOUR_OF_DAY);
		if (nTimeOfDay >= 1 && nTimeOfDay < 6)
		{
			return 1; // 1-MORNING"
		}
		if (nTimeOfDay >= 6 && nTimeOfDay < 10)
		{
			return 2; // 2-AM PEAK
		}
		if (nTimeOfDay >= 10 && nTimeOfDay < 16)
		{
			return 3; // 3-OFFPEAK
		}
		if (nTimeOfDay >= 16 && nTimeOfDay < 20)
		{
			return 4; // 4-PM PEAK
		}
		return 5; // 5-NIGHT
	}

	public static int extendPredictions(TreeMap<Id, double[]> oPreds, ArrayList<OsmWay> oAllSegments, WayNetworks oWayNetworks, double dTol, int[] nBB)
	{
		int nCount = 0;
		double dHdgThresh = Math.PI / 6;
		for (OsmWay oWay : oAllSegments)
		{
			if (!oPreds.containsKey(oWay.m_oId))
			{
				continue;
			}
			double[] dPred = oPreds.get(oWay.m_oId);
			double dTotal = 0.0;
			double dLastDistance;
			OsmWay oCur = oWay;
			if (oCur == null)
			{
				continue;
			}
			while (dTotal < dTol)
			{
				dLastDistance = dTotal;
				OsmWay oUp = null;
				double dMinHeading = Double.MAX_VALUE;
				OsmNode oCur1 = oCur.m_oNodes.get(0);
				OsmNode oCur2 = oCur.m_oNodes.get(1);
				double dCurHdg = GeoUtil.heading(oCur1.m_nLon, oCur1.m_nLat, oCur2.m_nLon, oCur2.m_nLat);
				for (OsmWay oTemp : oCur.m_oNodes.get(0).m_oRefs)
				{
					String sHighway = oTemp.get("highway");
					if ((sHighway != null && sHighway.contains("link")) || oTemp.m_oId.compareTo(oCur.m_oId) == 0)
					{
						continue;
					}
					OsmNode oTemp1 = oTemp.m_oNodes.get(oTemp.m_oNodes.size() - 2);
					OsmNode oTemp2 = oTemp.m_oNodes.get(oTemp.m_oNodes.size() - 1);
					double dTempHdg = GeoUtil.heading(oTemp1.m_nLon, oTemp1.m_nLat, oTemp2.m_nLon, oTemp2.m_nLat);
					double dHdgDiff = GeoUtil.hdgDiff(dCurHdg, dTempHdg);
					if (dHdgDiff < dHdgThresh && dHdgDiff < dMinHeading)
					{
						dMinHeading = dHdgDiff;
						oUp = oTemp;
					}
				}
				if (oUp != null)
				{
					oCur = oUp;
					dTotal += oUp.m_dLength;
					if (oPreds.containsKey(oUp.m_oId))
					{
						break;
					}
					if (oUp.m_nMinLon < nBB[0])
					{
						nBB[0] = oUp.m_nMinLon;
					}
					if (oUp.m_nMinLat < nBB[1])
					{
						nBB[1] = oUp.m_nMinLat;
					}
					if (oUp.m_nMaxLon > nBB[2])
					{
						nBB[2] = oUp.m_nMaxLon;
					}
					if (oUp.m_nMaxLat > nBB[3])
					{
						nBB[3] = oUp.m_nMaxLat;
					}
					double[] dNewPred = new double[dPred.length];
					dNewPred[0] = GeoUtil.fromIntDeg(oUp.m_nMidLon);
					dNewPred[1] = GeoUtil.fromIntDeg(oUp.m_nMidLat);
					System.arraycopy(dPred, 2, dNewPred, 2, dNewPred.length - 2);
					oPreds.put(oUp.m_oId, dNewPred);
					++nCount;
				}
				if (dLastDistance == dTotal)
				{
					break;
				}
			}
		}
		return nCount;
	}

	public static boolean isMLPContrib(int nContrib)
	{
		String sContrib = Integer.toString(nContrib, 36);
		return sContrib.toLowerCase().startsWith("mlp");
	}

	public static int getPrecip(double dRate, double dVisInFt, double dTempInF)
	{
		if (dRate == 0.0)
		{
			return 1;
		}
		if (dTempInF > 35.6)
		{
			if (dRate >= 7.6)
			{
				return 4;
			}
			else
			{
				if (dRate >= 2.5)
				{
					return 3;
				}
				else
				{
					return 2;
				}
			}
		}
		else
		{
			if (dVisInFt > 3300)
			{
				return 5;
			}
			else
			{
				if (dVisInFt >= 1650)
				{
					return 6;
				}
				else
				{
					return 7;
				}
			}
		}
	}

	public static boolean hurricaneModelFilesExist(String sDirectory)
	{
		String sFf = "online_model_%dhour.pth";
		boolean bExists = Files.exists(Paths.get(sDirectory + "oneshot_model.pth"));
		int nIndex = 0;
		while (bExists && nIndex++ < 6)
		{
			bExists = Files.exists(Paths.get(sDirectory + String.format(sFf, nIndex)));
		}
		return bExists;
	}
	
	
	protected class MLPSession implements Comparable<MLPSession>, Callable<MLPSession>
	{
		protected long m_lRuntime;
		protected long m_lRefTime;
		protected String m_sFunction;
		protected int m_nX;
		protected int m_nY;
		protected ArrayList<MLPSessionRecord> m_oSessionRecords = new ArrayList();
		protected boolean m_bProcess = false;
		protected WayNetworks m_oWayNetworks;
		protected Mercator m_oM;
		protected ResourceRecord m_oRR;
		protected String m_sDirectory;
		protected TimeZone m_oTz;
		protected Scenario m_oScenario;
		protected int m_nForecastOffset;
		protected String m_sNetworkDir;
		protected int m_nExit = 1;
		protected String m_sExtendedLtsDirectory = "";
		

		MLPSession()
		{
		}
		
		MLPSession(int nX, int nY, long lRuntime, String sFunction, WayNetworks oWayNetworks, Mercator oM, ResourceRecord oRR, String sNetworkDir)
		{
			m_nX = nX;
			m_nY = nY;
			m_lRuntime = lRuntime;
			m_sFunction = sFunction;
			m_oWayNetworks = oWayNetworks;
			m_oM = oM;
			m_oRR = oRR;
			m_sNetworkDir = sNetworkDir;
		}
		
		
		@Override
		public int compareTo(MLPSession o)
		{
			int nRet = m_nX - o.m_nX;
			if (nRet == 0)
				nRet = m_nY - o.m_nY;
			
			return nRet;
		}
	
		
		protected boolean createExtendedLongTsInputFiles()
			throws Exception
		{
			boolean bHasDataToProcess = false;
			double[] dBounds = new double[4];
			m_oM.lonLatBounds(m_nX, m_nY, m_oRR.getZoom(), dBounds);

			int[] nTileBb = new int[]{GeoUtil.toIntDeg(dBounds[0]), GeoUtil.toIntDeg(dBounds[1]), GeoUtil.toIntDeg(dBounds[2]), GeoUtil.toIntDeg(dBounds[3])};
			m_oTz = m_oWayNetworks.getTimeZone(nTileBb[0], nTileBb[1]);
			GregorianCalendar oCal = new GregorianCalendar(m_oTz);
			oCal.setTimeInMillis(m_lRuntime);
			oCal.set(Calendar.HOUR_OF_DAY, 0);
			oCal.set(Calendar.MINUTE, 0);
			oCal.set(Calendar.SECOND, 0);
			oCal.set(Calendar.MILLISECOND, 0);

			long lQueryTime = oCal.getTimeInMillis() - 604800000;
			long lEndTime = oCal.getTimeInMillis();

			ArrayList<ResourceRecord> oRRs = Directory.getResourcesByObsType(ObsType.SPDLNK);
			ArrayList<Integer> oContribSources = new ArrayList(oRRs.size() * 2);
			for (ResourceRecord oRR : oRRs)
			{
				if (!isMLPContrib(oRR.getContribId()))
				{
					oContribSources.add(oRR.getContribId());
					oContribSources.add(oRR.getSourceId());
				}
			}
			int[] nContribSources = new int[oContribSources.size()];
			for (int nIndex = 0; nIndex < nContribSources.length; nIndex++)
				nContribSources[nIndex] = oContribSources.get(nIndex);

			TileObsView oOV = (TileObsView)Directory.getInstance().lookup("ObsView");
			Units oUnits = Units.getInstance();
			Units.UnitConv oSpdlnkConv = oUnits.getConversion(ObsType.getUnits(ObsType.SPDLNK, true), "mph");
			LongTsRecord[][] oLtsRecords = new LongTsRecord[m_oSessionRecords.size()][];
			for (int nIndex = 0; nIndex < oLtsRecords.length; nIndex++)
				oLtsRecords[nIndex] = new LongTsRecord[2016];

			int nArrayIndex = 0;
			while (lQueryTime < lEndTime)
			{
				
				for (int nTimeIndex = 0; nTimeIndex < 12; nTimeIndex++) // 12 5 minute intervals in an hour
				{
					oCal.setTimeInMillis(lQueryTime);
					long lQueryEnd = lQueryTime + 300000;
					ObsList oSpds = oOV.getData(ObsType.SPDLNK, lQueryTime, lQueryEnd, nTileBb[1], nTileBb[3], nTileBb[0], nTileBb[2], m_lRefTime, nContribSources);
					for (int nRecIndex = 0; nRecIndex < oLtsRecords.length; nRecIndex++)
					{
						MLPSessionRecord oRec = m_oSessionRecords.get(nRecIndex);
						LongTsRecord oInput = new LongTsRecord();
						oInput.m_sId = oRec.m_oMetadata.m_sId;
						oInput.m_lTimestamp = lQueryTime;
						oInput.m_dSpeed = oSpdlnkConv.convert(getValue(oSpds, oRec.m_nQueryGeo));
						oInput.m_nDayOfWeek = getDayOfWeek(oCal);
						oLtsRecords[nRecIndex][nArrayIndex] = oInput;
					}
					++nArrayIndex;
					lQueryTime = lQueryEnd;
				}
			}
			Path oLts = Paths.get(String.format(m_sTempPath + m_sExtendedLongTsFf, m_nX, m_nY));
			Files.createDirectories(oLts.getParent(), FileUtil.DIRPERS);
			SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
			oSdf.setTimeZone(oCal.getTimeZone());
			oCal.add(Calendar.MINUTE, 5);
			oCal.add(Calendar.DAY_OF_YEAR, -1); // set calendar to 1 day before start time
			try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oLts, FileUtil.WRITE, FileUtil.FILEPERS), StandardCharsets.UTF_8)))
			{
				oOut.append(LongTsRecord.OUTPUTHEADER);
				ArrayList<LongTsRecord> oBuffer = new ArrayList(2016);
				for (int nRecIndex = 0; nRecIndex < oLtsRecords.length; nRecIndex++)
				{
					oBuffer.clear();
					for (LongTsRecord oRec : oLtsRecords[nRecIndex])
					{
						oBuffer.add(oRec);
					}
					if (interpolate(oBuffer, oBuffer.size(), m_nInterpolateLimit))
					{
						bHasDataToProcess = true;
						long lTs = oCal.getTimeInMillis();
						for (int nIndex = 1728; nIndex < 2016; nIndex++)
						{
							LongTsRecord oRec = oBuffer.get(nIndex);
							oOut.append(String.format("%s,%s,%.2f\n", oRec.m_sId, oSdf.format(lTs), oRec.m_dSpeed));
							lTs += 300000; // 5 minutes between each record
						}
						for (int nIndex = 0; nIndex < 2016; nIndex++)
						{
							LongTsRecord oRec = oBuffer.get(nIndex);
							oOut.append(String.format("%s,%s,%.2f\n", oRec.m_sId, oSdf.format(lTs), oRec.m_dSpeed));
							lTs += 300000; // 5 minutes between each record
						}
						for (int nIndex = 0; nIndex < 288; nIndex++)
						{
							LongTsRecord oRec = oBuffer.get(nIndex);
							oOut.append(String.format("%s,%s,%.2f\n", oRec.m_sId, oSdf.format(lTs), oRec.m_dSpeed));
							lTs += 300000; // 5 minutes between each record
						}
					}
				}
			}
			
			return bHasDataToProcess;
		}
		
		protected void createLongTsInputFiles()
		{
			try
			{
				double[] dBounds = new double[4];
				m_oM.lonLatBounds(m_nX, m_nY, m_oRR.getZoom(), dBounds);

				int[] nTileBb = new int[]{GeoUtil.toIntDeg(dBounds[0]), GeoUtil.toIntDeg(dBounds[1]), GeoUtil.toIntDeg(dBounds[2]), GeoUtil.toIntDeg(dBounds[3])};
				m_oTz = m_oWayNetworks.getTimeZone(nTileBb[0], nTileBb[1]);
				GregorianCalendar oCal = new GregorianCalendar(m_oTz);
				oCal.setTimeInMillis(m_lRuntime);
				oCal.set(Calendar.HOUR_OF_DAY, 0);
				
				long lQueryTime = oCal.getTimeInMillis() - 604800000;
				
				ArrayList<ResourceRecord> oRRs = Directory.getResourcesByObsType(ObsType.SPDLNK);
				ArrayList<Integer> oContribSources = new ArrayList(oRRs.size() * 2);
				for (ResourceRecord oRR : oRRs)
				{
					if (!isMLPContrib(oRR.getContribId()))
					{
						oContribSources.add(oRR.getContribId());
						oContribSources.add(oRR.getSourceId());
					}
				}
				int[] nContribSources = new int[oContribSources.size()];
				for (int nIndex = 0; nIndex < nContribSources.length; nIndex++)
					nContribSources[nIndex] = oContribSources.get(nIndex);

				TileObsView oOV = (TileObsView)Directory.getInstance().lookup("ObsView");
				Units oUnits = Units.getInstance();
				Units.UnitConv oSpdlnkConv = oUnits.getConversion(ObsType.getUnits(ObsType.SPDLNK, true), "mph");
				
//				StringBuilder sBuf = new StringBuilder();
//				int[] nGeo = m_oSessionRecords.get(0).m_nQueryGeo;
//				for (int nIndex = 7; nIndex < 15; nIndex += 2)
//				{
//					sBuf.append(String.format("[%.7f,%.7f],", GeoUtil.fromIntDeg(nGeo[nIndex]), GeoUtil.fromIntDeg(nGeo[nIndex + 1])));
//				}
//				System.out.append(sBuf).append('\n');
				ArrayList<LongTsRecord> oLtsRecords = new ArrayList(m_oSessionRecords.size() * 2016);
				while (lQueryTime < m_lRuntime)
				{
					for (int nTimeIndex = 0; nTimeIndex < 12; nTimeIndex++) // 12 5 minute intervals in an hour
					{
						oCal.setTimeInMillis(lQueryTime);
						long lQueryEnd = lQueryTime + 300000;
						ObsList oSpds = oOV.getData(ObsType.SPDLNK, lQueryTime, lQueryEnd, nTileBb[1], nTileBb[3], nTileBb[0], nTileBb[2], m_lRefTime, nContribSources);
//						if (m_lRuntime - 604800000 == lQueryTime)
//						{
//							sBuf.setLength(0);
//							for (Obs oSpd : oSpds)
//							{
//								sBuf.append(String.format("{'type':'Feature', 'properties':{'opacity':1.0,'color':'blue'},'geometry':{'type':'Point','coordinates':[%.7f,%.7f]}\n", GeoUtil.fromIntDeg(oSpd.m_oGeoArray[1]), GeoUtil.fromIntDeg(oSpd.m_oGeoArray[2])));
//							}
//							System.out.append(sBuf);
//						}
						for (MLPSessionRecord oRec : m_oSessionRecords)
						{
							LongTsRecord oInput = new LongTsRecord();
							oInput.m_sId = oRec.m_oMetadata.m_sId;
							oInput.m_lTimestamp = lQueryTime;
							oInput.m_dSpeed = oSpdlnkConv.convert(getValue(oSpds, oRec.m_nQueryGeo));
							oInput.m_nDayOfWeek = getDayOfWeek(oCal);
							oLtsRecords.add(oInput);
						}
						lQueryTime = lQueryEnd;
					}
				}
				Introsort.usort(oLtsRecords);
				Path oLtsInput = Paths.get(String.format(m_sDirectory + m_sLtsInputFf, m_nX, m_nY));
				Files.createDirectories(oLtsInput.getParent(), FileUtil.DIRPERS);
				m_sDirectory = oLtsInput.getParent().toString() + "/";
				SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
				oSdf.setTimeZone(oCal.getTimeZone());
				try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oLtsInput, FileUtil.WRITE, FileUtil.FILEPERS), StandardCharsets.UTF_8)))
				{
					oOut.append(LongTsRecord.INPUTHEADER);
					String sPrevId = oLtsRecords.get(0).m_sId;
					int nWeekendVals = 0;
					int nWeekdayVals = 0;
					int nSegsWithData = 0;
					StringBuilder sBuf = new StringBuilder();
					int nLast = oLtsRecords.size() - 1;
					for (int nLtsIndex = 0; nLtsIndex <= nLast; nLtsIndex++)
					{
						LongTsRecord oLtsRecord = oLtsRecords.get(nLtsIndex);
						if (sPrevId.compareTo(oLtsRecord.m_sId) != 0 || nLtsIndex == nLast)
						{
							if (nLtsIndex == nLast)
							{
								sBuf.append(oLtsRecord.format(oSdf));
								if (Double.isFinite(oLtsRecord.m_dSpeed))
								{
									if (oLtsRecord.m_nDayOfWeek == 1)
										++nWeekdayVals;
									else
										++nWeekendVals;
								}
							}
							if (nWeekendVals > 5 && nWeekdayVals > 5)
							{
								oOut.append(sBuf);
								++nSegsWithData;
							}
							sBuf.setLength(0);
							nWeekendVals = 0;
							nWeekdayVals = 0;
						}
						sBuf.append(oLtsRecord.format(oSdf));
						sPrevId = oLtsRecord.m_sId;
						if (Double.isFinite(oLtsRecord.m_dSpeed))
						{
							if (oLtsRecord.m_nDayOfWeek == 1)
								++nWeekdayVals;
							else
								++nWeekendVals;
						}
					}
				}
			}
			catch (Exception oEx)
			{
				m_oLogger.error(oEx, oEx);
			}
		}
		
		
		protected void createOneshotInputFiles()
		{
			try
			{
				long lQueryTime = m_lRuntime - 21600000; // python needs 6 extra inital records
				long lEnd = m_lRuntime + 608400000; // accumulate 1 week of weather forecasts
				double[] dBounds = new double[4];
				m_oM.lonLatBounds(m_nX, m_nY, m_oRR.getZoom(), dBounds);

				int[] nTileBb = new int[]{GeoUtil.toIntDeg(dBounds[0]), GeoUtil.toIntDeg(dBounds[1]), GeoUtil.toIntDeg(dBounds[2]), GeoUtil.toIntDeg(dBounds[3])};
				TileObsView oOV = (TileObsView)Directory.getInstance().lookup("ObsView");
				Units oUnits = Units.getInstance();
				Units.UnitConv oTairConv = oUnits.getConversion(ObsType.getUnits(ObsType.TAIR, true), "F");
				Units.UnitConv oVisConv = oUnits.getConversion(ObsType.getUnits(ObsType.VIS, true), "ft");
				Units.UnitConv oSpdwndConv = oUnits.getConversion(ObsType.getUnits(ObsType.SPDWND, true), "mph");
				Comparator<WaySnapInfo> oCompById = (WaySnapInfo o1, WaySnapInfo o2) -> Id.COMPARATOR.compare(o1.m_oWay.m_oId, o2.m_oWay.m_oId);
				Comparator<WaySnapInfo> oCompByDist = (WaySnapInfo o1, WaySnapInfo o2) -> Integer.compare(o1.m_nSqDist, o2.m_nSqDist);
				m_oTz = m_oWayNetworks.getTimeZone(nTileBb[0], nTileBb[1]);
				GregorianCalendar oCal = new GregorianCalendar(m_oTz);
				ArrayList<RealTimeRecord> oRTRecords = new ArrayList(m_oSessionRecords.size() * 175);
				int nForecastHour = -6;
				double dIncident = ObsType.lookup(ObsType.EVT, "incident");
				double dWorkzone = ObsType.lookup(ObsType.EVT, "workzone");
				double dFloodedRoad = ObsType.lookup(ObsType.EVT, "flooded-road");
				double dFloodedStpvt = ObsType.lookup(ObsType.STPVT, "flooded");

				while (lQueryTime < lEnd)
				{
					long lQueryEnd = lQueryTime + 3600000;
					boolean bUseScenarioMetadata = m_oScenario != null && nForecastHour >= m_nForecastOffset && nForecastHour < m_nForecastOffset + 24;
					int nScenarioTimeIndex = nForecastHour - m_nForecastOffset;
					ObsList oEvt = oOV.getData(ObsType.EVT, lQueryTime, lQueryEnd, nTileBb[1], nTileBb[3], nTileBb[0], nTileBb[2], m_lRefTime);
					ObsList oSnappedEvts = new ObsList(oEvt.size());
					int nEvtIndex = oEvt.size();
					while (nEvtIndex-- > 0)
					{
						Obs oEvent = oEvt.get(nEvtIndex);
						if (oEvent.m_yGeoType == Obs.POINT && (oEvent.m_dValue == dIncident || oEvent.m_dValue == dWorkzone || oEvent.m_dValue == dFloodedRoad))
						{
							double dHdg = Double.NaN;
							int nSegLimit = 1;
							if (oEvent.m_dValue != dFloodedRoad)
							{
								String sDir = oEvent.m_sStrings[4];
								if (sDir.equalsIgnoreCase("e"))
									dHdg = 0;
								else if (sDir.equalsIgnoreCase("n"))
									dHdg = Math.PI / 2;
								else if (sDir.equalsIgnoreCase("w"))
									dHdg = Math.PI;
								else if (sDir.equalsIgnoreCase("s"))
									dHdg = 3 * Math.PI / 2;
								else if (sDir.equalsIgnoreCase("b") || sDir.equalsIgnoreCase("a")) // all or both
									nSegLimit = 2; // allow snapping to two roadway segments if multiple directions
								else if (sDir.equalsIgnoreCase("ne"))
									dHdg = Math.PI / 4;
								else if (sDir.equalsIgnoreCase("nw"))
									dHdg = 3 * Math.PI / 4;
								else if (sDir.equalsIgnoreCase("se"))
									dHdg = 7 * Math.PI / 4;
								else if (sDir.equalsIgnoreCase("sw"))
									dHdg = 5 * Math.PI / 4;
								else
									dHdg = Double.NaN;
							}

							ArrayList<WaySnapInfo> oSnaps = m_oWayNetworks.getSnappedWays(10000, oEvent.m_oGeoArray[1], oEvent.m_oGeoArray[2], dHdg, Math.PI / 4, oCompById);
							if (oSnaps.isEmpty())
								continue;
							Introsort.usort(oSnaps, oCompByDist); // sort by distances
							oEvent.m_oObjId = oSnaps.get(0).m_oWay.m_oId;
							oSnappedEvts.add(oEvent);
							if (oSnaps.size() > 1 && nSegLimit == 2)
							{
								Obs oNewEvent = new Obs(oEvent);
								oNewEvent.m_oObjId = oSnaps.get(1).m_oWay.m_oId;
								oSnappedEvts.add(oNewEvent);
							}
						}
					}
					
					oCal.setTimeInMillis(lQueryTime);
					ObsList oRtepc = oOV.getPreferedData(ObsType.RTEPC, nTileBb, lQueryTime, lQueryTime + 60000, m_lRefTime);
					ObsList oTair = oOV.getPreferedData(ObsType.TAIR, nTileBb, lQueryTime, lQueryTime + 60000, m_lRefTime);
					ObsList oVis = oOV.getPreferedData(ObsType.VIS, nTileBb, lQueryTime, lQueryTime + 60000, m_lRefTime);
					ObsList oSpdwnd = oOV.getPreferedData(ObsType.SPDWND, nTileBb, lQueryTime, lQueryTime + 60000, m_lRefTime);
					ObsList oStpvt = oOV.getPreferedData(ObsType.STPVT, nTileBb, lQueryTime, lQueryTime + 60000, m_lRefTime);
					int nStpvtIndex = oStpvt.size();
					while (nStpvtIndex-- > 0)
					{
						Obs oState = oStpvt.get(nStpvtIndex);
						if (oState.m_dValue != dFloodedStpvt)
							oStpvt.remove(nStpvtIndex);
					}
					
					for (MLPSessionRecord oRec : m_oSessionRecords)
					{
						RealTimeRecord oInput = new RealTimeRecord();
						oInput.m_sId = oRec.m_oMetadata.m_sId;
						double dRate = getAverageValue(oRtepc, oRec.m_nQueryGeo);
						double dTair = getValue(oTair, oRec.m_nQueryGeo);
						if (Double.isFinite(dTair))
							dTair = Math.round(oTairConv.convert(dTair));
						double dVis = getValue(oVis, oRec.m_nQueryGeo);
						if (Double.isFinite(dVis))
							dVis = Math.round(oVisConv.convert(dVis));
						double dSpdwnd = getValue(oSpdwnd, oRec.m_nQueryGeo);
						if (Double.isFinite(dSpdwnd))
							dSpdwnd = Math.round(oSpdwndConv.convert(dSpdwnd));
						
						boolean bFloodedStpvt = Double.isFinite(getValue(oStpvt, oRec.m_nQueryGeo));
						oInput.m_lTimestamp = lQueryTime;
						oInput.m_nContraflow = -1;
						oInput.m_nCurve = oRec.m_oMetadata.m_nCurve;
						oInput.m_nDirection = oRec.m_oMetadata.m_nDirection;
						oInput.m_nHOV = oRec.m_oMetadata.m_nHOV;
						oInput.m_sSpeedLimit = oRec.m_oMetadata.m_sSpdLimit;
						oInput.m_nDayOfWeek = getDayOfWeek(oCal);
						oInput.m_nLanes = oRec.m_oMetadata.m_nLanes;
						oInput.m_nOffRamps = oRec.m_oMetadata.m_nOffRamps;
						oInput.m_nOnRamps = oRec.m_oMetadata.m_nOnRamps;
						oInput.m_nPavementCondition = oRec.m_oMetadata.m_nPavementCondition;
						oInput.m_nSpecialEvents = oRec.m_oMetadata.m_nSpecialEvents;
						int nLanesClosed = 0;
						int nLanesClosedDownstream = 0;
						if (bUseScenarioMetadata && nScenarioTimeIndex >= 0)
						{
							nLanesClosed = oRec.m_oMetadata.m_nLanes - oRec.m_oGroup.m_nLanes[nScenarioTimeIndex];
							oInput.m_nVsl = oRec.m_oGroup.m_nVsl[nScenarioTimeIndex];
							for (OsmWay oDown : oRec.m_oDownstreams)
							{
								for (int nTempIndex = 0; nTempIndex < m_oScenario.m_oGroups.length; nTempIndex++)
								{
									SegmentGroup oTempGroup = m_oScenario.m_oGroups[nTempIndex];
									int nSearch = Arrays.binarySearch(oTempGroup.m_oSegments, oDown.m_oId, Id.COMPARATOR);
									if (nSearch >= 0)
									{
										int nLanes = m_oWayNetworks.getLanes(oDown.m_oId);
										if (nLanes < 0)
											nLanes = 2;
										nLanesClosedDownstream += (nLanes - oTempGroup.m_nLanes[nScenarioTimeIndex]);
										break;
									}
								}
								if (nLanesClosedDownstream > 0)
									break;
							}
						}
						if (bFloodedStpvt)
							nLanesClosed = oInput.m_nLanes;

						oInput.m_sRoad = oRec.m_oMetadata.m_sRoad;
						oInput.m_nPrecipitation = getPrecip(dRate, dVis, dTair);
						oInput.m_nTemperature = Double.isFinite(dTair) ? (int)dTair : 55;
						oInput.m_nTimeOfDay = getTimeOfDay(oCal);
						oInput.m_nVisibility = Double.isFinite(dVis) ? getVisibility(dVis) : 1;
						oInput.m_nWindSpeed = Double.isFinite(dSpdwnd) ? (int)dSpdwnd : 5;
						int[] nEvents = getIncidentData(oSnappedEvts, oRec.m_oWay, oRec.m_oDownstreams, lQueryTime);
						oInput.m_nIncidentOnLink = nEvents[0];
						oInput.m_nIncidentDownstream = nEvents[1];
						
						if (oInput.m_nIncidentOnLink == 0 && nLanesClosed > 0)
							oInput.m_nIncidentOnLink = 1;
						if (oInput.m_nIncidentDownstream == 0 && nLanesClosedDownstream > 0)
							oInput.m_nIncidentDownstream = 1;
						oInput.m_nWorkzoneOnLink = nEvents[2];
						oInput.m_nWorkzoneDownstream = nEvents[3];
						if (nEvents[4] == Events.ALLLANES)
							oInput.m_nLanesClosedOnLink = oInput.m_nLanes;
						else if (nEvents[4] == Events.HALFLANES)
							oInput.m_nLanesClosedOnLink = oInput.m_nLanes / 2;
						else
							oInput.m_nLanesClosedOnLink = nEvents[4];
						
						if (nEvents[5] == Events.ALLLANES)
							oInput.m_nLanesClosedDownstream = oInput.m_nLanes;
						else if (nEvents[5] == Events.HALFLANES)
							oInput.m_nLanesClosedDownstream = oInput.m_nLanes / 2;
						else
							oInput.m_nLanesClosedDownstream = nEvents[5];
							
						oInput.m_nLanesClosedDownstream = Math.max(oInput.m_nLanesClosedDownstream, nLanesClosedDownstream);
						oInput.m_nLanesClosedOnLink = Math.max(oInput.m_nLanesClosedOnLink, nLanesClosed);
						if (oInput.m_nLanesClosedOnLink > oRec.m_oMetadata.m_nLanes)
							oInput.m_nLanesClosedOnLink = oRec.m_oMetadata.m_nLanes;
						oRTRecords.add(oInput);
					}
					++nForecastHour;
					lQueryTime = lQueryEnd;
				}
				Introsort.usort(oRTRecords);
				Path oOneshotInput = Paths.get(String.format(m_sDirectory + m_sOneshotFf, m_nX, m_nY));
				Files.createDirectories(oOneshotInput.getParent(), FileUtil.DIRPERS);
				m_sDirectory = oOneshotInput.getParent().toString() + "/";
				SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
				oSdf.setTimeZone(oCal.getTimeZone());
				try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oOneshotInput, FileUtil.WRITE, FileUtil.FILEPERS), StandardCharsets.UTF_8)))
				{
					oOut.append(RealTimeRecord.HEADER);
					for (RealTimeRecord oRTRecord : oRTRecords)
					{
						oOut.append(oRTRecord.format(oSdf)).append('\n');
					}
				}
				m_lRuntime += 300000;
			}
			catch (Exception oEx)
			{
				m_oLogger.error(oEx, oEx);
			}
		}
		
		
		private boolean createRealTimeInputFiles()
		{
			boolean bHasDataToProcess = false;
			try
			{
				long lQueryTime = m_lRuntime - 86400000;
				double[] dBounds = new double[4];
				m_oM.lonLatBounds(m_nX, m_nY, m_oRR.getZoom(), dBounds);

				int[] nTileBb = new int[]{GeoUtil.toIntDeg(dBounds[0]), GeoUtil.toIntDeg(dBounds[1]), GeoUtil.toIntDeg(dBounds[2]), GeoUtil.toIntDeg(dBounds[3])};
				ArrayList<ResourceRecord> oRRs = Directory.getResourcesByObsType(ObsType.SPDLNK);
				ArrayList<Integer> oContribSources = new ArrayList(oRRs.size() * 2);
				for (ResourceRecord oRR : oRRs)
				{
					if (!isMLPContrib(oRR.getContribId()))
					{
						oContribSources.add(oRR.getContribId());
						oContribSources.add(oRR.getSourceId());
					}
				}
				int[] nContribSources = new int[oContribSources.size()];
				for (int nIndex = 0; nIndex < nContribSources.length; nIndex++)
					nContribSources[nIndex] = oContribSources.get(nIndex);

				TileObsView oOV = (TileObsView)Directory.getInstance().lookup("ObsView");
				ObsList oTair = oOV.getPreferedData(ObsType.TAIR, nTileBb, lQueryTime, lQueryTime + 60000, m_lRuntime);
				ObsList oVis = oOV.getPreferedData(ObsType.VIS, nTileBb, lQueryTime, lQueryTime + 60000, m_lRuntime);
				ObsList oSpdwnd = oOV.getPreferedData(ObsType.SPDWND, nTileBb, lQueryTime, lQueryTime + 60000, m_lRuntime);
				Units oUnits = Units.getInstance();
				Units.UnitConv oTairConv = oUnits.getConversion(ObsType.getUnits(ObsType.TAIR, true), "F");
				Units.UnitConv oVisConv = oUnits.getConversion(ObsType.getUnits(ObsType.VIS, true), "ft");
				Units.UnitConv oSpdwndConv = oUnits.getConversion(ObsType.getUnits(ObsType.SPDWND, true), "mph");
				Units.UnitConv oSpdlnkConv = oUnits.getConversion(ObsType.getUnits(ObsType.SPDLNK, true), "mph");
				Comparator<WaySnapInfo> oCompById = (WaySnapInfo o1, WaySnapInfo o2) -> Id.COMPARATOR.compare(o1.m_oWay.m_oId, o2.m_oWay.m_oId);
				Comparator<WaySnapInfo> oCompByDist = (WaySnapInfo o1, WaySnapInfo o2) -> Integer.compare(o1.m_nSqDist, o2.m_nSqDist);
				m_oTz = m_oWayNetworks.getTimeZone(nTileBb[0], nTileBb[1]);
				GregorianCalendar oCal = new GregorianCalendar(m_oTz);
				ArrayList<RealTimeRecord> oRTRecords = new ArrayList(m_oSessionRecords.size() * 288);
				double dIncident = ObsType.lookup(ObsType.EVT, "incident");
				double dWorkzone = ObsType.lookup(ObsType.EVT, "workzone");
				double dFloodedRoad = ObsType.lookup(ObsType.EVT, "flooded-road");
				double dFloodedStpvt = ObsType.lookup(ObsType.STPVT, "flooded");
				while (lQueryTime < m_lRuntime + 7200000)
				{
					ObsList oEvt = oOV.getData(ObsType.EVT, lQueryTime, lQueryTime + 3600000, nTileBb[1], nTileBb[3], nTileBb[0], nTileBb[2], m_lRuntime);
					ObsList oSnappedEvts = new ObsList(oEvt.size());
					int nEvtIndex = oEvt.size();
					while (nEvtIndex-- > 0)
					{
						Obs oEvent = oEvt.get(nEvtIndex);
						if (oEvent.m_yGeoType == Obs.POINT && (oEvent.m_dValue == dIncident || oEvent.m_dValue == dWorkzone || oEvent.m_dValue == dFloodedRoad))
						{
							double dHdg = Double.NaN;
							int nSegLimit = 1;
							if (oEvent.m_dValue != dFloodedRoad)
							{
								String sDir = oEvent.m_sStrings[4];
								if (sDir.equalsIgnoreCase("e"))
									dHdg = 0;
								else if (sDir.equalsIgnoreCase("n"))
									dHdg = Math.PI / 2;
								else if (sDir.equalsIgnoreCase("w"))
									dHdg = Math.PI;
								else if (sDir.equalsIgnoreCase("s"))
									dHdg = 3 * Math.PI / 2;
								else if (sDir.equalsIgnoreCase("b") || sDir.equalsIgnoreCase("a")) // all or both
									nSegLimit = 2; // allow snapping to two roadway segments if multiple directions
								else if (sDir.equalsIgnoreCase("ne"))
									dHdg = Math.PI / 4;
								else if (sDir.equalsIgnoreCase("nw"))
									dHdg = 3 * Math.PI / 4;
								else if (sDir.equalsIgnoreCase("se"))
									dHdg = 7 * Math.PI / 4;
								else if (sDir.equalsIgnoreCase("sw"))
									dHdg = 5 * Math.PI / 4;
								else
									dHdg = Double.NaN;
							}

							ArrayList<WaySnapInfo> oSnaps = m_oWayNetworks.getSnappedWays(10000, oEvent.m_oGeoArray[1], oEvent.m_oGeoArray[2], dHdg, Math.PI / 4, oCompById);
							if (oSnaps.isEmpty())
								continue;
							Introsort.usort(oSnaps, oCompByDist); // sort by distances
							oEvent.m_oObjId = oSnaps.get(0).m_oWay.m_oId;
							oSnappedEvts.add(oEvent);
							if (oSnaps.size() > 1 && nSegLimit == 2)
							{
								Obs oNewEvent = new Obs(oEvent);
								oNewEvent.m_oObjId = oSnaps.get(1).m_oWay.m_oId;
								oSnappedEvts.add(oNewEvent);
							}
						}
					}
					for (MLPSessionRecord oRec : m_oSessionRecords)
					{
						oRec.m_dTair = getValue(oTair, oRec.m_nQueryGeo);
						if (Double.isFinite(oRec.m_dTair))
							oRec.m_dTair = Math.round(oTairConv.convert(oRec.m_dTair));
						oRec.m_dVis = getValue(oVis, oRec.m_nQueryGeo);
						if (Double.isFinite(oRec.m_dVis))
							oRec.m_dVis = Math.round(oVisConv.convert(oRec.m_dVis));
						oRec.m_dSpdwnd = getValue(oSpdwnd, oRec.m_nQueryGeo);
						if (Double.isFinite(oRec.m_dSpdwnd))
							oRec.m_dSpdwnd = Math.round(oSpdwndConv.convert(oRec.m_dSpdwnd));
					}
					for (int nTimeIndex = 0; nTimeIndex < 12; nTimeIndex++) // 12 5 minute intervals in an hour
					{
						oCal.setTimeInMillis(lQueryTime);
						long lQueryEnd = lQueryTime + 300000;
						ObsList oRtepc = oOV.getPreferedData(ObsType.RTEPC, nTileBb, lQueryTime, lQueryTime + 60000, m_lRuntime);
						ObsList oSpds;
						if (lQueryTime >= m_lRuntime)
							oSpds = new ObsList(0);
						else
							oSpds = oOV.getData(ObsType.SPDLNK, lQueryTime, lQueryEnd, nTileBb[1], nTileBb[3], nTileBb[0], nTileBb[2], m_lRuntime, nContribSources);
						ObsList oStpvt = oOV.getPreferedData(ObsType.STPVT, nTileBb, lQueryTime, lQueryTime + 60000, m_lRefTime);
						int nStpvtIndex = oStpvt.size();
						while (nStpvtIndex-- > 0)
						{
							Obs oState = oStpvt.get(nStpvtIndex);
							if (oState.m_dValue != dFloodedStpvt)
								oStpvt.remove(nStpvtIndex);
						}
						
						for (MLPSessionRecord oRec : m_oSessionRecords)
						{
							RealTimeRecord oInput = new RealTimeRecord();
							oInput.m_sId = oRec.m_oMetadata.m_sId;
							double dRate = getValue(oRtepc, oRec.m_nQueryGeo);
							double dSpeed = getValue(oSpds, oRec.m_nQueryGeo);
							boolean bFloodedStpvt = Double.isFinite(getValue(oStpvt, oRec.m_nQueryGeo));
							
							oInput.m_lTimestamp = lQueryTime;
							oInput.m_nContraflow = -1;
							oInput.m_nCurve = oRec.m_oMetadata.m_nCurve;
							oInput.m_nDirection = oRec.m_oMetadata.m_nDirection;
							oInput.m_nHOV = oRec.m_oMetadata.m_nHOV;
							oInput.m_sSpeedLimit = oRec.m_oMetadata.m_sSpdLimit;
							oInput.m_nDayOfWeek = getDayOfWeek(oCal);
							oInput.m_nLanes = oRec.m_oMetadata.m_nLanes;
							oInput.m_nOffRamps = oRec.m_oMetadata.m_nOffRamps;
							oInput.m_nOnRamps = oRec.m_oMetadata.m_nOnRamps;
							oInput.m_nPavementCondition = oRec.m_oMetadata.m_nPavementCondition;
							oInput.m_nSpecialEvents = oRec.m_oMetadata.m_nSpecialEvents;
							
							oInput.m_sRoad = oRec.m_oMetadata.m_sRoad;
							oInput.m_nPrecipitation = getPrecip(dRate, oRec.m_dVis, oRec.m_dTair);
							oInput.m_dSpeed = oSpdlnkConv.convert(dSpeed);
							oInput.m_nTemperature = Double.isFinite(oRec.m_dTair) ? (int)oRec.m_dTair : 55;
							oInput.m_nTimeOfDay = getTimeOfDay(oCal);
							oInput.m_nVisibility = Double.isFinite(oRec.m_dVis) ? getVisibility(oRec.m_dVis) : 1;
							oInput.m_nWindSpeed = Double.isFinite(oRec.m_dSpdwnd) ? (int)oRec.m_dSpdwnd : 5;
							int[] nEvents = getIncidentData(oSnappedEvts, oRec.m_oWay, oRec.m_oDownstreams, lQueryTime);
							oInput.m_nIncidentOnLink = nEvents[0];
							oInput.m_nIncidentDownstream = nEvents[1];
							oInput.m_nWorkzoneOnLink = nEvents[2];
							oInput.m_nWorkzoneDownstream = nEvents[3];
							if (nEvents[4] == Events.ALLLANES)
								oInput.m_nLanesClosedOnLink = oInput.m_nLanes;
							else if (nEvents[4] == Events.HALFLANES)
								oInput.m_nLanesClosedOnLink = oInput.m_nLanes / 2;
							else
								oInput.m_nLanesClosedOnLink = nEvents[4];

							if (nEvents[5] == Events.ALLLANES)
								oInput.m_nLanesClosedDownstream = oInput.m_nLanes;
							else if (nEvents[5] == Events.HALFLANES)
								oInput.m_nLanesClosedDownstream = oInput.m_nLanes / 2;
							else
								oInput.m_nLanesClosedDownstream = nEvents[5];
							if (oInput.m_nLanesClosedOnLink > oInput.m_nLanes)
								oInput.m_nLanesClosedOnLink = oInput.m_nLanes;
							if (bFloodedStpvt)
							{
								oInput.m_nLanesClosedOnLink = oInput.m_nLanes;
								oInput.m_nIncidentOnLink = 1;
							}
							oRTRecords.add(oInput);
						}
						lQueryTime = lQueryEnd;
						if (lQueryTime % 3600000 == 0) // new hour, get updated weather
						{
							oTair = oOV.getPreferedData(ObsType.TAIR, nTileBb, lQueryTime, lQueryTime + 60000, m_lRuntime);
							oVis = oOV.getPreferedData(ObsType.VIS, nTileBb, lQueryTime, lQueryTime + 60000, m_lRuntime);
							oSpdwnd = oOV.getPreferedData(ObsType.SPDWND, nTileBb, lQueryTime, lQueryTime + 60000, m_lRuntime);
						}
					}
				}
				Introsort.usort(oRTRecords);
				Path oRTInput = Paths.get(String.format(m_sTempPath + m_sRTInputFf, Long.toString(m_lRuntime), m_sFunction, m_nX, m_nY));
				Files.createDirectories(oRTInput.getParent(), FileUtil.DIRPERS);
				m_sDirectory = oRTInput.getParent().toString() + "/";
				SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
				oSdf.setTimeZone(oCal.getTimeZone());
				try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oRTInput, FileUtil.WRITE, FileUtil.FILEPERS), StandardCharsets.UTF_8)))
				{
					oOut.append(RealTimeRecord.HEADER);
					String sPrevId = oRTRecords.get(0).m_sId;
					ArrayList<RealTimeRecord> oBuffer = new ArrayList(336); // 26 hours at 5 minute intervals is 336 records for a single id
					int nLast = oRTRecords.size() - 1;
					for (int nRTIndex = 0; nRTIndex <= nLast; nRTIndex++)
					{
						RealTimeRecord oRTRecord = oRTRecords.get(nRTIndex);
						if (sPrevId.compareTo(oRTRecord.m_sId) != 0 || nRTIndex == nLast)
						{
							if (nRTIndex == nLast)
							{
								oBuffer.add(oRTRecord);
							}
							if (interpolate(oBuffer, 288, m_nInterpolateLimit)) // only do the first 288 records because the last 48 are two hours of weather forecast with no speeds
							{
								bHasDataToProcess = true;
								for (RealTimeRecord oRec : oBuffer)
									oOut.append(oRec.format(oSdf)).append('\n');
							}
							oBuffer.clear();
						}
						oBuffer.add(oRTRecord);
						sPrevId = oRTRecord.m_sId;
					}
				}
			}
			catch (Exception oEx)
			{
				m_oLogger.error(oEx, oEx);
			}
			return bHasDataToProcess;
		}

		@Override
		public MLPSession call() throws Exception
		{
			boolean bProcess = true;
			try
			{
				if (m_sFunction.compareTo("rt") == 0)
					bProcess = createRealTimeInputFiles();
				else if (m_sFunction.compareTo("lts") == 0)
					createLongTsInputFiles();
				else if (m_sFunction.compareTo("exos") == 0)
				{
					createOneshotInputFiles();
					Path oLts = Paths.get(String.format(m_sTempPath + m_sExtendedLongTsFf, m_nX, m_nY));
					m_sExtendedLtsDirectory = oLts.getParent().toString() + "/";
					GregorianCalendar oCal = new GregorianCalendar(m_oTz);
					oCal.setTimeInMillis(m_lRuntime);
					oCal.set(Calendar.HOUR_OF_DAY, 0);
					oCal.set(Calendar.MINUTE, 0);
					oCal.set(Calendar.SECOND, 0);
					oCal.set(Calendar.MILLISECOND, 0);
					oCal.add(Calendar.DAY_OF_YEAR, 7);
					long lCutoff = oCal.getTimeInMillis();
					boolean bCreateLts = true;
					if (Files.exists(oLts))
					{
						String sLastLine = Util.getLastLineOfFile(oLts.toString());
						String[] sCols = sLastLine.split(",");
						SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
						oSdf.setTimeZone(m_oTz);
						try
						{
							bCreateLts = oSdf.parse(sCols[1]).getTime() < lCutoff;
						}
						catch (Exception oEx)
						{
						}
					}
					if (bCreateLts)
						createExtendedLongTsInputFiles();
					m_sFunction = "os";
				}
			}
			catch (Exception oEx)
			{
				m_oLogger.error(oEx, oEx);
				bProcess = false;
			}
			
			if (!bProcess)
				return this;
			
			SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
			oSdf.setTimeZone(m_oTz);
			ProcessBuilder oPB = new ProcessBuilder(m_sPythonCmd, m_sPythonScript, m_sPythonLogLevel, m_sFunction, m_sDirectory, oSdf.format(m_lRuntime - 300000), m_sNetworkDir, Integer.toString(m_nForecastOffset), m_sExtendedLtsDirectory);
			oPB.redirectErrorStream(true);
			oPB.directory(new File(String.format("%smlp/", MLP.this.m_sTempPath)));
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
	}
	
	public static <T extends SpeedRecord> boolean interpolate(ArrayList<T> oRecords, int nListLimit, int nInterpolateLimit)
	{
		int nLimit = nListLimit;
		int nCurIndex = 0;
		int nPrevIndex = -1;
		double dPrevVal = Double.NaN;
		while (nCurIndex < nLimit)
		{
			double dCurVal = oRecords.get(nCurIndex++).m_dSpeed;
			if (!Double.isNaN(dCurVal))
			{
				dPrevVal = dCurVal;
				nPrevIndex = nCurIndex - 1;
				continue;
			}

			if (nPrevIndex >= 0) 
			{
				if (nCurIndex < nLimit)
					dCurVal = oRecords.get(nCurIndex++).m_dSpeed;
				while (Double.isNaN(dCurVal) && nCurIndex - nPrevIndex < nInterpolateLimit + 2 && nCurIndex < nLimit)
				{
					dCurVal = oRecords.get(nCurIndex++).m_dSpeed;
				}
				if (Double.isNaN(dCurVal))
				{
					if (nCurIndex == nLimit) // the last speed record is NaN
					{
						if (nCurIndex - nPrevIndex < nInterpolateLimit)
						{
							while (nCurIndex-- > nPrevIndex)
								oRecords.get(nCurIndex).m_dSpeed = dPrevVal;

							return true;
						}
					}
					else
						return false; // not enough valids speed to interpolate
				}

				int nSteps = nCurIndex - nPrevIndex - 1;
				double dRange = dCurVal - dPrevVal;
				double dStep = dRange / nSteps;

				for (int nIntIndex = nPrevIndex + 1; nIntIndex < nCurIndex - 1; nIntIndex++)
				{
					dPrevVal += dStep;
					oRecords.get(nIntIndex).m_dSpeed = dPrevVal;
				}
				
				nPrevIndex = nCurIndex - 1;
				dPrevVal = dCurVal;
			}
			else // first speed is NaN so look ahead to try and find a non NaN value
			{
				for (int nLookAhead = 1; nLookAhead < nInterpolateLimit + 1; nLookAhead++)
				{
					double dVal = oRecords.get(nLookAhead).m_dSpeed;;
					if (!Double.isNaN(dVal))
					{
						while (nLookAhead-- > 1)
							oRecords.get(nLookAhead).m_dSpeed = dVal;

						break;
					}
				}
			}
		}
		
		return nPrevIndex != -1; // if -1 then all values are NaN
	}
	
	
	private class MLPSessionRecord implements Comparable<MLPSessionRecord>
	{
		private OsmWay m_oWay;
		private ArrayList<OsmWay> m_oDownstreams;
		private int[] m_nQueryGeo;
		private MLPMetadata m_oMetadata;
		private double m_dTair;
		private double m_dVis;
		private double m_dSpdwnd;
		private SegmentGroup m_oGroup = null;
		
		MLPSessionRecord(OsmWay oWay, ArrayList<OsmWay> oDownstreams, int[] nQueryGeo, SegmentGroup oGroup, MLPMetadata oMetadata)
		{
			m_oWay = oWay;
			m_oDownstreams = oDownstreams;
			m_nQueryGeo = nQueryGeo;
			m_oGroup = oGroup;
			m_oMetadata = oMetadata;
		}

		@Override
		public int compareTo(MLPSessionRecord o)
		{
			return Id.COMPARATOR.compare(m_oWay.m_oId, o.m_oWay.m_oId);
		}
	}
	
	
	public static double getValue(ObsList oData, int[] nQueryGeo)
	{
		for (Obs oObs : oData)
		{
			if (oObs.spatialMatch(nQueryGeo))
				return oObs.m_dValue;
		}
		
		return Double.NaN;
	}
	
	public static double getAverageValue(ObsList oData, int[] nQueryGeo)
	{
		double dTotal = 0.0;
		int nCount = 0;
		
		for (Obs oObs : oData)
		{
			if (oObs.spatialMatch(nQueryGeo))
			{
				dTotal += oObs.m_dValue;
				++nCount;
			}
		}
		
		if (nCount == 0)
			return Double.NaN;
		
		return dTotal / nCount;
	}

	private class MLPTile implements Comparable<MLPTile>, Callable<MLPTile>
	{
		private int m_nX;
		private int m_nY;
		private Mercator m_oM;
		private ResourceRecord m_oRR;
		private ArrayList<double[]> m_oTileInfos = new ArrayList();
		byte[] m_yTileData;
		MLPTile(int nX, int nY)
		{
			m_nX = nX;
			m_nY = nY;
		}
		
		
		
		@Override
		public int compareTo(MLPTile o)
		{
			int nRet = m_nX - o.m_nX;
			if (nRet == 0)
				nRet = m_nY - o.m_nY;
			
			return nRet;
		}

		@Override
		public MLPTile call() throws Exception
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
}
