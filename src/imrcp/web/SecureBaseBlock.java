/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.web;

import imrcp.system.Arrays;
import imrcp.system.BaseBlock;
import imrcp.system.Introsort;
import imrcp.system.JSONUtil;
import imrcp.system.Text;
import java.io.IOException;
import java.lang.reflect.Method;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import org.apache.logging.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Base class used for system components that receive remote requests from the
 * IMRCP Web Application.
 * @author aaron.cherney
 */
public class SecureBaseBlock extends BaseBlock
{
	/**
	 * Contains the user groups allowed to access the methods in this SecureBaseBlock
	 */
	private String[] m_sGroups;

	
	/**
	 * Index used to parse the method name from the request URL
	 */
	private int m_nMethodIndex;
	
	
	/**
	 * Default constructor. Wrapper from {@link BaseBlock#BaseBlock()}
	 */
	protected SecureBaseBlock()
	{
		super();
	}
	
	private static boolean READSERVERCONFIG = false;
	protected static final ArrayList<ServerConfig> SERVERS = new ArrayList(1);
	protected static final ArrayList<ClientConfig> CLIENTS = new ArrayList(1);
	
	
	/**
	 * Initializes the block. Wrapper for {@link #setName(java.lang.String)}, 
	 * {@link #setLogger()}, {@link #setConfig()}, {@link #register()}, and 
	 * {@link #startService()}
	 * 
	 * @param oSConfig object containing configuration parameters in Tomcat's
	 * @throws ServletException
	 */
	@Override
	public void init()
		throws ServletException
	{
		try
		{
			ServletConfig oSConfig = getServletConfig();
			setName(oSConfig.getServletName());
			setLogger();
			reset();
			register();
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
			throw new ServletException(oEx);
		}
	}
	
	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_nMethodIndex = oBlockConfig.optInt("metidx", 3);
		m_sGroups = JSONUtil.optStringArray(oBlockConfig, "groups", "imrcp-user", "imrcp-admin");
		synchronized (SERVERS)
		{
			if (!READSERVERCONFIG)
			{
				READSERVERCONFIG = true;
				JSONArray oClients = JSONUtil.optJSONArray(oBlockConfig, "clients");
				CLIENTS.ensureCapacity(oClients.length());
				for (int nIndex = 0; nIndex < oClients.length(); nIndex++)
				{
					JSONObject oClient = oClients.getJSONObject(nIndex);
					try
					{
						InetAddress oHost = InetAddress.getByName(oClient.getString("host"));
						String sUUID = oClient.getString("uuid");
						JSONArray oObsTypes = oClient.getJSONArray("obstypes");
						int[] nObsTypes = new int[oObsTypes.length() + 2];
						for (int nObsIndex = 0; nObsIndex < nObsTypes.length - 2; nObsIndex++)
							nObsTypes[nObsIndex] = Integer.valueOf(oObsTypes.getString(nObsIndex), 36);
						nObsTypes[nObsTypes.length - 2] = Integer.valueOf("rnm", 36);
						nObsTypes[nObsTypes.length - 1] = Integer.valueOf("rnp", 36);
						CLIENTS.add(new ClientConfig(sUUID, oHost, nObsTypes));
						
					}
					catch (Exception oEx)
					{
						m_oLogger.error(oEx, oEx);
					}
				}
				
				JSONArray oServers = JSONUtil.optJSONArray(oBlockConfig, "servers");
				SERVERS.ensureCapacity(oServers.length());
				for (int nIndex = 0; nIndex < oServers.length(); nIndex++)
				{
					JSONObject oServer = oServers.getJSONObject(nIndex);
					try
					{
						InetAddress oHost = InetAddress.getByName(oServer.getString("host"));
						String sUUID = oServer.getString("uuid");
						JSONArray oObsTypes = oServer.getJSONArray("obstypes");
						
						for (int nObsIndex = 0; nObsIndex < oObsTypes.length(); nObsIndex++)
						{
							int nObsType = Integer.valueOf(oObsTypes.getString(nObsIndex), 36);
							SERVERS.add(new ServerConfig(sUUID, oHost, nObsType));
						}
						SERVERS.add(new ServerConfig(sUUID, oHost, Integer.valueOf("rnm", 36)));
						SERVERS.add(new ServerConfig(sUUID, oHost, Integer.valueOf("rnp", 36)));
					}
					catch (Exception oEx)
					{
						m_oLogger.error(oEx, oEx);
					}
				}
				Introsort.usort(SERVERS);
			}
		}
	}
	
	
	/**
	 * Wrapper for {@link #doPost(jakarta.servlet.http.HttpServletRequest, jakarta.servlet.http.HttpServletResponse)}
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @throws IOException
	 * @throws ServletException
	 */
	@Override
	public void doGet(HttpServletRequest oReq, HttpServletResponse oRes)
	   throws IOException, ServletException
	{
		doPost(oReq, oRes);
	}
	
	
	/**
	 * Processes the given request by first authenticating the request and then
	 * calling the appropriate method.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @throws IOException
	 * @throws ServletException
	 */
	@Override
	public void doPost(HttpServletRequest oReq, HttpServletResponse oRes)
	   throws IOException, ServletException
	{
		Session oSession = SessMgr.getInstance().getSession(false, SessMgr.getToken(oReq));
		ClientConfig oClient = null;
		boolean bOverride = false;
		if (oSession == null) // possible request coming from another instance of IMRCP
		{
			String sCode = oReq.getParameter("uuid");
			if (sCode == null)
			{
				oRes.sendError(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}
			
			String sIp = oReq.getHeader("X-Real-IP");
			if (sIp == null)
			{
				sIp = oReq.getHeader("X-Forwarded-for");
				if (sIp == null)
					sIp = oReq.getRemoteAddr();
			}
			for (int nIndex = 0; nIndex < CLIENTS.size(); nIndex++)
			{
				ClientConfig oTemp = CLIENTS.get(nIndex);
				if (oTemp.m_sUUID.compareTo(sCode) == 0 && oTemp.m_oHost.getHostAddress().compareTo(sIp) == 0)
				{
					bOverride = true;
					oClient = oTemp;
					break;
				}
			}
			if (!bOverride)
			{
				oRes.sendError(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}
		}
		if (!bOverride)
		{
			boolean bValidGroup = false;
			for (String sGroup : m_sGroups)
			{
				if (oSession.m_sGroup.contains(sGroup))
				{
					bValidGroup = true;
					break;
				}
			}

			if (!bValidGroup)
			{
				oRes.sendError(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}
		}
		
		StringBuilder sMethod = new StringBuilder("do");
		String[] sUrlParts = oReq.getRequestURI().split("/");
		sMethod.append(sUrlParts[m_nMethodIndex]); // get method name

		sMethod.setCharAt(2, Character.toUpperCase(sMethod.charAt(2))); // upper case the first character of the action
		if (Text.compare(sMethod, "doPost") == 0 || Text.compare(sMethod, "doGet") == 0)
		{
			oRes.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		try
		{
			Method oMethod = getClass().getMethod(sMethod.toString(), HttpServletRequest.class, HttpServletResponse.class, Session.class, ClientConfig.class);
			oRes.setStatus((int)oMethod.invoke(this, oReq, oRes, oSession, oClient));
		}
		catch (Exception oEx)
		{
			oRes.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			throw new ServletException(oEx);
		}
	}
	
	static void getRemoteNetworkOutlines()
	{
		ServerConfig oPrev = new ServerConfig("", null, 0);
		for (int nIndex = 0; nIndex < SERVERS.size(); nIndex++)
		{
			ServerConfig oConfig = SERVERS.get(nIndex);
			if (oConfig.m_sUUID.compareTo(oPrev.m_sUUID) != 0)
			{
				try
				{
					HttpsURLConnection oConn = getTrustingConnection(String.format("https://%s%s?uuid=%s", oConfig.m_oHost.getHostName(), "/api/networkGeo", URLEncoder.encode(oConfig.m_sUUID, StandardCharsets.UTF_8)));
					oConn.setConnectTimeout(500);
					oConn.setReadTimeout(3000);
					try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(oConn.getInputStream())))
					{
						if (oConn.getResponseCode() != HttpServletResponse.SC_OK || oConn.getContentLengthLong() == 0)
							break;
						
						ArrayList<int[]> oGeos = new ArrayList();
						while (oIn.available() > 0)
						{
							int nSize = oIn.readInt(); // response is a list of growable arrays so the first value read for each entry is the insertion point so subtract one to get the number of values to read
							int[] nGeo = Arrays.newIntArray(nSize);
							nGeo[0] = nSize;
							for (int nGeoIndex = 1; nGeoIndex < nSize; nGeoIndex++)
							{
								nGeo[nGeoIndex] = oIn.readInt();
							}
							oGeos.add(nGeo);
						}
						oConfig.m_oNetworkGeometries = oGeos;
					}
				}
				catch (Exception oEx)
				{
					LogManager.getLogger(SecureBaseBlock.class).error(oEx, oEx);
				}
			}
			else
			{

				oConfig.m_oNetworkGeometries = oPrev.m_oNetworkGeometries;
			}
			oPrev = oConfig;
		}
	}
	
	
	public static HttpsURLConnection getTrustingConnection(String sUrl)
		throws Exception
	{
		URL oUrl = new URL(sUrl);
		HttpsURLConnection oConn = (HttpsURLConnection)oUrl.openConnection();
		SSLContext sc = SSLContext.getInstance("SSL");
		sc.init(null, new TrustManager[]{new AlwaysTrustManager()}, new java.security.SecureRandom());
		HostnameVerifier allHostsValid = new HostnameVerifier() {
			public boolean verify(String hostname, SSLSession session) {
			 return true;
		   }
		 };
		oConn.setHostnameVerifier(allHostsValid);
		oConn.setSSLSocketFactory(sc.getSocketFactory());
		return oConn;
	}
}
