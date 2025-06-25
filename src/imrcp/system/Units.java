package imrcp.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.TreeMap;
import org.apache.logging.log4j.LogManager;
import org.json.JSONObject;

/**
 * The {@code Units} class keeps track of {
 *
 * @see UnitConv}'s stored in the database and wraps both Forward and Reverse
 * conversions.
 * <p>
 * Implements the {@code ILockFactory} interface to allow
 * {
 * @see UnitConv}'s to be modified in a mutually exclusive fashion through the
 * use of {@link StripeLock} containers.
 * </p>
 * <p>
 * This is a singleton class who's instance can be retrieved by the
 * {
 * @see Units#getInstance} method.
 * </p>
 *
 * @see UnitConvF
 * @see UnitConvR
 */
public class Units extends TreeMap<String[], Units.UnitConv>
{
	/**
	 * The singleton instance of Units.
	 */
	private static Units g_oInstance = new Units((String[] o1, String[] o2) ->
	{
		int nRet = o1[0].compareTo(o2[0]);
		if (nRet == 0)
			nRet = o1[1].compareTo(o2[1]);
		
		return nRet;
	});


	/**
	 * Default Constructor reads the units file contained in the
	 * config file by iterating through the units stored in the file, adding 
	 * both forward and reverse conversions to ({@code m_oUnits}), the list of 
	 * units contained in the {@code Units} class. It also reads the source units
	 * file to fill {@link #m_oSourceUnits} with the definitions of what units
	 * each source uses.
	 */
	private Units(Comparator<String[]> oComp)
	{
		super(oComp);
		try
		{
			JSONObject oConfig = Directory.getInstance().getConfig(getClass().getName(), "Units");
			String sConvFile = oConfig.getString("convfile");
			try (CsvReader oIn = new CsvReader(Files.newInputStream(Paths.get(sConvFile))))
			{
				while (oIn.readLine() > 0)
				{
					UnitConv oUnitConv = new UnitConv(oIn);
					put(new String[]{oUnitConv.m_sFromUnit, oUnitConv.m_sToUnit}, oUnitConv);
					
					oUnitConv = new UnitConvR(oIn);
					put(new String[]{oUnitConv.m_sFromUnit, oUnitConv.m_sToUnit}, oUnitConv);
				}
			}
		}
		catch (IOException oEx)
		{
			LogManager.getLogger("STARTUPFAILURE").error(oEx, oEx);
		}
	}


	/**
	 * Retrieves the singleton instance of {@code Units}.
	 *
	 * @return The instance of {@code Units}.
	 */
	public static Units getInstance()
	{
		return g_oInstance;
	}


	/**
	 * Attempts to convert the given value from one unit to another.
	 * 
	 * @param sFromUnit The unit to convert from
	 * @param sToUnit The unit to convert to
	 * @param dVal the value to convert
	 * @return The converted value if conversion was possible. If conversion isn't
	 * possible (due to either of the unit Strings being null, the unit Strings
	 * being equal to each other, or not having a conversion defined for the given
	 * units) the original value is returned.
	 */
	public double convert(String sFromUnit, String sToUnit, double dVal)
	{
		if (sFromUnit == null || sToUnit == null || sFromUnit.compareTo(sToUnit) == 0)
			return dVal;

		return getConversion(sFromUnit, sToUnit).convert(dVal);
	}


	/**
	 * This method searches the list of conversion for the conversion between
	 * the supplied units.
	 *
	 * @param sFromUnit The unit to convert from.
	 * @param sToUnit The unit to convert to.
	 * @return Null when either of the supplied units are
	 * null, if the supplied units are the same, or if the conversion is not
	 * stored in the conversion list. Else it returns the queried conversion.
	 */
	public UnitConv getConversion(String sFromUnit, String sToUnit)
	{
		UnitConv oConv = null;
		String[] sKey = new String[]{sFromUnit, sToUnit};
		synchronized (this)
		{
			oConv = get(sKey);
		}
		
		if (oConv == null)
		{
			oConv = new IdempConv(sFromUnit, sToUnit);
			UnitConv oOld = null;
			synchronized (this)
			{
				if (!containsKey(sKey))
				{
					oOld = put(sKey, oConv);
				}
			}
			if (oOld == null && sFromUnit.compareTo(sToUnit) != 0)
			{
				Directory.getInstance().addConfigError(String.format("Missing unit conversion %s to %s", sFromUnit, sToUnit));
			}
		}
		
		return oConv;
	}

	
	/**
	 * Wraps forward conversions which are conversions of the form:
	 * <blockquote>
	 * from-units -> to-units
	 * </blockquote>
	 * It extends {
	 *
	 * @see UnitConv} implementing the {
	 * @see UnitConv#convert} method.
	 *
	 * @see UnitConv
	 * @see UnitConvR
	 */
	public class UnitConv implements Comparable<UnitConv>
	{
		/**
		 * Multiply factor.
		 */
		protected double m_dMultiply = 1.0;

		
		/**
		 * Division factor.
		 */
		protected double m_dDivide = 1.0;

		
		/**
		 * Addition factor.
		 */
		protected double m_dAdd = 0.0;

		
		/**
		 * Unit label corresponding to the units to be converted from.
		 */
		protected String m_sFromUnit;

		
		/**
		 * Unit label corresponding to the units to be converted to.
		 */
		protected String m_sToUnit;


		/**
		 * <b> Default Constructor </b>
		 * <p>
		 * Creates new instances of {@code UnitConv}. Non-default constructor
		 * performs initializations.
		 * </p>
		 */
		UnitConv()
		{
		}


		/**
		 * Sets the convert-to and convert-from labels to sFromUnit, and sToUnit
		 * for a newly created instance of {@code UnitConv}.
		 *
		 * @param sFromUnit The new convert-from label.
		 * @param sToUnit The new convert-to label.
		 */
		UnitConv(CsvReader oIn)
		{
			m_sFromUnit = oIn.parseString(0);
			m_sToUnit = oIn.parseString(1);
			m_dMultiply = oIn.parseDouble(2);
			m_dDivide = oIn.parseDouble(3);
			m_dAdd = oIn.parseDouble(4);
		}


		/**
		 * The {@code convert} method returns the value passed in. It is meant
		 * to be the default conversion if no conversions can be found.
		 * Extension of {@code UnitConv} perform standard, more useful
		 * overridden conversion methods based off the conversion factors.
		 *
		 * @param dValue The value to be converted.
		 * @return The newly converted value.
		 */
		public double convert(double dValue)
		{
			return (dValue * m_dMultiply / m_dDivide + m_dAdd);
		}


		/**
		 * Compares the units by their labels to determine if they're the same.
		 *
		 * @param oUnitConv The units to compare to the base units.
		 * @return 0 - if both the convert-to and convert-from labels of the
		 * base units match those of oUnitConv.
		 */
		@Override
		public int compareTo(UnitConv oUnitConv)
		{
			int nReturn = m_sFromUnit.compareTo(oUnitConv.m_sFromUnit);
			if (nReturn != 0)
				return nReturn;

			return m_sToUnit.compareTo(oUnitConv.m_sToUnit);
		}
	}

	
	/**
	 * Wraps reverse conversions. Extends {@code UnitConvF} implementing the
     * {
	 *
	 * @see UnitConv#convert} method in such a way that the conversions are of
	 * the form:
	 * <blockquote>
	 * to-units -> from-units
	 * </blockquote>
	 *
	 * @see UnitConv
	 * @see UnitConvF
	 */
	private class UnitConvR extends UnitConv
	{
		/**
		 * <b> Default Constructor </b>
		 * <p>
		 * Creates new instances of {@code UnitConvR}
		 * </p>
		 */
		protected UnitConvR()
		{
		}


		/**
		 * Calls the {
		 *
		 * @see UnitConvF} constructor to setup the units and conversion
		 * factors.
		 *
		 * @param sFromUnit Units to convert from.
		 * @param sToUnit Units to convert to.
		 * @param dMultiply Multiplication factor.
		 * @param dDivide Division factor.
		 * @param dAdd Addition factor.
		 */
		protected UnitConvR(CsvReader oIn)
		{
			super(oIn);
			String sTemp = m_sFromUnit;
			m_sFromUnit = m_sToUnit;
			m_sToUnit = sTemp;
		}


		/**
		 * Overrides {
		 *
		 * @see UnitConv#convert} and performs a forward conversion of the form:
		 * <blockquote>
		 * [(Value - Addition Factor)* Division Factor] / Multiplication Factor
		 * </blockquote>
		 *
		 * @param dValue The value to convert.
		 * @return The newly converted value.
		 */
		@Override
		public double convert(double dValue)
		{
			return ((dValue - m_dAdd) * m_dDivide / m_dMultiply);
		}
	}
	
	
	private class IdempConv extends UnitConv
	{
		protected IdempConv(String sFrom, String sTo)
		{
			m_sFromUnit = sFrom;
			m_sToUnit = sTo;
		}
		
		
		@Override
		public double convert(double dValue)
		{
			return dValue;
		}
	}
}
