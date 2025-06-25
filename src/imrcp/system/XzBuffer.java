package imrcp.system;


public abstract class XzBuffer
{
	static
	{
		System.loadLibrary("imrcp");
	}
	
	
	public static byte[] decompress(byte[] yCompressed)
	{
		long lXzRef = init(0, 65536, 4);
		int nSize = XzBuffer.proc(lXzRef, yCompressed);
		byte[] yOutBuf = new byte[nSize];
		XzBuffer.free(lXzRef, yOutBuf);
		return yOutBuf;
	}
	
	
	public static byte[] compress(byte[] yRawBytes)
	{
		long lXzRef = init(9, 65536, 4);
		int nSize = XzBuffer.proc(lXzRef, yRawBytes);
		byte[] yCompressed = new byte[nSize];
		XzBuffer.free(lXzRef, yCompressed);
		return yCompressed;
	}
	
	
	private static native long init(int nLevel, int nChunkSize, int nDefaultElems);


	private static native int proc(long lXzBufferRef, byte[] yInBuf);


	private static native void free(long lXzBufferRef, byte[] yOutBuf);
}