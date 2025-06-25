/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.store.grib;

import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * FilterInputStream used to parse PNG datastreams
 * @author aaron.cherney
 */
public class PngInStream extends FilterInputStream
{
	/**
	 * Marker for IDAT section
	 */
	public final static int IDAT = 0x49444154;

	
	/**
	 * Marker for IEND section
	 */
	public final static int IEND = 0x49454e44;

	
	/**
	 * Marker for IHDR section
	 */
	public final static int IHDR = 0x49484452;

	
	/**
	 * The first eight bytes of a PNG datastream
	 */
	public final static long PNGHDR = -8552249625308161526L; // 0x89504E470D0A1A0A

	
	/**
	 * Width of image in pixels
	 */
	int m_nWidth;

	
	/**
	 * Height of image in pixels
	 */
	int m_nHeight;

	
	/**
	 * Bit depth. Can be 1, 2, 4, 8, or 16
	 */
	private int m_nDepth;

	
	/**
	 * Color type that defines the PNG image type. 
	 * 0 - greyscale
	 * 2 - Truecolor
	 * 3 - Indexed-color
	 * 4 - Greyscale with alpha
	 * 6 - Truecolor with alpha
	 */
	private int m_nColor;

	
	/**
	 * Compression method. Only compression method 0 (deflate/inflate compression
	 * with a sliding window of at most 32768 bytes) is defined by the W3C standard
	 */
	private int m_nCompMethod;

	
	/**
	 * Filter method. Only filter method 0 (adaptive filtering with five basic 
	 * filter types) is defined by the W3C standard.
	 */
	private int m_nFilterMethod;

	
	/**
	 * Interlace method
	 */
	private int m_nInterlaceMethod;

	
	/**
	 * Bytes Per Pixel
	 */
	int m_nBPP;

	
	/**
	 * Stores the bytes of the IDAT section
	 */
	private byte[] m_yIDAT;

	
	/**
	 * DataInputStream used to wrap the InputStream of the PNG datastream
	 */
	private DataInputStream m_oDis;

	
	/**
	 * Stores the type of the current chunk
	 */
	private int m_nChunkType;

	
	/**
	 * Stores the length of the current chunk
	 */
	private int m_nChunkLength;

	
	/**
	 * Inflater used to decompress the data
	 */
	private final Inflater m_oInf = new Inflater();

	
	/**
	 * Byte array used to read a single byte from the datastream
	 */
	private final byte[] m_yInfBuf = new byte[1];
	
	
	/**
	 * Calls {@link #PngInStream(java.io.DataInputStream)} by wrapping the given 
	 * InputStream with a DataInputStream.
	 * @param oIn InputStream representing the start of a PNG datastream
	 * @throws IOException
	 */
	public PngInStream(InputStream oIn) 
	   throws IOException
	{
		this(new DataInputStream(oIn));
	}
	
	/**
	 * Constructs a new PngInStream with the given DataInputStream. This function
	 * prepares the PngInStream to be ready to read the image line by line. To 
	 * do this the IHDR section is read and then the IDAT section is read into
	 * {@link #m_yIDAT} and set as the input of {@link #m_oInf}
	 * @param oIn DataInputStream wrapping a PNG datastream
	 * @throws IOException
	 */
	public PngInStream(DataInputStream oIn) 
	   throws IOException
	{
		super(oIn);
		m_oDis = oIn;
		if (oIn.readLong() != PNGHDR) // read file signature
			throw new IOException("Invalid PNG file signature");
		
		oIn.skipBytes(4); // skip length, which does not include these 4 bytes, chunk type, and CRC
		if ((m_nChunkType = oIn.readInt()) != IHDR) // read chunk type
			throw new IOException("Invalid first PNG chunk type");
		
		m_nWidth = oIn.readInt();
		m_nHeight = oIn.readInt();
		m_nDepth = oIn.readByte();
		m_nColor = oIn.readByte();
		m_nCompMethod = oIn.readByte();
		m_nFilterMethod = oIn.readByte();
		m_nInterlaceMethod = oIn.readByte();
		m_nBPP = m_nDepth / 8;
		oIn.skipBytes(4); // skip crc

		
		while (m_nChunkType != IDAT) // skip until the IDAT section
		{
			m_nChunkLength = oIn.readInt();
			m_nChunkType = oIn.readInt();
			if (m_nChunkType != IDAT)
				oIn.skipBytes(m_nChunkLength + 4); // skip chunk data and crc
		}
		m_yIDAT = new byte[m_nChunkLength];
		super.read(m_yIDAT, 0, m_nChunkLength); // read the IDAT chunk
		m_oDis.skipBytes(4);
		m_oInf.setInput(m_yIDAT);
	}
	
	
	/**
	 * Reads bytes from {@link #m_oInf} into yBuf
	 */
	@Override
	public int read(byte[] yBuf, int nOff, int nLen)
	   throws IOException
	{
		int nStart = nOff; // save for length calculation
		while (nLen > 0) // repeat until request is fulfilled or stream end
		{
			if (m_oInf.needsInput()) // check for empty buffer
			{
				if (readIDAT() == 0)
					return nOff - nStart; // no chars to read and/or read failed
			}

			int nBytes = 0;
			try
			{
				nBytes = m_oInf.inflate(yBuf, nOff, nLen);
			}
			catch (DataFormatException oDfe)
			{
				throw new IOException(oDfe);
			}
			
			nOff += nBytes; // increment dest offset
			nLen -= nBytes; // decrement remaining length
		}
		return nOff - nStart; // return copied byte count
	}

	
	/**
	 * Wrapper for {@link PngInStream#read(byte[], int, int)} passing {@link #m_yInfBuf}
	 * as the buffer meaning 1 byte from {@link #m_oInf} is read into {@link #m_yInfBuf}
	 */
	@Override
	public int read() 
	   throws IOException
	{
		return read(m_yInfBuf, 0, 1) == -1 ? -1 : m_yInfBuf[0] & 0xff;
	}

	/**
	 * Attempts to read the IDAT chunk of the PNG datastream into {@link #m_yIDAT}
	 * @return the length of the chunk in bytes.
	 * @throws IOException
	 */
	private int readIDAT() 
	   throws IOException
	{
		if (m_nChunkType != IDAT)
			return 0;
		
		m_nChunkLength = m_oDis.readInt();
		if ((m_nChunkType = m_oDis.readInt()) == IDAT)
		{
			if (m_yIDAT.length != m_nChunkLength)
				m_yIDAT = new byte[m_nChunkLength];
			
			super.read(m_yIDAT, 0, m_nChunkLength);
			m_oInf.setInput(m_yIDAT);
			m_oDis.skipBytes(4); // skip crc
			return m_nChunkLength;
		}
		else // read until the end of png file, ignoring any ancillary chunks
		{
			m_oDis.skipBytes(m_nChunkLength + 4); // skip chunk data and crc
			while (m_nChunkType != IEND)
			{
				m_nChunkLength = m_oDis.readInt();
				m_nChunkType = m_oDis.readInt();
				m_oDis.skipBytes(m_nChunkLength + 4); // skip chunk data and crc
			}
			return 0;
		}
	}
	
	/**
	 * Reads to the end of the PNG datastream.
	 * @return 0
	 * @throws IOException
	 */
	public int finish()
	   throws IOException
	{
		do // must call this function when at the beginning of a chunk
		{
			m_nChunkLength = m_oDis.readInt();
			m_nChunkType = m_oDis.readInt();
			m_oDis.skipBytes(m_nChunkLength + 4); // skip chunk data 
		} while (m_nChunkType != IEND);
		
		return 0;
	}
}
