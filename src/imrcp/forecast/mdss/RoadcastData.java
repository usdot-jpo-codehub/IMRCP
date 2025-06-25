/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.forecast.mdss;

import imrcp.system.ObsType;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * METRo calls its outputs Roadcasts which contain forecasts for the pavement 
 * state, pavement temperature, subsurface temperature, depth of accumulated 
 * liquid , and depth of accumulated ice/snow
 * @author aaron.cherney
 */
public class RoadcastData implements Comparable<RoadcastData>
{
	TreeMap<Integer, float[]> m_oDataArrays = new TreeMap();
	
	
	/**
	 * Longitude where METRo was ran in decimal degrees scaled to 7 decimal places
	 */
	int m_nLon;

	
	/**
	 * Latitude where METRo was ran in decimal degrees scaled to 7 decimal places
	 */
	int m_nLat;

	
	/**
	 * Constructs a RoadcastData with the given longitude and latitude and 
	 * allocates the output arrays with the size of the given value.
	 * @param nOutputs Number of outputs
	 * @param nLon Longitude where METRo was ran in decimal degrees scaled to 7 
	 * decimal places
	 * @param nLat Latitude where METRo was ran in decimal degrees scaled to 7 
	 * decimal places
	 */
	RoadcastData(int nOutputs, int nLon, int nLat)
	{
		m_oDataArrays.put(ObsType.TPVT, new float[nOutputs]);
		m_oDataArrays.put(ObsType.STPVT, new float[nOutputs]);
		m_oDataArrays.put(ObsType.TSSRF, new float[nOutputs]);
		m_oDataArrays.put(ObsType.DPHLIQ, new float[nOutputs]);
		m_oDataArrays.put(ObsType.DPHSN, new float[nOutputs]);
		m_nLon = nLon;
		m_nLat = nLat;
	}
	
	
	/**
	 * Copy constructor used for locations that have the save input parameters 
	 * to METRo and therefore the same outputs.
	 * @param oCopy RoadcastData to copy output arrays from
	 * @param nLon Longitude where METRo was ran in decimal degrees scaled to 7 
	 * decimal places
	 * @param nLat Latitude where METRo was ran in decimal degrees scaled to 7 
	 * decimal places
	 */
	RoadcastData(RoadcastData oCopy, int nLon, int nLat)
	{
		for (Entry<Integer, float[]> oEntry : oCopy.m_oDataArrays.entrySet())
			m_oDataArrays.put(oEntry.getKey(), oEntry.getValue());

		m_nLon = nLon;
		m_nLat = nLat;
	}

	
	/**
	 * Compares RoadcastData by longitude, then latitude
	 * @param o the object to be compared
	 * @return a negative integer, zero, or a positive integer as this object is
	 * less than, equal to, or greater than the specified object.
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object) 
	 */
	@Override
	public int compareTo(RoadcastData o)
	{
		int nRet = Integer.compare(m_nLon, o.m_nLon);
		if (nRet == 0)
			nRet = Integer.compare(m_nLat, o.m_nLat);
		return nRet;
	}
}
