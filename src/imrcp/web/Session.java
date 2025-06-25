package imrcp.web;

import imrcp.system.CsvReader;
import imrcp.system.Text;
import java.io.IOException;
import java.io.Writer;
import java.util.Comparator;

/**
 * This class represents a User and their sessions being logged into the IMRCP web 
 * application.
 * @author aaron.cherney
 */
public class Session implements Comparator<Session>
{
	/**
	 * Time in milliseconds since Epoch the Session last accessed something from
	 * the system.
	 */
	protected long m_lLastAccess;

	
	/**
	 * Time in millisecond since Epoch that a reset password request is valid
	 */
	protected long m_lReset;

	
	/**
	 * Random bytes used to hash the password
	 */
	protected byte[] m_ySalt;

	
	/**
	 * User name
	 */
	public String m_sName;

	
	/**
	 * Hashed password as a hex string
	 */
	protected String m_sPass;

	
	/**
	 * Email address used to contact the user
	 */
	protected String m_sContact;

	
	/**
	 * ; separated string of the system groups the user belongs too
	 */
	protected String m_sGroup;

	
	/**
	 * Stores the active session token
	 */
	protected String m_sToken = "";
	
	
	protected String m_sDeactivation = "";

	
	/**
	 * UserProfile object associated with the user
	 */
	public UserProfile m_oProfile = null;

	
	/**
	 * Default constructor. Does nothing.
	 */
	Session()
	{
	}

	
	/**
	 * Constructs a Session from a line of the IMRCP users CSV file.
	 * @param oCsv CsvReader wrapping the InputStream of the IMRCP users CSV file
	 * ready to parse the current line
	 * @throws IOException
	 */
	Session(CsvReader oCsv)
		throws IOException
	{
		StringBuilder sCol = new StringBuilder();
		
		if (!oCsv.isNull(0))
		{
			oCsv.parseString(sCol, 0);
			m_sDeactivation = sCol.toString();
		}
		oCsv.parseString(sCol, 1);
		m_sName = sCol.toString(); // save username

		oCsv.parseString(sCol, 2);
		m_ySalt = Text.fromHexString(sCol);

		oCsv.parseString(sCol, 3);
		m_sPass = sCol.toString(); // keep password as hexadecimal string

		oCsv.parseString(sCol, 4);
		m_sContact = sCol.toString(); // email address for 2FA 

		oCsv.parseString(sCol, 5);
		m_sGroup = sCol.toString().intern(); // very few group patterns
	}
	
	
	public void writeUser(Writer oOut)
		throws IOException
	{
		oOut.append(m_sDeactivation);
		oOut.append(',');
		oOut.append(m_sName).append(',');
		oOut.append(Text.toHexString(m_ySalt)).append(',');
		oOut.append(m_sPass).append(',');
		oOut.append(m_sContact).append(',');
		oOut.append(m_sGroup).append('\n');
	}

	
	/**
	 * Updates this Session with the given Session's parameters. This is used
	 * when reading the IMRCP user CSV file, a user can have multiple entries in 
	 * the file with the last one in the file being the most recent. This allows
	 * Users to be updated without restarting the system
	 * @param oSess
	 */
	void update(Session oSess)
	{
		m_sDeactivation = oSess.m_sDeactivation;
		m_sName = oSess.m_sName;
		m_ySalt = oSess.m_ySalt;
		m_sPass = oSess.m_sPass;
		m_sContact = oSess.m_sContact;
		m_sGroup = oSess.m_sGroup;
	}


	/**
	 * Compares Sessions by user name
	 */
	@Override
	public int compare(Session oLhs, Session oRhs)
	{
		return oLhs.m_sName.compareToIgnoreCase(oRhs.m_sName); // order by username
	}
}
