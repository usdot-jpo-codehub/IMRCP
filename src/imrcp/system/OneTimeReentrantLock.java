/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package imrcp.system;

import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author Federal Highway Administration
 */
public class OneTimeReentrantLock extends ReentrantLock
{
	private boolean m_bHasBeenLocked = false;
	
	@Override
	public void lock()
	{
		super.lock();
		m_bHasBeenLocked = true;
	}
	
	
	public boolean hasBeenLocked()
	{
		return m_bHasBeenLocked;
	}
}
