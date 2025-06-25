/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.store.grib;

import imrcp.geosrv.GeoUtil;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Projection implementation for Section 3 template 0 (Latitude/Longitude) of 
 * GRIB2 files.
 * @author aaron.cherney
 */
public class LatLonProj extends Projection
{
	/**
	 * Latitude of first grid point
	 */
	public double m_dStartLat;

	
	/**
	 * Longitude of first grid point
	 */
	public double m_dStartLon;

	
	/**
	 * Latitude increment
	 */
	public double m_dLatInc;

	
	/**
	 * Longitude increment
	 */
	public double m_dLonInc;

	
	/**
	 * Constructs a LatLonProj from Section 3 of a GRIB2 file
	 * 
	 * @param oIn DataInputStream of the GRIB2 file. It should be ready to read 
	 * the 15th byte of  Section 3 (meaning the length (4 bytes) of the section,
	 * section number (1 byte), source of grid defintion (1 byte), number of data
	 * poitns (4 bytes), number of octets for optional list of numbers, (1 byte), 
	 * Interpetation of list of numbers defining number of points (1 byte) and the
	 * grid definition template number (2 bytes) has been read)
	 * @param nSecLen length of Section 3 in bytes
	 * @param nTemplate Grid definition template number, for this case should be
	 * 0
	 */
	public LatLonProj(DataInputStream oIn, int nSecLen, int nTemplate)
	   throws IOException
	{
		m_nTemplate = nTemplate;
		byte yShapeOfEarth = oIn.readByte();
		byte yScale = oIn.readByte();
		oIn.skipBytes(14);
		m_nX = oIn.readInt(); // number of points along a parallel
		m_nY = oIn.readInt(); // number of points along a meridian
		int nAngle = oIn.readInt();
		int nSubDivs = oIn.readInt();
		m_dStartLat = GeoUtil.fromIntDeg(oIn.readInt(), 1000000); // latitude of first grid point
		m_dStartLon = GeoUtil.fromIntDeg(oIn.readInt(), 1000000); // longitude of first grid point
		oIn.skipBytes(1);
		double dLat2 = GeoUtil.fromIntDeg(oIn.readInt(), 1000000); // latitude of last grid point
		double dLon2 = GeoUtil.fromIntDeg(oIn.readInt(), 1000000); // longitude of last grid point
		m_dLonInc = GeoUtil.fromIntDeg(oIn.readInt(), 1000000); // longtitude increment
		if (m_dStartLon > dLon2)
			m_dLonInc = -m_dLonInc;
		m_dLatInc = GeoUtil.fromIntDeg(oIn.readInt(), 1000000); // latitude increment
		if (m_dStartLat > dLat2)
			m_dLatInc = -m_dLatInc;
		m_nScanningMode = oIn.readUnsignedByte();
		oIn.skipBytes(nSecLen - 5 - 9 - 58); // skip the rest of the section
	}
}
