/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.store.grib;

import imrcp.system.Scheduling;
import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.util.concurrent.Semaphore;

/**
 * Data Representation object used to parse Section 7 of GRIB2 files that have
 * template 41 (Grid point data - Portable Network Graphics (PNG)) defined in 
 * Section 5. 
 * See https://www.w3.org/TR/PNG-Compression.html for the PNG specification
 * @author aaron.cherney
 */
public class DataRepPng extends DataRep
{
	/**
	 * Byte array used to read a row of a png file.
	 */
	private byte[] m_yLoadRow;

	
	/**
	 * PNG Input Stream that wraps the InputStream of the GRIB2 file
	 */
	private PngInStream m_oPng;

	
	/**
	 * Bytes per pixel in PNG file
	 */
	private int m_nBpp;

	
	/**
	 * Semaphore used to aid parallel processing
	 */
	private final Semaphore m_oLock = new Semaphore(1);

	
	/**
	 * Flag indicating if an error occur when reading a row of the png file
	 */
	private boolean m_bFail = false;
	
	
	/**
	 * Default constructor. Does nothing.
	 */
	DataRepPng()
	{
	}
	
	
	/**
	 * Constructs a DataRepPng from the given input stream of a GRIB2 file.
	 * @param oIn DataInputStream of the GRIB2 file. It should be ready to read 
	 * the 12th byte of  Section 5 (meaning the length (4 bytes) of the section,
	 * section number (1 byte), total data points (4 bytes), and template number 
	 * (2 bytes) has been read)
	 * @param nSecLen Length of the section in bytes
	 * @throws IOException
	 */
	public DataRepPng(DataInputStream oIn, int nSecLen)
	   throws IOException
	{
		m_dR = oIn.readFloat(); // read Section 5 information from Grib2 format
		m_dEE = Math.pow(2.0, (double)oIn.readShort());
		m_dDD = Math.pow(10.0, (double)oIn.readShort());
		m_nBits = oIn.readUnsignedByte();
		m_nFieldCode = oIn.readUnsignedByte();
	}

	
	/**
	 * Reads the data in Section 7 of the GRIB2 which represents a PNG file for
	 * this Data Representation. The data is filled into the grid represented 
	 * by fRows. Parallel processing is used to read the next row of the png file
	 * into {@link #m_yLoadRow} while the current row is been decompressed and
	 * unfiltered.
	 */
	@Override
	public void read(FilterInputStream oIn, int nSecLen, Projection oProj, float[][] fRows)
	   throws IOException, InterruptedException
	{
		PngInStream oPng = new PngInStream(oIn);
		m_oPng = oPng;
		int nBpp = oPng.m_nBPP;
		m_nBpp = nBpp;
		byte[] yTempRow;
		byte[] yCurrRow = new byte[oPng.m_nWidth * 2 + 2]; // set the length of the rows and add 2 bytes of buffer
		byte[] yPrevRow = new byte[oPng.m_nWidth * 2 + 2];
		m_yLoadRow = new byte[oPng.m_nWidth * 2 + 2];
		Scheduling oSched = Scheduling.getInstance();

		m_oLock.acquire();
		run(); // read the first row
		for (int nRow = 0; nRow < oPng.m_nHeight; nRow++)
		{
			m_oLock.acquire(); // wait until the row has finished loading
			yTempRow = m_yLoadRow; // swap references to operate on the row that just got read
			m_yLoadRow = yPrevRow; // and allow the next row to be loaded into this array

			if (nRow < oPng.m_nHeight - 1)
				oSched.execute(this); // start a new thread to read the next row
			
			if (m_bFail)
				throw new IOException("Failed to parse png");
			
			yPrevRow = yCurrRow;
			yCurrRow = yTempRow;
			float[] fRow = new float[oPng.m_nWidth];
			fRows[nRow] = fRow;

			int nFilter = yCurrRow[1]; // get the filter algorithm
			yCurrRow[1] = 0; // reset to zero for filter algorithm to work			
			int nColCount = 0;
			int i = 2;
			// switch on png filter algorithms
			switch (nFilter) // right now we have implemented BPP 1 and 2, but for BPP 1 we are forcing it to act like BBP 2, that is why there are the hard coded 2's in filtering algorithms
			{
				case 0: // None
				{
					while (i < yCurrRow.length)
						fRow[nColCount++] = decompressSimple(yCurrRow[i++], yCurrRow[i++]);
					
					break;
				}
				case 1: // Sub: to reverse the effect of Sub compute: Sub(x) + Raw(x - bpp)
				{
					while (i < yCurrRow.length)
					{
						yCurrRow[i] += yCurrRow[i - 2];
						yCurrRow[i + 1] += yCurrRow[i - 1];
						fRow[nColCount++] = decompressSimple(yCurrRow[i++], yCurrRow[i++]);
					}
					break;
				}
				case 2: // Up: to reverse the effect of Up compute: Up(x) + Prior(x)
				{
					while (i < yCurrRow.length)
					{
						yCurrRow[i] += yPrevRow[i];
						yCurrRow[i + 1] += yPrevRow[i + 1];
						fRow[nColCount++] = decompressSimple(yCurrRow[i++], yCurrRow[i++]);
					}
					break;
				}
                case 3: // Average: to reverse the effect of Average compute: Average(x) + floor((Raw(x-bpp)+Prior(x))/2)
                {
                    while (i < yCurrRow.length)
                    {
                        yCurrRow[i] += (byte)(((yCurrRow[i - 2] & 0xff) + (yPrevRow[i] & 0xff)) / 2);
                        yCurrRow[i + 1] += (byte)(((yCurrRow[i - 1] & 0xff) + (yPrevRow[i + 1] & 0xff)) / 2);
                        fRow[nColCount++] = decompressSimple(yCurrRow[i++], yCurrRow[i++]);
                    }
                    break;
                } 
				case 4: // Paeth, to reverse the effect of Paeth compute: Paeth(x) + PaethPredictor(Raw(x-bpp), Prior(x), Prior(x-bpp))
				{
					while (i < yCurrRow.length)
					{
						yCurrRow[i] += paeth(yCurrRow[i - 2], yPrevRow[i], yPrevRow[i - 2]);
						yCurrRow[i + 1] += paeth(yCurrRow[i - 1], yPrevRow[i + 1], yPrevRow[i - 1]);
						fRow[nColCount++] = decompressSimple(yCurrRow[i++], yCurrRow[i++]);
					}
					break;
				}
				default:
					throw new IOException("Invalid PNG filter algorithm " + nFilter);
			}
		}
		m_oPng.finish();
	}
	
	
	/**
	 * PaethPredictor function used in PNG filtering. The Paeth filter computes 
	 * a simple linear function of the three neighboring pixels (left, above, 
	 * upper left), then cooses as predictor the neighboring pixel closest to 
	 * the computed value. Algorithm developed by Alan W. Paeth.
	 * @param yA left
	 * @param yB above
	 * @param yC upper left
	 * @return Paeth filter predictor
	 */
	private static byte paeth(byte yA, byte yB, byte yC)
    {
        int yPa = (yB & 0xff) - (yC & 0xff); // remove sign extension
        int yPb = (yA & 0xff) - (yC & 0xff); // for integer operations
        int yPc = yPa + yPb; // a + b - 2 * c

        if (yPa < 0) // flip signs as needed
            yPa = -yPa;

        if (yPb < 0)
            yPb = -yPb;

        if (yPc < 0)
            yPc = -yPc;

        if (yPa <= yPb && yPa <= yPc)
            return yA;
        else if (yPb <= yPc)
            return yB;
        else
            return yC;
    } 


	/**
	 * Reads a row of the PNG file into {@link #m_yLoadRow}
	 */
	@Override
	public void run()
	{
		try
		{
			if (m_nBpp == 1) // force BPP 1 to act like BPP 2
			{
				m_oPng.read(m_yLoadRow, 0, m_oPng.m_nWidth + 1);
				int nIndex = m_yLoadRow.length;
				while (nIndex-- > 0)
				{
					m_yLoadRow[nIndex] = m_yLoadRow[nIndex / 2];
					m_yLoadRow[--nIndex] = 0;
				}
			}
			else // BPP 2
			{
				m_oPng.read(m_yLoadRow, 1, m_yLoadRow.length - 1);
			}
		}
		catch (Exception oEx)
		{
			m_bFail = true; // set error flag
		}
		finally
		{
			m_oLock.release();
		}
	}
}
