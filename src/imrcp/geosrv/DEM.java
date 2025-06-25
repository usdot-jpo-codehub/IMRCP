/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.geosrv;

import imrcp.system.BaseBlock;
import java.awt.image.BufferedImage;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import org.json.JSONObject;

/**
 * Manages downloading, caching, and decoding PNG tiles from the Mapbox Terrian-DEM
 * (Digital Elevation Map)
 * @author aaron.cherney
 */
public class DEM extends BaseBlock implements Comparator<int[]>
{
	/**
	 * Format string used to generate the correct URL
	 */
	private String m_sUrlFormat;

	
	/**
	 * Mapbox access token
	 */
	private String m_sAccessToken;

	
	/**
	 * Time in milliseconds files can be on disk until they need to be updated
	 */
	private long m_lFileTimeout;

	
	/**
	 * Format string used to generate file names
	 */
	private String m_sFileFormat;

	
	/**
	 * Zoom level to download the map tiles. Zoom level 14 is the best 
	 * resolution for this tileset using tiles with 512 pixels
	 */
	private final int ZOOM = 14;

	
	/**
	 * Limit on the number of files that can be cached in memory
	 */
	private int m_nLimit;

	
	/**
	 * SKU string used in Mapbox URLs. Must be updated every 12 hours
	 */
	private String m_sSKU;

	
	/**
	 * Time in milliseconds since Epoch that a new SKU needs to be generated
	 */
	private long m_lSkuTimeout;

	
	/**
	 * Stores the PNG files in memory for quick lookup. The keys have the format
	 * [x tile index, y tile index]
	 */
	private final TreeMap<int[], BufferedImage> m_oCache = new TreeMap(this);
	
	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_sUrlFormat = oBlockConfig.optString("url", "");
		m_sAccessToken = oBlockConfig.optString("token", "");
		m_lFileTimeout = oBlockConfig.optLong("filetimeout", 31536000000L);
		m_sFileFormat = oBlockConfig.optString("file", "%d/%d/%d.png");
		if (m_sFileFormat.startsWith("/"))
			m_sFileFormat = m_sFileFormat.substring(1);
		m_nLimit = oBlockConfig.optInt("cachelimit", 100);
		StringBuilder sBuf = new StringBuilder();
		mapboxSKU(sBuf, new SecureRandom());
		m_sSKU = sBuf.toString();
		m_lSkuTimeout = System.currentTimeMillis() + 43140000; // timeout after 11hrs and 59mins
		synchronized (m_oCache)
		{
			m_oCache.clear();
		}
	}
	
	
	/**
	 * Looks up elevation at the given location. First this method determines the 
	 * file name based off the map tile that contains the location. If the file 
	 * is not saved on disk or its last modified time is too old based on 
	 * {@link #m_lFileTimeout}, the file is downloaded and written to disk. Then
	 * the method checks if the file is in {@link #m_oCache}, and loads it into 
	 * memory if it is not. Finally the location in the tile is calculated and
	 * the elevation is decoded from the values in the color channels at that 
	 * pixel.
	 * 
	 * @param nLon longitude of location in decimal degrees scaled to 7 decimal 
	 * places
	 * @param nLat latitude of location in decimal degrees scaled to 7 decimal 
	 * places
	 * @return Elevation at the location in meters
	 */
	public double getElev(int nLon, int nLat)
	{
		try
		{
			Mercator oM = new Mercator();
			int[] nTiles = new int[2];
			double[] dBounds = new double[4];
			double dLon = GeoUtil.fromIntDeg(nLon);
			double dLat = GeoUtil.fromIntDeg(nLat);
			oM.lonLatToTile(dLon, dLat, ZOOM, nTiles);
			oM.lonLatBounds(nTiles[0], nTiles[1], ZOOM, dBounds);
			Path oFile = Paths.get(getFilename(nTiles[0], nTiles[1]));
			if (!Files.exists(oFile) || System.currentTimeMillis() > Files.getLastModifiedTime(oFile).toMillis() + m_lFileTimeout)
			{
				if (System.currentTimeMillis() > m_lSkuTimeout)
				{
					StringBuilder sBuf = new StringBuilder();
					mapboxSKU(sBuf, new SecureRandom());
					m_sSKU = sBuf.toString();
					m_lSkuTimeout = System.currentTimeMillis()  + 43140000; // timeout after 11hrs and 59mins
				}
				String sUrl = String.format(m_sUrlFormat, ZOOM, nTiles[0], nTiles[1], m_sSKU, m_sAccessToken);
				URL oUrl = new URL(sUrl);
				HttpURLConnection oConn = (HttpURLConnection)oUrl.openConnection();
				oConn.setRequestProperty("Referer", "imrcp.data-env.com");
				oConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.87 Safari/537.36");
				m_oLogger.info("Downloading: " + sUrl.substring(0, sUrl.lastIndexOf("?")));
				Files.createDirectories(oFile.getParent());
				Files.copy(oConn.getInputStream(), oFile);
			}
			
			if (!Files.exists(oFile))
				return Double.NaN;
			
			synchronized (m_oCache)
			{
				BufferedImage oImg;
				if (!m_oCache.containsKey(nTiles))
				{
					oImg = ImageIO.read(Files.newInputStream(oFile));
					m_oCache.put(nTiles, oImg);
					if (m_oCache.size() > m_nLimit)
						m_oCache.remove(m_oCache.firstKey());
				}
				else
					oImg = m_oCache.get(nTiles);
				
				int nX = (int)((dLon - dBounds[0]) / (dBounds[2] - dBounds[0]) * 512);
				int nY = (int)((dBounds[3] - dLat) / (dBounds[3] - dBounds[1]) * 512);
				return ((oImg.getRGB(++nX, ++nY) & 0x00FFFFFF) - 100000) * 0.1; // ignore the alpha value when decoding, add one to each coordinate since there is a one pixel buffer. From mapbox: elevation = -10000 + (({R} * 256 * 256 + {G} * 256 + {B}) * 0.1)
			}
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
			return Double.NaN;
		}
	}
	
	
	/**
	 * Generates the file name for the given x and y tile indices
	 * @param nX x tile index
	 * @param nY y tile index
	 * @return File name used to save files on disk
	 */
	public String getFilename(int nX, int nY)
	{
		return m_sDataPath + String.format(m_sFileFormat, ZOOM, nX, nY);
	}
	
	
	/**
	 * Generates a mapbox SKU token to simulate a session
	 * @param sBuf StringBuilder to append the characters of the SKU to
	 * @param oRng object to generate random numbers
	 */
	private static void mapboxSKU(StringBuilder sBuf, SecureRandom oRng)
    {
        sBuf.append("101");
        int nCount = 10;
        while (nCount-- > 0)
            sBuf.append("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt((int)(oRng.nextDouble() * 61.0)));
    } 

	
	/**
	 * Integer arrays are used to represent a tile. Tile indices are stored in 
	 * [x tile index, y tile index] so this compares tiles by x index then y 
	 * index.
	 * 
	 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object) 
	 */
	@Override
	public int compare(int[] o1, int[] o2)
	{
		int nRet = o1[0] - o2[0];
		if (nRet == 0)
			nRet = o1[1] - o2[1];
		
		return nRet;
	}
}
