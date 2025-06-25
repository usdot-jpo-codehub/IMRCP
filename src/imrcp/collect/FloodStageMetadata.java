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
package imrcp.collect;

import imrcp.system.dbf.DbfResultSet;
import imrcp.system.ObsType;
import java.sql.SQLException;

/**
 * This class stores metadata for AHPS (Advanced Hydrologic Prediction Services)
 * flood stations
 * 
 * @author aaron.cherney
 */
public class FloodStageMetadata
{
	/**
	 * Stage level for action
	 */
	public double m_dAction;

	
	/**
	 * Stage level for flood
	 */
	public double m_dFlood;

	
	/**
	 * Stage level for moderate flood
	 */
	public double m_dModerate;

	
	/**
	 * Stage level for major flood
	 */
	public double m_dMajor;
	
	
	/**
	 * Default Constructor. Does nothing
	 */
	FloodStageMetadata()
	{
	
	}

	
	/**
	 * Parses a record of an AHPS .dbf file to obtain the different flood stage
	 * values
	 * @param oDbf DbfResultSet object that has already read the desired line of
	 * the .dbf file
	 * @throws SQLException 
	 */
	public FloodStageMetadata(DbfResultSet oDbf)
		throws SQLException
	{
		if (oDbf.getString("Action").isEmpty())
			m_dAction = Double.MAX_VALUE;
		else
		{
			m_dAction = oDbf.getDouble("Action");
			if (Double.isNaN(m_dAction))
				m_dAction = Double.MAX_VALUE;
		}
		
		if (oDbf.getString("Flood").isEmpty())
			m_dFlood = Double.MAX_VALUE;
		else
		{
			m_dFlood = oDbf.getDouble("Flood");
			if (Double.isNaN(m_dFlood))
				m_dFlood = Double.MAX_VALUE;
		}
		
		if (oDbf.getString("Moderate").isEmpty())
			m_dModerate = Double.MAX_VALUE;
		else
		{
			m_dModerate = oDbf.getDouble("Moderate");
			if (Double.isNaN(m_dModerate))
				m_dModerate = Double.MAX_VALUE;
		}
		
		if (oDbf.getString("Major").isEmpty())
			m_dMajor = Double.MAX_VALUE;
		else
		{
			m_dMajor = oDbf.getDouble("Major");
			if (Double.isNaN(m_dMajor))
				m_dMajor = Double.MAX_VALUE;
		}
	}
	
	
	/**
	 * Compares a value to the different flood stages and returns the corresponding
	 * flood stage enumeration
	 * @param dStageLevel Stage level to compare
	 * @return Flood stage enumeration from {@link ObsType#lookup(int, java.lang.String)}
	 */
	public double getStageValue(double dStageLevel)
	{
		if (dStageLevel >= m_dFlood || dStageLevel >= m_dModerate || dStageLevel >= m_dMajor) // it is possible some of the flood stages to not be defined in the ahps file so check flood first then the other more severe levels
			return ObsType.lookup(ObsType.STG, "flood");
		if (dStageLevel >= m_dAction)
			return ObsType.lookup(ObsType.STG, "action");
		if (m_dAction == Double.MAX_VALUE && m_dFlood == Double.MAX_VALUE && m_dModerate == Double.MAX_VALUE && m_dMajor == Double.MAX_VALUE)
			return ObsType.lookup(ObsType.STG, "not-defined");
		
		return ObsType.lookup(ObsType.STG, "no-action");
	}
}
