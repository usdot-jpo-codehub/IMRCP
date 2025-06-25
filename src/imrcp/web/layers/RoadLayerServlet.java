/*
 * Copyright 2017 Federal Highway Administration.
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
package imrcp.web.layers;

import imrcp.geosrv.Mercator;
import imrcp.geosrv.osm.OsmWay;
import imrcp.geosrv.WayNetworks;
import imrcp.store.Obs;
import imrcp.store.ObsList;
import imrcp.store.TileObsView;
import imrcp.system.Arrays;
import imrcp.system.Directory;
import imrcp.system.JSONUtil;
import imrcp.system.ObsType;
import imrcp.system.Units;
import imrcp.web.ClientConfig;
import imrcp.web.LatLngBounds;
import imrcp.web.ObsChartRequest;
import imrcp.web.ObsInfo;
import imrcp.web.ObsRequest;
import imrcp.web.Session;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.TreeMap;
import org.codehaus.jackson.JsonGenerator;
import org.json.JSONObject;

/**
 * Handles requests from the IMRCP Map UI when Road(line string) layer objects are clicked
 * or when a chart for Road observations is requested
 * @author aaron.cherney
 */
public class RoadLayerServlet extends LayerServlet
{
	/**
	 * Reference to the object to look up roadway segments
	 */
	private WayNetworks m_oRoads;
	
	/**
	 * Configurable array that contains the observation type ids that are valid
	 * for road observations
	 */
	private int[] ROAD_OBSTYPES =
	{
		ObsType.TPVT, ObsType.STPVT, ObsType.DPHLNK, ObsType.DPHSN, ObsType.VOLLNK, ObsType.QPRLNK, ObsType.SPDLNK, ObsType.DNTLNK, ObsType.TRFLNK, ObsType.TDNLNK
	};

	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_oRoads = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
		String[] sObsTypes = JSONUtil.getStringArray(oBlockConfig, "roadobs");
		ROAD_OBSTYPES = Arrays.newIntArray(sObsTypes.length);
		for (int nIndex = 0; nIndex < sObsTypes.length; nIndex++)
		{
			ROAD_OBSTYPES = Arrays.add(ROAD_OBSTYPES, Integer.valueOf(sObsTypes[nIndex], 36));
		}
	}


	/**
	 * Add the response to the given JSON stream for requests made from the IMRCP
	 * Map UI when a Road Layer object is clicked.
	 */
	@Override
	protected void buildObsResponseContent(JsonGenerator oOutputGenerator, ObsRequest oObsRequest, Session oSession, ClientConfig oClient) throws Exception
	{
		oOutputGenerator.writeStartObject();
		
		int nRoadBoundaryPadding = 100;
		int nElevation = Integer.MIN_VALUE;

		DecimalFormat oNumberFormatter = new DecimalFormat("0.##");

		LatLngBounds currentRequestBounds = oObsRequest.getRequestBounds();

		int nMidLon = (currentRequestBounds.getEast() + currentRequestBounds.getWest()) / 2; // midpoint of the requested spatial parameters
		int nMidLat = (currentRequestBounds.getNorth() + currentRequestBounds.getSouth()) / 2;
		OsmWay oRequestRoad = m_oRoads.getWay(nRoadBoundaryPadding, nMidLon, nMidLat);
		oOutputGenerator.writeArrayFieldStart("obs");
		if (oRequestRoad != null)
		{
			int[] nObsToQuery = Arrays.newIntArray();
			if (oClient != null)
			{
				for (int nObsIndex = 0; nObsIndex < oClient.m_nObsTypes.length; nObsIndex++)
				{
					int nClientObs = oClient.m_nObsTypes[nObsIndex];
					for (int nInner = 0; nInner < ROAD_OBSTYPES.length; nInner++)
					{
						int nRoadObs = ROAD_OBSTYPES[nInner];
						if (nClientObs == nRoadObs)
						{
							nObsToQuery = Arrays.add(nObsToQuery, nClientObs);
							break;
						}
					}
				}

				if (nObsToQuery[0] == 1)
					return;
			}
			else
			{
				nObsToQuery = ROAD_OBSTYPES;
			}
			LatLngBounds oPlatformSearchBounds = new LatLngBounds(oRequestRoad.m_nMaxLat + nRoadBoundaryPadding, oRequestRoad.m_nMaxLon + nRoadBoundaryPadding, oRequestRoad.m_nMinLat - nRoadBoundaryPadding, oRequestRoad.m_nMinLon - nRoadBoundaryPadding);
			double dElev = m_oRoads.getMslElev(oRequestRoad.m_oId);
			if (Double.isFinite(dElev))
				nElevation = (int)dElev;

			
			ArrayList<Obs> oObsList = new ArrayList();
			Iterator<int[]> oIt = Arrays.iterator(nObsToQuery, new int[1], 1, 1);
			while(oIt.hasNext()) // for each observation type, use ObsView to query the data stores
			{
				int nObstype = oIt.next()[0];
				TreeMap<ObsInfo, Obs> oObsMap = new TreeMap(ObsInfo.g_oCOMP);
				ObsList oData = ((TileObsView)Directory.getInstance().lookup("ObsView")).getData(nObstype, oObsRequest.getRequestTimestampStart(), oObsRequest.getRequestTimestampEnd(), oPlatformSearchBounds.getSouth(), oPlatformSearchBounds.getNorth(), oPlatformSearchBounds.getWest(), oPlatformSearchBounds.getEast(), oObsRequest.getRequestTimestampRef());
				for (Obs oNewObs : oData)
				{
					if (oNewObs.m_yGeoType != Obs.POINT)
						continue;
					OsmWay oSnap = m_oRoads.getWay(nRoadBoundaryPadding, oNewObs.m_oGeoArray[1], oNewObs.m_oGeoArray[2]);
					if (oSnap == null || oSnap.m_oId.compareTo(oRequestRoad.m_oId) != 0)
						continue;
					ObsInfo oInfo = new ObsInfo(nObstype, oNewObs.m_nContribId);
					Obs oCurrentObs = oObsMap.get(oInfo);
					if (oCurrentObs == null || oNewObs.m_lObsTime1 >= oCurrentObs.m_lObsTime1) // keep the most recent observation if multiple match the query
						oObsMap.put(oInfo, oNewObs);
				}
				oObsList.addAll(oObsMap.values());
			}

			Collections.sort(oObsList, g_oObsDetailComp);

			for (Obs oObs : oObsList) // add each obs to the JSON stream
				serializeObsRecord(oOutputGenerator, oNumberFormatter, oObs);
		}
		

		if (oSession != null)
			forwardRequest(oOutputGenerator, oObsRequest);
		
		oOutputGenerator.writeEndArray();

		if (nElevation != Integer.MIN_VALUE) // if there is a valid elevation, write it
		{
			DecimalFormat oElevationFormatter = new DecimalFormat("#,###");

			oOutputGenerator.writeStringField("tel", oElevationFormatter.format(nElevation));
		}

		oOutputGenerator.writeStringField("sdet", oRequestRoad.m_sName); // write the name of the roadway segment
		oOutputGenerator.writeStringField("imrcpid", oRequestRoad.m_oId.toString()); // write the IMRCP Id of the roadway segment

		oOutputGenerator.writeEndObject();
	}

	
	/**
	 * Add the response to the given JSON stream for requests made from the IMRCP
	 * Map UI to create a chart for road observations	
	 */
	@Override
	protected void buildObsChartResponseContent(JsonGenerator oOutputGenerator, ObsChartRequest oObsRequest) throws Exception
	{
		Mercator oM = new Mercator((int)Math.pow(2, 16) - 1);
		int nRoadBoundaryPadding = (int)Math.round(oM.RES[9] * 100);
		LatLngBounds currentRequestBounds = oObsRequest.getRequestBounds();
		int nContribId = oObsRequest.getSourceId();
		int nMidLon = (currentRequestBounds.getEast() + currentRequestBounds.getWest()) / 2;
		int nMidLat = (currentRequestBounds.getNorth() + currentRequestBounds.getSouth()) / 2;
		OsmWay oRequestRoad = m_oRoads.getWay(nRoadBoundaryPadding, nMidLon, nMidLat); // find the closest roadway segment to the requested lon/lat

		if (oRequestRoad == null) // failed to snap to a road
			return;

		LatLngBounds oPlatformSearchBounds = new LatLngBounds(oRequestRoad.m_nMaxLat + nRoadBoundaryPadding, oRequestRoad.m_nMaxLon + nRoadBoundaryPadding, oRequestRoad.m_nMinLat - nRoadBoundaryPadding, oRequestRoad.m_nMinLon - nRoadBoundaryPadding);

		ArrayList<Obs> oObsList = new ArrayList<>();
		ObsList oData = ((TileObsView)Directory.getInstance().lookup("ObsView")).getData(oObsRequest.getObstypeId(), oObsRequest.getRequestTimestampStart(), oObsRequest.getRequestTimestampEnd(), oPlatformSearchBounds.getSouth(), oPlatformSearchBounds.getNorth(), oPlatformSearchBounds.getWest(), oPlatformSearchBounds.getEast(), oObsRequest.getRequestTimestampRef(), oRequestRoad.m_oId);
		{
			for (Obs oNewObs : oData)
			{
				if (oNewObs.m_nContribId == nContribId)
					oObsList.add(oNewObs);
			}
		}

		Collections.sort(oObsList, Obs.g_oCompObsByTime);

		Units oUnits = Units.getInstance();
		oOutputGenerator.writeStartArray(); // write response which is an array of JSON objects
		for(Obs oObs : oObsList)
		{
			String sToEnglish = ObsType.getUnits(oObs.m_nObsTypeId, false);
			String sFromUnits = ObsType.getUnits(oObs.m_nObsTypeId, true);
			oOutputGenerator.writeStartObject();
			oOutputGenerator.writeNumberField("t", oObs.m_lObsTime1); // time axis value
			oOutputGenerator.writeNumberField("y", oUnits.convert(sFromUnits, sToEnglish, oObs.m_dValue)); // y axis value
			oOutputGenerator.writeEndObject();
		}
		oOutputGenerator.writeEndArray();
	}
}
