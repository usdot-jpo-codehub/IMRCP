/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.forecast.mlp;

import java.text.SimpleDateFormat;

/**
 *
 * @author Federal Highway Administration
 */
public class RealTimeRecord extends SpeedRecord implements Comparable<RealTimeRecord>
{
	public String m_sId;
	public int m_nPrecipitation;
	public int m_nVisibility;
	public int m_nDirection;
	public int m_nTemperature;
	public int m_nWindSpeed;
	public int m_nDayOfWeek;
	public int m_nTimeOfDay;
	public int m_nLanes;
	public String m_sSpeedLimit;
	public int m_nCurve;
	public int m_nHOV;
	public int m_nPavementCondition;
	public int m_nOnRamps;
	public int m_nOffRamps;
	public int m_nIncidentDownstream;
	public int m_nIncidentOnLink;
	public int m_nLanesClosedOnLink;
	public int m_nLanesClosedDownstream;
	public int m_nWorkzoneOnLink;
	public int m_nWorkzoneDownstream;
	public int m_nSpecialEvents;
	public double m_dFlow = Double.NaN;
	public double m_dOccupancy = Double.NaN;
	public long m_lTimestamp;
	public String m_sRoad;
	public int m_nContraflow = -1;
	public int m_nVsl = -1;
	public int m_nHsr = -1;
	public static final String HEADER = "Timestamp,Id,Precipitation,Visibility,Direction,Temperature,WindSpeed,DayOfWeek,TimeOfDay,Lanes,SpeedLimit,Curve,HOV,PavementCondition,OnRamps,OffRamps,IncidentDownstream,IncidentOnLink,LanesClosedOnLink,LanesClosedDownstream,WorkzoneOnLink,WorkzoneDownstream,SpecialEvents,Flow,Speed,Occupancy,road,contraflow,vsl,hsr\n";
	String format(SimpleDateFormat oSdf)
	{
		return String.format("%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.2f,%.2f,%.2f,%s,%d,%d", oSdf.format(m_lTimestamp), m_sId, m_nPrecipitation, m_nVisibility, m_nDirection,
					m_nTemperature, m_nWindSpeed, m_nDayOfWeek, m_nTimeOfDay, m_nLanes, m_sSpeedLimit, m_nCurve, m_nHOV, m_nPavementCondition, m_nOnRamps, m_nOffRamps,
					m_nIncidentDownstream, m_nIncidentOnLink, m_nLanesClosedOnLink, m_nLanesClosedDownstream, m_nWorkzoneOnLink, m_nWorkzoneDownstream, m_nSpecialEvents,
					m_dFlow, m_dSpeed, m_dOccupancy, m_sRoad, m_nContraflow, m_nVsl, m_nHsr);
	}

	@Override
	public int compareTo(RealTimeRecord o)
	{
		int nReturn = m_sId.compareTo(o.m_sId);
		if (nReturn == 0)
			nReturn = Long.compare(m_lTimestamp, o.m_lTimestamp);
		
		return nReturn;
	}
}
