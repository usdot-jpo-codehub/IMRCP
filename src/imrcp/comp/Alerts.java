package imrcp.comp;

import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.Mercator;
import imrcp.geosrv.Network;
import imrcp.geosrv.WayNetworks;
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
import imrcp.system.Units;
import imrcp.system.Util;
import imrcp.system.XzBuffer;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeSet;
import org.json.JSONObject;

/**
 * This class is used to generate Alerts when observed or forecasted values 
 * meet certain conditions.
 * @author aaron.cherney
 */
public class Alerts extends TileFileWriter
{
	/**
	 * Stores {@link imrcp.comp.Alerts.Rule}s by observation type 
	 */
	private final HashMap<Integer, ArrayList<Rule>> m_oRules = new HashMap();

	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		String[] sRules = JSONUtil.getStringArray(oBlockConfig, "rules");
		for (String sRule : sRules) // for each configured rule create the object
		{
			String[] sConditions = JSONUtil.getStringArray(oBlockConfig, sRule);
			
			if (sConditions.length != 5 || sConditions.length == 0) // skip invalid configurations
			{
				m_oLogger.error("Incorrect length for rule: " + sRule);
				continue;
			}
			Rule oRule = new Rule(sConditions);
			if (!m_oRules.containsKey(oRule.m_nObsType))
				m_oRules.put(oRule.m_nObsType, new ArrayList());
			m_oRules.get(oRule.m_nObsType).add(oRule);
		}
	}

	@Override
	protected void createFiles(TileFileInfo oInfo, TreeSet<Path> oArchiveFiles)
	{
		ResourceRecord oRR = oInfo.m_oRRs.get(0);
		int nFreq = oRR.getTileFileFrequency();
		int nRange = oRR.getRange();
		long lOverallStart = oInfo.m_lStart / nFreq * nFreq; // create alerts by hour
		long lOverallEnd = lOverallStart + nRange;
		if (oInfo.m_lEnd > lOverallEnd)
		{
			lOverallEnd = oInfo.m_lEnd / nFreq * nFreq;
		}
		long lRefTime = oInfo.m_lRef / nFreq * nFreq;
		
		
		FilenameFormatter oTileFf = new FilenameFormatter(oRR.getTiledFf());
		WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
		TileObsView oOV = (TileObsView)Directory.getInstance().lookup("ObsView");
		int[] nProcessBB = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (Network oNetwork : oWayNetworks.getNetworks())
		{
			int[] nNetworkBB = oNetwork.getBoundingBox();
			if (nNetworkBB[0] < nProcessBB[0])
				nProcessBB[0] = nNetworkBB[0];
			if (nNetworkBB[1] < nProcessBB[1])
				nProcessBB[1] = nNetworkBB[1];
			if (nNetworkBB[2] > nProcessBB[2])
				nProcessBB[2] = nNetworkBB[2];
			if (nNetworkBB[3] > nProcessBB[3])
				nProcessBB[3] = nNetworkBB[3];
		}
		
		while (lOverallStart < lOverallEnd)
		{
			long lStartTime = lOverallStart;
			long lEndTime = lStartTime + nRange;
			lOverallStart = lEndTime;
			Path oTiledFile = oRR.getFilename(lRefTime, lStartTime, lEndTime, oTileFf);
			
			if (Files.exists(oTiledFile))
				continue;
			
			String sTiledFile = oTiledFile.toString();
			synchronized (m_oProcessing)
			{
				if (!m_oProcessing.add(sTiledFile))
					continue;
			}
			m_oLogger.debug("Started " + sTiledFile);
			try
			{
				
				Units oUnits = Units.getInstance();
				ArrayList<TileForFile> oTiles = new ArrayList();
				TileForFile oSearch = new TileForFile();
				int[] nTile = new int[2];
				int nPPT = (int)Math.pow(2, oRR.getTileSize()) - 1;
				Mercator oM = new Mercator(nPPT);
				int[] nBB = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
				for (Entry<Integer, ArrayList<Rule>> oRuleSet : m_oRules.entrySet())
				{
					int nObstype = oRuleSet.getKey();
					ObsList oData = oOV.getPreferedData(nObstype, nProcessBB, lStartTime, lEndTime, lRefTime);
					if (oData.isEmpty())
						continue;

					ArrayList<Rule> oRules = oRuleSet.getValue();
					Units.UnitConv[] oConvs = new Units.UnitConv[oRules.size()];
					String sTo = ObsType.getUnits(nObstype, true);
					for (int nIndex = 0; nIndex < oConvs.length; nIndex++)
						oConvs[nIndex] = oUnits.getConversion(oRules.get(nIndex).m_sUnits, sTo);

					for (Obs oObs : oData)
					{
						for (int nRuleIndex = 0; nRuleIndex < oRules.size(); nRuleIndex++)
						{
							Rule oRule = oRules.get(nRuleIndex);
							if (oRule.evaluate(oObs.m_dValue))
							{
								Obs oAlert = new Obs(ObsType.EVT, oRR.getContribId(), Id.NULLID, oObs.m_lObsTime1, oObs.m_lObsTime2, oObs.m_lTimeRecv, oObs.m_oGeoArray, oObs.m_yGeoType, oRule.m_nType);
								if (oObs.m_yGeoType == Obs.POINT)
								{
									if (oObs.m_oGeoArray[1] < nBB[0])
										nBB[0] = oObs.m_oGeoArray[1];
									if (oObs.m_oGeoArray[2] < nBB[1])
										nBB[1] = oObs.m_oGeoArray[2];
									if (oObs.m_oGeoArray[1] > nBB[2])
										nBB[2] = oObs.m_oGeoArray[1];
									if (oObs.m_oGeoArray[2] > nBB[3])
										nBB[3] = oObs.m_oGeoArray[2];

									oAlert.m_nObsFlag = Obs.POINT;
									oM.lonLatToTile(GeoUtil.fromIntDeg(oObs.m_oGeoArray[1]), GeoUtil.fromIntDeg(oObs.m_oGeoArray[2]), oRR.getZoom(), nTile);
									oSearch.m_nX = nTile[0];
									oSearch.m_nY = nTile[1];
									int nSearch = Collections.binarySearch(oTiles, oSearch);
									if (nSearch < 0)
									{
										nSearch = ~nSearch;
										oTiles.add(nSearch, new TileForFile(nTile[0], nTile[1]));
									}

									oTiles.get(nSearch).m_oObsList.add(oAlert);
								}
								else if (oObs.m_yGeoType == Obs.POLYGON)
								{
									if (oObs.m_oGeoArray[3] < nBB[0])
										nBB[0] = oObs.m_oGeoArray[3];
									if (oObs.m_oGeoArray[4] < nBB[1])
										nBB[1] = oObs.m_oGeoArray[4];
									if (oObs.m_oGeoArray[5] > nBB[2])
										nBB[2] = oObs.m_oGeoArray[5];
									if (oObs.m_oGeoArray[6] > nBB[3])
										nBB[3] = oObs.m_oGeoArray[6];

									oAlert.m_nObsFlag = Obs.POLYGON;
									oM.lonLatToTile(GeoUtil.fromIntDeg(oObs.m_oGeoArray[3]), GeoUtil.fromIntDeg(oObs.m_oGeoArray[6]), oRR.getZoom(), nTile);
									int nStartX = nTile[0]; 
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

											int nSearch = Collections.binarySearch(oTiles, oSearch);
											if (nSearch < 0)
											{
												nSearch = ~nSearch;
												oTiles.add(nSearch, new TileForFile(nTileX, nTileY));
											}
											oTiles.get(nSearch).m_oObsList.add(oAlert);
										}
									}
								}
								break;
							}
						}
					}
				}

				Files.createDirectories(oTiledFile.getParent());
				for (TileForFile oTile : oTiles)
				{
					oTile.m_oSP = null;
					oTile.m_oM = oM;
					oTile.m_oRR = oRR;
					oTile.m_lFileRecv = lRefTime;
					oTile.m_bWriteObsFlag = true;
					oTile.m_bWriteRecv = true;
					oTile.m_bWriteStart = true;
					oTile.m_bWriteEnd = true;
					oTile.m_bWriteObsType = false;
				}
				Scheduling.processCallables(oTiles, m_nThreads);
				int nTileIndex = oTiles.size();
				while (nTileIndex-- > 0)
				{
					if (oTiles.get(nTileIndex).m_yTileData == null)
						oTiles.remove(nTileIndex);
				}
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
					oOut.writeLong(lRefTime);
					oOut.writeInt((int)((lEndTime - lRefTime) / 1000)); // end time offset from received time
					oOut.writeByte(1); // only file start time
					oOut.writeInt((int)((lStartTime - lRefTime) / 1000));

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
			finally
			{
				m_oLogger.debug("Finished " + sTiledFile);
				synchronized (m_oProcessing)
				{
					m_oProcessing.remove(sTiledFile);
				}
			}
		}	
	}

	
	/**
	 * Stores information that define rules for alerts
	 */
	private class Rule
	{
		/**
		 * Alert type 
		 * 
		 * @see imrcp.system.ObsType#LOOKUP for complete list of types 
		 */
		int m_nType;
		
		
		/**
		 * Observation type of values that are checked for this rule
		 */
		int m_nObsType;

		
		/**
		 * Minimum value an observation can be to match this rule, inclusive
		 */
		double m_dMin;

		
		/**
		 * Maximum value an observation can be to match this rule, exclusive
		 */
		double m_dMax;

		
		/**
		 * Units of the min and max value
		 */
		String m_sUnits;

		
		/**
		 * Constructor that uses string arrays found in the configuration file
		 * @param sValues [alert type, observation type, min, max, units]
		 */
		Rule(String[] sValues)
		{
			m_nType = ObsType.lookup(ObsType.EVT, sValues[0]); // the first entry is the alert(event) type
			m_nObsType = Integer.valueOf(sValues[1], 36);
			if (sValues[2].matches("^[a-zA-Z_\\/\\-]+( [a-zA-Z0-9_\\/\\-]+)*$")) // if the value is a word with spaces or a hyphen, meaning it is a string that has a lookup value in ObsType
				m_dMin = ObsType.lookup(m_nObsType, sValues[2]);
			else if (!sValues[2].isEmpty())
				m_dMin = Double.parseDouble(sValues[2]);
			else
				m_dMin = -Double.MAX_VALUE;
			if (sValues[3].matches("^[a-zA-Z_\\/\\-]+( [a-zA-Z0-9_\\/\\-]+)*$")) // if the value is a word with spaces or a hyphen, meaning it is a string that has a lookup value in ObsType
				m_dMax = ObsType.lookup(m_nObsType, sValues[3]);
			else if (!sValues[3].isEmpty())
				m_dMax = Double.parseDouble(sValues[3]);
			else
				m_dMax = Double.MAX_VALUE;
			m_sUnits = sValues[4];
		}
		
		
		/**
		 * Checks if the given value fulfills this rule. If {@link m_dMin} and
		 * {@link m_dMax} are the same, the given value must equal {@link m_dMin}
		 * to fulfill the condition of the rule. Otherwise the given value must
		 * be in between {@link m_dMin} (inclusive) and {@link m_dMax] (exclusive)
		 * @param dObsValue value to check
		 * @return true if the given value fulfills the condition of the rule
		 */
		public boolean evaluate(double dObsValue)
		{
			if (Double.compare(m_dMin, m_dMax) == 0)
				return Double.compare(dObsValue, m_dMin) == 0;
			else
				return m_dMin <= dObsValue && m_dMax > dObsValue;
		}
	}
}
