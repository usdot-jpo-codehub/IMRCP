/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.comp;

import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.Mercator;
import imrcp.store.Obs;
import imrcp.store.ObsList;
import imrcp.store.TileObsView;
import imrcp.system.Directory;
import imrcp.system.FilenameFormatter;
import imrcp.system.Id;
import imrcp.system.JSONUtil;
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
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import org.json.JSONObject;

/**
 *
 * @author Federal Highway Administration
 */
public class PcCat extends TileFileWriter
{
	protected int[] m_nAreaToProcess;
	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_nAreaToProcess = JSONUtil.getIntArray(oBlockConfig, "boundingbox");
	}
	
	
	@Override
	protected void createFiles(TileFileInfo oInfo, TreeSet<Path> oArchiveFiles)
	{
		try
		{
			ResourceRecord oPcCatRR = oInfo.m_oRRs.get(0);
			if (oPcCatRR.getContribId() == Integer.valueOf("wxde", 36))
				return;
			TileObsView oObsView = (TileObsView)Directory.getInstance().lookup("ObsView");
			ArrayList<ResourceRecord> oRateRRs = Directory.getResourcesByObsType(ObsType.RTEPC);
			int[] nTile = new int[2];

			int[] nContribSource = new int[2];
			long lTimestamp = oInfo.m_lEnd - 60000;
			ObsList oRates = new ObsList();
			ResourceRecord oRateRR = null;
			for (ResourceRecord oRR : oRateRRs)
			{
				if (oRR.getContribId() != oPcCatRR.getSourceId())
					continue;
				nContribSource[0] = oRR.getContribId();
				nContribSource[1] = oRR.getSourceId();
				oRateRR = oRR;
				oRates = oObsView.getData(ObsType.RTEPC, lTimestamp, oInfo.m_lEnd, m_nAreaToProcess[1], m_nAreaToProcess[3], m_nAreaToProcess[0], m_nAreaToProcess[2], oInfo.m_lRef, nContribSource);
				//m_oLogger.debug(String.format("%d %s for %s", oRates.size(), "RTEPC", Integer.toString(nContribSource[0], 36)));
			}
			if (oRates.isEmpty())
				return;

			int nSecondaryObsType = ObsType.TYPPC;
			int nPcCatContrib = oRateRR.getContribId();
			int nSourceId = Integer.MIN_VALUE;
			CategoryAlgorithm oAlgorithm;
			if (oRateRR.getContribId() == Integer.valueOf("mrms", 36) || oRateRR.getContribId() == Integer.valueOf("ndfd", 36))
			{
				nSecondaryObsType = ObsType.TAIR;
				oAlgorithm = new CategoryByTemp();
				nPcCatContrib = Integer.valueOf("imrcp", 36);
				nSourceId = oRateRR.getContribId();
			}
			else
				oAlgorithm = new CategoryByType();


			Comparator<int[]> oTileComp = (int[] o1, int[] o2) ->
			{
				int nRet = o1[0] - o2[0];
				if (nRet == 0)
					nRet = o1[1] - o2[1];

				return nRet;
			};
			TreeMap<int[], ObsList[]> oTiles = new TreeMap(oTileComp);

			int nZoom = oRateRR.getZoom();
			int nTileSize = oRateRR.getTileSize();
			int nPPT = (int)Math.pow(2, nTileSize) - 1;
			Mercator oM = new Mercator(nPPT);
			int[] nTileMinMax = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
			for (Obs oRate : oRates)
			{
				int[] oObsPoly = oRate.m_oGeoArray;
				oM.lonLatToTile(GeoUtil.fromIntDeg(oObsPoly[3]), GeoUtil.fromIntDeg(oObsPoly[6]), nZoom, nTile); // min lon, max lat (top left)
				int nStartX = nTile[0];
				int nStartY = nTile[1];
				oM.lonLatToTile(GeoUtil.fromIntDeg(oObsPoly[5]), GeoUtil.fromIntDeg(oObsPoly[4]), nZoom, nTile); // max lon, min lat (bottom right)
				int nEndX = nTile[0];
				int nEndY = nTile[1];
				for (int nTileY = nStartY; nTileY <= nEndY; nTileY++)
				{
					for (int nTileX = nStartX; nTileX <= nEndX; nTileX++)
					{
						nTile[0] = nTileX;
						nTile[1] = nTileY;

						if (!oTiles.containsKey(nTile))
						{
							oTiles.put(new int[]{nTileX, nTileY}, new ObsList[]{new ObsList(), new ObsList()});
							if (nTileX < nTileMinMax[0])
								nTileMinMax[0] = nTileX;
							if (nTileY < nTileMinMax[1])
								nTileMinMax[1] = nTileY;
							if (nTileX > nTileMinMax[2])
								nTileMinMax[2] = nTileX;
							if (nTileY > nTileMinMax[3])
								nTileMinMax[3] = nTileY;
						}

						oTiles.get(nTile)[0].add(oRate);
					}
				}
			}
			double[] dLonLatBounds = new double[4];
			oM.lonLatBounds(nTileMinMax[0], nTileMinMax[3], nZoom, dLonLatBounds); // bottom left tile
			int nQueryLon1 = GeoUtil.toIntDeg(dLonLatBounds[0]); // minx
			int nQueryLat1 = GeoUtil.toIntDeg(dLonLatBounds[1]); // miny
			oM.lonLatBounds(nTileMinMax[2], nTileMinMax[1], nZoom, dLonLatBounds); // top right tile
			int nQueryLon2 = GeoUtil.toIntDeg(dLonLatBounds[2]); // maxx
			int nQueryLat2 = GeoUtil.toIntDeg(dLonLatBounds[3]); // maxy
			int nSecondaryContrib;
			if (oRateRR.getContribId() == Integer.valueOf("mrms", 36))
				nSecondaryContrib = Integer.valueOf("rtma", 36);
			else
				nSecondaryContrib = oRateRR.getContribId();

			ResourceRecord oSecondaryRR = Directory.getResource(nSecondaryContrib, nSecondaryObsType);
			ObsList oSecondaryObsList = null;

			nContribSource[0] = oSecondaryRR.getContribId();
			nContribSource[1] = oSecondaryRR.getSourceId();
			oSecondaryObsList = oObsView.getData(nSecondaryObsType, lTimestamp, oInfo.m_lEnd, nQueryLat1, nQueryLat2, nQueryLon1, nQueryLon2, oInfo.m_lRef, nContribSource);
			//m_oLogger.debug(String.format("%d %s for %s", oSecondaryObsList.size(), Integer.toString(nSecondaryObsType, 36), Integer.toString(nContribSource[0], 36)));

			if (oSecondaryObsList.isEmpty())
				return;
			for (Obs oSecondaryObs : oSecondaryObsList)
			{
				int[] oObsPoly = oSecondaryObs.m_oGeoArray;
				oM.lonLatToTile(GeoUtil.fromIntDeg(oObsPoly[3]), GeoUtil.fromIntDeg(oObsPoly[6]), nZoom, nTile); // min lon, max lat (top left)
				int nStartX = nTile[0];
				int nStartY = nTile[1];
				oM.lonLatToTile(GeoUtil.fromIntDeg(oObsPoly[5]), GeoUtil.fromIntDeg(oObsPoly[4]), nZoom, nTile); // max lon, min lat (bottom right)
				int nEndX = nTile[0];
				int nEndY = nTile[1];
				for (int nTileY = nStartY; nTileY <= nEndY; nTileY++)
				{
					for (int nTileX = nStartX; nTileX <= nEndX; nTileX++)
					{
						nTile[0] = nTileX;
						nTile[1] = nTileY;

						if (oTiles.containsKey(nTile))
							oTiles.get(nTile)[1].add(oSecondaryObs);
					}
				}
			}

			long lFileStart = 0;
			long lFileEnd = 0;
			long lFileRecv = 0;
			ArrayList<PcCatTile> oProcess = new ArrayList();
			for (Entry<int[], ObsList[]> oEntry : oTiles.entrySet())
			{
				oProcess.add(new PcCatTile(oEntry.getKey()[0], oEntry.getKey()[1], oEntry.getValue()[0], oEntry.getValue()[1], oAlgorithm));
			}
			Scheduling.processCallables(oProcess, m_nThreads);
			
			ArrayList<TileForFile> oAllTiles = new ArrayList();
			boolean bSetTimes = true;
			for (PcCatTile oTile : oProcess)
			{
				TileForFile oTFP = new TileForFile(oTile.m_nX, oTile.m_nY);
				oTFP.m_oObsList = oTile.m_oOutput;
				if (!oTFP.m_oObsList.isEmpty())
				{
					oTFP.m_bWriteEnd = false;
					oTFP.m_bWriteObsFlag = false;
					oTFP.m_bWriteObsType = false;
					oTFP.m_bWriteRecv = false;
					oTFP.m_bWriteStart = false;
					oTFP.m_lFileRecv = oTFP.m_oObsList.get(0).m_lTimeRecv;
					oTFP.m_oLogger = m_oLogger;
					oTFP.m_oSP = null;
					oTFP.m_oRR = oPcCatRR;
					oTFP.m_oM = oM;
					if (bSetTimes)
					{
						bSetTimes = false;
						lFileStart = oTFP.m_oObsList.get(0).m_lObsTime1;
						lFileEnd = oTFP.m_oObsList.get(0).m_lObsTime2;
						lFileRecv = oTFP.m_oObsList.get(0).m_lTimeRecv;
					}
					oAllTiles.add(oTFP);
				}	
			}
			
			Scheduling.processCallables(oAllTiles, m_nThreads);

			m_oLogger.info(oAllTiles.size());
			FilenameFormatter oFF = new FilenameFormatter(oPcCatRR.getTiledFf());
			Path oTiledFile = oPcCatRR.getFilename(lFileRecv, lFileStart, lFileEnd, oFF);
			Files.createDirectories(oTiledFile.getParent());
			try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(oTiledFile))))
			{
				oOut.writeByte(1); // version
				oOut.writeInt(nQueryLon1); // bounds min x
				oOut.writeInt(nQueryLat1); // bounds min y
				oOut.writeInt(nQueryLon2); // bounds max x
				oOut.writeInt(nQueryLat2); // bounds max y
				oOut.writeInt(ObsType.PCCAT); // obsversation type
				oOut.writeByte(Util.combineNybbles(0, oPcCatRR.getValueType()));
				oOut.writeByte(Obs.POLYGON);
				oOut.writeByte(0); // id format: -1=variable, 0=null, 16=uuid, 32=32-bytes
				oOut.writeByte(Util.combineNybbles(0, 0b0000)); // associate with obj and timestamp flag, the lower bits are all 1 since recv, start, and end time are written per obs
				oOut.writeLong(lFileRecv);
				oOut.writeInt((int)((lFileEnd - lFileRecv) / 1000));
				oOut.writeByte(1); // only file start time
				oOut.writeInt((int)((lFileStart - lFileRecv) / 1000));
				oOut.writeInt(0);

				oOut.writeByte(oPcCatRR.getZoom()); // tile zoom level
				oOut.writeByte(oPcCatRR.getTileSize());

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
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
	
	
	private class PcCatTile implements Callable<PcCatTile>
	{
		int m_nX;
		int m_nY;
		ObsList m_oRates;
		ObsList m_oSecondary;
		CategoryAlgorithm m_oAlgo;
		ObsList m_oOutput;
		
		PcCatTile(int nX, int nY, ObsList oRates, ObsList oSecondary, CategoryAlgorithm oAlgo)
		{
			m_nX = nX;
			m_nY = nY;
			m_oRates = oRates;
			m_oSecondary = oSecondary;
			m_oAlgo = oAlgo;
			m_oOutput = new ObsList(oRates.size());
		}
		
		
		@Override
		public PcCatTile call() throws Exception
		{
			long[] oRateAreas = new long[m_oRates.size()];
			long[] oSecondaryAreas = new long[m_oSecondary.size()];
			try
			{
				for (int nRateIndex = 0; nRateIndex < oRateAreas.length; nRateIndex++)
				{
					Obs oRateObs = m_oRates.get(nRateIndex);
					int[] oRatePoly = oRateObs.m_oGeoArray;
					for (int nSecondaryIndex = 0; nSecondaryIndex < oSecondaryAreas.length; nSecondaryIndex++)
					{
						Obs oSecondaryObs = m_oSecondary.get(nSecondaryIndex);
						int[] oSecondaryPoly = oSecondaryObs.m_oGeoArray;
						if (!GeoUtil.boundingBoxesIntersect(oRatePoly[3], oRatePoly[4], oRatePoly[5], oRatePoly[6], 
							oSecondaryPoly[3], oSecondaryPoly[4], oSecondaryPoly[5], oSecondaryPoly[6]))
							continue;

						double dCat = m_oAlgo.getCategory(oRateObs.m_dValue, oSecondaryObs.m_dValue);
						if (Double.isNaN(dCat))
							continue;

						if (oRateAreas[nRateIndex] == 0L)
							oRateAreas[nRateIndex] = GeoUtil.makePolygon(oRatePoly);

						if (oSecondaryAreas[nSecondaryIndex] == 0L)
							oSecondaryAreas[nSecondaryIndex] = GeoUtil.makePolygon(oSecondaryPoly);

						long[] lClipRef = new long[]{0L, oRateAreas[nRateIndex], oSecondaryAreas[nSecondaryIndex]};
						int nResults = GeoUtil.clipPolygon(lClipRef);
						long lStart;
						long lEnd;
						if (oRateObs.m_lObsTime2 - oRateObs.m_lObsTime1 < oSecondaryObs.m_lObsTime2 - oSecondaryObs.m_lObsTime1)
						{
							lStart = oRateObs.m_lObsTime1;
							lEnd = oRateObs.m_lObsTime2;
						}
						else
						{
							lStart = oSecondaryObs.m_lObsTime1;
							lEnd = oSecondaryObs.m_lObsTime2;
						}

						while (nResults-- > 0)
						{
							try
							{
								m_oOutput.add(new Obs(ObsType.PCCAT, 0, Id.NULLID, lStart, lEnd, oRateObs.m_lTimeRecv, GeoUtil.popResult(lClipRef[0]), Obs.POLYGON, dCat, (String[])null));
							}
							catch (Exception oEx)
							{
								m_oLogger.error(oEx, oEx);
							}
						}
					}
				}
			}
			finally
			{
				for (int nIndex = 0; nIndex < oRateAreas.length; nIndex++)
				{
					if (oRateAreas[nIndex] != 0L)
						GeoUtil.freePolygon(oRateAreas[nIndex]);
				}
				
				for (int nIndex = 0; nIndex < oSecondaryAreas.length; nIndex++)
				{
					if (oSecondaryAreas[nIndex] != 0L)
						GeoUtil.freePolygon(oSecondaryAreas[nIndex]);
				}
			}
			
			
			return this;
		}
	}
	
	private abstract class CategoryAlgorithm
	{
		final double m_dNoPrecip = ObsType.lookup(ObsType.PCCAT, "no-precipitation");
		final double m_dLightRain = ObsType.lookup(ObsType.PCCAT, "light-rain");
		final double m_dModerateRain = ObsType.lookup(ObsType.PCCAT, "moderate-rain");
		final double m_dHeavyRain = ObsType.lookup(ObsType.PCCAT, "heavy-rain");
		final double m_dLightFreezingRain = ObsType.lookup(ObsType.PCCAT, "light-freezing-rain");
		final double m_dModerateFreezingRain = ObsType.lookup(ObsType.PCCAT, "moderate-freezing-rain");
		final double m_dHeavyFreezingRain = ObsType.lookup(ObsType.PCCAT, "heavy-freezing-rain");
		final double m_dLightSnow = ObsType.lookup(ObsType.PCCAT, "light-snow");
		final double m_dModerateSnow = ObsType.lookup(ObsType.PCCAT, "moderate-snow");
		final double m_dHeavySnow = ObsType.lookup(ObsType.PCCAT, "heavy-snow");
		final double m_dLightIce = ObsType.lookup(ObsType.PCCAT, "light-ice");
		final double m_dModerateIce = ObsType.lookup(ObsType.PCCAT, "moderate-ice");
		final double m_dHeavyIce = ObsType.lookup(ObsType.PCCAT, "heavy-ice");

		abstract double getCategory(double dRate, double dSecondary);
	}
	
	class CategoryByType extends CategoryAlgorithm
	{
		final double m_dFreezingRain = ObsType.lookup(ObsType.TYPPC, "freezing-rain");
		final double m_dIcePellets = ObsType.lookup(ObsType.TYPPC, "ice-pellets");
		final double m_dSnow = ObsType.lookup(ObsType.TYPPC, "snow");
		final double m_dRain = ObsType.lookup(ObsType.TYPPC, "rain");
		final double m_dNone = ObsType.lookup(ObsType.TYPPC, "none");
		
		@Override
		double getCategory(double dRate, double dType)
		{
			double dCat = Double.NaN;
			dType = Math.round(dType);
			if (dType == m_dNone || dRate <= 0.0) // ignore no precip
				return dCat;
			else if (dType == m_dRain)
			{
				if (dRate < ObsType.m_dLIGHTRAINMMPERHR)
					dCat = m_dLightRain;
				else if (dRate < ObsType.m_dMEDIUMRAINMMPERHR)
					dCat = m_dModerateRain;
				else
					dCat = m_dHeavyRain;
			}
			else if (dType == m_dFreezingRain)
			{
				if (dRate < ObsType.m_dLIGHTRAINMMPERHR)
					dCat = m_dLightFreezingRain;
				else if (dRate < ObsType.m_dMEDIUMRAINMMPERHR)
					dCat = m_dModerateFreezingRain;
				else
					dCat = m_dHeavyFreezingRain;
			}
			else if (dType == m_dSnow)
			{
				if (dRate < ObsType.m_dLIGHTSNOWMMPERHR)
					dCat = m_dLightSnow;
				else if (dRate < ObsType.m_dMEDIUMSNOWMMPERHR)
					dCat = m_dModerateSnow;
				else
					dCat = m_dHeavySnow;
			}
			else if (dType == m_dIcePellets)
			{
				if (dRate < ObsType.m_dLIGHTSNOWMMPERHR)
					dCat = m_dLightIce;
				else if (dRate < ObsType.m_dMEDIUMSNOWMMPERHR)
					dCat = m_dModerateIce;
				else
					dCat = m_dHeavyIce;
			}
			
			return dCat;
		}
	}
	
	
	private class CategoryByTemp extends CategoryAlgorithm
	{
		@Override
		double getCategory(double dRate, double dTemp)
		{
			double dCat = Double.NaN;
			if (dRate <= 0.0)
				return dCat;

			if (dTemp > ObsType.m_dRAINTEMP) // temp > 2C
			{
				if (dRate < ObsType.m_dLIGHTRAINMMPERHR)
					dCat = m_dLightRain;
				else if (dRate < ObsType.m_dMEDIUMRAINMMPERHR)
					dCat = m_dModerateRain;
				else
					dCat = m_dHeavyRain;
			}
			else if (dTemp > ObsType.m_dSNOWTEMP) // -2C < temp <= 2C
			{
				if (dRate < ObsType.m_dLIGHTRAINMMPERHR)
					dCat = m_dLightFreezingRain;
				else if (dRate < ObsType.m_dMEDIUMRAINMMPERHR)
					dCat = m_dModerateFreezingRain;
				else
					dCat = m_dHeavyFreezingRain;
			}
			else // temp <= -2C
			{
				if (dRate < ObsType.m_dLIGHTSNOWMMPERHR)
					dCat = m_dLightSnow;
				else if (dRate < ObsType.m_dMEDIUMSNOWMMPERHR)
					dCat = m_dModerateSnow;
				else
					dCat = m_dHeavySnow;
			}
			
			return dCat;
		}

	}
}
