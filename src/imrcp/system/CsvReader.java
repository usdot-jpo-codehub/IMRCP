package imrcp.system;

import java.io.InputStream;
import java.io.IOException;

/**
 * Implementation of a buffered FilterInputStream used to read CSV files.
 * @author aaron.cherney
 */
public class CsvReader extends BufferedInStream
{
	/**
	 * Default number of columns to allocate memory for
	 */
	protected static final int DEFAULT_COLS = 80;

	
	/**
	 * Stores the column count for each line read
	 */
	protected int m_nCol;

	
	/**
	 * Stores the index in {@link #m_sBuf} that each column ends at
	 */
	protected int[] m_nColEnds;

	
	/**
	 * Buffer used to store the characters of the current line being read
	 */
	protected StringBuilder m_sBuf = new StringBuilder(BUFFER_SIZE);

	
	/**
	 * State flag used to indicate the default state or that a control character
	 * was read
	 */
	protected static final int WHITESPACE = 0;

	
	/**
	 * State flag used to indicate reading a column has started
	 */
	protected static final int STARTCOL = 1;

	
	/**
	 * State flag used to indicate a column started with a quote
	 */
	protected static final int QUOTECOLUMN = 2;

	
	/**
	 * State flag used to indicate a quote was found inside of a column
	 */
	protected static final int INTERNALQUOTE = 3;
	
	protected static final int ESCAPE = 4;

	
	/**
	 * Stores the current state of the reader
	 */
	protected int m_nState = WHITESPACE;
	
	protected final char m_cDelimiter;

	
	/**
	 * Constructs a CsvReader for the given InputStream and allocates memory for
	 * the given number of columns
	 * 
	 * @param oInputStream InputStream representing a CSV data stream
	 * @param nCols number of columns to initially create
	 */
	public CsvReader(InputStream oInputStream, int nCols, char cDelimiter)
	{
		super(oInputStream);
		m_nColEnds = new int[nCols];
		m_cDelimiter = cDelimiter;
	}

	
	public CsvReader(InputStream oInputStream, char cDelimiter)
	{
		this(oInputStream, DEFAULT_COLS, cDelimiter);
	}
	
	/**
	 * Constructs a CsvReader for the given InputStream with the default number
	 * of columns
	 * 
	 * @param oInputStream InputStream representing a CSV data stream
	 */
	public CsvReader(InputStream oInputStream)
	{
		this(oInputStream, DEFAULT_COLS, ',');
	}

	
	/**
	 * Reads a line of the CSV data stream into {@link #m_sBuf}
	 * 
	 * @return the number of columns read for the current line
	 * @throws IOException
	 */
	public int readLine()
		throws IOException
	{
		m_nCol = 0; // reset column index
		m_sBuf.setLength(0); // reset line buffer
		m_nState = WHITESPACE;
		boolean bGo = true;
		int nChar;
		while (bGo && (nChar = read()) >= 0) // don't advance on line complete
		{
			switch (m_nState)
			{
				case WHITESPACE:
				{
					if (nChar == m_cDelimiter) // end of a column
					{
						addCol();
					}
					
					else if (nChar == '\n') // end of a line, acts as the end of a column as well
					{
						bGo = false;
						addCol();
					}
					else if (nChar == '"') // the line starts with a quote, do not add to the buffer but change the state to be able to escape quotes
					{
						m_nState = QUOTECOLUMN;
					}
					else if (nChar > ' ') // don't add control characters to the buffer
					{
						m_nState = STARTCOL;
						m_sBuf.append((char)nChar);
					}
					
					break;
				}
				case QUOTECOLUMN:
				{
					if (nChar == '"') // start of an internal quote
					{
						m_nState = INTERNALQUOTE;
					}
					else
						m_sBuf.append((char)nChar);
					break;
				}
				case INTERNALQUOTE:
				{
					if (nChar == '\\')
					{
						m_nState = ESCAPE;
					}
					else if (nChar == '"') // end of the internal quote
					{
						m_sBuf.append('"');
						m_nState = QUOTECOLUMN;
					}
					else 
					{
						m_nState = WHITESPACE;
						if (nChar == m_cDelimiter || nChar == '\n')
						{
							addCol();
							if (nChar == '\n')
								bGo = false;
						}
					}
					break;
				}
				case STARTCOL:
				{
					if (nChar == m_cDelimiter || nChar < ' ')
					{
						m_nState = WHITESPACE;
						bGo = (nChar != '\n');
						if (nChar != '\r') // ignore carriage return
							addCol(); // column found
					}
					else
						m_sBuf.append((char)nChar);
					break;
				}
				case ESCAPE: // \, "
				{
					if (nChar == '"' || nChar == '\\')
					{
						m_sBuf.append((char)nChar);
					}
					m_nState = INTERNALQUOTE;
					break;
				}
			}
			
		}

		if (bGo && m_nCol > 0) // check for missing final newline
			addCol();

		return m_nCol; // discovered column count
	}

	
	/**
	 * Grows the column array if needed and stores the current index in 
	 * {@link #m_nColEnds}
	 */
	private void addCol()
	{
		if (m_nCol == m_nColEnds.length) // extend column end array
		{
			int[] nColEnds = new int[m_nCol * 2];
			System.arraycopy(m_nColEnds, 0, nColEnds, 0, m_nCol);
			m_nColEnds = nColEnds;
		}
		m_nColEnds[m_nCol++] = m_sBuf.length();
	}

	
	/**
	 * Gets the start index of the given column
	 * 
	 * @param nCol column to get the start index of
	 * @return start index of the column, -1 if the column doesn't exists
	 */
	private int getStart(int nCol)
	{
		if (nCol < m_nCol)
		{
			if (nCol == 0) // special case for first column
				return 0;

			return m_nColEnds[nCol - 1];
		}
		return -1; // force index out of bounds
	}

	
	/**
	 * Gets the end index of the given column
	 * 
	 * @param nCol column to get the end index of
	 * @return end index of the column, -1 if the column doesn't exist
	 */
	private int getEnd(int nCol)
	{
		if (nCol < m_nCol)
			return m_nColEnds[nCol];

		return -1; // force index out of bounds
	}

	
	/**
	 * Determines if the given column of the current line held in {@link #m_sBuf} 
	 * contains nothing.
	 * 
	 * @param nCol column index to check
	 * @return true if the column has no contents, otherwise false
	 * @throws IndexOutOfBoundsException
	 */
	public boolean isNull(int nCol)
		throws IndexOutOfBoundsException
	{
		return (getEnd(nCol) - getStart(nCol) == 0);
	}

	
	/**
	 * Parses the given column of the the current line held in {@link #m_sBuf} 
	 * as a double
	 * 
	 * @param nCol column index to parse
	 * @return the characters of the column parsed as a double
	 * @throws IndexOutOfBoundsException
	 * @throws NumberFormatException
	 */
	public double parseDouble(int nCol)
		throws IndexOutOfBoundsException, NumberFormatException
	{
		return Text.parseDouble(m_sBuf.subSequence(getStart(nCol), getEnd(nCol)));
	}

	
	/**
	 * Parses the given column of the the current line held in {@link #m_sBuf} 
	 * as a long
	 * 
	 * @param nCol column index to parse
	 * @return the characters of the column parsed as a long
	 * @throws IndexOutOfBoundsException
	 * @throws NumberFormatException
	 */
	public long parseLong(int nCol)
		throws IndexOutOfBoundsException, NumberFormatException
	{
		return Text.parseLong(m_sBuf, getStart(nCol), getEnd(nCol));
	}

	
	/**
	 * Parses the given column of the the current line held in {@link #m_sBuf} 
	 * as a float
	 * 
	 * @param nCol column index to parse
	 * @return the characters of the column parsed as a float
	 * @throws IndexOutOfBoundsException
	 * @throws NumberFormatException
	 */
	public float parseFloat(int nCol)
		throws IndexOutOfBoundsException, NumberFormatException
	{
		return (float)parseDouble(nCol);
	}

	
	/**
	 * Parses the given column of the the current line held in {@link #m_sBuf} 
	 * as a integer
	 * 
	 * @param nCol column index to parse
	 * @return the characters of the column parsed as a integer
	 * @throws IndexOutOfBoundsException
	 * @throws NumberFormatException
	 */
	public int parseInt(int nCol)
		throws IndexOutOfBoundsException, NumberFormatException
	{
		return Text.parseInt(m_sBuf, getStart(nCol), getEnd(nCol));
	}

	
	/**
	 * Parses the given column of the the current line held in {@link #m_sBuf} 
	 * as a String
	 * 
	 * @param nCol column index to parse
	 * @return the characters of the column parsed as a String
	 * @throws IndexOutOfBoundsException
	 */
	public String parseString(int nCol)
		throws IndexOutOfBoundsException
	{
		return m_sBuf.substring(getStart(nCol), getEnd(nCol));
	}

	
	/**
	 * Parses the given column of the the current line held in {@link #m_sBuf} 
	 * as a String by placing it in the given StringBuilder
	 * 
	 * @param nCol column index to parse
	 * @param sBuf buffer to place the contents of the column in
	 * @return the number of characters in the column
	 * @throws IndexOutOfBoundsException
	 * @throws NullPointerException
	 */
	public int parseString(StringBuilder sBuf, int nCol)
		throws IndexOutOfBoundsException, NullPointerException
	{
		sBuf.setLength(0); // clear provided buffer
		sBuf.append(m_sBuf, getStart(nCol), getEnd(nCol));
		return sBuf.length();
	}
	
	
	/**
	 * Reconstructs the current line held in {@link #m_sBuf} with the original
	 * commas and places the contents in the given StringBuilder
	 * @param sLine buffer to place the contents of the line in
	 * @throws IOException
	 */
	public void getLine(StringBuilder sLine)
		throws IOException
	{
		int nPos = 0;
		sLine.setLength(0);
		StringBuilder sCol = new StringBuilder();
		String sDelimiter = Character.toString(m_cDelimiter);
		for (int nIndex = 0; nIndex < m_nCol; nIndex++)
		{
			sCol.setLength(0);
			sCol.append(m_sBuf.subSequence(nPos, m_nColEnds[nIndex]));
			if (sCol.indexOf(sDelimiter) >= 0)
			{
				int nLength = sCol.length();
				while (nLength-- > 0)
				{
					if (sCol.charAt(nLength) == '"') // escape " as ""
						sCol.insert(nLength, '"');
				}
				sCol.insert(0, '"'); // surround the column with ""s
				sCol.append('"');
			}
			sLine.append(sCol);
			sLine.append(sDelimiter);
			nPos = m_nColEnds[nIndex];
		}
		sLine.setCharAt(sLine.length() - 1, '\n');
	}
}
