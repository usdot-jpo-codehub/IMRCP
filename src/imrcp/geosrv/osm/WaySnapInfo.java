package imrcp.geosrv.osm;

import imrcp.geosrv.*;
import imrcp.geosrv.osm.OsmWay;

/**
 * Encapsulate information used when snapping a point to a roadway segment ({@link OsmWay})
 * @author aaron.cherney
 */
public class WaySnapInfo implements Comparable<WaySnapInfo>
{
	/**
	 * Longitude of the snap point in decimal degrees scaled to 7 decimal places
	 */
	public int m_nLonIntersect;

	
	/**
	 * Latitude of the snap point in decimal degrees scaled to 7 decimal places
	 */
	public int m_nLatIntersect;

	
	/**
	 * Squared perpendicular distance from the original point to snap point
	 */
	public int m_nSqDist;

	
	/**
	 * Right hand rule value
	 */
	public int m_nRightHandRule;

	
	/**
	 * 0 if the point snaps (projects) onto the line segment. Positive if the
	 * point snaps to the line segment past its terminal point, negative if the
	 * point snaps to the line segment before its initial point. 
	 */
	public double m_dProjSide;

	
	/**
	 * Reference to the roadway segment the point snapped to
	 */
	public OsmWay m_oWay = null;
	
	
	/**
	 * The node index of the line segment the point snapped to
	 */
	public int m_nIndex;
	
	public boolean m_bPerpAlgorithm = true;

	
	/**
	 * Default constructor. Initializes values to the defaults
	 */
	public WaySnapInfo()
	{
		m_nLonIntersect = Integer.MIN_VALUE;
		m_nLatIntersect = Integer.MIN_VALUE;
		m_nSqDist = Integer.MIN_VALUE;
		m_nRightHandRule = Integer.MIN_VALUE;
		m_dProjSide = Double.NaN;
		m_nIndex = Integer.MIN_VALUE;
	}

	
	/**
	 * Calls {@link #WaySnapInfo()} and set {@link #m_oWay} to the given roadway
	 * segment
	 * @param oWay roadway segment a point is being snapped to
	 */
	public WaySnapInfo(OsmWay oWay)
	{
		this();
		m_oWay = oWay;
	}

	
	/**
	 * Sets the values of this to the values of the given WaySnapInfo
	 * @param oInfo object to copy values from
	 */
	public void setValues(WaySnapInfo oInfo)
	{
		m_nLonIntersect = oInfo.m_nLonIntersect;
		m_nLatIntersect = oInfo.m_nLatIntersect;
		m_nSqDist = oInfo.m_nSqDist;
		m_nRightHandRule = oInfo.m_nRightHandRule;
		m_dProjSide = oInfo.m_dProjSide;
		m_nIndex = oInfo.m_nIndex;
	}

	
	/**
	 * Resets the values to the defaults.
	 */
	public void reset()
	{
		m_nLonIntersect = Integer.MIN_VALUE;
		m_nLatIntersect = Integer.MIN_VALUE;
		m_nSqDist = Integer.MIN_VALUE;
		m_nRightHandRule = Integer.MIN_VALUE;
		m_dProjSide = Double.NaN;
		m_nIndex = Integer.MIN_VALUE;
	}


	/**
	 * Compares WaySnapInfo by squared distance then by right hand rule
	 */
	@Override
	public int compareTo(WaySnapInfo o)
	{
		int nDiff = m_nSqDist - o.m_nSqDist;
		if (nDiff != 0)
			return nDiff;
		else
			return m_nRightHandRule - o.m_nRightHandRule;
	}
}
