package imrcp.comp;

import java.util.ArrayList;

/**
 * Represents a set of locations for a Notification that have the same start time,
 * end time and value (event type). The int[] in the list are bounding boxes in 
 * the format [min lat, max lat, min lon, max lon]
 * @author aaron.cherney
 */
public class NotificationSet extends ArrayList<int[]> implements Comparable<NotificationSet>
{
	/**
	 * Minimum latitude of all the locations
	 */
	int m_nLat1 = Integer.MAX_VALUE;

	
	/**
	 * Minimum longitude of all the locations
	 */
	int m_nLon1 = Integer.MAX_VALUE;

	
	/**
	 * Maximum latitude of all the locations
	 */
	int m_nLat2 = Integer.MIN_VALUE;

	
	/**
	 * Maximum longitude of all the locations
	 */
	int m_nLon2 = Integer.MIN_VALUE;

	
	/**
	 * Start time of the notification in milliseconds since Epoch
	 */
	long m_lStartTime;

	
	/**
	 * End time of the notification in milliseconds since Epoch
	 */
	long m_lEndTime;

	
	/**
	 * Received time of the notification in milliseconds since Epoch
	 */
	long m_lTimeRecv = Long.MAX_VALUE;

	
	/**
	 * Event type
	 * 
	 * @see imrcp.system.ObsType#LOOKUP for complete list of types
	 */
	double m_dValue;

	
	/**
	 * Stores the detail string
	 */
	ArrayList<String> m_sDetails = new ArrayList();

	
	/**
	 * Default constructor. Wrapper for {@link java.util.ArrayList#ArrayList()}
	 */
	NotificationSet()
	{
		super();
	}

	
	/**
	 * Calls {@link java.util.ArrayList#ArrayList()} then sets the member variables
	 * to the given values.
	 * @param lStart start time in milliseconds since Epoch
	 * @param lEnd end time in milliseconds since Epoch
	 * @param dVal event type
	 * @param lRecv received time in milliseconds since Epoch
	 */
	NotificationSet(long lStart, long lEnd, double dVal, long lRecv)
	{
		super();
		m_lStartTime = lStart;
		m_lEndTime = lEnd;
		m_dValue = dVal;
		if (m_lTimeRecv > lRecv)
			m_lTimeRecv = lRecv;
	}


	/**
	 * If {@link java.util.ArrayList#add(java.lang.Object)} returns true,
	 * updates the min and max lon/lat values for the NotificationSet
	 * @param nAdd location represented by [min lat, max lat, min lon, max lon]
	 * @return value returned by {@link java.util.ArrayList#add(java.lang.Object)}
	 */
	@Override
	public boolean add(int[] nAdd)
	{
		if (super.add(nAdd))
		{
			if (nAdd[0] < m_nLat1)
				m_nLat1 = nAdd[0];

			if (nAdd[1] != Integer.MIN_VALUE && nAdd[1] > m_nLat2)
				m_nLat2 = nAdd[1];

			if (nAdd[2] < m_nLon1)
				m_nLon1 = nAdd[2];

			if (nAdd[3] != Integer.MIN_VALUE && nAdd[3] > m_nLon2)
				m_nLon2 = nAdd[3];

			return true;
		}
		else
			return false;
	}


	/**
	 * Compares NotificationSets by value, then start time, then end time.
	 * 
	 * @param o the NotificationSet to be compared
	 * @return a negative integer, zero, or a positive integer as this object 
	 * is less than, equal to, or greater than the specified object.
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object) 
	 */
	@Override
	public int compareTo(NotificationSet o)
	{
		int nReturn = Double.compare(m_dValue, o.m_dValue);
		if (nReturn == 0)
		{
			nReturn = Long.compare(m_lStartTime, o.m_lStartTime);
			if (nReturn == 0)
				nReturn = Long.compare(m_lEndTime, o.m_lEndTime);
		}
		return nReturn;
	}
}
