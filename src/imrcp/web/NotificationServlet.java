package imrcp.web;

/**
 * This servlet manages requests for getting Notifications on the IMRCP Map UI.
 * @author aaron.cherney
 */
public class NotificationServlet extends SecureBaseBlock
{
//	/**
//	 * Format string used to create the JSON response.
//	 */
//	private static final String JSON
//	   = "\n\t{\"id\":%d,\"typeName\":\"%s\",\"typeId\":\"%d\"," + "\"start\":%d,\"end\":%d,\"cleared\":%d," + "\"description\":\"%s\"," + "\"issued\":%d," + "\"lat1\":%.6f,\"lon1\":%.6f,\"lat2\":%.6f,\"lon2\":%.6f}";
//
//
//	@Override
//	public void reset(JSONObject oBlockConfig)
//	{
//		super.reset(oBlockConfig);
//		m_bTest = oBlockConfig.optBoolean("test", false);
//	}
//
//	
//	/**
//	 * Queries the data stores for notifications and adds them to the response.
//	 * 
//	 * @param iReq object that contains the request the client has made of the servlet
//	 * @param iRep object that contains the response the servlet sends to the client
//	 * @param oSession object that contains information about the user that made the
//	 * request
//	 * @return HTTP status code to be included in the response.
//	 */
//	public int doNotify(HttpServletRequest iReq, HttpServletResponse iRep, Session oSession, ClientConfig oClient)
//	{
//		if (!m_bTest)
//		{
//			try
//			{
//				iRep.setContentType("application/json");
//				long lCurrentTime = System.currentTimeMillis();
//				lCurrentTime = (lCurrentTime / 60000) * 60000;
//				TileObsView oOV = (TileObsView) Directory.getInstance().lookup("ObsView");
//				ArrayList<Obs> oNotifications = new ArrayList();
//				WayNetworks oWayNetworks = (WayNetworks)Directory.getInstance().lookup("WayNetworks");
//				for (String sNetwork : oSession.m_oProfile.m_sNetworks) // get notification for each Network the user has access to
//				{
//					Network oNetwork = oWayNetworks.getNetwork(sNetwork);
//					int[] nBb = oNetwork.getBoundingBox();
//					ObsList oData = oOV.getData(ObsType.NOTIFY, lCurrentTime, lCurrentTime + 28800000, nBb[1], nBb[3], nBb[0], nBb[2], System.currentTimeMillis());
//
//					for (Obs oObs : oData)
//					{
//						oNotifications.add(oObs);
//					}
//				}
//
//				try (BufferedWriter oOut = new BufferedWriter(new OutputStreamWriter(iRep.getOutputStream())))
//				{
//					oOut.write("["); // start json array
//					int nMaxIndex = oNotifications.size() - 1;
//					for (int i = 0; i <= nMaxIndex; i++) // write each notification found to the response
//					{
//						Obs oNotification = oNotifications.get(i);
//						oOut.write(format(getId(oNotification), ObsType.lookup(ObsType.NOTIFY, (int)oNotification.m_dValue), (int)oNotification.m_dValue, (oNotification.m_lObsTime1 / 60000) * 60000, (oNotification.m_lObsTime2 / 60000) * 60000, oNotification.m_lClearedTime, oNotification.m_sDetail.replace(";", "<br/>"), oNotification.m_lTimeRecv, oNotification.m_nLat1, oNotification.m_nLon1, oNotification.m_nLat2, oNotification.m_nLon2));
//						if (i < nMaxIndex)
//							oOut.write(",");
//					}
//
//					oOut.write("\n]\n"); // finish JSON array
//				}
//			}
//			catch (Exception oEx)
//			{
//				m_oLogger.error(oEx, oEx);
//				return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
//			}
//		}
//		else // test data
//		{
//			iRep.setContentType("application/json");
//			String[] sUriParts = iReq.getRequestURI().split("/");
//
//			long lRefTime = Long.parseLong(sUriParts[sUriParts.length - 2]);
//			long lCurrentTime = Long.parseLong(sUriParts[sUriParts.length - 1]);
//			Calendar oCal = new GregorianCalendar(Directory.m_oUTC);
//			oCal.setTimeInMillis(lCurrentTime);
//			long[] lTimeExtents = new long[6];
//			lTimeExtents[0] = oCal.getTimeInMillis() + 60000; // first minute
//			lTimeExtents[1] = oCal.getTimeInMillis() + 180000; // 2 minutes after [0]
//			lTimeExtents[2] = lTimeExtents[0] + 60000; // second minute
//			lTimeExtents[3] = lTimeExtents[2] + 3600000; // 1 hour after [2]
//			lTimeExtents[4] = lTimeExtents[0] + 120000; // third minute
//			lTimeExtents[5] = lTimeExtents[4] + 1200000; // 20 minutes after [4]
//
//			try (BufferedWriter oOut = new BufferedWriter(new OutputStreamWriter(iRep.getOutputStream())))
//			{
//				oOut.write("["); // start json array
//
//				int nTimeIndex = (int)(lCurrentTime % 300000) / 60000;
//
//				if (nTimeIndex == 0)
//				{
//
//				}
//
//				if (nTimeIndex == 1)
//				{
//					lTimeExtents[0] = oCal.getTimeInMillis() - 3600000;
//					lTimeExtents[1] = oCal.getTimeInMillis() + 120000;
//					oOut.write(format(1, "unusual-congestion", 307, lTimeExtents[0], lTimeExtents[1], Long.MIN_VALUE, "I-435 W Between Metcalf Ave & Antioch Rd", 389338590 - 10000, -946767330 - 10000, 389338590 + 10000, -946767330 + 10000));
//				}
//
//				if (nTimeIndex == 2)
//				{
//					lTimeExtents[0] = oCal.getTimeInMillis() - 3540000;
//					lTimeExtents[1] = oCal.getTimeInMillis() + 60000;
//					lTimeExtents[2] = oCal.getTimeInMillis();
//					lTimeExtents[3] = lTimeExtents[2] + 1800000;
//					oOut.write(format(1, "unusual-congestion", 307, lTimeExtents[0], lTimeExtents[1], Long.MIN_VALUE, "I-435 W Between Metcalf Ave & Antioch Rd", 389338590 - 10000, -946767330 - 10000, 389338590 + 10000, -946767330 + 10000));
//					oOut.write(",");
//					oOut.write(format(2, "heavy-rain", 106, lTimeExtents[2], lTimeExtents[3], Long.MIN_VALUE, "3 Locations", 388689479, -947734071, 389867615, -946212850));
//					oOut.write(",");
//					oOut.write(format(3, "heavy-rain", 106, lTimeExtents[2], lTimeExtents[3], Long.MIN_VALUE, "500 Locations", 388686844, -946217087, 389863657, -944694177));
//				}
//
//				if (nTimeIndex == 3)
//				{
//					lTimeExtents[0] = oCal.getTimeInMillis() - 3480000;
//					lTimeExtents[1] = oCal.getTimeInMillis();
//					lTimeExtents[2] = oCal.getTimeInMillis() - 60000;
//					lTimeExtents[3] = lTimeExtents[2] + 1740000;
//					lTimeExtents[4] = oCal.getTimeInMillis();
//					lTimeExtents[5] = lTimeExtents[4] + 1200000;
//
//					oOut.write(format(2, "heavy-rain", 106, lTimeExtents[2], lTimeExtents[3], Long.MIN_VALUE, "3 Locations", 388689479, -947734071, 389867615, -946212850));
//					oOut.write(",");
//					oOut.write(format(3, "heavy-rain", 106, lTimeExtents[2], lTimeExtents[3], Long.MIN_VALUE, "500 Locations", 388686844, -946217087, 389863657, -944694177));
//					oOut.write(",");
//					oOut.write(format(4, "icy-bridge", 202, lTimeExtents[4], lTimeExtents[5], Long.MIN_VALUE, "E Blue Ridge Blvd WB<br/>E Blue Ridge Blvd EB", 388894572 - 10000, -945806132 - 10000, 388894572 + 10000, -945806132 + 10000));
//				}
//
//				if (nTimeIndex == 4)
//				{
//					lTimeExtents[2] = oCal.getTimeInMillis() - 120000;
//					lTimeExtents[3] = lTimeExtents[2] + 1680000;
//					lTimeExtents[4] = oCal.getTimeInMillis() - 60000;
//					lTimeExtents[5] = lTimeExtents[4] + 1140000;
//
//					oOut.write(format(2, "heavy-rain", 106, lTimeExtents[2], lTimeExtents[3], oCal.getTimeInMillis(), "3 Locations", 388689479, -947734071, 389867615, -946212850));
//					oOut.write(",");
//					oOut.write(format(3, "heavy-rain", 106, lTimeExtents[2], lTimeExtents[3], oCal.getTimeInMillis(), "500 Locations", 388686844, -946217087, 389863657, -944694177));
//					oOut.write(",");
//					oOut.write(format(4, "icy-bridge", 202, lTimeExtents[4], lTimeExtents[5], Long.MIN_VALUE, "E Blue Ridge Blvd WB<br/>E Blue Ridge Blvd EB", 388894572 - 10000, -945806132 - 10000, 388894572 + 10000, -945806132 + 10000));
//				}
//
//				oOut.write("\n]\n"); // finish JSON array
//			}
//			catch (Exception oEx)
//			{
//				m_oLogger.error(oEx, oEx);
//				return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
//			}
//		}
//		return HttpServletResponse.SC_OK;
//	}
//
//	
//	/**
//	 * Formats the given values using the format String {@link #JSON}.
//	 * @param args [id as a long, notification type as String, notification type as integer,
//	 * start time in milliseconds since Epoch as long, end time in milliseconds since Epoch as long,
//	 * cleared time in milliseconds since Epoch as long, location name as String,
//	 * min lat, min lon, max lat, max lon] all geo coordinates are in decimal
//	 * degrees scaled to 7 decimal places.
//	 * @return
//	 */
//	private String format(Object... args)
//	{
//		for (int i = args.length - 4; i < args.length; ++i)
//			args[i] = GeoUtil.fromIntDeg((Integer) args[i]);
//
//		if (m_bTest)
//		{
//			Object[] newArgs = new Object[12];
//			System.arraycopy(args, 0, newArgs, 0, 7);
//			System.arraycopy(args, 7, newArgs, 8, 4);
//			newArgs[7] = (Long) args[3] - 1000 * 60 * 15;
//			return String.format(JSON, newArgs);
//		}
//		return String.format(JSON, args);
//	}
//
//	
//	/**
//	 * Creates an Id for the Notification using {@link CRC32}
//	 * @param oNotification The notification to create an id for
//	 * @return an id generate from field of the notification using CRC32
//	 * @throws Exception
//	 */
//	private long getId(Obs oNotification) throws Exception
//	{
//		ByteArrayOutputStream oOutBytes = new ByteArrayOutputStream();
//		DataOutputStream oOutData = new DataOutputStream(oOutBytes);
//		oOutData.writeInt((int)oNotification.m_dValue);
//		oOutData.writeLong(oNotification.m_lTimeRecv);
//		oOutData.writeLong(oNotification.m_lObsTime1);
//		int nLocations = 1;
//		if (oNotification.m_sDetail.contains("Location"))
//			nLocations = Integer.parseInt(oNotification.m_sDetail.substring(0, oNotification.m_sDetail.indexOf(" ")));
//		else if (oNotification.m_sDetail.contains(";"))
//			nLocations = 2;
//		oOutData.writeInt(nLocations);
//		oOutData.writeInt(oNotification.m_nLat1);
//		oOutData.writeInt(oNotification.m_nLon1);
//		oOutData.writeInt(oNotification.m_nLat2);
//		oOutData.writeInt(oNotification.m_nLon2);
//		CRC32 oCRC32 = new CRC32();
//		oCRC32.update(oOutBytes.toByteArray());
//		return oCRC32.getValue();
//	}
}
