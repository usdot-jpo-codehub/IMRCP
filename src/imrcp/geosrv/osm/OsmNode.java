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

import imrcp.system.Arrays;
import imrcp.system.Id;
import imrcp.system.Introsort;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Represents an Open Street Map Node which make up Open Street Map Ways.
 * @author aaron.cherney
 */
public class OsmNode extends OsmObject
{
	/**
	 * Compares OsmNodes by longitude then latitude
	 */
	public static Comparator<OsmNode> GEOCOMP = (OsmNode o1, OsmNode o2) -> 
	{
		int nReturn = Integer.compare(o1.m_nLon, o2.m_nLon);
		if (nReturn == 0)
		{
			nReturn = Integer.compare(o1.m_nLat, o2.m_nLat);
		}
		return nReturn;
	};
	
	
	/**
	 * Compare OsmNodes by IMRCP Id
	 */
	public static Comparator<OsmNode> NODEBYTEID = (OsmNode o1, OsmNode o2) -> 
	{
		return Id.COMPARATOR.compare(o1.m_oId, o2.m_oId);
	};
	
	
	/**
	 * Compares OsmNodes by OSM id
	 */
	public static Comparator<OsmNode> LONGID = (OsmNode o1, OsmNode o2) -> {return Long.compare(o1.m_lId, o2.m_lId);};
	
	
	/**
	 * Latitude of the node in decimal degrees scaled to 7 decimal places
	 */
	public int m_nLat;

	
	/**
	 * Longitude of the node in decimal degrees scaled to 7 decimal places
	 */
	public int m_nLon;

	
	/**
	 * A list of OsmWays that contain this Node and is sorted using {@link OsmWay#WAYBYTEID}
	 */
	public ArrayList<OsmWay> m_oRefs;

	
	/**
	 * The hash index of the {@link HashBucket} this node is in
	 */
	public int m_nHash;

	
	/**
	 * List of nodes that were created from this node during the Separate 
	 * algorithm
	 */
	public ArrayList<OsmNode> m_oSeps = null;

	
	/**
	 * Not used for anything at the moment
	 */
	public boolean m_bDrawConnector = true;

	
	/**
	 * The number of OsmWays this Node is a part of in the original Open Street Map
	 * definitions
	 */
	public int m_nOriginalRefCount = 0;

	
	/**
	 * The number of OsmWays this Node is a part of at the start of the Separate
	 * algorithm
	 */
	public int m_nOriginalRefForSep = 0;

	
	/**
	 * Heading in radians to the next node of the OsmWay that is currently being
	 * processed. This value must be reset for each OsmWay it is a part of, usually
	 * done by {@link OsmWay#setHdgs()}
	 */
	public double m_dHdg;

	
	/**
	 * Default Constructor. Initializes {@link m_oRefs}
	 */
	public OsmNode()
	{
		m_oRefs = new ArrayList();
	}

	
	/**
	 * Constructs an OsmNode from the given DataInputStream which is wrapper the
	 * input stream of an IMRCP OSM binary file
	 * @param oIn input stream of the IMRCP OSM binary file
	 * @param oPool String Pool that contains the possible key and value strings
	 * @param nFp current position in the file. An array of size one is used
	 * to store this value so it can be updated and used by other methods
	 * @throws IOException
	 */
	public OsmNode(DataInputStream oIn, ArrayList<String> oPool, int[] nFp)
	   throws IOException
	{
		int nTemp = nFp[0]; // make local copy
		m_nFp = nTemp; // store the file position this node starts at
		m_nOriginalRefCount = oIn.readByte();
		m_oRefs = new ArrayList(m_nOriginalRefCount);
		nTemp += 1;
		m_nLon = oIn.readInt();
		nTemp += 4;
		m_nLat = oIn.readInt();
		nTemp += 4;
		int nTags = oIn.readInt();
		nTemp += 4;
		for (int nIndex = 0; nIndex < nTags; nIndex++)
		{
			put(oPool.get(oIn.readInt()), oPool.get(oIn.readInt()));
			nTemp += 8;
		}
		
		nFp[0] = nTemp; // update array copy
		
		generateId();
	}
	
	
	/**
	 * Constructs an OsmNode with the given parameters. Initializes {@link #m_oRefs}
	 * and {@link #m_nHash}
	 * @param lId OSM id of the node
	 * @param nLat latitude in decimal degrees scaled to 7 decimal places
	 * @param nLon longitude in decimal degrees scaled to 7 decimal places
	 */
	public OsmNode(long lId, int nLat, int nLon)
	{
		super(lId);
		m_oRefs = new ArrayList();
		m_nLat = nLat;
		m_nLon = nLon;
		m_nHash = HashBucket.hashLonLat(nLon, nLat);
	}
	
	
	/**
	 * Constructs an OsmNode with the given parameters. Initializes {@link #m_oId}
	 * @param nLat latitude in decimal degrees scaled to 7 decimal places
	 * @param nLon longitude in decimal degrees scaled to 7 decimal places
	 * @throws IOException
	 */
	public OsmNode(int nLat, int nLon)
		throws IOException
	{
		m_oRefs = new ArrayList();
		m_nLon = nLon;
		m_nLat = nLat;
		generateId();
	}
	
	
	/**
	 * Removes the given OsmWay from {@link #m_oRefs}
	 * @param oRemove The OsmWay to remove
	 */
	public void removeRef(OsmWay oRemove)
	{
		Introsort.usort(m_oRefs, OsmWay.WAYBYTEID);
		int nIndex = Collections.binarySearch(m_oRefs, oRemove, OsmWay.WAYBYTEID);
		if (nIndex >= 0)
			m_oRefs.remove(nIndex);
	}
	
	
	/**
	 * Adds the given OsmWay to {@link #m_oRefs}
	 * @param oAdd The OsmWay to add
	 */
	public void addRef(OsmWay oAdd)
	{
		Introsort.usort(m_oRefs, OsmWay.WAYBYTEID);
		int nIndex = Collections.binarySearch(m_oRefs, oAdd, OsmWay.WAYBYTEID);
		if (nIndex < 0)
			m_oRefs.add(~nIndex, oAdd);
	}

	
	/**
	 * Generates the Id for this Node which is based off of it latitude and longitude
	 * @throws IOException
	 */
	public void generateId()
		throws IOException
	{
		int[] nArr = Arrays.newIntArray(2);
		nArr = Arrays.add(nArr, m_nLon, m_nLat);
		m_oId = new Id(Id.NODE, nArr);
	}
	
	
	/**
	 * Returns the longitude and latitude concatenated together as a string
	 * @return 
	 */
	@Override
	public String toString()
	{
		return m_nLon + " " + m_nLat;
	}
}
