/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.web;

import imrcp.system.dbf.DbfResultSet;
import imrcp.system.shp.ShpReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.zip.ZipFile;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import org.json.JSONObject;

/**
 * This servlet manages requests for US Census Places (cities, counties, states).
 * @author aaron.cherney
 */
public class USCenPlaceLookup extends SecureBaseBlock
{
	/**
	 * Stores all of the Census Groups. Groups contain Places and act as a way
	 * of indexing Places for quick lookup
	 */
	public ArrayList<CenGroup> m_oGroups = new ArrayList();

	
	/**
	 * Stores all of the Census Places, sorted by id
	 */
	public ArrayList<CenPlace> m_oPlacesById = new ArrayList();

	
	/**
	 * Compares CenPlaces by label
	 */
	public static Comparator<CenPlace> PLACEBYLABEL = (CenPlace o1, CenPlace o2) -> o1.m_sLabel.compareTo(o2.m_sLabel);

	
	/**
	 * Compares CenPlaces by geo id
	 */
	public static Comparator<CenPlace> PLACEBYGEOID = (CenPlace o1, CenPlace o2) -> o1.m_nGeoId - o2.m_nGeoId;

	
	/**
	 * Convenience search object used for finding groups
	 */
	public char[] m_cSearch = new char[3];

	
	/**
	 * Array containing abbreviations for Census states and territories. The indices
	 * represent the state/territory FIPS code. Some positions will be null since
	 * all of the numbers are not used an FIPS codes
	 */
	public String[] m_sAbbrevs;

	
	/**
	 * Path to the directory containing the US Census shapefiles
	 */
	private String m_sCensusDir;

	
	/**
	 * Path to the file containing US Census State information
	 */
	private String m_sStateFile;
	
	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_sCensusDir = oBlockConfig.optString("censusdir", "");
		m_sStateFile = oBlockConfig.optString("statefile", "");
	}
	
	
	/**
	 * Reads all of the US Census shapefiles to have the Place definitions
	 * in memory.
	 * 
	 * @return true if no Exceptions are thrown
	 * @throws Exception
	 */
	@Override 
	public boolean start()
		throws Exception
	{
		File oStateFile = new File(m_sStateFile);
		try (ZipFile oZf = new ZipFile(oStateFile)) // read the state file to get the abbreivations
		{
			DbfResultSet oDbf = new DbfResultSet(new DataInputStream(oZf.getInputStream(oZf.getEntry(oStateFile.getName().replace(".zip", ".dbf")))));
			ArrayList<Integer> oStates = new ArrayList();
			ArrayList<String> oAbbrev = new ArrayList();
			int nMax = Integer.MIN_VALUE;
			while (oDbf.next())
			{
				int nState = oDbf.getInt("STATEFP");
				if (nState > nMax)
					nMax = nState;
				oStates.add(nState);
				oAbbrev.add(oDbf.getString("STUSPS"));
			}

			m_sAbbrevs = new String[nMax + 1]; // allocate enough space for the array
			for (int nIndex = 0; nIndex < oStates.size(); nIndex++)
				m_sAbbrevs[oStates.get(nIndex)] = oAbbrev.get(nIndex);
		}

		for (File oFile : new File(m_sCensusDir).listFiles(oFile -> oFile.getName().endsWith(".zip"))) // read all of the shapefiles to get Place definitions and index them by group
		{
			try (ZipFile oZf = new ZipFile(oFile))
			{
				if (oFile.getName().contains("state")) // skip the state file
					continue;
				DbfResultSet oDbf = new DbfResultSet(new DataInputStream(oZf.getInputStream(oZf.getEntry(oFile.getName().replace(".zip", ".dbf")))));
				boolean bCounty = oFile.getName().contains("county");
				while (oDbf.next())
				{
					CenPlace oPlace = new CenPlace(oDbf.getString("NAME") + (bCounty ? " County" : ""), oDbf.getInt("GEOID"), oDbf.getInt("STATEFP"), m_sAbbrevs);
					addPlace(oPlace);
				}
			}
		}
		return true;
	}
	

	/**
	 * Adds the given CenPlace to the correct {@link CenGroup} in {@link #m_oGroups}.
	 * If the group that the CenPlace belongs in doesn't exist, it is created.
	 * 
	 * @param oPlace the CenPlace to add to the indexed list of groups
	 */
	private void addPlace(CenPlace oPlace)
	{
		int nIndex = m_cSearch.length;
		String sVal = oPlace.m_sName;
		while (nIndex-- > 0) // create search key
		{
			if (nIndex < sVal.length())
				m_cSearch[nIndex] = Character.toUpperCase(sVal.charAt(nIndex));
			else
				m_cSearch[nIndex] = 0;
		}
		
		nIndex = Collections.binarySearch(m_oGroups, m_cSearch);
		if (nIndex < 0) // if the group isn't found
		{
			nIndex = ~nIndex;
			m_oGroups.add(nIndex, new CenGroup(m_cSearch)); // create and add to list
		}
		
		CenGroup oGroup = m_oGroups.get(nIndex);
		nIndex = Collections.binarySearch(oGroup, oPlace, PLACEBYLABEL); 
		if (nIndex < 0) // if the place is not in the group
			oGroup.add(~nIndex, oPlace); // add it
		
		nIndex = Collections.binarySearch(m_oPlacesById, oPlace, PLACEBYGEOID);
		if (nIndex < 0) // if the place is not in the overall list
			m_oPlacesById.add(~nIndex, oPlace); // add it
	}
	
	
	/**
	 * Adds all of the CenPlaces that start with the same characters as the 
	 * "lookup" request parameter to the response.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @param oSession object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws IOException
	 * @throws ServletException
	 */
	public int doLookup(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oClient)
	   throws IOException, ServletException
	{
		String sReq = oReq.getParameter("lookup");
		ArrayList<String> sResults = new ArrayList();
		char[] cSearch = new char[3];
		for (int nIndex = 0; nIndex < cSearch.length; nIndex++) // create search key
		{
			if (nIndex < sReq.length())
				cSearch[nIndex] = Character.toUpperCase(sReq.charAt(nIndex));
			else
				cSearch[nIndex] = 0;
		}
		
		int nIndex = Collections.binarySearch(m_oGroups, cSearch); // look up the group the character belong to
		if (nIndex >= 0)
		{
			CenGroup oGroup = m_oGroups.get(nIndex);

			int nReqLen = sReq.length();
			nIndex = 0;
			boolean bFound = true;
			int nCmp = 0;
			while(nIndex < oGroup.size() && bFound)
			{
				CenPlace oPlace = oGroup.get(nIndex++);
				String sVal = oPlace.m_sLabel;
				if (sVal.length() < nReqLen) // ignore place with a label shorter than the request string
					continue;
				for (int i = 3; i < sReq.length(); i++) // start at the 4th position since the string belong to the same group meaning their first 3 characters are the same
				{
					if ((nCmp = Character.toUpperCase(sVal.charAt(i)) - Character.toUpperCase(sReq.charAt(i))) != 0) // compare each character
					{
						if (nCmp > 0 && sVal.charAt(i) != ',')
							bFound = false;
						break;
					}
				}
				if (nCmp == 0) // if the characters are all the same
					sResults.add(oPlace.m_sLabel); // add to the respone buffer
			}
		}
		
		
		oRes.setContentType("application/json");
		StringBuilder sBuf = new StringBuilder();
		sBuf.append('[');
		if (!sResults.isEmpty())
		{
			for (String sRes : sResults) // add the label as a JSON string to the JSON array
				sBuf.append('"').append(sRes).append('"').append(',');

			sBuf.setLength(sBuf.length() - 1); // remove trailing comma
		}
		sBuf.append(']');
		
		try (PrintWriter oOut = oRes.getWriter())
		{
			oOut.append(sBuf);
		}
		
		return HttpServletResponse.SC_OK;
	}
	
	
	/**
	 * Adds the geometry of the CenPlace described by the "place" request parameter
	 * to the response as a GeoJSON Polygon coordinate array.
	 * 
	 * @param oReq object that contains the request the client has made of the servlet
	 * @param oRes object that contains the response the servlet sends to the client
	 * @param oSession object that contains information about the user that made the
	 * request
	 * @return HTTP status code to be included in the response.
	 * @throws IOException
	 * @throws ServletException
	 */
	public int doGeo(HttpServletRequest oReq, HttpServletResponse oRes, Session oSession, ClientConfig oClient)
	   throws IOException, ServletException
	{
		String sPlace = oReq.getParameter("place");
		char[] cSearch = new char[3];
		for (int nIndex = 0; nIndex < cSearch.length; nIndex++) // create search key
		{
			if (nIndex < sPlace.length())
				cSearch[nIndex] = Character.toUpperCase(sPlace.charAt(nIndex));
			else
				cSearch[nIndex] = 0;
		}
		
		CenPlace oPlace = null;
		int nIndex = Collections.binarySearch(m_oGroups, cSearch);
		if (nIndex >= 0) // find the group the place belongs to
		{
			CenGroup oGroup = m_oGroups.get(nIndex);
			CenPlace oSearch = new CenPlace(sPlace);
			nIndex = Collections.binarySearch(oGroup, oSearch, PLACEBYLABEL);
			if (nIndex >= 0) // find the place
				oPlace = oGroup.get(nIndex);
		}
		if (oPlace == null) // couldn't find the place
		{
			return HttpServletResponse.SC_NOT_FOUND;
		}
		
		String sContains; // determine the file that contains the place
		if (oPlace.m_sLabel.contains(",")) // county or place
		{
			if (oPlace.m_sLabel.contains("County"))
				sContains = "us_county";
			else
				sContains = String.format("%d_place", oPlace.m_nStateFp);
		}
		else
			sContains = "us_state";
		
		StringBuilder sGeoJson = new StringBuilder("["); // array containing the rings of the polygon
		try
		{
			for (File oFile : new File(m_sCensusDir).listFiles(oFile -> oFile.getName().endsWith(".zip")))
			{
				if (!oFile.getName().contains(sContains))
					continue;
				try (ZipFile oZf = new ZipFile(oFile);
					DbfResultSet oDbf = new DbfResultSet(new DataInputStream(oZf.getInputStream(oZf.getEntry(oFile.getName().replace(".zip", ".dbf")))));
					ShpReader oShp = new ShpReader(new BufferedInputStream(oZf.getInputStream(oZf.getEntry(oFile.getName().replace(".zip", ".shp"))))))
				{
					ArrayList<int[]> oRings = new ArrayList();
					while (oDbf.next())
					{
						oRings.clear();
						oShp.readPolygon(oRings);
						if (oDbf.getInt("GEOID") != oPlace.m_nGeoId)
							continue;

//						while (oIter.nextPart()) // each part is a ring of the polygon
//						{
//							sGeoJson.append("["); // array containing the coordinates making up this ring
//							oIter.nextPoint();
//							int nPrevX = oIter.getX();
//							int nPrevY = oIter.getY();
//							sGeoJson.append("[").append(nPrevX).append(",").append(nPrevY).append("]"); // array containing a single point's coordinates
//							while (oIter.nextPoint())
//							{
//								int nX = oIter.getX();
//								int nY = oIter.getY();
//								sGeoJson.append(",[").append(nX - nPrevX).append(",").append(nY - nPrevY).append("]");
//								nPrevX = nX;
//								nPrevY = nY;
//							}
//							sGeoJson.append("],");
//						}
//						sGeoJson.setLength(sGeoJson.length() - 1); // remove trailing comma
//						sGeoJson.append("]");
					}
				}
			}
		}
		catch (Exception oEx)
		{
			throw new ServletException(oEx);
		}
		
		oRes.setContentType("application/json");
		try (PrintWriter oOut = oRes.getWriter())
		{
			oOut.append(sGeoJson);
		}
		
		return HttpServletResponse.SC_OK;
	}
}
