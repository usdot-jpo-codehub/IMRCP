/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.store;

/**
 * Represents the time and location of a forecasted hurricane
 * @author aaron.cherney
 */
public class HurricaneCenter implements Comparable<HurricaneCenter>
{
	/**
	 * Time in milliseconds since Epoch the hurricane is forecasted to be at
	 * this location
	 */
	public long m_lTimestamp;

	
	/**
	 * Forecasted maximum wind speed in knots
	 */
	public int m_nMaxSpeed;

	
	/**
	 * Forecasted longitude of hurricane center in decimal degrees scaled to 7 
	 * decimal places
	 */
	public int m_nLon;

	
	/**
	 * Forecasted latitude of hurricane center in decimal degrees scaled to 7 
	 * decimal places
	 */
	public int m_nLat;

	
	/**
	 * Forecasted radius of the hurricane (minimum distance to the edge of the
	 * hurricane cone of probability from NHC)
	 */
	public double m_dDistance;

	
	/**
	 * Constructs a new HurricaneCenter with the given parameters.
	 * @param nLon Forecasted longitude of hurricane center in decimal degrees scaled to 7 
	 * decimal places
	 * @param nLat Forecasted latitude of hurricane center in decimal degrees scaled to 7 
	 * decimal places
	 * @param nWindSpeed max wind speed in knots
	 * @param lTimestamp time in milliseconds since Epoch hurricane center is at
	 * the given location
	 * @param dDistance radius of the hurricane at the given time and location
	 */
	public HurricaneCenter(int nLon, int nLat, int nWindSpeed, long lTimestamp, double dDistance)
	{
		m_nLon = nLon;
		m_nLat = nLat;
		m_nMaxSpeed = nWindSpeed;
		m_lTimestamp = lTimestamp;
		m_dDistance = dDistance;
	}

	
	/**
	 * Compares HurricaneCenters by timestamp 
	 */
	@Override
	public int compareTo(HurricaneCenter o)
	{
		return Long.compare(m_lTimestamp, o.m_lTimestamp);
	}
}
