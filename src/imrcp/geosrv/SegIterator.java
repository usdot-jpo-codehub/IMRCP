package imrcp.geosrv;

import java.util.Iterator;

/**
 * Object used to iterate over an int array that represents a roadway segment
 * (linestring) by line segment.
 * @author aaron.cherney
 */
public class SegIterator implements Iterator<int[]>
{
	/**
	 * Stores the position in the array
	 */
	private int m_nPos;

	
	/**
	 * Stores the end position of iteration
	 */
	private int m_nEnd;

	
	/**
	 * Local reference of the linestring
	 */
	private int[] m_nPoints;

	
	/**
	 * Stores the points of the current line segment
	 */
	private final int[] m_nLine = new int[4];

	
	/**
	 * Default constructor. Does nothing.
	 */
	private SegIterator()
	{
	}

	
	/**
	 * Constructs a SegIterator for the given array of points
	 * @param nPoints array of points that represents a line string
	 */
	SegIterator(int[] nPoints)
	{
		m_nPoints = nPoints; // local immutable copy of road line segments
		m_nEnd = nPoints.length - 2; // line segment end boundary
	}


	
	@Override
	public boolean hasNext()
	{
		return (m_nPos < m_nEnd);
	}


	
	/**
	 * Copies the points of the next line segment into {@link #m_nLine} and
	 * returns it reference.
	 */
	@Override
	public int[] next()
	{
		System.arraycopy(m_nPoints, m_nPos, m_nLine, 0, m_nLine.length);
		m_nPos += 2; // shift line to next point
		return m_nLine;
	}


	
	/**
	 * NOT IMPLEMENTED
	 */
	@Override
	public void remove()
	{
		throw new UnsupportedOperationException("remove");
	}
}
