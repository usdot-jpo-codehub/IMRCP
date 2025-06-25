/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.collect;

import imrcp.system.FilenameFormatter;
import org.json.JSONObject;

/**
 * Base class containing functions and member variables used by various collectors
 * @author aaron.cherney
 */
public abstract class Collector extends TileFileWriter
{
	protected FilenameFormatter m_oSrcFile;
	/**
	 * Base URL used for downloading data from remote sources
	 */
	protected String m_sBaseURL;
	
	protected int m_nContribId;
	
	protected int m_nObsTypeId;
	
	protected int m_nStrings;
	
	protected int m_nSourceId;
	
	protected boolean m_bCollectRT;
	/**
	 *
	 */
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_sBaseURL = oBlockConfig.optString("url", "");
		m_nContribId = Integer.valueOf(oBlockConfig.optString("contrib", ""), 36);
		m_oSrcFile = new FilenameFormatter(oBlockConfig.optString("src", ""));
		String sObsType = oBlockConfig.optString("obstypeid", "");
		if (sObsType.isEmpty())
			m_nObsTypeId = Integer.MIN_VALUE;
		else
			m_nObsTypeId = Integer.valueOf(sObsType, 36);
		m_nStrings = oBlockConfig.optInt("strings", 0);
		m_nSourceId = Integer.valueOf(oBlockConfig.optString("sourceid", "0"), 36);
		if (m_nSourceId == 0)
			m_nSourceId = Integer.MIN_VALUE;
		m_bCollectRT = oBlockConfig.optBoolean("collectrt", true);
	}
	
	
	/**
	 * Calls {@link imrcp.collect.Collector#getDestFilename(long, int, java.lang.String...)}
	 * with a file index of zero to get the time dependent file name
	 * @param lFileTime Expected collection time of file in milliseconds since the Epoch
	 * @param sStrings String array of values passed to {@link imrcp.system.FilenameFormatter#format(long, long, long, java.lang.String...)}
	 * @return time dependent file name
	 */
	protected String getDestFilename(FilenameFormatter oFf, long lFileTime, int nRange, int nDelay, String... sStrings)
	{
		return getDestFilename(oFf, lFileTime, nRange, nDelay, 0, sStrings);
	}
	
	
	/**
	 * Uses {@link imrcp.collect.Collector#m_oArchiveFile} to call {@link imrcp.system.FilenameFormatter#format(long, long, long, java.lang.String...) 
	 * with the correct timestamps based off of {@link imrcp.collect.Collector#m_oArchiveFile} and
	 * {@link imrcp.collect.Collector#m_oArchiveFile}
	 * @param lFileTime Expected collection time of file in milliseconds since the Epoch
	 * @param nFileIndex Forecast index from source files
	 * @param sStrings String array of values passed to {@link imrcp.system.FilenameFormatter#format(long, long, long, java.lang.String...)}
	 * @return time dependent file name
	 */
	protected String getDestFilename(FilenameFormatter oFf, long lFileTime, int nRange, int nDelay, int nFileIndex, String... sStrings)
	{
		return oFf.format(lFileTime, getStartTime(lFileTime, nRange, nDelay, nFileIndex), getEndTime(lFileTime, nRange, nDelay, nFileIndex), sStrings);
	}
	
	
	protected long getStartTime(long lRecvTime, int nRange, int nDelay, int nFileIndex)
	{
		return lRecvTime + nDelay + (nFileIndex * nRange);
	}
	
	
	protected long getEndTime(long lRecvTime, int nRange, int nDelay, int nFileIndex)
	{
		return lRecvTime + nDelay + nRange + (nFileIndex * nRange);
	}
}
