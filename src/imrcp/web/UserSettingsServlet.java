package imrcp.web;

import imrcp.system.FileUtil;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;

/**
 * Servlet that handles request from the IMRCP Map UI dealing with user's map
 * settings.
 * @author aaron.cherney
 */
public class UserSettingsServlet extends SecureBaseBlock
{
	/**
	 * Format string used to generate settings file for individual users
	 */
	private String m_sSettingsFile;
	
	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_sSettingsFile = SessMgr.getInstance().m_sUserDir + "%s/settings.json";
	}
	
	
	/**
	 * Adds the user's map settings to the response.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oResp object that contains the response the servlet sends to the client
	 * @param oSession object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws ServletException
	 * @throws IOException
	 */
	public int doMapSettings(HttpServletRequest oReq, HttpServletResponse oResp, Session oSession, ClientConfig oClient)
	   throws ServletException, IOException
	{
		try 
		{
			oResp.setHeader("Content-Type", "application/json");
			StringBuilder sBuf = new StringBuilder();
			Path oPath = Paths.get(String.format(m_sSettingsFile, oSession.m_sName));
			if (Files.exists(oPath))
			{
				try (BufferedReader oIn = Files.newBufferedReader(oPath, StandardCharsets.UTF_8))
				{
					int nByte;
					while ((nByte = oIn.read()) >= 0)
						sBuf.append((char)nByte);
				}
			}
			else
				sBuf.append("{}");
			
			try (PrintWriter oOut = oResp.getWriter())
			{
				oOut.append(sBuf);
			}
			return HttpServletResponse.SC_OK;
		}
		catch (Exception oEx)
		{
			return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
	}
	
	
	/**
	 * Saves the user's map settings in the request on disk.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oResp object that contains the response the servlet sends to the client
	 * @param oSession object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws ServletException
	 * @throws IOException
	 */
	public int doSaveMapSettings(HttpServletRequest oReq, HttpServletResponse oResp, Session oSession, ClientConfig oClient)
	   throws ServletException, IOException
	{
		try 
		{
			oResp.setHeader("Content-Type", "application/json");
			String sSettings = oReq.getParameter("settings");
			if (sSettings != null)
			{
				Path oFile = Paths.get(String.format(m_sSettingsFile, oSession.m_sName));
				Files.createDirectories(oFile.getParent());
				try (BufferedOutputStream oOut = new BufferedOutputStream(Files.newOutputStream(oFile, FileUtil.WRITEOPTS)))
				{
					oOut.write(sSettings.getBytes(StandardCharsets.UTF_8));
				}
			}
			return HttpServletResponse.SC_OK;
		}
		catch (Exception oEx)
		{
			return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
	}
}
