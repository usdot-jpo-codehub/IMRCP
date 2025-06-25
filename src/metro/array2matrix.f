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

********************************************************************************
* Author: Miguel Tremblay
* Date: April 15 2004
* Description: Program who test the creation of a matrix with an array.
*  integer, double precision and char are made.
*  Tools to transpose a matrix are also made, because C write information in 
*  a line fashion and fortran in column.
********************************************************************************



***************************************************************************
* Create a matrix with an array.  The arguments are the number of lines
*  and columns that must be created in the matrix.
*************************************************************************
      SUBROUTINE ARRAY2MATRIXINT(npInput, nppOutput, nLine, nColumn )
      IMPLICIT NONE
      INTEGER nLine, nColumn
      INTEGER npInput(0:nLine*nColumn-1) 
      INTEGER nppOutput(0:nLine-1,0:nColumn-1) 
      INTEGER  i,j
      
      DO j=0, nColumn-1
         DO i=0, nLine-1
            nppOutput(i,j) = npInput(i*nColumn + j)
         END DO
      END DO

      RETURN
      END SUBROUTINE ARRAY2MATRIXINT

***************************************************************************
* Transforme une matrice de double en un array de double.
*************************************************************************
      SUBROUTINE MATRIX2ARRAYDOUBLEPRECISION(dppInput, dpOutput, nLine, 
     *     nColumn)
      IMPLICIT NONE
      INTEGER nLine, nColumn
      DOUBLE PRECISION dppInput(0:nLine-1, 0:nColumn-1) 
      DOUBLE PRECISION dpOutput(0:nLine*nColumn-1) 

      INTEGER i,j

      DO i=0, nLine-1
         DO j=0, nColumn-1
            dpOutput(i*nColumn+j)=dppInput(i,j)
         END DO
      END DO

      RETURN
      END SUBROUTINE MATRIX2ARRAYDOUBLEPRECISION


***************************************************************************
* Transforme une matrice de integer en un array de double.
*************************************************************************
      SUBROUTINE MATRIX2ARRAYINT(nppInput, npOutput, nLine, nColumn)
      IMPLICIT NONE
      INTEGER  nLine, nColumn
      INTEGER nppInput(0:nLine-1, 0:nColumn-1) 
      INTEGER npOutput(0:nLine*nColumn-1) 
      INTEGER i,j

      DO i=0, nLine-1
         DO j=0, nColumn-1
            npOutput(i*nColumn+j)=nppInput(i,j)
         END DO
      END DO

      RETURN
      END SUBROUTINE MATRIX2ARRAYINT

***************************************************************************
* Create a matrix with an array.  The arguments are the number of lines
*  and columns that must be created in the matrix.
*************************************************************************
      SUBROUTINE ARRAY2MATRIXDOUBLEPRECISION(dpInput, dppOutput, nLine,
     *     nColumn)
      IMPLICIT NONE
      INTEGER nLine, nColumn
      DOUBLE PRECISION dpInput(0:nLine*nColumn-1)
      DOUBLE PRECISION dppOutput(0:nLine-1,0:nColumn-1) 

      INTEGER i,j

      DO i=0, nLine-1
         DO j=0, nColumn-1
            dppOutput(i,j) = dpInput(i*nColumn + j)
         END DO
      END DO

      RETURN
      END SUBROUTINE ARRAY2MATRIXDOUBLEPRECISION

***************************************************************************
* Create a matrix with an array.  The arguments are the number of lines
*  and columns that must be created in the matrix. The first line is printed.
*************************************************************************
      SUBROUTINE MIGARRAY2MATRIXDOUBLEPRECISION(dpInput, dppOutput, 
     *     nLine, nColumn)
      IMPLICIT NONE
      INTEGER nLine, nColumn
      DOUBLE PRECISION dpInput(0:nLine*nColumn-1) 
      DOUBLE PRECISION dppOutput(0:nLine-1,0:nColumn-1) 

      INTEGER  i,j

      DO i=0, nLine-1
         DO j=0, nColumn-1
            dppOutput(i,j) = dpInput(i*nColumn + j)
         END DO
      END DO

      RETURN
      END SUBROUTINE MIGARRAY2MATRIXDOUBLEPRECISION

*************************************************************************
* Transpose the column and line of the matrix.
*************************************************************************
      SUBROUTINE REVERTINT(nppInput, nLine, nColumn, nppOutput)
      IMPLICIT NONE
      INTEGER nLine, nColumn
      INTEGER nppInput(0:nLine-1,0:nColumn-1) 
      INTEGER nppOutput(0:nLine-1,0:nColumn-1) 
      INTEGER i,j
      INTEGER  nNewLine, nNewColumn
      INTEGER nTotalElement

      nTotalElement = 1
      DO i=0, nColumn-1
         DO j=0, nLine-1      
            nNewLine = (nTotalElement-1)/nColumn
            nNewColumn = MOD(nTotalElement, nColumn)-1
            IF(nNewColumn == -1) THEN
               nNewColumn = nColumn-1;
            END IF
            nppOutput(nNewLine, nNewColumn) = nppInput(j,i)
            nTotalElement = nTotalElement + 1
         END DO
      END DO

      RETURN
      END SUBROUTINE REVERTINT
