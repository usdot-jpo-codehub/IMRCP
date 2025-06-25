/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.store.grib;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * Represents the Grid of data in a GRIB2 file found in Section 7.
 * @author aaron.cherney
 */
public class Grid
{
	/**
	 * Projection object that contains the data from Section 3
	 */
	public Projection m_oProj;

	
	/**
	 * Data Representation object (from Section 5) that defines how to 
	 * interpret the data in Section 7.
	 */
	public DataRep m_oDataRep;

	
	/**
	 * Arrays used to store the gridded data
	 */
	public float[][] m_fData;

	
	/**
	 * Object that defines the observation type of the data
	 */
	public GribParameter m_oParameter;

	
	/**
	 * Default constructor. Does nothing.
	 */
	Grid()
	{	
	}
	
	
	/**
	 * Constructs a Grid with the given parameters.
	 * @param oIn DataInputStream of the GRIB2 file. It should be ready to read 
	 * the 6th byte of  Section 7 (meaning the length (4 bytes) of the section
	 * and section number (1 byte) has been read)
	 * @param nSecLen length of Section 7 in byte
	 * @param oProj Projection object that contains the data from Section 3
	 * @param oDataRep Data Representation object that defines how to interpret
	 * the data in Section 7
	 * @param oParameter GRIB2 observation type of the data
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public Grid(DataInputStream oIn, int nSecLen, Projection oProj, DataRep oDataRep, GribParameter oParameter)
	   throws IOException, InterruptedException
	{
		m_oProj = oProj;
		m_oDataRep = oDataRep;
		m_fData = new float[oProj.m_nY][oProj.m_nX]; // initialize grid
		m_oDataRep.read(oIn, nSecLen, m_oProj, m_fData); // read the data
		m_oParameter = oParameter;
	}
}
