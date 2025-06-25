package imrcp.system;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Comparator;
import com.github.aelstad.keccakj.fips202.Shake256;
import java.io.IOException;
import java.util.Iterator;

/**
 * This class is used to represent identifiers of objects used in the IMRCP 
 * system.
 * @author aaron.cherney
 */
public class Id implements Comparable<Id>
{
	/**
	 * Byte indicating if the Id represents a sensor
	 */
	public static final byte SENSOR = 1;

	
	/**
	 * Byte indicating if the Id represents a node
	 */
	public static final byte NODE = 2;

	
	/**
	 * Byte indicating if the Id represents a link
	 */
	public static final byte LINK = 3;

	
	/**
	 * Byte indicating if the Id represents a roadway segment
	 */
	public static final byte SEGMENT = 4;

	
	/**
	 * Byte indicating if the Id represents a route
	 */
	public static final byte ROUTE = 5;

	
	/**
	 * Comparator that wraps {@link #compareTo(imrcp.system.Id)}
	 */
	public static final Comparator<Id> COMPARATOR = (Id o1, Id o2) -> o1.compareTo(o2);

	
	/**
	 * Size of the Ids in bytes
	 */
	protected static final int SIZE = 16;

	
	/**
	 * String used to create {@link #NULLID}
	 */
	protected static final String NULLSTRING = "00000000000000000000000000000000";

	
	/**
	 * Long that stores the first 8 bytes of the Id
	 */
	protected long m_l0;

	
	/**
	 * Long that stores the second 8 bytes of the Id
	 */
	protected long m_l1;

	
	/**
	 * Single instance used for a "null" id
	 */
	public final static Id NULLID = new Id(NULLSTRING);

	
	/**
	 * Default constructor. Does nothing.
	 */
	protected Id()
	{
	}
	
	
	/**
	 *
	 * @param nType the type of object this Id represents, use {@link #SENSOR},
	 * {@link #NODE}, {@link #LINK}, {@link #SEGMENT}, or {@link #ROUTE}
	 * @param nGrowableArr Growable array containing the unique values of the
	 * object to use in the Shake256 squeeze algorithm
	 * @throws IOException
	 */
	public Id(int nType, int[] nGrowableArr)
		throws IOException
	{
		this(nType, nGrowableArr, 1);
	}
	
	
	/**
	 *
	 * @param nType the type of object this Id represents, use {@link #SENSOR},
	 * {@link #NODE}, {@link #LINK}, {@link #SEGMENT}, or {@link #ROUTE}
	 * @param nGrowableArr
	 * @param nStart
	 * @throws IOException
	 */
	public Id(int nType, int[] nGrowableArr, int nStart)
		throws IOException
	{
		ByteArrayOutputStream oOut = new ByteArrayOutputStream();
		DataOutputStream oDataOut = new DataOutputStream(oOut);
		Iterator<int[]> oIt = Arrays.iterator(nGrowableArr, new int[1], nStart, 1);
		while (oIt.hasNext())
			oDataOut.writeInt(oIt.next()[0]);
		init(nType, new ByteArrayInputStream(oOut.toByteArray()));
	}

	
	public Id(int nType, String[] sStrings)
		throws IOException
	{
		ByteArrayOutputStream oOut = new ByteArrayOutputStream();
		DataOutputStream oDataOut = new DataOutputStream(oOut);
		for (String sStr : sStrings)
		{
			if (sStr != null)
				oDataOut.writeUTF(sStr);
		}
			
		init(nType, new ByteArrayInputStream(oOut.toByteArray()));
	}
	
	/**
	 * Constructs an Id from the String representation of it
	 * @param sId String representation of the Id, which is the 16 bytes written
	 * as a hex string
	 */
	public Id(String sId)
	{
		if (sId == null || sId.isEmpty())
			sId = NULLSTRING;
		StringBuilder sBuf = new StringBuilder(SIZE);
		sBuf.append(sId.substring(0, SIZE));
		m_l0 = Util.bytesToLong(Text.fromHexString(sBuf));
		sBuf.setLength(0);
		sBuf.append(sId.substring(SIZE));
		m_l1 = Util.bytesToLong(Text.fromHexString(sBuf));
	}

	
	/**
	 * Constructs an Id from the given DataInputStream by reading the next two
	 * longs.
	 * @param oIn DataInputStream to read from
	 * @throws IOException
	 */
	public Id(DataInputStream oIn)
		throws IOException
	{
		m_l0 = oIn.readLong();
		m_l1 = oIn.readLong();
	}
	
	
	/**
	 * Initializes the Id by using the Shake256 squeeze algorithm on the given
	 * InputStream and setting the first byte of the 16 bytes to the given
	 * type.
	 * @param nType the type of object this Id represents, use {@link #SENSOR},
	 * {@link #NODE}, {@link #LINK}, {@link #SEGMENT}, or {@link #ROUTE}
	 * @param oIn InputStream to run the Shake256 squeeze algorithm on
	 * @throws IOException
	 */
	private void init(int nType, InputStream oIn)
		throws IOException
	{
		Shake256 oMd = new Shake256();
		try
		(
			OutputStream oAbsorb = oMd.getAbsorbStream();
			InputStream oSqueeze = oMd.getSqueezeStream();
		)
		{
			int nVal;
			while ((nVal = oIn.read()) >= 0)
				oAbsorb.write(nVal);

			byte[] yBuf = new byte[SIZE];
			oSqueeze.read(yBuf, 0, SIZE);
			yBuf[0] = (byte)nType;
			DataInputStream oDataIn = new DataInputStream(new ByteArrayInputStream(yBuf));
			m_l0 = oDataIn.readLong();
			m_l1 = oDataIn.readLong();
		}
	}

	
	/**
	 * Writes the Id as two longs to the given DataOutputStream
	 * @param oOut DataOutputStream to write the Id to
	 * @throws IOException
	 */
	public void write(DataOutputStream oOut)
		throws IOException
	{
		oOut.writeLong(m_l0);
		oOut.writeLong(m_l1);
	}


	/**
	 * Compares Ids by the first 8 bytes, {@link #m_l0}, then the second 8 bytes,
	 * {@link #m_l1}
	 * @param o
	 * @return 
	 */
	@Override
	public int compareTo(Id o)
	{
		if (m_l0 < o.m_l0)
			return -1;
		else if (m_l0 > o.m_l0)
			return 1;

		if (m_l1 < o.m_l1)
			return -1;
		else if (m_l1 > o.m_l1)
			return 1;

		return 0;
	}
	
	
	/**
	 * Converts {@link #m_l0} and {@link #m_l1} first to bytes and then those
	 * bytes to a hex string.
	 * @return Hex string representation of the 16 bytes of the Id
	 */
	@Override
	public String toString()
	{
		byte[] yBytes = new byte[Long.BYTES];
		Util.longToBytes(m_l0, yBytes);
		return Text.toHexString(yBytes) + Text.toHexString(Util.longToBytes(m_l1, yBytes));
	}

	
	/**
	 * Indicates if the Id is the null Id.
	 * @param oId Id to test
	 * @return true if the Id is the null Id, otherwise false
	 */
	public static boolean isNull(Id oId)
	{
		return oId.m_l0 == 0 && oId.m_l1 == 0;
	}
	
	
	/**
	 * Incidates if the object the Id represents is a Sensor  
	 * @param oId Id to test
	 * @return true if the the object the Id represents is a Sensor, otherwise
	 * false
	 */
	public static boolean isSensor(Id oId)
	{
		return (oId.m_l0 & 0xFF00000000000000L) == (long)SENSOR << 56;
	}
	
	
	/**
	 * Incidates if the object the Id represents is a Node 
	 * @param oId Id to test
	 * @return true if the the object the Id represents is a Node, otherwise
	 * false
	 */
	public static boolean isNode(Id oId)
	{
		return (oId.m_l0 & 0xFF00000000000000L) == (long)NODE << 56;
	}
	
	
	/**
	 * Incidates if the object the Id represents is a Link
	 * @param oId Id to test
	 * @return true if the the object the Id represents is a Link, otherwise
	 * false
	 */
	public static boolean isLink(Id oId)
	{
		return (oId.m_l0 & 0xFF00000000000000L) == (long)LINK << 56;
	}
	
	
	/**
	 * Incidates if the object the Id represents is a Segment  
	 * @param oId Id to test
	 * @return true if the the object the Id represents is a Segment, otherwise
	 * false
	 */
	public static boolean isSegment(Id oId)
	{
		return (oId.m_l0 & 0xFF00000000000000L) == (long)SEGMENT << 56;
	}
	
	
	/**
	 * Incidates if the object the Id represents is a Route 
	 * @param oId Id to test
	 * @return true if the the object the Id represents is a Route, otherwise
	 * false
	 */
	public static boolean isRoute(Id oId)
	{
		return (oId.m_l0 & 0xFF00000000000000L) == (long)ROUTE << 56;
	}
	
	
	/**
	 * Gets the low bytes of the Id
	 * @return The low bytes of the Id, {@link #m_l1}
	 */
	public long getLowBytes()
	{
		return m_l1;
	}
	
	
	/**
	 * Gets the high bytes of the Id
	 * @return The high bytes of the Id, {@link #m_l0}
	 */
	public long getHighBytes()
	{
		return m_l0;
	}
	
	
	@Override
	public int hashCode()
	{
		return Long.hashCode(m_l1);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		
		if (obj == null || getClass() != obj.getClass())
			return false;
		
		final Id other = (Id) obj;
		return !(this.m_l0 == other.m_l0 && this.m_l1 == other.m_l1);
	}
}
