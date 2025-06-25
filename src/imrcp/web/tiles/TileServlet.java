/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.web.tiles;

import imrcp.collect.NHC;
import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.Mercator;
import imrcp.geosrv.Network;
import imrcp.geosrv.RangeRules;
import imrcp.geosrv.osm.OsmWay;
import imrcp.geosrv.osm.WayIterator;
import imrcp.geosrv.WayNetworks;
import imrcp.store.Obs;
import imrcp.store.ObsList;
import imrcp.store.TileObsView;
import imrcp.system.Arrays;
import imrcp.system.Directory;
import imrcp.system.Introsort;
import imrcp.system.JSONUtil;
import imrcp.system.ObsType;
import imrcp.system.ResourceRecord;
import imrcp.system.Units;
import imrcp.web.ClientConfig;
import imrcp.web.SecureBaseBlock;
import static imrcp.web.SecureBaseBlock.getTrustingConnection;
import imrcp.web.ServerConfig;
import imrcp.web.Session;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import vector_tile.VectorTile;

/**
 * This class is responsible for fulfilling IMRCP Map UI requests for the geometries
 * and locations of Road Layer and Points Layer objects.
 * @author aaron.cherney
 */
public class TileServlet extends SecureBaseBlock
{
	/**
	 * Reference to ObsView which is used to make data queries from all the data
	 * stores.
	 */
	protected static TileObsView OBSVIEW;

	
	/**
	 * Reference to the object to look up roadway segments
	 */
	protected static WayNetworks WAYS;

	
	/**
	 * Comparator used to compare double[] that represent the linestring that
	 * need to be added to tiles by index 0 which is the group value
	 */
	protected static Comparator<double[]> LINECOMP = (double[] o1, double[] o2) -> {return Double.compare(o1[0], o2[0]);};


	
	/**
	 * Array of keys that get added to vector tiles
	 */
	protected String[] m_sKeys = new String[]{"roadtype", "bridge", "lon", "lat"};

	
	/**
	 * Array of values that get added to vector tiles
	 */
	protected String[] m_sValues = new String[]{"H", "A", "Y", "N"};

	
	/**
	 * Observation type id that stands for Road Network Model. Used for Road Layer
	 * requests that only need the geometry of the segments, not any associated
	 * data.
	 */
	protected static final int RNM = Integer.valueOf("rnm", 36);
	protected static final int RNP = Integer.valueOf("rnp", 36);
	
	/**
	 * The first zoom level arterial roadway segments are included in the vector
	 * tiles sent as responses. 
	 */
	protected int m_nMinArterialZoom;

	
	/**
	 * Maps observation type ids to a zoom level that is the minimum zoom level 
	 * that observation type will be included in the vector tiles sent as responses
	 */
	protected HashMap<Integer, Integer> m_oObsTypeZoom = new HashMap();

	
	/**
	 * Default minimum zoom level for observation types to be included in the
	 * vector tiles sent as responses.
	 */
	protected int m_nMinZoom;
	
	
	/**
	 * Value to multiply group values by when creating layer names. Default is 1.
	 * This is used when multiple group values would be rounded to the same 
	 * integer since that would yield the same layer name for the tile.
	 */
	protected HashMap<Integer, Integer> m_oLayerMultipliers = new HashMap();
	
	protected int[] m_nSkipNetworkFilter;
	
	
	
	
	/**
	 * Initializes the servlet by parsing both the servlet configuration and the IMRCP
	 * configuration.
	 */
	@Override
	public void init(ServletConfig oSConfig)
		throws ServletException
	{
		super.init(oSConfig);
		
	}
	
	
	@Override
	public boolean start()
	{
		OBSVIEW = (TileObsView)Directory.getInstance().lookup("ObsView");
		WAYS = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
		return true;
	}
	
	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);

		m_nMinArterialZoom = oBlockConfig.optInt("artzoom", 10);
		m_nMinZoom = oBlockConfig.optInt("minzoom", 6);
		String[] sObsTypes = JSONUtil.getStringArray(oBlockConfig, "obstypes");
		for (String sObsType : sObsTypes)
			m_oObsTypeZoom.put(Integer.valueOf(sObsType, 36), oBlockConfig.optInt(sObsType, m_nMinZoom));
		
		String[] sLayers = JSONUtil.getStringArray(oBlockConfig, "layers");
		int[] nMultipliers = JSONUtil.getIntArray(oBlockConfig, "multipliers");
		for (int nIndex = 0; nIndex < sLayers.length; nIndex++)
			m_oLayerMultipliers.put(Integer.valueOf(sLayers[nIndex], 36), nMultipliers[nIndex]);
		
		String[] sSkipNetwork = JSONUtil.optStringArray(oBlockConfig, "skipnetwork", "TRSCAT");
		m_nSkipNetworkFilter = new int[sSkipNetwork.length];
		for (int nIndex = 0; nIndex < sSkipNetwork.length; nIndex++)
			m_nSkipNetworkFilter[nIndex] = Integer.valueOf(sSkipNetwork[nIndex], 36);
		java.util.Arrays.sort(m_nSkipNetworkFilter);
	}
	
	
	/**
	 * This method handles requests for .mvt (Mapbox Vector Tile) files by
	 * generating points and linestrings that intersect the requested map tile
	 * 
	 * @param oRequest object that contains the request the client has made of the servlet
	 * @param oResponse object that contains the response the servlet sends to the client
	 * @param oSession object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws ServletException
	 * @throws IOException
	 */
	public int doMvt(HttpServletRequest oRequest, HttpServletResponse oResponse, Session oSession, ClientConfig oClient)
	   throws ServletException, IOException
	{
		String sUri = oRequest.getRequestURI();
		String[] sUriParts = sUri.split("/");
		
		int nRequestType;
		if (isObsType(sUriParts[sUriParts.length - 5])) // depending on the request it can be an observation type, or a range of values
			nRequestType = Integer.valueOf(sUriParts[sUriParts.length - 5], 36);
		else
			nRequestType = Integer.valueOf(sUriParts[sUriParts.length - 6], 36);
		if (oClient != null)
		{
			boolean bAllowed = false;
			for (int nObsIndex = 0; nObsIndex < oClient.m_nObsTypes.length; nObsIndex++)
			{
				if (nRequestType == oClient.m_nObsTypes[nObsIndex])
				{
					bAllowed = true;
					break;
				}
			}
			if (!bAllowed)
				return HttpServletResponse.SC_UNAUTHORIZED;
		}
		int nGeoType = Integer.valueOf(sUriParts[sUriParts.length - 4]);
		int nZ = Integer.parseInt(sUriParts[sUriParts.length - 3]);
		int nX = Integer.parseInt(sUriParts[sUriParts.length - 2]);
		int nY = Integer.parseInt(sUriParts[sUriParts.length - 1]);
		long lTimestamp = 0L;
		long lRefTime = 0L;
		StringBuilder sTimeCookies = new StringBuilder();
		for (Cookie oCookie : oRequest.getCookies())
		{
			if (oCookie.getName().compareTo("rtime") == 0)
			{
				lRefTime = Long.parseLong(oCookie.getValue());
				sTimeCookies.append(oCookie.getName()).append("=").append(oCookie.getValue()).append(";");
			}
			if (oCookie.getName().compareTo("ttime") == 0)
			{
				lTimestamp = Long.parseLong(oCookie.getValue());
				sTimeCookies.append(oCookie.getName()).append("=").append(oCookie.getValue()).append(";");
			}
		}
		sTimeCookies.setLength(sTimeCookies.length() - 1);
		
		if (nGeoType == Obs.POLYGON)
		{
			if (nRequestType == RNP && nZ >= 6)
				return HttpServletResponse.SC_OK;
			doArea(nZ, nX, nY, lTimestamp, lRefTime, nRequestType, sUriParts[sUriParts.length - 5], oResponse, oSession, sUri, sTimeCookies);
		}
		else
		{
			int nZoomFilter = m_oObsTypeZoom.containsKey(nRequestType) ? m_oObsTypeZoom.get(nRequestType) : m_nMinZoom;
			if (nZ >= nZoomFilter) // ignore requests that are not valid for the given zoom level
			{
				if (nGeoType == Obs.POINT)
				{
					doPoint(nZ, nX, nY, lTimestamp, lRefTime, nRequestType, sUriParts[sUriParts.length - 5], oResponse, oSession, sUri, sTimeCookies);
				}
				else if (nGeoType == Obs.LINESTRING)
				{
					doLinestring(nZ, nX, nY, lTimestamp, lRefTime, nRequestType, oResponse, oSession, sUri, sTimeCookies);
				}
			}
		}
		return HttpServletResponse.SC_OK;
	}
	
	
	/**
	 * Add the vector tile for the given request parameters to the response
	 * 
	 * @param nZ request map tile zoom level
	 * @param nX request map tile x index
	 * @param nY request map tile y index
	 * @param lTimestamp query time in milliseconds since Epoch
	 * @param lRefTime reference time in milliseconds since Epoch
	 * @param nRequestType requested IMRCP observation type id
	 * @param oResponse object that contains the response the servlet sends to the client
	 * @param oSession object that contains information about the user that made the
	 * request
	 * @throws IOException
	 */
	protected void doLinestring(int nZ, int nX, int nY, long lTimestamp, long lRefTime, int nRequestType, HttpServletResponse oResponse, Session oSession, String sRequest, StringBuilder sTimeCookies)
	   throws IOException
	{
		double[] dBounds = new double[4];
		double[] dLonLatBounds = new double[4];
		Mercator oM = new Mercator();
		oM.tileBounds(nX, nY, nZ, dBounds); // get the meter bounds of the requested tile
		oM.lonLatBounds(nX, nY, nZ, dLonLatBounds); // get the lon/lat bounds of the requested tile
		int nLat1 = GeoUtil.toIntDeg(dLonLatBounds[1]);
		int nLon1 = GeoUtil.toIntDeg(dLonLatBounds[0]);
		int nLat2 = GeoUtil.toIntDeg(dLonLatBounds[3]);
		int nLon2 = GeoUtil.toIntDeg(dLonLatBounds[2]);
		
		ArrayList<Network> oNetworks;
		if (oSession != null) // not a forwarded request
		{
			oNetworks = new ArrayList();
			for (String sNetwork : oSession.m_oProfile.m_sNetworks) // determine which networks to process based off of user permissions
			{
				Network oNetwork = WAYS.getNetwork(sNetwork);
				if (oNetwork == null)
					continue;
				int[] nBb = oNetwork.getBoundingBox();
				if (GeoUtil.boundingBoxesIntersect(nBb[0], nBb[1], nBb[2], nBb[3], nLon1, nLat1, nLon2, nLat2))
					oNetworks.add(oNetwork);
			}
		}
		else
		{
			oNetworks = WAYS.getNetworks();
		}
		
		ArrayList<OsmWay> oWays = new ArrayList();
		if (!oNetworks.isEmpty())
		{
			WAYS.getWays(oWays, 0, nLon1, nLat1, nLon2, nLat2); // get the roadway segments that intersect the tile

			int nWayIndex = oWays.size();
			while (nWayIndex-- > 0)
			{
				OsmWay oWay = oWays.get(nWayIndex);
				boolean bInclude = false;
				for (Network oNetwork : oNetworks) // only include roadway segments that are in the Networks the user has permission to use
				{
					if (oNetwork.wayInside(oWay))
					{
						bInclude = true;
						break;
					}
				}
				if (!bInclude)
					oWays.remove(nWayIndex);
			}
		}
		VectorTile.Tile.Builder oTileBuilder = VectorTile.Tile.newBuilder();
		if (!oWays.isEmpty())	
		{
			double dDeltaLon = (dLonLatBounds[2] - dLonLatBounds[0]) * 0.1;
			double dDeltaLat = (dLonLatBounds[3] - dLonLatBounds[1]) * 0.1;
			double[] dLineClippingBounds = new double[]{dLonLatBounds[0] - dDeltaLon, dLonLatBounds[1] - dDeltaLat, dLonLatBounds[2] + dDeltaLon, dLonLatBounds[3] + dDeltaLat};
			int[] nLineClippingBounds = new int[]{GeoUtil.toIntDeg(dLineClippingBounds[0]), GeoUtil.toIntDeg(dLineClippingBounds[1]), GeoUtil.toIntDeg(dLineClippingBounds[2]), GeoUtil.toIntDeg(dLineClippingBounds[3])};
			// compute the bounding box of all the roadway segments to use for the data query
			int nDataQueryLat1 = Integer.MAX_VALUE;
			int nDataQueryLat2 = Integer.MIN_VALUE;
			int nDataQueryLon1 = Integer.MAX_VALUE;
			int nDataQueryLon2 = Integer.MIN_VALUE;
			for (OsmWay oWay : oWays)
			{
				if (oWay.m_nMaxLon > nDataQueryLon2)
					nDataQueryLon2 = oWay.m_nMaxLon;
				if (oWay.m_nMinLon < nDataQueryLon1)
					nDataQueryLon1 = oWay.m_nMinLon;
				if (oWay.m_nMinLat < nDataQueryLat1)
					nDataQueryLat1 = oWay.m_nMinLat;
				if (oWay.m_nMaxLat > nDataQueryLat2)
					nDataQueryLat2 = oWay.m_nMaxLat;
			}
			ObsList oData = OBSVIEW.getData(nRequestType, lTimestamp, lTimestamp + 60000,
			   nDataQueryLat1, nDataQueryLat2, nDataQueryLon1, nDataQueryLon2, lRefTime);
			ArrayList<Obs> oObsList = new ArrayList();

			ResourceRecord oRR = null;
			int nSnapTol = 0;
			if (!oData.isEmpty())
			{
				Introsort.usort(oData, Obs.g_oCompObsByContrib);
				oRR = Directory.getResource(oData.get(0).m_nContribId, nRequestType);
				nSnapTol = (int)Math.round(oM.RES[oRR.getZoom()] * 100); // meters per pixel * 100 
			}
			for (Obs oObs : oData)
			{
				for (Network oNetwork : oNetworks)
				{
					if (oNetwork.obsInside(oObs)) // only include Obs that are in the Networks the user has permission to use
					{
						if (oObs.m_nContribId != oRR.getContribId())
						{
							oRR = Directory.getResource(oObs.m_nContribId, nRequestType);
							nSnapTol = (int)Math.round(oM.RES[oRR.getZoom()] * 100); // meters per pixel * 100 
						}
						if (oObs.m_yGeoType == Obs.POINT || oObs.m_yGeoType == Obs.LINESTRING)
						{
							Iterator<int[]> oIt = Arrays.iterator(oObs.m_oGeoArray, new int[2], oObs.m_yGeoType == Obs.POINT ? 1 : 5, 2);
							boolean bAdded = false;
							while (oIt.hasNext())
							{
								int[] nPt = oIt.next();
								OsmWay oSnap = WAYS.getWay(nSnapTol, nPt[0], nPt[1]);
								if (oSnap == null || !oNetwork.wayInside(oSnap))
									continue;

								oObs.m_oObjId = oSnap.m_oId;
								int nIndex = Collections.binarySearch(oObsList, oObs, Obs.g_oCompObsByObjId); // linestring obs are associated with a roadway segment Id so sort obs by object id
								Obs oCurrentObs = nIndex >= 0 ? oObsList.get(nIndex) : null;

								byte yNewPref = oRR.getPreference();
								byte yCurPref = oCurrentObs == null ? Byte.MAX_VALUE : Directory.getResource(oCurrentObs.m_nContribId, nRequestType).getPreference();
								if (oCurrentObs == null || yNewPref < yCurPref || (yNewPref == yCurPref && oObs.m_lObsTime1 >= oCurrentObs.m_lObsTime1)) // only keep the most preferred and most recent obs for each object id
								{
									if (nIndex < 0)
										oObsList.add(~nIndex, oObs);
									else
										oObsList.set(nIndex, oObs);

									bAdded = true;
								}
							}

							if (bAdded)
								break;
						}
					}
				}
			}

			ArrayList<double[]> oLines = new ArrayList();
			int[] nMids = Arrays.newIntArray(oWays.size() * 2);
			int nValueIndex = m_sValues.length;
			if (!oObsList.isEmpty() || nRequestType == RNM)
			{

				RangeRules oRules = ObsType.getRangeRules(nRequestType, ObsType.getUnits(nRequestType));
				Units oUnits = Units.getInstance();
				double dX1, dX2, dY1, dY2;
				Obs oSearchObs = new Obs();
				for (OsmWay oWay : oWays) // for each way that intersects the tile and that the user has permission to use
				{
					String sHighway = oWay.get("highway"); // to be considered a highway the classification (highway tag) must be motorway or trunk
					boolean bHighway;
					if (sHighway == null)
						bHighway = false;
					else
						bHighway = sHighway.compareTo("motorway") == 0 || sHighway.compareTo("trunk") == 0;
					if (!bHighway && nZ < m_nMinArterialZoom) // only display arterials are the configured zoom levels
						continue;
					oSearchObs.m_oObjId = oWay.m_oId;
					double dVal;
					int nIndex = Collections.binarySearch(oObsList, oSearchObs, Obs.g_oCompObsByObjId); // check if there is an obs associated with the way
					if (nIndex < 0)
						dVal = Double.NaN;
					else
					{
						Obs oObs = oObsList.get(nIndex);
						dVal = oRules.groupValue(oUnits.convert(ObsType.getUnits(oObs.m_nObsTypeId, true), oRules.m_sUnits, oObs.m_dValue));
						if (oRules.shouldDelete(dVal))
							continue;
					}

					if (nRequestType == RNM) // don't skip any roads for Road Network Model
						dVal = 0.0;

					if (Double.isNaN(dVal) || dVal == Integer.MIN_VALUE) // the roadway segment does not have a valid obs or value
						continue;

					WayIterator oIt = oWay.iterator();
					boolean bNotDone;
					if (oIt.hasNext()) // iterate through the points of the roadway segment to find its intersection with the map tile
					{
						int[] nLine = oIt.next();
						boolean bPrevInside = GeoUtil.isInside(nLine[0], nLine[1], nLineClippingBounds[0], nLineClippingBounds[1], nLineClippingBounds[2], nLineClippingBounds[3], 0);
						double[] dLine = new double[66];
						dLine[0] = 7; // insertion point
						dLine[1] = dVal; // group value
						dLine[2] = oWay.m_oId.getLowBytes(); // id in Mapbox can only be an 8-byte number
						dLine[3] = bHighway ? 0.0 : 1.0; // highway flag, values are indices for m_sValues
						dLine[4] = oWay.m_bBridge ? 2.0 : 3.0; // bridge flag, values are indices for m_sValues
						nMids = Arrays.add(nMids, oWay.m_nMidLon, oWay.m_nMidLat);
						dLine[5] = nValueIndex++;
						dLine[6] = nValueIndex++;
						if (bPrevInside) // first point is inside so include it in final linestring
							dLine = addPoint(dLine, GeoUtil.fromIntDeg(nLine[0]), GeoUtil.fromIntDeg(nLine[1]));

						do
						{
							bNotDone = false;
							if (bPrevInside) // previous point was inside 
							{
								if (GeoUtil.isInside(nLine[2], nLine[3], nLineClippingBounds[0], nLineClippingBounds[1], nLineClippingBounds[2], nLineClippingBounds[3], 0)) // current point is inside
									dLine = addPoint(dLine, GeoUtil.fromIntDeg(nLine[2]), GeoUtil.fromIntDeg(nLine[3])); // so add the current point
								else // current point is ouside
								{
									dX1 = GeoUtil.fromIntDeg(nLine[0]);
									dY1 = GeoUtil.fromIntDeg(nLine[1]);
									dX2 = GeoUtil.fromIntDeg(nLine[2]); // so need to calculate the intersection with the tile
									dY2 = GeoUtil.fromIntDeg(nLine[3]);
									dLine = addIntersection(dLine, dX1, dY1, dX2, dY2, dLineClippingBounds); // add intersection points
									bPrevInside = false;
									double[] dFinished = new double[(int)dLine[0] - 1]; // now that the line is outside finish the current line
									System.arraycopy(dLine, 1, dFinished, 0, dFinished.length);
									oLines.add(dFinished);
									dLine[0] = 7; // reset point buffer
								}
							}
							else // previous point was outside
							{
								if (GeoUtil.isInside(nLine[2], nLine[3], nLineClippingBounds[0], nLineClippingBounds[1], nLineClippingBounds[2], nLineClippingBounds[3], 0)) // current point is inside
								{
									dX1 = GeoUtil.fromIntDeg(nLine[0]);
									dY1 = GeoUtil.fromIntDeg(nLine[1]);
									dX2 = GeoUtil.fromIntDeg(nLine[2]); // so need to calculate the intersection with the tile
									dY2 = GeoUtil.fromIntDeg(nLine[3]);
									dLine = addIntersection(dLine, dX1, dY1, dX2, dY2, dLineClippingBounds); // add the intersection
									bPrevInside = true;
									dLine = addPoint(dLine, dX2, dY2); // and the next points
								}
								else // previous point and current point are outside, so check if the line segment intersects the tile
								{
									dX1 = GeoUtil.fromIntDeg(nLine[0]);
									dY1 = GeoUtil.fromIntDeg(nLine[1]);
									dX2 = GeoUtil.fromIntDeg(nLine[2]);
									dY2 = GeoUtil.fromIntDeg(nLine[3]);
									double[] dPoint = new double[2];
									GeoUtil.getIntersection(dX1, dY1, dX2, dY2, dLineClippingBounds[0], dLineClippingBounds[1], dLineClippingBounds[0], dLineClippingBounds[3], dPoint); // check left edge
									if (!Double.isNaN(dPoint[0]))
										dLine = addPoint(dLine, dPoint[0], dPoint[1]);

									GeoUtil.getIntersection(dX1, dY1, dX2, dY2, dLineClippingBounds[0], dLineClippingBounds[3], dLineClippingBounds[2], dLineClippingBounds[3], dPoint); // check top edge
									if (!Double.isNaN(dPoint[0]))
										dLine = addPoint(dLine, dPoint[0], dPoint[1]);

									GeoUtil.getIntersection(dX1, dY1, dX2, dY2, dLineClippingBounds[2], dLineClippingBounds[3], dLineClippingBounds[2], dLineClippingBounds[1], dPoint); // check right edge
									if (!Double.isNaN(dPoint[0]))
										dLine = addPoint(dLine, dPoint[0], dPoint[1]);

									GeoUtil.getIntersection(dX1, dY1, dX2, dY2, dLineClippingBounds[2], dLineClippingBounds[1], dLineClippingBounds[0], dLineClippingBounds[1], dPoint); // check bot edge
									if (!Double.isNaN(dPoint[0]))
										dLine = addPoint(dLine, dPoint[0], dPoint[1]);
								}
							}

							if (oIt.hasNext())
							{
								nLine = oIt.next();
								bNotDone = true;
							}
						} while (bNotDone);

						if (dLine[0] > 7)
						{
							double[] dFinished = new double[(int)dLine[0] - 1];
							System.arraycopy(dLine, 1, dFinished, 0, dFinished.length);
							oLines.add(dFinished);
						}
					}
				}
			}
			Collections.sort(oLines, LINECOMP);
			
			VectorTile.Tile.Layer.Builder oLayerBuilder = VectorTile.Tile.Layer.newBuilder();
			VectorTile.Tile.Feature.Builder oFeatureBuilder = VectorTile.Tile.Feature.newBuilder();
			VectorTile.Tile.Value.Builder oValueBuilder = VectorTile.Tile.Value.newBuilder();

			int nExtent = Mercator.getExtent(nZ);
			double dPrevVal;
			int[] nCur = new int[2]; // reusable arrays for feature methods
			int[] nPoints = new int[65];
			int nIndex = oLines.size();
			while (nIndex-- > 0) // layer write order doesn't matter
			{
				double[] dLine = oLines.get(nIndex);
				dPrevVal = dLine[0];
				TileUtil.addLinestring(oFeatureBuilder, nCur, dBounds, nExtent, dLine, nPoints);
				oLayerBuilder.addFeatures(oFeatureBuilder.build());
				oFeatureBuilder.clear();
				nCur[0] = nCur[1] = 0;
				if (nIndex == 0 || oLines.get(nIndex - 1)[0] != dPrevVal)
				{ // write layer at end of list or when group value will change
					oLayerBuilder.setVersion(2);
					oLayerBuilder.setName(String.format("2_%s%1.0f", Integer.toString(nRequestType, 36).toUpperCase(), dPrevVal));
					oLayerBuilder.setExtent(nExtent);
					for (int i = 0; i < m_sKeys.length; i++)
						oLayerBuilder.addKeys(m_sKeys[i]);

					for (int i = 0; i < m_sValues.length; i++)
					{
						oValueBuilder.setStringValue(m_sValues[i]);
						oLayerBuilder.addValues(oValueBuilder.build());
						oValueBuilder.clear();
					}
					Iterator<int[]> oIt = Arrays.iterator(nMids, new int[1], 1, 1);
					while (oIt.hasNext())
					{
						oValueBuilder.setIntValue(oIt.next()[0]);
						oLayerBuilder.addValues(oValueBuilder.build());
						oValueBuilder.clear();
					}
					oTileBuilder.addLayers(oLayerBuilder.build());
					oLayerBuilder.clear();
				}
			}
		}

		if (oSession != null) // do not forward request if this is already a forwarded request
			forwardRequest(sRequest, nRequestType, Obs.LINESTRING, nLon1, nLat1, nLon2, nLat2, oTileBuilder, sTimeCookies);
		oResponse.setContentType("application/x-protobuf");
		if (oTileBuilder.getLayersCount() > 0)
			oTileBuilder.build().writeTo(oResponse.getOutputStream());
	}
	
	
	/**
	 * Add the vector tile for the given request parameters to the response
	 * 
	 * @param nZ request map tile zoom level
	 * @param nX request map tile x index
	 * @param nY request map tile y index
	 * @param lTimestamp query time in milliseconds since Epoch
	 * @param lRefTime reference time in milliseconds since Epoch
	 * @param nRequestType requested IMRCP observation type id
	 * @param sRangeString used for ObsType.EVT. Range of values that are valid for this request
	 * @param oResponse object that contains the response the servlet sends to the client
	 * @param oSession object that contains information about the user that made the
	 * @throws IOException
	 */
	protected void doPoint(int nZ, int nX, int nY, long lTimestamp, long lRefTime, int nRequestType, String sRangeString, HttpServletResponse oResponse, Session oSession, String sRequest, StringBuilder sTimeCookies)
	   throws IOException
	{
		double[] dBounds = new double[4];
		double[] dLonLatBounds = new double[4];
		Mercator oM = new Mercator();
		oM.tileBounds(nX, nY, nZ, dBounds); // get the meter bounds of the requested tile
		oM.lonLatBounds(nX, nY, nZ, dLonLatBounds);
		int nLat1 = GeoUtil.toIntDeg(dLonLatBounds[1]);
		int nLon1 = GeoUtil.toIntDeg(dLonLatBounds[0]);
		int nLat2 = GeoUtil.toIntDeg(dLonLatBounds[3]);
		int nLon2 = GeoUtil.toIntDeg(dLonLatBounds[2]);
		
		VectorTile.Tile.Builder oTileBuilder = VectorTile.Tile.newBuilder();
		VectorTile.Tile.Layer.Builder oLayerBuilder = VectorTile.Tile.Layer.newBuilder();
		VectorTile.Tile.Feature.Builder oFeatureBuilder = VectorTile.Tile.Feature.newBuilder();
		int[] nCur = new int[2]; // reusable arrays for feature methods
		int nExtent = Mercator.getExtent(nZ);
		int nIdCount = 0;
		ArrayList<Network> oNetworks;
		if (oSession != null) // not a forwarded request
		{
			oNetworks = new ArrayList();
			for (String sNetwork : oSession.m_oProfile.m_sNetworks) // determine which networks to process based off of user permissions
			{
				Network oNetwork = WAYS.getNetwork(sNetwork);
				if (oNetwork == null)
					continue;
				int[] nBb = oNetwork.getBoundingBox();
				if (GeoUtil.boundingBoxesIntersect(nBb[0], nBb[1], nBb[2], nBb[3], nLon1, nLat1, nLon2, nLat2))
					oNetworks.add(oNetwork);
			}
		}
		else
		{
			oNetworks = WAYS.getNetworks();
		}
		
		boolean bSkipNetworks = java.util.Arrays.binarySearch(m_nSkipNetworkFilter, nRequestType) >= 0;
		ArrayList<Obs> oObsList = new ArrayList();
		if (!oNetworks.isEmpty() || bSkipNetworks)
		{
			ObsList oData = OBSVIEW.getData(nRequestType, lTimestamp, lTimestamp + 60000, // query the stores for data
				 nLat1, nLat2, nLon1, nLon2, lRefTime);

			for (Obs oObs : oData)
			{
				if (oObs.m_yGeoType != Obs.POINT)
					continue;
				if (bSkipNetworks)
				{
					oObsList.add(oObs);
					continue;
				}
				for (Network oNetwork : oNetworks)
				{
					if (oNetwork.obsInside(oObs)) // only include obs that are inside the Networks the user has permission to use
					{
						oObsList.add(oObs);
						break;
					}
				}
			}
		}

		if (!oObsList.isEmpty())
		{
			if (nRequestType == ObsType.TRSCAT)
			{
				HashMap<String, String> oBestStorms = NHC.getCurrentStorms(lTimestamp, lTimestamp + 60000, lRefTime);
				ObsList oTempList = new ObsList(oObsList.size());
				for (Obs oObs : oObsList)
				{
					if (oBestStorms.get(oObs.m_sStrings[0]).compareTo(oObs.m_sStrings[1]) == 0)
						oTempList.add(oObs);
				}
				oObsList = oTempList;
			}
			if (nRequestType != ObsType.VARIES) // event requests have specific ranges of values that are valid
			{
				ArrayList<Double> oAlertValues = new ArrayList();
				if (nRequestType == ObsType.EVT)
				{
					getAlertFilter(sRangeString.split(","), oAlertValues);
				}
				Collections.sort(oObsList, Obs.g_oCompObsByValue);

				double dPrevVal;	
				int nObsIndex = oObsList.size();
				while (nObsIndex-- > 0)
				{
					Obs oObs = oObsList.get(nObsIndex);
					if (nRequestType == ObsType.EVT && Collections.binarySearch(oAlertValues, oObs.m_dValue) < 0) // should handle skipping NaN values, invalid alert types, and cap alerts
						continue;

					double dLon = GeoUtil.fromIntDeg(oObs.m_oGeoArray[1]);
					double dLat = GeoUtil.fromIntDeg(oObs.m_oGeoArray[2]);

					dPrevVal = oObs.m_dValue;
					TileUtil.addPointToFeature(oFeatureBuilder, nCur, dBounds, nExtent, dLon, dLat);
					oFeatureBuilder.setId(nIdCount++);
					oFeatureBuilder.setType(VectorTile.Tile.GeomType.POINT);
					oLayerBuilder.addFeatures(oFeatureBuilder.build());
					oFeatureBuilder.clear();
					nCur[0] = nCur[1] = 0;
					if (nObsIndex == 0 || oObsList.get(nObsIndex - 1).m_dValue != dPrevVal)
					{ // write layer at end of list or when group value will change
						oLayerBuilder.setVersion(2);
						oLayerBuilder.setName(String.format("1_%s%1.0f", Integer.toString(nRequestType, 36).toUpperCase(), dPrevVal));
						oLayerBuilder.setExtent(nExtent);
						oTileBuilder.addLayers(oLayerBuilder.build());
						oLayerBuilder.clear();
					}
				}
			}
			else // other observation types correspond to ESS or CV data
			{
				Introsort.usort(oObsList, Obs.g_oCompObsByContribLocation); // sort obs by contributor and location to get a set of sensor locations
				VectorTile.Tile.Layer.Builder oCV = VectorTile.Tile.Layer.newBuilder(); // CV is a separate layer
				VectorTile.Tile.Value.Builder oValueBuilder = VectorTile.Tile.Value.newBuilder();
				oCV.addKeys("onlyspeed");
				oValueBuilder.setBoolValue(false);
				oCV.addValues(oValueBuilder);
				oValueBuilder.setBoolValue(true);
				oCV.addValues(oValueBuilder);

				int nObsIndex = oObsList.size();
				double dPrevLon = Double.MAX_VALUE;
				double dPrevLat = Double.MAX_VALUE;
				boolean bOnlySpeed = true;
				while (nObsIndex-- > 0)
				{
					Obs oObs = oObsList.get(nObsIndex);
	//				if (!Id.isSensor(oObs.m_oObjId)) // only include obs associated to a sensor
	//					continue;
					double dLon = GeoUtil.fromIntDeg(oObs.m_oGeoArray[1]);
					double dLat = GeoUtil.fromIntDeg(oObs.m_oGeoArray[2]);

					if (oObs.m_nObsTypeId != ObsType.SPDLNK)
					{
						bOnlySpeed = false;
					}


					if (dLon != dPrevLon || dLat != dPrevLat) // create a new point in the layer when the location is different
					{
						TileUtil.addPointToFeature(oFeatureBuilder, nCur, dBounds, nExtent, dLon, dLat);
						oFeatureBuilder.setType(VectorTile.Tile.GeomType.POINT);
						oFeatureBuilder.setId(oObs.m_oObjId.getLowBytes());

						if (oObs.isMobile()) // mobile/CV data
						{
							oFeatureBuilder.addTags(0);
							oFeatureBuilder.addTags(bOnlySpeed ? 1 : 0);
							oCV.addFeatures(oFeatureBuilder.build()); // so add to the correct layer
						}
						else
							oLayerBuilder.addFeatures(oFeatureBuilder.build());
						oFeatureBuilder.clear();
						nCur[0] = nCur[1] = 0;
						bOnlySpeed = true;
					}


					if ((nObsIndex == 0 || oObsList.get(nObsIndex - 1).m_nContribId != oObs.m_nContribId) && oLayerBuilder.getFeaturesCount() > 0) // write the layer to the tile if it is the last Obs or a different contributor than the previous obs
					{
						// write layer at end of list or when group value will change
						oLayerBuilder.setVersion(2);
						oLayerBuilder.setName(Integer.toString(oObs.m_nContribId, 36).toUpperCase());
						oLayerBuilder.setExtent(nExtent);
						oTileBuilder.addLayers(oLayerBuilder.build());
						oLayerBuilder.clear();
					}
				}

				if (oCV.getFeaturesCount() > 0) // add the CV layer to the tile only if it has features in it
				{
					oCV.setVersion(2);
					oCV.setName("CV");
					oCV.setExtent(nExtent);
					oTileBuilder.addLayers(oCV.build());
					oCV.clear();
				}
			}
		}
		
		if (oSession != null) // do not forward request if this is already a forwarded request
			forwardRequest(sRequest, nRequestType, Obs.POINT, nLon1, nLat1, nLon2, nLat2, oTileBuilder, sTimeCookies);
		oResponse.setContentType("application/x-protobuf");

		if (oTileBuilder.getLayersCount() > 0)
			oTileBuilder.build().writeTo(oResponse.getOutputStream());
	}
	
	
	private void forwardRequest(String sRequest, int nRequestType, int nGeoRequestType, int nLon1, int nLat1, int nLon2, int nLat2, VectorTile.Tile.Builder oTileBuilder, StringBuilder sTimeCookies)
	{
		for (int nIndex = 0; nIndex < SERVERS.size(); nIndex++)
		{
			ServerConfig oConfig = SERVERS.get(nIndex);
			if (oConfig.m_nObsType != nRequestType)
				continue;
			ArrayList<int[]> oGeos = oConfig.m_oNetworkGeometries;
			for (int nGeoIndex = 0; nGeoIndex < oGeos.size(); nGeoIndex++)
			{
				int[] oGeo = oGeos.get(nGeoIndex);
				if (nGeoRequestType == Obs.POLYGON || GeoUtil.boundingBoxesIntersect(nLon1, nLat1, nLon2, nLat2, oGeo[3], oGeo[4], oGeo[5], oGeo[6]))
				{
					try
					{
						HttpsURLConnection oConn = getTrustingConnection(String.format("https://%s%s?uuid=%s", oConfig.m_oHost.getHostName(), sRequest, URLEncoder.encode(oConfig.m_sUUID, StandardCharsets.UTF_8)));
						oConn.setRequestProperty("Cookie", sTimeCookies.toString());
						oConn.setConnectTimeout(500);
						oConn.setReadTimeout(3000);
						try (BufferedInputStream oIn = new BufferedInputStream(oConn.getInputStream()))
						{
							if (oConn.getResponseCode() != HttpServletResponse.SC_OK || oConn.getContentLengthLong() == 0)
								break;

							int nLimit = oTileBuilder.getLayersCount();
							
							VectorTile.Tile oTile = VectorTile.Tile.parseFrom(oIn);
							int nLayers = oTile.getLayersCount();
							for (int nLayerIndex = 0; nLayerIndex < nLayers; nLayerIndex++)
							{
								VectorTile.Tile.Layer oLayer = oTile.getLayers(nLayerIndex);
								int nExistingIndex = -1;
								VectorTile.Tile.Layer.Builder oLayerBuilder = null;
								for (int nExistingLayer = 0; nExistingLayer < nLimit; nExistingLayer++)
								{
									VectorTile.Tile.Layer oExisting = oTileBuilder.getLayers(nExistingLayer);
									if (oLayer.getName().compareTo(oExisting.getName()) == 0)
									{
										nExistingIndex = nExistingLayer;
										oLayerBuilder = VectorTile.Tile.Layer.newBuilder(oExisting);
										int nValueIndex = oLayerBuilder.getValuesCount();
										for (VectorTile.Tile.Feature oFeature: oLayer.getFeaturesList())
										{
											VectorTile.Tile.Feature.Builder oFB = oFeature.toBuilder();
											
											VectorTile.Tile.Value oLonValue = oLayer.getValues(oFB.getTags(5));
											VectorTile.Tile.Value oLatValue = oLayer.getValues(oFB.getTags(7));
											
											oLayerBuilder.addValues(oLonValue);
											oLayerBuilder.addValues(oLatValue);
											
											oFB.setTags(5, nValueIndex++);
											oFB.setTags(7, nValueIndex++);
											oLayerBuilder.addFeatures(oFB);
										}
										break;
									}
								}
								if (nExistingIndex >= 0)
									oTileBuilder.setLayers(nLayerIndex, oLayerBuilder.build());
								else
									oTileBuilder.addLayers(oLayer);
							}
						}
					}
					catch (Exception oEx)
					{
						m_oLogger.error(oEx, oEx);
					}
					break;
				}
			}
		}
	}
	
	
	/**
	 * Add the vector tile for the given request parameters to the response
	 * 
	 * @param nZ request map tile zoom level
	 * @param nX request map tile x index
	 * @param nY request map tile y index
	 * @param lTimestamp query time in milliseconds since Epoch
	 * @param lRefTime reference time in milliseconds since Epoch
	 * @param nRequestType requested IMRCP observation type id
	 * @param oResponse object that contains the response the servlet sends to the client
	 * @throws IOException
	 */
	protected void doArea(int nZ, int nX, int nY, long lTimestamp, long lRefTime, int nRequestType, String sRangeString, HttpServletResponse oResponse, Session oSession, String sRequest, StringBuilder sTimeCookies)
	   throws IOException
	{
		int[] nContribAndSource = new int[2];
	
		ArrayList<ResourceRecord> oRRs = Directory.getResourcesByObsType(nRequestType);
		Introsort.usort(oRRs, ResourceRecord.COMP_BY_PREF);
		ResourceRecord oRR = null;
		double[] dBounds = new double[4];
		
		double[] dLonLatBounds = new double[4];
		Mercator oM = new Mercator();
		oM.tileBounds(nX, nY, nZ, dBounds); // get the meter bounds of the requested tile
		oM.lonLatBounds(nX, nY, nZ, dLonLatBounds);
		int nLat1 = GeoUtil.toIntDeg(dLonLatBounds[1]);
		int nLon1 = GeoUtil.toIntDeg(dLonLatBounds[0]);
		int nLat2 = GeoUtil.toIntDeg(dLonLatBounds[3]);
		int nLon2 = GeoUtil.toIntDeg(dLonLatBounds[2]);
		
		ObsList oData = new ObsList(1);
		String sUnits = "";
		ArrayList<Double> oAlertValues = new ArrayList();
		if (nRequestType == ObsType.EVT)
		{
			getAlertFilter(sRangeString.split(","), oAlertValues);
			oData = OBSVIEW.getData(nRequestType, lTimestamp, lTimestamp + 60000, nLat1, nLat2, nLon1, nLon2, lRefTime);
		}
		else if (nRequestType != RNP)
		{
			sUnits = ObsType.getUnits(nRequestType);
			for (ResourceRecord oTemp : oRRs)
			{
				nContribAndSource[0] = oTemp.getContribId();
				nContribAndSource[1] = oTemp.getSourceId();
				oData = OBSVIEW.getData(nRequestType, lTimestamp, lTimestamp + 60000,
				 nLat1, nLat2, nLon1, nLon2, lRefTime, nContribAndSource);
				if (oData.m_bHasData || !oData.isEmpty())
				{
					oRR = oTemp;
					break;
				}
			}
		}
		else // road network boundary request
		{
			ArrayList<int[]> oPolygons = new ArrayList();
			for (Network oNetwork : WAYS.getNetworks())
			{
				for (int nNetworkIndex = 0; nNetworkIndex < oSession.m_oProfile.m_sNetworks.length; nNetworkIndex++)
				{
					if (oNetwork.m_sNetworkId.compareTo(oSession.m_oProfile.m_sNetworks[nNetworkIndex]) == 0)
					{
						oPolygons.add(oNetwork.getGeometry());
						break;
					}
				}
				
			}
			ServerConfig oPrev = new ServerConfig("", null, 0);
			for (int nIndex = 0; nIndex < SERVERS.size(); nIndex++)
			{
				ServerConfig oConfig = SERVERS.get(nIndex);
				if (oConfig.m_sUUID.compareTo(oPrev.m_sUUID) != 0)
				{
					oPolygons.addAll(oConfig.m_oNetworkGeometries);
				}
				oPrev = oConfig;
			}

			for (int[] nPoly : oPolygons)
			{
				if (GeoUtil.boundingBoxesIntersect(nLon1, nLat1, nLon2, nLat2, nPoly[3], nPoly[4], nPoly[5], nPoly[6]))
				{
					Obs oObs = new Obs();
					oObs.m_dValue = 1;
					oObs.m_yGeoType = Obs.POLYGON;
					oObs.m_oGeoArray = nPoly;
					oData.add(oObs);
				}
			}
		}
		
		RangeRules oRules = ObsType.getRangeRules(nRequestType, sUnits);
		Units oUnits = Units.getInstance();
		HashMap<Double, ObsList> oObsBuckets = new HashMap();

		for (Obs oObs : oData)
		{
			if (oObs.m_yGeoType != Obs.POLYGON && oObs.m_yGeoType != Obs.MULTIPOLYGON && Collections.binarySearch(oAlertValues, oObs.m_dValue) < 0)
				continue;
			double dVal = oRules.groupValue(oUnits.convert(ObsType.getUnits(oObs.m_nObsTypeId, true), oRules.m_sUnits, oObs.m_dValue));
			if (oRules.shouldDelete(dVal))
				continue;
			
			oObs.m_dValue = dVal;
			ObsList oList;
			if (!oObsBuckets.containsKey(dVal))
			{
				oList = new ObsList();
				oObsBuckets.put(dVal, oList);
			}
			else
				oList = oObsBuckets.get(dVal);
			oList.add(oObs);
		}
		if (oRR != null && oRR.getContribId() == Integer.valueOf("nhc", 36))
		{
			Comparator<Obs> oNhcComp = (Obs o1, Obs o2) -> // compare nhc obs by storm number and reference time in reverse order
			{
				int nReturn = (o1.m_sStrings[0]).compareTo(o2.m_sStrings[0]);
				if (nReturn == 0)
					nReturn = Long.compare(o2.m_lTimeRecv, o1.m_lTimeRecv);
				return nReturn;
			};
			HashMap<String, String> oBestStorms = NHC.getCurrentStorms(lTimestamp, lTimestamp + 60000, lRefTime);
			HashMap<Double, ObsList> oTempMap = new HashMap(oObsBuckets.size());
			for (Map.Entry<Double, ObsList> oEntry : oObsBuckets.entrySet())
			{
				ObsList oTempList = new ObsList(oEntry.getValue().size());
				for (Obs oObs : oEntry.getValue())
				{
					if (oBestStorms.get(oObs.m_sStrings[0]).compareTo(oObs.m_sStrings[1]) == 0)
						oTempList.add(oObs);
				}
				oTempMap.put(oEntry.getKey(), oTempList);
			}
			oObsBuckets = oTempMap;
		}
		
		int nClipDelta = (1 << (23 - nZ)) * 8;
		
		HashMap<Double, ArrayList<int[]>> oClipBuckets = new HashMap(oObsBuckets.size());
		int[] nTilePoly = GeoUtil.getBoundingPolygon(nLon1 - nClipDelta, nLat1 - nClipDelta, nLon2 + nClipDelta, nLat2 + nClipDelta);
		long lTilePolyRef = GeoUtil.makePolygon(nTilePoly); 			
		try
		{
			for (Map.Entry<Double, ObsList> oEntry : oObsBuckets.entrySet())
			{
				ArrayList<int[]> oClipped = new ArrayList(oEntry.getValue().size());
				for (Obs oObs : oEntry.getValue())
				{
					int[] nPoly = oObs.m_oGeoArray;
					if (nPoly[3] < nTilePoly[3] || nPoly[5] > nTilePoly[5] ||
						nPoly[4] < nTilePoly[4] || nPoly[6] > nTilePoly[6]) // only clip if polygon is outside of tile bounds
					{

						long lObsPoly = GeoUtil.makePolygon(nPoly);
						long[] lClipRef = new long[]{0L, lTilePolyRef, lObsPoly};
						int nResults = GeoUtil.clipPolygon(lClipRef);
						try
						{
							while (nResults-- > 0)
							{
								try
								{
									oClipped.add(GeoUtil.popResult(lClipRef[0]));
								}
								catch (Exception oEx)
								{
									m_oLogger.error(oEx, oEx);
								}
							}
						}
						finally
						{
							GeoUtil.freePolygon(lObsPoly);
						}
					}
					else
					{
						oClipped.add(nPoly);
					}
				}
				oClipBuckets.put(oEntry.getKey(), oClipped);
			}
		}
		finally
		{
			GeoUtil.freePolygon(lTilePolyRef);
		}
		
//		HashMap<Double, ArrayList<int[]>> oPolyBuckets = new HashMap(oObsBuckets.size());
//		
//		for (Map.Entry<Double, ArrayList<int[]>> oEntry : oClipBuckets.entrySet())
//		{
//			ArrayList<int[]> oList = oEntry.getValue();
//			ArrayList<int[]> oPolys = new ArrayList(oList.size());
//			for (int nOuter = 0; nOuter < oList.size(); nOuter++)
//			{
//				int[] oCur = oList.get(nOuter);
//				if (oCur[0] == 0)
//					continue; // skip already unioned polygons
//				for (int nInner = nOuter + 1; nInner < oList.size(); nInner++)
//				{
//					int[] oCmp = oList.get(nInner);
//					if (oCmp[0] == 0) // skip already unioned polygons
//						continue;
//					long lCurPoly = 0L;
//					long lCmpPoly = 0L;
//					if (GeoUtil.quickIsSamePolygon(oCur, oCmp))
//					{
//						oCmp[0] = 0;
//						continue;
//					}
//					if (GeoUtil.isInsideRingAndHoles(oCur, Obs.POLYGON, oCmp))
//					{
//						try
//						{
//							lCurPoly = GeoUtil.makePolygon(oCur);
//							lCmpPoly = GeoUtil.makePolygon(oCmp);
//							long[] lUnionRef = new long[]{0L, lCurPoly, lCmpPoly};
//							int nResults = GeoUtil.unionPolygon(lUnionRef);
//							if (nResults == 1) // union occured
//							{
//								oCur[0] = 0;
//								oCur = GeoUtil.popResult(lUnionRef[0]);
//								oCmp[0] = 0;
//								nInner = nOuter; // reset loop
//							}
//							else
//							{
//								while (nResults-- > 0) // union did not occur
//								{
//									try
//									{
//										GeoUtil.popResult(lUnionRef[0]); // free resources
//									}
//									catch (Exception oEx)
//									{
//										m_oLogger.error(oEx, oEx);
//									}
//								}
//							}
//
//						}
//						finally
//						{
//							if (lCurPoly != 0L)
//								GeoUtil.freePolygon(lCurPoly);
//							if (lCmpPoly != 0L)
//								GeoUtil.freePolygon(lCmpPoly);
//						}
//						
//					}
//				}
//				oPolys.add(oCur);
//			}
//			oPolyBuckets.put(oEntry.getKey(), oPolys);
//		}
		
		
		VectorTile.Tile.Builder oTileBuilder = VectorTile.Tile.newBuilder();
		VectorTile.Tile.Layer.Builder oLayerBuilder = VectorTile.Tile.Layer.newBuilder();
		VectorTile.Tile.Feature.Builder oFeatureBuilder = VectorTile.Tile.Feature.newBuilder();
		
		int nExtent = Mercator.getExtent(nZ);

		int[] nCur = new int[2]; // reusable arrays for feature methods
		int[] nPoints = new int[65];
		

		for (Map.Entry<Double, ArrayList<int[]>> oEntry : oClipBuckets.entrySet())
		{
			for (int[] nPoly : oEntry.getValue())
				TileUtil.addPolygon(oFeatureBuilder, nCur, dBounds, nExtent, nPoly, nPoints);

			nCur[0] = nCur[1] = 0;
			if (oFeatureBuilder.getGeometryCount() == 0)
				continue;
			oFeatureBuilder.setType(VectorTile.Tile.GeomType.POLYGON);
			oLayerBuilder.clear();
			oLayerBuilder.setVersion(2);
			int nMultiplier = 1;
			if (m_oLayerMultipliers.containsKey(nRequestType))
				nMultiplier = m_oLayerMultipliers.get(nRequestType);
			oLayerBuilder.setName(String.format("3_%s%1.0f", Integer.toString(nRequestType, 36).toUpperCase(), oEntry.getKey() * nMultiplier));
			oLayerBuilder.setExtent(nExtent);
			oLayerBuilder.addFeatures(oFeatureBuilder.build());
			oTileBuilder.addLayers(oLayerBuilder.build());
			oFeatureBuilder.clear();
			oLayerBuilder.clear();

		}


		if (oSession != null && nRequestType != RNP) // do not forward request if this is already a forwarded request, don't forward road network boundary request since the geometries are cached
			forwardRequest(sRequest, nRequestType, Obs.POLYGON, nLon1, nLat1, nLon2, nLat2, oTileBuilder, sTimeCookies);
		oResponse.setContentType("application/x-protobuf");
		if (oTileBuilder.getLayersCount() > 0)
			oTileBuilder.build().writeTo(oResponse.getOutputStream());
	}
		
	
	/**
	 * Returns true if the request string represents an observation type id.
	 * @param sString Request string to check
	 * @return true if the request string is a string representation of an IMRCP 
	 * observation type id, otherwise false
	 */
	static boolean isObsType(String sString)
	{
		if (sString.compareTo("0") == 0 || (sString.length() > 1 && Character.isAlphabetic(sString.charAt(1)))) // "0" is ObsType.ALL. any other string that contains a letter as the second character will be an observation type
			return true;
		
		return false;
	}
	
	
	/**
	 * Checks for and adds the intersection (if found) to the array of points of 
	 * the line segment and the bounding box.
	 * 
	 * @param dPoints array of points the intersection will be added to
	 * @param dX1 longitude of the first point of the line segment in decimal 
	 * degrees
	 * @param dY1 latitude of the first point of the line segment in decimal 
	 * degrees
	 * @param dX2 longitude of the second point of the line segment in decimal
	 * degrees
	 * @param dY2 latitude of the second point of the line segment in decimal 
	 * degrees
	 * @param dLonLats bounding box in the format [min longitude, min latitude,
	 * max longitude, max latitude]
	 * @return reference to the array of points (this could be a new reference than
	 * the one passed into the function if additional space needed to be allocated
	 * to add the intersection point)
	 */
	static double[] addIntersection(double[] dPoints, double dX1, double dY1, double dX2, double dY2, double[] dLonLats)
	{
		double[] dPoint = new double[2];
		GeoUtil.getIntersection(dX1, dY1, dX2, dY2, dLonLats[0], dLonLats[1], dLonLats[0], dLonLats[3], dPoint); // check left edge
		if (!Double.isNaN(dPoint[0]))
			return addPoint(dPoints, dPoint[0], dPoint[1]);
		
		GeoUtil.getIntersection(dX1, dY1, dX2, dY2, dLonLats[0], dLonLats[3], dLonLats[2], dLonLats[3], dPoint); // check top edge
		if (!Double.isNaN(dPoint[0]))
			return addPoint(dPoints, dPoint[0], dPoint[1]);
		
		GeoUtil.getIntersection(dX1, dY1, dX2, dY2, dLonLats[2], dLonLats[3], dLonLats[2], dLonLats[1], dPoint); // check right edge
		if (!Double.isNaN(dPoint[0]))
			return addPoint(dPoints, dPoint[0], dPoint[1]);
		
		GeoUtil.getIntersection(dX1, dY1, dX2, dY2, dLonLats[2], dLonLats[1], dLonLats[0], dLonLats[1], dPoint); // check bot edge
		if (!Double.isNaN(dPoint[0]))
			return addPoint(dPoints, dPoint[0], dPoint[1]);
		
		return dPoints; // no intersections
	}
		
	
	static void getAlertFilter(String[] sRanges, ArrayList<Double> oAlertValues)
	{
		for (String sRange : sRanges)
		{
			String[] sEndPoints = sRange.split(":"); // each comma separated range is colon separated. ,min:max, or a single value
			int nStart = Integer.parseInt(sEndPoints[0]);
			int nEnd;
			if (sEndPoints.length == 1)
				nEnd = nStart;
			else
				nEnd = Integer.parseInt(sEndPoints[1]);
			for (int i = nStart; i <= nEnd; i++) // add all the values in the range to the list
				oAlertValues.add((double)i);
		}
		Collections.sort(oAlertValues);
	}
	
	/**
	 * Adds the given x and y coordinate to the growable array (see {@link imrcp.system.Arrays}.
	 * 
	 * @param dPoints growable array that will have the point added to it
	 * @param dX x coordinate of the point
	 * @param dY y coordinate of the point
	 * @return reference to the growable array (this could be a new reference than
	 * the one passed into the function if additional space needed to be allocated
	 * to add the intersection point)
	 */
	static double[] addPoint(double[] dPoints, double dX, double dY)
	{
		if (dPoints[0] + 2 >= dPoints.length)
		{
			double[] dNewPoints = new double[dPoints.length * 2];
			System.arraycopy(dPoints, 0, dNewPoints, 0, dPoints.length);
			dPoints = dNewPoints;
		}
		int nIndex = (int)dPoints[0]; // extra space for hidden point
		dPoints[nIndex++] = dX;
		dPoints[nIndex++] = dY;
		dPoints[0] = nIndex; // track insertion point in array
		return dPoints;
	}
	
	
	public int doNetworkGeo(HttpServletRequest oRequest, HttpServletResponse oResponse, Session oSession, ClientConfig oClient)
	   throws ServletException, IOException
	{
		try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(oResponse.getOutputStream())))
		{
			for (Network oNetwork : WAYS.getNetworks())
			{
				if (!oNetwork.m_bExternalPublish)
					continue;
				
				int[] nGeo = oNetwork.getGeometry();
				for (int nIndex = 0; nIndex < nGeo[0]; nIndex++)
					oOut.writeInt(nGeo[nIndex]);
			}
		}
		return HttpServletResponse.SC_OK;
	}
}
