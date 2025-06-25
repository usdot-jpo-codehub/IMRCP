package imrcp.web;

import imrcp.geosrv.GeoUtil;
import java.io.Serializable;

/**
 * A point represented by a latitude and a longitude in decimal degrees scaled 
 * to 7 decimal places
 * @author aaron.cherney
 */
public class LatLng implements Serializable
{
	/**
	 * Latitude in decimal degrees scaled to 7 decimal places
	 */
	private int m_nLat;

	
	/**
	 * Longitude in decimal degrees scaled to 7 decimal places
	 */
	private int m_nLng;

	
	/**
	 * Default constructor. Does nothing.
	 */
	public LatLng()
	{
	}

	
	/**
	 * Constructs a new LatLng with the given values
	 * @param nLat latitude in decimal degrees scaled to 7 decimal places
	 * @param nlng longitude in decimal degrees scaled to 7 decimal places
	 */
	public LatLng(int nLat, int nlng)
	{
		this.m_nLat = nLat;
		this.m_nLng = nlng;
	}

	
	/**
	 * Constructs a new LatLng with the given values by converting the decimal
	 * degrees doubles into integer decimal degrees scaled to 7 decimal places
	 * @param nLat latitude in decimal degrees
	 * @param nLng longtitude in decimal degrees
	 */
	public LatLng(double nLat, double nLng)
	{
		this(GeoUtil.toIntDeg(nLat), GeoUtil.toIntDeg(nLng));
	}

	
	/**
	 * Gets the latitude value
	 * @return latitude of the point
	 */
	public int getLat()
	{
		return m_nLat;
	}

	
	/**
	 * Sets the latitude value
	 * @param nLat latitude in decimal degrees scaled to 7 decimal places
	 */
	public void setLat(int nLat)
	{
		this.m_nLat = nLat;
	}

	
	/**
	 * Gets the longitude value
	 * @return longitude of the point
	 */
	public int getLng()
	{
		return m_nLng;
	}

	
	/**
	 * Sets the longitude value
	 * @param nLng longitude in decimal degrees scaled to 7 decimal places
	 */
	public void setLng(int nLng)
	{
		this.m_nLng = nLng;
	}
}
