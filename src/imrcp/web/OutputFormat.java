
package imrcp.web;

import imrcp.store.Obs;
import java.io.PrintWriter;
import java.util.Comparator;
import java.util.List;

/**
 * Base class used to write the data files that fulfill subscriptions/reports.
 * Child classes implement {@link #fulfill(java.io.PrintWriter, java.util.List, imrcp.web.ReportSubscription)}
 * to write the data in different formats.
 * @author aaron.cherney
 */
public abstract class OutputFormat implements Comparator<Obs>
{
	/**
	 * File extension of the data files created by this class
	 */
	protected String m_sSuffix;

	
	/**
	 * Default constructor. Does nothing
	 */
	protected OutputFormat()
	{
	}

	
	/**
	 * Gets the file extension of the data files created by this class.
	 * @return the file extension of the data files created by this class.
	 */
	String getSuffix()
	{
		return m_sSuffix;
	}

	
	/**
	 * Fulfills the given subscription/report by writing the observations in the
	 * given list to the given Writer in the format specified in the implementation
	 * @param oWriter Writer to write Obs to
	 * @param oSubObsList List of Obs that matched the query of the subscription/report
	 * @param oSub The subscription/report
	 */
	abstract void fulfill(PrintWriter oWriter, List<Obs> oSubObsList,
	   ReportSubscription oSub);


	/**
	 * Compares Obs by start time, then contributor id, then observation type
	 * id, then end time
	 */
	@Override
	public int compare(Obs oSubObsL, Obs oSubObsR)
	{
		int nCompare = (int)(oSubObsL.m_lObsTime1 - oSubObsR.m_lObsTime1);
		if (nCompare != 0)
			return 0;

		// sort the observations for neat output by contrib, obstype, timestamp
		nCompare = oSubObsL.m_nContribId - oSubObsR.m_nContribId;
		if (nCompare != 0)
			return nCompare;

		nCompare = oSubObsL.m_nObsTypeId - oSubObsR.m_nObsTypeId;
		if (nCompare != 0)
			return nCompare;

		nCompare = (int)(oSubObsL.m_lObsTime2 - oSubObsR.m_lObsTime2);
		if (nCompare != 0)
			return 0;

		return nCompare;
	}
}
