/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.web;

import java.net.InetAddress;
import java.util.ArrayList;

/**
 *
 * @author Federal Highway Administration
 */
public class ServerConfig implements Comparable<ServerConfig>
{
	public final int m_nObsType;
	public final String m_sUUID;
	public final InetAddress m_oHost;
	public ArrayList<int[]> m_oNetworkGeometries = new ArrayList();
	
	public ServerConfig(String sUUID, InetAddress oHost, int nObsType)
	{
		m_sUUID = sUUID;
		m_oHost = oHost;
		m_nObsType = nObsType;
	}

	@Override
	public int compareTo(ServerConfig o)
	{
		int nRet = m_sUUID.compareTo(o.m_sUUID);
		if (nRet == 0)
			nRet = m_nObsType - o.m_nObsType;
		
		return nRet;
	}
}
