/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.geosrv.osm;

import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.Mercator;
import imrcp.geosrv.WayNetworks;
import imrcp.system.dbf.DbfResultSet;
import imrcp.system.FileUtil;
import imrcp.system.StringPool;
import imrcp.system.shp.ShpReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.Logger;

/**
 * This class contains utility methods for Open Street Maps(OSM) objects and algorithms used
 * for generating road network models
 * @author aaron.cherney
 */
public abstract class OsmUtil
{
	/**
	 * Angle threshold used in the merge algorithm
	 */
	private static final double MERGE_ANGLE = Math.PI * 2. / 3.0;

	
	/**
	 * Angle threshold used for identifying "straight" angles
	 */
	private static final double STRAIGHT_ANGLE = Math.PI * .95;

	
	/**
	 * Contains all of the "highway" tag values for OSM ways
	 */
	public static final String[] ALL_ROADS = {"motorway", "trunk", "primary", "secondary", "tertiary", "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link", "unclassified", "residential"};

	
	/**
	 * Gets the distance between two nodes in decimal degrees scaled to 7 decimal
	 * places. This function does not take in account that the earth is spherical,
	 * should only be used for points that are relatively close.
	 * 
	 * @param o1 node 1
	 * @param o2 node 2
	 * @return Distance between the two nodes in decimal degrees scaled to 7 
	 * decimal places.
	 */
	public static double dist(OsmNode o1, OsmNode o2)
	{
		double dX1 = o1.m_nLon;
		double dY1 = o1.m_nLat;
		double dX2 = o2.m_nLon;
		double dY2 = o2.m_nLat;
		double dX = dX2 - dX1;
		double dY = dY2 - dY1;
		return Math.sqrt(dX * dX + dY * dY);
	}

	
	/**
	 * Determines if the nodes are "equal" by using their distance. If the distance
	 * between the two nodes is less than or equal to the given tolerance, the
	 * nodes are considered "equal".
	 * 
	 * @param o1 node 1
	 * @param o2 node 2
	 * @param nTol tolerance in decimal degrees scaled to 7 decimal places
	 * @return true if the distance between the two nodes is less than or equal 
	 * to the given tolerance.
	 */
	public static boolean nodesEqual(OsmNode o1, OsmNode o2, int nTol)
	{
		return dist(o1, o2) <= nTol;
	}

	
	/**
	 * Attempts to merge any two OsmWays in the list that share a start/end point
	 * that is not shared by any other OsmWay.
	 * 
	 * @param oWays contains the OsmWays to merge
	 * @return A list of the OsmWays after merging is complete.
	 * @throws IOException
	 */
	public static ArrayList<OsmWay> merge(ArrayList<OsmWay> oWays)
		throws IOException
	{
		ArrayList<OsmWay> oReturn = new ArrayList();
		int nLimit = oWays.size();
		Comparator<OsmNode> oComparator = OsmNode.NODEBYTEID;
		for (int nOuter = 0; nOuter < nLimit; nOuter++) // iterate through all ways
		{
			OsmWay oCur = oWays.get(nOuter);
			OsmNode oCurStart = oCur.m_oNodes.get(0);
			OsmNode oCurEnd = oCur.m_oNodes.get(oCur.m_oNodes.size() - 1);
				
			boolean bCheckStart = oCurStart.m_nOriginalRefCount == 2 && oCurStart.m_oRefs.size() == 2;
			boolean bCheckEnd = oCurEnd.m_nOriginalRefCount == 2 && oCurEnd.m_oRefs.size() == 2;
			
			if (!bCheckStart && !bCheckEnd) // a merge can only happen when a start/end node of a way is shared by exactly one other way
			{
				oReturn.add(oCur);
				continue;
			}
			
			int nStartIndex = Integer.MIN_VALUE;
			int nEndIndex = Integer.MIN_VALUE;
			int nStartType = -1;
			int nEndType = -1;

			for (int nInner = 0; nInner < nLimit; nInner++) // compare each way to every other way
			{
				if (nInner == nOuter) // don't compare a way to its self
					continue;
				OsmWay oCmp = oWays.get(nInner);
				if (oCur.m_bBridge != oCmp.m_bBridge || !GeoUtil.boundingBoxesIntersect(oCur.m_nMinLon, oCur.m_nMinLat, oCur.m_nMaxLon, oCur.m_nMaxLat, oCmp.m_nMinLon, oCmp.m_nMinLat, oCmp.m_nMaxLon, oCmp.m_nMaxLat)) // don't merge a bridge with a non bridge, can skip if the ways are not close to each other
					continue;
				OsmNode oCmpStart = oCmp.m_oNodes.get(0);
				OsmNode oCmpEnd = oCmp.m_oNodes.get(oCmp.m_oNodes.size() - 1);
				if (bCheckStart)
				{
					if (oComparator.compare(oCurStart, oCmpStart) == 0) // the start of each way is the same
					{
						nStartIndex = nInner;
						nStartType = 0;
						bCheckStart = false;
					}
					else if (oComparator.compare(oCurStart, oCmpEnd) == 0) // the start of cur is the end of cmp
					{
						nStartIndex = nInner;
						nStartType = 1;
						bCheckStart = false;
					}
				}
				
				if (bCheckEnd)
				{
					if (oComparator.compare(oCurEnd, oCmpStart) == 0) // the end of cur is the start of cmp
					{
						nEndIndex = nInner;
						nEndType = 2;
						bCheckEnd = false;
					}
					else if (oComparator.compare(oCurEnd, oCmpEnd) == 0) // the end of each way is the same
					{
						nEndIndex = nInner;
						nEndType = 3;
						bCheckEnd = false;
					}
				}
				
				if (!bCheckStart && !bCheckEnd) // found the necessary connecting ways so don't check any more
					break;
			}
			int nMin = Integer.MAX_VALUE;
			int nMax = Integer.MIN_VALUE;
			if (nStartIndex != Integer.MIN_VALUE) // the start of cur shares a node with exactly one other way
			{
				if (merge(oCur, oWays.get(nStartIndex), nStartType, MERGE_ANGLE)) // so merge the two ways
					nMin = nMax = nStartIndex;
			}
			if (nEndIndex != Integer.MIN_VALUE) // the end of cur shares a node with exactly one other way
			{
				if (merge(oCur, oWays.get(nEndIndex), nEndType, MERGE_ANGLE)) // so merge the two ways
				{
					if (nEndIndex < nMin)
						nMin = nEndIndex;
					if (nEndIndex > nMax)
						nMax = nEndIndex;
				}
			}
			if (nMax >= 0)
			{
				--nOuter; // recompare current to the rest of the ways since its endpoints have changed
				if (nMin == nMax)
				{
					if (nOuter > nMin)
						--nOuter;
					oWays.remove(nMin); // remove the way that got merged
					--nLimit; // update the number of ways in the list
				}
				else // both endpoints merged
				{
					if (nOuter > nMin)
						--nOuter;
					if (nOuter > nMax)
						--nOuter;
					oWays.remove(nMax); // remove both ways that got merged
					oWays.remove(nMin);
					nLimit -= 2; // update the number of ways in the list
				}
			}
			else
				oReturn.add(oCur);
		}
		return oReturn;
	}

	
	/**
	 * Merges the two roadway segments regardless of the angle between them and
	 * their bridge flags.
	 * 
	 * @param oCur roadway segment 1
	 * @param oCmp roadway segment 2
	 * @return a new instance of an OsmWay with the merged nodes of the original
	 * two ways
	 * @throws IOException
	 */
	public static OsmWay forceMerge(OsmWay oCur, OsmWay oCmp)
		throws IOException
	{
		ArrayList<OsmNode> oCurNodes = oCur.m_oNodes;
		ArrayList<OsmNode> oCmpNodes = oCmp.m_oNodes;
		OsmNode oCurStart = oCurNodes.get(0);
		OsmNode oCurEnd = oCurNodes.get(oCurNodes.size() - 1);
		OsmNode oCmpStart = oCmpNodes.get(0);
		OsmNode oCmpEnd = oCmpNodes.get(oCmpNodes.size() - 1);
		Comparator<OsmNode> oComparator = OsmNode.NODEBYTEID;
		
		int nType = -1;
		if (oComparator.compare(oCurStart, oCmpStart) == 0) // determine which points are the same
			nType = 0;
		else if (oComparator.compare(oCurStart, oCmpEnd) == 0)
			nType = 1;
		else if (oComparator.compare(oCurEnd, oCmpStart) == 0)
			nType = 2;
		else if (oComparator.compare(oCurEnd, oCmpEnd) == 0)
			nType = 3;
		
		OsmWay oReturn = new OsmWay();
		ArrayList<OsmNode> oNew = oReturn.m_oNodes;
		switch (nType)
		{
			case 0:
				// cur start is the same as cmp start
				int nIndex = oCmpNodes.size();
				while (nIndex-- > 1)
					oNew.add(oCmpNodes.get(nIndex));
				oNew.addAll(oCurNodes);
				break;
			case 1:
				// cur start is the same as cmp end
				oNew.addAll(oCmpNodes);
				oNew.remove(oNew.size() - 1);
				oNew.addAll(oCurNodes);
				break;
			case 2:
				// cur end is the same as cmp start
				oNew.addAll(oCurNodes);
				oNew.remove(oNew.size() - 1);
				oNew.addAll(oCmpNodes);
				break;
			case 3:
				// cur end is the same as cmp end
				oNew.addAll(oCurNodes);
				int nCmpIndex = oCmpNodes.size() - 1;
				while (nCmpIndex-- > 0)
					oNew.add(oCmpNodes.get(nCmpIndex));
				break;
			default:
				return null;
		}
		oReturn.putAll(oCmp); // get all of the tags from each way
		oReturn.putAll(oCur);
		oReturn.m_bBridge = oCmp.m_bBridge || oCur.m_bBridge; // combine their bridge flag
		oReturn.setMinMax();
		oReturn.generateId();
		oReturn.updateRefs();
		oReturn.calcMidpoint();
		oCmp.removeRefs(); // cmp and cur no longer exist so remove their references from the nodes
		oCur.removeRefs();
		
		return oReturn;
	}
	
	
	/**
	 * Splits the given roadway segment at the given index of its node list. The
	 * reference of {@code oWay} will contain the nodes from 0 to {@link nSplitIndex}
	 * and the returned OsmWay with contains the nodes from {@link nSplitIndex}
	 * to the end of the list.
	 * 
	 * @param oWay roadway segment to split
	 * @param nSplitIndex index in the node list to split the way at
	 * @return the new OsmWay created from the nodes from {@link nSplitIndex}
	 * to the end of the list.
	 * @throws IOException
	 */
	public static OsmWay forceSplit(OsmWay oWay, int nSplitIndex)
	   throws IOException
	{
		OsmWay oReturn = new OsmWay();
		oReturn.putAll(oWay);
		ArrayList<OsmNode> oNewNodes = new ArrayList();
		
		oWay.removeRefs();
		for (int nIndex = 0; nIndex <= nSplitIndex; nIndex++) // get the first set of nodes
			oNewNodes.add(oWay.m_oNodes.get(nIndex));
		
		for (int nIndex = nSplitIndex; nIndex < oWay.m_oNodes.size(); nIndex++) // get the second set of nodes
			oReturn.m_oNodes.add(oWay.m_oNodes.get(nIndex));
		
		oWay.m_oNodes = oNewNodes; // book keeping for the original way
		oWay.updateRefs();
		oWay.generateId();
		oWay.calcMidpoint();
		oWay.setMinMax();
		
		oReturn.m_bBridge = oWay.m_bBridge; // book keeping for the new way
		oReturn.m_sName = oWay.m_sName;
		oReturn.setMinMax();
		oReturn.generateId();
		oReturn.calcMidpoint();
		oReturn.updateRefs();
		
		return oReturn;
	}
	
	
	/**
	 * Attempts to merge the new roadway segments. The ways will not be merged if
	 * the angle formed between them is less than {@code dAngleThreshold} or if
	 * an invalid {@code nMergeType} is used. The reference of {@code oCur} will
	 * contain the nodes of the both segment and represents the merged roadway.
	 * 
	 * @param oCur roadway segment 1
	 * @param oCmp roadway segment 2
	 * @param nMergeType a value from 1 to 4:
	 * 1 = oCur start node is the same as oCmp start node
	 * 2 = oCur start node is the same as oCmp end node
	 * 3 = oCur end node is the same as oCmp start node
	 * 4 = oCur end node is the same as oCmp end node
	 * @param dAngleThreshold the angle that the angle formed between the two 
	 * roadway segments must be greater than to be merged. This is used to avoid
	 * merging segments that have a sharp turn.
	 * @return true if the segments are merged, otherwise false
	 * @throws IOException
	 */
	public static boolean merge(OsmWay oCur, OsmWay oCmp, int nMergeType, double dAngleThreshold)
		throws IOException
	{
		ArrayList<OsmNode> oNew = new ArrayList();
		ArrayList<OsmNode> oCurNodes = oCur.m_oNodes;
		ArrayList<OsmNode> oCmpNodes = oCmp.m_oNodes;
		switch (nMergeType)
		{
			case 0:
				// cur start is the same as cmp start
				OsmNode oN1 = oCurNodes.get(1);
				OsmNode oN2 = oCurNodes.get(0);
				OsmNode oN3 = oCmpNodes.get(1);
				double dAngle = GeoUtil.angle(oN1.m_nLon, oN1.m_nLat, oN2.m_nLon, oN2.m_nLat, oN3.m_nLon, oN3.m_nLat);
				if (dAngle < dAngleThreshold)
					return false;
				int nIndex = oCmpNodes.size();
				while (nIndex-- > 1)
					oNew.add(oCmpNodes.get(nIndex));
				oNew.addAll(oCurNodes);
				break;
			case 1:
				// cur start is the same as cmp end
				oN1 = oCurNodes.get(1);
				oN2 = oCurNodes.get(0);
				oN3 = oCmpNodes.get(oCmpNodes.size() - 2);
				dAngle = GeoUtil.angle(oN1.m_nLon, oN1.m_nLat, oN2.m_nLon, oN2.m_nLat, oN3.m_nLon, oN3.m_nLat);
				if (dAngle < dAngleThreshold)
					return false;
				oNew.addAll(oCmpNodes);
				oNew.remove(oNew.size() - 1);
				oNew.addAll(oCurNodes);
				break;
			case 2:
				// cur end is the same as cmp start
				oN1 = oCurNodes.get(oCurNodes.size() - 2);
				oN2 = oCmpNodes.get(0);
				oN3 = oCmpNodes.get(1);
				dAngle = GeoUtil.angle(oN1.m_nLon, oN1.m_nLat, oN2.m_nLon, oN2.m_nLat, oN3.m_nLon, oN3.m_nLat);
				if (dAngle < dAngleThreshold)
					return false;
				oNew.addAll(oCurNodes);
				oNew.remove(oNew.size() - 1);
				oNew.addAll(oCmpNodes);
				break;
			case 3:
				// cur end is the same as cmp end
				oN1 = oCurNodes.get(oCurNodes.size() - 2);
				oN2 = oCmpNodes.get(oCmpNodes.size() - 1);
				oN3 = oCmpNodes.get(oCmpNodes.size() - 2);
				dAngle = GeoUtil.angle(oN1.m_nLon, oN1.m_nLat, oN2.m_nLon, oN2.m_nLat, oN3.m_nLon, oN3.m_nLat);
				if (dAngle < dAngleThreshold)
					return false;
				oNew.addAll(oCurNodes);
				int nCmpIndex = oCmpNodes.size() - 1;
				while (nCmpIndex-- > 0)
					oNew.add(oCmpNodes.get(nCmpIndex));
				break;
			default:
				return false;
		}
		
		oCur.m_oNodes = oNew; // book keeping
		oCur.setMinMax();
		oCur.removeRefs();
		oCur.generateId();
		oCur.updateRefs();
		oCur.calcMidpoint();
		oCmp.removeRefs();
		
		return true;
	}

	
	/**
	 * Splits the roadway segments in the given list if they have an inner node
	 * that is referenced by more than one way.
	 * 
	 * @param oWays list of roadway segments to split
	 * @return A list of the OsmWays after spliting is complete.
	 * @throws Exception
	 */
	public static ArrayList<OsmWay> split(ArrayList<OsmWay> oWays)
	   throws Exception
	{
		ArrayList<OsmWay> oReturn = new ArrayList();
		int nLimit = oWays.size();
		Comparator<OsmWay> oWayComparator = OsmWay.WAYBYTEID;
		for (int nOuter = 0; nOuter < nLimit; nOuter++) // iterate through all ways
		{
			OsmWay oCur = oWays.get(nOuter);
			boolean bUpdateRef = false;
			OsmWay oNew = new OsmWay(); // used to collect nodes and split the current way if needed
			oNew.putAll(oCur);
			oNew.m_sName = oCur.m_sName;
			int nNodeLimit = oCur.m_oNodes.size() - 1;
			oNew.m_oNodes.add(oCur.m_oNodes.get(0));
			for (int nCurNode = 1; nCurNode < nNodeLimit; nCurNode++) // check all nodes of the current way except endpoints to see if another way shares that node
			{
				OsmNode oCurNode = oCur.m_oNodes.get(nCurNode);
				oNew.m_oNodes.add(oCurNode);
				if (oCurNode.m_nOriginalRefCount == 1) // only referenced by this way so don't need to split
					continue;

				oNew.setMinMax();
				oNew.calcMidpoint();
				oNew.setBridge();
				oNew.generateId();
				oReturn.add(oNew); // add a way of the nodes so far
				if (bUpdateRef) // only need to update the references after the first iteration
				{
					for (int i = 0 ; i < oNew.m_oNodes.size(); i++)
					{
						OsmNode oNode = oNew.m_oNodes.get(i);

						int nSearch = Collections.binarySearch(oNode.m_oRefs, oNew, oWayComparator); // add the new way to its reference list
						if (nSearch < 0)
							oNode.m_oRefs.add(~nSearch, oNew);

						if (i > 0) // the first node is the endpoint of the previous way so only do after the first iteration
						{
							nSearch = Collections.binarySearch(oNode.m_oRefs, oCur, oWayComparator); // remove the original way from its reference list
							if (nSearch >= 0)
								oNode.m_oRefs.remove(nSearch);
						}
					}
				}
				bUpdateRef = true;
				oNew = new OsmWay();
				oNew.putAll(oCur);
				oNew.m_sName = oCur.m_sName;
				oNew.m_oNodes.add(oCurNode);
			}
			if (bUpdateRef)
			{
				oNew.m_oNodes.add(oCur.m_oNodes.get(oCur.m_oNodes.size() - 1));
				oNew.setMinMax();
				oNew.calcMidpoint();
				oNew.setBridge();
				oNew.generateId();
				oReturn.add(oNew);

				for (int i = 0 ; i < oNew.m_oNodes.size(); i++) // for each node
				{
					OsmNode oNode = oNew.m_oNodes.get(i);

					int nSearch = Collections.binarySearch(oNode.m_oRefs, oNew, oWayComparator); // add the new way to its reference list
					if (nSearch < 0)
						oNode.m_oRefs.add(~nSearch, oNew);

					if (i > 0) // the first node is the endpoint of the previous way so only do after the first iteration
					{
						nSearch = Collections.binarySearch(oNode.m_oRefs, oCur, oWayComparator); // remove the original way from its reference list
						if (nSearch >= 0)
							oNode.m_oRefs.remove(nSearch);
					}
				}
			}
			else
				oReturn.add(oCur);
		}
		return oReturn;
	}
	
	
	/**
	 * Determines if the given string is in the given string array.
	 * 
	 * @param sType string to look for
	 * @param sInclude array of strings to look in
	 * @return true if {@code sType} is in {@code sInclude} otherwise false.
	 */
	public static boolean include(String sType, String[] sInclude)
	{
		if (sType == null)
			return false;
	
		int nIndex = sInclude.length;
		while (nIndex-- > 0)
			if (sType.compareTo(sInclude[nIndex]) == 0)
				return true;
	
		return false;
	}
	
	
	/**
	 * Creates a new list and adds all of the nodes in the given list that have 
	 * at least one roadway segment in its reference list. A new list is created
	 * instead of removing nodes from the given list to aid in performance since
	 * the number of nodes can be a large number.
	 * 
	 * @param oNodes list of nodes to check for nodes with no roadway segments
	 * in their reference list.
	 * @return a list contains all of the nodes in the list that had at least 
	 * one roadway segment in their reference list.
	 */
	public static ArrayList<OsmNode> removeZeroRefs(ArrayList<OsmNode> oNodes)
	{
		int nIndex = oNodes.size();
		int nCount = 0;
		while (nIndex-- > 0)
		{
			OsmNode oTemp = oNodes.get(nIndex);
			if (oTemp.m_oRefs.isEmpty())
				++nCount;
		}
		
		ArrayList<OsmNode> oRet = new ArrayList(oNodes.size() - nCount);
		for (nIndex = 0; nIndex < oNodes.size(); nIndex++)
		{
			OsmNode oTemp = oNodes.get(nIndex);
			if (!oTemp.m_oRefs.isEmpty())
				oRet.add(oTemp);
		}
		
		oNodes.clear();
		return oRet;
	}
	
	
	/**
	 * Determines the type of ramp for each roadway segment in the given list and
	 * the number of on and off ramps highway segments have. It does this by 
	 * checking the "highway" from the OSM database. A "connector" is a segment
	 * with a highway tag of "motorway_link" or "trunk_link" that is in between
	 * two segments with a highway tag of "motorway" or "trunk". A "ramp" is a segment
	 * with a highway tag of "motorway_link" or "trunk_link" that is in between
	 * a segment with a highway of "motorway" or "trunk" and other segment with 
	 * a highway tag of lower classification (primary, secondary, etc).
	 * 
	 * @param oWays List of roadway segments to process, represents a road network
	 * @param oStates maps state names to the geometry of their border. This should
	 * contain the states that intersect the road network
	 * @param sOsmDir base directory for osm files
	 * @param sFilter filter strings used to create the road network
	 * @param oHashes Maps hash indices to the roadway segments contained in that
	 * grid.
	 */
	public static void determineRamps(ArrayList<OsmWay> oWays, HashMap<String, ArrayList<int[]>> oStates, String sOsmDir, String[] sFilter, HashMap<String, HashMap<Integer, ArrayList<OsmWay>>> oHashes)
	{
		int nLimit = oWays.size();
		
		ArrayList<OsmWay> oMwTrunkLinks = new ArrayList();
		for (int nOuter = 0; nOuter < nLimit; nOuter++) // iterate through all ways
		{
			OsmWay oCur = oWays.get(nOuter);
			if (oCur.m_yLinkType != OsmWay.NOTSET)
				continue;
			String sHighway = oCur.get("highway");
			if (!sHighway.contains("_link")) // determine on and off ramps for motorways and trunks
			{
				oCur.m_yLinkType = OsmWay.NOTRAMP; // these are not ramps
				if (sHighway.compareTo("motorway") != 0 && sHighway.compareTo("trunk") != 0) // if not a highway don't look for on and off ramps
					continue;
				
				ArrayList<OsmWay> oFrom = oCur.getFromWays();
				ArrayList<OsmWay> oTo = oCur.getToWays();
				int nOffRamp = 0;
				int nOnRamp = 0;
				for (OsmWay oCmp : oFrom) // determine on ramps
				{
					String sCmpHighway = oCmp.get("highway");
					if (sCmpHighway.contains("_link"))
					{
						setRamps(oCmp, oHashes, false, oStates, sOsmDir, sFilter);
						
						++nOnRamp;
						if (oCmp.m_yLinkType != OsmWay.NOTSET)
						{
							oCmp.put("linktype", oCmp.m_yLinkType == OsmWay.CONNECTOR ? "connector" : "ramp");
						}
					}
				}
				oCur.put("ingress", Integer.toString(nOnRamp));
				for (OsmWay oCmp : oTo) // determine off ramps
				{
					String sCmpHighway = oCmp.get("highway");
					if (sCmpHighway.contains("_link"))
					{
						setRamps(oCmp, oHashes, true, oStates, sOsmDir, sFilter);
						
						++nOffRamp;
						if (oCmp.m_yLinkType != OsmWay.NOTSET)
						{
							oCmp.put("linktype", oCmp.m_yLinkType == OsmWay.CONNECTOR ? "connector" : "ramp");
						}
					}
				}
				oCur.put("egress", Integer.toString(nOffRamp));
			}
			else // accumulate possible ramps and connectors
			{
				if (sHighway.contains("trunk") || sHighway.contains("motorway"))
				{
					oMwTrunkLinks.add(oCur);
				}
			}
		}

		for (OsmWay oWay : oMwTrunkLinks)
		{
			if (oWay.m_bTraversed) // skip ways that have already been processed
				continue;
			oWay.m_yLinkType = setRamps(oWay, oHashes, true, oStates, sOsmDir, sFilter);
			if (oWay.m_yLinkType == OsmWay.NOTSET)
				oWay.m_yLinkType = setRamps(oWay, oHashes, false,  oStates, sOsmDir, sFilter);
		}
			
	}
	
	
	/**
	 * Wrapper for {@link #determineRamps(java.util.ArrayList, java.util.HashMap, java.lang.String, java.lang.String[], java.util.HashMap)}
	 * 
	 * @param oWays List of roadway segments to process, represents a road network
	 * @param oStates maps state names to the geometry of their border. This should
	 * contain the states that intersect the road network
	 * @param sOsmDir base directory for osm files
	 * @param sFilter filter strings used to create the road network
	 */
	public static void determineRamps(ArrayList<OsmWay> oWays, HashMap<String, ArrayList<int[]>> oStates, String sOsmDir, String[] sFilter)
	{
		HashMap<String, HashMap<Integer, ArrayList<OsmWay>>> oHashes = new HashMap(); // first key is state name, second key is hash value
		for (String sState : oStates.keySet())
			oHashes.put(sState, new HashMap());
		determineRamps(oWays, oStates, sOsmDir, sFilter, oHashes);
		
	}
	
	
	/**
	 * This is a recurvise function that starts processing a segment with a
	 * "highway" tag of "motorway_link" or "trunk_link" and continues to process
	 * connecting segments until a segment with a different tag is found. Depending
	 * on the "highway" tag of the connecting segments each "motorway_link" or
	 * "trunk_link" segment's {@link  OsmWay#m_yLinkType} is set. The hash map
	 * of states is needed to look up segments not in the network that could
	 * aid in determining the type of ramp.
	 * 
	 * @param oFirstLink The previous OsmWay that this function was called on, if this
	 * is the first time the function is called in this execution stack then it
	 * is the OsmWay being processed first
	 * @param oHashes Maps state names to a HashMap that maps hash indices
	 * to a list of OsmWay that are contained in that grid
	 * @param bOffRamps true if processing off ramps, otherwise false (processing
	 * on ramps)
	 * @param oStates Maps state names to their border's geometry
	 * @param sOsmDir Base directory containing OSM files
	 * @param sFilter filter used to create the road network being processed
	 * @return The {@link OsmWay#m_yLinkType}
	 */
	private static byte setRamps(OsmWay oFirstLink, HashMap<String, HashMap<Integer, ArrayList<OsmWay>>> oHashes, boolean bOffRamps, HashMap<String, ArrayList<int[]>> oStates, String sOsmDir, String[] sFilter)
	{
		if (oFirstLink.m_yLinkType != OsmWay.NOTSET || oFirstLink.m_bTraversed)
			return oFirstLink.m_yLinkType;
		oFirstLink.m_bTraversed = true;
		ArrayList<OsmWay> oConnected = bOffRamps ? oFirstLink.getToWays() : oFirstLink.getFromWays();
		if (oConnected.isEmpty()) // some roads have been filtered so need to check the original file to see if any road connects to this one
		{
			OsmNode oEndpoint = bOffRamps ? oFirstLink.m_oNodes.get(oFirstLink.m_oNodes.size() - 1) : oFirstLink.m_oNodes.get(0);
			ArrayList<OsmWay> oHashedWays = null;
			for (Map.Entry<String, ArrayList<int[]>> oState : oStates.entrySet())
			{
				for (int[] nPart : oState.getValue())
				{
					if (GeoUtil.isPointInsideRingAndHoles(nPart, oEndpoint.m_nLon, oEndpoint.m_nLat)) // make sure the node is in the geometry of the state
					{
						int nHash = HashBucket.hashLonLat(oEndpoint.m_nLon, oEndpoint.m_nLat);
						if (!oHashes.get(oState.getKey()).containsKey(nHash)) // if the hash hasn't been loaded
						{
							oHashedWays = new ArrayList();
							try
							{
								new OsmBinParser().parseHash(sOsmDir + oState.getKey() + "-latest.bin", nHash, new ArrayList(), oHashedWays); // get the ways in the hash grid
							}
							catch (Exception oEx)
							{
								oEx.printStackTrace();
							}
							oHashes.get(oState.getKey()).put(nHash, oHashedWays);
							break;
						}
						oHashedWays = oHashes.get(oState.getKey()).get(nHash);
					}
				}
				if (oHashedWays != null)
					break;
			}
			
			for (OsmWay oCmp : oHashedWays)
			{
				String sCmpHighway = oCmp.get("highway");
				if (include(sCmpHighway, sFilter)) // skip road types already in the network
					continue;
				
				if (!GeoUtil.boundingBoxesIntersect(oFirstLink.m_nMinLon, oFirstLink.m_nMinLat, oFirstLink.m_nMaxLon, oFirstLink.m_nMaxLat, oCmp.m_nMinLon, oCmp.m_nMinLat, oCmp.m_nMaxLon, oCmp.m_nMaxLat)) // skip roads that have no chance of intersecting
					continue;
				
				for (int nCmpNode = 0; nCmpNode < oCmp.m_oNodes.size(); nCmpNode++) // check each node of the other way
				{
					OsmNode oCmpNode = oCmp.m_oNodes.get(nCmpNode);
					if (OsmNode.NODEBYTEID.compare(oEndpoint, oCmpNode) == 0)
					{
						if (sCmpHighway.compareTo("motorway") == 0 || sCmpHighway.compareTo("trunk") == 0)
						{
							oFirstLink.m_yLinkType = OsmWay.CONNECTOR;
							oFirstLink.put("linktype", "connector");
						}
						else if (sCmpHighway.compareTo("motorway_link") == 0 || sCmpHighway.compareTo("trunk_link") == 0)
						{
							oFirstLink.m_yLinkType = setRamps(oCmp, oHashes, bOffRamps, oStates, sOsmDir, sFilter);
							if (oFirstLink.m_yLinkType != OsmWay.NOTSET)
								oFirstLink.put("linktype", oCmp.m_yLinkType == OsmWay.CONNECTOR ? "connector" : "ramp");
						}
						else
						{
							oFirstLink.m_yLinkType = OsmWay.RAMP;
							oFirstLink.put("linktype", "ramp");
						}
						break;
					}
				}
				
				if (oFirstLink.m_yLinkType != OsmWay.NOTSET)
					break;
			}
			
			if (oFirstLink.m_yLinkType == OsmWay.NOTSET) // after checking hashed ways there the ramp is still not set so there is no connecting road so set it as a ramp
			{
				oFirstLink.m_yLinkType = OsmWay.RAMP;
				oFirstLink.put("linktype", "ramp");
			}
			
		}
		else
		{
			byte yType = oFirstLink.m_yLinkType;
			for (OsmWay oCmp : oConnected)
			{
				String sCmpHighway = oCmp.get("highway");
				if (sCmpHighway.compareTo("motorway") == 0 || sCmpHighway.compareTo("trunk") == 0)
					yType = OsmWay.CONNECTOR;
				else if (sCmpHighway.compareTo("motorway_link") == 0 || sCmpHighway.compareTo("trunk_link") == 0)
					yType = setRamps(oCmp, oHashes, bOffRamps, oStates, sOsmDir, sFilter);
				else
					yType = OsmWay.RAMP;
				
				oFirstLink.m_yLinkType = (byte)Math.max(oFirstLink.m_yLinkType, yType); // if a ramp has multiple paths take the larger value (connector) of the paths
				if (oFirstLink.m_yLinkType != OsmWay.NOTSET)
					oFirstLink.put("linktype", oFirstLink.m_yLinkType == OsmWay.CONNECTOR ? "connector" : "ramp");
			}
		}
		
		return oFirstLink.m_yLinkType;
	}
	
	
	/**
	 * Attempts to separate bi directional roads into two one directional roads
	 * with the given distance from the original center line.
	 * 
	 * @param oWays List of roadway segments to process
	 * @param oNodes List of nodes that make up all the roadway segments. New nodes
	 * will be added to this list.
	 * @param nSepDist distance in decimal degrees scaled to 7 decimal places
	 * to place nodes of the newly separated roadway segments from the center line
	 * of the bi-directional road being separated.
	 * @return A list containing the roadway segments after separating is finished.
	 * @throws IOException
	 */
	public static ArrayList<OsmWay> separate(ArrayList<OsmWay> oWays, ArrayList<OsmNode> oNodes, int nSepDist)
	   throws IOException
	{
		ArrayList<OsmWay> oReturn = new ArrayList();

		int nCount = 0;
		for (int nWayIndex = 0; nWayIndex < oWays.size(); nWayIndex++)
		{
			OsmWay oCur = oWays.get(nWayIndex);
			String sOneway = oCur.get("oneway");

			if (sOneway != null && sOneway.compareTo("yes") == 0) // already uni-directional so don't need to separate
			{
				oReturn.add(oCur); // add to final list
				continue;
			}
			if (oCur.m_oNodes.size() < 2) // skip invalid roadway segments
				continue;
			
			ArrayList<OsmNode> oCurNodes = oCur.m_oNodes;
			int nLimit = oCurNodes.size();
			OsmWay oPosWay = new OsmWay(nLimit); // allocate space for the two new roadway segments
			OsmWay oNegWay = new OsmWay(nLimit);
			ArrayList<OsmNode> oPosReverse = new ArrayList(nLimit);
			ArrayList<OsmNode> oPosNodes = oPosWay.m_oNodes;
			ArrayList<OsmNode> oNegNodes = oNegWay.m_oNodes;
			--nLimit; // the main separate loop is only for inner nodes, the endpoint is processed separately
			
			OsmNode oStart = oCur.m_oNodes.get(0);
			OsmNode oEnd = oCur.m_oNodes.get(oCur.m_oNodes.size() - 1);
			
			separateStartPoint(oCur, oNegNodes, oPosReverse, nSepDist, oNodes);
			
			for (int nNodeIndex = 1; nNodeIndex < nLimit; nNodeIndex++) // for each inner node
			{
				OsmNode o1 = oCurNodes.get(nNodeIndex - 1);
				OsmNode o2 = oCurNodes.get(nNodeIndex);
				OsmNode o3 = oCurNodes.get(nNodeIndex + 1);

				double dX1 = o1.m_nLon;
				double dY1 = o1.m_nLat;
				double dX2 = o2.m_nLon;
				double dY2 = o2.m_nLat;
				double dX3 = o3.m_nLon;
				double dY3 = o3.m_nLat;
				
				double dHdg = GeoUtil.heading(dX1, dY1, dX2, dY2); // get the average heading of the next two line segments of the polyline
				dHdg += GeoUtil.heading(dX2, dY2, dX3, dY3);
				dHdg /= 2;
				
				// get the negative tangent node position by subtracting pi/2(90 degrees) from the heading
				double dXd = dX2 + Math.sin(dHdg) * nSepDist; // cos(x - pi/2) = sin(x)
				double dYd = dY2 - Math.cos(dHdg) * nSepDist; // sin(x - pi/2) = -cos(x)
				OsmNode oNode = new OsmNode((int)Math.round(dYd), (int)Math.round(dXd));

				int nIndex = Collections.binarySearch(oNodes, oNode, OsmNode.NODEBYTEID);
				if (nIndex < 0) // if it is a new node 
					oNodes.add(~nIndex, oNode); // add it
				else
					oNode = oNodes.get(nIndex); // otherwise get the reference of the existing node
				
				if (GeoUtil.rightHand(dXd, dYd, dX1, dY1, dX2, dY2) > 0) // make sure the point is in the correct list based off direction of travel
					oNegNodes.add(oNode);
				else
					oPosReverse.add(oNode);
				
				// get the positive tangent node psoition by adding pi/2 (90 degrees) to the heading
				dXd = dX2 - Math.sin(dHdg) * nSepDist; // cos(x + pi/2) = -sin(x)
				dYd = dY2 + Math.cos(dHdg) * nSepDist; // sin(x + pi/2) = cos(x)
				oNode = new OsmNode((int)Math.round(dYd), (int)Math.round(dXd));
				
				nIndex = Collections.binarySearch(oNodes, oNode, OsmNode.NODEBYTEID);
				if (nIndex < 0) // if it is a new node 
					oNodes.add(~nIndex, oNode); // add it
				else
					oNode = oNodes.get(nIndex); // otherwise get the reference of the existing node
				if (GeoUtil.rightHand(dXd, dYd, dX1, dY1, dX2, dY2) < 0) // make sure the point is in the correct list based off direction of travel
					oPosReverse.add(oNode); 
				else
					oNegNodes.add(oNode);
			}
			
			separateEndPoint(oCur, oNegNodes, oPosReverse, nSepDist, oNodes);
			
			int nIndex = oPosReverse.size();
			while (nIndex-- > 0) // positive nodes are collected in reverse order so add them to the positive node list in correct order
				oPosNodes.add(oPosReverse.remove(nIndex));
			
			oPosWay.putAll(oCur);
			oNegWay.putAll(oCur);
			oPosWay.put("oneway", "yes");
			oNegWay.put("oneway", "yes");
			
			oPosWay.generateId();
			oNegWay.generateId();
			
			oPosWay.updateRefs();
			oNegWay.updateRefs();
			
			oReturn.add(oPosWay);
			oReturn.add(oNegWay);
			
			// keep track of the original start and end points for angle calculations at intersections
			oPosWay.m_oOriginalStart = oCur.m_oOriginalEnd != null ? oCur.m_oOriginalEnd : oCurNodes.get(nLimit - 1);
			oPosWay.m_oOriginalEnd = oCur.m_oOriginalStart != null ? oCur.m_oOriginalStart : oCurNodes.get(0);
			
			oNegWay.m_oOriginalStart = oCur.m_oOriginalStart != null ? oCur.m_oOriginalStart : oCurNodes.get(0);
			oNegWay.m_oOriginalEnd = oCur.m_oOriginalEnd != null ? oCur.m_oOriginalEnd : oCurNodes.get(nLimit - 1);
			
			oCur.m_oPosSep = oPosWay;
			oCur.m_oNegSep = oNegWay;
		}
		
		for (OsmWay oWay : oReturn) // book keeping for the nodes reference list
		{
			oWay.removeRefs();
			oWay.updateRefs();
		}
		
		return oReturn;
	}
	
	
	/**
	 * Separates the start node of the given roadway segment and adds the new
	 * points into the given neg and pos lists.
	 * 
	 * @param oWay The roadway segment being separated
	 * @param oNegNodes negative tangent node list
	 * @param oPosNodes positive tangent node list
	 * @param nSepDist  distance in decimal degrees scaled to 7 decimal places
	 * to place nodes of the newly separated roadway segments from the center line
	 * of the bi-directional road being separated.
	 * @param oNodes list of all the nodes making up roadway segments in the network
	 * being processed
	 * @throws IOException
	 */
	private static void separateStartPoint(OsmWay oWay, ArrayList<OsmNode> oNegNodes, ArrayList<OsmNode> oPosNodes, int nSepDist, ArrayList<OsmNode> oNodes)
		throws IOException
	{
		OsmNode o1;
		OsmNode o2 = oWay.m_oOriginalStart == null ? oWay.m_oNodes.get(0) : oWay.m_oOriginalStart;  // get the original start point so angle calculations are correct
		OsmNode o3 = oWay.m_oNodes.get(1);
		double dX1;
		double dY1;
		double dX2 = o2.m_nLon;
		double dY2 = o2.m_nLat;
		double dX3 = o3.m_nLon;
		double dY3 = o3.m_nLat;
		
		double dClosestPos = Math.PI;
		double dClosestNeg = Math.PI;
		double dPosAngle = Mercator.PI_OVER_TWO;
		double dNegAngle = Mercator.PI_OVER_TWO;
		double dHdg = GeoUtil.heading(dX2, dY2, dX3, dY3);
		ArrayList<OsmWay> oPosWays = new ArrayList();
		ArrayList<OsmWay> oNegWays = new ArrayList();
		ArrayList<OsmWay> oExtraWays = new ArrayList();
		for (OsmWay oComp : o2.m_oRefs) // for each way that shares the start node
		{
			if (OsmWay.WAYBYTEID.compare(oWay, oComp) == 0 || oComp.m_oNodes.size() < 2) // ignore the way being processed and invalid ways
				continue;

			oComp.m_bUseStart = false;
			o1 = oComp.m_oNodes.get(oComp.m_oNodes.size() - 1);
			if (OsmNode.NODEBYTEID.compare(o1, o2) != 0) // the start of this way is not the end of the comp
			{
				if (oComp.m_oOriginalEnd == null || OsmNode.NODEBYTEID.compare(oComp.m_oOriginalEnd, o2) != 0) // check if comp has already been separated meaning its original end node needs to be checked
				{
					oComp.m_bUseStart = true; // use the start node of comp since its end node is not the same as this way's start node
					if (oComp.m_oOriginalStart != null && OsmNode.NODEBYTEID.compare(oComp.m_oOriginalStart, o2) == 0) // comp has been separated and its original start is the start of this way
						o1 = oComp.m_oNodes.get(0);
					else // comp has not been separated so use its second node
						o1 = oComp.m_oNodes.get(1);
				}
				else
					o1 = oComp.m_oNodes.get(oComp.m_oNodes.size() - 2);
			}
			else // the start of this way is the same as the end of comp so use the next to last point of comp
				o1 = oComp.m_oNodes.get(oComp.m_oNodes.size() - 2);
			
			dX1 = o1.m_nLon;
			dY1 = o1.m_nLat;
			double dTemp = GeoUtil.angle(dX1, dY1, dX2, dY2, dX3, dY3);
			if (dTemp > STRAIGHT_ANGLE)
			{
				oExtraWays.add(oComp);
				continue;
			}
			
			double dMag = Math.abs(dTemp - Mercator.PI_OVER_TWO);
			int nRh = GeoUtil.rightHand(dX1, dY1, dX2, dY2, dX3, dY3);
			if (nRh == -1)
			{
				if (dMag < dClosestPos)
				{
					dPosAngle = dTemp;
					dClosestPos = dMag;
					oPosWays.add(0, oComp);
				}
				else
					oPosWays.add(oComp);
			}
			else
			{
				if (dMag < dClosestNeg)
				{
					dNegAngle = dTemp;
					dClosestNeg = dMag;
					oNegWays.add(0, oComp);
				}
				else
					oNegWays.add(oComp);
			}
		}
		
		oWay.m_bUseStart = true;
		addSeparatedNodes(oNegWays, dNegAngle, oNegNodes, o2, oWay, nSepDist, dHdg, oNodes, false);
		addSeparatedNodes(oPosWays, dPosAngle, oPosNodes, o2, oWay, nSepDist, dHdg, oNodes, true);
		OsmNode oNewNeg = oNegNodes.get(oNegNodes.size() - 1);
		OsmNode oNewPos = oPosNodes.get(oPosNodes.size() - 1);
		for (int nWayIndex = 0; nWayIndex < oExtraWays.size(); nWayIndex++)
		{
			OsmWay oExtra = oExtraWays.get(nWayIndex);
			if (oExtra.get("oneway") == null || oExtra.get("oneway").compareTo("no") == 0)
				continue;
			
			if (oExtra.m_bUseStart)
			{
				if (oExtra.m_oOriginalStart == null)
				{
					oExtra.m_oOriginalStart = oExtra.m_oNodes.set(0, oNewPos);
				}
			}
			else
			{
				if (oExtra.m_oOriginalEnd == null)
				{
					oExtra.m_oOriginalEnd = oExtra.m_oNodes.set(oExtra.m_oNodes.size() - 1, oNewNeg);
				}
			}
		}
		if (oPosWays.isEmpty() && oNegWays.isEmpty())
			o2.m_bDrawConnector = false;
	}
	
	
	/**
	 * Separates the end node of the given roadway segment and adds the new
	 * points into the given neg and pos lists.
	 * 
	 * @param oWay The roadway segment being separated
	 * @param oNegNodes negative tangent node list
	 * @param oPosNodes positive tangent node list
	 * @param nSepDist distance in decimal degrees scaled to 7 decimal places
	 * to place nodes of the newly separated roadway segments from the center line
	 * of the bi-directional road being separated.
	 * @param oNodes list of all the nodes making up roadway segments in the network
	 * being processed
	 * @throws IOException
	 */
	private static void separateEndPoint(OsmWay oWay, ArrayList<OsmNode> oNegNodes, ArrayList<OsmNode> oPosNodes, int nSepDist, ArrayList<OsmNode> oNodes)
		throws IOException
	{
		OsmNode o1 = oWay.m_oNodes.get(oWay.m_oNodes.size() - 2);
		OsmNode o2 = oWay.m_oOriginalEnd == null ? oWay.m_oNodes.get(oWay.m_oNodes.size() - 1) : oWay.m_oOriginalEnd; 
		OsmNode o3;
		double dX1 = o1.m_nLon;
		double dY1 = o1.m_nLat;
		double dX2 = o2.m_nLon;
		double dY2 = o2.m_nLat;
		double dX3;
		double dY3;
		
		double dClosestPos = Math.PI;
		double dClosestNeg = Math.PI;
		double dPosAngle = Mercator.PI_OVER_TWO;
		double dNegAngle = Mercator.PI_OVER_TWO;
		double dHdg = GeoUtil.heading(dX1, dY1, dX2, dY2);
		ArrayList<OsmWay> oPosWays = new ArrayList();
		ArrayList<OsmWay> oNegWays = new ArrayList();
		ArrayList<OsmWay> oExtraWays = new ArrayList();
		for (OsmWay oComp : o2.m_oRefs)
		{
			if (OsmWay.WAYBYTEID.compare(oWay, oComp) == 0 || oComp.m_oNodes.size() < 2)
				continue;
			
			oComp.m_bUseStart = true;
			o3 = oComp.m_oNodes.get(0);
			if (OsmNode.NODEBYTEID.compare(o3, o2) != 0)
			{
				if (oComp.m_oOriginalStart == null || OsmNode.NODEBYTEID.compare(oComp.m_oOriginalStart, o2) != 0)
				{
					oComp.m_bUseStart = false;
					if (oComp.m_oOriginalEnd != null && OsmNode.NODEBYTEID.compare(oComp.m_oOriginalEnd, o2) == 0)
						o3 = oComp.m_oNodes.get(oComp.m_oNodes.size() - 1);
					else
						o3 = oComp.m_oNodes.get(oComp.m_oNodes.size() - 2);
				}
				else
					o3 = oComp.m_oNodes.get(1);
			}
			else
				o3 = oComp.m_oNodes.get(1);
			
			dX3 = o3.m_nLon;
			dY3 = o3.m_nLat;
			double dTemp = GeoUtil.angle(dX1, dY1, dX2, dY2, dX3, dY3);
			if (dTemp > STRAIGHT_ANGLE)
			{
				oExtraWays.add(oComp);
				continue;
			}
			double dMag = Math.abs(dTemp - Mercator.PI_OVER_TWO);
			int nRh = GeoUtil.rightHand(dX1, dY1, dX2, dY2, dX3, dY3);
			if (nRh == -1)
			{
				if (dMag < dClosestPos)
				{
					dPosAngle = dTemp;
					dClosestPos = dMag;
					oPosWays.add(0, oComp);
				}
				else
					oPosWays.add(oComp);
			}
			else
			{
				if (dMag < dClosestNeg)
				{
					dNegAngle = dTemp;
					dClosestNeg = dMag;
					oNegWays.add(0, oComp);
				}
				else
					oNegWays.add(oComp);
			}
		}
		
		oWay.m_bUseStart = false;
		addSeparatedNodes(oNegWays, Math.PI - dNegAngle, oNegNodes, o2, oWay, nSepDist, dHdg, oNodes, false);
		addSeparatedNodes(oPosWays, Math.PI - dPosAngle, oPosNodes, o2, oWay, nSepDist, dHdg, oNodes, true);
		OsmNode oNewNeg = oNegNodes.get(oNegNodes.size() - 1);
		OsmNode oNewPos = oPosNodes.get(oPosNodes.size() - 1);
		for (int nWayIndex = 0; nWayIndex < oExtraWays.size(); nWayIndex++)
		{
			OsmWay oExtra = oExtraWays.get(nWayIndex);
			if (oExtra.get("oneway") == null || oExtra.get("oneway").compareTo("no") == 0)
				continue;
			
			if (oExtra.m_bUseStart)
			{
				if (oExtra.m_oOriginalStart == null)
				{
					oExtra.m_oOriginalStart = oExtra.m_oNodes.set(0, oNewNeg);
				}
			}
			else
			{
				if (oExtra.m_oOriginalEnd == null)
				{
					oExtra.m_oOriginalEnd = oExtra.m_oNodes.set(oExtra.m_oNodes.size() - 1, oNewPos);
				}
			}
		}
		if (oPosWays.isEmpty() && oNegWays.isEmpty())
			o2.m_bDrawConnector = false;
		
	}
	
	
	/**
	 * Handles the different cases of roadway segments intersecting at the given
	 * node to separate. Adds the new node to the overall node list and updates
	 * references as needed for the segments that may or may not have been
	 * separated already that share the intersection point.
	 * 
	 * @param oWays the ways that shared an endpoint with the way being separated
	 * @param dAngle the calculated angle in radians of way in position 0 of {@code oWays}
	 * @param oSepNodes the negative or positive tangent node list of the roadway
	 * segment being separated
	 * @param oSepPoint the node that is being separated
	 * @param oSepWay The roadway segment being separated
	 * @param nSepDist distance in decimal degrees scaled to 7 decimal places
	 * to place nodes of the newly separated roadway segments from the center line
	 * of the bi-directional road being separated.
	 * @param dHdg the heading in radians of the direction of travel
	 * @param oNodes list of all the nodes in the network being processed
	 * @param bPos flag indicating if the positive(true) or negative(false) tangent is being
	 * processed
	 * @throws IOException
	 */
	public static void addSeparatedNodes(ArrayList<OsmWay> oWays, double dAngle, ArrayList<OsmNode> oSepNodes, OsmNode oSepPoint, OsmWay oSepWay, int nSepDist, double dHdg, ArrayList<OsmNode> oNodes, boolean bPos)
		throws IOException
	{
		double dPiOverFour = Math.PI / 4;
		double dX = oSepPoint.m_nLon;
		double dY = oSepPoint.m_nLat;
		oSepWay.m_bSeparated = true;
		if (oSepPoint.m_oSeps == null) // stores all of the points the node being separated has been separated to in different parts of the algorithm, used to manage intersections of ways
			oSepPoint.m_oSeps = new ArrayList();
		OsmNode oNode;

		OsmWay oIntWay = null;
		if (!oWays.isEmpty())
			oIntWay = oWays.get(0); // the way with the closest angle is always in position 0
		else // no ways share the separate point
		{
			double dXPrime;
			double dYPrime;
			if (bPos)
			{
				dXPrime = dX + Math.cos(dHdg + dAngle) * nSepDist;
				dYPrime = dY + Math.sin(dHdg + dAngle) * nSepDist;
			}
			else
			{
				dXPrime = dX + Math.cos(dHdg - dAngle) * nSepDist;
				dYPrime = dY + Math.sin(dHdg - dAngle) * nSepDist;
			}
			
			OsmNode oNewNode = new OsmNode((int)Math.round(dYPrime), (int)Math.round(dXPrime));
			
			for (OsmNode oSep : oSepPoint.m_oSeps)
			{
				if (GeoUtil.distance(dXPrime, dYPrime, oSep.m_nLon, oSep.m_nLat) < 25)
				{
					oNewNode = oSep;
					break;
				}
			}
			int nIndex = Collections.binarySearch(oNodes, oNewNode, OsmNode.NODEBYTEID);
			if (nIndex < 0)
				oNodes.add(~nIndex, oNewNode);
			else
				oNewNode = oNodes.get(nIndex);
			
			oSepNodes.add(oNewNode);
			oSepPoint.m_oSeps.add(oNewNode);
			return;
		}

		oNode = oIntWay.m_bUseStart ? oIntWay.m_oNodes.get(1) : oIntWay.m_oNodes.get(oIntWay.m_oNodes.size() - 2);
		if (GeoUtil.distance(oNode.m_nLon, oNode.m_nLat, dX, dY) < nSepDist)
		{
			oSepNodes.add(oNode);
			if (oIntWay.m_oNodes.size() > 2)
			{
				if (oIntWay.m_bUseStart)
				{
					if (oIntWay.m_oOriginalStart == null)
					{
						oIntWay.m_oOriginalStart = oIntWay.m_oNodes.remove(0);
					}
				}
				else
				{
					if (oIntWay.m_oOriginalEnd == null)
					{
						oIntWay.m_oOriginalEnd = oIntWay.m_oNodes.remove(oIntWay.m_oNodes.size() - 1);
					}
				}
			}
		}
		else
		{
			if (dAngle < dPiOverFour || dAngle > 3 * dPiOverFour)
				dAngle = Mercator.PI_OVER_TWO;
			
			double dXPrime;
			double dYPrime;
			OsmWay oCompSep = null;
			if (bPos)
			{
				if (oIntWay.m_bSeparated)
				{
					if (oSepWay.m_bUseStart)
					{
						dXPrime = dX + Math.cos(dHdg) * nSepDist;
						dYPrime = dY + Math.sin(dHdg) * nSepDist;
					}
					else
					{
						dXPrime = dX + Math.cos(dHdg - Math.PI) * nSepDist;
						dYPrime = dY + Math.sin(dHdg - Math.PI) * nSepDist;
					}
					oCompSep = oIntWay.m_oNegSep;
					OsmNode oSepCheck;
					if (OsmNode.NODEBYTEID.compare(oCompSep.m_oOriginalStart, oSepPoint) == 0)
						oSepCheck = oCompSep.m_oNodes.get(0);
					else
						oSepCheck = oCompSep.m_oNodes.get(oCompSep.m_oNodes.size() - 1);
					
					double dDist1 = GeoUtil.sqDist(dXPrime, dYPrime, oSepCheck.m_nLon, oSepCheck.m_nLat);
					
					oSepCheck = OsmNode.NODEBYTEID.compare(oIntWay.m_oPosSep.m_oOriginalEnd, oSepPoint) == 0 ? oIntWay.m_oPosSep.m_oNodes.get(oIntWay.m_oPosSep.m_oNodes.size() - 1) : oIntWay.m_oPosSep.m_oNodes.get(0);
					double dDist2 = GeoUtil.sqDist(dXPrime, dYPrime, oSepCheck.m_nLon, oSepCheck.m_nLat);
					if (dDist2 < dDist1)
						oCompSep = oIntWay.m_oPosSep;
					
					OsmNode o1 = null;
					OsmNode o2 = null;
					OsmNode o3 = null;
					for (OsmNode oSep : oSepPoint.m_oSeps)
					{
						for (OsmWay oRef : oSep.m_oRefs)
						{
							if (OsmWay.WAYBYTEID.compare(oRef, oCompSep) == 0)
							{
								o2 = oSep;
								break;
							}
						}
						if (o2 != null)
							break;
					}
					if (o2 != null)
					{
						if (oSepWay.m_bUseStart)
						{
							o3 = oSepWay.m_oNodes.get(1);
						}
						else
						{
							o1 = oSepWay.m_oNodes.get(oSepWay.m_oNodes.size() - 2);
						}

						OsmNode oComp = oCompSep.m_oNodes.get(oCompSep.m_oNodes.size() - 1);
						if (OsmNode.NODEBYTEID.compare(oComp, o2) != 0)
						{
							oComp = oCompSep.m_oNodes.get(1);
							oCompSep.m_bUseStart = true;
						}
						else
						{
							oComp = oCompSep.m_oNodes.get(oCompSep.m_oNodes.size() - 2);
							oCompSep.m_bUseStart = false;
						}

						if (oSepWay.m_bUseStart)
							o1 = oComp;
						else
							o3 = oComp;

						double dX1 = o1.m_nLon;
						double dY1 = o1.m_nLat;
						double dX2 = o2.m_nLon;
						double dY2 = o2.m_nLat;
						double dX3 = o3.m_nLon;
						double dY3 = o3.m_nLat;
						double dNewAngle = GeoUtil.angle(dX1, dY1, dX2, dY2, dX3, dY3);
						if (!oSepWay.m_bUseStart)
							dNewAngle = Math.PI - dNewAngle;

						dXPrime = dX2 + Math.cos(dHdg + dNewAngle) * nSepDist;
						dYPrime = dY2 + Math.sin(dHdg + dNewAngle) * nSepDist;
					}
					else
					{
						dXPrime = dX + Math.cos(dHdg + dAngle) * nSepDist;
						dYPrime = dY + Math.sin(dHdg + dAngle) * nSepDist;
					}
				}
				else
				{
					dXPrime = dX + Math.cos(dHdg + dAngle) * nSepDist;
					dYPrime = dY + Math.sin(dHdg + dAngle) * nSepDist;
				}
			}
			else
			{
				if (oIntWay.m_bSeparated)
				{
					if (oSepWay.m_bUseStart)
					{
						dXPrime = dX + Math.cos(dHdg) * nSepDist;
						dYPrime = dY + Math.sin(dHdg) * nSepDist;
					}
					else
					{
						dXPrime = dX + Math.cos(dHdg - Math.PI) * nSepDist;
						dYPrime = dY + Math.sin(dHdg - Math.PI) * nSepDist;
					}
					oCompSep = oIntWay.m_oNegSep;
					OsmNode oSepCheck;
					if (OsmNode.NODEBYTEID.compare(oCompSep.m_oOriginalStart, oSepPoint) == 0)
						oSepCheck = oCompSep.m_oNodes.get(0);
					else
						oSepCheck = oCompSep.m_oNodes.get(oCompSep.m_oNodes.size() - 1);
					
					double dDist1 = GeoUtil.distance(dXPrime, dYPrime, oSepCheck.m_nLon, oSepCheck.m_nLat);
					
					oSepCheck = OsmNode.NODEBYTEID.compare(oIntWay.m_oPosSep.m_oOriginalEnd, oSepPoint) == 0 ? oIntWay.m_oPosSep.m_oNodes.get(oIntWay.m_oPosSep.m_oNodes.size() - 1) : oIntWay.m_oPosSep.m_oNodes.get(0);
					double dDist2 = GeoUtil.distance(dXPrime, dYPrime, oSepCheck.m_nLon, oSepCheck.m_nLat);
					if (dDist2 < dDist1)
						oCompSep = oIntWay.m_oPosSep;
					
					OsmNode o1 = null;
					OsmNode o2 = null;
					OsmNode o3 = null;
					for (OsmNode oSep : oSepPoint.m_oSeps)
					{
						for (OsmWay oRef : oSep.m_oRefs)
						{
							if (OsmWay.WAYBYTEID.compare(oRef, oCompSep) == 0)
							{
								o2 = oSep;
								break;
							}
						}
						if (o2 != null)
							break;
					}
					if (o2 != null)
					{
						if (oSepWay.m_bUseStart)
						{
							o3 = oSepWay.m_oNodes.get(1);
						}
						else
						{
							o1 = oSepWay.m_oNodes.get(oSepWay.m_oNodes.size() - 2);
						}

						OsmNode oComp = oCompSep.m_oNodes.get(oCompSep.m_oNodes.size() - 1);
						if (OsmNode.NODEBYTEID.compare(oComp, o2) != 0)
						{
							oComp = oCompSep.m_oNodes.get(1);
							oCompSep.m_bUseStart = true;
						}
						else
						{
							oComp = oCompSep.m_oNodes.get(oCompSep.m_oNodes.size() - 2);
							oCompSep.m_bUseStart = false;
						}

						if (oSepWay.m_bUseStart)
							o1 = oComp;
						else
							o3 = oComp;

						double dX1 = o1.m_nLon;
						double dY1 = o1.m_nLat;
						double dX2 = o2.m_nLon;
						double dY2 = o2.m_nLat;
						double dX3 = o3.m_nLon;
						double dY3 = o3.m_nLat;
						double dNewAngle = GeoUtil.angle(dX1, dY1, dX2, dY2, dX3, dY3);
						if (!oSepWay.m_bUseStart)
							dNewAngle = Math.PI - dNewAngle;

						dXPrime = dX2 + Math.cos(dHdg - dNewAngle) * nSepDist;
						dYPrime = dY2 + Math.sin(dHdg - dNewAngle) * nSepDist;
					}
					else
					{
						dXPrime = dX + Math.cos(dHdg - dAngle) * nSepDist;
						dYPrime = dY + Math.sin(dHdg - dAngle) * nSepDist;
					}
				}
				else
				{
					dXPrime = dX + Math.cos(dHdg - dAngle) * nSepDist;
					dYPrime = dY + Math.sin(dHdg - dAngle) * nSepDist;
				}
			}
			
			OsmNode oNewNode = new OsmNode((int)Math.round(dYPrime), (int)Math.round(dXPrime));
			
			for (OsmNode oSep : oSepPoint.m_oSeps)
			{
				if (GeoUtil.distance(dXPrime, dYPrime, oSep.m_nLon, oSep.m_nLat) < 25)
				{
					oNewNode = oSep;
					break;
				}
			}
			
			int nIndex = Collections.binarySearch(oNodes, oNewNode, OsmNode.NODEBYTEID);
			if (nIndex < 0)
				oNodes.add(~nIndex, oNewNode);
			else
				oNewNode = oNodes.get(nIndex);
			
			oSepNodes.add(oNewNode);
			oSepPoint.m_oSeps.add(oNewNode);
			
			if (oCompSep != null)
			{
				OsmNode oOld;
				if (oCompSep.m_bUseStart)
				{
					oOld = oCompSep.m_oNodes.set(0, oNewNode);
				}
				else
				{
					oOld = oCompSep.m_oNodes.set(oCompSep.m_oNodes.size() - 1, oNewNode);
				}
				oOld.removeRef(oCompSep);
				for (int nSepIndex = 0; nSepIndex < oSepPoint.m_oSeps.size(); nSepIndex++)
				{
					OsmNode oCmp = oSepPoint.m_oSeps.get(nSepIndex);
					if (OsmNode.NODEBYTEID.compare(oOld, oCmp) == 0)
					{
						oSepPoint.m_oSeps.set(nSepIndex, oNewNode);
						break;
					}
				}
				oCompSep.removeRefs();
				oCompSep.generateId();
				oCompSep.updateRefs();
			}
			
			
			if (oIntWay.m_bUseStart)
			{
				if (oIntWay.m_oOriginalStart == null)
				{
					oIntWay.m_oOriginalStart = oIntWay.m_oNodes.set(0, oNewNode);
				}
			}
			else
			{
				if (oIntWay.m_oOriginalEnd == null)
				{
					oIntWay.m_oOriginalEnd = oIntWay.m_oNodes.set(oIntWay.m_oNodes.size() - 1, oNewNode);
				}
			}
			
			oIntWay.removeRefs();
			oIntWay.generateId();
			oIntWay.updateRefs();
			if (oIntWay.m_oOriginalEnd != null)
				oIntWay.m_oOriginalEnd.addRef(oIntWay);

			if (oIntWay.m_oOriginalStart != null)
				oIntWay.m_oOriginalStart.addRef(oIntWay);
			
			for (int nWayIndex = 1; nWayIndex < oWays.size(); nWayIndex++)
			{
				OsmWay oWay = oWays.get(nWayIndex);
				if (oWay.get("oneway") == null || oWay.get("oneway").compareTo("no") == 0)
					continue;
				if (oWay.m_bUseStart)
				{
					if (oWay.m_oOriginalStart == null)
						oWay.m_oOriginalStart = oWay.m_oNodes.set(0, oNewNode);
				}
				else
				{
					if (oWay.m_oOriginalEnd == null)
						oWay.m_oOriginalEnd = oWay.m_oNodes.set(oWay.m_oNodes.size() -1, oNewNode);
				}
			}
		}
	}
	
	
	/**
	 * For each roadway segment in the given list, if the length of that segment
	 * is greater than the distance threshold, that segment is split into smaller
	 * segments.
	 * 
	 * @param oWays list of roadway segments to process
	 * @param oNodes list of all the nodes making up the roadway segments
	 * @param nDistThresh distance in decimal degrees scaled to 7 decimal places
	 * that segment's lengths must be less than
	 * @return a list of all the road segments after processing is finished
	 * @throws Exception
	 */
	public static ArrayList<OsmWay> extraSegs(ArrayList<OsmWay> oWays, ArrayList<OsmNode> oNodes, int nDistThresh)
		throws Exception
	{
		ArrayList<OsmWay> oReturn = new ArrayList();
		int nNew = 0;
		for (OsmWay oWay : oWays)
		{
			if (oWay.m_dLength < nDistThresh) // within the threshold
			{
				oReturn.add(oWay); // just add to the new list
				continue;
			}
			
			oWay.removeRefs();
			int nParts = (int)Math.ceil(oWay.m_dLength / nDistThresh); // determine how many segments this needs to be split into
			double dSplitDist = oWay.m_dLength / nParts;
			OsmWay oNew = new OsmWay();
			oNew.putAll(oWay);
			oNew.m_sName = oWay.m_sName;
			oNew.m_oNodes.add(oWay.m_oNodes.get(0));
			double dCurDist = 0.0;
			int nLimit = oWay.m_oNodes.size() - 1;
			int nPart = 0;
			for (int nNodeIndex = 0; nNodeIndex < nLimit; nNodeIndex++) // iterate through the roadway segment one line segment at a time
			{
				OsmNode oN1 = oWay.m_oNodes.get(nNodeIndex);
				OsmNode oN2 = oWay.m_oNodes.get(nNodeIndex + 1);
				double dDist = GeoUtil.distance(oN1.m_nLon, oN1.m_nLat, oN2.m_nLon, oN2.m_nLat);
				dCurDist += dDist;
				if (nNodeIndex == nLimit - 1) // at the end of the list
					dCurDist = dSplitDist;
				while (dCurDist >= dSplitDist) // need to split, continue to split until the current distance is no longer greater than the threshold
				{
					++nPart;
					OsmNode oNewNode;
					double dDiff;
					if (nPart == nParts) // last part so don't need to calculate a new node to split
					{
						dDiff = 0;
						oNewNode = oN2;
					}
					else
					{
						dDiff = dCurDist - dSplitDist; // determine where the split needs to happen on this line segment
						double dRatio = (dDist - dDiff) / dDist;
						int nDeltaX = (int)((oN2.m_nLon - oN1.m_nLon) * dRatio);
						int nDeltaY = (int)((oN2.m_nLat - oN1.m_nLat) * dRatio);
						oNewNode = new OsmNode(oN1.m_nLat + nDeltaY, oN1.m_nLon + nDeltaX);
						++nNew;
						int nIndex = Collections.binarySearch(oNodes, oNewNode, OsmNode.NODEBYTEID);
						if (nIndex < 0)
							oNodes.add(~nIndex, oNewNode);
						else
							oNewNode = oNodes.get(nIndex);
					}
					oNew.m_oNodes.add(oNewNode);
					oNew.setMinMax();
					oNew.calcMidpoint();
					oNew.setBridge();
					oNew.generateId();
					oReturn.add(oNew); // add a way of the nodes so far
					oNew.updateRefs();
					
					oNew = new OsmWay();
					oNew.putAll(oWay);
					oNew.m_sName = oWay.m_sName;
					oNew.m_oNodes.add(oNewNode);
					dCurDist = dDiff;
					oN1 = oNewNode;
				}
				
				oNew.m_oNodes.add(oN2);
			}
		}
		
		return oReturn;
	}
	
	
	/**
	 * Writes a metadata files for lanes and speed limits parsing the values from
	 * the OSM tags.
	 * 
	 * @param sGeoFile The path of the file defining the road network of the list
	 * of roadway segments
	 * @param oWays The roadway segments of the road network
	 * @param bAppend flag indicating if the metadata file should be appended to(true)
	 * or over written(false).
	 * @throws Exception
	 */
	public static void writeLanesAndSpeeds(String sGeoFf, String sNetworkId, ArrayList<OsmWay> oWays, boolean bAppend)
		throws Exception
	{
		OpenOption[] oOpts = bAppend ? FileUtil.APPENDOPTS : FileUtil.WRITEOPTS;
		synchronized (WayNetworks.METADATAFILELOCK)
		{
			try (DataOutputStream oSpeedLimits = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Paths.get(String.format(sGeoFf, sNetworkId, "spdlimit")), oOpts)));
				DataOutputStream oLanes = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Paths.get(String.format(sGeoFf, sNetworkId, "lanes")), oOpts))))
			{
				for (OsmWay oWay : oWays)
				{
					String sSpdLim = oWay.get("maxspeed");
					if (sSpdLim != null)
					{
						try
						{
							byte nLim;
							int nSpace = sSpdLim.indexOf(" ");
							if (nSpace > 0)
							{
								nLim = Byte.parseByte(sSpdLim.substring(0, nSpace));
								String sUnit = sSpdLim.substring(nSpace + 1);
								if (sUnit.compareTo("mph") != 0) // kph
									nLim = (byte)Math.round(nLim * 0.6213711922); // convert
							}
							else
							{
								nLim = Byte.parseByte(sSpdLim);
							}
							oWay.m_oId.write(oSpeedLimits);
							oSpeedLimits.writeByte(nLim);
						}
						catch (Exception oEx)
						{
							oEx.printStackTrace();
						}
					}

					String sOneway = oWay.get("oneway");
					String sLanes = oWay.get("lanes");
					if (sLanes != null && sOneway != null && sOneway.compareTo("yes") == 0)
					{
						try
						{
							byte yLanes = Byte.parseByte(sLanes);
							oWay.m_oId.write(oLanes);
							oLanes.writeByte(yLanes);
						}
						catch (NumberFormatException oEx)
						{
						}
					}
				}
			}
		}
	}
	
	
	/**
	 *Publishes the network defined by {@code sGeoFile}. The split, merge, extrasegs,
	 * ramp determination, and separate algorithms are ran on the roadway segments
	 * in the network.
	 * 
	 * @param sGeoFile path to the IMRCP OSM binary file defining the network
	 * @param sStateShp path to the shapefile containing US state border definitions
	 * @param sStates contains the US states the road network intersects
	 * @param sFilter contains the filter Strings used to create the network
	 * @param sOptions contains the option Strings used to create the network
	 * @param sOsmDir base directory for OSM files
	 * @return a list containing the roadway segments after all of the algorithms
	 * have been ran on the network.
	 * @throws Exception
	 */
	public static void publishNetwork(String sGeoFf, String sNetworkId, String sStateShp, String[] sStates, String[] sFilter, String[] sOptions, String sOsmDir, Logger oLogger)
		throws Exception
	{
		ArrayList<OsmNode> oNodes = new ArrayList();
		ArrayList<OsmWay> oWays = new ArrayList();
		StringPool oPool = new StringPool();
		boolean bRamps = false;
		boolean bConnectors = false;
		for (int nOptIndex = 0; nOptIndex < sOptions.length; nOptIndex++)
		{
			String sOpt = sOptions[nOptIndex];
			if (sOpt.compareTo("ramp") == 0)
				bRamps = true;
			else if (sOpt.compareTo("connector") == 0)
				bConnectors = true;
		}
		
		String sUnpublished = String.format(sGeoFf, sNetworkId, "unpublished");
		String sPublished = String.format(sGeoFf, sNetworkId, "published");
		new OsmBinParser().parseFile(sUnpublished, oNodes, oWays, oPool);
		int[] nWaysBb = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (OsmWay oWay : oWays)
		{
			if (oWay.m_nMinLon < nWaysBb[0])
				nWaysBb[0] = oWay.m_nMinLon;
			if (oWay.m_nMinLat < nWaysBb[1])
				nWaysBb[1] = oWay.m_nMinLat;
			if (oWay.m_nMaxLon > nWaysBb[2])
				nWaysBb[2] = oWay.m_nMaxLon;
			if (oWay.m_nMaxLat > nWaysBb[3])
				nWaysBb[3] = oWay.m_nMaxLat;
		}
		java.util.Arrays.sort(sStates);
		
		;
		HashMap<String, ArrayList<int[]>> oStates = new HashMap();
		HashMap<String, HashMap<Integer, ArrayList<OsmWay>>> oHashes = new HashMap();
		try (DbfResultSet oDbf = new DbfResultSet(sStateShp.replace(".shp", ".dbf"));
			ShpReader oShp = new ShpReader(new BufferedInputStream(Files.newInputStream(Paths.get(sStateShp)))))
		{
			while (oDbf.next())
			{
				ArrayList<int[]> oBorder = new ArrayList();
				oShp.readPolygon(oBorder);
				String sState = oDbf.getString("NAME");
				String sName = sState.toLowerCase().replaceAll(" ", "-");
				if (java.util.Arrays.binarySearch(sStates, sName) < 0)
					continue;

				HashMap<Integer, ArrayList<OsmWay>> oHash = new HashMap();
				oStates.put(sName, oBorder);

				oLogger.info(String.format("Parsing hash - %s", sName));
				new OsmBinParser().parseHashes(sOsmDir + sName + "-latest.bin", nWaysBb[0], nWaysBb[1], nWaysBb[2], nWaysBb[3], oHash);
				oHashes.put(sName, oHash);
				oLogger.info(String.format("Parsed hash - %s", sName));
			}
		}
		
		oLogger.debug("Spliting ways");
		oWays = OsmUtil.split(oWays);
		oNodes.forEach(o -> o.m_oRefs = new ArrayList());
		oWays.forEach(o -> {o.updateRefs();});
		oLogger.debug("Merging ways");
		oWays = OsmUtil.merge(oWays);
		oNodes.forEach(o -> o.m_oRefs = new ArrayList());
		oWays.forEach(o -> {o.updateRefs();});
		oLogger.debug("Extra segs");
		oWays = OsmUtil.extraSegs(oWays, oNodes, 250000);
		
		oLogger.debug("Determine Ramps");
		OsmUtil.determineRamps(oWays, oStates, sOsmDir, sFilter);
		
		int nWayIndex = oWays.size();
		while (nWayIndex-- > 0)
		{
			OsmWay oWay = oWays.get(nWayIndex);
			String sHighway = oWay.get("highway");
			if (sHighway.contains("_link") && ((!bRamps && (oWay.m_yLinkType == OsmWay.RAMP || oWay.m_yLinkType == OsmWay.NOTSET)) || 
				(!bConnectors && (oWay.m_yLinkType == OsmWay.CONNECTOR || oWay.m_yLinkType == OsmWay.NOTSET))))
			{
				oWays.remove(nWayIndex);
				oWay.removeRefs();
			}
		}
		
		oNodes.forEach(o -> o.m_oRefs = new ArrayList());
		oWays.forEach(o -> {o.updateRefs();});
		oNodes.forEach(o -> o.m_nOriginalRefForSep = o.m_oRefs.size());
		oLogger.debug("Separating ways");
		oWays = OsmUtil.separate(oWays, oNodes, 150);
		
		oNodes.forEach(o -> o.m_oRefs = new ArrayList());
		int nIndex = oWays.size();
		while (nIndex-- > 0)
		{
			OsmWay oWay = oWays.get(nIndex);
			if (oWay.m_oNodes.size() <= 1)
			{
				oWay.removeRefs();
				oWays.remove(nIndex);
				continue;
			}
			oWay.updateRefs();
			for (Map.Entry<String, String> oTag : oWay.entrySet())
			{
				oPool.intern(oTag.getKey());
				oPool.intern(oTag.getValue());
			}
		}
		
		oNodes = OsmUtil.removeZeroRefs(oNodes);
		oPool.intern("oneway");
		oPool.intern("yes");
		

		OsmBz2ToBin.writeBin(sPublished, oPool, oNodes, oWays);
		writeLanesAndSpeeds(sGeoFf, sNetworkId, oWays, false);
	}
}
