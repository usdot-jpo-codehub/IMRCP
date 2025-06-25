package imrcp.web.layers;

import imrcp.store.Obs;
import imrcp.store.ObsList;
import imrcp.store.TileObsView;
import imrcp.system.Directory;
import imrcp.system.Id;
import imrcp.system.Introsort;
import imrcp.system.ObsType;
import imrcp.system.Units;
import imrcp.web.ClientConfig;
import imrcp.web.LatLngBounds;
import imrcp.web.ObsChartRequest;
import imrcp.web.ObsRequest;
import imrcp.web.Session;
import java.text.DecimalFormat;
import java.util.Collections;
import org.codehaus.jackson.JsonGenerator;

/**
 * Handles requests from the IMRCP Map UI when Points(sensors, CVs, alerts) layer objects are clicked
 * or when a chart for Point observations is requested
 * @author aaron.cherney
 */
public class PointsLayerServlet extends LayerServlet
{	
	/**
	 * Add the response to the given JSON stream for requests made from the IMRCP
	 * Map UI when a Points Layer object is clicked.
	 */
	@Override
	protected void buildObsResponseContent(JsonGenerator oOutputGenerator, ObsRequest oObsRequest, Session oSession, ClientConfig oClient) throws Exception
	{
		oOutputGenerator.writeStartObject();
		LatLngBounds currentRequestBounds = oObsRequest.getRequestBounds();
		int nAlertBoundaryPadding = 0;
		LatLngBounds oSearchBounds = new LatLngBounds(currentRequestBounds.getNorth() + nAlertBoundaryPadding, currentRequestBounds.getEast() + nAlertBoundaryPadding, currentRequestBounds.getSouth() - nAlertBoundaryPadding, currentRequestBounds.getWest() - nAlertBoundaryPadding);
		DecimalFormat oNumberFormatter = new DecimalFormat("0.##");
		oOutputGenerator.writeArrayFieldStart("obs");
		StringBuilder sDetail = new StringBuilder();
		// query ObsView for stores configured to provide All observation types
		ObsList oData = ((TileObsView)Directory.getInstance().lookup("ObsView")).getData(ObsType.VARIES, oObsRequest.getRequestTimestampStart(), oObsRequest.getRequestTimestampEnd(), oSearchBounds.getSouth(), oSearchBounds.getNorth(), oSearchBounds.getWest(), oSearchBounds.getEast(), oObsRequest.getRequestTimestampRef());
		int nIndex = oData.size();
		while (nIndex-- > 0)
		{
			Obs oObs = oData.get(nIndex);
			if (oObs.m_yGeoType != Obs.POINT && oObs.m_yGeoType != Obs.MULTIPOINT)
				oData.remove(nIndex);
		}
		Introsort.usort(oData, Obs.g_oCompObsByObjTypeRecv);
		nIndex = oData.size();
		while (nIndex-- > 1)
		{
			Obs o1 = oData.get(nIndex);
			Obs o2 = oData.get(nIndex - 1);
			if (Id.COMPARATOR.compare(o1.m_oObjId, o2.m_oObjId) == 0 && o1.m_nObsTypeId == o2.m_nObsTypeId)
				oData.remove(nIndex - 1);
		}
		for (Obs oObs : oData)
		{
			serializeObsRecord(oOutputGenerator, oNumberFormatter, oObs);
			String sPresString = oObs.getPresentationString();
			if (sPresString.length() > 0 && sDetail.indexOf(sPresString) < 0)
				sDetail.append(sPresString).append("<br>");
		}
		
		if (sDetail.length() > "<br>".length())
			sDetail.setLength(sDetail.length() - "<br>".length());
		if (oSession != null)
			forwardRequest(oOutputGenerator, oObsRequest);
		oOutputGenerator.writeEndArray();

		oOutputGenerator.writeStringField("sdet", sDetail.toString());

		oOutputGenerator.writeEndObject();
	}
	
	
	/**
	 * Add the response to the given JSON stream for requests made from the IMRCP
	 * Map UI to create a chart for points observations
	 */
	@Override
	protected void buildObsChartResponseContent(JsonGenerator oOutputGenerator, ObsChartRequest oObsRequest) throws Exception
	{
		LatLngBounds currentRequestBounds = oObsRequest.getRequestBounds();
		int nAlertBoundaryPadding = 5000;
		LatLngBounds oSearchBounds = new LatLngBounds(currentRequestBounds.getNorth() + nAlertBoundaryPadding, currentRequestBounds.getEast() + nAlertBoundaryPadding, currentRequestBounds.getSouth() - nAlertBoundaryPadding, currentRequestBounds.getWest() - nAlertBoundaryPadding);
		
		oOutputGenerator.writeStartArray(); // write response which is an array of JSON objects

		Units oUnits = Units.getInstance();
		int nContribId = oObsRequest.getSourceId();
		ObsList oData = ((TileObsView)Directory.getInstance().lookup("ObsView")).getData(oObsRequest.getObstypeId(), oObsRequest.getRequestTimestampStart(), oObsRequest.getRequestTimestampEnd(), oSearchBounds.getSouth(), oSearchBounds.getNorth(), oSearchBounds.getWest(), oSearchBounds.getEast(), oObsRequest.getRequestTimestampRef());
		Collections.sort(oData, Obs.g_oCompObsByTime);
		for (Obs oObs : oData)
		{
			if (nContribId != oObs.m_nContribId) // ignore other contributor ids
				continue;

			String sToEnglish = ObsType.getUnits(oObs.m_nObsTypeId, false);
			String sFromUnits = ObsType.getUnits(oObs.m_nObsTypeId, true);
			oOutputGenerator.writeStartObject();
			oOutputGenerator.writeNumberField("t", oObs.m_lObsTime1); // time axis value
			oOutputGenerator.writeNumberField("y", oUnits.convert(sFromUnits, sToEnglish, oObs.m_dValue)); // y axis value
			oOutputGenerator.writeEndObject();
		}
	}
}
