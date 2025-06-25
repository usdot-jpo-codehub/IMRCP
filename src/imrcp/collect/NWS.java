package imrcp.collect;

import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.ProjProfile;
import imrcp.geosrv.ProjProfiles;
import imrcp.store.TileObsView;
import imrcp.system.Arrays;
import imrcp.system.FilenameFormatter;
import imrcp.system.Directory;
import imrcp.system.JSONUtil;
import imrcp.system.ObsType;
import imrcp.system.OneTimeReentrantLock;
import imrcp.system.ResourceRecord;
import imrcp.system.Scheduling;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;
import org.json.JSONObject;
import ucar.ma2.Array;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.dataset.VariableDS;
import ucar.nc2.dt.GridDatatype;
import ucar.nc2.dt.grid.GridDataset;
import ucar.unidata.geoloc.ProjectionImpl;

/**
 * Generic collector used to download gridded weather forecasts from different
 * National Weather Service products.
 * @author aaron.cherney
 */
public class NWS extends Collector
{	
	/**
	 * Number of subdirectories to check. This is used for products that keep
	 * multiple days of forecasts available
	 */
	protected int m_nDirs;

	
	/**
	 * Number of milliseconds between two consecutive subdirectories
	 */
	protected int m_nDirDiff;

	
	/**
	 * Object used to create time dependent paths that extend the base url
	 */
	protected FilenameFormatter m_oUrlExt;

	
	/**
	 * String to search for in the file index that starts a file name
	 */
	protected String m_sStart;

	
	/**
	 * String to search for in the file index that ends a file name
	 */
	protected String m_sEnd;

	
	/**
	 * String to search for to skip all of the header information of the file index
	 */
	protected String m_sInitSkip;

	
	/**
	 * String to search for after a file has been found in the file index to get 
	 * in a position to be able to find the next file.
	 */
	protected String m_sConSkip;

	
	/**
	 * Regex string used to validate if a file name found in the index is the
	 * correct type to download
	 */
	protected String m_sPattern;

	
	/**
	 * Set to true if the product has a single file that gets over written for
	 * each forecast. Set to false if the product has time dependent file names
	 */
	protected boolean m_bUseNow;

	
	/**
	 * Time in milliseconds that needs to be added to the timestamp parsed from
	 * source files to get the correct received time
	 */
	protected int m_nRecvOffset;

	
	/**
	 * How often it is expected to download a forecast in seconds
	 */
	protected int m_nDownloadPeriod;

	
	/**
	 * The midnight offset in seconds of when a new forecast is expected to be
	 * available
	 */
	protected int m_nDownloadOffset;

	
	/**
	 * For products that have multiple files for a forecast interval, this is 
	 * the lower limit of file indices to keep
	 */
	protected int m_nFileOffsetStart;

	
	/**
	 * For products that have multiple files for a forecast interval, this is 
	 * the upper limit of file indices to keep
	 */
	protected int m_nFileOffsetLimit;

	
	/**
	 * Default size of the buffer than contains the downloaded file
	 */
	protected int m_nDefaultBuffer;

	
	/**
	 * Timeout in milliseconds used for {@link java.net.URLConnection}
	 */
	protected int m_nTimeout;

	
	/**
	 * Time in millisecond to wait after an error has happened to start trying 
	 * to download files again.
	 */
	protected int m_nErrorRetry;
	
	protected int m_nPastLimit;
	
	protected int m_nTileProcessOffset;
	
	/**
	 * Configurable array that specifies which forecast hours to download
	 */
	private int[] m_nFileIndices;
	
	private int[] m_nIndexFails;
	
	
	/**
	 *
	 */
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_nDirs = oBlockConfig.optInt("dirs", 1);
		m_nDirDiff = oBlockConfig.optInt("dirdiff", 86400000);
		m_oUrlExt = new FilenameFormatter(oBlockConfig.optString("urlext", ""));
		m_sStart = oBlockConfig.optString("start", "");
		m_sEnd = oBlockConfig.optString("end", "");
		m_sInitSkip = oBlockConfig.optString("initskip", "");
		m_sConSkip = oBlockConfig.optString("conskip", "");
		m_sPattern = oBlockConfig.optString("pattern", "");
		m_bUseNow = oBlockConfig.optBoolean("usenow", false);
		m_nRecvOffset = oBlockConfig.optInt("recvoffset", 0);
		m_nDownloadPeriod = oBlockConfig.optInt("dlperiod", 3600);
		m_nDownloadOffset = oBlockConfig.optInt("dloffset", 3300);
		m_nFileOffsetStart = oBlockConfig.optInt("offsetstart", 0);
		m_nFileOffsetLimit = oBlockConfig.optInt("offsetlimit", 0);
		m_nDefaultBuffer = oBlockConfig.optInt("defbuf", 83886080);
		m_nTimeout = oBlockConfig.optInt("conntimeout", 90000);
		m_nErrorRetry = oBlockConfig.optInt("error", 300000);
		m_nPastLimit = oBlockConfig.optInt("pastlimit", Integer.MAX_VALUE);
		m_nTileProcessOffset = oBlockConfig.optInt("tileprocess", Integer.MIN_VALUE);
		m_nFileIndices = JSONUtil.getIntArray(oBlockConfig, "fileindices");
		if (m_nFileIndices.length == 0)
			m_nFileIndices = null;
		else
			java.util.Arrays.sort(m_nFileIndices);
		m_nIndexFails = new int[m_nDirs];
	}
	
	
	/**
	 * Attempts to download any available file from the configured product that
	 * is not saved to disk.
	 */
	@Override
	public void execute()
	{
		StringBuilder sIndex = new StringBuilder();
		TreeSet<String> oFilesOnDisk = new TreeSet();
		long lNow = System.currentTimeMillis();
		GregorianCalendar oTime = new GregorianCalendar(Directory.m_oUTC);
		ArrayList<ResourceRecord> oResourceRecords = Directory.getResourcesByContrib(m_nContribId);
		lNow = (lNow / 60000) * 60000; // floor to nearest minute
		
		long[] lTimes = new long[3];
		
		if (m_nObsTypeId != Integer.MIN_VALUE)
		{
			int nIndex = oResourceRecords.size();
			while (nIndex-- > 0)
			{
				if (oResourceRecords.get(nIndex).getObsTypeId() != m_nObsTypeId)
					oResourceRecords.remove(nIndex);
			}
		}
		
		if (m_nSourceId != Integer.MIN_VALUE)
		{
			int nIndex = oResourceRecords.size();
			while (nIndex-- > 0)
			{
				if (oResourceRecords.get(nIndex).getSourceId() != m_nSourceId)
					oResourceRecords.remove(nIndex);
			}
		}
		
		
		FilenameFormatter oArchiveFile = new FilenameFormatter(oResourceRecords.get(0).getArchiveFf());
		String sExt = oArchiveFile.getExtension();
		Pattern oRegEx = Pattern.compile(m_sPattern);
		int nRange = oResourceRecords.get(0).getRange();
		int nDelay = oResourceRecords.get(0).getDelay();
		int nFreq = oResourceRecords.get(0).getArchiveFileFrequency();
		long lPastProcessLimit = lNow - (nFreq * 2);
		ArrayDeque<OneTimeReentrantLock> oLocks = new ArrayDeque();
		try
		{
			for (int i = -2; i < 1; i++) // determine files that have already been downloaded, files are stored by day so go back two since some products have multiple days available
			{
				oTime.setTimeInMillis(lNow + (i * 86400000));
				Path oDirPath = Paths.get(getDestFilename(oArchiveFile, oTime.getTimeInMillis(), nRange, nDelay)).getParent();
				Files.createDirectories(oDirPath);
				try (DirectoryStream oDir = Files.newDirectoryStream(oDirPath, (oPath -> oPath.toString().endsWith(sExt))))
				{
					List<Path> oFiles = (List)StreamSupport.stream(oDir.spliterator(), false).sorted(TileObsView.PATHREFCOMP).collect(Collectors.toList());
					for (Path oFile : oFiles)
						oFilesOnDisk.add(oFile.toString());
				}
			}
		
			int nExtAdd = m_sEnd.length();
			if (m_sEnd.compareTo("\">") == 0)
				nExtAdd = 0;
			
			for (int i = 0; i < m_nDirs; i++) // for each subdirectory of the NWS product
			{
				ArrayList<String> oFilesToDownload = new ArrayList();
				sIndex.setLength(0);
				long lTimestamp = (lNow / m_nDirDiff) * m_nDirDiff - i * m_nDirDiff;
				String sUrl = m_sBaseURL + m_oUrlExt.format(lTimestamp, 0, 0);
				URL oUrl = new URL(sUrl);
				URLConnection oConn = oUrl.openConnection();
				oConn.setConnectTimeout(m_nTimeout);
				oConn.setReadTimeout(m_nTimeout);
				try (BufferedInputStream oIn = new BufferedInputStream(oConn.getInputStream()))
				{
					int nByte; // copy remote file index to buffer
					while ((nByte = oIn.read()) >= 0)
						sIndex.append((char)nByte);
				}
				catch (Exception oEx)
				{
					if (m_nIndexFails[i]++ > 3) // failed 3 collections in a row for the given directory
					{
						m_nIndexFails[i] = 0;
						m_oLogger.error(oEx, oEx);
						if (oEx instanceof FileNotFoundException)
						{
							int nTemp = m_nErrorRetry;
							m_nErrorRetry = 1800000;
							setError();
							m_nErrorRetry = nTemp;
						}
						else
						{
							setError();
						}
					}
					continue;
				}

				m_nIndexFails[i] = 0;
				int nStart = sIndex.indexOf(m_sInitSkip); // skip to where files are listed
				nStart = sIndex.indexOf(m_sStart, nStart); // find the first file entry
				int nEnd = sIndex.indexOf(m_sEnd, nStart);
				while (nStart >= 0 && nEnd >= 0) // iterate through the entire file index
				{
					String sFile = sIndex.substring(nStart, nEnd + nExtAdd);
					
					if (oRegEx.matcher(sFile).matches())
					{
						String sFullPath = sUrl + sFile;
						int nIndex = Collections.binarySearch(oFilesToDownload, sFullPath, (String o1, String o2) -> o2.compareTo(o1));
						if (nIndex < 0)
							oFilesToDownload.add(~nIndex, sFullPath);
					}

					if (!m_sConSkip.isEmpty()) // skip unnecessary characters
					{
						nStart = sIndex.indexOf(m_sConSkip, nEnd + m_sEnd.length());
						if (nStart < 0)
							continue;
						nEnd = nStart;
					}
					
					nStart = sIndex.indexOf(m_sStart, nEnd); // find the next file entry
					nEnd = sIndex.indexOf(m_sEnd, nStart);
				}
				
				int nDownloadIndex = oFilesToDownload.size();
				String sCurrentFile = null;
				String sCurrentDestFile = null;
				while (nDownloadIndex-- > 0)
				{
					// determine the destination name for source files
					sCurrentFile = oFilesToDownload.get(nDownloadIndex);
					
					int nOffset = 0;
					if (m_bUseNow)
						lTimes[0] = Scheduling.getLastPeriod(m_nDownloadOffset, m_nDownloadPeriod).getTimeInMillis();
					else
						nOffset = m_oSrcFile.parseRecv(sCurrentFile, lTimes);
					
					if (nOffset > m_nFileOffsetLimit || nOffset < m_nFileOffsetStart || (m_nFileIndices != null && java.util.Arrays.binarySearch(m_nFileIndices, nOffset) < 0))
						continue;
					
					long lRecv = lTimes[0] + m_nRecvOffset;
					sCurrentDestFile = getDestFilename(oArchiveFile, lRecv, nRange, nDelay, nOffset);
					if (oFilesOnDisk.contains(sCurrentDestFile) || lRecv < lNow - m_nPastLimit)
						continue;
					
					URL oFileUrl = new URL(sCurrentFile); // retrieve remote data file
					URLConnection oFileConn = oFileUrl.openConnection();
					oFileConn.setConnectTimeout(m_nTimeout);
					oFileConn.setReadTimeout(m_nTimeout);

					if (download(oFileConn, sCurrentFile, sCurrentDestFile))
						oFilesOnDisk.add(sCurrentDestFile);
					
					long lQueueStart;
					long lQueueEnd;
					long lQueueRecv;
					long lFileStart = getStartTime(lRecv, nRange, nDelay, nOffset);
					long lProcessLimit = Long.MAX_VALUE;
					if (m_nTileProcessOffset != Integer.MIN_VALUE)
					{
						lQueueStart  = lFileStart;
						if (nOffset != 0)
						{
							long lStartOfFileSet = getStartTime(lRecv, nRange, nDelay, 0);
							lQueueEnd = getEndTime(lRecv, nRange, nDelay, nOffset);
							lProcessLimit = lStartOfFileSet + m_nTileProcessOffset;
						}
						else
						{
							lQueueEnd = lProcessLimit = lQueueStart + m_nTileProcessOffset;
						}
					}
					else
					{
						lQueueStart = lFileStart;
						lQueueEnd = getEndTime(lRecv, nRange, nDelay, nOffset);
					}
					lQueueRecv = lRecv;
					
					if (lFileStart < lProcessLimit && lRecv >= lPastProcessLimit)
					{
						oLocks.addLast(processRealTime(oResourceRecords, lQueueStart, lQueueEnd, lQueueRecv));
					}
				}
			}
			while (!oLocks.isEmpty())
			{
				OneTimeReentrantLock oLock = oLocks.pollFirst();
				if (!oLock.hasBeenLocked())
					oLocks.addLast(oLock);
				else
				{
					oLock.lock();
					oLock.unlock();
				}
			}

			m_oLogger.debug("All done");
		}
		catch (Exception oException)
		{
			m_oLogger.error(oException, oException);
		}
	}

	
	/**
	 * Sets a schedule to execute once in 10 seconds so the block is initialed 
	 * and started before trying to download files because that can take a long
	 * time and we don't want the stores to wait on a bulk download to start, and
	 * a schedule to execute on a fixed interval.
	 * @return
	 * @throws Exception
	 */
	@Override
	public boolean start() throws Exception
	{
		if (m_bCollectRT)
		{
			m_nSchedId = Scheduling.getInstance().createSched(this, m_nOffset, m_nPeriod);
		}
		return true;
	}
	
	
	/**
	 * Set the {@link imrcp.system.BaseBlock#m_nStatus} to {@link imrcp.system.ImrcpBlock#ERROR}
	 * and then create a timer to schedule the block to call {@link NWS#setIdle()}
	 * after the configured time.
	 */
	@Override
	protected void setError()
	{
		super.setError();
		new Timer().schedule(new TimerTask() {@Override public void run(){setIdle();}}, m_nErrorRetry);
	}
	
	
	/**
	 * Set the {@link imrcp.system.BaseBlock#m_nStatus} to {@link imrcp.system.ImrcpBlock#IDLE}
	 */
	private void setIdle()
	{
		checkAndSetStatus(IDLE, ERROR);
	}
	
	
	protected boolean download(URLConnection oFileConn, String sSrc, String sDest)
	{
		try
		{
			m_oLogger.info("Downloading file: " + sDest);
			ByteArrayOutputStream oBaos = new ByteArrayOutputStream(m_nDefaultBuffer);
			try (InputStream oInStream = (sSrc.endsWith(".gz") ? new GZIPInputStream(oFileConn.getInputStream()) : new BufferedInputStream(oFileConn.getInputStream()))) // try for each file because sometimes the site says a file is there but it is actually not on their server yet. decompress the .gz files we get because they are already grb2 compressed and NetCDF library decompresses the files anyway
			{
				int nByte;
				while ((nByte = oInStream.read()) >= 0)
					oBaos.write(nByte);
			}
			if (oBaos.size() > 0)
			{
				oBaos = filter(oBaos);
				if (oBaos == null)
					return false;
				try (BufferedOutputStream oFileOut = new BufferedOutputStream(Files.newOutputStream(Paths.get(sDest))))
				{
					oBaos.writeTo(oFileOut);
				}
				m_oLogger.info("Download finished: " + sDest);
				return true;
			}
		}
		catch (SocketTimeoutException | FileNotFoundException oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
			setError();
		}
		return false;
	}
	
	
	protected ByteArrayOutputStream filter(ByteArrayOutputStream oFile)
	{
		return oFile;
	}

	@Override
	protected void createFiles(TileFileInfo oInfo, TreeSet<Path> oArchiveFiles)
	{
		try
		{
//			if (oArchiveFiles.isEmpty())
//			{
//				for (ResourceRecord oRR : oInfo.m_oRRs)
//				{
//					int nPeriod = m_nDownloadPeriod * 1000;
//					int nOffset = m_nDownloadOffset * 1000;
//					long lRecv = oInfo.m_lStart / nPeriod * nPeriod + nOffset;
//					long lStart = lRecv + oRR.getDelay();
//					long lEnd = lStart + oRR.getRange();
//					FilenameFormatter oFf = new FilenameFormatter(oRR.getTiledFf());
//					Path oPath = oRR.getFilename(lRecv, lStart, lEnd, oFf);
//					Files.createDirectories(oPath.getParent(), FileUtil.DIRPERS);
//					if (!Files.exists(oPath))
//						Files.createFile(oPath);
//				}
//			}
			long[] lParsedTimes = new long[3];
			HashMap<String, NetcdfFile> oCachedFiles = new HashMap();
			long lStart = oInfo.m_lStart;
			long lEnd = oInfo.m_lEnd;
			long lRecv = oInfo.m_lRef;

			for (Path oFile : oArchiveFiles)
			{
				String sFilepath = oFile.toString();
				if (!oCachedFiles.containsKey(sFilepath))
				{
					NetcdfFile oNcFile;
					try
					{
						oNcFile = NetcdfFile.open(sFilepath);
						oCachedFiles.put(sFilepath, oNcFile);
					}
					catch (IOException oEx)
					{
						Files.deleteIfExists(oFile);
					}
				}
			}
			for (ResourceRecord oRR : oInfo.m_oRRs)
			{
				if (oRR.getHrz() == null || oRR.getHrz().isEmpty())
					continue;
				FilenameFormatter oArchive = new FilenameFormatter(oRR.getArchiveFf());
				for (Path oFile : oArchiveFiles)
				{
					if (!Files.exists(oFile))
						continue;
					oArchive.parse(oFile.toString(), lParsedTimes);
					lRecv = lParsedTimes[FilenameFormatter.VALID];
					NetcdfFile oNcFile = oCachedFiles.get(oFile.toString());
					
					double[] dHrz = fromArray(oNcFile, oRR.getHrz()); // sort order varies
					double[] dVrt = fromArray(oNcFile, oRR.getVrt());
					double[] dTime = fromArray(oNcFile, oRR.getTime());
					if (dTime.length == 1)
						dTime[0] = lParsedTimes[FilenameFormatter.START];
					else
					{
						if (dTime[0] == dTime[1])
						{
							dTime = new double[]{lParsedTimes[FilenameFormatter.START]};
						}
						else
						{
							SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
							oSdf.setTimeZone(TimeZone.getTimeZone("UTC"));
							Variable oTimeVar = oNcFile.getRootGroup().findVariable(oRR.getTime());
							String sUnits = oTimeVar.getUnitsString();
							String sTs = sUnits.substring(sUnits.lastIndexOf(" "));
							long lTime = oSdf.parse(sTs).getTime();
							int nMultipier = 3600000; // default units is hours
							if (sUnits.toLowerCase().startsWith("minute")) // check if file is in minutes
								nMultipier = 60000;

							int nOffset = 0;
							if (oRR.getObsTypeId() == ObsType.RTEPC && oRR.getContribId() == Integer.valueOf("ndfd", 36)) // ndfd qpf files
								nOffset = 21600000;

							for (int nIndex = 0; nIndex < dTime.length; nIndex++)
							{
								dTime[nIndex] = lTime + dTime[nIndex] * nMultipier - nOffset;
							}
						}
					}
					double[] dCorners = new double[8];
					ArrayList<GridDatatype> oGrids = new ArrayList(oRR.getLabels().length);
					for (GridDatatype oGrid : new GridDataset(new NetcdfDataset(oNcFile)).getGrids())
					{
						String sGrid = oGrid.getName();
						for (String sObsLabel : oRR.getLabels())
						{
							if (sObsLabel.contains(sGrid))
								oGrids.add(oGrid);
						}
					}

					if (oGrids.isEmpty())
					{
						m_oLogger.info(String.format("No grids tile file for %s %s", Integer.toString(oRR.getContribId(), 36), Integer.toString(oRR.getObsTypeId(), 36)));
						continue;
					}


					ProjectionImpl oProj = oGrids.get(0).getProjection();
					VariableDS oVar = oGrids.get(0).getVariable();

					ProjProfile oProfile = ProjProfiles.getInstance().newProfile(dHrz, dVrt, oProj, 0);
					oProfile.getCell(0, 0, dCorners);
					int[] nMinMax = Arrays.newIntArray();
					nMinMax = Arrays.add(nMinMax, new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE});
					nMinMax = Arrays.addAndUpdate(nMinMax, GeoUtil.toIntDeg(dCorners[ProjProfile.xTL]), GeoUtil.toIntDeg(dCorners[ProjProfile.yTL]));
					nMinMax = Arrays.addAndUpdate(nMinMax, GeoUtil.toIntDeg(dCorners[ProjProfile.xBR]), GeoUtil.toIntDeg(dCorners[ProjProfile.yBR]));
					oProfile.getCell(dHrz.length - 2, dVrt.length - 2, dCorners);
					nMinMax = Arrays.addAndUpdate(nMinMax, GeoUtil.toIntDeg(dCorners[ProjProfile.xTL]), GeoUtil.toIntDeg(dCorners[ProjProfile.yTL]));
					nMinMax = Arrays.addAndUpdate(nMinMax, GeoUtil.toIntDeg(dCorners[ProjProfile.xBR]), GeoUtil.toIntDeg(dCorners[ProjProfile.yBR]));
					oProfile.getCell(0, dVrt.length - 2, dCorners);
					nMinMax = Arrays.addAndUpdate(nMinMax, GeoUtil.toIntDeg(dCorners[ProjProfile.xTL]), GeoUtil.toIntDeg(dCorners[ProjProfile.yTL]));
					nMinMax = Arrays.addAndUpdate(nMinMax, GeoUtil.toIntDeg(dCorners[ProjProfile.xBR]), GeoUtil.toIntDeg(dCorners[ProjProfile.yBR]));
					oProfile.getCell(dHrz.length - 2, 0, dCorners);
					nMinMax = Arrays.addAndUpdate(nMinMax, GeoUtil.toIntDeg(dCorners[ProjProfile.xTL]), GeoUtil.toIntDeg(dCorners[ProjProfile.yTL]));
					nMinMax = Arrays.addAndUpdate(nMinMax, GeoUtil.toIntDeg(dCorners[ProjProfile.xBR]), GeoUtil.toIntDeg(dCorners[ProjProfile.yBR]));
					int[] nBB;
					if (oRR.getBoundingBox()[0] == Integer.MIN_VALUE)
						nBB = new int[]{nMinMax[1], nMinMax[2], nMinMax[3], nMinMax[4]};
					else
						nBB = oRR.getBoundingBox();
					Array oMerged = null;
					ArrayList<NWSTileFileWriterJni> oWriters = new ArrayList();
					try
					{
						for (int nT = 0; nT < dTime.length; nT++)
						{
							if (dTime.length > 1 && (dTime[nT] < lStart || dTime[nT] >= lEnd))
								continue;
							NWSTileFileWriterJni oTFWriter = (NWSTileFileWriterJni)Class.forName(oRR.getClassName()).getDeclaredConstructor().newInstance();
							oTFWriter.merge(oGrids, oRR, oMerged);
							if (oMerged == null)
								oMerged = oTFWriter.getDataArray();
							oTFWriter.setValuesForCall(oProj, oVar, dHrz, dVrt, dTime, nT, oRR, lParsedTimes[FilenameFormatter.END], lRecv, nBB, m_oLogger, oFile.toString());
							oWriters.add(oTFWriter);
						}
						Scheduling.processCallables(oWriters, m_nThreads);
					}
					catch (Exception oEx)
					{
						m_oLogger.error(oEx, oEx);
					}
				}
			}

			for (NetcdfFile oNcFile : oCachedFiles.values())
			{
				oNcFile.close();
				Files.deleteIfExists(Paths.get(oNcFile.getLocation() + ".gbx9"));
				Files.deleteIfExists(Paths.get(oNcFile.getLocation() + ".ncx3"));
			}
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
	
	
	protected static double[] fromArray(NetcdfFile oNcFile, String sName)
	   throws IOException
	{
		Array oArray = oNcFile.getRootGroup().findVariable(sName).read();
		int nSize = (int)oArray.getSize();

		double[] dArray = new double[nSize]; // reserve capacity
		for (int nIndex = 0; nIndex < nSize; nIndex++)
			dArray[nIndex] = oArray.getDouble(nIndex); // copy values

		return dArray;
	}
}
