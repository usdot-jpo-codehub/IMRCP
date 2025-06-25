/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.web;

import imrcp.geosrv.Network;
import imrcp.geosrv.WayNetworks;
import imrcp.system.Directory;
import imrcp.system.Emails;
import imrcp.system.FileUtil;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 *
 * @author Federal Highway Administration
 */
public class CommonMapServlet extends SecureBaseBlock
{
	private String m_sGeojsonFf;
	private final String m_sGeojsonRegex = "^[a-zA-z0-9_\\-]+$";
	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_sGeojsonFf = oBlockConfig.optString("layerff", "%s/geojson/%s.json");
	}
	
	
	public int doProfile(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oClient)
		throws IOException
	{
		WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
		JSONArray oNetworks = new JSONArray();
		oRes.setContentType("application/json");
		for (String sId : oSession.m_oProfile.m_sNetworks)
		{
			Network oNetwork = oWayNetworks.getNetwork(sId);
			if (oNetwork != null && oNetwork.isStatus(Network.PUBLISHED))
			{
				JSONObject oNetworkObj = new JSONObject();
				oNetworkObj.put("id", sId);
				oNetworkObj.put("label", oNetwork.m_sLabel);
				oNetworks.put(oNetworkObj);
			}
		}

		JSONObject oResponse = new JSONObject();
		oResponse.put("networks", oNetworks);
		try (PrintWriter oOut = oRes.getWriter())
		{
			oResponse.write(oOut);
		}
		return HttpServletResponse.SC_OK;
	}
	
	
	/**
	 * Adds a list of all of the networks in the system to the response.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @param oSession object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws ServletException
	 * @throws IOException
	 */
	public int doNetworkList(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oClient)
		throws ServletException, IOException
	{
		WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
		oRes.setContentType("application/json");
		ArrayList<Network> oNetworks = oWayNetworks.getAllNetworks();
		JSONArray oGeoJson = new JSONArray();
		for (Network oNetwork : oNetworks)
		{
			oGeoJson.put(oNetwork.toGeoJsonFeature(true));
		}
		try (PrintWriter oOut = oRes.getWriter())
		{
			oGeoJson.write(oOut);
		}
		
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
	public int doSaveGeojson(HttpServletRequest oReq, HttpServletResponse oRes, Session oSess, ClientConfig oClient)
		throws IOException, ServletException
	{
		oRes.setContentType("application/json");
		String sGeojson = oReq.getParameter("geojson");
		String sLabel = oReq.getParameter("label");
		if (sGeojson == null || sLabel == null || !Pattern.matches(m_sGeojsonRegex, sLabel))
			return HttpServletResponse.SC_BAD_REQUEST;
		
		try
		{
			JSONObject oGeojson = new JSONObject(new JSONTokener(sGeojson));
			String sUserDir = SessMgr.getInstance().m_sUserProfileFf;
			sUserDir = sUserDir.substring(0, sUserDir.lastIndexOf("/"));
			sUserDir = String.format(sUserDir, oSess.m_sName);
			Path oPath = Paths.get(String.format(m_sGeojsonFf, sUserDir, sLabel));
			Files.createDirectories(oPath.getParent());
			try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oPath, FileUtil.WRITE, FileUtil.FILEPERS), "UTF-8")))
			{
				oGeojson.write(oOut);
			}
			JSONObject oResponse = new JSONObject();
			oResponse.put("geojson", oGeojson);
			try (PrintWriter oOut = oRes.getWriter())
			{
				oResponse.write(oOut);
			}
		}
		catch (JSONException oEx)
		{
			oRes.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			JSONObject oResponse = new JSONObject();
			oResponse.put("status", "Geojson file upload failed");
			oResponse.put("msg", oEx.getLocalizedMessage());
			try (PrintWriter oOut = oRes.getWriter())
			{
				oResponse.write(oOut);
			}
			return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
		
		return HttpServletResponse.SC_OK;
	}
	
	
	public int doDeleteGeojson(HttpServletRequest oReq, HttpServletResponse oRes, Session oSess, ClientConfig oClient)
		throws IOException, ServletException
	{
		oRes.setContentType("application/json");
		String sLabel = oReq.getParameter("label");
		String sUserDir = SessMgr.getInstance().m_sUserProfileFf;
		sUserDir = sUserDir.substring(0, sUserDir.lastIndexOf("/"));
		sUserDir = String.format(sUserDir, oSess.m_sName);
		Path oPath = Paths.get(String.format(m_sGeojsonFf, sUserDir, sLabel));
		JSONObject oResponse = new JSONObject();
		try
		{
			Files.delete(oPath);
		}
		catch (Exception oEx)
		{
			oRes.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			oResponse.put("status", "Geojson file delete failed");
			oResponse.put("msg", oEx.getLocalizedMessage());
		}
		try (PrintWriter oOut = oRes.getWriter())
		{
			oResponse.write(oOut);
		}
		
		return HttpServletResponse.SC_OK;
	}
	
	
	public int doListGeojson(HttpServletRequest oReq, HttpServletResponse oRes, Session oSess, ClientConfig oClient)
		throws IOException, ServletException
	{
		oRes.setContentType("application/json");
		JSONObject oResponse = new JSONObject();
		JSONArray oGeojsons = new JSONArray();
		String sUserDir = SessMgr.getInstance().m_sUserProfileFf;
		sUserDir = sUserDir.substring(0, sUserDir.lastIndexOf("/"));
		sUserDir = String.format(sUserDir, oSess.m_sName);
		Path oGeojsonDir = Paths.get(String.format(m_sGeojsonFf, sUserDir, "")).getParent();
		if (Files.exists(oGeojsonDir))
		{
			try (DirectoryStream oStream = Files.newDirectoryStream(oGeojsonDir, oPath -> Files.isRegularFile(oPath) && oPath.toString().endsWith(".json")))
			{
				List<Path> oFiles = (List)StreamSupport.stream(oStream.spliterator(), false).collect(Collectors.toList());
				for (Path oFile : oFiles)
				{
					String sFilename = oFile.getFileName().toString();
					sFilename = sFilename.substring(0, sFilename.lastIndexOf(".json"));
					oGeojsons.put(sFilename);
				}
			}
		}
		oResponse.put("geojsons", oGeojsons);
		
		try (PrintWriter oOut = oRes.getWriter())
		{
			oResponse.write(oOut);
		}
		
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
	public int doGetGeojson(HttpServletRequest oReq, HttpServletResponse oRes, Session oSess, ClientConfig oClient)
		throws IOException, ServletException
	{
		oRes.setContentType("application/json");
		JSONObject oResponse = new JSONObject();
		String sUserDir = SessMgr.getInstance().m_sUserProfileFf;
		sUserDir = sUserDir.substring(0, sUserDir.lastIndexOf("/"));
		sUserDir = String.format(sUserDir, oSess.m_sName);
		Path oGeojsonDir = Paths.get(String.format(m_sGeojsonFf, sUserDir, "")).getParent();
		if (Files.exists(oGeojsonDir))
		{
			try (DirectoryStream oStream = Files.newDirectoryStream(oGeojsonDir, oPath -> Files.isRegularFile(oPath) && oPath.toString().endsWith(".json")))
			{
				List<Path> oFiles = (List)StreamSupport.stream(oStream.spliterator(), false).collect(Collectors.toList());
				for (Path oFile : oFiles)
				{
					try (BufferedReader oIn = Files.newBufferedReader(oFile, StandardCharsets.UTF_8))
					{
						String sFilename = oFile.getFileName().toString();
						sFilename = sFilename.substring(0, sFilename.lastIndexOf(".json"));
						oResponse.put(sFilename, new JSONObject(new JSONTokener(oIn)));
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
	
	public int doBug(HttpServletRequest oReq, HttpServletResponse oRes, Session oSess, ClientConfig oClient)
		throws IOException, ServletException
	{
		oRes.setContentType("application/json");
		JSONObject oResponse = new JSONObject();
		String sBug = oReq.getParameter("bug");
		String sPage = oReq.getParameter("page");
		String sUser = oSess.m_sName;
		String sUserEmail = oSess.m_sContact;
		
		Emails oEmails = (Emails)Directory.getInstance().lookup("Emails");
		String sSupportBody = String.format("%s submitted the following bug while on the %s page:\n\n%s", sUser, sPage, sBug);
		String sUserBody = String.format("Thank you for submitting a bug for review. This is an unmonitored account, do not directly reply to this email address.\n\nBug Description:\n%s", sBug);
		try
		{
			oEmails.send("IMRCP - Bug Notification", sSupportBody, oEmails.getSupportEmail());
			oEmails.send("IMRCP - Bug Received", sUserBody, sUserEmail);
			
		}
		catch (Exception oEx)
		{
			oRes.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			oResponse.put("status", "Failed to send emails");
			oResponse.put("msg", oEx.getLocalizedMessage());
		}
		
		try (PrintWriter oOut = oRes.getWriter())
		{
			oResponse.write(oOut);
		}
		
		return HttpServletResponse.SC_OK;
	}
}
