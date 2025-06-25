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
*  Yves Delage - Recherche en prevision numerique (RPN)
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


***S/P  FLXSURFZ
*
      SUBROUTINE FLXSURFZ(CMU, CTU, RIB, FTEMP, FVAP, ILMO,
     X                    UE, FCOR, TA , QA , ZU, ZT, VA,
     Y                    TG , QG , H , Z0 , Z0T,
     %                    LZZ0, LZZ0T, FM, FH, N, ITER )
      IMPLICIT NONE
      INTEGER N,ITER(N)
      REAL CMU(N),CTU(N),RIB(N),ILMO(N)
      REAL FTEMP(N),FVAP(N)
      DOUBLE PRECISION FCOR(N)
      DOUBLE PRECISION TA(N),QA(N),ZU(N),ZT(N),VA(N)
      DOUBLE PRECISION Z0(N), Z0T(N)
      REAL TG(N),QG(N),UE(N), H(N)
      REAL LZZ0(N),LZZ0T(N)
      REAL fm(N),fh(N)
*
*Author
*          Y.Delage (Jul 1990)
*Revision
* 001      G. Pellerin (Jun 94) New function for unstable case
* 002      G. Pellerin (Jui 94) New formulation for stable case
* 003      B. Bilodeau (Nov 95) Replace VK by KARMAN
* 004      M. Desgagne (Dec 95) Add safety code in function ff
*                               and ensures that RIB is non zero
* 005      R. Sarrazin (Jan 96) Correction for H
* 006      C. Girard (Nov 95) - Diffuse T instead of Tv
* 007      G. Pellerin (Feb 96) Revised calculation for H (stable)
* 008      G. Pellerin (Feb 96) Remove corrective terms to CTU
* 009      Y. Delage and B. Bilodeau (Jul 97) - Cleanup
* 010      Y. Delage (Feb 98) - Addition of HMIN
* 011      Y. Delage (Sept 00) - Set top of surface layer at ZU +Z0
*                              - Output UE instead of UE**2
*                              - Initialise ILMO and H
*                              - Change iteration scheme for stable case
*                              - Introduce log-linear profile for near-
*                                 neutral stable cases
*                              - set VAMIN inside flxsurf
* 012      Y. Delage (Oct 00) - Input of wind and temperatur/humidity
*                                at different levels
*
*Object
*          to calculate surface layer transfer coefficients and fluxes
*          FLXSURFZ is a variant of FLXSURF3 that permits to input
*          wind and temperature (humidity) at different levels
*
*Arguments
*
*          - Output -
* CMU      transfer coefficient of momentum times UE
* CTU      transfer coefficient of temperature times UE
* RIB      bulk Richardson number
* FTEMP    temperature flux
* FVAP     vapor flux
* ILMO     (1/length of Monin-Obukov)
* UE       friction velocity 
* H        height of the boundary layer
* FM       momentum stability function
* FH       heat stability function
* LZZ0     log ((zu+z0)/z0)
* LZZ0T    log ((zt+z0)/z0t)
*          - Input -
* FCOR     Coriolis factor
* ZU       height of wind input
* ZT       height of temperature and humidity input
* TA       temperature  at z=zt
* QA       specific humidity at z=zt 
* VA       wind speed at z=zu 
* TG       surface temperature
* QG       specific humidity at the surface
* Z0       roughness length for momentum      flux calculations
* Z0T      roughness length for heat/moisture flux calculations
* N        horizontal dimension
* ITER     key allowing to bypass the calculations
*
*Notes
*          SEE DELAGE AND GIRARD BLM 58 (19-31)
*                "       BLM 82 (23-48)
*
*     DIVERSES CONSTANTES PHYSIQUES 
*
c
      REAL CPD, CPV, RGASD, RGASV, TRPL, TCDK, RAUW, EPS1
      REAL DELTA, CAPPA, TGL, CONSOL, GRAV, RAYT, STEFAN, PI
      REAL OMEGA, EPS2
      REAL KNAMS, STLO, KARMAN, RIC, CHLC, CHLF, DG2RAD
*
      COMMON/CTSPHY/  CPD, CPV, RGASD, RGASV, TRPL, TCDK, RAUW,
     $                EPS1, EPS2, DELTA, CAPPA, TGL, CONSOL,
     $                GRAV, RAYT, STEFAN, PI, OMEGA,
     $                KNAMS, STLO, KARMAN, RIC, CHLC, CHLF, DG2RAD
      REAL AS,ASX,CI,BS,BETA,FACTN,HMIN
c
      COMMON /SURFCON/AS,ASX,CI,BS,BETA,FACTN,HMIN
      DOUBLE PRECISION TPOTA(N)
      INTEGER J
      INTEGER IT,ITMAX
      REAL HMAX,CORMIN,EPSLN
      REAL RAC3,CM,CT
      DOUBLE PRECISION ZP
      REAL F,X,X0,Y,Y0
      DOUBLE PRECISION DG, UNSH, GG
      REAL HE,HS
      DOUBLE PRECISION DTHV,TVA,TVS
      REAL VAMIN,U
      DOUBLE PRECISION HL
      REAL CS
      REAL ZB,DD,ILMOX
      DOUBLE PRECISION ZZ, DF
      SAVE HMAX,CORMIN,EPSLN
      SAVE ITMAX
      SAVE VAMIN
*
      DATA CORMIN, HMAX /0.7E-4 ,  1500.0/
      DATA ITMAX / 3  /
      DATA VAMIN /0.1/
      DATA EPSLN / 1.0e-05 /
*
      DOUBLE PRECISION a,b,c,d,z
      REAL unsl
      DOUBLE PRECISION hi, psi
************************************************************************
**  fonctions de couche de surface pour le cas stable                 **
************************************************************************
*
      d  (unsl) = 4*AS*BETA*unsl
      c  (hi)   = d(unsl)*hi - hi**2
      b  (hi)   = d(unsl) - 2*hi
      a  (z,hi) = sqrt(1 + b(hi)*z - c(hi)*z**2)
      psi(z,hi) = 0.5 * (a(z,hi)-z*hi-log(1+b(hi)*z*0.5+a(z,hi))-
     +     b(hi)/(2.0*sqrt(c(hi)))*asin((b(hi)-2.0*c(hi)*z)/d(unsl)))
*
*   Limites de validite: unsl >= 0 (cas stable ou neutre)
*                        c > 0 (hi < d)
*                        z*hi < 1
*   Ces 2 dernieres conditions imposees a l'aide du facteur 'factn'
*
*   Reference :  Y. Delage, BLM, 82 (p23-48) (Eq.33-37)
************************************************************************
      DF(ZZ)=(1.0-ZZ*UNSH)*sqrt(1.0+d(ilmo(j))*ZZ/(1-ZZ*UNSH))
*
*     CONVERTIR TA A TEMPERATURE POTENTIELLE TPOTA
      DO J=1,N
         TPOTA(J) = TA(J) + (GRAV/CPD)*ZT(J)
      END DO
*
      RAC3=sqrt(3.0)
      CS=AS*2.5
*
      DO 1 J=1,N
      IF(ITER(J).EQ.1) THEN
*
*  CALCULATE THE RICHARDSON NUMBER
        ZP=ZU(J)**2/(ZT(J)+Z0(J)-Z0T(J))
        u=max(vamin,REAL(va(j)))
        tva=(1+DELTA*QA(J))*TPOTA(J)
        tvs=(1+DELTA*QG(J))*TG(J)
        dthv=tva-tvs
        RIB(J)=REAL(grav/(tvs+0.5*dthv)*ZP*dthv/u**2)
        if (rib(j).ge.0.0) rib(j) = max(rib(j), EPSLN)
        if (rib(j).lt.0.0) rib(j) = min(rib(j),-EPSLN)
*
*  FIRST APPROXIMATION TO ILMO
        LZZ0(J)=REAL(LOG(1+ZU(J)/Z0(J)))
        LZZ0T(J)=REAL(LOG((ZT(J)+Z0(J))/Z0T(J)))
        IF(RIB(J).GT.0.)  THEN
           FM(J)=LZZ0(J)+CS*RIB(J)/max(REAL(2*z0(j)),1.0)
           FH(J)=BETA*(LZZ0T(J)+CS*RIB(J))/
     1           max(REAL(sqrt(z0(j)*z0t(j))),1.0)
           ILMO(J)=REAL(RIB(J)*FM(J)**2/(ZP*FH(J)))
           F=MAX(REAL(ABS(FCOR(J))),CORMIN)
           H(J)=BS*sqrt(karman*u/(ILMO(J)*F*fm(j)))
        ELSE
           FM(J)=LZZ0(J)-min(0.7+log(1-rib(j)),LZZ0(J)-1)
           FH(J)=BETA*(LZZ0T(J)-min(0.7+log(1-rib(j)),LZZ0T(J)-1))
           ILMO(J)=REAL(RIB(J)*FM(J)**2/(ZP*FH(J)))
        ENDIF
      ENDIF
    1 CONTINUE
* - - - - - - - - -  BEGINNING OF ITERATION LOOP - - - - - - - - - - - 
      DO 35 IT=1,ITMAX
      DO 35 J=1,N
      IF(ITER(J).EQ.1) THEN
        u=max(vamin,REAL(va(j)))
        ZP=ZU(J)**2/(ZT(J)+Z0(J)-Z0T(J))
        IF(RIB(J).GT.0.)  THEN
*----------------------------------------------------------------------
*  STABLE CASE
       	ILMO(J)=max(EPSLN,ILMO(J))
        hl=(ZU(J)+10*Z0(J))*FACTN
        F=MAX(REAL(ABS(FCOR(J))),CORMIN)
        hs=BS*sqrt(karman*u/(ILMO(J)*F*fm(j)))
        H(J)=REAL(MAX(HMIN,hs,hl,factn/d(ILMO(J))))
        UNSH=1/H(J)
        unsl=ILMO(J)
        fm(J)=REAL(LZZ0(J)+psi(ZU(J)+Z0(J),unsh)-psi(Z0(J),unsh))
        fh(J)=REAL(BETA*(LZZ0T(J)+psi(ZT(J)+Z0(J),unsh)
     *       -psi(Z0T(J) ,unsh)))
        DG=(-ZP)*FH(J)/FM(J)**2*(1.0+beta*(DF(ZT(J)+Z0(J))
     *       -DF(Z0T(J)))/
     1          (2*FH(J))-(DF(ZU(J)+Z0(J))-DF(Z0(J))))/FM(J)

*----------------------------------------------------------------------
*  UNSTABLE CASE
      ELSE
         ILMO(J)=MIN(0.,ILMO(J))
         X=REAL((1-CI*(ZU(J)+Z0(J))*BETA*ILMO(J))**(1./6))
         X0=REAL((1-CI*Z0(J)*BETA*ILMO(J))**(1./6.))
         FM(J)=LZZ0(J)+LOG((X0+1)**2*SQRT(X0**2-X0+1)*(X0**2+X0+1)**1.5
     %               /((X+1)**2*SQRT(X**2-X+1)*(X**2+X+1)**1.5))
     %              +RAC3*ATAN(RAC3*((X**2-1)*X0-(X0**2-1)*X)/
     %              ((X0**2-1)*(X**2-1)+3*X*X0))
         Y=REAL((1-CI*(ZT(J)+Z0(J))*BETA*ILMO(J))**(1./3))
         Y0=REAL((1-CI*Z0T(J)*BETA*ILMO(J))**(1./3))
         FH(J)=BETA*(LZZ0T(J)+1.5*LOG((Y0**2+Y0+1)/(Y**2+Y+1))+RAC3*
     %        ATAN(RAC3*2*(Y-Y0)/((2*Y0+1)*(2*Y+1)+3)))
         DG=(-ZP)*FH(J)/FM(J)**2*(1+beta/FH(J)*(1/Y-1/Y0)-2/FM(J)*
     %                (1/X-1/X0))
      ENDIF
*----------------------------------------------------------------------
      IF(IT.LT.ITMAX) THEN
         GG=RIB(J)-FH(J)/FM(J)**2*ZP*ILMO(J)
         ILMO(J)=ILMO(J)-REAL(GG/DG)
      ENDIF
      ENDIF
   35 CONTINUE
* - - - - - -  - - - END OF ITERATION LOOP - - - - - - - - - - - - - -
      DO 80 J=1,N
      IF(ITER(J).EQ.1) THEN
        u=max(vamin,REAL(va(j)))
*----------------------------------------------------------------------
*  CALCULATE ILMO AND STABILITY FUNCTIONS FROM LOG-LINEAR PROFILE
        zb=REAL(zu(j)/(zt(j)+z0(j)-z0t(j)))
        dd=(beta*lzz0t(j)*zb)**2-4*rib(j)*asx*lzz0(j)*beta*
     1        (lzz0t(j)*zb-lzz0(j))
        if(rib(j).gt.0..and.rib(j).lt.beta/asx.and.dd.ge.0.) then
         ilmox=REAL((beta*lzz0t(j)*zb-2*rib(j)*asx*lzz0(j)-sqrt(dd))
     1          /(2*zu(j)*(asx**2*rib(j)-beta*asx)))
         if(ilmox.lt.ilmo(j)) then
            ilmo(j)=ilmox
            fm(j)=REAL(lzz0(j)+asx*zu(j)*ilmox)
            fh(j)=REAL(beta*(lzz0t(j)+asx*(zt(j)+z0(j)-z0t(j))*ilmox))
         endif
        endif
*----------------------------------------------------------------------
        CM=karman/FM(J)
        CT=karman/FH(J)
        UE(J)=u*CM
        CMU(J)=CM*UE(J)
        CTU(J)=CT*UE(J)
        if (rib(j).gt.0.0) then
*             cas stable
              H(J)=MIN(H(J),hmax)
        else
*             cas instable
              F=MAX(ABS(REAL(FCOR(J))),CORMIN)
              he=max(HMIN,0.3*UE(J)/F)
              H(J)=MIN(he,hmax)
        endif
        FTEMP(J)=REAL((-CTU(J))*(TPOTA(J)-TG(J)))
        FVAP(J)=REAL((-CTU(J))*(QA(J)-QG(J)))
      ENDIF
   80 CONTINUE
      RETURN
      END
