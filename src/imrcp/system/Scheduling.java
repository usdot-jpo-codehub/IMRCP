package imrcp.system;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Singleton class that contains a thread pool and manages scheduling tasks for
 * the system.
 * @author aaron.cherney
 */
public class Scheduling implements Executor
{
	/**
	 * Singleton instance
	 */
	private static Scheduling g_oScheduling = new Scheduling();

	
	/**
	 * Timer object used to schedule the execution of tasks
	 */
	private Timer m_oTimer = new Timer();

	
	/**
	 * System thread pool
	 */
	private ExecutorService m_iExecutor = null;

	
	/**
	 * Stores the Sched objects that represent the tasks scheduled for execution
	 */
	private final ArrayList<Sched> m_oTasks = new ArrayList();

	
	/**
	 * Counter used to assign schedule ids
	 */
	private static AtomicInteger m_nIdCount = new AtomicInteger();
	
	private Logger m_oLogger = LogManager.getLogger(Scheduling.class);

	
	/**
	 * Default constructor. Does nothing.
	 */
	private Scheduling()
	{
	}

	
	/**
	 * Gets the singleton instance
	 * 
	 * @return The singleton instance
	 */
	public static Scheduling getInstance()
	{
		return g_oScheduling;
	}
	
	
	void setThreadPool(int nThreads)
	{
		if (m_iExecutor == null)
		{
			 m_iExecutor = Executors.newFixedThreadPool(nThreads);
		}
	}
	
	
	public <T> Future<T> submit(Callable<T> oWork)
	{
		return m_iExecutor.submit(oWork);
	}

	
	/**
	 * Gets a Calendar with its time set to the next period of execution based off
	 * of the given offset and period.
	 * 
	 * @param nOffset Schedule offset from midnight in seconds
	 * @param nPeriod Period of execution in seconds
	 * @return Calendar with the time set as the next period of execution
	 */
	public static Calendar getNextPeriod(int nOffset, int nPeriod)
	{
		Calendar iCalendar = new GregorianCalendar(Directory.m_oUTC);

		// set the current time to midnight UTC and add the schedule offset
		iCalendar.set(Calendar.HOUR_OF_DAY, 0);
		iCalendar.set(Calendar.MINUTE, 0);
		iCalendar.set(Calendar.SECOND, nOffset);
		iCalendar.set(Calendar.MILLISECOND, 0);

		// adjust the period from seconds to milliseconds
		nPeriod *= 1000;
		// determine the timestamp of the next period
		long lOffsetTime = iCalendar.getTimeInMillis();
		long lDeltaTime = System.currentTimeMillis() - lOffsetTime;
		long lPeriods = lDeltaTime / nPeriod;
		if (lDeltaTime % nPeriod > 0)
			++lPeriods;
		iCalendar.setTimeInMillis(lOffsetTime + lPeriods * nPeriod);
		return iCalendar;
	}
	
	
	/**
	 * Gets a Calendar with its time set to the last period of execution based off
	 * of the given offset and period.
	 * 
	 * @param nOffset Schedule offset from midnight in seconds
	 * @param nPeriod Period of execution in seconds
	 * @return Calendar with the time set as the last period of execution
	 */
	public static Calendar getLastPeriod(int nOffset, int nPeriod)
	{
		Calendar iCalendar = new GregorianCalendar(Directory.m_oUTC);

		// set the current time to midnight UTC and add the schedule offset
		iCalendar.set(Calendar.HOUR_OF_DAY, 0);
		iCalendar.set(Calendar.MINUTE, 0);
		iCalendar.set(Calendar.SECOND, nOffset);
		iCalendar.set(Calendar.MILLISECOND, 0);

		// adjust the period from seconds to milliseconds
		nPeriod *= 1000;
		// determine the timestamp of the last period
		long lOffsetTime = iCalendar.getTimeInMillis();
		long lDeltaTime = System.currentTimeMillis() - lOffsetTime;
		long lPeriods = lDeltaTime / nPeriod;

		iCalendar.setTimeInMillis(lOffsetTime + lPeriods * nPeriod);
		return iCalendar;
	}

	
	/**
	 * Creates a Sched object wrapping the given Runnable, adds it to the task
	 * list, {@link #m_oTasks} and uses the Timer, {@link #m_oTimer} to schedule
	 * the task to be executed at a fixed rate based off of the given offset and
	 * period.
	 * 
	 * @param iRunnable Runnable to schedule for execution
	 * @param nOffset Schedule offset from midnight in seconds
	 * @param nPeriod Period of execution in seconds
	 * @return The schedule id assigned to the task
	 */
	public synchronized int createSched(Runnable iRunnable, int nOffset, int nPeriod)
	{
		Calendar iCalendar = getNextPeriod(nOffset, nPeriod);
		Sched oTask = null;
		oTask = new Sched(iRunnable, m_nIdCount.getAndIncrement());
		m_oTasks.add(oTask);
		Collections.sort(m_oTasks);
		m_oTimer.scheduleAtFixedRate(oTask, iCalendar.getTime(), nPeriod * 1000);

		return oTask.m_nId;
	}

	
	/**
	 * Creates a Sched object wrapping the given Runnable, adds it to the task
	 * list, {@link #m_oTasks} and uses the Timer, {@link #m_oTimer} to schedule
	 * the task to be executed at a fixed rate, with the first time it is executed
	 * is the time in the given Date.
	 * 
	 * @param iRunnable Runnable to schedule for execution
	 * @param oTime Time to first execute the task
	 * @param nPeriod Period of execution in seconds
	 * @return The schedule id assigned to the task
	 */
	public synchronized int createSched(Runnable iRunnable, Date oTime, int nPeriod)
	{
		Sched oTask = new Sched(iRunnable, m_nIdCount.getAndIncrement());
		m_oTasks.add(oTask);
		Collections.sort(m_oTasks);
		m_oTimer.scheduleAtFixedRate(oTask, oTime, nPeriod);

		return oTask.m_nId;
	}

	
	/**
	 * Schedules the given Runnable to execute once after the given delay in 
	 * milliseconds
	 * 
	 * @param iRunnable Runnable to schedule for execution
	 * @param nDelay time to wait in milliseconds before executing the Runnable
	 */
	public void scheduleOnce(Runnable iRunnable, int nDelay)
	{
		Sched oTask = new Sched(iRunnable, -1);  // id doesn't matter since it is a one time task
		m_oTimer.schedule(oTask, nDelay);
	}
	
	
	/**
	 * Schedules the given Runnable to execute once at the given time.
	 * 
	 * @param iRunnable Runnable to schedule for execution
	 * @param oWhen Time to execute the Runnable
	 */
	public void scheduleOnce(Runnable iRunnable, Date oWhen)
	{
		Sched oTask = new Sched(iRunnable, -1);
		m_oTimer.schedule(oTask, oWhen);
	}

	
	
	/**
	 * Wrapper for {@link #m_iExecutor#execute(java.lang.Runnable)}
	 * 
	 * @param iRunnable The Runnable to execute
	 */
	@Override
	public void execute(Runnable iRunnable)
	{
		m_iExecutor.execute(iRunnable);
	}

	
	/**
	 * Wrapper for {@link java.util.concurrent.ExecutorService#shutdownNow()}
	 */
	public void stop()
	{
		m_iExecutor.shutdownNow();
	}

	
	/**
	 * Attempts to cancel the schedule for the given Runnable and schedule id.
	 * 
	 * @param iRunnable Runnable to cancel its fixed interval of execution
	 * @param nSchedId schedule id associated with the Runnable
	 * @return true if the task was successfully canceled and removed from the
	 * task list, otherwise false.
	 */
	public synchronized boolean cancelSched(Runnable iRunnable, int nSchedId)
	{
		Sched oSearch = new Sched(iRunnable, nSchedId);
		int nIndex = Collections.binarySearch(m_oTasks, oSearch);
		if (nIndex >= 0 && m_oTasks.get(nIndex).m_iRunnable == iRunnable)
		{
			m_oTasks.remove(nIndex).cancel();
			return true;
		}

		return false;
	}

	public static <T extends Callable> ArrayList<Future<T>> processCallables(ArrayList<T> oCallables, int nThreadsToUse) throws Exception
	{
		ArrayDeque<Future<T>> oTasks = new ArrayDeque();
		ArrayList<Future<T>> oComplete = new ArrayList();
		ArrayDeque<T> oToDo = new ArrayDeque();
		Scheduling oScheduling = getInstance();
		int nAvailable = nThreadsToUse;
		if (nAvailable == 0)
		{
			nAvailable = 1;
		}
		for (T oCallable : oCallables)
		{
			if (nAvailable > 0)
			{
				oTasks.addLast(oScheduling.submit(oCallable)); // submit work to thread pool
				--nAvailable; // update available threads
			}
			else
			{
				oToDo.addLast(oCallable); // add to queue if no available threads
			}
			int nTaskIndex = oTasks.size();
			while (nTaskIndex-- > 0)
			{
				Future<T> oTask = oTasks.pollFirst();
				if (oTask.isDone())
				{
					oComplete.add(oTask);
					if (!oToDo.isEmpty())
					{
						oTasks.addLast(oScheduling.submit(oToDo.getFirst()));
					}
					else
					{
						++nAvailable; // update available threads
					}
				}
				else
				{
					// task isn't done so put back in queue
					oTasks.addLast(oTask);
				}
			}
		}
		while (!oTasks.isEmpty() || !oToDo.isEmpty())
		{
			while (nAvailable > 0 && !oToDo.isEmpty())
			{
				oTasks.add(oScheduling.submit(oToDo.pollFirst()));
				--nAvailable;
			}
			if (!oTasks.isEmpty())
			{
				int nIndex = oTasks.size();
				boolean bGo = true;
				while (nIndex-- > 0 && bGo)
				{
					Future<T> oTask = oTasks.pollFirst();
					if (oTask.isDone())
					{
						oComplete.add(oTask);
						++nAvailable;
						bGo = false;
					}
					else
					{
						oTasks.addLast(oTask);
					}
				}
			}
		}
		
		return oComplete;
	}

	
	/**
	 * Object used to keep track of scheduled tasks of execution.
	 */
	private class Sched extends TimerTask implements Comparable<Sched>
	{
		/**
		 * The runnable that is executed when this Sched calls {@link #run()}
		 */
		private Runnable m_iRunnable;

		
		/**
		 * Schedule id
		 */
		private int m_nId;

		
		/**
		 * Default constructor. Does nothing.
		 */
		private Sched()
		{
		}

		
		/**
		 * Constructs a Sched with the given parameters.
		 * 
		 * @param iRunnable The runnable that is executed when this Sched calls
		 * {@link #run()}
		 * @param nId Schedule id
		 */
		private Sched(Runnable iRunnable, int nId)
		{
			m_iRunnable = iRunnable;
			m_nId = nId;
		}


		/**
		 * Wrapper for {@link #m_iExecutor#execute(java.lang.Runnable)} passing
		 * {@link #m_iRunnable} as the parameter
		 */
		@Override
		public void run()
		{
			m_iExecutor.execute(m_iRunnable);
		}


		/**
		 * Compares Scheds by id
		 */
		@Override
		public int compareTo(Sched oSched)
		{
			return m_nId - oSched.m_nId;
		}
	}
}
