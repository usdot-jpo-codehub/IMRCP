package imrcp.system;

import java.io.File;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * This class is used to generate time dependent file names that contain some
 * metadata about what is contained in the file. Data files collected
 * and created by the collect and comp packages while use this class to create the
 * file names. In general file names in the IMRCP system have this format:
 * contrib_extrainfo0_extrainfo1_..._starttime_endtime_validtime.ext where contrib
 * is the up to 6 character alphanumeric string representing the IMRCP contributor
 * ID, extrainfos can be anything like the size of grid for gridded data files,
 * starttime, endtime, and validtime are all date/time strings in the format
 * yyyyMMddHHmm for times in UTC, and ext is the file extension. An example of a
 * pattern string is: "/opt/imrcp-data-prod/rtma/%ryM/%ryMd/rtma_030_%syMdHm_%eyMdHm_%ryMdHm.grb2"
 * The '%' indicates the start of a replacement pattern. The character immediately
 * after the '%' indicates the type of pattern. 
 * <br>Possible types of pattern are:<br>
 * r = received(valid) time<br>
 * s = start time<br>
 * e = end time<br>
 * S = String<br>
 * <br>Implemented replacement patters are:
 * S = String<br>
 * yMdHm = yyyyMMddHHmm (year month day hour(0-23) minute)<br>
 * yMdH = yyyyMMddHH (year month day hour(0-23))<br>
 * yMd = yyyyMMdd (year month day)<br>
 * yM = yyyyMM (year month)<br>
 * y = yyyy (year)<br>
 * nnn = 3 digit file offset index<br>
 * n = 1 digit file offset index<br>
 * HHmm = hour(0-23) minute<br>
 * H = hour(0-23)<br>
 * 
 * @author aaron.cherney
 */
public class FilenameFormatter extends GregorianCalendar
{
	/**
	 * String that contains the file name pattern.
	 */
	private String m_sPattern;

	
	/**
	 * Internal buffer used to create the filenames
	 */
	private StringBuilder m_sBuf;

	/**
	 * Index of the valid time in long[] frequently used by stores
	 */
	public static int VALID = 0;

	
	/**
	 * Index of the start time in long[] frequently used by stores
	 */
	public static int START = 2;

	
	/**
	 * Index of the end time in long[] frequently used by stores
	 */
	public static int END = 1;
	
	/**
	 * Default constructor. Does nothing.
	 */
	protected FilenameFormatter()
	{
	}

	
	/**
	 * Constructs a FilenameFormatter with the given pattern and sets its 
	 * timezone to UTC.
	 * @param sPattern file name pattern
	 */
	public FilenameFormatter(String sPattern)
	{
		setTimeZone(TimeZone.getTimeZone("UTC"));
		m_sPattern = sPattern; // save pattern
		m_sBuf = new StringBuilder(sPattern.length() * 2); // reserve buffer space
	}

	public String getPattern()
	{
		return m_sPattern;
	}
	/**
	 * Gets the file name using the given parameters to format {@link #m_sPattern}
	 * 
	 * @param lRcvd received time in milliseconds since Epoch
	 * @param lStart start time in milliseconds since Epoch
	 * @param lEnd end time in milliseconds since Epoch
	 * @param nOffset file offset index
	 * @param sStrings string array used to replace '%S's in the pattern in the
	 * order of the array
	 * @return The formatted filename
	 */
	public synchronized String format(long lRcvd, long lStart, long lEnd, int nOffset, String... sStrings)
	{
		m_sBuf.setLength(0); // reset buffer
		m_sBuf.append(m_sPattern);
		
		int nStringPos = 0;
		int nPos = m_sBuf.indexOf("%");
		while (nPos >= 0)
		{
			int nStartPos = nPos;
			setTimeInMillis(lEnd);
			char cWhich = m_sBuf.charAt(nPos + 1); // switch by time reference
			if (cWhich == '%')
			{
				m_sBuf.deleteCharAt(nPos++);
			}
			else if (cWhich == 'r')
				setTimeInMillis(lRcvd);
			else if (cWhich == 's')
				setTimeInMillis(lStart);
			else if (cWhich == 'S')
			{
				if (nStringPos < sStrings.length)
				{
					String sString = sStrings[nStringPos++];
					S(nPos, sString);
					nPos = m_sBuf.indexOf("%", nPos + sString.length());
				}
				else
					nPos = m_sBuf.indexOf("%", nPos + 1);
				continue;
			}

			if (m_sBuf.indexOf("yMdHm", nPos) == nPos + 2) // switch on pattern
				yMdHm(nPos);
			else if (m_sBuf.indexOf("yMdH", nPos) == nPos + 2)
				yMdH(nPos);
			else if (m_sBuf.indexOf("yMd", nPos) == nPos + 2)
				yMd(nPos);
			else if (m_sBuf.indexOf("yM", nPos) == nPos + 2)
				yM(nPos);
			else if (m_sBuf.indexOf("y", nPos) == nPos + 2)
				y(nPos);
			else if (m_sBuf.indexOf("nnn", nPos) == nPos + 2)
				nnn(nPos, nOffset);
			else if (m_sBuf.indexOf("n", nPos) == nPos + 2)
				n(nPos, nOffset);
			else if (m_sBuf.indexOf("HHmm", nPos) == nPos + 2)
				HHmm(nPos);
			else if (m_sBuf.indexOf("H", nPos) == nPos + 2)
				H(nPos);
				
				
			nPos = m_sBuf.indexOf("%", nPos);
			if (nPos == nStartPos)
				++nPos;
		}

		return m_sBuf.toString();
	}
	
	
	/**
	 * Wrapper for {@link #format(long, long, long, int, java.lang.String...)} 
	 * passing a file offset index of zero.
	 * 
	 * @param lRcvd received time in milliseconds since Epoch
	 * @param lStart start time in milliseconds since Epoch
	 * @param lEnd end time in milliseconds since Epoch
	 * @param sStrings string array used to replace '%S's in the pattern in the
	 * order of the array
	 * @return The formatted filename
	 */
	public synchronized String format(long lRcvd, long lStart, long lEnd, String... sStrings)
	{
		return format(lRcvd, lStart, lEnd, 0, sStrings);
	}

	
	/**
	 * Parses the given file name and places the start, end, and valid times in
	 * the given array in the format [received, end, start]. Filename must be in
	 * the IMRCP time dependent file name format.
	 * 
	 * @param sFilename file name with the format: contrib_extrainfo0_extrainfo1_..._starttime_endtime_validtime.ext (described above in the class javadoc)
	 * @param lTimes array to fill with the times
	 * @return IMRCP contributor id for the file
	 */
	public synchronized int parse(String sFilename, long[] lTimes)
	{
		m_sBuf.setLength(0);
		m_sBuf.append(sFilename);
		int nPos = m_sBuf.lastIndexOf(File.separator) + 1;
		int nReturn = Integer.valueOf(m_sBuf.substring(nPos, m_sBuf.indexOf("_", nPos)), 36);
		nPos = m_sBuf.length();
		set(Calendar.SECOND, 0);
		set(Calendar.MILLISECOND, 0);
		for (int i = 0; i < 3; i++)
		{
			nPos = m_sBuf.lastIndexOf("_", nPos);
			set(Calendar.YEAR, Integer.parseInt(m_sBuf.substring(nPos + 1, nPos + 5)));
			set(Calendar.MONTH, Integer.parseInt(m_sBuf.substring(nPos + 5, nPos + 7)) - 1);
			set(Calendar.DAY_OF_MONTH, Integer.parseInt(m_sBuf.substring(nPos + 7, nPos + 9)));
			set(Calendar.HOUR_OF_DAY, Integer.parseInt(m_sBuf.substring(nPos + 9, nPos + 11)));
			set(Calendar.MINUTE, Integer.parseInt(m_sBuf.substring(nPos + 11, nPos + 13)));
			lTimes[i] = getTimeInMillis();
			--nPos;
		}
		
		return nReturn;
	}
	
	
	/**
	 * Parses source file names from National Weather Service collectors, determining
	 * the received time of the file and returning its file offset index.
	 * 
	 * @param sFilename File name to parse
	 * @param lTimes array to fill with the received time in position zero.
	 * @return file offset index of the forecast file.
	 */
	public synchronized int parseRecv(String sFilename, long[] lTimes)
	{
		m_sBuf.setLength(0);
		m_sBuf.append(sFilename);
		int nOffset = 0; // default is file offset index = 0
		int nPos = m_sPattern.indexOf("%r"); // find the received time position in the pattern
		int nCount = 0;
		set(Calendar.MINUTE, 0);
		set(Calendar.SECOND, 0);
		set(Calendar.MILLISECOND, 0);
		while (nPos >= 0)
		{
			int nParsePos = nPos - nCount++ * 2;
			if (m_sPattern.indexOf("yyyyMMdd", nPos) == nPos + 2) // switch on possible date and file offset patterns
				setyyyyMMdd(nParsePos);
			else if (m_sPattern.indexOf("HHmmss", nPos) == nPos + 2)
				setHHmmss(nParsePos);
			else if (m_sPattern.indexOf("HHmm", nPos) == nPos + 2)
				setHHmm(nParsePos);
			else if (m_sPattern.indexOf("HH", nPos) == nPos + 2)
				setHH(nParsePos);
			else if (m_sPattern.indexOf("nnn", nPos) == nPos + 2)
				nOffset = Integer.parseInt(m_sBuf.substring(nParsePos, nParsePos + 3));
			else if (m_sPattern.indexOf("nn", nPos) == nPos + 2)
				nOffset = Integer.parseInt(m_sBuf.substring(nParsePos, nParsePos + 2));
			
			nPos = m_sPattern.indexOf("%r", nPos + 1);
		}
		
		lTimes[0] = getTimeInMillis();
		return nOffset;
	}
	
	
	/**
	 * Parses the given file name of a data file in a SpatialFileCache to determine
	 * the map tile indices of the file and places them in {@code nTile}
	 * 
	 * @param sFilename file name of a data file in a SpatialFileCache, the x tile index
	 * must be in the first "%S" position and the y tile index must be in the second
	 * "%S" position.
	 * @param nTile array to be filled in the format [x map tile index, y map tile index]
	 */
	public synchronized void parseTile(String sFilename, int[] nTile)
	{
		m_sBuf.setLength(0);
		m_sBuf.append(sFilename);
		nTile[0] = nTile[1] = Integer.MIN_VALUE;
		int nPos = m_sPattern.indexOf("%S");
		int nPatternPos = 0;
		int nBufPos = 0;
		int nParseEnd = 0;
		for (int nTileIndex = 0; nTileIndex < 2; nTileIndex++)
		{
			if (nPos < 0) // invalid file name pattern
				return;
			
			while (nPatternPos < nPos)
			{
				if (m_sBuf.charAt(nBufPos++) != m_sPattern.charAt(nPatternPos++)) 
					return; // file name doesn't match pattern
			}

			int nParseStart = nBufPos;
			nParseEnd = nBufPos + 1;
			nPatternPos = nPos + "%S".length();
			char cNext = m_sPattern.charAt(nPatternPos);
			while (m_sBuf.charAt(nParseEnd) != cNext) // don't know how many digits are in tile index, so go until the next char in the pattern is found
				++nParseEnd;

			nTile[nTileIndex] = Text.parseInt(m_sBuf, nParseStart, nParseEnd);
			nBufPos = nParseEnd;
			
			nPos = m_sPattern.indexOf("%S", nPatternPos);
		}
	}

	
	/**
	 * Sets the internal calendar's year month and day from the given position
	 * in {@link #m_sBuf}
	 * 
	 * @param nPos position to start parsing
	 */
	protected void setyyyyMMdd(int nPos)
	{
		set(Calendar.YEAR, Integer.parseInt(m_sBuf.substring(nPos, nPos + 4)));
		set(Calendar.MONTH, Integer.parseInt(m_sBuf.substring(nPos + 4, nPos + 6)) - 1); // months are 0 based so subtract one
		set(Calendar.DAY_OF_MONTH, Integer.parseInt(m_sBuf.substring(nPos + 6, nPos + 8)));
	}
	
	
	/**
	 * Sets the internal calendar's hour from the given position in {@link #m_sBuf}
	 * 
	 * @param nPos position to start parsing
	 */
	protected void setHH(int nPos)
	{
		set(Calendar.HOUR_OF_DAY, Integer.parseInt(m_sBuf.substring(nPos, nPos + 2)));
	}
	
	
	/**
	 * Sets the internal calendar's hour and minute from the given position
	 * in {@link #m_sBuf}
	 * 
	 * @param nPos position to start parsing
	 */
	protected void setHHmm(int nPos)
	{
		set(Calendar.HOUR_OF_DAY, Integer.parseInt(m_sBuf.substring(nPos, nPos + 2)));
		set(Calendar.MINUTE, Integer.parseInt(m_sBuf.substring(nPos + 2, nPos + 4)));
	}
	
	
	/**
	 * Sets the internal calendar's hour, minute, and second from the given position
	 * in {@link #m_sBuf}
	 * 
	 * @param nPos position to start parsing
	 */
	protected void setHHmmss(int nPos)
	{
		set(Calendar.HOUR_OF_DAY, Integer.parseInt(m_sBuf.substring(nPos, nPos + 2)));
		set(Calendar.MINUTE, Integer.parseInt(m_sBuf.substring(nPos + 2, nPos + 4)));
		set(Calendar.SECOND, Integer.parseInt(m_sBuf.substring(nPos + 4, nPos + 6)));
	}

	
	/**
	 * Adds the internal calendar's year, month, day, hour, and minute to {@link #m_sBuf}
	 * at the given position.
	 * 
	 * @param nPos position to insert the date/time string
	 */
	protected void yMdHm(int nPos)
	{
		m_sBuf.delete(nPos, nPos + 7); // remove %?yMdHm pattern
		zeroPad(m_sBuf, nPos, get(Calendar.MINUTE));
		zeroPad(m_sBuf, nPos, get(Calendar.HOUR_OF_DAY));
		zeroPad(m_sBuf, nPos, get(Calendar.DAY_OF_MONTH));
		zeroPad(m_sBuf, nPos, get(Calendar.MONTH) + 1);
		m_sBuf.insert(nPos, get(Calendar.YEAR)); // should contain at least 4-digits
	}

	
	/**
	 * Adds the internal calendar's year, month, day, and hour to {@link #m_sBuf}
	 * at the given position.
	 * 
	 * @param nPos position to insert the date/time string
	 */
	protected void yMdH(int nPos)
	{
		m_sBuf.delete(nPos, nPos + 6); // remove %?yMdH pattern
		zeroPad(m_sBuf, nPos, get(Calendar.HOUR_OF_DAY));
		zeroPad(m_sBuf, nPos, get(Calendar.DAY_OF_MONTH));
		zeroPad(m_sBuf, nPos, get(Calendar.MONTH) + 1);
		m_sBuf.insert(nPos, get(Calendar.YEAR)); // should contain at least 4-digits
	}

	
	/**
	 * Adds the internal calendar's year, month, and day to {@link #m_sBuf}
	 * at the given position.
	 * 
	 * @param nPos position to insert the date/time string
	 */
	protected void yMd(int nPos)
	{
		m_sBuf.delete(nPos, nPos + 5); // remove %?yMd pattern
		zeroPad(m_sBuf, nPos, get(Calendar.DAY_OF_MONTH));
		zeroPad(m_sBuf, nPos, get(Calendar.MONTH) + 1);
		m_sBuf.insert(nPos, get(Calendar.YEAR)); // should contain at least 4-digits
	}

	
	/**
	 * Adds the internal calendar's year and month to {@link #m_sBuf}
	 * at the given position.
	 * 
	 * @param nPos position to insert the date/time string
	 */
	protected void yM(int nPos)
	{
		m_sBuf.delete(nPos, nPos + 4); // remove %?yM pattern
		zeroPad(m_sBuf, nPos, get(Calendar.MONTH) + 1);
		m_sBuf.insert(nPos, get(Calendar.YEAR)); // should contain at least 4-digits
	}

	
	/**
	 * Adds the internal calendar's year to {@link #m_sBuf} at the given position.
	 * 
	 * @param nPos position to insert the date/time string
	 */
	protected void y(int nPos)
	{
		m_sBuf.delete(nPos, nPos + 3); // remove %?y pattern
		m_sBuf.insert(nPos, get(Calendar.YEAR)); // should contain at least 4-digits
	}
	
	
	/**
	 * Adds the internal calendar's hour and minute to {@link #m_sBuf}
	 * at the given position.
	 * 
	 * @param nPos position to insert the date/time string
	 */
	protected void HHmm(int nPos)
	{
		m_sBuf.delete(nPos, nPos + 6); // remove %?HHmm pattern
		zeroPad(m_sBuf, nPos, get(Calendar.MINUTE));
		zeroPad(m_sBuf, nPos, get(Calendar.HOUR_OF_DAY));
	}
	
	
	/**
	 * Adds the internal calendar's hour to {@link #m_sBuf} at the given position.
	 * 
	 * @param nPos position to insert the date/time string
	 */
	protected void H(int nPos)
	{
		m_sBuf.delete(nPos, nPos + 3); // remove %?H pattern
		zeroPad(m_sBuf, nPos, get(Calendar.HOUR_OF_DAY));
	}
	
	
	/**
	 * Adds the given file offset index to {@link #m_sBuf} at the given position.
	 * 
	 * @param nPos position to insert the file offset index
	 * @param nOffset file offset index
	 */
	protected void n(int nPos, int nOffset)
	{
		m_sBuf.delete(nPos, nPos + 3); // remove %?n pattern
		zeroPad(m_sBuf, nPos, nOffset);
	}
	
	
	/**
	 * Adds the given file offset index to {@link #m_sBuf} at the given position.
	 * 
	 * @param nPos position to insert the file offset index
	 * @param nOffset file offset index
	 */
	protected void nnn(int nPos, int nOffset)
	{
		m_sBuf.delete(nPos, nPos + 5); // remove %?nnn pattern
		zeroPad(m_sBuf, nPos, nOffset, 3);
	}
	
	
	/**
	 * Adds the given string to {@link #m_sBuf} at the given position.
	 * 
	 * @param nPos position to insert the string
	 * @param sString the string to add
	 */
	protected void S(int nPos, String sString)
	{
		m_sBuf.delete(nPos, nPos + 2); // remove %S pattern
		m_sBuf.insert(nPos, sString);
	}

	
	/**
	 * If the given value is less than 10, adds a 0 at the front of it to ensure
	 * it takes up 2 character positions before inserting it in the given StringBuilder
	 * at the given position.
	 * 
	 * @param sBuf StringBuilder to insert the value in
	 * @param nPos position to insert the value
	 * @param nVal value to insert
	 */
	private static void zeroPad(StringBuilder sBuf, int nPos, int nVal)
	{
		sBuf.insert(nPos, nVal);
		if (nVal < 10)
			sBuf.insert(nPos, "0"); // zero pad
	}
	
	
	/**
	 * If necessary, adds 0s to the front of the given value to ensure it is 
	 * {@code nPlaces} characters long before inserting it in the given StringBuilder
	 * at the given positions.
	 * 
	 * @param sBuf StringBuilder to insert the value in
	 * @param nPos position to insert the value
	 * @param nVal value to insert
	 * @param nPlaces number of characters the number needs take up in the StringBuilder
	 */
	private static void zeroPad(StringBuilder sBuf, int nPos, int nVal, int nPlaces)
	{
		sBuf.insert(nPos, nVal);
		for (int nIndex = nPlaces; nIndex > 1; nIndex--)
		{
			if (nVal < Math.pow(10, nIndex - 1))
				sBuf.insert(nPos, "0");
		}
	}
	
	
	/**
	 * Gets the file extension of the pattern
	 * @return the file extension of the pattern
	 */
	public String getExtension()
	{
		return m_sPattern.substring(m_sPattern.lastIndexOf("."));
	}
}
