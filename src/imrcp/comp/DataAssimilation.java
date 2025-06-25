/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.comp;

import imrcp.geosrv.GeoUtil;
import imrcp.store.Obs;
import imrcp.store.ObsList;
import imrcp.geosrv.ProjProfile;
import imrcp.geosrv.ProjProfiles;
import imrcp.store.TileObsView;
import imrcp.system.Directory;
import imrcp.system.Introsort;
import imrcp.system.ResourceRecord;
import imrcp.system.Scheduling;
import imrcp.collect.TileFileInfo;
import imrcp.collect.TileFileWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeSet;
import org.json.JSONObject;
import ucar.unidata.geoloc.projection.LatLonProjection;

/**
 * Class that uses Data Assimilation techniques to combine a numerical model, 
 * namely a class of Kriging algorithms, with observations to have a more 
 * complete coverage of conditions.
 * @author aaron.cherney
 */
public class DataAssimilation extends TileFileWriter
{
	/**
	 * Number of columns for the observations grid
	 */
	protected int m_nHrz;

	
	/**
	 * Number of rows for the observations grid
	 */
	protected int m_nVrt;

	
	/**
	 * Starting x value of the observations grid
	 */
	protected double m_dStartHrz;

	
	/**
	 * Starting y value of the observation grid
	 */
	protected double m_dStartVrt;

	
	/**
	 * Step size used for consecutive columns
	 */
	protected double m_dHrzStep;

	
	/**
	 * Step size used for consecutive rows
	 */
	protected double m_dVrtStep;

	
	/**
	 * End x value of the observations grid
	 */
	protected double m_dEndHrz;

	
	/**
	 * End y value of the observations grid
	 */
	protected double m_dEndVrt;

	
	/**
	 * Offset value used when storing values in memory
	 */
	protected double m_dBias;

	
	/**
	 * Minimum value allowed for observations
	 */
	protected double m_dMinVal;

	
	/**
	 * Maximum value allowed for observations
	 */
	protected double m_dMaxVal;

	
	/**
	 * Number of cells from the current cell to include in kriging calculations 
	 */
	protected int m_nKernelSize;

	
	/**
	 * Number of cells to use in each row of the kernel
	 */
	protected int[] m_nRowRanges;

	
	/**
	 * Stores the distance from the center of the kernel to each cell in the kernel
	 */
	protected double[][] m_dKernDists;
	
	
	protected int m_nContribId;
	protected int m_nSourceId;
	protected int m_nObsTypeId;
	protected boolean m_bRun;
	

	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_nHrz = oBlockConfig.optInt("hrz", 0);
		m_nVrt = oBlockConfig.optInt("vrt", 0);
		m_dStartHrz = oBlockConfig.optDouble("startx", 0);
		m_dStartVrt = oBlockConfig.optDouble("starty", 0);
		m_dHrzStep = oBlockConfig.optDouble("stepx", 0);
		m_dVrtStep = oBlockConfig.optDouble("stepy", 0);
		m_dEndHrz = m_dStartHrz + (m_dHrzStep * (m_nHrz - 1));
		m_dEndVrt = m_dStartVrt + (m_dVrtStep * (m_nVrt - 1));
		m_dMinVal = oBlockConfig.optDouble("minval", -273.15);
		m_dMaxVal = oBlockConfig.optDouble("maxval", 60);
		m_dBias = oBlockConfig.optDouble("bias", 0);
		m_nKernelSize = oBlockConfig.optInt("kernel", 13);
		m_nContribId = Integer.valueOf(oBlockConfig.optString("contribid", "imrcp"), 36);
		m_nSourceId = Integer.valueOf(oBlockConfig.optString("sourceid", "da"), 36);
		m_nObsTypeId = Integer.valueOf(oBlockConfig.optString("obstypeid", "0"), 36);
		if (m_nObsTypeId == 0)
			m_nObsTypeId = Integer.MIN_VALUE;
		m_bRun = oBlockConfig.optBoolean("run", true);
	}
	
	
	/**
	 * Calculates the values for {@link m_nRowRanges} and {@link m_dKernDists} to
	 * be used for future runs of the algorithm to save processing time that can
	 * be done once. Then sets a schedule to execute on a fixed interval.
	 * @return true if no exceptions are thrown
	 * @throws Exception
	 */
	@Override
	public boolean start() throws Exception
	{
		m_nRowRanges = new int[m_nKernelSize + 1];
		double dKernel = (double)m_nKernelSize;
		for (int nIndex = 0; nIndex < m_nKernelSize; nIndex++)
		{
			double dAngle = Math.asin(nIndex / dKernel);
			m_nRowRanges[nIndex] = (int)GeoUtil.round(m_nKernelSize * Math.cos(dAngle), 0);
		}
		
		int nKernelDia = m_nKernelSize * 2 + 1;
		m_dKernDists = new double[nKernelDia][];
		for (int nIndex = 0; nIndex < m_dKernDists.length; nIndex++)
		{
			double[] dTemp = new double[nKernelDia];
			Arrays.fill(dTemp, Double.NaN);
			
			int nDy = nIndex - m_nKernelSize;			
			for (int nInner = 0; nInner < nKernelDia; nInner++)
			{
				int nDx = nInner - m_nKernelSize;
				dTemp[nInner] = 1 / Math.sqrt(nDx * nDx + nDy * nDy); 
			}
			m_dKernDists[nIndex] = dTemp;
		}
		
		ProjProfile oProj = ProjProfiles.getInstance().getProfile(m_nHrz, m_nVrt, m_dStartHrz, m_dStartVrt, m_dEndHrz, m_dEndVrt);
		if (oProj == null)
		{
			m_oLogger.error("No valid Projection Profile");
			double[] dX = new double[m_nHrz];
			double[] dY = new double[m_nVrt];
			dX[0] = m_dStartHrz;
			dY[0] = m_dStartVrt;
			for (int nIndex = 1; nIndex < m_nHrz; nIndex++)
				dX[nIndex] = dX[nIndex - 1] + m_dHrzStep;
			
			for (int nIndex = 1; nIndex < m_nVrt; nIndex++)
				dY[nIndex] = dY[nIndex - 1] + m_dVrtStep;
			
			ProjProfiles.getInstance().newProfile(dX, dY, new LatLonProjection(), Integer.valueOf("imrcp", 36));
		}
		
		if (m_bRun)
			m_nSchedId = Scheduling.getInstance().createSched(this, m_nOffset, m_nPeriod);
		return true;
	}
	
	
	/**
	 * Wrapper for {@link DataAssimilation#processFile(long)} using the current 
	 * time floored to the most recent period of execution.
	 */
	@Override
	public void execute()
	{
		long lTime = System.currentTimeMillis();
		long nPeriodMillis = m_nPeriod * 1000;
		lTime = (lTime / nPeriodMillis) * nPeriodMillis;
		ArrayList<ResourceRecord> oRRs = Directory.getResourcesByContribSource(m_nContribId, m_nSourceId);
		int nIndex = oRRs.size();
		while (nIndex-- > 0)
		{
			if (oRRs.get(nIndex).getObsTypeId() != m_nObsTypeId)
				oRRs.remove(nIndex);
		}
		lTime += oRRs.get(0).getDelay();
		processRealTime(oRRs, lTime, lTime, lTime);
	}

	
	/**
	 * Applies a kriging algorithm to estimate values for cells in the grid 
	 * that are near cells that have observed values in them. If a cell contains
	 * {@code Double.NEGATIVE_INFINITY} that means it is a cell that has sufficient
	 * observed values around it to make an estimation for its value. Cells that
	 * contain {@Double.NaN} do not have sufficient observed values around it and
	 * will be skipped.
	 * 
	 * @param dGrid Grid of values where each cell is either the average of the 
	 * observed values at that location, {@code Double.NEGATIVE_INFINITY} , or 
	 * {@code Double.NaN}
	 */
	protected void estimate(double[][] dGrid, int[] nMinMax)
	{
		m_oLogger.info("Starting estimate");
		ArrayList<double[]> oNeighborhood = new ArrayList();
		for (int nRow = nMinMax[1]; nRow < nMinMax[3]; nRow++)
		{
			for (int nCol = nMinMax[0]; nCol < nMinMax[2]; nCol++)
			{
				double dCellVal = dGrid[nRow][nCol];
				if (!Double.isInfinite(dCellVal)) // don't estimate when there are no measured values around (NaN) or this cell is a measured value
					continue;
				
				int nStartY = nRow - m_nKernelSize;
				int nEndY = nRow + m_nKernelSize;
				int nEndYKern = nEndY;
				if (nStartY < 0)
					nStartY = 0;
				if (nEndY > m_nVrt)
					nEndY = m_nVrt;
				
				oNeighborhood.clear();
				double dTotalDist = 0;
				for (int nY = nStartY; nY < nEndY; nY++)
				{
					int nCurRow = nEndYKern - nY;
					int nRowRange;
					
					if (nCurRow < m_nKernelSize)
						nRowRange = m_nRowRanges[m_nKernelSize - nCurRow];
					else if (nCurRow > m_nKernelSize)
						nRowRange = m_nRowRanges[nCurRow - m_nKernelSize];
					else
						nRowRange = m_nKernelSize;
					
					int nStartX = nCol - nRowRange;
					int nEndX = nCol + nRowRange;
					if (nStartX < 0)
						nStartX = 0;
					if (nEndX > m_nHrz)
						nEndX = m_nHrz;
					
					int nKernY = nY - nRow + m_nKernelSize;
					for (int nX = nStartX; nX < nEndX; nX++)
					{
						double dNeighVal = dGrid[nY][nX];
						if (Double.isFinite(dNeighVal) && dNeighVal > m_dMinVal)
						{
							int nKernX = nX - nCol + m_nKernelSize;
							double dDist = m_dKernDists[nKernY][nKernX];
							dTotalDist += dDist;
							oNeighborhood.add(new double[]{dNeighVal, dDist});
						}
					}
				}
				
				if (oNeighborhood.isEmpty())
					continue;
				
				double dEstimate = 0;
				for (double[] dNeighbor : oNeighborhood)
				{
					dEstimate += dNeighbor[0] * (dNeighbor[1] / dTotalDist);
				}

				
				dGrid[nRow][nCol] = dEstimate - m_dBias; // subtract the bias so it is below the min value and will not be used to estimate other values
			}
		}
		m_oLogger.info("Exiting estimate");
	}
	
	
	/**
	 * Creates the grid of values from the given list of observation to run the
	 * kriging algorithm on. Values in the grid can be the average of the 
	 * observations founds in that cell, {@code Double.NEGATIVE_INFINITY} meaning
	 * that cell is a candidate for the kriging algorithm since there are 
	 * observed values within the configured kernel size, or {@code Double.NaN}
	 * meaning the cell does not have any observations in it nor within the
	 * kernel size so it can be skipped for processing in {@link DataAssimilation#estimate(double[][])}
	 * 
	 * @param oData List containing observations
	 * @return grid filled in with values from the given observations
	 */
	protected double[][] createGrid(ObsList oData, ProjProfile oProj, int[] nMinMax)
	{
		double[][] dGrid = new double[m_nVrt][];
		for (int nIndex = 0; nIndex < dGrid.length; nIndex++)
		{
			dGrid[nIndex] = new double[m_nHrz];
			Arrays.fill(dGrid[nIndex], Double.NaN);
		}
		
		int[] nIndices = new int[2];
		
		ArrayList<int[]> nCounts = new ArrayList();
		Comparator<int[]> oComp = (int[] o1, int[] o2) -> 
		{
			int nRet = o1[0] - o2[0];
			if (nRet == 0)
				nRet = o1[1] - o2[1];
			
			return nRet;
		};

		int nMinX = Integer.MAX_VALUE;
		int nMinY = Integer.MAX_VALUE;
		int nMaxX = Integer.MIN_VALUE;
		int nMaxY = Integer.MIN_VALUE;
		for (Obs oObs : oData)
		{
			if (oObs.m_dValue >= m_dMaxVal || oObs.m_dValue <= m_dMinVal) // skip invalid obs
				continue;
			
			oProj.getPointIndices(GeoUtil.fromIntDeg(oObs.m_oGeoArray[1]), GeoUtil.fromIntDeg(oObs.m_oGeoArray[2]), nIndices);
			int nCol = nIndices[0];
			int nRow = nIndices[1];
			if (nCol < 0 || nCol >= m_nHrz || nRow < 0 || nRow >= m_nVrt) // skip observations outside of the grid
				continue;
			
			double dVal = dGrid[nRow][nCol];
			if (Double.isFinite(dVal)) // if the cell in the grid already has a value
			{
				dGrid[nRow][nCol] = dVal + oObs.m_dValue; // accumulate the total to be able to compute the average
				++nCounts.get(Collections.binarySearch(nCounts, new int[]{nRow, nCol}, oComp))[2]; // keep track of the number of observations in this cell
			}
			else
			{
				dGrid[nRow][nCol] = oObs.m_dValue;
				int[] nCount = new int[]{nRow, nCol, 1};
				int nSearch = Collections.binarySearch(nCounts, nCount, oComp);
				if (nSearch < 0)
					nCounts.add(~nSearch, nCount);
			}
			
			int nStartX = nCol - m_nKernelSize;
			int nEndX = nCol + m_nKernelSize;
			if (nStartX < 0)
				nStartX = 0;
			if (nEndX >= m_nHrz)
				nEndX = m_nHrz - 1;
			
			int nStartY = nRow - m_nKernelSize;
			int nEndY = nRow + m_nKernelSize;
			if (nStartY < 0)
				nStartY = 0;
			if (nEndY >= m_nVrt)
				nEndY = m_nVrt - 1;
			
			if (nStartX < nMinX)
				nMinX = nStartX;
			if (nStartY < nMinY)
				nMinY = nStartY;
			if (nEndX > nMaxX)
				nMaxX = nEndX;
			if (nEndY > nMaxY)
				nMaxY = nEndY;
			
			for (int i = nStartY; i <= nEndY; i++)
			{
				for (int j = nStartX; j <= nEndX; j++)
				{
					if (Double.isNaN(dGrid[i][j])) // fill in neg inf for cells within the current kernel that don't have an observation
						dGrid[i][j] = Double.NEGATIVE_INFINITY;
				}
			}
		}
		
		for (int[] nCount : nCounts) // calculate averages
		{
			if (nCount[2] > 1)
				dGrid[nCount[0]][nCount[1]] /= nCount[2];
		}
		
		nMinMax[0] = nMinX;
		nMinMax[1] = nMinY;
		nMinMax[2] = nMaxX;
		nMinMax[3] = nMaxY;
		return dGrid;
	}


	@Override
	protected void createFiles(TileFileInfo oInfo, TreeSet<Path> oArchiveFiles)
	{
		m_oLogger.debug("create files");
		try
		{		
			// flip geo coordinates if needed for query
			int nLat1 = GeoUtil.toIntDeg(m_dStartVrt);
			int nLat2 = GeoUtil.toIntDeg(m_dEndVrt);
			if (nLat1 > nLat2)
			{
				int nTemp = nLat1;
				nLat1 = nLat2;
				nLat2 = nTemp;
			}
			
			int nLon1 = GeoUtil.toIntDeg(m_dStartHrz);
			int nLon2 = GeoUtil.toIntDeg(m_dEndHrz);
			if (nLon1 > nLon2)
			{
				int nTemp = nLon1;
				nLon1 = nLon2;
				nLon2 = nTemp;
			}
			
			int[] nContrib = new int[]{Integer.valueOf("wxde", 36), Integer.MIN_VALUE};
			ProjProfile oProj = ProjProfiles.getInstance().getProfile(m_nHrz, m_nVrt, m_dStartHrz, m_dStartVrt, m_dEndHrz, m_dEndVrt);

			for (ResourceRecord oRR : oInfo.m_oRRs)
			{
				int nFreq = oRR.getTileFileFrequency();
				long lTimestamp = oInfo.m_lStart / nFreq * nFreq + oRR.getDelay();
				long lEnd = oInfo.m_lEnd / nFreq * nFreq + nFreq + oRR.getDelay();

				while (lTimestamp < lEnd)
				{
					try
					{
						long lFileEnd = lTimestamp + oRR.getRange();
						
						ObsList oData = ((TileObsView)Directory.getInstance().lookup("ObsView")).getData(oRR.getObsTypeId(), lTimestamp, lTimestamp + 1, nLat1, nLat2, nLon1, nLon2, oInfo.m_lRef, nContrib); // query the configured store to get the last 20 minutes of observations
						Introsort.usort(oData, Obs.g_oCompObsByTime);
						Comparator<String[]> oWxdeComp = (String[] o1, String[] o2) ->
						{
							int nRet = o1[0].compareTo(o2[0]);
							if (nRet == 0)
							{
								nRet = o1[1].compareTo(o2[1]);
								if (nRet == 0)
								{
									nRet = o1[2].compareTo(o2[2]);
									if (nRet == 0)
									{
										nRet = o1[3].compareTo(o2[3]);
									}
								}
							}
							return nRet;
						};
						TreeSet<String[]> oPlatforms = new TreeSet(oWxdeComp);
						int nDataIndex = oData.size();
						ObsList oObsToProcess = new ObsList(nDataIndex);
						while(nDataIndex-- > 0)
						{
							Obs oObs = oData.get(nDataIndex);
							if (oPlatforms.add(oObs.m_sStrings))
								oObsToProcess.add(oObs);
						}
						int[] nMinMax = new int[4];
						double[][] dGrid = createGrid(oObsToProcess, oProj, nMinMax);
						if (dGrid == null)
							return;

						estimate(dGrid, nMinMax);
						new DATileFileWriter().write(oProj, dGrid, oRR, lTimestamp, lFileEnd, nMinMax, m_dMinVal, m_dBias);
					}
					catch (Exception oEx)
					{
						m_oLogger.error(oEx, oEx);
					}
					
					lTimestamp += nFreq;
				}
			}
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
		m_oLogger.debug("create files done");
	}
}
