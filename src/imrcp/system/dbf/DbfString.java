package imrcp.system.dbf;

import java.io.DataInputStream;

/**
 * Holds data base file field natively as a string.
 *
 * @author bryan.krueger
 * @version 1.0
 */
public class DbfString extends DbfField
{

	/**
	 * String field value.
	 */
	private String m_sValue;


	/**
	 * Creates a new instance of DbfString.
	 */
	private DbfString()
	{
	}


	/**
	 * Creates a new instance of DbfString with field name and data length
	 * defined.
	 *
	 * @param yChars array of ASCII byte characters
	 * @param nLength length of byte character buffer
	 */
	public DbfString(byte[] yChars, int nLength)
	{
		super(yChars, nLength);
	}


	/**
	 * Reads the field and stores its contents as a string with leading and
	 * trailing whitespace removed.
	 *
	 * @param oDataInputStream data file input stream
	 * @throws java.lang.Exception
	 */
	@Override
	public void parseRecord(DataInputStream oDataInputStream) throws Exception
	{
		super.parseRecord(oDataInputStream);
		m_sValue = new String(m_yBuffer, m_nStartIndex, m_nDataLength).intern();
	}


	/**
	 * Returns field data as an integer.
	 *
	 * @return integer value of field data
	 */
	public int getInt()
	{
		return Integer.parseInt(m_sValue);
	}


	/**
	 * Returns field data as an long integer.
	 *
	 * @return long integer value of field data
	 */
	public long getLong()
	{
		return Long.parseLong(m_sValue);
	}


	/**
	 * Returns field data as a floating point number.
	 *
	 * @return floating point number value of field data
	 */
	public float getFloat()
	{
		return Float.parseFloat(m_sValue);
	}


	/**
	 * Returns field data as a double precision number.
	 *
	 * @return double precision number value of field data
	 */
	public double getDouble()
	{
		return Double.parseDouble(m_sValue);
	}


	/**
	 * Returns field data as a string.
	 *
	 * @return string value of field data
	 */
	@Override
	public String toString()
	{
		return m_sValue;
	}
}
