/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.collect;

/**
 *
 * @author Federal Highway Administration
 */
public class LibImrcpWriter 
{
	static
	{
		System.loadLibrary("imrcp");
	}
	
	protected static native long init(int nPPT, int nZoom);


	protected static native int addCell(long lTileWriterRef, int nX, int nY, double[] dCell);


	protected static native void process(long lTileWriterRef, int[] nTileInfo, int nLevel, int nIndex);


	protected static native void getData(long lTileWriterRef, byte[] yBuf, int nIndex);


	protected static native void free(long lTileWriterRef);
}
