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
 * Projection implementation for Section 3 template 30 (Lambert Conformal) of 
 * GRIB2 files.
 * @author aaron.cherney
 */
public class LambertConformalProj extends Projection
{
	/**
	 * Radius of spherical Earth in km
	 */
	public double m_dRadius;

	
	/**
	 * LaD - Latitude where Dx (x-direction grid length) and Dy (y-direction 
	 * grid length) are specified
	 */
	public double m_dOriginLat;

	
	/**
	 * LoV - longitude of meridian parallel to y-axis along which latitude 
	 * increases as the y-coordinate increases
	 */
	public double m_dOriginLon;

	
	/**
	 * Latin 1 - first latitude from the pole at which the secant cone cuts the 
	 * sphere
	 */
	public double m_dParallelOne;

	
	/**
	 * Latin 2 - second latitude from the pole at which the secant cone cuts the
	 * sphere
	 */
	public double m_dParallelTwo;
	
	
	/**
	 * Constructs a LambertConformalProj from Section 3 of a GRIB2 file
	 * 
	 * @param oIn DataInputStream of the GRIB2 file. It should be ready to read 
	 * the 15th byte of  Section 3 (meaning the length (4 bytes) of the section,
	 * section number (1 byte), source of grid defintion (1 byte), number of data
	 * poitns (4 bytes), number of octets for optional list of numbers, (1 byte), 
	 * Interpetation of list of numbers defining number of points (1 byte) and the
	 * grid definition template number (2 bytes) has been read)
	 * @param nSecLen length of Section 3 in bytes
	 * @param nTemplate Grid definition template number, for this case should be
	 * 30
	 * @throws IOException
	 */
	public LambertConformalProj(DataInputStream oIn, int nSecLen, int nTemplate)
	   throws IOException
	{
		m_nTemplate = nTemplate;
		byte yShapeOfEarth = oIn.readByte();
		byte yScaleFactor = oIn.readByte();
		m_dRadius = oIn.readInt() / 1000.0; // convert meters to km
		oIn.skipBytes(10);
		m_nX = oIn.readInt(); // number of points along x axis
		m_nY = oIn.readInt(); // number of points along y axis
		int nFirstLat = oIn.readInt(); // latitude of first grid point
		int nFirstLon = oIn.readInt(); // longitude of first grid point
		oIn.skipBytes(1);
		m_dOriginLat = GeoUtil.fromIntDeg(oIn.readInt(), 1000000); // latitude where Dx and Dy are specified
		m_dOriginLon = GeoUtil.fromIntDeg(oIn.readInt(), 1000000); // longitude of meridian parallel to y-axis along with latitude increases as the y-coordinate increases
		int nDx = oIn.readInt(); // x-direction grid length
		int nDy = oIn.readInt(); // y-direction grid length
		oIn.skipBytes(1);
		m_nScanningMode = oIn.readUnsignedByte();
		m_dParallelOne = GeoUtil.fromIntDeg(oIn.readInt(), 1000000); // first secant latitude
		m_dParallelTwo = GeoUtil.fromIntDeg(oIn.readInt(), 1000000); // second secant latitude
		int nPoleLat = oIn.readInt(); // latitude of southern pole
		int nPoleLon = oIn.readInt(); // longitude of southern pole
	}
}
