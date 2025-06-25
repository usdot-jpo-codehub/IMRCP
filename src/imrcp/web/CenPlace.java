/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.web;

/**
 * Represents a place (state, city, county) found in the Cartographic Boundary 
 * Files provided by the United States Census Bureau.
 * @author aaron.cherney
 */
public class CenPlace
{
	/**
	 * Name of place
	 */
	public String m_sName;

	
	/**
	 * Geographic identifier used by the census
	 */
	public int m_nGeoId;

	
	/**
	 * Fips code of the state the place is located
	 */
	public int m_nStateFp;

	
	/**
	 * Label used to describe the place
	 */
	public String m_sLabel;

	
	/**
	 * Constructs a CenPlace with the given label
	 * @param sLabel label describing the place
	 */
	public CenPlace(String sLabel)
	{
		m_sLabel = sLabel;
	}

	
	/**
	 * Constructs a CenPlace setting the given parameters
	 * @param sName name of place
	 * @param nGeoId geographic id of place
	 * @param nStateFp fips code of the state this place is in
	 * @param sAbbrevs abbreviation 
	 */
	public CenPlace(String sName, int nGeoId, int nStateFp, String[] sAbbrevs)
	{
		m_sName = sName;
		m_nGeoId = nGeoId;
		m_nStateFp = nStateFp;
		if (nGeoId > sAbbrevs.length) // don't include the state abbreviation for places that are the actual state, whose geoids are the fips code
			m_sLabel = m_sName + ", " + sAbbrevs[m_nStateFp];
		else
			m_sLabel = m_sName;
	}

	/**
	 * @return The concatenation of {@link #m_sName}, {@link #m_nStateFp} and
	 * {@link #m_nGeoId}
	 */
	@Override
	public String toString()
	{
		return m_sName + " " + m_nStateFp + " " + m_nGeoId;
	}
}
