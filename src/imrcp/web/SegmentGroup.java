/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.web;

import imrcp.system.Id;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * This class represents a group of segments and the actions defined for them
 * in a Scenario.
 * @author aaron.cherney
 */
public class SegmentGroup
{
	/**
	 * Label of the segment group
	 */
	public String m_sLabel;

	
	/**
	 * Id of the segments included in this segment group
	 */
	public Id[] m_oSegments;

	
	/**
	 * Array indicating if the segments were plowed for each hour of the Scenario
	 */
	public boolean[] m_bPlowing;

	
	/**
	 * Array indicating if the segments were treated for each hour of the Scenario
	 */
	public boolean[] m_bTreating;

	
	/**
	 * Array indicating what the Variable Speed Limit is for each hour of the Scenario
	 */
	public int[] m_nVsl;

	
	/**
	 * Array indicating how many lanes are available for each hour of the Scenario
	 */
	public int[] m_nLanes;
	
	
	/**
	 * Constructs a SegmentGroup from the JSON representation of it.
	 * @param oGroup JSON object defining the SegmentGroup
	 */
	public SegmentGroup(JSONObject oGroup)
	{
		m_sLabel = oGroup.getString("label");
		JSONArray oSegments = oGroup.getJSONArray("segments");
		m_oSegments = new Id[oSegments.length()];
		for (int nIndex = 0; nIndex < oSegments.length(); nIndex++)
			m_oSegments[nIndex] = new Id(oSegments.getString(nIndex));
		
		Arrays.sort(m_oSegments, Id.COMPARATOR);
		
		JSONArray oPlowed = oGroup.getJSONArray("plowing");
		m_bPlowing = new boolean[oPlowed.length()];
		for (int nIndex = 0; nIndex < oPlowed.length(); nIndex++)
			m_bPlowing[nIndex] = oPlowed.getBoolean(nIndex);
		
		JSONArray oTreated = oGroup.getJSONArray("treating");
		m_bTreating = new boolean[oTreated.length()];
		for (int nIndex = 0; nIndex < oTreated.length(); nIndex++)
			m_bTreating[nIndex] = oTreated.getBoolean(nIndex);
		
		JSONArray oVsl = oGroup.getJSONArray("vsl");
		m_nVsl = new int[oVsl.length()];
		for (int nIndex = 0; nIndex < oVsl.length(); nIndex++)
			m_nVsl[nIndex] = oVsl.getInt(nIndex);
		
		JSONArray oLanesAdded = oGroup.getJSONArray("lanes");
		m_nLanes = new int[oLanesAdded.length()];
		for (int nIndex = 0; nIndex < oLanesAdded.length(); nIndex++)
			m_nLanes[nIndex] = oLanesAdded.getInt(nIndex);
	}
}
