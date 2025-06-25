/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.comp;

import imrcp.geosrv.GeoUtil;
import imrcp.geosrv.RangeRules;
import imrcp.store.Obs;
import imrcp.geosrv.ProjProfile;
import imrcp.system.FilenameFormatter;
import imrcp.collect.LibImrcpWriter;
import imrcp.system.ObsType;
import imrcp.system.ResourceRecord;
import imrcp.collect.TileFileWriter;
import imrcp.system.Util;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;


/**
 *
 * @author Federal Highway Administration
 */
public class DATileFileWriter extends LibImrcpWriter
{
	public void write(ProjProfile oProfile, double[][] dGrid, ResourceRecord oRR, long lRecvAndStart, long lEnd, int[] nGridMinMax, double dMinVal, double dBias)
		throws Exception
	{
		int nZoom = oRR.getZoom();
		int nTileSize = oRR.getTileSize();
		long lTileWriterRef = init((int)Math.pow(2, nTileSize) - 1, nZoom);
		double[] dCell = new double[9]; // 4 corner points and 1 data value
		

		RangeRules oRules = ObsType.getRangeRules(oRR.getObsTypeId(), oRR.getSrcUnits());
		FilenameFormatter oFf = new FilenameFormatter(oRR.getTiledFf());
		Path oPath = oRR.getFilename(lRecvAndStart, lRecvAndStart, lEnd, oFf);
		if (Files.exists(oPath))
			return;
		Files.createDirectories(oPath.getParent());
		
		int[] nBB = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (int nGridMinMaxIndex = 0; nGridMinMaxIndex < nGridMinMax.length;)
		{
			oProfile.getCell(nGridMinMax[nGridMinMaxIndex++], nGridMinMax[nGridMinMaxIndex++], dCell);
			for (int nCornersIndex = 0; nCornersIndex < dCell.length - 1;)
			{
				int nX = GeoUtil.toIntDeg(dCell[nCornersIndex++]);
				int nY = GeoUtil.toIntDeg(dCell[nCornersIndex++]);

				if (nX < nBB[0])
					nBB[0] = nX;
				if (nY < nBB[1])
					nBB[1] = nY;
				if (nX > nBB[2])
					nBB[2] = nX;
				if (nY > nBB[3])
					nBB[3] = nY;
			}
		}
		
		int nStart = nGridMinMax[1];
		int nEnd = nGridMinMax[3];
		int nStep = 1;
		if (!oProfile.m_bUseReverseY)
		{
			nStart = nGridMinMax[3];
			nEnd = nGridMinMax[1];
			nStep = -1;
		}

		int nTileCount = 0;
		int[] nBounds = oRR.getBoundingBox();
		double[] dProcessBounds = new double[]{GeoUtil.fromIntDeg(nBounds[0]), GeoUtil.fromIntDeg(nBounds[1]), GeoUtil.fromIntDeg(nBounds[2]), GeoUtil.fromIntDeg(nBounds[3])};
		int nPseudoY = 0; // ensure cell Y is zero-based
		int nMeasured = 0;
		int nEstimates = 0;
		for (int nY = nStart; nY != nEnd; nY += nStep)
		{
			for (int nX = nGridMinMax[0]; nX < nGridMinMax[2]; nX++)
			{
				double dVal = dGrid[nY][nX];
				if (!Double.isFinite(dVal))
					continue;
				if (dVal > dMinVal)
				{
					++nMeasured;
				}
				else
				{
					dVal += dBias;
					++nEstimates;
				}
				dVal = TileFileWriter.nearest(dVal, oRR.getRound());

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
			oWrap.writeInt(nBB[0]); // bounds min x
			oWrap.writeInt(nBB[1]); // bounds min y
			oWrap.writeInt(nBB[2]); // bounds max x
			oWrap.writeInt(nBB[3]); // bounds max y
			oWrap.writeInt(oRR.getObsTypeId()); // obsversation type
			oWrap.writeByte(Util.combineNybbles(0, oRR.getValueType())); // obs flag = 0 (upper nybble) value type (lower nybble)
			oWrap.writeByte(Obs.POLYGON); // geo type
			oWrap.writeByte(0); // id format: -1=variable, 0=null, 16=uuid, 32=32-bytes
			oWrap.writeByte(0b00000000); // don't assoc with an obj and timestamp formats, all zero since times for obs are found in header
			oWrap.writeLong(lRecvAndStart);
			oWrap.writeInt((int)((lEnd - lRecvAndStart) / 1000)); // end time offset from received time
			oWrap.writeByte(1); // 1 start time
			oWrap.writeInt(0); // start time offset from received time
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
	}
}
