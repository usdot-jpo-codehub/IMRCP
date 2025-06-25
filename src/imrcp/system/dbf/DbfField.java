package imrcp.system.dbf;

import java.io.DataInputStream;

/**
 * Holds data base file field
 *
 * @author bryan.krueger
 * @version 1.0
 */
public abstract class DbfField implements Comparable<String>
{

	/**
	 * Maximum metadata field length.
	 */
	public static final int METADATA_LENGTH = 32;

	/**
	 * Data field starting position.
	 */
	protected int m_nStartIndex;

	/**
	 * Data field length.
	 */
	protected int m_nDataLength;

	/**
	 * Data field name.
	 */
	protected String m_sName;

	/**
	 * Data field byte stream buffer.
	 */
	protected byte[] m_yBuffer;


	/**
	 * Creates a new instance of DbfField.
	 */
	protected DbfField()
	{
	}


	/**
	 * Creates a new instance of DbfField with field name and data length
	 * defined.
	 *
	 * @param yChars array of ASCII byte characters
	 * @param nDataLength length of byte character buffer
	 */
	protected DbfField(byte[] yChars, int nDataLength)
	{
		// remove extra whitespace and interpret field name characters
		int nStartIndex = getStartIndex(yChars);
		int nLabelLength = getLength(yChars, nStartIndex);
		m_sName = new String(yChars, nStartIndex, nLabelLength).intern();

		// initialize the data buffer to read information from each field
		m_yBuffer = new byte[nDataLength];
	}


	/**
	 * Reads the field and determines the starting index and length of the data
	 * by stripping leading and trailing whitespace.
	 *
	 * @param oDataInputStream data file input stream
	 * @throws java.lang.Exception
	 */
	public void parseRecord(DataInputStream oDataInputStream) throws Exception
	{
		// read the field data and trim the whitespace
		oDataInputStream.readFully(m_yBuffer);
		m_nStartIndex = getStartIndex(m_yBuffer);
		m_nDataLength = getLength(m_yBuffer, m_nStartIndex);
	}


	/**
	 * Returns the array of ASCII byte characters contained in the field.
	 *
	 * @return array of ASCII byte characters
	 */
	public byte[] getBytes()
	{
		return m_yBuffer;
	}


	/**
	 * Abstract method to retrieve field data as an integer.
	 *
	 * @return integer value of field data
	 */
	public abstract int getInt();


	/**
	 * Abstract method to retrieve field data as a long integer.
	 *
	 * @return long integer value of field data
	 */
	public abstract long getLong();


	/**
	 * Abstract method to retrieve field data as a floating point number.
	 *
	 * @return floating point number value of field data
	 */
	public abstract float getFloat();


	/**
	 * Abstract method to retrieve field data as a double precision number.
	 *
	 * @return double precision number value of field data
	 */
	public abstract double getDouble();


	/**
	 * Determines if the field data was null
	 *
	 * @return true if null field data, false otherwise
	 */
	public boolean wasNull()
	{
		return (m_nDataLength == 0);
	}


	/**
	 * Determines if the parameter string is equal to the field name.
	 *
	 * @param sFieldName field name to compare
	 * @return 0 if field names are equal
	 */
	public int compareTo(String sFieldName)
	{
		return m_sName.compareTo(sFieldName);
	}


	/**
	 * Returns the location of the first ASCII character that is not null or
	 * whitespace
	 *
	 * @param yChars array of ASCII character bytes
	 * @return integer index of the first valid ASCII character
	 */
	public static int getStartIndex(byte[] yChars)
	{
		// search for the first occurrence of an ASCII code other than null or space
		int nIndex = 0;

		while (nIndex < yChars.length && (yChars[nIndex] == 0 || yChars[nIndex] == 32))
			++nIndex;

		return nIndex;
	}


	/**
	 * Returns the length of the ASCII character array without leading or
	 * trailing whitespace
	 *
	 * @param yChars array of ASCII character bytes
	 * @param nStartIndex index of the first valid ASCII character
	 * @return integer length of the valid ASCII character array
	 */
	public static int getLength(byte[] yChars, int nStartIndex)
	{
		// start from the last position and 
		// search for the first occurrence of an ASCII code other than null or space
		int nIndex = yChars.length;
		--nIndex;

		while (nIndex >= nStartIndex && (yChars[nIndex] == 0 || yChars[nIndex] == 32))
			--nIndex;

		return (++nIndex - nStartIndex);
	}
}
