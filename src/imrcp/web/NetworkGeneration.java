/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.web;

import com.github.aelstad.keccakj.fips202.Shake256;
import imrcp.forecast.mlp.MLPHurricane;
import imrcp.system.FileUtil;
import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.Network;
import imrcp.geosrv.osm.OsmNode;
import imrcp.geosrv.osm.OsmBinParser;
import imrcp.geosrv.osm.OsmBz2ToBin;
import imrcp.geosrv.osm.OsmUtil;
import imrcp.geosrv.osm.OsmWay;
import imrcp.geosrv.WayNetworks;
import imrcp.store.Obs;
import imrcp.system.dbf.DbfResultSet;
import imrcp.system.Directory;
import imrcp.system.Id;
import imrcp.system.Introsort;
import imrcp.system.Scheduling;
import imrcp.system.StringPool;
import imrcp.system.shp.ShpReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map.Entry;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.TimeZone;
import org.json.JSONObject;

/**
 * This servlet manages and processes all the necessary data and requests for
 * the IMRCP Road Network Generation tool.
 * @author aaron.cherney
 */
public class NetworkGeneration extends SecureBaseBlock
{
	/**
	 * Path to the US Census shapefile that contains the definitions of US state
	 * boundaries.
	 */
	private static String g_sStateShp;
	
	private static String g_sUSBorderShp;
	
	/**
	 * Queue used to process network creation requests
	 */
	private final ArrayDeque<String> m_oProcessQueue = new ArrayDeque();

	
	/**
	 * Schedule offset from midnight in seconds
	 */
	private int m_nOffset;

	
	/**
	 * Period of execution in seconds
	 */
	private int m_nPeriod;

	
	/**
	 * Format string used generate URLs for downloading OSM data files
	 */
	private String m_sOsmDownload;

	
	/**
	 * Base directory used to save OSM data files
	 */
	private String m_sOsmDir;

	
	/**
	 * Time in milliseconds that OSM data files are considered valid. 
	 */
	private long m_lFileTimeout;

	
	/**
	 * Time in milliseconds to timeout HTTP connections
	 */
	private int m_nConnTimeout;

	
	/**
	 * Format string used to generate IMRCP OSM binary file names
	 */
	private String m_sGeoFileFormat;

	
	/**
	 * Tolerance in decimal degrees scaled to 7 decimal places to determine is
	 * nodes are the "same"
	 */
	private int m_nTol;

	
	/**
	 * Contains the EditLists that are actively being used
	 */
	private final ArrayList<EditList> m_oSessionLists = new ArrayList();

	
	/**
	 * Flag indicating if the network currently being processed should be saved.
	 */
	private boolean m_bSave = true;

	
	private final HashMap<String, ArrayList<int[]>> m_oCachedStates = new HashMap();
	private long m_lClearCache = 0;
	
	
	/**
	 * Sets a schedule to execute on a fixed interval.
	 * @return true if no Exceptions are thrown
	 * @throws Exception 
	 */
	@Override
	public boolean start()
	   throws Exception
	{
		m_nSchedId = Scheduling.getInstance().createSched(this, m_nOffset, m_nPeriod);
		return true;
	}
	
	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		g_sStateShp = oBlockConfig.getString("statefile");
		g_sUSBorderShp = oBlockConfig.getString("usborder");
		m_nOffset = oBlockConfig.optInt("offset", 0);
		m_nPeriod = oBlockConfig.optInt("period", 1800);
		m_sOsmDownload = oBlockConfig.optString("osm", "http://download.geofabrik.de/north-america/us/%s-latest.osm.bz2");
		m_sOsmDir = oBlockConfig.optString("osmdir", "");
		if (m_sOsmDir.startsWith("/"))
			m_sOsmDir = m_sOsmDir.substring(1);
		if (!m_sOsmDir.endsWith("/"))
			m_sOsmDir += "/";
		m_sOsmDir = m_sArchPath + m_sOsmDir;
		m_lFileTimeout = oBlockConfig.optLong("filetimeout", 2592000000L);
		m_nConnTimeout = oBlockConfig.optInt("conntimeout", 60000);
		m_sGeoFileFormat = m_sDataPath + WayNetworks.NETWORKFF;
		m_nTol = oBlockConfig.optInt("tol", 10);
	}
	
	
	/**
	 * Adds roadway segments in the cell of the requested hash index to the response.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @param oSession object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws ServletException
	 */
	public int doHash(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oClient)
	   throws ServletException
	{
		try
		{
			int nHash = Integer.parseInt(oReq.getParameter("hash"));
			int nLat = GeoUtil.toIntDeg(Double.parseDouble(oReq.getParameter("lat")));
			int nLon = GeoUtil.toIntDeg(Double.parseDouble(oReq.getParameter("lon")));
			String sNetworkId = oReq.getParameter("networkid");
			String sFile = null;
			synchronized (m_oCachedStates)
			{
				if (m_lClearCache < System.currentTimeMillis())
				{
					m_oCachedStates.clear();
					m_lClearCache = System.currentTimeMillis() + 3600000;
				}
				for (Entry<String, ArrayList<int[]>> oEntry: m_oCachedStates.entrySet())
				{
					int nRingIndex = 0;
					boolean bInside = false;
					while (!bInside && nRingIndex < oEntry.getValue().size())
					{
						int[] nStateGeo = oEntry.getValue().get(nRingIndex++);
						bInside = GeoUtil.isPointInsideRingAndHoles(nStateGeo, nLon, nLat);
					}
					if (bInside)
					{
						sFile = oEntry.getKey();
					}
				}
				if (sFile == null)
				{
					try (DbfResultSet oDbf = new DbfResultSet(g_sStateShp.replace(".shp", ".dbf"));
						 ShpReader oShp = new ShpReader(new BufferedInputStream(Files.newInputStream(Paths.get(g_sStateShp)))))
					{
						while (oDbf.next()) // find states the network intersects
						{
							ArrayList<int[]> oPolygons = new ArrayList();
							String sState = oDbf.getString("NAME");
							oShp.readPolygon(oPolygons);

							boolean bInside = false;
							int nRingIndex = 0;
							while (!bInside && nRingIndex < oPolygons.size())
							{
								int[] nStateGeo = oPolygons.get(nRingIndex++);
								bInside = GeoUtil.isPointInsideRingAndHoles(nStateGeo, nLon, nLat);
							}
							if (bInside)
							{
								sFile = m_sOsmDir + sState.toLowerCase().replaceAll(" ", "-") + "-latest.bin";
								m_oCachedStates.put(sFile, oPolygons);
								break;
							}
						}
					}
				}
			}
			oRes.setContentType("application/json");
			if (sFile == null || !Files.exists(Paths.get(sFile)))
			{
				oRes.getWriter().append("{}");
				return HttpServletResponse.SC_NOT_FOUND;
			}
			EditList oList = getList(oSession.m_sToken, sNetworkId);
			WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
			synchronized (oList)
			{
				ArrayList<OsmNode> oNodes = new ArrayList();
				ArrayList<OsmWay> oWays = new ArrayList();
				new OsmBinParser().parseHash(sFile, nHash, oNodes, oWays); // open the hash index file for the state
				StringBuilder sWayBuf = new StringBuilder();
				sWayBuf.append('[');
				ArrayList<OsmWay> oWayList = oList.m_oWays;
				oWayList.ensureCapacity(oWays.size() + oWayList.size());
				for (OsmWay oWay : oWays) // add each way to the list
				{
//					if (Collections.binarySearch(oList.m_oOriginalWays, oWay.m_oId, Id.COMPARATOR) >= 0)
//						continue;
					oWay.appendLineGeoJson(sWayBuf, oWayNetworks);
					int nIndex = Collections.binarySearch(oWayList, oWay, OsmWay.WAYBYTEID);
					if (nIndex < 0)
					{
						oWayList.add(~nIndex, oWay);
					}
				}
			
				if (sWayBuf.length() > 1)
					sWayBuf.setLength(sWayBuf.length() - 1);
				sWayBuf.append("]");
				StringBuilder sBuf = new StringBuilder();
				sBuf.append("{\"lines\":").append(sWayBuf).append("}");
				oRes.getWriter().append(sBuf); // write the response
			}
		}
		catch (Exception oEx)
		{
			throw new ServletException(oEx);
		}
		return HttpServletResponse.SC_OK;
	}
	
	
	/**
	 * Deletes the requested network, removing it from the system. If the 
	 * network is currently processing then {@link #m_bSave} is set to false.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @param oSession object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws IOException
	 * @throws ServletException
	 */
	public int doDelete(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oClient)
	   throws IOException, ServletException
	{
		String sNetworkId = oReq.getParameter("networkid");
		WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
		synchronized (m_oProcessQueue)
		{
			if (oWayNetworks.deleteNetwork(sNetworkId))
			{
				int nSize = m_oProcessQueue.size();
				boolean bFound = false;
				for (int nIndex = 0; nIndex < nSize; nIndex++) // check the process queue for the network being deleted
				{
					String sCurrentId = m_oProcessQueue.pollFirst();
					if (!bFound)
					{
						if (sCurrentId.compareTo(sNetworkId) == 0)
						{
							bFound = true;
							m_bSave = nIndex != 0 || status()[0] != RUNNING; // only set save to false if the network being deleted is currently being processed
							continue;
						}
					}

					m_oProcessQueue.addLast(sCurrentId);
				}
			}
			else
				throw new ServletException("Network does not exist.");
		}
		return HttpServletResponse.SC_OK;
	}
	
	
	/**
	 * Queues the requested network to be reprocessed. The network's label is
	 * over written by the requested label.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @param oSession object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws IOException
	 * @throws ServletException
	 */
	public int doReprocess(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oClient)
	   throws IOException, ServletException
	{
		String sNetworkId = oReq.getParameter("networkid");
		String sLabel = oReq.getParameter("label");
		if (sNetworkId != null)
		{
			WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
			synchronized (m_oProcessQueue)
			{
				if (oWayNetworks.reprocessNetwork(sNetworkId, sLabel))
				{
					m_oProcessQueue.addLast(sNetworkId);
				}
				else
					return HttpServletResponse.SC_BAD_REQUEST;
			}
			Scheduling.getInstance().execute(this);
		}
		else
			return HttpServletResponse.SC_BAD_REQUEST;
		
		return HttpServletResponse.SC_OK;
	}
	
	
	/**
	 * Creates a new roadway network with the requested parameters and queues it
	 * to process.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @param oSession object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws IOException
	 * @throws ServletException
	 */
	public int doCreate(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oClient)
	   throws IOException, ServletException
	{
		String sCoords = oReq.getParameter("coords");
		String sOptions = oReq.getParameter("options");
		String sLabel = oReq.getParameter("label");
		if (sCoords != null)
		{
			synchronized (m_oProcessQueue)
			{
				Shake256 oMd = new Shake256();
				String sNetworkId = null;
				try
				(
					DataOutputStream oAbsorb = new DataOutputStream(oMd.getAbsorbStream());
					InputStream oSqueeze = oMd.getSqueezeStream();
				)
				{
					oAbsorb.writeUTF(sCoords);
					byte[] yNetworkId = new byte[16];
					oSqueeze.read(yNetworkId);
					sNetworkId = java.util.Base64.getEncoder().withoutPadding().encodeToString(yNetworkId).replaceAll("\\+", "-").replaceAll("/", "_");
					WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
					oWayNetworks.createNetwork(sNetworkId, sLabel, sOptions.split(","), sCoords);
				}
				if (sNetworkId != null)
				{
					m_oProcessQueue.addLast(sNetworkId);
					Scheduling.getInstance().execute(this);
				}
			}
			
		}
		else
			return HttpServletResponse.SC_BAD_REQUEST;
		
		return HttpServletResponse.SC_OK;
	}
	
	
	/**
	 * Processes the first network in the process queue. This includes determining
	 * which US states the network intersects, downloading any necessary OSM
	 * data files, and converting OSM xml.bz2 files into IMRCP OSM binary files.
	 */
	@Override
	public void execute()
	{
		String sNetworkId = null;
		Network oNetwork = null;
		WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
		try
		{
			synchronized (m_oProcessQueue)
			{
				sNetworkId = m_oProcessQueue.pollFirst();
				if (sNetworkId == null) // nothing to process
					return;
				oNetwork = oWayNetworks.getNetwork(sNetworkId); 
				m_bSave = true; // always save a newly processed network unless it gets deleted while processing

				ArrayList<String> oFilter = new ArrayList();
				boolean bRamps = false;
				boolean bConnectors = false;
				for (int nOptIndex = 0; nOptIndex < oNetwork.m_sOptions.length; nOptIndex++) // read options and set the necessary flags
				{
					String sOpt = oNetwork.m_sOptions[nOptIndex];
					if (sOpt.compareTo("ramp") == 0 || sOpt.compareTo("trunk_link") == 0)
						bRamps = true;
					else if (sOpt.compareTo("connector") == 0 || sOpt.compareTo("motorway_link") == 0)
						bConnectors = true;
					else
						oFilter.add(sOpt);
				}
				if (bConnectors)
					oFilter.add("motorway_link");
				if (bRamps)
					oFilter.add("trunk_link");
				
				oNetwork.m_sFilter = new String[oFilter.size()];
				for (int nFilterIndex = 0; nFilterIndex < oNetwork.m_sFilter.length; nFilterIndex++)
					oNetwork.m_sFilter[nFilterIndex] = oFilter.get(nFilterIndex);
				
				m_oProcessQueue.addFirst(sNetworkId);

				m_oLogger.info(String.format("Request: %s", sNetworkId));

			}
		
			
			long lFileTimeout = System.currentTimeMillis() - m_lFileTimeout;
			ArrayList<String> oStates = new ArrayList();
			int[] nNetworkGeo = oNetwork.getGeometry();
			try (DbfResultSet oDbf = new DbfResultSet(g_sStateShp.replace(".shp", ".dbf"));
				ShpReader oShp = new ShpReader(new BufferedInputStream(Files.newInputStream(Paths.get(g_sStateShp)))))
			{

				ArrayList<int[]> oOuters = new ArrayList();				
				while (oDbf.next()) // find states the network intersects
				{
					String sState = oDbf.getString("NAME");
					oOuters.clear();
					oShp.readPolygon(oOuters);
					int nRingIndex = 0;

					while (nRingIndex < oOuters.size())
					{
						int[] nStateGeo = oOuters.get(nRingIndex++);
						if (GeoUtil.isInsideRingAndHoles(nStateGeo, Obs.POLYGON, nNetworkGeo))
						{
							oStates.add(sState.toLowerCase().replaceAll(" ", "-"));
							break;
						}
					}
				}
			}
			
			ArrayList<OsmWay> oWays = new ArrayList();
			ArrayList<OsmNode> oNodes = new ArrayList();
			StringPool oStringPool = new StringPool();
			String[] sStates = new String[oStates.size()];
			int nStateCount = 0;
			if (oStates.isEmpty())
			{
				throw new Exception("Does not intersect any state polygon");
			}
			for (String sState : oStates) // for each state
			{
				sStates[nStateCount++] = sState;
				m_oLogger.info(String.format("Processing: %s", sState));
				String sDest = m_sOsmDir + sState + "-latest.osm.bz2";
				File oFile = new File(sDest);
				if (!oFile.exists() || oFile.lastModified() < lFileTimeout) // determine if the OSM file needs to be downloaded
				{
					URL oUrl = new URL(String.format(m_sOsmDownload, sState));
					URLConnection oConn = oUrl.openConnection();
					oConn.setConnectTimeout(m_nConnTimeout);
					oConn.setReadTimeout(m_nConnTimeout);
					ByteArrayOutputStream oBaos = new ByteArrayOutputStream();
					m_oLogger.info(String.format("Downloading: %s", String.format(m_sOsmDownload, sState)));
					try (BufferedInputStream oIn = new BufferedInputStream(oConn.getInputStream()))
					{	
						int nByte;
						while ((nByte = oIn.read()) >= 0)
							oBaos.write(nByte);
					}
					
					new File(sDest.substring(0, sDest.lastIndexOf("/"))).mkdirs();
					try (BufferedOutputStream oFileOut = new BufferedOutputStream(new FileOutputStream(sDest)))
					{
						oBaos.writeTo(oFileOut);
					}
				}
				else
				{
					m_oLogger.info(String.format("Already have %s", sDest));
				}
				
				String sBin = sDest.replace(".osm.bz2", ".bin");
				oFile = new File(sBin);
				if (!oFile.exists() || oFile.lastModified() < lFileTimeout) // if needed, convert the xml.bz2 into IMRCP OSM binary file
				{
					m_oLogger.info(String.format("Converting file: %s", sDest));
					new OsmBz2ToBin().convertFile(sDest, m_nTol);
				}
				
				m_oLogger.info(String.format("Parsing file: %s", sBin));
				new OsmBinParser().parseFileWithFilters(sBin, oNodes, oWays, nNetworkGeo, oStringPool, oNetwork.m_sFilter); // extract the nodes, ways, and key/value strings from the file
			}
			
			oNetwork.m_sStates = sStates;
			m_oLogger.info(String.format("%d ways", oWays.size()));
			m_oLogger.info(String.format("%d nodes", oNodes.size()));
			String sGeoFile = String.format(m_sGeoFileFormat, sNetworkId, "unpublished");
			Files.createDirectories(Paths.get(sGeoFile).getParent(), FileUtil.DIRPERS);
			try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Paths.get(sGeoFile + ".ids"))))) // write a list of the original ways of the network
			{
				oOut.writeInt(oWays.size());
				for (OsmWay oWay : oWays)
					oWay.m_oId.write(oOut);
			}
			
			oNodes = OsmUtil.removeZeroRefs(oNodes);
			StringPool oNetworkPool = new StringPool();
			for (OsmWay oWay : oWays)
			{
				oWay.setMinMax();
				oWay.calcMidpoint();
				oWay.setBridge();
				oWay.generateId();
				oNetwork.m_oNetworkWays.add(oWay);
				for (Entry<String, String> oTag : oWay.entrySet())
				{
					oNetworkPool.intern(oStringPool.intern(oTag.getKey()));
					oNetworkPool.intern(oStringPool.intern(oTag.getValue()));
				}
				for (OsmNode oNode : oWay.m_oNodes)
				{
					for (Entry<String, String> oNodeTag : oNode.entrySet())
					{
						oNetworkPool.intern(oNodeTag.getKey());
						oNetworkPool.intern(oNodeTag.getValue());
					}
				}
			}
			

			synchronized (m_oProcessQueue)
			{
				if (m_bSave)
				{
					OsmBz2ToBin.writeBin(sGeoFile, oNetworkPool, oNodes, oWays); // write the IMRCP OSM binary file
					m_oProcessQueue.pollFirst(); // remove id
					oNetwork.removeStatus(Network.ASSEMBLING);
					oNetwork.addStatus(Network.WORKINPROGRESS);
					oWayNetworks.writeNetworkFile();
				}
				if (!m_oProcessQueue.isEmpty())
					Scheduling.getInstance().scheduleOnce(this, 1000); // execute again if there is more work
			}
		}
		catch (Exception oEx)
		{
			try
			{
				synchronized (m_oProcessQueue)
				{
					oNetwork.m_nStatus = Network.ERROR;
					oWayNetworks.writeNetworkFile();
				}
			}
			catch (Exception oAnotherEx)
			{
				m_oLogger.error(oAnotherEx, oAnotherEx);
			}
			m_oLogger.error(oEx, oEx);
		}
	}
	
	
	/**
	 * Saves any edits that have been performed for the requested network and
	 * session.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @param oSession object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws IOException
	 * @throws ServletException
	 */
	public int doSave(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oClient)
	   throws IOException, ServletException
	{
		String sNetworkId = oReq.getParameter("networkid");
		String sGeoFile = String.format(m_sGeoFileFormat, sNetworkId, "unpublished");
		ArrayList<OsmWay> oWays = new ArrayList();
		ArrayList<OsmNode> oNodes = new ArrayList();
		StringPool oPool = new StringPool();
		EditList oList = getList(oSession.m_sToken, sNetworkId);
		ArrayList<OsmWay> oNewWays = new ArrayList();
		synchronized (oList)
		{
			new OsmBinParser().parseFile(sGeoFile, oNodes, oWays, oPool); // get the current nodes and ways of the network
			OsmWay oSearch = new OsmWay();
			for (Id oId : oList.m_oAdd) // for each of the ways that need to be added
			{
				oSearch.m_oId = oId;
				int nIndex = Collections.binarySearch(oList.m_oWays, oSearch, OsmWay.WAYBYTEID);
				if (nIndex >= 0)
				{
					OsmWay oWay = oList.m_oWays.get(nIndex);
					oNewWays.add(oWay);
					nIndex = Collections.binarySearch(oWays, oWay, OsmWay.WAYBYTEID);
					if (nIndex < 0) // if it is a new way to the network
					{
						oWays.add(~nIndex, oWay); // add it to the overall list
						for (Entry<String, String> oEntry : oWay.entrySet()) // add its keys and values to the string pool
						{
							oPool.intern(oEntry.getKey());
							oPool.intern(oEntry.getValue());
						}
						for (int nNodeIndex = 0; nNodeIndex < oWay.m_oNodes.size(); nNodeIndex++)
						{
							OsmNode oNode = oWay.m_oNodes.get(nNodeIndex);
							for (Entry<String, String> oEntry : oNode.entrySet())
							{
								oPool.intern(oEntry.getKey());
								oPool.intern(oEntry.getValue());
							}

							nIndex = Collections.binarySearch(oNodes, oNode, OsmNode.NODEBYTEID);
							if (nIndex < 0) // add new nodes to the overall list
							{
								oNodes.add(~nIndex, oNode);
							}
							else // if the node already exists...
							{
								oNode = oNodes.get(nIndex);
								oWay.m_oNodes.set(nNodeIndex, oNode); // make sure the way has the correct reference to it
							}
							nIndex = Collections.binarySearch(oNode.m_oRefs, oWay, OsmWay.WAYBYTEID);
							if (nIndex < 0)
							{
								oNode.m_oRefs.add(~nIndex, oWay);
							}
						}
					}
				}
			}
			
			for (Id oId : oList.m_oRemove) // for each way to remove
			{
				oSearch.m_oId = oId;
				int nIndex = Collections.binarySearch(oWays, oSearch, OsmWay.WAYBYTEID);
				if (nIndex >= 0)
				{
					OsmWay oWay = oWays.get(nIndex);
					oWays.remove(nIndex); // remove from overall list
					oWay.removeRefs(); // update node references in the way
				}
			}
			oList.clear();
			removeList(oList);
		}
			
		oNodes = OsmUtil.removeZeroRefs(oNodes);
		for (OsmWay oWay : oWays) // reset nodes to be ready to be written to file
		{
			for (OsmNode oNode : oWay.m_oNodes)
				oNode.m_nFp = 0;
		}
		try
		{
			OsmBz2ToBin.writeBin(sGeoFile, oPool, oNodes, oWays); // write the edited network
			OsmUtil.writeLanesAndSpeeds(m_sGeoFileFormat, sNetworkId, oNewWays, true); // append the new way to the metadata files			
		}
		catch (Exception oEx)
		{
			throw new ServletException(oEx);
		}
			
		
		return HttpServletResponse.SC_OK;
	}
	
	
	/**
	 * Adds the geometry of all the roadway segments of the requested network
	 * to the response.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @param oSession object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws IOException
	 * @throws ServletException
	 */
	public int doGeo(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oClient)
	   throws IOException, ServletException
	{
		String sNetworkId = oReq.getParameter("networkid");
		StringBuilder sLineBuf = new StringBuilder();
		ArrayList<OsmNode> oNodes = new ArrayList();
		StringPool oPool = new StringPool();
		String sPublished = oReq.getParameter("published");
		String sFile = "unpublished";
		if (sPublished != null && Boolean.parseBoolean(sPublished))
			sFile = "published";
		String sGeoFile = String.format(m_sGeoFileFormat, sNetworkId, sFile);
		EditList oList = getList(oSession.m_sToken, sNetworkId);
		WayNetworks oWays = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
		synchronized (oList)
		{
			oList.m_oWays.clear();
			oList.m_oOriginalWays.clear();
			if (oReq.getParameter("separate") == null)
			{
				try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(Files.newInputStream(Paths.get(String.format(m_sGeoFileFormat, sNetworkId, "unpublished") + ".ids")))))
				{
					int nCount = oIn.readInt();
					while (nCount-- > 0)
					{
						Id oId = new Id(oIn);
						oList.m_oOriginalWays.add(oId);
					}
				}
			}
			
			Introsort.usort(oList.m_oOriginalWays, Id.COMPARATOR);
			new OsmBinParser().parseFile(sGeoFile, oNodes, oList.m_oWays, oPool);
			sLineBuf.append('[');
			for (OsmWay oWay : oList.m_oWays)
			{
				oWay.appendLineGeoJson(sLineBuf, oWays);
			}
			if (!oList.m_oWays.isEmpty())
			{
				sLineBuf.setLength(sLineBuf.length() - 1);
			}
		}
		sLineBuf.append(']');
		oRes.setContentType("application/json");
		try (BufferedWriter oOut = new BufferedWriter(oRes.getWriter()))
		{
			oOut.append(sLineBuf);
		}
		
		return HttpServletResponse.SC_OK;
	}
	
	
	/**
	 * Adds the requested Id to the add list of the EditList for the requested network and 
	 * session.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @param oSession object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws IOException
	 * @throws ServletException
	 */
	public int doAdd(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oClient)
	   throws IOException, ServletException
	{
		EditList oList = getList(oSession.m_sToken, oReq.getParameter("networkid"));
		synchronized (oList)
		{
			Id oId = new Id(oReq.getParameter("id"));
			int nIndex = Collections.binarySearch(oList.m_oAdd, oId, Id.COMPARATOR);
			if (nIndex < 0)
				oList.m_oAdd.add(~nIndex, oId);

			nIndex = Collections.binarySearch(oList.m_oRemove, oId, Id.COMPARATOR);
			if (nIndex >= 0)
				oList.m_oRemove.remove(nIndex);
		}
		
		return HttpServletResponse.SC_OK;
	}
	
	
	/**
	 * Adds the requested Id to the remove list of the EditList for the requested network and 
	 * session.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @param oSession object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws IOException
	 * @throws ServletException
	 */
	public int doRemove(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oClient)
	   throws IOException, ServletException
	{
		EditList oList = getList(oSession.m_sToken, oReq.getParameter("networkid"));
		synchronized (oList)
		{
			Id oId = new Id(oReq.getParameter("id"));
			int nIndex = Collections.binarySearch(oList.m_oAdd, oId, Id.COMPARATOR);
			if (nIndex >= 0)
				oList.m_oAdd.remove(nIndex);

			nIndex = Collections.binarySearch(oList.m_oRemove, oId, Id.COMPARATOR);
			if (nIndex < 0)
				oList.m_oRemove.add(~nIndex, oId);
		}
		
		return HttpServletResponse.SC_OK;
	}
		
	
	/**
	 * Queues the requested network to be published.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @param oSession object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws IOException
	 * @throws ServletException
	 */
	public int doPublish(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oClient)
	   throws IOException, ServletException
	{
		String sNetworkId = oReq.getParameter("networkid");
		String sTrafficModel = oReq.getParameter("trafficmodel");
		boolean bTrafficModel = false;
		if (sTrafficModel != null)
			bTrafficModel = Boolean.parseBoolean(sTrafficModel);
		
		String sRoadWxModel = oReq.getParameter("roadwxmodel");
		boolean bRoadWxModel = false;
		if (sRoadWxModel != null)
			bRoadWxModel = Boolean.parseBoolean(sRoadWxModel);
		
		String sExternalPublish = oReq.getParameter("externalpublish");
		boolean bExternalPublish = false;
		if (sExternalPublish != null)
			bExternalPublish = Boolean.parseBoolean(sExternalPublish);
		
		WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
		m_oLogger.debug("publishing " + sNetworkId);
		oWayNetworks.publishNetwork(sNetworkId, g_sStateShp, m_sOsmDir, bTrafficModel, bRoadWxModel, bExternalPublish);
		return HttpServletResponse.SC_OK;
	}
	
	
	public int doTrain(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oClient)
		throws IOException, ServletException
	{
		MLPHurricane oBlock = (MLPHurricane)Directory.getInstance().lookup("MLPHurricane");
		oRes.setContentType("application/json");
		if (oBlock.isTraining())
		{
			oRes.setStatus(HttpServletResponse.SC_FORBIDDEN);
			try (PrintWriter oOut = oRes.getWriter())
			{
				oOut.append("{\"msg\":\"Cannot train multiple networks at once\"}");
			}
			
			return HttpServletResponse.SC_FORBIDDEN;
		}
		String sNetworkId = oReq.getParameter("networkid");
		SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd");
		oSdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
		Network oNetwork = oWayNetworks.getNetwork(sNetworkId);
		if (!oNetwork.isStatus(Network.TRAINING))
		{
			oNetwork.addStatus(Network.TRAINING);
			JSONObject oStatus = oWayNetworks.getHurricaneModelStatus(oNetwork);
			oWayNetworks.writeHurricaneModelStatus(oNetwork, oStatus.getString("model"), WayNetworks.HURMODEL_TRAINING);
			Scheduling.getInstance().execute(() -> oBlock.train(oNetwork));
		}
		
		try (PrintWriter oOut = oRes.getWriter())
		{
			oOut.append("{}");
		}
		return HttpServletResponse.SC_OK;
	}
	
	
	/**
	 * Removes the given list from the active session list.
	 * @param oList edit list to remove
	 */
	private void removeList(EditList oList)
	{
		synchronized (m_oSessionLists)
		{
			int nIndex = Collections.binarySearch(m_oSessionLists, oList);
			if (nIndex >= 0)
				m_oSessionLists.remove(nIndex);
		}
	}
	
	
	public static String getUSBorderShp()
	{
		return g_sUSBorderShp;
	}
	
	
	/**
	 * Gets the active EditList for the given session id and network id
	 * @param sSessionId session token for the user editing the network
	 * @param sNetworkId network id
	 * @return active EditList associated with the session id and network id.
	 */
	private EditList getList(String sSessionId, String sNetworkId)
	{
		synchronized (m_oSessionLists)
		{
			EditList oSearch = new EditList(sSessionId, sNetworkId);
			int nIndex = Collections.binarySearch(m_oSessionLists, oSearch);
			if (nIndex < 0)
			{
				nIndex = ~nIndex;
				m_oSessionLists.add(nIndex, oSearch);
			}

			EditList oRet = m_oSessionLists.get(nIndex);
			synchronized (oRet)
			{
				if (oRet.m_sNetworkId.compareTo(sNetworkId) != 0) // the user has changed networks, clear all the lists
				{
					oRet.m_oAdd.clear();
					oRet.m_oRemove.clear();
					oRet.m_oWays.clear();
					oRet.m_sNetworkId = sNetworkId;
				}

				return oRet;
			}
		}
	}

	
	/**
	 * Encapsulates the object necessary for making edits to a roadway network
	 */
	class EditList implements Comparable<EditList>
	{
		/**
		 * Session id
		 */
		String m_sSession;

		
		/**
		 * Network id
		 */
		String m_sNetworkId;

		
		/**
		 * Stores the ids of the roadway segments that will be removed from the
		 * network
		 */
		ArrayList<Id> m_oRemove;

		
		/**
		 * Stores the ids of the roadway segments that will be added to the
		 * network
		 */
		ArrayList<Id> m_oAdd;

		
		/**
		 * Stores the ids of the roadway segments that were originally apart of
		 * the network
		 */
		ArrayList<Id> m_oOriginalWays;

		
		/**
		 * Contains the roadway segments created by merge and split operations
		 */
		ArrayList<OsmWay> m_oWays;
		
		
		/**
		 * Default constructor. Does nothing.
		 */
		EditList()
		{
		}
		
		
		/**
		 * Constructs an EditList for the given session id and network id.
		 * @param sSession session id
		 * @param sNetworkId network id
		 */
		EditList(String sSession, String sNetworkId)
		{
			m_sSession = sSession;
			m_sNetworkId = sNetworkId;
			m_oRemove = new ArrayList();
			m_oAdd = new ArrayList();
			m_oWays = new ArrayList();
			m_oOriginalWays = new ArrayList();
		}
		
		
		/**
		 * Compares EditLists by session id
		 */
		@Override
		public int compareTo(EditList o)
		{
			return m_sSession.compareTo(o.m_sSession);
		}
		
		
		/**
		 * Clears all of the lists
		 */
		void clear()
		{
			m_oRemove.clear();
			m_oAdd.clear();
			m_oOriginalWays.clear();
			m_oWays.clear();
		}
	}
}
