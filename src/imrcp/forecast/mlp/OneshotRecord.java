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

/**
 *
 * @author Federal Highway Administration
 */
public class OneshotRecord 
{
	static final String HEADER = "Id,t_to_lf,Direction,Lanes,lat,lon,dis_to_lf,timeofday,spd_mean_past7,spd_std_past7,category,lf_zone,congested\n";
	int m_nTimeToLandfall;
	OsmWay m_oWay;
	int m_nLanes;
	int m_nDirection;
	double m_dDistanceToLandfall;
	int m_nTimeOfDay;
	double m_dSpeedMean;
	double m_dSpeedStd;
	int m_nHurricaneCategory;
	int m_nLandfallLocation;
	int[] m_nCongested = new int[28]; // 28 6 hour periods in 1 week
	int m_nCongestedToWrite;
	
	
	OneshotRecord(boolean bMins)
	{
		m_oWay = new OsmWay();
		m_oWay.m_oId = Id.NULLID;
		if (bMins)
		{
			m_oWay.m_nMidLat = Integer.MAX_VALUE;
			m_oWay.m_nMidLon = Integer.MAX_VALUE;
			m_nLanes = Integer.MAX_VALUE;
			m_dDistanceToLandfall = Double.MAX_VALUE;
			m_dSpeedMean = Double.MAX_VALUE;
			m_dSpeedStd = Double.MAX_VALUE;
			m_nLandfallLocation = 0;
			m_nHurricaneCategory = Integer.MAX_VALUE;
			m_nDirection = 1;
			m_nTimeToLandfall = -14;
			m_nTimeOfDay = 1;
			m_nCongestedToWrite = 0;
		}
		else
		{
			m_oWay.m_nMidLat = Integer.MIN_VALUE;
			m_oWay.m_nMidLon = Integer.MIN_VALUE;
			m_nLanes = Integer.MIN_VALUE;
			m_dDistanceToLandfall = -Double.MAX_VALUE;
			m_dSpeedMean = -Double.MAX_VALUE;
			m_dSpeedStd = -Double.MAX_VALUE;
			m_nLandfallLocation = 1;
			m_nHurricaneCategory = Integer.MIN_VALUE;
			m_nDirection = 2;
			m_nTimeToLandfall = 13;
			m_nTimeOfDay = 2;
			m_nCongestedToWrite = 2;
		}
	}
	
	OneshotRecord(OsmWay oWay, double[] dSpeeds, double dMean, double dStd, int nHurCat, int nLfLoc, int nDirection, int nLanes, double dDistanceToLandfall)
	{
		m_oWay = oWay;
		m_dSpeedMean = dMean;
		m_dSpeedStd = dStd;
		m_nHurricaneCategory = nHurCat;
		m_nLandfallLocation = nLfLoc;
		m_nDirection = nDirection;
		m_nLanes = nLanes;
		m_dDistanceToLandfall = dDistanceToLandfall;
		int nSpeedIndex = 1;
		int nCongestedIndex = 0;
		for (int nIndex = -14; nIndex < 14; nIndex++)
		{
			m_nCongested[nCongestedIndex] = 0;
			double dSpeed = 0;
			int nCount = 0;
			for (int n = 0; n < 72; n++) // 5 minute records
			{
				double dTemp = dSpeeds[nSpeedIndex++];
				if (Double.isFinite(dTemp))
				{
					++nCount;
					dSpeed += dTemp;
				}
			}
			if (nCount > 0)
			{
				double dSixHourAverage = dSpeed / nCount;
				double dRatio = dSixHourAverage / m_dSpeedMean;
				if (dRatio < 0.5)
					m_nCongested[nCongestedIndex] = 2;
				else if (dRatio < 0.75)
					m_nCongested[nCongestedIndex] = 1;
			}
			++nCongestedIndex;
		}
	}

	void write(BufferedWriter oOut)
		throws IOException
	{
		oOut.append(m_oWay.m_oId.toString()).append(',');
		oOut.append(Integer.toString(m_nTimeToLandfall)).append(',');
		oOut.append(Integer.toString(m_nDirection)).append(',');
		oOut.append(Integer.toString(m_nLanes)).append(',');
		oOut.append(String.format("%.7f,%.7f,", GeoUtil.fromIntDeg(m_oWay.m_nMidLat), GeoUtil.fromIntDeg(m_oWay.m_nMidLon)));
		oOut.append(String.format("%.2f,", m_dDistanceToLandfall));
		oOut.append(Integer.toString(m_nTimeOfDay)).append(',');
		oOut.append(String.format("%.2f,%.2f,", m_dSpeedMean, m_dSpeedStd));
		oOut.append(Integer.toString(m_nHurricaneCategory)).append(',');
		oOut.append(Integer.toString(m_nLandfallLocation)).append(',');
		oOut.append(Integer.toString(m_nCongestedToWrite)).append('\n');
	}
}
