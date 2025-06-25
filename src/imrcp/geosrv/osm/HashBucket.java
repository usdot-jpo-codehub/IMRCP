/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.geosrv.osm;

import imrcp.geosrv.GeoUtil;
import java.util.ArrayList;

/**
 * Represents a cell(bucket) on a hashed grid of the world used to spatially
 * index roadway segments for quick lookup. The hash indices are computed by 
 * computing 16-bit values from latitude and longitude coordinates based off of 
 * {@link #BUCKET_SPACING} and storing the longitude value in the first 2 bytes
 * of an integer and the latitude value in the last 2 bytes of the same integer.
 * @author aaron.cherney
 */
public class HashBucket extends ArrayList<OsmWay> implements Comparable<HashBucket>
{
	/**
	 * Hash index
	 */
	public int m_nHash;

	
	/**
	 * List of OsmNodes inside the bucket
	 */
	public ArrayList<OsmNode> m_oNodes = new ArrayList();

	
	/**
	 * Value used to size the buckets.
	 */
	private static final int BUCKET_SPACING = 100000; // approximately 1.1 km east/west distance at equator, 0.79 km east/west distance at 45 deg N/S latitude
	
	
	/**
	 * Default constructor. Does nothing.
	 */
	public HashBucket()
	{
	}
	
	
	/**
	 * Constructs a new HashBucket with the given hash index
	 * 
	 * @param nHash Hash index of the bucket
	 */
	public HashBucket(int nHash)
	{
		m_nHash = nHash;
	}
	
	
	/**
	 * Constructs a new HashBucket with the given longitude and latitude by
	 * calculating the hash index.
	 * 
	 * @param nX longitude in decimal degrees scaled to 7 decimal places
	 * @param nY latitude in decimal degrees scaled to 7 decimal places
	 */
	public HashBucket(int nX, int nY)
	{
		m_nHash = hashLonLat(nX, nY);
	}
	
	
	/**
	 * Computes the hash index of the given longitude and latitude
	 * 
	 * @param nLon longitude in decimal degrees scaled to 7 decimal places
	 * @param nLat latitude in decimal degrees scaled to 7 decimal places
	 * @return Hash index of the bucket the point is inside of
	 */
	public static int hashLonLat(int nLon, int nLat)
	{
		return (getBucket(nLon) << 16) | (getBucket(nLat) & 0xffff); // 16-bit hashLonLat index by lat/lon
	}
	
	
	/**
	 * Computes the hash index of the given x and y bucket values.
	 * 
	 * @param nX value returned from {@link #getBucket(int)} of a longitude in
	 * decimal degrees scaled to 7 decimal places
	 * @param nY value returned from {@link #getBucket(int)} of a latitude in
	 * decimal degrees scaled to 7 decimal places
	 * @return Hash index of the bucket with the x and y value
	 */
	public static int hashBucketVals(int nX, int nY)
	{
		return (nX << 16) | (nY & 0xffff);
	}
	
	
	/**
	 * Fills the given array with the x and y bucket values for the given
	 * hash index
	 * 
	 * @param nHash hash index
	 * @param nVals array to be filled with [x bucket value, y bucket value]
	 */
	public static void unhash(int nHash, int[] nVals)
	{
		nVals[1] = (nHash & 0x8000) == 0 ? nHash & 0xffff : nHash | 0xffff0000;
		nVals[0] = nHash >> 16;
	}

	
	/**
	 * Floors the given latitude or longitude using {@link #BUCKET_SPACING} and 
	 * divide that number by {@link #BUCKET_SPACING} to compute the bucket value.
	 * @param nValue latitude or longitude in decimal degrees scaled to 7 decimal
	 * places
	 * @return Bucket value of the given latitude or longitude.
	 */
	public static int getBucket(int nValue)
	{
		return GeoUtil.floor(nValue, BUCKET_SPACING) / BUCKET_SPACING;
	}
	
	
	/**
	 * Compares HashBuckets by hash value.
	 */
	@Override
	public int compareTo(HashBucket o)
	{
		return Integer.compare(m_nHash, o.m_nHash);
	}
}
