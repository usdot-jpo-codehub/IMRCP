package imrcp.system;

import java.util.Iterator;

/**
 * This class contains static utility methods for other classes to use
 */
public class MathUtil
{
	/**
	 * Gets the standard deviation of the values in the given growable array. The
	 * mean is calculated and stored in the first position of {@code dMeanOutput}
	 * @param dVals growable array containing the values used to calculate the standard
	 * deviation
	 * @param dMeanOutput array to store the mean of the values
	 * @return The standard deviation of the values in the growable array
	 */
	public static double standardDeviation(double[] dVals, double[] dMeanOutput)
	{
		double dMean;
		int nLen = Arrays.size(dVals) - 1;
		if (nLen == 0)
		{
			dMeanOutput[0] = Double.NaN;
			return Double.NaN;
		}
		if (Double.isNaN(dMeanOutput[0]))
		{
			dMean = 0.0;
			Iterator<double[]> oIt = Arrays.iterator(dVals, new double[1], 1, 1);
			while (oIt.hasNext())
				dMean += oIt.next()[0];
			dMean /= (Arrays.size(dVals) - 1);
		}
		else
			dMean = dMeanOutput[0];
		double dSummation = 0.0;
		Iterator<double[]> oIt = Arrays.iterator(dVals, new double[1], 1, 1);
		while (oIt.hasNext())
			dSummation += Math.pow(oIt.next()[0] - dMean, 2);

		dMeanOutput[0] = dMean;
		
		return Math.sqrt(dSummation / (Arrays.size(dVals) - 1));
	}
	
	public static boolean interpolate(double[] dArr, int nInterpolateLimit)
	{
		int nLimit = Arrays.size(dArr);
		int nCurIndex = 1;
		int nPrevIndex = 0;
		double dPrevVal = Double.NaN;
		while (nCurIndex < nLimit)
		{
			double dCurVal = dArr[nCurIndex++];
			if (!Double.isNaN(dCurVal))
			{
				dPrevVal = dCurVal;
				nPrevIndex = nCurIndex - 1;
				continue;
			}

			if (nPrevIndex > 0) 
			{
				if (nCurIndex < nLimit)
					dCurVal = dArr[nCurIndex++];
				while (Double.isNaN(dCurVal) && nCurIndex - nPrevIndex < nInterpolateLimit + 2 && nCurIndex < nLimit)
				{
					dCurVal = dArr[nCurIndex++];
				}
				if (Double.isNaN(dCurVal))
				{
					if (nCurIndex == nLimit) // the last speed record is NaN
					{
						if (nCurIndex - nPrevIndex < nInterpolateLimit)
						{
							while (nCurIndex-- > nPrevIndex)
								dArr[nCurIndex] = dPrevVal;

							return true;
						}
					}
					else
						return false; // not enough valids speed to interpolate
				}

				int nSteps = nCurIndex - nPrevIndex - 1;
				double dRange = dCurVal - dPrevVal;
				double dStep = dRange / nSteps;

				for (int nIntIndex = nPrevIndex + 1; nIntIndex < nCurIndex - 1; nIntIndex++)
				{
					dPrevVal += dStep;
					dArr[nIntIndex] = dPrevVal;
				}
				
				nPrevIndex = nCurIndex - 1;
				dPrevVal = dCurVal;
			}
			else // first speed is NaN so look ahead to try and find a non NaN value
			{
				for (int nLookAhead = 2; nLookAhead < nInterpolateLimit + 1; nLookAhead++)
				{
					double dVal = dArr[nLookAhead];
					if (!Double.isNaN(dVal))
					{
						while (nLookAhead-- > 1)
							dArr[nLookAhead] = dVal;

						break;
					}
				}
			}
		}
		
		return nPrevIndex != 0; // if 0 then all values are NaN
	}
}
