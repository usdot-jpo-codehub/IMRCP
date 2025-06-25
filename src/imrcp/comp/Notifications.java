package imrcp.comp;

import imrcp.system.BaseBlock;
//import imrcp.store.FileCache;

/**
 * Notifications modules subscribe to Alerts modules to create Notification 
 * observations for configured alert types. The goal is of Notifications are to 
 * filter sets of Alerts to smaller sets focusing on specific alert types that
 * are useful to operators and can pop up on the map UI.
 * @author aaron.cherney
 */
public class Notifications extends BaseBlock
{
//	/**
//	 * Configurable array to store the alert types that should generate 
//	 * notifications
//	 * 
//	 * @see imrcp.system.ObsType#LOOKUP for complete list of types
//	 */
//	private int[] m_nNotificationTypes;
//
//	
//	/**
//	 * Tolerance in decimal degrees scaled to 7 decimal places used for 
//	 * spatially combining notifications
//	 */
//	private int m_nTol;
//	
//	
//	/**
//	 * Object used to create time dependent file names to save on disk
//	 */
//	private FilenameFormatter m_oFilenameFormat;
//	
//	
//	/**
//	 * How often a file should be made to store notification obs
//	 */
//	private int m_nFileFrequency;
//
//	
//	@Override
//	public void reset(JSONObject oBlockConfig)
//	{
//		m_nTol = oBlockConfig.optInt("tol", 50000);
//		m_oFilenameFormat = new FilenameFormatter(oBlockConfig.optString("format", ""));
//		m_nFileFrequency = oBlockConfig.optInt("freq", 86400000);
//		String[] sTypes = JSONUtil.getStringArray(oBlockConfig, "types");
//		m_nNotificationTypes = new int[sTypes.length];
//		for (int i = 0; i < sTypes.length; i++)
//			m_nNotificationTypes[i] = ObsType.lookup(ObsType.EVT, sTypes[i]);
//	}
//
//	
//	/**
//	 * Called when a message from {@link imrcp.system.Directory} is received. 
//	 * If the message is "new data" {@link Notifications#createNotifications(imrcp.store.FileCache)}
//	 * is called with the FileCache that sent the message.
//	 * @param sMessage [BaseBlock message is from, message name]
//	 */
//	@Override
//	public void process(String[] sMessage)
//	{
//		if (sMessage[MESSAGE].compareTo("new data") == 0)
//		{
//			FileCache oStore = (FileCache) Directory.getInstance().lookup(sMessage[FROM]);
//			createNotifications(oStore);
//		}
//	}
//
//	
//	/**
//	 * Queries the given FileCache for current alerts and generates a set of
//	 * notifications from the result set.
//	 * @param oStore Filecahe to query
//	 */
//	public void createNotifications(FileCache oStore)
//	{
//		try
//		{
//			long lTime = System.currentTimeMillis();
//			lTime = (lTime / 60000) * 60000;
//			ArrayList<NotificationSet> oNotificationSets = new ArrayList();
//			WayNetworks oWays = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
//			// generate rolling file name
//			long lStartTime = (lTime / m_nFileFrequency) * m_nFileFrequency;
//			long lEndTime = lStartTime + m_nFileFrequency * 2;
//			String sFilename = m_oFilenameFormat.format(lStartTime, lStartTime, lEndTime);
//			Path oPath = Paths.get(sFilename);
//			Files.createDirectories(oPath.getParent(), FileUtil.DIRPERS);
//			// get current alerts
//			for (Network oNetwork : oWays.getNetworks())
//			{
//				int[] nBb = oNetwork.getBoundingBox();
//				ObsList oData = oStore.getData(ObsType.EVT, lTime, lTime + 86400000, nBb[1], nBb[3], nBb[0], nBb[2], lTime); // get alerts that are valid from now to 24 hours from now
//				NotificationSet oSearch = new NotificationSet();
//				for (int nObsIndex = 0; nObsIndex < oData.size(); nObsIndex++)
//				{
//					Obs oObs = (Obs)oData.get(nObsIndex);
//					oSearch.m_dValue = oObs.m_dValue;
//					if (!makeNotification(oSearch.m_dValue)) // skip alerts that do not generate notifications
//						continue;
//
//					oSearch.m_lStartTime = oObs.m_lObsTime1;
//					oSearch.m_lEndTime = oObs.m_lObsTime2;
//					int nIndex = Collections.binarySearch(oNotificationSets, oSearch); // check if there is already a NotificationSet with the same value and timestamps
//					if (nIndex < 0)
//					{
//						nIndex = ~nIndex;
//						oNotificationSets.add(nIndex, new NotificationSet(oSearch.m_lStartTime, oSearch.m_lEndTime, oSearch.m_dValue, oObs.m_lTimeRecv));
//					}
//					NotificationSet oSet = oNotificationSets.get(nIndex);
//					int[] nLocation = new int[]
//					{
//						oObs.m_nLat1, oObs.m_nLat2, oObs.m_nLon1, oObs.m_nLon2
//					}; // lat1, lat2, lon1, lon2
//					if (nLocation[1] == Integer.MIN_VALUE) // if the alert is for a single point
//					{
//
//						OsmWay oWay = null;
//						if (Id.isSegment(oObs.m_oObjId)) // find segments by id
//							oWay = oWays.getWayById(oObs.m_oObjId);
//						if (oWay == null) // alert does not belong to a segment
//						{
//							nLocation[1] = nLocation[0];
//							nLocation[3] = nLocation[2];
//							oSet.m_sDetails.add("");
//						}
//						else // alert does belong to a segment so put the name of it in the details
//						{
//							nLocation[0] = oWay.m_nMinLat;
//							nLocation[1] = oWay.m_nMaxLat;
//							nLocation[2] = oWay.m_nMinLon;
//							nLocation[3] = oWay.m_nMaxLon;
//							oSet.m_sDetails.add(oWay.m_sName);
//						}
//					}
//					else
//						oSet.m_sDetails.add("");
//					oSet.add(nLocation);
//				}
//			
//
//				ArrayList<NotificationSet> oFinalNotifications = new ArrayList();
//				for (NotificationSet oSet : oNotificationSets) // group the locations together
//					groupLocations(oSet, oFinalNotifications);
//
//				mergeFinal(oFinalNotifications); // merge the final notifications
//				ArrayList<Obs> oNotifications = new ArrayList();
//				for (NotificationSet oFinal : oFinalNotifications)
//				{
//					String sDetail = "";
//					ArrayList<String> sDetails = oFinal.m_sDetails;
//					int nSize = sDetails.size();
//					switch (nSize) // set the detail for the Notification
//					{
//						case 1:
//							if (sDetails.get(0).length() > 0)
//								sDetail = sDetails.get(0);
//							else
//								sDetail = "1 Location";
//							break;
//						case 2:
//							if (sDetails.get(0).length() > 0 && sDetails.get(1).length() > 0)
//								sDetail = sDetails.get(0) + ";" + sDetails.get(1);
//							else
//								sDetail = "2 Locations";
//							break;
//						default:
//							sDetail = nSize + " Locations";
//							break;
//					}
//					oNotifications.add(new Obs(ObsType.NOTIFY, Integer.valueOf("imrcp", 36), Id.NULLID, oFinal.m_lStartTime, oFinal.m_lEndTime, lTime, oFinal.m_nLat1, oFinal.m_nLon1, oFinal.m_nLat2, oFinal.m_nLon2, Short.MIN_VALUE, oFinal.m_dValue, Short.MIN_VALUE, sDetail));
//				}
//
//				// append the notifications to the rolling file
//				
//				boolean bWriteHeader = !Files.exists(oPath);
//				try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oPath, FileUtil.APPENDTO, FileUtil.FILEPERS), "UTF-8")))
//				{
//					if (bWriteHeader)
//						oOut.write(Obs.CSVHEADER);
//					for (Obs oNotification : oNotifications)
//						oNotification.writeCsv(oOut);
//				}
//			}
//			notify("file download", sFilename);
//		}
//		catch (Exception oException)
//		{
//			m_oLogger.error(oException, oException);
//		}
//	}
//
//	
//	/**
//	 * Iterates through the list of given NotificationSets and determines if
//	 * the given NotificationSet can be grouped together with any in the list
//	 * by type, start time, end time, and location
//	 * @param oSet NotificationSet candidate to merge
//	 * @param oFinalNotifications list of NotificationSets oSet can be merged with
//	 */
//	private void groupLocations(NotificationSet oSet, ArrayList<NotificationSet> oFinalNotifications)
//	{
//		int nIndex = oSet.size();
//
//		// iterate backward for easy removal
//		while (nIndex-- > 0)
//		{
//			int[] nLocation = oSet.get(nIndex);
//			boolean bMerged = false;
//			for (NotificationSet oFinalNotification : oFinalNotifications)
//			{
//				if (oSet.m_dValue == oFinalNotification.m_dValue && oSet.m_lStartTime == oFinalNotification.m_lStartTime && oSet.m_lEndTime == oFinalNotification.m_lEndTime)
//				{
//					if (nLocation[1] >= oFinalNotification.m_nLat1 - m_nTol && nLocation[0] <= oFinalNotification.m_nLat2 + m_nTol && nLocation[2] <= oFinalNotification.m_nLon2 + m_nTol && nLocation[3] >= oFinalNotification.m_nLon1 - m_nTol)
//					{
//						oFinalNotification.add(nLocation);
//						oFinalNotification.m_sDetails.add(oSet.m_sDetails.get(nIndex));
//						bMerged = true;
//						break; // merge with the first available NotificationSet
//					}
//				}
//			}
//
//			if (!bMerged) // if the current location of the NotificationSet does not get merged create a new NotificationSet and add it to the list to get searched through in subsequent loops and calls of this function
//			{
//				NotificationSet oNewSet = new NotificationSet(oSet.m_lStartTime, oSet.m_lEndTime, oSet.m_dValue, oSet.m_lTimeRecv);
//				oNewSet.add(nLocation);
//				oNewSet.m_sDetails.add(oSet.m_sDetails.get(nIndex));
//				oFinalNotifications.add(oNewSet);
//			}
//
//			oSet.remove(nIndex);
//			oSet.m_sDetails.remove(nIndex);
//		}
//	}
//
//	
//	/**
//	 * Checks to see if any of the NotificationSets in the given list can be 
//	 * merged with one another
//	 * @param oFinalNotifications List of NotificationSets to try and merge
//	 */
//	private void mergeFinal(ArrayList<NotificationSet> oFinalNotifications)
//	{
//		int nIndex = oFinalNotifications.size();
//
//		while (nIndex-- > 0)
//		{
//			NotificationSet oSet = oFinalNotifications.get(nIndex);
//			int nInnerIndex = nIndex;
//			boolean bMerged = false;
//			while (nInnerIndex-- > 0)
//			{
//				NotificationSet oMergeCandidate = oFinalNotifications.get(nInnerIndex);
//				if (oSet.m_dValue == oMergeCandidate.m_dValue && oSet.m_lStartTime == oMergeCandidate.m_lStartTime && oSet.m_lEndTime == oMergeCandidate.m_lEndTime)
//				{
//					if (oMergeCandidate.m_nLat2 >= oSet.m_nLat1 - m_nTol && oMergeCandidate.m_nLat1 <= oSet.m_nLat2 + m_nTol && oMergeCandidate.m_nLon1 <= oSet.m_nLon2 + m_nTol && oMergeCandidate.m_nLon2 >= oSet.m_nLon1 - m_nTol)
//					{
//						bMerged = true;
//						for (int i = 0; i < oSet.size(); i++)
//						{
//							oMergeCandidate.add(oSet.get(i));
//							oMergeCandidate.m_sDetails.add(oSet.m_sDetails.get(i));
//						}
//					}
//
//					if (bMerged)
//					{
//						oFinalNotifications.remove(nIndex);
//						break;
//					}
//				}
//			}
//		}
//	}
//
//	
//	/**
//	 * Checks if the given alert type is configured to generate notifications
//	 * @param dValue {@link imrcp.system.ObsType#EVT} value that corresponds to
//	 * alert types
//	 * @return true if the given value is in {@link Notifications#m_nNotificationTypes}
//	 */
//	private boolean makeNotification(double dValue)
//	{
//		for (int nType : m_nNotificationTypes)
//			if (nType == dValue)
//				return true;
//
//		return false;
//	}
}
