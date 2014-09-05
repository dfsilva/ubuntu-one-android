/*
 * Ubuntu One Files - access Ubuntu One cloud storage on Android platform.
 * 
 * Copyright 2011-2012 Canonical Ltd.
 *   
 * This file is part of Ubuntu One Files.
 *  
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *  
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses 
 */

package com.ubuntuone.android.files.activity;

import greendroid.app.GDActivity;
import greendroid.widget.ActionBar;
import greendroid.widget.ActionBarItem.Type;
import greendroid.widget.LoaderActionBarItem;

import java.io.IOException;
import java.util.List;

import oauth.signpost.signature.HmacSha1MessageSigner;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;
import com.ubuntuone.android.files.Analytics;
import com.ubuntuone.android.files.Preferences;
import com.ubuntuone.android.files.R;
import com.ubuntuone.android.files.service.MetaService.Status;
import com.ubuntuone.android.files.service.MetaServiceHelper;
import com.ubuntuone.android.files.util.DetachableResultReceiver;
import com.ubuntuone.android.files.util.DetachableResultReceiver.Receiver;
import com.ubuntuone.android.files.util.Log;
import com.ubuntuone.android.files.util.NetworkUtil;
import com.ubuntuone.android.files.util.UIUtil;
import com.ubuntuone.api.sso.authorizer.Authorizer;
import com.ubuntuone.api.sso.authorizer.AuthorizerException;
import com.ubuntuone.api.sso.authorizer.OAuthAuthorizer;

@SuppressWarnings("deprecation") // TODO Update GA tracker calls.
public class StoreActivity extends GDActivity implements Receiver {
	private static final String TAG = StoreActivity.class.getSimpleName();
	
	private static final String UBUNTUONE_DOMAIN =
			"one.ubuntu.com";
	
	private static final String UBUNTUONE_URL =
			"https://" + UBUNTUONE_DOMAIN;
	
	private static final String UBUNTUONE_FROM_OAUTH_URL =
			UBUNTUONE_URL + "/api/1.0/from_oauth/";
	
	private static final String PURCHASE_STORAGE_URL =
			UBUNTUONE_URL + "/services/add-storage/?hide-hdft=1";
	
	private static final String PAYMENT_CONFIRMED_PATH =
			"/payment/confirmed";
	private static final String QUANTITY_CHANGED_PATH =
			"/qty_changed";
	
	public static final int REFRESH = 0;
	
	private static final int DIALOG_LOGGING_IN_ID = 1;
	private static final int DIALOG_UPDATING_ACCOUNT_INFO_ID = 2;
	private static final int DIALOG_NO_NETWORK_ID = 3;
	
	private GoogleAnalyticsTracker mTracker;
	
	private WebView webView;
	private Handler mHandler;
	private DetachableResultReceiver mReceiver;
	
	private LoaderActionBarItem mProgressIcon;
	
	private LoginTask mLoginTask;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setActionBarContentView(R.layout.activity_store);
		
		webView = (WebView) findViewById(R.id.webview);
		webView.setWebViewClient(new ContainedWebViewClient());
		webView.setScrollBarStyle(WebView.SCROLLBARS_INSIDE_OVERLAY);
		
		final WebSettings webSettings = webView.getSettings();
		webSettings.setJavaScriptEnabled(true);
		
		mHandler = new Handler();
		mReceiver = new DetachableResultReceiver(mHandler);
		
		setUpActionBar();
		
		if (!NetworkUtil.isConnected(this)) {
			showDialog(DIALOG_NO_NETWORK_ID);
			return;
		}
		
		mLoginTask = new LoginTask();
		mLoginTask.execute();
		
		mTracker = GoogleAnalyticsTracker.getInstance();
		mTracker.start(Analytics.U1F_ACCOUNT, this);
		mTracker.trackPageView(TAG);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		mReceiver.setReceiver(this);
	}
	
	@Override
	protected void onPause() {
		mReceiver.detach();
		super.onPause();
	}
	
	@Override
	protected void onDestroy() {
		if (mTracker != null) {
			mTracker.dispatch();
			mTracker.stop();
		}
		super.onDestroy();
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog;
		switch (id) {
		case DIALOG_LOGGING_IN_ID:
			dialog = buildLoggingInDialog();
			break;
		case DIALOG_UPDATING_ACCOUNT_INFO_ID:
			dialog = buildUpdatingAccountInfoDialog();
			break;
		case DIALOG_NO_NETWORK_ID:
			dialog = buildNoNetworkDialog();
			break;
		default:
			dialog = null;
			break;
		}
		return dialog;
	}
	
	private void setUpActionBar() {
		final ActionBar actionBar = getActionBar();
		actionBar.setType(ActionBar.Type.Dashboard);
		
		// Set home button drawable.
		final ImageButton homeButton =
			(ImageButton) getActionBar().findViewById(
					R.id.gd_action_bar_home_item);
		homeButton.setImageResource(R.drawable.u1_logo);
		
		// Add indeterminate progress indicator.
		addActionBarItem(Type.Refresh);
		mProgressIcon = (LoaderActionBarItem) actionBar.getItem(REFRESH);
		mProgressIcon.setLoading(true);
		// Initially hide it.
		hideProgress();
	}
	
	private void showProgress() {
		runOnUiThread(new Runnable() {
			public void run() {
				if (mProgressIcon != null)
					mProgressIcon.getItemView().setVisibility(View.VISIBLE);
			}
		});
	}
	
	private void hideProgress() {
		runOnUiThread(new Runnable() {
			public void run() {
				if (mProgressIcon != null)
					mProgressIcon.getItemView().setVisibility(View.INVISIBLE);
			}
		});
	}

	/**
	 * Builds a "logging in" progress dialog. If user cancels, the activity
	 * finishes.
	 * 
	 * @return a dialog indicating logging in
	 */
	private Dialog buildLoggingInDialog() {
		final ProgressDialog dialog = 
				buildSimpleProgressDialog(R.string.signing_in);
		dialog.setOnCancelListener(new OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				if (mLoginTask != null) {
					mLoginTask.cancel(true);
				}
			}
		});
		dialog.setCancelable(true);
		return dialog;
	}
	
	/**
	 * Builds an "updating account info" dialog. It is not cancellable to avoid
	 * user seeing old account info right after storage purchase.
	 * 
	 * @return a dialog indicating updating account info
	 */
	private Dialog buildUpdatingAccountInfoDialog() {
		final ProgressDialog dialog =
				buildSimpleProgressDialog(R.string.updating_please_wait);
		dialog.setCancelable(false);
		return dialog;
	}
	
	/**
	 * Builds a "no network" alert dialog indicating we can't do much without
	 * network connection.
	 * 
	 * @return
	 */
	private Dialog buildNoNetworkDialog() {
		final AlertDialog dialog =
				new AlertDialog.Builder(StoreActivity.this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setMessage(R.string.purchase_no_network)
				.setCancelable(false)
				.setPositiveButton(R.string.ok, new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						finish();
					}
				})
				.create();
		return dialog;
	}

	/**
	 * Builds a simple indeterminate progress dialog.
	 * 
	 * @param messageResId
	 *            the message resource id to use
	 * @return a simple progress dialog
	 */
	private ProgressDialog buildSimpleProgressDialog(int messageResId) {
		final ProgressDialog dialog =
				new ProgressDialog(StoreActivity.this);
		dialog.setIcon(android.R.drawable.ic_dialog_info);
		dialog.setIndeterminate(true);
		dialog.setMessage(getText(messageResId));
		return dialog;
	}
	
	public void onReceiveResult(int resultCode, Bundle resultData) {
		switch (resultCode) {
		case Status.RUNNING:
			showProgress();
			showDialog(DIALOG_UPDATING_ACCOUNT_INFO_ID);
			break;
		case Status.FINISHED:
			hideProgress();
			removeDialog(DIALOG_UPDATING_ACCOUNT_INFO_ID);
			break;
		}
	}
	
	private void signRequest(HttpUriRequest request)
			throws AuthorizerException {
		final Authorizer authorizer = OAuthAuthorizer.getWithTokens(
				Preferences.getSerializedOAuthToken(),
				new HmacSha1MessageSigner());
		authorizer.signRequest(request);
	}
	
	/**
	 * Allows cookie-auth in the embedded web view using OAuth signed request.
	 */
	private class LoginTask extends AsyncTask<Void, Void, Void> {
		private DefaultHttpClient httpClient;
		private HttpUriRequest httpRequest;
		private Exception exception;
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			showDialog(DIALOG_LOGGING_IN_ID);
			httpClient = new DefaultHttpClient();
			httpRequest = new HttpGet(UBUNTUONE_FROM_OAUTH_URL);
		}
		
		private String getSessionCookieContent(Cookie sessionCookie) {
			final StringBuilder cookieContent = new StringBuilder(64);
			cookieContent.append(sessionCookie.getName());
			cookieContent.append("=");
			cookieContent.append(sessionCookie.getValue());
			cookieContent.append("; domain=");
			cookieContent.append(sessionCookie.getDomain());
			return cookieContent.toString();
		}
		
		@Override
		protected Void doInBackground(Void... params) {
			try {
				final CookieSyncManager cookieSyncManager =
					CookieSyncManager.createInstance(webView.getContext());
				final CookieManager cookieManager = CookieManager.getInstance();
				cookieManager.setAcceptCookie(true);
				cookieManager.removeSessionCookie();
				
				signRequest(httpRequest);
				httpClient.execute(httpRequest);
				final CookieStore cookieStore = httpClient.getCookieStore();
				final List<Cookie> cookies = cookieStore.getCookies();
				
				Cookie sessionCookie = null;
				if (cookies != null) {
					for (int i = 0; i < cookies.size(); i++) {
						Log.i(TAG, "cookie: " + cookies.get(i).getName());
						if (cookies.get(i).getName().equals("sessionid")) {
							sessionCookie = cookies.get(i);
							Log.i(TAG, "Found cookie for "
									+ sessionCookie.getDomain());
							break;
						}
					}
				}
				
				if (sessionCookie != null) {
					cookieManager.setCookie(
							UBUNTUONE_DOMAIN,
							getSessionCookieContent(sessionCookie));
					cookieSyncManager.sync();
					webView.loadUrl(PURCHASE_STORAGE_URL);
				} else {
					exception = new Exception(getString(R.string
							.purchase_init_failed));
				}
			} catch (AuthorizerException e) {
				// TODO karni: Consider invalidating oauth token in AccountManager
				Log.e(TAG, "auth exception while logging in", e);
				exception = e;
				finish();
			} catch (ClientProtocolException e) {
				// This should never happen.
				Log.e(TAG, "http exception while logging in", e);
				exception = e;
				finish();
			} catch (IOException e) {
				Log.e(TAG, "connectivity issues while logging in", e);
				exception = e;
				finish();
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(Void result) {
			removeDialog(DIALOG_LOGGING_IN_ID);
			if (exception != null) {
				UIUtil.showToast(StoreActivity.this, exception.getMessage());
				finish();
			}
			super.onPostExecute(result);
		}
		
		@Override
		protected void onCancelled() {
			httpClient.getConnectionManager().shutdown();
			removeDialog(DIALOG_LOGGING_IN_ID);
			finish();
			super.onCancelled();
		}
	};
	
	private class ContainedWebViewClient extends WebViewClient {
		/*
		 * Workaround for:
		 * http://code.google.com/p/android/issues/detail?id=15723
		 */
		private boolean onPageFinishedCall = false;
		
		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			showProgress();
			Log.i(TAG, "opening " + url);
			super.onPageStarted(view, url, favicon);
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			super.onPageFinished(view, url);
			Log.i(TAG, "finished opening " + url);
			hideProgress();
			
			if (url.contains(PAYMENT_CONFIRMED_PATH) ||
					url.contains(QUANTITY_CHANGED_PATH)) {
				// Listen for every second onPageFinished call per purchase completion.
				onPageFinishedCall = !onPageFinishedCall;
				if (onPageFinishedCall) {
					mTracker.trackEvent("Purchase", "Storage", "", 1);
					MetaServiceHelper.getUserInfo(StoreActivity.this, mReceiver);
				}
			}
		}

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			if (url.endsWith("account/")) {
				// We present account info ourselves.
				finish();
			} else if (url.endsWith("terms/")) {
				// Show terms in real browser.
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
			} else if (url.contains("one.ubuntu.com")) {
				// Keep everything here.
				view.loadUrl(url);
				return false;
			} else {
				// WorldPay and any other custom url.
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
			}
			return true;
		}
	}
	
	public static void startFrom(Context context) {
		final Intent intent = new Intent(context, StoreActivity.class);
		context.startActivity(intent);
	}
}
