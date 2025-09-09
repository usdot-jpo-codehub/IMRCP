/*************************************************************************
* METRo : Model of the Environment and Temperature of Roads
* METRo is Free and is proudly provided by the Government of Canada
* Copyright (C) Her Majesty The Queen in Right of Canada, Environment Canada, 2006

*  Questions or bugs report: metro@ec.gc.ca
*  METRo repository: https://framagit.org/metroprojects/metro
*  Documentation: https://framagit.org/metroprojects/metro/wikis/home
*
*
* Code contributed by:
*  Miguel Tremblay - Canadian meteorological center
*
*  $LastChangedDate$
*  $LastChangedRevision$
*
************************************************************************
*  This program is free software; you can redistribute it and/or modify
*  it under the terms of the GNU General Public License as published by
*  the Free Software Foundation; either version 2 of the License, or
*  (at your option) any later version.
*
*  This program is distributed in the hope that it will be useful,
*  but WITHOUT ANY WARRANTY; without even the implied warranty of
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*  GNU General Public License for more details.
*
*  You should have received a copy of the GNU General Public License
*  along with this program; if not, write to the Free Software
*  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*
*
*****************************************************************************/

/***************************************************************************
**
** Nom:         macadam.c
**
** Auteur:      Miguel Tremblay
**
** Date:        April 16, 2004
**
** Description: File that handled the METRo model.
**  All the fortran routines must be called in this file. The modele
**  sequence is here.
**
**
***************************************************************************/

#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include "macadam.h"
#define f77name(x) x##_


/* Constants for the string width for the compatibility with the fortran code. */

#define nNBROFSTRING 50
#define nNAMELENGTH 100
#define nNAMELENGTHLONG 150
#define nNBRARGS 27



/****************************************************************************
 Name: Do_Metro

 Parameters:
[I BOOL bFlat : road (FALSE) or bridge (TRUE)]
[I double dMLat : Latitude of the RWIS station]
[I double dMLong : Longitude of the RWIS station]
[I double* dpZones : Depth in meter of each layer of the road]
[I long nNbrOfZone : Number of layers in the road]
[I long* npMateriau : code indicating the composition of the road:
   see https://framagit.org/metroprojects/metro/wikis/Layer_type_(METRo)]
[I double* dpTA : interpolated air temperature]
[I double* dpQP : interpolated quantity of precipitation]
[I double* dpFF : interpolated wind velocity]
[I double* dpPS : interpolated surface pressure]
[I double* dpFS : interpolated solar flux]
[I double* dpFI : interpolated visible flux]
[I double* dpFA : interpolated anthropogenic flux]
[I double* TYP : Type of precipitation: 0 = nada, 1= liquid, 2=solid]
[I long* npRC : interpolated road condition. 0 = dry, 1=wet]
[I double dpTAO : interpolated observed air temperature]
[I double* dpRTO : interpolated observed road temperature]
[I double* dpDTO : interpolated observed deep road temperature]
[I double* dpTimeO : steps of 30 seconds for the observation]
[I long* npSWO1 : Boolean field to check if the deep road temperature
    passed the QA/QC]
[I long* npSWO2 : Boolean field to check if the air temperature
    passed the QA/QC]
[I long* npSWO3 : Boolean field to check if the dew point
    passed the QA/QC]
[I long* npSWO4 : Boolean field to check if the wind speed
    passed the QA/QC]
[I BOOL* bpNoObs : boolean field to tell the number of observation used]
[I double dDeltaT : Time difference, in hours, between the first observation
  and the start of METRo.]
[I long nLenObservation : Number of valid observations.  30 seconds steps.]
[I long nNbrTimeSteps : number of 30 seconds steps in the forecast]
[I double dSstDepth : SST sensor depth from station file]
[I BOOL* bDeepTemp : is the bottom temperature layer given as input?]
[I double* dDeepTemp : temperature of the bottom layer if bDeepTemp == TRUE]
[I long* lpOutRC : output array of Road Condition]
[I double* dpOutRT: output array of Road Temperature]
[I double* dpOutSST : output array of Sub Surface Temperature]

Returns: None

Functions Called:
Those are fortran functions called with f77name.  Only the function
balanc and grille should remains here at the release of macadam.
grille : Grid creation for the model.
makitp : Creation of an analytic temperature for the temperature of the road.
initial : Initilization of the initial temperature profile
coupla : Coupling
balanc : Forecast

<function description>
Description:
This is part of the module "metro_model.py".  This C function make the forecast
 for the METRo software

Notes:

Revision History:

Author		Date		Reason
Miguel Tremblay  May 2004

***************************************************************************/

void Do_Metro( BOOL bFlat, double dMLat, double dMLon, double* dpZones,\
	       long nNbrOfZone,  long* npMateriau, double* dpTA, double* dpQP,\
	       double* dpFF,  double* dpPS, double* dpFS, double* dpFI, \
	       double* dpFA, long* npTYP, long* npRC, double* dpTAO, \
	       double* dpRTO, double* dpDTO, double* dpAH, double* dpTimeO,\
	       long* npSwo, BOOL* bpNoObs, double dDeltaT,\
	       long nLenObservation, long nNbrTimeSteps, BOOL bSilent,\
	       double dSstDepth, BOOL bDeepTemp, double dDeepTemp, long* lpOutRC,
               double* dpOutRT, double* dpOutSST, double* dpOutSN,
               double* dpOutRA, long TMTTYP, double dRainReservoir, double dSnowReservoir, double dRainCutoff, double dSnowCutoff)

  /* Argument de la ligne de commande. Donne par python  */

 /**     A "O" at the end of a variable name
  **     indicates it comes from the local observation
  **
  **     Ex: TA, TAO (observations)
  **
  **     TA : air temperature
  **     TD : dew point temperature
  **     VA : wind speed
  **     DD : wind direction
  **     FS : solar flux
  **     FI : infra-red flux
  **     FA : anthropogenic flux
  **     AC : accumulations
  **     TYP: precipitation type
  **     P0 : surface pressure
  **     GMT: Time GMT
  **     DT : Subsurface temperature
  ******/
{
    struct doubleStruct stRA; /* Liquid accumlation */
    struct doubleStruct stSN; /* Snow/ice accumulation */
    struct longStruct   stRC; /* Road condition */
    struct doubleStruct stRT; /* Road temperature */
    struct doubleStruct stIR; /* Infra-red flux */
    struct doubleStruct stSF; /* Solar flux */
    struct doubleStruct stFV; /* Vapor flux */
    struct doubleStruct stFC; /* Sensible heat */
    struct doubleStruct stG;  /* Ground flux */
    struct doubleStruct stBB; /* Black body radiation */
    struct doubleStruct stFP; /* Phase change energy */
    struct longStruct   stEc; /* Boolean to know if the execution was a success */
    struct doubleStruct stSST; /* Subsurface temperature */
    struct doubleStruct stTemperatureDepth;  /* Depth of temperature grid levels */
    struct doubleStruct stLT; /* Level temperature */

    stRC.nSize = nNbrTimeSteps;
    stRA.nSize = nNbrTimeSteps;
    stRT.nSize = nNbrTimeSteps;
    stIR.nSize = nNbrTimeSteps;
    stSF.nSize = nNbrTimeSteps;
    stSN.nSize = nNbrTimeSteps;
    stFV.nSize = nNbrTimeSteps;
    stFC.nSize = nNbrTimeSteps;
    stG.nSize = nNbrTimeSteps;
    stBB.nSize = nNbrTimeSteps;
    stFP.nSize = nNbrTimeSteps;
    stEc.nSize = 1;
    stSST.nSize = nNbrTimeSteps;
    stTemperatureDepth.nSize = 0; /* Will be computed later */
    stLT.nSize = nNbrTimeSteps*nNGRILLEMAX;
    /* Memory alloc */
    stRC.plArray = (long*)calloc((nNbrTimeSteps),sizeof(long));
    stRA.pdArray = (double*)calloc((nNbrTimeSteps),sizeof(double));
    stIR.pdArray = (double*)calloc((nNbrTimeSteps),sizeof(double));
    stSF.pdArray = (double*)calloc((nNbrTimeSteps),sizeof(double));
    stRT.pdArray = (double*)calloc((nNbrTimeSteps),sizeof(double));
    stSN.pdArray = (double*)calloc((nNbrTimeSteps),sizeof(double));
    stFV.pdArray = (double*)calloc((nNbrTimeSteps),sizeof(double));
    stFC.pdArray = (double*)calloc((nNbrTimeSteps),sizeof(double));
    stG.pdArray = (double*)calloc((nNbrTimeSteps),sizeof(double));
    stBB.pdArray = (double*)calloc((nNbrTimeSteps),sizeof(double));
    stFP.pdArray = (double*)calloc((nNbrTimeSteps),sizeof(double));
    stEc.plArray = (long*)calloc((1),sizeof(long));
    stSST.pdArray = (double*)calloc((nNbrTimeSteps),sizeof(double));
    stTemperatureDepth.pdArray =  (double*)calloc((nNbrTimeSteps),sizeof(double));
    stLT.pdArray = (double*)calloc((nNbrTimeSteps*nNGRILLEMAX),sizeof(double));
  BOOL bFail = FALSE;
  long nNtp;
  long nNtp2;
  long nNtdcl;
  double* dpItp;
  double dDiff;
  double dAln = 0.5;
  double dAlr = 0.1;
  double dEpsilon = 0.92;
  double dZ0 = 0.001;
  double dZ0t = 0.0005;
  double dZt = 1.5;
  double dZu = 10;
  double dFCorr;
  double dFsCorr=0;
  double dFiCorr=0;
    double dEr1=dRainReservoir;
    double dEr2=dSnowReservoir;
  double dFp=0.0;
  /* Grid values */
  long nIR40;
  double* dpCnt;
  double* dpCapacity;
  double* dpConductivity;
  long nDeltaTIndice=0;
  long nOne = 1;

    memcpy(stIR.pdArray, dpFI, nNbrTimeSteps * sizeof(double));

  /* double */
  dpItp = (double*)malloc((nNGRILLEMAX)*sizeof(double));
  dpCnt = (double*)calloc((2*nNGRILLEMAX),sizeof(double));
  dpCapacity  = (double*)calloc((2*nNGRILLEMAX),sizeof(double));
  dpConductivity = (double*)calloc((2*nNGRILLEMAX),sizeof(double));

  /* Initilization of physical constants in the fortran code */
  f77name(setconstphys)(&bSilent);

  /******************************* Station ********************************/

  *stEc.plArray = FALSE;
  dFCorr = 2.0*dOMEGA*sin(dPI*dMLat/180.0);

  if(!bFlat){
    /* Note: In the case of a 'road', a 20 sand meters layer is added at the bottom.*/
    dpZones[nNbrOfZone] = 20.0;
    npMateriau[nNbrOfZone]= 4;
    nNbrOfZone = nNbrOfZone +1;
  }


  /* Grid creation */

  f77name(grille)(&(stTemperatureDepth.nSize), &nIR40, &bFlat, &nNbrOfZone, \
		  dpZones, npMateriau, &dDiff, stTemperatureDepth.pdArray, \
		  stEc.plArray, dpCapacity, dpConductivity, &dSstDepth);
  if(*(stEc.plArray)){
    goto liberation;
  }

  /* Extraction of observations */
  /*  Those -1 is because it is use in fortran */
  nDeltaTIndice = (dDeltaT)*3600/30.-1;
  nLenObservation = nLenObservation -1;


  /***********************************************************************/
  /*   Coupling is different if there is more or less than 3 hours.     */
  /***********************************************************************/

    if(bpNoObs[1]){   //This is the branch that always gets ran for us because we never have an overlap of 3 hours between the forecast and observations
    /* less than 3 hours of observation in the coupling */
    if(!bSilent){
      printf("Less than 3 hours of overlap between the\n");
      printf("forecast and the road observations. Not enough ");
      printf("data for coupling.\n");
    }
    f77name(makitp)(dpItp, &stTemperatureDepth.nSize, &nIR40, &bFlat, &(dpTimeO[0]),\
		    &(dpRTO[0]), &(dpDTO[0]), &(dpTAO[0]), &dDiff, \
		    &dMLon, npSwo, stTemperatureDepth.pdArray, &bDeepTemp, &dDeepTemp);
    f77name(initial)(dpItp , (dpRTO+1), (dpDTO+1), (dpTAO+1), &nOne,	\
		     &nLenObservation, &stTemperatureDepth.nSize, &nIR40,\
		     &bFlat, npSwo, dpCapacity, dpConductivity);
    nNtp2 = nLenObservation - nDeltaTIndice;
  }
  else if(bpNoObs[0]){
    if(!bSilent)
      printf(" Not enough data for initialization.\n");
    nNtdcl  = nLenObservation - ((nLenObservation < 28800.0/dDT)\
				 ? nLenObservation : 28800.0/dDT);
    /* Patch because nNtdcl does not take the value 0 in fortran!*/
    if(nNtdcl == 0)
      nNtdcl =1;
    f77name(makitp)(dpItp, &stTemperatureDepth.nSize, &nIR40, &bFlat, &(dpTimeO[nNtdcl]),\
		    &(dpRTO[nNtdcl]), &(dpDTO[nNtdcl]), &(dpTAO[nNtdcl]),\
		    &dDiff, &dMLon, npSwo, stTemperatureDepth.pdArray, \
		    &bDeepTemp, &dDeepTemp);
    nNtp = - nDeltaTIndice + nNtdcl;
    nNtp2 = nLenObservation - nDeltaTIndice;
    f77name(coupla)(dpFS, dpFI, dpPS, dpTA, dpAH, dpFF, npTYP, dpQP, npRC, \
		    &stTemperatureDepth.nSize, &nNtp, &nNtp2, dpItp, \
		    &(dpRTO[nLenObservation]), &bFlat, &dFCorr,   \
		    &dAln, &dAlr, &dFp, &dFsCorr, &dFiCorr, &dEr1, &dEr2, \
		    &bFail, &dEpsilon, &dZ0, &dZ0t, &dZu, &dZt, stEc.plArray, \
		    stRA.pdArray, stSN.pdArray, stRC.plArray, stRT.pdArray,\
		    stIR.pdArray, stSF.pdArray, stFV.pdArray, stFC.pdArray, \
		    dpFA, stG.pdArray, stBB.pdArray, stFP.pdArray,\
                      dpCapacity, dpConductivity, &TMTTYP);
    if(!bSilent)
      printf("coupla 1 \n");
    if(*(stEc.plArray)){
      goto liberation;
    }
    if(bFail){
      if(!bSilent)
	printf("fail\n");
      f77name(initial)(dpItp, (dpRTO+1), (dpDTO+1), (dpTAO+1), &nOne,\
		       &nLenObservation, &stTemperatureDepth.nSize,\
		       &nIR40, &bFlat, npSwo, dpCapacity, dpConductivity);
     }
  }
  else{/* Complete observations */
    if(!bSilent)
      printf("Complete observations\n");

    f77name(makitp)(dpItp, &stTemperatureDepth.nSize, &nIR40, &bFlat,\
		    &(dpTimeO[nDeltaTIndice]),			      \
		    &(dpRTO[nDeltaTIndice]), &(dpDTO[nDeltaTIndice]), \
		    &(dpTAO[nDeltaTIndice]), &dDiff, &dMLon, npSwo, \
		    stTemperatureDepth.pdArray, &bDeepTemp, &dDeepTemp);
    nNtdcl  = nLenObservation - nDeltaTIndice -\
      ((nLenObservation-nDeltaTIndice < 28800.0/dDT)	\
       ? nLenObservation-nDeltaTIndice : 28800.0/dDT);
    f77name(initial)(dpItp , (dpRTO+1), (dpDTO+1), (dpTAO+1), &nOne,	\
		     &nLenObservation, &stTemperatureDepth.nSize,\
		     &nIR40, &bFlat, npSwo, dpCapacity, dpConductivity);
    nNtp = 0 + nNtdcl;
    nNtp2 = nLenObservation - nDeltaTIndice;
    f77name(coupla)(dpFS, dpFI, dpPS, dpTA, dpAH, dpFF, npTYP, dpQP, \
		    npRC, &stTemperatureDepth.nSize, &nNtp, &nNtp2, dpItp,\
		    &(dpRTO[nLenObservation]), &bFlat, &dFCorr, \
		    &dAln, &dAlr, &dFp, &dFsCorr, &dFiCorr, &dEr1, &dEr2,\
		    &bFail, &dEpsilon, &dZ0, &dZ0t, &dZu, &dZt, stEc.plArray,\
		    stRA.pdArray, stSN.pdArray, stRC.plArray, stRT.pdArray,\
		    stIR.pdArray, stSF.pdArray, stFV.pdArray, stFC.pdArray,\
		    dpFA, stG.pdArray, stBB.pdArray, stFP.pdArray, \
                      dpCapacity, dpConductivity, &TMTTYP);
    if(!bSilent)
      printf("coupla 2\n");
    if(*(stEc.plArray)){
       goto liberation;
     }
     if(bFail){

       if(!bSilent)
	 printf("fail\n");
       f77name(initial)(dpItp, (dpRTO+1), (dpDTO+1), (dpTAO+1), &nOne,\
			&nLenObservation, &stTemperatureDepth.nSize, \
			&nIR40, &bFlat, npSwo, dpCapacity, dpConductivity);
     }
  }/* End else observation complete */

  /************ roadcast **************************************************/
  f77name(balanc)(dpFS, dpFI, dpPS, dpTA, dpAH, dpFF, npTYP, dpQP,\
		  &stTemperatureDepth.nSize,					\
		  &nIR40, &nNtp2, &nNbrTimeSteps, dpItp, &bFlat, &dFCorr,\
		   &dAln, &dAlr, &dFp, &dFsCorr, &dFiCorr, &dEr1,\
		  &dEr2, &dEpsilon, &dZ0, &dZ0t, &dZu, &dZt, stEc.plArray,\
		  stRT.pdArray, stRA.pdArray ,stSN.pdArray, stRC.plArray,\
		  stIR.pdArray, stSF.pdArray, stFV.pdArray, stFC.pdArray,\
		  dpFA, stG.pdArray, stBB.pdArray, stFP.pdArray,\
                    stSST.pdArray, stLT.pdArray, dpCapacity, dpConductivity, &TMTTYP, &dRainCutoff, &dSnowCutoff);
  if(*(stEc.plArray)){
    if(!bSilent)
      printf("Failed in balanc\n");
    goto liberation;
  }
  /* Preparation of output file */
  if(!bSilent)
    printf("Free memory\n");

    //copy the output data into the output arrays
    memcpy(lpOutRC, stRC.plArray, nNbrTimeSteps * sizeof(long));
    memcpy(dpOutRT, stRT.pdArray, nNbrTimeSteps * sizeof(double));
    memcpy(dpOutSST, stSST.pdArray, nNbrTimeSteps * sizeof(double));
    memcpy(dpOutSN, stSN.pdArray, nNbrTimeSteps * sizeof(double));
    memcpy(dpOutRA, stRA.pdArray, nNbrTimeSteps * sizeof(double));

 liberation:
/* Free everybody */
/* String */
  /* double */
  free(dpItp);
  dpItp = NULL;
  free(dpCnt);
  dpCnt = NULL;
  free(dpCapacity);
  dpCapacity = NULL;
  free(dpConductivity);
  dpConductivity = NULL;
    free(stRC.plArray);
    stRC.plArray = NULL;
    free(stRA.pdArray);
    stRA.pdArray = NULL;
    free(stIR.pdArray);
    stIR.pdArray = NULL;
    free(stSF.pdArray);
    stSF.pdArray = NULL;
    free(stRT.pdArray);
    stRT.pdArray = NULL;
    free(stSN.pdArray);
    stSN.pdArray = NULL;
    free(stFV.pdArray);
    stFV.pdArray = NULL;
    free(stFC.pdArray);
    stFC.pdArray = NULL;
    free(stG.pdArray);
    stG.pdArray = NULL;
    free(stBB.pdArray);
    stBB.pdArray = NULL;
    free(stFP.pdArray);
    stFP.pdArray = NULL;
    free(stEc.plArray);
    stEc.plArray = NULL;
    free(stSST.pdArray);
    stSST.pdArray = NULL;
    free(stTemperatureDepth.pdArray);
    stTemperatureDepth.pdArray = NULL;
    free(stLT.pdArray);
    stLT.pdArray = NULL;

}/* End Do_Metro */



