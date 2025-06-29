/*
 * Copyright 2018 Synesis-Partners.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package imrcp.geosrv.osm;

import imrcp.geosrv.WayNetworks;
import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.WaySnapInfo;
import imrcp.geosrv.WayNetworks.WayMetadata;
import imrcp.system.Arrays;
import imrcp.system.Id;
import imrcp.system.Introsort;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

/**
 * This class represents roadway segments in the system which were originally
 * derived from Open Street Map Way objects.
 * @author aaron.cherney
 */
public class OsmWay extends OsmObject
{
	/**
	 * Enumeration for {@link #m_yLinkType} indicating this Way's type has not
	 * been set
	 */
	public static final byte NOTSET = -1;

	
	/**
	 * Enumeration for {@link #m_yLinkType} indicating this Way's type is not
	 * a ramp
	 */
	public static final byte NOTRAMP = 0;

	
	/**
	 * Enumeration for {@link #m_yLinkType} indicating this Way's type is a ramp
	 */
	public static final byte RAMP = 1;

	
	/**
	 * Enumeration for {@link #m_yLinkType} indicating this Way's type is a 
	 * connector.
	 */
	public static final byte CONNECTOR = 2;
	
	
	/**
	 * The list of Nodes that define the geometry and direction of this Way
	 */
	public ArrayList<OsmNode> m_oNodes;

	
	/**
	 * Maximum latitude in decimal degrees scaled to 7 decimal places
	 */
	public int m_nMaxLat = Integer.MIN_VALUE;

	
	/**
	 * Maximum longitude in decimal degrees scaled to 7 decimal places
	 */
	public int m_nMaxLon = Integer.MIN_VALUE;

	
	/**
	 * Minimum latitude in decimal degrees scaled to 7 decimal places
	 */
	public int m_nMinLat = Integer.MAX_VALUE;

	
	/**
	 * Minimum longitude in decimal degrees scaled to 7 decimal places
	 */
	public int m_nMinLon = Integer.MAX_VALUE;

	
	/**
	 * Latitude in decimal degrees scaled to 7 decimal places of the midpoint 
	 * of the Way
	 */
	public int m_nMidLat = Integer.MIN_VALUE;

	
	/**
	 * Longitude in decimal degrees scaled to 7 decimal places of the midpoint 
	 * of the Way
	 */
	public int m_nMidLon = Integer.MIN_VALUE;

	
	/**
	 * Elevation of the Way at it's midpoint in meters
	 */
	public short m_tElev = Short.MIN_VALUE;

	
	/**
	 * Name of the road this Way represents
	 */
	public String m_sName = "";

	
	/**
	 * Flag indicating if the road is a bridge
	 */
	public boolean m_bBridge = false;

	
	/**
	 * Enumerated value that tells what type of ramp the Way is, used mainly for
	 * Ways with a highway tag ending in "_link"
	 */
	public byte m_yLinkType = NOTSET;

	
	/**
	 * Flag indicating if this Way has been traversed during the determine ramps
	 * algorithm
	 */
	public boolean m_bTraversed = false;

	
	/**
	 * Flag indicating if this Way has been separated during the separate algorithm
	 */
	public boolean m_bSeparated = false;

	
	/**
	 * The Way generated by separating this Way in the positive tangent direction
	 */
	public OsmWay m_oPosSep = null;

	
	/**
	 * The Way generated by separating this Way in the negative tangent direction
	 */
	public OsmWay m_oNegSep = null;

	
	/**
	 * Flag indicating if the first node or end node should be used during parts
	 * of the separate algorithm
	 */
	public boolean m_bUseStart = true;

	
	/**
	 * Reference of the original start node of this Way, used during the separate
	 * algorithm
	 */
	public OsmNode m_oOriginalStart = null;

	
	/**
	 * Reference of the original end node of this Way, used during the separate
	 * algorithm
	 */
	public OsmNode m_oOriginalEnd = null;

	
	/**
	 * Length of the Way in decimal degrees
	 */
	public double m_dLength;
	
	
	public boolean m_bInUse = false;

	
	/**
	 * Compares OsmWays by their Id
	 */
	public static Comparator<OsmWay> WAYBYTEID = (OsmWay o1, OsmWay o2) -> 
	{
		return Id.COMPARATOR.compare(o1.m_oId, o2.m_oId);
	};
	
	
	/**
	 * Default constructor. Sets {@link #m_oNodes} to a new ArrayList
	 */
	public OsmWay()
	{
		m_oNodes = new ArrayList();
	}
	
	
	/**
	 * Constructs an OsmWay with the given capacity for {@link #m_oNodes}
	 * @param nNodeCap initial capacity for the node list
	 */
	public OsmWay(int nNodeCap)
	{
		m_oNodes = new ArrayList(nNodeCap);
	}
	
	
	/**
	 * Constructs an OsmWay by reading its definition from the given DataInputStream
	 * which is wrapping an InputStream to an IMRCP OSM binary file. 
	 * 
	 * @param oIn DataInputStream wrapping the InputStream of the IMRCP OSM binary file
	 * @param oPool list representing the StringPool of the file
	 * @param oNodes list of all the node in the IMRCP OSM binary file, they 
	 * should be sorted by {@link OsmObject#FPCOMP}
	 * @param nFp current position in the file, gets updated as bytes are read
	 * @throws IOException
	 */
	public OsmWay(DataInputStream oIn, ArrayList<String> oPool, ArrayList<OsmNode> oNodes, int[] nFp)
	   throws IOException
	{
		int nTemp = nFp[0]; // make local copy
		m_nFp = nTemp;
		
		int nLimit = oIn.readInt(); // read number of nodes
		m_oNodes = new ArrayList(nLimit);
		nTemp += 4;
		OsmNode oSearch = new OsmNode();
		for (int nIndex = 0; nIndex < nLimit; nIndex++)
		{
			oSearch.m_nFp = oIn.readInt();
			nTemp += 4;
			OsmNode oNode = oNodes.get(Collections.binarySearch(oNodes, oSearch, OsmObject.FPCOMP));
			if (oNode.m_nLon < m_nMinLon)
				m_nMinLon = oNode.m_nLon;
			if (oNode.m_nLon > m_nMaxLon)
				m_nMaxLon = oNode.m_nLon;
			if (oNode.m_nLat < m_nMinLat)
				m_nMinLat = oNode.m_nLat;
			if (oNode.m_nLat > m_nMaxLat)
				m_nMaxLat = oNode.m_nLat;
			m_oNodes.add(oNode);
		}
		
		nLimit = oIn.readInt(); // read number of tags
		nTemp += 4;
		for (int nIndex = 0; nIndex < nLimit; nIndex++)
		{
			put(oPool.get(oIn.readInt()), oPool.get(oIn.readInt()));
			nTemp += 8;
		}
		
		if (!containsKey("highway"))
			put("highway", "unclassified");
		generateId();
		setBridge();
		calcMidpoint();
		setName();
		nFp[0] = nTemp;
	}
	
	
	/**
	 * Generates a unique Id for the Way by adding all of the nodes of the Way
	 * to a growable int array and calling {@link Id#Id(int, int[])}
	 * @throws IOException
	 */
	public void generateId()
	   throws IOException
	{
		int[] nArr = Arrays.newIntArray(m_oNodes.size() * 2);
		for (OsmNode oNode : m_oNodes)
			nArr = Arrays.add(nArr, oNode.m_nLon, oNode.m_nLat);
		
		m_oId = new Id(Id.SEGMENT, nArr);
	}
	
	
	/**
	 * Constructs an OsmWay with the given OSM long id
	 * @param lId
	 */
	public OsmWay(long lId)
	{
		super(lId);
		m_oNodes = new ArrayList();
	}

	
	/**
	 * Attempts to get the name of the road by checking the ref and name tags 
	 * from the OSM definition
	 */
	public void setName()
	{
		String sName = get("ref");
		if (sName == null || sName.isEmpty())
			sName = get("name");
		if (sName == null)
			sName = "";
		m_sName = sName;
	}
	
	
	/**
	 * Sets the bridge flag by reading the bridge tag from the OSM definition
	 */
	public void setBridge()
	{
		String sBridge = get("bridge");
		m_bBridge = sBridge != null && sBridge.compareTo("no") != 0;
	}

	
	/**
	 * For each node that makes up this Way, ensures that the node contains this
	 * Way in its reference list, {@link OsmNode#m_oRefs}.
	 */
	public void updateRefs()
	{
		for (OsmNode oNode : m_oNodes)
		{
			Introsort.usort(oNode.m_oRefs, OsmWay.WAYBYTEID);
			int nIndex = Collections.binarySearch(oNode.m_oRefs, this, WAYBYTEID);
			if (nIndex < 0)
				oNode.m_oRefs.add(~nIndex, this);
		}
	}
	
	
	/**
	 * For each node that makes up this Way, remove this Way from its reference
	 * list, {@link OsmNode#m_oRefs}
	 */
	public void removeRefs()
	{
		for (OsmNode oNode : m_oNodes)
		{
			Introsort.usort(oNode.m_oRefs, OsmWay.WAYBYTEID);
			int nIndex = Collections.binarySearch(oNode.m_oRefs, this, WAYBYTEID);
			if (nIndex >= 0)
				oNode.m_oRefs.remove(nIndex);
		}
	}

	
	/**
	 * Finds the midpoint of the polyline that makes up this Way. The length
	 * of the Way is also set in this method.
	 */
	public void calcMidpoint()
	{
		double dLen = 0.0; // accumulate total length
		double[] dLens = new double[m_oNodes.size() - 1]; // individual segment lengths
		OsmNode[] oSeg = new OsmNode[2];
		for (int nIndex = 0; nIndex < m_oNodes.size() - 1; nIndex++) // derive midpoint
		{
			oSeg[0] = m_oNodes.get(nIndex);
			oSeg[1] = m_oNodes.get(nIndex + 1);
			//m_oWayNodes.subList(nNodeIndex, nNodeIndex + 2).toArray(oSeg);
			double dDeltaX = oSeg[1].m_nLon - oSeg[0].m_nLon;
			double dDeltaY = oSeg[1].m_nLat - oSeg[0].m_nLat;
			double dSegLen = Math.sqrt(dDeltaX * dDeltaX + dDeltaY * dDeltaY);
			dLen += dSegLen;
			dLens[nIndex] = dSegLen;
		}

		m_dLength = dLen;
		double dMidLen = dLen / 2.0; // half the length
		dLen = 0.0; // reset total length
		int nIndex = 0;
		while (nIndex < dLens.length && dLen < dMidLen)
			dLen += dLens[nIndex++]; // find length immediately before the midpoint

		if (nIndex == 0)
		{
			m_nMidLon = m_oNodes.get(0).m_nLon;
			m_nMidLat = m_oNodes.get(0).m_nLat;
			return;
		}
		dLen -= dLens[--nIndex]; // rewind one position
		m_oNodes.subList(nIndex, nIndex + 2).toArray(oSeg);
		double dRatio = (dMidLen - dLen) / dLens[nIndex];

		int nDeltaX = (int)((oSeg[1].m_nLon - oSeg[0].m_nLon) * dRatio);
		int nDeltaY = (int)((oSeg[1].m_nLat - oSeg[0].m_nLat) * dRatio);
		m_nMidLon = oSeg[0].m_nLon + nDeltaX;
		m_nMidLat = oSeg[0].m_nLat + nDeltaY;
	}
	
	
	/**
	 * Iterates through the nodes that make up this Way to determine the minimum
	 * and maximum latitude and longitudes
	 */
	public void setMinMax()
	{
	   	m_nMaxLat = Integer.MIN_VALUE;
		m_nMaxLon = Integer.MIN_VALUE;
		m_nMinLat = Integer.MAX_VALUE;
		m_nMinLon = Integer.MAX_VALUE;
		for (int i = 0; i < m_oNodes.size(); i++)
		{
			OsmNode oNode = m_oNodes.get(i);
			if (oNode.m_nLat > m_nMaxLat)
				m_nMaxLat = oNode.m_nLat;
			if (oNode.m_nLat < m_nMinLat)
				m_nMinLat = oNode.m_nLat;
			if (oNode.m_nLon > m_nMaxLon)
				m_nMaxLon = oNode.m_nLon;
			if (oNode.m_nLon < m_nMinLon)
				m_nMinLon = oNode.m_nLon;
		}
	}

	
	/**
	 * Appends a GeoJson Feature representation of this Way to the given
	 * StringBuilder.
	 * @param sLineBuf buffer to add the GeoJson Feature to
	 * @param oWays Object that contains the metadata definitions for Ways
	 */
	public void appendLineGeoJson(StringBuilder sLineBuf, WayNetworks oWays)
	{
		sLineBuf.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[");
		for (int nIndex = 0; nIndex < m_oNodes.size(); nIndex++)
		{
			OsmNode oNode = m_oNodes.get(nIndex);
			double dLon = GeoUtil.fromIntDeg(oNode.m_nLon);
			double dLat = GeoUtil.fromIntDeg(oNode.m_nLat);
			sLineBuf.append(String.format("[%f,%f],", dLon, dLat));
		}
		
		WayMetadata oMetadata = oWays.getMetadata(m_oId);
		sLineBuf.setLength(sLineBuf.length() - 1);
		sLineBuf.append(String.format("]},\"properties\":{\"spdlimit\":%d,\"lanecount\":%d,\"type\":\"%s\"%s,\"length\":%4.2f,\"imrcpid\":\"%s\"", oMetadata.m_nSpdLimit, oMetadata.m_nLanes, get("highway"), m_bBridge ? ",\"bridge\":\"y\"" : "", m_dLength, m_oId.toString()));
		sLineBuf.append(",\"tags\":{");
		for (Entry<String, String> oEntry : entrySet())
		{
			sLineBuf.append("\"").append(oEntry.getKey()).append("\":\"").append(oEntry.getValue().replace("\\", "\\\\").replace("\"", "\\\"")).append("\",");
		}
		if (!isEmpty())
			sLineBuf.setLength(sLineBuf.length() - 1);
		sLineBuf.append("}}},");
	}
	
	
	/**
	 * Gets a list of all the Ways whose end node is this Way's start node
	 * 
	 * @return A list containing Ways whose end node is this Way's start node
	 */
	public ArrayList<OsmWay> getFromWays()
	{
		return getConnectedWays(0);
	}
	
	
	/**
	 * Gets a list of all the Ways whose start node is this Way's end node
	 * 
	 * @return A list containing Ways whose start node is this Way's end node
	 */
	public ArrayList<OsmWay> getToWays()
	{
		return getConnectedWays(m_oNodes.size() - 1);
	}
	
	
	/**
	 * Gets a list of the Ways that connect to this Way depending on the index 
	 * passed into the method. {@code nNodeIndex} should be 0 or number of nodes - 1 
	 * of this way. If it is 0 this method gets a list of all the 
	 * Ways whose end node is this Way's start node. If it is size - 1, this 
	 * method gets a list of all the Ways whose start node is this Way's end node.
	 * 
	 * @param nNodeIndex 0 or number of nodes - 1
	 * @return If {@code nNodeIndex} is 0 a list of all the Ways whose end node 
	 * is this Way's start node. If {@code nNodeIndex} is the number of nodes - 1
	 * a list of all the Ways whose start node is this Way's end node.
	 */
	private ArrayList<OsmWay> getConnectedWays(int nNodeIndex)
	{
		OsmNode oEndpoint = m_oNodes.get(nNodeIndex);
		int nLimit = oEndpoint.m_oRefs.size();
		ArrayList<OsmWay> oWays = new ArrayList();

		for (int nIndex = 0; nIndex < nLimit; nIndex++)
		{
			OsmWay oCmp = oEndpoint.m_oRefs.get(nIndex);
			if (OsmWay.WAYBYTEID.compare(this, oCmp) == 0)
				continue;
			
			if (nNodeIndex == 0)
			{
				if (OsmNode.NODEBYTEID.compare(oEndpoint, oCmp.m_oNodes.get(oCmp.m_oNodes.size() - 1)) == 0)
					oWays.add(oCmp);
			}
			else
			{
				if (OsmNode.NODEBYTEID.compare(oEndpoint, oCmp.m_oNodes.get(0)) == 0)
					oWays.add(oCmp);
			}
		}
		
		return oWays;
	}
	
	
	/**
	 * Attempts to snap the given point to this Way by trying to snap the point
	 * to each line segment of the polyline that defines the Way's geometry.
	 * 
	 * @param nTol tolerance used for perpendicular distance, the point must be
	 * at within this distance of the line segment it gets snapped to
	 * @param nX longitude in decimal degrees scaled to 7 decimal places of the point
	 * @param nY latitude in decimal degrees scaled to 7 decimal places of the point
	 * @return A WaySnapInfo containing the parameters calculated during the
	 * {@link GeoUtil#getPerpDist(int, int, int, int, int, int, imrcp.geosrv.WaySnapInfo)}
	 */
	public WaySnapInfo snap(int nTol, int nX, int nY)
	{
		WaySnapInfo oReturn = new WaySnapInfo(this);
		if (!GeoUtil.isInside(nX, nY, m_nMinLon, m_nMinLat, m_nMaxLon, m_nMaxLat, nTol))
			return oReturn; // point not inside minimum bounding rectangle

		int nDist = Integer.MAX_VALUE; // narrow to the minimum dist
		long nSqTol = nTol * nTol; // squared tolerance for comparison

		WaySnapInfo oReuse = new WaySnapInfo();
		
		int nLimit = m_oNodes.size() - 1;
		int[] nIndices = Arrays.newIntArray();
		for (int nNodeIndex = 0; nNodeIndex < nLimit;)
		{
			OsmNode o1 = m_oNodes.get(nNodeIndex++);
			OsmNode o2 = m_oNodes.get(nNodeIndex);
			if (GeoUtil.isInside(nX, nY, o1.m_nLon, o1.m_nLat, o2.m_nLon, o2.m_nLat, nTol)) // quick check if the node is close to the line segment
			{
				int nSqDist = GeoUtil.getPerpDist(nX, nY, o1.m_nLon, o1.m_nLat, o2.m_nLon, o2.m_nLat, oReuse);
				nIndices = Arrays.add(nIndices, nNodeIndex); // store the indices of line segments the point is close to
				oReuse.m_nIndex = nNodeIndex - 1;
				if (nSqDist >= 0 && nSqDist <= nSqTol && nSqDist < nDist)
				{
					nDist = nSqDist; // reduce to next smallest distance
					oReturn.setValues(oReuse);
				}
			}
		}
		
		if (nDist == Integer.MAX_VALUE && nIndices[0] > 1) // if the point didn't snap to a line segment
		{
			Iterator<int[]> oIt = Arrays.iterator(nIndices, new int[1], 1, 1);
			while (oIt.hasNext()) // go back to the line segments the point was within the tolerance to
			{
				int nNodeIndex = oIt.next()[0];
				OsmNode o1 = m_oNodes.get(nNodeIndex - 1);
				OsmNode o2 = m_oNodes.get(nNodeIndex);
				OsmNode oNode;
				long lXd1 = o1.m_nLon - nX;
				long lYd1 = o1.m_nLat - nY;
				long lXd2 = o2.m_nLon - nX;
				long lYd2 = o2.m_nLat - nY;
				long lXp;
				long lYp;
				int nSqDist1 = (int)(lXd1 * lXd1 + lYd1 * lYd1); // calculate the distance to the endpoints of the line segment
				int nSqDist2 = (int)(lXd2 * lXd2 + lYd2 * lYd2);
				int nSqDist;
				if (nSqDist1 < nSqDist2) // get the endpoint the point is closest too
				{
					nSqDist = nSqDist1;
					oNode = o1;
				}
				else
				{
					nSqDist = nSqDist2;
					oNode = o2;
				}
				if (nSqDist >= 0 && nSqDist <= nSqTol && nSqDist < nDist) // if it is within the tolerance snap it to the end point
				{
					nDist = nSqDist;
					oReturn.m_dProjSide = 0;
					oReturn.m_nLonIntersect = oNode.m_nLon;
					oReturn.m_nLatIntersect = oNode.m_nLat;
					oReturn.m_nSqDist = nDist;
					oReturn.m_nRightHandRule = (int)(((o2.m_nLon - o1.m_nLon) * (nY - o1.m_nLat)) - ((o2.m_nLat - o1.m_nLat) * (nX - o1.m_nLon)));
					oReturn.m_nIndex = nNodeIndex - 1;
					oReturn.m_bPerpAlgorithm = false;
				}
			}
		}
		if (!Double.isNaN(oReuse.m_dProjSide))
			oReturn.m_dProjSide = oReuse.m_dProjSide;

		return oReturn;
	}

	
	/**
	 * Returns a value representing the direction of travel of the roadway segment
	 * by calculating the heading going from the first node to the second.
	 * @return 1 = east, 2 = south, 3 = west, 4 = north
	 */
	public int getDirection()
	{
		OsmNode o1 = m_oNodes.get(0);
		OsmNode o2 = m_oNodes.get(1);
		double dPiOver4 = Math.PI / 4;
		double dHdg = GeoUtil.heading(o1.m_nLon, o1.m_nLat, o2.m_nLon, o2.m_nLat);
		if (dHdg < dPiOver4) // 0 <= x < pi/4
			return 1; // east
		else if (dHdg < 3 * dPiOver4) // pi/4 <= x < 3pi/4
			return 4; // north
		else if (dHdg < 5 * dPiOver4) // 3pi/4 <= x < 5pi/4
			return 3; // west
		else if (dHdg < 7 * dPiOver4) // 5pi/4 <= x < 7pi/4
			return 2; // south
		else // 7pi/4 <= x < 2pi
			return 1; //east 
	}
	
	
	/**
	 * Determines if the road is "curved". The algorithm checks if any node of
	 * the Way has a perpendicular distance to the line segment created from the
	 * first and last node that is greater than 10% of the length of that line
	 * segment.
	 * 
	 * @return 0 = not curved, 1 = curved
	 */
	public int getCurve()
	{
		int nLastNode = m_oNodes.size() - 1;
		if (nLastNode < 2)
			return 0;
		OsmNode oStart = m_oNodes.get(0);
		OsmNode oEnd = m_oNodes.get(nLastNode);
		double dDist = GeoUtil.distance(oStart.m_nLon, oStart.m_nLat, oEnd.m_nLon, oEnd.m_nLat);
		double dMax = -Double.MAX_VALUE;
		for (int nIndex = 1; nIndex < nLastNode; nIndex++)
		{
			OsmNode oNode = m_oNodes.get(nIndex);
			int nSqPerp = GeoUtil.getPerpDist(oNode.m_nLon, oNode.m_nLat, oStart.m_nLon, oStart.m_nLat, oEnd.m_nLon, oEnd.m_nLat);
			if (nSqPerp > 0)
			{
				double dPerp = Math.sqrt(nSqPerp);
				if (dPerp > dMax)
				{
					dMax = dPerp;
				}
			}
		}
		
		return dMax > dDist * 0.1 ? 1 : 0;
	}
	
	
	/**
	 * Gets a WayIterator to iterate through the nodes of the Way
	 * @return a WayIterator to iterate through the nodes of the Way
	 */
	public WayIterator iterator()
	{
		return new WayIterator(m_oNodes);
	}
	
	
	/**
	 * Sets the {@link OsmNode#m_dHdg} variable for each of the nodes in this
	 * Way.
	 */
	public void setHdgs()
	{
		OsmNode o1 = null;
		OsmNode o2 = null;
		for (int nIndex = 0; nIndex < m_oNodes.size() - 1; nIndex++)
		{
			o1 = m_oNodes.get(nIndex);
			o2 = m_oNodes.get(nIndex + 1);
			o1.m_dHdg = GeoUtil.heading(o1.m_nLon, o1.m_nLat, o2.m_nLon, o2.m_nLat);
		}
		
		o2.m_dHdg = o1.m_dHdg;
	}

	
	/**
	 * Gets the length of the Way in meters using the Haversine formula.
	 * @return The length in meters of the Way
	 */
	public double getLengthInM()
	{
		double dLen = 0.0;
		OsmNode[] oSeg = new OsmNode[2];
		for (int nIndex = 0; nIndex < m_oNodes.size() - 1; nIndex++)
		{
			oSeg[0] = m_oNodes.get(nIndex);
			oSeg[1] = m_oNodes.get(nIndex + 1);
			dLen += GeoUtil.distanceFromLatLon(GeoUtil.fromIntDeg(oSeg[0].m_nLat), GeoUtil.fromIntDeg(oSeg[0].m_nLon), GeoUtil.fromIntDeg(oSeg[1].m_nLat), GeoUtil.fromIntDeg(oSeg[1].m_nLon));
		}
		
		return dLen * 1000; // convert km to m
	}
}

