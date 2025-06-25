/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.web;

import com.github.aelstad.keccakj.fips202.Shake256;
import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.Mercator;
import imrcp.geosrv.osm.OsmWay;
import imrcp.geosrv.WayNetworks;
import imrcp.geosrv.osm.OsmNode;
import imrcp.store.Obs;
import imrcp.store.ObsList;
import imrcp.store.TileObsView;
import imrcp.system.Arrays;
import imrcp.system.Directory;
import imrcp.system.FileUtil;
import imrcp.system.Id;
import imrcp.system.Introsort;
import imrcp.system.ObsType;
import imrcp.system.ResourceRecord;
import imrcp.system.Scheduling;
import imrcp.system.Units;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.file.FileVisitOption;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.function.BiFunction;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 *
 * @author aaron.cherney
 */
public class DashboardServlet extends SecureBaseBlock
{
	private String m_sAlertDataFf;
	private int m_nTimeout;
	private int m_nThreads;
	private int m_nForecastRange;
	private final ArrayList<String[]> m_oAlertStatus = new ArrayList();
	private final ArrayDeque<DashboardAlert> m_oQueue = new ArrayDeque();
	private final ArrayList<String> m_oProcessing = new ArrayList();
	private final ArrayList<String[]> m_oActiveSessions = new ArrayList();
	private final Object FILELOCK = new Object();
	
	private final static Comparator<String[]> ALERTCOMP = (String[] o1, String[] o2) -> o1[0].compareTo(o2[0]);
	private final static String DATEFORMAT = "yyyyMMddHHmmssSSS";
	private final static String STALE = "4";
	private final static String QUEUED = "3";
	private final static String PROCESSING = "2";
	private final static String FULFILLED = "1";
	
	private final static long AMBERCUTOFF = 3600000 * 2;
	private final static long GREENCUTOFF = 3600000 * 12;
	private final static String GREEN = "#0C0";
	private final static String AMBER = "#F90";
	private final static String RED = "#C00";
	
	private final static BiFunction<Double, Double, Boolean> GT = (Double d1, Double d2) -> d1 > d2;
	private final static BiFunction<Double, Double, Boolean> LT = (Double d1, Double d2) -> d1 < d2;
	private final static BiFunction<Double, Double, Boolean> EQ = (Double d1, Double d2) -> GeoUtil.compareTol(d1, d2, 0.0000001) == 0;
	
	
	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_sAlertDataFf = oBlockConfig.optString("datafile", "");
		if (m_sAlertDataFf.startsWith("/"))
			m_sAlertDataFf = m_sAlertDataFf.substring(1);
		m_sAlertDataFf = m_sTempPath + m_sAlertDataFf;
		m_nThreads = oBlockConfig.optInt("threads", 1);
		m_nTimeout = oBlockConfig.optInt("timeout", 600000); // default 10 minute timeout
		m_nForecastRange = oBlockConfig.optInt("forecastrange", 604800000);
	}
	
	
	@Override
	public boolean start()
		throws Exception
	{
		m_oProcessing.trimToSize();
		m_oProcessing.ensureCapacity(m_nThreads);
		Files.createDirectories(Paths.get(m_sAlertDataFf).getParent());
		return true;
	}
	
	
	private ArrayList<String> getAlertIds(Session oSess)
		throws IOException
	{
		Path oDashboardDirectory = Paths.get(getDashboardDirectory(oSess));
		if (!Files.exists(oDashboardDirectory))
			Files.createDirectories(oDashboardDirectory);
		List<Path> oFiles = Files.walk(oDashboardDirectory, 1).filter(oPath -> oPath.toString().endsWith(".dalert")).collect(Collectors.toList());
		ArrayList<String> oIds = new ArrayList(oFiles.size());
		for (Path oFile : oFiles)
		{
			String sFilename = oFile.getFileName().toString();
			oIds.add(sFilename.substring(0, sFilename.lastIndexOf(".dalert")));
		}
		
		return oIds;
	}
	
	
	private DashboardAlert getAlert(Session oSess, String sId)
	{
		try (BufferedReader oIn = Files.newBufferedReader(Paths.get(getDashboardFile(oSess, sId)), StandardCharsets.UTF_8))
		{
			return new DashboardAlert(new JSONObject(new JSONTokener(oIn)));
		}
		catch (IOException oEx)
		{
			m_oLogger.error(oEx, oEx);
			return null;
		}
	}
	
	
	private ArrayList<DashboardAlert> getAlerts(Session oSess)
		throws IOException
	{
		Path oDashboardDirectory = Paths.get(getDashboardDirectory(oSess));
		if (!Files.exists(oDashboardDirectory))
			Files.createDirectories(oDashboardDirectory);
		List<Path> oFiles = Files.walk(oDashboardDirectory, 1).filter(oPath -> oPath.toString().endsWith(".dalert")).collect(Collectors.toList());
		ArrayList<DashboardAlert> oAlerts = new ArrayList(oFiles.size());
		for (Path oFile : oFiles)
		{
			try (BufferedReader oIn = Files.newBufferedReader(oFile, StandardCharsets.UTF_8))
			{
				oAlerts.add(new DashboardAlert(new JSONObject(new JSONTokener(oIn))));
			}
		}
		
		return oAlerts;
	}
	
	
	private void writeDashboardFile(Session oSess, DashboardAlert oAlert)
	{
		Path oDashboardFile = Paths.get(getDashboardFile(oSess, oAlert.m_sId));
		try
		{
			Files.createDirectories(oDashboardFile.getParent(), FileUtil.DIRPERS);
			try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oDashboardFile, FileUtil.WRITE), StandardCharsets.UTF_8)))
			{
				oAlert.toJson().write(oOut);
			}
		}
		catch (IOException oEx)
		{
			m_oLogger.error(oEx, oEx);
		}	
	}
	
	
	public int doData(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oCC)
		throws ServletException, IOException
	{
		JSONObject oResponse = new JSONObject();
		try
		{
			oRes.setContentType("application/json");
			checkSessions(oSession);
			String sAlertId = oReq.getParameter("id");
			if (sAlertId == null)
			{
				oRes.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				oResponse.put("msg", "Bad request");
				return HttpServletResponse.SC_BAD_REQUEST;
			}
			
			Path oDataFile = Paths.get(getGraphDataFile(sAlertId));
			if (!Files.exists(oDataFile))
			{
				oRes.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				oResponse.put("msg", "Bad request");
				return HttpServletResponse.SC_BAD_REQUEST;
			}
			
			try (BufferedReader oIn = Files.newBufferedReader(oDataFile, StandardCharsets.UTF_8))
			{
				oResponse = new JSONObject(new JSONTokener(oIn));
			}
		}
		finally
		{
			try (PrintWriter oOut = oRes.getWriter())
			{
				oResponse.write(oOut);
			}
		}
		return HttpServletResponse.SC_OK;
	}
		
		
	public int doDelete(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oCC)
		throws ServletException, IOException
	{
		JSONObject oResponse = new JSONObject();
		try
		{
			oRes.setContentType("application/json");
			checkSessions(oSession);
			String sAlertId = oReq.getParameter("id");
			synchronized (m_oAlertStatus)
			{
				int nSearch = Collections.binarySearch(m_oAlertStatus, new String[]{sAlertId}, ALERTCOMP);
				if (nSearch >= 0)
				{
					m_oAlertStatus.remove(nSearch);
				}
			}
			
			Files.deleteIfExists(Paths.get(getDashboardFile(oSession, sAlertId)));
			Files.deleteIfExists(Paths.get(getAlertDataFile(sAlertId)));
			Files.deleteIfExists(Paths.get(getGeojsonFeaturesFile(sAlertId)));
			Files.deleteIfExists(Paths.get(getGraphDataFile(sAlertId)));
			oResponse.put("msg", "Success");
		}
		finally
		{
			try (PrintWriter oOut = oRes.getWriter())
			{
				oResponse.write(oOut);
			}
		}
		return HttpServletResponse.SC_OK;
	}
	
	
	private void checkSessions(Session oSession)
	{
		long lCutoff = System.currentTimeMillis() - m_nTimeout;
		SimpleDateFormat oSdf = new SimpleDateFormat(DATEFORMAT);
		ArrayList<String> oRemove = new ArrayList(m_oActiveSessions.size());
		boolean bNew = true;
		synchronized (m_oAlertStatus)
		{
			int nIndex = m_oActiveSessions.size();
			while (nIndex-- > 0)
			{
				String[] sSession = m_oActiveSessions.get(nIndex);
				if (sSession[0].compareTo(oSession.m_sName) == 0)
				{
					sSession[1] = oSdf.format(System.currentTimeMillis());
					bNew = false;
				}
				else
				{
					boolean bRemove = true;
					try
					{
						bRemove = oSdf.parse(sSession[1]).getTime() < lCutoff;
					}
					catch (ParseException oPex)
					{
					}
					finally
					{
						if (bRemove)
						{
							m_oActiveSessions.remove(nIndex);
							oRemove.add(sSession[0]);
						}
					}
				}
			}
			
			if (bNew)
			{
				try
				{
					ArrayList<String> oAlertIds = getAlertIds(oSession);
					m_oActiveSessions.add(new String[] {oSession.m_sName, oSdf.format(System.currentTimeMillis())});
					for (String sAlertId : oAlertIds)
					{
						String[] sAlert;
						int nSearch = Collections.binarySearch(m_oAlertStatus, new String[]{sAlertId}, ALERTCOMP);
						if (nSearch < 0)
						{
							DashboardAlert oAlert = getAlert(oSession, sAlertId);
							sAlert = new String[]{oAlert.m_sId, STALE, oSdf.format(System.currentTimeMillis()), oSession.m_sName, oAlert.m_sLabel, Integer.toString(oAlert.m_oSegmentIds.length)};
							m_oAlertStatus.add(~nSearch, sAlert);
						}
						else
						{
							sAlert = m_oAlertStatus.get(nSearch);
							Path oDataFile = Paths.get(String.format(m_sAlertDataFf, sAlertId));
							boolean bExists = Files.exists(oDataFile);
							if ((sAlert[1].compareTo(FULFILLED) == 0 && !bExists) || (bExists && Files.getLastModifiedTime(oDataFile).toMillis() < lCutoff))
							{
								sAlert[1] = STALE;
								sAlert[2] = oSdf.format(System.currentTimeMillis());
							}
						}
					}
				}
				catch (IOException oEx)
				{
					m_oLogger.error(oEx, oEx);
				}
			}
			
			if (!oRemove.isEmpty())
			{
				Introsort.usort(oRemove);
				nIndex = m_oAlertStatus.size();
				while (nIndex-- > 0)
				{
					if (Collections.binarySearch(oRemove, m_oAlertStatus.get(nIndex)[3]) >= 0)
						m_oAlertStatus.remove(nIndex);
				}
			}
		}

		lCutoff -= m_nTimeout; // subtract another timeout period so we don't delete things that are close to the cutoff - probably don't need to do this but want to ensure files still in use are not deleted
		
		try
		{
			List<Path> oPaths = Files.walk(Paths.get(m_sAlertDataFf).getParent(), FileVisitOption.FOLLOW_LINKS).filter(Files::isRegularFile).filter(oP -> oP.toString().endsWith(".json")).collect(Collectors.toList());
			for (Path oPath : oPaths)
			{
				if (oPath.toString().endsWith("_features.json"))
					continue;
				
				if (Files.getLastModifiedTime(oPath).toMillis() < lCutoff)
				{
					synchronized (FILELOCK)
					{
						Files.deleteIfExists(oPath);
					}
				}
			}
		}
		catch (IOException oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
	
	
	public int doStatus(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oCC)
	   throws ServletException, IOException
	{
		JSONObject oResponse = new JSONObject();
		oRes.setContentType("application/json");
		checkSessions(oSession); // previously not in the active sessions, need to load into memory
		long lCutoff = System.currentTimeMillis() - m_nTimeout;
		SimpleDateFormat oSdf = new SimpleDateFormat(DATEFORMAT);
		synchronized (m_oAlertStatus)
		{
			for (String[] sAlert : m_oAlertStatus)
			{
				if (sAlert[3].compareTo(oSession.m_sName) == 0)
				{
					String sAlertId = sAlert[0];
					Path oDataFile = Paths.get(String.format(m_sAlertDataFf, sAlertId));
					boolean bExists = Files.exists(oDataFile);
					if ((sAlert[1].compareTo(FULFILLED) == 0 && !bExists) || (bExists && Files.getLastModifiedTime(oDataFile).toMillis() < lCutoff))
					{
						sAlert[1] = STALE;
						sAlert[2] = oSdf.format(System.currentTimeMillis());
					}
					JSONObject oStatus = new JSONObject();
					oStatus.put("status", Integer.parseInt(sAlert[1]));
					oStatus.put("name", sAlert[4]);
					oStatus.put("updated", sAlert[2]);
					oStatus.put("total", sAlert[5]);
					oResponse.put(sAlert[0], oStatus);
				}
			}
		}

		try (PrintWriter oOut = oRes.getWriter())
		{
			oResponse.write(oOut);
		}
		
		return HttpServletResponse.SC_OK;
	}
	
	
	public int doGeojson(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oCC)
	   throws ServletException, IOException
	{
		JSONObject oResponse = new JSONObject();
		try
		{
			oRes.setContentType("application/json");
			checkSessions(oSession);
			String sAlertId = oReq.getParameter("id");
			if (sAlertId == null)
			{
				oRes.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				oResponse.put("msg", "Bad request");
				return HttpServletResponse.SC_BAD_REQUEST;
			}
			
			oResponse.put("id", sAlertId);
			Path oGeojsonFeatures = Paths.get(getGeojsonFeaturesFile(sAlertId));
			try (BufferedReader oIn = Files.newBufferedReader(oGeojsonFeatures, StandardCharsets.UTF_8))
			{
				oResponse.put("features", new JSONArray(new JSONTokener(oIn)));
			}
		}
		finally
		{
			try (PrintWriter oOut = oRes.getWriter())
			{
				oResponse.write(oOut);
			}
		}
		
		return HttpServletResponse.SC_OK;
	}
	
	
	public int doQueue(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oCC)
	   throws ServletException, IOException
	{
		JSONObject oResponse = new JSONObject();
		try
		{
			oRes.setContentType("application/json");
			checkSessions(oSession);
			String sAlertId = oReq.getParameter("id");
			if (sAlertId == null)
			{
				oRes.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				oResponse.put("msg", "Bad request");
				return HttpServletResponse.SC_BAD_REQUEST;
			}
			queueAlerts(oSession, sAlertId);
			oResponse.put("msg", "Successfully queued");
		}
		finally
		{
			try (PrintWriter oOut = oRes.getWriter())
			{
				oResponse.write(oOut);
			}
		}
		
		return HttpServletResponse.SC_OK;
	}
	
	
	public int doFulfill(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oCC)
	   throws ServletException, IOException
	{
		JSONObject oResponse = new JSONObject();
		try
		{
			oRes.setContentType("application/json");
			checkSessions(oSession);
			String sAlertId = oReq.getParameter("id");
			if (sAlertId == null)
			{
				oRes.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				oResponse.put("msg", "Bad request");
				return HttpServletResponse.SC_BAD_REQUEST;
			}
			
			Path oDataFile = Paths.get(getAlertDataFile(sAlertId));
			if (!Files.exists(oDataFile))
			{
				oRes.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				oResponse.put("msg", "Bad request");
				return HttpServletResponse.SC_BAD_REQUEST;
			}
			
			try (BufferedReader oIn = Files.newBufferedReader(oDataFile, StandardCharsets.UTF_8))
			{
				oResponse = new JSONObject(new JSONTokener(oIn));
			}
		}
		finally
		{
			try (PrintWriter oOut = oRes.getWriter())
			{
				oResponse.write(oOut);
			}
		}
		
		return HttpServletResponse.SC_OK;
	}
	
	public int doAddAlert(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oCC)
	   throws ServletException, IOException
	{
		JSONObject oResponse = new JSONObject();
		try
		{
			oRes.setContentType("application/json");
			checkSessions(oSession);
			WayNetworks oWays = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
			String sLabel = oReq.getParameter("label");
			String sIdCsv = oReq.getParameter("ids");
			String sValueCsv = oReq.getParameter("vals");
			String sObstypeCsv = oReq.getParameter("obstypes");
			String sCompCsv = oReq.getParameter("comps");
			String sLogic = oReq.getParameter("logic");
			String sCoordinates = oReq.getParameter("coords");

			if (sLabel == null || sIdCsv == null || sValueCsv == null || sObstypeCsv == null ||
				sCompCsv == null || sLogic == null || sCoordinates == null)
			{
				oRes.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				oResponse.put("msg", "Bad request");
				return HttpServletResponse.SC_BAD_REQUEST;
			}
			DashboardAlert oAlert = new DashboardAlert();
			oAlert.m_sLabel = sLabel;
			oAlert.m_sUser = oSession.m_sName;
			String[] sIds = sIdCsv.split(",");
			int[] nBB = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
			if (sIds.length == 1 && sIds[0].isEmpty()) // region selected
			{
				String[] sCoords = sCoordinates.split(",");
				int[] nPolygon = Arrays.newIntArray(sCoords.length + 6);
				nPolygon = Arrays.add(nPolygon, 1); // 1 ring
				nPolygon = Arrays.add(nPolygon, 0); // start at 0 points
				nPolygon = Arrays.add(nPolygon, nBB); // add bounding box
				for (int nIndex = 0; nIndex < sCoords.length;)
				{
					int nLon = GeoUtil.toIntDeg(Double.parseDouble(sCoords[nIndex++]));
					int nLat = GeoUtil.toIntDeg(Double.parseDouble(sCoords[nIndex++]));
					nPolygon = Arrays.addAndUpdate(nPolygon, nLon, nLat, 3);
					++nPolygon[2];
				}

				nBB[0] = nPolygon[3];
				nBB[1] = nPolygon[4];
				nBB[2] = nPolygon[5];
				nBB[3] = nPolygon[6];

				if (nPolygon[nPolygon[0] - 2] == nPolygon[7] && nPolygon[nPolygon[0] - 1] == nPolygon[8]) // geo algorithms except an open polygon
				{
					nPolygon[2] -= 1;
					nPolygon[0] -= 2;
				}
				
				oAlert.m_nGeometry = nPolygon;
				ArrayList<OsmWay> oWayList = new ArrayList();
				oWays.getWays(oWayList, 0, nBB[0], nBB[1], nBB[2], nBB[3]);
				int nWayIndex = oWayList.size();
				int[] nPolyline = Arrays.newIntArray();
				while (nWayIndex-- > 0)
				{
					OsmWay oWay = oWayList.get(nWayIndex);
					nPolyline[0] = 5;
					nPolyline[1] = Integer.MAX_VALUE;
					nPolyline[2] = Integer.MAX_VALUE;
					nPolyline[3] = Integer.MIN_VALUE;
					nPolyline[4]= Integer.MIN_VALUE;
					for (OsmNode oNode : oWay.m_oNodes)
					{
						nPolyline = Arrays.addAndUpdate(nPolyline, oNode.m_nLon, oNode.m_nLat, 1);
					}

					if (GeoUtil.isInsideRingAndHoles(nPolygon, Obs.LINESTRING, nPolyline))
					{
						if (oWay.m_nMinLon < nBB[0])
							nBB[0] = oWay.m_nMinLon;
						if (oWay.m_nMinLat < nBB[1])
							nBB[1] = oWay.m_nMinLat;
						if (oWay.m_nMaxLon > nBB[2])
							nBB[2] = oWay.m_nMaxLon;
						if (oWay.m_nMaxLat > nBB[3])
							nBB[3] = oWay.m_nMaxLat;
					}
					else
					{
						oWayList.remove(nWayIndex);
					}
				}
				oAlert.m_oSegmentIds = new Id[oWayList.size()];
				for (int nIndex = 0; nIndex < oWayList.size(); nIndex++)
					oAlert.m_oSegmentIds[nIndex] = oWayList.get(nIndex).m_oId;
			}
			else // set of segments selected
			{
				oAlert.m_oSegmentIds = new Id[sIds.length];
				for (int nIndex = 0; nIndex < sIds.length; nIndex++)
				{
					Id oId = new Id(sIds[nIndex]);
					oAlert.m_oSegmentIds[nIndex] = oId;
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
				}
			}
			java.util.Arrays.sort(oAlert.m_oSegmentIds, Id.COMPARATOR);

			oAlert.m_oBB = nBB;
			String[] sValues = sValueCsv.split(",");
			String[] sObstypes = sObstypeCsv.split(",");
			String[] sComps = sCompCsv.split(",");
			oAlert.m_nObsTypes = new int[sObstypes.length];
			oAlert.m_dValues = new double[sObstypes.length];
			oAlert.m_sComps = new String[sObstypes.length];
			for (int nIndex = 0; nIndex < sObstypes.length; nIndex++)
			{
				oAlert.m_nObsTypes[nIndex] = Integer.parseInt(sObstypes[nIndex]);
				oAlert.m_dValues[nIndex] = Double.parseDouble(sValues[nIndex]);
				oAlert.m_sComps[nIndex] = sComps[nIndex];
			}
			oAlert.setComps();

			oAlert.m_bAnd = sLogic.toLowerCase().compareTo("and") == 0;
			oAlert.generateId();
			String[] sNew = new String[]{oAlert.m_sId, STALE, new SimpleDateFormat(DATEFORMAT).format(System.currentTimeMillis()), oSession.m_sName, oAlert.m_sLabel, Integer.toString(oAlert.m_oSegmentIds.length)};
			synchronized (m_oAlertStatus)
			{
				int nSearch = Collections.binarySearch(m_oAlertStatus, sNew, ALERTCOMP);
				if (nSearch < 0)
				{
					m_oAlertStatus.add(~nSearch, sNew);
				}
				else
				{
					oResponse.put("msg", "Alert already exists");
					return HttpServletResponse.SC_OK;
				}
			}

			writeDashboardFile(oSession, oAlert);
			queueAlerts(oSession, oAlert.m_sId);
			oResponse.put("msg", "Successfully added new alert");
		}
		catch (Exception oEx)
		{
			oRes.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			oResponse.put("msg", "Failed to add alert");
			m_oLogger.error(oEx, oEx);
			return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
		finally
		{
			try (PrintWriter oOut = oRes.getWriter())
			{
				oResponse.write(oOut);
			}
		}
		
		return HttpServletResponse.SC_OK;
	}
	
	
	public int doEditAlert(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oCC)
	   throws ServletException, IOException
	{
		JSONObject oResponse = new JSONObject();
		try
		{
			oRes.setContentType("application/json");
			checkSessions(oSession);
			String sLabel = oReq.getParameter("label");
			String sValueCsv = oReq.getParameter("vals");
			String sObstypeCsv = oReq.getParameter("obstypes");
			String sCompCsv = oReq.getParameter("comps");
			String sLogic = oReq.getParameter("logic");
			String sCurrentId = oReq.getParameter("currentid");
			if (sLabel == null  || sValueCsv == null || sObstypeCsv == null ||
				sCompCsv == null || sLogic == null || sCurrentId == null)
			{
				oRes.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				oResponse.put("msg", "Bad request");
				return HttpServletResponse.SC_BAD_REQUEST;
			}
			JSONObject oJsonAlert;
			try (BufferedReader oIn = Files.newBufferedReader(Paths.get(getDashboardFile(oSession, sCurrentId))))
			{
				oJsonAlert = new JSONObject(new JSONTokener(oIn));
			}
			DashboardAlert oCurrentAlert = new DashboardAlert(oJsonAlert);
			
			DashboardAlert oAlert = new DashboardAlert();
			oAlert.m_sLabel = sLabel;
			oAlert.m_sUser = oSession.m_sName;
			oAlert.m_nGeometry = oCurrentAlert.m_nGeometry;
			oAlert.m_oBB = oCurrentAlert.m_oBB;
			oAlert.m_oSegmentIds = oCurrentAlert.m_oSegmentIds;
			String[] sValues = sValueCsv.split(",");
			String[] sObstypes = sObstypeCsv.split(",");
			String[] sComps = sCompCsv.split(",");
			oAlert.m_nObsTypes = new int[sObstypes.length];
			oAlert.m_dValues = new double[sObstypes.length];
			oAlert.m_sComps = new String[sObstypes.length];
			for (int nIndex = 0; nIndex < sObstypes.length; nIndex++)
			{
				oAlert.m_nObsTypes[nIndex] = Integer.parseInt(sObstypes[nIndex]);
				oAlert.m_dValues[nIndex] = Double.parseDouble(sValues[nIndex]);
				oAlert.m_sComps[nIndex] = sComps[nIndex];
			}
			oAlert.setComps();

			oAlert.m_bAnd = sLogic.toLowerCase().compareTo("and") == 0;
			oAlert.generateId();
			String[] sNew = new String[]{oAlert.m_sId, STALE, new SimpleDateFormat(DATEFORMAT).format(System.currentTimeMillis()), oSession.m_sName, oAlert.m_sLabel, Integer.toString(oAlert.m_oSegmentIds.length)};
			synchronized (m_oAlertStatus)
			{
				writeDashboardFile(oSession, oAlert);
				int nSearch = Collections.binarySearch(m_oAlertStatus, sNew, ALERTCOMP);
				if (nSearch < 0)
				{
					m_oAlertStatus.add(~nSearch, sNew);
					Files.deleteIfExists(Paths.get(getDashboardFile(oSession, sCurrentId)));
					Files.deleteIfExists(Paths.get(getAlertDataFile(sCurrentId)));
					Files.deleteIfExists(Paths.get(getGeojsonFeaturesFile(sCurrentId)));
					Files.deleteIfExists(Paths.get(getGraphDataFile(sCurrentId)));
					nSearch = Collections.binarySearch(m_oAlertStatus, new String[]{sCurrentId}, ALERTCOMP);
					if (nSearch >= 0)
						m_oAlertStatus.remove(nSearch);
					queueAlerts(oSession, oAlert.m_sId);
				}
				else
				{
					sNew[1] = m_oAlertStatus.get(nSearch)[1];
					m_oAlertStatus.set(nSearch, sNew);
				}
			}

			
			oResponse.put("msg", "Successfully edited alert");
		}
		catch (Exception oEx)
		{
			oRes.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			oResponse.put("msg", "Failed to save alert");
			m_oLogger.error(oEx, oEx);
			return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
		finally
		{
			try (PrintWriter oOut = oRes.getWriter())
			{
				oResponse.write(oOut);
			}
		}
		
		return HttpServletResponse.SC_OK;
	}
	
	private void queueAlerts(Session oSess, String sId)
	{
		synchronized (m_oAlertStatus)
		{
			SimpleDateFormat oSdf = new SimpleDateFormat(DATEFORMAT);
			
			int nSearch = Collections.binarySearch(m_oAlertStatus, new String[]{sId}, ALERTCOMP);
			if (nSearch < 0)
				return;

			try
			{
				String[] sAlert = m_oAlertStatus.get(nSearch);

				long lTime = oSdf.parse(sAlert[2]).getTime();
				if (sAlert[1].compareTo(QUEUED) == 0 || sAlert[1].compareTo(PROCESSING) == 0 || // don't queue something in the queue or processing
					(sAlert[1].compareTo(FULFILLED) == 0 && lTime + m_nTimeout > System.currentTimeMillis())) // don't queue something that has been fulfilled and has not timed out
					return;

				JSONObject oJsonAlert;
				try (BufferedReader oIn = Files.newBufferedReader(Paths.get(getDashboardFile(oSess, sId))))
				{
					oJsonAlert = new JSONObject(new JSONTokener(oIn));
				}
				DashboardAlert oAlert = new DashboardAlert(oJsonAlert);
				m_oQueue.addLast(oAlert);
				sAlert[1] = QUEUED;
				sAlert[2] = oSdf.format(System.currentTimeMillis());
				m_oLogger.debug("Queued " + oAlert.m_sId);
				if (m_oProcessing.size() < m_nThreads)
				{
					DashboardAlert oProcess = m_oQueue.pollFirst();
					if (oProcess == null)
						return;
					nSearch = Collections.binarySearch(m_oAlertStatus, new String[]{oProcess.m_sId}, ALERTCOMP);
					if (nSearch < 0)
						return;
					String[] sProcess = m_oAlertStatus.get(nSearch);
					sProcess[1] = PROCESSING;
					sProcess[2] = oSdf.format(System.currentTimeMillis());
					m_oProcessing.add(oProcess.m_sId);
					Scheduling.getInstance().execute(oProcess);
				}
			}
			catch (Exception oEx)
			{
				m_oLogger.error(oEx, oEx);
			}
		}
	}
	
	
	public int doLookup(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oCC)
	   throws ServletException, IOException
	{
		StringBuilder sBuf = new StringBuilder();
		ObsType.getJsonLookupValues(sBuf);
		
		oRes.setContentType("application/json");
		try (PrintWriter oOut = oRes.getWriter())
		{
			oOut.append(sBuf);
		}
		return HttpServletResponse.SC_OK;
	}
	
	
	public int doParameters(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oCC)
	   throws ServletException, IOException
	{
		JSONObject oResponse = new JSONObject();
		try
		{
			oRes.setContentType("application/json");
			checkSessions(oSession);
			String sAlertId = oReq.getParameter("id");
			if (sAlertId == null)
			{
				oRes.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				oResponse.put("msg", "Bad request");
				return HttpServletResponse.SC_BAD_REQUEST;
			}
			JSONObject oJsonAlert;
			try (BufferedReader oIn = Files.newBufferedReader(Paths.get(getDashboardFile(oSession, sAlertId))))
			{
				oJsonAlert = new JSONObject(new JSONTokener(oIn));
			}
			oJsonAlert.put("id", sAlertId);
			oResponse.put("alert", oJsonAlert);
		}
		catch (Exception oEx)
		{
			oRes.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			oResponse.put("msg", "Failed to add alert");
			m_oLogger.error(oEx, oEx);
			return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
		finally
		{
			try (PrintWriter oOut = oRes.getWriter())
			{
				oResponse.write(oOut);
			}
		}
		
		return HttpServletResponse.SC_OK;
	}
	
	
	private static String getDashboardFile(Session oSess, String sId)
	{
		return getDashboardDirectory(oSess) + sId + ".dalert";
	}
	
	
	private static String getDashboardDirectory(Session oSess)
	{
		return String.format(SessMgr.getInstance().m_sUserProfileFf, oSess.m_sName).replace("profile.csv", "dashboard/");
	}
	
	
	private String getAlertDataFile(String sId)
	{
		return String.format(m_sAlertDataFf, sId);
	}
	
	
	private String getGraphDataFile(String sId)
	{
		int nLastPeriod = m_sAlertDataFf.lastIndexOf(".");
		String sFormatString;
		if (nLastPeriod < 0)
			sFormatString = m_sAlertDataFf + "_graph";
		else
			sFormatString = m_sAlertDataFf.substring(0, nLastPeriod) + "_graph" + m_sAlertDataFf.substring(nLastPeriod);
		return String.format(sFormatString, sId);
	}
	
	private String getGeojsonFeaturesFile(String sId)
	{
		int nLastPeriod = m_sAlertDataFf.lastIndexOf(".");
		String sFormatString;
		if (nLastPeriod < 0)
			sFormatString = m_sAlertDataFf + "_features";
		else
			sFormatString = m_sAlertDataFf.substring(0, nLastPeriod) + "_features" + m_sAlertDataFf.substring(nLastPeriod);
		return String.format(sFormatString, sId);
	}

	
	private static String getColor(int nCount, long lTimeDiff)
	{
		if (nCount == 0)
			return GREEN;
		
		if (lTimeDiff < AMBERCUTOFF)
			return RED;
		if (lTimeDiff < GREENCUTOFF)
			return AMBER;
		
		return GREEN;
	}
	
	private class AlertRecord implements Comparable<AlertRecord>
	{
		long m_lTs;
		long m_lEndTs;
		long m_lRefTs;
		double m_dVal;
		boolean m_bTriggered = false;
		OsmWay m_oSegment;

		
		AlertRecord(long lTs, long lEndTs, long lRefTs, double dVal, OsmWay oSegment)
		{
			m_lTs = lTs;
			m_lEndTs = lEndTs;
			m_lRefTs = lRefTs;
			m_dVal = dVal;
			m_oSegment = oSegment;
		}
		
		
		boolean temporalMatch(AlertRecord oOther)
		{
			return m_lTs <= oOther.m_lEndTs && m_lEndTs > oOther.m_lTs;
		}
		

		@Override
		public int compareTo(AlertRecord o)
		{
			return Long.compare(m_lTs, o.m_lTs);
		}
	}
	
	
	private class DashboardAlert implements Comparable<DashboardAlert>, Runnable
	{
		private String m_sId;
		private String m_sLabel;
		private String m_sUser;
		private int[] m_oBB;
		private Id[] m_oSegmentIds;
		private ArrayList<OsmWay> m_oWays = null;
		private int[] m_nObsTypes;
		private double[] m_dValues;
		private String[] m_sComps;
		private BiFunction<Double, Double, Boolean>[] m_oComps;
		private boolean m_bAnd;
		private int[] m_nGeometry = null;
		
		
		private DashboardAlert()
		{
		}
		
		private DashboardAlert(JSONObject oJson)
			throws IOException
		{
			m_oBB = new int[4];
			JSONArray oBB = oJson.getJSONArray("bbox");
			for (int nIndex = 0; nIndex < m_oBB.length; nIndex++)
				m_oBB[nIndex] = oBB.getInt(nIndex);
			
			JSONArray oSegs = oJson.getJSONArray("segs");
			m_oSegmentIds = new Id[oSegs.length()];
			for (int nIndex = 0; nIndex < oSegs.length(); nIndex++)
				m_oSegmentIds[nIndex] = new Id(oSegs.getString(nIndex));
			
			java.util.Arrays.sort(m_oSegmentIds, Id.COMPARATOR);
			JSONArray oObsTypes = oJson.getJSONArray("obstypes");
			JSONArray oValues = oJson.getJSONArray("vals");
			JSONArray oComps = oJson.getJSONArray("comps");
			m_nObsTypes = new int[oObsTypes.length()];
			m_dValues = new double[m_nObsTypes.length];
			m_sComps = new String[m_nObsTypes.length];
			for (int nIndex = 0; nIndex < m_nObsTypes.length; nIndex++)
			{
				m_nObsTypes[nIndex] = oObsTypes.getInt(nIndex);
				m_dValues[nIndex] = oValues.getDouble(nIndex);
				m_sComps[nIndex] = oComps.getString(nIndex);
			}
			
			setComps();
			
			m_bAnd = oJson.getBoolean("and");
			m_sUser = oJson.getString("user");
			m_sLabel = oJson.getString("label");
			if (oJson.has("geometry"))
			{
				JSONArray oGeo = oJson.getJSONArray("geometry");
				int[] nPolygon = Arrays.newIntArray(oGeo.length() + 6);
				nPolygon = Arrays.add(nPolygon, 1); // 1 ring
				nPolygon = Arrays.add(nPolygon, 0); // start at 0 points
				nPolygon = Arrays.add(nPolygon, new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE}); // add bounding box
				for (int nIndex = 0; nIndex < oGeo.length();)
				{
					int nLon = oGeo.getInt(nIndex++);
					int nLat = oGeo.getInt(nIndex++);
					nPolygon = Arrays.addAndUpdate(nPolygon, nLon, nLat, 3);
					++nPolygon[2];
				}
				m_nGeometry = nPolygon;
			}
			
			generateId();
		}
		
		private JSONObject toJson()
		{
			JSONObject oJson = new JSONObject();
			JSONArray oBB = new JSONArray();
			for (int nCoord : m_oBB)
				oBB.put(nCoord);
			oJson.put("bbox", oBB);
			JSONArray oSegs = new JSONArray();
			for (Id oId : m_oSegmentIds)
				oSegs.put(oId.toString());
			
			oJson.put("segs", oSegs);
			JSONArray oObsTypes = new JSONArray();
			JSONArray oValues = new JSONArray();
			JSONArray oComps = new JSONArray();
			for (int nIndex = 0; nIndex < m_nObsTypes.length; nIndex++)
			{
				oObsTypes.put(m_nObsTypes[nIndex]);
				oValues.put(m_dValues[nIndex]);
				oComps.put(m_sComps[nIndex]);
			}
			
			oJson.put("obstypes", oObsTypes);
			oJson.put("vals", oValues);
			oJson.put("comps", oComps);
			oJson.put("and", m_bAnd);
			oJson.put("user", m_sUser);
			oJson.put("label", m_sLabel);
			
			if (m_nGeometry != null)
			{
				JSONArray oGeo = new JSONArray();
				int nPolyPos = 2;
				int nNumPoints = m_nGeometry[nPolyPos];
				nPolyPos += 5; // skip number of points and bounding box
				int nPolyEnd = nPolyPos + nNumPoints * 2;
				while (nPolyPos < nPolyEnd)
					oGeo.put(m_nGeometry[nPolyPos++]);
				
				oJson.put("geometry", oGeo);
			}
			return oJson;
		}
		
		
		private final void setComps()
		{
			m_oComps = new BiFunction[m_sComps.length];
			for (int nIndex = 0; nIndex < m_sComps.length; nIndex++)
			{
				String sComp = m_sComps[nIndex].toLowerCase();
				BiFunction<Double, Double, Boolean> oFunc;
				if (sComp.compareTo("gt") == 0)
					oFunc = GT;
				else if (sComp.compareTo("lt") == 0)
					oFunc = LT;
				else
					oFunc = EQ;
				m_oComps[nIndex] = oFunc;
			}
		}
		
		
		private void setWays()
		{
			if (m_oWays != null)
				return;
			
			WayNetworks oWays = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
			m_oWays = new ArrayList(m_oSegmentIds.length);
			for (int nIndex = 0; nIndex < m_oSegmentIds.length; nIndex++)
				m_oWays.add(oWays.getWayById(m_oSegmentIds[nIndex]));
		}
		
		
		private void createGeojson(Path oFile)
			throws IOException
		{
			if (Files.exists(oFile))
				return;
			
			setWays();
			
			JSONArray oFeatureSet = new JSONArray();
			JSONArray oRoadFeatures = new JSONArray();
			for (OsmWay oWay : m_oWays)
			{
				JSONObject oFeature = new JSONObject();
				oFeature.put("type", "Feature");
				JSONObject oProps = new JSONObject();
				oProps.put("imrcpid", oWay.m_oId.toString());
				JSONObject oGeo = new JSONObject();
				oGeo.put("type", "LineString");
				JSONArray oCoords = new JSONArray();
				for (OsmNode oNode : oWay.m_oNodes)
				{
					JSONArray oPt = new JSONArray();
					oPt.put(GeoUtil.fromIntDeg(oNode.m_nLon));
					oPt.put(GeoUtil.fromIntDeg(oNode.m_nLat));
					oCoords.put(oPt);
				}
				oGeo.put("coordinates", oCoords);
				oFeature.put("geometry", oGeo);
				oFeature.put("properties", oProps);
				oRoadFeatures.put(oFeature);
			}
			oFeatureSet.put(oRoadFeatures);
			
			if (m_nGeometry != null)
			{
				JSONArray oRegionFeatures = new JSONArray();
				JSONObject oFeature = new JSONObject();
				oFeature.put("type", "Feature");
				JSONObject oGeo = new JSONObject();
				oGeo.put("type", "LineString");
				JSONArray oCoords = new JSONArray();
				int nPolyPos = 2;
				int nNumPoints = m_nGeometry[nPolyPos];
				nPolyPos += 5; // skip number of points and bounding box
				int nPolyEnd = nPolyPos + nNumPoints * 2;
				while (nPolyPos < nPolyEnd)
				{
					JSONArray oPt = new JSONArray();
					oPt.put(GeoUtil.fromIntDeg(m_nGeometry[nPolyPos++]));
					oPt.put(GeoUtil.fromIntDeg(m_nGeometry[nPolyPos++]));
					oCoords.put(oPt);
				}
				oCoords.put(oCoords.get(0)); // polygon is open in memory so close it for the geojson
				oGeo.put("coordinates", oCoords);
				oFeature.put("geometry", oGeo);
				oRegionFeatures.put(oFeature);
				oFeatureSet.put(oRegionFeatures);
			}
			
			
			try (BufferedWriter oOut = Files.newBufferedWriter(oFile, StandardCharsets.UTF_8, FileUtil.WRITEOPTS))
			{
				oFeatureSet.write(oOut);
			}
		}
		
		private void generateId()
			throws IOException
		{
			Shake256 oMd = new Shake256();
			try
			(
				DataOutputStream oAbsorb = new DataOutputStream(oMd.getAbsorbStream());
				InputStream oSqueeze = oMd.getSqueezeStream();		   
			)
			{
				oAbsorb.writeUTF(m_sUser);
				for (Id oId : m_oSegmentIds)
				{
					oAbsorb.writeLong(oId.getHighBytes());
					oAbsorb.writeLong(oId.getLowBytes());
				}
				
				for (int nIndex = 0; nIndex < m_nObsTypes.length; nIndex++)
				{
					oAbsorb.writeInt(m_nObsTypes[nIndex]);
					oAbsorb.writeDouble(m_dValues[nIndex]);
					oAbsorb.writeUTF(m_sComps[nIndex]);
				}
				oAbsorb.writeBoolean(m_bAnd);

				byte[] yId = new byte[32];
				oSqueeze.read(yId);

				m_sId = Base64.getUrlEncoder().withoutPadding().encodeToString(yId);
			}
		}

		@Override
		public int compareTo(DashboardAlert o)
		{
			return m_sId.compareTo(o.m_sId);
		}

		@Override
		public void run()
		{
			try
			{
				synchronized (m_oAlertStatus)
				{
					int nSearch = Collections.binarySearch(m_oAlertStatus, new String[]{m_sId}, ALERTCOMP);
					if (nSearch < 0)
						return;
				}
				m_oLogger.debug("Processing " + m_sId);
				Directory oDir = Directory.getInstance();
				TileObsView oObsView = (TileObsView)oDir.lookup("ObsView");
				WayNetworks oWayNetworks = (WayNetworks)oDir.lookup("WayNetworks");
				setWays();
				long lNow = System.currentTimeMillis();
				long lRefTime = lNow / m_nTimeout * m_nTimeout; // floor to the period
				long lObsTime = lRefTime;
				long lEndTime = lRefTime + m_nForecastRange;
				Path oAlertData = Paths.get(getAlertDataFile(m_sId));
				Path oGraphData = Paths.get(getGraphDataFile(m_sId));
				createGeojson(Paths.get(getGeojsonFeaturesFile(m_sId)));
				HashMap<String, ArrayList<AlertRecord>>[] oObsMap = new HashMap[m_nObsTypes.length];
				long[] lEarliest = new long[m_nObsTypes.length];
				for (int nIndex = 0; nIndex < lEarliest.length; nIndex++)
					lEarliest[nIndex] = Long.MAX_VALUE;
				Units oUnits = Units.getInstance();
				ArrayList<OsmWay> oScratchSegs = new ArrayList();
				int[] nPt = Arrays.newIntArray(2);
				nPt = Arrays.add(nPt, 0 , 0);
				for (int nObsIndex = 0; nObsIndex < m_nObsTypes.length; nObsIndex++)
				{
					if (nObsIndex == 1 && m_nObsTypes[0] == m_nObsTypes[1])
					{
						oObsMap[1] = oObsMap[0];
						lEarliest[1] = lEarliest[0];
						break;
					}
					
					lObsTime = lRefTime;
					int nObstype = m_nObsTypes[nObsIndex];
					String sObstype = Integer.toString(nObstype, 36);
					SimpleDateFormat oLogSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
					BiFunction<Double, Double, Boolean> oComp = m_oComps[nObsIndex];
					Double dCompVal = m_dValues[nObsIndex];
					HashMap<String, ArrayList<AlertRecord>> oMap = new HashMap();
					oObsMap[nObsIndex] = oMap;
					String sTo = ObsType.getUnits(nObstype, false);
					String sFrom = ObsType.getUnits(nObstype, true);
					ArrayList<ResourceRecord> oRRs = Directory.getResourcesByObsType(nObstype);
					Introsort.usort(oRRs, ResourceRecord.COMP_BY_PREF);
					int nSnapTol = 0;
					for (int nIndex = 0; nIndex < oRRs.size(); nIndex++)
					{
						int nPPT = (int)Math.pow(2, oRRs.get(nIndex).getTileSize()) - 1;
						int nTemp = (int)Math.round(new Mercator(nPPT).RES[oRRs.get(nIndex).getZoom()] * 100);
						if (nTemp > nSnapTol)
							nSnapTol = nTemp;
					}
					
					long[][] lTimings = new long[oRRs.size()][];
					for (int nRR = 0; nRR < oRRs.size(); nRR++)
					{
						ResourceRecord oRR = oRRs.get(nRR);
						lTimings[nRR] = new long[]{lObsTime, lObsTime + oRR.getRange(), oRR.getRange(), oRR.getMaxFcst()};
					}
					boolean bQuerying = true;
					while (bQuerying)
					{
						ObsList oData = null;
						if (nObstype == ObsType.EVT)
						{
							oData = oObsView.getData(nObstype, lObsTime, lEndTime, m_oBB[1], m_oBB[3], m_oBB[0], m_oBB[2], lRefTime);
							bQuerying = false;
						}
						else
						{
							int[] nContribAndSource = new int[2];
							oData = new ObsList();
							bQuerying = false;
							for (int nIndex = 0; nIndex < oRRs.size(); nIndex++)
							{
								ResourceRecord oTemp = oRRs.get(nIndex);
								nContribAndSource[0] = oTemp.getContribId();
								nContribAndSource[1] = oTemp.getSourceId();
								long[] lTiming = lTimings[nIndex];
								if (lTiming[0] - lRefTime > lTiming[3]) // skip resource records past their maximum forecast
									continue;
//								m_oLogger.debug(String.format("%s %s %s %s", sObstype, Integer.toString(oTemp.getContribId(), 36), oLogSdf.format(lTiming[0]), oLogSdf.format(lTiming[1])));
								ObsList oTempList = oObsView.getData(nObstype, lTiming[0], lTiming[1], m_oBB[1], m_oBB[3], m_oBB[0], m_oBB[2], lRefTime, nContribAndSource);
								bQuerying = true;
								lTiming[0] = lTiming[1];
								lTiming[1] = lTiming[0] + lTiming[2]; // start time up range
								oData.addAll(oTempList);
								if (!oTempList.isEmpty() && oTempList.get(0).m_yGeoType == Obs.POLYGON) // only use the higher priority areal weather observations
								{
									for (int nRest = nIndex + 1; nRest < oRRs.size(); nRest++)
									{
										lTiming = lTimings[nRest];
										lTiming[0] = lTiming[1];
										lTiming[1] = lTiming[0] + lTiming[2]; // start time up range
									}
									break;
								}
							}
						}

						Collections.sort(oData, Obs.g_oCompObsByTime);
						
						for (Obs oObs : oData)
						{
							double dVal = oUnits.convert(sFrom, sTo, oObs.m_dValue);
							oScratchSegs.clear();
							if (oObs.m_yGeoType == Obs.POINT)
							{
								OsmWay oWay = oWayNetworks.getWay(nSnapTol, oObs.m_oGeoArray[1], oObs.m_oGeoArray[2]);
								if (oWay != null)
									oScratchSegs.add(oWay);
							}
							else if (oObs.m_yGeoType == Obs.POLYGON)
							{
								for (OsmWay oWay : m_oWays)
								{
									if (oWay != null)
									{
										nPt[1] = oWay.m_nMidLon;
										nPt[2] = oWay.m_nMidLat;
										if (GeoUtil.isInsideRingAndHoles(oObs.m_oGeoArray, Obs.POINT, nPt))
											oScratchSegs.add(oWay);
									}
								}
							}

							for (OsmWay oWay : oScratchSegs)
							{
								if (java.util.Arrays.binarySearch(m_oSegmentIds, oWay.m_oId, Id.COMPARATOR) >= 0)
								{
									AlertRecord oRec = new AlertRecord(oObs.m_lObsTime1, oObs.m_lObsTime2, oObs.m_lTimeRecv, dVal, oWay);
									ArrayList<AlertRecord> oRecords;
									if (!oMap.containsKey(oWay.m_oId.toString()))
									{
										oRecords = new ArrayList();
										oMap.put(oWay.m_oId.toString(), oRecords);
									}
									else
									{
										oRecords = oMap.get(oWay.m_oId.toString());
									}
									
									if (oComp.apply(dVal, dCompVal))
									{
										if (oRec.m_lTs < lEarliest[nObsIndex])
											lEarliest[nObsIndex] = oRec.m_lTs;
										oRec.m_bTriggered = true;
									}
									int nIndex = Collections.binarySearch(oRecords, oRec);
									if (nIndex < 0)
										oRecords.add(~nIndex, oRec);
									else
									{
										AlertRecord oInList = oRecords.get(nIndex);
										if (oInList.m_lRefTs < oRec.m_lRefTs)
											oRecords.set(nIndex, oRec);
									}
								}
							}
						}
					}
				}
				JSONObject oAlertFileContent = new JSONObject();
				JSONObject oDataFileContent = new JSONObject();
				JSONArray oIdsAffected = new JSONArray();
				int nCount = 0;
				double dMiles = 0.0;
				boolean bRuleTriggered = false;
				for (int nTimeIndex = 0; nTimeIndex < lEarliest.length; nTimeIndex++)
				{
					if (lEarliest[nTimeIndex] <= lRefTime)
						lEarliest[nTimeIndex] = lRefTime;
				}
				
				if (lEarliest.length == 1) // one obstype
				{
					if (lEarliest[0] != Long.MAX_VALUE)
					{
						bRuleTriggered = true;
					}
				}
				else // two obstypes
				{
					if (m_bAnd)
					{
						if (lEarliest[0] != Long.MAX_VALUE && lEarliest[1] != Long.MAX_VALUE)
							bRuleTriggered = true;
					}
					else if (lEarliest[0] != Long.MAX_VALUE || lEarliest[1] != Long.MAX_VALUE)
						bRuleTriggered = true;
				}

				if (bRuleTriggered)
				{
					long lFirstOccurance = Long.MAX_VALUE;
					if (oObsMap.length == 1) // one obstype
					{
						long lCutoff = lEarliest[0];
						for (java.util.Map.Entry<String, ArrayList<AlertRecord>> oEntry : oObsMap[0].entrySet())
						{
							for (AlertRecord oRec : oEntry.getValue())
							{
								if (oRec.m_lTs > lCutoff)
									break;

								if (oRec.m_bTriggered)
								{
									++nCount;
									dMiles += (oRec.m_oSegment.getLengthInM() * 0.0006213712); // convert meters to miles
									oIdsAffected.put(oEntry.getKey());
									lFirstOccurance = oRec.m_lTs;
									break;
								}
							}
						}
					}
					else // two obstypes
					{
						if (m_bAnd) 
						{
							for (java.util.Map.Entry<String, ArrayList<AlertRecord>> oEntry : oObsMap[0].entrySet())
							{
								ArrayList<AlertRecord> oOtherObs = null;
								boolean bFound = false;
								for (AlertRecord oRec : oEntry.getValue())
								{
									if (lFirstOccurance != Long.MAX_VALUE && oRec.m_lTs > lFirstOccurance)
										break;
									if (oRec.m_bTriggered)
									{
										if (oOtherObs == null)
											oOtherObs = oObsMap[1].get(oEntry.getKey());
										for (AlertRecord oOther : oOtherObs)
										{
											if (oRec.temporalMatch(oOther))
											{
												++nCount;
												oIdsAffected.put(oEntry.getKey());
												bFound = true;
												lFirstOccurance = oRec.m_lTs;
												break;
											}
										}
									}
									if (bFound)
										break;
								}
							}
						}
						else // evaluate rules "or"
						{
							int nEarliestIndex = 0;
							if (lEarliest[1] < lEarliest[0])
								nEarliestIndex = 1;
							long lCutoff = lEarliest[nEarliestIndex];
							for (java.util.Map.Entry<String, ArrayList<AlertRecord>> oEntry : oObsMap[nEarliestIndex].entrySet())
							{
								for (AlertRecord oRec : oEntry.getValue())
								{
									if (oRec.m_lTs > lCutoff)
										break;

									if (oRec.m_bTriggered)
									{
										++nCount;
										oIdsAffected.put(oEntry.getKey());
										lFirstOccurance = oRec.m_lTs;
										break;
									}
								}
							}
						}
					}

					oAlertFileContent.put("color", getColor(nCount, lFirstOccurance - lNow));
					oAlertFileContent.put("in", lFirstOccurance);
				}
				else
				{
					oAlertFileContent.put("in", Long.MAX_VALUE);
					oAlertFileContent.put("color", GREEN);
				}

				oAlertFileContent.put("miles", dMiles);
				oAlertFileContent.put("count", nCount);
				oAlertFileContent.put("name", m_sLabel);
				oAlertFileContent.put("total", m_oSegmentIds.length);

				if (m_oSegmentIds.length == 1)
				{
					JSONArray oObstype = new JSONArray();
					JSONArray oLabel = new JSONArray();
					JSONArray oDataArrays = new JSONArray();
					SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
					oSdf.setTimeZone(oWayNetworks.getTimeZone(m_oWays.get(0).m_nMidLon, m_oWays.get(0).m_nMidLat));
					for (int nObsIndex = 0; nObsIndex < m_nObsTypes.length; nObsIndex++)
					{
						if (nObsIndex == 1 && m_nObsTypes[0] == m_nObsTypes[1])
							break;
						oObstype.put(Integer.toString(m_nObsTypes[nObsIndex], 36));
						oLabel.put(ObsType.getLabel(m_nObsTypes[nObsIndex]));
						JSONArray oData = new JSONArray();
						if (oObsMap[nObsIndex].containsKey(m_oSegmentIds[0].toString()))
						{
							ArrayList<AlertRecord> oRecs = oObsMap[nObsIndex].get(m_oSegmentIds[0].toString());
							for (AlertRecord oRec : oRecs)
							{
								JSONArray oRecArray = new JSONArray();
								oRecArray.put(oSdf.format(oRec.m_lTs));
								oRecArray.put(oRec.m_dVal);
								oData.put(oRecArray);
							}
						}
						oDataArrays.put(oData);
					}
					oDataFileContent.put("obstype", oObstype);
					oDataFileContent.put("label", oLabel);
					oDataFileContent.put("data", oDataArrays);
				}
				else
				{
					oDataFileContent.put("ids", oIdsAffected);
				}
				
				synchronized (m_oAlertStatus)
				{
					int nSearch = Collections.binarySearch(m_oAlertStatus, new String[]{m_sId}, ALERTCOMP);
					if (nSearch < 0)
						return;
					try (BufferedWriter oOut = Files.newBufferedWriter(oGraphData, StandardCharsets.UTF_8, FileUtil.WRITEOPTS))
					{
						oDataFileContent.write(oOut);
					}

					try (BufferedWriter oOut = Files.newBufferedWriter(oAlertData, StandardCharsets.UTF_8, FileUtil.WRITEOPTS))
					{
						oAlertFileContent.write(oOut);
					}
				}
			}
			catch (Exception oEx)
			{
				m_oLogger.error(oEx, oEx);
			}
			finally
			{
				m_oLogger.debug("Finally " + m_sId);
				synchronized (m_oAlertStatus)
				{
					SimpleDateFormat oSdf = new SimpleDateFormat(DATEFORMAT);
					int nSearch = m_oProcessing.size();
					while (nSearch-- > 0)
					{
						if (m_oProcessing.get(nSearch).compareTo(m_sId) == 0)
							m_oProcessing.remove(nSearch);
					}
					nSearch = Collections.binarySearch(m_oAlertStatus, new String[]{m_sId}, ALERTCOMP);
					if (nSearch >= 0)
					{
						String[] sAlert = m_oAlertStatus.get(nSearch);
						sAlert[1] = FULFILLED;
						sAlert[2] = oSdf.format(System.currentTimeMillis());
					}
					
					if (m_oProcessing.size() < m_nThreads)
					{
						DashboardAlert oProcess = m_oQueue.pollFirst();
						if (oProcess != null)
						{
							nSearch = Collections.binarySearch(m_oAlertStatus, new String[]{oProcess.m_sId}, ALERTCOMP);
							if (nSearch >= 0)
							{
								String[] sProcess = m_oAlertStatus.get(nSearch);
								sProcess[1] = PROCESSING;
								sProcess[2] = oSdf.format(System.currentTimeMillis());
								m_oProcessing.add(oProcess.m_sId);
								Scheduling.getInstance().execute(oProcess);
							}
						}
					}		
				}
			}
		}
	}
}
