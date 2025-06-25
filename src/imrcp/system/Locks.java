/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.system;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.json.JSONObject;

/**
 * System component used to map a String (usually a file name) and a RenetrantReadWriteLock.
 * This allows for synchronized processing on files that can be read and written
 * by different threads in the system.
 * @author aaron.cherney
 */
public class Locks extends BaseBlock
{
	/**
	 * Map used to store locks associated with a string
	 */
	private final HashMap<String, ReentrantReadWriteLock> LOCKS = new HashMap();

	
	/**
	 * Period of execution in seconds
	 */
	private int m_nPeriod;

	
	/**
	 * Schedule offset from midnight in seconds
	 */
	private int m_nOffset;
	
	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_nPeriod = oBlockConfig.optInt("period", 3600);
		m_nOffset = oBlockConfig.optInt("offset", 485);
	}
	
	
	/**
	 * sets a schedule to execute on a fixed interval.
	 * @return true if no exceptions are thrown
	 */
	@Override
	public boolean start()
	{
		m_nSchedId = Scheduling.getInstance().createSched(this, m_nOffset, m_nPeriod);
		return true;
	}
	
	
	/**
	 * Get the lock associated with the given string. If there is not an 
	 * associated lock it is created before being returned.
	 * @param sName Name of the desired lock, usually a file path
	 * @return RenetrantReadWriteLock associated with the name
	 */
	public ReentrantReadWriteLock getLock(String sName)
	{
		synchronized (LOCKS)
		{
			if (!LOCKS.containsKey(sName))
				LOCKS.put(sName, new ReentrantReadWriteLock(true));

			return LOCKS.get(sName);
		}
	}
	
	
	/**
	 * Checks the map of locks for stale locks no longer being used by any
	 * system components
	 */
	@Override
	public void execute()
	{
		synchronized (LOCKS)
		{
			Iterator<Map.Entry<String, ReentrantReadWriteLock>> oIt = LOCKS.entrySet().iterator();
			while (oIt.hasNext())
			{
				Map.Entry<String, ReentrantReadWriteLock> oEntry = oIt.next();
				ReentrantReadWriteLock oLock = oEntry.getValue();
				if (!oLock.isWriteLocked() && oLock.getReadLockCount() == 0 && !oLock.hasQueuedThreads())
					oIt.remove();
			}
		}
	}
}
