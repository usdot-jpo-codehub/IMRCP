/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.store;

import java.util.Comparator;

/**
 * Maps Advanced Hydrologic Prediction Service (AHPS) identifiers to United States
 * Geological Survey (USGS) identifiers and contains information used to compute
 * the flood stage at the given sensor.
 * @author aaron.cherney
 */
public class FloodMapping
{
	/**
	 * Advanced Hydrologic Prediction Service (AHPS) identifier
	 */
	public String m_sAHPSId;

	
	/**
	 * United States Geological Survey (USGS) identifier
	 */
	public String m_sUSGSId;

	
	/**
	 * Flood stage in ft
	 */
	public double m_dStage;

	
	/**
	 * Observed/forecasted flood level in ft.
	 */
	public double m_dObsValue;
	
	
	/**
	 * Compares {@link FloodMapping}s by AHPS id
	 */
	public static final Comparator<FloodMapping> AHPSCOMP = (FloodMapping o1, FloodMapping o2) -> o1.m_sAHPSId.compareTo(o2.m_sAHPSId);

	
	/**
	 * Compares {@link FloodMapping}s by USGS id
	 */
	public static final Comparator<FloodMapping> USGSCOMP = (FloodMapping o1, FloodMapping o2) -> o1.m_sUSGSId.compareTo(o2.m_sUSGSId);
	
	
	/**
	 * Constructs a FloodMapping with the given ids
	 * @param sAhps AHPS id
	 * @param sUsgs USGS id
	 */
	public FloodMapping(String sAhps, String sUsgs)
	{
		m_sAHPSId = sAhps;
		m_sUSGSId = sUsgs;
	}
	
	
	/**
	 * Constructs a FloodMapping with the given ids and flood stage information
	 * @param sAhps AHPS id
	 * @param sUsgs USGS id
	 * @param dStage flood stage in ft
	 * @param dObsValue Observed/forecasted flood level in ft
	 */
	public FloodMapping(String sAhps, String sUsgs, double dStage, double dObsValue)
	{
		this(sAhps, sUsgs);
		m_dStage = dStage;
		m_dObsValue = dObsValue;
	}
}
