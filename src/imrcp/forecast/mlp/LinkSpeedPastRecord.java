/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.forecast.mlp;

import imrcp.system.CsvReader;
import imrcp.system.Id;
import java.io.BufferedWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 *
 * @author Federal Highway Administration
 */
public class LinkSpeedPastRecord implements Comparable<LinkSpeedPastRecord>
{
	static final String HEADER = "Id,time,Speed,category,lf_zone\n";
	public Id m_oId;
	public long m_lTimestamp;
	public double m_dSpeed;
	public int m_nHurricaneCategory;
	public int m_nLandfallLocation;
	public byte m_yPref = 0;
	
	LinkSpeedPastRecord()
	{
		
	}
	
	
	public LinkSpeedPastRecord(Id oId, long lTimestamp, double dSpeed, int nHurCat, int nLfLoc)
	{
		m_oId = oId;
		m_lTimestamp = lTimestamp;
		m_dSpeed = dSpeed;
		m_nHurricaneCategory = nHurCat;
		m_nLandfallLocation = nLfLoc;
	}
	
	
	LinkSpeedPastRecord(Id oId, long lTimestamp, double dSpeed, int nHurCat, int nLfLoc, byte yPref)
	{
		this(oId, lTimestamp, dSpeed, nHurCat, nLfLoc);
		m_yPref = yPref;
	}

	
	LinkSpeedPastRecord(CsvReader oIn, SimpleDateFormat oSdf)
		throws ParseException
	{
		m_oId = new Id(oIn.parseString(0));
		m_lTimestamp = oSdf.parse(oIn.parseString(1)).getTime();
		m_dSpeed = oIn.parseDouble(2);
		m_nHurricaneCategory = oIn.parseInt(3);
		m_nLandfallLocation = oIn.parseInt(4);
	}
	
	
	@Override
	public int compareTo(LinkSpeedPastRecord o)
	{
		int nRet = m_oId.compareTo(o.m_oId);
		if (nRet == 0)
			nRet = Long.compare(m_lTimestamp, o.m_lTimestamp);
		
		return nRet;
	}

	public void write(BufferedWriter oOut, SimpleDateFormat oSdf)
		throws IOException
	{
		oOut.append(m_oId.toString()).append(',');
		oOut.append(oSdf.format(m_lTimestamp)).append(',');
		if (Double.isNaN(m_dSpeed))
			oOut.append("nan").append(',');
		else
			oOut.append(String.format("%.2f", m_dSpeed)).append(',');
		oOut.append(Integer.toString(m_nHurricaneCategory)).append(',');
		oOut.append(Integer.toString(m_nLandfallLocation)).append('\n');
	}
}
