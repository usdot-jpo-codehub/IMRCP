
*
* METRo : Model of the Environment and Temperature of Roads
* METRo is Free and is proudly provided by the Government of Canada
* Copyright (C) Her Majesty The Queen in Right of Canada, Environment Canada, 2006

*  Questions or bugs report: metro@ec.gc.ca
*  METRo repository: https://framagit.org/metroprojects/metro
*  Documentation: https://framagit.org/metroprojects/metro/wikis/home
*
*
* Code contributed by:
*  Louis-Philippe Crevier - Canadian meteorological center
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
*
************************************************************************


************************************************************************
***
*     Sous-routine MAKITP: Sous-routine pour generer un profil analytique
*                          de temperature de la route, a un instant donne
*
*     Auteur/Author: Louis-Philippe Crevier
*     Date: Decembre 1999 / December 1999
***
      SUBROUTINE MAKITP (ITP, iref, ir40, FLAT, FT, TS, 
     *                   TU, TA, DIFF, LON, SWO_IN, dpTemperatureDepth,
     *                   bDeepTemp, dDeepTemp)
      IMPLICIT NONE
      INTEGER j
      INTEGER Nl, n
      REAL DT
      INTEGER DTMAX
      REAL PADDING
      COMMON /BUFFER_SIZE/ DTMAX, Nl, DT, PADDING, n

***                 ***
*     DEFINITIONS     *
***                 ***
***
*     Entrees
*     -------
*     FLAT: switch pont / route
*     iref: indice du dernier niveau utilisee par le modele
*     ir40: indice du niveau le plus pres de 40 cm.
*     SWO: Serie temporelle de validite des observations (0 ou 1)
*     FT: Forecast time
*     TS: Temperature de surface de la route
*     TU: Temperature a 40 cm
*     TA: Temperature de l'air
*     LON: Longitude de la station
*     DIFF: Vecteur utilise pour creer les profiles initiaux de temperature
*     dpTemperatureDepth:  Depth of temperature grid levels
*     bDeepTemp: is the bottom temperature layer given as input?
*     fDeepTemp: temperature of the bottom layer if bDeepTemp == TRUE
***
      LOGICAL FLAT, bDeepTemp
      DOUBLE PRECISION FT, TS, TU, TA, DIFF, LON
      DOUBLE PRECISION dpTemperatureDepth(n), dDeepTemp
      INTEGER iref, ir40, SWO_IN(Nl*4), SWO(Nl,4)

***
*     Sorties
*     -------
*     ITP: Profil de temperature
***
      DOUBLE PRECISION ITP(n)
***
*     Internes
*     --------
***
      DOUBLE PRECISION ASURF, ABOTT, B, C, K, E, Ew

***
*     Definition des constantes physiques
***
*
      REAL CPD, CPV, RGASD, RGASV, TRPL, TCDK, RAUW, EPS1
      REAL DELTA, CAPPA, TGL, CONSOL, GRAV, RAYT, STEFAN, PI
      REAL OMEGA, EPS2
      REAL KNAMS, STLO, KARMAN, RIC, CHLC, CHLF, DG2RAD
*
      COMMON/CTSPHY/  CPD, CPV, RGASD, RGASV, TRPL, TCDK, RAUW,
     $                EPS1, EPS2, DELTA, CAPPA, TGL, CONSOL,
     $                GRAV, RAYT, STEFAN, PI, OMEGA,
     $                KNAMS, STLO, KARMAN, RIC, CHLC, CHLF, DG2RAD

      LOGICAL bSilent
      COMMON /SILENT/ bSilent

*
**
***
*
*     Procedure
*     ---------
      if( .not. bSilent) then
         WRITE(*,*) "DEBUT MAKTIP"
      end if

*     Conversion du array en matrice
      CALL ARRAY2MATRIXINT(SWO_IN, SWO, Nl, 4)
      FT = FT*3600.0
      ASURF = 7.5
      ABOTT = 3.75
      C = LON*PI/180.0
      K = SQRT( OMEGA / DIFF )
      B = TS - EXP((-K)*dpTemperatureDepth(1))*ASURF
     *     *SIN(OMEGA*FT - K*dpTemperatureDepth(1) + C)
      if ( SWO(1,1) .eq. 1 ) then
         E = TU - EXP((-K)*dpTemperatureDepth(ir40))*
     *     ASURF*SIN(OMEGA*FT - K*dpTemperatureDepth(ir40) + C)
     *      - B
      else
         E = 0.0
      end if

      do j=1,ir40
         Ew = E*(dpTemperatureDepth(j)-dpTemperatureDepth(1))
     *     /(dpTemperatureDepth(ir40)-dpTemperatureDepth(1))
         ITP(j) = REAL(B + Ew + EXP((-K)*dpTemperatureDepth(j))*
     *        ASURF*SIN(OMEGA*FT -K*dpTemperatureDepth(j)+C))
      end do
      if ( FLAT .and. SWO(1,2) .eq. 1) then
         do j=ir40+1,iref
            Ew = (TA - ITP(ir40))/(dpTemperatureDepth(iref)
     *            -dpTemperatureDepth(ir40))
            ITP(j) = ITP(ir40) + (dpTemperatureDepth(j) 
     *               - dpTemperatureDepth(ir40))*Ew
         end do
      else
         do j=ir40+1,iref
            ITP(j) = B + E +
     *           EXP((-K)*dpTemperatureDepth(j))*ASURF
     *             *SIN(OMEGA*FT-K*dpTemperatureDepth(j)+C)
         end do
         if ( bDeepTemp ) then
            ITP(iref) = dDeepTemp
         end if
      end if

      if( .not. bSilent) then
         WRITE(*,*) "FIN MAKTIP"
      end if

      return
      end

