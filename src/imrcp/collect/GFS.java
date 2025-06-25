/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.collect;

import imrcp.system.BufferedInStream;
import imrcp.system.JSONUtil;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import org.json.JSONObject;

/**
 * Collects files from GFS (Global Forecast System) and saves a filtered version 
 * of the .grb2 containing only the configurable observation types. 
 * 
 * @author aaron.cherney
 */
public class GFS extends NWS
{	
	/**
	 * This integer marks the start of a GRIB2 file
	 */
	public static int GRIBMARKER = 1196575042;

	
	/**
	 * This integer marks the end of a GRIB2 file
	 */
	public static int ENDMARKER = 926365495;
	/**
	 * List of int[] that stores the parameters used to identify observation types
	 * inside the .grb2 files
	 */
	private ArrayList<int[]> m_oGribVariables;
 	
	
	/**
	 * Comparator for {@link GFS#m_oGribVariables}
	 */
	private final static Comparator<int[]> VARCOMP = (int[] o1, int[] o2) ->
	{
		int nReturn = o1[0] - o2[0];
		if (nReturn == 0)
		{
			nReturn = o1[1] - o2[1];
			if (nReturn == 0)
			{
				nReturn = o1[2] - o2[2];
				if (nReturn == 0)
				{
					nReturn = o1[3] - o2[3];
					if (nReturn == 0)
						nReturn = o1[4] - o2[4];
				}
			}
		}
		return nReturn;
	};

	
	/**
	 *
	 */
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		int[] nVariables = JSONUtil.getIntArray(oBlockConfig, "gribvars");
		if (nVariables.length % 5 == 0)
		{
			m_oGribVariables = new ArrayList();
			for (int nIndex = 0; nIndex < nVariables.length;)
				m_oGribVariables.add(new int[]{nVariables[nIndex++], nVariables[nIndex++], nVariables[nIndex++], nVariables[nIndex++], nVariables[nIndex++]});
			
			Collections.sort(m_oGribVariables, VARCOMP);
		}
	}

	
	protected long getStartTime(long lRecvTime, int nRange, int nDelay, int nFileIndex)
	{
		return lRecvTime + nDelay + (nFileIndex * 3600000);
	}
	
	
	protected long getEndTime(long lRecvTime, int nRange, int nDelay, int nFileIndex)
	{
		return lRecvTime + nDelay + (nFileIndex * 3600000) + nRange;
	}
	
	/**
	 * Opens the grb2 file and filters out undesired observation types and saves
	 * the resulting file to disk.
	 * 
	 * @param sFilename the grb2 file to filter
	 * @return the filename of the filtered file
	 */
	@Override
	public ByteArrayOutputStream filter(ByteArrayOutputStream oBaos)
	{
		try
		{
			ArrayList<long[]> oIndices = new ArrayList();
			byte[] yGrib = oBaos.toByteArray();
			int[] nVariableSearch = new int[5];
			try (DataInputStream oIn = new DataInputStream(new BufferedInStream(new ByteArrayInputStream(yGrib))))
			{
				long lPos = 0;
				byte[] ySecId = new byte[1];
				while (oIn.available() > 0)
				{
					long lLeft = readHeader(oIn, nVariableSearch);
					long[] lIndex = new long[]{lPos, lPos + lLeft + 16};
					lPos += lLeft + 16;
					while (lLeft > 4)
					{						
						int nSecLen = readSection(oIn, ySecId);
						if (ySecId[0] == 4)
						{
							int nCoordValues = oIn.readUnsignedShort(); 
							nVariableSearch[4] = oIn.readUnsignedShort(); 
							nVariableSearch[1] = oIn.readUnsignedByte(); // 10
							nVariableSearch[2] = oIn.readUnsignedByte(); // 11
							oIn.skipBytes(11);
							//		int nProcess = oIn.readUnsignedByte(); // 12
							//		int nBackgroundId = oIn.readUnsignedByte(); // 13
							//		int nAnalysis = oIn.readUnsignedByte(); // 14
							//		int nHours = oIn.readUnsignedShort(); // 15-16 hours of observational data cutoff after reference time (hours greater than 65534 will be coded as 65534
							//		int nMinutes = oIn.readUnsignedByte(); // 17
							//		int nTimeUnit = oIn.readUnsignedByte(); // 18
							//		int nTimeInUnits = oIn.readInt(); // 19 - 22
							nVariableSearch[3] = oIn.readUnsignedByte(); // 23
							//		int nScaleFirstSur = oIn.readUnsignedByte(); // 24
							//		int nValFirstSur = oIn.readInt(); // 25 - 28
							//		int nTypeSecondSur = oIn.readUnsignedByte(); // 29
							//		int nScaleSecondSur = oIn.readUnsignedByte(); // 30 
							//		int nValSecondSur = oIn.readInt(); // 31-34
							oIn.skipBytes(nSecLen - 5 - 4 - 14);
							if (Collections.binarySearch(m_oGribVariables, nVariableSearch, VARCOMP) >= 0)
								oIndices.add(lIndex);
						}
						else
							oIn.skipBytes(nSecLen - 5);

						lLeft -= nSecLen;
					}

					oIn.readInt(); // section 8
				}
			}

			ByteArrayOutputStream oReturn = new ByteArrayOutputStream(yGrib.length);
			try (BufferedInStream oIn = new BufferedInStream(new ByteArrayInputStream(yGrib));
			   BufferedOutputStream oOut = new BufferedOutputStream(oReturn))
			{
				long lPos = 0;
				for (long[] lIndex : oIndices)
				{
					long lSkip = lIndex[0] - lPos;
					oIn.skip(lSkip);
					lPos += lSkip;
					int nLen = (int)(lIndex[1] - lIndex[0]);
					for (int i = 0; i < nLen; i++)
						oOut.write(oIn.read());
					lPos += nLen;
				}
			}
			return oReturn;
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
			return null;
		}
	}
	
	public static long readHeader(DataInputStream oIn, int[] nDiscipline)
	   throws IOException
	{
		int nMarker = oIn.readInt();
		while (nMarker != GRIBMARKER) // read until "GRIB"
			nMarker = (nMarker <<  8) | oIn.readUnsignedByte();
		
		oIn.skipBytes(2); // skip the rest of the header
		nDiscipline[0] = oIn.readUnsignedByte();
		oIn.skipBytes(1); // edition number, always 2
		return oIn.readLong() - 16; // read the number of bytes for this grib file, subtract the length of this section to get the number of bytes left
	}
	
	/**
	 * Reads the length of the section (4 bytes) and the section number (1 byte)
	 * of the current section
	 * @param oIn DataInputStream of the GRIB2 file. Its position should be at 
	 * the start of a GRIB2 section
	 * @param ySection stores the section number
	 * @return Length of the section in bytes
	 * @throws Exception
	 */
	public static int readSection(DataInputStream oIn, byte[] ySection)
		throws Exception
	{
		int nReturn = oIn.readInt(); // section length
		ySection[0] = oIn.readByte(); // section number
		
		return nReturn;
	}
}
