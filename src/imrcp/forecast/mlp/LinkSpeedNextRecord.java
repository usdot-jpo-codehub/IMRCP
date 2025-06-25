/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.forecast.mlp;

import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.osm.OsmWay;
import imrcp.system.Id;
import java.io.BufferedWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 *
 * @author Federal Highway Administration
 */
public class LinkSpeedNextRecord 
{
	static final String HEADER = "Id,time,Direction,Lanes,Speed,t_to_lf,lat,lon,name,hour,category,lf_zone,dis_to_lf,spd_mean_past7,spd_std_past7\n";
	
	long m_lTime;
	int m_nTimeToLandfall;
	OsmWay m_oWay;
	int m_nLanes;
	int m_nDirection;
	double m_dDistanceToLandfall;
	double m_dSpeed;
	double m_dSpeedMean;
	double m_dSpeedStd;
	int m_nHurricaneCategory;
	int m_nLandfallLocation;
	int m_nHour;
	
	LinkSpeedNextRecord(boolean bMins)
	{
		m_oWay = new OsmWay();
		m_oWay.m_oId = Id.NULLID;
		if (bMins)
		{
			m_oWay.m_nMidLat = Integer.MAX_VALUE;
			m_oWay.m_nMidLon = Integer.MAX_VALUE;
			m_lTime = Long.MAX_VALUE;
			m_nLanes = Integer.MAX_VALUE;
			m_dDistanceToLandfall = Double.MAX_VALUE;
			m_dSpeed = Double.MAX_VALUE;
			m_dSpeedMean = Double.MAX_VALUE;
			m_dSpeedStd = Double.MAX_VALUE;
			m_nLandfallLocation = 0;
			m_nDirection = 1;
			m_nTimeToLandfall = Integer.MAX_VALUE;
			m_nHurricaneCategory = 1;
			m_nHour = 0;
		}
		else
		{
			m_oWay.m_nMidLat = Integer.MIN_VALUE;
			m_oWay.m_nMidLon = Integer.MIN_VALUE;
			m_lTime = Long.MIN_VALUE;
			m_nLanes = Integer.MIN_VALUE;
			m_dDistanceToLandfall = -Double.MAX_VALUE;
			m_dSpeed = -Double.MAX_VALUE;
			m_dSpeedMean = -Double.MAX_VALUE;
			m_dSpeedStd = -Double.MAX_VALUE;
			m_nLandfallLocation = 1;
			m_nDirection = 2;
			m_nHurricaneCategory = 2;
			m_nTimeToLandfall = Integer.MIN_VALUE;
			m_nHour = 23;
		}
	}
	
	LinkSpeedNextRecord(long lTime, GregorianCalendar oCal, long lLandfall, OsmWay oWay, int nDirection, int nLanes, double dDistanceToLandfall, double dSpeed, double dSpeedMean, double dSpeedStd, int nHurricaneCategory, int nLandfallLoc)
	{
		m_lTime = lTime;
		oCal.setTimeInMillis(m_lTime);
		m_nHour = oCal.get(Calendar.HOUR_OF_DAY);
		m_nTimeToLandfall = (int)((lTime - lLandfall) / 3600000);
		m_oWay = oWay;
		m_nLanes = nLanes;
		m_nDirection = nDirection;
		m_dDistanceToLandfall = dDistanceToLandfall;
		m_dSpeed = dSpeed;
		m_dSpeedMean = dSpeedMean;
		m_dSpeedStd = dSpeedStd;
		m_nHurricaneCategory = nHurricaneCategory;
		m_nLandfallLocation = nLandfallLoc;
	}
	
	void write(BufferedWriter oOut, SimpleDateFormat oSdf)
		throws IOException
	{
		oOut.append(m_oWay.m_oId.toString()).append(',');
		oOut.append(oSdf.format(m_lTime)).append(',');
		oOut.append(Integer.toString(m_nDirection)).append(',');
		oOut.append(Integer.toString(m_nLanes)).append(',');
		if (Double.isNaN(m_dSpeed))
			oOut.append("nan").append(',');
		else
			oOut.append(String.format("%.2f", m_dSpeed)).append(',');
		oOut.append(Integer.toString(m_nTimeToLandfall)).append(',');
		oOut.append(String.format("%.7f,%.7f,", GeoUtil.fromIntDeg(m_oWay.m_nMidLat), GeoUtil.fromIntDeg(m_oWay.m_nMidLon)));
		oOut.append(m_oWay.m_sName.replace(',', '_')).append(',');
		oOut.append(Integer.toString(m_nHour)).append(',');
		oOut.append(Integer.toString(m_nHurricaneCategory)).append(',');
		oOut.append(Integer.toString(m_nLandfallLocation)).append(',');
		oOut.append(String.format("%.2f,", m_dDistanceToLandfall));
		if (Double.isNaN(m_dSpeedMean))
			oOut.append("nan").append(',');
		else
			oOut.append(String.format("%.2f", m_dSpeedMean)).append(',');
		if (Double.isNaN(m_dSpeedStd))
			oOut.append("nan").append('\n');
		else
			oOut.append(String.format("%.2f", m_dSpeedStd)).append('\n');
	}
}
