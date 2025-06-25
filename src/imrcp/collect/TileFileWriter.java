/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.collect;

import imrcp.store.TileObsView;
import imrcp.system.BaseBlock;
import imrcp.system.FilenameFormatter;
import imrcp.system.OneTimeReentrantLock;
import imrcp.system.ResourceRecord;
import imrcp.system.Scheduling;
import imrcp.store.TileFileReader;
import imrcp.system.Util;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeSet;
import org.json.JSONObject;

/**
 *
 * @author aaron.cherney
 */
public abstract class TileFileWriter extends BaseBlock
{
	private static final Comparator<String> STRCOMP = (String s1, String s2) ->
	{
		return s1.compareTo(s2);
	};
	protected final ArrayDeque<TileFileInfo> m_oQueue = new ArrayDeque();
	protected final ArrayDeque<TileFileInfo> m_oRealTime = new ArrayDeque();
	protected final TreeSet<String> m_oProcessing = new TreeSet(STRCOMP);
	protected int m_nThreads;
	protected int m_nPeriod;
	protected int m_nOffset;
	
	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_nThreads = oBlockConfig.optInt("threads", 1);
		m_nPeriod = oBlockConfig.optInt("period", 0);
		m_nOffset = oBlockConfig.optInt("offset", 0);
	}
	
	
	public static double nearest(double dVal, double dTrgt)
	{
		if (dVal < 0.0)
		{
			dVal -= dTrgt / 2.0; // round up to
		}
		else
		{
			dVal += dTrgt / 2.0; // largest magnitude
		}
		return ((long) (dVal / dTrgt)) * dTrgt;
	}
	
	protected abstract void createFiles(TileFileInfo oInfo, TreeSet<Path> oArchiveFiles);
	
	
	private void process(TileFileInfo oInfo)
	{
		oInfo.m_oLock.lock();
		try
		{
			final ArrayDeque<TileFileInfo> oQueue;
			if (oInfo.m_bRequest)
				oQueue = m_oQueue;
			else
				oQueue = m_oRealTime;
			
			synchronized (oQueue)
			{
				oQueue.notify();
			}
			ResourceRecord oRR = oInfo.m_oRRs.get(0);
			TreeSet<Path> oProcess = new TreeSet();
			FilenameFormatter oArchiveFF = null;
			if (!oRR.getArchiveFf().isEmpty())
			{
				oArchiveFF = new FilenameFormatter(oRR.getArchiveFf());
				TreeSet<Path> oArchiveFiles = TileObsView.searchArchive(oInfo.m_lStart, oInfo.m_lEnd, oInfo.m_lRef, oRR);
				
				synchronized (m_oProcessing)
				{
					for (Path oFile : oArchiveFiles)
					{
						String sFile = oFile.toString();
						if (m_oProcessing.add(sFile))
							oProcess.add(oFile);
					}
				}
			}
			createFiles(oInfo, oProcess);
			long[] lTimes = new long[3];
			FilenameFormatter oTileFF = new FilenameFormatter(oRR.getTiledFf());
			if (oArchiveFF != null)
			{
				synchronized (m_oProcessing)
				{
					for (Path oFile : oProcess)
					{
						String sFile = oFile.toString();
						m_oProcessing.remove(sFile);
						oArchiveFF.parse(oFile.toString(), lTimes);
						Path oBlankTile = oRR.getFilename(lTimes[FilenameFormatter.VALID], lTimes[FilenameFormatter.START], lTimes[FilenameFormatter.END], oTileFF);
						try
						{
							if (Files.exists(oBlankTile) && Files.size(oBlankTile) == 0)
								Files.delete(oBlankTile);
						}
						catch (IOException oEx)
						{
						}
					}
				}
			}
		}
		finally
		{
			oInfo.m_oLock.unlock();
		}
		final ArrayDeque<TileFileInfo> oQueue;
		if (oInfo.m_bRequest)
			oQueue = m_oQueue;
		else
			oQueue = m_oRealTime;
		synchronized (oQueue)
		{
			oQueue.pollFirst(); // remove the object that just processed
			if (!oQueue.isEmpty())
			{
				Scheduling.getInstance().execute(() -> process(oQueue.peekFirst()));
				try
				{
					oQueue.wait(1000);
				}
				catch (InterruptedException oEx)
				{
				}
			}
		}
	}
	
	protected OneTimeReentrantLock processRealTime(ArrayList<ResourceRecord> oRRs, long lQueryStart, long lQueryEnd, long lQueryRef)
	{
		return queue(oRRs, lQueryStart, lQueryEnd, lQueryRef, false);
	}
	
	public OneTimeReentrantLock queueRequest(ArrayList<ResourceRecord> oRRs, long lQueryStart, long lQueryEnd, long lQueryRef)
	{
		return queue(oRRs, lQueryStart, lQueryEnd, lQueryRef, true);
	}
	
	
	protected OneTimeReentrantLock queue(ArrayList<ResourceRecord> oRRs, long lQueryStart, long lQueryEnd, long lQueryRef, boolean bRequest)
	{
		TileFileInfo oInfo = new TileFileInfo(oRRs, lQueryStart, lQueryEnd, lQueryRef, bRequest);
		OneTimeReentrantLock oReturn = oInfo.m_oLock;
		if (oRRs.get(0).getTiledFf().isEmpty())
			return oReturn;
		final ArrayDeque<TileFileInfo> oQueue;
		if (bRequest)
			oQueue = m_oQueue;
		else
			oQueue = m_oRealTime;
		synchronized (oQueue)
		{
			boolean bCanQueue = true;
			for (TileFileInfo oTFI : oQueue)
			{
				if (oInfo.compareTo(oTFI) == 0)
				{
					bCanQueue = false;
					oReturn = oTFI.m_oLock;
					break;
				}
			}
			if (bCanQueue && oQueue.isEmpty())
			{
				oQueue.addLast(oInfo);
				Scheduling.getInstance().execute(() -> process(oInfo));
				try
				{
					oQueue.wait(1000);
				}
				catch (InterruptedException oEx)
				{
				}
			}
			else if (bCanQueue)
				oQueue.addLast(oInfo);	
		}
		
		return oReturn;
	}
	
	
	public static ValueWriter newValueWriter(int nByte)
	{
		switch (nByte)
		{
			case TileFileReader.UBYTE:
			case TileFileReader.SBYTE:
				return new ByteWriter();
			case TileFileReader.USHORT:
			case TileFileReader.SSHORT:
				return new ShortWriter();
			case TileFileReader.HALFFLOAT:
				return new HalfPrecisionWriter();
			case TileFileReader.SINGLEFLOAT:
				return new FloatWriter();
		}
		
		return null;
	}
	public static abstract class ValueWriter
	{
		public abstract void writeValue(DataOutputStream oOut, double dValue) throws IOException;
	}

	
	public static class ByteWriter extends ValueWriter
	{
		@Override
		public void writeValue(DataOutputStream oOut, double dValue) throws IOException
		{
			oOut.writeByte((int)dValue);
		}	
	}
	
	
	public static class ShortWriter extends ValueWriter
	{
		@Override
		public void writeValue(DataOutputStream oOut, double dValue) throws IOException
		{
			oOut.writeShort((int)dValue);
		}	
	}
	
	
	public static class HalfPrecisionWriter extends ValueWriter
	{
		@Override
		public void writeValue(DataOutputStream oOut, double dValue) throws IOException
		{
			oOut.writeShort(Util.toHpfp((float)dValue));
		}	
	}
	
	
	public static class FloatWriter extends ValueWriter
	{
		@Override
		public void writeValue(DataOutputStream oOut, double dValue) throws IOException
		{
			oOut.writeFloat((float)dValue);
		}
	}
}
