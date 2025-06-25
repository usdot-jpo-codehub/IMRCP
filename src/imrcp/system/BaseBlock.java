/* 
 * Copyright 2017 Synesis-Partners.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package imrcp.system;


import imrcp.store.ObsList;
import jakarta.servlet.ServletConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

/**
 * This class acts as the base class for system components. It contains the 
 * methods and variables to be able to register with the directory to communicate
 * with other components. Extends HttpServlet so that any component of the system
 * can receive http requests.
 * 
 * @author aaron.cherney
 */
public abstract class BaseBlock extends HttpServlet implements Runnable, IRunTarget<String[]>
{
		/**
	 * Index in string array system messages that corresponds to the ImrcpBlock 
	 * the message is from
	 */
	public static final int FROM = 0;

	
	/**
	 * Index in string array system messages that corresponds to the type of 
	 * message
	 */
	public static final int MESSAGE = 1;
	
	
	/**
	 * Status enumeration used to override the current status regardless of 
	 * what it is
	 */
	public static final int OVERRIDESTATUS = -1;

	
	/**
	 * Status enumeration indicating that the ImrcpBlock is starting
	 */
	public static final int STARTING = 0;

	
	/**
	 * Status enumeration indicating that the ImrcpBlock actively executing
	 * its task
	 */
	public static final int RUNNING = 1;

	
	/**
	 * Status enumeration indicating that the ImrcpBlock is idle and ready to
	 * execute its task
	 */
	public static final int IDLE = 2;

	
	/**
	 * Status enumeration indicating that the ImrcpBlock has had an error
	 * occur and is not ready to execute its task
	 */
	public static final int ERROR = 3;

	
	/**
	 * Status enumeration indicating that the ImrcpBlock is actively shuting 
	 * down and stopping its service
	 */
	public static final int STOPPING = 4;

	
	/**
	 * Status enumeration indicating that the ImrcpBlock has finishing stopping
	 * its service
	 */
	public static final int STOPPED = 5;

	
	/**
	 * Status enumeration indicating that the ImrcpBlock is currently initializing
	 */
	public static final int INIT = 6;
	
	
	/**
	 * String names for the different status enumerations
	 */
	public static final String[] STATUSES = new String[]
	{
		"STARTING", "RUNNING", "IDLE", "ERROR", "STOPPING", "STOPPED", "INIT"
	};
	
	
	/**
	 * Used to create unique sequential ids
	 */
	private static AtomicInteger m_nIdCount = new AtomicInteger();

	
	/**
	 * System id used by the {@link imrcp.system.Directory}
	 */
	public int m_nId;

	
	/**
	 * Schedule id used by {@link imrcp.system.Scheduling}
	 */
	protected int m_nSchedId;

	
	/**
	 * Stores the current status of the block
	 */
	private AtomicInteger m_nStatus = new AtomicInteger();
	
	
	/**
	 * Stores the last time in milliseconds since Epoch that the status changed
	 */
	private AtomicLong m_lStatusChanged = new AtomicLong();

	
	/**
	 * The name of the system component
	 */
	protected String m_sInstanceName;

	
	/**
	 * Queue that stores the notification messages received from other system
	 * components
	 */
	protected final AsyncQ<String[]> m_oNotifications = new AsyncQ(this);
	
	
	/**
	 * Log4j Logger
	 */
	protected Logger m_oLogger;

	
	/**
	 * Flag indicating if this component should run in test mode or not
	 */
	protected boolean m_bTest = false;
	
	protected String m_sDataPath;
	protected String m_sArchPath;
	protected String m_sTempPath;
	
	/**
	 * Default constructor. Sets the status to {@link #INIT} and sets the id
	 */
	protected BaseBlock()
	{
		m_nStatus.set(INIT);
		m_nId = m_nIdCount.getAndIncrement(); //set Id
	}

	
	/**
	 * Called by {@link Directory} once all of the BaseBlocks this BaseBlock is 
	 * subscribed have finished their start up sequence. It calls {@link #reset()}
	 * and {@link #start()}. If no problems occur then the the block's status will
	 * be {@link #IDLE}
	 */
	public void startService()
	{
		int nStatus = m_nStatus.get();
		if (nStatus == INIT || m_nStatus.get() == STOPPED) //to start the status must be STARTING or STOPPED
		{
			try
			{
				checkAndSetStatus(STARTING, OVERRIDESTATUS); //if status is STOPPED set it to STARTING
//				reset(); //sets all of the configurable variables
				m_oLogger.info("Calling start()");
				if (m_nStatus.get() == STARTING && start()) // make sure an error didn't happen in reset and call the overridable start()
				{
					m_oLogger.info("Exiting start()");
					checkAndSetStatus(IDLE, OVERRIDESTATUS); // set status to IDLE
					notify("service started");
				}
				else
				{
					m_oLogger.error("start() returned false. Setting status to ERROR");
					checkAndSetStatus(ERROR, OVERRIDESTATUS); // set status to ERROR
				}
			}
			catch (Exception oException)
			{
				m_oLogger.error("Error starting service");
				m_oLogger.error(oException, oException);
				checkAndSetStatus(ERROR, OVERRIDESTATUS); // set status to ERROR
			}
		}
		else
		{
			m_oLogger.error("Cannot start. Current status: " + STATUSES[m_nStatus.get()]);
		}
	}

	
	/**
	 * This function is to be overridden by each BaseBlock to implement what 
	 * the block needs to do to start its service
	 * @return true if no Exceptions are thrown
	 * @throws Exception
	 */
	public boolean start() throws Exception
	{
		return true;
	}
	
	
	/**
	 * Initializes the BaseBlock, wrapper for {@link #setLogger()}}
	 */
	@Override
	public void init()
		throws ServletException
	{
		try
		{
			ServletConfig oSConfig = getServletConfig();
			if (oSConfig != null) // if there is a ServletConfig this instance was created by tomcat
			{
				if (Directory.getInstance().canRegister(oSConfig.getServletName()))
				{
					setName(oSConfig.getServletName());
					setLogger();
					reset();
					register();
				}
				else
				{
					throw new ServletException("Configured to not run");
				}
			}
			else
			{
				setLogger();
				reset();
			}
		}
		catch (IOException oEx)
		{
			throw new ServletException(oEx);
		}
	}
	
	
	/**
	 * Wrapper for {@link Directory#notifyBlocks(java.lang.String[])}, sending
	 * the notification message created from the message name and resource to
	 * the BaseBlocks subscribed to this block.
	 * 
	 * @param sMessageName type of message being sent
	 * @param sResource resources of the message
	 */
	public void notify(String sMessageName, String... sResource)
	{
		String[] sMessage = new String[2 + sResource.length];
		sMessage[FROM] = getName();
		sMessage[MESSAGE] = sMessageName;
		System.arraycopy(sResource, 0, sMessage, 2, sResource.length);
		Directory.getInstance().notifyBlocks(sMessage);
	}
	
	
	/**
	 * Wrapper for {@link AsyncQ#queue(java.lang.Object)}, queuing the given
	 * notification message to be processed.
	 * @param sMessage Notification message from another BaseBlock this block is
	 * subscribed to.
	 */
	public void receive(String[] sMessage)
	{
		m_oNotifications.queue(sMessage);
	}
	
	
	/**
	 * Registers the BaseBlock to the {@link imrcp.system.Directory}
	 */
	public void register()
		throws IOException
	{
		JSONObject oBlockConfig = Directory.getInstance().getConfig(getClass().getName(), m_sInstanceName);
		Directory.getInstance().register(this, JSONUtil.optJSONArray(oBlockConfig, "subscribe"));
	}


	
	/**
	 * Gets a long array with the current status of the block and the last time
	 * the status changed.
	 * @return [status, time in milliseconds since Epoch the since last changed]
	 */
	public long[] status()
	{
		return new long[]{m_nStatus.get(), m_lStatusChanged.get()};
	}

	
	/**
	 * Wrapper for {@link #stopService}, this gets called when the container
	 * managing servlets is terminating the servlet
	 */
	@Override
	public void destroy()
	{
		stopService();
	}

	
	/**
	 * This method stops the service of this BaseBlock. If this block has a
	 * task scheduled for execution with {@link Scheduling}, that task is 
	 * canceled. {@link #stop()} which is overriden by child classes is called
	 * and this block status is changed to {@link #STOPPED}
	 */
	public void stopService()
	{
		if (m_nStatus.get() == STOPPED) //can't stop an BaseBlock that is STOPPED
		{
			m_oLogger.error("Cannot stop service because it is already stopped");
			return;
		}
		try
		{
			checkAndSetStatus(STOPPING, OVERRIDESTATUS); //set status to STOPPING
			if (Scheduling.getInstance().cancelSched(this, m_nSchedId)) //cancel scheduled task
				m_oLogger.info("Task canceled");
			m_oNotifications.stop();

			if (stop()) //call the child's overridable stop
				checkAndSetStatus(STOPPED, OVERRIDESTATUS); //set status to STOPPED
			else
				checkAndSetStatus(ERROR, OVERRIDESTATUS); //set status to ERROR
			notify("service stopped");
		}
		catch (Exception oException)
		{
			m_oLogger.error("Error while trying to stop service");
			m_oLogger.error(oException, oException);
			checkAndSetStatus(ERROR, OVERRIDESTATUS); //set status to ERROR
		}
	}

	
	/**
	 * This function is to be overridden by each BaseBlock to implement what 
	 * the block needs to do to stop its service and clean up resources.
	 * @return true if no Exceptions are thrown
	 * @throws Exception
	 */
	public boolean stop() throws Exception
	{
		return true;
	}


	/**
	 * Sets configurable member variables from the BlockConfig object, should be
	 * overridden by child classes.
	 */
	protected void reset() 
		throws IOException
	{
		ArrayList<String> oNames = new ArrayList();
		oNames.add(m_sInstanceName);
		Class oClass = getClass();
		oNames.add(oClass.getName());
		while ((oClass = oClass.getSuperclass()) != HttpServlet.class)
		{
			oNames.add(oClass.getName());
		}
		Collections.reverse(oNames);
		String[] sConfigNames = new String[oNames.size()];
		for (int nIndex = 0; nIndex < sConfigNames.length; nIndex++)
			sConfigNames[nIndex] = oNames.get(nIndex);
		reset(Directory.getInstance().getConfig(sConfigNames));
	}

	
	protected void reset(JSONObject oBlockConfig)
	{
		m_sDataPath = oBlockConfig.getString("datapath");
		if (!m_sDataPath.endsWith("/"))
			m_sDataPath += "/";
		
		m_sArchPath = oBlockConfig.getString("archpath");
		if (!m_sArchPath.endsWith("/"))
			m_sArchPath += "/";
		
		m_sTempPath = oBlockConfig.getString("temppath");
		if (!m_sTempPath.endsWith("/"))
			m_sTempPath += "/";
	}

	
	public ObsList getData(int nType, long lStartTime, long lEndTime,
	   int nStartLat, int nEndLat, int nStartLon, int nEndLon, long lRefTime)
	{
		return getData(nType, lStartTime, lEndTime, nStartLat, nEndLat, nStartLon, nEndLon, lRefTime, new int[0]);
	}
	
	/**
	 * This method creates a new {@link ImrcpObsResultSet} and if the status 
	 * of the block is {@link #RUNNING} or {@link #IDLE} calls 
	 * {@link #getData(imrcp.store.ImrcpResultSet, int, long, long, int, int, int, int, long)}
	 * 
	 * @param nType IMRCP observation type id
	 * @param lStartTime start time of the query in milliseconds since Epoch
	 * @param lEndTime end time of the query in milliseconds since Epoch
	 * @param nStartLat lower bound of latitude in decimal degrees scaled to 7 
	 * decimal places
	 * @param nEndLat upper bound of latitude in decimal degrees scaled to 7 
	 * decimal places
	 * @param nStartLon lower bound of longitude in decimal degrees scaled to 7 
	 * decimal places
	 * @param nEndLon upper bound of longitude in decimal degrees scaled to 7 
	 * decimal places
	 * @param lRefTime reference time in milliseconds since Epoch (observations 
	 * received after this time will not be included).
	 */
	public ObsList getData(int nType, long lStartTime, long lEndTime,
	   int nStartLat, int nEndLat, int nStartLon, int nEndLon, long lRefTime, int[] nContribAndSources)
	{
		ObsList oReturn = new ObsList();
		int nStatus = m_nStatus.get();
		if (nStatus == RUNNING || nStatus == IDLE)
			getData(oReturn, nType, lStartTime, lEndTime, nStartLat, nEndLat, nStartLon, nEndLon, lRefTime, nContribAndSources);
		return oReturn;
	}


	/**
	 * This method is meant to be overridden by children classes to implement
	 * how the block returns data. The ImrcpResultSet that is passed as the
	 * first parameter is filled with the obs from the block that match the 
	 * query parameters
	 *
	 * @param oReturn ImrcpResultSet that will be filled with obs
	 * @param nType IMRCP observation type id
	 * @param lStartTime start time of the query in milliseconds since Epoch
	 * @param lEndTime end time of the query in milliseconds since Epoch
	 * @param nStartLat lower bound of latitude in decimal degrees scaled to 7 
	 * decimal places
	 * @param nEndLat upper bound of latitude in decimal degrees scaled to 7 
	 * decimal places
	 * @param nStartLon lower bound of longitude in decimal degrees scaled to 7 
	 * decimal places
	 * @param nEndLon upper bound of longitude in decimal degrees scaled to 7 
	 * decimal places
	 * @param lRefTime reference time in milliseconds since Epoch (observations 
	 * received after this time will not be included)
	 */
	protected void getData(ObsList oReturn, int nType, long lStartTime, long lEndTime,
	   int nStartLat, int nEndLat, int nStartLon, int nEndLon, long lRefTime, int[] nContrib)
	{
	}


	/**
	 * This method is usually called as a fixed interval execution by {@link Scheduling}.
	 * If this block's status is {@link #IDLE} then {@link #execute()} 
	 * (or {@link #executeTest()} if in test mode) is called and its status is 
	 * changed to {@link #RUNNING}. If no errors occur during {@link #execute()} 
	 * then the status is changed back to {@link #IDLE}
	 */
	@Override
	public void run()
	{
		if (checkAndSetStatus(RUNNING, IDLE)) // check if IDLE, if it is set to RUNNING and execute the block's task
		{
			if (m_bTest)
				executeTest();
			else
				execute();
			checkAndSetStatus(IDLE, RUNNING); // if still RUNNING, set back to IDLE
		}
		else
			m_oLogger.info(String.format("%s didn't run. Status was %s", m_sInstanceName, STATUSES[m_nStatus.get()]));
	}
	
	
	protected ThreadPoolExecutor createThreadPool(int nThreads)
	{
		return (ThreadPoolExecutor)Executors.newFixedThreadPool(nThreads, new NameableThreadFactory());
	}
	
	
	private class NameableThreadFactory implements ThreadFactory
	{
		@Override
		public Thread newThread(Runnable r)
		{
			return new Thread(r, String.format("%s-%d", getName(), System.nanoTime()));
		}
	}
	
	
	/**
	 * Sets the status of the block to {@link #ERROR}
	 */
	protected synchronized void setError()
	{
		checkAndSetStatus(ERROR, OVERRIDESTATUS);
	}
	
	
	/**
	 * Sets the status of the block to {@code nStatus} as long as the current
	 * status is {@code nCheck}. If {@code nCheck} is {@link #OVERRIDESTATUS} then
	 * the status is set to {@code nStatus} regardless of the current status.
	 * 
	 * @param nStatus value to set the status to
	 * @param nCheck value to check the current status to
	 * @return true if the status is set to {@code nStatus}, otherwise false
	 */
	protected synchronized boolean checkAndSetStatus(int nStatus, int nCheck)
	{
		if (nCheck == OVERRIDESTATUS) // always set the status if the check is override
		{
			
			m_nStatus.set(nStatus);
			m_lStatusChanged.set(System.currentTimeMillis());
			if (nStatus == ERROR)
				m_oLogger.error("Status set to ERROR");
			return true;
		}
		else
		{
			if (m_nStatus.compareAndSet(nCheck, nStatus)) // otherwise check if the current status is the check value, and if it is set it to the status value
			{
				m_lStatusChanged.set(System.currentTimeMillis());
				if (nStatus == ERROR)
					m_oLogger.error("Status set to ERROR");
				return true;
			}
			return false;
		}
	}

	
	/**
	 * Child classes implement this function to execute their process on a fixed
	 * interval schedule.
	 */
	public void execute()
	{
	}

	
	/**
	 * Child classes implement this function to execute their process on a fixed
	 * interval schedule if the {@link #m_bTest} flag is true.
	 */
	public void executeTest()
	{
	}


	/**
	 * Gets the instance name of this block
	 * @return The instance name of the block, {@link #m_sInstanceName}
	 */
	public String getName()
	{
		return m_sInstanceName;
	}
	
	
	public String getDataPath()
	{
		return m_sDataPath;
	}


	/**
	 * Sets the instance name of this block
	 * @param sName The instance name of the block
	 */
	public void setName(String sName)
	{
		m_sInstanceName = sName;
	}

	
	/**
	 * Sets the Log4J logger object
	 */
	public void setLogger()
	{
		// should fix the properties file later
		m_oLogger = LogManager.getLogger("imrcp." + m_sInstanceName); // append "imrcp." because of how the properties file for log4j is set up right now.
	}


	/**
	 * This method is called when a notification from another block. If the
	 * status of this block is {@link #IDLE} then {@link #process(java.lang.String[])}
	 * is called, otherwise the notification is placed back in the queue.
	 * @param e Notification message from another block
	 */
	@Override
	public void run(String[] e)
	{
		if (checkAndSetStatus(RUNNING, IDLE)) // process the notification if the status is IDLE and set status to RUNNING
		{
			process(e);
			checkAndSetStatus(IDLE, RUNNING); // if still RUNNING, set back to IDLE
		}
		else
		{
			int nStatus = m_nStatus.get();
			if (nStatus == STARTING || nStatus == RUNNING || nStatus == IDLE || nStatus == INIT) // if the block is starting or running requeue the notification to be processed later
				m_oNotifications.queue(e);
		}
	}	

	
	/**
	 * Child classes implement this function to handle notification messages 
	 * from other blocks.
	 * @param e Notification message from another block
	 */
	public void process(String[] e)
	{
	}
}
