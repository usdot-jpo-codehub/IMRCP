/*
 * Copyright 2018 Synesis-Partners.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package imrcp.web;

import imrcp.system.BaseBlock;
import imrcp.system.Directory;
import imrcp.system.JSONUtil;
import imrcp.system.Scheduling;
import imrcp.system.Text;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map.Entry;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;

/**
 * This servlet manages requests for system monitor information.
 * @author aaron.cherney
 */
public class MonitorServlet extends BaseBlock
{
	/**
	 * Format String used for creating SimpleDateFormats
	 */
	private final String SDFFORMAT = "yyyy-MM-dd HH:mm:ss";

	
	/**
	 * Maps BaseBlock instance names to time in millisecond that block can have
	 * the status {@link #RUNNING} before being flagged as something is wrong. 
	 * Only the BaseBlock's that have an entry in this map are monitored.
	 */
	private HashMap<String, Integer> m_oTimeouts = new HashMap();

	
	/**
	 * Path to the file to write monitor messages
	 */
	private String m_sFilename;

	
	/**
	 * Period of execution in seconds
	 */
	private int m_nPeriod;

	
	/**
	 * Schedule offset from midnight in seconds
	 */
	private int m_nOffset;

	
	/**
	 * Token used to authenticate monitor requests
	 */
	private String m_sToken;

	
	/**
	 * Initializes the block. Wrapper for {@link #setName(java.lang.String)}, 
	 * {@link #setLogger()}, {@link #setConfig()}, {@link #register()}, and 
	 * {@link #startService()}
	 * 
	 * @param oSConfig object containing configuration parameters in Tomcat's
	 * @throws ServletException
	 */
	@Override
	public void init(ServletConfig oSConfig)
		throws ServletException
	{
		try
		{
			setName(oSConfig.getServletName());
			setLogger();
			reset();
			register();
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
			throw new ServletException(oEx);
		}
	}

	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		int nDefault = oBlockConfig.optInt("timeout", 300000);
		String[] sBlocks = JSONUtil.getStringArray(oBlockConfig, "blocks");
		for (String sBlock : sBlocks)
			m_oTimeouts.put(sBlock, oBlockConfig.optInt(sBlock, nDefault));
		m_sFilename = oBlockConfig.optString("file", "");
		m_nPeriod = oBlockConfig.optInt("period", 300);
		m_nOffset = oBlockConfig.optInt("offset", 0);
		m_sToken = oBlockConfig.optString("token", Text.toHexString(getBytes(16)));
	}
	
	
	/**
	 * Writes the current time to the monitor file and ts a schedule to execute 
	 * on a fixed interval.
	 * @return
	 * @throws Exception
	 */
	@Override
	public boolean start() throws Exception
	{
		SimpleDateFormat oSdf = new SimpleDateFormat(SDFFORMAT);
		oSdf.setTimeZone(Directory.m_oUTC);
		synchronized (this)
		{
			try (BufferedWriter oOut = new BufferedWriter(new FileWriter(m_sFilename)))
			{
				oOut.append(oSdf.format(System.currentTimeMillis())).append("\n");
			}
		}
		
		m_nSchedId = Scheduling.getInstance().createSched(this, m_nOffset, m_nPeriod);
		return true;
	}
	
	
	/**
	 * For each BaseBlock configured in {@link #m_oTimeouts} it checks the status
	 * of the block and determines if the block is executing in a normal/expected
	 * fashion.
	 */
	@Override
	public void execute()
	{
		try
		{
			long lNow = System.currentTimeMillis();

			StringBuilder sBuffer = new StringBuilder();
			SimpleDateFormat oSdf = new SimpleDateFormat(SDFFORMAT);
			oSdf.setTimeZone(Directory.m_oUTC);
			Directory oDir = Directory.getInstance();
			for (Entry<String, Integer> oBlockTimeout : m_oTimeouts.entrySet())
			{
				String sName = oBlockTimeout.getKey();
				BaseBlock oBlock = oDir.lookup(sName); // get the block reference from Directory
				if (oBlock == null)
					continue;
				long[] lStatus = oDir.lookup(sName).status(); // get status
				int nStatus = (int)lStatus[0];
				
				String sGoodBad = "good";
				if (nStatus == BaseBlock.IDLE || nStatus == BaseBlock.INIT || nStatus == BaseBlock.STOPPING || nStatus == BaseBlock.STOPPED) // these statuses mean nothing is going wrong
				{
					sBuffer.append(String.format("%s,%s,%s,%s\n", sName, oSdf.format(lStatus[1]), STATUSES[nStatus], sGoodBad));
					continue;
				}
				
				if (lNow - lStatus[1] > oBlockTimeout.getValue() || nStatus == BaseBlock.ERROR) // the status is either running or error, if running check if it has been running for too long indicting a stalled process
					sGoodBad = "bad";
				sBuffer.append(String.format("%s,%s,%s,%s\n", sName, oSdf.format(lStatus[1]), STATUSES[nStatus], sGoodBad));
			}
			sBuffer.append(Directory.getInstance().getConfigErrors());
			
			sBuffer.insert(0, oSdf.format(System.currentTimeMillis()) + "\n");
			synchronized (this)
			{
				try (BufferedWriter oOut = new BufferedWriter(new FileWriter(m_sFilename))) // write the messages to the file
				{
					oOut.append(sBuffer);
				}
			}
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
	}
	
	
	/**
	 * If the correct token is part of the request, the monitor file is added to
	 * the response.
	 * 
	 * @param iReq object that contains the request the client has made of the servlet
	 * @param iRep object that contains the response the servlet sends to the client
	 */
	@Override
	public void doGet(HttpServletRequest iReq, HttpServletResponse iRep)
	{
		String sToken = iReq.getParameter("token"); // get token
		if (sToken == null || sToken.toLowerCase().compareTo(m_sToken) != 0) // if it isn't the configured token, do nothing
			return;
		iRep.setContentType("text/plain");
		synchronized (this)
		{
			try
			{
				Files.copy(Paths.get(m_sFilename), iRep.getOutputStream()); // copy the monitor file to the response.
			}
			catch (IOException oEx)
			{
			}
		}
	}
	
	
	/**
	 * Gets a byte array of the given size filled with random bytes.
	 * @param nSize Size of the array to created
	 * @return a byte array of the given size filled with random bytes.
	 */
	private byte[] getBytes(int nSize)
	{
		byte[] yBytes = new byte[nSize];
		new SecureRandom().nextBytes(yBytes);
		return yBytes;
	}
}
