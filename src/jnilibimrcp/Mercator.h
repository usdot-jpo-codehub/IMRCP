#ifndef _Mercator
#define _Mercator


typedef struct Mercator_t
{
	int m_nTileSize;
	double m_dRes[24];
	double m_dRes2[24];
}
Mercator;


Mercator* Mercator_new();
Mercator* Mercator_new_size(int);
void Mercator_lon_lat_to_meters(double, double, double*);
void Mercator_meters_to_lon_lat(double, double, double*);
int Mercator_get_extent(Mercator*, int);
void Mercator_pixels_to_meters(Mercator*, double, double, int, double*);
void Mercator_meters_to_pixels(Mercator*, double, double, int, double*);
void Mercator_tile_bounds(Mercator*, double, double, int, double*);
void Mercator_lon_lat_bounds(Mercator*, double, double, int, double*);
void Mercator_lon_lat_mdpt(Mercator*, double, double, int, double*);
void Mercator_lon_lat_to_tile(Mercator*, double, double, int, int*);
void Mercator_meters_to_tile(Mercator*, double, double, int, int*);
void Mercator_lon_lat_to_tile_pixels(Mercator*, double, double, int, int, int, int*);


#endif
