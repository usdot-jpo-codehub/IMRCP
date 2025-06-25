#define __USE_MISC // flag to define pi contants
#include <math.h>
#include <stdlib.h>
#include "Mercator.h"


/**
* Contains methods for converting WGS 84 longitude/latitude (epsg:4326), Spherical Mercator 
* (epsg:3857), and map tile coordinates. Most methods were adapted from algorithms
* found at https://www.maptiler.com/google-maps-coordinates-tile-bounds-projection
* @author aaron.cherney
*/


/**
* Major (equatorial) radius of the earth in m
*/

const double R_MAJOR = 6378137.0;


/**
* Pi times equatorial radius of earth
*/
const double ORIGIN_SHIFT = M_PI * R_MAJOR;


/**
* Pi times equatorial radius of earth divided by 180
*/
const double ORIGIN_SHIFT_180 = ORIGIN_SHIFT / 180.0;


/**
* Pi divided by 360
*/
const double M_PI_360 = M_PI / 360.0;


static double nearest(double dVal, double dScale)
{
	return round(dVal * dScale) / dScale;
}


/**
* Default constructor. Wrapper for {@link Mercator#Mercator(int)} with tile
* size 256.
*/
Mercator* Mercator_new()
{
	return Mercator_new_size(256); // shouldn't this be 255?
}


/**
* Constructs a Mercator object with the given tile size. Calculates the
* resolution for each zoom level.
* @param nTileSize
*/
Mercator* Mercator_new_size(int nTileSize)
{
	Mercator* pThis = malloc(sizeof(Mercator));
	pThis->m_nTileSize = nTileSize;
	for (int nIndex = 0; nIndex < 24; nIndex++)
	{
		pThis->m_dRes2[nIndex] = 2.0 * ORIGIN_SHIFT / (1 << nIndex);
		pThis->m_dRes[nIndex] = pThis->m_dRes2[nIndex] / nTileSize;
	}
	return pThis;
}


/**
* Gets the extent used in vector tiles for the given zoom level
* @param nZoom map zoom level
* @return extent used in vector tiles for the given zoom level
*/
int Mercator_get_extent(Mercator* pThis, int nZoom)
{
	return (1 << nZoom) * pThis->m_nTileSize - 1;
}


/**
* Fills the given double array with the corresponding x and y Spherical 
* Mercator meter coordinate of the given longitude and latitude in 
* decimal degrees
* @param dLon longitude in decimal degrees
* @param dLat latitude in decimal degrees
* @param dMeters array to be filled with [mercator meter x, mercator meter y]
*/
void Mercator_lon_lat_to_meters(double dLon, double dLat, double* dMeters)
{
	*dMeters++ = dLon * ORIGIN_SHIFT_180;
	*dMeters = log(tan((90.0 + dLat) * M_PI_360)) * R_MAJOR;
}


/**
* Fills the given double array with the corresponding longitude and latitude
* in decimal degrees of the given x and y Spherical Mercator meter coordinates.
* @param dX mercator meters x 
* @param dY mercator meters y
* @param dLatLon array to be filled with [longitude, latitude]
*/
void Mercator_meters_to_lon_lat(double dX, double dY, double* dLatLon)
{
	*dLatLon++ = dX / ORIGIN_SHIFT * 180.0;
	*dLatLon = 180.0 * (2.0 * atan(exp(M_PI * dY / ORIGIN_SHIFT)) - M_PI_2) / M_PI;
}


/**
* Fills the given double array by converting pixel coordinates to Spherical 
* Mercator meters at the given zoom level.
* @param dXp pixel x coordinate
* @param dYp pixel y coordinate
* @param nZoom map zoom level
* @param dMeters array to be filled with [mercator meters x, mercator meters y]
*/
void Mercator_pixels_to_meters(Mercator* pThis, double dXp, double dYp, int nZoom, double* dMeters)
{
	double dRes = pThis->m_dRes[nZoom];
	*dMeters++ = dXp * dRes - ORIGIN_SHIFT;
	*dMeters = dRes * (pThis->m_nTileSize * (1 << nZoom) - dYp) - ORIGIN_SHIFT;
}


/**
* Files the given double array by converting Spherical Mercator meters to
* pixel coordinates at the given zoom level.
* @param dXm mercator meters x
* @param dYm mercator meters y
* @param nZoom map zoom level
* @param dPixels array to be filled with [pixel x, pixel y]
*/
void Mercator_meters_to_pixels(Mercator* pThis, double dXm, double dYm, int nZoom, double* dPixels)
{
	double dRes = pThis->m_dRes[nZoom];
	*dPixels++ = (dXm + ORIGIN_SHIFT) / dRes;
	*dPixels = (1 << nZoom) * pThis->m_nTileSize - (dYm + ORIGIN_SHIFT) / dRes;
}


/**
* Fills the given int array with the corresponding tile x and y coordinates
* of the given pixel coordinates
* @param dXp pixel x coordinate
* @param dYp pixel y coordinate
* @param nTiles array to be filled with [tile x, tile y]
*/
void Mercator_pixels_to_tile(Mercator* pThis, double dXp, double dYp, int nZoom, int* nTiles)
{
	*nTiles++ = (int)floor(dXp / pThis->m_nTileSize);
	*nTiles = (int)floor(dYp / pThis->m_nTileSize);
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
void Mercator_tile_bounds(Mercator* pThis, double dXt, double dYt, int nZoom, double* dBounds)
{
	double dMeters[2];

	Mercator_pixels_to_meters(pThis, dXt * pThis->m_nTileSize, dYt * pThis->m_nTileSize, nZoom, dMeters);
	dBounds[0] = dMeters[0];
	dBounds[3] = dMeters[1]; // adjust for shared edge to not include the top side

	Mercator_pixels_to_meters(pThis, (dXt + 1) * pThis->m_nTileSize, (dYt + 1) * pThis->m_nTileSize, nZoom, dMeters);
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
void Mercator_lon_lat_bounds(Mercator* pThis, double dXt, double dYt, int nZoom, double* dBounds)
{
	double dMeterBounds[4];
	Mercator_tile_bounds(pThis, dXt, dYt, nZoom, dMeterBounds);

	double dLonLat[2];
	Mercator_meters_to_lon_lat(dMeterBounds[0], dMeterBounds[1], dLonLat);
	dBounds[0] = nearest(dLonLat[0], 10000000.0);
	dBounds[1] = nearest(dLonLat[1], 10000000.0);

	Mercator_meters_to_lon_lat(dMeterBounds[2], dMeterBounds[3], dLonLat);
	dBounds[2] = nearest(dLonLat[0], 10000000.0);
	dBounds[3] = nearest(dLonLat[1], 10000000.0);
}


/**
* Fills the given double array with the corresponding longitude and latitude
* in decimal degrees midpoint of the given tile coordinates.
* @param dXt tile x coordinate
* @param dYt tile y coordinate
* @param nZoom map zoom level
* @param dMdpt array to be filled with [midpoint longitude, midpoint latitude]
*/
void Mercator_lon_lat_mdpt(Mercator* pThis, double dXt, double dYt, int nZoom, double* dMdpt)
{
	double dBounds[4];
	Mercator_lon_lat_bounds(pThis, dXt, dYt, nZoom, dBounds);
	*dMdpt++ = (dBounds[0] + dBounds[2]) / 2.0;
	*dMdpt = (dBounds[1] + dBounds[3]) / 2.0;
}


/**
* Fills the given array with the corresponding tile coordinate of the given
* longitude and latitude in decimal degrees for the given map zoom level
* @param dLon longitude in decimal degrees
* @param dLat latitude in decimal degrees
* @param nZoom map zoom level
* @param nTiles array to be filled with [tile x, tile y]
*/
void Mercator_lon_lat_to_tile(Mercator* pThis, double dLon, double dLat, int nZoom, int* nTiles)
{
	double dTemp[2];
	Mercator_lon_lat_to_meters(dLon, dLat, dTemp);
	Mercator_meters_to_pixels(pThis, dTemp[0], dTemp[1], nZoom, dTemp);
	Mercator_pixels_to_tile(pThis, dTemp[0], dTemp[1], nZoom, nTiles);
}


void Mercator_lon_lat_to_tile_pixels(Mercator* pThis, double dLon, double dLat, int nXt, int nYt, int nZoom, int* nPixels)
{
	double dPixels[2];
	Mercator_lon_lat_to_meters(dLon, dLat, dPixels);
	Mercator_meters_to_pixels(pThis, dPixels[0], dPixels[1], nZoom, dPixels);
	*nPixels++ = dPixels[0] - nXt * pThis->m_nTileSize;
	*nPixels = dPixels[1] - nYt * pThis->m_nTileSize;
}


void Mercator_tile_pixels_to_lon_lat(Mercator* pThis, double dPixelX, double dPixelY, int nXt, int nYt, int nZoom, double* dLonLat)
{
	dPixelX += nXt * pThis->m_nTileSize; // pixel x left
	dPixelY += nYt * pThis->m_nTileSize; // pixel y top
	Mercator_pixels_to_meters(pThis, dPixelX, dPixelY, nZoom, dLonLat);
	Mercator_meters_to_lon_lat(dLonLat[0], dLonLat[1], dLonLat);
}


/**
* Fills the given array with the corresponding tile coordinate of the given
* Spherical Mercator meter coordinates for the given map zoom level.
* @param dXm mercator meters x
* @param dYm mercator meters y
* @param nZoom map zoom level
* @param nTiles array to be filled with [tile x, tile y]
*/
void Mercator_meters_to_tile(Mercator* pThis, double dXm, double dYm, int nZoom, int* nTiles)
{
	double dTemp[2];
	Mercator_meters_to_pixels(pThis, dXm, dYm, nZoom, dTemp);
	Mercator_pixels_to_tile(pThis, dTemp[0], dTemp[1], nZoom, nTiles);
}


int Mercator_get_tile_size(Mercator* pThis)
{
	return pThis->m_nTileSize;
}
