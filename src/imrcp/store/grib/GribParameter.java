/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.store.grib;

/**
 * GRIB2 observation types are defined by 3 values, their discipline, category,
 * and number. Disciplines are defined in GRIB2 - TABLE 0.0, Categories are 
 * defined in GRIB2 - CODE TABLE 4.1, and numbers are defined in GRIB2 - TABLE 4.2-d-c
 * where d is the discipline and c is the category.
 * See https://www.nco.ncep.noaa.gov/pmb/docs/grib2/grib2_doc/
 * @author aaron.cherney
 */
public class GribParameter implements Comparable<GribParameter>
{
	/**
	 * GRIB2 Parameter Discipline
	 */
	public final int m_nDiscipline;

	
	/**
	 * GRIB2 Parameter Category
	 */
	public final int m_nCategory;

	
	/**
	 * GRIB2 Parameter Number
	 */
	public final int m_nNumber;

	
	/**
	 * IMRCP observation type id this GribParameter represents
	 */
	public final int m_nImrcpObsType;
	
	
	/**
	 * Constructs a new GribParameter with the given parameters.
	 * @param nImrcpObsType IMRCP observation type id this GribParameter represents
	 * @param nDiscipline GRIB2 discipline
	 * @param nCategory GRIB2 category
	 * @param nNumber GRIB2 number
	 */
	public GribParameter(int nImrcpObsType, int nDiscipline, int nCategory, int nNumber)
	{
		m_nImrcpObsType = nImrcpObsType;
		m_nDiscipline = nDiscipline;
		m_nCategory = nCategory;
		m_nNumber = nNumber;
	}
	
	
	/**
	 * Compares GribParameters by discipline, then category, then number
	 */
	@Override
	public int compareTo(GribParameter o)
	{
		int nReturn = m_nDiscipline - o.m_nDiscipline;
		if (nReturn == 0)
		{
			nReturn = m_nCategory - o.m_nCategory;
			if (nReturn == 0)
				nReturn = m_nNumber - o.m_nNumber;
		}
		
		return nReturn;
	}
}
