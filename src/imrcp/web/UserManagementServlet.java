/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.web;

import imrcp.system.Text;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Federal Highway Administration
 */
public class UserManagementServlet extends SecureBaseBlock
{
	public int doTable(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oClient)
		throws IOException
	{
		oRes.setContentType("application/json");
		JSONObject oResponse = new JSONObject();
		JSONArray oData = new JSONArray();
		SessMgr.getInstance().getUserTable(oData, oSession.m_sName);
		oResponse.put("data", oData);
		try (PrintWriter oOut = oRes.getWriter())
		{
			oResponse.write(oOut);
		}
		
		return HttpServletResponse.SC_OK;
	}
	
	
	public int doSave(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oClient)
		throws IOException
	{
		try
		{
			oRes.setContentType("application/json");
			String sUserName = oReq.getParameter("name");
			String sGroup = oReq.getParameter("group");
			String sDeactivation = oReq.getParameter("deactivation");
			String sPw = oReq.getParameter("pw");
			if (sUserName == null || sGroup == null || sDeactivation == null || oSession.m_sName.compareTo(sUserName) == 0)
				return HttpServletResponse.SC_BAD_REQUEST;
			
			
			SessMgr oMgr = SessMgr.getInstance();
			Session oUser = oMgr.getUser(sUserName);
			if (oUser == null) // new user
			{
				SecureRandom oRng = SecureRandom.getInstance("SHA1PRNG");
				oUser = new Session();
				byte[] ySalt = new byte[32]; // use 256-bit algorithm
				oRng.nextBytes(ySalt);
				if (sPw == null || sPw.isEmpty())
				{
					byte[] yPass = new byte[32]; // random password must be reset for first login
					oRng.nextBytes(yPass);
					sPw = Text.toHexString(yPass);
				}

				StringBuilder sHash = new StringBuilder();
				SessMgr.getSecurePassword(sPw, ySalt, sHash);
				oUser.m_sName = sUserName;
				oUser.m_ySalt = ySalt;
				oUser.m_sPass = sHash.toString();
				oUser.m_sContact = sUserName;
			}
			
			if (sPw != null && !sPw.isEmpty())
			{
				SecureRandom oRng = SecureRandom.getInstance("SHA1PRNG");
				byte[] ySalt = new byte[32]; // use 256-bit algorithm
				oRng.nextBytes(ySalt);
				
				StringBuilder sHash = new StringBuilder();
				SessMgr.getSecurePassword(sPw, ySalt, sHash);
				oUser.m_ySalt = ySalt;
				oUser.m_sPass = sHash.toString();
			}
			
			oUser.m_sGroup = sGroup;
			if ((oUser.m_sDeactivation == null || oUser.m_sDeactivation.isEmpty()) && sDeactivation.length() > 0) // deactivating a user, give the user a random password
			{
				SecureRandom oRng = SecureRandom.getInstance("SHA1PRNG");
				byte[] ySalt = new byte[32]; // use 256-bit algorithm
				oRng.nextBytes(ySalt);
				byte[] yPass = new byte[32]; // random password
				oRng.nextBytes(yPass);
				StringBuilder sHash = new StringBuilder();
				
				SessMgr.getSecurePassword(Text.toHexString(yPass), ySalt, sHash);
				oUser.m_ySalt = ySalt;
				oUser.m_sPass = sHash.toString();
			}
			oUser.m_sDeactivation = sDeactivation;
			oMgr.updateUser(oUser);
			
		}
		catch (NoSuchAlgorithmException oEx)
		{
		}

		try (PrintWriter oOut = oRes.getWriter())
		{
			oOut.append("{}");
		}
		return HttpServletResponse.SC_OK;
	}
}
