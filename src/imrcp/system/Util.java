package imrcp.system;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

/**
 * Contains utility methods for half-precision floating point numbers, getting 
 * byte array from primitive numbers, and reading the last line of a file.
 * @author aaron.cherney
 */
public class Util
{
	/**
	 * Lookup array for half-precision floating point values
	 */
	private static final float[] SHORT_FLOAT = new float[65536];

	
	static
	{
		for (int nIndex = 0; nIndex < 65536; nIndex++) // fill lookup table
			SHORT_FLOAT[nIndex] = fromHpfp(nIndex); // half-precision floating point
	}

	
	/**
	 * Gets the last line of the text file with the given path.
	 * 
	 * @param sFilename path to the file to open
	 * @return the last line of the file
	 * @throws Exception
	 */
	public static String getLastLineOfFile(String sFilename) throws Exception
	{
		File oFile = new File(sFilename);
		if (!oFile.exists())
			return null;
		try (RandomAccessFile oIn = new RandomAccessFile(sFilename, "r")) // open for read
		{
			long lLength = oIn.length() - 1; // start at the end of the file
			StringBuilder sBuffer = new StringBuilder();

			for (long lPointer = lLength; lPointer != -1; lPointer--) // iterate backwards
			{
				oIn.seek(lPointer);
				int nByte = oIn.readByte();

				if (nByte == 0xA) // check for new line character
				{
					if (lPointer == lLength) // check for new line at the end of the file
						continue; // skip it
					break;
				}
				else if (nByte == 0xD) // check for carriage return
				{
					if (lPointer == lLength - 1) // check for carriage return at end of the file
						continue; // skip it
					break;
				}
				sBuffer.append((char)nByte);
			}
			return sBuffer.reverse().toString();
		}
	}
	
	
	public static String getLastLinesOfFile(String sFilename, int nLines)
	{
		try
		{
			if (!Files.exists(Paths.get(sFilename)) || nLines < 1)
				return null;
			int nCount = 0;
			try (RandomAccessFile oIn = new RandomAccessFile(sFilename, "r")) // open for read
			{
				long lLength = oIn.length() - 1; // start at the end of the file
				StringBuilder sBuffer = new StringBuilder();
				for (long lPointer = lLength; lPointer != -1; lPointer--) // iterate backwards
				{
					oIn.seek(lPointer);
					int nByte = oIn.readByte();

					if (nByte == 0xA) // check for new line character
					{
						if (lPointer == lLength) // check for new line at the end of the file
							continue; // skip it
						if (++nCount == nLines)
							break;
					}
					else if (nByte == 0xD) // check for carriage return
					{
						continue;
					}
					sBuffer.append((char)nByte);
				}
				return sBuffer.reverse().toString();
			}
		}
		catch (IOException oEx)
		{
			return null;
		}
	}
	
	
	public static int getUpperNybble(int nByte, boolean bSigned)
	{
		int nNybble = nByte >> 4 & 0b00001111;
		if (bSigned && nNybble > 7)
			nNybble -= 16;
		
		return nNybble;
	}
	
	public static int getLowerNybble(int nByte, boolean bSigned)
	{
		int nNybble = nByte & 0b00001111;
		if (bSigned && nNybble > 7)
			nNybble -= 16;
		
		return nNybble;
	}
	
	
	public static int combineNybbles(int nMSN, int nLSN)
	{
		nMSN = nMSN & 0b1111;
		nLSN = nLSN & 0b1111;
		int nByte = nMSN << 4;
		nByte = nByte | nLSN;
		
		return nByte;
	}
	
	/**
	 * Fills the given byte array with the bytes of the given long.
	 * 
	 * @param lLong The long to convert to bytes
	 * @param yBytes byte array to be filled with the bytes of the long.
	 * @return the reference of the byte array passed into the function. It is 
	 * filled with the bytes that make up the long.
	 */
	public static byte[] longToBytes(long lLong, byte[] yBytes)
	{
		yBytes[0] = (byte)(lLong >> 56);
		yBytes[1] = (byte)(lLong >> 48);
		yBytes[2] = (byte)(lLong >> 40);
		yBytes[3] = (byte)(lLong >> 32);
		yBytes[4] = (byte)(lLong >> 24);
		yBytes[5] = (byte)(lLong >> 16);
		yBytes[6] = (byte)(lLong >> 8);
		yBytes[7] = (byte)lLong;
		
		return yBytes;
	}
	
	
	/**
	 * Converts the bytes in the array into a long.
	 * 
	 * @param yBytes the bytes defining a long
	 * @return a long defined by the bytes in the array
	 */
	public static long bytesToLong(byte[] yBytes)
	{
		return (((long)yBytes[0] << 56) +
                ((long)(yBytes[1] & 255) << 48) +
                ((long)(yBytes[2] & 255) << 40) +
                ((long)(yBytes[3] & 255) << 32) +
                ((long)(yBytes[4] & 255) << 24) +
                ((yBytes[5] & 255) << 16) +
                ((yBytes[6] & 255) <<  8) +
                ((yBytes[7] & 255) <<  0));
	}

	
	/**
	 * Convenience function used to wrap the given OutputStream with a GZIPOutputStream
	 * using level 9 (best compression, most processing) compression
	 * @param oOs OutputStream to wrap
	 * @return GZIPOutputStream that is wrapping the OutputStream
	 * @throws IOException
	 */
	public static GZIPOutputStream getGZIPOutputStream(OutputStream oOs)
		throws IOException
	{
		return new GZIPOutputStream(oOs) {{def.setLevel(Deflater.BEST_COMPRESSION);}};
	}
	
	
	/**
	 * Looks up the half-precision floating point number associated with the given
	 * short.
	 * 
	 * @param hbits  half-precision floating point short value
	 * @return half-precision floatign point number associated with the given
	 * short
	 */
	public static float toFloat(int hbits)
	{
		return SHORT_FLOAT[hbits];
	}


	/**
	 * Calculates the half-precision floating point number for the given integer
	 * which is treated as a short as the higher 16 bits are ignored.
	 * @param hbits short to convert to half-precision floating point
	 * @return
	 */
	public static float fromHpfp(int hbits)
	{
			int mant = hbits & 0x03ff;            // 10 bits mantissa
			int exp =  hbits & 0x7c00;            // 5 bits exponent
			if( exp == 0x7c00 )                   // NaN/Inf
					exp = 0x3fc00;                    // -> NaN/Inf
			else if( exp != 0 )                   // normalized value
			{
					exp += 0x1c000;                   // exp - 15 + 127
					if( mant == 0 && exp > 0x1c400 )  // smooth transition
							return Float.intBitsToFloat( ( hbits & 0x8000 ) << 16
																							| exp << 13 | 0x3ff );
			}
			else if( mant != 0 )                  // && exp==0 -> subnormal
			{
					exp = 0x1c400;                    // make it normal
					do {
							mant <<= 1;                   // mantissa * 2
							exp -= 0x400;                 // decrease exp by 1
					} while( ( mant & 0x400 ) == 0 ); // while not normal
					mant &= 0x3ff;                    // discard subnormal bit
			}                                     // else +/-0 -> +/-0
			return Float.intBitsToFloat(          // combine all parts
					( hbits & 0x8000 ) << 16          // sign  << ( 31 - 15 )
					| ( exp | mant ) << 13 );         // value << ( 23 - 10 )
	}


	/**
	 * Converts the given float to a short value that acts as a look up index for
	 * {@link #SHORT_FLOAT}
	 * 
	 * @param fval the float to convert
	 * @return The short value associated with the given float as a half-precision floating point number.
	 * All higher 16 bits as 0 for all results
	 */
	public static int toHpfp(float fval)
	{
			int fbits = Float.floatToIntBits( fval );
			int sign = fbits >>> 16 & 0x8000;          // sign only
			int val = ( fbits & 0x7fffffff ) + 0x1000; // rounded value

			if( val >= 0x47800000 )               // might be or become NaN/Inf
			{                                     // avoid Inf due to rounding
					if( ( fbits & 0x7fffffff ) >= 0x47800000 )
					{                                 // is or must become NaN/Inf
							if( val < 0x7f800000 )        // was value but too large
									return sign | 0x7c00;     // make it +/-Inf
							return sign | 0x7c00 |        // remains +/-Inf or NaN
									( fbits & 0x007fffff ) >>> 13; // keep NaN (and Inf) bits
					}
					return sign | 0x7bff;             // unrounded not quite Inf
			}
			if( val >= 0x38800000 )               // remains normalized value
					return sign | val - 0x38000000 >>> 13; // exp - 127 + 15
			if( val < 0x33000000 )                // too small for subnormal
					return sign;                      // becomes +/-0
			val = ( fbits & 0x7fffffff ) >>> 23;  // tmp exp for subnormal calc
			return sign | ( ( fbits & 0x7fffff | 0x800000 ) // add subnormal bit
					 + ( 0x800000 >>> val - 102 )     // round depending on cut off
				>>> 126 - val );   // div by 2^(1-(exp-127+15)) and >> 13 | exp=0
	}
}
