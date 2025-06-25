/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.system;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import org.apache.logging.log4j.LogManager;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author aaron.cherney
 */
public class ResourceRecord
{
	private int m_nContribId;
	private int m_nObsTypeId;
	private String m_sArchiveFf;
	private String m_sTiledFf;
	private String m_sHrz;
	private String m_sVrt;
	private String m_sTime;
	private String[] m_sLabels;
	private int m_nRange;
	private	int m_nTileFileFrequency;
	private	int m_nArchiveFileFrequency;
	private String m_sSrcUnits;
	private double m_dRound;
	private int m_nZoom;
	private String m_sClassName;
	private byte m_yPref;
	private int m_nTileSize;
	private int m_nMaxFcst;
	private int m_nDelay;
	private int m_nTileSearchOffset;
	private int m_nArchiveSearchOffset;
	private boolean m_bInVaries;
	private int m_nSourceId;
	private String m_sWriter;
	private boolean m_bReprocess;
	private int[] m_nBoundingBox;
	private byte m_yValueType;
	
	private static int[] UNBOUNDED = new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
	public static Comparator<ResourceRecord> COMP_BY_CONTRIB_OBSTYPE = (ResourceRecord o1, ResourceRecord o2) ->
	{
		int nRet = o1.m_nContribId - o2.m_nContribId;
		if (nRet == 0)
			nRet = o1.m_nObsTypeId - o2.m_nObsTypeId;

		return nRet;
	};
	
	
	public static Comparator<ResourceRecord> COMP_BY_OBSTYPE_CONTRIB = (ResourceRecord o1, ResourceRecord o2) ->
	{
		int nRet = o1.m_nObsTypeId - o2.m_nObsTypeId;
		if (nRet == 0)
			nRet = o1.m_nContribId - o2.m_nContribId;

		return nRet;
	};
	
	
	public static Comparator<ResourceRecord> COMP_BY_ARCHIVE = (ResourceRecord o1, ResourceRecord o2) ->
	{
		return o1.m_sArchiveFf.compareTo(o2.m_sArchiveFf);
	};
	
	
	public static Comparator<ResourceRecord> COMP_BY_PREF = (ResourceRecord o1, ResourceRecord o2) ->
	{
		return o1.m_yPref - o2.m_yPref;
	};

	ResourceRecord(int nContribId, int nObsTypeId)
	{
		m_nContribId = nContribId;
		m_nObsTypeId = nObsTypeId;
	}


	protected ResourceRecord(int nContribId, int nObsTypeId, int nSourceId, String sArchiveFf, String sTiledFf, 
		String sHrz, String sVrt, String sTime, String[] sLabels, int nRange, int nTileFileFrequency, int nArchiveFileFrequency, 
		String sSrcUnits, double dRound, int nZoom, String sClassName, byte yPref, int nTileSize, 
		int nMaxFcst, int nDelay, int nTileSearchOffset, int nArchiveSearchOffset, boolean bInVaries, String sWriter, boolean bReprocess, int[] nBoundingBox, byte yValueType)
	{
		this(nContribId, nObsTypeId);
		m_nSourceId = nSourceId;
		m_sArchiveFf = sArchiveFf;
		m_sTiledFf = sTiledFf;
		m_sHrz = sHrz;
		m_sVrt = sVrt;
		m_sTime = sTime;
		m_sLabels = sLabels;
		m_nRange = nRange;
		m_nTileFileFrequency = nTileFileFrequency;
		m_nArchiveFileFrequency = nArchiveFileFrequency;
		m_sSrcUnits = sSrcUnits;
		m_dRound = dRound;
		m_nZoom = nZoom;
		m_sClassName = sClassName;
		m_yPref = yPref;
		m_nTileSize = nTileSize;
		m_nMaxFcst = nMaxFcst;
		m_nDelay = nDelay;
		m_nTileSearchOffset = nTileSearchOffset;
		m_nArchiveSearchOffset = nArchiveSearchOffset;
		m_bInVaries = bInVaries;
		m_sWriter = sWriter;
		m_bReprocess = bReprocess;
		m_nBoundingBox = nBoundingBox;
		m_yValueType = yValueType;
	}
	

	protected static void createResources(JSONObject oConfig, ArrayList<ResourceRecord> oResources)
	{
		try
		{
			String sWriter = oConfig.getString("writer");
			BaseBlock oWriter = Directory.getInstance().lookup(sWriter);
			if (oWriter == null)
				return;
			int nContribId = Integer.valueOf(oConfig.getString("contrib"), 36);
			int nSourceId = oConfig.has("sourceid") ? Integer.valueOf(oConfig.getString("sourceid"), 36) : nContribId;
			String sArchive = oConfig.optString("archiveff");				
			if (sArchive.startsWith("/"))
				sArchive = sArchive.substring(1);
			if (!sArchive.isEmpty())
				sArchive = oWriter.m_sArchPath + sArchive;
			String sTile = oConfig.getString("tileff");
			if (sTile.startsWith("/"))
				sTile = sTile.substring(1);
			if (!sTile.isEmpty())
				sTile = oWriter.m_sDataPath + sTile;
			String sHrz = oConfig.optString("hrz");
			String sVrt = oConfig.optString("vrt");
			String sTime = oConfig.optString("time");
			int nRange = oConfig.getInt("range");
			int nFreq = oConfig.getInt("freq");
			int nArchiveFreq = oConfig.optInt("archivefreq", nFreq);
			int nZoom = oConfig.optInt("zoom", 8);
			String[] sObsTypes = JSONUtil.getStringArray(oConfig, "obsid");
			
			String[] sSrcUnits = JSONUtil.getStringArray(oConfig, "srcunits");
			double[] dRound = JSONUtil.getDoubleArray(oConfig, "round");			
			String[] sClassNames = JSONUtil.getStringArray(oConfig, "classes");
			int[] nPref = JSONUtil.getIntArray(oConfig, "preference");
			int nTileSize = oConfig.optInt("tilesize", 16);
			int nMaxFcst = oConfig.optInt("maxfcst", nRange);
			int nDelay = oConfig.optInt("delay", 0);
			int nTileSearchOffset = oConfig.optInt("tilesearchoffset", 0);
			int nArchiveSearchOffset = oConfig.optInt("archivesearchoffset", 0);
			boolean bReprocess = oConfig.optBoolean("reprocess", true);
			boolean bInVaries = oConfig.optBoolean("varies", false);
			
			int[] nBoundingBox = JSONUtil.optIntArray(oConfig, "boundingbox", UNBOUNDED);
			int[] nValueTypes = JSONUtil.getIntArray(oConfig, "valuetype");
			for (int nIndex = 0; nIndex < sObsTypes.length; nIndex++)
			{
				byte yPref = nPref.length == 0 ? Byte.MAX_VALUE : (byte)nPref[nIndex];
				String[] sLabels = JSONUtil.getStringArray(oConfig, sObsTypes[nIndex]);
				oResources.add(new ResourceRecord(nContribId, Integer.valueOf(sObsTypes[nIndex], 36), nSourceId, sArchive, sTile, 
					sHrz, sVrt, sTime, sLabels, nRange, nFreq, nArchiveFreq, sSrcUnits[nIndex], dRound[nIndex], nZoom, 
					sClassNames[nIndex].isEmpty() ? "imrcp.collect.NWSTileFileWriterJni" : sClassNames[nIndex], yPref, nTileSize, 
					nMaxFcst, nDelay, nTileSearchOffset, nArchiveSearchOffset, bInVaries, sWriter, bReprocess, nBoundingBox, (byte)nValueTypes[nIndex]));
			}
		}
		catch (JSONException | ArrayIndexOutOfBoundsException oJEx)
		{
			LogManager.getLogger(ResourceRecord.class.getName()).error(oJEx, oJEx);
			Directory.getInstance().addConfigError(oConfig.getString("contrib"));
		}
	}
	
	
	public int getRange()
	{
		return m_nRange;
	}
	
	
	public double getRound()
	{
		return m_dRound;
	}
	
	
	public int getContribId()
	{
		return m_nContribId;
	}
	
	
	public int getTileFileFrequency()
	{
		return m_nTileFileFrequency;
	}
	
	
	public int getObsTypeId()
	{
		return m_nObsTypeId;
	}
	
	
	public int getZoom()
	{
		return m_nZoom;
	}
	
	
	public String getArchiveFf()
	{
		return m_sArchiveFf;
	}
	
	
	public String getTiledFf()
	{
		return m_sTiledFf;
	}
	
	
	public int getMaxFcst()
	{
		return m_nMaxFcst;
	}
	
	
	public int getTileSize()
	{
		return m_nTileSize;
	}
	
	
	public int getDelay()
	{
		return m_nDelay;
	}
	
	
	public byte getPreference()
	{
		return m_yPref;
	}
	
	public int getTileSearchOffset()
	{
		return m_nTileSearchOffset;
	}
	
	
	public int getArchiveSearchOffset()
	{
		return m_nArchiveSearchOffset;
	}
	
	
	public int getArchiveFileFrequency()
	{
		return m_nArchiveFileFrequency;
	}
	
	public String getSrcUnits()
	{
		return m_sSrcUnits;
	}
	
	
	public boolean getInVaries()
	{
		return m_bInVaries;
	}
	
	
	public int getSourceId()
	{
		return m_nSourceId;
	}
	
	
	public String getHrz()
	{
		return m_sHrz;
	}
	
	
	public String getVrt()
	{
		return m_sVrt;
	}
	
	
	public String getTime()
	{
		return m_sTime;
	}
	
	
	public String[] getLabels()
	{
		return m_sLabels;
	}
	
	
	public String getClassName()
	{
		return m_sClassName;
	}
	
	
	public String getWriter()
	{
		return m_sWriter;
	}
	
	
	public boolean getReprocess()
	{
		return m_bReprocess;
	}
	
	
	public int[] getBoundingBox()
	{
		return m_nBoundingBox;
	}
	
	
	public byte getValueType()
	{
		return m_yValueType;
	}
	
	public Path getFilename(long lValid, long lStart, long lEnd, FilenameFormatter oFF)
	{
		int nObstype = m_bInVaries ? ObsType.VARIES : m_nObsTypeId;
		String sObstype = Integer.toString(nObstype, 36);
		return Paths.get(oFF.format(lValid, lStart, lEnd, sObstype, sObstype));
	}
}
