/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.forecast.mdss;

import imrcp.geosrv.GeoUtil;
import imrcp.store.Obs;
import imrcp.store.ObsList;
import imrcp.store.TileObsView;
import imrcp.system.Directory;
import imrcp.system.ObsType;
import imrcp.system.ResourceRecord;
import java.util.ArrayList;
import java.util.Calendar;

/**
 *
 * @author Federal Highway Administration
 */
public class MetroObsSet 
{
	ObsList[] m_oObsAirTemp;
	ObsList[] m_oObsDewPoint;
	ObsList[] m_oObsWindSpeed;
	ObsList[] m_oObsTpvt;
	ObsList[] m_oMetroTpvt;
	ObsList[] m_oMetroTssrf;
	ObsList[] m_oObsTssrf;
	ObsList[] m_oObsRoadCond;
	double[] m_dObsTime;
	
	ObsList[] m_oFcstAirTemp;
	ObsList[] m_oFcstDewPoint;
	ObsList[] m_oFcstWindSpeed;
	ObsList[] m_oFcstCloudCover;
	ObsList[] m_oFcstSfcPres;
	ObsList m_oRainRes;
	ObsList m_oSnowRes;
	ObsList[] m_oFcstPrecipRate;
	ObsList[] m_oFcstPrecipType;
	double[] m_dFTime;
	double[] m_dFTimeSeconds;
	
	int m_nSourceId;
	
	
	MetroObsSet(int nObsHrs, int nFcstHrs, int nSource)
	{
		m_oObsAirTemp = new ObsList[nObsHrs];
		m_oObsDewPoint = new ObsList[nObsHrs];
		m_oObsWindSpeed = new ObsList[nObsHrs];
		m_oObsTpvt = new ObsList[nObsHrs];
		m_oObsTssrf = new ObsList[nObsHrs];
		m_oMetroTpvt = new ObsList[nObsHrs];
		m_oMetroTssrf = new ObsList[nObsHrs];
		m_oObsRoadCond = new ObsList[nObsHrs];
		m_dObsTime = new double[nObsHrs];
		
		m_oFcstAirTemp = new ObsList[nFcstHrs];
		m_oFcstDewPoint = new ObsList[nFcstHrs];
		m_oFcstWindSpeed = new ObsList[nFcstHrs];
		m_oFcstCloudCover = new ObsList[nFcstHrs];
		m_oFcstSfcPres = new ObsList[nFcstHrs];
		m_oRainRes = new ObsList();
		m_oSnowRes = new ObsList();
		m_oFcstPrecipRate = new ObsList[nFcstHrs];
		m_oFcstPrecipType = new ObsList[nFcstHrs];
		m_dFTime = new double[nFcstHrs];
		m_dFTimeSeconds = new double[nFcstHrs];
		
		m_nSourceId = nSource;
	}
	
	
	public void getData(int[] nBb, long lStartTime)
	{
		long lObservation = lStartTime - (3600000 * m_oObsAirTemp.length);
		long lForecast = lStartTime - 3600000; // the first "forecast" actually uses observed values
		TileObsView oOV = (TileObsView)Directory.getInstance().lookup("ObsView");
		Calendar oCal = Calendar.getInstance(Directory.m_oUTC);
		ResourceRecord oMetroTpvt = Directory.getResource(Integer.valueOf("metro", 36), ObsType.TPVT);
		int[] nTpvtContribSrc = new int[]{oMetroTpvt.getContribId(), oMetroTpvt.getSourceId()};
		ResourceRecord oMetroTssrf = Directory.getResource(Integer.valueOf("metro", 36), ObsType.TSSRF);
		int[] nTssrfContribSrc = new int[]{oMetroTssrf.getContribId(), oMetroTssrf.getSourceId()};
		for (int i = 0; i < m_oObsAirTemp.length; i++)
		{
			long lQueryStart = lObservation + (i * 3600000);
			long lQueryEnd = lQueryStart + 120000;
			m_oObsAirTemp[i] = oOV.getPreferedData(ObsType.TAIR, nBb, lQueryStart, lQueryEnd, lStartTime);
			m_oObsDewPoint[i] = oOV.getPreferedData(ObsType.TDEW, nBb, lQueryStart, lQueryEnd, lStartTime);
			m_oObsWindSpeed[i] = oOV.getPreferedData(ObsType.SPDWND, nBb, lQueryStart, lQueryEnd, lStartTime);
			m_oObsTpvt[i] = oOV.getPreferedData(ObsType.TPVT, nBb, lQueryStart, lQueryEnd, lStartTime);
			m_oObsTssrf[i] = oOV.getPreferedData(ObsType.TSSRF, nBb, lQueryStart, lQueryEnd, lStartTime);
			m_oMetroTpvt[i] = oOV.getData(ObsType.TPVT, lQueryStart, lQueryEnd, nBb[1], nBb[3], nBb[0], nBb[2], lStartTime, nTpvtContribSrc);
			m_oMetroTssrf[i] = oOV.getData(ObsType.TSSRF, lQueryStart, lQueryEnd, nBb[1], nBb[3], nBb[0], nBb[2], lStartTime, nTssrfContribSrc);
			m_oObsRoadCond[i] = oOV.getPreferedData(ObsType.STPVT, nBb, lQueryStart, lQueryEnd, lStartTime);
			oCal.setTimeInMillis(lQueryStart);
			m_dObsTime[i] = oCal.get(Calendar.HOUR_OF_DAY) + (double)oCal.get(Calendar.MINUTE) / 60.0;
		}
		
		ArrayList<ResourceRecord> oMetroRRs = Directory.getResourcesByContribSource(Integer.valueOf("metro", 36), m_nSourceId);
		ResourceRecord oMetroDphSn = null;
		for (ResourceRecord oRR : oMetroRRs)
		{
			if (oRR.getObsTypeId() == ObsType.DPHSN)
			{
				oMetroDphSn = oRR;
				break;
			}
		}
		
		ResourceRecord oMetroDphLiq = null;
		for (ResourceRecord oRR : oMetroRRs)
		{
			if (oRR.getObsTypeId() == ObsType.DPHLIQ)
			{
				oMetroDphLiq = oRR;
				break;
			}
		}

		m_oRainRes = oOV.getData(ObsType.DPHLIQ, lForecast, lForecast + 60000, nBb[1], nBb[3], nBb[0], nBb[2], lStartTime, new int[]{oMetroDphLiq.getContribId(), oMetroDphLiq.getSourceId()});
		m_oSnowRes = oOV.getData(ObsType.DPHSN, lForecast, lForecast + 60000, nBb[1], nBb[3], nBb[0], nBb[2], lStartTime, new int[]{oMetroDphSn.getContribId(), oMetroDphSn.getSourceId()});
		
		for (int i = 0; i < m_oFcstAirTemp.length; i++)
		{
			long lQueryStart = lForecast + (3600000 * i);
			long lQueryEnd = lQueryStart + 60000;
			oCal.setTimeInMillis(lQueryStart);
			m_dFTime[i] = oCal.get(Calendar.HOUR_OF_DAY) + (double)oCal.get(Calendar.MINUTE) / 60.0;
			m_dFTimeSeconds[i] = (int)(lQueryStart / 1000);
			if (i == 0)
			{
				m_oFcstAirTemp[i] = m_oObsAirTemp[m_oObsAirTemp.length - 1];
				m_oFcstDewPoint[i] = m_oObsDewPoint[m_oObsDewPoint.length - 1];
				m_oFcstWindSpeed[i] = m_oObsWindSpeed[m_oObsWindSpeed.length - 1];
				m_oFcstCloudCover[i] = oOV.getPreferedData(ObsType.COVCLD, nBb, lQueryStart, lQueryEnd, lStartTime);
				m_oFcstSfcPres[i] = oOV.getPreferedData(ObsType.PRSUR, nBb, lQueryStart, lQueryEnd, lStartTime);
				m_oFcstPrecipRate[i] = oOV.getPreferedData(ObsType.RTEPC, nBb, lQueryStart, lQueryStart + 3600000, lStartTime);
				m_oFcstPrecipType[i] = null;
			}
			else
			{
				m_oFcstAirTemp[i] = oOV.getPreferedData(ObsType.TAIR, nBb, lQueryStart, lQueryEnd, lStartTime);
				m_oFcstDewPoint[i] = oOV.getPreferedData(ObsType.TDEW, nBb, lQueryStart, lQueryEnd, lStartTime);
				m_oFcstWindSpeed[i] = oOV.getPreferedData(ObsType.SPDWND, nBb, lQueryStart, lQueryEnd, lStartTime);
				m_oFcstCloudCover[i] = oOV.getPreferedData(ObsType.COVCLD, nBb, lQueryStart, lQueryEnd, lStartTime);
				m_oFcstSfcPres[i] = oOV.getPreferedData(ObsType.PRSUR, nBb, lQueryStart, lQueryEnd, lStartTime);
				m_oFcstPrecipRate[i] = oOV.getPreferedData(ObsType.RTEPC, nBb, lQueryStart, lQueryEnd, lStartTime);
				m_oFcstPrecipType[i] = oOV.getPreferedData(ObsType.TYPPC, nBb, lQueryStart, lQueryEnd, lStartTime);
			}
		}
	}

	void fillObsSet(MetroObsSet oAllObs, int[] nBB)
	{
		int[] nBoundingGeo = GeoUtil.getBoundingPolygon(nBB[0], nBB[1], nBB[2], nBB[3]);
		m_dObsTime = oAllObs.m_dObsTime;
		m_dFTime = oAllObs.m_dFTime;
		m_dFTimeSeconds = oAllObs.m_dFTimeSeconds;
		for (int i = 0; i < m_oObsAirTemp.length; i++)
		{
			fillObsList(oAllObs.m_oObsAirTemp[i], m_oObsAirTemp, i, nBoundingGeo);
			fillObsList(oAllObs.m_oObsDewPoint[i], m_oObsDewPoint, i, nBoundingGeo);
			fillObsList(oAllObs.m_oObsWindSpeed[i], m_oObsWindSpeed, i, nBoundingGeo);
			fillObsList(oAllObs.m_oObsTpvt[i], m_oObsTpvt, i, nBoundingGeo);
			fillObsList(oAllObs.m_oObsTssrf[i], m_oObsTssrf, i, nBoundingGeo);
			fillObsList(oAllObs.m_oMetroTpvt[i], m_oMetroTpvt, i, nBoundingGeo);
			fillObsList(oAllObs.m_oMetroTssrf[i], m_oMetroTssrf, i, nBoundingGeo);
			fillObsList(oAllObs.m_oObsRoadCond[i], m_oObsRoadCond, i, nBoundingGeo);
		}
		
		for (int i = 0; i < m_oFcstAirTemp.length; i++)
		{
			if (i == 0)
			{
				m_oFcstAirTemp[i] = m_oObsAirTemp[m_oObsAirTemp.length - 1];
				m_oFcstDewPoint[i] = m_oObsDewPoint[m_oObsDewPoint.length - 1];
				m_oFcstWindSpeed[i] = m_oObsWindSpeed[m_oObsWindSpeed.length - 1];
				fillObsList(oAllObs.m_oFcstCloudCover[i], m_oFcstCloudCover, i, nBoundingGeo);
				fillObsList(oAllObs.m_oFcstSfcPres[i], m_oFcstSfcPres, i, nBoundingGeo);
				fillObsList(oAllObs.m_oFcstPrecipRate[i], m_oFcstPrecipRate, i, nBoundingGeo);
				m_oFcstPrecipType[i] = null;
				fillObsList(oAllObs.m_oRainRes, m_oRainRes, nBoundingGeo);
				fillObsList(oAllObs.m_oSnowRes, m_oSnowRes, nBoundingGeo);
			}
			else
			{
				fillObsList(oAllObs.m_oFcstAirTemp[i], m_oFcstAirTemp, i, nBoundingGeo);
				fillObsList(oAllObs.m_oFcstDewPoint[i], m_oFcstDewPoint, i, nBoundingGeo);
				fillObsList(oAllObs.m_oFcstWindSpeed[i], m_oFcstWindSpeed, i, nBoundingGeo);
				fillObsList(oAllObs.m_oFcstCloudCover[i], m_oFcstCloudCover, i, nBoundingGeo);
				fillObsList(oAllObs.m_oFcstSfcPres[i], m_oFcstSfcPres, i, nBoundingGeo);
				fillObsList(oAllObs.m_oFcstPrecipRate[i], m_oFcstPrecipRate, i, nBoundingGeo);
				fillObsList(oAllObs.m_oFcstPrecipType[i], m_oFcstPrecipType, i, nBoundingGeo);
			}
		}
	}
	
	
	void fillObsList(ObsList oAll, ObsList oFill, int[] nBoundingGeo)
	{
		for (Obs oObs : oAll)
		{
			if (oObs.spatialMatch(nBoundingGeo))
				oFill.add(oObs);
		}
	}
	
	
	void fillObsList(ObsList oAll, ObsList[] oFill, int nIndex, int[] nBoundingGeo)
	{
		oFill[nIndex] = new ObsList();
		ObsList oList = oFill[nIndex];
		for (Obs oObs : oAll)
		{
			if (oObs.spatialMatch(nBoundingGeo))
				oList.add(oObs);
		}
	}
}
