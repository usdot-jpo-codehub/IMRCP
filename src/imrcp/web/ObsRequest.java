/*
 * Copyright 2017 Federal Highway Administration.
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

/**
 * This class stores the parameters used for observation requests from the IMRCP
 * Map UI
 * @author aaron.cherney
 */
public class ObsRequest
{
	/**
	 * Spatial bounds of the request
	 */
	private LatLngBounds m_oRequestBounds;

	
	/**
	 * Query start time in milliseconds since Epoch
	 */
	private long m_lRequestTimestampStart;

	
	/**
	 * Query end time in milliseconds since Epoch
	 */
	private long m_lRequestTimestampEnd;

	
	/**
	 * Reference time of the query in milliseconds since Epoch
	 */
	private long m_lRequestTimestampRef;

	
	/**
	 * IMRCP contributor Id to use in the query
	 */
	private int m_nSourceId;
	
	private String m_sRequest;

	
	/**
	 * Getter for {@link #m_oRequestBounds}
	 * 
	 * @return The spatial extents of the request
	 */
	public LatLngBounds getRequestBounds()
	{
		return m_oRequestBounds;
	}

	
	/**
	 * Setter for {@link #m_oRequestBounds}
	 * 
	 * @param requestBounds The spatial extents of the request
	 */
	public void setRequestBounds(LatLngBounds requestBounds)
	{
		this.m_oRequestBounds = requestBounds;
	}

	
	/**
	 * Getter for {@link #m_lRequestTimestampStart}
	 * 
	 * @return Start time of the request in milliseconds since Epoch
	 */
	public long getRequestTimestampStart()
	{
		return m_lRequestTimestampStart;
	}

	
	/**
	 * Setter for {@link #m_lRequestTimestampStart}
	 * 
	 * @param lRequestTimestamp Start time of the request in milliseconds since Epoch
	 */
	public void setRequestTimestampStart(long lRequestTimestamp)
	{
		this.m_lRequestTimestampStart = lRequestTimestamp;
	}

	
	/**
	 * Getter for {@link #m_lRequestTimestampRef}
	 * 
	 * @return Reference time of the query in milliseconds since Epoch
	 */
	public long getRequestTimestampRef()
	{
		return m_lRequestTimestampRef;
	}

	
	/**
	 * Setter for {@link #m_lRequestTimestampRef}
	 * 
	 * @param lRequestTimestamp Reference time of the query in milliseconds since Epoch
	 */
	public void setRequestTimestampRef(long lRequestTimestamp)
	{
		this.m_lRequestTimestampRef = lRequestTimestamp;
	}

	
	/**
	 * Getter for {@link #m_lRequestTimestampEnd}
	 * 
	 * @return End time of the request in milliseconds since Epoch
	 */
	public long getRequestTimestampEnd()
	{
		return m_lRequestTimestampEnd;
	}

	
	/**
	 * Setter for {@link #m_lRequestTimestampEnd}
	 * 
	 * @param lRequestTimestampEnd End time of the request in milliseconds since Epoch
	 */
	public long setRequestTimestampEnd(long lRequestTimestampEnd)
	{
		return m_lRequestTimestampEnd = lRequestTimestampEnd;
	}

	
	/**
	 * Getter for {@link #m_nSourceId}
	 * @return the IMRCP contributor Id of the request
	 */
	public int getSourceId()
	{
		return m_nSourceId;
	}
	
	
	/**
	 * Setter for {@link #m_nSourceId}
	 * @param nSourceId the IMRCP contributor Id of the request
	 */
	public void setSourceId(int nSourceId)
	{
		m_nSourceId = nSourceId;
	}
	
	
	public void setRequest(String sRequest)
	{
		m_sRequest = sRequest;
	}
	
	
	public String getRequest()
	{
		return m_sRequest;
	}
}
