/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.system;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/**
 * Keeps track of and maps external system identifiers to their corresponding
 * IMRCP Ids.
 * @author aaron.cherney
 */
public class ExtMapping extends BaseBlock
{
	/**
	 * The Integer key represents a 6 character id for an external system converted
	 * to an integer using base 36. The HashMap values maps external system ids 
	 * as strings to one or more IMRCP Id
	 */
	private HashMap<Integer, HashMap<String, Id[]>> m_oMappings = new HashMap();

	
	/**
	 * Maintains a list of all the IMRCP ids that have a mapping to an external
	 * system id
	 */
	private ArrayList<Id> m_oMappedIds = new ArrayList();
	
	
	/**
	 *
	 */
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		String[] sContribs = JSONUtil.getStringArray(oBlockConfig, "contribs");
		for (String sContrib : sContribs) // for each configured contributor, create an entry in the map
			m_oMappings.put(Integer.valueOf(sContrib, 36), new HashMap());
	}
	
	
	/**
	 *
	 * @return
	 * @throws Exception
	 */
	@Override
	public boolean start()
		throws Exception
	{
		JSONObject oBlockConfig = Directory.getInstance().getConfig(getClass().getName(), m_sInstanceName);
		for (Map.Entry<Integer, HashMap<String, Id[]>> oEntry : m_oMappings.entrySet()) // for each contributor
		{
			String sContrib = Integer.toString(oEntry.getKey(), 36).toLowerCase(); // get string representation of contrib id
			HashMap<String, Id[]> oMapping = oEntry.getValue(); // get the map to add to
			if (sContrib.compareTo("odot") == 0) // read the odot file
			{
				String sFile = oBlockConfig.optString(sContrib, "");
				try (CsvReader oIn = new CsvReader(Files.newInputStream(Paths.get(sFile)))) // lines in this file are odotid,imrcpid0,imrcpid1,...,imrcpidn
				{
					int nCol;
					while ((nCol = oIn.readLine()) > 0)
					{
						if (nCol == 1) // skip ids that do no correspond to an imrcp id
							continue;
						
						ArrayList<Id> oIdsList = new ArrayList(nCol);
						for (int nCount = 1; nCount < nCol; nCount++)
						{
							Id oTemp = new Id(oIn.parseString(nCount));
							
							int nSearch = Collections.binarySearch(oIdsList, oTemp, Id.COMPARATOR);
							if (nSearch < 0) // add ids only once
								oIdsList.add(~nSearch, oTemp);
							int nIndex = Collections.binarySearch(m_oMappedIds, oTemp, Id.COMPARATOR); 
							if (nIndex < 0) // add ids to overall list
								m_oMappedIds.add(~nIndex, oTemp);
						}
						
						Id[] oIds = new Id[oIdsList.size()];
						for (int nIndex = 0; nIndex < oIds.length; nIndex++)
							oIds[nIndex] = oIdsList.get(nIndex);
						oMapping.put(oIn.parseString(0), oIds);
					}
				}
			}
			
			if (sContrib.compareTo("ladotd") == 0) // read the ladotd file
			{
				String sFile = oBlockConfig.optString(sContrib, "");
				try (CsvReader oIn = new CsvReader(Files.newInputStream(Paths.get(sFile)))) // lines in this file are imrcpid,ladotdid
				{
					int nCol;
					oIn.readLine();
					while ((nCol = oIn.readLine()) > 0)
					{
						Id oTemp = new Id(oIn.parseString(0));
						Id[] oIds = new Id[]{oTemp};
						int nIndex = Collections.binarySearch(m_oMappedIds, oTemp, Id.COMPARATOR); // add ids to overall list
						if (nIndex < 0)
							m_oMappedIds.add(~nIndex, oTemp);
						
						oMapping.put(oIn.parseString(1), oIds);
					}
				}
			}
			
			if (sContrib.compareTo("ahps") == 0)
			{
				
			}
		}
		return true;
	}
	
	
	/**
	 * Retrieves the IMRCP Ids that correspond to the given contributor id and
	 * external system id
	 * @param nContribId Contributor id
	 * @param sExtId external system id
	 * @return Id[] of IMRCP Ids that map to the external system id of the
	 * contributor, null if no mapping exists
	 */
	public Id[] getMapping(int nContribId, String sExtId)
	{
		HashMap<String, Id[]> oContrib = m_oMappings.get(nContribId);
		if (oContrib != null)
		{
			return oContrib.get(sExtId);
		}
		return null;
	}
	
	
	/**
	 * Tells whether or not the given Id has a mapping to an external system
	 * identifier
	 * @param oId IMRCP Id
	 * @return true if a mapping exists for the Id, otherwise false
	 */
	public boolean hasMapping(Id oId)
	{
		return Collections.binarySearch(m_oMappedIds, oId, Id.COMPARATOR) >= 0;
	}
}
