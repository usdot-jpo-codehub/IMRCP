/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.forecast.mlp;

import imrcp.geosrv.WayNetworks;
import imrcp.system.Directory;
import imrcp.system.Scheduling;
import java.util.ArrayList;

/**
 *
 * @author Federal Highway Administration
 */
public class MLPExtended extends MLP
{
	private final String m_sExtendedOneshotDir = "mlp/%s_%s/";

	@Override
	public void execute()
	{
		try
		{
			WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
			long lNow = System.currentTimeMillis();
			lNow = (lNow / 3600000) * 3600000;
			long lLongTsUpdateStart = lNow;
			long lRefTime = lNow;
			ArrayList<MLP.MLPSession> oSessions = getSessions(lLongTsUpdateStart + 3600000, "exos", oWayNetworks, null);
			if (oSessions.isEmpty())
				return;
			for (int nIndex = 0; nIndex < oSessions.size(); nIndex++)
			{
				MLPSession oOneshotSess = oSessions.get(nIndex);
				oOneshotSess.m_sDirectory = String.format(m_sTempPath + m_sExtendedOneshotDir, Long.toString(lNow), "os");
				oOneshotSess.m_nForecastOffset = -1; // flag for python to use all 169 outputs
				oOneshotSess.m_lRefTime = lRefTime;
			}
			
			Scheduling.processCallables(oSessions, m_nPythonProcesses);
			createFiles(oSessions);
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
}
