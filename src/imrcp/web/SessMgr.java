package imrcp.web;

import imrcp.geosrv.Network;
import imrcp.geosrv.WayNetworks;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import imrcp.system.CsvReader;
import imrcp.system.Directory;
import imrcp.system.Emails;
import imrcp.system.FileUtil;
import imrcp.system.Scheduling;
import imrcp.system.Text;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jakarta.servlet.http.Cookie;
import java.io.BufferedReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * This singleton class manages the system Users and their Sessions using the
 * IMRCP Web Application.
 * @author aaron.cherney
 */
public class SessMgr extends HttpServlet implements Comparator<Session>
{
	/**
	 * Time in milliseconds since Epoch the IMRCP user CSV file was read to
	 * refresh User definitions.
	 */
	protected static long LAST_REFRESH = 0L;

	
	/**
	 * Time in milliseconds used to determine if a Session is stale
	 */
	protected static long TIMEOUT = 1800000; // default 30-minute sessions 

	
	/**
	 * MessageDigest used for one-way hash algorithms
	 */
	protected static MessageDigest DIGEST;

	
	/**
	 * Singleton instance of the class
	 */
	private static SessMgr INSTANCE;

	
	/**
	 * Random number generator object
	 */
	private static SecureRandom RNG;

	
	/**
	 * Stores the current active IMRCP Web Application sessions
	 */
	private static final ArrayList<Session> SESSIONS = new ArrayList();

	
	/**
	 * Stores all of the users in the system
	 */
	private static final ArrayList<Session> USERS = new ArrayList();

	
	/**
	 * Stores password reset requests by mapping a 256-bit reset key to the
	 * user requesting the reset.
	 */
	private static final TreeMap<String, Session> RESET = new TreeMap();

	
	/**
	 * Stores the request handles by their unique last two characters
	 */
	private static final TreeMap<String, Handler> HANDLERS = new TreeMap();

	
	/**
	 * Convenience search object for Sessions
	 */
	private static final Session SEARCH_SESS = new Session();

	
	/**
	 * Convenience search object for Users
	 */
	private static final Session SEARCH_USER = new Session();

	
	/**
	 * Log4j Logger
	 */
	private static final Logger LOGGER = LogManager.getLogger(SessMgr.class);

	
	/** 
	 * Path to the IMRCP user CSV file
	 */
	private String m_sPwdFile;

	String m_sUserDir;
	
	/**
	 * Format String used to generate User Profile file paths
	 */
	String m_sUserProfileFf;


	/**
	 * Initialize the the MessageDigest and RNG objects
	 */
	static
	{
		try
		{
			DIGEST = MessageDigest.getInstance("SHA-256");
			RNG = SecureRandom.getInstance("NativePRNGNonBlocking");
		}
		catch (Exception oEx)
		{
		}
	}

	
	/**
	 * Gets the instance of the singleton
	 * @return The SessMgr singleton instance
	 */
	public static SessMgr getInstance()
	{
		return INSTANCE;
	}

	
	/**
	 * Default constructor. Initializes the handlers
	 */
	public SessMgr()
	{
		HANDLERS.put("in", new SessMgrLogin());
		HANDLERS.put("ck", new SessMgrCheck());
		HANDLERS.put("ut", new SessMgrLogout());
		HANDLERS.put("et", new SessMgrReset());
		HANDLERS.put("te", new SessMgrUpdate());
		HANDLERS.put("le", new SessMgrProfile());
	}

	
	/**
	 * Initializes the servlet by reading all of the configuration parameters from
	 * the ServletConfig object.
	 */
	@Override
	public void init()
	{
		ServletConfig oConf = getServletConfig();

		m_sPwdFile = oConf.getInitParameter("pwdfile");
		m_sUserDir = oConf.getInitParameter("userdir");
		if (!m_sUserDir.endsWith("/"))
			m_sUserDir += "/";
		m_sUserProfileFf = m_sUserDir + "%s/profile.csv";		

		String sExp = oConf.getInitParameter("timeout");
		if (sExp != null)
			TIMEOUT = Text.parseInt(sExp);

		INSTANCE = this;
	}

	
	public String getUserDir()
	{
		return m_sUserDir;
	}
	
	/**
	 * Manages all of the requests sent dealing with user authentication and
	 * sessions of the IMRCP Web Application. It does this by parsing the request
	 * and calling the appropriate Handler.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRep object that contains the response the servlet sends to the client
	 */
	@Override
	public void doPost(HttpServletRequest oReq, HttpServletResponse oRep)
	{
		String sPath = oReq.getPathInfo(); // last two chars are unique
		if (sPath.length() < 2 || sPath.length() > 8) // check for valid requests
			return;
		
		Handler iHandler = HANDLERS.get(sPath.substring(sPath.length() - 2)); // look up the handler by last two unique characters
		if (iHandler == null) // ignore requests that do not have a handler
			return;

		StringBuilder sBuf = new StringBuilder("{");
		String sStatus = iHandler.process(sBuf, oReq); // process the request and get the response in the StringBuilder
		sBuf.append("\"status\":\"").append(sStatus).append("\"}");

		oRep.setContentType("application/json");
		try (ServletOutputStream oOut = oRep.getOutputStream()) // write response
		{
			for (int nIndex = 0; nIndex < sBuf.length(); nIndex++)
				oOut.print(sBuf.charAt(nIndex));

			oOut.close(); // finazlize output
		}
		catch (Exception oEx)
		{
		}
	}


	/**
	 * Compares Session by session token
	 */
	@Override
	public int compare(Session oLhs, Session oRhs)
	{
		return oLhs.m_sToken.compareTo(oRhs.m_sToken); // order by session id
	}

	
	/**
	 * Uses {@link #DIGEST} to get the hashed password String given the plain
	 * test password and salt bytes and places it in the given StringBuilder
	 * 
	 * @param sPass plain text password
	 * @param ySalt salt bytes used to hash password
	 * @param sBuf buffer that gets filled with the hashed password as a hex string
	 */
	static void getSecurePassword(String sPass, byte[] ySalt, StringBuilder sBuf)
	{
		synchronized(DIGEST)
		{
			DIGEST.reset();
			DIGEST.update(ySalt);
			Text.toHexString(DIGEST.digest(sPass.getBytes()), sBuf);
		}
	}

	
	/**
	 * Gets the session token from the given request. The token can be a parameter
	 * in the body of the request if it the method is POST or in a Cookie if the
	 * method is GET.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @return The session token if it can be found in the request, otherwise
	 * null.
	 */
	static String getToken(HttpServletRequest oReq)
	{
		String sToken = null;
		if (oReq.getMethod().compareTo("POST") == 0)
			sToken = oReq.getParameter("token");
		else
		{
			Cookie[] oCookies = oReq.getCookies();
			if (oCookies != null)
			{
				for (Cookie oCookie : oCookies)
				{
					if (oCookie.getName().compareTo("token") == 0)
						sToken = oCookie.getValue();
				}
			}
		}
		return sToken;
	}

	
	/**
	 * Gets the active Session associated with the given session token.
	 * 
	 * @param bRemove flag indicating if the Session should be removed from the
	 * active list
	 * @param sToken requested session token
	 * @return The Session associated with the session token if it exists, otherwise
	 * null
	 */
	Session getSession(boolean bRemove, String sToken)
	{
		if (sToken == null) // invalid token
			return null;
		synchronized(SESSIONS)
		{
			SEARCH_SESS.m_sToken = sToken;
			int nIndex = Collections.binarySearch(SESSIONS, SEARCH_SESS, INSTANCE); // search for Session by token
			if (nIndex >= 0)
			{
				if (bRemove)
					return SESSIONS.remove(nIndex);

				Session oSess = SESSIONS.get(nIndex); // update session access time
				oSess.m_lLastAccess = System.currentTimeMillis();
				return oSess;
			}
		}
		return null;
	}

	
	/**
	 * Gets the Session(User) from {@link #USERS} with the given user name.
	 * 
	 * @param sUname User name used to search for the Session(User)
	 * @return the Session(User) with the given user name if it exists in {@link #USERS},
	 * otherwise null.
	 */
	Session getUser(String sUname)
	{
		long lNow = System.currentTimeMillis();
		synchronized(USERS)
		{
			if (lNow - LAST_REFRESH > TIMEOUT) // refresh user credentials as needed
			{
				LAST_REFRESH = lNow;
				readUserFile();
			}

			SEARCH_USER.m_sName = sUname; // find session by username
			int nIndex = Collections.binarySearch(USERS, SEARCH_USER, SEARCH_USER);
			if (nIndex >= 0)
				return USERS.get(nIndex);
		}
		return null;
	}
	
	
	private void writeUserFile()
	{
		long lNow = System.currentTimeMillis();
		synchronized(USERS)
		{
			LAST_REFRESH = lNow;
			try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(Paths.get(m_sPwdFile), FileUtil.WRITE), StandardCharsets.UTF_8)))
			{
				for (Session oUser : USERS)
				{
					oUser.writeUser(oOut);
				}
			}
			catch (IOException oEx)
			{
				LOGGER.error(oEx, oEx);
			}
			readUserFile();
		}
	}
	
	
	private void readUserFile()
	{
		try 
		{
			try (CsvReader oCsv = new CsvReader(Files.newInputStream((Paths.get(m_sPwdFile)))))
			{
				while (oCsv.readLine() > 0)
				{
					Session oUser = new Session(oCsv);
					int nIndex = Collections.binarySearch(USERS, oUser, oUser);
					if (nIndex < 0)
						USERS.add(~nIndex, oUser);
					else
						USERS.get(nIndex).update(oUser); // later entries are newer
				}
			}
		}
		catch (Exception oEx)
		{
			LOGGER.error(oEx, oEx);
		}
	}
	
	
	void updateUser(Session oUser)
	{
		boolean bNew = false;
		synchronized (USERS)
		{
			int nIndex = Collections.binarySearch(USERS, oUser, oUser);
			if (nIndex < 0)
			{
				USERS.add(~nIndex, oUser);
				bNew = true;
			}
			else
				USERS.get(nIndex).update(oUser);
			writeUserFile();
		}
		if (oUser.m_sDeactivation.length() > 0) // deactivated user so remove an current sessions
		{
			synchronized (SESSIONS)
			{
				int nIndex = SESSIONS.size();
				while (nIndex-- > 0)
				{
					Session oSess = SESSIONS.get(nIndex);
					if (oSess.m_sName.compareTo(oUser.m_sName) == 0)
						SESSIONS.remove(nIndex);
				}
			}
		}
		
		if (bNew)
		{
			StringBuilder sBuf = new StringBuilder();
			WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
			ArrayList<Network> oPublishedNetworks = oWayNetworks.getNetworks();
			for (Network oNetwork : oPublishedNetworks)
			{
				sBuf.append(oNetwork.m_sNetworkId).append(',');
			}
			if (!oPublishedNetworks.isEmpty())
				sBuf.setLength(sBuf.length() - 1);
			sBuf.append('\n');
			
			Path oPath = Paths.get(String.format(m_sUserProfileFf, oUser.m_sName));
			try
			{
				Files.createDirectories(oPath.getParent(), FileUtil.DIRPERS);
				try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oPath, FileUtil.WRITE), StandardCharsets.UTF_8)))
				{
					oOut.append(sBuf);
				}
			}
			catch (Exception oEx)
			{
				LOGGER.error(oEx, oEx);
			}
		}
		
	}
	
	
	void getUserTable(JSONArray oData, String sUser)
	{
		synchronized (USERS)
		{
			for (Session oUser : USERS)
			{
				if (sUser.compareTo(oUser.m_sName) == 0)
					continue;
				JSONArray oUserArray = new JSONArray();
				oUserArray.put(oUser.m_sName);
				oUserArray.put(oUser.m_sGroup);
				oUserArray.put(oUser.m_sDeactivation);
				oData.put(oUserArray);
			}
		}
	}

	public void addNetwork(String sNetworkId)
	{
		ArrayList<Session> oUsers = new ArrayList();
		synchronized (USERS)
		{
			for (Session oUser : USERS)
				oUsers.add(oUser);
		}
		StringBuilder sBuf = new StringBuilder();
		for (Session oUser : oUsers)
		{
			Path oPath = Paths.get(String.format(m_sUserProfileFf, oUser.m_sName));
			sBuf.setLength(0);
			if (Files.exists(oPath))
			{
				try (BufferedReader oIn = Files.newBufferedReader(oPath))
				{
					String sNetworksLine = oIn.readLine();
					sBuf.append(sNetworksLine);
					if (sBuf.indexOf(sNetworkId) < 0)
						sBuf.append(',').append(sNetworkId);
					sBuf.append('\n');
				}
				catch (Exception oEx)
				{
					LOGGER.error(oEx, oEx);
				}
			}
	
			try
			{
				Files.createDirectories(oPath.getParent(), FileUtil.DIRPERS);
				try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oPath, FileUtil.WRITE), StandardCharsets.UTF_8)))
				{
					oOut.append(sBuf);
				}
			}
			catch (Exception oEx)
			{
				LOGGER.error(oEx, oEx);
			}
		}
		synchronized (SESSIONS)
		{
			for (Session oSess : SESSIONS)
			{
				String[] sTemp = new String[oSess.m_oProfile.m_sNetworks.length + 1];
				System.arraycopy(oSess.m_oProfile.m_sNetworks, 0, sTemp, 0, oSess.m_oProfile.m_sNetworks.length);
				sTemp[sTemp.length - 1] = sNetworkId;
				oSess.m_oProfile.m_sNetworks = sTemp;
			}
		}
	}
	
	
	/**
	 * Interface used to define how different requests are handled
	 */
	private interface Handler
	{
		/**
		 * This method is always called in doPost to handle the given request.
		 * The response is placed in the given StringBuilder
		 * 
		 * @param sBuf buffer used to store the response
		 * @param oReq object that contains the request the client has made of the servlet
		 * @return status String of the process
		 */
		public String process(StringBuilder sBuf, HttpServletRequest oReq);
	}

	
	/**
	 * This handler processes user login requests from the IMRCP Web Application
	 */
	private class SessMgrLogin implements Handler
	{
		/**
		 * Processes the user login request.
		 * 
		 * @return "failure" if the login request is invalid, otherwise "success"
		 */
		@Override
		public String process(StringBuilder sBuf, HttpServletRequest oReq)
		{
			String sUname = oReq.getParameter("uname"); // no username
			String sPword = oReq.getParameter("pword"); // password not provided
			if (Text.isEmpty(sUname) || Text.isEmpty(sPword))
				return "failure";

			Session oUser = getUser(sUname);
			if (oUser == null)
			{
				LOGGER.error(sUname + " not found " + oReq.getHeader("X-Real-IP"));
				return "failure"; // log missing user
			}
			
			if (oUser.m_sDeactivation.length() > 0)
			{
				LOGGER.error("Deactivated user " + sUname + " attempted to login " + oReq.getHeader("X-Real-IP"));
				return "failure";
			}

			StringBuilder sSecPass = new StringBuilder();
			getSecurePassword(sPword, oUser.m_ySalt, sSecPass);
			if (Text.compare(oUser.m_sPass, sSecPass) != 0)
			{
				LOGGER.error(sUname + " invalid password " + oReq.getHeader("X-Real-IP"));
				return "failure"; // password doesn't match
			}
			
			Scheduling.getInstance().execute(() -> 
			{
				SecureBaseBlock.getRemoteNetworkOutlines();
			});
			byte[] yBytes = new byte[16];
			synchronized(SESSIONS)
			{
				RNG.nextBytes(yBytes);
				oUser.m_sToken = Text.toHexString(yBytes);
				int nIndex = Collections.binarySearch(SESSIONS, oUser, INSTANCE);
				if (nIndex < 0) // there should be no collisions
				{
					oUser.m_lLastAccess = System.currentTimeMillis();
					SESSIONS.add(~nIndex, oUser); // reset session timeout
				}

				Path oPath = Paths.get(String.format(m_sUserProfileFf, oUser.m_sName));
				if (!Files.exists(oPath))
				{
					StringBuilder sNetworkBuf = new StringBuilder();
					for (Network oNetwork : ((WayNetworks)Directory.getInstance().lookup("WayNetworks")).getNetworks())
						sNetworkBuf.append(oNetwork.m_sNetworkId).append(',');
					if (sNetworkBuf.length() > 0)
						sNetworkBuf.setLength(sNetworkBuf.length() - 1);
					try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oPath, FileUtil.WRITE), StandardCharsets.UTF_8)))
					{
						oOut.append(sNetworkBuf).append('\n');
					}
					catch (Exception oEx)
					{
					}
				}

				try (CsvReader oIn = new CsvReader(Files.newInputStream(oPath)))
				{
					oUser.m_oProfile = new UserProfile(oIn);
				}
				catch (Exception oEx)
				{
					oUser.m_oProfile = new UserProfile();
				}
			}
			
			Path oSettingsFile = Paths.get(String.format("%s%s/settings.json", m_sUserDir, oUser.m_sName));
			JSONObject oSettings = new JSONObject();
			if (Files.exists(oSettingsFile))
			{
				try (BufferedReader oIn = Files.newBufferedReader(oSettingsFile, StandardCharsets.UTF_8))
				{
					oSettings = new JSONObject(new JSONTokener(oIn));
				}
				catch (IOException oEx)
				{
					oSettings = new JSONObject();
				}
			}
			
			if (oSettings.has("page"))
				sBuf.append("\"page\":\"").append(oSettings.getString("page")).append("\",");
			
			sBuf.append("\"token\":\"").append(oUser.m_sToken).append("\",");
			sBuf.append("\"groups\":\"").append(oUser.m_sGroup).append("\",");
			
			LOGGER.error(sUname + " logged in " + oReq.getHeader("X-Real-IP"));
			return "success"; // sucessful login
		}
	}

	
	/**
	 * This handler processes authentication checks requests from the IMRCP Web Application
	 */
	private class SessMgrCheck implements Handler
	{
		/**
		 * Processes the authentication check request.
		 * 
		 * @return "failure" if the authentication credentials are invalid, 
		 * otherwise "success"
		 */
		@Override
		public String process(StringBuilder sBuf, HttpServletRequest oReq)
		{
			String sToken = getToken(oReq);
			if (Text.isEmpty(sToken))
				return "failure"; // no token provided

			Session oSess = getSession(false, sToken); // keep when found
			if (oSess == null)
				return "failure"; // session not found

			sBuf.append("\"token\":\"").append(oSess.m_sToken).append("\",");
			sBuf.append("\"groups\":\"").append(oSess.m_sGroup).append("\",");
			return "success";
		}
	}

	
	/**
	 * This handler processes user logout requests from the IMRCP Web Application
	 */
	private class SessMgrLogout implements Handler
	{
		/**
		 * Processes the user logout request.
		 * 
		 * @return "failure" if the authentication credentials are invalid, 
		 * otherwise "success"
		 */
		@Override
		public String process(StringBuilder sBuf, HttpServletRequest oReq)
		{
			String sToken = getToken(oReq);
			if (Text.isEmpty(sToken))
				return "failure"; // no token provided

			Session oSess = getSession(true, sToken); // remove when found
			if (oSess != null)
			{
				oSess.m_sToken = ""; // clear session token
				LOGGER.error(oSess.m_sName + " logged out " + oReq.getHeader("X-Real-IP"));
			}
			return "success"; // missing session is okay
		}
	}

	
	/**
	 * This handler processes password reset requests from the IMRCP Web Application
	 */
	private class SessMgrReset implements Handler
	{
		/**
		 * Processes the password reset request.
		 * 
		 * @return "failure" if the uname parameter is empty, "lockout" if another
		 * password reset request has been made in the last ten minutes from the
		 * same user, otherwise "success"
		 */
		@Override
		public String process(StringBuilder sBuf, HttpServletRequest oReq)
		{
			String sUname = oReq.getParameter("uname"); // username not provided
			if (Text.isEmpty(sUname))
				return "failure";

			Session oUser = getUser(sUname);
			if (oUser == null)
			{
				LOGGER.error(sUname + " reset not found " + oReq.getHeader("X-Real-IP"));
				return "success"; // ignore missing user for reset
			}

			long lNow = System.currentTimeMillis();
			if (lNow - oUser.m_lReset < 600000) // ten-minute lockout
				return "lockout";

			byte[] yKey = new byte[32]; // 256-bit reset key
			synchronized(SESSIONS) // sessions list used to guard RNG
			{
				RNG.nextBytes(yKey);
			}
			String sKey = Text.toHexString(yKey);
			oUser.m_lReset = lNow;
			synchronized(RESET)
			{
				RESET.put(sKey, oUser); // save new reset request
				for (String sId : RESET.keySet())
				{
					oUser = RESET.get(sId);
					if (lNow - oUser.m_lReset >= 600000)
						RESET.remove(sKey); // cleanup stale reset requests
				}
			}


			try
			{
				Emails oEmails = (Emails)Directory.getInstance().lookup("Emails");
				oEmails.send("Request reset", String.format("Here is the requested link to reset your password:\nhttps://%s/reset.html?key=%s\n\nThis is an unmonitored account, do not directly reply to this email address.", oEmails.getDomain(), sKey), oUser.m_sContact);
			}
			catch (Exception oEx)
			{
				LOGGER.error(oEx, oEx);
			}

			LOGGER.error(sUname + " reset key " + sKey + " " + oReq.getHeader("X-Real-IP"));
			return "success";
		}
	}

	
	/**
	 * This handler processes password update requests from the IMRCP Web Application
	 */
	private class SessMgrUpdate implements Handler
	{

		/**
		 * Processes the password update request.
		 * 
		 * @return "success"
		 */
		@Override
		public String process(StringBuilder sBuf, HttpServletRequest oReq)
		{
			String sKey = oReq.getParameter("key"); // reset key not provided
			String sPword = oReq.getParameter("pword"); // password not provided
			if (Text.isEmpty(sKey) || Text.isEmpty(sPword))
				return "success"; // don't care

			Session oUser;
			synchronized(RESET)
			{
				oUser = RESET.remove(sKey);
			}

			if (oUser != null) // update password
			{
				StringBuilder sHash = new StringBuilder();
				byte[] ySalt = new byte[32];
				synchronized(SESSIONS)
				{
					RNG.nextBytes(ySalt);
				}
				SessMgr.getSecurePassword(sPword, ySalt, sHash);
				oUser.m_ySalt = ySalt; // replace salt too
				oUser.m_sPass = sHash.toString();
				updateUser(oUser);
			}
			return "success";
		}
	}
	
	
	/**
	 * This handler processes password update requests from the IMRCP Web Application
	 */
	private class SessMgrProfile implements Handler
	{

		/**
		 * Processes the password update request.
		 * 
		 * @return "success"
		 */
		@Override
		public String process(StringBuilder sBuf, HttpServletRequest oReq)
		{
			Session oSession = getSession(false, getToken(oReq));
			if (oSession == null)
				return "success";
			
			WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
			sBuf.append("\"networks\":[");

			for (String sId : oSession.m_oProfile.m_sNetworks)
			{
				Network oNetwork = oWayNetworks.getNetwork(sId);
				if (oNetwork != null)
					sBuf.append("{\"id\":\"").append(sId).append("\",\"label\":\"").append(oNetwork.m_sLabel).append("\"},");
			}
			if (oSession.m_oProfile.m_sNetworks.length > 0)
				sBuf.setLength(sBuf.length() - 1); // remove trailing comma
			sBuf.append("],");
			return "success";
		}
	}

	
	/**
	 * Main function that is intended to be used outside of the system to generate
	 * lines for the IMRCP user CSV file. It hashes and saved the encrypted password
	 * as a hex string. The email address and group list might need to be edited.
	 * @param sArgs [user name, password]
	 * @throws Exception
	 */
	public static void main(String[] sArgs)
		throws Exception
	{
		String sUser = sArgs[0];
		String sPass = sArgs[1];

		byte[] ySalt = new byte[32]; // use 256-bit algorithm
		SessMgr.RNG.nextBytes(ySalt); // SecureRandom.getInstance("SHA1PRNG").nextBytes(ySalt);
		StringBuilder sHash = new StringBuilder();
		SessMgr.getSecurePassword(sPass, ySalt, sHash);

		StringBuilder sEntry = new StringBuilder(sUser);
		sEntry.append(",").append(Text.toHexString(ySalt)).append(",").append(sHash).
			append(",").append("<emailaddress>").append(",").append("imrcp-user");

		System.out.println(sEntry.toString());
	}
}
