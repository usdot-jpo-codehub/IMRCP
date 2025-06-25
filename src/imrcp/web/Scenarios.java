/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.web;

import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.osm.OsmNode;
import imrcp.geosrv.osm.OsmWay;
import imrcp.geosrv.WayNetworks;
import imrcp.geosrv.WayNetworks.WayMetadata;
import imrcp.forecast.mdss.MetroProcess;
import imrcp.forecast.mlp.MLP;
import imrcp.system.CsvReader;
import imrcp.system.Directory;
import imrcp.system.FileUtil;
import imrcp.system.Id;
import imrcp.system.Scheduling;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * This servlet manages all of the requests and processing necessary to run the
 * Create and View Scenarios UIs.
 * @author aaron.cherney
 */
public class Scenarios extends SecureBaseBlock
{
	/**
	 * Format String used to generate configuration file names
	 */
	private String m_sConfigFf;

	
	/**
	 * Base directory for all of the Scenarios files
	 */
	private String m_sBaseDir;

	
	/**
	 * Format String used to generate scenario data files
	 */
	private String m_sDataFf;

	
	/**
	 * Stores the Scenario Templates
	 */
	private final ArrayList<String[]> m_oTemplates = new ArrayList();

	private double m_dExtendDistTol;
	
	/**
	 * Single reference for an array representing no data was able to be calculated
	 */
	private final int[] NODATA = new int[]{-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
	
	/**
	 * Single reference for an array representing no data was able to be calculated
	 */
	private final double[] NODATADOUBLES = new double[]{-999.0, -999.0, -999.0, -999.0, -999.0, -999.0, -999.0, -999.0, -999.0, -999.0, -999.0, -999.0, -999.0, -999.0, -999.0, -999.0, -999.0, -999.0, -999.0, -999.0, -999.0, -999.0, -999.0, -999.0, -999.0};
		
	
	/**
	 * Compares String[] representing Scenario Templates by position 0 (name of the scenario) and then
	 * position 1 (user who create the scenario).
	 */
	private Comparator<String[]> TEMPLATECOMP = (String[] o1, String[] o2) ->
	{
		int nRet = o1[0].compareTo(o2[0]);
		if (nRet == 0)
			nRet = o1[1].compareTo(o2[1]);
		
		return nRet;
	};
	
	
	/**
	 * If the base directory doesn't exists, it creates the necessary parent 
	 * directories and the configured base directory. It then iterates through
	 * the existing scenarios and loads the templates into memory in {@link #m_oTemplates}.
	 * Then sets a schedule to execute on a fixed interval.
	 * 
	 * @return true if no Exceptions are thrown
	 * @throws Exception 
	 */
	@Override
	public boolean start()
		throws Exception
	{
		try
		{
			Files.createDirectories(Paths.get(m_sBaseDir));
		}
		catch (FileAlreadyExistsException oEx)
		{
		}
		
		for (Scenario oScenario : getScenarios(null))
		{
			if (!oScenario.m_bRun)
			{
				String[] sTemplate = new String[]{oScenario.m_sName, oScenario.m_sUser, oScenario.m_sId};
				int nIndex = Collections.binarySearch(m_oTemplates, sTemplate, TEMPLATECOMP);
				if (nIndex < 0)
					m_oTemplates.add(~nIndex, sTemplate);
			}
		}
		return true;
	}
	
	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_sConfigFf = oBlockConfig.optString("configff", "%s/scenario.json");
		m_sDataFf = oBlockConfig.optString("dataff", "%s/data.json");
		m_sBaseDir = oBlockConfig.optString("basedir", "");
		if (m_sBaseDir.startsWith("/"))
			m_sBaseDir = m_sBaseDir.substring(1);
		if (!m_sBaseDir.endsWith("/"))
			m_sBaseDir += "/";
		
		m_sBaseDir = m_sDataPath + m_sBaseDir;
		m_dExtendDistTol = oBlockConfig.optDouble("disttol", 480000);
	}
	
	
	@Override
	public void process(String[] sNotification)
	{
		if (sNotification[MESSAGE].compareTo("mlp") == 0)
		{
			String sId = sNotification[2];
			try
			{
				createData(getScenario(sId));
			}
			catch (Exception oEx)
			{
				m_oLogger.error(oEx, oEx);
			}
		}
	}
	
	
	private void createData(Scenario oScenario)
		throws Exception
	{
		if (oScenario == null)
			return;
		WayNetworks oWays = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
		TreeMap<Id, double[]> oPreds = new TreeMap(Id.COMPARATOR);
		ArrayList<OsmWay> oSegments = new ArrayList();
		for (SegmentGroup oGroup : oScenario.m_oGroups)
		{
			oSegments.ensureCapacity(oGroup.m_oSegments.length);
			for (Id oId : oGroup.m_oSegments)
			{
				OsmWay oWay = oWays.getWayById(oId);
				if (oWay != null)
					oSegments.add(oWay);
			}
		}
		Path oProcessedFile = Paths.get(m_sBaseDir + String.format(m_sDataFf, oScenario.m_sId).replace("data", "processed"));
		List<Path> oOneshotOutputs = Files.walk(oProcessedFile.getParent(), 2, FileVisitOption.FOLLOW_LINKS).filter(oP -> oP.toString().endsWith("mlp_oneshot_output.csv")).collect(Collectors.toList());
		for (Path oOneshotOutput : oOneshotOutputs)
		{
			try (CsvReader oIn = new CsvReader(Files.newInputStream(oOneshotOutput)))
			{
				int nCols;
				while ((nCols = oIn.readLine()) > 0)
				{
					Id oId = new Id(oIn.parseString(0));
					double[] dPred = new double[nCols + 1]; // number of columns is the number of predictions + 1, we need number of predictions + 2
					OsmWay oWay = oWays.getWayById(oId);
					if (oWay == null)
						continue;
					java.util.Arrays.fill(dPred, Double.NaN);
					dPred[0] = GeoUtil.fromIntDeg(oWay.m_nMidLon);
					dPred[1] = GeoUtil.fromIntDeg(oWay.m_nMidLat);
					for (int nCol = 1; nCol < nCols; nCol++)
					{
						dPred[nCol + 1] = oIn.parseDouble(nCol);
					}
					oPreds.put(oId, dPred);
				}
			}
			for (Path oFile : Files.walk(oOneshotOutput.getParent(), FileVisitOption.FOLLOW_LINKS).sorted(Comparator.reverseOrder()).collect(Collectors.toList())) // delete all associated files
			{
				Files.delete(oFile);
			}
		}
		MLP.extendPredictions(oPreds, oSegments, oWays, m_dExtendDistTol, new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE});
		JSONArray oGeoJsonFeatures;
		
		try (BufferedReader oIn = Files.newBufferedReader(oProcessedFile, StandardCharsets.UTF_8))
		{
			oGeoJsonFeatures = new JSONArray(new JSONTokener(oIn));
		}
		for (int nIndex = 0; nIndex < oGeoJsonFeatures.length(); nIndex++)
		{
			JSONObject oFeature = oGeoJsonFeatures.getJSONObject(nIndex);
			JSONObject oProps = oFeature.getJSONObject("properties");
			String sId = oProps.getString("imrcpid");
			Id oId = new Id(sId);
			if (oPreds.containsKey(oId))
			{
				double[] dSpeeds = oPreds.get(oId);
				int[] nTraffic = new int[dSpeeds.length - 2];
				for (int nSpdIndex = 2; nSpdIndex < dSpeeds.length; nSpdIndex++)
				{
					if (Double.isNaN(dSpeeds[nSpdIndex]))
						nTraffic[nSpdIndex - 2] = -1;
					else
						nTraffic[nSpdIndex - 2] = (int)Math.round(dSpeeds[nSpdIndex]);
				}

				oProps.put("spdlnk", nTraffic);
			}
			else
			{
				oProps.put("spdlnk", NODATA);
			}
		}
		
		try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(Paths.get(m_sBaseDir + String.format(m_sDataFf, oScenario.m_sId)), FileUtil.WRITE, FileUtil.FILEPERS), "UTF-8")))
		{
			oGeoJsonFeatures.write(oOut);
		}
		Files.deleteIfExists(oProcessedFile);
	}
	
	/**
	 * Saves the Scenario Template defined in the request.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @param oSess object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws IOException
	 * @throws ServletException
	 */
	public int doSave(HttpServletRequest oReq, HttpServletResponse oRes, Session oSess, ClientConfig oClient)
		throws IOException, ServletException
	{
		JSONObject oResponse = new JSONObject();
		oRes.setContentType("application/json");
		String sJson = oReq.getParameter("scenario"); // get String that is the json definition of scenario
		boolean bCommitMetadata = Boolean.parseBoolean(oReq.getParameter("commit"));
		if (sJson == null)
		{
			oResponse.put("status", "Save Failed");
			oResponse.put("msg", "Invalid request");
			try (PrintWriter oOut = oRes.getWriter())
			{
				oResponse.write(oOut);
			}
			return HttpServletResponse.SC_BAD_REQUEST;
		}
		
		JSONObject oJson = new JSONObject(sJson); // convert String to JSONObject
		String sUser = oJson.optString("user", oSess.m_sName);
		String[] sTemp = new String[]{oJson.getString("name"), sUser};
		
		
		synchronized (m_oTemplates)
		{
			int nIndex = Collections.binarySearch(m_oTemplates, sTemp, TEMPLATECOMP);
			if (nIndex < 0) // if the template doesn't exist, add it to the list
			{
				nIndex = ~nIndex;
				m_oTemplates.add(nIndex, new String[]{sTemp[0], oSess.m_sName, UUID.randomUUID().toString()});
			}
			
			sTemp = m_oTemplates.get(nIndex);
		}
		
		oJson.put("uuid", sTemp[2]);
		oJson.put("user", sUser);
		oJson.put("run", false); // flag to indicate it is a template
		Scenario oScenario = new Scenario(oJson);
		if (bCommitMetadata && !oScenario.m_oUserDefinedMetadata.isEmpty())
		{
			((WayNetworks)Directory.getInstance().lookup("WayNetworks")).commitMetadata(oScenario);
		}
		Path oPath = Paths.get(m_sBaseDir + String.format(m_sConfigFf, oScenario.m_sId));
		
		Files.createDirectories(oPath.getParent(), FileUtil.DIRPERS);
		try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oPath, FileUtil.WRITE, FileUtil.FILEPERS), "UTF-8")))
		{
			oJson.write(oOut);
		}
		
		if (oScenario.m_bShare && !oScenario.m_bIsShared)
		{
			Scenario oShared = new Scenario(oJson);
			oShared.m_bShare = false;
			oShared.m_bIsShared = true;
			oShared.m_sName += "_" + oScenario.m_sUser;
			sTemp = new String[]{oShared.m_sName, oShared.m_sUser};
			synchronized (m_oTemplates)
			{
				int nIndex = Collections.binarySearch(m_oTemplates, sTemp, TEMPLATECOMP);
				if (nIndex < 0) // if the template doesn't exist, add it to the list
				{
					nIndex = ~nIndex;
					m_oTemplates.add(nIndex, new String[]{sTemp[0], oShared.m_sUser, UUID.randomUUID().toString()});
				}

				sTemp = m_oTemplates.get(nIndex);
			}
			oShared.m_sId = sTemp[2];
			
			oPath = Paths.get(m_sBaseDir + String.format(m_sConfigFf, oShared.m_sId));
		
			Files.createDirectories(oPath.getParent(), FileUtil.DIRPERS);
			JSONObject oSharedJson = oShared.toJSONObject(false, true);
			try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oPath, FileUtil.WRITE, FileUtil.FILEPERS), "UTF-8")))
			{
				oSharedJson.write(oOut);
			}
			oResponse.put("shared", oSharedJson);
		}
		oResponse.put("status", "Save Succeeded");
		oResponse.put("msg", "Scenario settings were saved. You can continue editing the scenario or submit the scenario to run.");
		oResponse.put("saved", oJson);
		try (PrintWriter oOut = oRes.getWriter())
		{
			oResponse.write(oOut);
		}
		return HttpServletResponse.SC_OK;
	}
	
	
	public int doMetadata(HttpServletRequest oReq, HttpServletResponse oRes, Session oSess, ClientConfig oClient)
		throws IOException, ServletException
	{
		String sName = oReq.getParameter("name");
		String sWayId = oReq.getParameter("wayid");
		String sLanes = oReq.getParameter("lanes");
		String sSpdLimit = oReq.getParameter("spdlimit");
		JSONObject oResponse = new JSONObject();
		oRes.setContentType("application/json");
		if (sWayId == null || sLanes == null || sSpdLimit == null)
		{
			oResponse.put("status", "Metadata Save Failed");
			oResponse.put("msg", "Invalid request");
			try (PrintWriter oOut = oRes.getWriter())
			{
				oResponse.write(oOut);
			}
			return HttpServletResponse.SC_BAD_REQUEST;
		}
		
		if (sName != null)
		{
			String[] sTemp = new String[]{sName, oSess.m_sName};
			synchronized (m_oTemplates)
			{
				int nIndex = Collections.binarySearch(m_oTemplates, sTemp, TEMPLATECOMP);
				if (nIndex >= 0) // find the template in the list
				{
					sTemp = m_oTemplates.get(nIndex);
					Scenario oScenario = getScenario(sTemp[2]); 
					oScenario.m_oUserDefinedMetadata.put(new Id(sWayId), new int[]{Integer.parseInt(sLanes), Integer.parseInt(sSpdLimit)});

					Path oPath = Paths.get(m_sBaseDir + String.format(m_sConfigFf, oScenario.m_sId));

					Files.createDirectories(oPath.getParent(), FileUtil.DIRPERS);
					try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oPath, FileUtil.WRITE, FileUtil.FILEPERS), "UTF-8")))
					{
						oScenario.toJSONObject(false, true).write(oOut);
					}
				}
			}
		}
		
		try (PrintWriter oOut = oRes.getWriter())
		{
			oResponse.write(oOut);
		}
		return HttpServletResponse.SC_OK;
	}
	
	/**
	 * Deletes the Scenario Template defined in the request
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @param oSess object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws IOException
	 * @throws ServletException
	 */
	public int doDeleteTemplate(HttpServletRequest oReq, HttpServletResponse oRes, Session oSess, ClientConfig oClient)
		throws IOException, ServletException
	{
		JSONObject oResponse = new JSONObject();
		oRes.setContentType("application/json");
		String[] sTemp = new String[]{oReq.getParameter("name"), oSess.m_sName};
		if (sTemp[0] == null) // invalid request
		{
			oResponse.put("status", "Delete Failed");
			oResponse.put("msg", "Invalid request");
			try (PrintWriter oOut = oRes.getWriter())
			{
				oResponse.write(oOut);
			}
			return HttpServletResponse.SC_BAD_REQUEST;
		}
		
		synchronized (m_oTemplates)
		{
			int nIndex = Collections.binarySearch(m_oTemplates, sTemp, TEMPLATECOMP);
			if (nIndex >= 0) // find the template in the list
			{
				sTemp = m_oTemplates.get(nIndex);
				Path oPath = Paths.get(m_sBaseDir + String.format(m_sConfigFf, sTemp[2]));
				if (Files.exists(oPath))
				{
					try
					{
						for (Path oFile : Files.walk(oPath.getParent(), FileVisitOption.FOLLOW_LINKS).sorted(Comparator.reverseOrder()).collect(Collectors.toList())) // delete all associated files
						{
							Files.delete(oFile);
						}
					}
					catch (IOException oEx)
					{
						m_oLogger.error(oEx, oEx);
						oResponse.put("status", "Delete Failed");
						oResponse.put("msg", "Error deleting template, try again later");
						try (PrintWriter oOut = oRes.getWriter())
						{
							oResponse.write(oOut);
						}
						return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
					}
				}
				sTemp = m_oTemplates.remove(nIndex); // remove it
			}
		}
		oResponse.put("status", "Delete Succeeded");
		oResponse.put("msg", "Template " + sTemp[0] + " was successfully deleted");
		try (PrintWriter oOut = oRes.getWriter())
		{
			oResponse.write(oOut);
		}
		return HttpServletResponse.SC_OK;
	}
	
	
	/**
	 * Adds a list of the Scenarios to the response, that if the "processed"
	 * parameter exists and is true represent the Scenarios that have been 
	 * queued to process, otherwise the list represents the Scenario Templates
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @param oSess object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws IOException
	 * @throws ServletException
	 */
	public int doList(HttpServletRequest oReq, HttpServletResponse oRes, Session oSess, ClientConfig oClient)
		throws IOException, ServletException
	{
		JSONArray oResponse = new JSONArray();
		String sProcessed = oReq.getParameter("processed");
		boolean bProcessed = sProcessed != null && sProcessed.compareToIgnoreCase("true") == 0;
		ArrayList<Scenario> oScenarios = getScenarios(oSess.m_sName);
		for (Scenario oScenario : oScenarios)
		{
			if (bProcessed == oScenario.m_bRun) // match the processed parameter
			{
				if (bProcessed)
					oScenario.m_bProcessed = Files.exists(Paths.get(m_sBaseDir + String.format(m_sDataFf, oScenario.m_sId)));

				oResponse.put(oScenario.toJSONObject(true, true));
			}
		}
		
		oRes.setContentType("application/json");
		try (PrintWriter oOut = oRes.getWriter())
		{
			oResponse.write(oOut);
		}
		return HttpServletResponse.SC_OK;
	}
	
	
	/**
	 * Queues the Scenario Template defined in the request to process.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @param oSess object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws IOException
	 * @throws ServletException
	 */
	public int doRun(HttpServletRequest oReq, HttpServletResponse oRes, Session oSess, ClientConfig oClient)
		throws IOException, ServletException
	{
		JSONObject oResponse = new JSONObject();
		String sScenarioUser = oReq.getParameter("user");
		String[] sTemp = new String[]{oReq.getParameter("template"), sScenarioUser};
		String sName = oReq.getParameter("name");
		oRes.setContentType("application/json");
		synchronized (m_oTemplates)
		{
			int nIndex = Collections.binarySearch(m_oTemplates, sTemp, TEMPLATECOMP);
			if (nIndex < 0) // the template could not be found
			{
				oResponse.put("status", "Run Failed");
				oResponse.put("msg", "Error queuing the scenario to run, try again later");
				try (PrintWriter oOut = oRes.getWriter())
				{
					oResponse.write(oOut);
				}
				return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
			}
			
			sTemp = m_oTemplates.get(nIndex);
			
			Scenario oScenario = getScenario(sTemp[2]); 
			if (oScenario == null || (!oScenario.m_bIsShared && sScenarioUser.compareTo(oSess.m_sName) != 0)) // the scenario with the template id couldn't be found or it is not a shared template and user is not the correct user
			{
				oResponse.put("status", "Run Failed");
				oResponse.put("msg", "Error queuing the scenario to run, try again later");
				try (PrintWriter oOut = oRes.getWriter())
				{
					oResponse.write(oOut);
				}
				return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
			}
			
			if (oScenario.m_bIsShared)
				sName += "_" + oSess.m_sName;
			oScenario.m_lStartTime = Long.parseLong(oReq.getParameter("starttime")); // get the start time from the request
			oScenario.generateId(); // Shake256 squeeze algorithm id
			
			Path oPath = Paths.get(m_sBaseDir + String.format(m_sConfigFf, oScenario.m_sId));
			Files.createDirectories(oPath.getParent(), FileUtil.DIRPERS); // create necessary directories if needed
			JSONObject oJson = oScenario.toJSONObject(false, true);
			oJson.put("name", sName);
			oJson.put("run", true);
			oJson.put("trafficmodel", Boolean.parseBoolean(oReq.getParameter("trafficmodel")));
			oJson.put("roadwxmodel", Boolean.parseBoolean(oReq.getParameter("roadwxmodel")));
			try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oPath, FileUtil.WRITE, FileUtil.FILEPERS), "UTF-8"))) // write the configuration file for this Scenario run
			{
				oJson.write(oOut);
			}
			
			
		}
		oResponse.put("status", "Run Queued");
		oResponse.put("msg", "Scenario was sucessfully queued to run. Once it has been processed, the scenario can be view on the \"View Scenario\" page");
		try (PrintWriter oOut = oRes.getWriter())
		{
			oResponse.write(oOut);
		}
		Scheduling.getInstance().scheduleOnce(this, 1000);
		return HttpServletResponse.SC_OK;
	}
	
	
	/**
	 * Adds the data associated with the Scenario defined in the request to the
	 * response.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @param oSess object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws IOException
	 * @throws ServletException
	 */
	public int doData(HttpServletRequest oReq, HttpServletResponse oRes, Session oSess, ClientConfig oClient)
		throws IOException, ServletException
	{
		oRes.setContentType("application/json");
		String sId = oReq.getParameter("id");
		if (sId == null) // did provide an id
		{
			JSONObject oResponse = new JSONObject();
			oResponse.put("status", "Scenario Load Failed");
			oResponse.put("msg", "Error loading scenario, try again later");
			try (PrintWriter oOut = oRes.getWriter())
			{
				oResponse.write(oOut);
			}
			return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
		
		
		Path oDataFile = Paths.get(m_sBaseDir + String.format(m_sDataFf, sId));
		try (BufferedReader oIn = Files.newBufferedReader(oDataFile, StandardCharsets.UTF_8); // will throw an Exception if hte file doesn't exist
			PrintWriter oOut = oRes.getWriter())
		{
			JSONArray oGeoFeatures = new JSONArray(new JSONTokener(oIn));
			oGeoFeatures.write(oOut);
		}
		
		return HttpServletResponse.SC_OK;
	}
	
	
	/**
	 * Gets a list of Scenario objects that the given user created. If {@code sUserName}
	 * is null then all Scenarios are returned.
	 * 
	 * @param sUserName user name to check against the user name that created
	 * the Scenarios.
	 * @return a list of Scenario objects that the given user created. If {@code sUserName}
	 * is null then all Scenarios are returned.
	 * @throws IOException
	 */
	public ArrayList<Scenario> getScenarios(String sUserName)
		throws IOException
	{
		ArrayList<Scenario> oScenarios = new ArrayList();
		Path oDir = Paths.get(m_sBaseDir);
		String sEndsWith = m_sConfigFf.substring(m_sConfigFf.lastIndexOf("/") + 1);
		List<Path> oPaths = Files.walk(oDir, 2, FileVisitOption.FOLLOW_LINKS)
			.filter(oPath -> Files.isRegularFile(oPath) && oPath.toString().endsWith(sEndsWith))
			.collect(Collectors.toList());
		for (Path oPath : oPaths)
		{
			Scenario oScenario = new Scenario(oPath);
			if ((sUserName == null || sUserName.compareTo(oScenario.m_sUser) == 0 || oScenario.m_bIsShared))
				oScenarios.add(oScenario);
		}
		
		return oScenarios;
	}
	
	
	/**
	 * Get the Scenario object with the given id. This can be a UUID for Scenario
	 * Templates or a Shake256 squeeze algorithm id for Scenarios that have been
	 * processed.
	 * 
	 * @param sId Scenario id.
	 * @return The Scenario with the given id if it exists, otherwise null
	 * @throws IOException
	 */
	public Scenario getScenario(String sId)
		throws IOException
	{
		Path oPath = Paths.get(m_sBaseDir + String.format(m_sConfigFf, sId));
		if (Files.exists(oPath))
			return new Scenario(oPath);
		else
			return null;
	}
	
	
	/**
	 * Iterates through the directories in the Scenarios base directory and
	 * processes any Scenarios that need to be processed.
	 */
	@Override
	public void execute()
	{
		try
		{
			m_oLogger.debug("Processing Scenarios");
			WayNetworks oWays = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
			ArrayList<Scenario> oProcess = new ArrayList();
			for (Scenario oScenario : getScenarios(null))
			{
				if (!oScenario.m_bRun) // skip templates
					continue;
				
				Path oDataFile = Paths.get(m_sBaseDir + String.format(m_sDataFf, oScenario.m_sId));
				Path oProcessedFile = Paths.get(m_sBaseDir + String.format(m_sDataFf, oScenario.m_sId).replace("data", "processed"));
				if (Files.exists(oDataFile) || Files.exists(oProcessedFile)) // skip scenarios that have already been processed
					continue;
				oProcess.add(oScenario);
			}
			for (Scenario oScenario : oProcess)
			{
				m_oLogger.debug("Processing scenario: " + oScenario.m_sId);
				Path oDataFile = Paths.get(m_sBaseDir + String.format(m_sDataFf, oScenario.m_sId));
				Path oProcessedFile = Paths.get(m_sBaseDir + String.format(m_sDataFf, oScenario.m_sId).replace("data", "processed"));
				boolean bWriteProcessed = oScenario.m_bRunTraffic;
				try
				{
					MetroProcess oMetro = null;
					
					if (oScenario.m_bRunRoadWeather)
					{
						oMetro = new MetroProcess(oScenario.m_oGroups[0].m_bPlowing.length); 
						m_oLogger.debug("Starting metro for: " + oScenario.m_sId);
						oMetro.process(oScenario); // road weater prediction
						m_oLogger.debug("Finished metro for: " + oScenario.m_sId);
					}
					if (oScenario.m_bRunTraffic)
					{
						m_oLogger.debug("Starting mlp for: " + oScenario.m_sId);
						MLP oMLP = (MLP)Directory.getInstance().lookup("MLP");
						bWriteProcessed = oMLP.queueOneshot(oScenario, m_sBaseDir);
						m_oLogger.debug("Finished queuing mlp for: " + oScenario.m_sId);
					}

					JSONArray oGeoJsonFeatures = new JSONArray();
					int nMetroCount = 0;
					for (SegmentGroup oGroup : oScenario.m_oGroups) // create the JSONArray that contains all of the data
					{
						for (Id oId : oGroup.m_oSegments)
						{
							OsmWay oWay = oWays.getWayById(oId);
							if (oWay == null)
								continue;
							JSONObject oFeature = new JSONObject();
							JSONObject oGeo = new JSONObject();
							JSONArray oCoords = new JSONArray();
							for (OsmNode oN : oWay.m_oNodes)
							{
								oCoords.put(new double[]{GeoUtil.fromIntDeg(oN.m_nLon), GeoUtil.fromIntDeg(oN.m_nLat)});
							}
							oGeo.put("coordinates", oCoords);
							oGeo.put("type", "LineString");
							JSONObject oProps = new JSONObject();
							WayMetadata oMetadata = oWays.getMetadata(oId);
							oProps.put("imrcpid", oId.toString());
							if (oScenario.m_bRunRoadWeather)
							{
								oProps.put("stpvt", oMetro.m_oStPvts.get(nMetroCount));
								oProps.put("tpvt", oMetro.m_oTPvts.get(nMetroCount));
								oProps.put("dphsn", oMetro.m_oDphsns.get(nMetroCount));
							}
							else
							{
								oProps.put("stpvt", NODATA);
								oProps.put("tpvt", NODATADOUBLES);
								oProps.put("dphsn", NODATADOUBLES);
							}
							if (!bWriteProcessed)
								oProps.put("spdlnk", NODATA);
							oProps.put("spdlimit", oMetadata.m_nSpdLimit);
							oProps.put("lanecount", oMetadata.m_nLanes);
							oFeature.put("type", "Feature");
							oFeature.put("properties", oProps);
							oFeature.put("geometry", oGeo);
							oGeoJsonFeatures.put(oFeature);
							++nMetroCount;
						}
					}
					
					try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(bWriteProcessed ? oProcessedFile : oDataFile, FileUtil.WRITE, FileUtil.FILEPERS), "UTF-8"))) // write the file
					{
						oGeoJsonFeatures.write(oOut);
					}
				}
				catch (Exception oEx)
				{
					Files.deleteIfExists(oProcessedFile);
					Files.createFile(oProcessedFile);
					m_oLogger.error(oEx, oEx);
				}
			}
			m_oLogger.debug("Finished processing scenarios");
			
			oProcess.clear();
			for (Scenario oScenario : getScenarios(null))
			{
				if (!oScenario.m_bRun) // skip templates
					continue;
				
				Path oDataFile = Paths.get(m_sBaseDir + String.format(m_sDataFf, oScenario.m_sId));
				Path oProcessedFile = Paths.get(m_sBaseDir + String.format(m_sDataFf, oScenario.m_sId).replace("data", "processed"));
				if (Files.exists(oDataFile) || Files.exists(oProcessedFile)) // skip scenarios that have already been processed
					continue;
				oProcess.add(oScenario);
			}
			if (!oProcess.isEmpty())
				Scheduling.getInstance().scheduleOnce(this, 1000);
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
}
