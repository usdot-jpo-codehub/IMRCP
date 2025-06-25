package imrcp.collect;

import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.Mercator;
import imrcp.store.Obs;
import static imrcp.store.TileObsView.PATHREFCOMP;
import imrcp.system.FilenameFormatter;
import imrcp.system.dbf.DbfResultSet;
import imrcp.system.Directory;
import imrcp.system.Id;
import imrcp.system.ObsType;
import imrcp.system.ResourceRecord;
import imrcp.system.Scheduling;
import imrcp.system.StringPool;
import imrcp.system.Util;
import imrcp.system.XzBuffer;
import imrcp.system.shp.ShpReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.json.JSONObject;

/**
 *
 * @author aaron.cherney
 */
public class CAP extends Collector
{	
	/**
	 * Timestamp of the last time the file was updated on the CAP website
	 */
	private String m_sLastUpdated = "";

	
	/**
	 * Sets the fixed interval schedule of execution
	 * @return true
	 */
	@Override
	public boolean start()
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
	}

	
	/**
	 * Attempts to make a connection to the CAP server and downloads a data file
	 * if there is a new one available
	 */
	@Override
	public void execute()
	{
		try
		{
			long lNow = System.currentTimeMillis() / 60000 * 60000;
			ResourceRecord oRR = Directory.getResource(m_nContribId, m_nObsTypeId);
			String sSrc = m_oSrcFile.format(0, 0, 0);
			URL oUrl = new URL(m_sBaseURL + sSrc);
			URLConnection oConn = oUrl.openConnection();
			oConn.setConnectTimeout(60000);
			oConn.setReadTimeout(60000);
			String sLastModified = oConn.getHeaderField("Last-Modified");
			if (sLastModified.compareTo(m_sLastUpdated) != 0)
			{
				byte[] yFile = new byte[oConn.getContentLength()];
				m_oLogger.debug("Downloading " + sSrc);
				try (BufferedInputStream oIn = new BufferedInputStream(oConn.getInputStream()))
				{
					int nOffset = 0;
					int nBytesRead = 0;
					while (nOffset < yFile.length && (nBytesRead = oIn.read(yFile, nOffset, yFile.length - nOffset)) >= 0)
						nOffset += nBytesRead;
				}
				Path oArchive = Paths.get(new FilenameFormatter(oRR.getArchiveFf()).format(lNow, lNow, lNow + oRR.getRange()));
				Files.createDirectories(oArchive.getParent());
				try (BufferedOutputStream oOut = new BufferedOutputStream(Files.newOutputStream(oArchive)))
				{
					oOut.write(yFile);
				}
				m_sLastUpdated = sLastModified;
				ArrayList<ResourceRecord> oRRs = new ArrayList(1);
				oRRs.add(oRR);
				processRealTime(oRRs, lNow, lNow, lNow);
			}
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
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
			int nStringFlag = 0;
			for (int nIndex = 0; nIndex < m_nStrings; nIndex++)
				nStringFlag = Obs.addFlag(nStringFlag, nIndex);
			FilenameFormatter oDataFf = new FilenameFormatter(oRR.getTiledFf());
			String sEndsWith = oDataFf.getExtension();
			long[] lDataTimes = new long[3];
			for (Path oPath : oArchiveFiles)
			{
				
				long[] lTimes = new long[3];
				new FilenameFormatter(oRR.getArchiveFf()).parse(oPath.toString(), lTimes);
				long lFileRecv = lTimes[FilenameFormatter.VALID];
				long lDay = lFileRecv / 86400000 * 86400000;
				
				Path oDailyDir = oRR.getFilename(lDay, 0, 0, oDataFf).getParent();
				boolean bCreate = true;
				if (Files.exists(oDailyDir))
				{
					try (DirectoryStream oDir = Files.newDirectoryStream(oDailyDir, (oFilePath -> oFilePath.toString().endsWith(sEndsWith))))
					{
						List<Path> oFiles = (List)StreamSupport.stream(oDir.spliterator(), false).sorted(PATHREFCOMP).collect(Collectors.toList());
						for (int nIndex = 0; nIndex < oFiles.size() && bCreate; nIndex++)
						{
							Path oFile = oFiles.get(nIndex);
							oDataFf.parse(oFile.toString(), lDataTimes);
							if (lDataTimes[FilenameFormatter.VALID] == lFileRecv)
							{
								bCreate = false;
							}
						}
					}
				}
				
				if (!bCreate)
					continue;
				
				m_oLogger.debug("create files");
				byte[] yShp = null;
				byte[] yDbf = null;
				StringPool oSP = new StringPool();
				m_oLogger.debug(oPath.toString());
				try (TarArchiveInputStream oTar = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(Files.newInputStream(oPath)))))
				{
					TarArchiveEntry oEntry = null;
					while ((oEntry = oTar.getNextTarEntry()) != null) // search through the files in the .tar
					{
						byte[] yBuffer = null;
						if (oEntry.getName().endsWith(".shp")) // read the .shp file into memory
						{
							long lSize = oEntry.getSize();
							yShp = new byte[(int)lSize];
							yBuffer = yShp;
						}
						if (oEntry.getName().endsWith(".dbf")) // read the .shp file into memory
						{
							long lSize = oEntry.getSize();
							yDbf = new byte[(int)lSize];
							yBuffer = yDbf;
						}

						if (yBuffer != null)
						{
							int nOffset = 0;
							int nBytesRead = 0;
							while (nOffset < yBuffer.length && (nBytesRead = oTar.read(yBuffer, nOffset, yBuffer.length - nOffset)) >= 0)
								nOffset += nBytesRead;
						}
					}
				}
				
				if (yShp == null || yDbf == null)
					throw new Exception("Error reading tar.gz file " + oPath.toString());
				
				SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
				oSdf.setTimeZone(Directory.m_oUTC);
				long lFileStart = Long.MAX_VALUE;
				long lFileEnd = Long.MIN_VALUE;
				ArrayList<TileForFile> oAllTiles = new ArrayList();
				int[] nFileBB = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
				TileForFile oSearch = new TileForFile();
				int nCount = 0;
				
				try (DbfResultSet oDbf = new DbfResultSet(new BufferedInputStream(new ByteArrayInputStream(yDbf)));
					ShpReader oShp = new ShpReader(new BufferedInputStream(new ByteArrayInputStream(yShp))))
				{
					// find the correct column for each of the needed parameters
					int nExCol = oDbf.findColumn("EXPIRATION"); // end time
					int nOnCol = oDbf.findColumn("ONSET"); // start time
					int nIsCol = oDbf.findColumn("ISSUANCE"); // time received
					int nIdCol = oDbf.findColumn("CAP_ID"); // cap id
					int nTypeCol = oDbf.findColumn("PROD_TYPE"); // alert type
					int nUrlCol = oDbf.findColumn("URL"); // cap url
					ArrayList<int[]> oPolygonsFromShp = new ArrayList();
					while (oDbf.next()) // for each record in the .dbf
					{
						oPolygonsFromShp.clear();
						long lEventStart = oSdf.parse(oDbf.getString(nOnCol)).getTime();
						long lEventEnd= oSdf.parse(oDbf.getString(nExCol)).getTime();
						long lEventRecv = oSdf.parse(oDbf.getString(nIsCol)).getTime();
						if (lEventStart < lFileStart)
							lFileStart = lEventStart;
						if (lEventEnd > lFileEnd)
							lFileEnd = lEventEnd;
						double dEventValue = ObsType.lookup(ObsType.EVT, oDbf.getString(nTypeCol));
						String[] sStrings = new String[]{oDbf.getString(nIdCol), oDbf.getString(nUrlCol), null, null, null, null, null, null};
						for (int nString = 0; nString < m_nStrings; nString++)
							oSP.intern(sStrings[nString]);
						oShp.readPolygon(oPolygonsFromShp);
						
						for (int[] nPolygon : oPolygonsFromShp)
						{
							if (nPolygon[3] < nFileBB[0])
								nFileBB[0] = nPolygon[3];
							if (nPolygon[4] < nFileBB[1])
								nFileBB[1] = nPolygon[4];
							if (nPolygon[5] > nFileBB[2])
								nFileBB[2] = nPolygon[5];
							if (nPolygon[6] > nFileBB[3])
								nFileBB[3] = nPolygon[6];
							
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
									
									int nIndex = Collections.binarySearch(oAllTiles, oSearch);
									if (nIndex < 0)
									{
										nIndex = ~nIndex;
										oAllTiles.add(nIndex, new TileForFile(nTileX, nTileY, oM, oRR, lFileRecv, nStringFlag, m_oLogger));
									}
									
									oAllTiles.get(nIndex).m_oObsList.add(new Obs(oRR.getObsTypeId(), oRR.getContribId(), Id.NULLID, lEventStart, lEventEnd, lEventRecv, nPolygon, Obs.POLYGON, dEventValue, sStrings));
								}
							}
						}
					}
				}

				ArrayList<String> oSPList = oSP.toList();
				for (TileForFile oTile : oAllTiles)
				{
					oTile.m_oSP = oSPList;
					oTile.m_bWriteRecv = true;
					oTile.m_bWriteStart = true;
					oTile.m_bWriteEnd = true;

				}
				Scheduling.processCallables(oAllTiles, m_nThreads);
				m_oLogger.debug(oAllTiles.size());
				
				
				FilenameFormatter oFF = new FilenameFormatter(oRR.getTiledFf());
				Path oTiledFile = oRR.getFilename(lFileRecv, lFileStart, lFileEnd, oFF);
				Files.createDirectories(oTiledFile.getParent());
				try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(oTiledFile))))
				{
					oOut.writeByte(1); // version
					oOut.writeInt(nFileBB[0]); // bounds min x
					oOut.writeInt(nFileBB[1]); // bounds min y
					oOut.writeInt(nFileBB[2]); // bounds max x
					oOut.writeInt(nFileBB[3]); // bounds max y
					oOut.writeInt(oRR.getObsTypeId()); // obsversation type
					oOut.writeByte(Util.combineNybbles(0, oRR.getValueType()));
					oOut.writeByte(Obs.POLYGON);
					oOut.writeByte(0); // id format: -1=variable, 0=null, 16=uuid, 32=32-bytes
					oOut.writeByte(Util.combineNybbles(0, 0b0111)); // associate with obj and timestamp flag, the lower bits are all 1 since recv, start, and end time are written per obs
					oOut.writeLong(lFileRecv);
					oOut.writeInt((int)((lFileEnd - lFileRecv) / 1000));
					oOut.writeByte(1); // only file start time
					oOut.writeInt((int)((lFileStart - lFileRecv) / 1000));
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
				m_oLogger.debug("done");
			}
			
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
}
