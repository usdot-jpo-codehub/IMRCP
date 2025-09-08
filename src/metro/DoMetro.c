#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include <time.h>
#include <jni.h>
#include "macadam.h"
#include <pthread.h>


    /* moon stuff */
    #define SEC_PER_DAY                              (24 * 60 * 60)
    #define DEGREE_TO_RADIAN                  ((2.0 * PI)/ 360.)
    #define J1970   /* 24 */40587.5 /* VAX clock Epoch 1970 Jan 1 (0h UT) */
    #define J2000   /* 24 */51545.0 /* Epoch 2000 Jan 1 (12h UT) */
    /* end moon stuff */

    /* sun stuff */
    #define SUNRISE (double) 0.
    #define SUNSET  (double) 1.
    #define M_PI (double) 3.14159265358979323846
    #define PI (double) M_PI
    #define QUAD1 (double) (PI/2.)
    #define QUAD2 (double) PI
    #define QUAD3 (double) (3.*PI/2.)
    #define QUAD4 (double) (2.*PI)
    #define DEG2RAD (double) (PI/180.)

    #define RISET  (double) -0.0145439  /* cosine  90 degrees 50 minutes */


    pthread_mutex_t time_lock = PTHREAD_MUTEX_INITIALIZER;
    // METRo constants from metro_constant.py (may not all be used)

    const int METRO_MISSING_VALUE = 9999;
    const int SEC_PER_HOUR = 3600;
    double fConsol = 0.1367e4;
    double fTimeStep = 30;
    double nSnowWaterRatio = 10;
    double lCloudsDay[] = {1.0, 0.97, 0.94, 0.89, 0.85, 0.80, 0.71, 0.65, 0.33};
    double lCloudsNightCoeff1[] = {3.79, 4.13, 4.13, 4.26, 4.38, 4.19, 4.395,
                                   4.34, 4.51};
    double lCloudsNightCoeff2[] = {214.7, 226.2, 234.8, 243.4, 250.7,
                                   259.2, 270.9, 280.9, 298.4};
    double nRoadTemperatureMin = -50;  // originally -40 in METRo
    double nRoadTemperatureHigh = 80;
    double nSubSurRoadTmpHigh = 80;
    double nSubSurRoadTmpMin = -40;
    double nAirTempHigh = 50;
    double nAirTempMin = -60;
    double nMaxWindSpeed = 90;
    double nHourForExpirationOfObservation = 48;
    double nGapMinuteObservation = 240;
    double nThreeHours = 3;
    double nLowerPressure = 60000; // changed these values to Pa
    double fNormalPressure = 101325; // 1 atmosphere
    double nUpperPressure = 110000;

    double fConst = 1. / ( 4.0 * 3.6E3 );

    double Eps1 = 0.62194800221014;
    double Eps2 = 0.3780199778986;
    double fTrpl = 273.16;
    double fTcdk = 273.15;

    // end METRo constants

    //-----------------------------------------------------------------------------
    //
    //  Routine: interp()
    //
    //  Description:
    //    Interpolates the data values in 'in' of size 'inSize' to a finer resolution
    //    based on 'increment' and 'outSize'. The interpolation is linear
    //
    //  Inputs:
    //    out ............... the output array where interpolated data will be put in
    //    outSize ........... the size of the output array
    //    in ................ array containing original data points
    //    inSize ............ size of in
    //    increment ......... number subdivisions to make between each a value
    //    flat .............. flag for a flat interpolation, 0 = regular, 1 =
    //
    //-----------------------------------------------------------------------------

    void interp(double* out, int outSize, double *in, int inSize, int increment, int flat)
    {
        int i, j;
        double slope;
        for (i = 0; i < inSize - 1; i++)
        {
            if (flat == 0)
                slope = (double)(in[i +1] - in[i]) / (double)increment;
            else
                slope = 0;
            for(j = 0; j < increment; j++)
            {
                int nIndex = (increment * i) + j;
                out[nIndex] = in[i] + slope * j;
            }
        }
    }

    void interpLong(long* out, int outSize, long *in, int inSize, int increment)
    {
		int i, j;
		for (i = 0; i < inSize - 1; i++)
		{
			for (j = 0; j < increment; j++)
			{
				int nIndex = (increment * i) + j;
				out[nIndex] = in[i];
			}
		}
	}


    // Function used in AH computation
    float sign(float v1, float v2) {return ((v2 >= 0) ? fabs(v1) : -fabs(v1));};

    //-----------------------------------------------------------------------------
    //
    //  Routine: absoluteHumidity()
    //
    //  Description:
    //    Compute absolute humidity. The algorithm comes from the METRo code in
    //    metro_physics.py. Note that the METRO calculation forces the sfcPres
    //    between 900 and 1100 mb, and if outside this range, sets it to the
    //    standard SLP, 1013mb. We are not doing that here.
    //
    //  Inputs:
    //    dewPoint ....... dew point temperature, deg C
    //    sfcPres ........ surface pressure, Pa
    //
    //  Returns:
    //    absolute humidity value
    //
    //-----------------------------------------------------------------------------

    double absoluteHumidity(double dewPoint, double sfcPres)
    {
      double absHum;
      double tdk = dewPoint + fTcdk;
      double dDiff = tdk - fTrpl;
      double vap_pr = 610.78 *
        exp(fmin(sign(17.269,dDiff),sign(21.875,dDiff)) *
            fabs(dDiff)/(tdk-35.86+fmax(0.0,sign(28.2,-dDiff))));

      absHum = Eps1/(fmax(1.0, sfcPres/vap_pr) - Eps2);
      return(absHum);
    }


    //-----------------------------------------------------------------------------
    //
    //  Routine: infraredFlux()
    //
    //  Description:
    //    Compute the IR flux. Algorithm from metro_physics.py.
    //
    //  Inputs:
    //    cloudOctal ....... octal cloud cover (0-8)
    //    airTemp .......... air temp, deg C
    //
    //  Returns:
    //    IR flux, W/m^2
    //
    //-----------------------------------------------------------------------------

    double infraredFlux(int cloudOctal, double airTemp)
    {
      double coeff1, coeff2;

      coeff1 = lCloudsNightCoeff1[cloudOctal];
      coeff2 = lCloudsNightCoeff2[cloudOctal];

      return(coeff1 * airTemp + coeff2);
    }


    //All of the functions and definitons below until solarFlux come from gtime.cc and calc_sun.cc
    //and are used in solarFlux


    /*====================================================*/
    /* riseset
     * input:
     *       quad - quadrants of a circle in radians
     *       doy - day of year
     *       lng - longitude
     * output:
     *       rtasc - something to do with right ascention
     *       estm - approximate rise/set time
     * returns:
     *       some angle having to do with rise or setting
     */
    double riseset( double quad, double doy, double lng, double *rtasc,
                    double * estm )
    {

      double L, q, m, z;
      double n;

      /*      approximate rise-or-set time  */
      *estm = doy + ((quad+lng)/QUAD4);

      /* solar mean anomaly */
      L = *estm * .017202 - .0574039;

      /* solar true longitude  */
      m = L + .0334405 * sin(L) + 3.49066e-04 * sin(L*2.) + 4.93289;

      /* Quadrant Determination  */
      while ( m < 0. )         m += QUAD4;
      while ( m >=QUAD4 ) m -= QUAD4;
      z = m/QUAD1 - (int) (m/QUAD1);
      if ( z == 0. ) m += 4.84814e-06;
      n  = (m > QUAD3) ? 2. : 1.;
      n  = (m > QUAD1) ? n  : 0.;

      /*  solar right ascension  */
      *rtasc = sin(m) / cos(m);
      *rtasc = atan( 0.91746 * (*rtasc) );

      /*  quadrant adjustment  */
      /*if ( n!=0. ) *rtasc = (n==2.) ? *rtasc+=QUAD4 : *rtasc+=QUAD2;*/

      if (n!=0.)
        {
          if (n==2.) {
            *rtasc+=QUAD4;
          } else {
            *rtasc+=QUAD2;
          }
        }

      /*  solar declination  */
      q = .39782 * sin(m);
      q = atan( q / sqrt(1. - q*q) );
      return q;
    }

    /*====================================================*/
    /*      COOrdinate COnversion  */
    /* cooco
    * input:
    *       updn -
    *       code -
    *       evtm - some angle of rise or set
    *       lat - your latitude
    *       lng - your longitude
    *       tmzn - your time zone
    *       rtasc - some ascention angle
    *       estm - estimated time
    * output:
    *       hr - hour of rise or set
    *       min - minute of rise or set
    * returns:
    *       something to do with rise or set
    */
    double cooco(double updn, double code, double evtm, double lat, double lng,
                 double tmzn, double rtasc, double estm, double * hr, double * min ) {

      double  s, t, u, v, w, x, y, z;

      s = code - (sin(evtm)*sin(lat));
      s = s   / (cos(evtm)*cos(lat));

             /*  NULL phenomenon  */
      if ( fabs(s) > 1. )  {
        *hr = 0;
        *min = 0;
        return (0.);
      }

      /*  Adjustment */
      s = s / sqrt(1. - s*s);
      s = QUAD1 - atan(s);
      if ( updn != 1. ) s = QUAD4 - s;

      /*  Local Apparent Time  */
      t = s + rtasc - 0.0172028*estm - 1.73364;

      /*  Universal Time  */
      u = t + lng;

      /*  Wall Clock Time  */
      v = u - tmzn;

      /*  Decimal to SEXAgesimal  */
      z = v;
      while ( z < 0.  )  z += QUAD4;
      while ( z >=QUAD4 )  z -= QUAD4;
      z = z * 3.81972;
      v = (int) z ;
      w = (z - v) * 60.;
      x = (int) w ;
      y = w - x;
      if ( y >= .5 )  x++;
      if ( x >= 60.)  v++, x=0.;
      *hr = v;
      *min= x;
      return  v + x/100.;
    }


    void calc_sun2( float latitude, float longitude, double fc_time, double* sunr_time, double* suns_time)
    {
      double  event, k, z;
      double mm;
      double doy;
      double dd;
      double lat;
      double lng;
      double rtasc;
      double estm;
      double hr;
      double min;
      int tmzn = 0;

      double sunrzH;             /* Rise Hours   Sun     */
      double sunrzM;             /* Rise Minutes Sun     */
      double sunsetH;            /* Set  Hours   Sun     */
      double sunsetM;            /* Set  Minutes Sun     */

      lat = DEG2RAD * latitude;
      lng = DEG2RAD * longitude * -1;

      time_t new_fc_time = (time_t) fc_time;
      struct tm fc_day;

      pthread_mutex_lock(&time_lock); //lock
      struct tm *temp_fc_gtm = gmtime(&new_fc_time);
      memcpy(&fc_day, temp_fc_gtm, sizeof(struct tm));
      pthread_mutex_unlock(&time_lock); //unlock


      doy  = fc_day.tm_year + 1900;
      mm  = fc_day.tm_mon + 1;
      dd  = fc_day.tm_mday;


      fc_day.tm_sec = 0;
      fc_day.tm_min = 0;
      fc_day.tm_hour = 0;
//      fc_day.tm_year = fc_gtm.tm_year;
//      fc_day.tm_mon = fc_gtm.tm_mon;
//      fc_day.tm_mday = fc_gtm.tm_mday;

      *sunr_time = *suns_time = (double)mktime(&fc_day);

      /*  day of year  */
      k = (int) ((mm+9.)/12.);
      z = doy/4. - (int) (doy/4.);
      if ( z != 0. )  k *= 2.;
      doy = (int) (275. * mm/9.);
      doy = doy + dd - k - 30.;

      /*  rising phenomena  */
      event = riseset( QUAD1, doy, lng, &rtasc, &estm);

      cooco( SUNRISE, RISET, event, lat, lng, tmzn, rtasc, estm, &hr, &min );
      sunrzH = hr;
      sunrzM = min;

      /*  setting phenomena  */
      event = riseset(QUAD3, doy, lng, &rtasc, &estm );
      cooco(SUNSET, RISET, event, lat, lng, tmzn, rtasc, estm, &hr, &min );
      sunsetH = hr;
      sunsetM = min;

      /* time fix just because things seem to be running a little behind */
      sunrzM+=1;
      if (sunrzM > 59) {
        sunrzM-=60;
        sunrzH+=1;
      }
      sunsetM+=1;
      if (sunsetM > 59) {
        sunsetM-=60;
        sunsetH+=1;
      }

      /*
      if ( sunrzH > 12 ) sunrzH -= 12;
      if ( sunsetH > 12 ) sunsetH -= 12;
      */

      // printf("sunrise2 %2.0f:%2.0f\n", sunrzH, sunrzM );
      // printf("sunset2 %2.0f:%2.0f\n", sunsetH, sunsetM );

      /* Do we want this in here or does it matter???
      if (sunsetH < sunrzH)
        sunsetH+=24.;
      */

      *sunr_time += (sunrzH*60 + sunrzM)*60;
      *suns_time += (sunsetH*60 + sunsetM)*60;

      return;
    }


    //-----------------------------------------------------------------------------
    //
    //  Routine: solarFlux()
    //
    //  Description:
    //    Compute the solar flux. Algorithm adapted from metro_physics.py.
    //
    //  Inputs:
    //    cloudOctal ....... octal cloud cover (0-8)
    //    fcstStartTime .... start time of forecast, UNIX time in seconds
    //    fcstHour ......... hour of forecast, UNIX time in seconds
    //    lat .............. latitude
    //    lon .............. longitude
    //
    //  Returns:
    //    solar flux, W/m^2
    //
    //-----------------------------------------------------------------------------

    double solarFlux(int cloudOctal, double fcstStartTime, double fcstHour,
                     double lat, double lon)
    {
      //
      // Get sunrise, sunset Unix times. This function is comparable to the METRo
      // third-party Sun.py version, within a minute.
      //
      double sunRise, sunSet;
      calc_sun2(lat, lon, fcstStartTime, &sunRise, &sunSet);


      //
      // For a discernable sunrise/sunset (sunries != sunset), check for night
      // time, in which case return with 0 flux. If sunrise = sunset, there is
      // no rise or set (polar regions) so we allow the code below to figure
      // out the solar flux (if any).
      //
      int nCurrentHour = (((int)fcstHour)/3600)%24;
      if (sunRise != sunSet)
        {
          double sunRiseHour, sunSetHour;
          sunRiseHour = (((int)sunRise)/3600)%24;
          sunSetHour = (((int)sunSet)/3600)%24;

          if ((sunSetHour > sunRiseHour &&
               (nCurrentHour < sunRiseHour || nCurrentHour > sunSetHour)) ||
              (sunRiseHour > sunSetHour &&
               (nCurrentHour > sunSetHour && nCurrentHour < sunRiseHour)))
            return (0.0);
        }

      //
      // Convert time to tm struct, compute fractional day of year
      //
      time_t uTime = (time_t)fcstStartTime;
      pthread_mutex_lock(&time_lock); //lock
      struct tm *tms = gmtime(&uTime);
      double fJulianDate = tms->tm_yday+1 + tms->tm_hour/24.0;
      double fDivide;
      if (tms->tm_year%4 == 0)
        fDivide = 366.0;
      else
        fDivide = 365.0;
      pthread_mutex_unlock(&time_lock); //unlock


      double fA = fJulianDate/fDivide*2*M_PI;

      //
      // Compute maximum theoretical solar flux for this day of year
      // regardless of latitude (accounts for orbital eccentricity)
      //
      double fR0r = 1.0/pow(1.0-9.464e-4*sin(fA)-0.01671*cos(fA)-
                            + 1.489e-4*cos(2.0*fA)-2.917e-5*sin(3.0*fA)-
                            + 3.438e-4*cos(4.0*fA),2) * fConsol;
      //
      // Compute earth-sun angles
      //
      double fRdecl = 0.412*cos((fJulianDate+10.0)*2.0*M_PI/fDivide-M_PI);
      double fDeclsc1 = sin(lat*M_PI/180.0)*sin(fRdecl);
      double fDeclsc2 = cos(lat*M_PI/180.0)*cos(fRdecl);

      //
      // Compute time adjustment (not clear if this is needed or overkill)
      //
      double fEot = 0.002733 -7.343*sin(fA)+ .5519*cos(fA) -9.47*sin(2.0*fA)
        -3.02*cos(2.0*fA) -0.3289*sin(3.*fA) -0.07581*cos(3.0*fA)
        -0.1935*sin(4.0*fA) -0.1245*cos(4.0*fA);

      fEot = fEot/60.0;           // minutes to fraction of hour
      fEot = fEot*15*M_PI/180.0;  // to radians

      //
      // Compute max flux for this time of day
      //
      double fDh =  M_PI*(nCurrentHour/12.0 + lon/180 - 1) + fEot;
      double fCosz = fDeclsc1 + fDeclsc2*cos(fDh);
      double npSft = fmax(0.0, fCosz)*fR0r;

      //
      // This factor appears to reduce the flux, perhaps due to absorption
      // and reflectance.
      //
      double npCoeff = -1.56e-12*pow(npSft,4) + 5.972e-9*pow(npSft,3) -
        8.364e-6*pow(npSft,2)  + 5.183e-3*npSft - 0.435;

      //
      // Compute and return final solar flux
      //
      return(fmax(0.0, npSft * npCoeff * lCloudsDay[cloudOctal]));

    }

    /**
     *  This function calls the METRo Heat Balance Model from macadam.c. It was
     *  adapted from NCAR metro_direct and the python source code for METRo.
     *  Most of the error checking is done in the java code of DoMetroWrapper,
     *  since that is were all of the data is created.
     *
     *  bBridge  is the road a bridge? 0 = road, 1 = bridge
     *  dMLat     latitude in decimal degrees
     *  dMLon     longitude in decimal degrees
     *  nObservationHrs  the number of observation hours
     *  nForecastHrs     the number of forecast hours
     *  dObsRoadTemp     array containing the observed road temperatures (Celsius)
     *  dObsSubSurfTemp  array containing the observed sub surface temperatures (Celsius)
     *  dObsAirTemp      array containing the observed air temperatures (Celsius)
     *  dObsDewPoint     array containing the observed dew points (Celsius)
     *  dObsWindSpeed    array containing the observed wind speeds (km/h)
     *  dObsTime         array containing the Hour of Day of each observation
     *  dFTime		 array containing the Hour of Day of each forecast
     *  dFTimeSeconds    array containing the Unix Time in seconds of each forecast
     *  dAirTemp         array containing the forecasted air temperatures (Celsius)
     *  dDewPoint        array containing the forecasted dew points (Celsius)
     *  dWindSpeed	 array containing the forecasted wind speeds (km/h)
     *  dSfcPres         array containing the forecasted surface pressure (Pa)  (METRo documentation says the units is mb but when I printed out the data that was input into METRo from Environment Canada's python code it was in Pa)
     *  dPrecipAmt       array containing the forecasted precipitation amounts (mm)
     *  lPrecipType      array containing the forecasted precipitation types (0 = none, 1 = rain, 2 = snow)
     *  lRoadCond	 array containing the observed road condition (we initialize the 1st based off of precipitation and air temp. after that we use the METRo results as the input, the values are listed below)
     *  dCloudCover      array containing the forecasted cloud covers (the value is "octal" being from 0-8)
     *  lOutRoadCond     array containing the data output from METRo for the road condition (1 = dry road, 2 = wet road, 3 = ice/snow on the road, 4 = mix water/snow on the road, 5 = dew, 6 = melting snow, 7 = frost, 8 = icing rain)
     *  dOutRoadTemp     array containing the data output from METRo for the road temperature (Celsius)
     *  dOutSubSurfTemp  array containing the data output from METRo for the sub surface temperature (Celsius)
     *  nTmtType         code for the type of treatment being put on road, need documentation for the codes.
     *
     */
//    JNIEXPORT void JNICALL Java_javametro_Program_doMetroWrapper
    JNIEXPORT void JNICALL Java_imrcp_forecast_mdss_DoMetroWrapper_doMetroWrapper
      (JNIEnv *env, jobject thisObj, jint bBridge, jdouble dMLat, jdouble dMLon,
            jint nObservationHrs, jint nForecastHrs,
            jdoubleArray dObsRoadTemp, jdoubleArray dObsSubSurfTemp,
            jdoubleArray dObsAirTemp, jdoubleArray dObsDewPoint, jdoubleArray dObsWindSpeed,
            jdoubleArray dObsTime, jdoubleArray dFTime, jdoubleArray dFTimeSeconds, jdoubleArray dAirTemp, jdoubleArray dDewPoint,
            jdoubleArray dWindSpeed, jdoubleArray dSfcPres, jdoubleArray dPrecipAmt,
            jlongArray lPrecipType, jlongArray lRoadCond, jdoubleArray dCloudCover,
            jlongArray lOutRoadCond, jdoubleArray dOutRoadTemp, jdoubleArray dOutSubSurfTemp, jdoubleArray dOutSnowIceAcc,
            jdoubleArray dOutLiquidAcc, jint nTmtType, jdouble dRainReservoir, jdouble dSnowReservoir, jdouble dRainCutoff, jdouble dSnowCutoff)
    {
        //convert java arrays into c arrays
        jdouble *cdObsRoadTemp = (*env)->GetDoubleArrayElements(env, dObsRoadTemp, NULL);
        jdouble *cdObsSubSurfTemp = (*env)->GetDoubleArrayElements(env, dObsSubSurfTemp, NULL);
        jdouble *cdObsAirTemp = (*env)->GetDoubleArrayElements(env, dObsAirTemp, NULL);
        jdouble *cdObsDewPoint = (*env)->GetDoubleArrayElements(env, dObsDewPoint, NULL);
        jdouble *cdObsWindSpeed = (*env)->GetDoubleArrayElements(env, dObsWindSpeed, NULL);
        jdouble *cdObsTime = (*env)->GetDoubleArrayElements(env, dObsTime, NULL);
        jdouble *cdFTime = (*env)->GetDoubleArrayElements(env, dFTime, NULL);
        jdouble *cdFTimeSeconds = (*env)->GetDoubleArrayElements(env, dFTimeSeconds, NULL);
        jdouble *cdAirTemp = (*env)->GetDoubleArrayElements(env, dAirTemp, NULL);
        jdouble *cdDewPoint = (*env)->GetDoubleArrayElements(env, dDewPoint, NULL);
        jdouble *cdWindSpeed = (*env)->GetDoubleArrayElements(env, dWindSpeed, NULL);
        jdouble *cdSfcPres = (*env)->GetDoubleArrayElements(env, dSfcPres, NULL);
        jdouble *cdPrecipAmt = (*env)->GetDoubleArrayElements(env, dPrecipAmt, NULL);
        jlong *clPrecipType = (*env)->GetLongArrayElements(env, lPrecipType, NULL);
        jlong *clRoadCond = (*env)->GetLongArrayElements(env, lRoadCond, NULL);
        jdouble *cdCloudCover = (*env)->GetDoubleArrayElements(env, dCloudCover, NULL);
        jlong *clOutRoadCond = (*env)->GetLongArrayElements(env, lOutRoadCond, NULL);
        jdouble *cdOutRoadTemp = (*env)->GetDoubleArrayElements(env, dOutRoadTemp, NULL);
        jdouble *cdOutSubSurfTemp = (*env)->GetDoubleArrayElements(env, dOutSubSurfTemp, NULL);
        jdouble *cdOutSnowIceAcc = (*env)->GetDoubleArrayElements(env, dOutSnowIceAcc, NULL);
        jdouble *cdOutLiquidAcc = (*env)->GetDoubleArrayElements(env, dOutLiquidAcc, NULL);

	int metroTimesPerHour = SEC_PER_HOUR/fTimeStep;  //30 second time steps
        int i;

        //set bridge flag
        BOOL bFlat = bBridge;

        // Set up subsurface materials and depths (need to reverse order). For our purposes assuming everything is .5m of asphalt.
        long nNbrOfZone = 1;
        int extra_layer = 1; // Do_Metro() adds a sand layer at the bottom for roads
        if (bFlat)
            extra_layer = 0;
        double dZones[nNbrOfZone+extra_layer];
        long nMateriau[nNbrOfZone+extra_layer];
        //assuming all roads are .5 meters of asphalt, METRo code adds another layer of sand at the bottom
        dZones[0] = .5;
        nMateriau[0] = 1;

        BOOL bNoObs[4];
        bNoObs[0] = 0;    //set to 1 if first observation is at or after the first forecast time
        bNoObs[1] = 0;    //set to 1 if there is less than 3 hours of overlap between the observations and forecasts
        bNoObs[2] = 0;    //NCAR documentation says setting this to 1 returns an error so don't set it
        bNoObs[3] = 0;    //set to 1 if there is only one valid observation

        long nLenObservation = (nObservationHrs - 1) * metroTimesPerHour; //this is the size of the observation arrays after they are interpolated to data points every 30 seconds

        //create the arrays that will contain the interpolated values for observations
        double dTimeO[nLenObservation];
        double dTRO[nLenObservation];
        double dDTO[nLenObservation];
        double dTAO[nLenObservation];
        double dTDO[nLenObservation];
        double dFFO[nLenObservation];
        long lRC[nLenObservation];
        int j;
        for (i = 0; i < nObservationHrs; i++)
            dTimeO[i * metroTimesPerHour] = cdObsTime[i];
        for (i = 0; i < nObservationHrs - 1; i++)
            for (j = 1; j < metroTimesPerHour; j ++)
                dTimeO[j + (i * metroTimesPerHour)] = dTimeO[(j + (i * metroTimesPerHour)) - 1] + (1.0/120.0);

        //interpolate all of the observation data.
        interp(dTRO, nLenObservation, cdObsRoadTemp, nObservationHrs, metroTimesPerHour, 0);
        interp(dDTO, nLenObservation, cdObsSubSurfTemp, nObservationHrs, metroTimesPerHour, 0);
        interp(dTAO, nLenObservation, cdObsAirTemp, nObservationHrs, metroTimesPerHour, 0);
        interp(dTDO, nLenObservation, cdObsDewPoint, nObservationHrs, metroTimesPerHour, 0);
        interp(dFFO, nLenObservation, cdObsWindSpeed, nObservationHrs, metroTimesPerHour, 0);
        interpLong(lRC, nLenObservation, clRoadCond, nObservationHrs, metroTimesPerHour);


        long nSwo[46080]; //have to use 46080 which is the max size in the fortran METRo code
        for (i = 0; i < nLenObservation; i++)
        {
            // Initialize to pass. do not have to check to set them to zero because error checking was done in java code
            nSwo[i] = 1;
        }
        for (; i < 46080; i++) //set the rest to zero. this is consistent with what the input from python code looked like
            nSwo[i] = 0;

        long nNbrTimeSteps = (nForecastHrs - 1) * metroTimesPerHour;  //set the size for the forecast and output arrays


        //calculate the solar and infrared flux
        double swFlux[nForecastHrs];
        double lwFlux[nForecastHrs];
        for (i = 0; i < nForecastHrs; i++)
        {
            swFlux[i] = solarFlux((int) cdCloudCover[i], cdFTimeSeconds[0], cdFTimeSeconds[i], dMLat, dMLon);
            lwFlux[i] = infraredFlux((int) cdCloudCover[i], cdAirTemp[i]);
        }

        //create the arrays that will contain the interpolated values for forecasts
        double dTA[nNbrTimeSteps];
        double dTD[nNbrTimeSteps];
        double dPS[nNbrTimeSteps];
        double dFF[nNbrTimeSteps];
        double dFS[nNbrTimeSteps];
        double dFI[nNbrTimeSteps];
        double dQP[nNbrTimeSteps];
        long lTYP[nNbrTimeSteps];

        //interpolate all of the forecast data
        interp(dTA, nNbrTimeSteps, cdAirTemp, nForecastHrs, metroTimesPerHour, 0);
        interp(dTD, nNbrTimeSteps, cdDewPoint, nForecastHrs, metroTimesPerHour, 0);
        interp(dPS, nNbrTimeSteps, cdSfcPres, nForecastHrs, metroTimesPerHour, 0);
        interp(dFF, nNbrTimeSteps, cdWindSpeed, nForecastHrs, metroTimesPerHour, 0);
        interp(dFS, nNbrTimeSteps, swFlux, nForecastHrs, metroTimesPerHour, 0);
        interp(dFI, nNbrTimeSteps, lwFlux, nForecastHrs, metroTimesPerHour, 0);
//        interp(dQP, nNbrTimeSteps, cdPrecipAmt, nForecastHrs, metroTimesPerHour, 0);
	for (i = 0; i < nNbrTimeSteps; i++)
		dQP[i] = cdPrecipAmt[i];
	for (i = 0; i < nNbrTimeSteps; i++)
		lTYP[i] = clPrecipType[i];
//        interpLong(lTYP, nNbrTimeSteps, clPrecipType, nForecastHrs, metroTimesPerHour);
/*	for (int n = 0; n < nNbrTimeSteps; n++)
		printf("%2.9f\n", dQP[n]);
*/
        //create an array for and calculate absolute humidity
        double dAH[nNbrTimeSteps];
        for (i = 0; i < nNbrTimeSteps; i++)
        {
            if (dTD[i] > dTA[i])
                dTD[i] = dTA[i];

            dAH[i] = absoluteHumidity(dTD[i], dPS[i]);
        }

        //calculate deltaT (difference of the first forecast time and the first observation time)
        double dDeltaT = cdFTime[0] - cdObsTime[0];
        if (dDeltaT <= 0)
        {
            dDeltaT = dDeltaT + 24;
        }

            //bNoObs[0] = 1;

        if (cdFTime[0] - cdObsTime[nObservationHrs - 1] > -3) //if there is less than a 3 hour overlap do not do the coupling
            bNoObs[1] = 1;

        BOOL bSilent = 1; //set the debug variable 1 = no debugging messages are printed

        double dSstDepth = .4; //40 cm is the sub surface temperature depth
        BOOL bDeepTemp = 0;    //we do not give the bottom temperature layer as an input
        double dDeepTemp = 0;  // since bDeepTemp is always false, this value is never used

        //create and calculate the anthropogenic flux (in all of the testing of METRo using python these values were always 10 so we set them to 10 here)
        double dFA[nNbrTimeSteps];
        for (i = 0; i < nNbrTimeSteps; i++)
            dFA[i] = 10;

        //Run the METRo Heat Balance Model
        Do_Metro(bFlat, dMLat, dMLon, dZones, nNbrOfZone, nMateriau, dTA,
               dQP, dFF, dPS, dFS, dFI, dFA, lTYP, lRC, dTAO, dTRO, dDTO,
               dAH, dTimeO, nSwo, bNoObs, dDeltaT, nLenObservation,
               nNbrTimeSteps, bSilent, dSstDepth, bDeepTemp, dDeepTemp,
               clOutRoadCond, cdOutRoadTemp, cdOutSubSurfTemp, cdOutSnowIceAcc,
               cdOutLiquidAcc, nTmtType, dRainReservoir, dSnowReservoir, dRainCutoff, dSnowCutoff);
//        int a;
//        for (a = 0; a < nNbrTimeSteps; a++)
//        {
//            if (cdOutSubSurfTemp[a] < 10)
//            {
//
//                FILE *fp;
//                char file[100];
//                sprintf(file, "/home/cherneya/MetroLog/log%f,%f", dMLat, dMLon);
//                fp = fopen(file, "w");
//
//                fprintf(fp, "bFlat: %d\n", bFlat);
//                fprintf(fp, "dMLat: %5.2f\n", dMLat);
//                fprintf(fp, "dMLon: %5.2f\n", dMLon);
//                fprintf(fp, "dZones[0]: %5.2f\n", dZones[0]);
//                fprintf(fp, "nNbrOfZone: %d\n", nNbrOfZone);
//                fprintf(fp, "nMateriau[0]: %d\n", nMateriau[0]);
//
//                fprintf(fp, "dTA:\n");
//                for (i = 0; i < nNbrTimeSteps; i++)
//                    fprintf(fp, "%5.2f ", dTA[i]);
//                fprintf(fp, "\n");
//                fprintf(fp, "dQP:\n");
//                for (i = 0; i < nNbrTimeSteps; i++)
//                    fprintf(fp, "%5.2f ", dQP[i]);
//                fprintf(fp, "\n");
//                fprintf(fp, "dFF:\n");
//                for (i = 0; i < nNbrTimeSteps; i++)
//                    fprintf(fp, "%5.2f ", dFF[i]);
//                fprintf(fp, "\n");
//                fprintf(fp, "dPS:\n");
//                for (i = 0; i < nNbrTimeSteps; i++)
//                    fprintf(fp, "%5.2f ", dPS[i]);
//                fprintf(fp, "\n");
//                fprintf(fp, "dFS:\n");
//                for (i = 0; i < nNbrTimeSteps; i++)
//                    fprintf(fp, "%5.2f ", dFS[i]);
//                fprintf(fp, "\n");
//                fprintf(fp, "dFI:\n");
//                for (i = 0; i < nNbrTimeSteps; i++)
//                    fprintf(fp, "%5.2f ", dFI[i]);
//                fprintf(fp, "\n");
//                fprintf(fp, "dFA:\n");
//                for (i = 0; i < nNbrTimeSteps; i++)
//                    fprintf(fp, "%5.2f ", dFA[i]);
//                fprintf(fp, "\n");
//                fprintf(fp, "dTYP:\n");
//                for (i = 0; i < nNbrTimeSteps; i++)
//                    fprintf(fp, "%5.2f ", dTYP[i]);
//                fprintf(fp, "\n");
//                fprintf(fp, "dRC:\n");
//                for (i = 0; i < nNbrTimeSteps; i++)
//                    fprintf(fp, "%5.2f ", dRC[i]);
//                fprintf(fp, "\n");
//                fprintf(fp, "dTAO:\n");
//                for (i = 0; i < nNbrTimeSteps; i++)
//                    fprintf(fp, "%5.2f ", dTAO[i]);
//                fprintf(fp, "\n");
//                fprintf(fp, "dTRO:\n");
//                for (i = 0; i < nNbrTimeSteps; i++)
//                    fprintf(fp, "%5.2f ", dTRO[i]);
//                fprintf(fp, "\n");
//                fprintf(fp, "dDTO:\n");
//                for (i = 0; i < nNbrTimeSteps; i++)
//                    fprintf(fp, "%5.2f ", dDTO[i]);
//                fprintf(fp, "\n");
//                fprintf(fp, "dAH:\n");
//                for (i = 0; i < nNbrTimeSteps; i++)
//                    fprintf(fp, "%5.2f ", dAH[i]);
//                fprintf(fp, "\n");
//                fprintf(fp, "dTimeO: ");
//                for (i = 0; i < nNbrTimeSteps; i++)
//                  fprintf(fp, "%5.2f ", dTimeO[i]);
//                fprintf(fp, "\n");
//                fprintf(fp, "nSwo:\n");
//                for (i = 0; i < nNbrTimeSteps; i++)
//                    fprintf(fp, "%d ", nSwo[i]);
//                fprintf(fp, "\n");
//                fprintf(fp, "bNoObs0: %d\n", bNoObs[0]);
//                fprintf(fp, "bNoObs1: %d\n", bNoObs[1]);
//                fprintf(fp, "bNoObs2: %d\n", bNoObs[2]);
//                fprintf(fp, "bNoObs3: %d\n", bNoObs[3]);
//                fprintf(fp, "dDeltaT: %5.2f\n", dDeltaT);
//                fprintf(fp, "nLenObservation: %d\n", nLenObservation);
//                fprintf(fp, "nNbrTimeSteps: %d\n", nNbrTimeSteps);
//                fprintf(fp, "bSilent: %d\n", bSilent);
//                fprintf(fp, "dSstDepth: %5.2f\n", dSstDepth);
//                fprintf(fp, "bDeepTemp: %d\n", bDeepTemp);
//                fprintf(fp, "dDeepTemp: %5.2f\n", dDeepTemp);
//                fprintf(fp, "clOutRoadCond:\n");
//                for (i = 0; i < nNbrTimeSteps; i++)
//                    fprintf(fp, "%d ", clOutRoadCond[i]);
//                fprintf(fp, "\n");
//                fprintf(fp, "cdOutRoadTemp:\n");
//                for (i = 0; i < nNbrTimeSteps; i++)
//                    fprintf(fp, "%5.2f ", cdOutRoadTemp[i]);
//                fprintf(fp, "\n");
//                fprintf(fp, "cdOutSubSurfTemp:\n");
//                for (i = 0; i < nNbrTimeSteps; i++)
//                    fprintf(fp, "%5.2f ", cdOutSubSurfTemp[i]);
//                fprintf(fp, "\n");
//
//                fclose(fp);
//                break;
//            }
//        }

        //free all of the memory
        (*env)->ReleaseDoubleArrayElements(env, dObsRoadTemp, cdObsRoadTemp, 0);
        (*env)->ReleaseDoubleArrayElements(env, dObsSubSurfTemp, cdObsSubSurfTemp, 0);
        (*env)->ReleaseDoubleArrayElements(env, dObsAirTemp, cdObsAirTemp, 0);
        (*env)->ReleaseDoubleArrayElements(env, dObsDewPoint, cdObsDewPoint, 0);
        (*env)->ReleaseDoubleArrayElements(env, dObsWindSpeed, cdObsWindSpeed, 0);
        (*env)->ReleaseDoubleArrayElements(env, dObsTime, cdObsTime, 0);
        (*env)->ReleaseDoubleArrayElements(env, dFTime, cdFTime, 0);
        (*env)->ReleaseDoubleArrayElements(env, dFTimeSeconds, cdFTimeSeconds, 0);
        (*env)->ReleaseDoubleArrayElements(env, dAirTemp, cdAirTemp, 0);
        (*env)->ReleaseDoubleArrayElements(env, dDewPoint, cdDewPoint, 0);
        (*env)->ReleaseDoubleArrayElements(env, dWindSpeed, cdWindSpeed, 0);
        (*env)->ReleaseDoubleArrayElements(env, dSfcPres, cdSfcPres, 0);
        (*env)->ReleaseDoubleArrayElements(env, dPrecipAmt, cdPrecipAmt, 0);
        (*env)->ReleaseLongArrayElements(env, lPrecipType, clPrecipType, 0);
        (*env)->ReleaseLongArrayElements(env, lRoadCond, clRoadCond, 0);
        (*env)->ReleaseDoubleArrayElements(env, dCloudCover, cdCloudCover, 0);
        (*env)->ReleaseLongArrayElements(env, lOutRoadCond, clOutRoadCond, 0);
        (*env)->ReleaseDoubleArrayElements(env, dOutRoadTemp, cdOutRoadTemp, 0);
        (*env)->ReleaseDoubleArrayElements(env, dOutSubSurfTemp, cdOutSubSurfTemp, 0);
        (*env)->ReleaseDoubleArrayElements(env, dOutSnowIceAcc, cdOutSnowIceAcc, 0);
        (*env)->ReleaseDoubleArrayElements(env, dOutLiquidAcc, cdOutLiquidAcc, 0);
    }
