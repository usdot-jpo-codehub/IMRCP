package imrcp.system;

import java.util.Iterator;

/**
 * Contains methods for creating and using growable arrays. These were implemented
 * to use in place of ArrayLists for primitive types to use less memory by avoiding
 * using {@link java.lang.Integer}, {@link java.lang.Double}, and {@link java.lang.Long}
 * @author aaron.cherney
 */
public abstract class Arrays
{
	/**
	 * Default size for arrays
	 */
	private static final int DEFAULT_CAPACITY = 12;

	
	/**
	 * Default constructor. Does nothing.
	 */
	private Arrays()
	{
	}

	
	/**
	 * Gets a new growable double array
	 * 
	 * @return a new growable double array
	 */
	public static double[] newDoubleArray()
	{
		return newDoubleArray(DEFAULT_CAPACITY);
	}

	
	/**
	 * Get a new growable double array with the given initial capacity
	 * 
	 * @param nCapacity initial size of the array
	 * @return a new growable double array with the given capacity
	 */
	public static double[] newDoubleArray(int nCapacity)
	{
		double[] dVals = new double[++nCapacity]; // reserve slot for size
		dVals[0] = 1.0; // initial position is always one
		return dVals;
	}

	
	/**
	 * Gets an iterator for the source double array that copies values into the
	 * destination array, starting at the given start position of the source and 
	 * incrementing by the given step each time {@link java.util.Iterator#next()}
	 * is called.
	 * 
	 * @param dSrc source array to iterate over
	 * @param dDest destination array that gets filled with values from the source
	 * array
	 * @param nStart initial position to start at in the source array
	 * @param nStep number to increment by each time {@link java.util.Iterator#next()}
	 * is called.
	 * @return Iterator for the source array
	 */
	public static Iterator<double[]> iterator(double[] dSrc, double[] dDest, int nStart, int nStep)
	{
		return new DoubleGroupIterator(dSrc, dDest, nStart, nStep);
	}

	
	/**
	 * Grows the array, if necessary, to be able to add an additional number of 
	 * elements equal to the given demand.
	 * 
	 * @param dVals growable array to ensure the capacity on
	 * @param nDemand the number of elements that need to be added
	 * @return if the array didn't need to grow, the reference to the array that
	 * was passed into the function. If the array did need to grow, the reference
	 * to a new growable array with a larger capacity
	 */
	public static double[] ensureCapacity(double[] dVals, int nDemand)
	{
		if ((int)dVals[0] + nDemand <= dVals.length)
			return dVals; // no changes needed

		double[] dNew = new double[nDemand + (3 * dVals.length >> 1)];
		System.arraycopy(dVals, 0, dNew, 0, (int)dVals[0]);
		return dNew;
	}

	
	/**
	 * Gets the number of elements in the array aka the insertion point for the
	 * next element.
	 * 
	 * @param dVals growable array to get the size of
	 * @return number of elements in the array aka the insertion point for the
	 * next element
	 */
	public static int size(double[] dVals)
	{
		return (int)dVals[0];
	}

	
	/**
	 * Adds the given value to the given growable array and returns the reference
	 * of the array that contains all of the original values and the new value.
	 * When using this method always assign the given growable array to the return
	 * value of the function since it could be a different reference if the array
	 * needed to grow.
	 * 
	 * @param dVals growable array to add to
	 * @param d1 value to add
	 * @return The reference of the growable array after the value has been added,
	 * this could be a different reference that the one passed into the method.
	 */
	public static double[] add(double[] dVals, double d1)
	{
		dVals = ensureCapacity(dVals, 1);
		dVals[(int)dVals[0]] = d1; // current insertion position
		dVals[0] += 1.0; // update position
		return dVals;
	}

	
	/**
	 * Adds the given values to the given growable array in order and returns the reference
	 * of the array that contains all of the original values and the new values.
	 * When using this method always assign the given growable array to the return
	 * value of the function since it could be a different reference if the array
	 * needed to grow.
	 * 
	 * @param dVals growable array to add to
	 * @param d1 first value to add
	 * @param d2 second value to add
	 * @return The reference of the growable array after the values have been added,
	 * this could be a different reference that the one passed into the method.
	 */
	public static double[] add(double[] dVals, double d1, double d2)
	{
		dVals = ensureCapacity(dVals, 2);
		int nIndex = (int)dVals[0]; // current insertion position
		dVals[nIndex++] = d1;
		dVals[nIndex++] = d2;
		dVals[0] = (double)nIndex; // track insertion position
		return dVals;
	}
	
	
	/**
	 * This method is used for growable arrays that represent geometries. The positions
	 * 1,2,3,4 contain the bounding box of the geometry so the array has the
	 * format [insertion point, min x, min y, max x, max y, x0, y0, x1, y1, ...]
	 * Calls {@link #add(double[], double, double)} then updates the bounding box
	 * based off of the x and y coordinates added.
	 * 
	 * @param dVals growable array to add to
	 * @param d1 x value to add
	 * @param d2 y value to add
	 * @return The reference of the growable array after the values have been added,
	 * this could be a different reference that the one passed into the method.
	 */
	public static double[] addAndUpdate(double[] dVals, double d1, double d2)
	{
		dVals = add(dVals, d1, d2);
		if (d1 < dVals[1]) // update bounding box stored in indeces 1-4
			dVals[1] = d1;
		if (d2 < dVals[2])
			dVals[2] = d2;
		if (d1 > dVals[3])
			dVals[3] = d1;
		if (d2 > dVals[4])
			dVals[4] = d2;
		return dVals;
	}

	
	/**
	 * Copies the values in the given "more" array to the end of the given growable array 
	 * and returns the reference of the array that contains all of the original 
	 * values and the new values. When using this method always assign the given 
	 * growable array to the return value of the function since it could be a 
	 * different reference if the array needed to grow.
	 * 
	 * @param dVals growable array to add to
	 * @param dMore array of values to add to the end of growable array
	 * @return The reference of the growable array after the values have been added,
	 * this could be a different reference that the one passed into the method.
	 */
	public static double[] add(double[] dVals, double[] dMore)
	{
		dVals = ensureCapacity(dVals, dMore.length);
		int nIndex = (int)dVals[0]; // current insertion position
		System.arraycopy(dMore, 0, dVals, nIndex, dMore.length);
		dVals[0] = nIndex + dMore.length; // track insertion position
		return dVals;
	}

	
	/**
	 * Gets a new growable int array
	 * 
	 * @return a new growable int array
	 */
	public static int[] newIntArray()
	{
		return newIntArray(DEFAULT_CAPACITY);
	}

	
	/**
	 * Get a new growable int array with the given initial capacity
	 * 
	 * @param nCapacity initial size of the array
	 * @return a new growable int array with the given capacity
	 */
	public static int[] newIntArray(int nCapacity)
	{
		int[] nVals = new int[++nCapacity]; // reserve slot for size
		nVals[0] = 1; // initial position is always one
		return nVals;
	}

	
	/**
	 * Gets an iterator for the source int array that copies values into the
	 * destination array, starting at the given start position of the source and 
	 * incrementing by the given step each time {@link java.util.Iterator#next()}
	 * is called.
	 * 
	 * @param nSrc source array to iterate over
	 * @param nDest destination array that gets filled with values from the source
	 * array
	 * @param nStart initial position to start at in the source array
	 * @param nStep number to increment by each time {@link java.util.Iterator#next()}
	 * is called.
	 * @return Iterator for the source array
	 */
	public static Iterator<int[]> iterator(int[] nSrc, int[] nDest, int nStart, int nStep)
	{
		return new IntGroupIterator(nSrc, nDest, nStart, nStep);
	}

	
	/**
	 * Grows the array, if necessary, to be able to add an additional number of 
	 * elements equal to the given demand.
	 *
	 * @param nVals growable array to ensure the capacity on
	 * @param nDemand the number of elements that need to be added
	 * @return if the array didn't need to grow, the reference to the array that
	 * was passed into the function. If the array did need to grow, the reference
	 * to a new growable array with a larger capacity
	 */
	public static int[] ensureCapacity(int[] nVals, int nDemand)
	{
		if (nVals[0] + nDemand <= nVals.length)
			return nVals; // no changes needed

		int[] nNew = new int[nDemand + (3 * nVals.length >> 1)];
		System.arraycopy(nVals, 0, nNew, 0, nVals[0]);
		return nNew;
	}

	
	/**
	 * Gets the number of elements in the array aka the insertion point for the
	 * next element.
	 *
	 * @param nVals growable array to get the size of
	 * @return number of elements in the array aka the insertion point for the
	 * next element
	 */
	public static int size(int[] nVals)
	{
		return nVals[0];
	}

	
	/**
	 * Adds the given value to the given growable array and returns the reference
	 * of the array that contains all of the original values and the new value.
	 * When using this method always assign the given growable array to the return
	 * value of the function since it could be a different reference if the array
	 * needed to grow.
	 * 
	 * @param nVals growable array to add to
	 * @param n1 value to add
	 * @return The reference of the growable array after the value has been added,
	 * this could be a different reference that the one passed into the method.
	 */
	public static int[] add(int[] nVals, int n1)
	{
		nVals = ensureCapacity(nVals, 1);
		nVals[nVals[0]] = n1; // current insertion position
		++nVals[0]; // update position
		return nVals;
	}

	
	/**
	 * Adds the given values to the given growable array in order and returns the reference
	 * of the array that contains all of the original values and the new values.
	 * When using this method always assign the given growable array to the return
	 * value of the function since it could be a different reference if the array
	 * needed to grow.
	 * 
	 * @param nVals growable array to add to
	 * @param n1 first value to add
	 * @param n2 second value to add
	 * @return The reference of the growable array after the values have been added,
	 * this could be a different reference that the one passed into the method.
	 */
	public static int[] add(int[] nVals, int n1, int n2)
	{
		nVals = ensureCapacity(nVals, 2);
		int nIndex = nVals[0]; // current insertion position
		nVals[nIndex++] = n1;
		nVals[nIndex++] = n2;
		nVals[0] = nIndex; // track insertion position
		return nVals;
	}

	
	/**
	 * Copies the values in the given "more" array to the end of the given growable array 
	 * and returns the reference of the array that contains all of the original 
	 * values and the new values. When using this method always assign the given 
	 * growable array to the return value of the function since it could be a 
	 * different reference if the array needed to grow.
	 *
	 * @param nVals growable array to add to
	 * @param nMore array of values to add to the end of growable array
	 * @return The reference of the growable array after the values have been added,
	 * this could be a different reference that the one passed into the method.
	 */
	public static int[] add(int[] nVals, int[] nMore)
	{
		nVals = ensureCapacity(nVals, nMore.length);
		int nIndex = nVals[0]; // current insertion position
		System.arraycopy(nMore, 0, nVals, nIndex, nMore.length);
		nVals[0] = nIndex + nMore.length; // track insertion position
		return nVals;
	}
	
	
	/**
	 * This method is used for growable arrays that represent geometries. The positions
	 * 1,2,3,4 contain the bounding box of the geometry so the array has the
	 * format [insertion point, min x, min y, max x, max y, x0, y0, x1, y1, ...]
	 * Calls {@link #add(double[], double, double)} then updates the bounding box
	 * based off of the x and y coordinates added.
	 * 
	 * @param nVals growable array to add to
	 * @param n1 x value to add
	 * @param n2 y value to add
	 * @return The reference of the growable array after the values have been added,
	 * this could be a different reference that the one passed into the method.
	 */
	public static int[] addAndUpdate(int[] nVals, int n1, int n2)
	{
		return addAndUpdate(nVals, n1, n2, 1);
	}
	
	
	public static int[] addAndUpdate(int[] nVals, int n1, int n2, int nBbIndex)
	{
		nVals = add(nVals, n1, n2);
		updateBb(nVals, n1, n2, nBbIndex);
		return nVals;
	}
	
	
	public static void updateBb(int[] nVals, int n1, int n2, int nBbIndex)
	{
		if (n1 < nVals[nBbIndex]) 
			nVals[nBbIndex] = n1;
		if (n2 < nVals[++nBbIndex])
			nVals[nBbIndex] = n2;
		if (n1 > nVals[++nBbIndex])
			nVals[nBbIndex] = n1;
		if (n2 > nVals[++nBbIndex])
			nVals[nBbIndex] = n2;
	}
	
	
	/**
	 * Inserts the given value at the specified position in the growable array. 
	 * 
	 * @param nVals growable array to insert the value in
	 * @param n1 value to insert into the growable array
	 * @param nIndex position to insert the value at
	 * @return The reference of the growable array after the value has been inserted,
	 * this could be a different reference that the one passed into the method.
	 */
	public static int[] insert(int[] nVals, int n1, int nIndex)
	{
		nVals = ensureCapacity(nVals, 1);
        System.arraycopy(nVals, nIndex, nVals, nIndex + 1, nVals[0] - nIndex); // shift all values from the insertion point on one to the right
		nVals[nIndex] = n1;
        ++nVals[0]; // increment insertion point
		return nVals;
	}
	
	
	/**
	 * Wrapper for {@link java.util.Arrays#binarySearch(int[], int, int, int)}
	 * passing the 1 for the start and the insertion point for the end, 
	 * essentially searching the entire growable array.
	 * @param nVals growable array to search for the given value, the array must
	 * be sorted
	 * @param n1 value to search for in the array
	 * @return index of the search key, if it is contained in the array within the specified range; 
	 * otherwise, (-(insertion point) - 1). The insertion point is defined as the 
	 * point at which the key would be inserted into the array: the index of the 
	 * first element in the range greater than the key, or toIndex if all elements 
	 * in the range are less than the specified key. Note that this guarantees that 
	 * the return value will be >= 0 if and only if the key is found
	 */
	public static int binarySearch(int[] nVals, int n1)
	{
		return java.util.Arrays.binarySearch(nVals, 1, nVals[0], n1);
	}

	
	/**
	 * Base class for iterators for growable arrays. Can be implement for each
	 * type of primitive. These Iterators are flexible as the start position in 
	 * the array, number of elements to get for each iteration, and the number
	 * to increment by for each iteration can all be set upon construction.
	 */
	private static abstract class GroupIterator
	{
		/**
		 * Stores the current position in the growable array
		 */
		protected int m_nPos;

		
		/**
		 * Stores the end position of iteration 
		 */
		protected int m_nEnd;

		
		/**
		 * Amount to increment the position each iteration
		 */
		protected int m_nStep;

		
		/**
		 * Default constructor. Does nothing.
		 */
		protected GroupIterator()
		{
		}

		
		/**
		 * Constructs a GroupIterator with the given parameters
		 * @param nStart
		 * @param nLimit
		 * @param nDestSize
		 * @param nStep
		 */
		protected GroupIterator(int nStart, int nLimit, int nDestSize, int nStep)
		{
			m_nStep = nStep;
			m_nEnd = nLimit - nDestSize; // array end boundary
			m_nPos = nStart;
		}

		
		/**
		 * Indicates if the Iterator is at the finished iterating over that array
		 * or not.
		 * 
		 * @return true if the Iterator is not at finished iterating aka next()
		 * can be called, otherwise false.
		 */
		public boolean hasNext()
		{
			return (m_nPos <= m_nEnd);
		}

		
		/**
		 * NOT IMPLEMENTED
		 */
		public void remove()
		{
			throw new UnsupportedOperationException("remove");
		}
	}

	
	/**
	 * GroupIterator implementation for double[]
	 */
	private static class DoubleGroupIterator extends GroupIterator implements Iterator<double[]>
	{
		/**
		 * Array to iterate over
		 */
		private double[] m_dSrc;

		
		/**
		 * Array to copy values for each iteration into
		 */
		private double[] m_dDest;

		
		/**
		 * Default constructor. Does nothing.
		 */
		protected DoubleGroupIterator()
		{
		}

		
		/**
		 * Constructs a DoubleGroupIterator with the given parameters.
		 * 
		 * @param dSrc Array to iterate over
		 * @param dDest Array to copy values for each iteration into, this reference
		 * will be returned when {@link #next()} is called
		 * @param nStart position in dSrc to start at
		 * @param nStep number of positions to increment each time {@link #next()}
		 * is called
		 * @throws IllegalArgumentException
		 */
		public DoubleGroupIterator(double[] dSrc, double[] dDest, int nStart, int nStep)
			throws IllegalArgumentException
		{
			super(nStart, (int)dSrc[0], dDest.length, nStep);
			if (dSrc.length == 0 || dDest.length == 0 || dSrc.length < dDest.length + 1 || nStart < 0 || nStep <= 0)
				throw new IllegalArgumentException();
			m_dDest = dDest;
			m_dSrc = dSrc; // local reference to values
		}


		@Override
		public double[] next()
		{
			System.arraycopy(m_dSrc, m_nPos, m_dDest, 0, m_dDest.length);
			m_nPos += m_nStep; // shift to next group position
			return m_dDest;
		}
	}

	
	/**
	 * GroupIterator implementation for int[]
	 */
	private static class IntGroupIterator extends GroupIterator implements Iterator<int[]>
	{
		/**
		 * Array to iterate over
		 */
		private int[] m_nSrc;

		
		/**
		 * Array to copy values for each iteration into
		 */
		private int[] m_nDest;

		
		/**
		 * Default constructor. Does nothing.
		 */
		protected IntGroupIterator()
		{
		}

		
		/**
		 * Constructs a IntGroupIterator with the given parameters.
		 * 
		 * @param nSrc Array to iterate over
		 * @param nDest Array to copy values for each iteration into, this reference
		 * will be returned when {@link #next()} is called
		 * @param nStart position in dSrc to start at
		 * @param nStep number of positions to increment each time {@link #next()}
		 * is called
		 * @throws IllegalArgumentException
		 */
		public IntGroupIterator(int[] nSrc, int[] nDest, int nStart, int nStep)
			throws IllegalArgumentException
		{
			super(nStart, nSrc[0], nDest.length, nStep);
			if (nSrc.length == 0 || nDest.length == 0 || nSrc.length < nDest.length + 1 || nStart < 0 || nStep <= 0)
				throw new IllegalArgumentException();
			m_nDest = nDest;
			m_nSrc = nSrc; // local reference to values
		}


		@Override
		public int[] next()
		{
			System.arraycopy(m_nSrc, m_nPos, m_nDest, 0, m_nDest.length);
			m_nPos += m_nStep; // shift to next group position
			return m_nDest;
		}
	}
}
