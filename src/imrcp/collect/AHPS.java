package imrcp.collect;

import imrcp.comp.InundationRecord;
import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.Mercator;
import imrcp.store.Obs;
import imrcp.system.Directory;
import imrcp.system.FilenameFormatter;
import imrcp.system.Id;
import imrcp.system.Introsort;
import imrcp.system.ObsType;
import imrcp.system.ResourceRecord;
import imrcp.system.Scheduling;
import imrcp.system.StringPool;
import imrcp.system.Util;
import imrcp.system.XzBuffer;
import imrcp.system.dbf.DbfResultSet;
import imrcp.system.shp.ShpReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.json.JSONObject;

/**
 * Collects .shp files from the National Weather Service's Advanced Hydrologic
 * Prediction Services that contain observed and forecasted flood stages for 
 * rivers and streams across the United States.
 * 
 * @author aaron.cherney
 */
public class AHPS extends Collector
{
	/**
	 * The last time the file was modified on the AHPS website
	 */
	private String m_sLastModified = "";
	
	private String m_sInundationDir;
	private String m_sDateFormat;

	/**
	 * Default constructor.
	 */
	public AHPS()
	{
	}

	
	/**
	 * Attempts to download the file and then sets a schedule to execute on a 
	 * fixed interval.
	 * 
	 * @return true if no Exceptions are thrown
	 * @throws Exception
	 */
	@Override
	public boolean start() throws Exception
	{
		if (m_bCollectRT)
			m_nSchedId = Scheduling.getInstance().createSched(this, m_nOffset, m_nPeriod);
		return true;
	}

	
	/**
	 * 
	 */
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_sInundationDir = oBlockConfig.optString("inundationdir", "ahps_inundation/");
		if (!m_sInundationDir.endsWith("/"))
			m_sInundationDir += "/";
		m_sInundationDir = m_sArchPath + m_sInundationDir;
		m_sDateFormat = oBlockConfig.optString("dateformat", "yyyy-MM-dd HH:mm");
	}

	
	/**
	 * Attempts to make a connection to the AHPS website and downloads a data file
	 * if there is a new one available
	 */
	@Override
	public void execute()
	{
		try
		{
			URL oUrl = new URL(m_sBaseURL);
			URLConnection oConn = oUrl.openConnection();
			oConn.setReadTimeout(60000);
			oConn.setConnectTimeout(60000);
			StringBuilder sBuffer = new StringBuilder();
			try (BufferedInputStream oIn = new BufferedInputStream(oConn.getInputStream())) // last modified/updated is not in the header for the url so must skim the html
			{
				int nByte;
				while ((nByte = oIn.read()) >= 0)
					sBuffer.append((char)nByte);
			}
			String sSrc = m_oSrcFile.format(0, 0, 0); // not time dependent
			int nIndex = sBuffer.indexOf(sSrc);
			nIndex = sBuffer.indexOf("</a>", nIndex) + "</a>".length() + 1;
			while ((Character.isWhitespace(sBuffer.charAt(nIndex++)))); // skip whitespace to get to date
			--nIndex;
			String sLastModified = sBuffer.substring(nIndex, nIndex + m_sDateFormat.length());
			if (sLastModified.compareTo(m_sLastModified) != 0)
			{
				SimpleDateFormat oDate = new SimpleDateFormat(m_sDateFormat);
				oDate.setTimeZone(TimeZone.getTimeZone("UTC"));
				long lTimestamp = oDate.parse(sLastModified).getTime();
				ResourceRecord oRR = null;
				for (ResourceRecord oTemp : Directory.getResourcesByContribSource(m_nContribId, m_nSourceId))
				{
					if (oTemp.getObsTypeId() == m_nObsTypeId)
					{
						oRR = oTemp;
						break;
					}
				}
				if (oRR == null)
					throw new Exception("missing resource record");
				Path oArchive = Paths.get(new FilenameFormatter(oRR.getArchiveFf()).format(lTimestamp, lTimestamp, lTimestamp + oRR.getRange()));
				if (Files.exists(oArchive))
					return;
				m_sLastModified = sLastModified; // update last modified
				
				oUrl = new URL(m_sBaseURL + sSrc);
				oConn = oUrl.openConnection();
				oConn.setConnectTimeout(60000);
				oConn.setReadTimeout(60000);
				byte[] yFile = new byte[oConn.getContentLength()];
				m_oLogger.debug("Downloading " + sSrc);
				try (BufferedInputStream oIn = new BufferedInputStream(oConn.getInputStream()))
				{
					int nOffset = 0;
					int nBytesRead = 0;
					while (nOffset < yFile.length && (nBytesRead = oIn.read(yFile, nOffset, yFile.length - nOffset)) >= 0)
						nOffset += nBytesRead;
				}
				Files.createDirectories(oArchive.getParent());
				try (BufferedOutputStream oOut = new BufferedOutputStream(Files.newOutputStream(oArchive)))
				{
					oOut.write(yFile);
				}
				ArrayList<ResourceRecord> oRRs = new ArrayList(1);
				oRRs.add(oRR);
				processRealTime(oRRs, lTimestamp, lTimestamp, lTimestamp);
			}
		}
		catch (Exception oException)
		{
			m_oLogger.error(oException, oException);
		}
	}

	@Override
	protected void createFiles(TileFileInfo oInfo, TreeSet<Path> oArchiveFiles)
	{
		try
		{
			if (oArchiveFiles.isEmpty())
			{
				return;
			}
			ArrayList<ResourceRecord> oRRs = oInfo.m_oRRs;
			m_oLogger.debug("create files");
			ResourceRecord oRR = oRRs.get(0);
			int[] nTile = new int[2];
			int nPPT = (int)Math.pow(2, oRR.getTileSize()) - 1;
			Mercator oM = new Mercator(nPPT);
			HashMap<String, ArrayList<InundationRecord>> oInundationRecordsByGauge = getInundation();
			for (Path oPath : oArchiveFiles)
			{
				byte[] yBuffer = null;
				long[] lTimes = new long[3];
				FilenameFormatter oArchiveFF = new FilenameFormatter(oRR.getArchiveFf());
				oArchiveFF.parse(oPath.toString(), lTimes);
				long lFileStart = Long.MAX_VALUE;
				long lFileEnd = Long.MIN_VALUE;
				try (TarArchiveInputStream oTar = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(Files.newInputStream(oPath)))))
				{
					TarArchiveEntry oEntry = null;
					while ((oEntry = oTar.getNextTarEntry()) != null) // search through the files in the .tar
					{
						if (oEntry.getName().endsWith(".dbf")) // download the .dbf
						{
							long lSize = oEntry.getSize();
							yBuffer = new byte[(int)lSize];
							int nOffset = 0;
							int nBytesRead = 0;
							while (nOffset < yBuffer.length && (nBytesRead = oTar.read(yBuffer, nOffset, yBuffer.length - nOffset)) >= 0)
								nOffset += nBytesRead;
						}
					}
				}
				catch (EOFException oException)
				{
					if (yBuffer == null || yBuffer.length == 0)
						continue;
				}
				
				StringPool oSP = new StringPool();
				ArrayList<TileForFile> oTiles = new ArrayList();
				ArrayList<int[]> oPolygonsFromShp = new ArrayList();
				int[] nBB = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
				SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				oSdf.setTimeZone(Directory.m_oUTC);
				try (DbfResultSet oAhps = new DbfResultSet(new ByteArrayInputStream(yBuffer)))
				{
					int nValCol = oAhps.findColumn("Forecast"); // find the correct value column depending on if the file is a forecast or observation file
					if (nValCol == 0) // indices are 1 based so 0 means it was not found
						nValCol = oAhps.findColumn("Observed");

					boolean bFcstTime = false;
					int nTimeCol = oAhps.findColumn("ObsTime"); // find the correct time stamp column depending on if the file is a forecast or observation file
					if (nTimeCol == 0)
					{
						nTimeCol = oAhps.findColumn("FcstTime");
						bFcstTime = true;
					}
					int nIssCol = oAhps.findColumn("FcstIssunc");

					while (oAhps.next())
					{
						if (oAhps.getString(nValCol).isEmpty())
							continue;
						String sGaugeLID = oAhps.getString("GaugeLID");
						String sWaterbody = oAhps.getString("Waterbody");
						String sLocation = oAhps.getString("Location");
						int nLon = GeoUtil.toIntDeg(oAhps.getDouble("Longitude"));
						int nLat = GeoUtil.toIntDeg(oAhps.getDouble("Latitude"));
						long lStartTime = lTimes[FilenameFormatter.START];
						long lEndTime = lTimes[FilenameFormatter.END];
						long lRecvTime = lTimes[FilenameFormatter.VALID];

						FloodStageMetadata oTemp = new FloodStageMetadata(oAhps);
						double dStageLevel = oAhps.getDouble(nValCol);
						double dVal = oTemp.getStageValue(dStageLevel);
						String sTimeVal = oAhps.getString(nTimeCol);
						if (!sTimeVal.isEmpty() && sTimeVal.compareTo("N/A") != 0)
						{
							try
							{
								long lTime = oSdf.parse(sTimeVal).getTime();
								if (bFcstTime)
								{
									lEndTime = lTime;
									sTimeVal = oAhps.getString(nIssCol);
									if (!sTimeVal.isEmpty() && sTimeVal.compareTo("N/A") != 0)
									{
										lStartTime =  oSdf.parse(sTimeVal).getTime();
										lRecvTime = lStartTime;
									}
								}
								else
								{
									lStartTime = lTime;
									lEndTime = lStartTime + 3600000; // observation are valid for an hour
								}
							}
							catch (ParseException oEx)
							{
								m_oLogger.debug(oEx, oEx);
							}
						}
						String[] sAhpsStrings =new String[]{sGaugeLID, sWaterbody, sLocation, null, null, null, null, null};
						Obs oObs = new Obs(ObsType.STG, oRR.getContribId(), Id.NULLID, lStartTime, lEndTime, lRecvTime, Obs.createPoint(nLon, nLat), Obs.POINT, dVal, sAhpsStrings);
						oObs.m_nObsFlag = Obs.POINT;
						for (int nString = 0; nString < m_nStrings; nString++)
							oSP.intern(oObs.m_sStrings[nString]);
						if (nLon < nBB[0])
							nBB[0] = nLon;
						if (nLat < nBB[1])
							nBB[1] = nLat;
						if (nLon > nBB[2])
							nBB[2] = nLon;
						if (nLat > nBB[3])
							nBB[3] = nLat;
						if (lStartTime < lFileStart)
							lFileStart = lStartTime;
						if (lEndTime > lFileEnd)
							lFileEnd = lEndTime;
						oM.lonLatToTile(GeoUtil.fromIntDeg(nLon), GeoUtil.fromIntDeg(nLat), oRR.getZoom(), nTile);
						TileForFile oTile = new TileForFile(nTile[0], nTile[1]);
						int nIndex = Collections.binarySearch(oTiles, oTile);
						if (nIndex < 0)
						{
							nIndex = ~nIndex;
							oTiles.add(nIndex, oTile);
						}

						oTiles.get(nIndex).m_oObsList.add(oObs);
						ArrayList<InundationRecord> oIRecords = oInundationRecordsByGauge.get(sGaugeLID.toLowerCase());
						if (oIRecords != null)
						{
							TileForFile oSearch = new TileForFile();
							int nInunIndex = oIRecords.size();
							
							while (nInunIndex-- > 0)
							{
								InundationRecord oRec = oIRecords.get(nInunIndex);
								if (dStageLevel >= oRec.m_dStage)
									break;
							}
							if (nInunIndex < 0)
								continue;
							
							InundationRecord oRec = oIRecords.get(nInunIndex);
							
							try (DbfResultSet oDbf = new DbfResultSet(new BufferedInputStream(new ByteArrayInputStream(oRec.m_yDbf)));
								 ShpReader oShp = new ShpReader(new BufferedInputStream(new ByteArrayInputStream(oRec.m_yShp))))
							{
								while (oDbf.next()) // for each record in the .dbf
								{
									oShp.readPolygon(oPolygonsFromShp);
									
									for (int[] nPolygon : oPolygonsFromShp)
									{
										if (nPolygon[3] < nBB[0])
											nBB[0] = nPolygon[3];
										if (nPolygon[4] < nBB[1])
											nBB[1] = nPolygon[4];
										if (nPolygon[5] > nBB[2])
											nBB[2] = nPolygon[5];
										if (nPolygon[6] > nBB[3])
											nBB[3] = nPolygon[6];

										oM.lonLatToTile(GeoUtil.fromIntDeg(nPolygon[3]), GeoUtil.fromIntDeg(nPolygon[6]), oRR.getZoom(), nTile);
										int nStartX = nTile[0]; 
										int nStartY = nTile[1];
										oM.lonLatToTile(GeoUtil.fromIntDeg(nPolygon[5]), GeoUtil.fromIntDeg(nPolygon[4]), oRR.getZoom(), nTile);
										int nEndX = nTile[0];
										int nEndY = nTile[1];
										for (int nTileY = nStartY; nTileY <= nEndY; nTileY++)
										{
											for (int nTileX = nStartX; nTileX <= nEndX; nTileX++)
											{
												oSearch.m_nX = nTileX;
												oSearch.m_nY = nTileY;

												nIndex = Collections.binarySearch(oTiles, oSearch);
												if (nIndex < 0)
												{
													nIndex = ~nIndex;
													oTiles.add(nIndex, new TileForFile(nTileX, nTileY));
												}
												oObs = new Obs(ObsType.STG, oRR.getContribId(), Id.NULLID, lStartTime, lEndTime, lRecvTime, nPolygon, Obs.POLYGON, ObsType.lookup(ObsType.STG, "flood"), sAhpsStrings);
												oObs.m_nObsFlag = Obs.POLYGON;
												oTiles.get(nIndex).m_oObsList.add(oObs);
											}
										}
									}
								}
							}
						}
					}
				}
				FilenameFormatter oFF = new FilenameFormatter(oRR.getTiledFf());
				Path oTiledFile = oRR.getFilename(lTimes[FilenameFormatter.VALID], lFileStart, lFileEnd, oFF);
				if (Files.exists(oTiledFile))
					continue;
				Files.createDirectories(oTiledFile.getParent());
				
				m_oLogger.info(oTiles.size());
				ArrayList<String> oSPList = oSP.toList();

				int nStringFlag = 0;
				for (int nString = 0; nString < m_nStrings; nString++)
					nStringFlag = Obs.addFlag(nStringFlag, nString);
				for (TileForFile oTile : oTiles)
				{
					oTile.m_oSP = oSPList;
					oTile.m_oM = oM;
					oTile.m_oRR = oRR;
					oTile.m_lFileRecv = lTimes[FilenameFormatter.VALID];
					oTile.m_nStringFlag = nStringFlag;
					oTile.m_bWriteObsFlag = true;
					oTile.m_bWriteRecv = true;
					oTile.m_bWriteStart = true;
					oTile.m_bWriteEnd = true;
					oTile.m_bWriteObsType = false;
				}
				Scheduling.processCallables(oTiles, m_nThreads);

				try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(oTiledFile))))
				{
					oOut.writeByte(1); // version
					oOut.writeInt(nBB[0]); // bounds min x
					oOut.writeInt(nBB[1]); // bounds min y
					oOut.writeInt(nBB[2]); // bounds max x
					oOut.writeInt(nBB[3]); // bounds max y
					oOut.writeInt(oRR.getObsTypeId()); // obsversation type
					oOut.writeByte(Util.combineNybbles(1, oRR.getValueType())); // obs flag  present. value type
					oOut.writeByte(0);
					oOut.writeByte(0); // id format: -1=variable, 0=null, 16=uuid, 32=32-bytes
					oOut.writeByte(Util.combineNybbles(Id.SENSOR, 0b0111)); // associate with obj and timestamp flag. the lower bits are all 1 since recv, start, and end time are written per obs 
					oOut.writeLong(lTimes[FilenameFormatter.VALID]);
					oOut.writeInt((int)((lFileEnd - lTimes[FilenameFormatter.VALID]) / 1000)); // end time offset from received time
					oOut.writeByte(1); // only file start time
					oOut.writeInt((int)((lFileStart - lTimes[FilenameFormatter.VALID]) / 1000));
					
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
					oOut.writeInt(oTiles.size());
					
					for (TileForFile oTile : oTiles) // finish writing tile metadata
					{
						oOut.writeShort(oTile.m_nX);
						oOut.writeShort(oTile.m_nY);
						oOut.writeInt(oTile.m_yTileData.length);
					}

					for (TileForFile oTile : oTiles)
					{
						oOut.write(oTile.m_yTileData);
					}
				}
			}
			m_oLogger.debug("done");
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
	
	private HashMap<String, ArrayList<InundationRecord>> getInundation()
	{
		HashMap<String, ArrayList<InundationRecord>> oRecordMap = new HashMap();
		Path oDir = Paths.get(m_sInundationDir);
		InundationRecord oSearch = new InundationRecord();
		try (DirectoryStream oDirStream = Files.newDirectoryStream(oDir, (oPath -> oPath.toString().endsWith("_shapefiles.zip"))))
		{
			List<Path> oFiles = (List)StreamSupport.stream(oDirStream.spliterator(), false).collect(Collectors.toList());
			for (Path oFile : oFiles)
			{
				String sGauge = oFile.getFileName().toString().substring(0, oFile.getFileName().toString().indexOf("_"));
				ArrayList<InundationRecord> oRecords = new ArrayList();
				oSearch.m_sId = sGauge;
				try (ZipInputStream oZipIn = new ZipInputStream(Files.newInputStream(oFile)))
				{
					ZipEntry oZe;
					
					while ((oZe = oZipIn.getNextEntry()) != null)
					{
						String sEntry = oZe.getName();
						int nStartIndex = sEntry.lastIndexOf("/") + 1;
						int nEnd = sEntry.indexOf(".", nStartIndex);
						if (nEnd < 0)
							continue;
						String sName = sEntry.substring(nStartIndex, nEnd);
						oSearch.m_sFilename = sName;
						if (sEntry.contains("polygons") && !sEntry.contains("extra"))
						{
							boolean bShp = sEntry.endsWith(".shp");
							boolean bDbf = sEntry.endsWith(".dbf");
							if (!bShp && !bDbf)
								continue;
							int nSearchIndex = Collections.binarySearch(oRecords, oSearch, InundationRecord.COMPBYFILE);
							if (nSearchIndex < 0)
							{
								nSearchIndex = ~nSearchIndex;
								oRecords.add(nSearchIndex, new InundationRecord(sGauge, sName));
							}
							InundationRecord oRec = oRecords.get(nSearchIndex);
							byte[] yBuf = new byte[(int)oZe.getSize()];
							int nOffset = 0;
							int nBytesRead = 0;
							while (nOffset < yBuf.length && (nBytesRead = oZipIn.read(yBuf, nOffset, yBuf.length - nOffset)) >= 0)
								nOffset += nBytesRead;
							if (bShp)
								oRec.m_yShp = yBuf;
							else
								oRec.m_yDbf = yBuf;
						}
					}
				}
				for (InundationRecord oRec : oRecords)
					oRec.findStage();
				
				Introsort.usort(oRecords, InundationRecord.COMPBYSTAGE);
				oRecordMap.put(sGauge, oRecords);
			}
		}
		catch (Exception oEx)
		{
			
		}
		
		return oRecordMap;
	}
}
