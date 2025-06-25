package imrcp.system;

import imrcp.geosrv.RangeRules;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import org.json.JSONObject;

/**
 * This class contains the defined observation types available in system as well
 * as the enumerated values for those observation types.
 * @author aaron.cherney
 */
public class ObsType
{
	/**
	 * Array that defines all of the observation types. Each observation type
	 * is defined by a String[] in the format [up to 6 character alphanumeric String
	 * that is the IMRCP observation type id, english units, metric units, observation type description, in use flag 'y' or 'n']
	 */
	private static final String OBS_TYPES[][] = new String[][]
	{
		{"obstype", "english", "metric", "description", "in use(y/n)", "label"}, // reserved for unknown observation type
		{"TAIR", "F", "C", "air temperature", "y", "Air Temperature (F)"},
		{"TDEW", "F", "C", "dew point temperature", "y", "Dew Point (F)"},
		{"TPVT", "F", "C", "pavement temperature", "y", "Pavement Temperature (F)"},
		{"PRBAR", "psi", "mbar", "barometric pressure", "y", "Barometric Pressure (psi)"},
		{"PRSUR", "psi", "mbar", "surface pressure", "y", "Surface Pressure (psi)"},
		{"RH", "%", "%", "relative humidity", "y", "Relative Humidity (%)"},
		{"VIS", "mi", "m", "surface visibility", "y", "Visibility (mi)"},
		{"CONPVT", "V", "V", "pavement conductivity", "y", "Conductivity (V)"},
		{"STPVT", "", "", "pavement state", "y", "Pavement State"},
		{"DIRWND", "º", "º", "wind direction", "y", "Wind Direction (º)"},
		{"GSTWND", "mph", "m/s", "wind speed gust", "y", "Wind Gust Speed (mph)"},
		{"SPDWND", "mph", "m/s", "wind speed", "y", "Wind Speed (mph)"},
		{"COVCLD", "%", "%", "total cloud cover", "y", "Cloud Cover (%)"},
		{"RTEPC", "in/hr", "mm/hr", "precipitation rate surface", "y", "Precipitation Rate (in/hr)"},
		{"TYPPC", "", "", "precipitation type", "y", "Precipitation Type"},
		{"STG", "", "", "stage", "y", "Flood Stage"},
		{"EVT", "", "", "event", "y", "Event"},
		{"VOLLNK", "veh/min", "veh/min", "link volume", "y", "Link Volume (veh/min)"},
		{"GENLNK", "veh", "veh", "vehicles generated on each generation link", "n", ""},
		{"VEHLNK", "veh", "veh", "number of vehicles on each link", "n", ""},
		{"QUELNK", "veh", "veh", "number of queued vehicles on each link", "n", ""},
		{"SPDLNK", "mph", "kph", "average speed of vehicles on each link", "y", "Link Speed (mph)"},
		{"DNTLNK", "%", "%", "average density of vehicles on each link", "y", "Link Density (%)"},
		{"SPFLNK", "mph", "kph", "average speed of moving vehicles on each link", "n", ""},
		{"DNFLNK", "%", "%", "average density of moving vehicles on each link", "n", ""},
		{"CTLEFT", "veh", "veh", "number of left-turning vehicles on each link", "n", ""},
		{"DURGRN", "s", "s", "average green time for each approach", "n", ""},
		{"CTTHRU", "veh", "veh", "number of vehicles that pass through the link", "n", ""},
		{"CTMID", "veh", "veh", "cumulative number of vehicles that pass the mid point of links", "n", ""},
		{"TSSRF", "F", "C", "subsurface temperature", "y", "Subsurface Temperature (F)"},
		{"DPHLIQ", "in", "mm", "liquid inundation depth", "y", "Liquid Depth (in)"},
		{"DPHSN", "in", "mm", "snow inundation depth", "y", "Snow Depth (in)"},
		{"FLWCAT", "", "", "predicted flow category", "n", ""},
		{"SPDCAT", "", "", "predicted speed category", "n", ""},
		{"OCCCAT", "", "", "predicted occupancy category", "n", ""},
		{"QPRLNK", "%", "%", "queue percentage on link", "n", ""},
		{"DPHLNK", "in", "mm", "link depth", "y", "Link Depth (in)"},
		{"PCCAT", "", "", "precipitation category", "y", "Precipitation Category"},
		{"TRFLNK", "%", "%", "traffic", "y", "Traffic (%)"},
		{"TDNLNK", "", "", "traffic density", "y", "Traffic Density"},
		{"TIMERT", "min", "min", "route time", "y", "Route Time"},
		{"RDR0", "dBZ", "dBZ", "merged base reflectivity", "y", "Radar Reflectivity (dBZ)"},
		{"NOTIFY", "", "", "Notification", "y", "Notification"},
		{"TRSCAT", "", "", "Tropical Storm Category", "y", "Tropical Storm Category"},
		{"TRSTRK", "", "", "Tropical Storm Track", "y", "Tropical Storm Track"},
		{"TRSCNE", "", "", "Tropical Storm Cone", "y", "Tropical Storm Cone"},
		{"KRTPVT", "F", "C", "kriged pavement temperature", "n", ""},
		{"KTSSRF", "F", "C", "kriged subsurface temperature", "n", ""},
		{"SSCST", "ft", "m", "extra tropical storm surge combined surge and tide", "y", "Storm Surge and Tide (ft)"},
		{"VARIES", "", "", "various", "y", "Various"},
		{"RTSLDM", "", "kg/km", "Solid material rate", "y", "Solid Material Rate (kg/km)"},
		{"RTLIQM", "", "L/km", "Liquid material rate", "y", "Liquid Material Rate (L/km)"},
		{"RTPREM", "", "L/Mg", "Prewet material rate", "y", "Prewet Material Rate (L/Mg)"},
		{"TPSLDM", "", "", "Solid material type", "y", "Solid Material Type"},
		{"TPLIQM", "", "", "Liquid material type", "y", "Liquid Material Type"},
		{"TPPREM", "", "", "Prewet material type", "y", "Prewet Material Type"},
		{"MPLOW", "", "", "MAC main plow", "y", "MAC Main Plow"},
		{"WPLOW", "", "", "MAC wing plow", "y", "MAC Wing Plow"},
		{"TPLOW", "", "", "MAC tow plow", "y", "MAC Tow Plow"},
		{"SPDVEH", "mph", "kph", "vehicle speed", "y", "Vehicle Speed (mph)"},
		{"VSLLNK", "mph", "kph", "variable speed limit on link", "y", "Variable Speed Limit (mph)"}
	};

	
	/**
	 * air temperature
	 */
	public static final int TAIR = Integer.valueOf(OBS_TYPES[1][0], 36);

	
	/**
	 * dew point temperature
	 */
	public static final int TDEW = Integer.valueOf(OBS_TYPES[2][0], 36);

	
	/**
	 * pavement temperature
	 */
	public static final int TPVT = Integer.valueOf(OBS_TYPES[3][0], 36);

	
	/**
	 * barometric pressure
	 */
	public static final int PRBAR = Integer.valueOf(OBS_TYPES[4][0], 36);

	
	/**
	 * surface pressure
	 */
	public static final int PRSUR = Integer.valueOf(OBS_TYPES[5][0], 36);

	
	/**
	 * relative humidity
	 */
	public static final int RH = Integer.valueOf(OBS_TYPES[6][0], 36);

	
	/**
	 * surface visibility
	 */
	public static final int VIS = Integer.valueOf(OBS_TYPES[7][0], 36);

	
	/**
	 * pavement conductivity
	 */
	public static final int CONPVT = Integer.valueOf(OBS_TYPES[8][0], 36);

	
	/**
	 * pavement state
	 */
	public static final int STPVT = Integer.valueOf(OBS_TYPES[9][0], 36);

	
	/**
	 * wind direction
	 */
	public static final int DIRWND = Integer.valueOf(OBS_TYPES[10][0], 36);

	
	/**
	 * wind speed gust
	 */
	public static final int GSTWND = Integer.valueOf(OBS_TYPES[11][0], 36);

	
	/**
	 * wind speed
	 */
	public static final int SPDWND = Integer.valueOf(OBS_TYPES[12][0], 36);

	
	/**
	 * total cloud cover
	 */
	public static final int COVCLD = Integer.valueOf(OBS_TYPES[13][0], 36);

	
	/**
	 * precipitation rate surface
	 */
	public static final int RTEPC = Integer.valueOf(OBS_TYPES[14][0], 36);

	
	/**
	 * precipitation type
	 */
	public static final int TYPPC = Integer.valueOf(OBS_TYPES[15][0], 36);

	
	/**
	 * stage
	 */
	public static final int STG = Integer.valueOf(OBS_TYPES[16][0], 36);

	
	/**
	 * event
	 */
	public static final int EVT = Integer.valueOf(OBS_TYPES[17][0], 36);

	
	/**
	 * link volume
	 */
	public static final int VOLLNK = Integer.valueOf(OBS_TYPES[18][0], 36);

	
	/**
	 * vehicles generate on each generation link
	 */
	public static final int GENLNK = Integer.valueOf(OBS_TYPES[19][0], 36);

	
	/**
	 * number of vehicles on each link
	 */
	public static final int VEHLNK = Integer.valueOf(OBS_TYPES[20][0], 36);

	
	/**
	 * number of queued vehicles on each link
	 */
	public static final int QUELNK = Integer.valueOf(OBS_TYPES[21][0], 36);

	
	/**
	 * average speed of vehicles on each link
	 */
	public static final int SPDLNK = Integer.valueOf(OBS_TYPES[22][0], 36);

	
	/**
	 * average density of vehicles on each link
	 */
	public static final int DNTLNK = Integer.valueOf(OBS_TYPES[23][0], 36);

	
	/**
	 * average speed of moving vehicles on each link
	 */
	public static final int SPFLNK = Integer.valueOf(OBS_TYPES[24][0], 36);

	
	/**
	 * average density of moving vehicles on each link
	 */
	public static final int DNFLNK = Integer.valueOf(OBS_TYPES[25][0], 36);

	
	/**
	 * number of left-turning vehicles on each link
	 */
	public static final int CTLEFT = Integer.valueOf(OBS_TYPES[26][0], 36);

	
	/**
	 * average green time for each approach
	 */
	public static final int DURGRN = Integer.valueOf(OBS_TYPES[27][0], 36);

	
	/**
	 * number of vehicles that pass through the link
	 */
	public static final int CTTHRU = Integer.valueOf(OBS_TYPES[28][0], 36);

	
	/**
	 * cumulative number of vehicles that pass the mid point of links
	 */
	public static final int CTMID = Integer.valueOf(OBS_TYPES[29][0], 36);

	
	/**
	 * subsurface temperature
	 */
	public static final int TSSRF = Integer.valueOf(OBS_TYPES[30][0], 36);

	
	/**
	 * liquid inundation depth
	 */
	public static final int DPHLIQ = Integer.valueOf(OBS_TYPES[31][0], 36);

	
	/**
	 * snow inundation depth
	 */
	public static final int DPHSN = Integer.valueOf(OBS_TYPES[32][0], 36);

	
	/**
	 * predicted flow category
	 */
	public static final int FLWCAT = Integer.valueOf(OBS_TYPES[33][0], 36);

	
	/**
	 * predicted speed category
	 */
	public static final int SPDCAT = Integer.valueOf(OBS_TYPES[34][0], 36);

	
	/**
	 * predicted occupancy category
	 */
	public static final int OCCCAT = Integer.valueOf(OBS_TYPES[35][0], 36);

	
	/**
	 * queue percentage on link
	 */
	public static final int QPRLNK = Integer.valueOf(OBS_TYPES[36][0], 36);

	
	/**
	 * link depth
	 */
	public static final int DPHLNK = Integer.valueOf(OBS_TYPES[37][0], 36);

	
	/**
	 * precipitation category
	 */
	public static final int PCCAT = Integer.valueOf(OBS_TYPES[38][0], 36);

	
	/**
	 * traffic
	 */
	public static final int TRFLNK = Integer.valueOf(OBS_TYPES[39][0], 36);

	
	/**
	 * traffic density
	 */
	public static final int TDNLNK = Integer.valueOf(OBS_TYPES[40][0], 36);

	
	/**
	 * route time
	 */
	public static final int TIMERT = Integer.valueOf(OBS_TYPES[41][0], 36);

	
	/**
	 * merged base reflectivity
	 */
	public static final int RDR0 = Integer.valueOf(OBS_TYPES[42][0], 36);

	
	/**
	 * notification
	 */
	public static final int NOTIFY = Integer.valueOf(OBS_TYPES[43][0], 36);
	
	
	/**
	 * tropical storm category
	 */
	public static final int TRSCAT = Integer.valueOf(OBS_TYPES[44][0], 36);
	
	
	/**
	 * tropical storm track
	 */
	public static final int TRSTRK = Integer.valueOf(OBS_TYPES[45][0], 36);
	
	
	/**
	 * tropical storm cone
	 */
	public static final int TRSCNE = Integer.valueOf(OBS_TYPES[46][0], 36);
	
	
	/**
	 * kriged pavement temperature
	 */
	public static final int KRTPVT = Integer.valueOf(OBS_TYPES[47][0], 36);
	
	
	/**
	 * kriged subsurface temperature
	 */
	public static final int KTSSRF = Integer.valueOf(OBS_TYPES[48][0], 36);
	
	
	/**
	 * extra tropical storm surge combined surge and tide
	 */
	public static final int SSCST = Integer.valueOf(OBS_TYPES[49][0], 36);
	
	
	/**
	 * all
	 */
	public static final int VARIES = Integer.valueOf(OBS_TYPES[50][0], 36);
	
	
	/**
	 * solid material rate
	 */
	public static final int RTSLDM = Integer.valueOf(OBS_TYPES[51][0], 36);
	
	
	/**
	 * liquid material rate
	 */
	public static final int RTLIQM = Integer.valueOf(OBS_TYPES[52][0], 36);
	
	
	/**
	 * prewet material rate
	 */
	public static final int RTPREM = Integer.valueOf(OBS_TYPES[53][0], 36);
	
	
	/**
	 * solid material type
	 */
	public static final int TPSLDM = Integer.valueOf(OBS_TYPES[54][0], 36);
	
	
	/**
	 * liquid material type
	 */
	public static final int TPLIQM = Integer.valueOf(OBS_TYPES[55][0], 36);
	
	
	/**
	 * prewet material type
	 */
	public static final int TPPREM = Integer.valueOf(OBS_TYPES[56][0], 36);
	
	
	/**
	 * MAC main plow
	 */
	public static final int MPLOW = Integer.valueOf(OBS_TYPES[57][0], 36);
	
	
	/**
	 * MAC wing plow
	 */
	public static final int WPLOW = Integer.valueOf(OBS_TYPES[58][0], 36);
	
	
	/**
	 * MAC tow plow
	 */
	public static final int TPLOW = Integer.valueOf(OBS_TYPES[59][0], 36);
	
	/**
	 * Vehicle Speed
	 */
	public static final int SPDVEH = Integer.valueOf(OBS_TYPES[60][0], 36);

	public static final int VSLLNK = Integer.valueOf(OBS_TYPES[61][0], 36);
	
	
	/**
	 * Contains all of the defined observation type ids, with the first index
	 * reserved for unknown observation types
	 */
	private static final int[] TYPE_MAP
	   =
	   {
		   0, // reserved for unknown observation type
		   TAIR, TDEW, TPVT, PRBAR, PRSUR, RH, VIS, CONPVT,
		   STPVT, DIRWND, GSTWND, SPDWND, COVCLD, RTEPC, TYPPC, STG,
		   EVT, VOLLNK, GENLNK, VEHLNK, QUELNK, SPDLNK, DNTLNK, SPFLNK,
		   DNFLNK, CTLEFT, DURGRN, CTTHRU, CTMID, TSSRF, DPHLIQ, DPHSN,
		   FLWCAT, SPDCAT, OCCCAT, QPRLNK, DPHLNK, PCCAT, TRFLNK, TDNLNK,
		   TIMERT, RDR0, NOTIFY, TRSCAT, TRSTRK, TRSCNE, KRTPVT, KTSSRF, SSCST, VARIES,
		   RTSLDM, RTLIQM, RTPREM, TPSLDM, TPLIQM, TPPREM, MPLOW, WPLOW, TPLOW, SPDVEH, VSLLNK
	   };

	
	/**
	 *  Contains all of the defined observation type ids, without the reserved
	 * spot for unknown observation types
	 */
	public static final int[] ALL_OBSTYPES = Arrays.copyOfRange(TYPE_MAP, 1, TYPE_MAP.length);

	
	/**
	 * Stores the enumerated values for each observation type
	 */
	private static final TreeMap<String, TreeMap<Integer, String>> LOOKUP = new TreeMap();
	
	
	/**
	 * Stores all of the RangeRules available in the system
	 */
	private static final ArrayList<RangeRules> RANGERULES;
	
	
	private static final DefaultRangeRules DEFAULTRR = new DefaultRangeRules();

	
	/**
	 * Light rain threshold in kg/(m^2*s)
	 */
	public static final double m_dLIGHTRAIN;

	
	/**
	 * Medium rain threshold in kg/(m^2*s)
	 */
	public static final double m_dMEDIUMRAIN;

	
	/**
	 * light snow threshold in kg/(m^2*s)
	 */
	public static final double m_dLIGHTSNOW;

	
	/**
	 * medium snow threshold in kg/(m^2*s)
	 */
	public static final double m_dMEDIUMSNOW;
	
	
	/**
	 * temperature for rain threshold in C
	 */
	public static final double m_dRAINTEMP;

	
	/**
	 * temperature for snow threshold in C
	 */
	public static final double m_dSNOWTEMP;

	
	/**
	 * light rain threshold in mm/hr
	 */
	public static final double m_dLIGHTRAINMMPERHR;

	
	/**
	 * medium rain threshold in mm/hr
	 */
	public static final double m_dMEDIUMRAINMMPERHR;

	
	/**
	 * light snow threshold in mm/hr
	 */
	public static final double m_dLIGHTSNOWMMPERHR;

	
	/**
	 * medium snow threshold in mm/hr
	 */
	public static final double m_dMEDIUMSNOWMMPERHR;
	
	
	/**
	 * Initializes configured values, rangerules, and enumerated values
	 */
	static
	{
		JSONObject oConfig = Directory.getInstance().getConfig(ObsType.class.getName(), "ObsType");
		m_dLIGHTRAIN = oConfig.optDouble("lrain", 0.0007055556);
		m_dMEDIUMRAIN = oConfig.optDouble("mrain", 0.0021166667);
		m_dLIGHTSNOW = oConfig.optDouble("lsnow", 0.0000705556);
		m_dMEDIUMSNOW = oConfig.optDouble("msnow", 0.0007055556);
		m_dRAINTEMP = oConfig.optDouble("raintemp", 2); // in C
		m_dSNOWTEMP = oConfig.optDouble("snowtemp", -2); // in C
		m_dLIGHTRAINMMPERHR = oConfig.optDouble("lightrainmm", 2.6); // mm/hr
		m_dMEDIUMRAINMMPERHR = oConfig.optDouble("medrainmm", 7.6); // mm/hr
		m_dLIGHTSNOWMMPERHR = oConfig.optDouble("lightsnowmm", 0.26); // mm/hr
		m_dMEDIUMSNOWMMPERHR = oConfig.optDouble("medsnowmm", 0.76); // mm/hr

		String[] sRangeRuleObs = JSONUtil.getStringArray(oConfig, "rules");
		RANGERULES = new ArrayList();
		Units oUnits = Units.getInstance();
		for (int i = 0; i < sRangeRuleObs.length; i++)
		{
			JSONObject oRRConfig = Directory.getInstance().getConfig(RangeRules.class.getName(), "rangerules_" + sRangeRuleObs[i]);
			RangeRules oOriginal = new RangeRules(oRRConfig, Integer.valueOf(sRangeRuleObs[i], 36));
			RANGERULES.add(oOriginal);
			String[] sConvertUnits = JSONUtil.getStringArray(oRRConfig, "convert");
			for (String sConvertTo : sConvertUnits)
				RANGERULES.add(new RangeRules(oOriginal, sConvertTo));
		}
		Introsort.usort(RANGERULES);


		TreeMap<Integer, String> oTemp = new TreeMap(); // NTCIP 1204 surface condition
		oTemp.put(1, "other");
		oTemp.put(2, "error");
		oTemp.put(3, "dry");
		oTemp.put(4, "trace-moisture");
		oTemp.put(5, "wet");
		oTemp.put(6, "chemically-wet");
		oTemp.put(7, "ice-warning");
		oTemp.put(8, "ice-watch");
		oTemp.put(9, "snow-warning");
		oTemp.put(10, "snow-watch");
		oTemp.put(11, "absorption");
		oTemp.put(12, "dew");
		oTemp.put(13, "frost");
		oTemp.put(14, "absorption-at-dewpoint");
		oTemp.put(20, "ice/snow"); // METRo Road conditions
		oTemp.put(21, "slush");
		oTemp.put(22, "melting-snow");
		oTemp.put(23, "icing-rain");
		oTemp.put(30, "flooded"); // imrcp road condition
		oTemp.put(31, "plowed");
		LOOKUP.put("STPVT", oTemp);

		oTemp = new TreeMap();
		oTemp.put(0, "no-precipitation");
		oTemp.put(1, "light-rain");
		oTemp.put(2, "moderate-rain");
		oTemp.put(3, "heavy-rain");
		oTemp.put(4, "light-freezing-rain");
		oTemp.put(5, "moderate-freezing-rain");
		oTemp.put(6, "heavy-freezing-rain");
		oTemp.put(7, "light-snow");
		oTemp.put(8, "moderate-snow");
		oTemp.put(9, "heavy-snow");
		oTemp.put(10, "light-ice");
		oTemp.put(11, "moderate-ice");
		oTemp.put(12, "heavy-ice");
		oTemp.put(101, "other");
		oTemp.put(102, "unknown");
		oTemp.put(104, "light-unidentified");
		oTemp.put(105, "moderate-unidentified");
		oTemp.put(106, "heavy-unidentified");
		LOOKUP.put("PCCAT", oTemp);

		oTemp = new TreeMap(); // RAP categorical precipitation types
		oTemp.put(0, "none");
		oTemp.put(1, "rain");
		oTemp.put(2, "snow");
		oTemp.put(3, "ice-pellets");
		oTemp.put(4, "freezing-rain");
		oTemp.put(5, "other");
		oTemp.put(6, "unknown");
		LOOKUP.put("TYPPC", oTemp);

		oTemp = new TreeMap();
		oTemp.put(1, "very-low");
		oTemp.put(2, "low");
		oTemp.put(3, "high");
		oTemp.put(4, "very-high");
		LOOKUP.put("FLWCAT", oTemp);

		oTemp = new TreeMap();
		oTemp.put(1, "low");
		oTemp.put(2, "medium-low");
		oTemp.put(3, "medium");
		oTemp.put(4, "medium-high");
		oTemp.put(5, "high");
		LOOKUP.put("SPDCAT", oTemp);

		oTemp = new TreeMap();
		oTemp.put(1, "very-low");
		oTemp.put(2, "low");
		oTemp.put(3, "high");
		oTemp.put(4, "very-high");
		LOOKUP.put("OCCCAT", oTemp);
		
		oTemp = new TreeMap();
		oTemp.put(0, "not-defined");
		oTemp.put(1, "no-action");
		oTemp.put(2, "action");
		oTemp.put(3, "flood");
		oTemp.put(4, "moderate");
		oTemp.put(5, "major");
		LOOKUP.put("STG", oTemp);

		oTemp = new TreeMap(); // notifications (match corresponding alerts)
		oTemp.put(101, "light-winter-precip"); // imrcp alert types 100s - areal weather, 200s - road weather, 300s - traffic
		oTemp.put(102, "moderate-winter-precip");
		oTemp.put(103, "heavy-winter-precip");
		oTemp.put(104, "light-precip");
		oTemp.put(105, "moderate-precip");
		oTemp.put(106, "heavy-precip");
		oTemp.put(107, "low-visibility");
		oTemp.put(108, "flood-stage-action");
		oTemp.put(109, "flood-stage-flood");
		oTemp.put(201, "dew-on-roadway");
		oTemp.put(202, "frost-on-roadway");
		oTemp.put(203, "blowing-snow");
		oTemp.put(204, "icy-roadway");
		oTemp.put(301, "incident");
		oTemp.put(302, "workzone");
		oTemp.put(303, "slow-traffic");
		oTemp.put(304, "very-slow-traffic");
		oTemp.put(305, "flooded-road");
		oTemp.put(306, "lengthy-queue");
		oTemp.put(307, "unusual-congestion");
		oTemp.put(399, "test");
		LOOKUP.put("NOTIFY", oTemp);

		oTemp = new TreeMap(); // TMDD PavementConditions
		oTemp.put(5888, "impassable");
		oTemp.put(5889, "almost-impassable");
		oTemp.put(5890, "passable-with-care");
		oTemp.put(5891, "passable");
		oTemp.put(5892, "surface-water-hazard");
		oTemp.put(5893, "danger-of-hydroplaning");
		oTemp.put(5894, "wet-pavement");
		oTemp.put(5895, "treated-pavement");
		oTemp.put(5896, "slippery");
		oTemp.put(5897, "low-ground-clearance");
		oTemp.put(5898, "at-grade-level-crossing");
		oTemp.put(5899, "mud-on-roadway");
		oTemp.put(5900, "leaves-on-roadway");
		oTemp.put(5901, "loose-sand-on-roadway");
		oTemp.put(5902, "loose-gravel");
		oTemp.put(5903, "fuel-on-roadway");
		oTemp.put(5904, "oil-on-roadway");
		oTemp.put(5905, "road-surface-in-poor-condition");
		oTemp.put(5906, "melting-tar");
		oTemp.put(5907, "uneven-lanes");
		oTemp.put(5908, "rough-road");
		oTemp.put(5909, "rough-crossing");
		oTemp.put(5910, "ice");
		oTemp.put(5911, "icy-patches");
		oTemp.put(5912, "black-ice");
		oTemp.put(5913, "ice-pellets-on-roadway");
		oTemp.put(5914, "ice-build-up");
		oTemp.put(5915, "freezing-rain");
		oTemp.put(5916, "wet-and-icy-roads");
		oTemp.put(5917, "melting-snow");
		oTemp.put(5918, "slush");
		oTemp.put(5919, "frozen-slush");
		oTemp.put(5920, "snow-on-roadway");
		oTemp.put(5921, "packed-snow");
		oTemp.put(5922, "packed-snow-patches");
		oTemp.put(5923, "plowed-snow");
		oTemp.put(5924, "wet-snow");
		oTemp.put(5925, "fresh-snow");
		oTemp.put(5926, "powder-snow");
		oTemp.put(5927, "granular-snow");
		oTemp.put(5928, "froazen-snow");
		oTemp.put(5929, "crusted-snow");
		oTemp.put(5930, "deep-snow");
		oTemp.put(5931, "snow-drifts");
		oTemp.put(5932, "drifting-snow");
		oTemp.put(5933, "expected-snow-accumulation");
		oTemp.put(5934, "current-snow-accumulation");
		oTemp.put(5935, "sand");
		oTemp.put(5936, "gravel");
		oTemp.put(5937, "paved");
		oTemp.put(5938, "dry-pavement");
		oTemp.put(5939, "snow-cleared");
		oTemp.put(5940, "pavement-conditions-improved");
		oTemp.put(5941, "skid-hazard-reduced");
		oTemp.put(5942, "pavement-conditions-cleared");

		oTemp.put(512, "accident"); // TMDD AccidentsAndIncidents
		oTemp.put(513, "serious-accident");
		oTemp.put(514, "injury-accident");
		oTemp.put(515, "minor-accident");
		oTemp.put(516, "multi-vehicle-accident");
		oTemp.put(517, "numerous-accidents");
		oTemp.put(518, "accident-involving-a-bicycle");
		oTemp.put(519, "accident-involving-a-bus");
		oTemp.put(520, "accident-involving-a-motorcycle");
		oTemp.put(521, "accident-involving-a-pedestrian");
		oTemp.put(522, "accident-involving-a-train");
		oTemp.put(523, "accident-involving-a-truck");
		oTemp.put(524, "accident-involving-a-semi-trailer");
		oTemp.put(525, "accident-involving-a-hazardous-materials");
		oTemp.put(526, "earlier-accident");
		oTemp.put(527, "medical-emergency");
		oTemp.put(528, "secondary-accident");
		oTemp.put(529, "rescue-and-recovery-work-removed");
		oTemp.put(530, "accident-investigation-work");
		oTemp.put(531, "incident");
		oTemp.put(532, "stalled-vehicle");
		oTemp.put(533, "abandoned-vehicle");
		oTemp.put(534, "disabled-vehicle");
		oTemp.put(535, "disabled-truck");
		oTemp.put(536, "disabled-semi-trailer");
		oTemp.put(537, "disabled-bus");
		oTemp.put(538, "disabled-train");
		oTemp.put(539, "vehicle-spun-out");
		oTemp.put(540, "vehicle-on-fire");
		oTemp.put(541, "vehicle-in-water");
		oTemp.put(542, "vehicles-slowing-to-look-at-accident");
		oTemp.put(543, "jackknifed-semi-trailer");
		oTemp.put(544, "jackknifed-trailer-home");
		oTemp.put(545, "jackknifed-trailer");
		oTemp.put(546, "spillage-occurring-from-moving-vehicle");
		oTemp.put(547, "acid-spill");
		oTemp.put(548, "chemical-spill");
		oTemp.put(549, "fuel-spill");
		oTemp.put(550, "hazardous-materials-spill");
		oTemp.put(551, "oil-spill");
		oTemp.put(552, "spilled-load");
		oTemp.put(553, "toxic-spill");
		oTemp.put(554, "overturned-vehicle");
		oTemp.put(555, "overturned-truck");
		oTemp.put(556, "overturned-semi-trailer");
		oTemp.put(557, "overturned-bus");
		oTemp.put(558, "derailed-train");
		oTemp.put(559, "stuck-vehicle");
		oTemp.put(560, "truck-stuck-under-bridge");
		oTemp.put(561, "bus-stuck-under-bridge");
		oTemp.put(562, "accident-cleared");
		oTemp.put(563, "incident-cleared");

		oTemp.put(101, "light-winter-precip"); // imrcp alert types 100s - areal weather, 200s - road weather, 300s - traffic
		oTemp.put(102, "moderate-winter-precip");
		oTemp.put(103, "heavy-winter-precip");
		oTemp.put(104, "light-precip");
		oTemp.put(105, "moderate-precip");
		oTemp.put(106, "heavy-precip");
		oTemp.put(107, "low-visibility");
		oTemp.put(108, "flood-stage-action");
		oTemp.put(109, "flood-stage-flood");
		oTemp.put(201, "dew-on-roadway");
		oTemp.put(202, "frost-on-roadway");
		oTemp.put(203, "blowing-snow");
		oTemp.put(204, "icy-roadway");
		oTemp.put(301, "incident");
		oTemp.put(302, "workzone");
		oTemp.put(303, "slow-traffic");
		oTemp.put(304, "very-slow-traffic");
		oTemp.put(305, "flooded-road");
		oTemp.put(306, "lengthy-queue");
		oTemp.put(307, "unusual-congestion");
		oTemp.put(399, "test");
		// NWS Cap alert types 1000s
		oTemp.put(1000, "Extreme Fire Danger"); // fire
		oTemp.put(1001, "Fire Warning");
		oTemp.put(1002, "Fire Weather Watch");
		oTemp.put(1003, "Red Flag Warning");
		oTemp.put(1004, "Heat Advisory"); // heat
		oTemp.put(1005, "Excessive Heat Warning");
		oTemp.put(1006, "Excessive Heat Watch");
		oTemp.put(1007, "Severe Thunderstorm Warning"); //storm/tornado
		oTemp.put(1008, "Severe Thunderstorm Watch");
		oTemp.put(1009, "Storm Warning");
		oTemp.put(1010, "Storm Watch");
		oTemp.put(1011, "Tornado Warning");
		oTemp.put(1012, "Tornado Watch");
		oTemp.put(1013, "Severe Weather Statement");
		oTemp.put(1014, "High Wind Warning"); // wind/fog/smoke
		oTemp.put(1015, "High Wind Watch");
		oTemp.put(1016, "Wind Advisory");
		oTemp.put(1017, "Extreme Wind Warning");
		oTemp.put(1018, "Brisk Wind Advisory");
		oTemp.put(1019, "Blowing Dust Advisory");
		oTemp.put(1020, "Dust Storm Warning");
		oTemp.put(1021, "Dense Fog Advisory");
		oTemp.put(1022, "Dense Smoke Advisory");
		oTemp.put(1023, "Air Quality Alert"); // air quality
		oTemp.put(1024, "Air Stagnation Advisory");
		oTemp.put(1025, "Ashfall Advisory"); // earthquake/volcano
		oTemp.put(1026, "Ashfall Warning");
		oTemp.put(1027, "Earthquake Warning");
		oTemp.put(1028, "Volcano Warning");
		oTemp.put(1029, "Winter Storm Warning"); //winter storm
		oTemp.put(1030, "Winter Storm Watch");
		oTemp.put(1031, "Winter Weather Advisory");
		oTemp.put(1032, "Ice Storm Warning");
		oTemp.put(1033, "Blizzard Warning");
		oTemp.put(1034, "Blizzard Watch");
		oTemp.put(1035, "Avalanche Warning");
		oTemp.put(1036, "Avalanche Watch");
		oTemp.put(1037, "Blowing Snow Advisory");
		oTemp.put(1038, "Snow and Blowing Snow Advisory");
		oTemp.put(1039, "Heavy Snow Warning");
		oTemp.put(1040, "Sleet Advisory");
		oTemp.put(1041, "Sleet Warning");
		oTemp.put(1042, "Snow Advisory");
		oTemp.put(1043, "Freeze Warning"); // freeze
		oTemp.put(1044, "Freeze Watch");
		oTemp.put(1045, "Freezing Drizzle Advisory");
		oTemp.put(1046, "Freezing Fog Advisory");
		oTemp.put(1047, "Freezing Rain Advisory");
		oTemp.put(1048, "Freezing Spray Advisory");
		oTemp.put(1049, "Frost Advisory");
		oTemp.put(1050, "Hard Freeze Warning");
		oTemp.put(1051, "Hard Freeze Watch");
		oTemp.put(1052, "Wind Chill Advisory"); // cold
		oTemp.put(1053, "Wind Chill Warning");
		oTemp.put(1054, "Wind Chill Watch");
		oTemp.put(1055, "Extreme Cold Warning");
		oTemp.put(1056, "Extreme Cold Watch");
		oTemp.put(1057, "Flash Flood Statement"); // flood
		oTemp.put(1058, "Flash Flood Warning");
		oTemp.put(1059, "Flash Flood Watch");
		oTemp.put(1060, "Flood Advisory");
		oTemp.put(1061, "Flood Statement");
		oTemp.put(1062, "Flood Warning");
		oTemp.put(1063, "Flood Watch");
		oTemp.put(1064, "Hydrologic Advisory");
		oTemp.put(1065, "Hydrologic Outlook");
		oTemp.put(1066, "Beach Hazards Statement"); // lake/marine/coastal
		oTemp.put(1067, "Coastal Flood Advisory");
		oTemp.put(1068, "Coastal Flood Statement");
		oTemp.put(1069, "Coastal Flood Warning");
		oTemp.put(1070, "Coastal Flood Watch");
		oTemp.put(1071, "Gale Warning");
		oTemp.put(1072, "Gale Watch");
		oTemp.put(1073, "Hazardous Seas Warning");
		oTemp.put(1074, "Hazardous Seas Watch");
		oTemp.put(1075, "Heavy Freezing Spray Warning");
		oTemp.put(1076, "Heavy Freezing Spray Watch");
		oTemp.put(1077, "High Surf Advisory");
		oTemp.put(1078, "High Surf Warning");
		oTemp.put(1079, "Lake Effect Snow Advisory");
		oTemp.put(1080, "Lake Effect Snow and Blowing Snow Advisory");
		oTemp.put(1081, "Lake Effect Snow Warning");
		oTemp.put(1082, "Lake Effect Snow Watch");
		oTemp.put(1083, "Lakeshore Flood Advisory");
		oTemp.put(1084, "Lakeshore Flood Statement");
		oTemp.put(1085, "Lakeshore Flood Warning");
		oTemp.put(1086, "Lakeshore Flood Watch");
		oTemp.put(1087, "Lake Wind Advisory");
		oTemp.put(1088, "Low Water Advisory");
		oTemp.put(1089, "Marine Weather Statement");
		oTemp.put(1090, "Rip Current Statement");
		oTemp.put(1091, "Small Craft Advisory");
		oTemp.put(1092, "Special Marine Warning");
		oTemp.put(1093, "Tsunami Advisory");
		oTemp.put(1094, "Tsunami Warning");
		oTemp.put(1095, "Tsunami Watch");
		oTemp.put(1096, "Hurricane Force Wind Warning"); // tropical storm
		oTemp.put(1097, "Hurricane Force Wind Watch");
		oTemp.put(1098, "Hurricane Statement");
		oTemp.put(1099, "Hurricane Warning");
		oTemp.put(1100, "Hurricane Watch");
		oTemp.put(1101, "Hurricane Wind Warning");
		oTemp.put(1102, "Hurricane Wind Watch");
		oTemp.put(1103, "Tropical Storm Warning");
		oTemp.put(1104, "Tropical Storm Watch");
		oTemp.put(1105, "Tropical Storm Wind Warning");
		oTemp.put(1106, "Tropical Storm Wind Watch");
		oTemp.put(1107, "Typhoon Statement");
		oTemp.put(1108, "Typhoon Warning");
		oTemp.put(1109, "Typhoon Watch");
		oTemp.put(1110, "Hazardous Weather Outlook"); // special weather
		oTemp.put(1111, "Special Weather Statement");
		oTemp.put(1112, "911 Telephone Outage"); // other
		oTemp.put(1113, "Administrative Message");
		oTemp.put(1114, "Child Abduction Emergency");
		oTemp.put(1115, "Civil Danger Warning");
		oTemp.put(1116, "Civil Emergency Message");
		oTemp.put(1117, "Evacuation Immediate");
		oTemp.put(1118, "Hazardous Materials Warning");
		oTemp.put(1119, "Law Enforcement Warning");
		oTemp.put(1120, "Local Area Emergency");
		oTemp.put(1121, "Nuclear Power Plant Warning");
		oTemp.put(1122, "Radiological Hazard Warning");
		oTemp.put(1123, "Shelter In Place Warning");
		oTemp.put(1124, "Test");

		LOOKUP.put("EVT", oTemp);
		
		oTemp = new TreeMap(); // NHC storm classifications
//		oTemp.put(Integer.valueOf("DB", 36), "Tropical Depression");
		oTemp.put(Integer.valueOf("HU", 36), "Hurricane");
		oTemp.put(Integer.valueOf("MH", 36), "Major Hurricane");
//		oTemp.put(Integer.valueOf("PT", 36), "Tropical Depression");
//		oTemp.put(Integer.valueOf("PTC", 36), "Post-tropical Cyclone Remnants Of");
//		oTemp.put(Integer.valueOf("SD", 36), "Tropical Storm");
//		oTemp.put(Integer.valueOf("SS", 36), "Tropical Storm");
		oTemp.put(Integer.valueOf("STD", 36), "Subtropical Depression");
		oTemp.put(Integer.valueOf("STS", 36), "Subtropical Storm");
		oTemp.put(Integer.valueOf("TD", 36), "Tropical Depression");
		oTemp.put(Integer.valueOf("TS", 36), "Tropical Storm");
		
		LOOKUP.put("TRSCAT", oTemp);
		LOOKUP.put("TRSTRK", oTemp);
		LOOKUP.put("TRSCNE", oTemp);
		
		oTemp = new TreeMap(); // plow flag
		oTemp.put(0, "Plow up");
		oTemp.put(1, "Plow down");
		
		LOOKUP.put("MPLOW", oTemp);
		LOOKUP.put("WPLOW", oTemp);
		LOOKUP.put("TPLOW", oTemp);
	}

	
	/**
	 * Default constructor. Does nothing.
	 */
	private ObsType()
	{
	}

	
	/**
	 * Gets the index of the given IMRCP observation type id in {@link #TYPE_MAP}
	 * 
	 * @param nObsTypeId IMRCP observation type id
	 * @return index of the IMRCP observation in {@link #TYPE_MAP} if it is found,
	 * otherwise 0.
	 */
	private static int getIndex(int nObsTypeId)
	{
		int nIndex = TYPE_MAP.length;
		while (nIndex-- > 0)
		{
			if (TYPE_MAP[nIndex] == nObsTypeId)
				return nIndex;
		}
		return 0;
	}

	
	/**
	 * Gets the name of the given IMRCP observation type id.
	 * 
	 * @param nObsTypeId IMRCP observation type id
	 * @return Name associated with the IMRCP observation type id
	 */
	public static String getName(int nObsTypeId)
	{
		return OBS_TYPES[getIndex(nObsTypeId)][0];
	}

	
	/**
	 * Gets the English units of the given IMRCP observation type id.
	 * 
	 * @param nObsTypeId IMRCP observation type
	 * @return English units associated with the IMRCP observation type id
	 */
	public static String getUnits(int nObsTypeId)
	{
		return getUnits(nObsTypeId, false);
	}

	
	/**
	 * Get the English or metric units of the given IMRCP observation type id
	 * based on the metric flag.
	 * 
	 * @param nObsTypeId IMRCP observation type id
	 * @param bMetric Flag indicating the metric or English units should be returned.
	 * true = metric, false = English
	 * @return If bMetric is true, the metric units associated with the IMRCP 
	 * observation type id, otherwise the English units associated with the
	 * IMRCP observation type id.
	 */
	public static String getUnits(int nObsTypeId, boolean bMetric)
	{
		if (bMetric)
			return OBS_TYPES[getIndex(nObsTypeId)][2];
		else
			return OBS_TYPES[getIndex(nObsTypeId)][1];
	}

	
	/**
	 * Get the description of the given IMRCP observation type id.
	 * 
	 * @param nObsTypeId IMRCP observation type id
	 * @return the description associated with the IMRCP observation type id
	 */
	public static String getDescription(int nObsTypeId)
	{
		return OBS_TYPES[getIndex(nObsTypeId)][3];
	}
	
	
	public static String getLabel(int nObsTypeId)
	{
		return OBS_TYPES[getIndex(nObsTypeId)][5];
	}

	
	/**
	 * Tells if the given IMRCP observation type id has enumerated values to lookup.
	 * 
	 * @param nObstypeId IMRCP observation type id
	 * @return true if the IMRCP observation type id has an entry in {@link #LOOKUP},
	 * otherwise false.
	 */
	public static boolean hasLookup(int nObstypeId)
	{
		return LOOKUP.containsKey(getName(nObstypeId));
	}

	
	/**
	 * Gets the String lookup value of the given value of the given IMRCP 
	 * observation type id
	 * 
	 * @param nObsTypeId IMRCP observation type
	 * @param nValue enumerated value to look up
	 * @return String associated with the value of the IMRCP observation type
	 * id. If the observation type does not have a look up for enumerated values,
	 * an empty string is returned
	 */
	public static String lookup(int nObsTypeId, int nValue)
	{
		TreeMap<Integer, String> oMap = LOOKUP.get(getName(nObsTypeId));
		if (oMap != null)
			return oMap.get(nValue);

		return "";
	}

	
	/**
	 * Gets the enumerated value of the given look up String of the given IMRCP
	 * observation type id
	 * 
	 * @param nObsTypeId IMRCP observation type id
	 * @param sValue look up String to get the enumerated value of
	 * @return the enumerated value associated with the look up String for the 
	 * IMRCP observation type id. If an enumerated value is not associated with
	 * the String, {@code Integer.MIN_VALUE} is returned
	 */
	public static int lookup(int nObsTypeId, String sValue)
	{
		TreeMap<Integer, String> oMap = LOOKUP.get(getName(nObsTypeId));
		if (oMap != null)
		{
			Iterator<Map.Entry<Integer, String>> oIt = oMap.entrySet().iterator();
			while (oIt.hasNext())
			{
				Map.Entry<Integer, String> oEntry = oIt.next();
				if (oEntry.getValue().compareTo(sValue) == 0)
					return oEntry.getKey();
			}
		}
		return Integer.MIN_VALUE;
	}
	
	
	/**
	 * Tells if the given IMRCP observation type id is currently in use by the
	 * system.
	 * 
	 * @param nObsTypeId IMRCP observation type id
	 * @return true if the IMRCP observation type id is in use by the system,
	 * otherwise false.
	 */
	public static boolean isInUse(int nObsTypeId)
	{
		return OBS_TYPES[getIndex(nObsTypeId)][4].compareTo("y") == 0;
	}
	
	
	/**
	 * Gets the RangeRules associated with the given IMRCP observation type id
	 * 
	 * @param nObsType IMRCP observation type id
	 * @return The RangeRules associated with the IMRCP observation type id if
	 * it exists, otherwise null.
	 */
	public static RangeRules getRangeRules(int nObsType, String sUnits)
	{
		RangeRules oSearch = new RangeRules(nObsType, sUnits);
		int nIndex = Collections.binarySearch(RANGERULES, oSearch);
		if (nIndex >= 0)
			return RANGERULES.get(nIndex);
		
		return DEFAULTRR;
	}
	
	
	/**
	 * Fills the given StringBuilder with a JSON representation of the enumerated
	 * values lookup map.
	 * 
	 * @param sBuf buffer to add the JSON representation of the look up map to
	 */
	public static void getJsonLookupValues(StringBuilder sBuf)
	{
		Iterator<Map.Entry<String, TreeMap<Integer, String>>> oIt = LOOKUP.entrySet().iterator();
		sBuf.append("{");
		while (oIt.hasNext())
		{
			Map.Entry<String, TreeMap<Integer, String>> oLookupMap = oIt.next();
			sBuf.append("\"").append(oLookupMap.getKey()).append("\":[");
			Iterator<Map.Entry<Integer, String>> oValues = oLookupMap.getValue().entrySet().iterator();
			while (oValues.hasNext())
			{
				Map.Entry<Integer, String> oLookupValue = oValues.next();
				sBuf.append("[\"").append(oLookupValue.getKey()).append("\",\"").append(oLookupValue.getValue()).append("\"],");
			}
			sBuf.setLength(sBuf.length() - 1);
			sBuf.append("],");
		}
		sBuf.setLength(sBuf.length() - 1);
		sBuf.append("}");
	}
	
	private static class DefaultRangeRules extends RangeRules
	{
		@Override
		public double groupValue(double dVal)
		{
			return dVal;
		}
		
		
		@Override
		public boolean shouldDelete(double dGroupValue)
		{
			return false;
		}
	}
}
