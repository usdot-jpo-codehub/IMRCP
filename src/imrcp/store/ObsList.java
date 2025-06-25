/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.store;

import java.util.ArrayList;

/**
 *
 * @author Federal Highway Administration
 */
public class ObsList extends ArrayList<Obs>
{
	public boolean m_bHasData = false;
	public ObsList()
	{
		super();
	}
	
	public ObsList(int nInitCapicity)
	{
		super(nInitCapicity);
	}
}
