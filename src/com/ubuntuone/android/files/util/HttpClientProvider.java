package com.ubuntuone.android.files.util;

import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import com.ubuntuone.android.files.Preferences;
import com.ubuntuone.android.files.UbuntuOneFiles;

public class HttpClientProvider {
	private static DefaultHttpClient httpClient;
	
	private HttpClientProvider() {
	}
	
	public static DefaultHttpClient getInstance() {
		if (httpClient == null) {
			httpClient = newInstance();
		}
		return httpClient;
	}
	
	public static DefaultHttpClient newInstance() {
		HttpParams params = new BasicHttpParams();
		
		HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
		HttpProtocolParams.setContentCharset(params, "UTF-8");
		HttpProtocolParams.setUseExpectContinue(params, true);
		HttpProtocolParams.setUserAgent(params, String.format("%s/%s",
				UbuntuOneFiles.class.getPackage(),
				Preferences.getCurrentVersionName(UbuntuOneFiles.getInstance())
		));
		
		HttpConnectionParams.setStaleCheckingEnabled(params, false);
		HttpConnectionParams.setConnectionTimeout(params, 20 * 1000);
		HttpConnectionParams.setSoTimeout(params, 20 * 1000);
		HttpConnectionParams.setSocketBufferSize(params, 8192);
		
		HttpClientParams.setRedirecting(params, false);
		
		SchemeRegistry schreg = new SchemeRegistry();
		schreg.register(
				new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		schreg.register(
				new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
		ClientConnectionManager connectionManager =
				new ThreadSafeClientConnManager(params, schreg);
		
		DefaultHttpClient client =
				new DefaultHttpClient(connectionManager, params);
		
		return client;
	}
	
	public static void safeShutdown(HttpClient client) {
		if (client != null && client.getConnectionManager() != null) {
			client.getConnectionManager().shutdown();
		}
	}
}
