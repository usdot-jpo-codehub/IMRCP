package imrcp.system;

import java.util.Date;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.json.JSONObject;

/**
 * 
 * @author aaron.cherney
 */
public class Emails extends BaseBlock
{
	private String m_sPw;
	private String m_sEmail;
	private int m_nSmtpPort;
	private String m_sSmtpHost;
	private String m_sDomain;
	private String m_sFrom;
	private String m_sSupportEmail;
		
	
	@Override
	public void reset(JSONObject oBlockConfig)
	{
		super.reset(oBlockConfig);
		m_sEmail = oBlockConfig.getString("email");
		m_sPw = oBlockConfig.getString("pw");
		m_nSmtpPort = oBlockConfig.optInt("smtpport", 587);
		m_sSmtpHost = oBlockConfig.getString("smtphost");
		m_sDomain = oBlockConfig.getString("domain");
		m_sFrom = oBlockConfig.optString("from", "donotreply@" + m_sDomain);
		m_sSupportEmail = oBlockConfig.optString("support", null);
	}
	
	
	/**
	 * Sends the given email
	 * @param oEmail Email to send
	 * @throws Exception
	 */
	public void send(String sSubject, String sBody, String... sTo) throws Exception
	{
		InternetAddress[] oAddresses = new InternetAddress[sTo.length]; // create the necessary address objects
		for (int i = 0; i < oAddresses.length; i++)
			oAddresses[i] = new InternetAddress(sTo[i]);
		
		if (oAddresses.length == 0)
		{
			m_oLogger.info("send() invoked with empty recipients");
			return;
		}
		Properties oProps = new Properties();
		oProps.put("mail.smtp.auth", "true");
		oProps.put("mail.smtp.starttls.enable", "true");
		oProps.put("mail.smtp.host", m_sSmtpHost);
		oProps.put("mail.smtp.port", Integer.toString(m_nSmtpPort));
		oProps.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
		Session oSession = Session.getInstance(oProps, new Authenticator() 
		{
			final String strUsername = m_sEmail;
			final String strPassword = m_sPw;

			@Override
			protected PasswordAuthentication getPasswordAuthentication() 
			{
				return new PasswordAuthentication(strUsername, strPassword);
			}
		});

		// construct the email
		try
		{
			MimeMessage oMessage = new MimeMessage(oSession);
			oMessage.setSentDate(new Date());
			oMessage.setRecipients(Message.RecipientType.TO, oAddresses);

			oMessage.setSubject(sSubject);
			oMessage.setText(sBody);
			oMessage.setFrom(m_sFrom);
			
			Transport oTransport = oSession.getTransport("smtp");
			oTransport.connect();
			oTransport.sendMessage(oMessage, oMessage.getAllRecipients());
			oTransport.close();
		}
		catch (Exception oEx)
		{
			m_oLogger.error(oEx, oEx);
		}
    }
	
	
	public String getDomain()
	{
		return m_sDomain;
	}
	
	
	public String getSupportEmail()
	{
		return m_sSupportEmail;
	}
}
