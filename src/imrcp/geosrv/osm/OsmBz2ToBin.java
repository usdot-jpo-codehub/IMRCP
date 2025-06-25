/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.geosrv.osm;

import imrcp.geosrv.GeoUtil;
import imrcp.system.Arrays;
import imrcp.system.Introsort;
import imrcp.system.StringPool;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;
import javax.xml.parsers.SAXParserFactory;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This class is used to convert Open Street Map .xml.bz2 files and convert them
 * to IMRCP OSM binary file which is a more compact form 
 * @author aaron.cherney
 */
public class OsmBz2ToBin extends DefaultHandler
{
	/**
	 * Stores the nodes as the file is parsed
	 */
	private ArrayList<OsmNode> m_oNodes = new ArrayList();

	
	/**
	 * Stores the roadway segments as the file is parsed
	 */
	private ArrayList<OsmWay> m_oWays = new ArrayList();

	
	/**
	 * String Pool used to store the xml tag keys and values
	 */
	private StringPool m_oStringPool = new StringPool();

	
	/**
	 * Convenience node search object
	 */
	private OsmNode m_oSearch = new OsmNode();

	
	/**
	 * The current object being parsed
	 */
	private OsmObject m_oCurrent = null;

	
	/**
	 * Stores the hash buckets the roadway segments intersect
	 */
	private ArrayList<HashBucket> m_oBuckets = new ArrayList();

	
	/**
	 * Convenience hash bucket search object
	 */
	private HashBucket m_oHashSearch = new HashBucket();

	
	/**
	 * Stores the hash bucket indices the current way intersects
	 */
	private int[] m_nHashes = Arrays.newIntArray();

	
	/**
	 * Array used to iterator through {@link #m_nHashes}
	 */
	private int[] m_nHash = new int[1];

	
	/**
	 * Flag used to indicate if the list of nodes needs to be sorted or not when
	 * a way is parsed. Should only be true for the first way parsed
	 */
	private boolean m_bSortNodes = true;

	
	/**
	 * Log4j Logger
	 */
	private Logger m_oLogger = LogManager.getLogger(OsmBz2ToBin.class);

	
	/**
	 * OSM tags that are saved
	 */
	private String[] m_sTags = new String[]{"highway", "name", "ref", "bridge", "oneway", "lanes", "maxspeed", "ele"};
	
	
	/**
	 * Default constructor. Does nothing.
	 */
	public OsmBz2ToBin()
	{
	}

	
	@Override
 	public void startElement(String sUri, String sLocalName, 
		String sQname, Attributes iAtt)
	{
		if (sQname.compareTo("node") == 0) // insert into node lookup array
		{
			try
			{
				OsmNode oNode = new OsmNode(Long.parseLong(iAtt.getValue("id")), GeoUtil.toIntDeg(Double.parseDouble(iAtt.getValue("lat"))), GeoUtil.toIntDeg(Double.parseDouble(iAtt.getValue("lon"))));	
				m_oCurrent = oNode;
				m_oNodes.add(oNode);
			}
			catch (Exception oEx)
			{
				oEx.printStackTrace();
			}
		}

		if (sQname.compareTo("way") == 0)
		{
			try
			{
				if (m_bSortNodes) // sort the nodes once after all have been parsed
				{
					m_bSortNodes = false;
					Introsort.usort(m_oNodes, OsmNode.LONGID);
					m_oLogger.info(String.format("Sorted Nodes: %d", m_oNodes.size()));
				}
				OsmWay oWay = new OsmWay(Long.parseLong(iAtt.getValue("id")));
				m_oCurrent = oWay;
			}
			catch (Exception oEx)
			{
				oEx.printStackTrace();
			}
		}

		if (sQname.compareTo("nd") == 0)
		{
			m_oSearch.m_lId = Long.parseLong(iAtt.getValue("ref"));
			int nIndex = Collections.binarySearch(m_oNodes, m_oSearch, OsmObject.LONGID);
			if (nIndex >= 0)
				((OsmWay)m_oCurrent).m_oNodes.add(m_oNodes.get(nIndex));
		}

		if (sQname.compareTo("tag") == 0) // save way tags
		{
			String sKey = m_oStringPool.intern(iAtt.getValue("k")); // store values in StringPool
			for (String sTag : m_sTags)
			{
				if (sKey.startsWith(sTag)) // only save the configured tags
				{
					String sValue = m_oStringPool.intern(iAtt.getValue("v"));
					m_oCurrent.put(sKey, sValue);
					return;
				}
			}
		}
	}


	@Override
 	public void endElement(String sUri, String sLocalName, String sQname)
	{
		if (sQname.compareTo("way") == 0)
		{
			OsmWay oWay = (OsmWay)m_oCurrent;
			if (!OsmUtil.include(oWay.get("highway"), OsmUtil.ALL_ROADS))
				return;
			int nNodeIndex = oWay.m_oNodes.size();
			while (nNodeIndex-- > 1)
			{
				if (OsmUtil.nodesEqual(oWay.m_oNodes.get(nNodeIndex), oWay.m_oNodes.get(nNodeIndex - 1), 10)) // remove nodes that are very close spatially
					oWay.m_oNodes.remove(nNodeIndex);
			}
			if (oWay.m_oNodes.size() < 2) // skip invalid ways
				return;

			m_nHashes[0] = 1;
			int[] nVals = new int[2];
			for (nNodeIndex = 0 ; nNodeIndex < oWay.m_oNodes.size() - 1;) // for each node determine the hash bucket
			{
				OsmNode oN1 = oWay.m_oNodes.get(nNodeIndex++);
				OsmNode oN2 = oWay.m_oNodes.get(nNodeIndex);
				HashBucket.unhash(oN1.m_nHash, nVals);
				int nX1 = nVals[0];
				int nY1 = nVals[1];
				HashBucket.unhash(oN2.m_nHash, nVals);
				int nX2 = nVals[0];
				int nY2 = nVals[1];

				if (nX1 > nX2) // re-order longitude as needed
				{
					nX1 ^= nX2;
					nX2 ^= nX1;
					nX1 ^= nX2;
				}

				if (nY1 > nY2) // re-order latitude as needed
				{
					nY1 ^= nY2;
					nY2 ^= nY1;
					nY1 ^= nY2;
				}

				for (int nX = nX1; nX <= nX2; nX++)
				{
					for (int nY = nY1; nY <= nY2; nY++)
					{
						int nHash = HashBucket.hashBucketVals(nX, nY);
						int nSearch = Arrays.binarySearch(m_nHashes, nHash);
						if (nSearch < 0)
							m_nHashes = Arrays.insert(m_nHashes, nHash, ~nSearch);
					}
				}
			}

			int nIndex = Collections.binarySearch(m_oWays, oWay, OsmObject.LONGID);
			if (nIndex < 0) // add ways once
			{
				oWay.setMinMax();
				for (OsmNode oNode : oWay.m_oNodes) // update the nodes to show they are contained by this way
				{					
					int nRefSearch = Collections.binarySearch(oNode.m_oRefs, oWay, OsmObject.LONGID);
					if (nRefSearch < 0)
						oNode.m_oRefs.add(~nRefSearch, oWay);
				}
				m_oWays.add(~nIndex, oWay);
				Iterator<int[]> oIt = Arrays.iterator(m_nHashes, m_nHash, 1, 1);
				while (oIt.hasNext()) // for each hash bucket index
				{
					oIt.next();
					m_oHashSearch.m_nHash = m_nHash[0];
					int nSearch = Collections.binarySearch(m_oBuckets, m_oHashSearch);
					if (nSearch < 0) // create new bucket if needed
					{
						nSearch = ~nSearch;
						m_oBuckets.add(nSearch, new HashBucket(m_nHash[0]));
					}

					m_oBuckets.get(nSearch).add(oWay); // add the way to the hash bucket
				}
			}
		}
	}

	
	/**
	 * Converts the given OSM .xml.bz file into IMRCP's OSM binary file.
	 * @param sFile OSM .xml.bz file to convert
	 * @param nTol tolerance in decimal degrees scaled to 7 decimal places used
	 * when comparing distance between nodes
	 * 
	 * @return the StringPool created by parsing the file
	 * @throws Exception
	 */
	public StringPool convertFile(String sFile, int nTol)
	   throws Exception
	{
		try (BufferedInputStream oOsm = new BufferedInputStream(
				new CompressorStreamFactory().createCompressorInputStream(
				new BufferedInputStream(Files.newInputStream(Paths.get(sFile)), 16384))))
		{
			XMLReader iXmlReader = SAXParserFactory.newInstance().
				newSAXParser().getXMLReader();
			iXmlReader.setContentHandler(this);
			iXmlReader.parse(new InputSource(oOsm));				
			
			m_oLogger.info(String.format("Ways: %d", m_oWays.size()));
			
			m_oNodes = OsmUtil.removeZeroRefs(m_oNodes); // remove nodes that are not contained by a roadway segment
			
			m_oLogger.info(String.format("Nodes after removing 0 refs: %d", m_oNodes.size()));
			Introsort.usort(m_oNodes, OsmNode.GEOCOMP); // sort nodes by lon then lat
			int nIndex = m_oNodes.size();
			int nSqTol = nTol * nTol;
			int nCount = 0;
			while (nIndex-- > 0)
			{
				OsmNode oCur = m_oNodes.get(nIndex);

				int nInner = nIndex;
				while (nInner-- > 0)
				{
					OsmNode oCmp = m_oNodes.get(nInner);
					int nXDiff = oCur.m_nLon - oCmp.m_nLon;
					if (nXDiff > nTol) // list is sorted by lon first so oCur's lon should always be >= oCmp's lon so don't check against negative tolerance
						break;
					
					int nYDiff = oCur.m_nLat - oCmp.m_nLat;
					if (Math.abs(nYDiff) > nTol)
						break;
					
					if (nXDiff * nXDiff + nYDiff * nYDiff < nSqTol) // if the nodes are within the distance tolerance
					{
						boolean bProcess = true;
						boolean bBridge = oCur.m_oRefs.get(0).m_bBridge;
						for (int i = 1; i < oCur.m_oRefs.size(); i++)
						{
							OsmWay oWay = oCur.m_oRefs.get(i);
							if (OsmObject.LONGID.compare(oWay.m_oNodes.get(0), oCur) == 0 || OsmObject.LONGID.compare(oWay.m_oNodes.get(oWay.m_oNodes.size() - 1), oCur) == 0) // ignore if this node is the endpoint of a way
								continue;
							if (bBridge != oWay.m_bBridge)
							{
								m_oLogger.info("Bridge and non-bridge midpoint intersect");
								bProcess = false;
								break;
							}
						}
						if (bProcess)
						{
							for (OsmWay oWay : oCur.m_oRefs)
							{
								for (int nNodeIndex = 0 ; nNodeIndex < oWay.m_oNodes.size(); nNodeIndex++)
								{
									OsmNode oNode = oWay.m_oNodes.get(nNodeIndex);
									if (oNode.m_lId == oCur.m_lId) // make sure there is only one reference of a node in the list
									{
										oWay.m_oNodes.set(nNodeIndex, oCmp);
										int nRefSearch = Collections.binarySearch(oCmp.m_oRefs, oWay, OsmObject.LONGID);
										if (nRefSearch < 0)
											oCmp.m_oRefs.add(~nRefSearch, oWay);
									}
								}
							}
							oCur.m_oRefs.clear(); // clear the refs to flag that the node can be removed
							++nCount;
							break;
						}
					}
				}
			}
			
			ArrayList<OsmNode> oTempList = new ArrayList(m_oNodes.size() - nCount);
			for (nIndex = 0; nIndex < m_oNodes.size(); nIndex++)
			{
				OsmNode oTemp = m_oNodes.get(nIndex);
				if (!oTemp.m_oRefs.isEmpty()) // determine the original number of ways that contain the node
				{
					int nRefCount = oTemp.m_oRefs.size();
					if (nRefCount > 1)
					{
						for (OsmWay oWay : oTemp.m_oRefs)
						{
							if (OsmObject.LONGID.compare(oWay.m_oNodes.get(0), oTemp) != 0 && OsmObject.LONGID.compare(oWay.m_oNodes.get(oWay.m_oNodes.size() - 1), oTemp) != 0)
								++nRefCount;
						}
					}
					oTemp.m_nOriginalRefCount = nRefCount;
					oTempList.add(oTemp);
				}
			}
			
			m_oNodes.clear();
			m_oNodes = oTempList;
			m_oLogger.info(String.format("Nodes after removing dups: %d", m_oNodes.size()));
			
			m_oLogger.info("Writing " + sFile.replace(".osm.bz2", ".bin"));
			writeBin(sFile, m_oStringPool, m_oNodes, m_oWays);
			m_oLogger.info("Writing " + sFile.replace(".osm.bz2", ".bin.ndx"));
			writeHashIndex(sFile, m_oBuckets);
			return m_oStringPool;
		}
	}
	
	
	/**
	 * Writes the given string pool, list of nodes, and list of ways into the
	 * IMRCP OSM binary file format.
	 * 
	 * @param sFile Path of the original OSM file that is being converted
	 * @param oStringPool String pool contain all of the tag's keys and values
	 * @param oNodes Node to write to the file
	 * @param oWays Way to write to the file
	 * @throws Exception
	 */
	public static void writeBin(String sFile, StringPool oStringPool, ArrayList<OsmNode> oNodes, ArrayList<OsmWay> oWays)
	   throws Exception
	{
		Path oBinFile = Paths.get(sFile.substring(0, sFile.indexOf(".", sFile.lastIndexOf("/"))) + ".bin");
		oStringPool.intern("unclassified");
		try (DataOutputStream oBin = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(oBinFile))))
		{
			ArrayList<String> oPool = oStringPool.toList();
			oBin.writeInt(oPool.size()); // write the number of strings in the pool
			
			for (String oString : oPool) // write all strings
				oBin.writeUTF(oString);
			oBin.writeInt(oBin.size() + 8); // write position were nodes start
			oBin.writeInt(oNodes.size()); //write number of nodes
			int nCount = 0;
			for (OsmNode oNode : oNodes) // write each node
			{
				oNode.m_nFp = oBin.size();
				oBin.writeByte(oNode.m_nOriginalRefCount);
				oBin.writeInt(oNode.m_nLon);
				oBin.writeInt(oNode.m_nLat);
				oBin.writeInt(oNode.size()); // number of tags
				for (Entry<String, String> oKV : oNode.entrySet())
				{
					oBin.writeInt(Collections.binarySearch(oPool, oKV.getKey()));
					oBin.writeInt(Collections.binarySearch(oPool, oKV.getValue()));
				}
			}
			
			int nMaxFp = oNodes.get(oNodes.size() - 1).m_nFp;
			oBin.writeInt(oWays.size()); // write number of ways
			for (OsmWay oWay : oWays)
			{
				oWay.m_nFp = oBin.size();
				oBin.writeInt(oWay.m_oNodes.size());
				for (OsmNode oNode : oWay.m_oNodes)
				{
					if (oNode.m_nFp == 0 || oNode.m_nFp > nMaxFp)
					{
						throw new IOException("Invalid node position " + oNode.m_nFp + " node number " + nCount);
					}
					nCount++;
					oBin.writeInt(oNode.m_nFp);
				}
				
				oBin.writeInt(oWay.size()); // write number of tags
				for (Entry<String, String> oKV : oWay.entrySet())
				{
					oBin.writeInt(Collections.binarySearch(oPool, oKV.getKey()));
					oBin.writeInt(Collections.binarySearch(oPool, oKV.getValue()));
				}
			}
		}
	}
	
	
	/**
	 * Writes the index file for the given OSM file which assists in quick look 
	 * up of node and way definitions via a spatial index using hash buckets.
	 * 
	 * @param sFile Path of the original OSM file that is being converted
	 * @param oBuckets List of hash buckets that were generated from the file
	 * @throws Exception
	 */
	public static void writeHashIndex(String sFile, ArrayList<HashBucket> oBuckets)
	   throws Exception
	{
		Path oIndexFile = Paths.get(sFile.substring(0, sFile.indexOf(".", sFile.lastIndexOf("/"))) +  ".bin.ndx");
		try (DataOutputStream oNdx = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(oIndexFile))))
		{
			oNdx.writeInt(oBuckets.size()); // write the number of buckets
			for (HashBucket oBucket : oBuckets)
			{
				oNdx.writeInt(oBucket.m_nHash); // write hash bucket index
				oNdx.writeInt(oBucket.size()); // write the number of ways in the bucket
				for (OsmWay oWay : oBucket)
				{
					oNdx.writeInt(oWay.m_nFp); // write the file position of the way
					for (OsmNode oNode : oWay.m_oNodes)
					{
						int nIndex = Collections.binarySearch(oBucket.m_oNodes, oNode, OsmObject.FPCOMP); // collect the nodes that are in this bucket
						if (nIndex < 0)
							oBucket.m_oNodes.add(~nIndex, oNode);
					}
				}
				oNdx.writeInt(oBucket.m_oNodes.size()); // write the number of nodes
				for (OsmNode oNode : oBucket.m_oNodes)
					oNdx.writeInt(oNode.m_nFp); // write the file position of each node
			}
		}
	}
}
