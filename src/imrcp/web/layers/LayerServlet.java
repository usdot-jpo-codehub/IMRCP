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
import imrcp.system.Directory;
import imrcp.system.ObsType;
import imrcp.system.Units;
import imrcp.web.ClientConfig;
import imrcp.web.LatLngBounds;
import imrcp.web.ObsChartRequest;
import imrcp.web.ObsRequest;
import imrcp.web.SecureBaseBlock;
import static imrcp.web.SecureBaseBlock.getTrustingConnection;
import imrcp.web.ServerConfig;
import imrcp.web.Session;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.regex.Pattern;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import javax.net.ssl.HttpsURLConnection;
import org.apache.http.HttpStatus;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Base class used for handling different requests based on the IMRCP Map UI's 
 * layers which include points (sensors, events, alerts), lines (roads), 
 * and polygons (areal weather)
 * @author aaron.cherney
 */
public abstract class LayerServlet extends SecureBaseBlock
{
	/**
	 * Regex Pattern used to verify the validity of obs requests
	 */
	private final Pattern m_oObsRequestPattern = Pattern.compile("^(?:[a-zA-Z0-9_-]*/){1,}platformObs(?:/[0-9]{11,14}){2}(?:/-?[0-9]{1,3}\\.?[0-9]*){4,4}$");

	
	/**
	 * Regex Pattern used to verify the validity of chart requests
	 */
	private final Pattern m_oObsChartRequestPattern = Pattern.compile("^(?:[a-zA-Z0-9_-]*/){1,}chartObs(?:/-?[0-9]{1,10}){1}(?:/[0-9]{11,14}){3}(?:/-?[0-9]{1,3}\\.?[0-9]*){4,4}$");

	
	/**
	 * Factory object used to create {@link JsonGenerator} objects
	 */
	private final JsonFactory m_oJsonFactory = new JsonFactory();

	
	/**
	 * Compares Obs by observation type id, then contributor id, then start time,
	 * then end time.
	 */
	protected static Comparator<Obs> g_oObsDetailComp = (Obs o1, Obs o2) ->
	{
		int nReturn = ObsType.getDescription(o1.m_nObsTypeId).compareTo(ObsType.getDescription(o2.m_nObsTypeId));
		if (nReturn == 0)
		{
			nReturn = o1.m_nContribId - o2.m_nContribId;
			if (nReturn == 0)
			{
				nReturn = Long.compare(o1.m_lObsTime1, o2.m_lObsTime1);
				if (nReturn == 0)
					nReturn = Long.compare(o1.m_lObsTime2, o2.m_lObsTime2);
			}
		}
		return nReturn;
	};

	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
	}

	
	/**
	 * Handles finding observation that match the HTTP request.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @param oSess object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws IOException
	 * @throws ServletException
	 */
	public int doPlatformObs(HttpServletRequest oReq, HttpServletResponse oRes, Session oSess, ClientConfig oClient)
	   throws IOException, ServletException
	{
		try
		{
			oRes.setContentType("application/json");
			String sRequestUri = oReq.getRequestURI();
			// Pattern.compile("^(?:[a-zA-Z0-9_-]*/){1,}platformObs/[0-9]*/[0-9]{1,30}$").matcher(requestUrl).matches()
			if (!m_oObsRequestPattern.matcher(sRequestUri).find()) // ignore bad requests
			{
				return HttpStatus.SC_BAD_REQUEST;
			}

			String[] sUriParts = oReq.getRequestURI().split("/");

			if (sUriParts.length < 6) // requests must have less than 6 parts
			{
				return HttpStatus.SC_BAD_REQUEST;
			}

			// get all of the query parameters from the request URL
			long lRequestTimeRef = Long.parseLong(sUriParts[sUriParts.length - 6]);
			long lRequestTimeStart = Long.parseLong(sUriParts[sUriParts.length - 5]);
			double dLat1 = Double.parseDouble(sUriParts[sUriParts.length - 4]);
			double dLng1 = Double.parseDouble(sUriParts[sUriParts.length - 3]);
			double dLat2 = Double.parseDouble(sUriParts[sUriParts.length - 2]);
			double dLng2 = Double.parseDouble(sUriParts[sUriParts.length - 1]);
			ObsRequest oObsRequest = new ObsRequest();
			oObsRequest.setRequestBounds(new LatLngBounds(dLat1, dLng1, dLat2, dLng2));
			oObsRequest.setRequestTimestampRef(lRequestTimeRef);
			oObsRequest.setRequestTimestampStart(lRequestTimeStart);
			oObsRequest.setRequestTimestampEnd(lRequestTimeStart + 60000);
			oObsRequest.setRequest(sRequestUri);
			String sSourceId = oReq.getParameter("src");
			oObsRequest.setSourceId(sSourceId == null ? Integer.valueOf("null", 36) : Integer.valueOf(sSourceId, 36));

			processObsRequest(oRes, oObsRequest, oSess, oClient); // process the request
			return HttpServletResponse.SC_OK; // OK if no Exception are thrown
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
			return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
	}

	
	/**
	 * Handles finding observation that match the HTTP request to generate a
	 * chart.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @param oSession object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws ServletException
	 * @throws IOException
	 */
	public int doChartObs(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oClient)
	   throws ServletException, IOException
	{
		String sRequestUri = oReq.getRequestURI();
		try
		{
			// Pattern.compile("^(?:[a-zA-Z0-9_-]*/){1,}platformObs/[0-9]*/[0-9]{1,30}$").matcher(requestUrl).matches()
			if (!m_oObsChartRequestPattern.matcher(sRequestUri).find()) // ignore bad requests
			{
				return HttpStatus.SC_BAD_REQUEST;
			}

			String[] sUriParts = oReq.getRequestURI().split("/");

			if (sUriParts.length < 8) // requests must have less than 8 parts
			{
				return HttpStatus.SC_BAD_REQUEST;
			}

			// set all of the query parameters from the URL request
			long lRequestTimeRef = Long.parseLong(sUriParts[sUriParts.length - 7]);
			long lRequestTimeStart = Long.parseLong(sUriParts[sUriParts.length - 6]);
			long lRequestTimeEnd = Long.parseLong(sUriParts[sUriParts.length - 5]);
			double dLat1 = Double.parseDouble(sUriParts[sUriParts.length - 4]);
			double dLng1 = Double.parseDouble(sUriParts[sUriParts.length - 3]);
			double dLat2 = Double.parseDouble(sUriParts[sUriParts.length - 2]);
			double dLng2 = Double.parseDouble(sUriParts[sUriParts.length - 1]);
			int nObstypeId = Integer.parseInt(sUriParts[sUriParts.length - 8]);
			ObsChartRequest oObsRequest = new ObsChartRequest();
			oObsRequest.setRequestBounds(new LatLngBounds(dLat1, dLng1, dLat2, dLng2));
			oObsRequest.setRequestTimestampRef(lRequestTimeRef);
			oObsRequest.setRequestTimestampStart(lRequestTimeStart);
			oObsRequest.setRequestTimestampEnd(lRequestTimeEnd);
			oObsRequest.setObstypeId(nObstypeId);
			String sSourceId = oReq.getParameter("src");
			oObsRequest.setSourceId(sSourceId == null ? Integer.valueOf("null", 36) : Integer.valueOf(sSourceId, 36));

			processChartRequest(oRes, oObsRequest); // process the request
			oRes.setContentType("application/json");
			return HttpServletResponse.SC_OK;
		}
		catch (Exception oEx)
		{
			return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
	}

	
	/**
	 * Creates a {@link JsonGenerator} to pass into {@link #buildObsResponseContent(org.codehaus.jackson.JsonGenerator, imrcp.web.ObsRequest)}
	 * 
	 * @param oResp object that contains the response the servlet sends to the client
	 * @param oObsRequest object that contains the query parameters for the request
	 * @throws Exception
	 */
	protected void processObsRequest(HttpServletResponse oResp, ObsRequest oObsRequest, Session oSession, ClientConfig oClient) throws Exception
	{
		try (JsonGenerator oOutputGenerator = m_oJsonFactory.createJsonGenerator(oResp.getOutputStream(), JsonEncoding.UTF8))
		{
			buildObsResponseContent(oOutputGenerator, oObsRequest, oSession, oClient);
		}
	}

	
	/**
	 * Creates a {@link JsonGenerator} to pass into {@link #buildObsChartResponseContent(org.codehaus.jackson.JsonGenerator, imrcp.web.ObsChartRequest)}
	 * 
	 * @param oResp object that contains the response the servlet sends to the client
	 * @param oObsRequest object that contains the query parameters for the request
	 * @throws Exception
	 */
	protected void processChartRequest(HttpServletResponse oResp, ObsChartRequest oObsRequest) throws Exception
	{

		try (JsonGenerator oOutputGenerator = m_oJsonFactory.createJsonGenerator(oResp.getOutputStream(), JsonEncoding.UTF8))
		{
			buildObsChartResponseContent(oOutputGenerator, oObsRequest);
		}
	}

	
	/**
	 * Formats the Obs as a JSON object in the given JsonGenerator.
	 * 
	 * @param oOutputGenerator JSON stream
	 * @param oNumberFormatter Formatting object for converting numbers to Strings
	 * @param oObs the Obs to serialize into JSON object
	 * @throws IOException
	 */
	protected void serializeObsRecord(JsonGenerator oOutputGenerator, DecimalFormat oNumberFormatter, Obs oObs) throws IOException
	{
		oOutputGenerator.writeStartObject();

		if (ObsType.hasLookup(oObs.m_nObsTypeId)) // enumerated values
		{
			String sLookup = ObsType.lookup(oObs.m_nObsTypeId, (int)oObs.m_dValue);
			if (sLookup == null)
			{
				m_oLogger.error("Lookup failed for " + ObsType.getName(oObs.m_nObsTypeId) + "," + oObs.m_dValue);
				sLookup = "Unknown";
			}
			oOutputGenerator.writeStringField("mv", sLookup);
			oOutputGenerator.writeStringField("ev", sLookup);
			oOutputGenerator.writeStringField("mu", "");
			oOutputGenerator.writeStringField("eu", "");
		}
		else
		{
			String sMetricUnits = ObsType.getUnits(oObs.m_nObsTypeId, true);
			String sEnglishUnits = ObsType.getUnits(oObs.m_nObsTypeId, false);
			

			double dEnglishVal = Units.getInstance().convert(sMetricUnits, sEnglishUnits, oObs.m_dValue); // convert to english units
			double dMetricVal = oObs.m_dValue;

			oOutputGenerator.writeStringField("mv", oNumberFormatter.format(dMetricVal));
			oOutputGenerator.writeStringField("ev", oNumberFormatter.format(dEnglishVal));
			oOutputGenerator.writeStringField("mu", sMetricUnits);
			oOutputGenerator.writeStringField("eu", sEnglishUnits);
		}

		oOutputGenerator.writeNumberField("oi", oObs.m_nObsTypeId);
		oOutputGenerator.writeStringField("od", ObsType.getDescription(oObs.m_nObsTypeId));
		oOutputGenerator.writeNumberField("ts1", oObs.m_lObsTime1);
		oOutputGenerator.writeNumberField("ts2", oObs.m_lObsTime2);
		oOutputGenerator.writeStringField("src", Integer.toString(oObs.m_nContribId, 36).toUpperCase());
		if (oObs.m_nContribId == Integer.valueOf("cap", 36))
			oOutputGenerator.writeStringField("url", oObs.m_sStrings[1]);
		oOutputGenerator.writeNumberField("pref", Directory.getResource(oObs.m_nContribId, oObs.m_nObsTypeId).getPreference());
		oOutputGenerator.writeEndObject();
	}

	
	/**
	 * Child classes implement this method to create the response when an object
	 * in its layer is clicked on the map.
	 * 
	 * @param oOutputGenerator JSON stream
	 * @param oObsRequest object that contains the query parameters for the request
	 * @throws Exception
	 */
	protected abstract void buildObsResponseContent(JsonGenerator oOutputGenerator, ObsRequest oObsRequest, Session oSession, ClientConfig oClient) throws Exception;

	
	/**
	 * Child classes implement this method to create the response when a chart is
	 * requested from the map.
	 * @param oOutputGenerator JSON stream
	 * @param oObsRequest object that contains the query parameters for the request
	 * @throws Exception
	 */
	protected void buildObsChartResponseContent(JsonGenerator oOutputGenerator, ObsChartRequest oObsRequest) throws Exception
	{
	}

	
	/**
	 * Used to format the detail string of a response to produce a better
	 * presentation of it in the observation table on the IMRCP Map UI
	 * 
	 * @param sDetail String to format
	 * @param sInsert String to insert approximately every nStep characters, usually
	 * "{@literal <br>}" 
	 * @param nStep Number of characters to skip before inserting sInsert into
	 * sDetail
	 * @return sDetail with sInsert added approximately every nStep characters
	 */
	protected String formatDetailString(String sDetail, String sInsert, int nStep)
	{
		StringBuilder sReturn = new StringBuilder();
		int nPos = 0;
		char cChar = ' ';
		int nLength = sDetail.length();
		int nLimit = nStep;

		while (nPos < nLength)
		{
			while (nPos < nLength && nPos < nLimit)
				sReturn.append(sDetail.charAt(nPos++));
			while (nPos < nLength && (cChar = sDetail.charAt(nPos++)) != ' ')
				sReturn.append(cChar);
			sReturn.append(sInsert);
			nLimit = nPos + nStep;
		}

		return sReturn.toString();
	}
	
	
	protected void forwardRequest(JsonGenerator oOutputGenerator, ObsRequest oObsRequest)
	{
		LatLngBounds oBounds = oObsRequest.getRequestBounds();
		ServerConfig oPrev = new ServerConfig("", null, 0);
		for (int nIndex = 0; nIndex < SERVERS.size(); nIndex++)
		{
			ServerConfig oConfig = SERVERS.get(nIndex);
			if (oConfig.m_sUUID.compareTo(oPrev.m_sUUID) != 0)
			{
				ArrayList<int[]> oGeos = oConfig.m_oNetworkGeometries;
				for (int nGeoIndex = 0; nGeoIndex < oGeos.size(); nGeoIndex++)
				{
					if (GeoUtil.isInsideRingAndHoles(oGeos.get(nGeoIndex), Obs.POLYGON, GeoUtil.getBoundingPolygon(oBounds.getWest(), oBounds.getSouth(), oBounds.getEast(), oBounds.getNorth())))
					{
						try
						{
							HttpsURLConnection oConn = getTrustingConnection(String.format("https://%s%s?uuid=%s", oConfig.m_oHost.getHostName(), oObsRequest.getRequest(), URLEncoder.encode(oConfig.m_sUUID, StandardCharsets.UTF_8)));
							oConn.setConnectTimeout(500);
							oConn.setReadTimeout(3000);
							try (BufferedReader oIn = new BufferedReader(new InputStreamReader(oConn.getInputStream(), StandardCharsets.UTF_8)))
							{
								if (oConn.getResponseCode() != HttpServletResponse.SC_OK || oConn.getContentLengthLong() == 0)
									break;
								
								JSONObject oRes = new JSONObject(new JSONTokener(oIn));
								JSONArray oObsList = oRes.getJSONArray("obs");
								for (int nObsIndex = 0; nObsIndex < oObsList.length(); nObsIndex++)
								{
									JSONObject oObs = oObsList.getJSONObject(nObsIndex);
									oOutputGenerator.writeStartObject();
									oOutputGenerator.writeStringField("mv", oObs.getString("mv"));
									oOutputGenerator.writeStringField("ev", oObs.getString("ev"));
									oOutputGenerator.writeStringField("mu", oObs.getString("mu"));
									oOutputGenerator.writeStringField("eu", oObs.getString("eu"));
									oOutputGenerator.writeNumberField("oi", oObs.getInt("oi"));
									oOutputGenerator.writeStringField("od", oObs.getString("od"));
									oOutputGenerator.writeNumberField("ts1", oObs.getLong("ts1"));
									oOutputGenerator.writeNumberField("ts2", oObs.getLong("ts2"));
									oOutputGenerator.writeStringField("src", oObs.getString("src"));
									if (oObs.has("url"))
										oOutputGenerator.writeStringField("url", oObs.getString("url"));
									oOutputGenerator.writeEndObject();
								}
							}
						}
						catch (Exception oEx)
						{
							m_oLogger.error(oEx, oEx);
						}
					}
				}
			}
			oPrev = oConfig;
		}
	}
}
