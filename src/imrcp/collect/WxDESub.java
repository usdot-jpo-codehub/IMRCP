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
import imrcp.system.CsvReader;
import imrcp.system.Directory;
import imrcp.system.FilenameFormatter;
import imrcp.system.Id;
import imrcp.system.Introsort;
import imrcp.system.JSONUtil;
import imrcp.system.ObsType;
import imrcp.system.ResourceRecord;
import imrcp.system.Scheduling;
import imrcp.system.StringPool;
import imrcp.system.Units;
import imrcp.system.Units.UnitConv;
import imrcp.system.Util;
import imrcp.system.XzBuffer;
import imrcp.web.SecureBaseBlock;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.TreeSet;
import org.json.JSONObject;

/**
 * Collector for Weather Data Environment subscription files
 * @author aaron.cherney
 */
public class WxDESub extends Collector
{
	/**
	 * Timeout in milliseconds used for {@link java.net.URLConnection}
	 */
	private int m_nTimeout;

	
	/**
	 * UUID of the WxDE subscription
	 */
	private String m_sSubUuid;

	
	/**
	 * Time in seconds subtracted from the collection time to get the correct
	 * time for the source file
	 */
	private int m_nCollectOffset;
	
	
		/**
	 * Maps WxDE observation type ids to IMRCP observation type ids
	 */
	private static final HashMap<Integer, Integer> OBSTYPEMAP = new HashMap();

	
	/**
	 * Maps IMRCP observation type ids to HashMaps that map WxDE enumerated values
	 * to IMRCP enumerated values
	 */
	private static final HashMap<Integer, HashMap<Integer, Integer>> MAPPEDVALUES = new HashMap();

	
	/**
	 * Format String used to create {@link SimpleDateFormat} objects
	 */
	private static String m_sDateFormat = "yyyy-MM-dd HH:mm:ss";
	
	
	/**
	 * 
	 */
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_nTimeout = oBlockConfig.optInt("conntimeout", 90000);
		m_sSubUuid = oBlockConfig.optString("uuid", "");
		m_nCollectOffset = oBlockConfig.optInt("coloffset", 0);
		if (OBSTYPEMAP.isEmpty())
		{
			int[] nWxdeObsTypes = JSONUtil.getIntArray(oBlockConfig, "wxdeobs");
			String[] sImrcpObsTypes = JSONUtil.getStringArray(oBlockConfig, "imrcpobs");
			for (int nIndex = 0; nIndex < nWxdeObsTypes.length; nIndex++)
				OBSTYPEMAP.put(nWxdeObsTypes[nIndex], Integer.valueOf(sImrcpObsTypes[nIndex], 36));

			String[] sWxdeMappedValues = JSONUtil.getStringArray(oBlockConfig, "wxdemapped");
			for (String sObsType : sWxdeMappedValues)
			{
				HashMap<Integer, Integer> oMap = new HashMap();
				int[] nMappings = JSONUtil.getIntArray(oBlockConfig, sObsType);
				for (int nIndex = 0; nIndex < nMappings.length; nIndex += 2)
					oMap.put(nMappings[nIndex], nMappings[nIndex + 1]);

				MAPPEDVALUES.put(Integer.valueOf(sObsType, 36), oMap);
			}
		}
	}
	
	
	/**
	 * Calls {@link WxDESub#execute()} then sets a schedule to execute on a 
	 * fixed interval.
	 * @return true if no exceptions are thrown
	 */
	@Override
	public boolean start()
	{
		if (m_bCollectRT)
		{
			execute();
			m_nSchedId = Scheduling.getInstance().createSched(this, m_nOffset, m_nPeriod);
		}
		return true;
	}
	
	
	/**
	 * Downloads the current subscription file from WXDE and appends it to the
	 * rolling observation file.
	 */
	@Override
	public void execute()
	{
		try
		{
			int nPeriodMillis = m_nPeriod * 1000;
			long lTimestamp = (System.currentTimeMillis() / nPeriodMillis) * nPeriodMillis + m_nOffset * 1000;
			
			String sSrc = m_sBaseURL + m_oSrcFile.format(lTimestamp - m_nCollectOffset * 1000, lTimestamp, lTimestamp + m_nPeriod, m_sSubUuid);
			URLConnection oConn = SecureBaseBlock.getTrustingConnection(sSrc);
			oConn.setConnectTimeout(m_nTimeout);
			oConn.setReadTimeout(m_nTimeout);
			m_oLogger.info("Downloading " + sSrc);
			ByteArrayOutputStream oBaos = new ByteArrayOutputStream();
			
			try (BufferedInputStream oIn = new BufferedInputStream(oConn.getInputStream()))
			{
				oBaos.write(oIn.readAllBytes());
			}
			
			ResourceRecord oRR = Directory.getResource(m_nContribId, ObsType.VARIES);
			Path oArchive = Paths.get(new FilenameFormatter(oRR.getArchiveFf()).format(lTimestamp, lTimestamp + oRR.getDelay(), lTimestamp + oRR.getDelay() + oRR.getRange()));
			Files.createDirectories(oArchive.getParent());
			m_oLogger.info("Writing " + oArchive.toString());
			try (BufferedOutputStream oOut = new BufferedOutputStream(Files.newOutputStream(oArchive)))
			{
				oOut.write(oBaos.toByteArray());
			}
			
			ArrayList<ResourceRecord> oRRs = Directory.getResourcesByContrib(m_nContribId);
			processRealTime(oRRs, lTimestamp, lTimestamp, lTimestamp);
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
			Units oUnits = Units.getInstance();
			ResourceRecord oRR = oInfo.m_oRRs.get(0);
			SimpleDateFormat oSdf = new SimpleDateFormat(m_sDateFormat);
			oSdf.setTimeZone(Directory.m_oUTC);
			for (Path oArchive : oArchiveFiles)
			{
				int[] nBB = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
				FilenameFormatter oArchiveFf = new FilenameFormatter(oRR.getArchiveFf());
				long[] lTimes = new long[3];
				oArchiveFf.parse(oArchive.toString(), lTimes);
				ObsList oObsList = new ObsList();
				long lMaxTime = lTimes[FilenameFormatter.END];
				long lMinTime = lTimes[FilenameFormatter.START];
				long lFileTime = lTimes[FilenameFormatter.VALID];
				long lTimeLimit = lFileTime - 3600000; // ignore stale obs
				try (CsvReader oIn = new CsvReader(Files.newInputStream(oArchive)))
				{
					oIn.readLine(); // skip header
					int nCol;
					while ((nCol = oIn.readLine()) > 0)
					{
						if (nCol > 1) // skip end of record line
						{
							double dConf = oIn.parseDouble(19);
							if (dConf < 1) // ignore records that fail some of the quality checking
								continue;
							WxDERecord oRec = new WxDERecord(oIn, oSdf);
							if (!OBSTYPEMAP.containsKey(oRec.m_nObsTypeId)) // skip observation that are not in IMRCP
								continue;
							int nType = OBSTYPEMAP.get(oRec.m_nObsTypeId);
							double dVal = oRec.m_dVal;
							if (MAPPEDVALUES.containsKey(nType)) // look up enumerated values if needed
							{
								HashMap<Integer, Integer> oMap = MAPPEDVALUES.get(nType);
								if (oMap.containsKey((int)dVal))
									dVal = oMap.get((int)dVal);
								else
									continue;
							}
							
							if (oRec.m_lTimestamp < lTimeLimit)
								continue;
							if (oRec.m_lTimestamp > lMaxTime)
								lMaxTime = oRec.m_lTimestamp;
							if (oRec.m_lTimestamp < lMinTime)
								lMinTime = oRec.m_lTimestamp;

							if (oRec.m_nLon < nBB[0])
								nBB[0] = oRec.m_nLon;
							if (oRec.m_nLat < nBB[1])
								nBB[1] = oRec.m_nLat;
							if (oRec.m_nLon > nBB[2])
								nBB[2] = oRec.m_nLon;
							if (oRec.m_nLat > nBB[3])
								nBB[3] = oRec.m_nLat;

							int[] nGeo = Obs.createPoint(oRec.m_nLon, oRec.m_nLat);
							int nFlags = Obs.POINT; // ls nybble is geo type which is 1 = point
							if (oRec.m_sCategory.toLowerCase().compareTo("m") == 0) // mobile observations
								nFlags |= 0b01000000; // ms nybble is obs flags (bridge, mobile, event, reserved)
							Obs oObs = new Obs(nType, m_nContribId, Id.NULLID, oRec.m_lTimestamp, oRec.m_lTimestamp + 3600000, oRec.m_lTimestamp, nGeo, Obs.POINT, dVal);
							oObs.m_sStrings = new String[8];
							oObs.m_sStrings[0] = oRec.m_sContributor;
							oObs.m_sStrings[1] = oRec.m_sStationCode;
							oObs.m_sStrings[2] = Integer.toString(oRec.m_nSensorId);
							oObs.m_sStrings[3] = Integer.toString(oRec.m_nSensorIndex);
							oObs.m_nObsFlag = nFlags;
							oObsList.add(oObs);
						}
					}
				}

				Introsort.usort(oObsList, (Obs o1, Obs o2) -> 
				{
					int nReturn = o1.m_oGeoArray[2] - o2.m_oGeoArray[2];
					if (nReturn == 0)
					{
						nReturn = o1.m_oGeoArray[1] - o2.m_oGeoArray[1];
						if (nReturn == 0)
						{
							nReturn = o1.m_nObsTypeId - o2.m_nObsTypeId; 
						}
					}

					return nReturn;
				});


				m_oLogger.info("Starting tile file for varies");
				int[] nTile = new int[2];
				int nPrevType = Integer.MIN_VALUE;
				FilenameFormatter oFF = new FilenameFormatter(oRR.getTiledFf());
				Path oTiledFile = oRR.getFilename(lFileTime, lMinTime, lMaxTime + 3600000, oFF);
				Files.createDirectories(oTiledFile.getParent());
				if (oObsList.isEmpty() && !Files.exists(oTiledFile))
				{
					Files.createFile(oTiledFile);
					return;
				}
				UnitConv oConv = null;
				int nPPT = (int)Math.pow(2, oRR.getTileSize()) - 1;
				Mercator oM = new Mercator(nPPT);
				ArrayList<TileForFile> oTiles = new ArrayList();
				StringPool oSP = new StringPool();
				for (Obs oObs : oObsList)
				{
					if (oObs.m_nObsTypeId != nPrevType)
					{
						oRR = Directory.getResource(m_nContribId, oObs.m_nObsTypeId);
						oConv = oUnits.getConversion(oRR.getSrcUnits(), ObsType.getUnits(oRR.getObsTypeId(), true));
					}
					for (int nString = 0; nString < m_nStrings; nString++)
						oSP.intern(oObs.m_sStrings[nString]);
					oM.lonLatToTile(GeoUtil.fromIntDeg(oObs.m_oGeoArray[1]), GeoUtil.fromIntDeg(oObs.m_oGeoArray[2]), oRR.getZoom(), nTile);
					oObs.m_dValue = oConv.convert(oObs.m_dValue);
					TileForFile oTile = new TileForFile(nTile[0], nTile[1]);
					int nIndex = Collections.binarySearch(oTiles, oTile);
					if (nIndex < 0)
					{
						nIndex = ~nIndex;
						oTiles.add(nIndex, oTile);
					}

					oTiles.get(nIndex).m_oObsList.add(oObs);
				}

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
					oTile.m_lFileRecv = lFileTime;
					oTile.m_nStringFlag = nStringFlag;
					oTile.m_bWriteObsFlag = true;
					oTile.m_bWriteObsType = true;
					oTile.m_bWriteRecv = true;
					oTile.m_bWriteStart = true;
					oTile.m_bWriteEnd = true;
				}
				Scheduling.processCallables(oTiles, m_nThreads);
				try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(oTiledFile))))
				{
					oOut.writeByte(1); // version
					oOut.writeInt(nBB[0]); // bounds min x
					oOut.writeInt(nBB[1]); // bounds min y
					oOut.writeInt(nBB[2]); // bounds max x
					oOut.writeInt(nBB[3]); // bounds max y
					oOut.writeInt(ObsType.VARIES); // obsversation type
					oOut.writeByte(Util.combineNybbles(1, oRR.getValueType())); // obs flag present on obs = 1 (upper nybble) value type (lower nybble)
					oOut.writeByte(Obs.POINT);
					oOut.writeByte(0); // id format: -1=variable, 0=null, 16=uuid, 32=32-bytes
					oOut.writeByte(Util.combineNybbles(Id.SENSOR, 0b0111)); // associate with obj and timestamp flag, assoc as sensor(point). the lower bits are all 1 since recv, start, and end time are written per obs
					oOut.writeLong(lFileTime);
					oOut.writeInt((int)((lMaxTime + 3600000 - lFileTime) / 1000)); // end time offset from received time
					oOut.writeByte(1); // only file start time, others are written per record
					oOut.writeInt((int)((lMinTime - lFileTime) / 1000));
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
				m_oLogger.info("Finished tile file for varies");
			}
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
	
	
	/**
	 * Encapsulates the fields from the WxDE subscription file that are needed 
	 * by IMRCP
	 */
	private class WxDERecord
	{
		/**
		 * WxDE observation type id
		 */
		int m_nObsTypeId;

		
		/**
		 * Station id
		 */
		int m_nStationId;

		
		/**
		 * Sensor id
		 */
		int m_nSensorId;
		
		
		/**
		 * Sensor index
		 */
		int m_nSensorIndex;

		
		/**
		 * Contributor of the data
		 */
		String m_sContributor;

		
		/**
		 * WxDE station code
		 */
		String m_sStationCode;

		
		/**
		 * Time in milliseconds since Epoch the observation is associated with
		 */
		long m_lTimestamp;

		
		/**
		 * Latitude in decimal degrees scaled to 7 decimal places
		 */
		int m_nLat;

		
		/**
		 * Longitude in decimal degrees scaled to 7 decimal places
		 */
		int m_nLon;

		
		/**
		 * Observation value
		 */
		double m_dVal;

		
		/**
		 * Units of the observed value
		 */
		String m_sUnits;

		
		/**
		 * Category of the station. P = permanent. M = mobile
		 */
		String m_sCategory;
		
		
		/**
		 * Constructs a WxDERecord from a line of a WxDE subscription file
		 * @param oIn CsvReader ready to parse the current line.
		 * @param oSdf Date/time parsing object
		 * @throws Exception
		 */
		WxDERecord(CsvReader oIn, SimpleDateFormat oSdf)
			throws Exception
		{
			m_nObsTypeId = oIn.parseInt(1);
			String sLat = oIn.parseString(12);
			String sLon = oIn.parseString(13);
			m_nLat = sLat.contains(".") ? GeoUtil.toIntDeg(oIn.parseDouble(12)) : oIn.parseInt(12) * 10; // multiple by ten since WxDE lon/lats are scaled to 6 decimal places and IMRCP used 7
			m_nLon = sLon.contains(".") ? GeoUtil.toIntDeg(oIn.parseDouble(13)) : oIn.parseInt(13) * 10;
			m_nStationId = oIn.parseInt(5); // station id
			m_nSensorId = oIn.parseInt(3);
			m_nSensorIndex = oIn.parseInt(4); // sensor index
			m_lTimestamp = oSdf.parse(oIn.parseString(11)).getTime();
			m_dVal = oIn.parseDouble(15); // observation value
			m_sUnits = oIn.parseString(16);
			m_sContributor = oIn.parseString(9);
			m_sStationCode = oIn.parseString(10);
			m_sCategory = oIn.parseString(7);
		}
	}
}
