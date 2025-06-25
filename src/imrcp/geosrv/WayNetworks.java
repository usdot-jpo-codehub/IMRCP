/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.geosrv;

import imrcp.forecast.mlp.MLP;
import imrcp.forecast.mlp.MLPHurricane;
import imrcp.system.BaseBlock;
import imrcp.geosrv.osm.HashBucket;
import imrcp.geosrv.osm.OsmBinParser;
import imrcp.geosrv.osm.OsmNode;
import imrcp.geosrv.osm.OsmUtil;
import imrcp.geosrv.osm.OsmWay;
import imrcp.system.Arrays;
import imrcp.system.FileUtil;
import imrcp.system.Id;
import imrcp.system.Scheduling;
import imrcp.system.StringPool;
import imrcp.system.Util;
import imrcp.web.Scenario;
import imrcp.web.SessMgr;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * This class contains methods for managing and retrieving data for roadway 
 * networks including geometric definitions, number of lanes, and speed limits. 
 * It maintains the Network JSON file which describes the different networks 
 * available in the system. 
 * @author aaron.cherney
 */
public class WayNetworks extends BaseBlock
{
	public static final String NETWORKFF = "networks/%s/%s.bin";
	public static final int HURMODEL_NOTTRAINED = 0;
	public static final int HURMODEL_TRAINING = 1;
	public static final int HURMODEL_TRAINED = 2;
	public static final int HURMODEL_ERROR = 3;
	
	/**
	 * Contains HashBuckets which spatial index roadway segments on a grid whose
	 * size is based on {@link HashBucket#BUCKET_SPACING}
	 */
	private ArrayList<HashBucket> m_oHashes = new ArrayList();

	
	/**
	 * Contains OsmWays sorted by Id
	 */
	private ArrayList<OsmWay> m_oWaysById = new ArrayList();

	
	/**
	 * Format string used to generate geometry files for the different networks
	 */
	private String m_sGeoFileFormat;

	
	/**
	 * Contains WayMetadata objects sorted by Id
	 */
	private ArrayList<WayMetadata> m_oMetadata = new ArrayList();

	
	/**
	 * WayMetadata that is contains the default values and is used when a 
	 * WayMetadata cannot be found for a given Id
	 */
	private WayMetadata DEFAULTMETADATA = new WayMetadata(Id.NULLID); 

	
	/**
	 * Contains all the Networks available in the system
	 */
	private final ArrayList<Network> m_oNetworks = new ArrayList();

	
	/**
	 * Path to the Network JSON file
	 */
	private String m_sNetworkFile;
	
	public final static Object METADATAFILELOCK = new Object();
	
	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_sNetworkFile = oBlockConfig.optString("networkfile", "");
		if (m_sNetworkFile.startsWith("/"))
			m_sNetworkFile = m_sNetworkFile.substring(1);
		m_sGeoFileFormat = m_sDataPath + NETWORKFF;
		m_sNetworkFile = m_sDataPath + m_sNetworkFile;
	}
	
	
	/**
	 * Parses the Network JSON file to load the defined Networks into memory
	 * to be available to the system.
	 * @return true if no Exceptions are thrown
	 * @throws Exception
	 */
	@Override
	public boolean start()
		throws Exception
	{
		DEFAULTMETADATA.m_nHOV = 0; // set defaults for metadata
		DEFAULTMETADATA.m_nPavementCondition = 1;
		DEFAULTMETADATA.m_nLanes = 2;
		DEFAULTMETADATA.m_nSpdLimit = 65;
		m_oMetadata.add(DEFAULTMETADATA);
				
		Path oNetworkFile = Paths.get(m_sNetworkFile);
		if (!Files.exists(oNetworkFile)) // create the Network JSON file if it doesn't exist
		{
			Files.createDirectories(oNetworkFile.getParent(), FileUtil.DIRPERS);
			try (Writer oOut = Channels.newWriter(Files.newByteChannel(oNetworkFile, FileUtil.WRITEOPTS), "UTF-8"))
			{
				oOut.append("[]");
			}
		}
		else // read the Network JSON file
		{
			try (BufferedReader oIn = Files.newBufferedReader(oNetworkFile, StandardCharsets.UTF_8))
			{
				JSONArray oFeatures = new JSONArray(new JSONTokener(oIn));
				for (int nIndex = 0; nIndex < oFeatures.length(); nIndex++)
					loadNetwork(oFeatures.getJSONObject(nIndex));										
			}
		}
		
		return true;
	}
	
	
	@Override
	public boolean stop()
		throws Exception
	{
		synchronized (m_oNetworks)
		{
			for (Network oNetwork : m_oNetworks)
			{
				if (oNetwork.isStatus(Network.PUBLISHING))
				{
					oNetwork.removeStatus(Network.PUBLISHING);
					oNetwork.addStatus(Network.WORKINPROGRESS);
				}
				if (oNetwork.isStatus(Network.TRAINING))
				{
					oNetwork.removeStatus(Network.TRAINING);
				}
			}
		}
		writeNetworkFile();
		return true;
	}
	
	
	/**
	 * Loads the given Network defined in the JSONObject. The IMRCP OSM binary
	 * file is parsed to get the roadway segment definitions. Then speed limit,
	 * lanes, mean sea level elevations, and ground elevation files are parsed to
	 * load metadata. Finally the roadway segments are indexed by hash grid
	 * and map tiles for quick lookup.
	 * @param oNetworkFeature GeoJson Feature object defining the Network
	 * @param bNotify flag indicating if other blocks should be notified that a
	 * new Network has been loaded into memory
	 * @throws IOException
	 */
	private synchronized void loadNetwork(JSONObject oNetworkFeature)
		throws IOException
	{
		String sId = oNetworkFeature.getJSONObject("properties").getString("networkid");
		ArrayList<OsmWay> oWays = new ArrayList();
		if ((oNetworkFeature.getJSONObject("properties").getInt("status") & Network.PUBLISHED) == Network.PUBLISHED)
		{
			String sGeoFile = String.format(m_sGeoFileFormat, sId, "published");
			ArrayList<OsmNode> oNodes = new ArrayList();
			
			StringPool oPool = new StringPool();
			new OsmBinParser().parseFile(sGeoFile, oNodes, oWays, oPool); // read the OSM binary file

			synchronized (m_oMetadata)
			{
				Path oSpdLimitFile = Paths.get(String.format(m_sGeoFileFormat, sId, "spdlimit"));
				if (Files.exists(oSpdLimitFile))
				{
					try (DataInputStream oIn = new DataInputStream(Files.newInputStream(oSpdLimitFile))) // read speed limit file
					{
						while (oIn.available() > 0)
						{
							WayMetadata oMetadata = new WayMetadata(new Id(oIn));
							oMetadata.m_nSpdLimit = oIn.readUnsignedByte();
							int nIndex = Collections.binarySearch(m_oMetadata, oMetadata);
							if (nIndex < 0)
								m_oMetadata.add(~nIndex, oMetadata);
							else
								m_oMetadata.get(nIndex).m_nSpdLimit = oMetadata.m_nSpdLimit;
						}
					}
				}

				Path oLanesFile = Paths.get(String.format(m_sGeoFileFormat, sId, "lanes"));
				if (Files.exists(oLanesFile))
				{
					try (DataInputStream oIn = new DataInputStream(Files.newInputStream(oLanesFile))) // read lanes file
					{	
						while (oIn.available() > 0)
						{
							WayMetadata oMetadata = new WayMetadata(new Id(oIn));
							oMetadata.m_nLanes = oIn.readByte();
							int nIndex = Collections.binarySearch(m_oMetadata, oMetadata);
							if (nIndex < 0)
								m_oMetadata.add(~nIndex, oMetadata);
							else
								m_oMetadata.get(nIndex).m_nLanes = oMetadata.m_nLanes;
						}
					}
				}

				Path oElevFile = Paths.get(String.format(m_sGeoFileFormat, sId, "msl_elev"));
				if (Files.exists(oElevFile)) // read mean sea level elevation file
				{
					try (DataInputStream oIn = new DataInputStream(Files.newInputStream(oElevFile)))
					{
						while (oIn.available() > 0)
						{
							WayMetadata oMetadata = new WayMetadata(new Id(oIn));
							oMetadata.m_dMinMslElev = oIn.readDouble();
							oMetadata.m_dMaxMslElev = oIn.readDouble();
							int nIndex = Collections.binarySearch(m_oMetadata, oMetadata);
							if (nIndex < 0)
								m_oMetadata.add(~nIndex, oMetadata);
							else
							{
								WayMetadata oMeta = m_oMetadata.get(nIndex);
								if (Double.isNaN(oMeta.m_dMinMslElev) || oMetadata.m_dMinMslElev < oMeta.m_dMinMslElev)
									oMeta.m_dMinMslElev = oMetadata.m_dMinMslElev;
								if (Double.isNaN(oMeta.m_dMaxMslElev) || oMetadata.m_dMaxMslElev > oMeta.m_dMaxMslElev)
									oMeta.m_dMaxMslElev = oMetadata.m_dMaxMslElev;
							}
						}
					}
				}
				oElevFile = Paths.get(String.format(m_sGeoFileFormat, sId, "ground_elev")); // read ground elevation file
				if (Files.exists(oElevFile))
				{
					try (DataInputStream oIn = new DataInputStream(Files.newInputStream(oElevFile)))
					{
						while (oIn.available() > 0)
						{
							WayMetadata oMetadata = new WayMetadata(new Id(oIn));
							oMetadata.m_dMinGroundElev = oIn.readDouble();
							oMetadata.m_dMaxGroundElev = oIn.readDouble();
							int nIndex = Collections.binarySearch(m_oMetadata, oMetadata);
							if (nIndex < 0)
								m_oMetadata.add(~nIndex, oMetadata);
							else
							{
								WayMetadata oMeta = m_oMetadata.get(nIndex);
								if (Double.isNaN(oMeta.m_dMinGroundElev) || oMetadata.m_dMinGroundElev < oMeta.m_dMinGroundElev)
									oMeta.m_dMinGroundElev = oMetadata.m_dMinGroundElev;
								if (Double.isNaN(oMeta.m_dMaxGroundElev) || oMetadata.m_dMaxGroundElev > oMeta.m_dMaxGroundElev)
									oMeta.m_dMaxGroundElev = oMetadata.m_dMaxGroundElev;
							}
						}
					}
				}
			}
			ArrayList<HashBucket> oHashes = new ArrayList();
			for (OsmWay oWay : oWays)
			{
				oWay.m_bInUse = true;
				oHashes.clear();
				int nLimit = oWay.m_oNodes.size() - 1;
				for (int nNodeIndex = 0; nNodeIndex < nLimit;) // for each node determine which hash grid and map tile the roadway segment intersects
				{
					OsmNode o1 = oWay.m_oNodes.get(nNodeIndex++);
					OsmNode o2 = oWay.m_oNodes.get(nNodeIndex);
					getHashes(oHashes, o1.m_nLon, o1.m_nLat, o2.m_nLon, o2.m_nLat, 0, 0, true);
				}


				for (HashBucket oHash : oHashes)
				{
					synchronized (oHash)
					{
						int nWayIndex = Collections.binarySearch(oHash, oWay, OsmWay.WAYBYTEID);
						if (nWayIndex < 0)
							oHash.add(~nWayIndex, oWay);
					}
				}

				synchronized (m_oWaysById)
				{
					int nWayIndex = Collections.binarySearch(m_oWaysById, oWay, OsmWay.WAYBYTEID);
					if (nWayIndex < 0)
						m_oWaysById.add(~nWayIndex, oWay);
				}
			}
		}
		m_oLogger.debug("Total ways: " + m_oWaysById.size());
		
		Network oNetwork = new Network(oNetworkFeature, oWays, this);
		synchronized (m_oNetworks)
		{
			int nSearch = Collections.binarySearch(m_oNetworks, oNetwork);
			if (nSearch < 0)
				m_oNetworks.add(~nSearch, oNetwork);
			else
				m_oNetworks.set(nSearch, oNetwork);
		}
	}
	
	
	public void commitMetadata(Scenario oScenario)
	{
		TreeMap<Id, Integer> oUpdateLanes = new TreeMap(Id.COMPARATOR);
		TreeMap<Id, Integer> oUpdateSpds = new TreeMap(Id.COMPARATOR);
		synchronized (m_oMetadata)
		{
			WayMetadata oSearch = new WayMetadata(Id.NULLID);
			for (Entry<Id, int[]> oUpdate : oScenario.m_oUserDefinedMetadata.entrySet())
			{
				oSearch.m_oId = oUpdate.getKey();
				int nLanes = oUpdate.getValue()[0];
				int nSpdLimit = oUpdate.getValue()[1];
				int nIndex = Collections.binarySearch(m_oMetadata, oSearch);
				if (nIndex < 0)
				{
					nIndex = ~nIndex;
					m_oMetadata.add(nIndex, new WayMetadata(oUpdate.getKey()));					
				}

				WayMetadata oMetadata = m_oMetadata.get(nIndex);
				if (nLanes > 0 && oMetadata.m_nLanes != nLanes)
				{
					oMetadata.m_nLanes = nLanes;
					oUpdateLanes.put(oUpdate.getKey(), nLanes);
				}
				if (nSpdLimit > 0 && oMetadata.m_nSpdLimit != nSpdLimit)
				{
					oMetadata.m_nSpdLimit = nSpdLimit;
					oUpdateSpds.put(oUpdate.getKey(), nSpdLimit);
				}
			}
		}
		
		synchronized (METADATAFILELOCK)
		{
			try (DataOutputStream oLanes = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Paths.get(String.format(m_sGeoFileFormat, oScenario.m_sNetwork, "lanes")), FileUtil.APPENDOPTS))))
			{
				for (Entry<Id, Integer> oUpdate : oUpdateLanes.entrySet())
				{
					oUpdate.getKey().write(oLanes);
					oLanes.writeByte(oUpdate.getValue());
				}
			}
			catch (Exception oEx)
			{
				m_oLogger.error(oEx);
			}
			
			try (DataOutputStream oSpeedLimits = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Paths.get(String.format(m_sGeoFileFormat, oScenario.m_sNetwork, "spdlimit")), FileUtil.APPENDOPTS))))
			{
				for (Entry<Id, Integer> oUpdate : oUpdateSpds.entrySet())
				{
					oUpdate.getKey().write(oSpeedLimits);
					oSpeedLimits.writeByte(oUpdate.getValue());
				}
			}
			catch (Exception oEx)
			{
				m_oLogger.error(oEx);
			}
		}
	}
	
	
	/**
	 * Fills the given list with HashBuckets that intersect the bounding box
	 * defined by the two longitude and latitude pairs.
	 * 
	 * @param oHashes List to fill with HashBuckets
	 * @param nXmin longitude of first point in decimal degrees scaled to 7
	 * decimal places
	 * @param nYmin latitude of first point in decimal degrees scaled to 7
	 * decimal places
	 * @param nXmax longitude of second point in decimal degrees scaled to 7
	 * decimal places
	 * @param nYmax latitude of second point in decimal degrees scaled to 7
	 * decimal places
	 * @param nTol tolerance to add to/subtract from longitudes
	 * @param nLatTol tolerance to add to/subtract from latitudes
	 * @param bAdd flag indicating if new buckets should be added to {@link #m_oHashes}
	 */
	private void getHashes(ArrayList<HashBucket> oHashes, int nXmin, int nYmin,
	   int nXmax, int nYmax, int nTol, int nLatTol, boolean bAdd)
	{
		if (nXmin > nXmax) // re-order longitude as needed
		{
			nXmin ^= nXmax;
			nXmax ^= nXmin;
			nXmin ^= nXmax;
		}

		if (nYmin > nYmax) // re-order latitude as needed
		{
			nYmin ^= nYmax;
			nYmax ^= nYmin;
			nYmin ^= nYmax;
		}
		
		nXmin -= nTol; // adjust for tolerances
		nYmin -= nLatTol;
		nXmax += nTol;
		nYmax += nLatTol;
		
		HashBucket oSearch = new HashBucket(); // find polyline sets by hash index
		
		int nXbeg = HashBucket.getBucket(nXmin); // get hash grid indices
		int nYbeg = HashBucket.getBucket(nYmin);
		
		int nXend = HashBucket.getBucket(nXmax);
		int nYend = HashBucket.getBucket(nYmax);

		HashBucket oHash; // <= comparison used to always have at least one bucket
		synchronized (m_oHashes)
		{
			for (int nY = nYbeg; nY <= nYend; nY++)
			{
				for (int nX = nXbeg; nX <= nXend; nX++)
				{
					oSearch.m_nHash = HashBucket.hashBucketVals(nX, nY);
					int nCellIndex = Collections.binarySearch(m_oHashes, oSearch);
					if (bAdd && nCellIndex < 0) // existing bucket cell not found
					{
						oHash = new HashBucket();
						oHash.m_nHash = oSearch.m_nHash;
						nCellIndex = ~nCellIndex;
						m_oHashes.add(nCellIndex, oHash); // add bucket cell to cache
					}

					if (nCellIndex >= 0)
						oHashes.add(m_oHashes.get(nCellIndex));
				}
			}
		}
	}
	
	
	/**
	 * Attempts to get roadway segment from the list that is the closest to the 
	 * given point that is within the tolerance using a snap point to line 
	 * algorithm.
	 * 
	 * @param nTol tolerance to add to/subtract from longitudes
	 * @param nLon longitude of point in decimal degrees scaled to 7 decimal places
	 * @param nLat latitude of point in decimal degrees scaled to 7 decimal places
	 * @param oWays list of roadway segments to try and snap the point to
	 * @return The closest roadway segment to the point. If the point fails to
	 * snap to any roadway segment {@code null} is returned
	 */
	public OsmWay getWay(int nTol, int nLon, int nLat, ArrayList<OsmWay> oWays)
	{
		int nDist = Integer.MAX_VALUE; // track minimum distance
		ArrayList<WaySnapInfo> oInTol = new ArrayList();
		for (OsmWay oWay : oWays) // try to snap to each roadway segment
		{
			WaySnapInfo oInfo = oWay.snap(nTol, nLon, nLat); // snap algorithm
			if (oInfo.m_nSqDist >= 0 && oInfo.m_nSqDist <= nDist) // only keep track of ways that are candidates for been the closest
			{
				nDist = oInfo.m_nSqDist;
				oInfo.m_oWay = oWay;
				int nIndex = Collections.binarySearch(oInTol, oInfo);
				if (nIndex < 0)
					oInTol.add(~nIndex, oInfo);
			}
		}
		if (nDist == Integer.MAX_VALUE)
			return null; // no link found

		return oInTol.get(0).m_oWay; // return the closest of all the roadway segments that were within the tolerance
	}
	
	
	/**
	 * Determines that roadway segments that are close to the given point and calls
	 * {@link #getWay(int, int, int, java.util.ArrayList)}.
	 * 
	 * @param nTol tolerance to add to/subtract from longitudes
	 * @param nLon longitude of point in decimal degrees scaled to 7 decimal places
	 * @param nLat latitude of point in decimal degrees scaled to 7 decimal places
	 * @return The closest roadway segment to the point. If the point fails to
	 * snap to any roadway segment {@code null} is returned
	 */
	public OsmWay getWay(int nTol, int nLon, int nLat)
	{
		ArrayList<OsmWay> oWays = new ArrayList();
		int nLatTol = getWays(oWays, nTol, nLon, nLat, nLon, nLat);
		if (oWays.isEmpty()) // no set of links nearby
			return null;

		return getWay(nLatTol, nLon, nLat, oWays);
	}
	
	
	/**
	 * Fills the given list with roadway segments are in the hash grids that
	 * intersect the bounding box defined by the two longitude and latitude points
	 * and the tolerance.
	 * 
	 * @param oWays list to fill with roadway segments
	 * @param nTol tolerance to add to/subtract from longitudes
	 * @param nLon1 longitude of point 1 in decimal degrees scaled to 7 decimal places
	 * @param nLat1 latitude of point 1 in decimal degrees scaled to 7 decimal places
	 * @param nLon2 longitude of point 2 in decimal degrees scaled to 7 decimal places
	 * @param nLat2 latitude of point 2 in decimal degrees scaled to 7 decimal places
	 * @return tolerance adjusted for latitude
	 */
	public int getWays(ArrayList<OsmWay> oWays, int nTol, int nLon1, int nLat1, int nLon2, int nLat2)
	{
		ArrayList<HashBucket> oBuckets = new ArrayList();
		int nLatTol = (int)(nTol / Math.cos(Math.PI
		   * GeoUtil.fromIntDeg((nLat1 + nLat2) / 2) / 180.0)); // adjust latitude tolerance due to curvature of earth
		getHashes(oBuckets, nLon1, nLat1, nLon2, nLat2, nTol, nLatTol, false);
		if (oBuckets.isEmpty()) // no set of links nearby
			return nLatTol;

		for (HashBucket oBucket : oBuckets)
		{
			for (OsmWay oWay : oBucket)
			{
				if (!oWay.m_bInUse)
					continue;
				int nIndex = Collections.binarySearch(oWays, oWay, OsmWay.WAYBYTEID);
				if (nIndex < 0) // include each segment only once
					oWays.add(~nIndex, oWay);
			}
		}
		
		return nLatTol;
	}
	
	
	/**
	 * Creates a new list of WaySnapInfos and returns it. The list gets filled
	 * with objects that encapsulate the roadway segments that are close to the 
	 * given point and geometric parameters calculated during the snap algorithm
	 * to make better decisions on which roadway segment the point should be snapped 
	 * to if there are multiple candidates.
	 * 
	 * @param oWays list of roadway segments to attempt to snap the point to
	 * @param nTol tolerance to add to/subtract from longitudes
	 * @param nLon longitude of point in decimal degrees scaled to 7 decimal places
	 * @param nLat latitude of point in decimal degrees scaled to 7 decimal places
	 * @param dHdg the angle associated with the given point. Usually the direction
	 * to the next point in a line string representing a roadway segment. Use
	 * {@code Double.NaN} if the point is not directed
	 * @param dHdgTol the angle used as a tolerance to determine if the roadway
	 * segment the point gets snapped to is going in the same direction. 
	 * @param oComp Comparator used to sort the WaySnapInfo objects created from
	 * the snapping algorithm
	 * @return A list of WaySnapInfos of candidate roadway segments the point 
	 * could be snapped in sorted order defined by the Comparator.
	 */
	public ArrayList<WaySnapInfo> getSnappedWays(ArrayList<OsmWay> oWays, int nTol, int nLon, int nLat, double dHdg, double dHdgTol, Comparator<WaySnapInfo> oComp)
	{
		ArrayList<WaySnapInfo> oInTol = new ArrayList();
		if (oWays.isEmpty()) // no roadway segments to check
			return oInTol;
		boolean bHasPerp = false;
		for (OsmWay oWay : oWays)
		{
			WaySnapInfo oInfo = oWay.snap(nTol, nLon, nLat); // attempt to snap
			
			if (oInfo.m_nSqDist >= 0) // positive squared distance means the point can be snapped to the segment
			{
				if (Double.isFinite(dHdg)) // for directed points
				{
					oWay.setHdgs(); // ensure headings for the nodes are correcto for this roadway segment
					double dHdgDiff = GeoUtil.hdgDiff(dHdg, oInfo.m_oWay.m_oNodes.get(oInfo.m_nIndex).m_dHdg);
					if (dHdgDiff > dHdgTol) // skip ways that are not in the "same" direction
						continue;
				}
				bHasPerp = bHasPerp || oInfo.m_bPerpAlgorithm;
				int nIndex = Collections.binarySearch(oInTol, oInfo, oComp);
				if (nIndex < 0) // add WaySnapInfos in sorted order
					oInTol.add(~nIndex, oInfo);
			}
		}

		if (bHasPerp)
		{
			int nIndex = oInTol.size();
			while (nIndex-- > 0)
			{
				if (!oInTol.get(nIndex).m_bPerpAlgorithm)
					oInTol.remove(nIndex);
			}
		}
		return oInTol;
	}
	
	
	/**
	 * Wrapper for calling {@link #getWays(java.util.ArrayList, int, int, int, int, int)}
	 * and then {@link #getSnappedWays(java.util.ArrayList, int, int, int, double, double, java.util.Comparator)}
	 * 
	 * @param nTol tolerance to add to/subtract from longitudes
	 * @param nLon longitude of point in decimal degrees scaled to 7 decimal places
	 * @param nLat latitude of point in decimal degrees scaled to 7 decimal places
	 * @param dHdg the angle associated with the given point. Usually the direction
	 * to the next point in a line string representing a roadway segment. Use
	 * {@code Double.NaN} if the point is not directed
	 * @param dHdgTol the angle used as a tolerance to determine if the roadway
	 * segment the point gets snapped to is going in the same direction. 
	 * @param oComp Comparator used to sort the WaySnapInfo objects created from
	 * the snapping algorithm
	 * @return A list of WaySnapInfos of candidate roadway segments the point 
	 * could be snapped in sorted order defined by the Comparator.
	 */
	public ArrayList<WaySnapInfo> getSnappedWays(int nTol, int nLon, int nLat, double dHdg, double dHdgTol, Comparator<WaySnapInfo> oComp)
	{
		ArrayList<OsmWay> oWays = new ArrayList();
		int nLatTol = getWays(oWays, nTol, nLon, nLat, nLon, nLat);
		return getSnappedWays(oWays, nLatTol, nLon, nLat, dHdg, dHdgTol, oComp);
	}
	
	
	/**
	 * Gets the roadway segment with the given Id.
	 * @param oId Id of the OsmWay to search for
	 * @return The OsmWay with the given Id, if there is not an OsmWay with the
	 * given Id, {@code null} is returned.
	 */
	public OsmWay getWayById(Id oId)
	{
		OsmWay oSearch = new OsmWay();
		oSearch.m_oId = oId;
		synchronized (m_oWaysById)
		{
			int nIndex = Collections.binarySearch(m_oWaysById, oSearch, OsmWay.WAYBYTEID);
			if (nIndex >= 0)
			{
				OsmWay oReturn = m_oWaysById.get(nIndex);
				if (oReturn.m_bInUse)
					return oReturn;
			}
		}
		return null;
	}
	
	
	/**
	 * Gets the Network with the given NetworkId
	 * @param sId Base64 encoded 16 byte NetworkId
	 * @return The Network with the given NetworkId, if there is not a Network 
	 * with the given Id, {@code null} is returned.
	 */
	public Network getNetwork(String sId)
	{
		Network oSearch = new Network();
		oSearch.m_sNetworkId = sId;
		synchronized (m_oNetworks)
		{
			int nIndex = Collections.binarySearch(m_oNetworks, oSearch);
			if (nIndex >= 0)
				return m_oNetworks.get(nIndex);
		}
		
		return null;
	}
	
	
	/**
	 * Deletes the Network with the given Id by removing it from the in memory
	 * list, rewriting the Network JSON without it, and deleting the directory
	 * and files associated with the Network.
	 * 
	 * @param sId Base64 encoded 16 byte NetworkId of the Network to delete.
	 * @return true if the Network is found and no Exceptions are thrown in the
	 * process of deleting it
	 * @throws IOException
	 */
	public boolean deleteNetwork(String sId)
		throws IOException
	{
		Network oSearch = new Network();
		oSearch.m_sNetworkId = sId;
		Network oRemove = null;
		synchronized (m_oNetworks)
		{
			int nIndex = Collections.binarySearch(m_oNetworks, oSearch);
			if (nIndex >= 0) // find the Network in memory
			{
				oRemove = m_oNetworks.remove(nIndex); // remove it from memory
			}
		}
		writeNetworkFile(); // rewrite the Network JSON file
		if (oRemove == null)
			return false;
		if (oRemove.isStatus(Network.PUBLISHED))
			updateWays(oRemove);
		Path oDir = Paths.get(String.format(m_sGeoFileFormat, sId, "unpublished")).getParent();
		if (Files.exists(oDir)) // delete all of the files and directory associated with the Network.
		{
			List<Path> oPaths = Files.walk(oDir, FileVisitOption.FOLLOW_LINKS).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
			for (Path oPath : oPaths)
			{
				if (Files.exists(oPath))
					Files.delete(oPath);
			}
		}
		return true;
	}
	
	
	public synchronized void updateWays(Network oNetwork)
	{
		for (OsmWay oWay : oNetwork.m_oNetworkWays)
		{
			oWay.m_bInUse = false;
		}
		m_oLogger.debug("Flagged to remove: " + oNetwork.m_oNetworkWays.size());
		ArrayList<HashBucket> oNewBuckets = new ArrayList(m_oHashes.size());
		for (int nHashIndex = 0; nHashIndex < m_oHashes.size(); nHashIndex++)
		{
			HashBucket oHash = m_oHashes.get(nHashIndex);
			HashBucket oNew = new HashBucket(oHash.m_nHash);

			for (int nWayIndex = 0; nWayIndex< oHash.size(); nWayIndex++)
			{
				OsmWay oWay = oHash.get(nWayIndex);
				if (oWay.m_bInUse)
					oNew.add(oWay);
				else
					oWay.removeRefs();
			}
			oNew.m_oNodes = OsmUtil.removeZeroRefs(oHash.m_oNodes);
			oNewBuckets.add(oNew);
		}
		
		ArrayList<OsmWay> oNewWaysById = new ArrayList(m_oWaysById.size());
		ArrayList<WayMetadata> oNewMetadata = new ArrayList(m_oMetadata.size());
		for (int nWayIndex = 0; nWayIndex < m_oWaysById.size(); nWayIndex++)
		{
			OsmWay oWay = m_oWaysById.get(nWayIndex);
			if (oWay.m_bInUse)
			{
				oNewWaysById.add(oWay);
				WayMetadata oMetadata = getMetadata(oWay.m_oId);
				if (oMetadata.m_oId.compareTo(Id.NULLID) != 0)
					oNewMetadata.add(oMetadata);
			}
		}
		int nIndex = Collections.binarySearch(oNewMetadata, DEFAULTMETADATA);
		if (nIndex < 0)
			oNewMetadata.add(~nIndex, DEFAULTMETADATA);
		
		ArrayList<HashBucket> oTempHashes;
		synchronized (m_oHashes)
		{
			oTempHashes = m_oHashes;
			m_oHashes = oNewBuckets;
		}
		ArrayList<OsmWay> oTempWays;
		synchronized (m_oWaysById)
		{
			oTempWays = m_oWaysById;
			m_oWaysById = oNewWaysById;
		}
		m_oLogger.debug("Total ways after update: " + m_oWaysById.size());
		ArrayList<WayMetadata> oTempMetadata;
		synchronized (m_oMetadata)
		{
			oTempMetadata = m_oMetadata;
			m_oMetadata = oNewMetadata;
		}
		
		for (nIndex = 0; nIndex < oTempHashes.size(); nIndex++)
		{
			HashBucket oHash = oTempHashes.get(nIndex);
			oHash.clear();
			oHash.m_oNodes.clear();
			oHash.m_oNodes = null;
		}
		oTempHashes.clear();
		oTempHashes = null;
		
		for (nIndex = 0; nIndex < oTempWays.size(); nIndex++)
		{
			OsmWay oWay = oTempWays.get(nIndex);
			if (!oWay.m_bInUse)
			{
				oWay.clear();
				oWay.m_oNodes.clear();
				oWay.m_oNodes = null;
			}
		}
		oTempWays.clear();
		oTempWays = null;
		
		oTempMetadata.clear();
		oTempMetadata = null;
	}
	
	/**
	 * Resets the Network with the given Id to have its roadway segments be
	 * reprocessed.
	 * 
	 * @param sId Base64 encoded 16 byte NetworkId of the Network to reprocess.
	 * @param sLabel Label to assign to the Network
	 * @return true if the Network is found and no Exceptions are thrown in 
	 * resetting it
	 * @throws IOException
	 */
	public boolean reprocessNetwork(String sId, String sLabel)
		throws IOException
	{
		Network oSearch = new Network();
		oSearch.m_sNetworkId = sId;
		synchronized (m_oNetworks)
		{
			int nIndex = Collections.binarySearch(m_oNetworks, oSearch);
			if (nIndex >= 0) // find the Network in memory
			{
				Network oNetwork = m_oNetworks.get(nIndex);
				oNetwork.m_sLabel = sLabel; // update the label
				oNetwork.removeStatus(Network.WORKINPROGRESS);
				oNetwork.addStatus(Network.ASSEMBLING);
				writeNetworkFile(); // rewrite the Network JSON file to save the changes
				return true;
			}
		}
		
		return false;
	}
	
	
	/**
	 * Calls {@link Scheduling#execute(java.lang.Runnable)} to schedule the 
	 * Network with the given Id to be published which calls {@link OsmUtil#publishNetwork(java.lang.String, java.lang.String, java.lang.String[], java.lang.String[], java.lang.String[], java.lang.String)}
	 * and then rewrites the Network JSON file to save the changes.
	 * @param sId Base64 encoded 16 byte Network Id of the Network to publish
	 * @param sStateShps Path to the shapefile that contains the geometric 
	 * definitions of the states in the USA.
	 * @param sOsmDir Path to the directory that contains the Open Street Map
	 * files
	 */
	public void publishNetwork(String sId, String sStateShps, String sOsmDir, boolean bTrafficModel, boolean bRoadWxModel, boolean bExternalPublish)
	{
		Network oNetwork = getNetwork(sId);
		if (oNetwork.isStatus(Network.PUBLISHING)) //  publishing in progress
		{
			return;
		}
		if (oNetwork == null) // if a Network with the id isn't found do nothing
		{
			m_oLogger.error(sId + " does not exist");
			return;
		}
		oNetwork.removeStatus(Network.WORKINPROGRESS);
		oNetwork.addStatus(Network.PUBLISHING);
		try
		{
			writeNetworkFile();
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
		Scheduling.getInstance().execute(() -> 
		{
			try
			{
				OsmUtil.publishNetwork(m_sGeoFileFormat, sId, sStateShps, oNetwork.m_sStates, oNetwork.m_sFilter, oNetwork.m_sOptions, sOsmDir, m_oLogger);
				if (oNetwork.isStatus(Network.PUBLISHED)) // if the network is already published, remove the old definitions of ways
					updateWays(oNetwork);
				oNetwork.m_bCanRunTraffic = bTrafficModel;
				oNetwork.m_bCanRunRoadWeather = bRoadWxModel;
				oNetwork.m_bExternalPublish = bExternalPublish;
				oNetwork.removeStatus(Network.PUBLISHING);
				oNetwork.addStatus(Network.PUBLISHED);
				SessMgr.getInstance().addNetwork(oNetwork.m_sNetworkId);
				loadNetwork(oNetwork.toGeoJsonFeature(false));
				writeNetworkFile(); // save changes
			}
			catch (Exception oEx)
			{
				m_oLogger.error("Failed to publish " + sId);
				m_oLogger.error(oEx, oEx);
				oNetwork.m_nStatus = Network.ERROR;
				try
				{
					writeNetworkFile(); // save changes
				}
				catch (IOException oIOEx)
				{
					m_oLogger.error(oIOEx, oIOEx);
				}
			}
		});
	}
	
	
	/**
	 * Creates and adds a new Network with the given parameters to the in memory
	 * list and rewrites the Network JSON file to save the changes.
	 * 
	 * @param sId Base64 encoded 16 byte NetworkId
	 * @param sLabel Label of the Network
	 * @param sOptions Array of options used to create the Network
	 * @param sCoords comma separated String of the coordinates of the polygon
	 * used to create the Network.
	 */
	public void createNetwork(String sId, String sLabel, String[] sOptions, String sCoords)
		throws IOException
	{
		String[] sCoordArray = sCoords.split(",");
		int[] nGeo = Arrays.newIntArray(sCoordArray.length + 7); // add 4 for the bounding box, 1 for hole flag, and 2 for the first point repeated to close the polygon
		nGeo = Arrays.add(nGeo, 1); // 1 ring
		nGeo = Arrays.add(nGeo, sCoordArray.length / 2); // number of points
		nGeo = Arrays.add(nGeo, new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE}); // initialize the bounding box
		for (int nIndex = 0; nIndex < sCoordArray.length;)
		{
			int nLon = GeoUtil.toIntDeg(Double.parseDouble(sCoordArray[nIndex++]));
			int nLat = GeoUtil.toIntDeg(Double.parseDouble(sCoordArray[nIndex++]));
			nGeo = Arrays.addAndUpdate(nGeo, nLon, nLat, 3); // add point to the array
		}
		Network oNetwork = new Network(sId, sLabel, sOptions, nGeo);
		
		synchronized (m_oNetworks)
		{
			int nIndex = Collections.binarySearch(m_oNetworks, oNetwork); // add the network to the list in sorted order
			if (nIndex < 0)
				m_oNetworks.add(~nIndex, oNetwork);
			writeNetworkFile();
		}
	}
	
	
	/**
	 * Creates and writes a GeoJson representation of each network as the Network
	 * JSON file. It contains of a JSONArray that contains GeoJson Feature objects.
	 * @throws IOException
	 */
	public void writeNetworkFile()
		throws IOException
	{
		JSONArray oFeatures = new JSONArray();
		synchronized (m_oNetworks)
		{
			for (Network oNetwork : m_oNetworks)
			{
				oFeatures.put(oNetwork.toGeoJsonFeature(false));
			}
		
			try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(Paths.get(m_sNetworkFile), FileUtil.WRITEOPTS), "UTF-8"))) // rewrite the entire file
			{
				oFeatures.write(oOut);
			}
		}
	}
	
	
	/**
	 * Gets the speed limit of the roadway segment with the given Id.
	 * 
	 * @param oId Id of the OsmWay to get the speed limit for
	 * @return The speed limit value stored in the metadata list for the given
	 * Id. If the Id is not found in the list, -1.
	 */
	public int getSpdLimit(Id oId)
	{
		synchronized (m_oMetadata)
		{
			WayMetadata oSearch = new WayMetadata(oId);
			int nIndex = Collections.binarySearch(m_oMetadata, oSearch);
			if (nIndex >= 0)
				return m_oMetadata.get(nIndex).m_nSpdLimit;
		}
		
		return -1;
	}
	
	
	/**
	 * Gets the number of lanes of the roadway segment with the given Id.
	 * 
	 * @param oId Id of the OsmWay to get the number of lanes for
	 * @return The number of lanes stored in the metadata list for the given
	 * Id. If the Id is not found in the list, -1.
	 */
	public int getLanes(Id oId)
	{
		synchronized (m_oMetadata)
		{
			WayMetadata oSearch = new WayMetadata(oId);
			int nIndex = Collections.binarySearch(m_oMetadata, oSearch);
			if (nIndex >= 0)
				return m_oMetadata.get(nIndex).m_nLanes;
		}
		
		return -1;
	}
	
	
	/**
	 * Gets the average mean sea level elevation of the roadway segment with the
	 * given Id.
	 * 
	 * @param oId Id of the OsmWay to get the mea sea level elevation for
	 * @return The average of the mean sea level elevations stored in the metadata
	 * list for the given Id. If the Id is not found in the list, {@code Double.NaN}
	 */
	public double getMslElev(Id oId)
	{
		synchronized (m_oMetadata)
		{
			WayMetadata oSearch = new WayMetadata(oId);
			int nIndex = Collections.binarySearch(m_oMetadata, oSearch);
			if (nIndex >= 0)
			{
				WayMetadata oFound = m_oMetadata.get(nIndex);
				return (oFound.m_dMinMslElev + oFound.m_dMaxMslElev) / 2;
			}
		}
		return Double.NaN;
	}
	
	
	/**
	 * Creates a new list and adds the published Networks to it.
	 * 
	 * @return The created list that is filled with published Networks
	 */
	public ArrayList<Network> getNetworks()
	{
		ArrayList<Network> oReturn = new ArrayList();
		synchronized (m_oNetworks)
		{
			for (Network oNetwork : m_oNetworks)
			{
				if (oNetwork.isStatus(Network.PUBLISHED)) // only add published Networks
					oReturn.add(oNetwork);
			}
		}
		
		return oReturn;
	}
	
	
	/**
	 * Creates a new list and adds all of the Networks to it.
	 * @return The created list that is filled all of the Networks
	 */
	public ArrayList<Network> getAllNetworks()
	{
		ArrayList<Network> oReturn = new ArrayList();
		synchronized (m_oNetworks)
		{
			boolean bRewrite = false;
			for (Network oNetwork : m_oNetworks)
			{
				oReturn.add(oNetwork);
				if (oNetwork.isStatus(Network.TRAINING))
				{
					JSONObject oStatus = getHurricaneModelStatus(oNetwork);
					if (oStatus == null)
					{
						bRewrite = true;
						oNetwork.addStatus(Network.ERROR);
						continue;
					}
					
					int nStatus = oStatus.getInt("status");
					if (nStatus == HURMODEL_ERROR)
					{
						bRewrite = true;
						oNetwork.addStatus(Network.ERROR);
						continue;
					}

					if (nStatus == HURMODEL_TRAINING)
					{
						String sNetworkDir = getNetworkDir(oNetwork.m_sNetworkId);
						String sStatusLog = sNetworkDir + MLPHurricane.STATUSLOG;
						oNetwork.m_sMsg = Util.getLastLinesOfFile(sStatusLog, 4);
						if (oNetwork.m_sMsg != null)
							oNetwork.m_sMsg = oNetwork.m_sMsg.replace("\n", "<br>");
					}
				}
				else
					oNetwork.m_sMsg = "";
				
			}
			if (bRewrite)
			{
				try
				{
					writeNetworkFile(); // save changes
				}
				catch (IOException oIOEx)
				{
					m_oLogger.error(oIOEx, oIOEx);
				}
			}
		}
		
		return oReturn;
	}
	
	
	/**
	 * Gets the TimeZone String associated with the given NetworkId. If there is
	 * not a configured mapping for the given Id, "UTC" is returned.
	 * @param sNetworkId Base64 encoded 16 byte Network Id
	 * @return The associated TimeZone string of the given NetworkId, or "UTC" 
	 * if there is not an associated TimeZone.
	 */
	public TimeZone getTimeZone(int nLon, int nLat)
	{
		return TimeZone.getTimeZone("America/Chicago"); // all networks are central time in the current implementation
	}
	
	
	public String getNetworkDir(String sNetworkId)
	{
		String sGeoFile = String.format(m_sGeoFileFormat, sNetworkId, "published");
		return sGeoFile.substring(0, sGeoFile.lastIndexOf("/") + 1);
	}
	
	
	public synchronized JSONObject getHurricaneModelStatus(Network oNetwork)
	{
		String sNetworkDir = getNetworkDir(oNetwork.m_sNetworkId);
		Path oStatusFile = Paths.get(sNetworkDir + "modelstatus.json");
		JSONObject oStatus;
		if (!Files.exists(oStatusFile))
		{
			writeHurricaneModelStatus(oNetwork, "model_b/", HURMODEL_NOTTRAINED);
		}
		try (BufferedReader oIn = Files.newBufferedReader(oStatusFile, StandardCharsets.UTF_8))
		{
			oStatus = new JSONObject(new JSONTokener(oIn));
		}
		catch (IOException oEx)
		{
			m_oLogger.error(oEx, oEx);
			return null;
		}
		
		return oStatus;
	}
	
	
	public synchronized void writeHurricaneModelStatus(Network oNetwork, String sModel, int nStatus)
	{
		String sNetworkDir = getNetworkDir(oNetwork.m_sNetworkId);
		Path oStatusFile = Paths.get(sNetworkDir + "modelstatus.json");
		if (!sModel.endsWith("/"))
			sModel += "/";
		try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oStatusFile, FileUtil.WRITE, FileUtil.FILEPERS), StandardCharsets.UTF_8)))
		{
			oOut.append("{\"model\":\"").append(sModel).append("\",\"status\":").append(Integer.toString(nStatus)).append("}");
		}
		catch (IOException oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
	
	/**
	 * Gets the WayMetadata object of the roadway segment with the given Id.
	 * 
	 * @param oId Id of the OsmWay to get WayMetadata object for
	 * @return The WayMetadata for the given Id. If the Id is not found in the 
	 * list, {@link #DEFAULTMETADATA} is returned
	 */
	public WayMetadata getMetadata(Id oId)
	{
		synchronized (m_oMetadata)
		{
			WayMetadata oSearch = new WayMetadata(oId);
			int nIndex = Collections.binarySearch(m_oMetadata, oSearch);
			if (nIndex >= 0)
				return m_oMetadata.get(nIndex);
		}
		
		return DEFAULTMETADATA;
	}
	
	
	/**
	 * Encapsulates multiple metadata parameters associated with roadway segments
	 */
	public class WayMetadata implements Comparable<WayMetadata>
	{
		/**
		 * Id of the associated OsmWay
		 */
		public Id m_oId;

		
		/**
		 * Speed limit in mph of the roadway segment
		 */
		public int m_nSpdLimit = -1;

		
		/**
		 * Number of lanes of the roadway segment
		 */
		public int m_nLanes = -1;

		
		/**
		 * HOV flag. 
		 * 0 = no HOV lane
		 * 1 = has an HOV lane
		 */
		public int m_nHOV = 0;

		
		/**
		 * Pavement condition enumeration.
		 * 1 = good condition
		 * 2 = average condition
		 * 3 = poor condition
		 */
		public int m_nPavementCondition = 1;

		
		/**
		 * Minimum Mean Sea Level Elevation of the roadway segment
		 */
		public double m_dMinMslElev = Double.NaN;

		
		/**
		 * Maximum Mean Sea Level Elevation of the roadway segment
		 */
		public double m_dMaxMslElev = Double.NaN;

		
		/**
		 * Minimum Ground Elevation of the roadway segment
		 */
		public double m_dMinGroundElev = Double.NaN;

		
		/**
		 * Maximum Ground Elevation of the roadway segment
		 */
		public double m_dMaxGroundElev = Double.NaN;
		
		
		/**
		 * Constructs a WayMetadata with the given Id.
		 * @param oId Id of the associated OsmWay
		 */
		WayMetadata(Id oId)
		{
			m_oId = oId;
		}

		
		/**
		 * Compares WayMetadata by Id
		 */
		@Override
		public int compareTo(WayMetadata o)
		{
			return Id.COMPARATOR.compare(m_oId, o.m_oId);
		}
	}
}
