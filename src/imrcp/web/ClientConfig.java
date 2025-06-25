/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.web;

import java.net.InetAddress;

/**
 *
 * @author Federal Highway Administration
 */
public class ClientConfig 
{
	public final int[] m_nObsTypes;
	public final String m_sUUID;
	public final InetAddress m_oHost;
	
	ClientConfig(String sUUID, InetAddress oHost, int[] nObsTypes)
	{
		m_sUUID = sUUID;
		m_oHost = oHost;
		m_nObsTypes = nObsTypes;
	}
}
