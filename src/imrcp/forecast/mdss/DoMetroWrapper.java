package imrcp.forecast.mdss;

import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.osm.OsmWay;
import imrcp.store.Obs;
import imrcp.store.ObsList;
import imrcp.system.Directory;
import imrcp.system.Introsort;
import imrcp.system.ObsType;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

/**
 * This class contains methods to create input arrays for METRo and save the
 * returned output arrays. It also contains the wrapper function for the C code
 * to run METRo directly. By doing this we bypass using METRo’s Python code,
 * which improves the performance greatly.
 * @author aaron.cherney
 */
public class DoMetroWrapper implements Runnable
{
	/**
	 * Flag to tell if the Metro shared library was loaded correctly
	 */
	public static boolean g_bLibraryLoaded = true;

	
	/**
	 * Minimum road/surface temperature allowed in C
	 */
	private static final double m_dROADTEMPMIN;

	
	/**
	 * Maximum road/surface temperature allowed in C
	 */
	private static final double m_dROADTEMPMAX;

	
	/**
	 * Minimum sub surface temperature allowed in C
	 */
	private static final double m_dSUBSURTEMPMIN;

	
	/**
	 * Maximum sub surface temperature allowed in C
	 */
	private static final double m_dSUBSURTEMPMAX;

	
	/**
	 * Minimum air temperature allowed in C
	 */
	private static final double m_dAIRTEMPMIN;

	
	/**
	 * Maximum air temperature allowed in C
	 */
	private static final double m_dAIRTEMPMAX;

	
	/**
	 * Maximum wind speed allowed in m/s
	 */
	private static final double m_dWINDSPEEDMAX;

	
	/**
	 * Minimum pressure allowed in Pa
	 */
	private static final double m_dPRESSUREMIN;

	
	/**
	 * Maximum pressure allowed in Pa
	 */
	private static final double m_dPRESSUREMAX;

	
	/**
	 * Value pressure is defaulted to if it is outside of he min and max
	 */
	private static final double m_dPRESSUREDEFAULT;

	
	/**
	 * Value that gets passed to the METRo code to be used as the cutoff value
	 * for rain when calculating pavement states.
	 */
	private static final double m_dRAINCUTOFF;

	
	/**
	 * Value that gets passed to the METRo code to be used as the cutoff value
	 * for snow when calculating pavement states
	 */
	private static final double m_dSNOWCUTOFF;

	
	/**
	 * 1 if the segment is a bridge, 0 if it is not
	 */
	private int m_bBridge;

	
	/**
	 * Latitude of the segment in decimal degree
	 */
	private double m_dLat;

	
	/**
	 * Longitude of the segment in decimal degrees
	 */
	private double m_dLon;

	
	/**
	 * Array used to store the road condition outputs from METRo 1 = dry road, 2
	 * = wet road, 3 = ice/snow on the road, 4 = mix water/snow on the road, 5 =
	 * dew, 6 = melting snow, 7 = frost, 8 = icing rain
	 */
	private final long[] m_lOutRoadCond;

	
	/**
	 * Array used to store the road temperature outputs from METRo in C
	 */
	private final double[] m_dOutRoadTemp;

	
	/**
	 * Array used to store the sub surface temperature outputs from METRo in C
	 */
	private final double[] m_dOutSubSurfTemp;

	
	/**
	 * Array used to store the snow and ice accumulation outputs from METRo in
	 * mm
	 */
	private final double[] m_dOutSnowIceAcc;

	
	/**
	 * Array used to store the liquid accumulation outputs from METRo in mm
	 */
	private final double[] m_dOutLiquidAcc;
	
	
	/**
	 * Array that contains the hour of day (using 24 hour clock) of each
	 * forecast
	 */
	private double[] m_dFTime;

	
	/**
	 * Array that contains the Unix time in seconds of each forecast
	 */
	private double[] m_dFTimeSeconds;

	
	/**
	 * Array that contains the forecasted dew point values in C
	 */
	private final double[] m_dDewPoint;

	
	/**
	 * Array that contains the forecasted cloud cover values (values are "octal"
	 * being 0 to 8, where 0 = 0% and 8 = 100% coverage)
	 */
	private final double[] m_dCloudCover;

	
	/**
	 * Array that contains the forecasted air temperature values in C
	 */
	private final double[] m_dAirTemp;

	
	/**
	 * Array that contains the forecasted precipitation amount values in m/s
	 */
	private final double[] m_dPrecipAmt;

	
	/**
	 * Array that contains the forecasted wind speed values in m/s
	 */
	private final double[] m_dWindSpeed;

	
	/**
	 * Array that contains the forecasted surface pressure values in Pa
	 */
	private final double[] m_dSfcPres;

	
	/**
	 * Array that contains the forecasted precipitation type values 0 = none 1 =
	 * rain 2 = snow
	 */
	private final long[] m_lPrecipType;

	
	/**
	 * Array that contains the forecasted road condition. Uses METRo values: 1 =
	 * dry road, 2 = wet road, 3 = ice/snow on the road, 4 = mix water/snow on
	 * the road, 5 = dew, 6 = melting snow, 7 = frost, 8 = icing rain
	 */
	private final long[] m_lRoadCond;

	
	/**
	 * Array that contains the observed air temperature values in C
	 */
	private final double[] m_dObsAirTemp;

	
	/**
	 * Array that contains the observed road temperature values in C
	 */
	private final double[] m_dObsRoadTemp;

	
	/**
	 * Array that contains the observed sub surface temperature values in C
	 */
	private final double[] m_dObsSubSurfTemp;

	
	/**
	 * Array that contains the observed dew point values in C
	 */
	private final double[] m_dObsDewPoint;

	
	/**
	 * Array that contains the observed wind speed values in m/s
	 */
	private final double[] m_dObsWindSpeed;

	
	/**
	 * Array that contains the hour of day (uses 24 hour clock) of each
	 * observation
	 */
	private double[] m_dObsTime;

	
	/**
	 * The initial value of the rain reservoir to be passed to METRo (set using
	 * the previous METRo output's liquid accumulation
	 */
	private double m_dRainReservoir; // er1

	
	/**
	 * The initial value of the snow reservoir to be passed to METRo (set using
	 * the previous METRo output's snow/ice accumulation
	 */
	private double m_dSnowReservoir; // er2

	
	/**
	 * Treatment type used. 0 = not treated, 1 = treated
	 */
	private int m_nTmtType;

	
	/**
	 * Number of forecast hours
	 */
	private final int m_nForecastHrs;

	
	/**
	 * Number of observation hours
	 */
	private final int m_nObsHrs;
	
	
	/**
	 * The number of outputs each array will contain from METRo. The time in
	 * between each output is 30 seconds. It is calculated by taking the number 
	 * of forecast hours minus one and multiplying that by 120
	 */
	private final int m_nOutputs;

	
	/**
	 * Log4J Logger
	 */
	private static final Logger m_oLogger = LogManager.getLogger(DoMetroWrapper.class);
	
	
	/**
	 * Stores all of the outputs from a run of METRo
	 */
	public RoadcastData m_oOutput;
	

	
	/**
	 * Reads configurable values and attempts to load libDoMetroWrapper.so
	 */
	static
	{
		JSONObject oBlockConfig = Directory.getInstance().getConfig("imrcp.forecast.mdss.DoMetroWrapper", "DoMetroWrapper");
		m_dROADTEMPMIN = oBlockConfig.optInt("roadmin", -50);
		m_dROADTEMPMAX = oBlockConfig.optInt("roadmax", 80);
		m_dSUBSURTEMPMIN = oBlockConfig.optInt("ssmin", -40);
		m_dSUBSURTEMPMAX = oBlockConfig.optInt("ssmax", 80);
		m_dAIRTEMPMIN = oBlockConfig.optInt("airmin", -60);
		m_dAIRTEMPMAX = oBlockConfig.optInt("airmax", 50);
		m_dWINDSPEEDMAX = oBlockConfig.optInt("windmax", 90);
		m_dPRESSUREMIN = oBlockConfig.optInt("prmin", 60000);
		m_dPRESSUREDEFAULT = oBlockConfig.optInt("prdef", 101325);
		m_dPRESSUREMAX = oBlockConfig.optInt("prmax", 110000);
		m_dRAINCUTOFF = oBlockConfig.optDouble("raincut", 0.2);
		m_dSNOWCUTOFF = oBlockConfig.optDouble("snowcut", 0.2);
		
		try
		{
			System.loadLibrary("DoMetroWrapper");
		}
		catch (UnsatisfiedLinkError oULE)
		{
			g_bLibraryLoaded = false;
			m_oLogger.error(oULE, oULE);
		}
	}

	
	/**
	 * Allocates memory for all the arrays based off the number of observation
	 * and forecast hours
	 * @param nObsHrs number of observation hours
	 * @param nForecastHrs number of forecast hours
	 * @throws Exception 
	 */
	public DoMetroWrapper(int nObsHrs, int nForecastHrs) throws Exception
	{
		m_nOutputs = (nForecastHrs - 1) * 120;
		m_lOutRoadCond = new long[m_nOutputs];
		m_dOutRoadTemp = new double[m_nOutputs];
		m_dOutSubSurfTemp = new double[m_nOutputs];
		m_dOutSnowIceAcc = new double[m_nOutputs];
		m_dOutLiquidAcc = new double[m_nOutputs];
		m_dFTime = new double[nForecastHrs];
		m_dFTimeSeconds = new double[nForecastHrs];
		m_dDewPoint = new double[nForecastHrs];
		m_dAirTemp = new double[nForecastHrs];
		m_dPrecipAmt = new double[(nForecastHrs - 1) * 120]; // precip amount is interpoled in java instead of C like the other obstypes
		m_dWindSpeed = new double[nForecastHrs];
		m_dSfcPres = new double[nForecastHrs];
		m_lPrecipType = new long[m_dPrecipAmt.length]; // precip type is interpoled in java instead of C like the other obstypes
		m_lRoadCond = new long[nObsHrs];
		m_dObsAirTemp = new double[nObsHrs];
		m_dObsRoadTemp = new double[nObsHrs];
		m_dObsSubSurfTemp = new double[nObsHrs];
		m_dObsDewPoint = new double[nObsHrs];
		m_dObsWindSpeed = new double[nObsHrs];
		m_dCloudCover = new double[nForecastHrs];
		m_dObsTime = new double[nObsHrs];
		m_nForecastHrs = nForecastHrs;
		m_nObsHrs = nObsHrs;
	}

	
	/**
	 * This function uses JNI to call the C function doMetroWrapper which calls
	 * Do_Metro to run the METRo heat-balance model
	 *
	 * @param bBridge is the road a bridge? 0 = road, 1 = bridge
	 * @param dLat latitude in decimal degrees
	 * @param dLon longitude in decimal degrees
	 * @param nObservationHrs the number of observation hours
	 * @param nForecastHrs the number of forecast hours
	 * @param dObsRoadTemp array containing the observed road temperatures
	 * (Celsius)
	 * @param dObsSubSurfTemp array containing the observed sub surface
	 * temperatures (Celsius)
	 * @param dObsAirTemp array containing the observed air temperatures
	 * (Celsius)
	 * @param dObsDewPoint array containing the observed dew points (Celsius)
	 * @param dObsWindSpeed array containing the observed wind speeds (m/s)
	 * @param dObsTime array containing the Hour of Day of each observation
	 * @param dFTime	array containing the Hour of Day of each forecast
	 * @param dFTimeSeconds array containing the Unix Time in seconds of each
	 * forecast
	 * @param dAirTemp array containing the forecasted air temperatures
	 * (Celsius)
	 * @param dDewPoint array containing the forecasted dew points (Celsius)
	 * @param dWindSpeed	array containing the forecasted wind speeds (m/s)
	 * @param dSfcPres array containing the forecasted surface pressure (Pa)
	 * @param dPrecipAmt array containing the forecasted precipitation amounts
	 * (m/s)
	 * @param lPrecipType array containing the forecasted precipitation types (0
	 * = none, 1 = rain, 2 = snow)
	 * @param lRoadCond	array containing the observed road condition (values are
	 * the same as the lOutRoadCond listed below)
	 * @param dCloudCover array containing the forecasted cloud covers (the
	 * value is "octal" being from 0-8, 0 = 0% and 8 = 100% coverage)
	 * @param lOutRoadCond array containing the data output from METRo for the
	 * road condition (1 = dry road, 2 = wet road, 3 = ice/snow on the road, 4 =
	 * mix water/snow on the road, 5 = dew, 6 = melting snow, 7 = frost, 8 =
	 * icing rain)
	 * @param dOutRoadTemp array containing the data output from METRo for the
	 * road temperature (Celsius)
	 * @param dOutSubSurfTemp array containing the data output from METRo for
	 * the sub surface temperature (Celsius)
	 * @param dOutSnowIceAcc	array containing the data output from METRo for the
	 * snow/ice accumulation (mm)
	 * @param dOutLiquidAcc	array containing the data output from METRo for the
	 * liquid accumulation (mm)
	 * @param nTmtType	treatment type used, not implemented
	 * @param dRainReservoir	initial value used to set the rain reservoir value
	 * in mm
	 * @param dSnowReservoir	initial value used to set the snow reservoir value
	 * in mm
	 * @param dRainCutoff	cutoff value used for rain in mm when calculating road
	 * conditions
	 * @param dSnowCuttoff	cutoff value used for snow in mm when calculating road
	 * conditions
	 */
	private native void doMetroWrapper(int bBridge, double dLat, double dLon, int nObservationHrs, int nForecastHrs,
	   double[] dObsRoadTemp, double[] dObsSubSurfTemp, double[] dObsAirTemp, double[] dObsDewPoint, double[] dObsWindSpeed,
	   double[] dObsTime, double[] dFTime, double[] dFTimeSeconds, double[] dAirTemp, double[] dDewPoint,
	   double[] dWindSpeed, double[] dSfcPres, double[] dPrecipAmt, long[] lPrecipType, long[] lRoadCond,
	   double[] dCloudCover, long[] lOutRoadCond, double[] dOutRoadTemp, double[] dOutSubSurfTemp, double[] dOutSnowIceAcc,
	   double[] dOutLiquidAcc, int nTmtType, double dRainReservoir, double dSnowReservoir, double dRainCutoff, double dSnowCutoff);


	/**
	 * Wrapper for {@link DoMetroWrapper#doMetroWrapper(int, double, double, int, int, double[], double[], double[], double[], double[], double[], double[], double[], double[], double[], double[], double[], double[], long[], long[], double[], long[], double[], double[], double[], double[], int, double, double, double, double)}
	 */
	@Override
	public void run()
	{
		doMetroWrapper(m_bBridge, m_dLat, m_dLon, m_nObsHrs, m_nForecastHrs, m_dObsRoadTemp, m_dObsSubSurfTemp,
		   m_dObsAirTemp, m_dObsDewPoint, m_dObsWindSpeed, m_dObsTime, m_dFTime, m_dFTimeSeconds, m_dAirTemp, m_dDewPoint, m_dWindSpeed, m_dSfcPres, m_dPrecipAmt, m_lPrecipType,
		   m_lRoadCond, m_dCloudCover, m_lOutRoadCond, m_dOutRoadTemp, m_dOutSubSurfTemp, m_dOutSnowIceAcc, m_dOutLiquidAcc, m_nTmtType, m_dRainReservoir, m_dSnowReservoir,
		   m_dRAINCUTOFF, m_dSNOWCUTOFF);
	}

	
	/**
	 * Fills the input arrays for the C and Fortran code
	 * @param nLon longitude of the segment being used for the METRo model in 
	 * decimal degrees scaled to 7 decimal places
	 * @param nLat latitude of the segment being used for the METRo model in 
	 * decimal degrees scaled to 7 decimal places
	 * @param bBridge true if the segment is a bridge
	 * @param nTmtType 1 if the segment is chemically treated, otherwise 0
	 * @param lStartTime start time of the METRo run
	 * @param oFiles Data files need for the METRo run
	 * @return true if no errors or invalid values are found
	 */
	public boolean fillArrays(int nLon, int nLat, boolean bBridge, int nTmtType, long lStartTime, MetroObsSet oObsSet, int nTol)
	{
		long lForecast = lStartTime - 3600000; // the first "forecast" actually uses observed values
		int nEndLon = nLon + 1; // put small tolerance around segment midpoint for area weather obs
		int nEndLat = nLat + 1;
		nLon -= 1;
		nLat -= 1;
		int nImrcpContrib = Integer.valueOf("imrcp", 36);
		int nSegmentLonStart = nLon - nTol;
		int nSegmentLatStart = nLat - nTol;
		int nSegmentLonEnd = nLon + nTol;
		int nSegmentLatEnd = nLat + nTol;
		nLon -= 1;
		nLat -= 1;
		
		int[] nBoundingPolygon = GeoUtil.getBoundingPolygon(nLon, nLat, nEndLon, nEndLat);
		int[] nSegmentPolygon = GeoUtil.getBoundingPolygon(nSegmentLonStart, nSegmentLatStart, nSegmentLonEnd, nSegmentLatEnd);
		//fill observation arrays
		for (int i = 0; i < m_nObsHrs; i++)
		{
			m_dObsAirTemp[i] = getValue(oObsSet.m_oObsAirTemp[i], nBoundingPolygon);
			m_dObsDewPoint[i] = getValue(oObsSet.m_oObsDewPoint[i], nBoundingPolygon);
			m_dObsWindSpeed[i] = getValue(oObsSet.m_oObsWindSpeed[i], nBoundingPolygon);
			m_dObsRoadTemp[i] = getValue(oObsSet.m_oObsTpvt[i], nSegmentPolygon);
			m_dObsSubSurfTemp[i] = getValue(oObsSet.m_oObsTssrf[i], nSegmentPolygon);
			
			if (Double.isNaN(m_dObsRoadTemp[i]))
			{
				m_dObsRoadTemp[i] = getValue(oObsSet.m_oMetroTpvt[i], nSegmentPolygon);
				if (Double.isNaN(m_dObsRoadTemp[i]))
					m_dObsRoadTemp[i] = m_dObsAirTemp[i];
			}
			
			if (Double.isNaN(m_dObsSubSurfTemp[i]))
			{
				m_dObsSubSurfTemp[i] = getValue(oObsSet.m_oMetroTssrf[i], nSegmentPolygon);
				if (Double.isNaN(m_dObsSubSurfTemp[i]))
					m_dObsSubSurfTemp[i] = m_dObsRoadTemp[i];
			}
			
			m_lRoadCond[i] = imrcpToMetroRoadCond((int)getValue(oObsSet.m_oObsRoadCond[i], nSegmentPolygon, nImrcpContrib));

			//check that values fall within the correct range, if not exit the function and set that it failed so METRo isn't ran for the location
			if (Double.isNaN(m_dObsRoadTemp[i]) || m_dObsRoadTemp[i] < m_dROADTEMPMIN || m_dObsRoadTemp[i] > m_dROADTEMPMAX)
			{
//				m_oLogger.debug("Observation Road Temp out of range: " + " " + m_dObsRoadTemp[i] + " at " + nLat + " " + nLon + " for hour " + i);
				return false;
			}

			if (Double.isNaN(m_dObsSubSurfTemp[i]) || m_dObsSubSurfTemp[i] < m_dSUBSURTEMPMIN || m_dObsSubSurfTemp[i] > m_dSUBSURTEMPMAX)
			{
//				m_oLogger.debug("Observation Sub Surface Temp out of range: " + " " + m_dObsSubSurfTemp[i] + " at " + nLat + " " + nLon + " for hour " + i);
				return false;
			}

			if (Double.isNaN(m_dObsAirTemp[i]) || m_dObsAirTemp[i] < m_dAIRTEMPMIN || m_dObsAirTemp[i] > m_dAIRTEMPMAX)
			{
//				m_oLogger.debug("Observation Air Temperature out of range: " + " " + m_dObsAirTemp[i] + " at " + nLat + " " + nLon + " for hour " + i);
				return false;
			}

			if (Double.isNaN(m_dObsDewPoint[i]) || m_dObsDewPoint[i] < m_dAIRTEMPMIN || m_dObsDewPoint[i] > m_dAIRTEMPMAX)
			{
//				m_oLogger.debug("Observation Dew Point out of range: " + " " + m_dObsDewPoint[i] + " at " + nLat + " " + nLon + " for hour " + i);
				return false;
			}

			if (Double.isNaN(m_dObsWindSpeed[i]) || m_dObsWindSpeed[i] < 0 || m_dObsWindSpeed[i] > m_dWINDSPEEDMAX)
			{
//				m_oLogger.debug("Observation Wind Speed out of range: " + " " + m_dObsWindSpeed[i] + " at " + nLat + " " + nLon + " for hour " + i);
				return false;
			}
		}
		m_dObsTime = oObsSet.m_dObsTime;
		m_dFTime = oObsSet.m_dFTime;
		m_dFTimeSeconds = oObsSet.m_dFTimeSeconds;
		//fill forecast arrays
		for (int i = 0; i < m_nForecastHrs; i++)
		{
			long lTimestamp = lForecast + (3600000 * i);
			long lQueryStart = lTimestamp;
			// for the first hour of forecast use RTMA and radar precip, after that use NDFD for air temp, dew point, wind speed, and cloud cover and RAP for other values
			// the first hour ofc "forecast" is an hour in the past
			if (i == 0)
			{
				
				m_dAirTemp[i] = m_dObsAirTemp[m_dObsAirTemp.length - 1];
				m_dDewPoint[i] = m_dObsDewPoint[m_dObsDewPoint.length - 1];
				m_dWindSpeed[i] = m_dObsWindSpeed[m_dObsWindSpeed.length - 1];
				m_dCloudCover[i] = Math.round(getValue(oObsSet.m_oFcstCloudCover[i], nBoundingPolygon) / 12.5); // convert % to "octal"
				m_dSfcPres[i] = getValue(oObsSet.m_oFcstSfcPres[i], nBoundingPolygon) * 100; // convert mbar to Pa

				m_dRainReservoir = getValue(oObsSet.m_oRainRes, nSegmentPolygon);
				m_dSnowReservoir = getValue(oObsSet.m_oSnowRes, nSegmentPolygon);
				if (Double.isNaN(m_dRainReservoir))
					m_dRainReservoir = 0;
				if (Double.isNaN(m_dSnowReservoir))
					m_dSnowReservoir = 0;
				m_dSnowReservoir /= 10; // account for liquid equivalence
				ObsList oPrecip = getValues(oObsSet.m_oFcstPrecipRate[i], nBoundingPolygon);
				if (!oPrecip.isEmpty())
				{
					Introsort.usort(oPrecip, Obs.g_oCompObsByTime);
					int nPrecipIndex = 0;
					Obs oPrecipObs = oPrecip.get(nPrecipIndex);
					int nPrecipLimit = oPrecip.size();
					long lType = m_dAirTemp[i] > 0.5 ? 1 : 2; // infer type from the air temp
					for (int nIndex = 0; nIndex < 30; nIndex++) // fill in the precip arrays for every 30 secs of the last ten minutes
					{
						long lPrecipStart = lQueryStart + 120000 * nIndex;
						double dVal = Double.NaN;
						if (oPrecipObs.temporalMatch(lPrecipStart, lPrecipStart + 60000, lStartTime))
						{
							dVal = oPrecipObs.m_dValue / 3600000.0; // convert mm/hr to m/s
							if (++nPrecipIndex < nPrecipLimit)
								oPrecipObs = oPrecip.get(nPrecipIndex);
						}

						int nArrIndex = nIndex * 4; // multiply by 4 since mrms files come every 2 minutes
						if (Double.isFinite(dVal) && dVal > 0.0)
						{
							java.util.Arrays.fill(m_dPrecipAmt, nArrIndex, nArrIndex + 4, dVal); // these values are multipled by 30 in the Fortran code to get a 30 second value
							java.util.Arrays.fill(m_lPrecipType, nArrIndex, nArrIndex + 4, lType);
						}
						else
						{
							java.util.Arrays.fill(m_dPrecipAmt, nArrIndex, nArrIndex + 4, 0);
							java.util.Arrays.fill(m_lPrecipType, nArrIndex, nArrIndex + 4, 0);
						}
					}
				}
				else
				{
					java.util.Arrays.fill(m_dPrecipAmt, 0, 120, 0);
					java.util.Arrays.fill(m_lPrecipType, 0, 120, 0);
				}
			}
			else
			{
				m_dAirTemp[i] = getValue(oObsSet.m_oFcstAirTemp[i], nBoundingPolygon);
				m_dDewPoint[i] = getValue(oObsSet.m_oFcstDewPoint[i], nBoundingPolygon);
				m_dWindSpeed[i] = getValue(oObsSet.m_oFcstWindSpeed[i], nBoundingPolygon);
				m_dCloudCover[i] = Math.round(getValue(oObsSet.m_oFcstCloudCover[i], nBoundingPolygon) / 12.5); // convert % to "octal"
				m_dSfcPres[i] = getValue(oObsSet.m_oFcstSfcPres[i], nBoundingPolygon) * 100; // convert mbar to Pa

				int nPrecipIndex = i * 120; // multiply by 120 because RAP precip values are valid for an hour
				if (nPrecipIndex < m_dPrecipAmt.length)
				{
					double dPrecip = getValue(oObsSet.m_oFcstPrecipRate[i], nBoundingPolygon);
					double dPrecipType = getValue(oObsSet.m_oFcstPrecipType[i], nBoundingPolygon);
					int nType;
					if (Double.isNaN(dPrecipType) || dPrecipType == ObsType.lookup(ObsType.TYPPC, "none"))
						nType = 0; //none
					else if (dPrecipType == ObsType.lookup(ObsType.TYPPC, "snow"))
						nType = 2;  //snow
					else
						nType = 1;  //rain
					
					if (Double.isFinite(dPrecip) && dPrecip > 0.0)
					{
						java.util.Arrays.fill(m_dPrecipAmt, nPrecipIndex, nPrecipIndex + 120, dPrecip / 3600000.0); // convert mm/hr to m/s, these values are multipled by 30 in fortran code to get 30 second values
						java.util.Arrays.fill(m_lPrecipType, nPrecipIndex, nPrecipIndex + 120, nType);
					}
					else
					{
						java.util.Arrays.fill(m_dPrecipAmt, nPrecipIndex, nPrecipIndex + 120, 0);
						java.util.Arrays.fill(m_lPrecipType, nPrecipIndex, nPrecipIndex + 120, nType);
					}
				}
			}
			

			//check that values fall within the correct range, if not exit the function and set that it failed so METRo isn't ran for the lat/lon except for Surface Pressure
			if (Double.isNaN(m_dAirTemp[i]) || m_dAirTemp[i] < m_dAIRTEMPMIN || m_dAirTemp[i] > m_dAIRTEMPMAX)
			{
//				m_oLogger.debug("Forecast Air Temperature out of range: " + " " + m_dAirTemp[i] + " at " + nLat + " " + nLon + " for hour " + i);
				return false;
			}
			if (Double.isNaN(m_dDewPoint[i]) || m_dDewPoint[i] < m_dAIRTEMPMIN || m_dDewPoint[i] > m_dAIRTEMPMAX)
			{
//				m_oLogger.debug("Forecast Dew Point out of range: " + " " + m_dDewPoint[i] + " at " + nLat + " " + nLon + " for hour " + i);
				return false;
			}
			if (Double.isNaN(m_dWindSpeed[i]) || m_dWindSpeed[i] < 0 || m_dWindSpeed[i] > m_dWINDSPEEDMAX)
			{
//				m_oLogger.debug("Forecast Wind Speed out of range: " + " " + m_dWindSpeed[i] + " at " + nLat + " " + nLon + " for hour " + i);
				return false;
			}
			if (Double.isNaN(m_dSfcPres[i]) || m_dSfcPres[i] < m_dPRESSUREMIN || m_dSfcPres[i] > m_dPRESSUREMAX)
				m_dSfcPres[i] = m_dPRESSUREDEFAULT;  //METRo code set the Pressure to normal pressure if it was not in the correct range
			if (Double.isNaN(m_dCloudCover[i]) || m_dCloudCover[i] < 0 || m_dCloudCover[i] > 8)
			{
//				m_oLogger.debug("Forecast Cloud Cover out of range: " + " " + m_dCloudCover[i] + " at " + nLat + " " + nLon + " for hour " + i);
			return false;
			}
		}
		
		for (int nIndex = m_dPrecipAmt.length - 1; nIndex > 0; nIndex--)
			m_dPrecipAmt[nIndex] = m_dPrecipAmt[nIndex - 1];
		m_dPrecipAmt[0] = 0;

		//fill station variables
		if (bBridge)
			m_bBridge = 1;
		else
			m_bBridge = 0;
		m_dLat = GeoUtil.fromIntDeg(nLat);
		m_dLon = GeoUtil.fromIntDeg(nLon);
		m_nTmtType = nTmtType;
		return true;
	}

	
	/**
	 * Takes the output arrays from the C and Fortran code for the METRo run and
	 * stores them in a {@link imrcp.forecast.mdss.RoadcastData} object to be
	 * able to be used by other locations that have the same inputs to eliminate
	 * duplicate METRo runs
	 * @param nLon longitude in decimal degrees scaled to 7 decimal places
	 * @param nLat latitude in decimal degrees scaled to 7 decimal places
	 * @param lStartTime start time of the METRo run
	 */
	public void saveRoadcast(int nLon, int nLat, long lStartTime)
	{
		int nObsPerType = Metro.getObservationCount(m_nForecastHrs);
		RoadcastData oRD = new RoadcastData(nObsPerType, nLon, nLat);
		int nRoadcastIndex = 0;
		float[] fStpvt = oRD.m_oDataArrays.get(ObsType.STPVT);
		float[] fTpvt = oRD.m_oDataArrays.get(ObsType.TPVT);
		float[] fTssrf = oRD.m_oDataArrays.get(ObsType.TSSRF);
		float[] fDphsn = oRD.m_oDataArrays.get(ObsType.DPHSN);
		float[] fDphliq = oRD.m_oDataArrays.get(ObsType.DPHLIQ);
		
		for (int i = 0; i < m_nForecastHrs - 2; i++) // subtract 2 because the first forecast hour is an hour behind the run time and input values are interpolated so we get one hour less than the forecast hours
		{
			if (i == 0) // for the first hour save values every 2 minutes
			{
				for (int nIndex = 20; nIndex < 240; nIndex += 4) // start at 20 which is the index that corresponds to 50 minutes before lStartTime (each value in the output arrays is 30 seconds apart)
				{												//  we start there since that will be time index 0 in the next metro run
					fStpvt[nRoadcastIndex] = convertRoadCondition((int)m_lOutRoadCond[nIndex]);
					fTpvt[nRoadcastIndex] = (float)m_dOutRoadTemp[nIndex];
					fTssrf[nRoadcastIndex] = (float)m_dOutSubSurfTemp[nIndex];
					fDphsn[nRoadcastIndex] = (float)m_dOutSnowIceAcc[nIndex] * 10; // liquid equivalent for snow so multiple by ten to mm
					fDphliq[nRoadcastIndex++] = (float)m_dOutLiquidAcc[nIndex];
					
				}
			}
			else if (i < 12) // up to 12 hours save values for every 20 minutes
			{
				for (int j = 0; j < 3; j++)
				{
					int nIndex = ((i + 1) * 120) + (j * 40);
					fStpvt[nRoadcastIndex] = convertRoadCondition((int)m_lOutRoadCond[nIndex]);
					fTpvt[nRoadcastIndex] = (float)m_dOutRoadTemp[nIndex];
					fTssrf[nRoadcastIndex] = (float)m_dOutSubSurfTemp[nIndex];
					fDphsn[nRoadcastIndex] = (float)m_dOutSnowIceAcc[nIndex] * 10; // liquid equivalent for snow so multiple by ten to mm
					fDphliq[nRoadcastIndex++] = (float)m_dOutLiquidAcc[nIndex];
				}
			}
			else // for all other forecast hours save values for every 1 hour
			{
				int nIndex = (i + 1) * 120;
				fStpvt[nRoadcastIndex] = convertRoadCondition((int)m_lOutRoadCond[nIndex]);
				fTpvt[nRoadcastIndex] = (float)m_dOutRoadTemp[nIndex];
				fTssrf[nRoadcastIndex] = (float)m_dOutSubSurfTemp[nIndex];
				fDphsn[nRoadcastIndex] = (float)m_dOutSnowIceAcc[nIndex] * 10; // liquid equivalent for snow so multiple by ten to mm
				fDphliq[nRoadcastIndex++] = (float)m_dOutLiquidAcc[nIndex];
			}
		}
		m_oOutput = oRD;
	}


	/**
	 * Takes the given METRo road condition and returns the corresponding IMRCP
	 * {@link ObsType#STPVT} value
	 * @param nCond METRo road condition value
	 * @return Corresponding IMRCP {@link ObsType#STPVT} value
	 */
	public int convertRoadCondition(int nCond)
	{
		switch (nCond)
		{
			case 1:
				return ObsType.lookup(ObsType.STPVT, "dry");
			case 2:
				return ObsType.lookup(ObsType.STPVT, "wet");
			case 3:
				return ObsType.lookup(ObsType.STPVT, "ice/snow");
			case 4:
				return ObsType.lookup(ObsType.STPVT, "slush");
			case 5:
				return ObsType.lookup(ObsType.STPVT, "dew");
			case 6:
				return ObsType.lookup(ObsType.STPVT, "melting-snow");
			case 7:
				return ObsType.lookup(ObsType.STPVT, "frost");
			case 8:
				return ObsType.lookup(ObsType.STPVT, "icing-rain");
			default:
				return ObsType.lookup(ObsType.STPVT, "other");
		}
	}

	
	/**
	 * Takes the given IMRCP {@link ObsType#STPVT} value and returns the
	 * corresponding METRo road condition
	 * @param nCond IMRCP {@link ObsType#STPVT} value
	 * @return corresponding METRo road condition
	 */
	public int imrcpToMetroRoadCond(int nCond)
	{
		if (nCond == ObsType.lookup(ObsType.STPVT, "dry"))
			return 1;
		else if (nCond == ObsType.lookup(ObsType.STPVT, "wet"))
			return 2;
		else if (nCond == ObsType.lookup(ObsType.STPVT, "ice/snow"))
			return 3;
		else if (nCond == ObsType.lookup(ObsType.STPVT, "slush"))
			return 4;
		else if (nCond == ObsType.lookup(ObsType.STPVT, "dew"))
			return 5;
		else if (nCond == ObsType.lookup(ObsType.STPVT, "melting-snow"))
			return 6;
		else if (nCond == ObsType.lookup(ObsType.STPVT, "frost"))
			return 7;
		else if (nCond == ObsType.lookup(ObsType.STPVT, "icing-rain"))
			return 8;
		else
			return 1;
	}

	
	/**
	 * Fills the input arrays for the C and Fortran code. This method is used by
	 * {@link imrcp.web.Scenarios}
	 * @param oProcess contains the information needed to run METRo for a 
	 * {@link imrcp.web.Scenario}
	 * @param nIndex hour index of the scenario
	 * @param oWay OsmWay being used for this run of METRo
	 */
	public void fillArrays(MetroProcess oProcess, int nIndex, OsmWay oWay)
	{
		long lObservation = oProcess.m_lTimes[nIndex - 1];
		long lForecast = oProcess.m_lTimes[nIndex]; // the first "forecast" actually uses observed values

		Calendar oCal = Calendar.getInstance();
		int nStartObsIndex = nIndex - oProcess.m_nObsPerRun + 1;
		//fill observation arrays
		System.arraycopy(oProcess.m_dAirTemp, nStartObsIndex, m_dObsAirTemp, 0, m_nObsHrs);
		System.arraycopy(oProcess.m_dDewPoint, nStartObsIndex, m_dObsDewPoint, 0, m_nObsHrs);
		System.arraycopy(oProcess.m_dWindSpeed, nStartObsIndex, m_dObsWindSpeed, 0, m_nObsHrs);
		
		for (int i = 0; i < m_nObsHrs; i++)
		{
			int nProcessIndex = nStartObsIndex + i;
			long lTimestamp = lObservation + (i * 3600000);
			double dTpvt = oProcess.m_dPavementTemp[nProcessIndex];
			
			if (Double.isNaN(dTpvt))
				dTpvt = oProcess.m_dAirTemp[nProcessIndex];
			
			double dTssrf = oProcess.m_dSubSurfTemp[nProcessIndex];
			if (Double.isNaN(dTssrf))
				dTssrf = dTpvt;

			m_dObsRoadTemp[i] = dTpvt;
			m_dObsSubSurfTemp[i] = dTssrf;
			
			int nRoadCond = oProcess.m_nPavementState[nProcessIndex];
			if (nRoadCond == Integer.MIN_VALUE)
			{
				int nPrecipType = oProcess.m_nPrecipType[nProcessIndex];
				if (nPrecipType == 0)
					nRoadCond = 1; // dry for no precip
				else if (nPrecipType == 1)
					nRoadCond = 2; // wet for rain
				else
					nRoadCond = 3; // ice/snow	
			}
			else
				nRoadCond = imrcpToMetroRoadCond(nRoadCond);


			m_lRoadCond[i] = nRoadCond;

			oCal.setTimeInMillis(lTimestamp);
			m_dObsTime[i] = oCal.get(Calendar.HOUR_OF_DAY) + (double)oCal.get(Calendar.MINUTE) / 60.0;
		}

		//fill forecast arrays
		System.arraycopy(oProcess.m_dAirTemp, nIndex, m_dAirTemp, 0, m_dAirTemp.length);
		System.arraycopy(oProcess.m_dDewPoint, nIndex, m_dDewPoint, 0, m_dDewPoint.length);
		System.arraycopy(oProcess.m_dWindSpeed, nIndex, m_dWindSpeed, 0, m_dWindSpeed.length);
		System.arraycopy(oProcess.m_dPressure, nIndex, m_dSfcPres, 0, m_dSfcPres.length);
		
		m_dRainReservoir = oProcess.m_dRainRes[nIndex];
		if (Double.isNaN(m_dRainReservoir))
			m_dRainReservoir = 0;
		m_dSnowReservoir = oProcess.m_dSnowRes[nIndex];
		if (Double.isNaN(m_dSnowReservoir) || oProcess.m_bPlowing[nIndex])
			m_dSnowReservoir = 0;
		for (int i = 0; i < m_nForecastHrs; i++)
		{
			int nProcessIndex = nIndex + i;
			long lTimestamp = lForecast + (3600000 * i);
			m_dCloudCover[i] = Math.round(oProcess.m_dCloudCover[nProcessIndex] / 12.5);
			int nPrecipIndex = i * 120;
			if (nPrecipIndex < m_dPrecipAmt.length)
			{
				java.util.Arrays.fill(m_dPrecipAmt, nPrecipIndex, nPrecipIndex + 120, oProcess.m_dPrecipRate[nProcessIndex] / 3600000);
				java.util.Arrays.fill(m_lPrecipType, nPrecipIndex, nPrecipIndex + 120, oProcess.m_nPrecipType[nProcessIndex]);
			}

			oCal.setTimeInMillis(lTimestamp);
			m_dFTime[i] = oCal.get(Calendar.HOUR_OF_DAY) + (double)oCal.get(Calendar.MINUTE) / 60.0;
			m_dFTimeSeconds[i] = (int)(lTimestamp / 1000);
			
			if (Double.isNaN(m_dSfcPres[i]) || m_dSfcPres[i] < m_dPRESSUREMIN || m_dSfcPres[i] > m_dPRESSUREMAX)
				m_dSfcPres[i] = m_dPRESSUREDEFAULT;  //METRo code set the Pressure to normal pressure if it was not in the correct range

		}

		//fill station variables
		if (oWay.m_bBridge)
			m_bBridge = 1;
		else
			m_bBridge = 0;
		m_dLat = GeoUtil.fromIntDeg(oWay.m_nMidLat);
		m_dLon = GeoUtil.fromIntDeg(oWay.m_nMidLon);
		m_nTmtType = 0;
		if (oProcess.m_bTreating[nIndex])
			m_nTmtType = 1;
	}

	
	/**
	 * Creates a StringBuilder containing information about the METRo run
	 * @param lTimestamp run time of METRo
	 * @return StringBuilder with log messages
	 * @throws Exception
	 */
	public StringBuilder log(long lTimestamp)
		throws Exception
	{
		StringBuilder oOut = new StringBuilder();
		if (m_oOutput == null)
			return oOut;
		
		SimpleDateFormat oSdf = new SimpleDateFormat("yyyyMMdd HHmm");
		long lObservation = lTimestamp - (3600000 * m_nObsHrs);
		long lForecast = lTimestamp - 3600000; // the first "forecast" actually uses observed values
		oSdf.setTimeZone(Directory.m_oUTC);
//		oOut.append(String.format("runtime:%s,%2.7f,%2.7f,%s,%2.7f,%2.7f\n", oSdf.format(lTimestamp), m_dLon, m_dLat, m_bBridge == 1 ? "yes" : "no", m_dRainReservoir, m_dSnowReservoir));
//		oOut.append("obs_times");
//		for (int i = 0; i < m_nObsHrs; i++)
//		{
//			long lTime = lObservation + (i * 3600000);
//			oOut.append(',').append(oSdf.format(lTime));
//		}
//		oOut.append('\n');
//		
//		oOut.append("fcst_times");
//		for (int i = 0; i < m_nForecastHrs; i++)
//		{
//			long lTime = lForecast + (3600000 * i);
//			oOut.append(',').append(oSdf.format(lTime));
//		}
//		oOut.append('\n');
//		
//		oOut.append("obs_tair_C");
//		for (double dVal : m_dObsAirTemp)
//			oOut.append(String.format(",%2.2f", dVal));
//		oOut.append('\n');
//		
//		oOut.append("obs_tdew_C");
//		for (double dVal : m_dObsDewPoint)
//			oOut.append(String.format(",%2.2f", dVal));
//		oOut.append('\n');
//		
//		oOut.append("obs_spdwnd_m/s");
//		for (double dVal : m_dObsWindSpeed)
//			oOut.append(String.format(",%2.2f", dVal));
//		oOut.append('\n');
//		
//		oOut.append("obs_tpvt_C");
//		for (double dVal : m_dObsRoadTemp)
//			oOut.append(String.format(",%2.2f", dVal));
//		oOut.append('\n');
//		
//		oOut.append("obs_tssrf_C");
//		for (double dVal : m_dObsSubSurfTemp)
//			oOut.append(String.format(",%2.2f", dVal));
//		oOut.append('\n');
//		
//		oOut.append("obs_stpvt_(1=dry_ 2=wet_ 3=ice/snow_ 4=mix water/snow_ 5=dew_ 6=melting snow_ 7=frost_ 8=icing rain)");
//		for (long dVal : m_lRoadCond)
//			oOut.append(String.format(",%2.2f", (double)dVal));
//		oOut.append('\n');
//		
//		oOut.append("obs_time_hourofday");
//		for (double dVal : m_dObsTime)
//			oOut.append(String.format(",%2.2f", dVal));
//		oOut.append('\n');
//		
//		oOut.append("fcst_tair_C");
//		for (double dVal : m_dAirTemp)
//			oOut.append(String.format(",%2.2f", dVal));
//		oOut.append('\n');
//		
//		oOut.append("fcst_tdew_C");
//		for (double dVal : m_dDewPoint)
//			oOut.append(String.format(",%2.2f", dVal));
//		oOut.append('\n');
//		
//		oOut.append("fcst_spdwnd_m/s");
//		for (double dVal : m_dWindSpeed)
//			oOut.append(String.format(",%2.2f", dVal));
//		oOut.append('\n');
//		
//		oOut.append("fcst_prsur_Pa");
//		for (double dVal : m_dSfcPres)
//			oOut.append(String.format(",%2.2f", dVal));
//		oOut.append('\n');
//		
//		oOut.append("fcst_covcld_12.5%");
//		for (double dVal : m_dCloudCover)
//			oOut.append(String.format(",%2.2f", dVal));
//		oOut.append('\n');	
//		
//		oOut.append("fcst_rtepc_m/s");
//		for (double dVal : m_dPrecipAmt)
//			oOut.append(String.format(",%2.9f", dVal));
//		oOut.append('\n');
//		
//		oOut.append("fcst_typpc_(values 0 = none 1 = rain 2 = snow)");
//		for (long dVal : m_lPrecipType)
//			oOut.append(String.format(",%2.2f", (double)dVal));
//		oOut.append('\n');
		oOut.append("outputs");
		int nOutputStart = 25;
		long lFirstValue = lForecast + 600000;
		for (int i = 0; i < m_nForecastHrs - 2; i++)
		{
			if (i == 0) // for the first hour save values every 2 minutes
			{
				for (int j = nOutputStart; j < 55; j++)
				{
					long lTs = lFirstValue + j * 120000;
					oOut.append(',').append(oSdf.format(lTs));
				}
				lFirstValue = lTimestamp + 3600000;
			}
			else if (i < 12) // for all other forecast hours save values for every 20 minutes
			{
				for (int j = 0; j < 3; j++)
				{
					
					long lTs = lFirstValue + j * 1200000;
					oOut.append(',').append(oSdf.format(lTs));
				}
				lFirstValue += 3600000;
			}
			else
			{
				oOut.append(',').append(oSdf.format(lFirstValue));
				lFirstValue += 3600000;
			}
		}

		oOut.append('\n');
		oOut.append("fcst_tair_C");
		for (int nTemp = nOutputStart; nTemp < 25; nTemp++)
			oOut.append(String.format(",%2.2f", m_dAirTemp[0]));
		for (int nTemp = 0; nTemp < 30; nTemp++)
			oOut.append(String.format(",%2.2f", m_dAirTemp[1]));
		for (int nTemp = 0; nTemp < 3; nTemp++)
			oOut.append(String.format(",%2.2f", m_dAirTemp[2]));
		oOut.append("\nout_stpvt_(3=dry_5=wet_20=ice/snow_21=slush_12=dew_22=melting-snow_13=frost_23=icing-rain_1=other)");
		float[] fStpvt = m_oOutput.m_oDataArrays.get(ObsType.STPVT);
		float[] fTpvt = m_oOutput.m_oDataArrays.get(ObsType.TPVT);
		float[] fTssrf = m_oOutput.m_oDataArrays.get(ObsType.TSSRF);
		float[] fDphsn = m_oOutput.m_oDataArrays.get(ObsType.DPHSN);
		float[] fDphliq = m_oOutput.m_oDataArrays.get(ObsType.DPHLIQ);
		
		for (int n = nOutputStart; n < fStpvt.length; n++)
			oOut.append(String.format(",%d", (int)fStpvt[n]));
		oOut.append('\n');
		oOut.append("out_tpvt_C");
		for (int n = nOutputStart; n < fStpvt.length; n++)
			oOut.append(String.format(",%2.2f", fTpvt[n]));
		oOut.append('\n');
		oOut.append("out_tssrf_C");
		for (int n = nOutputStart; n < fStpvt.length; n++)
			oOut.append(String.format(",%2.2f", fTssrf[n]));
		oOut.append('\n');
		oOut.append("out_dphliq_mm");
		for (int n = nOutputStart; n < fStpvt.length; n++)
			oOut.append(String.format(",%2.2f", fDphliq[n]));
		oOut.append('\n');
		oOut.append("out_dphsn_mm");
		for (int n = nOutputStart; n < fStpvt.length; n++)
			oOut.append(String.format(",%2.2f", fDphsn[n]));
		
		oOut.append('\n');
		oOut.append("precip_into_model_mm");
		
		double dPrecip = 0.0;
		for (int nTempIndex = 0; nTempIndex < 20; nTempIndex++)
			dPrecip += m_dPrecipAmt[nTempIndex] * 30 * 1000;
		
		oOut.append(String.format(",%2.2f,,,,", dPrecip * 10));
		oOut.append('\n');
		oOut.append("input_snow_res_mm");
		oOut.append(String.format(",%2.2f,,,,", m_dSnowReservoir * 10));
		oOut.append('\n');
		oOut.append("tpvt-comp");
		oOut.append(String.format(",%2.2f,%2.2f,,,", fTpvt[0], m_dObsRoadTemp[m_dObsRoadTemp.length - 1]));
		
		
		oOut.append('\n');
		return oOut;
	}
	
	
	public static double getValue(ObsList oData, int[] nBoundingPolygon)
	{
		for (Obs oObs : oData)
		{
			if (oObs.spatialMatch(nBoundingPolygon))
				return oObs.m_dValue;
		}
		
		return Double.NaN;
	}
	
	
	public static double getValue(ObsList oData, int[] nBoundingPolygon, int nContrib)
	{
		double dReturn = Double.NaN;
		
		for (Obs oObs : oData)
		{
			if (oObs.spatialMatch(nBoundingPolygon))
			{
				dReturn = oObs.m_dValue;
				if (oObs.m_nContribId == nContrib)
					return dReturn;
			}
		}
		
		return dReturn;
	}
	
	
	public static ObsList getValues(ObsList oData, int[] nBoundingPolygon)
	{
		ObsList oReturn = new ObsList(30);
		for (Obs oObs : oData)
		{
			if (oObs.spatialMatch(nBoundingPolygon))
				oReturn.add(oObs);
		}
		
		return oReturn;
	}
}
