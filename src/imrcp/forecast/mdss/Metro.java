package imrcp.forecast.mdss;

import imrcp.system.BaseBlock;
import imrcp.system.FilenameFormatter;
import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.Mercator;
import imrcp.geosrv.Network;
import imrcp.geosrv.osm.OsmWay;
import imrcp.geosrv.WayNetworks;
import imrcp.store.Obs;
import imrcp.geosrv.ProjProfile;
import imrcp.system.CsvReader;
import imrcp.system.Directory;
import imrcp.system.Id;
import imrcp.system.Introsort;
import imrcp.system.ObsType;
import imrcp.system.ResourceRecord;
import imrcp.system.Scheduling;
import imrcp.system.Util;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import ucar.unidata.geoloc.ProjectionImpl;
import ucar.unidata.geoloc.projection.LambertConformal;
import ucar.unidata.geoloc.projection.LatLonProjection;
import imrcp.collect.TileFileWriter;
import imrcp.system.FileUtil;
import imrcp.system.XzBuffer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.channels.Channels;
import java.nio.file.Paths;
import java.util.Map.Entry;
import java.util.TimeZone;

/**
 * Manages running the METRo model in real-time or can be configured to process 
 * specific dates on demand for all the roadway segments in the system. A multi
 * threaded approach is used along with categorizing locations that share the 
 * same characteristics and weather forecasts to optimize performance
 * @author aaron.cherney
 */
public class Metro extends BaseBlock
{
	/**
	 * Number of hours of forecast data used as input for the METRo model.
	 */
	private int m_nForecastHours;

	
	/**
	 * Number of hours of observed data used as input for the METRo model
	 */
	private int m_nObsHours;

	
	/**
	 * Schedule offset from midnight in seconds
	 */
	private int m_nOffset;

	
	/**
	 * Period of execution in seconds
	 */
	private int m_nPeriod;
	
	
	/**
	 * Number of runs that can be processed per execution
	 */
	private int m_nRunsPerPeriod;
	
	
	/**
	 * Maximum number of times that can be queued at once
	 */
	private int m_nMaxQueue;
	
	
	/**
	 * Queue of times to process
	 */
	private final ArrayDeque<Long> m_oRunTimes = new ArrayDeque();
	
	
	/**
	 * File used to store timestamps that are queued
	 */
	private String m_sQueueFile;

	
	/**
	 * Flag indicating if this instance is processing real time data. If it is
	 * false times have to be queue to run on demand
	 */
	private boolean m_bRealTime;
	
	
	private int m_nThreads;
	
	
	private String m_sToken;
	
	
	/**
	 * Counts the total number of segments processed each period of execution
	 */
	private AtomicInteger m_nTotal = new AtomicInteger();

	
	/**
	 * Counts the number of times the METRo model is ran each period of execution
	 */
	private AtomicInteger m_nRuns = new AtomicInteger();

	
	/**
	 * Counts the number of times the METRo model fails to execute each period 
	 * of execution
	 */
	private AtomicInteger m_nFailed = new AtomicInteger();

	
	/**
	 * Compares int[]s by comparing values in corresponding positions. Used for 
	 * comparing {@link MetroLocation#m_nCategories}
	 */
	private Comparator<int[]> CATCOMP = (int[] o1, int[] o2) ->
	{
		for (int nIndex = 0; nIndex < o1.length; nIndex++)
		{
			int nComp = o1[nIndex] - o2[nIndex];
			if (nComp != 0)
				return nComp;
		}
		
		return 0;
	};

	
	/**
	 * Compares {@link MetroLocation}s by {@link MetroLocation#m_nCategories}. 
	 * Wrapper for {@link Metro#CATCOMP}
	 */
	private Comparator<MetroLocation> LOCBYARRAY = (MetroLocation o1, MetroLocation o2) -> CATCOMP.compare(o1.m_nCategories, o2.m_nCategories);

	
	/**
	 * Compares {@link MetroLocation}s by {@link MetroLocation#m_nCategory}.
	 */
	private Comparator<MetroLocation> LOCBYCAT = (MetroLocation o1, MetroLocation o2) -> o1.m_nCategory - o2.m_nCategory;

	
	/**
	 * Instance name of the {@link imrcp.geosrv.WayNetworks} block that serves
	 * the {@link imrcp.geosrv.osm.OsmWay}s and {@link imrcp.geosrv.osm.TileBucket}
	 * to process
	 */
	private String m_sWaysBlock;

	
	/**
	 * Format string used to generate file names for log files. Only used to debug
	 */
	private String m_sLogFf;

	
	/**
	 * Name of the file that if it exists during a period of execution triggers 
	 * log files to be created
	 */
	private String m_sLogTrigger;
	
	private ProjProfile[] m_oProjProfiles;
	
	private int m_nSourceId;
	
	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_nThreads = oBlockConfig.optInt("threads", 3);
		m_nForecastHours = oBlockConfig.optInt("fcsthrs", 6);
		m_nObsHours = oBlockConfig.optInt("obshrs", 6);
		m_nOffset = oBlockConfig.optInt("offset", 3300);
		m_nPeriod = oBlockConfig.optInt("period", 3600);
		m_nRunsPerPeriod = oBlockConfig.optInt("runs", 4);
		m_nMaxQueue = oBlockConfig.optInt("maxqueue", 504);
		m_sQueueFile = oBlockConfig.optString("queuefile", "metroqueue.txt");
		if (m_sQueueFile.startsWith("/"))
			m_sQueueFile.substring(1);
		m_sQueueFile = m_sArchPath + m_sQueueFile;
		m_bRealTime = oBlockConfig.optBoolean("realtime", true);
		m_sWaysBlock = oBlockConfig.optString("way", "WayNetworks");
		m_sLogFf = oBlockConfig.optString("logff", "");
		m_sLogTrigger = oBlockConfig.optString("logtrigger", "");
		m_sToken = oBlockConfig.optString("token", null);
		m_nSourceId = Integer.valueOf(oBlockConfig.optString("sourceid", "metro"), 36);
		JSONArray oProfiles = oBlockConfig.getJSONArray("projprofiles");
		m_oProjProfiles = new ProjProfile[oProfiles.length()];
		for (int nIndex = 0; nIndex < oProfiles.length(); nIndex++)
		{
			JSONObject oProfile = oProfiles.getJSONObject(nIndex);
			String sType = oProfile.optString("projtype", "");
			ProjectionImpl oProj;
			if (sType.compareTo("lambert_conformal") == 0)
				oProj = new LambertConformal(oProfile.getDouble("origin_lat"), oProfile.getDouble("origin_lon"), oProfile.getDouble("parallel_1"), oProfile.getDouble("parallel_2"), oProfile.getDouble("false_easting"), oProfile.getDouble("false_northing"));
			else
				oProj = new LatLonProjection();
			double dStartX = oProfile.getDouble("x0");
			double dEndX = oProfile.getDouble("xn");
			double dStartY = oProfile.getDouble("y0");
			double dEndY = oProfile.getDouble("yn");
			int nX = oProfile.getInt("x");
			int nY = oProfile.getInt("y");
			double dStepX = (dEndX - dStartX) / (nX - 1);
			double dStepY = (dEndY - dStartY) / (nY - 1);
			double[] dXs = new double[nX];
			double[] dYs = new double[nY];
			dXs[0] = dStartX;
			for (int nDimIndex = 1; nDimIndex < nX; nDimIndex++)
			{
				dStartX += dStepX;
				dXs[nDimIndex] = dStartX;
			}
			dYs[0] = dStartY;
			for (int nDimIndex = 1; nDimIndex < nY; nDimIndex++)
			{
				dStartY += dStepY;
				dYs[nDimIndex] = dStartY;
			}
			ProjProfile oProjProfile = new ProjProfile(dXs, dYs);
			oProjProfile.initGrid(dXs, dYs, oProj);
			m_oProjProfiles[nIndex] = oProjProfile;
		}
	}

	
	/**
	 * Queues any times found in the queue file. Then updates the tiles that will
	 * be processed each period of execution. Then sets a schedule to execute 
	 * this instance and {@link Metro#m_oTileUpdate} on a fixed interval.
	 * @return true if no exceptions are thrown
	 * @throws Exception
	 */
	@Override
	public boolean start() throws Exception
	{
		if (m_nForecastHours < 2)
		{
			m_oLogger.error("Must have at least 2 forecast hours");
			return false;
		}
		
		File oQueue = new File(m_sQueueFile);
		if (!oQueue.exists())
			oQueue.createNewFile();
		try (CsvReader oIn = new CsvReader(new FileInputStream(m_sQueueFile)))
		{
			while (oIn.readLine() > 0)
				m_oRunTimes.addLast(oIn.parseLong(0));
		}

		if (m_bRealTime)
			m_nSchedId = Scheduling.getInstance().createSched(this, m_nOffset, m_nPeriod);
		else if (!m_oRunTimes.isEmpty())
			Scheduling.getInstance().scheduleOnce(this, 60000);
		return true;
	}
	
	
	@Override
	public void doGet(HttpServletRequest oReq, HttpServletResponse oRes)
		throws ServletException, IOException
	{
		if (m_sToken == null)
			return;
		
		String[] sUriParts = oReq.getRequestURI().split("/");
		String sStart = sUriParts[sUriParts.length - 3];
		String sEnd = sUriParts[sUriParts.length - 2];
		String sToken = sUriParts[sUriParts.length - 1];
		
		if (m_sToken.compareTo(sToken) != 0)
			return;
		StringBuilder sBuf = new StringBuilder();
		queue(sStart, sEnd, sBuf);
		Scheduling.getInstance().scheduleOnce(this, 1000);
		try (PrintWriter oOut = oRes.getWriter())
		{
			oOut.append(sBuf);
		}
	}
	
	/**
	 * If this instance is configured to reprocess files, it parses the given
	 * start and end times and queues times within that range to be reprocessed.
	 * The given StringBuilder gets filled with basic html that contains the
	 * files that were queued.
	 * @param sStart start timestamp in the format yyyy-MM-ddTHH:mm 
	 * @param sEnd end timestamp in the format yyyy-MM-ddTHH:mm
	 * @param sBuffer Buffer that gets fills with html containing the queued files
	 */
	private void queue(String sStart, String sEnd, StringBuilder sBuffer)
	{
		try
		{
			SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
			oSdf.setTimeZone(Directory.m_oUTC);
			int nPeriodInMillis = 600 * 1000; // METRo is usually ran every 10 minutes
			long lStartTime = oSdf.parse(sStart).getTime();
			lStartTime = (lStartTime / nPeriodInMillis) * nPeriodInMillis;
			long lEndTime = oSdf.parse(sEnd).getTime();
			lEndTime = (lEndTime / nPeriodInMillis) * nPeriodInMillis;
			int nCount = 0;
			while (lStartTime <= lEndTime && nCount++ < m_nMaxQueue)
			{
				synchronized (m_oRunTimes)
				{
					m_oRunTimes.addLast(lStartTime);
				}
				lStartTime += nPeriodInMillis;
			}
			synchronized (m_oRunTimes)
			{
				try (BufferedWriter oOut = new BufferedWriter(new FileWriter(m_sQueueFile)))
				{
					Iterator<Long> oIt = m_oRunTimes.iterator();
					while (oIt.hasNext())
					{
						Long lTime = oIt.next();
						oOut.write(lTime.toString());
						oOut.write("\n");
						sBuffer.append(lTime.toString()).append("<br></br>");
					}
				}
			}
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
	
	
	/**
	 * Adds the current times in the queue to the StringBuilder in basic html
	 * @param sBuffer StringBuilder to fill with current files queued
	 */
	public void queueStatus(StringBuilder sBuffer)
	{
		synchronized (m_oRunTimes)
		{
			sBuffer.append(m_oRunTimes.size()).append(" times in queue");
			Iterator<Long> oIt = m_oRunTimes.iterator();
			while (oIt.hasNext())
			{
				Long lTime = oIt.next();
				sBuffer.append("<br></br>").append(lTime.toString());
			}
		}
	}
	
	
	/**
	 * If {@link Metro#m_bRealTime} is true, adds the current time to the front
	 * of the queue. Then processes up to {@link Metro#m_nRunsPerPeriod} times
	 * in the queue.
	 */
	@Override
	public void execute()
	{
		long lNow = System.currentTimeMillis();
		lNow = (lNow / 600000) * 600000;
		int nTimesToRun;
		synchronized (m_oRunTimes)
		{
			if (m_bRealTime)
				m_oRunTimes.addFirst(lNow);
			nTimesToRun = Math.min(m_nRunsPerPeriod, m_oRunTimes.size());
		}
		
		for (int i = 0; i < nTimesToRun; i++)
		{
			runMetro(m_oRunTimes.removeFirst());
		}
		
		synchronized (m_oRunTimes)
		{
			try (BufferedWriter oOut = new BufferedWriter(new FileWriter(m_sQueueFile))) // write the times since in the queue to disk
			{
				Iterator<Long> oIt = m_oRunTimes.iterator();
				while (oIt.hasNext())
				{
					oOut.write(oIt.next().toString());
					oOut.write("\n");
				}
			}
			catch (Exception oEx)
			{
				m_oLogger.error(oEx, oEx);
			}
			if (!m_oRunTimes.isEmpty()) // if there are still times in the queue, execute this instance again
				Scheduling.getInstance().scheduleOnce(this, 10);
		}
	}

	
	/**
	 * Runs the METRo model for the each tile in {@link Metro#m_oTiles} by 
	 * aggregating all of the input files (except previous METRo runs,
	 * which is done later by each thread) needed and then creating a 
	 * {@link java.util.concurrent.Callable} object for each {@link MetroTile} 
	 * and calling {@link java.util.concurrent.ExecutorService#invokeAll(java.util.Collection)}
	 * on {@link Metro#m_oThreadPool}.
	 * @param lRunTime Time in milliseconds since Epoch of the METRo run. The first
	 * forecast generated by the run is this time.
	 */
	public void runMetro(long lRunTime)
	{
		try
		{
			if (!DoMetroWrapper.g_bLibraryLoaded) // don't run if the shared library isn't loaded
				return;
			ArrayList<MetroTile> oTiles = getTiles();
			if (oTiles.isEmpty())
				return;
			m_oLogger.info("Running METRo for " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(lRunTime) + " for " + oTiles.size() + " tiles");
			m_nTotal.set(0);
			m_nRuns.set(0);
			m_nFailed.set(0);
			int[] nBB = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
			for (MetroTile oTile : oTiles)
			{
				oTile.m_lRunTime = lRunTime;
				if (oTile.m_nWaysBb[0] < nBB[0])
					nBB[0] = oTile.m_nWaysBb[0];
				if (oTile.m_nWaysBb[1] < nBB[1])
					nBB[1] = oTile.m_nWaysBb[1];
				if (oTile.m_nWaysBb[2] > nBB[2])
					nBB[2] = oTile.m_nWaysBb[2];
				if (oTile.m_nWaysBb[3] > nBB[3])
					nBB[3] = oTile.m_nWaysBb[3];
			}
			nBB[0] -= 1000;
			nBB[1] -= 1000;
			nBB[2] += 1000;
			nBB[3] += 1000;
			MetroObsSet oObsSet = new MetroObsSet(m_nObsHours, m_nForecastHours, m_nSourceId);
			oObsSet.getData(nBB, lRunTime);
			for (MetroTile oTile : oTiles)
				oTile.m_oAllObs = oObsSet;
			long[] lStartTimes = new long[getObservationCount(m_nForecastHours)];
			lStartTimes[0] = lRunTime - 3000000; // start 50 minutes before the run time which corresponds to time index 0 of the next metro run
			for (int nIndex = 1; nIndex < 55; nIndex++) // the first hour and 50 minutes of values are 2 minutes apart
				lStartTimes[nIndex] = lStartTimes[nIndex - 1] + 120000;
			if (lStartTimes.length > 55)
			{
				lStartTimes[55] = lRunTime + 3600000;
				int nTwentyMinLimit = 88; // 55 2 minute values and 33 20 minute values
				int nLimit = Math.min(nTwentyMinLimit, lStartTimes.length);
				for (int nIndex = 56; nIndex < nLimit; nIndex++) // the rest of the values are 20 minutes apart
					lStartTimes[nIndex] = lStartTimes[nIndex - 1] + 1200000;
				if (nTwentyMinLimit < lStartTimes.length)
					lStartTimes[nTwentyMinLimit] = lStartTimes[nTwentyMinLimit - 1] + 1200000;
				for (int nIndex = nTwentyMinLimit + 1; nIndex < lStartTimes.length; nIndex++)
					lStartTimes[nIndex] = lStartTimes[nIndex - 1] + 3600000;
			}
			
			Scheduling.processCallables(oTiles, m_nThreads);

			ResourceRecord oRR = Directory.getResource(Integer.valueOf("METRO", 36), ObsType.VARIES);
			FilenameFormatter oFf = new FilenameFormatter(oRR.getTiledFf());

			long lStart = lRunTime + oRR.getDelay();
			long lEnd = lStart + oRR.getRange();
			Path oTileFile = oRR.getFilename(lRunTime, lStart, lEnd, oFf);
			Files.createDirectories(oTileFile.getParent());
			try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(oTileFile))))
			{
				oOut.writeByte(1); // version
				oOut.writeInt(nBB[0]); // bounds min x
				oOut.writeInt(nBB[1]); // bounds min y
				oOut.writeInt(nBB[2]); // bounds max x
				oOut.writeInt(nBB[3]); // bounds max y
				oOut.writeInt(oRR.getObsTypeId()); // obsversation type
				oOut.writeByte(Util.combineNybbles(0, oRR.getValueType())); // obs flag = 0 (upper nybble) value type (lower nybble)
				oOut.writeByte(Obs.POINT); // geo type
				oOut.writeByte(0); // id format: -1=variable, 0=null, 16=uuid, 32=32-bytes
				int nObjAndTimes = Id.SEGMENT;
				nObjAndTimes <<= 4;
				oOut.writeByte(nObjAndTimes); // associate with obj and timestamp flag, first nybble is Id.SEGMENT the lower bytes are all zero since times for obs are found in header
				oOut.writeLong(lRunTime);
				oOut.writeInt((int)((lEnd - lRunTime) / 1000)); // end time offset from received time
				if (lStartTimes.length > Byte.MAX_VALUE)
					oOut.writeShort(-lStartTimes.length);
				else
					oOut.writeByte(lStartTimes.length);
				oOut.writeInt((int)((lStart - lRunTime) / 1000)); // start time offset from the received time for the first value
				for (int nIndex = 1; nIndex < lStartTimes.length; nIndex++) // the rest are offset from the previous value
					oOut.writeInt((int)((lStartTimes[nIndex] - lStartTimes[nIndex - 1]) / 1000 ));
				oOut.writeInt(0); // no string pool

				oOut.writeByte(oRR.getZoom()); // tile zoom level
				oOut.writeByte(oRR.getTileSize());
				oOut.writeInt(oTiles.size());
				for (MetroTile oTile : oTiles) // finish writing tile metadata
				{
					oOut.writeShort(oTile.m_nX);
					oOut.writeShort(oTile.m_nY);
					oOut.writeInt(oTile.m_yData.length);
				}

				for (MetroTile oTile : oTiles)
				{
					oOut.write(oTile.m_yData);
				}
			}
			m_oLogger.info(String.format("Finished METRo for %s. %d runs out of %d total. %d failed.", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(lRunTime), m_nRuns.get(), m_nTotal.get(), m_nFailed.get()));
		}
		catch (Exception oException)
		{
			m_oLogger.error(oException, oException);
		}
	}

	
	public static int getObservationCount(int nForecastHours)
	{
		int nTwoMin = 55;
		int nTwentyCount = Math.min(11, nForecastHours - 3);
		int nTwentyMin = nTwentyCount * 3;
		int nHour = nForecastHours - 14;
		if (nHour < 0)
			nHour = 0;
		return nTwoMin + nTwentyMin + nHour;
	}

	
	/**
	 * Encapsulates the necessary metadata used for running the METRo model
	 * at a specific location
	 */
	private class MetroLocation
	{
		/**
		 * Longitude of location in decimal degrees scaled to 7 decimal places
		 */
		private final int m_nLon;

		
		/**
		 * Latitude of location in decimal degrees scaled to 7 decimal places
		 */
		private final int m_nLat;

		
		/**
		 * Flag indicating if the location represent a roadway or a bridge deck
		 */
		private final boolean m_bBridge;

		
		/**
		 * Treatment type. 0 = no treatment, 1 = chemically treated
		 */
		private final int m_nTmtType;

		
		/**
		 * Number representing the category of this location, which is an index
		 * used in a list of outputs. The category is determined by 
		 * {@link MetroLocation#m_bBridge} and the indices of the location inside
		 * of the different data files.
		 */
		private int m_nCategory;

		
		/**
		 * Stores information to determine {@link MetroLocation#m_nCategory}.
		 * Format is [bridge flag, x index for file 1, y index for file 1, x index 
		 * for file 2, y index for file 2, ..., x index for file n, y index for 
		 * file n]
		 */
		private int[] m_nCategories;

		
		/**
		 * Constructs a MetroLocation with the given parameters
		 * @param nLon longitude of location in decimal degrees scaled to 7 decimal places
		 * @param nLat latitude of location in decimal degrees scaled to 7 decimal places
		 * @param bBridge true if bridge, otherwise false
		 * @param nTmtType 0 = no treatment, 1 = chemically treated
		 */
		MetroLocation(int nLon, int nLat, boolean bBridge, int nTmtType)
		{
			m_nLon = nLon;
			m_nLat = nLat;
			m_bBridge = bBridge;
			m_nTmtType = nTmtType;
		}
	}
	
	private ArrayList<MetroTile> getTiles()
	{
		WayNetworks oWays = (WayNetworks)Directory.getInstance().lookup(m_sWaysBlock);
		ArrayList<Network> oNetworks = oWays.getNetworks();
		Mercator oM = new Mercator();
		int[] nTile = new int[2];
		ArrayList<MetroTile> oTiles = new ArrayList();
		MetroTile oSearch = new MetroTile();
		ArrayList<ResourceRecord> oRRs = Directory.getResourcesByContribSource(Integer.valueOf("metro", 36), m_nSourceId);
		Introsort.usort(oRRs, ResourceRecord.COMP_BY_OBSTYPE_CONTRIB);
		int nZoom = oRRs.get(0).getZoom();
		for (Network oNetwork : oNetworks)
		{
			if (!oNetwork.m_bCanRunRoadWeather)
				continue;
			for (OsmWay oWay : oNetwork.m_oNetworkWays)
			{
				oM.lonLatToTile(GeoUtil.fromIntDeg(oWay.m_nMidLon), GeoUtil.fromIntDeg(oWay.m_nMidLat), nZoom, nTile);
				oSearch.m_nX = nTile[0];
				oSearch.m_nY = nTile[1];
				int nIndex = Collections.binarySearch(oTiles, oSearch);
				MetroTile oMTile = null;
				if (nIndex < 0) // process tiles once
				{
					oMTile = new MetroTile(nTile[0], nTile[1]);
					oTiles.add(~nIndex, oMTile);
					oMTile.m_nWaysBb = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
				}
				else
					oMTile = oTiles.get(nIndex);

				oMTile.m_oLocations.add(new MetroLocation(oWay.m_nMidLon, oWay.m_nMidLat, oWay.m_bBridge, 0));
				if (oWay.m_nMinLon < oMTile.m_nWaysBb[0])
					oMTile.m_nWaysBb[0] = oWay.m_nMinLon;
				if (oWay.m_nMinLat < oMTile.m_nWaysBb[1])
					oMTile.m_nWaysBb[1] = oWay.m_nMinLat;
				if (oWay.m_nMaxLon > oMTile.m_nWaysBb[2])
					oMTile.m_nWaysBb[2] = oWay.m_nMaxLon;
				if (oWay.m_nMaxLat > oMTile.m_nWaysBb[3])
					oMTile.m_nWaysBb[3] = oWay.m_nMaxLat;
				
				oMTile.m_nWaysBb[0] -= 1000;
				oMTile.m_nWaysBb[1] -= 1000;
				oMTile.m_nWaysBb[2] += 1000;
				oMTile.m_nWaysBb[3] += 1000;
			}
		}


		int nIndex = oTiles.size();
		while (nIndex-- > 0) // remove empty tiles
		{
			MetroTile oTile = oTiles.get(nIndex);
			if (oTile.m_oLocations.isEmpty())
				oTiles.remove(nIndex);
		}
		int[] nSearch = new int[m_oProjProfiles.length * 2 + 1];
		int[] nIndices = new int[2];

		for (MetroTile oTile : oTiles)
		{
			ArrayList<MetroLocation> oLocsByCategories = new ArrayList();
			for (MetroLocation oLoc : oTile.m_oLocations)
			{
				// determine the category for each location in the tile
				int nCatIndex = 0;
				nSearch[nCatIndex++] = oLoc.m_bBridge ? 1 : 0;
				for (ProjProfile oProj : m_oProjProfiles)
				{
					oProj.getPointIndices(GeoUtil.fromIntDeg(oLoc.m_nLon), GeoUtil.fromIntDeg(oLoc.m_nLat), nIndices);
					nSearch[nCatIndex++] = nIndices[0];
					nSearch[nCatIndex++] = nIndices[1];
				}
				oLoc.m_nCategories = nSearch;
				int nSearchIndex = Collections.binarySearch(oLocsByCategories, oLoc, LOCBYARRAY);
				if (nSearchIndex < 0) // add new categories to the list
				{
					int[] nTemp = new int[nSearch.length];
					System.arraycopy(nSearch, 0, nTemp, 0, nTemp.length);
					oLoc.m_nCategories = nTemp;
					oLocsByCategories.add(~nSearchIndex, oLoc);
				}
				else // if the category is already in the list, set the current location's category to it
				{
					oLoc.m_nCategories = oLocsByCategories.get(nSearchIndex).m_nCategories;
				}
			}
			for (MetroLocation oLoc : oTile.m_oLocations)
			{
				oLoc.m_nCategory = Collections.binarySearch(oLocsByCategories, oLoc, LOCBYARRAY);
			}
			for (MetroLocation oLoc : oTile.m_oLocations) // don't need the categories array any more so save memory by setting to null
				oLoc.m_nCategories = null;

			Introsort.usort(oTile.m_oLocations, LOCBYCAT);
			oTile.m_oRRs = oRRs;
		}
		
		return oTiles;
	}
	
	
	/**
	 * Represents a map tile with locations inside of it that will be inputs
	 * to the METRo model
	 */
	class MetroTile implements Comparable<MetroTile>, Callable<MetroTile>
	{
		/**
		 * Locations inside of the tile
		 */
		ArrayList<MetroLocation> m_oLocations;

		
		/**
		 * Current Metro run time 
		 */
		long m_lRunTime;

		
		/**
		 * tile's x index
		 */
		int m_nX;

		
		/**
		 * tile's y index
		 */
		int m_nY;
		
		
		int[] m_nWaysBb;
		
		ArrayList<ResourceRecord> m_oRRs;

		
		/**
		 * List that contains the outputs of METRo
		 */
		ArrayList<RoadcastData> m_oResult = new ArrayList();

		byte[] m_yData;
		private MetroObsSet m_oAllObs;
		
		/**
		 * Default constructor, which set {@link MetroTile#m_oLocations} to an
		 * empty {@link java.util.ArrayList}
		 */
		MetroTile()
		{	
			m_oLocations = new ArrayList();
		}

		
		/**
		 * Calls {@link MetroTile#MetroTile()} and sets the x and y index to the
		 * given values.
		 * @param nX tile's x index
		 * @param nY tile's y index
		 */
		MetroTile(int nX, int nY)
		{
			this();
			m_nX = nX;
			m_nY = nY;
		}

		
		/**
		 * Compares MetroTiles by x index, then y index.
		 * @param o the object to be compared.
		 * @return a negative integer, zero, or a positive integer as this 
		 * object is less than, equal to, or greater than the specified object.
		 * 
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		@Override
		public int compareTo(MetroTile o)
		{
			int nRet = m_nX - o.m_nX;
			if (nRet == 0)
				nRet = m_nY - o.m_nY;

			return nRet;
		}

		
		/**
		 * For each location in the tile, if a location with the same category
		 * has not been processed, a {@link imrcp.forecast.mdss.DoMetroWrapper} 
		 * is created and used to call the JNI methods to run the METRo model 
		 * via C and Fortran code, otherwise the location's output is set to
		 * the output already computed for that category.
		 */
		@Override
		public MetroTile call()
		{
			try
			{
				m_oResult.clear();
				RoadcastData[] oRoadcasts = new RoadcastData[m_oLocations.get(m_oLocations.size() - 1).m_nCategory + 1];
//				StringBuilder[] oLogs = new StringBuilder[oRoadcasts.length];
//				double[] dPoint = new double[2];
//				new Mercator().lonLatMdpt(m_nX, m_nY, m_oRRs.get(0).getZoom(), dPoint);
//				SimpleDateFormat oSdf = new SimpleDateFormat("yyyyMMdd");
//				oSdf.setTimeZone(Directory.m_oUTC);
//				WayNetworks oWays = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
//				FilenameFormatter oFf = null;
//				long lFileTime = (m_lRunTime / 86400000) * 86400000;
//				if (m_sLogTrigger != null && !m_sLogTrigger.isEmpty() && Files.exists(Paths.get(m_sLogTrigger)) &&  m_sLogFf != null && !m_sLogFf.isEmpty())
//				{
//					oFf = new FilenameFormatter(m_sLogFf);
//					Files.createDirectories(Paths.get(oFf.format(m_lRunTime, 0, 0, "")).getParent(), FileUtil.DIRPERS);
//				}

				int nPPT = (int)Math.pow(2, m_oRRs.get(0).getTileSize()) - 1;
				Mercator oM = new Mercator(nPPT);
				int nTol = (int)Math.round(oM.RES[m_oRRs.get(0).getZoom()] * 100); // meters per pixel * 100 
				MetroObsSet oMOS = new MetroObsSet(m_nObsHours, m_nForecastHours, m_nSourceId);
				oMOS.fillObsSet(m_oAllObs, m_nWaysBb);
				boolean bLogFail = true;
				for (MetroLocation oLoc : m_oLocations)
				{
					m_nTotal.incrementAndGet();
					if (oRoadcasts[oLoc.m_nCategory] == null)
					{
						DoMetroWrapper oDmw = new DoMetroWrapper(m_nObsHours, m_nForecastHours);
						boolean bFill = oDmw.fillArrays(oLoc.m_nLon, oLoc.m_nLat, oLoc.m_bBridge, oLoc.m_nTmtType, m_lRunTime, oMOS, nTol);
						if (bFill)
						{
							oDmw.run();
							oDmw.saveRoadcast(oLoc.m_nLon, oLoc.m_nLat, m_lRunTime);
							m_oResult.add(oDmw.m_oOutput);
							m_nRuns.incrementAndGet();
						}
						else
						{
							m_nFailed.incrementAndGet();
							if (bLogFail)
							{
								bLogFail = false;
								m_oLogger.debug("Failed at " + oLoc.m_nLon + " " + oLoc.m_nLat);
							}
						}
						oRoadcasts[oLoc.m_nCategory] = oDmw.m_oOutput;
//						oLogs[oLoc.m_nCategory] = oDmw.log(m_lRunTime);
					}
					else
					{
						m_oResult.add(new RoadcastData(oRoadcasts[oLoc.m_nCategory], oLoc.m_nLon, oLoc.m_nLat));
					}
//					if (oFf != null)
//					{
//						
//						OsmWay oWay = oWays.getWay(100, oLoc.m_nLon, oLoc.m_nLat);
//						if (oWay == null)
//							continue;
//						if (oLogs[oLoc.m_nCategory].length() == 0)
//							continue;
//						Path oPath = Paths.get(oFf.format(m_lRunTime, 0, 0, oWay.m_oId.toString()));
//						try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oPath, FileUtil.APPENDOPTS), "UTF-8")))
//						{
//							oOut.append(oLogs[oLoc.m_nCategory]);
//						}
//						
//					}
				}
				
				double[] dPixels = new double[2];
				ByteArrayOutputStream oRawStream = new ByteArrayOutputStream(8192);
				DataOutputStream oRawDataStream = new DataOutputStream(oRawStream);
				int nObsCount = getObservationCount(m_nForecastHours);
				int nZoom = m_oRRs.get(0).getZoom();
				for (RoadcastData oRD : m_oResult)
				{
					oM.lonLatToTilePixels(GeoUtil.fromIntDeg(oRD.m_nLon), GeoUtil.fromIntDeg(oRD.m_nLat), m_nX, m_nY, nZoom, dPixels);
					for (Entry<Integer, float[]> oEntry : oRD.m_oDataArrays.entrySet())
					{
						float[] fData = oEntry.getValue();
						int nObstype = oEntry.getKey();
						oRawDataStream.writeInt(nObstype);
						ResourceRecord oRR = null;
						for (ResourceRecord oTempRR : m_oRRs)
						{
							if (oTempRR.getObsTypeId() == nObstype)
							{
								oRR = oTempRR;
								break;
							}
						}
						oRawDataStream.writeShort((int)dPixels[0]);
						oRawDataStream.writeShort((int)dPixels[1]);
						for (int nTimeIndex = 0; nTimeIndex < nObsCount; nTimeIndex++)
						{
							if (fData.length > nTimeIndex)
								oRawDataStream.writeShort(Util.toHpfp((float)TileFileWriter.nearest(fData[nTimeIndex], oRR.getRound())));
						}
					}
				}
				m_yData = XzBuffer.compress(oRawStream.toByteArray());
			}
			catch (Exception oEx)
			{
				m_oLogger.error(oEx, oEx);
			}
			return this;
		}
	}
}
