/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.collect;

import imrcp.system.OneTimeReentrantLock;
import imrcp.system.ResourceRecord;
import java.util.ArrayList;

/**
 *
 * @author Federal Highway Administration
 */
public class TileFileInfo implements Comparable<TileFileInfo>
{
	public ArrayList<ResourceRecord> m_oRRs;
	public long m_lStart;
	public long m_lEnd;
	public long m_lRef;
	public boolean m_bRequest;
	OneTimeReentrantLock m_oLock = new OneTimeReentrantLock();

	TileFileInfo(ArrayList<ResourceRecord> oRRs, long lStart, long lEnd, long lRef, boolean bRequest)
	{
		m_oRRs = oRRs;
		m_lStart = lStart;
		m_lEnd = lEnd;
		if (lStart == lEnd)
			++m_lEnd;
		m_lRef = lRef;
		m_bRequest = bRequest;
	}

	@Override
	public int compareTo(TileFileInfo o)
	{
		ResourceRecord oR1 = m_oRRs.get(0);
		ResourceRecord oR2 = o.m_oRRs.get(0);
		if (oR1.getContribId() == oR2.getContribId() && oR1.getObsTypeId() == oR2.getObsTypeId() && oR1.getSourceId() == oR2.getSourceId())
		{
			int nReturn = Long.compare(m_lStart, o.m_lStart);
			if (nReturn == 0)
			{
				nReturn = Long.compare(m_lEnd, o.m_lEnd);
				if (nReturn == 0)
					nReturn = Long.compare(m_lRef, o.m_lRef);
			}
			
			return nReturn;
		}
		else
			return 1;
	}
}
