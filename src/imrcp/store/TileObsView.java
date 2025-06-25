/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.store;

import imrcp.geosrv.WayNetworks;
import imrcp.geosrv.osm.OsmWay;
import imrcp.system.BaseBlock;
import imrcp.system.Directory;
import imrcp.system.FilenameFormatter;
import imrcp.system.Id;
import imrcp.system.Introsort;
import imrcp.system.ObsType;
import imrcp.system.OneTimeReentrantLock;
import imrcp.system.ResourceRecord;
import imrcp.collect.TileFileWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Federal Highway Administration
 */
public class TileObsView extends BaseBlock
{	
	public final static Comparator<String> REFTIMECOMP = (String o1, String o2) -> 
	{
		int nIndex1 = o1.lastIndexOf("_") + 1;
		String s1 = o1.substring(nIndex1, o1.indexOf(".", nIndex1));
		int nIndex2 = o2.lastIndexOf("_") + 1;
		String s2 = o2.substring(nIndex2, o2.indexOf(".", nIndex2));
		int nReturn = s2.compareTo(s1);
		if (nReturn == 0)
		{
			nIndex1 = o1.lastIndexOf("_", nIndex1 - 2);
			nIndex1 = o1.lastIndexOf("_", nIndex1 - 1) + 1;
			s1 = o1.substring(nIndex1, o1.indexOf("_", nIndex1));
			nIndex2 = o2.lastIndexOf("_", nIndex2 - 2);
			nIndex2 = o1.lastIndexOf("_", nIndex2 - 1) + 1;
			s2 = o2.substring(nIndex2, o2.indexOf("_", nIndex2));
			nReturn = s1.compareTo(s2);
		}
		return nReturn;
	};
	public final static Comparator<Path> PATHREFCOMP = (Path o1, Path o2) -> REFTIMECOMP.compare(o1.toString(), o2.toString());
	private final static Logger SEARCHLOGGER = LogManager.getLogger(TileObsView.class.getName());
	
	
	/**
	 * Wrapper for {@link #getData(int, long, long, int, int, int, int, long, imrcp.system.Id)}
	 * with {@link imrcp.system.Id#NULLID} passed as the object id.
	 * 
	 * @param nType IMRCP observation id
	 * @param lStartTime start time in milliseconds since Epoch
	 * @param lEndTime end time in milliseconds since Epoch
	 * @param nStartLat minimum latitude in decimal degrees scaled to 7 decimal places
	 * @param nEndLat maximum latitude in decimal degrees scaled to 7 decimal places
	 * @param nStartLon minimum longitude in decimal degrees scaled to 7 decimal places
	 * @param nEndLon maximum longitude in decimal degrees scaled to 7 decimal places
	 * @param lRefTime reference time in milliseconds since Epoch
	 * @return a ResultSet with 0 or more Obs that are valid for the query
	 */
	@Override
	public ObsList getData(int nType, long lStartTime, long lEndTime,
	   int nStartLat, int nEndLat, int nStartLon, int nEndLon, long lRefTime)
	{
		return getData(nType, lStartTime, lEndTime, nStartLat, nEndLat, nStartLon, nEndLon, lRefTime, Id.NULLID, new int[0]);
	}
	
	
	/**
	 * Wrapper for {@link #getData(int, long, long, int, int, int, int, long, imrcp.system.Id)}
	 * with {@link imrcp.system.Id#NULLID} passed as the object id.
	 * 
	 * @param nType IMRCP observation id
	 * @param lStartTime start time in milliseconds since Epoch
	 * @param lEndTime end time in milliseconds since Epoch
	 * @param nStartLat minimum latitude in decimal degrees scaled to 7 decimal places
	 * @param nEndLat maximum latitude in decimal degrees scaled to 7 decimal places
	 * @param nStartLon minimum longitude in decimal degrees scaled to 7 decimal places
	 * @param nEndLon maximum longitude in decimal degrees scaled to 7 decimal places
	 * @param lRefTime reference time in milliseconds since Epoch
	 * @return a ResultSet with 0 or more Obs that are valid for the query
	 */
	public ObsList getData(int nType, long lStartTime, long lEndTime,
	   int nStartLat, int nEndLat, int nStartLon, int nEndLon, long lRefTime, Id oObjId)
	{
		return getData(nType, lStartTime, lEndTime, nStartLat, nEndLat, nStartLon, nEndLon, lRefTime, oObjId, new int[0]);
	}
	
	
		/**
	 * Wrapper for {@link #getData(int, long, long, int, int, int, int, long, imrcp.system.Id)}
	 * with {@link imrcp.system.Id#NULLID} passed as the object id.
	 * 
	 * @param nType IMRCP observation id
	 * @param lStartTime start time in milliseconds since Epoch
	 * @param lEndTime end time in milliseconds since Epoch
	 * @param nStartLat minimum latitude in decimal degrees scaled to 7 decimal places
	 * @param nEndLat maximum latitude in decimal degrees scaled to 7 decimal places
	 * @param nStartLon minimum longitude in decimal degrees scaled to 7 decimal places
	 * @param nEndLon maximum longitude in decimal degrees scaled to 7 decimal places
	 * @param lRefTime reference time in milliseconds since Epoch
	 * @return a ResultSet with 0 or more Obs that are valid for the query
	 */
	@Override
	public ObsList getData(int nType, long lStartTime, long lEndTime,
	   int nStartLat, int nEndLat, int nStartLon, int nEndLon, long lRefTime, int[] nContrib)
	{
		return getData(nType, lStartTime, lEndTime, nStartLat, nEndLat, nStartLon, nEndLon, lRefTime, Id.NULLID, nContrib);
	}
	
	
	public ObsList getPreferedData(int nObstype, int[] nBb, long lStartQuery, long lEndQuery, long lRef)
	{
		ArrayList<ResourceRecord> oRRs = Directory.getResourcesByObsType(nObstype);
		Introsort.usort(oRRs, ResourceRecord.COMP_BY_PREF);
		int[] nContribAndSource = new int[2];
		for (ResourceRecord oTemp : oRRs)
		{
			nContribAndSource[0] = oTemp.getContribId();
			nContribAndSource[1] = oTemp.getSourceId();
			ObsList oData = getData(nObstype, lStartQuery, lEndQuery,
			 nBb[1], nBb[3], nBb[0], nBb[2], lRef, nContribAndSource);
			if (oData.m_bHasData || !oData.isEmpty())
				return oData;
		}
		
		return new ObsList();
	}
	
	public ObsList getData(int nObsTypeId, long lStartTime, long lEndTime,
	   int nStartLat, int nEndLat, int nStartLon, int nEndLon, long lRefTime, Id oObjId, int[] nContribAndSources)
	{
		ObsList oData = new ObsList();
		int nTemp = nStartLat; // ensure that startlat/lon <= endlat/lon
		if (nEndLat < nStartLat)
		{
			nStartLat = nEndLat;
			nEndLat = nTemp;
		}
		nTemp = nStartLon;
		if (nEndLon < nStartLon)
		{
			nStartLon = nEndLon;
			nEndLon = nTemp;
		}
		
		ArrayList<ResourceRecord> oRRs = Directory.getResourcesByObsType(nObsTypeId);
		if (nContribAndSources.length > 0 && nContribAndSources.length % 2 == 0)
		{
			ArrayList<ResourceRecord> oTemp = new ArrayList(oRRs.size());
			int nIndex = oRRs.size();
			while (nIndex-- > 0)
			{
				ResourceRecord oTempRR = oRRs.get(nIndex);
				for (int nContribSource = 0; nContribSource < nContribAndSources.length;)
				{
					int nContrib = nContribAndSources[nContribSource++];
					int nSource = nContribAndSources[nContribSource++];
					if (nContrib == oTempRR.getContribId() && (nSource == Integer.MIN_VALUE || nSource == oTempRR.getSourceId()))
					{
						oTemp.add(oRRs.get(nIndex));
						break;
					}
				}
			}
			
			oRRs = oTemp;
		}
		
		if (nObsTypeId == ObsType.VARIES)
		{
			Introsort.usort(oRRs, ResourceRecord.COMP_BY_CONTRIB_OBSTYPE);
			ArrayList<ResourceRecord> oTemp = new ArrayList();
			int nPrevContrib = Integer.MIN_VALUE;
			for (ResourceRecord oRR : oRRs)
			{
				if (nPrevContrib != oRR.getContribId())
				{
					nPrevContrib = oRR.getContribId();
					if (oRR.getInVaries())
						oTemp.add(oRR);
					else
					{
						for (ResourceRecord oOtherObs : Directory.getResourcesByContrib(nPrevContrib))
						{
							if (oOtherObs.getObsTypeId() != ObsType.VARIES && !oOtherObs.getTiledFf().isEmpty())
								oTemp.add(oOtherObs);
						}
					}
				}
			}
			
			oRRs = oTemp;
		}
		
		for (ResourceRecord oRR : oRRs)
		{
			long lObsTime = lStartTime;
			FilenameFormatter oFf = new FilenameFormatter(oRR.getTiledFf());
			String sObsType = Integer.toString(oRR.getObsTypeId(), 36);
			int nMaxFcst = oRR.getMaxFcst();
			int nFreq = oRR.getTileFileFrequency();
			int nCount = oRR.getTileSearchOffset() / nFreq;
			if (nCount == 0)
				nCount = 1;
			TreeSet<Path> oChecked = new TreeSet(PATHREFCOMP);
			boolean bCanReprocess = true;
			while (lObsTime < lEndTime)
			{
				Path[] oBestFiles = new Path[nCount];
				long lNextInterval = lObsTime + nFreq;
				if (lNextInterval > lEndTime)
					lNextInterval = lEndTime;
				long lSearchStart = (lRefTime + nMaxFcst) / 86400000 * 86400000; // floor search times to the nearest day since directories are by day
				long lSearchEnd = (lRefTime - nMaxFcst) / 86400000 * 86400000;
				ArrayList<String> oSearchedDirs = new ArrayList();
				int nPathIndex = search(oBestFiles, lSearchStart, lSearchEnd, lObsTime, lNextInterval, lRefTime, sObsType, oFf, oRR, oSearchedDirs);
				if (nPathIndex < oBestFiles.length)
				{
					lSearchStart = (lObsTime + nMaxFcst) / 86400000 * 86400000;
					lSearchEnd = (lObsTime - nMaxFcst) / 86400000 * 86400000;
					nPathIndex = search(oBestFiles, lSearchStart, lSearchEnd, lObsTime, lNextInterval, lRefTime, sObsType, oFf, oRR, oSearchedDirs);
					if (nPathIndex < oBestFiles.length)
					{
						lSearchStart = (lNextInterval + nMaxFcst) / 86400000 * 86400000;
						lSearchEnd = (lNextInterval - nMaxFcst) / 86400000 * 86400000;
						search(oBestFiles, lSearchStart, lSearchEnd, lObsTime, lNextInterval, lRefTime, sObsType, oFf, oRR, oSearchedDirs);
					}
				}

				long lThisInterval = lObsTime;
				lObsTime = lNextInterval;
				boolean bTileFileExists = false;
				for (Path oPath : oBestFiles)
				{
					if (oPath == null)
						continue;
					bTileFileExists = true;
					bCanReprocess = true;
					if (oChecked.add(oPath))
					{
						try
						{
							TileFileReader.parseFile(oPath, oData, nObsTypeId, nStartLon, nStartLat, nEndLon, nEndLat, lStartTime, lEndTime, lRefTime, oRR);
						}
						catch (IOException oEx)
						{
							//m_oLogger.error(oEx, oEx);
						}
					}
				}
				if (!bTileFileExists && bCanReprocess)
				{
					if (!oRR.getReprocess())
						continue;
//					SEARCHLOGGER.debug("reprocess hit");
					TileFileWriter oWriter = (TileFileWriter)Directory.getInstance().lookup(oRR.getWriter());
					if (oWriter == null)
						continue;
					
					ArrayList<ResourceRecord> oTempRRs = new ArrayList(1);
					oTempRRs.add(oRR);
					
					OneTimeReentrantLock oLock = oWriter.queueRequest(oTempRRs, lThisInterval, lNextInterval, lRefTime);
					boolean bWasLocked = oLock.isLocked() || oLock.hasBeenLocked();
					try
					{
						if (oLock.tryLock(20, TimeUnit.SECONDS))
							oLock.unlock();
					}
					catch (InterruptedException oEx)
					{
					}
					if (bWasLocked)
					{
						lObsTime = lThisInterval;
						bCanReprocess = false;
						continue;
					}
				}
				bCanReprocess = true;
			}
		}
		
		ObsList oReturn = new ObsList();
		oReturn.m_bHasData = oData.m_bHasData;
		WayNetworks oWays = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
		for (int i = 0; i < oData.size(); i++)
		{
			Obs oObs = (Obs)oData.get(i);
			boolean bNullObj = oObjId == null || Id.isNull(oObjId) || Id.isNull(oObs.m_oObjId);
			if (bNullObj || (!bNullObj && Id.COMPARATOR.compare(oObs.m_oObjId, oObjId) == 0)) // check the object id is valid
			{
				if (oObs.m_sStrings == null && oObs.m_nObsTypeId != ObsType.EVT)
				{
					OsmWay oSeg = null;
					if (Id.isSegment(oObs.m_oObjId)) // if the obs is associated with a roadway segment
						oSeg = oWays.getWayById(oObs.m_oObjId);
					if (oSeg != null)
						oObs.m_sStrings = new String[]{oSeg.m_sName, null, null, null, null, null, null, null}; // set the detail to the name of the roadway
				}
				oReturn.add(oObs);
			}
		}
		return oReturn;
	}
	
	
	public static int search(Path[] oBestFiles, long lSearchTime, long lSearchEnd, long lQueryTime, long lQueryEnd, long lRefTime, String sObsType, FilenameFormatter oFf, ResourceRecord oRR, ArrayList<String> oSearchedDirs)
	{
		if (oFf.getPattern().isEmpty())
			return 0;
		long[] lTimes = new long[3];
		String sEndsWith = oFf.getExtension();
		int nPathIndex = 0;
		for (;nPathIndex < oBestFiles.length; nPathIndex++)
		{
			if (oBestFiles[nPathIndex] == null)
				break;
		}
		while (lSearchTime >= lSearchEnd)
		{
			Path oDailyDir = oRR.getFilename(lSearchTime, 0, 0, oFf).getParent();
			lSearchTime -= 86400000;
			int nSearchIndex = Collections.binarySearch(oSearchedDirs, oDailyDir.toString());
			if (nSearchIndex < 0)
				oSearchedDirs.add(~nSearchIndex, oDailyDir.toString());
			if (nSearchIndex >= 0 || !Files.exists(oDailyDir))
				continue;
			
			try (DirectoryStream oDir = Files.newDirectoryStream(oDailyDir, (oPath -> oPath.toString().endsWith(sEndsWith))))
			{
				List<Path> oFiles = (List)StreamSupport.stream(oDir.spliterator(), false).sorted(PATHREFCOMP).collect(Collectors.toList());
				for (Path oFile : oFiles)
				{
					oFf.parse(oFile.toString(), lTimes);
					if (lTimes[FilenameFormatter.VALID] <= lRefTime && lTimes[FilenameFormatter.END] > lQueryTime && lTimes[FilenameFormatter.START] < lQueryEnd)
					{
						oBestFiles[nPathIndex++] = oFile;
						if (nPathIndex == oBestFiles.length)
							return nPathIndex;
					}
				}
			}
			catch (IOException oEx)
			{
				SEARCHLOGGER.error(oEx, oEx);
			}
		}
		
		return nPathIndex;
	}
	
	
	public static void getFiles(TreeSet<Path> oAllFiles, long lSearchTime, long lSearchEnd, long lQueryTime, long lQueryEnd, long lRefTime, String sObsType, FilenameFormatter oFf, ResourceRecord oRR, ArrayList<String> oSearchedDirs)
	{
		long[] lTimes = new long[3];
		String sEndsWith = oFf.getExtension();
		while (lSearchTime >= lSearchEnd)
		{
			Path oDailyDir = oRR.getFilename(lSearchTime, 0, 0, oFf).getParent();
			lSearchTime -= 86400000;
			int nSearchIndex = Collections.binarySearch(oSearchedDirs, oDailyDir.toString());
			if (nSearchIndex < 0)
				oSearchedDirs.add(~nSearchIndex, oDailyDir.toString());
			if (nSearchIndex >= 0 || !Files.exists(oDailyDir))
				continue;
			
			try (DirectoryStream oDir = Files.newDirectoryStream(oDailyDir, (oPath -> oPath.toString().endsWith(sEndsWith))))
			{
				List<Path> oFiles = (List)StreamSupport.stream(oDir.spliterator(), false).sorted(PATHREFCOMP).collect(Collectors.toList());
				for (Path oFile : oFiles)
				{
					oFf.parse(oFile.toString(), lTimes);
					if (lTimes[FilenameFormatter.VALID] <= lRefTime && lTimes[FilenameFormatter.END] > lQueryTime && lTimes[FilenameFormatter.START] < lQueryEnd)
						oAllFiles.add(oFile);
				}
			}
			catch (IOException oEx)
			{
				SEARCHLOGGER.error(oEx, oEx);
			}
		}
						
	}
	
	
	public static TreeSet<Path> getArchiveFiles(long lStartTime, long lEndTime, long lRefTime, ResourceRecord oRR)
	{
		TreeSet<Path> oReturn = new TreeSet(PATHREFCOMP);
		FilenameFormatter oFf = new FilenameFormatter(oRR.getArchiveFf());
		String sObsType = Integer.toString(oRR.getObsTypeId(), 36);
		int nFreq = oRR.getArchiveFileFrequency();
		int nCount = oRR.getArchiveSearchOffset() / nFreq;
		if (nCount == 0)
			nCount = 1;
		int nMaxFcst = oRR.getMaxFcst();
		long lObsTime = lStartTime;
		while (lObsTime < lEndTime)
		{
			long lNextInterval = lObsTime + nFreq;
			long lSearchStart = (lRefTime + nMaxFcst) / 86400000 * 86400000; // floor search times to the nearest day since directories are by day
			long lSearchEnd = (lRefTime - nMaxFcst) / 86400000 * 86400000;
			ArrayList<String> oSearchedDirs = new ArrayList();
			getFiles(oReturn, lSearchStart, lSearchEnd, lStartTime, lEndTime, lRefTime, sObsType, oFf, oRR, oSearchedDirs);
			lSearchStart = (lObsTime + nMaxFcst) / 86400000 * 86400000;
			lSearchEnd = (lObsTime - nMaxFcst) / 86400000 * 86400000;
			getFiles(oReturn, lSearchStart, lSearchEnd, lStartTime, lEndTime, lRefTime, sObsType, oFf, oRR, oSearchedDirs);
			lSearchStart = (lNextInterval + nMaxFcst) / 86400000 * 86400000;
			lSearchEnd = (lNextInterval - nMaxFcst) / 86400000 * 86400000;
			getFiles(oReturn, lSearchStart, lSearchEnd, lStartTime, lEndTime, lRefTime, sObsType, oFf, oRR, oSearchedDirs);
			lObsTime = lNextInterval;
		}
		
		return oReturn;
	}
	
	
	public static TreeSet<Path> searchArchive(long lStartTime, long lEndTime, long lRefTime, ResourceRecord oRR)
	{
		TreeSet<Path> oReturn = new TreeSet(PATHREFCOMP);
		FilenameFormatter oFf = new FilenameFormatter(oRR.getArchiveFf());
		String sObsType = Integer.toString(oRR.getObsTypeId(), 36);
		int nFreq = oRR.getArchiveFileFrequency();
		int nCount = oRR.getArchiveSearchOffset() / nFreq;
		if (nCount == 0)
			nCount = 1;
		int nMaxFcst = oRR.getMaxFcst();
		long lObsTime = lStartTime;
		while (lObsTime < lEndTime)
		{
			Path[] oBestFiles = new Path[nCount];
			long lNextInterval = lObsTime + nFreq;
			long lSearchStart = (lRefTime + nMaxFcst) / 86400000 * 86400000; // floor search times to the nearest day since directories are by day
			long lSearchEnd = (lRefTime - nMaxFcst) / 86400000 * 86400000;
			ArrayList<String> oSearchedDirs = new ArrayList();
			int nPathIndex = search(oBestFiles, lSearchStart, lSearchEnd, lStartTime, lEndTime, lRefTime, sObsType, oFf, oRR, oSearchedDirs);
			if (nPathIndex < oBestFiles.length)
			{
				lSearchStart = (lObsTime + nMaxFcst) / 86400000 * 86400000;
				lSearchEnd = (lObsTime - nMaxFcst) / 86400000 * 86400000;
				nPathIndex = search(oBestFiles, lSearchStart, lSearchEnd, lStartTime, lEndTime, lRefTime, sObsType, oFf, oRR, oSearchedDirs);
				if (nPathIndex < oBestFiles.length)
				{
					lSearchStart = (lNextInterval + nMaxFcst) / 86400000 * 86400000;
					lSearchEnd = (lNextInterval - nMaxFcst) / 86400000 * 86400000;
					search(oBestFiles, lSearchStart, lSearchEnd, lStartTime, lEndTime, lRefTime, sObsType, oFf, oRR, oSearchedDirs);
				}
			}

			lObsTime = lNextInterval;
			for (int nIndex = 0; nIndex < nPathIndex; nIndex++)
				oReturn.add(oBestFiles[nIndex]);
		}
		return oReturn;
	}
}
