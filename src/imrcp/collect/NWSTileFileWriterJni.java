/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.collect;

import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.RangeRules;
import imrcp.store.Obs;
import imrcp.geosrv.ProjProfile;
import imrcp.geosrv.ProjProfiles;
import imrcp.system.FilenameFormatter;
import imrcp.system.ObsType;
import imrcp.system.ResourceRecord;
import imrcp.system.Units;
import imrcp.system.Units.UnitConv;
import imrcp.system.Util;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.logging.log4j.Logger;
import ucar.ma2.Array;
import ucar.ma2.Index;
import ucar.nc2.Dimension;
import ucar.nc2.dataset.VariableDS;
import ucar.nc2.dt.GridDatatype;
import ucar.unidata.geoloc.ProjectionImpl;

/**
 *
 * @author aaron.cherney
 */
public class NWSTileFileWriterJni extends LibImrcpWriter implements Callable
{
	protected Array m_oData = null;
	protected ProjectionImpl m_oProj;
	protected VariableDS m_oVar;
	protected double[] m_dHrz;
	protected double[] m_dVrt;
	protected double[] m_dTime;
	protected int m_nT;
	protected ResourceRecord m_oRR;
	protected long m_lExpectedEnd;
	protected long m_lRecv;
	protected int[] m_nBB;
	protected Logger m_oLogger;
	protected String m_sFile;
	
	public NWSTileFileWriterJni()
	{
	}
	
	
	public void merge(List<GridDatatype> oGrids, ResourceRecord oRR, Array oMerged)
		throws IOException
	{
		if (oMerged == null)
			m_oData = oGrids.get(0).getVariable().read();
		else
			m_oData = oMerged;
	}
	
	
	public Array getDataArray()
	{
		return m_oData;
	}
	
	public void setValuesForCall(ProjectionImpl oProj, VariableDS oVar, double[] dHrz, double[] dVrt, double[] dTime, int nT, ResourceRecord oRR, long lExpectedEnd, long lRecv, int[] nBB, Logger oLogger, String sFile)
		throws Exception
	{
		m_oProj = oProj;
		m_oVar = oVar;
		m_dHrz = dHrz;
		m_dVrt = dVrt;
		m_dTime = dTime;
		m_nT = nT;
		m_oRR = oRR;
		m_lExpectedEnd = lExpectedEnd;
		m_lRecv = lRecv;
		m_nBB = nBB;
		m_oLogger = oLogger;
		m_sFile = sFile;
		
	}

	@Override
	public Object call() throws Exception
	{
		m_oLogger.info(String.format("Writing tile file for %s for time %d - %s",  Integer.toString(m_oRR.getObsTypeId(), 36), m_nT, m_sFile));
		int nZoom = m_oRR.getZoom();
		int nTileSize = m_oRR.getTileSize();
		long lTileWriterRef = init((int)Math.pow(2, nTileSize) - 1, nZoom);
		double[] dCell = new double[9]; // 4 corner points and 1 data value
		ProjProfile oProfile = ProjProfiles.getInstance().newProfile(m_dHrz, m_dVrt, m_oProj, 0);
		
		String sTimeName = m_oRR.getTime();
		for (Dimension oDim : m_oVar.getDimensions())
		{
			if (oDim.getShortName().startsWith(sTimeName))
				sTimeName = oDim.getShortName();
		}
		Index oIndex = m_oData.getIndex();
		int nHrzIndex = m_oVar.findDimensionIndex(m_oRR.getHrz());
		int nVrtIndex = m_oVar.findDimensionIndex(m_oRR.getVrt());
		int nTimeIndex = m_oVar.findDimensionIndex(sTimeName);
		oIndex.setDim(nTimeIndex, m_nT);
		
		
		long lStart = (long)m_dTime[m_nT];
		long lEnd;
		if (m_nT == m_dTime.length - 1)
		{
			if (m_dTime.length == 1)
				lEnd = m_lExpectedEnd;
			else
				lEnd = (long)m_dTime[m_dTime.length - 1] - (long)m_dTime[m_dTime.length - 2] + (long)m_dTime[m_dTime.length - 1];
		}
		else
			lEnd = (long)m_dTime[m_nT + 1];

		RangeRules oRules = ObsType.getRangeRules(m_oRR.getObsTypeId(), m_oRR.getSrcUnits());
		FilenameFormatter oFf = new FilenameFormatter(m_oRR.getTiledFf());
		Path oPath = m_oRR.getFilename(m_lRecv, lStart, lEnd, oFf);
		Files.createDirectories(oPath.getParent());
		
		int nStart = 0;
		int nEnd = m_dVrt.length - 1;
		int nStep = 1;
		if (!oProfile.m_bUseReverseY)
		{
			nStart = m_dVrt.length - 2;
			nEnd = nStep = -1;
		}

		int nTileCount = 0;
		int[] nBounds = m_oRR.getBoundingBox();
		double[] dProcessBounds = new double[]{GeoUtil.fromIntDeg(nBounds[0]), GeoUtil.fromIntDeg(nBounds[1]), GeoUtil.fromIntDeg(nBounds[2]), GeoUtil.fromIntDeg(nBounds[3])};
		Units oUnits = Units.getInstance();
		UnitConv oConv = oUnits.getConversion(m_oRR.getSrcUnits(), ObsType.getUnits(m_oRR.getObsTypeId(), true));
		int nPseudoY = 0; // ensure cell Y is zero-based
		for (int nY = nStart; nY != nEnd; nY += nStep)
		{
			oIndex.setDim(nVrtIndex, nY);
			for (int nX = 0; nX < m_dHrz.length - 1; nX++)
			{
				oIndex.setDim(nHrzIndex, nX);
				double dVal = m_oData.getDouble(oIndex);
				if (!Double.isFinite(dVal) || m_oVar.isMissing(dVal) || m_oVar.isFillValue(dVal) || m_oVar.isInvalidData(dVal))
					continue;
				
				dVal = oConv.convert(dVal);
				dVal = TileFileWriter.nearest(dVal, m_oRR.getRound());
				if (oRules.shouldDelete(oRules.groupValue(dVal)))
					continue;
				
				dCell[8] = dVal;
				oProfile.getCell(nX, nY, dCell);
				
				double dMinX = Math.min(dCell[ProjProfile.xTL], dCell[ProjProfile.xBL]);
				double dMinY = Math.min(dCell[ProjProfile.yBL], dCell[ProjProfile.yBR]);
				double dMaxX = Math.max(dCell[ProjProfile.xTR], dCell[ProjProfile.xBR]);
				double dMaxY = Math.max(dCell[ProjProfile.yTL], dCell[ProjProfile.yTR]);
				if (!GeoUtil.boundingBoxesIntersect(dMinX, dMinY, dMaxX, dMaxY, dProcessBounds[0], dProcessBounds[1], dProcessBounds[2], dProcessBounds[3]))
					continue;
				
				nTileCount = addCell(lTileWriterRef, nX, nPseudoY, dCell);
			}
			++nPseudoY;
		}

		try (DataOutputStream oWrap = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(oPath))))
		{
			oWrap.writeByte(1); // version
			oWrap.writeInt(m_nBB[0]); // bounds min x
			oWrap.writeInt(m_nBB[1]); // bounds min y
			oWrap.writeInt(m_nBB[2]); // bounds max x
			oWrap.writeInt(m_nBB[3]); // bounds max y
			oWrap.writeInt(m_oRR.getObsTypeId()); // obsversation type
			oWrap.writeByte(Util.combineNybbles(0, m_oRR.getValueType())); // obs flag = 0 (upper nybble) value type (lower nybble)
			oWrap.writeByte(Obs.POLYGON); // geo type
			oWrap.writeByte(0); // id format: -1=variable, 0=null, 16=uuid, 32=32-bytes
			oWrap.writeByte(0b00000000); // don't assoc with an obj and timestamp formats, all zero since times for obs are found in header
			oWrap.writeLong(m_lRecv);
			oWrap.writeInt((int)((lEnd - m_lRecv) / 1000)); // end time offset from received time
			oWrap.writeByte(1); // 1 start time
			oWrap.writeInt((int)((lStart - m_lRecv) / 1000)); // start time offset from received time
			oWrap.writeInt(0); // no string pool
			
			oWrap.writeByte(nZoom); // tile zoom level
			oWrap.writeByte(nTileSize);
			oWrap.writeInt(nTileCount);

			int[][] oInfoList = new int[nTileCount][];
			for (int nIndex = 0; nIndex < nTileCount; nIndex++) // write tile index
			{
				int[] nTileInfo = new int[3]; // contains tile x, tile y, compressed size
				process(lTileWriterRef, nTileInfo, 9, nIndex); // max compression 9
				oInfoList[nIndex] = nTileInfo;

				oWrap.writeShort(nTileInfo[0]); // tile x
				oWrap.writeShort(nTileInfo[1]); // tile y
				oWrap.writeInt(nTileInfo[2]); // compressed size
			}

			byte[] yBuf = new byte[65536];
			for (int nIndex = 0; nIndex < nTileCount; nIndex++) // write tile data
			{
				int[] nTileInfo = oInfoList[nIndex];
				if (yBuf.length < nTileInfo[2])
					yBuf = new byte[nTileInfo[2]]; // resize buffer as needed

				getData(lTileWriterRef, yBuf, nIndex);
				oWrap.write(yBuf, 0, nTileInfo[2]);
			}
		}
		free(lTileWriterRef);
		m_oLogger.info(String.format("Finished file for %s for time %d - %s",  Integer.toString(m_oRR.getObsTypeId(), 36), m_nT, m_sFile));
		return null;
	}
}
