/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.system.shp;

import imrcp.geosrv.GeoUtil;
import imrcp.system.Arrays;
import imrcp.system.Utility;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 *
 * @author Federal Highway Administration
 */
public class ShpReader extends DataInputStream
{
	private int m_nCurRecord = 1; // records start at 1
	private final ArrayList<int[]> m_oOuters = new ArrayList();
	private final ArrayList<int[]> m_oHoles = new ArrayList();
	
	public ShpReader(InputStream oIn)
		throws IOException
	{
		super(oIn);
		readFileHeader();
	}

	public final void readFileHeader()
		throws IOException
	{
		int nCode = readInt();
		// skip the reserved portion of the Header
		skip(20);

		int nLength = readInt();
		int nVersion = Utility.swap(readInt());
		int nType = Utility.swap(readInt());
		if (nType != 1 && nType != 5) // only implemented point and polygon
			throw new IOException("Invalid/not implemented Shape Type: " + nType);

		double dXmin = Utility.swapD(readLong());
		double dYmin = Utility.swapD(readLong());
		double dXmax = Utility.swapD(readLong());
		double dYmax = Utility.swapD(readLong());

		// throw the rest away Z and M min and maxes
		skip(32);
	}
	
	
	public int readRecordHeader()
		throws IOException
	{
		while (readInt() != m_nCurRecord); // read ints until the expected record number is found. Shouldn't ever get off by single bytes since all values in file are ints or doubles
		++m_nCurRecord;
		
		return readInt(); // number of 16-bit words in record content
	}
	
	
	public int[] readPoint()
		throws IOException
	{
		int nContentLength = readRecordHeader(); // number of 16-bit words in record content
		
		int nType = Utility.swap(readInt());
		nContentLength -= 2;
		
		int[] nPt = Arrays.newIntArray(2);
		nPt = Arrays.add(nPt, readCoordinate(), readCoordinate());
		
		return nPt;
	}
	
	
	public ArrayList<int[]> readPolyline()
		throws IOException
	{
		throw new IOException("not implemented");
	}
	
	
	public void readPolygon(ArrayList<int[]> oPolygons)
		throws IOException
	{
		int nContentLength = readRecordHeader(); // number of 16-bit words in record content
		
		int nType = Utility.swap(readInt());
		nContentLength -= 2;

		int nXmin = readCoordinate();
		int nYmin = readCoordinate();
		int nXmax = readCoordinate();
		int nYmax = readCoordinate();

		nContentLength -= 16;
		if (nXmax < nXmin) // swap the min and max values as needed
		{
			nXmax ^= nXmin;
			nXmin ^= nXmax;
			nXmax ^= nXmin;
		}

		if (nYmax < nYmin)
		{
			nYmax ^= nYmin;
			nYmin ^= nYmax;
			nYmax ^= nYmin;
		}
		
		
		int nNumParts = Utility.swap(readInt());
		int[] nParts = new int[nNumParts];
		nContentLength -= 2;

		int nNumPoints = Utility.swap(readInt());
		int nPointLimit = nNumPoints * 2;
		nContentLength -= 2;

		int nPartIndex = 0;
		while (nNumParts-- > 0)
		{
			nParts[nPartIndex++] = Utility.swap(readInt()) * 2;
			nContentLength -= 2;
		}
		
		nPartIndex = 0;
		int nPointIndexEnd = 0;
		int nPointIndex = 0;
		m_oHoles.clear();
		m_oOuters.clear();
		oPolygons.clear();
		int[] nRing = null;
		int nPointCountIndex = 2;
		int nBbIndex = 3;
		int nPrevX = Integer.MIN_VALUE;
		int nPrevY = Integer.MIN_VALUE;
		while (nNumPoints-- > 0)
		{
			if (nPointIndex == nPointIndexEnd)
			{
				++nPartIndex;
				if (nPartIndex == nParts.length)
					nPointIndexEnd = nPointLimit;
				else
					nPointIndexEnd = nParts[nPartIndex];
				nRing = Arrays.newIntArray(nPointIndexEnd - nPointIndex + 7);
				nRing = Arrays.add(nRing, 1); // each array will represent 1 ring
				nRing = Arrays.add(nRing, 0); // starts with zero points
				nRing = Arrays.add(nRing, new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE});
				nPrevX = Integer.MIN_VALUE;
				nPrevY = Integer.MIN_VALUE;
				oPolygons.add(nRing);
			}
			
			int nX = readCoordinate();
			int nY = readCoordinate();
			nPointIndex += 2;
			nContentLength -= 8;
			
			if (nX != nPrevX || nY != nPrevY)
			{
				nRing = Arrays.addAndUpdate(nRing, nX, nY, nBbIndex);
				++nRing[nPointCountIndex];
				nPrevX = nX;
				nPrevY = nY;
			}
		}
		
		nPartIndex = oPolygons.size();
		while (nPartIndex-- > 0)
		{
			nRing = oPolygons.remove(nPartIndex);
			if (nRing[nRing[0] - 2] == nRing[nPointCountIndex + 5] && nRing[nRing[0] - 1] == nRing[nPointCountIndex + 6]) // if the polygon is closed (it should be), remove the last point
			{
				nRing[nPointCountIndex] -= 1;
				nRing[0] -= 2;
			}
			
			if (GeoUtil.isClockwise(nRing, 2))
				m_oOuters.add(nRing);
			else
				m_oHoles.add(nRing);
		}
		
		GeoUtil.getPolygons(m_oOuters, m_oHoles);
		oPolygons.addAll(m_oOuters);
	}
	
	
	private int readCoordinate()
		throws IOException
	{
		return (int)(Utility.swapD(readLong()) * 10000000.0 + .5);
	}

}
