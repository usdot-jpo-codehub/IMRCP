/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.geosrv;

import imrcp.geosrv.GeoUtil;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import ucar.unidata.geoloc.LatLonPointImpl;
import ucar.unidata.geoloc.ProjectionImpl;
import ucar.unidata.geoloc.ProjectionPointImpl;
import ucar.unidata.geoloc.projection.LambertConformal;
import ucar.unidata.geoloc.projection.LatLonProjection;

/**
 * Represents a Projected Coordinate System that can be used by other system
 * components. The main reason this class was created was to lower the memory
 * footprint by having multiple files that use the same Projected Coordinate System
 * can all access a single object instead of having each file create its own
 * Projected Coordinate System.
 * @author aaron.cherney
 */
public class ProjProfile implements Comparable<ProjProfile>
{
	/**
	 * Index used in arrays that represent the corners of a cell for the top left x
	 */
	public static int xTL = 0;

	
	/**
	 * Index used in arrays that represent the corners of a cell for the top left y
	 */
	public static int yTL = 1;

	
	/**
	 * Index used in arrays that represent the corners of a cell for the top right x
	 */
	public static int xTR = 2;

	
	/**
	 * Index used in arrays that represent the corners of a cell for the top right y
	 */
	public static int yTR = 3;

	
	/**
	 * Index used in arrays that represent the corners of a cell for the bottom right x
	 */
	public static int xBR = 4;

	
	/**
	 * Index used in arrays that represent the corners of a cell for the bottom right y
	 */
	public static int yBR = 5;

	
	/**
	 * Index used in arrays that represent the corners of a cell for the bottom left x
	 */
	public static int xBL = 6;

	
	/**
	 * Index used in arrays that represent the corners of a cell for the bottom left y
	 */
	public static int yBL = 7;

	
	/**
	 * Tolerance used when comparing doubles
	 */
	private static double TOL = 0.0000001;

	
	/**
	 * Number of rows (y values) in the grid
	 */
	public int m_nVrt;

	
	/**
	 * Number of columns (x values) in the grid
	 */
	public int m_nHrz;

	
	/**
	 * First x value
	 */
	public double m_dX1;

	
	/**
	 * Last x value
	 */
	public double m_dX2;

	
	/**
	 * First y value
	 */
	public double m_dY1;

	
	/**
	 * Last y value
	 */
	public double m_dY2;

	
	/**
	 * Values of the x axis
	 */
	public double[] m_dXs;

	
	/**
	 * Values of the y axis
	 */
	public double[] m_dYs;

	
	/**
	 * Stores the longitude and latitudes of the coordinate system. Each row has
	 * the format [lon0, lat0, lon1, lat1,... lonn, latn]
	 */
	public double[][] m_dGrid;

	
	/**
	 * Flag indicated if the y axis is in ascending or descending order.
	 * true = descending, false = ascending
	 */
	public boolean m_bUseReverseY;

	
	/**
	 * Object that converts points from lon/lat to the Projected Coordinate
	 * System and vice versa
	 */
	public ProjectionImpl m_oProj;

	
	/**
	 * Default constructor. Does nothing.
	 */
	public ProjProfile()
	{
	}

	
	/**
	 * Constructs a new ProjProfile with the given x and y coordinate grids
	 * @param dX the values of the Projected Coordinate System's x axis
	 * @param dY the values of the Projected Coordinate System's y axis
	 */
	public ProjProfile(double[] dX, double[] dY)
	{
		m_nVrt = dY.length;
		m_nHrz = dX.length;
		m_dXs = dX; // keep the original coordinate grids to use for lookup later
		m_dYs = dY;
		m_dX1 = dX[0];
		m_dX2 = dX[m_nHrz - 1];
		m_dY1 = dY[0];
		m_dY2 = dY[m_nVrt - 1];
		m_bUseReverseY = m_dY1 > m_dY2;
	}
	
	
	public ProjProfile(DataInputStream oIn)
		throws IOException
	{
		ProjectionImpl oProj = null;
		int nProj = oIn.readByte();
		if (nProj == 0)
			oProj = new LatLonProjection();
		else if (nProj == 1)
			oProj = new LambertConformal(oIn.readDouble(), oIn.readDouble(), oIn.readDouble(), oIn.readDouble(), oIn.readDouble(), oIn.readDouble());
		else
			throw new IOException("Invalid projection");
		
		boolean bFixHrz = false;
		double[] dY = new double[oIn.readInt()];
		for (int nIndex = 0; nIndex < dY.length; nIndex++)
			dY[nIndex] = oIn.readDouble();
		double[] dX = new double[oIn.readInt()];
		for (int nIndex = 0; nIndex < dX.length; nIndex++) // read the x values of the grid
		{
			double dVal = oIn.readDouble();
			if (nProj == 0 && dVal > 180.0) // necessary for lat lon projections with values in degrees E (0 < x < 360)
				bFixHrz = true;
			dX[nIndex] = dVal;
		}
		
		if (bFixHrz) // convert degrees E values to decimal degrees -180 < x < 180
		{
			for (int nIndex = 0; nIndex < dX.length; nIndex++)
				dX[nIndex]  -= 180;
		}
		
		m_nVrt = dY.length;
		m_nHrz = dX.length;
		m_dXs = dX; // keep the original coordinate grids to use for lookup later
		m_dYs = dY;
		m_dX1 = dX[0];
		m_dX2 = dX[m_nHrz - 1];
		m_dY1 = dY[0];
		m_dY2 = dY[m_nVrt - 1];
		m_bUseReverseY = m_dY1 > m_dY2;
		initGrid(dX, dY, oProj);
	}
	
	
	public void write(DataOutputStream oOut)
		throws IOException
	{
		if (m_oProj.isLatLon())
		{
			oOut.writeByte(0);
		}
		else // only have lambert conformal as other projection coordinate system currently
		{
			oOut.writeByte(1);
			LambertConformal oLam = (LambertConformal)m_oProj;
			oOut.writeDouble(oLam.getOriginLat());
			oOut.writeDouble(oLam.getOriginLon());
			oOut.writeDouble(oLam.getParallelOne());
			oOut.writeDouble(oLam.getParallelTwo());
			oOut.writeDouble(oLam.getFalseEasting());
			oOut.writeDouble(oLam.getFalseNorthing());
		}
		oOut.writeInt(m_dYs.length);
		for (double dY : m_dYs)
			oOut.writeDouble(dY);
		oOut.writeInt(m_dXs.length);
		for (double dX : m_dXs)
			oOut.writeDouble(dX);
	}
	
	
	/**
	 * Creates the grid for the given x and y axis for the Projected Coordinate
	 * System.
	 * @param dX the values of the Projected Coordinate System's x axis
	 * @param dY the values of the Projected Coordinate System's y axis
	 * @param oProj Object that converts points from lon/lat to the 
	 * Projected Coordinate System and vice versa
	 */
	public void initGrid(double[] dX, double[] dY, ProjectionImpl oProj)
	{
		m_oProj = oProj;

		if (oProj != null)
		{
			ProjectionPointImpl oProjPt = new ProjectionPointImpl();
			LatLonPointImpl oLatLonPt = new LatLonPointImpl();

			int nHrz = m_nHrz * 2; // for each cell in the grid store the lon and lat so double the length of the x axis
			m_dGrid = new double[m_nVrt][];
			for (int i = 0; i < m_nVrt; i++) // for each row
			{
				m_dGrid[i] = new double[nHrz];
				for (int j = 0; j < nHrz;)
				{
					oProjPt.setLocation(dX[j >> 1], dY[i]); // right shift divides by 2
					oProj.projToLatLon(oProjPt, oLatLonPt); // convert from projection to lat lon
					m_dGrid[i][j++] = oLatLonPt.getLongitude(); // store x
					m_dGrid[i][j++] = oLatLonPt.getLatitude(); // store y
				}
			}
		}
		else // if there isn't a projection object just copy the x and y values
		{
			System.arraycopy(dY, 0, m_dYs, 0, m_nVrt);
			System.arraycopy(dX, 0, m_dXs, 0, m_nHrz);
		}
	}

	
	/**
	 * Fills the given double array with the lon/lat coordinates of the corners
	 * of the cell of the grid at the given horizontal and vertical indices
	 * @param nHrz horizontal index
	 * @param nVrt vertical index
	 * @param dCorners double array with length of 8 to be filled the geo coordinates
	 * of the corners of the cell.
	 */
	public void getCell(int nHrz, int nVrt, double[] dCorners)
	{
		nHrz = nHrz << 1; // shift left to multiply by 2 since the rows by lon/lat pairs
		double dTop = m_dGrid[nVrt][nHrz + 1];
		double dBot = m_dGrid[nVrt + 1][nHrz + 1];
		if (dTop > dBot) // handle reverse y order
		{
			dCorners[xTL] = m_dGrid[nVrt][nHrz]; // top left lon
			dCorners[yTL] = dTop; // lat
			dCorners[xTR] = m_dGrid[nVrt][nHrz + 2]; // top right lon
			dCorners[yTR] = m_dGrid[nVrt][nHrz + 3]; // lat

			dCorners[xBR] = m_dGrid[++nVrt][nHrz + 2]; // increment to next row, bot right lon
			dCorners[yBR] = m_dGrid[nVrt][nHrz + 3]; // lat
			dCorners[xBL] = m_dGrid[nVrt][nHrz]; // bot left lon
			dCorners[yBL] = dBot; // lat
		}
		else
		{
			dCorners[xBL] = m_dGrid[nVrt][nHrz]; // top left lon
			dCorners[yBL] = dTop; // lat
			dCorners[xBR] = m_dGrid[nVrt][nHrz + 2]; // top right lon
			dCorners[yBR] = m_dGrid[nVrt][nHrz + 3]; // lat

			dCorners[xTR] = m_dGrid[++nVrt][nHrz + 2]; // increment to next row, bot right lon
			dCorners[yTR] = m_dGrid[nVrt][nHrz + 3]; // lat
			dCorners[xTL] = m_dGrid[nVrt][nHrz]; // bot left lon
			dCorners[yTL] = dBot; // lat
		}
	}

	
	/**
	 *
	 * @param dLon
	 * @param dLat
	 * @param nIndices
	 */
	public void getPointIndices(double dLon, double dLat, int[] nIndices)
	{
		ProjectionPointImpl oProjPt = new ProjectionPointImpl();
		LatLonPointImpl oLatLonPt = new LatLonPointImpl();
		oLatLonPt.set(dLat, dLon);
		m_oProj.latLonToProj(oLatLonPt, oProjPt);

		int nIndex = Arrays.binarySearch(m_dXs, oProjPt.getX());
		if (nIndex < 0)
			nIndex = ~nIndex - 1;
		nIndices[0] = nIndex;

		if (m_bUseReverseY)
		{
			nIndex = reverseBinarySearch(m_dYs, oProjPt.getY());
			if (nIndex < 0)
				nIndex = ~nIndex - 1;
		}
		else
		{
			nIndex = Arrays.binarySearch(m_dYs, oProjPt.getY());
			if (nIndex < 0)
				nIndex = ~nIndex - 1;
		}
		nIndices[1] = nIndex;
	}

	
	/**
	 * Fills the given int array with the indices of the grid that correspond to
	 * the given lon/lat points.
	 * @param dLon1 first point longitude in decimal degrees
	 * @param dLat1 first point latitude in decimal degrees
	 * @param dLon2 second point longitude in decimal degrees
	 * @param dLat2 second point latitude in decimal degrees
	 * @param nIndices array of length 4 that gets filled with the indices of the
	 * grid corresponding to the lon/lat points. [min x index, max y index, max x index, min y index]
	 */
	public void getIndices(double dLon1, double dLat1, double dLon2, double dLat2, int[] nIndices)
	{
		ProjectionPointImpl oProjPt = new ProjectionPointImpl();
		LatLonPointImpl oLatLonPt = new LatLonPointImpl();
		oLatLonPt.set(dLat1, dLon1);
		m_oProj.latLonToProj(oLatLonPt, oProjPt); // get the projected coordinate for the first point

		int nIndex = Arrays.binarySearch(m_dXs, oProjPt.getX()); // find where the x value is on the axis
		if (nIndex < 0)
			nIndex = ~nIndex - 1;
		nIndices[0] = nIndex;

		if (m_bUseReverseY) // handle reverse y values
		{
			nIndex = reverseBinarySearch(m_dYs, oProjPt.getY());
			if (nIndex < 0)
					nIndex = ~nIndex - 1;
		}
		else
		{
			nIndex = Arrays.binarySearch(m_dYs, oProjPt.getY());
			if (nIndex < 0)
				nIndex = ~nIndex - 1;
		}
		nIndices[1] = nIndex;

		oLatLonPt.set(dLat2, dLon2);
		m_oProj.latLonToProj(oLatLonPt, oProjPt); // get the project coordinate for the second point

		nIndex = Arrays.binarySearch(m_dXs, oProjPt.getX()); // find where the x value is on the axis
		if (nIndex < 0)
			nIndex = ~nIndex - 1;
		
		if (nIndex < nIndices[0]) // ensure index 0 is min x
		{
			nIndices[2] = nIndices[0];
			nIndices[0] = nIndex;
		}
		else
			nIndices[2] = nIndex;

		if (m_bUseReverseY) // handle reverse y values
		{
			nIndex = reverseBinarySearch(m_dYs, oProjPt.getY());
			if (nIndex < 0)
				nIndex = ~nIndex - 1;
		}
		else
		{
			nIndex = Arrays.binarySearch(m_dYs, oProjPt.getY());
			if (nIndex < 0)
				nIndex = ~nIndex - 1;
		}

		if (nIndex > nIndices[1]) // ensure index 1 is max y
		{
			nIndices[3] = nIndices[1];
			nIndices[1] = nIndex;
		}
		else
			nIndices[3] = nIndex;
		
	}

	
	/**
	 * Compares ProjProjiles by number of rows, number of columns, first x value,
	 * last x value, first y value, last y value.
	 */
	@Override
	public int compareTo(ProjProfile o)
	{
		int nCompare = m_nVrt - o.m_nVrt;
		if (nCompare != 0)
			return nCompare;

		nCompare = m_nHrz - o.m_nHrz;
		if (nCompare != 0)
			return nCompare;

		nCompare = GeoUtil.compareTol(m_dX1, o.m_dX1, TOL);
		if (nCompare != 0)
			return nCompare;

		nCompare = GeoUtil.compareTol(m_dX2, o.m_dX2, TOL);
		if (nCompare != 0)
			return nCompare;

		nCompare = GeoUtil.compareTol(m_dY1, o.m_dY1, TOL);
		if (nCompare != 0)
			return nCompare;

		return GeoUtil.compareTol(m_dY2, o.m_dY2, TOL);
	}
	
	
	/**
	 * A binary search that works for an array sorted in descending order.
	 * @param dArr array of values sorted in descending order
	 * @param dKey value to search for
	 * @return the index of the search key, if it is contained in the list; 
	 * otherwise, (-(insertion point) - 1). The insertion point is defined as 
	 * the point at which the key would be inserted into the list: the index of 
	 * the first element greater than the key, or list.size() if all elements in
	 * the list are less than the specified key. Note that this guarantees that 
	 * the return value will be >= 0 if and only if the key is found.
	 */
	private static int reverseBinarySearch(double[] dArr, double dKey) 
	{
        int nLow = 0;
        int nHigh = dArr.length - 1;

        while (nLow <= nHigh) 
		{
            int nMid = (nLow + nHigh) >>> 1;
            double dMidVal = dArr[nMid];

            if (dMidVal > dKey)
                nLow = nMid + 1;  // Neither val is NaN, thisVal is smaller
            else if (dMidVal < dKey)
                nHigh = nMid - 1; // Neither val is NaN, thisVal is larger
            else 
			{
                long dMidBits = Double.doubleToLongBits(dMidVal);
                long dKeyBits = Double.doubleToLongBits(dKey);
                if (dMidBits == dKeyBits)     // Values are equal
                    return nMid;             // Key found
                else if (dMidBits > dKeyBits) // (-0.0, 0.0) or (!NaN, NaN)
                    nLow = nMid + 1;
                else                        // (0.0, -0.0) or (NaN, !NaN)
                    nHigh = nMid - 1;
            }
        }
        return -(nLow + 1);  // key not found.
    }
}
