/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.web;

import java.util.ArrayList;

/**
 * Represents a group of places found from the Cartographic Boundary Files
 * provided by the United States Census Bureau. The places are grouped the first
 * n characters of their name (currently n = 3 in 
 * {@link imrcp.web.USCenPlaceLookup})
 * @author aaron.cherney
 */
public class CenGroup extends ArrayList<CenPlace> implements Comparable<char[]>
{
	/**
	 * Characters used to determine if a {@link imrcp.web.CenPlace} is part of 
	 * this group
	 */
	char[] m_cKey;

	
	/**
	 * Constructs a new CenGroup with the given key in a new char[]
	 * @param cKey Characters used to group places
	 */
	public CenGroup(char[] cKey)
	{
		m_cKey = new char[cKey.length];
		System.arraycopy(cKey, 0, m_cKey, 0, cKey.length);
	}


	/**
	 * Compares the first n characters to the characters in {@link #m_cKey} where
	 * n is {@code m_cKey.length}
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object) 
	 */
	@Override
	public int compareTo(char[] o)
	{
		int nIndex = 0;
		int nComp = 0;
		while (nIndex < m_cKey.length)
		{
			nComp = m_cKey[nIndex] - o[nIndex];
			if (nComp != 0)
				break;
			++nIndex;
		}

		return nComp;
	}
}
