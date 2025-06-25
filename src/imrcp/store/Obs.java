package imrcp.store;

import imrcp.geosrv.GeoUtil;
import imrcp.system.Arrays;
import imrcp.system.Id;
import imrcp.system.ObsType;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Class used to represent a generic Observation
 * @author aaron.cherney
 */
public class Obs
{
	public static final byte POINT = 1;
	public static final byte LINESTRING = 2;
	public static final byte POLYGON = 3;
	public static final byte MULTIPOINT = -1;
	public static final byte MULTILINESTRING = -2;
	public static final byte MULTIPOLYGON = -3;
	public static final byte BRIDGEFLAG = 3;
	public static final byte MOBILEFLAG = 2;
	public static final byte EVENTFLAG = 1;
	public static final byte RESERVEDFLAG = 0;
	
	
	/**
	 * IMRCP observation type id. The observation types are defined in the ObsType
	 * class. These values are computed by converting an up to 6 character
	 * alphanumeric string in to an integer using base 36
	 * 
	 * @see imrcp.system.ObsType
	 */
	public int m_nObsTypeId;

	
	/**
	 * IMRCP contributor Id which is a computed by converting an up to a 6 
	 * character alphanumeric string using base 36.
	 */
	public int m_nContribId;

	
	/**
	 * Id of the object the Obs is associated with. If there is no associated 
	 * object us {@link imrcp.system.Id#NULLID}
	 */
	public Id m_oObjId;

	
	/**
	 * Start time of the Obs in milliseconds since Epoch
	 */
	public long m_lObsTime1;

	
	/**
	 * End time of the Obs in milliseconds since Epoch
	 */
	public long m_lObsTime2;

	
	/**
	 * Received time of the Obs in milliseconds since Epoch
	 */
	public long m_lTimeRecv;

	
	/**
	 * Value of the Obs
	 */
	public double m_dValue;

	
	/**
	 * Time in milliseconds since Epoch the Obs stops being valid. Used to
	 * show that alerts or events have expired. If an Obs hasn't expired, use
	 * {@code Long.MIN_VALUE}
	 */
	public long m_lClearedTime = Long.MIN_VALUE;
	
	public int[] m_oGeoArray;
	
	
	public byte m_yGeoType;
	
	public static int HOLEFLAG = 1;
	public static int BBSTART = 2;
	public static int POINTSTART = 6;
	
	public String[] m_sStrings = null;
	public int m_nObsFlag;
	
	/**
	 * Compares Obs by start time the observation type id
	 */
	public static final Comparator<Obs> g_oCompObsByTimeType = (Obs o1, Obs o2) ->
	{
		int nRet = Long.compare(o1.m_lObsTime1, o2.m_lObsTime1);
		if (nRet == 0)
			nRet = o1.m_nObsTypeId - o2.m_nObsTypeId;
		return nRet;
	};
	
	
	/**
	 * Compares Obs by start time
	 */
	public static final Comparator<Obs> g_oCompObsByTime = (Obs o1, Obs o2) -> Long.compare(o1.m_lObsTime1, o2.m_lObsTime1);

	
	/**
	 * Compares Obs by associated object id
	 */
	public static final Comparator<Obs> g_oCompObsByObjId = (Obs o1, Obs o2) -> Id.COMPARATOR.compare(o1.m_oObjId, o2.m_oObjId);

	
	/**
	 * Compares Obs by start time, observation type id, contributor id, then associated
	 * object id
	 */
	public static final Comparator<Obs> g_oCompObsByTimeTypeContribObj = (Obs o1, Obs o2) ->
	{
		int nReturn = Long.compare(o1.m_lObsTime1, o2.m_lObsTime1);
		if (nReturn == 0)
		{
			nReturn = o1.m_nObsTypeId - o2.m_nObsTypeId;
			if (nReturn == 0)
			{
				nReturn = o1.m_nContribId - o2.m_nContribId;
				if (nReturn == 0)
				{
					nReturn = Id.COMPARATOR.compare(o1.m_oObjId, o2.m_oObjId);
				}
			}
		}
		return nReturn;
	};
	
	
	/**
	 * Compares Obs by contributor id
	 */
	public static final Comparator<Obs> g_oCompObsByContrib = (Obs o1, Obs o2) ->
	{
		return o1.m_nContribId - o2.m_nContribId;
	};
	
	
	/**
	 * Compares Obs by value
	 */
	public static final Comparator<Obs> g_oCompObsByValue = (Obs o1, Obs o2) ->
	{
		return Double.compare(o1.m_dValue, o2.m_dValue);
	};
	
	
	/**
	 * Compares Obs by associated object id then start time
	 */
	public static final Comparator<Obs> g_oCompObsByIdTime = (Obs o1, Obs o2) ->
	{
		int nReturn = Id.COMPARATOR.compare(o1.m_oObjId, o2.m_oObjId);
		if (nReturn == 0)
			nReturn = Long.compare(o1.m_lObsTime1, o2.m_lObsTime1);
		return nReturn;
	};
	
	
	/**
	 * Compares Obs by contributor id then min lat, then min lon, then max lat
	 */
	public static final Comparator<Obs> g_oCompObsByContribLocation = (Obs o1, Obs o2) ->
	{
		int nReturn = o1.m_nContribId - o2.m_nContribId;
		if (nReturn == 0)
		{
			nReturn = o1.m_oGeoArray[2] - o2.m_oGeoArray[2];
			if (nReturn == 0)
			{
				nReturn = o1.m_oGeoArray[1] - o2.m_oGeoArray[1];
			}				
		}
			
		
		return nReturn;
	};
	
	
	public static final Comparator<Obs> g_oCompObsByObjTypeRecv = (Obs o1, Obs o2) ->
	{
		int nReturn = Id.COMPARATOR.compare(o1.m_oObjId, o2.m_oObjId);
		if (nReturn == 0)
		{
			nReturn = o1.m_nObsTypeId - o2.m_nObsTypeId;
			if (nReturn == 0)
				nReturn = Long.compare(o1.m_lTimeRecv, o2.m_lTimeRecv);
		}
		
		return nReturn;
	};

	
	/**
	 * Default constructor. Does nothing.
	 */
	public Obs()
	{
	}


	
	/**
	 * Constructs an Obs with the given parameters
	 * 
	 * @param nObsTypeId IMRCP observation id
	 * @param nContribId IMRCP contributor id
	 * @param oObjId associated object id
	 * @param lObsTime1 start time in milliseconds since Epoch
	 * @param lObsTime2 end time in milliseconds since Epoch
	 * @param lTimeRecv received time in milliseconds since Epoch
	 * @param dValue value of the obs
	 */
	public Obs(int nObsTypeId, int nContribId, Id oObjId, long lObsTime1, long lObsTime2, long lTimeRecv, int[] oGeo, byte yGeoType, double dValue, String... sStrings)
	{
		m_nObsTypeId = nObsTypeId;
		m_nContribId = nContribId;
		m_oObjId = oObjId == null ? Id.NULLID : oObjId;
		m_lObsTime1 = lObsTime1;
		m_lObsTime2 = lObsTime2;
		m_lTimeRecv = lTimeRecv;
		m_oGeoArray = oGeo;
		m_yGeoType = yGeoType;
		m_dValue = dValue;
		if (sStrings != null && sStrings.length > 0)
		{
			m_sStrings = new String[8];
			System.arraycopy(sStrings, 0, m_sStrings, 0, Math.min(sStrings.length, 8));
		}
	}

	
	/**
	 * Constructs an Obs with the given parameters
	 * 
	 * @param nObsTypeId IMRCP observation id
	 * @param nContribId IMRCP contributor id
	 * @param oObjId associated object id
	 * @param lObsTime1 start time in milliseconds since Epoch
	 * @param lObsTime2 end time in milliseconds since Epoch
	 * @param lTimeRecv received time in milliseconds since Epoch
	 * @param dValue value of the obs
	 * @param tConf confidence value
	 * @param sDetail additional obs detail
	 * @param lClearedTime time the obs expires in milliseconds since Epoch, if 
	 * the obs is not expired use {@link java.lang.Long#MIN_VALUE}
	 */
	public Obs(int nObsTypeId, int nContribId, Id oObjId, long lObsTime1, long lObsTime2, long lTimeRecv, int[] oGeo, byte yGeoType, double dValue, long lClearedTime, String... sStrings)
	{
		this(nObsTypeId, nContribId, oObjId, lObsTime1, lObsTime2, lTimeRecv, oGeo, yGeoType, dValue, sStrings);
		m_lClearedTime = lClearedTime;
	}

	public Obs(Obs oObs)
	{
		m_nObsTypeId = oObs.m_nObsTypeId;
		m_nContribId = oObs.m_nContribId;
		m_oObjId = oObs.m_oObjId;
		m_lObsTime1 = oObs.m_lObsTime1;
		m_lObsTime2 = oObs.m_lObsTime2;
		m_lTimeRecv = oObs.m_lTimeRecv;
		m_oGeoArray = oObs.m_oGeoArray;
		m_yGeoType = oObs.m_yGeoType;
		m_dValue = oObs.m_dValue;
		m_sStrings = oObs.m_sStrings;
	}
	
	
	public boolean matches(int nObsType, long lStartTime, long lEndTime, long lRefTime, int nStartLat, int nEndLat, int nStartLon, int nEndLon)
	{
		return (m_nObsTypeId == nObsType || nObsType == ObsType.VARIES) 
			&& temporalMatch(lStartTime, lEndTime, lRefTime) 
			&& spatialMatch(nStartLon, nStartLat, nEndLon, nEndLat);
	}
	
	public boolean temporalMatch(long lStartTime, long lEndTime, long lRefTime)
	{
		return (m_lTimeRecv <= lRefTime || m_lTimeRecv > m_lObsTime1) 
			&& m_lObsTime1 < lEndTime && m_lObsTime2 > lStartTime  
			&& (m_lClearedTime < 0 || m_lClearedTime > lRefTime);
	}
	
	
	public boolean spatialMatch(int[] nGeometry)
	{
		return GeoUtil.isInsideRingAndHoles(nGeometry, m_yGeoType, m_oGeoArray) ||
			(m_yGeoType == POLYGON && GeoUtil.isInsideRingAndHoles(m_oGeoArray, m_yGeoType, nGeometry));
	}
	
	
	public boolean spatialMatch(int nStartLon, int nStartLat, int nEndLon, int nEndLat)
	{
		return spatialMatch(GeoUtil.getBoundingPolygon(nStartLon, nStartLat, nEndLon, nEndLat));
	}
	
	
	public static int[] createPoint(int nLon, int nLat)
	{
		int[] nPoint = Arrays.newIntArray(2);
		nPoint = Arrays.add(nPoint, nLon, nLat);
		return nPoint;
	}
	
		
	
	public static int addFlag(int nFlags, int nFlag)
	{
		return nFlags | (1  << nFlag);
	}
	
	
	public static boolean hasFlag(int nFlags, int nFlag)
	{
		return ((nFlags >> nFlag) & 1) == 1;
	}
	
	
	public static void writeStrings(String[] sStrings, DataOutputStream oOut, ArrayList<String> oSP)
		throws IOException
	{
		if (sStrings == null)
			return;
		
		int nFlag = 8;
		while (nFlag-- > 0)
		{
			if (sStrings[nFlag] != null)
				oOut.writeInt(Collections.binarySearch(oSP, sStrings[nFlag]));
		}
		
	}
	
	public void writeStrings(DataOutputStream oOut, ArrayList<String> oSP)
		throws IOException
	{
		writeStrings(m_sStrings, oOut, oSP);
	}
	
	
	public boolean isBridge()
	{
		return ((m_nObsFlag >> BRIDGEFLAG) & 1) == 1;
	}
	
	
	public boolean isMobile()
	{
		return ((m_nObsFlag >> MOBILEFLAG) & 1) == 1;
	}

	public String getPresentationString()
	{
		if (m_sStrings == null)
			return "";
		
		StringBuilder sRet = new StringBuilder();
		if (m_nContribId == Integer.valueOf("wxde", 36))
			sRet.append(m_sStrings[0]).append(" ").append(m_sStrings[1]);
		else if (m_nContribId == Integer.valueOf("cap", 36))
			sRet.append(ObsType.lookup(ObsType.EVT, (int)m_dValue));
		else if (m_nObsTypeId == ObsType.EVT)
			sRet.append(m_sStrings[1]).append(" ").append(m_sStrings[2]);
		else
		{
			for (String sStr : m_sStrings)
			{
				if (sStr != null)
					sRet.append(sStr).append(' ');
			}
			if (sRet.isEmpty())
				return "";

			sRet.setLength(sRet.length() - 1);
		}
		
		return sRet.toString();
	}
	
	
	/**
	 * Closes the event by setting the end time to the given timestamp. Some 
	 * child classes use the SimpleDateFormat even though the base implementation
	 * does not need it.
	 * @param lTime end time of the event in milliseconds since Epoch
	 * @param oSdf date parsing/formatting object
	 */
	public void close(long lTime, SimpleDateFormat oSdf)
	{
		m_lObsTime2 = lTime;
	}
}
