/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.forecast.mdss;

import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.Mercator;
import imrcp.geosrv.osm.OsmWay;
import imrcp.geosrv.WayNetworks;
import imrcp.web.Scenario;
import imrcp.web.SegmentGroup;
import imrcp.store.Obs;
import imrcp.store.ObsList;
import imrcp.store.TileObsView;
import imrcp.system.Directory;
import imrcp.system.Id;
import imrcp.system.Introsort;
import imrcp.system.ObsType;
import imrcp.system.ResourceRecord;
import imrcp.system.Units;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Manages running the METRo model for Scenarios. For each segment in a Scenario
 * METRo is ran for each hour of a 24 forecast, using the previous run's outputs
 * as inputs for the next.
 * 
 * @see imrcp.web.Scenarios
 * @author aaron.cherney
 */
public class MetroProcess
{
	/**
	 * Contains air temperature observations and forecasts
	 */
	public double[] m_dAirTemp;

	
	/**
	 * Contains dew point observations and forecasts
	 */
	public double[] m_dDewPoint;

	
	/**
	 * Contains wind speed observations and forecasts
	 */
	public double[] m_dWindSpeed;

	
	/**
	 * Contains pavement temperature observations and forecasts
	 */
	public double[] m_dPavementTemp;

	
	/**
	 * Contains subsurface temperature observations and forecasts
	 */
	public double[] m_dSubSurfTemp;

	
	/**
	 * Contains pavement state observations and forecasts
	 */
	public int[] m_nPavementState;

	
	/**
	 * Contains cloud cover observations and forecasts
	 */
	public double[] m_dCloudCover;

	
	/**
	 * Contains surface pressure observations and forecasts
	 */
	public double[] m_dPressure;

	
	/**
	 * Contains precipitation type observations and forecasts
	 */
	public int[] m_nPrecipType;

	
	/**
	 * Contains precipitation rate observations and forecasts
	 */
	public double[] m_dPrecipRate;

	
	/**
	 * Contains rain reservoir observations
	 */
	public double[] m_dRainRes;

	
	/**
	 * Contains snow reservoir observations
	 */
	public double[] m_dSnowRes;

	
	/**
	 * Indicates which hours of the scenario the segments are treated
	 */
	public boolean[] m_bTreating;

	
	/**
	 * Indicates which hours of the scenario the segments are plowed
	 */
	public boolean[] m_bPlowing;

	
	/**
	 * Contains the timestamps of each hour of the scenario in milliseconds 
	 * since Epoch
	 */
	public long[] m_lTimes;

	
	/**
	 * Number of forecast hours used as input for the METRo runs
	 */
	public int m_nFcstPerRun = 4;

	
	/**
	 * Number of observation hours used as input for the METRo runs
	 */
	public int m_nObsPerRun = 2;

	
	/**
	 * Stores the output pavement state arrays for each segment in the scenario
	 */
	public ArrayList<int[]> m_oStPvts = new ArrayList();

	
	/**
	 * Stores the output pavement temperature arrays for each segment in the 
	 * scenario
	 */
	public ArrayList<double[]> m_oTPvts = new ArrayList();
	
	public ArrayList<double[]> m_oDphsns = new ArrayList();

	
	/**
	 * Allocates the memory for the observation/forecast array. The size of each
	 * array is the number hours of the scenario + {@link MetroProcess#m_nObsPerRun}
	 * + {@link MetroProcess#m_nFcstPerRun}
	 * @param nHours how long the scenario is in hours
	 */
	public MetroProcess(int nHours)
	{
		int nSize = nHours + m_nObsPerRun + m_nFcstPerRun + 1;
		m_dAirTemp = new double[nSize];
		m_dDewPoint = new double[nSize];
		m_dWindSpeed = new double[nSize];
		m_dPavementTemp = new double[nSize];
		m_dSubSurfTemp = new double[nSize];
		m_nPavementState = new int[nSize];
		m_dCloudCover = new double[nSize];
		m_dPressure = new double[nSize];
		m_nPrecipType = new int[nSize];
		m_dPrecipRate = new double[nSize];
		m_dRainRes = new double[nSize];
		m_dSnowRes = new double[nSize];
		m_bTreating = new boolean[nSize];
		m_bPlowing = new boolean[nSize];
		m_lTimes = new long[nSize];
	}
	
	
	/**
	 * Runs the METRo model for each segment in the given Scenario for each hour
	 * of the Scenario.
	 * @param oScenario Scenario being processed
	 * @throws Exception
	 */
	public void process(Scenario oScenario)
		throws Exception
	{
		long lNow = System.currentTimeMillis();
		lNow = (lNow / 3600000) * 3600000; // scenario are ran by the hour so floor to the current hour
		long lRefTime;
		if (oScenario.m_lStartTime > lNow)
			lRefTime = lNow;
		else
			lRefTime = oScenario.m_lStartTime; // allows scenarios to be ran in the past using the "best" forecast at that time
		ResourceRecord oRR = Directory.getResource(Integer.valueOf("metro", 36), ObsType.TPVT);
		int nPPT = (int)Math.pow(2, oRR.getTileSize()) - 1;
		Mercator oM = new Mercator(nPPT);
		int nTol = (int)Math.round(oM.RES[oRR.getZoom()] * 100); // meters per pixel * 100 
		long lStartTime = oScenario.m_lStartTime - 3600000 * (m_nObsPerRun - 1); // the first forecast time is an hour after the reftime, for metro the run time is used for both the last observation hour and first forecast hour
		long lEndTime = lStartTime + (m_dAirTemp.length * 3600000); // could use any of the array, they are the same length
		for (int nIndex = 0; nIndex < m_lTimes.length; nIndex++) // fill time array
			m_lTimes[nIndex] = lStartTime + 3600000 * nIndex;
		TileObsView oOv = (TileObsView)Directory.getInstance().lookup("ObsView");
		WayNetworks oWays = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
		DoMetroWrapper oMetro = new DoMetroWrapper(m_nObsPerRun, m_nFcstPerRun);
		Units oUnits = Units.getInstance();
		int nNonePrecip = (int)ObsType.lookup(ObsType.TYPPC, "none");
		int nSnowPrecip = (int)ObsType.lookup(ObsType.TYPPC, "snow");
		for (SegmentGroup oGroup : oScenario.m_oGroups) // for each group of the scenario
		{
			System.arraycopy(oGroup.m_bTreating, 0, m_bTreating, m_nObsPerRun - 1, oGroup.m_bTreating.length); // copy the treated and plowed booleans into the correct position
			System.arraycopy(oGroup.m_bPlowing, 0, m_bPlowing, m_nObsPerRun - 1, oGroup.m_bPlowing.length); // it is m_nObsPerRun - 1 since the runtime of metro is the time of the last observation
			for (Id oId : oGroup.m_oSegments)
			{
				OsmWay oWay = oWays.getWayById(oId);
				if (oWay == null) // skip ids that are not ways
					continue;
				resetArrays();
				
				int[] nWayBb = new int[]{oWay.m_nMidLon - nTol, oWay.m_nMidLat - nTol, oWay.m_nMidLon + nTol, oWay.m_nMidLat + nTol};
				int[] nWayQuery = GeoUtil.getBoundingPolygon(nWayBb[0], nWayBb[1], nWayBb[2], nWayBb[3]);
				
				int[] nBb = new int[]{oWay.m_nMidLon - 1, oWay.m_nMidLat - 1, oWay.m_nMidLon + 1, oWay.m_nMidLat + 1};
				int[] nQuery = GeoUtil.getBoundingPolygon(nBb[0], nBb[1], nBb[2], nBb[3]);
				int[] nStPvt = new int[oGroup.m_bPlowing.length + 1]; // will store output pavement states
				double[] dTPvt = new double [oGroup.m_bPlowing.length + 1]; // will store output pavement temperatures
				double[] dDphsn = new double[oGroup.m_bPlowing.length + 1];
				
				// fill all the data arrays for the entire length of the scenario
				for (int nIndex = 0; nIndex < m_lTimes.length; nIndex++)
				{
					long lStartQuery = m_lTimes[nIndex];
					long lEndQuery = lStartQuery + 3600000;
					getData(oOv, ObsType.TPVT, nWayBb, nWayQuery, lStartQuery, lEndQuery, lRefTime, nIndex, m_dPavementTemp, false);
					getData(oOv, ObsType.TSSRF, nWayBb, nWayQuery, lStartQuery, lEndQuery, lRefTime, nIndex, m_dSubSurfTemp, false);
					getData(oOv, ObsType.STPVT, nWayBb, lStartQuery, lEndQuery, lRefTime, nIndex, m_nPavementState);
					getData(oOv, ObsType.TAIR, nBb, nQuery, lStartQuery, lEndQuery, lRefTime, nIndex, m_dAirTemp, false);
					getData(oOv, ObsType.TDEW, nBb, nQuery, lStartQuery, lEndQuery, lRefTime, nIndex, m_dDewPoint, false);
					getData(oOv, ObsType.SPDWND, nBb, nQuery, lStartQuery, lEndQuery, lRefTime, nIndex, m_dWindSpeed, false);
					
					getData(oOv, ObsType.COVCLD, nBb, nQuery, lStartQuery, lEndQuery, lRefTime, nIndex, m_dCloudCover, false);
					getData(oOv, ObsType.PRSUR, nBb, nQuery, lStartQuery, lEndQuery, lRefTime, nIndex, m_dPressure, false);
					getData(oOv, ObsType.DPHLIQ, nWayBb, nWayQuery, lStartQuery, lEndQuery, lRefTime, nIndex, m_dRainRes, false);
					getData(oOv, ObsType.DPHSN, nWayBb, nWayQuery, lStartQuery, lEndQuery, lRefTime, nIndex, m_dSnowRes, false);
					getData(oOv, ObsType.RTEPC, nBb, nQuery, lStartQuery, lEndQuery, lRefTime, nIndex, m_dPrecipRate, true);
					getData(oOv, ObsType.TYPPC, nBb, lStartQuery, lEndQuery, lRefTime, nIndex, m_nPrecipType);
					if (m_dPrecipRate[nIndex] == 0.0)
						m_nPrecipType[nIndex] = 0;
					else if (m_nPrecipType[nIndex] == Integer.MIN_VALUE)
					{
						if (m_dAirTemp[nIndex] > -2) // if temp > -2C then precip type is rain
							m_nPrecipType[nIndex] = 1;
						else // if temp <= -2C then precip type is snow
							m_nPrecipType[nIndex] = 2;
					}
					else
					{
						int nPrecipType = m_nPrecipType[nIndex];
						if (nPrecipType == nNonePrecip)
							m_nPrecipType[nIndex] = 0; //none
						else if (nPrecipType == nSnowPrecip)
							m_nPrecipType[nIndex] = 2;  //snow
						else
							m_nPrecipType[nIndex] = 1;  //rain
					}
				}
				
				int nIndex = m_nObsPerRun - 1; // this index is for the runtime of each metro run
				for (int i = 0; i <= oGroup.m_bPlowing.length; i++) // start at 1 since the 0 hour of nStPvt and dTpvt got filled in above
				{
					oMetro.m_oOutput = null;
					oMetro.fillArrays(this, nIndex, oWay);
					oMetro.run();
					oMetro.saveRoadcast(oWay.m_nMidLat, oWay.m_nMidLon, m_lTimes[nIndex]);
					int nNext = nIndex + 1;
					int nMetroForecastIndex = 30;
					
					float[] oMetroStpvt = oMetro.m_oOutput.m_oDataArrays.get(ObsType.STPVT);
					float[] oMetroTpvt = oMetro.m_oOutput.m_oDataArrays.get(ObsType.TPVT);
					float[] oMetroDphsn = oMetro.m_oOutput.m_oDataArrays.get(ObsType.DPHSN);
					nStPvt[i] = (int)oMetroStpvt[nMetroForecastIndex];
					dTPvt[i] = oUnits.convert("C", "F", oMetroTpvt[nMetroForecastIndex]);
					dDphsn[i] = oUnits.convert("mmle", "in", oMetroDphsn[nMetroForecastIndex]);
					double dPlowedSnow = 0;
					if (m_bPlowing[nIndex - 1])
					{
						dPlowedSnow = oMetroDphsn[nMetroForecastIndex];
						nStPvt[i] = ObsType.lookup(ObsType.STPVT, "plowed");
					}
					for (int nForecast = 0; nForecast < m_nFcstPerRun - 3; nForecast++) // fill in forecasted values of the this run to be used for the next run of metro
					{
						int nMetroIndex = nMetroForecastIndex + nForecast * 3; // metro outputs are every 20 minutes so multiple by 3 to get the next hour
						int nScenarioIndex = nNext + nForecast;
						m_dPavementTemp[nScenarioIndex] = oMetroTpvt[nMetroIndex];
						m_dSubSurfTemp[nScenarioIndex] = oMetro.m_oOutput.m_oDataArrays.get(ObsType.TSSRF)[nMetroIndex];
						m_nPavementState[nScenarioIndex] = (int)oMetroStpvt[nMetroIndex];
						m_dRainRes[nScenarioIndex] = oMetro.m_oOutput.m_oDataArrays.get(ObsType.DPHLIQ)[nMetroIndex];
						m_dSnowRes[nScenarioIndex] = oMetroDphsn[nMetroIndex];
					}
					++nIndex;
				}
				
				for (int nTpvtIndex = 0; nTpvtIndex < dTPvt.length; nTpvtIndex++)
				{
					if (!Double.isFinite(dTPvt[nTpvtIndex]))
						dTPvt[nTpvtIndex] = -999.0;
					else
						dTPvt[nTpvtIndex] = GeoUtil.round(dTPvt[nTpvtIndex], 2);
					
					if (!Double.isFinite(dDphsn[nTpvtIndex]))
						dDphsn[nTpvtIndex] = -999.0;
					else
						dDphsn[nTpvtIndex] = GeoUtil.round(dDphsn[nTpvtIndex], 2);
					
					if (nStPvt[nTpvtIndex] <= 0)
						nStPvt[nTpvtIndex] = -1;
				}
				
				m_oStPvts.add(nStPvt);
				m_oTPvts.add(dTPvt);
				m_oDphsns.add(dDphsn);
			}
		}
		
	}
		
	private void getData(TileObsView oOV, int nObstype, int[] nBb, int[] nQueryGeo, long lStartQuery, long lEndQuery, long lRef, int nIndex, double[] dArr, boolean bAverage)
	{
		ObsList oData = oOV.getPreferedData(nObstype, nBb, lStartQuery, lEndQuery, lRef);

		if (!oData.isEmpty())
		{
			if (bAverage)
			{
				double dTotal = 0.0;
				int nCount = 0;

				for (Obs oObs : oData)
				{
					if (oObs.spatialMatch(nQueryGeo) && Double.isFinite(oObs.m_dValue))
					{
						dTotal += oObs.m_dValue;
						++nCount;
					}
				}

				if (nCount > 0)
					dArr[nIndex] = dTotal / nCount;
			}
			else
			{
				dArr[nIndex] = oData.get(0).m_dValue;
			}
		}
	}
	
	private void getData(TileObsView oOV, int nObstype, int[] nBb, long lStartQuery, long lEndQuery, long lRef, int nIndex, int[] nArr)
	{
		ObsList oData = oOV.getPreferedData(nObstype, nBb, lStartQuery, lEndQuery, lRef);
		
		if (!oData.isEmpty())
		{
			nArr[nIndex] = (int)oData.get(0).m_dValue;
		}
	}
	
	
	/**
	 * Resets the value of double[]s to {@code Double.NaN} and int[]s to 
	 * {@code Integer.MIN_VALUE}
	 */
	public void resetArrays()
	{
		Arrays.fill(m_dAirTemp, Double.NaN);
		Arrays.fill(m_dDewPoint, Double.NaN);
		Arrays.fill(m_dWindSpeed, Double.NaN);
		Arrays.fill(m_dPavementTemp, Double.NaN);
		Arrays.fill(m_dSubSurfTemp, Double.NaN);
		Arrays.fill(m_nPavementState, Integer.MIN_VALUE);
		Arrays.fill(m_dCloudCover, Double.NaN);
		Arrays.fill(m_dPressure, Double.NaN);
		Arrays.fill(m_nPrecipType, Integer.MIN_VALUE);
		Arrays.fill(m_dPrecipRate, Double.NaN);
		Arrays.fill(m_dRainRes, Double.NaN);
		Arrays.fill(m_dSnowRes, Double.NaN);
	}
}
