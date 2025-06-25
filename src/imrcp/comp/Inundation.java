/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.comp;

import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.Mercator;
import imrcp.geosrv.Network;
import imrcp.geosrv.WayNetworks;
import imrcp.geosrv.osm.OsmNode;
import imrcp.geosrv.osm.OsmWay;
import imrcp.store.Obs;
import imrcp.store.ObsList;
import imrcp.store.TileObsView;
import imrcp.system.Arrays;
import imrcp.system.Directory;
import imrcp.system.FilenameFormatter;
import imrcp.system.Id;
import imrcp.system.ObsType;
import imrcp.system.ResourceRecord;
import imrcp.system.Scheduling;
import imrcp.collect.TileFileInfo;
import imrcp.collect.TileFileWriter;
import imrcp.collect.TileForFile;
import imrcp.system.Util;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeSet;
import org.json.JSONObject;

/**
 *
 * @author Federal Highway Administration
 */
public class Inundation extends TileFileWriter
{
	protected int m_nContribId;
	protected int m_nSourceId;
	protected int m_nObsTypeId;
	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_nContribId = Integer.valueOf(oBlockConfig.optString("contribid", "imrcp"), 36);
		m_nSourceId = Integer.valueOf(oBlockConfig.optString("sourceid", "flood"), 36);
		m_nObsTypeId = Integer.valueOf(oBlockConfig.optString("obstypeid", "stpvt"), 36);
	}
	
	
	@Override
	public boolean start()
		throws Exception
	{
		m_nSchedId = Scheduling.getInstance().createSched(this, m_nOffset, m_nPeriod);
		return true;
	}
	
	@Override
	public void execute()
	{
		
		long lTime = System.currentTimeMillis();
		long nPeriodMillis = m_nPeriod * 1000;
		lTime = (lTime / nPeriodMillis) * nPeriodMillis;
		ArrayList<ResourceRecord> oRRs = Directory.getResourcesByContribSource(m_nContribId, m_nSourceId);
		int nIndex = oRRs.size();
		while (nIndex-- > 0)
		{
			if (oRRs.get(nIndex).getObsTypeId() != m_nObsTypeId)
				oRRs.remove(nIndex);
		}
		processRealTime(oRRs, lTime, lTime + 60000, lTime);
	}


	@Override
	protected void createFiles(TileFileInfo oInfo, TreeSet<Path> oArchiveFiles)
	{
		long lTime = oInfo.m_lStart;
		long nPeriodMillis = m_nPeriod * 1000;
		lTime = (lTime / nPeriodMillis) * nPeriodMillis;
		int[] nBB = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
		for (Network oNetwork : oWayNetworks.getNetworks())
		{
			int[] nNetworkBB = oNetwork.getBoundingBox();
			if (nNetworkBB[0] < nBB[0])
				nBB[0] = nNetworkBB[0];
			if (nNetworkBB[1] < nBB[1])
				nBB[1] = nNetworkBB[1];
			if (nNetworkBB[2] > nBB[2])
				nBB[2] = nNetworkBB[2];
			if (nNetworkBB[3] > nBB[3])
				nBB[3] = nNetworkBB[3];
		}
		ArrayList<ResourceRecord> oStgRRs = Directory.getResourcesByObsType(ObsType.STG);
		int[] nContribAndSource = new int[2];
		TileObsView oOV = (TileObsView)Directory.getInstance().lookup("ObsView");
		int[] nTile = new int[2];
		ResourceRecord oRR = oInfo.m_oRRs.get(0);
		int nPPT = (int)Math.pow(2, oRR.getTileSize()) - 1;
		int nZoom = oRR.getZoom();
		Mercator oM = new Mercator(nPPT);
		String[] sNullStrings = new String[8];
		double dFloodedVal = ObsType.lookup(ObsType.STPVT, "flooded");
		while (lTime < oInfo.m_lEnd)
		{
			try
			{
				ObsList oAllObs = new ObsList();
				for (ResourceRecord oTemp : oStgRRs)
				{
					nContribAndSource[0] = oTemp.getContribId();
					nContribAndSource[1] = oTemp.getSourceId();
					ObsList oData = oOV.getData(ObsType.STG, lTime, lTime + oTemp.getMaxFcst(), nBB[1], nBB[3], nBB[0], nBB[2], lTime, nContribAndSource);
					for (Obs oObs : oData)
					{
						if (oObs.m_yGeoType == Obs.POLYGON) // only get inundation polygons
							oAllObs.add(oObs);
					}
				}
				long lFileTime = lTime;
				long lStartTime = lTime;
				long lEndTime = lTime;
				lTime += nPeriodMillis;

				ArrayList<OsmWay> oWays = new ArrayList();
				ArrayList<OsmWay> oFlooded = new ArrayList();
				int[] nGeo = Arrays.newIntArray();
				int[] nFileBB = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
				
				ArrayList<TileForFile> oTiles = new ArrayList();
				for (Obs oObs : oAllObs)
				{
					oWays.clear();
					oWayNetworks.getWays(oWays, 0, oObs.m_oGeoArray[3], oObs.m_oGeoArray[4], oObs.m_oGeoArray[5], oObs.m_oGeoArray[6]);
					for (OsmWay oWay : oWays)
					{
						if (oWay.m_bBridge)
							continue;
						nGeo[0] = 5;
						nGeo[1] = Integer.MAX_VALUE;
						nGeo[2] = Integer.MAX_VALUE;
						nGeo[3] = Integer.MIN_VALUE;
						nGeo[4]= Integer.MIN_VALUE;
						for (OsmNode oNode : oWay.m_oNodes)
						{
							nGeo = Arrays.addAndUpdate(nGeo, oNode.m_nLon, oNode.m_nLat, 1);
						}

						if (GeoUtil.isInsideRingAndHoles(oObs.m_oGeoArray, Obs.LINESTRING, nGeo))
						{
							int nSearch = Collections.binarySearch(oFlooded, oWay, OsmWay.WAYBYTEID);
							if (nSearch < 0)
							{
								oFlooded.add(~nSearch, oWay);
								if (oObs.m_lObsTime1 < lStartTime)
									lStartTime = oObs.m_lObsTime1;
								if (oObs.m_lObsTime2 > lEndTime)
									lEndTime = oObs.m_lObsTime2;
								if (oWay.m_nMinLon < nFileBB[0])
									nFileBB[0] = oWay.m_nMinLon;
								if (oWay.m_nMinLat < nFileBB[1])
									nFileBB[1] = oWay.m_nMinLat;
								if (oWay.m_nMaxLon > nFileBB[2])
									nFileBB[2] = oWay.m_nMaxLon;
								if (oWay.m_nMaxLat > nFileBB[3])
									nFileBB[3] = oWay.m_nMaxLat;

								oM.lonLatToTile(GeoUtil.fromIntDeg(oWay.m_nMidLon), GeoUtil.fromIntDeg(oWay.m_nMidLat), nZoom, nTile);
								TileForFile oTile = new TileForFile(nTile[0], nTile[1]);
								int nIndex = Collections.binarySearch(oTiles, oTile);
								if (nIndex < 0)
								{
									nIndex = ~nIndex;
									oTiles.add(nIndex, oTile);
								}

								Obs oStpvtObs = new Obs();
								oStpvtObs.m_lObsTime1 = oObs.m_lObsTime1;
								oStpvtObs.m_lObsTime2 = oObs.m_lObsTime2;
								oStpvtObs.m_lTimeRecv = oObs.m_lTimeRecv;
								oStpvtObs.m_dValue = dFloodedVal;
								oStpvtObs.m_oGeoArray = Obs.createPoint(oWay.m_nMidLon, oWay.m_nMidLat);
								oStpvtObs.m_yGeoType = Obs.POINT;
								oStpvtObs.m_sStrings = sNullStrings;
								oTiles.get(nIndex).m_oObsList.add(oStpvtObs);
							}
						}
					}
				}

				if (oTiles.isEmpty())
					continue;

				for (TileForFile oTile : oTiles)
				{
					oTile.m_oSP = null;
					oTile.m_oM = oM;
					oTile.m_oRR = oRR;
					oTile.m_lFileRecv = lFileTime;
					oTile.m_bWriteObsFlag = false;
					oTile.m_bWriteRecv = true;
					oTile.m_bWriteStart = true;
					oTile.m_bWriteEnd = true;
					oTile.m_bWriteObsType = false;
				}
				Scheduling.processCallables(oTiles, m_nThreads);

				FilenameFormatter oFF = new FilenameFormatter(oRR.getTiledFf());
				Path oTiledFile = oRR.getFilename(lFileTime, lStartTime, lEndTime, oFF);
				Files.createDirectories(oTiledFile.getParent());
				try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(oTiledFile))))
				{
					oOut.writeByte(1); // version
					oOut.writeInt(nFileBB[0]); // bounds min x
					oOut.writeInt(nFileBB[1]); // bounds min y
					oOut.writeInt(nFileBB[2]); // bounds max x
					oOut.writeInt(nFileBB[3]); // bounds max y
					oOut.writeInt(oRR.getObsTypeId()); // obsversation type
					oOut.writeByte(Util.combineNybbles(0, oRR.getValueType())); // obs flag not present. value type
					oOut.writeByte(Obs.POINT);
					oOut.writeByte(0); // id format: -1=variable, 0=null, 16=uuid, 32=32-bytes
					oOut.writeByte(Util.combineNybbles(Id.SEGMENT, 0b0111)); // associate with obj and timestamp flag.
					oOut.writeLong(lFileTime);
					oOut.writeInt((int)((lEndTime - lFileTime) / 1000)); // end time offset from received time
					oOut.writeByte(1); // only file start time
					oOut.writeInt((int)((lStartTime - lFileTime) / 1000));
					
					oOut.writeInt(0); // no string pool
					
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
			catch (Exception oEx)
			{
				m_oLogger.error(oEx, oEx);
			}
		}
	}
}
