/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.collect;

import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.Mercator;
import imrcp.store.Obs;
import imrcp.store.ObsList;
import imrcp.store.TileObsView;
import imrcp.system.Arrays;
import imrcp.system.Directory;
import imrcp.system.FilenameFormatter;
import imrcp.system.Introsort;
import imrcp.system.ObsType;
import imrcp.system.ResourceRecord;
import imrcp.system.Scheduling;
import imrcp.system.StringPool;
import imrcp.system.Units;
import imrcp.system.Units.UnitConv;
import imrcp.system.Util;
import imrcp.system.XzBuffer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.json.JSONObject;

/**
 * Collector used to download hurricane forecasts from the National Hurricane
 * Center.
 * @author aaron.cherney
 */
public class NHC extends Collector
{
	/**
	 * Threshold wind speed values in knots for the Saffir–Simpson hurricane wind scale
	 */
	private static final double[] SSHWS = new double[]{64.0, 83.0, 96.0, 113.0, 137.0}; // wind speeds in knots
	
	private static final double[] SSHWSMPERSEC = new double[]{32.924, 42.699, 49.387, 58.132, 70.479};
	
	/**
	 * Array of file names to download
	 */
	private static final String[] FILES = new String[]{"CONE", "TRACK", "WW"};
	
	/**
	 * Wind speed threshold for major hurricanes in mph
	 */
	private static final int MAJOR = 110;

	
	/**
	 * Wind speed threshold for hurricanes in mph
	 */
	private static final int HURRICANE = 74;

	
	/**
	 * Wind speed threshold for tropical storms in mph
	 */
	private static final int STORM = 39;
	
	/**
	 * Maps storm type abbreviations to their IMRCP integer value
	 */
	public static final HashMap<String, Integer> STORMTYPES = new HashMap();

	
	/**
	 * The year to start downloading hurricane forecasts upon system startup
	 */
	private int m_nStartYear;
	
	private int m_nPointsInCircle;
	
	
	/**
	 * Set the STORMTYPES mappings
	 */
	static
	{
		int nTropicalDepression = Integer.valueOf("TD", 36);
		int nTropicalStorm = Integer.valueOf("TS", 36);
		int nHurricane = Integer.valueOf("HU", 36);
		STORMTYPES.put("TD", nTropicalDepression);
		STORMTYPES.put("TS", nTropicalStorm);
		STORMTYPES.put("HU", nHurricane);
		STORMTYPES.put("DB", nTropicalDepression);
		STORMTYPES.put("MH", Integer.valueOf("MH", 36));
		STORMTYPES.put("PT", nTropicalDepression);
		STORMTYPES.put("PTC", nTropicalDepression);
		STORMTYPES.put("SD", nTropicalStorm);
		STORMTYPES.put("SS", nTropicalStorm);
		STORMTYPES.put("STD", Integer.valueOf("STD", 36));
		STORMTYPES.put("STS", Integer.valueOf("STS", 36));
	}
	
	/**
	 *
	 */
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_nStartYear = oBlockConfig.optInt("startyr", new GregorianCalendar(TimeZone.getTimeZone("UTC")).get(Calendar.YEAR));
		m_nPointsInCircle = oBlockConfig.optInt("circlepts", 24);
	}
	
	
	/**
	 * Calls {@link NHC#checkYear(int, boolean)} starting at 
	 * {@link NHC#m_nStartYear} and going until the current year. Then sets a
	 * schedule to execute on a fixed interval.
	 * @return true if no exceptions are thrown
	 * @throws Exception
	 */
	@Override
	public boolean start()
		throws Exception
	{
		int nCurYear = new GregorianCalendar(TimeZone.getTimeZone("UTC")).get(Calendar.YEAR);
		for (int nYear = m_nStartYear; nYear <= nCurYear; nYear++)
		{
			try
			{
				checkYear(nYear);
			}
			catch (Exception oEx)
			{
				m_oLogger.error(oEx, oEx);
			}
		}
		m_nSchedId = Scheduling.getInstance().createSched(this, m_nOffset, m_nPeriod);
		return true;
	}
	
	
	/**
	 * Wrapper for {@link NHC#checkYear(int, boolean)}. Calls it for the 
	 * current year.
	 */
	@Override
	public void execute()
	{
		try
		{
			checkYear(new GregorianCalendar(TimeZone.getTimeZone("UTC")).get(Calendar.YEAR));
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
	
	
	/**
	 * Check for and downloads any hurricane forecast in the NHC archive that is
	 * not stored on disk for the given year.
	 * @param nYear Year to check
	 * @throws Exception
	 */
	public void checkYear(int nYear) 
		throws Exception
	{
		ArrayList<ResourceRecord> oRRs = Directory.getResourcesByContrib(m_nContribId);
		FilenameFormatter oArchiveFf = new FilenameFormatter(oRRs.get(0).getArchiveFf());
		Path oBaseDir = Paths.get(oArchiveFf.format(0, 0, 0, "")).getParent().getParent();
		String sExt = oArchiveFf.getExtension();
		ArrayList<String> m_oFilesOnDisk = new ArrayList();
		TreeSet<Long> oDaysToProcess = new TreeSet();
		if (Files.exists(oBaseDir))
		{
			Files.walk(oBaseDir, FileVisitOption.FOLLOW_LINKS) // accumulate all the files on disk
				.filter(Files::isRegularFile)
				.filter(oPath -> oPath.toString().endsWith(sExt))
				.forEach(oPath -> 
				{
					String sFile = oPath.toString();
					int nStart = sFile.lastIndexOf("/");
					nStart = sFile.indexOf("_", nStart) + 1;
					int nEnd = sFile.lastIndexOf("_");
					nEnd = sFile.lastIndexOf("_", nEnd - 1);
					nEnd = sFile.lastIndexOf("_", nEnd - 1);
					m_oFilesOnDisk.add(sFile.substring(nStart, nEnd));
				});
		}
		Introsort.usort(m_oFilesOnDisk, (String o1, String o2) -> o1.compareTo(o2));
		int nByte;
		StringBuilder sBuf = new StringBuilder();

		String sUrl = String.format(this.m_oSrcFile.getPattern(), nYear);
		URL oIndexUrl = new URL(sUrl);
		URLConnection oIndexConn = oIndexUrl.openConnection();
		oIndexConn.setConnectTimeout(60000);
		oIndexConn.setReadTimeout(60000);
		try (BufferedInputStream oIn = new BufferedInputStream(oIndexConn.getInputStream())) // read the archive for the year
		{
			while ((nByte = oIn.read()) >= 0)
				sBuf.append((char)nByte);
		}
		
		ArrayList<String> sStormsToCheck = new ArrayList();
		int nStart = sBuf.indexOf("NHC Storm Identifier");
		int nLimit = sBuf.indexOf("</table>", nStart);
		nStart = sBuf.indexOf("<a href=\"", nStart);
		while (nStart >= 0 && nStart < nLimit) // determine all the available storms
		{
			nStart += "<a href=\"".length();
			sStormsToCheck.add(sBuf.substring(nStart, sBuf.indexOf("\">", nStart)));
			nStart = sBuf.indexOf("<a href=\"", nStart);
		}

		for (String sStormUrl : sStormsToCheck)
		{
			StringBuilder sStormBuf = new StringBuilder();
			URL oStormUrl = new URL((m_sBaseURL + sStormUrl).replace(" ", "%20"));
			URLConnection oConn = oStormUrl.openConnection();
			oConn.setConnectTimeout(60000);
			oConn.setReadTimeout(60000);
			try (BufferedInputStream oIn = new BufferedInputStream(oConn.getInputStream())) // read the index for the storm
			{
				while ((nByte = oIn.read()) >= 0)
					sStormBuf.append((char)nByte);
			}
			
			nStart = sStormBuf.indexOf("<a href=\"../storm_graphics/api/");
			ArrayList<String> sAdvs = new ArrayList();
			while (nStart >= 0) // determine all of the files for this storm
			{
				nStart += "<a href=\"../storm_graphics/api/".length();
				int nEnd = sStormBuf.indexOf(".kmz\">", nStart);
				nEnd = sStormBuf.lastIndexOf("_", nEnd);
				String sAdv = sStormBuf.substring(nStart, nEnd);
				int nIndex = Collections.binarySearch(sAdvs, sAdv);
				if (nIndex < 0)
					sAdvs.add(~nIndex, sAdv);
				nStart = sStormBuf.indexOf("<a href=\"../storm_graphics/api/", nStart);
			}
			
			for (String sAdv : sAdvs)
			{
				int nSearch = Collections.binarySearch(m_oFilesOnDisk, sAdv);
				if (nSearch >= 0) // ignore files that are already on disk
					continue;
				byte[][] yBuffers = new byte[FILES.length][];
				String[] sEntries = new String[FILES.length];
				m_oLogger.info(String.format("Downloading files for storm %s", sAdv));
				for (int nFileIndex = 0; nFileIndex < FILES.length; nFileIndex++) // download each file for the forecast interval
				{
					String sFile = String.format("%s_%s.kmz", sAdv, FILES[nFileIndex]);
					sEntries[nFileIndex] = sFile;
					URL oDownload = new URL(m_sBaseURL + "/storm_graphics/api/" + sFile);
					
					URLConnection oDlConn = oDownload.openConnection();
					oDlConn.setConnectTimeout(60000);
					oDlConn.setReadTimeout(60000);

					ByteArrayOutputStream oBaos = new ByteArrayOutputStream();
					try (BufferedInputStream oIn = new BufferedInputStream(oDlConn.getInputStream()))
					{
						while ((nByte = oIn.read()) >= 0)
							oBaos.write(nByte);
					}
					catch (Exception oEx) // WW file is not produced for every forecast
					{
						continue;
					}
					yBuffers[nFileIndex] = oBaos.toByteArray();
				}
				
				long lValid = Long.MIN_VALUE;
				if (yBuffers[0] == null || yBuffers[1] == null)
				{
					m_oLogger.info(String.format("Missing cone or track file on NHC server for %s", sAdv));
					continue;
				}
				try (ZipInputStream oIn = new ZipInputStream(new ByteArrayInputStream(yBuffers[0]))) // read the cone file to determine the date created
				{
					ZipEntry oZe;
					
					while ((oZe = oIn.getNextEntry()) != null)
					{
						if (!oZe.getName().endsWith(".kml"))
							continue;
						
						StringBuilder sCone = new StringBuilder((int)oZe.getSize());
						while ((nByte = oIn.read()) >= 0)
							sCone.append((char)nByte);
						
						nStart = sCone.indexOf("<TR><TD><B>Date Created:</B>") + "<TR><TD><B>Date Created:</B>".length();
						String sCreated = sCone.substring(nStart, sCone.indexOf("</TD></TR>", nStart)).trim();
						SimpleDateFormat oCreateSdf = new SimpleDateFormat("MM-dd-yyyy - HH:mm z");
						lValid = oCreateSdf.parse(sCreated).getTime();
					}
				}
				
				Path oArchive = Paths.get(oArchiveFf.format(lValid, lValid, lValid + oRRs.get(0).getRange(), sAdv));
				Files.createDirectories(oArchive.getParent());
				
				try (ZipOutputStream oOut = new ZipOutputStream(Files.newOutputStream(oArchive))) // make a zip file of all the files for the current storm and forecast interval
				{
					for (int nFileIndex = 0 ; nFileIndex < yBuffers.length; nFileIndex++)
					{
						if (yBuffers[nFileIndex] != null)
						{
							oOut.putNextEntry(new ZipEntry(sEntries[nFileIndex]));
							oOut.write(yBuffers[nFileIndex]);
						}
					}
				}
				
				long lDay = lValid / 86400000 * 86400000;
				oDaysToProcess.add(lDay);
			}
		}
		ReentrantLock oLock = null;
		for (Long oDay : oDaysToProcess)
		{
			long lDay = oDay.longValue();
			if (oLock != null)
			{
				oLock.lock();
				oLock.unlock();
			}
			oLock = processRealTime(oRRs, lDay, lDay, lDay);
		}
	}
	
	
	/**
	 * Parses the file name of the .zip files create by {@link NHC} to extract
	 * metadata about the storm forecast the file contains
	 * @param sFile IMRCP NHC .zip file to parse
	 * @param sParts Gets filled with the 2 parts of the storm metadata string 
	 * defined below in the format [BB##yyyy, %%%?adv]
	 * 
	 * @return The string that represents the storm metadata in the format 
	 * BB##yyyy_%%%?adv where BB is the 2 char basin abbreviation, ## is the 2 digit
	 * storm number for that basin, yyyy is the 4 digit year, %%% is the 3 digit 
	 * (with leading 0s) forecast number for that storm, ? is an optional 1 char
	 * used for intermediate forecasts (starts at A and increments in alphabetical
	 * order) and adv is the constant NHC use at the end of storm numbers. An 
	 * example is if sFile = "2022/nhc_EP032022_019adv_202206210900_202206261800_202206210844.zip"
	 * EP032022_019adv is returned and sParts = [EP032022, 019adv]
	 */
	public static String getStormNumber(String sFile, String[] sParts)
	{
		int nStart = sFile.lastIndexOf("/");
		nStart = sFile.indexOf("_", nStart) + 1;
		int nEnd = sFile.lastIndexOf("_"); // position is 1 before ref time
		nEnd = sFile.lastIndexOf("_", nEnd - 1); // position is 1 before end time
		nEnd = sFile.lastIndexOf("_", nEnd - 1); // position is 1 before start time
		String sStorm = sFile.substring(nStart, nEnd); // get the entire store metadata in the format 
		String[] sSplit = sStorm.split("_");
		sParts[0] = sSplit[0];
		sParts[1] = sSplit[1];
		return sStorm;
	}
	

	@Override
	protected void createFiles(TileFileInfo oInfo, TreeSet<Path> oArchiveFiles)
	{
		try
		{
			ArrayList<ResourceRecord> oRRs = oInfo.m_oRRs;
			
			ResourceRecord oRR = oRRs.get(0);
			int[] nTile = new int[2];
			int nPPT = (int)Math.pow(2, oRR.getTileSize()) - 1;
			Mercator oM = new Mercator(nPPT);
			long lValid = oInfo.m_lRef / 86400000 * 86400000 + 86400000; // creating daily tile files so the ref time needs to be the start of the next day to get all of the files that could have forecasts for the day
			long lStart = oInfo.m_lStart / 86400000 * 86400000; // start of the day of the file
			long lEndDay = lStart + 86400000;
			long lFileRecv = lStart;
			long lEnd = lStart + oRR.getRange();
			int[] nBB = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
			TreeSet<Path> oAllValid = TileObsView.getArchiveFiles(lStart, lEnd, lValid, oRR);
			if (oAllValid.isEmpty())
				return;
			m_oLogger.debug("create files");
			HashMap<Integer, ObsList> oObsMap = new HashMap();
			for (ResourceRecord oTempRR : oRRs)
				oObsMap.put(oTempRR.getObsTypeId(), new ObsList());
			String[] sStormParts = new String[2];
			StringPool oSP = new StringPool();

			int nStringFlag = 0;
			for (int nIndex = 0; nIndex < m_nStrings; nIndex++)
				nStringFlag = Obs.addFlag(nStringFlag, nIndex);
			UnitConv oPressureConv = Units.getInstance().getConversion("mbar", ObsType.getUnits(ObsType.PRSUR, true));
			UnitConv oWindConv = Units.getInstance().getConversion("kn", ObsType.getUnits(ObsType.GSTWND, true));
			for (Path oPath : oAllValid)
			{
				ConeParser oCP = null;
				TrackParser oTP = null;
				try (ZipInputStream oZip = new ZipInputStream(Files.newInputStream(oPath))) // decompress the zip file
				{
					ZipEntry oZe;
					ZipInputStream oCone;
					ZipInputStream oTrack;
					while ((oZe = oZip.getNextEntry()) != null)
					{
						String sName = oZe.getName();
						if (sName.contains("_CONE")) // cone file
						{
							oCone = new ZipInputStream(oZip);
							ZipEntry oConeEntry;
							while ((oConeEntry = oCone.getNextEntry()) != null)
							{
								if (oConeEntry.getName().endsWith(".kml")) // parse the kml
								{
									oCP = new ConeParser();
									oCP.parse(oCone, (int)oConeEntry.getSize(), nBB);
								}
							}

						}
						else if (sName.contains("_TRACK")) // track file, contains points as well
						{
							oTrack = new ZipInputStream(oZip);
							ZipEntry oTrackEntry;
							while ((oTrackEntry = oTrack.getNextEntry()) != null)
							{
								if (oTrackEntry.getName().endsWith(".kml")) // parse the kml
								{
									oTP = new TrackParser();
									oTP.parse(oTrack, (int)oTrackEntry.getSize(), nBB);
								}
							}
						}
					}
				}
				catch (Exception oEx)
				{
					m_oLogger.error(oEx, oEx);
				}

				if (oCP == null || oTP == null) // invalid forecast file
					continue;
				getStormNumber(oPath.toString(), sStormParts);
				long lStormRecv = oCP.m_lCreated;
				if (lStormRecv >= lEndDay || lStormRecv < lStart)
					continue;
				long lStormStart = oTP.m_lStart;
				long lStormEnd = oTP.m_lEnd;
				if (lStormStart < lStart)
					lStart = lStormStart;
				if (lStormEnd > lEnd)
					lEnd = lStormEnd;

				String[] sStrings = new String[]{sStormParts[0], sStormParts[1], oTP.m_sStormName, null, null, null, null, null};
				for (int nIndex = 0; nIndex < m_nStrings; nIndex++)
					oSP.intern(sStrings[nIndex]);
				Obs oCone = new Obs();
				Obs oPressure = new Obs();
				oPressure.m_sStrings = oCone.m_sStrings = new String[]{sStormParts[0], sStormParts[1], oTP.m_sStormName, null, null, null, null, null};
				oPressure.m_lObsTime1 = oCone.m_lObsTime1 = lStormStart;
				oPressure.m_lObsTime2 = oCone.m_lObsTime2 = lStormEnd;
				oPressure.m_lTimeRecv = oCone.m_lTimeRecv = lStormRecv;
				oPressure.m_oGeoArray = oCone.m_oGeoArray = oCP.m_nGeo;
				oPressure.m_yGeoType = oCone.m_yGeoType = Obs.POLYGON;
				oCone.m_dValue = lookupStormType(oCP.m_sStormType);
				oPressure.m_dValue = oPressureConv.convert(oTP.m_nMinPressure);
				
				
				if (oObsMap.containsKey(ObsType.TRSCNE))
					oObsMap.get(ObsType.TRSCNE).add(oCone);
				if (oObsMap.containsKey(ObsType.PRSUR))
					oObsMap.get(ObsType.PRSUR).add(oPressure);
				
				SimpleDateFormat oSdf = new SimpleDateFormat("yyyyMMddHHmm");
				oSdf.setTimeZone(Directory.m_oUTC);
				double[] dPoint = new double[2];
				double dAngleStep = -(Math.PI * 2) / m_nPointsInCircle;
				for (int nIndex = 0; nIndex < oTP.m_oPoints.size(); nIndex++)
				{
					Point oP = oTP.m_oPoints.get(nIndex);
					Obs oCat = new Obs();
					oCat.m_sStrings = sStrings;
					oCat.m_lObsTime1 = oP.m_lTime;
					if (nIndex != oTP.m_oPoints.size() - 1)
					{
						Point oP2 = oTP.m_oPoints.get(nIndex + 1);
						oCat.m_lObsTime2 = oP2.m_lTime;

						int nWindDiff = oP2.m_nMaxWind - oP.m_nMaxWind;
						long lDiff = oP2.m_lTime - oP.m_lTime;
						int nSteps = (int)(lDiff / 3600000); // divide into 1 hour time steps
						double dHdg = GeoUtil.heading(oP.m_nX, oP.m_nY, oP2.m_nX, oP2.m_nY);
						double dLength = GeoUtil.distance(oP.m_nX, oP.m_nY, oP2.m_nX, oP2.m_nY);
						double dStep = dLength / nSteps;
						double dDeltaX = dStep * Math.cos(dHdg);
						double dDeltaY = dStep * Math.sin(dHdg);
						double dWindStep = (double)nWindDiff / nSteps;
						for (int nTime = 0; nTime < nSteps; nTime++)
						{
							int nLon = (int)(oP.m_nX + dDeltaX * nTime);
							int nLat = (int)(oP.m_nY + dDeltaY * nTime);
							double dLon = GeoUtil.fromIntDeg(nLon);
							double dLat = GeoUtil.fromIntDeg(nLat);
							Iterator<int[]> oPoly = Arrays.iterator(oCone.m_oGeoArray, new int[2], 7, 2);
							double dMinDist = Double.MAX_VALUE;
							while (oPoly.hasNext()) // set the radius of the hurricane center to the minimum distance to a point on the cone of probability
							{
								int[] nPt = oPoly.next();
								double dDist = GeoUtil.distanceFromLatLon(dLat, dLon, GeoUtil.fromIntDeg(nPt[1]), GeoUtil.fromIntDeg(nPt[0]));
								if (dDist < dMinDist)
									dMinDist = dDist;
							}

							Obs oWindCenter = new Obs();
							oWindCenter.m_dValue = oWindConv.convert(oP.m_nMaxWind + dWindStep * nTime);
							oWindCenter.m_lTimeRecv = lStormRecv;
							oWindCenter.m_lObsTime1 = oP.m_lTime + 3600000 * nTime;
							oWindCenter.m_lObsTime2 = oWindCenter.m_lObsTime1 + 3600000;
							
							oWindCenter.m_sStrings = sStrings;
							int[] nGeo = Arrays.newIntArray(m_nPointsInCircle * 2 + 6);
							nGeo = Arrays.add(nGeo, 1 , 0); // 1 ring start with zero holes
							nGeo = Arrays.add(nGeo, new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE});
							
							for (int nStep = 0; nStep < m_nPointsInCircle; nStep++)
							{
								double dTheta = nStep * dAngleStep;
								GeoUtil.getPoint(dLon, dLat, dTheta, dMinDist, dPoint);
								nGeo = Arrays.addAndUpdate(nGeo, GeoUtil.toIntDeg(dPoint[0]), GeoUtil.toIntDeg(dPoint[1]), 3);
								++nGeo[2]; // increment point count
							}
							
							oWindCenter.m_oGeoArray = nGeo;
							oWindCenter.m_yGeoType = Obs.POLYGON;

							if (oObsMap.containsKey(ObsType.GSTWND))
								oObsMap.get(ObsType.GSTWND).add(oWindCenter);
						}
					}
					else
					{
						long lDiff;
						if (nIndex == 0)
							lDiff = 3600000 * 12; // default 12 hours
						else
							lDiff = oP.m_lTime - oTP.m_oPoints.get(nIndex - 1).m_lTime;
						oCat.m_lObsTime2 = oP.m_lTime + lDiff;
						double dLon = GeoUtil.fromIntDeg(oP.m_nX);
						double dLat = GeoUtil.fromIntDeg(oP.m_nY);

						Iterator<int[]> oPoly = Arrays.iterator(oCone.m_oGeoArray, new int[2], 7, 2);
						double dMinDist = Double.MAX_VALUE;
						while (oPoly.hasNext()) // set the radius of the hurricane center to the minimum distance to a point on the cone of probability
						{
							int[] nPt = oPoly.next();
							double dDist = GeoUtil.distanceFromLatLon(dLat, dLon, GeoUtil.fromIntDeg(nPt[1]), GeoUtil.fromIntDeg(nPt[0]));
							if (dDist < dMinDist)
								dMinDist = dDist;
						}

						Obs oWindCenter = new Obs();
						oWindCenter.m_dValue = oWindConv.convert(oP.m_nMaxWind);
						oWindCenter.m_lTimeRecv = lStormRecv;
						oWindCenter.m_lObsTime1 = oP.m_lTime;
						oWindCenter.m_lObsTime2 = oWindCenter.m_lObsTime1 + 3600000;
						oWindCenter.m_sStrings = sStrings;
						int[] nGeo = Arrays.newIntArray(m_nPointsInCircle * 2 + 6);
						nGeo = Arrays.add(nGeo, 1 , 0); // 1 ring start with zero holes
						nGeo = Arrays.add(nGeo, new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE});
						
						for (int nStep = 0; nStep < m_nPointsInCircle; nStep++)
						{
							double dTheta = nStep * dAngleStep;
							GeoUtil.getPoint(dLon, dLat, dTheta, dMinDist, dPoint);
							nGeo = Arrays.addAndUpdate(nGeo, GeoUtil.toIntDeg(dPoint[0]), GeoUtil.toIntDeg(dPoint[1]), 3);
							++nGeo[2];
						}
						
						oWindCenter.m_oGeoArray = nGeo;

						if (oObsMap.containsKey(ObsType.GSTWND))
							oObsMap.get(ObsType.GSTWND).add(oWindCenter);
					}
					oCat.m_lTimeRecv = lStormRecv;
					oCat.m_oGeoArray = Obs.createPoint(oP.m_nX, oP.m_nY);
					oCat.m_yGeoType = Obs.POINT;
					oCat.m_dValue = lookupStormType(oP.m_sStormType);
					if (oObsMap.containsKey(ObsType.TRSCAT))
						oObsMap.get(ObsType.TRSCAT).add(oCat);
				}
			}

			ArrayList<String> oSPList = oSP.toList();
			for (Entry<Integer, ObsList> oEntry : oObsMap.entrySet())
			{
				int nObstype = oEntry.getKey();
				for (int nIndex = 0; nIndex < oRRs.size(); nIndex++)
				{
					ResourceRecord oTemp = oRRs.get(nIndex);
					if (oTemp.getObsTypeId() == nObstype)
					{
						oRR = oTemp;
						break;
					}
					oRR = null;
				}

				if (oRR == null)
					throw new Exception("Missing resource record");

				
				
				ArrayList<TileForFile> oAllTiles = new ArrayList();
				TileForFile oSearch = new TileForFile();
				byte yGeoType;
				if (nObstype == ObsType.TRSCNE || nObstype == ObsType.PRSUR || nObstype == ObsType.GSTWND) // polygons
				{
					yGeoType = Obs.POLYGON;
					for (Obs oObs : oEntry.getValue())
					{
						oM.lonLatToTile(GeoUtil.fromIntDeg(oObs.m_oGeoArray[3]), GeoUtil.fromIntDeg(oObs.m_oGeoArray[6]), oRR.getZoom(), nTile);
						int nStartX = nTile[0]; // does this handle lambert conformal?
						int nStartY = nTile[1];
						oM.lonLatToTile(GeoUtil.fromIntDeg(oObs.m_oGeoArray[5]), GeoUtil.fromIntDeg(oObs.m_oGeoArray[4]), oRR.getZoom(), nTile);
						int nEndX = nTile[0];
						int nEndY = nTile[1];
						for (int nTileY = nStartY; nTileY <= nEndY; nTileY++)
						{
							for (int nTileX = nStartX; nTileX <= nEndX; nTileX++)
							{
								oSearch.m_nX = nTileX;
								oSearch.m_nY = nTileY;

								int nIndex = Collections.binarySearch(oAllTiles, oSearch);
								if (nIndex < 0)
								{
									nIndex = ~nIndex;
									oAllTiles.add(nIndex, new TileForFile(nTileX, nTileY, oM, oRR, lFileRecv, nStringFlag, m_oLogger));
								}
								
								oAllTiles.get(nIndex).m_oObsList.add(oObs);
							}
						}
					}
				}
				else if (nObstype == ObsType.TRSCAT)
				{
					yGeoType = Obs.POINT;
					for (Obs oObs : oEntry.getValue())
					{
						double dX = GeoUtil.fromIntDeg(oObs.m_oGeoArray[1]);
						double dY = GeoUtil.fromIntDeg(oObs.m_oGeoArray[2]);
						oM.lonLatToTile(dX, dY, oRR.getZoom(), nTile);
						oSearch.m_nX = nTile[0];
						oSearch.m_nY = nTile[1];
						int nIndex = Collections.binarySearch(oAllTiles, oSearch);
						if (nIndex < 0)
						{
							nIndex = ~nIndex;
							oAllTiles.add(nIndex, new TileForFile(nTile[0], nTile[1]));
						}
						
						oAllTiles.get(nIndex).m_oObsList.add(oObs);
					}
				}
				else
					continue;

				for (TileForFile oTile : oAllTiles)
				{
					oTile.m_oSP = oSPList;
					oTile.m_oM = oM;
					oTile.m_oRR = oRR;
					oTile.m_lFileRecv = lFileRecv;
					oTile.m_nStringFlag = nStringFlag;
					oTile.m_bWriteObsFlag = false;
					oTile.m_bWriteRecv = true;
					oTile.m_bWriteStart = true;
					oTile.m_bWriteEnd = true;
					oTile.m_bWriteObsType = false;
				}
				m_oLogger.debug(Integer.toString(oRR.getObsTypeId(), 36) + " " + oAllTiles.size());
				Scheduling.processCallables(oAllTiles, m_nThreads);
				FilenameFormatter oFF = new FilenameFormatter(oRR.getTiledFf());
				Path oTiledFile = oRR.getFilename(lFileRecv, lStart, lEnd, oFF);
				Files.createDirectories(oTiledFile.getParent());
				try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(oTiledFile))))
				{
					oOut.writeByte(1); // version
					oOut.writeInt(nBB[0]); // bounds min x
					oOut.writeInt(nBB[1]); // bounds min y
					oOut.writeInt(nBB[2]); // bounds max x
					oOut.writeInt(nBB[3]); // bounds max y
					oOut.writeInt(oRR.getObsTypeId()); // obsversation type
					oOut.writeByte(Util.combineNybbles(0, oRR.getValueType()));
					oOut.writeByte(yGeoType);
					oOut.writeByte(0); // id format: -1=variable, 0=null, 16=uuid, 32=32-bytes
					oOut.writeByte(Util.combineNybbles(0, 0b0111)); // associate with obj and timestamp flag, the lower bits are all 1 since recv, start, and end time are written per obs
					oOut.writeLong(lFileRecv);
					oOut.writeInt((int)((lEnd - lFileRecv) / 1000));
					oOut.writeByte(1); // only file start time
					oOut.writeInt((int)((lStart - lFileRecv) / 1000));
					ByteArrayOutputStream oRawBytes = new ByteArrayOutputStream(8192);
					DataOutputStream oRawOut = new DataOutputStream(oRawBytes);
					
					for (String sStr : oSPList)
					{
						oRawOut.writeUTF(sStr);
					}
					oRawOut.flush();

					byte[] yCompressed = XzBuffer.compress(oRawBytes.toByteArray());
					oOut.writeInt(yCompressed.length);  // compressed string pool length
					oOut.writeInt(oSPList.size());
					oOut.write(yCompressed);

					oOut.writeByte(oRR.getZoom()); // tile zoom level
					oOut.writeByte(oRR.getTileSize());

					int nIndex = oAllTiles.size();
					while (nIndex-- > 0) // remove possible empty tiles
					{
						if (oAllTiles.get(nIndex).m_yTileData == null)
							oAllTiles.remove(nIndex);
					}
					oOut.writeInt(oAllTiles.size());
					
					for (TileForFile oTile : oAllTiles) // finish writing tile metadata
					{
						oOut.writeShort(oTile.m_nX);
						oOut.writeShort(oTile.m_nY);
						oOut.writeInt(oTile.m_yTileData.length);
					}

					for (TileForFile oTile : oAllTiles)
					{
						oOut.write(oTile.m_yTileData);
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
	 * Object used to parse the cone .kml files from the National Hurricane Center
	 */
	private class ConeParser
	{
		String m_sStormType;
		int[] m_nGeo;
		long m_lCreated;
		/**
		 * Parses the given InputStream which should be at the start of a cone
		 * .kml file from the National Hurricane Center.
		 * @param oIn cone .kml file
		 * @param nSize length of the file in bytes
		 * @throws Exception
		 */
		protected void parse(InputStream oIn, int nSize, int[] nBB)
			throws Exception
		{
			int nByte;
			StringBuilder sBuf = new StringBuilder(nSize); 
			while ((nByte = oIn.read()) >= 0) // read the file into a StringBuilder
				sBuf.append((char)nByte);
			
			int nStart = sBuf.indexOf("<TR><TD><B>Date Created:</B>") + "<TR><TD><B>Date Created:</B>".length();
			String sCreated = sBuf.substring(nStart, sBuf.indexOf("</TD></TR>", nStart)).trim();
			SimpleDateFormat oCreateSdf = new SimpleDateFormat("MM-dd-yyyy - HH:mm z");
			m_lCreated = oCreateSdf.parse(sCreated).getTime();
			int[] nGeo = Arrays.newIntArray();
			nGeo = Arrays.add(nGeo, 1);
			nGeo = Arrays.add(nGeo, 0);
			nGeo = Arrays.add(nGeo, new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE});
			
			nStart = sBuf.indexOf("<coordinates>") + "<coordinates>".length();
			String[] sCoords = sBuf.substring(nStart, sBuf.indexOf("</coordinates>", nStart)).trim().split(" "); // find the coordinates of the cone which are a space separate list
			for (String sCoord : sCoords) // set the bounding box and the points of the polygon by reading the coordinates
			{
				String[] sVals = sCoord.split(",");
				nGeo = Arrays.addAndUpdate(nGeo, GeoUtil.toIntDeg(Double.parseDouble(sVals[0])), GeoUtil.toIntDeg(Double.parseDouble(sVals[1])), 3);
				++nGeo[2];
			}
			
			int nPolySize = Arrays.size(nGeo);
			if (nGeo[7] == nGeo[nPolySize - 2] && nGeo[8] == nGeo[nPolySize - 1]) // ensure the polygon is open
			{
				nGeo[0] -= 2;
				--nGeo[2];
			}
			
			if (nGeo[3] < nBB[0])
				nBB[0] = nGeo[3];
			if (nGeo[4] < nBB[1])
				nBB[1] = nGeo[4];
			if (nGeo[5] > nBB[2])
				nBB[2] = nGeo[5];
			if (nGeo[6] > nBB[3])
				nBB[3] = nGeo[6];
			
			if (!GeoUtil.isClockwise(nGeo, 2))
			{
				GeoUtil.reverseRing(nGeo, 2);
			}
			m_nGeo = nGeo;
			nStart = sBuf.indexOf("<Data name=\"stormType\">"); // get the storm type
			nStart = sBuf.indexOf("<value>", nStart) + "<value>".length();
			m_sStormType = sBuf.substring(nStart, sBuf.indexOf("</value>", nStart));
		}
	}
	
	
	/**
	 * Object used to parse the track .kml files from the National Hurricane Center
	 */
	private class TrackParser
	{
		String m_sStormName = null;
		int m_nMinPressure = Integer.MIN_VALUE;
		ArrayList<Track> m_oTracks = new ArrayList();
		ArrayList<Point> m_oPoints = new ArrayList();
		long m_lStart = Long.MAX_VALUE;
		long m_lEnd = Long.MIN_VALUE;
		
		/**
		 * Parses the given InputStream which should be at the start of a track
		 * .kml file from the National Hurricane Center.
		 * @param oIn cone .kml file
		 * @param nSize length of the file in bytes
		 * @throws Exception
		 */
		protected void parse(InputStream oIn, int nSize, int[] nBB)
			throws Exception
		{
			int nByte;
			
			StringBuilder sBuf = new StringBuilder(nSize);
			while ((nByte = oIn.read()) >= 0) // read the file into a StringBuilder
				sBuf.append((char)nByte);
			
			int nPlacemark = sBuf.indexOf("<Placemark>");
			int nStart;
			int nEnd;
			SimpleDateFormat oValidSdf = new SimpleDateFormat("h:mm a z MMMM dd, yyyy");
			while (nPlacemark >= 0) // read all of the Placemarks in the file. They are be either points or LineStrings
			{
				int nLimit = sBuf.indexOf("</Placemark>", nPlacemark); // get the end of the current Placemark
				if (m_sStormName == null) // if the storm name hasn't been determined yet, set it
				{
					nStart = sBuf.indexOf("<b>", nPlacemark) + "<b>".length();
					nEnd = sBuf.indexOf("</b", nStart);
					m_sStormName = sBuf.substring(nStart, nEnd);
				}
				int nLineString = sBuf.indexOf("<LineString>", nPlacemark);
				if (nLineString >= nPlacemark && nLineString < nLimit) // if the current Placemark is a LineString
				{
					nStart = sBuf.indexOf("<coordinates>", nPlacemark) + "<coordinates>".length();
					String[] sCoords = sBuf.substring(nStart, sBuf.indexOf("</coordinates>", nStart)).trim().split(" "); // find the coordinates of the cone which are a space separate list
					int[] nCoords = Arrays.newIntArray(sCoords.length / 3 * 2); // points are lon,lat,elev
					
					for (String sCoord : sCoords)
					{
						String[] sOrds = sCoord.split(",");
						int nX = GeoUtil.toIntDeg(Double.parseDouble(sOrds[0]));
						int nY = GeoUtil.toIntDeg(Double.parseDouble(sOrds[1]));
						if (nX < nBB[0])
							nBB[0] = nX;
						if (nY < nBB[1])
							nBB[1] = nY;
						if (nX > nBB[2])
							nBB[2] = nX;
						if (nY > nBB[3])
							nBB[3] = nY;

						nCoords = Arrays.add(nCoords, nX, nY);
					}
					
					nStart = sBuf.indexOf("<Data name=\"stormType\"", nStart);
					nStart = sBuf.indexOf("<value>", nStart) + "<value>".length();
					String sType = sBuf.substring(nStart, sBuf.indexOf("</value>", nStart));
					
					nStart = sBuf.indexOf("<Data name=\"fcstpd\"", nStart) + "<Data name=\"fcstpd\"".length();
					nStart = sBuf.indexOf("<value>", nStart) + "<value>".length();
					int nPredHours = Integer.parseInt(sBuf.substring(nStart, sBuf.indexOf("</value>", nStart)));
					
					if (m_nMinPressure == Integer.MIN_VALUE) // if the minimum pressure hasn't been determined yet, set it
					{
						nStart = sBuf.indexOf("<Data name=\"minimumPressure\"", nStart) + "<Data name=\"minimumPressure\"".length();
						nStart = sBuf.indexOf("<value>", nStart) + "<value>".length();
						m_nMinPressure = Integer.parseInt(sBuf.substring(nStart, sBuf.indexOf("</value>", nStart)));
					}
					m_oTracks.add(new Track(sType, nPredHours, nCoords));
				}
				else // Placemark is a point
				{
					int nPoint = sBuf.indexOf("<Point>", nPlacemark);
					if (nPoint >= nPlacemark && nPoint < nLimit)
					{
						nStart = sBuf.indexOf("<tr><td nowrap>Maximum Wind: ", nPlacemark) + "<tr><td nowrap>Maximum Wind: ".length();
						int nWindKnots = Integer.parseInt(sBuf.substring(nStart, sBuf.indexOf(" ", nStart))); // read knots
						nStart = sBuf.indexOf("(", nStart) + 1;
						int nWindMph = Integer.parseInt(sBuf.substring(nStart, sBuf.indexOf(" ", nStart))); // read mph
						
						String sStormType; // determine the storm type based off of the maximum wind speed
						if (nWindMph > MAJOR)
							sStormType = "MH";
						else if (nWindMph > HURRICANE)
							sStormType = "HU";
						else if (nWindMph > STORM)
						{
							if (m_sStormName.toLowerCase().startsWith("sub"))
								sStormType = "STS";
							else
								sStormType = "TS";
						}
						else
						{
							if (m_sStormName.toLowerCase().startsWith("sub"))
								sStormType = "STD";
							else
								sStormType = "TD";
						}
						
						nStart = sBuf.indexOf("<coordinates>", nPoint) + "<coordinates>".length();
						String[] sCoord = sBuf.substring(nStart, sBuf.indexOf("</coordinates>", nStart)).trim().split(",");
						int nX = GeoUtil.toIntDeg(Double.parseDouble(sCoord[0]));
						int nY = GeoUtil.toIntDeg(Double.parseDouble(sCoord[1]));
						
						nStart = sBuf.indexOf("<tr><td nowrap>Valid at:", nPlacemark) + "<tr><td nowrap>Valid at:".length();
						String sTime = sBuf.substring(nStart, sBuf.indexOf("</td></tr>", nStart)).trim();
						long lTime = oValidSdf.parse(sTime).getTime();
						m_oPoints.add(new Point(sStormType, nX, nY, lTime, nWindKnots));
						
						if (lTime < m_lStart)
							m_lStart = lTime;
						if (lTime > m_lEnd)
							m_lEnd = lTime;
					}
				}

				nPlacemark = sBuf.indexOf("<Placemark>", nLimit);
			}
		}
	}
	
	
	public static HashMap<String, String> getCurrentStorms(long lStart, long lEnd, long lRef)
	{
		HashMap<String, String> oStorms = new HashMap();
		ObsList oCones = Directory.getInstance().lookup("ObsView").getData(ObsType.TRSCNE, lStart, lEnd, 0, 650000000, -1790000000, 0, lRef); // get all possible cones
		Comparator<Obs> oNhcComp = (Obs o1, Obs o2) -> // compare nhc obs by storm number and reference time in reverse order
		{
			int nReturn = (o1.m_sStrings[0]).compareTo(o2.m_sStrings[0]);
			if (nReturn == 0)
				nReturn = Long.compare(o2.m_lTimeRecv, o1.m_lTimeRecv);
			return nReturn;
		};
		Introsort.usort(oCones, oNhcComp);
		String[] sPrevStorm = new String[]{"", ""};
		for (Obs oObs : oCones)
		{
			if (oObs.m_sStrings[0].compareTo(sPrevStorm[0]) != 0)
			{
				sPrevStorm = oObs.m_sStrings;
				oStorms.put(oObs.m_sStrings[0], oObs.m_sStrings[1]);
			}
		}
		return oStorms;
	}
	
	
	private class Track
	{
		String m_sStormType;
		int m_nPredHours;
		int[] m_nGeo;
		
		Track(String sType, int nHours, int[] nGeo)
		{
			m_sStormType = sType;
			m_nPredHours = nHours;
			m_nGeo = nGeo;
		}
	}
	
	
	private class Point
	{
		String m_sStormType;
		int m_nX;
		int m_nY;
		long m_lTime;
		int m_nMaxWind;
		
		Point(String sType, int nX, int nY, long lTime, int nWind)
		{
			m_sStormType = sType;
			m_nX = nX;
			m_nY = nY;
			m_lTime = lTime;
			m_nMaxWind = nWind;
		}
	}
	
	
	/**
	 * Determines the category of the storm based off of its wind speed in knots
	 * using the Saffir–Simpson hurricane wind scale
	 * @param dWindSpeed Maximum wind speed in knots.
	 * @return The category number on the Saffir–Simpson hurricane wind scale
	 */
	public static int getSSHWS(double dWindSpeed)
	{
		int nCat = 0;
		int nIndex = SSHWS.length;
		while (nIndex-- > 0 && nCat == 0)
			if (dWindSpeed >= SSHWS[nIndex])
				nCat = nIndex + 1;
		
		return nCat;
	}
	
	
	public static int getSSHWS(double dWindSpeed, double[] dThresholds)
	{
		int nCat = 0;
		int nIndex = dThresholds.length;
		while (nIndex-- > 0 && nCat == 0)
			if (dWindSpeed >= dThresholds[nIndex])
				nCat = nIndex + 1;
		
		return nCat;
	}
	
	
	public static int getSSHWS(double dWindSpeed, boolean bKnots)
	{
		if (bKnots)
			return getSSHWS(dWindSpeed, SSHWS);
		else
			return getSSHWS(dWindSpeed, SSHWSMPERSEC);
	}
	
	
	/**
	 * Wrapper for {@link HashMap#get(java.lang.Object)} with a default value
	 * of the mapping for Tropical Storm ("TS")
	 * @param sVal Storm type abbreviation 
	 * @return The integer that represents the storm type if a mapping exists 
	 * for the given String, otherwise the integer that represents Tropical Storm
	 */
	private static int lookupStormType(String sVal)
	{
		Integer oVal = STORMTYPES.get(sVal);
		if (oVal == null)
			return STORMTYPES.get("TS");
		
		return oVal;
	}
}
