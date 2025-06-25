/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.geosrv.osm;

import imrcp.geosrv.GeoUtil;
import imrcp.system.Introsort;
import imrcp.system.StringPool;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The class is used to parse IMRCP OSM binary files which contain the definitions
 * of roadway segments available to the system
 * @author aaron.cherney
 */
public class OsmBinParser
{
	/**
	 * Log4j Logger
	 */
	private Logger m_oLogger = LogManager.getLogger(OsmBinParser.class);
	
	
	/**
	 * Default constructor. Does nothing.
	 */
	public OsmBinParser()
	{
	}


	/**
	 * Parses the hash index file associated with the given IMRCP OSM
	 * binary file adding entries for the hash buckets that intersect the given 
	 * bounding box into the HashMap
	 * 
	 * @param sFile Path of the IMRCP OSM binary file
	 * @param nMinLon minimum longitude of the bounding box in decimal degrees 
	 * scaled to 7 decimal places 
	 * @param nMinLat minimum latitude of the bounding box in decimal degrees 
	 * scaled to 7 decimal places 
	 * @param nMaxLon maximum longitude of the bounding box in decimal degrees 
	 * scaled to 7 decimal places 
	 * @param nMaxLat maximum latitude of the bounding box in decimal degrees 
	 * scaled to 7 decimal places 
	 * @param oHashes HashMap to map a hash bucket value to a list of OsmWays in 
	 * that hash bucket
	 * @throws IOException
	 */
	public void parseHashes(String sFile, int nMinLon, int nMinLat, int nMaxLon, int nMaxLat, HashMap<Integer, ArrayList<OsmWay>> oHashes)
		throws IOException
	{
		int nNodeStart; // will store where node definitions start in the file
		ArrayList<String> oPool;
		try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(Files.newInputStream(Paths.get(sFile))))) // open the file initially to read in the string pool and determine the starting position of node definitions
		{ // do this now so we don't parse the string pool everytime in the loop below
			int nCount = oIn.readInt();
			oPool = new ArrayList(nCount); // local string pool
			while (nCount-- > 0)
				oPool.add(oIn.readUTF());
			
			nNodeStart = oIn.readInt();
		}
		
		// determine the buckets the bounding box intersect
		int nXbeg = HashBucket.getBucket(nMinLon);
		int nYbeg = HashBucket.getBucket(nMinLat);
		
		int nXend = HashBucket.getBucket(nMaxLon);
		int nYend = HashBucket.getBucket(nMaxLat);
		
		for (int nY = nYbeg; nY <= nYend; nY++) // for each bucket
		{
			for (int nX = nXbeg; nX <= nXend; nX++)
			{
				int nHash = HashBucket.hashBucketVals(nX, nY);
				oHashes.put(nHash, new ArrayList()); // add it to the map
			}
		}
		
		ArrayList<OsmNode> oNodes = new ArrayList();
		
		try (DataInputStream oIndexIn = new DataInputStream(new BufferedInputStream(Files.newInputStream(Paths.get(sFile + ".ndx"))))) // read the index file
		{
			int nBuckets = oIndexIn.readInt(); // read the number of buckets in the file
			while (nBuckets-- > 0) // for each bucket
			{
				int nBucket = oIndexIn.readInt(); // read hash index 
				int nSize = oIndexIn.readInt(); // read the number of ways in the bucket
				int[] nNodes = null;
				int[] nWays = null;
				boolean bParse = oHashes.containsKey(nBucket); // only parse if the bucket intersect the bounding box
				if (bParse)
				{
					nWays = new int[nSize];
					for (int nIndex = 0; nIndex < nWays.length; nIndex++)
					{
						nWays[nIndex] = oIndexIn.readInt(); // read each way which is the file position of its definition in the IMRCP OSM binary file
					}
				}
				else
				{
					oIndexIn.skipBytes(nSize * 4); // skip all of the integers if they don't need to be parsed
				}
				
				nSize = oIndexIn.readInt(); // read the number of nodes
				if (bParse)
				{
					nNodes = new int[nSize];
					for (int nIndex = 0; nIndex < nNodes.length; nIndex++)
					{
						nNodes[nIndex] = oIndexIn.readInt(); // read each node which is the file position of its definition in the IMRCP OSM binary file
					}
				}
				else
				{
					oIndexIn.skipBytes(nSize * 4); // skip all of the integers if they don't need to be parsed
				}
				
				if (nNodes == null) 
					continue;
				ArrayList<OsmWay> oWays = oHashes.get(nBucket);
				try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(Files.newInputStream(Paths.get(sFile))))) // re-open the file
				{
					oIn.skip(nNodeStart); // skip to the start of the nodes
					int[] nFp = new int[]{nNodeStart};
					for (int nNodeFp : nNodes) // for each node file position
					{
						int nSkip = nNodeFp - nFp[0]; // skip to its position
						oIn.skipBytes(nSkip);
						nFp[0] = nNodeFp;
						
						OsmNode oNode = new OsmNode(oIn, oPool, nFp); // read the node from the file
						int nIndex = Collections.binarySearch(oNodes, oNode, OsmNode.FPCOMP);
						if (nIndex < 0)
							oNodes.add(~nIndex, oNode);
					}
					
					for (int nWayFp : nWays) // for each way file position
					{
						int nSkip = nWayFp - nFp[0]; // skip to its position
						oIn.skipBytes(nSkip);
						nFp[0] = nWayFp;

						OsmWay oTemp = new OsmWay(oIn, oPool, oNodes, nFp); // read the way from the file
						oTemp.updateRefs();
						oWays.add(oTemp);
					}
				}
				
			}
		}
	}
	
	
	/**
	 * Parses the given file and its index to fill the lists with the nodes and
	 * roadway segments that are in the given hash bucket index .
	 * @param sFile IMRCP OSM binary file
	 * @param nHash hash bucket index
	 * @param oNodes list to be filled with the nodes in the hash bucket, the list is cleared before nodes are added
	 * @param oWays list to be filled with the ways in the hash bucket, the list is cleared before ways are added
	 * @throws Exception
	 */
	public void parseHash(String sFile, int nHash, ArrayList<OsmNode> oNodes, ArrayList<OsmWay> oWays)
	   throws Exception
	{
		int[] nNodes = null;
		int[] nWays = null;
		
		try (DataInputStream oIn = new DataInputStream(Files.newInputStream(Paths.get(sFile + ".ndx")))) // read the index file
		{
			int nBuckets = oIn.readInt();
			while (nBuckets-- > 0)
			{
				int nBucket = oIn.readInt();
				int nSize = oIn.readInt();
				
				if (nBucket == nHash) // only parse the given hash bucket index
				{
					nWays = new int[nSize];
					for (int nIndex = 0; nIndex < nWays.length; nIndex++)
					{
						nWays[nIndex] = oIn.readInt(); // get the file position for all the ways
					}
				}
				else // skip sections that don't need to be parsed
				{
					oIn.skipBytes(nSize * 4);
				}
				
				nSize = oIn.readInt();
				if (nBucket == nHash) // only parse the given hash bucket index
				{
					nNodes = new int[nSize];
					for (int nIndex = 0; nIndex < nNodes.length; nIndex++)
					{
						nNodes[nIndex] = oIn.readInt(); // get the file position for all the nodes
					}
					break; // have parse the desired hash bucket so close the file
				}
				else
				{
					oIn.skipBytes(nSize * 4);
				}
			}
		}
		
		if (nNodes == null) // didn't have the hash bucket index so there is nothing left to do
			return;
		
		oNodes.clear();
		oWays.clear();
		try (DataInputStream oIn = new DataInputStream(Files.newInputStream(Paths.get(sFile))))
		{
			int nCount = oIn.readInt();
			ArrayList<String> oPool = new ArrayList(nCount); // local string pool
			while (nCount-- > 0)
				oPool.add(oIn.readUTF());
			
			int[] nFp = new int[]{oIn.readInt()};
			oIn.skipBytes(4); // skip the total number of nodes
			for (int nNodeFp : nNodes) // since the file position is known for each of the nodes that need to be read
			{
				int nSkip = nNodeFp - nFp[0];
				oIn.skipBytes(nSkip); // skip to the next desired node
				nFp[0] = nNodeFp;
				
				oNodes.add(new OsmNode(oIn, oPool, nFp));
			}
			
			for (int nWayFp : nWays)
			{
				int nSkip = nWayFp - nFp[0];
				oIn.skipBytes(nSkip); // skip to the next desired way
				nFp[0] = nWayFp;
				
				OsmWay oTemp = new OsmWay(oIn, oPool, oNodes, nFp);
				oTemp.updateRefs();
				oWays.add(oTemp);
			}
		}
		
		int nIndex = oNodes.size();
		while (nIndex-- > 0) // remove any nodes that are not part of a way
		{
			if (oNodes.get(nIndex).m_oRefs.isEmpty())
				oNodes.remove(nIndex);
		}
		
		Introsort.usort(oNodes, OsmNode.NODEBYTEID);
		Introsort.usort(oWays, OsmWay.WAYBYTEID);
	}
	
	
	/**
	 * Parses the given IMRCP OSM binary file, adding all of the roadway segment
	 * and node defintions to the given list. This method is primarily used for
	 * IMRCP OSM binary files of Networks that have already been created.
	 * 
	 * @param sFile Path to the IMRCP OSM binary file
	 * @param oNodes List to be filled with nodes, should be empty
	 * @param oWays List to be filled with roadway segments, should be empty
	 * @param oMainPool StringPool to use when parsing key and value Strings
	 * @return a bounding box of all the roadway segments. [min lon, min lat, max lon, max lat],
	 * all the values are in decimal degrees scaled to 7 decimal places
	 * @throws IOException
	 */
	public int[] parseFile(String sFile, ArrayList<OsmNode> oNodes, ArrayList<OsmWay> oWays, StringPool oMainPool)
	   throws IOException
	{
		int[] nBounds = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		try (DataInputStream oIn = new DataInputStream(Files.newInputStream(Paths.get(sFile))))
		{
			m_oLogger.info("Reading local string pool");
			int nCount = oIn.readInt();
			ArrayList<String> oPool = new ArrayList(nCount); // local string pool
			while (nCount-- > 0)
				oPool.add(oMainPool.intern(oIn.readUTF()));
			
			m_oLogger.info("Reading nodes");
			int[] nFp = new int[]{oIn.readInt()};
			nCount = oIn.readInt();
			oNodes.ensureCapacity(nCount);
			while (nCount-- > 0)
				oNodes.add(new OsmNode(oIn, oPool, nFp));
			
			m_oLogger.info("Reading ways");
			nCount = oIn.readInt();
			oWays.ensureCapacity(nCount);
			while (nCount-- > 0)
			{
				OsmWay oTemp = new OsmWay(oIn, oPool, oNodes, nFp);
				if (oTemp.m_oNodes.size() == 1)
					continue;
				oTemp.updateRefs();
				if (oTemp.m_nMinLon < nBounds[0])
					nBounds[0] = oTemp.m_nMinLon;
				if (oTemp.m_nMinLat < nBounds[1])
					nBounds[1] = oTemp.m_nMinLat;
				if (oTemp.m_nMaxLon > nBounds[2])
					nBounds[2] = oTemp.m_nMaxLon;
				if (oTemp.m_nMaxLat > nBounds[3])
					nBounds[3] = oTemp.m_nMaxLat;
				oWays.add(oTemp);
			}
			
			Introsort.usort(oNodes, OsmNode.NODEBYTEID);
			Introsort.usort(oWays, OsmWay.WAYBYTEID);
		}
		
		return nBounds;
	}
	
	
	/**
	 * Parses the IMRCP OSM binary file, filtering which roadway segments and
	 * nodes are added to the list based on the given polygon and highway tag
	 * filters. This method is primarily used to aid in the creation of a Network
	 * by parsing a statewide IMRCP OSM binary file to get the desired roadway
	 * segments and node for the Network.
	 * 
	 * @param sFile Path to the IMRCP OSM binary file
	 * @param oAllNodes List to be filled with nodes, it may already contain some
	 * nodes that have to be sorted by {@link OsmNode#NODEBYTEID}
	 * @param oAllWays List to be filled with ways, it may already contain come
	 * ways that have to be sorted by {@link OsmWay#WAYBYTEID}
	 * @param nPolygon Array of coordinates describing a polygon, only roadway
	 * segments that intersect this polygon will be added to the list. Format
	 * is [y0, x0, y1, x1, ..., yn, xn, y0, x0], all coordinates are in decimal
	 * degrees scaled to 7 decimal places
	 * @param nBoundingBox Array of coordinates describing the bounding box of
	 * the polygon. Format is [min lon, min lat, max lon, max lat]
	 * @param oMainPool StringPool to use when parsing key and value Strings
	 * @param sFilter Array containing the values of the "highway" tag of the
	 * roadway segments that are to be included in the list
	 */
	public void parseFileWithFilters(String sFile, ArrayList<OsmNode> oAllNodes, ArrayList<OsmWay> oAllWays, int[] nPolygon, StringPool oMainPool, String[] sFilter)
	{
		try (DataInputStream oIn = new DataInputStream(Files.newInputStream(Paths.get(sFile))))
		{
			m_oLogger.info("Reading local string pool");
			int nCount = oIn.readInt();
			ArrayList<String> oPool = new ArrayList(nCount); // local string pool
			while (nCount-- > 0)
				oPool.add(oMainPool.intern(oIn.readUTF()));
			
			m_oLogger.info("Reading nodes");
			int[] nFp = new int[]{oIn.readInt()};
			nCount = oIn.readInt();
			ArrayList<OsmNode> oNodes = new ArrayList(nCount);
			while (nCount-- > 0)
				oNodes.add(new OsmNode(oIn, oPool, nFp));
			
			m_oLogger.info("Reading ways");
			nCount = oIn.readInt();
			ArrayList<OsmWay> oWays = new ArrayList(nCount);
			while (nCount-- > 0)
			{
				OsmWay oTemp = new OsmWay(oIn, oPool, oNodes, nFp);
				if (!OsmUtil.include(oTemp.get("highway"), sFilter) || !GeoUtil.boundingBoxesIntersect(oTemp.m_nMinLon, oTemp.m_nMinLat, oTemp.m_nMaxLon, oTemp.m_nMaxLat, nPolygon[3], nPolygon[4], nPolygon[5], nPolygon[6])) // quick filter on highway tag and bounding box
					continue;
				for (OsmNode oNode : oTemp.m_oNodes)
				{
					if (GeoUtil.isInside(oNode.m_nLon, oNode.m_nLat, nPolygon[3], nPolygon[4], nPolygon[5], nPolygon[6], 0) && // first test if the node is inside the bounding box
						GeoUtil.isPointInsideRingAndHoles(nPolygon, oNode.m_nLon, oNode.m_nLat)) // this test if it is inside the polygon
					{
						oTemp.updateRefs();
						oWays.add(oTemp);
						break; // if one node is inside, the way intersects so don't test other nodes for this way
					}
				}
			}
			
			nCount = oNodes.size();
			while (nCount-- > 0)
			{
				OsmNode oNode = oNodes.get(nCount);
				if (!oNodes.get(nCount).m_oRefs.isEmpty()) // only include nodes that are contained by a way
				{
					oNode.m_nFp = 0;
					int nSearch = Collections.binarySearch(oAllNodes, oNode, OsmNode.NODEBYTEID);
					if (nSearch < 0) // if the node is not in the list
						oAllNodes.add(~nSearch, oNode); // add it
					else
					{
						OsmNode oSavedNode = oAllNodes.get(nSearch); // otherwise find it in the list
						for (OsmWay oWay : oNode.m_oRefs) // check all of the ways that contain the node
						{
							for (int nIndex = 0; nIndex < oWay.m_oNodes.size(); nIndex++)
							{
								if (OsmNode.NODEBYTEID.compare(oWay.m_oNodes.get(nIndex), oSavedNode) == 0) // find the node in the way's list
								{
									oWay.m_oNodes.set(nIndex, oSavedNode); // and ensure it has the reference to node that is in the list
									oSavedNode.m_oRefs.add(oWay);
								}
							}
						}
					}
				}
			}
			
			for (OsmWay oWay : oWays)
			{
				int nSearch = Collections.binarySearch(oAllWays, oWay, OsmWay.WAYBYTEID);
				if (nSearch < 0)
					oAllWays.add(~nSearch, oWay);
			}
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
		}
	}
}
