package imrcp.geosrv;

import java.util.Arrays;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Contains methods for converting WGS 84 longitude/latitude (epsg:4326), Spherical Mercator 
 * (epsg:3857), and map tile coordinates. Most methods were adapted from algorithms
 * found at https://www.maptiler.com/google-maps-coordinates-tile-bounds-projection
 * @author aaron.cherney
 */
public class Mercator 
{
	/**
	 * Stores the Resolution (meters/pixel) for each zoom level
	 */
	public final double[] RES = new double[24];

	
	/**
	 * Major (equatorial) radius of the earth in m
	 */
	private static final double R_MAJOR = 6378137.0;

	
	/**
	 * Pi divided by 2
	 */
	public static final double PI_OVER_TWO = Math.PI / 2.0;

	
	/**
	 * Pi times equatorial radius of earth
	 */
	private static final double ORIGIN_SHIFT = Math.PI * R_MAJOR;

	
	/**
	 * Pi times equatorial radius of earth divided by 180
	 */
	private static final double ORIGIN_SHIFT_DIVIDED_BY_180 = ORIGIN_SHIFT / 180.0;

	
	/**
	 * Pi divided by 180
	 */
	private static final double PI_OVER_180 = Math.PI / 180.0;

	
	/**
	 * Pi divided by 360
	 */
	private static final double PI_OVER_360 = PI_OVER_180 / 2.0;
	
	
	/**
	 * Number of pixels in a tile
	 */
	int m_nTileSize;
	
	
	/**
	 * Default constructor. Wrapper for {@link Mercator#Mercator(int)} with tile
	 * size 256.
	 */
	public Mercator()
	{
		this(256);
	}
	
	
	public static void main(String[] sArgs)
		throws Exception
	{
		Mercator oM = new Mercator();
		int nTileX = 20;
		int nTileY = 43;
		double[] dBounds = new double[4];
		int nZoom = 7;
		oM.lonLatBounds(nTileX, nTileY, nZoom, dBounds);
		System.out.println(Arrays.toString(dBounds));
		System.out.println(dBounds[2] - dBounds[0]);
		System.out.println(dBounds[3] - dBounds[1]);
	}
	/**
	 * Constructs a Mercator object with the given tile size. Calculates the
	 * resolution for each zoom level.
	 * @param nTileSize
	 */
	public Mercator(int nTileSize)
	{
		m_nTileSize = nTileSize;
		double dInitRes = 2.0 * ORIGIN_SHIFT / m_nTileSize;
		for (int nIndex = 0; nIndex < RES.length; nIndex++)
			RES[nIndex] = dInitRes / (1 << nIndex);
	}
	
	
	/**
	 * Gets the extent used in vector tiles for the given zoom level
	 * @param nZoom map zoom level
	 * @return extent used in vector tiles for the given zoom level
	 */
	public static int getExtent(int nZoom)
	{
		return (1 << nZoom) * 256 - 1;
	}
	
	
	/**
	 * Converts a WGS 84 longitude to Spherical Mercator meters
	 * @param dLon longitude in decimal degrees
	 * @return Corresponding mercator meter x coordinate
	 */
	public static double lonToMeters(double dLon)
	{
		return dLon * ORIGIN_SHIFT_DIVIDED_BY_180;
	}
	
	
	/**
	 * Converts a WGS 84 latitude to Spherical Mercator meters
	 * @param dLat latitude in decimal degrees
	 * @return Corresponding mercator meter y coordinate
	 */
	public static double latToMeters(double dLat)
	{
		return Math.log(Math.tan((90.0 + dLat) * PI_OVER_360)) * R_MAJOR;
	}
	
	
	/**
	 * Fills the given double array with the corresponding x and y Spherical 
	 * Mercator meter coordinate of the given longitude and latitude in 
	 * decimal degrees
	 * @param dLon longitude in decimal degrees
	 * @param dLat latitude in decimal degrees
	 * @param dMeters array to be filled with [mercator meter x, mercator meter y]
	 */
	public static void lonLatToMeters(double dLon, double dLat, double[] dMeters)
	{
		dMeters[0] = lonToMeters(dLon);
		dMeters[1] = latToMeters(dLat);
	}
	
	
	/**
	 * Fills the given double array with the corresponding longitude and latitude
	 * in decimal degrees of the given x and y Spherical Mercator meter coordinates.
	 * @param dX mercator meters x 
	 * @param dY mercator meters y
	 * @param dLatLon array to be filled with [longitude, latitude]
	 */
	public void metersToLonLat(double dX, double dY, double[] dLatLon)
	{
		dLatLon[0] = dX / ORIGIN_SHIFT * 180.0;
		dLatLon[1] = 180.0 * (2.0 * Math.atan(Math.exp(Math.PI * dY / ORIGIN_SHIFT)) - PI_OVER_TWO) / Math.PI;
	}
	
	
	/**
	 * Fills the given double array by converting pixel coordinates to Spherical 
	 * Mercator meters at the given zoom level.
	 * @param dXp pixel x coordinate
	 * @param dYp pixel y coordinate
	 * @param nZoom map zoom level
	 * @param dMeters array to be filled with [mercator meters x, mercator meters y]
	 */
	public void pixelsToMeters(double dXp, double dYp, int nZoom, double[] dMeters)
	{
		double dRes = RES[nZoom];
		dMeters[0] = dXp * dRes - ORIGIN_SHIFT;
		dMeters[1] = dRes * (m_nTileSize * (1 << nZoom) - dYp) - ORIGIN_SHIFT;
	}
	
	
	/**
	 * Files the given double array by converting Spherical Mercator meters to
	 * pixel coordinates at the given zoom level.
	 * @param dXm mercator meters x
	 * @param dYm mercator meters y
	 * @param nZoom map zoom level
	 * @param dPixels array to be filled with [pixel x, pixel y]
	 */
	public void metersToPixels(double dXm, double dYm, int nZoom, double[] dPixels)
	{
		double dRes = RES[nZoom];
		dPixels[0] = (dXm + ORIGIN_SHIFT) / dRes;
		dPixels[1] = (1 << nZoom) * m_nTileSize - ((dYm + ORIGIN_SHIFT) / dRes);
	}
	
	
	/**
	 * Fills the given int array with the corresponding tile x and y coordinates
	 * of the given pixel coordinates
	 * @param dXp pixel x coordinate
	 * @param dYp pixel y coordinate
	 * @param nTiles array to be filled with [tile x, tile y]
	 */
	public void pixelsToTile(double dXp, double dYp, int nZoom, int[] nTiles)
	{
		nTiles[0] = (int)((Math.ceil(dXp / m_nTileSize)) - 1);
		nTiles[1] = (int)((Math.ceil(dYp / m_nTileSize)) - 1);
	}
	
	
	/**
	 * Fills the given double array with the corresponding bounds in Spherical 
	 * Mercator meters of the given map tile coordinates at the given zoom level
	 * @param dXt map tile x coordinate
	 * @param dYt map tile y coordinate
	 * @param nZoom map zoom level
	 * @param dBounds array to be filled with [min x mercator meters, min y mercator
	 * meters, max x mercator meters, max y mercator meters]
	 */
	public void tileBounds(double dXt, double dYt, int nZoom, double[] dBounds)
	{
		double[] dMeters = new double[2];
		pixelsToMeters(dXt * m_nTileSize, dYt * m_nTileSize, nZoom, dMeters);
		dBounds[0] = dMeters[0];
		dBounds[3] = dMeters[1]; // adjust for shared edge to not include the top side
		pixelsToMeters((dXt + 1) * m_nTileSize, (dYt + 1) * m_nTileSize, nZoom, dMeters);
		dBounds[2] = dMeters[0]; // adjust for shared edge to not include the right side
		dBounds[1] = dMeters[1];
	}
	
	
	/**
	 * Fills the given double array with the corresponding bounds in longitude
	 * and latitude in decimal degrees of the given map tile coordinates at the 
	 * given zoom level
	 * @param dXt map tile x coordinate
	 * @param dYt map tile y coordinate
	 * @param nZoom map zoom level
	 * @param dBounds array to be filled with [min longitude, min latitude, max
	 * longitude, max latitude]
	 */
	public void lonLatBounds(double dXt, double dYt, int nZoom, double[] dBounds)
	{
		double[] dMeterBounds = new double[4];
		tileBounds(dXt, dYt, nZoom, dMeterBounds);
		double[] dLonLat = new double[2];
		metersToLonLat(dMeterBounds[0], dMeterBounds[1], dLonLat);
		dBounds[0] = dLonLat[0];
		dBounds[1] = dLonLat[1];
		metersToLonLat(dMeterBounds[2], dMeterBounds[3], dLonLat);
		dBounds[2] = dLonLat[0];
		dBounds[3] = dLonLat[1];
	}
	
	
	/**
	 * Fills the given double array with the corresponding longitude and latitude
	 * in decimal degrees midpoint of the given tile coordinates.
	 * @param dXt tile x coordinate
	 * @param dYt tile y coordinate
	 * @param nZoom map zoom level
	 * @param dMdpt array to be filled with [midpoint longitude, midpoint latitude]
	 */
	public void lonLatMdpt(double dXt, double dYt, int nZoom, double[] dMdpt)
	{
		double[] dBounds = new double[4];
		lonLatBounds(dXt, dYt, nZoom, dBounds);
		dMdpt[0] = (dBounds[0] + dBounds[2]) / 2;
		dMdpt[1] = (dBounds[1] + dBounds[3]) / 2;
	}
	
	
	/**
	 * Fills the given array with the corresponding tile coordinate of the given
	 * longitude and latitude in decimal degrees for the given map zoom level
	 * @param dLon longitude in decimal degrees
	 * @param dLat latitude in decimal degrees
	 * @param nZoom map zoom level
	 * @param nTiles array to be filled with [tile x, tile y]
	 */
	public void lonLatToTile(double dLon, double dLat, int nZoom, int[] nTiles)
	{
		double[] dTemp = new double[2];
		lonLatToMeters(dLon, dLat, dTemp);
		metersToPixels(dTemp[0], dTemp[1], nZoom, dTemp);
		pixelsToTile(dTemp[0], dTemp[1], nZoom, nTiles);
	}
	
	
	public void lonLatToTilePixels(double dLon, double dLat, int nXt, int nYt, int nZoom, double[] dPixels)
	{
		lonLatToMeters(dLon, dLat, dPixels);
		metersToPixels(dPixels[0], dPixels[1], nZoom, dPixels);
		int nPixelXLeft = nXt * m_nTileSize;
		int nPixelYTop = nYt * m_nTileSize;
		dPixels[0] = dPixels[0] - nPixelXLeft;
		dPixels[1] = dPixels[1] - nPixelYTop;
	}
	
	
	public void tilePixelsTolonLat(double dPixelX, double dPixelY, int nXt, int nYt, int nZoom, double[] dLonLat)
	{
		int nPixelXLeft = nXt * m_nTileSize;
		int nPixelYTop = nYt * m_nTileSize;
		dPixelX += nPixelXLeft;
		dPixelY += nPixelYTop;
		pixelsToMeters(dPixelX, dPixelY, nZoom, dLonLat);
		metersToLonLat(dLonLat[0], dLonLat[1], dLonLat);
	}
	
	/**
	 * Fills the given array with the corresponding tile coordinate of the given
	 * Spherical Mercator meter coordinates for the given map zoom level.
	 * @param dXm mercator meters x
	 * @param dYm mercator meters y
	 * @param nZoom map zoom level
	 * @param nTiles array to be filled with [tile x, tile y]
	 */
	public void metersToTile(double dXm, double dYm, int nZoom, int[] nTiles)
	{
		double[] dPixels = new double[2];
		metersToPixels(dXm, dYm, nZoom, dPixels);
		pixelsToTile(dPixels[0], dPixels[1], nZoom, nTiles);
	}
	
	
	public int getTileSize()
	{
		return m_nTileSize;
	}
}
