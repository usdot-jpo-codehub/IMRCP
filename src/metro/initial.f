
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

***
*     Sous-routine INITIAL: Initialization of the temperature profile
*                            of the road based on the surface temperature,
*                            the sub-surface temperature and the air 
*                            temperature.
*                          Effectue l'initialisation du profile de 
*                           temperature de la route a partir des donnees
*                           de temperature a la surface, a 40 cm et la 
*                           temperature de l'air.
*
*     Auteur / Author: Louis-Philippe Crevier
*     Date: Juillet 1999 / July 1999
***
      SUBROUTINE INITIAL ( ITP, TSO, TUO, TAO, DEB, FIN, 
     *     iref, ir40, FLAT, SWO_IN, dpCapacity,
     *     dpConductivity)

      IMPLICIT NONE
      INTEGER i, j
      INTEGER Nl, n
      REAL DT
      INTEGER DTMAX
      REAL PADDING
      COMMON /BUFFER_SIZE/ DTMAX, Nl, DT, PADDING, n
***                 ***
*     DEFINITIONS     *
***                 ***
***
*     Input
*     -------
*     FLAT: switch bridge / road 
*     iref: number of grid levels 
*     ir40: level at 40 cm depth
*     DEB: Start indice of initialization
*     FIN: End indice of initialization
*     TSO, TUO, TAO: forcing ( surface, 40 cm, air [under the bridge] )
*     SWO: temporel serie indicating the hole in the observations
***
      LOGICAL FLAT
      INTEGER iref, ir40, DEB, FIN
      INTEGER SWO(Nl,4), SWO_IN(4*Nl)
      DOUBLE PRECISION TSO(Nl)
      DOUBLE PRECISION TUO(Nl), TAO(Nl)
      DOUBLE PRECISION dpCapacity(n)
      DOUBLE PRECISION dpConductivity(n)
***
*     Output
*     -------
*     ITP: Initial temperature profile
***
      DOUBLE PRECISION ITP(n)
***
*     Internes
*     --------
*     now, next: time step references
*     T: Temperature profile
*     G: Flux profile
*     DTDZ: Initial temperature gradient
***
      CHARACTER*80 outfmt
      INTEGER next, now
      REAL G(0:n), T(n,2)

      LOGICAL bSilent
      COMMON /SILENT/ bSilent


***
*
*     Procedure
*     =========
      if( .not. bSilent) then
         WRITE(*,*) "DEBUT INITIAL"
      end if


      CALL ARRAY2MATRIXINT(SWO_IN, SWO, Nl, 4)
      next = 1
      now = 2
      do j=1,iref
         T(j,now) = REAL(ITP(j))
      end do

      do i = DEB,FIN
         G(0) = 0.0
         G(1) = REAL(dpConductivity(1)) * ( T(2,now) - T(1,now) )
         T(1,next) = REAL(TSO(i))
         do j=2,iref-1
            G(j) = REAL(dpConductivity(j)) * ( T(j+1,now) - T(j,now) )
         end do
         do j=2,iref-1
            T(j,next) = T(j,now)+REAL(DT*(dpCapacity(j)*(G(j)- G(j-1))))
         end do
         if ( SWO(i,1) .eq. 1 ) then
            T(ir40,next) = REAL(TUO(i))
         end if
         if ( FLAT .and. SWO(i,2) .eq. 1 ) then
*        BC: underside temp. is air temp
            T(iref,next) = REAL(TAO(i))
         else
*        BC: no flux ( G(iref) = 0.0 )
            T(iref,next) = T(j,now) - REAL(DT*dpCapacity(j)*G(iref-1))
         end if
         next = 3 - next
         now = 3 - now
      end do

      do i=1,iref
         ITP(i) = T(i,now)
      end do

*     Log writing
*     -------------------
      write(outfmt,667) min(99.99,max(-99.99,REAL(TSO(FIN)))),' / ',
     *                  real(FIN-DEB)*DT/3.6e3
 667  format(f6.2,a,f6.2)

      if( .not. bSilent) then
         WRITE(*,*) "FIN INITIAL"
      end if

      return
      end
