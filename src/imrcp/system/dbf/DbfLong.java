package imrcp.system.dbf;

import java.io.DataInputStream;

/**
 * Holds data base file field natively as a long integer.
 *
 * @author bryan.krueger
 * @version 1.0
 */
public class DbfLong extends DbfField
{

	/**
	 * Long integer field value.
	 */
	private long m_lValue;


	/**
	 * Creates a new instance of DbfLong.
	 */
	private DbfLong()
	{
	}


	/**
	 * Creates a new instance of DbfLong with field name and data length
	 * defined.
	 *
	 * @param yChars array of ASCII byte characters
	 * @param nLength length of byte character buffer
	 */
	public DbfLong(byte[] yChars, int nLength)
	{
		super(yChars, nLength);
	}


	/**
	 * Reads the field and stores its contents as a long integer.
	 *
	 * @param oDataInputStream data file input stream
	 * @throws java.lang.Exception
	 */
	@Override
	public void parseRecord(DataInputStream oDataInputStream) throws Exception
	{
		super.parseRecord(oDataInputStream);
		if (m_nDataLength > 0)
			m_lValue = Long.parseLong(new String(m_yBuffer, m_nStartIndex, m_nDataLength));
		else
			m_lValue = Long.MIN_VALUE;
	}


	/**
	 * Returns field data as an integer.
	 *
	 * @return integer value of field data
	 */
	public int getInt()
	{
		return ((int)m_lValue);
	}


	/**
	 * Returns field data as an long integer.
	 *
	 * @return long integer value of field data
	 */
	public long getLong()
	{
		return m_lValue;
	}


	/**
	 * Returns field data as a floating point number.
	 *
	 * @return floating point number value of field data
	 */
	public float getFloat()
	{
		return m_lValue;
	}


	/**
	 * Returns field data as a double precision number.
	 *
	 * @return double precision number value of field data
	 */
	public double getDouble()
	{
		return m_lValue;
	}


	/**
	 * Returns field data as a string.
	 *
	 * @return string value of field data
	 */
	@Override
	public String toString()
	{
		return Long.toString(m_lValue);
	}
}
