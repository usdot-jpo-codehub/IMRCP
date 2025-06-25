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

import imrcp.geosrv.GeoUtil;
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
 * Handles requests from the IMRCP Map UI when Area layer objects are clicked
 * or when a chart for an areal observations is requested
 * @author aaron.cherney
 */
public class AreaLayerServlet extends LayerServlet
{
	/**
	 * Configurable array that contains the observation type ids that are valid
	 * for area observations
	 */
	private int[] AREA_OBSTYPES;

	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		String[] sObsTypes = JSONUtil.getStringArray(oBlockConfig, "areaobs");
		AREA_OBSTYPES = imrcp.system.Arrays.newIntArray(sObsTypes.length);
		for (int nIndex = 0; nIndex < sObsTypes.length; nIndex++)
		{
			AREA_OBSTYPES = Arrays.add(AREA_OBSTYPES, Integer.valueOf(sObsTypes[nIndex], 36));
		}
	}


	/**
	 * Add the response to the given JSON stream for requests made from the IMRCP
	 * Map UI when an Area Layer object is clicked.
	 */
	@Override
	protected void buildObsResponseContent(JsonGenerator oOutputGenerator, ObsRequest oObsRequest, Session oSession, ClientConfig oClient) throws Exception
	{
		int[] nObsToQuery = Arrays.newIntArray();
		if (oClient != null)
		{
			for (int nObsIndex = 0; nObsIndex < oClient.m_nObsTypes.length; nObsIndex++)
			{
				int nClientObs = oClient.m_nObsTypes[nObsIndex];
				for (int nInner = 0; nInner < AREA_OBSTYPES.length; nInner++)
				{
					int nAreaObs = AREA_OBSTYPES[nInner];
					if (nClientObs == nAreaObs)
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
			nObsToQuery = AREA_OBSTYPES;
		}
			
		LatLngBounds oRequestBounds = oObsRequest.getRequestBounds();
		ArrayList<Obs> oObsList = new ArrayList<>();
		StringBuilder sDetail = new StringBuilder();
		boolean bAddCapDetail = false;
		Iterator<int[]> oIt = Arrays.iterator(nObsToQuery, new int[1], 1, 1);
		TileObsView oOV = (TileObsView)Directory.getInstance().lookup("ObsView");
		while(oIt.hasNext()) // for each observation type, use ObsView to query the data stores
		{
			int nObstype = oIt.next()[0];
			TreeMap<ObsInfo, Obs> oObsMap = new TreeMap(ObsInfo.g_oCOMP);
			ObsList oData = oOV.getData(nObstype, oObsRequest.getRequestTimestampStart(), oObsRequest.getRequestTimestampEnd(), oRequestBounds.getSouth(), oRequestBounds.getNorth(), oRequestBounds.getWest(), oRequestBounds.getEast(), oObsRequest.getRequestTimestampRef());
			oObsList.addAll(oData);
//			for (Obs oNewObs : oData)
//			{
//				ObsInfo oInfo = new ObsInfo(nObstype, oNewObs.m_nContribId); // store observations by observation type and contributor id
//				Obs oCurrentObs = oObsMap.get(oInfo);
//				if (oCurrentObs == null || (oNewObs.m_lTimeRecv > oCurrentObs.m_lTimeRecv)) // use the most recent observation
//					oObsMap.put(oInfo, oNewObs);
//			}
//			oObsList.addAll(oObsMap.values());
		}

		Collections.sort(oObsList, g_oObsDetailComp);

		oOutputGenerator.writeStartObject(); // response is an object

		int nElevation = Integer.MIN_VALUE;

		DecimalFormat oNumberFormatter = new DecimalFormat("0.##");

		oOutputGenerator.writeArrayFieldStart("obs"); // with a field call obs which is an array of objects, each object is a serialized Obs

		for (Obs oObs : oObsList)
		{
			serializeObsRecord(oOutputGenerator, oNumberFormatter, oObs);
		}
		
		if (oSession != null)
			forwardRequest(oOutputGenerator, oObsRequest);
		
		oOutputGenerator.writeEndArray();

		if (nElevation != Short.MIN_VALUE && nElevation != Integer.MIN_VALUE) // write elevation field if the Obs has a valid elevation
		{
			DecimalFormat oElevationFormatter = new DecimalFormat("#,###");

			oOutputGenerator.writeStringField("tel", oElevationFormatter.format(nElevation));
		}

		if (bAddCapDetail && sDetail.length() != 0) // add detail for CAP alerts
			oOutputGenerator.writeStringField("sdet", formatDetailString(sDetail.toString(), "<br/>", 90));
		oOutputGenerator.writeStringField("lat", Double.toString(GeoUtil.fromIntDeg(oRequestBounds.getNorth()))); // write lat
		oOutputGenerator.writeStringField("lon", Double.toString(GeoUtil.fromIntDeg(oRequestBounds.getEast()))); // write lon

		oOutputGenerator.writeEndObject(); // close the object
	}
	
	/**
	 * Add the response to the given JSON stream for requests made from the IMRCP
	 * Map UI to create a chart for areal observations	
	 */
	@Override
	protected void buildObsChartResponseContent(JsonGenerator oOutputGenerator, ObsChartRequest oObsRequest) throws Exception
	{
		LatLngBounds oRequestBounds = oObsRequest.getRequestBounds();
		int nContribId = oObsRequest.getSourceId();
		ArrayList<Obs> oObsList = new ArrayList<>();

		// query ObsView for Obs that match the query
		ObsList oData = ((TileObsView)Directory.getInstance().lookup("ObsView")).getData(oObsRequest.getObstypeId(), oObsRequest.getRequestTimestampStart(), oObsRequest.getRequestTimestampEnd(), oRequestBounds.getSouth(), oRequestBounds.getNorth(), oRequestBounds.getWest(), oRequestBounds.getEast(), oObsRequest.getRequestTimestampRef());
		for (Obs oNewObs : oData)
		{
			if (oNewObs.m_nContribId != nContribId || oNewObs.m_nContribId == Integer.valueOf("cap", 36)) // ignore other contributor ids and cap since it doesn't really make sense to graph CAP alert values
				continue;
			
			oObsList.add(oNewObs);
		}

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
