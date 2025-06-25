package imrcp.system;

/**
 * Standard utility functions.
 */
public abstract class Utility
{
	/**
	 * Reverses the two bytes in a short integer. The first byte of the short
	 * becomes the second byte and the second byte of the short becomes the
	 * first.
	 * <br><br>
	 * Example: swap(1) returns 256
	 * <ul style='list-style-type:none'>
	 * <li><nobr>1 => 00000000 00000001</nobr></li>
	 * <li><nobr>256 => 00000001 00000000</nobr></li>
	 * </ul>
	 * <br> and swap(257) returns 257
	 * <ul style='list-style-type:none'>
	 * <li><nobr>257 => 00000001 00000001</nobr></li>
	 * <li><nobr>257 => 00000001 00000001</nobr></li>
	 * </ul>
	 *
	 * @param rValue short integer value to have its bytes swapped
	 * @return short integer with bytes swapped
	 */
	public static short swap(short rValue)
	{
		int nByte1 = rValue & 0xff;
		int nByte2 = (rValue >> 8) & 0xff;

		return (short)(nByte1 << 8 | nByte2 << 0);
	}


	/**
	 * Reverses the four bytes in an integer. The first byte becomes the fourth
	 * byte, the second byte becomes the third byte, the third byte becomes the
	 * second byte and the fourth byte becomes the first byte.
	 * <br><br>
	 * Example: swap(134217728) returns 16909320
	 * <ul style='list-style-type:none'>
	 * <li><nobr>134217728 => 00001000 00000100 00000010 00000001</nobr></li>
	 * <li><nobr>16777216 => 00000001 00000010 00000100 00001000</nobr></li>
	 * </ul>
	 *
	 * @param nValue integer value to have its bytes swapped
	 * @return integer with bytes swapped
	 */
	public static int swap(int nValue)
	{
		return ((nValue << 24)
		   + (nValue << 8 & 0x00FF0000)
		   + (nValue >>> 8 & 0x0000FF00)
		   + (nValue >>> 24));
	}


	/**
	 * Reverses the eight bytes in a long integer. The first byte becomes the
	 * eighth byte, the second byte becomes the seventh byte, etc...
	 * <br><br>
	 * Example: swap(72057594037927936) returns 1
	 * <ul style='list-style-type:none'>
	 * <li><nobr>134217728 => 00000001 00000000 00000000 00000000 00000000
	 * 00000000 00000000 00000000</nobr></li>
	 * <li><nobr>16777216 => 00000000 00000000 00000000 00000000 00000000
	 * 00000000 00000000 00000001</nobr></li>
	 * </ul>
	 *
	 * @param lValue long integer value to have its bytes swapped
	 * @return long integer with bytes swapped
	 */
	public static long swap(long lValue)
	{
		long lByte1 = (lValue >> 0) & 0xff;
		long lByte2 = (lValue >> 8) & 0xff;
		long lByte3 = (lValue >> 16) & 0xff;
		long lByte4 = (lValue >> 24) & 0xff;
		long lByte5 = (lValue >> 32) & 0xff;
		long lByte6 = (lValue >> 40) & 0xff;
		long lByte7 = (lValue >> 48) & 0xff;
		long lByte8 = (lValue >> 56) & 0xff;

		return (lByte1 << 56 | lByte2 << 48 | lByte3 << 40 | lByte4 << 32
		   | lByte5 << 24 | lByte6 << 16 | lByte7 << 8 | lByte8 << 0);
	}


	/**
	 * Reverses the four bytes in a floating point number. The first byte
	 * becomes the fourth byte, the second byte becomes the third byte, the
	 * third byte becomes the second byte and the fourth byte becomes the first
	 * byte.
	 *
	 * @param fValue floating point value to have its bytes swapped
	 * @return long integer with bytes swapped
	 */
	public static float swap(float fValue)
	{
		return Float.intBitsToFloat(swap(Float.floatToIntBits(fValue)));
	}


	/**
	 * Reverses the eight bytes in a double precision number. The first byte
	 * becomes the eighth byte, the second byte becomes the seventh byte, etc...
	 *
	 * @param dValue double precision value to have its bytes swapped
	 * @return long integer with bytes swapped
	 */
	public static double swap(double dValue)
	{
		return Double.longBitsToDouble(swap(Double.doubleToLongBits(dValue)));
	}


	/**
	 * 
	 * @param lValue
	 * @return
	 */
	public static double swapD(long lValue)
	{
		return Double.longBitsToDouble(swap(lValue));
	}


	/**
	 * Returns the unsigned integer value of a byte.
	 *
	 * @param yValue the byte
	 * @return the unsigned integer value of the byte
	 */
	public static int unsignByte(byte yValue)
	{
		int nValue = yValue;
		if (nValue < 0)
			nValue += 256;

		return nValue;
	}


	/**
	 * Determines the next smallest integer value.
	 *
	 * @param nValue integer value to floor
	 * @param nPrecision
	 * @return next smallest integer to the parameter integer
	 */
	public static int floor(int nValue, int nPrecision)
	{
		// this integer flooring method returns the next smallest integer
		int nFlooredValue = nValue / nPrecision * nPrecision;

		// correct for negative numbers 
		// ensure the value was not previously floored or this will return the wrong result
		if (nValue < 0 && nFlooredValue != nValue)
			nFlooredValue -= nPrecision;

		return nFlooredValue;
	}


	/**
	 * Determines if the specified point is within the specified rectangular
	 * region.
	 *
	 * @param nX x coordinate of point
	 * @param nY y coordinate of point
	 * @param nTop y value of the top of the region
	 * @param nRight x value of the right side of the region
	 * @param nBottom y value of the botton of the region
	 * @param nLeft x value of the left side of the region
	 * @param nTolerance coordinate thickness of the rectangular region
	 * @return true if the points are within or on the rectangular region, false
	 * otherwise
	 */
	public static boolean isPointInsideRegion(int nX, int nY, int nTop, int nRight, int nBottom, int nLeft, int nTolerance)
	{
		// swap the bounds as needed
		int nTemp = 0;

		if (nRight < nLeft)
		{
			nTemp = nRight;
			nRight = nLeft;
			nLeft = nTemp;
		}

		if (nTop < nBottom)
		{
			nTemp = nTop;
			nTop = nBottom;
			nBottom = nTemp;
		}

		// adjust the bounds to include the tolerance
		if (nTolerance != 0)
		{
			nTop += nTolerance;
			nRight += nTolerance;
			nBottom -= nTolerance;
			nLeft -= nTolerance;
		}

		return (nY <= nTop && nX <= nRight && nY >= nBottom && nX >= nLeft);
	}


	/**
	 * Provides information on the currently running thread. Prints the current
	 * system time in milliseconds, the parameter value, and the currently
	 * running thread identifier.
	 *
	 * @param nValue identifing value
	 */
	public static synchronized void timing(int nValue)
	{
		System.out.print(System.currentTimeMillis());
		System.out.print(" ");
		System.out.print(nValue);
		System.out.print(" ");
		System.out.println(Thread.currentThread().getId());
	}
}
