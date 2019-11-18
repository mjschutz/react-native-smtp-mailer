
package com.reactlibrary;

import android.os.AsyncTask;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import java.security.AccessController;
import java.security.Provider;
import java.security.Security;
import java.util.Date;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;

import javax.mail.Message;
import javax.mail.Address;
import javax.mail.Folder;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.BodyPart;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class RNIMapModule extends ReactContextBaseJavaModule {

    private final ReactApplicationContext reactContext;
	private IMapConnect imapConn;

    public RNIMapModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "RNIMap";
    }

    @ReactMethod
    public void connect(final ReadableMap obj, final Promise promise) {
        AsyncTask.execute(new Runnable() {

            String hostname = obj.getString("hostname");
			String port = (obj.getType("port") == ReadableType.Number) ? String.valueOf((int)obj.getDouble("port")) : obj.getString("port");
            Boolean ssl = obj.hasKey("ssl") ? obj.getBoolean("ssl") : false;
            String username = obj.getString("username");
            String password = obj.getString("password");

            @Override
            public void run() {
                try {
                    imapConn = new IMapConnect(username, password, hostname, port, ssl);

                    WritableMap success = new WritableNativeMap();
                    success.putString("status", "SUCCESS");
                    promise.resolve(success);
                } catch (Exception e) {
                    promise.reject("ERROR", e.getMessage());
                }
            }
        });
    }
	
	@ReactMethod
    public void getMails(final ReadableMap obj, final Promise promise) {
        AsyncTask.execute(new Runnable() {
			String folder = obj.getString("folder");
			
            @Override
            public void run() {
                try {
					Message[] messages = imapConn.getMessages(folder);

                    WritableArray promiseArray=Arguments.createArray();
					for(int i=0;i < messages.length; i++){
						WritableMap message = new WritableNativeMap();
						message.putInt("id", messages[i].getMessageNumber());
						message.putString("from", messages[i].getFrom().toString());
						message.putString("subject", messages[i].getSubject());
						message.putString("content", getTextFromMessage(messages[i]));
						promiseArray.pushMap(message);
					}
					promise.resolve(promiseArray);
                } catch (Exception e) {
                    promise.reject("ERROR", e.getMessage());
                }
            }
        });
    }
	
	private String getTextFromMessage(Message message) throws Exception {
		String result = "";
		if (message.isMimeType("text/plain")) {
			result = message.getContent().toString();
		} else if (message.isMimeType("multipart/*")) {
			MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
			result = getTextFromMimeMultipart(mimeMultipart);
		}
		return result;
	}

	private String getTextFromMimeMultipart(MimeMultipart mimeMultipart) throws Exception {
		String result = "";
		int count = mimeMultipart.getCount();
		for (int i = 0; i < count; i++) {
			BodyPart bodyPart = mimeMultipart.getBodyPart(i);
			if (bodyPart.isMimeType("text/plain")) {
				result = result + "\n" + bodyPart.getContent();
				break; // without break same text appears twice in my tests
			} else if (bodyPart.isMimeType("text/html")) {
				String html = (String) bodyPart.getContent();
				result = result + "\n" + org.jsoup.Jsoup.parse(html).text();
			} else if (bodyPart.getContent() instanceof MimeMultipart){
				result = result + getTextFromMimeMultipart((MimeMultipart)bodyPart.getContent());
			}
		}
		return result;
	}
}

class IMapConnect extends javax.mail.Authenticator {

    private String hostname;
    private String user;
    private String password;
    private Session session;
	private Store store;
    private String port;
    private Boolean ssl;
    private Multipart _multipart = new MimeMultipart();

    static {
        Security.addProvider(new JSSEProvider());
    }

    public IMapConnect(String user, String password, String hostname, String port, Boolean ssl) throws Exception {
        this.user = user;
        this.password = password;
        this.hostname = hostname;
		this.port = port;
		
		Properties props = new Properties();
		//IMAPS protocol
		props.setProperty("mail.store.protocol", "imaps");
		//Set host address
		props.setProperty("mail.imaps.host", hostname);
		//Set specified port
		props.setProperty("mail.imaps.port", port);
		//Using SSL
		if (ssl) {
            props.setProperty("mail.imaps.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else {
            props.setProperty("mail.imaps.starttls.enable", "true");
        }
		props.setProperty("mail.imaps.socketFactory.fallback", "false");
		//Setting IMAP session
		session = Session.getDefaultInstance(props, null);

		store = session.getStore("imaps");
		//Connect to server by sending username and password.
		//Example mailServer = imap.gmail.com, username = abc, password = abc
		store.connect(hostname, user, password);
    }

    protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(user, password);
    }

    public Message[] getMessages(String folder) throws Exception {
		//Get all mails in Inbox Forlder
		Folder inbox = store.getFolder(folder);
		inbox.open(Folder.READ_ONLY);
		//Return result to array of message
		return inbox.getMessages();
	}
}
