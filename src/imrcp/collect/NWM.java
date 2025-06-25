/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.collect;

import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.Mercator;
import imrcp.store.Obs;
import imrcp.system.Directory;
import imrcp.system.FilenameFormatter;
import imrcp.system.Id;
import imrcp.system.ObsType;
import imrcp.system.ResourceRecord;
import imrcp.system.Scheduling;
import imrcp.system.Util;
import imrcp.system.XzBuffer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 *
 * @author Federal Highway Administration
 */
public class NWM extends Collector
{	
	private String m_sLastReference = "";
	private String m_sAdditionalQuery = "&returnGeometry=%s&resultRecordCount=%d&resultOffset=%d";
	@Override
	public boolean start()
	{
		if (m_bCollectRT)
			Scheduling.getInstance().createSched(this, m_nOffset, m_nPeriod);
		return true;
	}
	
	
	@Override
	public void execute()
	{
		try
		{
			String sReference = getDownloadReferenceTime();
			if (sReference == null)
				return;

			ResourceRecord oRR = Directory.getResource(m_nContribId, m_nObsTypeId);
			SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
			long lStartTime = oSdf.parse(sReference).getTime();
			long lRecvTime = lStartTime - oRR.getDelay();
			long lEndTime = lStartTime + oRR.getRange();
			
			int nOffset = 0;
			boolean bGo = true;
			JSONArray oGeojsons = new JSONArray();
			while (bGo)
			{
				URL oUrl = new URL(m_sBaseURL + m_oSrcFile.format(0, 0, 0) + String.format(m_sAdditionalQuery, "true", 1000, nOffset));
				URLConnection oConn = oUrl.openConnection();
				oConn.setConnectTimeout(10000);
				oConn.setReadTimeout(10000);
				JSONObject oGeojson;
				try (BufferedInputStream oIn = new BufferedInputStream(oConn.getInputStream()))
				{
					oGeojson = new JSONObject(new JSONTokener(oIn));
				}
				JSONArray oFeatures = oGeojson.getJSONArray("features");
				JSONObject oLast = oFeatures.getJSONObject(oFeatures.length() - 1);
				nOffset = oLast.getInt("id");
				bGo = oGeojson.optBoolean("exceededTransferLimit", false);
				oGeojsons.put(oGeojson);
			}
			
			Path oArchive = Paths.get(new FilenameFormatter(oRR.getArchiveFf()).format(lRecvTime, lStartTime, lEndTime));
			Files.createDirectories(oArchive.getParent());
			m_oLogger.info(oArchive.toString());
			try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Channels.newChannel(Util.getGZIPOutputStream(Files.newOutputStream(oArchive))), "UTF-8"))) // save the original json output
			{
				oGeojsons.write(oOut);
			}
			m_oLogger.info("finished writing");
			m_sLastReference = sReference;
			ArrayList<ResourceRecord> oRRs = new ArrayList(1);
			oRRs.add(oRR);
			processRealTime(oRRs, lRecvTime, lRecvTime, lRecvTime);
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
	
	
	protected String getDownloadReferenceTime()
	{
		try
		{
			URL oUrl = new URL(m_sBaseURL + m_oSrcFile.format(0, 0, 0) + String.format(m_sAdditionalQuery, "false", 1, 0));
			URLConnection oConn = oUrl.openConnection();
			oConn.setConnectTimeout(10000);
			oConn.setReadTimeout(10000);
			JSONObject oResponse;
			try (BufferedInputStream oIn = new BufferedInputStream(oConn.getInputStream()))
			{
				oResponse = new JSONObject(new JSONTokener(oIn));
			}
			JSONObject oFeature = oResponse.getJSONArray("features").getJSONObject(0);
			String sReference = oFeature.getJSONObject("properties").getString("reference_time");
			if (sReference.compareTo(m_sLastReference) != 0)
				return sReference;
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
		return null;
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
			ArrayList<int[]> oOuters = new ArrayList();
			ArrayList<int[]> oHoles = new ArrayList();
			double dStgValue = ObsType.lookup(ObsType.STG, "flood");
			TileForFile oSearch = new TileForFile();

			for (Path oPath : oArchiveFiles)
			{
				ArrayList<TileForFile> oAllTiles = new ArrayList();
				int[] nFileBB = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
				
				long[] lTimes = new long[3];
				new FilenameFormatter(oRR.getArchiveFf()).parse(oPath.toString(), lTimes);
				long lFileRecv = lTimes[FilenameFormatter.VALID];
				long lStartTime = lTimes[FilenameFormatter.START];
				long lEndTime = lTimes[FilenameFormatter.END];
				JSONArray oGeojsons;
				try (BufferedReader oIn = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(oPath)))))
				{
					oGeojsons = new JSONArray(new JSONTokener(oIn));	
				}
				
				for (int nGeojsonIndex = 0; nGeojsonIndex < oGeojsons.length(); nGeojsonIndex++)
				{
					JSONObject oGeojson = oGeojsons.getJSONObject(nGeojsonIndex);
					JSONArray oFeatures = oGeojson.getJSONArray("features");
					for (int nFeatureIndex = 0; nFeatureIndex < oFeatures.length(); nFeatureIndex++)
					{
						JSONObject oFeature = oFeatures.getJSONObject(nFeatureIndex);
						JSONObject oGeometry = oFeature.getJSONObject("geometry");
						for (int[] nPolygon : GeoUtil.parseGeojsonGeometry(oGeometry, oOuters, oHoles))
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
									
									oAllTiles.get(nIndex).m_oObsList.add(new Obs(oRR.getObsTypeId(), oRR.getContribId(), Id.NULLID, lStartTime, lEndTime, lFileRecv, nPolygon, Obs.POLYGON, dStgValue));
								}
							}
						}
					}
				}
				
				for (TileForFile oTile : oAllTiles)
				{
					oTile.m_bWriteRecv = false;
					oTile.m_bWriteStart = false;
					oTile.m_bWriteEnd = false;
				}
				
				Scheduling.processCallables(oAllTiles, m_nThreads);
				m_oLogger.debug(oAllTiles.size());
				FilenameFormatter oFF = new FilenameFormatter(oRR.getTiledFf());
				Path oTiledFile = oRR.getFilename(lFileRecv, lStartTime, lEndTime, oFF);
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
					oOut.writeByte(0); // associate with obj and timestamp flag
					oOut.writeLong(lFileRecv);
					oOut.writeInt((int)((lEndTime - lFileRecv) / 1000));
					oOut.writeByte(1); // only file start time
					oOut.writeInt((int)((lStartTime - lFileRecv) / 1000));
					
					oOut.writeInt(0); // no string pool

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
