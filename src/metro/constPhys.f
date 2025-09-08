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
*     Sous-routine SETCONSTPHYS: Initialise les constantes physiques 
*     qui seront utilisee partout dans le code fortran.  Ces constantes
*     et ces formules sont recuperees de METRo 1.o
*     
*     Auteur/Author: Miguel Tremblay
*     Date: 10 Juin 2004
************************************************************************

      SUBROUTINE SETCONSTPHYS (silent)
      IMPLICIT NONE

      
      REAL CPD, CPV, RGASD, RGASV, TRPL, TCDK, RAUW, EPS1
      REAL DELTA, CAPPA, TGL, CONSOL, GRAV, RAYT, STEFAN, PI
      REAL OMEGA, EPS2
      REAL KNAMS, STLO, KARMAN, RIC, CHLC, CHLF, DG2RAD
      
      REAL AS,ASX,CI,BS,BETA,FACTN,HMIN
      LOGICAL bSilent, silent
      INTEGER Nl, n
      REAL DT
      INTEGER DTMAX
      COMMON/CTSPHY/  CPD, CPV, RGASD, RGASV, TRPL, TCDK, RAUW,
     *                EPS1, EPS2, DELTA, CAPPA, TGL, CONSOL,
     *                GRAV, RAYT, STEFAN, PI, OMEGA,
     *                KNAMS, STLO, KARMAN, RIC, CHLC, CHLF, DG2RAD

      COMMON /SURFCON/AS,ASX,CI,BS,BETA,FACTN,HMIN
      COMMON /SILENT/ bSilent
      

*     DTMAX -> # de pas de temps maximal
*     Nl -> nombre de pas de temps maximal de la periode contenant 
*     les observations (48h + un coussin = 60h)
*     DT -> pas de temps du modele de bilan energetique
*     n -> nombre maximal de niveaux des grilles dans le sol
      REAL PADDING
      COMMON /BUFFER_SIZE/ DTMAX, Nl, DT, PADDING, n

      INTRINSIC SIGN


      if(silent) then
         bSilent = .true.
      else
         bSilent = .false.
      end if

      if( .not. bSilent) then
         WRITE(*,*) "DEBUT SETCONSTPHYS"
      end if
*     initialiser les constantes physiques
      PI = 3.141592653590e0
      STEFAN = 5.6698e-8
      OMEGA = 0.7292e-4
      CPD = 0.10054600e4
      CHLC = 0.250100e7
      RGASD = .28705E3
      RGASV = .46151E3
      TRPL = .27316e3
      TCDK = .27315e3
      RAUW = .1e4
      EPS1 = .62194800221014
      EPS2 = .3780199778986
      DELTA = .6077686814144
      CAPPA = .28549121795
      TGL = .27316e3
      CONSOL = .1367e4
      GRAV = .980616e1
      RAYT = .637122e7
      KNAMS = .514791
      RIC = .2
      CHLF = .334e6
      KARMAN = .40
      DG2RAD = 0.17453293e-1
      AS = 12.
      ASX= 5.0
      CI = 40.
      BS = 1.0
*     Facteur multiplicatif du flux de chaleur
      BETA = 1.0
      FACTN = 1.2
      HMIN = 35.
*     Dimension des arrays
      DTMAX = 12000
      Nl = 11520
      DT = 30.0
      n = 200

      if( .not. bSilent) then
         WRITE(*,*) "FIN SETCONSTPHYS"
      end if

      END SUBROUTINE SETCONSTPHYS
