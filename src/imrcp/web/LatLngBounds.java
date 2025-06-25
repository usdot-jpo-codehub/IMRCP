package imrcp.web;

import imrcp.geosrv.GeoUtil;
import java.io.Serializable;

/**
 * A bounding box represented by two {@link LatLng} points, namely the north west
 * corner and the south east corner.
 * @author aaron.cherney
 */
public class LatLngBounds implements Serializable
{
	/**
	 * Point representing the north west corner of the bounding box
	 */
	private LatLng m_oNorthWest;

	
	/**
	 * Point representing the south east corner of the bounding box
	 */
	private LatLng m_oSouthEast;

	
	/**
	 * Constructs a new LatLngBounds with the given latitudes and longitudes in 
	 * integer decimal degrees scaled to 7 decimal places
	 * @param nLat1 first latitude in decimal degrees scaled to 7 decimal places
	 * @param nLng1 first longitude in decimal degrees scaled to 7 decimal places
	 * @param nLat2 second latitude in decimal degrees scaled to 7 decimal places
	 * @param nLng2 second longitude in decimal degrees scaled to 7 decimal places
	 */
	public LatLngBounds(int nLat1, int nLng1, int nLat2, int nLng2)
	{
		m_oNorthWest = new LatLng(Math.max(nLat1, nLat2), Math.min(nLng1, nLng2));
		m_oSouthEast = new LatLng(Math.min(nLat1, nLat2), Math.max(nLng1, nLng2));
	}

	
	/**
	 * Constructs a new LatLngBounds with the given latitudes and longitudes in 
	 * double decimal degrees
	 * @param dLat1 first latitude in decimal degrees
	 * @param dLng1 first longitude in decimal degrees
	 * @param dLat2 second latitude in decimal degrees
	 * @param dLng2 second longitude in decimal degrees
	 */
	public LatLngBounds(double dLat1, double dLng1, double dLat2, double dLng2)
	{
		this(GeoUtil.toIntDeg(dLat1), GeoUtil.toIntDeg(dLng1), GeoUtil.toIntDeg(dLat2), GeoUtil.toIntDeg(dLng2));
	}

	
	/**
	 * Determines if the bounding boxes made from the given latitudes and 
	 * longitudes in integer decimal degrees scaled to 7 decimal places intersects
	 * this {@link LatLngBounds}
	 * @param nLat1 first latitude in decimal degrees scaled to 7 decimal places
	 * @param nLng1 first longitude in decimal degrees scaled to 7 decimal places
	 * @param nLat2 second latitude in decimal degrees scaled to 7 decimal places
	 * @param nLng2 second longitude in decimal degrees scaled to 7 decimal places
	 * @return true if the bounding boxes intersect, otherwise false
	 */
	public boolean intersects(int nLat1, int nLng1, int nLat2, int nLng2)
	{
		int nMinLat = Math.min(nLat1, nLat2);
		int nMaxLat = Math.max(nLat1, nLat2);
		int nMinLng = Math.min(nLng1, nLng2);
		int nMaxLng = Math.max(nLng1, nLng2);

		return nMaxLat >= this.getSouth() && nMinLat <= this.getNorth() && nMinLng <= this.getEast() && nMaxLng >= this.getWest();
	}

	
	/**
	 * Gets the maximum latitude value
	 * @return Maximum (north) latitude value
	 */
	public int getNorth()
	{
		return m_oNorthWest.getLat();
	}

	
	/**
	 * Gets the minimum latitude value
	 * @return Minimum (south) latitude value
	 */
	public int getSouth()
	{
		return m_oSouthEast.getLat();
	}

	
	/**
	 * Gets the maximum longitude value
	 * @return Maximum (east) longitude value
	 */
	public int getEast()
	{
		return m_oSouthEast.getLng();
	}

	
	/**
	 * Get the minimum longitude value
	 * @return Minimum (west) longitude value
	 */
	public int getWest()
	{
		return m_oNorthWest.getLng();
	}
}
