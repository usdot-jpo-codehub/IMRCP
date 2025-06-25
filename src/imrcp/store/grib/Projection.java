/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.store.grib;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * Base class for reading Section 3 - Grid Definition Section of GRIB2 files.
 * 
 * See https://www.nco.ncep.noaa.gov/pmb/docs/grib2/grib2_doc/grib2_sect3.shtml
 * @author aaron.cherney
 */
public abstract class Projection
{
	/**
	 * Number of points along the x axis
	 */
	public int m_nX;

	
	/**
	 * Number of points along the y axis
	 */
	public int m_nY;

	
	/**
	 * Scanning mode. See Flag Table 3.4 of NCEP GRIB2 documentation
	 */
	public int m_nScanningMode;

	
	/**
	 * Template number, tells what kind of projected coordinate system is used.
	 * See GRIB2 - TABLE 3.1
	 */
	public int m_nTemplate;
	
	
	/**
	 * Constructs a new Projection object based off the information found in 
	 * Section 3 of a GRIB2 file.
	 * 
	 * @param oIn DataInputStream of the GRIB2 file. It should be ready to read 
	 * the 6th byte of  Section 3 (meaning the length (4 bytes) of the section and
	 * section number (1 byte) has been read)
	 * @param nSecLen length of Section 3 in bytes
	 * @return a new Projection, its type depending on the template number. Right
	 * now there are 2 implemented: 0 = LatLonProj and 30 = LambertConformalProj.
	 * Any other template will return null
	 * @throws IOException
	 */
	public static Projection newProjection(DataInputStream oIn, int nSecLen)
	   throws IOException
	{
		if (oIn.readUnsignedByte() != 0) // get the source of grid defintion. 0 means specified in Code table 3.1 - everything else we can't use or there is no grid defintion
		{
			oIn.skipBytes(nSecLen - 6);
			return null;
		}
		oIn.skipBytes(6); // skip number of data points (4) number of octets for optional list (1) and interpretation of list of numbers (1)
		int nTemplate = oIn.readUnsignedShort();
		if (nTemplate == 0)
		{
			return new LatLonProj(oIn, nSecLen, nTemplate);
		}
		else if (nTemplate == 30)
		{
			return new LambertConformalProj(oIn, nSecLen, nTemplate);
		}
		
		oIn.skipBytes(nSecLen - 14);
		return null;
	}
}
