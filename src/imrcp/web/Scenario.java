/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.web;

import com.github.aelstad.keccakj.fips202.Shake256;
import imrcp.system.Id;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map.Entry;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * This class encapsulates all of the parameters needed to represent a Scenario.
 * A Scenario consists of groups of roadway segments with an associated time series
 * of actions for each group.
 * @author aaron.cherney
 */
public class Scenario
{
	/**
	 * The user that created the Scenario
	 */
	public String m_sUser;

	
	/**
	 * The label given to the Scenario
	 */
	public String m_sName;

	
	/**
	 * Scenario Id which is a UUID if this object represents a Scenario template
	 * or a 32 byte array converted to a String using Base64 if this object is
	 * a Scenario that is being ran for a specific time
	 */
	public String m_sId;

	
	/**
	 * Start time of the Scenario in milliseconds since Epoch
	 */
	public long m_lStartTime;

	
	/**
	 * The roadway segment groups
	 */
	public SegmentGroup[] m_oGroups;

	
	/**
	 * Flag indicating if the Scenario is being ran for a specific time or if
	 * it is a Scenario template
	 */
	public boolean m_bRun;

	
	/**
	 * Flag indicating if the Scenario has finished processing or not
	 */
	public boolean m_bProcessed = false;

	
	/**
	 * Network ID of the Network used for this Scenario
	 */
	public String m_sNetwork;
	
	public boolean m_bRunRoadWeather = false;
	public boolean m_bRunTraffic = false;
	public boolean m_bShare = false;
	public boolean m_bIsShared = false;
	
	public TreeMap<Id, int[]> m_oUserDefinedMetadata = new TreeMap(Id.COMPARATOR);
	
	
	/**
	 * Constructs a Scenario from the given Path which should point to a JSON
	 * file defining the Scenario
	 * 
	 * @param oPath Path of the JSON file
	 * @throws IOException
	 */
	public Scenario(Path oPath)
		throws IOException
	{
		try (BufferedReader oIn = Files.newBufferedReader(oPath, StandardCharsets.UTF_8))
		{
			JSONObject oJson = new JSONObject(new JSONTokener(oIn));			
			setValues(oJson, oPath);
		}
	}
	
	
	/**
	 * Constructs a Scenario from the given JSONObject by calling 
	 * {@link #setValues(org.json.JSONObject, java.nio.file.Path)}
	 * 
	 * @param oJson JSONObject defining the Scenario
	 * @throws IOException
	 */
	public Scenario(JSONObject oJson)
		throws IOException
	{
		setValues(oJson, null);
	}
	
	
	/**
	 * Sets the member variables of this Scenario by parsing the given JSON
	 * object.
	 * 
	 * @param oJson JSONObject defining the Scenario
	 * @param oPath Path of the file containing the JSON definition, can be null.
	 * @throws IOException
	 */
	public final void setValues(JSONObject oJson, Path oPath)
		throws IOException
	{
		m_lStartTime = oJson.optLong("starttime", Long.MIN_VALUE); // scenario templates do not have a start time
		if (m_lStartTime != Long.MIN_VALUE)
			m_lStartTime = (m_lStartTime / 3600000) * 3600000; // floor start times to the nearest hour
		m_sUser = oJson.getString("user");
		m_sName = oJson.getString("name");
		m_sNetwork = oJson.optString("network");
		JSONArray oGroups = oJson.getJSONArray("groups");
		m_oGroups = new SegmentGroup[oGroups.length()];
		int nCount = 0;
		for (int nIndex = 0; nIndex < oGroups.length(); nIndex++)
		{
			JSONObject oGroup = oGroups.getJSONObject(nIndex);
			m_oGroups[nCount++] = new SegmentGroup(oGroup);
		}
		
		m_bRun = oJson.getBoolean("run");
		m_bRunRoadWeather = oJson.optBoolean("roadwxmodel", true);
		m_bRunTraffic = oJson.optBoolean("trafficmodel", true);
		m_bShare = oJson.optBoolean("share", false);
		m_bIsShared = oJson.optBoolean("isshared", false);
		m_sId = oJson.optString("uuid"); // scenario templates have a uuid field, scenarios being processed do not and have their ids generated later
		if (m_sId == null || m_sId.isEmpty())
		{
			if (oPath == null)
				generateId();
			else
				m_sId = oPath.getParent().getFileName().toString();
		}
		JSONObject oMetadata = oJson.optJSONObject("metadata");
		if (oMetadata != null)
		{
			for (String sKey : oMetadata.keySet())
			{
				JSONArray oArr = oMetadata.getJSONArray(sKey);
				m_oUserDefinedMetadata.put(new Id(sKey), new int[]{oArr.getInt(0), oArr.getInt(1)});
			}
		}
	}
	
	
	/**
	 * Uses a Shake256 squeeze algorithm to create a unique id based off of the
	 * parameters of the Scenario.
	 * @throws IOException
	 */
	public void generateId()
		throws IOException
	{
		Shake256 oMd = new Shake256();
		try
		(
			DataOutputStream oAbsorb = new DataOutputStream(oMd.getAbsorbStream());
			InputStream oSqueeze = oMd.getSqueezeStream();		   
		)
		{
			oAbsorb.writeLong(m_lStartTime);
			oAbsorb.writeUTF(m_sUser);
			for (SegmentGroup oGroup : m_oGroups)
			{
				for (Id oId : oGroup.m_oSegments)
				{
					oAbsorb.writeLong(oId.getHighBytes());
					oAbsorb.writeLong(oId.getLowBytes());
				}
				
				for (boolean bPlow : oGroup.m_bPlowing)
				{
					oAbsorb.writeBoolean(bPlow);
				}
				
				for (boolean bTreated : oGroup.m_bTreating)
				{
					oAbsorb.writeBoolean(bTreated);
				}
				
				for (int nVsl : oGroup.m_nVsl)
				{
					oAbsorb.writeInt(nVsl);
				}
				
				for (int nLanes : oGroup.m_nLanes)
				{
					oAbsorb.writeInt(nLanes);
				}
			}
			
			byte[] yId = new byte[32];
			oSqueeze.read(yId);
			
			m_sId = Base64.getUrlEncoder().withoutPadding().encodeToString(yId);
		}
	}
	
	
	/**
	 * Serializes the Scenario into a JSONObject.
	 * @param bIncludeProcessed flag indicating if the processed field should be included
	 * @param bIncludeUser flag indicating if the user field should be included
	 * @return JSONObject that represents the Scenario
	 */
	public JSONObject toJSONObject(boolean bIncludeProcessed, boolean bIncludeUser)
	{
		JSONObject oJson = new JSONObject();
		oJson.put("starttime", m_lStartTime);
		oJson.put("name", m_sName);
		oJson.put("network", m_sNetwork);
		if (bIncludeProcessed)
		{
			oJson.put("processed", m_bProcessed);
			oJson.put("id", m_sId);
		}
		if (bIncludeUser)
			oJson.put("user", m_sUser);
		
		oJson.put("roadwxmodel", m_bRunRoadWeather);
		oJson.put("trafficmodel", m_bRunTraffic);
		oJson.put("share", m_bShare);
		oJson.put("run", m_bRun);
		oJson.put("isshared", m_bIsShared);
		JSONArray oGroups = new JSONArray();
		
		for (SegmentGroup oGroup : m_oGroups)
		{
			JSONObject oJsonGroup = new JSONObject();
			JSONArray oIds = new JSONArray();
			for (Id oId : oGroup.m_oSegments)
				oIds.put(oId.toString());
			oJsonGroup.put("segments", oIds);
			oJsonGroup.put("plowing", new JSONArray(oGroup.m_bPlowing));
			oJsonGroup.put("treating", new JSONArray(oGroup.m_bTreating));
			oJsonGroup.put("vsl", new JSONArray(oGroup.m_nVsl));
			oJsonGroup.put("lanes", new JSONArray(oGroup.m_nLanes));
			oJsonGroup.put("label", oGroup.m_sLabel);
			oGroups.put(oJsonGroup);
		}
		
		oJson.put("groups", oGroups);
		JSONObject oMetadata = new JSONObject();
		for (Entry<Id, int[]> oEntry : m_oUserDefinedMetadata.entrySet())
		{
			JSONArray oArr = new JSONArray();
			oArr.put(oEntry.getValue()[0]);
			oArr.put(oEntry.getValue()[1]);
			oMetadata.put(oEntry.getKey().toString(), oArr);
		}
		oJson.put("metadata", oMetadata);
		return oJson;
	}
}
