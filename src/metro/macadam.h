
/***************************************************************************
**
** Nom:         macadam.h
**
** Auteur:      Miguel Tremblay
**
** Date:        April 16, 2004
**
** Description: .h qui contient les constantes qui étaient autrefois dans 
**  params.cdk.  L'allocation dynamique étant impossible à faire avec un 
**  pointeur créé en C et passé au Fortran, l'utilisation de constante comme
**  DTMAX doit être maintenu.
**
** TODO: Enlever les constantes à mesure que le code fortran les utilisant
**  est remplacé par du code en C.
**
****************************************************************************/

#include "number.h"

#define BOOL long
#define TRUE 1
#define FALSE 0

/* Pas de temps du modèle de bilan énergétique */
/* Model time step in second for the energy balance */
#define dDT 30.0

/* Nombre maximal de niveaux des grilles dans le sol */
/* Maximal number of grid level in the ground*/
#define nNGRILLEMAX 200

/* Physical constants */
#define dPI  3.141592653590e0  
#define dOMEGA  0.7292e-4 

/* Main call from  python */
void Do_Metro(BOOL bFlat, double dMLat, double dMLon, double* dpZones, \
	      long nNbrOfZone, long* npMateriau, double* dpTA, double* dpQP,\
	      double* dpFF, double* dpPS, double* dpFsPy, double* dpFI, \
	      double* dpFA, long* npTYP, long* npRc, double* dpTAO, \
	      double* dpRTO, double* dpDTO, double* dpAH, double* dpTimeO,\
	      long* npSWO,  BOOL* bpNoObs, double dDeltaT, \
	      long nLenObservation, long nNbrTimeSteps, BOOL bSilent,\
	      double dSstDepth, BOOL bDeepTemp, double dDeepTemp, long* lpOutRC,
              double* dpOutRT, double* dpOutSST, double* dpOutSN,
              double* dpOutRA, long nTmtType, double dRainReservoir, double dSnowReservoir, double dRainCutoff, double dSnowCutoff);
	      
void init_structure(long nTimeStepMax, long nGrilleLevelMax);


/* Fortran functions */ 
/* Only pointers can be given to fortran code */
extern void setconstphys_(BOOL* bSilent);
extern void grille_(long* nSize, long* nIR40, BOOL* bFlat, long* nNbrOfZone,\
		    double* dpZones, long* npMateriau, double* dDiff, double* pdArray, \
		    long* plArray, double* dpCapacity, double* dpConductivity, double* dSstDepth);
extern void makitp_(double* dpItp, long* nSize, long* nIR40, BOOL* bFlat, double* dpTimeO, \
		    double* dpRTO, double* dpDTO, double* dpTAO, double* dDiff, \
		    double* dMLon, long* npSwo, double* pdArray, BOOL* bDeepTemp, double* dDeepTemp);
extern void initial_(double* dpItp , double* dpRTO, double* dpDTO, double* dpTAO, long* nOne, \
		     long* nLenObservation, long* nSize, long* nIR40, \
		     BOOL* bFlat, long* npSwo, double* dpCapacity, double* dpConductivity); 
extern void coupla_(double* dpFS, double* dpFI, double* dpPS, double* dpTA, double* dpAH, \
		    double* dpFF, long* npTYP,  double* dpQP, long* npRC, \
		    long* nSize, long* nNtp, long* nNtp2, double* dpItp, double* dpRTO, \
		    BOOL* bFlat, double* dFCorr, double* dAln, double* dAlr, \
		    double* dFp, double* dFsCorr, double* dFiCorr, double* dEr1, double* dEr2, \
		    BOOL* bFail, double* dEpsilon, double* dZ0, double* dZ0t, double* dZu, \
		    double* dZt, long* stEcplArray, double* stRApdArray, double* stSNpdArray, \
		    long* stRCplArray, double* stRTpdArray, double* stIRpdArray, double* stSFpdArray,\
		    double* stFVpdArray, double* stFCpdArray, double* dpFA, double* stGpdArray, \
		    double* stBBpdArray, double* stFPpdArray, double* dpCapacity, double* dpConductivity, long* TMTTYP);


extern void balanc_(double* dpFS, double* dpFI, double* dpPS, double* dpTA, double* dpAH, \
		    double* dpFF, long* npTYP,  double * dpQP, \
		    long* nSize, long* nIR40, long* nNtp2, long* nNbrTimeSteps, double* dpItp,\
		    BOOL* bFlat, double* dFCorr, double* dAln, double* dAlr, \
		    double* dFp, double* dFsCorr, double* dFiCorr, double* dEr1, \
		    double* dEr2, double* dEpsilon, double* dZ0, double* dZ0t, double* dZu, \
		    double* dZt, long* stEcplArray, double* stRTpdArray, double* stRApdArray, \
		    double* stSNpdArray, long* stRCplArray, double* stIRpdArray, double* stSFpdArray,\
		    double* stFVpdArray, double* stFCpdArray, double* dpFA, double* stGpdArray, \
		    double* stBBpdArray, double* stFPpdArray, double* stSSTpdArray, \
		    double* stLTdArray, double* dpCapacity, double* dpConductivity, long* TMTTYP, double* dRainCutoff, double* dSnowCutoff);


struct doubleStruct get_ra(void);
struct doubleStruct get_sn(void);
struct longStruct get_rc(void);
struct doubleStruct get_rt(void);
struct doubleStruct get_ir(void);
struct doubleStruct get_sf(void);
struct doubleStruct get_fv(void);
struct doubleStruct get_fc(void);
struct doubleStruct get_g(void);
struct doubleStruct get_bb(void);
struct doubleStruct get_fp(void);
struct longStruct get_echec(void);
struct doubleStruct get_sst(void);
struct doubleStruct get_depth(void);
long get_nbr_levels(void);
struct doubleStruct get_lt(void);
