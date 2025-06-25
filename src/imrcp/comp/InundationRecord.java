/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.comp;

import imrcp.system.dbf.DbfResultSet;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.util.Comparator;

/**
 *
 * @author Federal Highway Administration
 */
public class InundationRecord
{
	public byte[] m_yDbf;
	public byte[] m_yShp;
	public double m_dStage = Double.NaN;
	public String m_sFilename;
	public String m_sId;

	public static Comparator<InundationRecord> COMPBYFILE = (InundationRecord o1, InundationRecord o2) ->
	{
		int nRet = o1.m_sFilename.compareTo(o2.m_sFilename);
		if (nRet == 0 )
			nRet = o1.m_sId.compareTo(o2.m_sId);
		
		return nRet;
	};
	
	public static Comparator<InundationRecord> COMPBYSTAGE = (InundationRecord o1, InundationRecord o2) ->
	{
		return Double.compare(o1.m_dStage, o2.m_dStage);
	};
	
	public InundationRecord()
	{
	}

	public InundationRecord(String sGauge, String sName)
	{
		m_sId = sGauge;
		m_sFilename = sName;
	}

	
	public void findStage()
	{
		if (m_sId.compareToIgnoreCase("hatm6") == 0)
		{
			int nStart = m_sFilename.lastIndexOf("/elev_");
			nStart += "/elev_".length();
			int nEnd = m_sFilename.indexOf("_", nStart);
			m_dStage = Double.parseDouble(m_sFilename.substring(nStart, nEnd));
		}
		else
		{
			try (ByteArrayInputStream oBaos = new ByteArrayInputStream(m_yDbf);
				DbfResultSet oDbf = new DbfResultSet(new BufferedInputStream(oBaos)))
			{
				int nCol = oDbf.findColumn("elev");
				if (nCol == 0)
				{
					nCol = oDbf.findColumn("ELEV");
					if (nCol == 0)
					{
						nCol = oDbf.findColumn("Id");
						if (nCol == 0)
							return;
					}
				}
				if (oDbf.next())
				{
					m_dStage = oDbf.getDouble(nCol);
				}
			}
			catch (Exception oEx)
			{
	//			LogManager.getLogger(InundationRecord.class).error(String.format("Couldn't determine stage for %s %s", m_sId, m_sFilename));
			}
		}
	}
}
