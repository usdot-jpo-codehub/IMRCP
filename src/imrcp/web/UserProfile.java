/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.web;

import imrcp.system.CsvReader;
import java.io.IOException;

/**
 * Object that contains information about system components the user is allowed 
 * to access.
 * @author aaron.cherney
 */
public class UserProfile
{
	/**
	 * The Network ids the User has permission to access
	 */
	public String[] m_sNetworks;
    
	
	/**
	 * Default constructor. Gives the user access to no networks.
	 */
	public UserProfile()
	{
		m_sNetworks = new String[0];
	}
	
	
	/**
	 * Constructs a UserProfile from the given user profile CSV file
	 * @param oIn CsvReader wrapping the InputStream of the user profile CSV file
	 * @throws IOException
	 */
	public UserProfile(CsvReader oIn)
		throws IOException
    {
		int nCols = oIn.readLine(); // read the first line
		m_sNetworks = new String[nCols]; // number of columns is the number of networks
		for (int nIndex = 0; nIndex < nCols; nIndex++)
			m_sNetworks[nIndex] = oIn.parseString(nIndex); // read each network
    }
}
