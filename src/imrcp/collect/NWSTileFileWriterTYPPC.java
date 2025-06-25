/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.collect;

import imrcp.system.Directory;
import imrcp.system.JSONUtil;
import imrcp.system.ObsType;
import imrcp.system.ResourceRecord;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import org.json.JSONObject;
import ucar.ma2.Array;
import ucar.ma2.Index;
import ucar.nc2.Dimension;
import ucar.nc2.dataset.VariableDS;
import ucar.nc2.dt.GridDatatype;

/**
 *
 * @author aaron.cherney
 */
public class NWSTileFileWriterTYPPC extends NWSTileFileWriterJni
{
	private static final HashMap<String, String> TYPEMAP = new HashMap();
	static
	{
		JSONObject oConfig = Directory.getInstance().getConfig("TileFileWriterTYPPC");
		String[] sVals = JSONUtil.getStringArray(oConfig, "valuemap");
		for (int nIndex = 0; nIndex < sVals.length;)
		{
			TYPEMAP.put(sVals[nIndex++], sVals[nIndex++]);
		}
	}
	
	
	public NWSTileFileWriterTYPPC()
	{
		
	}
	
	@Override
	public void merge(List<GridDatatype> oGrids, ResourceRecord oRR, Array oMerged)
		throws IOException
	{
		if (oMerged != null)
		{
			m_oData = oMerged;
			return;
		}
		VariableDS[] oVars = new VariableDS[oGrids.size()];
		Array[] oArrays = new Array[oGrids.size()];
		double[] dVals = new double[oGrids.size()];
		for (int nIndex = 0; nIndex < oGrids.size(); nIndex++)
		{
			GridDatatype oGrid = oGrids.get(nIndex);
			oVars[nIndex] = oGrid.getVariable();
			oArrays[nIndex] = oVars[nIndex].read();
			dVals[nIndex] = ObsType.lookup(ObsType.TYPPC, TYPEMAP.get(oGrid.getName()));
		}
		Index oIndex = oArrays[0].getIndex();
		String sTimeName = oRR.getTime();
		for (Dimension oDim : oVars[0].getDimensions())
		{
			if (oDim.getShortName().startsWith(sTimeName))
				sTimeName = oDim.getShortName();
		}
		int nHrzIndex = oVars[0].findDimensionIndex(oRR.getHrz());
		int nVrtIndex = oVars[0].findDimensionIndex(oRR.getVrt());
		int nTimeIndex = oVars[0].findDimensionIndex(sTimeName);
		Array oReturn = oArrays[0].copy();
		int nHrz = oIndex.getShape(nHrzIndex);
		int nVrt = oIndex.getShape(nVrtIndex);
		int nTime = oIndex.getShape(nTimeIndex);
		for (int nT = 0; nT < nTime; nT++)
		{
			oIndex.setDim(nTimeIndex, nT);
			for(int nY = 0; nY < nVrt; nY++)
			{
				oIndex.setDim(nVrtIndex, nY);
				for (int nX = 0; nX < nHrz; nX++)
				{
					oIndex.setDim(nHrzIndex, nX);
					int nArray = oArrays.length;
					double dVal = Double.NaN;
					while (nArray-- > 0)
					{
						double dTemp = oArrays[nArray].getDouble(oIndex);
						if (dTemp == 1)
						{
							dVal = dVals[nArray];
							break;
						}
					}
					oReturn.setDouble(oIndex, dVal);
				}
			}
		}
			
		
		m_oData = oReturn;
	}
}
