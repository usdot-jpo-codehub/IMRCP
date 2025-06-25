/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.store.grib;

import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;

/**
 * Base class used to encapsulate fields from Section 5 (Data Representation Section)
 * of GRIB2 files.
 * See https://www.nco.ncep.noaa.gov/pmb/docs/grib2/grib2_doc/
 * @author aaron.cherney
 */
public abstract class DataRep implements Runnable
{
	/**
	 * Reference value for simple packing. IEEE 32-bit floating-point value
	 */
	public double m_dR; // 

	
	/**
	 * EE = 2^E where E = Binary scale factor for simple packing
	 */
	public double m_dEE; 

	
	/**
	 * DD = 10^D where D = Decimal scale factor for simple packing
	 */
	public double m_dDD; 

	
	/**
	 * Number of bits required to hold the resulting scaled and referece data values
	 */
	public int m_nBits;

	
	/**
	 * Type of original field values from Code Table 5.1
	 */
	public int m_nFieldCode;
	
	
	/**
	 * Creates and returns a new DataRep from the given DataInputStream of a
	 * GRIB2 file. 
	 * @param oIn DataInputStream of the GRIB2 file. It should be ready to read 
	 * the 6th byte of Section 5 (meaning the length (4 bytes) of the section
	 * and section number (1 byte) has been read)
	 * @param nSecLen length of the section in bytes
	 * @return DataRep object defined by Section 5 or null if the template in
	 * the section has not been implemented
	 * @throws IOException
	 */
	public static DataRep newDataRep(DataInputStream oIn, int nSecLen)
	   throws IOException
	{
		int nTotalPoints = oIn.readInt(); // skip total number of data points
		int nTemplate = oIn.readUnsignedShort();
		if (nTemplate == 41)
			return new DataRepPng(oIn, nSecLen);
		
		oIn.skipBytes(nSecLen - 11);
		return null;
	}
	
	
	/**
	 * Child classes must implement this function which specifies the logic to
	 * parse Section 7 depending on the template type defined in Section 5.
	 * @param oIn DataInputStream of the GRIB2 file. It should be ready to read 
	 * the 6th byte of  Section 7 (meaning the length (4 bytes) of the section
	 * and section number (1 byte) has been read)
	 * @param nSecLen length of Section 7 in bytes
	 * @param oProj Projection object that contains the data from Section 3
	 * @param fRows The grid that gets filled with the data
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public abstract void read(FilterInputStream oIn, int nSecLen, Projection oProj, float[][] fRows)
	   throws IOException, InterruptedException;
	
	
	/**
	 * Decompresses the given bytes using the simple packing formula defined in
	 * section 2.3 of the GRIB2 specification
	 * @param yX1 0
	 * @param yX2 Scaled (encoded) value
	 * @return Original data value
	 */
	public float decompressSimple(byte yX1, byte yX2)
    {
        return (float)((m_dR + (256 * (yX1 & 0xff) + (yX2 & 0xff)) * m_dEE) / m_dDD);
    }
}
