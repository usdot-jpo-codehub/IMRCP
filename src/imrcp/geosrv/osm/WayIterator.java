/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.geosrv.osm;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Object used to iterate through the nodes of a roadway segment, where each
 * iteration provides a line segment define by the current point and the next point.
 * @author aaron.cherney
 */
public class WayIterator implements Iterator<int[]>
{
	/**
	 * Stores the position in the node list
	 */
	private int m_nPos;

	
	/**
	 * Stores the end position of iteration
	 */
	private int m_nEnd;

	
	/**
	 * Local copy of the OsmNodes that make up the OsmWay being iterated through
	 */
	private ArrayList<OsmNode> m_oPoints;

	
	/**
	 * Stores the points of the current line segment
	 */
	private final int[] m_nLine = new int[4];

	
	/**
	 * Default Constructor. Does nothing.
	 */
	private WayIterator()
	{
	}

	
	/**
	 * Constructs a WayIterator for the given list of nodes
	 * @param oNodes
	 */
	WayIterator(ArrayList<OsmNode> oNodes)
	{
		m_oPoints = oNodes; // local immutable copy of road line segments
		m_nEnd = oNodes.size() - 1; // line segment end boundary
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
		OsmNode o1 = m_oPoints.get(m_nPos++); // shift line to next point
		OsmNode o2 = m_oPoints.get(m_nPos); 
		m_nLine[0] = o1.m_nLon;
		m_nLine[1] = o1.m_nLat;
		m_nLine[2] = o2.m_nLon;
		m_nLine[3] = o2.m_nLat;
		
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