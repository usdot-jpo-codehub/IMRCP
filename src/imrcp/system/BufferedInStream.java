package imrcp.system;

import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 * Provides similar functionality as {@link java.io.BufferedInputStream} without
 * some of the error checking which increases performance.
 * @author aaron.cherney
 */
public class BufferedInStream extends FilterInputStream
{
	/**
	 * Default 16k buffer size
	 */
	protected static final int BUFFER_SIZE = 16384;

	
	/**
	 * Value the position can get to before more bytes need to be read into
	 * the buffer
	 */
	private int m_nLimit;

	
	/**
	 * Current position in buffer
	 */
	private int m_nPos;

	
	/**
	 * Buffer
	 */
	private byte[] m_yBuf;

	
	/**
	 * Constructs a BufferedInStream wrapping the given InputStream, using a
	 * buffer of the given size
	 * @param oInputStream InputStream to wrap
	 * @param nSize Buffer size
	 */
	public BufferedInStream(InputStream oInputStream, int nSize)
	{
		super(oInputStream);
		m_yBuf = new byte[nSize];
	}

	
	/**
	 * Constructs a BufferedInStream wrapping the given InputStream, using the
	 * default buffer size
	 * @param oInputStream
	 */
	public BufferedInStream(InputStream oInputStream)
	{
		this(oInputStream, BUFFER_SIZE);
	}


	@Override
	public int read()
		throws IOException
	{
		if (m_nPos >= m_nLimit) // check for empty buffer
		{
			if ((m_nLimit = in.read(m_yBuf, 0, m_yBuf.length)) <= 0)
				return -1; // no bytes to read and/or read failed

			m_nPos = 0; // reset buffer read position
		}
		return ((int)m_yBuf[m_nPos++]) & 0xff;
	}


	@Override
	public int read(byte[] yBuf, int nOff, int nLen)
		throws IOException
	{
		int nStart = nOff; // save for length calculation
		while (nLen > 0) // repeat until request is fulfilled or stream end
		{
			if (m_nPos >= m_nLimit) // check for empty buffer
			{
				if ((m_nLimit = in.read(m_yBuf, 0, m_yBuf.length)) <= 0)
					return nOff - nStart; // no chars to read and/or read failed

				m_nPos = 0; // reset buffer read position
			}

			int nBytes = Math.min(nLen, m_nLimit - m_nPos); // available bytes
			System.arraycopy(m_yBuf, m_nPos, yBuf, nOff, nBytes); // copy buffer
			m_nPos += nBytes; // adjust buffer position
			nOff += nBytes; // increment dest offset
			nLen -= nBytes; // decrement remaining length
		}
		return nOff - nStart; // return copied byte count
	}


	@Override
	public long skip(long lBytes)
		throws IOException
	{
		int nAvailable = (m_nLimit - m_nPos);
		if (lBytes <= nAvailable)
		{
			m_nPos += lBytes;
			return lBytes;
		}

		m_nPos = m_nLimit; // skip buffer entirely
		return in.skip(lBytes - nAvailable) + nAvailable; // include skipped buffer
	}
}
