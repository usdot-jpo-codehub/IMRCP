/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.forecast.mlp;

/**
 *
 * @author aaron.cherney
 */
public class MLPMetadata
{
	public String m_sId;
	public String m_sSpdLimit;
	public int m_nHOV;
	public int m_nDirection;
	public int m_nCurve;
	public int m_nOffRamps;
	public int m_nOnRamps;
	public int m_nPavementCondition;
	public String m_sRoad;
	public int m_nSpecialEvents;
	public int m_nLanes;

	MLPMetadata(String sId, String sSpdLimit, int nHOV, int nDirection, int nCurve, int nOffRamps, int nOnRamps, int nPavementCondition, String sRoad, int nLanes)
	{
		m_sId = sId;
		m_sSpdLimit = sSpdLimit;
		m_nHOV = nHOV;

		m_nDirection = nDirection;
		m_nCurve = nCurve;
		m_nOffRamps = nOffRamps;
		m_nOnRamps = nOnRamps;

		m_nPavementCondition = nPavementCondition;
		m_sRoad = sRoad;
		m_nSpecialEvents = 0;
		m_nLanes = nLanes;
	}
}
