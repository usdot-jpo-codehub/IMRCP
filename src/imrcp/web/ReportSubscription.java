package imrcp.web;

import imrcp.geosrv.GeoUtil;
import imrcp.store.Obs;
import imrcp.system.FileUtil;
import imrcp.system.Id;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;

/**
 * This class represents a report or subscription created by the IMRCP Create
 * Reports UI.
 * @author aaron.cherney
 */
public class ReportSubscription
{
	/**
	 * Default format enumeration, current CSV
	 */
	public static final Format DEFAULT_FORMAT = Format.CSV;

	
	/**
	 * Id of the report/subscription
	 */
	public String m_sUuid;

	
	/**
	 * User that created the report/subscription
	 */
	public String m_sUsername;

	
	/**
	 * The number of minutes that need to elapse before the subscription creates
	 * a new data file. For reports, this value is 0.
	 */
	public int m_nCycle;

	
	/**
	 * The output format to use for data files created by this report/subscription
	 */
	public Format m_sOutputFormat;

	
	/**
	 * The reference time in milliseconds since Epoch to be used for the query 
	 * to fulfill a report. Not used for subscriptions
	 */
	public long m_lRefTime;

	
	/**
	 * The start time in milliseconds since Epoch to be used for the query to
	 * fulfill a report. Not used for subscriptions
	 */
	public long m_lStartTime;

	
	/**
	 * The end time in milliseconds since Epoch to be used for the query to
	 * fulfill a report. Not used for subscriptions
	 */
	public long m_lEndTime;
	
	
	/**
	 * Minimum latitude of the roadway segments included in the report/subscription
	 */
	public int m_nMinLat;

	
	/**
	 * Minimum longitude of the roadway segments included in the report/subscription
	 */
	public int m_nMinLon;

	
	/**
	 * Maximum latitude of the roadway segments included in the report/subscription
	 */
	public int m_nMaxLat;

	
	/**
	 * Maximum longitude of the roadway segments included in the report/subscription
	 */
	public int m_nMaxLon;

	
	/**
	 * Stores the observation types to query for this report/subscription
	 */
	public int[] m_nObsTypes = new int[0];

	
	/**
	 * Stores the Ids of the roadway segments included in this report/subscription
	 */
	public Id[] m_oElementIds = new Id[0];

	
	/**
	 * Minimum value of observations to include in the report/subscription
	 */
	public double m_dMin;

	
	/**
	 * Maximum value of observation to include in the report/subscription
	 */
	public double m_dMax;

	
	/**
	 * Time in milliseconds since Epoch the report/subscription was fulfilled
	 */
	public long m_lFulfillmentTime;

	
	/**
	 * Time in milliseconds since Epoch the report/subscription was last
	 * accessed
	 */
	public long m_lLastAccess;

	
	/**
	 * Time in milliseconds since Epoch the report/subscription was created
	 */
	public long m_lCreatedTime;

	
	/**
	 * Time in minutes to offset the reference time when creating a data file to
	 * get the start time of the query.
	 */
	public int m_nOffset;

	
	/**
	 * Time in minutes to add to the start time of a query to get the end time of
	 * the query
	 */
	public int m_nDuration;

	
	/**
	 * Name given to the report/subscription by the user
	 */
	public String m_sName;

	
	/**
	 * Description given to the report/subscription by the user
	 */
	public String m_sDescription;

	
	/**
	 * Value in decimal degrees scaled to 7 decimal place to add to the bounds
	 * of the report
	 */
	private final static int m_nTOL = 1;
	
	
	/**
	 * Path to the file that stores the configuration parameters for the report/subscription
	 */
	private String m_sFilename;

	
	/**
	 * Default constructor. Calls {@link #clearAll()} to set all values to their
	 * defaults.
	 */
	public ReportSubscription()
	{
		clearAll();
	}

	
	/**
	 * Constructs a ReportSubscription from the Path that points to the 
	 * report/subscription configuration file.
	 * @param oPath Path to the report/subscription configuration file
	 * @throws Exception
	 */
	public ReportSubscription(Path oPath)
		throws Exception
	{
		m_sFilename = oPath.toString();
		try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(Files.newInputStream(oPath))))
		{
			m_lLastAccess = oIn.readLong();
			m_lFulfillmentTime = oIn.readLong();
			m_lCreatedTime = oIn.readLong();
			m_lStartTime = oIn.readLong();
			m_lEndTime = oIn.readLong();
			m_lRefTime = oIn.readLong();
			m_nCycle = oIn.readInt();
			m_nOffset = oIn.readInt();
			m_nDuration = oIn.readInt();
			m_dMax = oIn.readDouble();
			m_dMin = oIn.readDouble();
			m_nObsTypes = new int[oIn.readByte()];
			
			for (int nIndex = 0; nIndex < m_nObsTypes.length; nIndex++)
				m_nObsTypes[nIndex] = oIn.readInt();
			m_oElementIds = new Id[oIn.readInt()];
			
			for (int nIndex = 0; nIndex < m_oElementIds.length; nIndex++)
				m_oElementIds[nIndex] = new Id(oIn);

			java.util.Arrays.sort(m_oElementIds, Id.COMPARATOR);
			m_nMinLon = oIn.readInt();
			m_nMinLat = oIn.readInt();
			m_nMaxLon = oIn.readInt();
			m_nMaxLat = oIn.readInt();
			
			m_sUuid = oIn.readUTF();
			m_sName = oIn.readUTF();
			m_sDescription = oIn.readUTF();
			m_sUsername = oIn.readUTF();
			m_sOutputFormat = Format.valueOf(oIn.readUTF());
		}
	}

	
	/**
	 * Constructs a ReportSubscription from the given Http request
	 * @param oReq object that contains the request the client has made of the servlet
	 * @throws Exception
	 */
	public ReportSubscription(HttpServletRequest oReq)
		throws Exception
	{
		clearAll();

		m_sName = oReq.getParameter("name");
		m_sDescription = oReq.getParameter("description");
		if (m_sDescription == null)
			m_sDescription = "";
		m_sUuid = oReq.getParameter("uuid");
		
		m_nMinLon = GeoUtil.toIntDeg(Double.parseDouble(oReq.getParameter("minLon")));
		m_nMinLat = GeoUtil.toIntDeg(Double.parseDouble(oReq.getParameter("minLat")));
		m_nMaxLon = GeoUtil.toIntDeg(Double.parseDouble(oReq.getParameter("maxLon")));
		m_nMaxLat = GeoUtil.toIntDeg(Double.parseDouble(oReq.getParameter("maxLat")));


		if (m_nMinLat == m_nMaxLat)
		{
			m_nMinLat -= m_nTOL;
			m_nMaxLat += m_nTOL;
		}

		if (m_nMinLon == m_nMaxLon)
		{
			m_nMinLon -= m_nTOL;
			m_nMaxLon += m_nTOL;
		}

		fixBoundary();
		
		String[] sIds = oReq.getParameterValues("elementIds[]");
		if (sIds != null)
		{
			m_oElementIds = new Id[sIds.length];
			for (int nIndex = 0; nIndex < m_oElementIds.length; nIndex++)
				m_oElementIds[nIndex] = new Id(sIds[nIndex]);
		}
		else
			m_oElementIds = new Id[0];
		
		java.util.Arrays.sort(m_oElementIds, Id.COMPARATOR);
		
		String[] sObsTypes = oReq.getParameterValues("obsTypeId[]");
		if (sObsTypes != null)
		{
			int nMin = Math.min(sObsTypes.length, 5); // can only have a max of 5 obstypes
			m_nObsTypes = new int[nMin];
			for (int nIndex = 0; nIndex < nMin; nIndex++)
				m_nObsTypes[nIndex] = Integer.parseInt(sObsTypes[nIndex]);
		}
		else
		{
			m_nObsTypes = new int[0];
		}

		if (m_nObsTypes.length == 1)
		{
			String sMin = oReq.getParameter("minValue");
			if (sMin != null && !sMin.trim().isEmpty())
				m_dMin = Integer.parseInt(sMin);

			String sMax = oReq.getParameter("maxValue");
			if (sMax != null && !sMax.trim().isEmpty())
				m_dMax = Integer.parseInt(sMax);
		}

		String sCycle = oReq.getParameter("cycle");
		if (sCycle != null && !sCycle.trim().isEmpty())
			m_nCycle = Integer.parseInt(sCycle);

		String sOffset = oReq.getParameter("offset");
		if (sOffset != null && !sOffset.trim().isEmpty())
			m_nOffset = Integer.parseInt(sOffset);

		String sDuration = oReq.getParameter("duration");
		if (sDuration != null && !sDuration.trim().isEmpty())
			m_nDuration = Integer.parseInt(sDuration);

		String sRef = oReq.getParameter("reftime");
		if (sRef != null && !sRef.trim().isEmpty())
			m_lRefTime = Long.parseLong(sRef);

		String sStart = oReq.getParameter("starttime");
		String sEnd = oReq.getParameter("endtime");

		if (sStart != null && sEnd != null && !sStart.trim().isEmpty() && !sEnd.trim().isEmpty())
		{
			m_lStartTime = Long.parseLong(sStart);
			m_lEndTime = Long.parseLong(sEnd);
		}
		else
		{
			m_lStartTime = 0;
			m_lEndTime = 0;
		}

		String sFormat = oReq.getParameter("format");
		if (sFormat == null)
			m_sOutputFormat = DEFAULT_FORMAT;
		else
			m_sOutputFormat = Format.valueOf(sFormat);

		m_lCreatedTime = System.currentTimeMillis();
		// subscription requires a cycle, report requires start/end dates.
		if (!(isReport() || isSubscription()))
			throw new Exception("No valid combination of subscription or report parameters");
	}

	
	/**
	 * Swaps the bounding box lat and lons if needed
	 */
	private void fixBoundary()
	{
		int nTemp;
		if (m_nMinLat > m_nMaxLat)
		{
			nTemp = m_nMaxLat;
			m_nMaxLat = m_nMinLat;
			m_nMinLat = nTemp;
		}

		if (m_nMinLon > m_nMaxLon)
		{
			nTemp = m_nMaxLon;
			m_nMaxLon = m_nMinLon;
			m_nMinLon = nTemp;
		}
	}

	
	/**
	 * Resets all the member variables to the default values
	 */
	public void clearAll()
	{
		m_sOutputFormat = DEFAULT_FORMAT;
		m_sUuid = null;
		m_lRefTime = Long.MIN_VALUE;
		m_lStartTime = Long.MIN_VALUE;
		m_lEndTime = Long.MIN_VALUE;

		m_nMinLat = m_nMinLon = -Integer.MIN_VALUE;
		m_nMaxLat = m_nMaxLon = Integer.MIN_VALUE;

		m_nCycle = 0;
		m_nObsTypes = new int[0];
		m_oElementIds = new Id[0];
		m_dMin = Double.NEGATIVE_INFINITY;
		m_dMax = Double.POSITIVE_INFINITY;
	}

	
	/**
	 * Determines if the value in within the min and max value.
	 * @param dValue value to test
	 * @return true if the value satisfies {@code m_dMin <= dValue <= m_dMax} or
	 * the length of {@link #m_nObsTypes} is 0 true, otherwise false.
	 * 
	 */
	public boolean inRange(double dValue)
	{
		if (m_nObsTypes.length == 0)
			return true;

		return (dValue >= m_dMin && dValue <= m_dMax);
	}

	
	/**
	 * Determines if the given bounding box intersects the bounding box of the
	 * report/subscription
	 * @param nLat1 minimum latitude in decimal degrees scaled to 7 decimal places
	 * @param nLon1 maximum longitude in decimal degrees scaled to 7 decimal places
	 * @param nLat2 minimum latitude in decimal degrees scaled to 7 decimal places 
	 * @param nLon2 maximum longitude in decimal degrees scaled to 7 decimal places
	 * @return
	 */
	public boolean inRegion(int nLat1, int nLon1, int nLat2, int nLon2)
	{
		return (nLat2 >= m_nMinLat && nLat1 <= m_nMaxLat && nLon2 >= m_nMinLon && nLon1 <= m_nMaxLon);
	}

	
	/**
	 * Determine if the observation type id matches the report/subscription's
	 * observation type
	 * @param nObsType IMRCP observation type id
	 * @return true if {@link #m_nObsTypes} length is 0 or if {@code nObsType}
	 * equals one of the values in {@link #m_nObsTypes}
	 */
	public boolean isObs(int nObsType)
	{
		if (m_nObsTypes.length == 0)
			return true;
		for (int nSubObstype : m_nObsTypes)
		{
			if (nSubObstype == nObsType)
				return true;
		}
		return false;
	}

	
	/**
	 * Determines if the Observation matches the query parameters of the report/subscription
	 * by calling {@link #isObs(int)}, {@link #inRange(double)}, and {@link #inRegion(int, int, int, int)}
	 * @param oSubObs the Obs to check
	 * @return true if {@link #isObs(int)}, {@link #inRange(double)}, and {@link #inRegion(int, int, int, int)}
	 * all return true
	 */
	boolean matches(Obs oSubObs)
	{
		boolean matchesObstype = isObs(oSubObs.m_nObsTypeId);
		boolean inRange = inRange(oSubObs.m_dValue);
		boolean inRegion = oSubObs.spatialMatch(m_nMinLon, m_nMinLat, m_nMaxLon, m_nMaxLat);
		return inRange && inRegion && matchesObstype;
	}

	
	/**
	 * Determines if this object represents a report
	 * @return true if this object is a report, otherwise false
	 */
	public boolean isReport()
	{
		return m_lStartTime != 0 && m_lEndTime != 0;
	}

	
	/**
	 * Determines if this object represents a subscription
	 * @return true if this object is a subscription, otherwise false.
	 */
	public boolean isSubscription()
	{
		return m_nCycle != 0;
	}

	
	/**
	 * Determines if this report/subscription has a configured observation type
	 * id array.
	 * @return {@code m_nObsTypes != null}
	 */
	public boolean hasObstype()
	{
		return m_nObsTypes != null;
	}

	
	/**
	 * Determines if this report/subscription has a configured minimum value
	 * @return true if {@link #m_dMin is a finite value}
	 */
	public boolean hasMin()
	{
		return !Double.isInfinite(m_dMin);
	}

	
	/**
	 * Determines if this report/subscription has a configured maximum value
	 * @return true if {@link #m_dMax is a finite value}
	 */
	public boolean hasMax()
	{
		return !Double.isInfinite(m_dMax);
	}

	
	/**
	 * Enumeration for output formats
	 */
	public enum Format
	{
		/**
		 * Comma Separated Values
		 */
		CSV
	}

	
	/**
	 * Write the parameters of this report/subscription to its configuration 
	 * file.
	 * 
	 * @param sBaseDir base directory for report/subscriptions
	 * @param sFileFormat Format String used to generate file names
	 * @throws Exception
	 */
	public void writeSubConfig(String sBaseDir, String sFileFormat)
		throws Exception
	{
		Path oPath;
		do
		{
			m_sUuid = UUID.randomUUID().toString();
			oPath = Paths.get(sBaseDir + String.format(sFileFormat, m_sUuid));
		} while (Files.exists(oPath)); // ensure no collisions 
		
		Files.createDirectories(oPath.getParent(), FileUtil.DIRPERS);
		m_sFilename = oPath.toString();
		try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(oPath))))
		{
			oOut.writeLong(m_lLastAccess);
			oOut.writeLong(m_lFulfillmentTime);
			oOut.writeLong(m_lCreatedTime);
			oOut.writeLong(m_lStartTime);
			oOut.writeLong(m_lEndTime);
			oOut.writeLong(m_lRefTime);
			oOut.writeInt(m_nCycle);
			oOut.writeInt(m_nOffset);
			oOut.writeInt(m_nDuration);
			oOut.writeDouble(m_dMax);
			oOut.writeDouble(m_dMin);
			oOut.writeByte(m_nObsTypes.length);
			for (int nIndex = 0; nIndex < m_nObsTypes.length; nIndex++)
				oOut.writeInt(m_nObsTypes[nIndex]);
			oOut.writeInt(m_oElementIds.length);
			for (int nIndex = 0; nIndex < m_oElementIds.length; nIndex++)
				m_oElementIds[nIndex].write(oOut);
			oOut.writeInt(m_nMinLon);
			oOut.writeInt(m_nMinLat);
			oOut.writeInt(m_nMaxLon);
			oOut.writeInt(m_nMaxLat);
			oOut.writeUTF(m_sUuid);
			oOut.writeUTF(m_sName);
			oOut.writeUTF(m_sDescription);
			oOut.writeUTF(m_sUsername);
			oOut.writeUTF(m_sOutputFormat.name());
		}
	}
	
	
	/**
	 * Updates the fulfillment time in the report/subscription configuration file
	 * to the current time
	 * @throws Exception
	 */
	public void updateFulfillmentTime()
		throws Exception
	{
		try (RandomAccessFile oRaf = new RandomAccessFile(m_sFilename, "rw"))
		{
			oRaf.seek(8); // skip the first long to get to fulfillment time
			oRaf.writeLong(System.currentTimeMillis());
		}
	}
	
	
	/**
	 * Updates the last access time in the report/subscription configuration file
	 * to the current time
	 * @throws Exception
	 */
	public void updateLastAccess()
		throws Exception
	{
		try (RandomAccessFile oRaf = new RandomAccessFile(m_sFilename, "rw"))
		{
			oRaf.writeLong(System.currentTimeMillis()); // last access is in the first position of the file so we do not need to seek
		}
	}
}
