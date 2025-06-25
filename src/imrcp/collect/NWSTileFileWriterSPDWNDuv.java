/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.collect;

import imrcp.system.ResourceRecord;
import java.io.IOException;
import java.util.List;
import ucar.ma2.Array;
import ucar.ma2.Index;
import ucar.nc2.Dimension;
import ucar.nc2.dataset.VariableDS;
import ucar.nc2.dt.GridDatatype;

/**
 *
 * @author aaron.cherney
 */
public class NWSTileFileWriterSPDWNDuv extends NWSTileFileWriterJni
{	
	public NWSTileFileWriterSPDWNDuv()
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
		for (int nIndex = 0; nIndex < oGrids.size(); nIndex++)
		{
			GridDatatype oGrid = oGrids.get(nIndex);
			oVars[nIndex] = oGrid.getVariable();
			oArrays[nIndex] = oVars[nIndex].read();
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
		int nTime = oIndex.getShape(nTimeIndex);
		int nHrz = oIndex.getShape(nHrzIndex);
		int nVrt = oIndex.getShape(nVrtIndex);
		for (int nT = 0; nT < nTime; nT++)
		{
			oIndex.setDim(nTimeIndex, nT);
			for(int nY = 0; nY < nVrt; nY++)
			{
				oIndex.setDim(nVrtIndex, nY);
				for (int nX = 0; nX < nHrz; nX++)
				{
					oIndex.setDim(nHrzIndex, nX);
					double dU = oArrays[0].getDouble(oIndex);
					double dV = oArrays[1].getDouble(oIndex);
					oReturn.setDouble(oIndex, Math.sqrt(dU * dU + dV * dV)); // get the magnitude of the u and v component vectors				
				}
			}
		}
			
		m_oData = oReturn;
	}
}
