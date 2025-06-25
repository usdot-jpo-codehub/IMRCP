package imrcp.system.dbf;

import imrcp.system.Utility;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;

/**
 * Holds database file (DBF) records in a SQL type result set.
 *
 * @author bryan.krueger
 * @version 1.0
 */
public class DbfResultSet implements ResultSet
{

	/**
	 * Count of available rows (records)
	 */
	private int m_nRecordCount;

	/**
	 * Current row (record) index
	 */
	private int m_nRecordIndex;

	/**
	 * Input stream from .dbf file
	 */
	private DataInputStream m_oDataInputStream;

	/**
	 * Current data field
	 */
	private DbfField m_oCurrentField;

	/**
	 * Array of data fields used to hold current row (record) data
	 */
	private DbfField[] m_oFields;


	/**
	 * Creates a new instance of DbfResultSet. The DbfResultSet wraps an ESRI
	 * DBF file with a JDBC ResultSet interface.
	 */
	private DbfResultSet()
	{
	}


	/**
	 *
	 * @param oInputStream
	 * @throws Exception
	 */
	public DbfResultSet(InputStream oInputStream) throws Exception
	{
		m_oDataInputStream = new DataInputStream(oInputStream);

		// read the header information to get the total number of records and the length of column metadata
		m_oDataInputStream.skip(4);
		m_nRecordCount = Utility.swap(m_oDataInputStream.readInt());

		// the header size includes the header portion plus the field metadata
		int nFieldCount = (Utility.swap(m_oDataInputStream.readShort()) - 1) / DbfField.METADATA_LENGTH;
		m_oFields = new DbfField[--nFieldCount];

		// skip the remainder of the header data
		m_oDataInputStream.skip(22);

		// create the field reading objects
		constructFields(m_oDataInputStream);

		// skip the field metadata terminator and be ready to read records
		m_oDataInputStream.skip(1);
	}


	public DbfResultSet(DataInputStream oIn) throws Exception
	{
		
		// read the header information to get the total number of records and the length of column metadata
		oIn.skip(4);
		m_nRecordCount = Utility.swap(oIn.readInt());

		// the header size includes the header portion plus the field metadata
		int nFieldCount = (Utility.swap(oIn.readShort()) - 1) / DbfField.METADATA_LENGTH;
		m_oFields = new DbfField[--nFieldCount];

		// skip the remainder of the header data
		oIn.skip(22);

		// create the field reading objects
		constructFields(oIn);

		// skip the field metadata terminator and be ready to read records
		oIn.skip(1);
		
		m_oDataInputStream = oIn;
	}
	/**
	 * Creates a new instance of DbfResultsSet from the specified file.
	 *
	 * @param sFilename absolute path file name of .dbf file to process
	 * @throws java.lang.Exception
	 */
	public DbfResultSet(String sFilename) throws Exception
	{
		this(new DataInputStream(new BufferedInputStream(new FileInputStream(sFilename))));
	}

	
	public String[] getColumnNames()
	{
		String[] sRet = new String[m_oFields.length];
		for (int nIndex = 0; nIndex < sRet.length; nIndex++)
			sRet[nIndex] = m_oFields[nIndex].m_sName;
			
		return sRet;
	}
	
	
	/**
	 * Virtual constuctor method to create the appropriate field data objects.
	 *
	 * @param oDataInputStream database file stream
	 * @throws java.lang.Exception
	 */
	private void constructFields(DataInputStream oDataInputStream) throws Exception
	{
		char cFieldType = ' ';
		int nDataLength = 0;
		int nDecimalPlaces = 0;

		// each field has a name containing a maximum of 11 characters
		byte[] yChars = new byte[11];

		// parse the column definitions into column objects that can interpret each row
		for (int nFieldIndex = 0; nFieldIndex < m_oFields.length; nFieldIndex++)
		{
			oDataInputStream.read(yChars);

			// read the field type character
			cFieldType = (char)Utility.unsignByte(oDataInputStream.readByte());
			// skip the data address as it is not used by this application
			oDataInputStream.skip(4);

			// convert the unsigned byte for field data length to a signed integer
			nDataLength = Utility.unsignByte(oDataInputStream.readByte());
			// the decimal count is needed to determine what type of number is represented
			nDecimalPlaces = Utility.unsignByte(oDataInputStream.readByte());

			switch (cFieldType)
			{
				case 'C':
				{
					nDataLength += nDecimalPlaces * 256; // extended character field
					m_oFields[nFieldIndex] = new DbfString(yChars, nDataLength);
					break;
				}

				case 'N':
				{
					if (nDecimalPlaces == 0)
						m_oFields[nFieldIndex] = new DbfLong(yChars, nDataLength);
					else
						m_oFields[nFieldIndex] = new DbfDouble(yChars, nDataLength);

					break;
				}

				case 'D':
				case 'F':
				{
					m_oFields[nFieldIndex] = new DbfDouble(yChars, nDataLength);
					break;
				}
			}

			// skip the remainder of the field metadata
			oDataInputStream.skip(14);
		}
	}


	/**
	 * Move to the next row in the results set.
	 *
	 * @return true if there are more records remaining
	 * @throws java.sql.SQLException
	 */
	public boolean next() throws SQLException
	{
		int nFieldIndex = 0;
		if (m_nRecordCount > 0)
		{
			try
			{
				// skip the deleted flag
				m_oDataInputStream.skip(1);

				// read data for each field
//				int nFieldIndex = 0;
				while (nFieldIndex < m_oFields.length)
					m_oFields[nFieldIndex++].parseRecord(m_oDataInputStream);

				++m_nRecordIndex; // set record index
			}
			catch (Exception oException)
			{
				oException.printStackTrace();
			}
		}

		return (m_nRecordCount-- > 0);
	}


	/**
	 * Move to a specific data field.
	 *
	 * @param nFieldIndex data field index (1's based)
	 */
	private void setField(int nFieldIndex)
	{
		m_oCurrentField = m_oFields[--nFieldIndex];
	}


	/**
	 * Close the database file stream.
	 *
	 * @throws java.sql.SQLException
	 */
	public void close() throws SQLException
	{
		try
		{
			m_oDataInputStream.close();
		}
		catch (Exception oException)
		{
		}
	}


	/**
	 * Determines if the current field was null.
	 *
	 * @return true if current field was null, false otherwise
	 * @throws java.sql.SQLException
	 */
	public boolean wasNull() throws SQLException
	{
		if (m_oCurrentField != null)
			return m_oCurrentField.wasNull();

		return false;
	}


	/**
	 * Returns the specified data field as a String.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return String representation of field data
	 * @throws java.sql.SQLException
	 */
	public String getString(int columnIndex) throws SQLException
	{
		setField(columnIndex);
		return m_oCurrentField.toString();
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a boolean.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return boolean representation of field data
	 * @throws java.sql.SQLException
	 */
	public boolean getBoolean(int columnIndex) throws SQLException
	{
		return false;
	}


	/**
	 * Returns the specified data field as a byte integer.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return byte integer representation of field data
	 * @throws java.sql.SQLException
	 */
	public byte getByte(int columnIndex) throws SQLException
	{
		setField(columnIndex);
		return ((byte) m_oCurrentField.getLong());
	}


	/**
	 * Returns the specified data field as a short integer.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return short integer representation of field data
	 * @throws java.sql.SQLException
	 */
	public short getShort(int columnIndex) throws SQLException
	{
		setField(columnIndex);
		return ((short)m_oCurrentField.getLong());
	}


	/**
	 * Returns the specified data field as an integer.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return integer representation of field data
	 * @throws java.sql.SQLException
	 */
	public int getInt(int columnIndex) throws SQLException
	{
		setField(columnIndex);
		return m_oCurrentField.getInt();
	}


	/**
	 * Returns the specified data field as a long integer.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return long integer representation of field data
	 * @throws java.sql.SQLException
	 */
	public long getLong(int columnIndex) throws SQLException
	{
		setField(columnIndex);
		return m_oCurrentField.getLong();
	}


	/**
	 * Returns the specified data field as a floating point number.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return floating point number representation of field data
	 * @throws java.sql.SQLException
	 */
	public float getFloat(int columnIndex) throws SQLException
	{
		setField(columnIndex);
		return m_oCurrentField.getFloat();
	}


	/**
	 * Returns the specified data field as a double precision number.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return double precision number representation of field data
	 * @throws java.sql.SQLException
	 */
	public double getDouble(int columnIndex) throws SQLException
	{
		setField(columnIndex);
		return m_oCurrentField.getDouble();
	}


	/**
	 * Returns the specified data field as a big decimal number.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param scale scale of the big decimal value to be returned
	 * @return big decimal number representation of field data
	 * @throws java.sql.SQLException
	 */
	public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException
	{
		setField(columnIndex);
		BigDecimal oBigDecimal = new BigDecimal(m_oCurrentField.getDouble());

		return oBigDecimal.setScale(scale);
	}


	/**
	 * Returns the specified data field as a byte array.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return byte array representation of the field data
	 * @throws java.sql.SQLException
	 */
	public byte[] getBytes(int columnIndex) throws SQLException
	{
		setField(columnIndex);
		return m_oCurrentField.getBytes();
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a Date object.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return Date representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Date getDate(int columnIndex) throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a Time object.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return Time representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Time getTime(int columnIndex) throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a Timestamp object.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return Timestamp representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Timestamp getTimestamp(int columnIndex) throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as an ASCII
	 * InputStream object.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return ASCII InputStream representation of the field data
	 * @throws java.sql.SQLException
	 */
	public InputStream getAsciiStream(int columnIndex) throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as an Unicode
	 * InputStream object.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return Unicode InputStream representation of the field data
	 * @throws java.sql.SQLException
	 */
	public InputStream getUnicodeStream(int columnIndex) throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a binary
	 * InputStream object.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return binary InputStream representation of the field data
	 * @throws java.sql.SQLException
	 */
	public InputStream getBinaryStream(int columnIndex) throws SQLException
	{
		return null;
	}


	/**
	 * Returns the specified data field as a String.
	 *
	 * @param columnLabel data field label
	 * @return String representation of the field data
	 * @throws java.sql.SQLException
	 */
	public String getString(String columnLabel) throws SQLException
	{
		return getString(findColumn(columnLabel));
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a boolean.
	 *
	 * @param columnLabel data field label
	 * @return boolean representation of the field data
	 * @throws java.sql.SQLException
	 */
	public boolean getBoolean(String columnLabel) throws SQLException
	{
		return false;
	}


	/**
	 * Returns the specified data field as a byte.
	 *
	 * @param columnLabel data field label
	 * @return byte representation of the field data
	 * @throws java.sql.SQLException
	 */
	public byte getByte(String columnLabel) throws SQLException
	{
		return getByte(findColumn(columnLabel));
	}


	/**
	 * Returns the specified data field as a short integer.
	 *
	 * @param columnLabel data field label
	 * @return short integer representation of the field data
	 * @throws java.sql.SQLException
	 */
	public short getShort(String columnLabel) throws SQLException
	{
		return getShort(findColumn(columnLabel));
	}


	/**
	 * Returns the specified data field as an integer.
	 *
	 * @param columnLabel data field label
	 * @return integer representation of the field data
	 * @throws java.sql.SQLException
	 */
	public int getInt(String columnLabel) throws SQLException
	{
		return getInt(findColumn(columnLabel));
	}


	/**
	 * Returns the specified data field as a long integer.
	 *
	 * @param columnLabel data field label
	 * @return long integer representation of the field data
	 * @throws java.sql.SQLException
	 */
	public long getLong(String columnLabel) throws SQLException
	{
		return getLong(findColumn(columnLabel));
	}


	/**
	 * Returns the specified data field as a floating point number.
	 *
	 * @param columnLabel data field label
	 * @return floating point number representation of the field data
	 * @throws java.sql.SQLException
	 */
	public float getFloat(String columnLabel) throws SQLException
	{
		return getFloat(findColumn(columnLabel));
	}


	/**
	 * Returns the specified data field as a double precision number.
	 *
	 * @param columnLabel data field label
	 * @return double precision number representation of the field data
	 * @throws java.sql.SQLException
	 */
	public double getDouble(String columnLabel) throws SQLException
	{
		return getDouble(findColumn(columnLabel));
	}


	/**
	 * Returns the specified data field as a big decimal number.
	 *
	 * @param columnLabel data field label
	 * @return big decimal number representation of the field data
	 * @throws java.sql.SQLException
	 */
	public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException
	{
		return getBigDecimal(findColumn(columnLabel));
	}


	/**
	 * Returns the specified data field as a byte array.
	 *
	 * @param columnLabel data field label
	 * @return byte array representation of the field data
	 * @throws java.sql.SQLException
	 */
	public byte[] getBytes(String columnLabel) throws SQLException
	{
		return getBytes(findColumn(columnLabel));
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a Date object.
	 *
	 * @param columnLabel data field label
	 * @return Date representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Date getDate(String columnLabel) throws SQLException
	{
		return getDate(findColumn(columnLabel));
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a Time object.
	 *
	 * @param columnLabel data field label
	 * @return Time representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Time getTime(String columnLabel) throws SQLException
	{
		return getTime(findColumn(columnLabel));
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a Timestamp object.
	 *
	 * @param columnLabel data field label
	 * @return Timestamp representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Timestamp getTimestamp(String columnLabel) throws SQLException
	{
		return getTimestamp(findColumn(columnLabel));
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as an ASCII
	 * InputStream object.
	 *
	 * @param columnLabel data field label
	 * @return ASCII InputStream representation of the field data
	 * @throws java.sql.SQLException
	 */
	public InputStream getAsciiStream(String columnLabel) throws SQLException
	{
		return getAsciiStream(findColumn(columnLabel));
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as an Unicode
	 * InputStream object.
	 *
	 * @param columnLabel data field label
	 * @return Unicode InputStream representation of the field data
	 * @throws java.sql.SQLException
	 */
	public InputStream getUnicodeStream(String columnLabel) throws SQLException
	{
		return getUnicodeStream(findColumn(columnLabel));
	}


	/**
	 * Returns the specified data field as a binary InputStream object.
	 *
	 * @param columnLabel data field label
	 * @return binary InputStream representation of the field data
	 * @throws java.sql.SQLException
	 */
	public InputStream getBinaryStream(String columnLabel) throws SQLException
	{
		return getBinaryStream(findColumn(columnLabel));
	}


	/**
	 * (NOT IMPLEMENTED) Returns the first warning reported by calls on this
	 * DbfResultSet object. Subsequent warnings on this ResultSet object will be
	 * chained to the SQLWarning object that this method returns.
	 *
	 * @return the first SQLWarning object reported or null if there are none
	 * @throws java.sql.SQLException
	 */
	public SQLWarning getWarnings() throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED) Clears all warnings reported on this DdbfResultSet
	 * object. After this method is called, the method getWarnings returns null
	 * until a new warning is reported for this DbfResultSet object.
	 *
	 * @throws java.sql.SQLException
	 */
	public void clearWarnings() throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Returns the name of the cursor used by this
	 * DbfResultSet object.
	 *
	 * @return the name for this DbfResultSet object's cursor
	 * @throws java.sql.SQLException
	 */
	public String getCursorName() throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED) Returns the number, types and properties of this
	 * DbfResultSet object's columns.
	 *
	 * @return the description of this DbfResultSet object's columns
	 * @throws java.sql.SQLException
	 */
	public ResultSetMetaData getMetaData() throws SQLException
	{
		return null;
	}


	/**
	 * Returns the specified data field as an object.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return object DbfField object
	 * @throws java.sql.SQLException
	 */
	public Object getObject(int columnIndex) throws SQLException
	{
		setField(columnIndex);
		return m_oCurrentField;
	}


	/**
	 * Returns the specified data field as an object.
	 *
	 * @param columnLabel data field label
	 * @return object DbfField object
	 * @throws java.sql.SQLException
	 */
	public Object getObject(String columnLabel) throws SQLException
	{
		return getObject(findColumn(columnLabel));
	}


	/**
	 * Returns the data field index associated with the specified label.
	 *
	 * @param columnLabel data field label
	 * @return integer index of the data filed (1's based)
	 * @throws java.sql.SQLException
	 */
	public int findColumn(String columnLabel) throws SQLException
	{
		// find the field object by name
		int nFieldIndex = m_oFields.length;
		while (nFieldIndex-- > 0 && m_oFields[nFieldIndex].compareTo(columnLabel) != 0);

		// column indices are 1 based
		return ++nFieldIndex;
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a character stream
	 * Reader.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return character stream Reader representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Reader getCharacterStream(int columnIndex) throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a character stream
	 * Reader.
	 *
	 * @param columnLabel data field label
	 * @return character stream Reader representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Reader getCharacterStream(String columnLabel) throws SQLException
	{
		return null;
	}


	/**
	 * Returns the specified data field as a big decimal.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return big decimal representation of the field data
	 * @throws java.sql.SQLException
	 */
	public BigDecimal getBigDecimal(int columnIndex) throws SQLException
	{
		return getBigDecimal(columnIndex, 0);
	}


	/**
	 * Returns the specified data field as a big decimal.
	 *
	 * @param columnLabel data field label
	 * @return big decimal representation of the field data
	 * @throws java.sql.SQLException
	 */
	public BigDecimal getBigDecimal(String columnLabel) throws SQLException
	{
		return getBigDecimal(columnLabel, 0);
	}


	/**
	 * Determines if before the first row in this DbfResultSet object.
	 *
	 * @return true if before the first row; false if at any other position or
	 * the result set contains no rows
	 * @throws java.sql.SQLException
	 */
	public boolean isBeforeFirst() throws SQLException
	{
		return (m_nRecordIndex == 0);
	}


	/**
	 * Determines if after the last row in this DbfResultSet object.
	 *
	 * @return true if after the last row; false if at any other position or the
	 * result set contains no rows
	 * @throws java.sql.SQLException
	 */
	public boolean isAfterLast() throws SQLException
	{
		return (m_nRecordCount == 0);
	}


	/**
	 * Determines if on the first row of this DbfResultSet object.
	 *
	 * @return true if on the first row; false otherwise
	 * @throws java.sql.SQLException
	 */
	public boolean isFirst() throws SQLException
	{
		return (m_nRecordIndex == 1);
	}


	/**
	 * Determines if on the last row of this DbfResultSet object.
	 *
	 * @return true if on the last row; false otherwise
	 * @throws java.sql.SQLException
	 */
	public boolean isLast() throws SQLException
	{
		return (m_nRecordCount == 1);
	}


	/**
	 * (NOT IMPLEMENTED) Moves to the front of this DbfResultSet object, just
	 * before the first row.
	 *
	 * @throws java.sql.SQLException
	 */
	public void beforeFirst() throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Moves to the end of this DbfResultSet object, just
	 * after the last row.
	 *
	 * @throws java.sql.SQLException
	 */
	public void afterLast() throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Moves to the first row in this DbfResultSet object.
	 *
	 * @return true if on a valid row; false if there are no rows in the result
	 * set
	 * @throws java.sql.SQLException
	 */
	public boolean first() throws SQLException
	{
		return false;
	}


	/**
	 * (NOT IMPLEMENTED) Moves to the last row in this DbfResultSet object.
	 *
	 * @return true if on a valid row; false if there are no rows in the result
	 * set
	 * @throws java.sql.SQLException
	 */
	public boolean last() throws SQLException
	{
		return false;
	}


	/**
	 * Returns the current row number.
	 *
	 * @return integer row number (1's based)
	 * @throws java.sql.SQLException
	 */
	public int getRow() throws SQLException
	{
		return m_nRecordIndex;
	}


	/**
	 * (NOT IMPLEMENTED) Moves to the given row number in this DbfResultSet
	 * object.
	 * <br><br>
	 * If the row number is positive, the cursor moves to the given row number
	 * with respect to the beginning of the result set.
	 * <br><br>
	 * If the given row number is negative, the cursor moves to an absolute row
	 * position with respect to the end of the result set.
	 * <br><br>
	 * Note: Calling absolute(1) is the same as calling first(). Calling
	 * absolute(-1) is the same as calling last().
	 *
	 * @param row the row number which to move (1's based)
	 * @return true if moved to a position in this DbfResultSet object; false if
	 * before the first row or after the last row
	 * @throws java.sql.SQLException
	 */
	public boolean absolute(int row) throws SQLException
	{
		return false;
	}


	/**
	 * (NOT IMPLEMENTED) Moves a relative number of rows, either positive or
	 * negative.
	 * <br></br>
	 * Note: Calling the method relative(1) is identical to calling the method
	 * next() and calling the method relative(-1) is identical to calling the
	 * method previous().
	 *
	 * @param rows number of rows to move
	 * @return true if on a row; false otherwise
	 * @throws java.sql.SQLException
	 */
	public boolean relative(int rows) throws SQLException
	{
		return false;
	}


	/**
	 * (NOT IMPLEMENTED) Move to the previous rown in this DbfResultsSet object.
	 *
	 * @return true if positioned on a valid row; false if positioned before the
	 * first row
	 * @throws java.sql.SQLException
	 */
	public boolean previous() throws SQLException
	{
		return false;
	}


	/**
	 * (NOT IMPLEMENTED) Gives a hint as to the direction in which the rows in
	 * this DbfResultSet object will be processed. The fetch direction may be
	 * changed at any time.
	 *
	 * @param direction integer specifying the suggested fetch direction; one of
	 * ResultSet.FETCH_FORWARD, ResultSet.FETCH_REVERSE, or
	 * ResultSet.FETCH_UNKNOWN
	 * @throws java.sql.SQLException
	 */
	public void setFetchDirection(int direction) throws SQLException
	{
	}


	/**
	 * Retrieves the fetch direction for this ResultSet object.
	 *
	 * @return the current fetch direction for this DbfResultSet object; one of
	 * ResultSet.FETCH_FORWARD, ResultSet.FETCH_REVERSE, or
	 * ResultSet.FETCH_UNKNOWN
	 * @throws java.sql.SQLException
	 */
	public int getFetchDirection() throws SQLException
	{
		return FETCH_FORWARD;
	}


	/**
	 * (NOT IMPLEMENTED) Defines the number of rows that should be fetched when
	 * more rows are needed for the DbfResutlsSet object.
	 *
	 * @param rows the number of rows to fetch
	 * @throws java.sql.SQLException
	 */
	public void setFetchSize(int rows) throws SQLException
	{
	}


	/**
	 * Retrieves the fetch size for this ResultSet object.
	 *
	 * @return integer fetch size for this ResultSet object
	 * @throws java.sql.SQLException
	 */
	public int getFetchSize() throws SQLException
	{
		return 1;
	}


	/**
	 * Returns the type of this DbfResultSet object.
	 *
	 * @return integer specifing the DbfResultSet type; one of
	 * ResultSet.TYPE_FORWARD_ONLY, ResultSet.TYPE_SCROLL_INSENSITIVE, or
	 * ResultSet.TYPE_SCROLL_SENSITIVE
	 * @throws java.sql.SQLException
	 */
	public int getType() throws SQLException
	{
		return ResultSet.TYPE_FORWARD_ONLY;
	}


	/**
	 * Retrieves the concurrency mode of this DbfResultSet object.
	 *
	 * @return integer specifying the concurrency type, either
	 * ResultSet.CONCUR_READ_ONLY or ResultSet.CONCUR_UPDATABLE
	 * @throws java.sql.SQLException
	 */
	public int getConcurrency() throws SQLException
	{
		return ResultSet.CONCUR_READ_ONLY;
	}


	/**
	 * (NOT IMPLEMENTED) Determines if the current row has been updated.
	 *
	 * @return true if the current row has been updated; false otherwise
	 * @throws java.sql.SQLException
	 */
	public boolean rowUpdated() throws SQLException
	{
		return false;
	}


	/**
	 * (NOT IMPLEMENTED) Determines if the current row has been inserted.
	 *
	 * @return true if the current row has been inserted; false otherwise
	 * @throws java.sql.SQLException
	 */
	public boolean rowInserted() throws SQLException
	{
		return false;
	}


	/**
	 * (NOT IMPLEMENTED) Determines if the current row has been deleted.
	 *
	 * @return true if the current row has been deleted; false otherwise
	 * @throws java.sql.SQLException
	 */
	public boolean rowDeleted() throws SQLException
	{
		return false;
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a null value. The
	 * updater methods are used to update column values in the current row or
	 * the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnIndex data field index (1's based)
	 * @throws java.sql.SQLException
	 */
	public void updateNull(int columnIndex) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a boolean value. The
	 * updater methods are used to update column values in the current row or
	 * the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param x boolean field value
	 * @throws java.sql.SQLException
	 */
	public void updateBoolean(int columnIndex, boolean x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a bbye value. The
	 * updater methods are used to update column values in the current row or
	 * the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param x byte field value
	 * @throws java.sql.SQLException
	 */
	public void updateByte(int columnIndex, byte x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a short integer
	 * value. The updater methods are used to update column values in the
	 * current row or the insert row. The updater methods do not update the
	 * underlying database; instead the updateRow or insertRow methods are
	 * called to update the database.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param x short integer field value
	 * @throws java.sql.SQLException
	 */
	public void updateShort(int columnIndex, short x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with an integer value. The
	 * updater methods are used to update column values in the current row or
	 * the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param x integer field value
	 * @throws java.sql.SQLException
	 */
	public void updateInt(int columnIndex, int x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a long integer value.
	 * The updater methods are used to update column values in the current row
	 * or the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param x long integer field value
	 * @throws java.sql.SQLException
	 */
	public void updateLong(int columnIndex, long x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a floating point
	 * number value. The updater methods are used to update column values in the
	 * current row or the insert row. The updater methods do not update the
	 * underlying database; instead the updateRow or insertRow methods are
	 * called to update the database.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param x floating point number field value
	 * @throws java.sql.SQLException
	 */
	public void updateFloat(int columnIndex, float x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a double precision
	 * number value. The updater methods are used to update column values in the
	 * current row or the insert row. The updater methods do not update the
	 * underlying database; instead the updateRow or insertRow methods are
	 * called to update the database.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param x double precision number field value
	 * @throws java.sql.SQLException
	 */
	public void updateDouble(int columnIndex, double x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a BigDecimal value.
	 * The updater methods are used to update column values in the current row
	 * or the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param x BigDecimal field value
	 * @throws java.sql.SQLException
	 */
	public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a String value. The
	 * updater methods are used to update column values in the current row or
	 * the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param x String field value
	 * @throws java.sql.SQLException
	 */
	public void updateString(int columnIndex, String x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a byte array value.
	 * The updater methods are used to update column values in the current row
	 * or the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param x byte array field value
	 * @throws java.sql.SQLException
	 */
	public void updateBytes(int columnIndex, byte[] x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a Date value. The
	 * updater methods are used to update column values in the current row or
	 * the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param x Date field value
	 * @throws java.sql.SQLException
	 */
	public void updateDate(int columnIndex, Date x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a Time value. The
	 * updater methods are used to update column values in the current row or
	 * the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param x Time field value
	 * @throws java.sql.SQLException
	 */
	public void updateTime(int columnIndex, Time x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a TimeStamp value.
	 * The updater methods are used to update column values in the current row
	 * or the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param x TimeStamp field value
	 * @throws java.sql.SQLException
	 */
	public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with an ASCII InputStream
	 * value. The updater methods are used to update column values in the
	 * current row or the insert row. The updater methods do not update the
	 * underlying database; instead the updateRow or insertRow methods are
	 * called to update the database.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param x ASCII InputStream field value
	 * @param length integer length of the data in the stream
	 * @throws java.sql.SQLException
	 */
	public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a binary InputStream
	 * value. The updater methods are used to update column values in the
	 * current row or the insert row. The updater methods do not update the
	 * underlying database; instead the updateRow or insertRow methods are
	 * called to update the database.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param x binary InputStream field value
	 * @param length integer length of the data in the stream
	 * @throws java.sql.SQLException
	 */
	public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a character stream
	 * Reader value. The updater methods are used to update column values in the
	 * current row or the insert row. The updater methods do not update the
	 * underlying database; instead the updateRow or insertRow methods are
	 * called to update the database.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param x character stream Reader field value
	 * @param length integer length of the data in the reader
	 * @throws java.sql.SQLException
	 */
	public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with an Object value. The
	 * updater methods are used to update column values in the current row or
	 * the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param x Object field value
	 * @param scaleOrLength integer for an object of java.math.BigDecimal , this
	 * is the number of digits after the decimal point. For Java Object types
	 * InputStream and Reader, this is the length of the data in the stream or
	 * reader. For all other types, this value will be ignored.
	 * @throws java.sql.SQLException
	 */
	public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with an Object value. The
	 * updater methods are used to update column values in the current row or
	 * the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param x Object field value
	 * @throws java.sql.SQLException
	 */
	public void updateObject(int columnIndex, Object x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a null value. The
	 * updater methods are used to update column values in the current row or
	 * the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnLabel data field label
	 * @throws java.sql.SQLException
	 */
	public void updateNull(String columnLabel) throws SQLException
	{
		updateNull(findColumn(columnLabel));
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a boolean value. The
	 * updater methods are used to update column values in the current row or
	 * the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnLabel data field label
	 * @param x boolean field value
	 * @throws java.sql.SQLException
	 */
	public void updateBoolean(String columnLabel, boolean x) throws SQLException
	{
		updateBoolean(findColumn(columnLabel), x);
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a byte value. The
	 * updater methods are used to update column values in the current row or
	 * the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnLabel data field label
	 * @param x byte field value
	 * @throws java.sql.SQLException
	 */
	public void updateByte(String columnLabel, byte x) throws SQLException
	{
		updateByte(findColumn(columnLabel), x);
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a short integer
	 * value. The updater methods are used to update column values in the
	 * current row or the insert row. The updater methods do not update the
	 * underlying database; instead the updateRow or insertRow methods are
	 * called to update the database.
	 *
	 * @param columnLabel data field label
	 * @param x short integer field value
	 * @throws java.sql.SQLException
	 */
	public void updateShort(String columnLabel, short x) throws SQLException
	{
		updateShort(findColumn(columnLabel), x);
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with an integer value. The
	 * updater methods are used to update column values in the current row or
	 * the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnLabel data field label
	 * @param x integer field value
	 * @throws java.sql.SQLException
	 */
	public void updateInt(String columnLabel, int x) throws SQLException
	{
		updateInt(findColumn(columnLabel), x);
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a long integer value.
	 * The updater methods are used to update column values in the current row
	 * or the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnLabel data field label
	 * @param x long integer field value
	 * @throws java.sql.SQLException
	 */
	public void updateLong(String columnLabel, long x) throws SQLException
	{
		updateLong(findColumn(columnLabel), x);
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a floating point
	 * number value. The updater methods are used to update column values in the
	 * current row or the insert row. The updater methods do not update the
	 * underlying database; instead the updateRow or insertRow methods are
	 * called to update the database.
	 *
	 * @param columnLabel data field label
	 * @param x floating point number field value
	 * @throws java.sql.SQLException
	 */
	public void updateFloat(String columnLabel, float x) throws SQLException
	{
		updateFloat(findColumn(columnLabel), x);
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a double precision
	 * number value. The updater methods are used to update column values in the
	 * current row or the insert row. The updater methods do not update the
	 * underlying database; instead the updateRow or insertRow methods are
	 * called to update the database.
	 *
	 * @param columnLabel data field label
	 * @param x double precision number field value
	 * @throws java.sql.SQLException
	 */
	public void updateDouble(String columnLabel, double x) throws SQLException
	{
		updateDouble(findColumn(columnLabel), x);
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a BigDecimal value.
	 * The updater methods are used to update column values in the current row
	 * or the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnLabel data field label
	 * @param x BigDecimal field value
	 * @throws java.sql.SQLException
	 */
	public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException
	{
		updateBigDecimal(findColumn(columnLabel), x);
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a String value. The
	 * updater methods are used to update column values in the current row or
	 * the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnLabel data field label
	 * @param x String field value
	 * @throws java.sql.SQLException
	 */
	public void updateString(String columnLabel, String x) throws SQLException
	{
		updateString(findColumn(columnLabel), x);
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a byte array value.
	 * The updater methods are used to update column values in the current row
	 * or the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnLabel data field label
	 * @param x byte array field value
	 * @throws java.sql.SQLException
	 */
	public void updateBytes(String columnLabel, byte[] x) throws SQLException
	{
		updateBytes(findColumn(columnLabel), x);
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a Date value. The
	 * updater methods are used to update column values in the current row or
	 * the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnLabel data field label
	 * @param x Date field value
	 * @throws java.sql.SQLException
	 */
	public void updateDate(String columnLabel, Date x) throws SQLException
	{
		updateDate(findColumn(columnLabel), x);
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a Time value. The
	 * updater methods are used to update column values in the current row or
	 * the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnLabel data field label
	 * @param x Time field value
	 * @throws java.sql.SQLException
	 */
	public void updateTime(String columnLabel, Time x) throws SQLException
	{
		updateTime(findColumn(columnLabel), x);
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a Timestamp value.
	 * The updater methods are used to update column values in the current row
	 * or the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnLabel data field label
	 * @param x Timestamp field value
	 * @throws java.sql.SQLException
	 */
	public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException
	{
		updateTimestamp(findColumn(columnLabel), x);
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with an ASCII InputStream
	 * value. The updater methods are used to update column values in the
	 * current row or the insert row. The updater methods do not update the
	 * underlying database; instead the updateRow or insertRow methods are
	 * called to update the database.
	 *
	 * @param columnLabel data field label
	 * @param x ASCII InputStream field value
	 * @param length integer length of the data in the stream
	 * @throws java.sql.SQLException
	 */
	public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException
	{
		updateAsciiStream(findColumn(columnLabel), x);
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a binary InputStream
	 * value. The updater methods are used to update column values in the
	 * current row or the insert row. The updater methods do not update the
	 * underlying database; instead the updateRow or insertRow methods are
	 * called to update the database.
	 *
	 * @param columnLabel data field label
	 * @param x binary InputStream field value
	 * @param length integer length of the data in the stream
	 * @throws java.sql.SQLException
	 */
	public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException
	{
		updateBinaryStream(findColumn(columnLabel), x, length);
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with a character stream
	 * Reader value. The updater methods are used to update column values in the
	 * current row or the insert row. The updater methods do not update the
	 * underlying database; instead the updateRow or insertRow methods are
	 * called to update the database.
	 *
	 * @param columnLabel data field label
	 * @param x character stream Reader field value
	 * @param length integer length of the data in the reader
	 * @throws java.sql.SQLException
	 */
	public void updateCharacterStream(String columnLabel, Reader x, int length) throws SQLException
	{
		updateCharacterStream(findColumn(columnLabel), x, length);
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with an Object value. The
	 * updater methods are used to update column values in the current row or
	 * the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnLabel data field label
	 * @param x Object field value
	 * @param scaleOrLength integer for an object of java.math.BigDecimal , this
	 * is the number of digits after the decimal point. For Java Object types
	 * InputStream and Reader, this is the length of the data in the stream or
	 * reader. For all other types, this value will be ignored.
	 * @throws java.sql.SQLException
	 */
	public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException
	{
		updateObject(findColumn(columnLabel), x, scaleOrLength);
	}


	/**
	 * (NOT IMPLEMENTED) Updates the specified column with an object value. The
	 * updater methods are used to update column values in the current row or
	 * the insert row. The updater methods do not update the underlying
	 * database; instead the updateRow or insertRow methods are called to update
	 * the database.
	 *
	 * @param columnLabel data field label
	 * @param x Object field value
	 * @throws java.sql.SQLException
	 */
	public void updateObject(String columnLabel, Object x) throws SQLException
	{
		updateObject(findColumn(columnLabel), x);
	}


	/**
	 * (NOT IMPLEMENTED) Inserts the contents of the insert row into this
	 * DbfResultSet object and into the database. Must be on the insert row when
	 * this method is called.
	 *
	 * @throws java.sql.SQLException
	 */
	public void insertRow() throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Updates the underlying database with the new contents
	 * of the current row of this DbfResultSet object. This method cannot be
	 * called when on the insert row.
	 *
	 * @throws java.sql.SQLException
	 */
	public void updateRow() throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Deletes the current row from this DbfResultSet object
	 * and from the underlying database. This method cannot be called when on
	 * the insert row.
	 *
	 * @throws java.sql.SQLException
	 */
	public void deleteRow() throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Refreshes the current row with its most recent value in
	 * the database. This method cannot be called when on the insert row.
	 *
	 * @throws java.sql.SQLException
	 */
	public void refreshRow() throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Cancels the updates made to the current row in this
	 * DbfResultSet object. This method may be called after calling an updater
	 * method(s) and before calling the method updateRow to roll back the
	 * updates made to a row. If no updates have been made or updateRow has
	 * already been called, this method has no effect.
	 *
	 * @throws java.sql.SQLException
	 */
	public void cancelRowUpdates() throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Moves to the insert row. The current position is
	 * remembered while positioned on the insert row.
	 *
	 * @throws java.sql.SQLException
	 */
	public void moveToInsertRow() throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Moves to the remembered position, usually the current
	 * row. This method has no effect if not on the insert row.
	 *
	 * @throws java.sql.SQLException
	 */
	public void moveToCurrentRow() throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED) Returns the Statement object that produced this
	 * ResultSet object. Since the result set is not produced from a SQL query,
	 * the statement is null.
	 *
	 * @return the Statment object that produced this DbfResultSet object or
	 * null if the result set was produced some other way
	 * @throws java.sql.SQLException
	 */
	public Statement getStatement() throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as an object. This
	 * method uses the given Map object for the custom mapping of the SQL
	 * structured or distinct type that is being retrieved.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param map a Map object that contains the mapping from SQL type names to
	 * classes
	 * @return object DbfField object
	 * @throws java.sql.SQLException
	 */
	public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as Ref object.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return reference object to SQL REF value
	 * @throws java.sql.SQLException
	 */
	public Ref getRef(int columnIndex) throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a Blob object.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return Blob representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Blob getBlob(int columnIndex) throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a Clob object.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return Clob representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Clob getClob(int columnIndex) throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as an Array object.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return Array representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Array getArray(int columnIndex) throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as an object. This
	 * method uses the given Map object for the custom mapping of the SQL
	 * structured or distinct type that is being retrieved.
	 *
	 * @param columnLabel data field label
	 * @param map a Map object that contains the mapping from SQL type names to
	 * classes
	 * @return object DbfField object
	 * @throws java.sql.SQLException
	 */
	public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException
	{
		return getObject(findColumn(columnLabel), map);
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as an Ref object.
	 *
	 * @param columnLabel data field label
	 * @return reference object to SQL REF value
	 * @throws java.sql.SQLException
	 */
	public Ref getRef(String columnLabel) throws SQLException
	{
		return getRef(findColumn(columnLabel));
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a Blob object.
	 *
	 * @param columnLabel data field label
	 * @return Blob representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Blob getBlob(String columnLabel) throws SQLException
	{
		return getBlob(findColumn(columnLabel));
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a Clob object.
	 *
	 * @param columnLabel data field label
	 * @return Clob representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Clob getClob(String columnLabel) throws SQLException
	{
		return getClob(findColumn(columnLabel));
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a Array object.
	 *
	 * @param columnLabel data field label
	 * @return Array representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Array getArray(String columnLabel) throws SQLException
	{
		return getArray(findColumn(columnLabel));
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a Date object. This
	 * method uses the given calendar to construct an appropriate millisecond
	 * value for the date if the underlying database does not store timezone
	 * information.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param cal the Calendar object to use in constructing the date
	 * @return Date representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Date getDate(int columnIndex, Calendar cal) throws SQLException
	{
		return getDate(columnIndex);
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a Date object. This
	 * method uses the given calendar to construct an appropriate millisecond
	 * value for the date if the underlying database does not store timezone
	 * information.
	 *
	 * @param columnLabel data field label
	 * @param cal the Calendar object to use in constructing the date
	 * @return Date representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Date getDate(String columnLabel, Calendar cal) throws SQLException
	{
		return getDate(findColumn(columnLabel), cal);
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a Time object. This
	 * method uses the given calendar to construct an appropriate millisecond
	 * value for the time if the underlying database does not store timezone
	 * information.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param cal the Calendar object to use in constructing the time
	 * @return Time representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Time getTime(int columnIndex, Calendar cal) throws SQLException
	{
		return getTime(columnIndex);
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a Time object. This
	 * method uses the given calendar to construct an appropriate millisecond
	 * value for the time if the underlying database does not store timezone
	 * information.
	 *
	 * @param columnLabel data field label
	 * @param cal the Calendar object to use in constructing the time
	 * @return Time representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Time getTime(String columnLabel, Calendar cal) throws SQLException
	{
		return getTime(findColumn(columnLabel), cal);
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a Timestamp object.
	 * This method uses the given calendar to construct an appropriate
	 * millisecond value for the time if the underlying database does not store
	 * timezone information.
	 *
	 * @param columnIndex data field index (1's based)
	 * @param cal the Calendar object to use in constructing the time
	 * @return Timestamp representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException
	{
		return getTimestamp(columnIndex);
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a Timestamp object.
	 * This method uses the given calendar to construct an appropriate
	 * millisecond value for the time if the underlying database does not store
	 * timezone information.
	 *
	 * @param columnLabel data field label
	 * @param cal the Calendar object to use in constructing the time
	 * @return Timestamp representation of the field data
	 * @throws java.sql.SQLException
	 */
	public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException
	{
		return getTimestamp(findColumn(columnLabel));
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a URL object.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return URL representation of the field data
	 * @throws java.sql.SQLException
	 */
	public URL getURL(int columnIndex) throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED) Returns the specified data field as a URL object.
	 *
	 * @param columnLabel data field label
	 * @return URL representation of the field data
	 * @throws java.sql.SQLException
	 */
	public URL getURL(String columnLabel) throws SQLException
	{
		return getURL(findColumn(columnLabel));
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateRef(int columnIndex, Ref x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateRef(String columnLabel, Ref x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateBlob(int columnIndex, Blob x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateBlob(String columnLabel, Blob x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateClob(int columnIndex, Clob x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateClob(String columnLabel, Clob x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateArray(int columnIndex, Array x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateArray(String columnLabel, Array x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public RowId getRowId(int columnIndex) throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public RowId getRowId(String columnLabel) throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateRowId(int columnIndex, RowId x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateRowId(String columnLabel, RowId x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public int getHoldability() throws SQLException
	{
		return 0;
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public boolean isClosed() throws SQLException
	{
		return false;
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateNString(int columnIndex, String nString) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateNString(String columnLabel, String nString) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateNClob(int columnIndex, NClob nClob) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateNClob(String columnLabel, NClob nClob) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public NClob getNClob(int columnIndex) throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public NClob getNClob(String columnLabel) throws SQLException
	{
		return getNClob(findColumn(columnLabel));
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public SQLXML getSQLXML(int columnIndex) throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public SQLXML getSQLXML(String columnLabel) throws SQLException
	{
		return getSQLXML(findColumn(columnLabel));
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException
	{
	}


	/**
	 * Returns the specified data field as a String.
	 *
	 * @param columnIndex data field index (1's based)
	 * @return String representation of field data
	 * @throws java.sql.SQLException
	 */
	public String getNString(int columnIndex) throws SQLException
	{
		return getString(columnIndex);
	}


	/**
	 * Returns the specified data field as a String.
	 *
	 * @param columnLabel data field label
	 * @return String representation of field data
	 * @throws java.sql.SQLException
	 */
	public String getNString(String columnLabel) throws SQLException
	{
		return getString(columnLabel);
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public Reader getNCharacterStream(int columnIndex) throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public Reader getNCharacterStream(String columnLabel) throws SQLException
	{
		return getNCharacterStream(findColumn(columnLabel));
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateClob(int columnIndex, Reader reader, long length) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateClob(String columnLabel, Reader reader, long length) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateCharacterStream(int columnIndex, Reader x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateClob(int columnIndex, Reader reader) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateClob(String columnLabel, Reader reader) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateNClob(int columnIndex, Reader reader) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public void updateNClob(String columnLabel, Reader reader) throws SQLException
	{
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public <T> T unwrap(Class<T> iface) throws SQLException
	{
		return null;
	}


	/**
	 * (NOT IMPLEMENTED)
	 */
	public boolean isWrapperFor(Class<?> iface) throws SQLException
	{
		return false;
	}


	@Override
	public <T> T getObject(int i, Class<T> type) throws SQLException
	{
		throw new UnsupportedOperationException("Not supported yet.");
	}


	@Override
	public <T> T getObject(String string, Class<T> type) throws SQLException
	{
		throw new UnsupportedOperationException("Not supported yet.");
	}
}
