/*
 * Ubuntu One Files - access Ubuntu One cloud storage on Android platform.
 * 
 * Copyright 2011-2013 Canonical Ltd.
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

package com.ubuntuone.android.files.service;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.ubuntuone.android.files.Alarms;
import com.ubuntuone.android.files.Analytics;
import com.ubuntuone.android.files.Constants;
import com.ubuntuone.android.files.Preferences;
import com.ubuntuone.android.files.R;
import com.ubuntuone.android.files.UbuntuOneFiles;
import com.ubuntuone.android.files.activity.FilesActivity;
import com.ubuntuone.android.files.activity.LoginActivity;
import com.ubuntuone.android.files.activity.PreferencesActivity;
import com.ubuntuone.android.files.event.ActivityStateEvent;
import com.ubuntuone.android.files.event.AuthStateEvent;
import com.ubuntuone.android.files.provider.MetaContract.Nodes;
import com.ubuntuone.android.files.provider.MetaContract.ResourceState;
import com.ubuntuone.android.files.provider.MetaContract.Volumes;
import com.ubuntuone.android.files.provider.MetaUtilities;
import com.ubuntuone.android.files.provider.TransfersContract.Downloads;
import com.ubuntuone.android.files.provider.TransfersContract.TransferPriority;
import com.ubuntuone.android.files.provider.TransfersContract.TransferState;
import com.ubuntuone.android.files.provider.TransfersContract.Uploads;
import com.ubuntuone.android.files.provider.TransfersProvider;
import com.ubuntuone.android.files.receiver.BatteryStatusReceiver;
import com.ubuntuone.android.files.receiver.NetworkStatusReceiver;
import com.ubuntuone.android.files.receiver.OnAutoUploadEventListener;
import com.ubuntuone.android.files.util.Authorizer;
import com.ubuntuone.android.files.util.HttpClientProvider;
import com.ubuntuone.android.files.util.Log;
import com.ubuntuone.android.files.util.TransferUtils;
import com.ubuntuone.android.files.util.UIUtil;
import com.ubuntuone.api.files.U1FileAPI;
import com.ubuntuone.api.files.model.U1Node;
import com.ubuntuone.api.files.model.U1Volume;
import com.ubuntuone.api.files.request.U1DownloadListener;
import com.ubuntuone.api.files.request.U1NodeListener;
import com.ubuntuone.api.files.request.U1UploadListener;
import com.ubuntuone.api.files.request.U1VolumeListener;
import com.ubuntuone.api.files.util.U1CancelTrigger;
import com.ubuntuone.api.files.util.U1Failure;
import com.ubuntuone.api.sso.authorizer.OAuthAuthorizer;

public class UpDownService extends AwakeService implements
		OnAutoUploadEventListener
{
	public static final String TAG = UpDownService.class.getSimpleName();
	
	public static final String ACTION_UPLOAD =
			"com.ubuntuone.android.files.updown.ACTION_UPLOAD";
	
	public static final String ACTION_DOWNLOAD =
			"com.ubuntuone.android.files.updown.ACTION_DOWNLOAD";
	
	public static final String ACTION_RETRY =
			"com.ubuntuone.android.files.updown.ACTION_RETRY";
	
	public static final String ACTION_CANCEL_UPLOAD =
			"com.ubuntuone.android.files.updown.ACTION_CANCEL_UPLOAD";
	
	public static final String ACTION_CANCEL_DOWNLOAD =
			"com.ubuntuone.android.files.updown.ACTION_CANCEL_DOWNLOAD";
	
	public static final String BROADCAST_UPLOAD_INFO =
			"com.ubuntuone.android.files.updown.UPLOAD_INFO";
	
	private static final int REQUEST_PURCHASE_SCREEN = 1;
	private static final int REQUEST_AUTOUPLOAD_SCREEN = 2;
	private static final int REQUEST_RETRY_SCREEN = 3;
	
	private static final String PART = ".part";
	
	private Bus mBus;
	private AuthStateEvent mAuthStateEvent = new AuthStateEvent();
	private boolean isAppVisible = false;
	
	private ConnectivityManager connectivityManager;
	private NotificationManager notificationManager;
	
	private List<DownloadListenerRef> downloadListeners =
			new LinkedList<UpDownService.DownloadListenerRef>();
	
	private ContentResolver contentResolver;
	
	private HttpClient mHttpClient;
	private OAuthAuthorizer mAuthorizer;
	private U1FileAPI mApi;
	
	private NetworkStatusReceiver networkStatusReceiver;
	private BatteryStatusReceiver batteryStatusReceiver;
	private boolean canAutoUpload = false;
	
	private int autoUploadedCount = 0;
	private int userUploadedCount = 0;
	private int userDownloadedCount = 0;
	private long duration;
	
	private UploadWorker uploadWorker;
	private OnSharedPreferenceChangeListener uploadPrefsChangeListener;
	private BroadcastReceiver uploadCancelReceiver;
	private U1CancelTrigger uploadCancelTrigger;
	private final int uploadNotificationId = R.id.stat_ongoing_upload_id;
	private int recentFailedUploadCount = 0;
	
	private boolean hasQuotaExceeded = false;
	
	private DownloadWorker downloadWorker;
	private BroadcastReceiver downloadCancelReceiver;
	private U1CancelTrigger downloadCancelTrigger;
	
	private final int downloadNotificationId = R.id.stat_ongoing_download_id;
	
	private GoogleAnalyticsTracker mTracker;
	
	@Subscribe public void onActivityStateEvent(final ActivityStateEvent event) {
		isAppVisible = event.isVisible();
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mBus = UbuntuOneFiles.getBus();
		mBus.register(this);
		duration = System.currentTimeMillis();
	}

	@Override
	public void onCreateAuthenticated() {
		super.onCreateAuthenticated();
		super.TAG = TAG;
		
		mTracker = GoogleAnalyticsTracker.getInstance();
		mTracker.startNewSession(Analytics.U1F_ACCOUNT, this);
		
		connectivityManager = (ConnectivityManager)
				getSystemService(CONNECTIVITY_SERVICE);
		notificationManager = (NotificationManager) getApplicationContext()
				.getSystemService(NOTIFICATION_SERVICE);
		
		contentResolver = getContentResolver();
		
		mHttpClient = HttpClientProvider.newInstance();
		mAuthorizer = Authorizer.getInstance(false);
		
		mApi = new U1FileAPI(UbuntuOneFiles.class.getPackage().getName(),
				Preferences.getSavedVersionName(),
				Constants.U1_METADATA_HOST, Constants.U1_CONTENT_HOST,
				mHttpClient, mAuthorizer);
		
		registerUploadCancelReceiver();
		registerDownloadCancelReceiver();
		
		networkStatusReceiver = new NetworkStatusReceiver(this, this);
		registerNetworkReceiver(networkStatusReceiver);
		batteryStatusReceiver = new BatteryStatusReceiver(this, this);
		registerBatteryReceiver(batteryStatusReceiver);
		
		registerOnSharedPreferenceChangeListener();
	}
	
	@Override
	public int onStartCommandAuthenticated(Intent intent, int flags, int startId) {
		if (intent != null) {
			final String action = intent.getAction();
			if (action.equals(ACTION_UPLOAD)) {
				startUploader();
				return START_REDELIVER_INTENT;
			} else if (action.equals(ACTION_DOWNLOAD)) {
				startDownloader();
				return START_REDELIVER_INTENT;
			} else if (ACTION_RETRY.equals(action)) {
				TransferUtils.clearFailedUploadsState(contentResolver);
				startUploader();
			}
		}
		return START_NOT_STICKY;
	}
	
	public synchronized void registerDownloadListener(OnDownloadListener listener) {
		downloadListeners.add(new DownloadListenerRef(listener));
	}
	
	public synchronized void unregisterDownloadListener(OnDownloadListener listener) {
		downloadListeners.remove(listener);
	}
	
	private synchronized void startUploader() {
		uploaderLazyInit();
	}
	
	private synchronized void startDownloader() {
		downloaderLazyInit();
	}
	
	/** Lazily initialize the upload worker thread. */
	private void uploaderLazyInit() {
		if (uploadWorker == null) {
			uploadWorker = new UploadWorker();
			uploadWorker.setPriority(Thread.MIN_PRIORITY);
			uploadWorker.start();
			synchronized (this) {
				try {
					wait(1000);
				} catch (InterruptedException e) {
				}
			}
		}
	}
	
	/** Lazily initialize the download worker thread. */
	private void downloaderLazyInit() {
		if (downloadWorker == null) {
			downloadWorker = new DownloadWorker();
			downloadWorker.start();
			synchronized (this) {
				try {
					wait(1000);
				} catch (InterruptedException e) {
				}
			}
		}
	}
	
	public class LocalBinder extends Binder {
		public UpDownService getService() {
			return UpDownService.this;
		}
	}
	
	private final IBinder mBinder = new LocalBinder();
	
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	@Override
	public void onDestroyAuthenticated() {
		if (uploadWorker != null && uploadWorker.isWorking) {
			Log.e(TAG, "Interrupting upload worker!");
		}
		if (downloadWorker != null && downloadWorker.isWorking) {
			Log.e(TAG, "Interrupting download worker!");
		}
		
		unregisterOnSharedPreferenceChangeListener();
		if (networkStatusReceiver != null) {
			getApplicationContext().unregisterReceiver(networkStatusReceiver);
		}
		if (batteryStatusReceiver != null) {
			getApplicationContext().unregisterReceiver(batteryStatusReceiver);
		}
		
		unregisterUploadCancelReceiver();
		synchronized (this) {
			if (uploadWorker != null) {
				uploadWorker = null;
			}
		}
		setAutoUploadInfo("");
		
		unregisterDownloadCancelReceiver();
		synchronized (this) {
			if (downloadWorker != null) {
				downloadWorker = null;
			}
		}
		
		HttpClientProvider.safeShutdown(mHttpClient);
		
		// Register retry alarm for failed uploads, if neccessary.
		if (contentResolver != null) {
			int queuedFailedUploadCount =
					TransferUtils.getFailedUploadsCount(contentResolver);
			if (queuedFailedUploadCount == 0) {
				Alarms.unregisterRetryFailedAlarm();
			} else {
				if (recentFailedUploadCount > 0) {
					// Show the notification only if this run had failed uploads.
					showFailedTransfersNotification(queuedFailedUploadCount);
				}
				if (!hasQuotaExceeded) {
					Alarms.maybeRegisterRetryFailedAlarm();
				}
			}
		}
		
		if (mTracker != null) {
			if (autoUploadedCount > 0) {
				mTracker.trackEvent("Transfers", "AutoUpload", "Photo", autoUploadedCount);
			}
			if (userUploadedCount > 0) {
				mTracker.trackEvent("Transfers", "Upload", null, autoUploadedCount);
			}
			if (userDownloadedCount > 0) {
				mTracker.trackEvent("Transfers", "Download", null, autoUploadedCount);
			}
			mTracker.dispatch();
			mTracker.stopSession();
		}
		super.onDestroyAuthenticated();
	}

	@Override
	public void onDestroy() {
		mBus.unregister(this);
		long elapsed = System.currentTimeMillis() - duration;
		Log.i(TAG, "Operation time " + UIUtil.formatTime(elapsed));
		super.onDestroy();
	}

	private void registerUploadCancelReceiver() {
		LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
		uploadCancelReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (uploadCancelReceiver != null) {
					uploadCancelTrigger.onCancel();
				}
				Uri uri = intent.getData();
				if (uri == null) {
					// Cancel all uploads.
					uri = Uploads.CONTENT_URI;
				}
				TransferUtils.dequeue(contentResolver, uri);
			}
		};
		IntentFilter filter = new IntentFilter(ACTION_CANCEL_UPLOAD);
		bm.registerReceiver(uploadCancelReceiver, filter);
	}
	
	private void unregisterUploadCancelReceiver() {
		if (uploadCancelReceiver != null) {
			LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
			bm.unregisterReceiver(uploadCancelReceiver);
		}
	}
	
	private void registerDownloadCancelReceiver() {
		LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
		downloadCancelReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (downloadCancelTrigger != null) {
					downloadCancelTrigger.onCancel();
				}
				Uri uri = intent.getData();
				if (uri == null) {
					// Cancel all downloads.
					uri = Downloads.CONTENT_URI;
				}
				TransferUtils.dequeue(contentResolver, uri);
			}
		};
		IntentFilter filter = new IntentFilter(ACTION_CANCEL_DOWNLOAD);
		IntentFilter filterId = null;
		try {
			filterId = new IntentFilter(ACTION_CANCEL_DOWNLOAD, "*/*");
		} catch (MalformedMimeTypeException e) {
			// Not interested.
		}
		bm.registerReceiver(downloadCancelReceiver, filter);
		if (filterId != null) {
			bm.registerReceiver(downloadCancelReceiver, filterId);
		}
	}
	
	private void unregisterDownloadCancelReceiver() {
		if (downloadCancelReceiver != null) {
			LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
			bm.unregisterReceiver(downloadCancelReceiver);
		}
	}

	@Override
	public void onAutoUploadEventReceived() {
		updateCanAutoUpload();
	}

	public void updateCanAutoUpload() {
		if (networkStatusReceiver == null || batteryStatusReceiver == null)
			return;
		
		if (!Preferences.isPhotoUploadEnabled()) {
			canAutoUpload = false;
			return;
		}
		
		boolean uploadOnlyOnWiFi = Preferences.getAutoUploadOnlyOnWiFi();
		if (uploadOnlyOnWiFi)
			Log.d(TAG, "auto-upload only over WiFi");
		boolean uploadOnlyWhenCharging = Preferences.getAutoUploadOnlyWhenCharging();
		if (uploadOnlyWhenCharging)
			Log.d(TAG, "auto-upload only when charging");
		boolean uploadAlsoWhenRoaming = Preferences.getAutoUploadAlsoWhenRoaming();
		if (uploadAlsoWhenRoaming)
			Log.d(TAG, "auto-upload also when roaming");
		
		boolean canAutoUpload = true;
		canAutoUpload &= networkStatusReceiver.isConnected();
		canAutoUpload &= connectivityManager.getBackgroundDataSetting();
		canAutoUpload &= uploadOnlyOnWiFi ? networkStatusReceiver.isWifi() : true;
		canAutoUpload &= uploadAlsoWhenRoaming ? true : !networkStatusReceiver.isRoaming();
		canAutoUpload &= uploadOnlyWhenCharging ? batteryStatusReceiver.isCharging() : true;
		Log.d(TAG, String.format("Can auto-upload: %b", canAutoUpload));
		this.canAutoUpload = canAutoUpload;
		
		if (canAutoUpload) {
			TransferUtils.updateAutoUploadsState(contentResolver,
					TransferState.WAITING, TransferState.QUEUED);
		} else {
			TransferUtils.updateAutoUploadsState(contentResolver,
					TransferState.QUEUED, TransferState.WAITING);
			
			if (networkStatusReceiver.isRoaming() && !uploadAlsoWhenRoaming) {
				// The notification is useless if 'Only on Wi-Fi' is set.
				if (!Preferences.getAutoUploadOnlyOnWiFi()) {
					notifyRoamingSoAutoUploadDisabled();
				}
			}
		}
	}
	
	private void registerNetworkReceiver(NetworkStatusReceiver receiver) {
		IntentFilter filter = new IntentFilter(
				ConnectivityManager.CONNECTIVITY_ACTION);
		getApplicationContext().registerReceiver(receiver, filter);
	}
	
	private void registerBatteryReceiver(BatteryStatusReceiver receiver) {
		IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		getApplicationContext().registerReceiver(receiver, filter);
	}
	
	private void registerOnSharedPreferenceChangeListener() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		uploadPrefsChangeListener = new OnSharedPreferenceChangeListener() {
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
					String key) {
				if (key.equals(Preferences.SECONDARY_STORAGE)) return;
				Log.d(TAG, "onSharedPreferenceChanged()");
				updateCanAutoUpload();
			}
		};
		prefs.registerOnSharedPreferenceChangeListener(uploadPrefsChangeListener);
	}
	
	private void unregisterOnSharedPreferenceChangeListener() {
		if (uploadPrefsChangeListener != null) {
			SharedPreferences prefs = PreferenceManager
					.getDefaultSharedPreferences(this);
			prefs.unregisterOnSharedPreferenceChangeListener(uploadPrefsChangeListener);
		}
	}
	
	/**
	 * Worker thread responsible for uploading queued transfers saved in
	 * {@link TransfersProvider}. Transfers with priority
	 * {@link TransferPriority#USER} are processed before other transfers.
	 */
	private class UploadWorker extends Thread {
		// True when the worker thread is currently processing a transfer.
		private boolean isWorking = true;
		
		private Notification notification;
		
		private NotifyingUploadListener listener;
		
		// --- Workaround for hanging uploads in httpClient.execute()
		private HttpClient httpClient;
		private U1FileAPI api;
		// ---
		
		public UploadWorker() {
			notification = new NotificationCompat.Builder(
					UpDownService.this)
					.setOngoing(true)
					.setTicker("Uploading file(s)...")
					.setSmallIcon(R.drawable.stat_sys_upload)
					.setContentTitle("Ubuntu One")
					.setOnlyAlertOnce(true)
					.setNumber(1)
					.build();
			listener = new NotifyingUploadListener(contentResolver, notification);
		}

		@Override
		public void run() {
			final ContentResolver r = contentResolver;
			final String[] projection = Uploads.getDefaultProjection();
			
			isWorking = true;
			int uploadCount = 0;
			int autoUploadCount = 0;
			updateCanAutoUpload();
			
			String selection = Uploads.STATE + "=?";
			String[] selectionArgs = new String[] { TransferState.QUEUED };
			isWorking = true;
			do {
				// Default sort order favors user uploads.
				final Cursor c = r.query(Uploads.CONTENT_URI, projection,
						selection, selectionArgs, null);
				try {
					uploadCount = c.getCount();
					if (uploadCount > 0) {
						setAutoUploadInfo(String.format("%d files left to upload",
								uploadCount));
					} else {
						setAutoUploadInfo("");
					}
					notification.number = uploadCount;
					if (c != null && c.moveToFirst()) {
						Log.d(TAG, "Uploads left: " + uploadCount);
						
						final int priority = c.getInt(
								c.getColumnIndex(Uploads.PRIORITY));
						final long id = c.getLong(
								c.getColumnIndex(Uploads._ID));
								
						final String path = c.getString(
								c.getColumnIndex(Uploads.PATH));
						final File file = new File(path);
						final String filePath = file.getAbsolutePath();
						final String contentType = c.getString(
								c.getColumnIndex(Uploads.MIME));
						final String resourcePath = c.getString(
								c.getColumnIndex(Uploads.RESOURCE_PATH));
						
						if (!file.exists()) {
							Log.w(TAG, "File does not exist: " + file.getAbsolutePath());
							Uri uri = Uploads.buildUploadUri(id);
							r.delete(uri, null, null);
						} else if (priority == TransferPriority.USER ||
								(canAutoUpload && priority == TransferPriority.AUTO)) {
							final Intent intent = new Intent(
									UpDownService.this, FilesActivity.class);
							// TODO Make the click show upload directory.
							final PendingIntent pi = PendingIntent.getActivity(
									getApplicationContext(), 0, intent, 0);
							listener.setListenerData(file, pi, id, resourcePath);
							listener.setPriority(priority);
							
							uploadCancelTrigger = new U1CancelTrigger();
							upload(filePath, contentType, resourcePath, listener,
									uploadCancelTrigger);
						} else {
							Log.i(TAG, "Not Auto Uploading because of settings.");
							break;
						}
					}
				} finally {
					if (c != null) c.close();
				}
				uploadCount = TransferUtils.getQueuedUploadsCount(
						contentResolver, TransferPriority.USER);
				autoUploadCount = TransferUtils.getQueuedUploadsCount(
						contentResolver, TransferPriority.AUTO);
				if (!canAutoUpload) {
					autoUploadCount = 0;
				}
			} while (uploadCount > 0 || autoUploadCount > 0);

			uploadWorker = null; // allow GC
			notificationManager.cancel(uploadNotificationId);
			if (TransferUtils.getNonFailedDownloadsCount(contentResolver) == 0) {
				stopSelf();
			}
		}
		
		public void upload(String filePath, String contentType, String path,
				U1UploadListener callback, U1CancelTrigger cancelTrigger) {
			acquireWifiLock();
			
			httpClient = HttpClientProvider.newInstance();
			api = new U1FileAPI(UbuntuOneFiles.class.getPackage().getName(),
					Preferences.getSavedVersionName(),
					Constants.U1_METADATA_HOST, Constants.U1_CONTENT_HOST,
					httpClient, mAuthorizer);
			
			api.uploadFile(filePath, contentType, path, true, null, callback,
					cancelTrigger);
			
			HttpClientProvider.safeShutdown(httpClient);
			releaseWifiLock();
		}
	}
	
	private class NotifyingUploadListener extends U1UploadListener {
		private ContentResolver resolver;
		private Notification notification;
		private int priority = TransferPriority.USER;
		private boolean notify = true;
		private long id;
		private File file;
		private PendingIntent intent;
		private String resourcePath;
		
		private long duration;
		
		public NotifyingUploadListener(ContentResolver resolver,
				Notification notification) {
			this.resolver = resolver;
			this.notification = notification;
		}
		
		public void setListenerData(File file, PendingIntent intent, long id,
				String resourcePath) {
			this.id = id;
			this.file = file;
			this.intent = intent;
			this.resourcePath = resourcePath;
		}
		
		public void setPriority(int priority) {
			boolean show = Preferences.getAutoUploadShowNotifications();
			this.priority = priority;
			if (priority == TransferPriority.USER) {
				notify = true;
			} else if (priority == TransferPriority.AUTO) {
				notify = show;
			}
		}
		
		@Override
		public void onStart() {
			super.onStart();
			duration = System.currentTimeMillis();
			Log.i(TAG, "Starting upload: " + file.getAbsolutePath());
			String subtitle = "Uploading " + file.getName();
			if (notify) {
				notification.setLatestEventInfo(UpDownService.this,
						"Ubuntu One", subtitle, intent);
				notificationManager.notify(uploadNotificationId, notification);
			} else {
				notificationManager.cancel(uploadNotificationId);
			}
		}
		
		@Override
		public void onSuccess(U1Node node) {
			Log.i(TAG, "Upload success: " + node.getResourcePath());
			Uri uri = Uploads.buildUploadUri(id);
			resolver.delete(uri, null, null);
			
			if (priority == TransferPriority.USER) {
				userUploadedCount++;
			} else if (priority == TransferPriority.AUTO) {
				autoUploadedCount++;
			}
			
			String data = file.getAbsolutePath();
			MetaUtilities.updateNode(getContentResolver(), node, data);
			MetaUtilities.setState(node.getResourcePath(), null);
		}

		@Override
		public void onUbuntuOneFailure(U1Failure failure) {
			super.onUbuntuOneFailure(failure);
			if (uploadCancelTrigger != null && uploadCancelTrigger.isCancelled()) {
				// Ignore failure, request canceled.
				return;
			}
			Log.e(TAG, "Upload U1 failure", failure.getCause());
			if (failure.getStatusCode() == HttpStatus.SC_NOT_FOUND
					&& resourcePath.startsWith(Preferences
							.getPhotoUploadResourcePath())) {
				createPhotoAutoUploadLocation();
			} else {
				TransferUtils.updateAutoUploadsState(resolver,
						TransferState.QUEUED, TransferState.WAITING);
				
				recentFailedUploadCount++;
				onFailureCallback(failure);
			}
		}

		@Override
		public void onFailure(U1Failure failure) {
			super.onFailure(failure);
			if (uploadCancelTrigger != null && uploadCancelTrigger.isCancelled()) {
				// Ignore failure, request canceled.
				return;
			}
			Log.e(TAG, "Upload failure with HTTP " + failure.getStatusCode(),
					failure.getCause());
			Uri uri = Uploads.buildUploadUri(id);
			ContentValues values = new ContentValues();
			values.put(Uploads.STATE, TransferState.FAILED);
			resolver.update(uri, values, null, null);
			
			recentFailedUploadCount++;

			onFailureCallback(failure);
		}

		@Override
		public void onProgress(long bytes, long total) {
			super.onProgress(bytes, total);
			int progress = (int) (bytes*100/(double)total);
			Log.d(TAG, String.format("upload progress: %d", progress));
			String subtitle = String.format("%d%% of %s",
					progress, file.getName());
			if (notify) {
				notification.setLatestEventInfo(
						UpDownService.this, "Ubuntu One", subtitle, intent);
				notificationManager.notify(uploadNotificationId, notification);
			}
		}

		@Override
		public void onFinish() {
			super.onFinish();
			resolver.notifyChange(Nodes.CONTENT_URI, null);
			long elapsed = System.currentTimeMillis() - duration;
			Log.i(TAG, String.format("Upload took %s", UIUtil.formatTime(elapsed)));
		}
	}
	
	private void setAutoUploadInfo(String info) {
		Intent broadcast = new Intent(BROADCAST_UPLOAD_INFO);
		broadcast.putExtra(Intent.EXTRA_TEXT, info);
		sendBroadcast(broadcast);
	}
	
	boolean shouldCreateVolume = false;
	
	private void createPhotoAutoUploadLocation() {
		Log.i(TAG, "Creating photo upload location.");
		final String resourcePath = Preferences.getPhotoUploadResourcePath();
		mApi.makeDirectory(resourcePath, new U1NodeListener() {
			@Override
			public void onSuccess(U1Node node) {
				Log.i(TAG, "Created photo auto upload directory.");
				ContentValues values = Nodes.valuesFromRepr(node);
				contentResolver.insert(Nodes.CONTENT_URI, values);
				
				TransferUtils.updateAutoUploadsState(contentResolver,
						TransferState.WAITING, TransferState.QUEUED);
			}
			
			@Override
			public void onUbuntuOneFailure(U1Failure failure) {
				Log.e(TAG, "Failed to create photo auto upload directory: " +
						"HTTP " + failure.getStatusCode(), failure.getCause());
				if (failure.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
					// TODO Call to createPhotoAutoUploadVolume() creates a deadlock?!
					shouldCreateVolume = true;
				} else {
					Log.e(TAG, "Unexpected problem when creating photo auto upload directory: " +
							"HTTP " + failure.getStatusCode(), failure.getCause());
					TransferUtils.updateAutoUploadsState(contentResolver,
							TransferState.QUEUED, TransferState.WAITING);
					
					recentFailedUploadCount++;
					onFailureCallback(failure);
				}
			}

			@Override
			public void onFailure(U1Failure failure) {
				TransferUtils.updateAutoUploadsState(contentResolver,
						TransferState.QUEUED, TransferState.WAITING);
			}
		});
		
		if (shouldCreateVolume) {
			shouldCreateVolume = false;
			createPhotoAutoUploadVolume("/volumes" + resourcePath);
		}
	}
	
	public void createPhotoAutoUploadVolume(final String resourcePath) {
		Log.i(TAG, "Creating photo upload volume.");
		mApi.createVolume(resourcePath, new U1VolumeListener() {
			@Override
			public void onSuccess(U1Volume volume) {
				Log.i(TAG, "Created photo auto upload volume.");
				ContentValues values = Volumes.valuesFromRepr(volume);
				contentResolver.insert(Volumes.CONTENT_URI, values);
				
				MetaServiceHelper.getNode(
						getBaseContext(), volume.getNodePath(), null);
				
				TransferUtils.updateAutoUploadsState(contentResolver,
						TransferState.WAITING, TransferState.QUEUED);
			}
			
			@Override
			public void onUbuntuOneFailure(U1Failure failure) {
				Log.e(TAG, "Failed to create photo auto upload volume: HTTP "
						+ failure.getStatusCode(), failure.getCause());
				
				TransferUtils.updateAutoUploadsState(contentResolver,
						TransferState.QUEUED, TransferState.WAITING);
				
				recentFailedUploadCount++;
				onFailureCallback(failure);
			}

			@Override
			public void onFailure(U1Failure failure) {
				Log.e(TAG, "Failed to create photo auto upload volume: "
						+ resourcePath);
				TransferUtils.updateAutoUploadsState(contentResolver,
						TransferState.QUEUED, TransferState.WAITING);
			}
		});
	}
	
	/**
	 * Worker thread responsible for downloading queued transfers saved in
	 * {@link TransfersProvider}. Transfers with priority
	 * {@link TransferPriority#USER} are processed before other transfers.
	 */
	private class DownloadWorker extends Thread {
		// True when the worker thread is currently processing a transfer.
		private boolean isWorking = true;
		
		private Notification notification;
		
		private NotifyingDownloadListener listener;
		
		public DownloadWorker() {
			notification = new NotificationCompat.Builder(
					UpDownService.this)
					.setOngoing(true)
					.setTicker("Downloading file(s)...")
					.setSmallIcon(R.drawable.stat_sys_download)
					.setContentTitle("Ubuntu One")
					.setOnlyAlertOnce(true)
					.setNumber(1)
					.build();
			listener = new NotifyingDownloadListener(contentResolver, notification);
		}

		@Override
		public void run() {
			final ContentResolver r = contentResolver;
			final String[] projection = Downloads.getDefaultProjection();
			
			isWorking = true;
			int downloadCount = 0;
			
			String selection = Downloads.STATE + " != ?";
			String[] selectionArgs = new String[] { TransferState.FAILED };
			do {
				// Default sort order favors user uploads.
				final Cursor c = r.query(Downloads.CONTENT_URI, projection,
						selection, selectionArgs, null);
				try {
					downloadCount = c.getCount();
					notification.number = downloadCount;
					if (c != null && c.moveToFirst()) {
						Log.d(TAG, "Downloads left: " + downloadCount);
						
						final long id = c.getLong(
								c.getColumnIndex(Uploads._ID));
						final String resourcePath = c.getString(
								c.getColumnIndex(Downloads.RESOURCE_PATH));
						final String path = c.getString(
								c.getColumnIndex(Downloads.PATH));
						
						if (networkStatusReceiver.isConnected()) {
							File file = new File(path);
							final Intent intent = new Intent(
									UpDownService.this, FilesActivity.class);
							// TODO Make the click show upload directory.
							final PendingIntent pi = PendingIntent.getActivity(
									getApplicationContext(), 0, intent, 0);
							
							listener.setListenerData(resourcePath, file, pi, id);
							downloadCancelTrigger = new U1CancelTrigger();
							download(resourcePath, path + PART, listener, downloadCancelTrigger);
						} else {
							Log.i(TAG, "Not Auto Uploading because of settings.");
							break;
						}
					}
				} finally {
					if (c != null) c.close();
				}
			} while (downloadCount > 0);

			downloadWorker = null; // allow GC
			notificationManager.cancel(downloadNotificationId);
			if (TransferUtils.getNonFailedUploadsCount(contentResolver) == 0) {
				stopSelf();
			}
		}
		
		public void download(String resourcePath, String path,
				U1DownloadListener callback, U1CancelTrigger cancelTrigger) {
			acquireWifiLock();
			mApi.downloadFile(resourcePath, path, callback, cancelTrigger);
			releaseWifiLock();
		}
	}
	
	private class NotifyingDownloadListener extends U1DownloadListener {
		private ContentResolver resolver;
		private Notification notification;
		private boolean notify = true;
		private String resourcePath;
		private File partFile;
		private File file;
		private long id;
		private PendingIntent intent;
		
		private long duration;
		
		public NotifyingDownloadListener(ContentResolver resolver,
				Notification notification) {
			this.resolver = resolver;
			this.notification = notification;
		}
		
		public void setListenerData(String resourcePath, File file,
				PendingIntent intent, long id) {
			this.resourcePath = resourcePath;
			this.partFile = new File(file.getAbsolutePath() + PART);
			this.file = file;
			this.id = id;
			this.intent = intent;
			this.duration = 0;
		}
		
		@Override
		public void onStart() {
			super.onStart();
			duration = System.currentTimeMillis();
			Log.i(TAG, "Starting download: " + resourcePath);
			String subtitle = "Downloading " + file.getName();
			if (notify) {
				notification.setLatestEventInfo(UpDownService.this,
						"Ubuntu One", subtitle, intent);
				notificationManager.notify(downloadNotificationId, notification);
			} else {
				notificationManager.cancel(downloadNotificationId);
			}
			MetaUtilities.setStateAndData(resourcePath,
					ResourceState.STATE_GETTING, file.getAbsolutePath());
		}
		
		@Override
		public void onSuccess() {
			// XXX u1f library bug? canceled transfer calls onSuccess
			if (downloadCancelTrigger != null && downloadCancelTrigger.isCancelled()) {
				this.onCancel();
				return;
			}
			Log.i(TAG, "Download success: " + file.getAbsolutePath());
			Uri uri = Downloads.buildDownloadUri(id);
			resolver.delete(uri, null, null);
			
			userDownloadedCount++;
			
			partFile.renameTo(file);
			
			String data = file.getAbsolutePath();
			MetaUtilities.setStateAndData(resourcePath, null, data);
			UbuntuOneFiles.getMediaScannerHelper().scanFile(data);
			
			U1Node node = MetaUtilities.getNodeByResourcePath(resourcePath);
			synchronized (UpDownService.this) {
				for (DownloadListenerRef listenerRef : downloadListeners) {
					OnDownloadListener listener = listenerRef.get();
					if (listener != null) {
						listener.onDownloadSuccess(node.getKey(), data);
					}
				}
			}
		}

		@Override
		public void onUbuntuOneFailure(U1Failure failure) {
			super.onUbuntuOneFailure(failure);
			if (downloadCancelTrigger != null && downloadCancelTrigger.isCancelled()) {
				// Ignore failure, request canceled.
				return;
			}
			Log.e(TAG, "Download U1 failure", failure.getCause());
			// Remove from queue.
			Uri uri = Downloads.buildDownloadUri(id);
			resolver.delete(uri, null, null);
			// Notify failure.
			MetaUtilities.setState(resourcePath, ResourceState.STATE_GETTING_FAILED);
			
			onFailureCallback(failure);
		}

		@Override
		public void onFailure(U1Failure failure) {
			super.onFailure(failure);
			if (downloadCancelTrigger != null && downloadCancelTrigger.isCancelled()) {
				// Ignore failure, request canceled.
				return;
			}
			Log.e(TAG, "Download failure", failure.getCause());
			// Remove from queue.
			Uri uri = Downloads.buildDownloadUri(id);
			resolver.delete(uri, null, null);
			// Notify failure.
			MetaUtilities.setState(resourcePath, ResourceState.STATE_GETTING_FAILED);
			
			onFailureCallback(failure);
		}

		@Override
		public void onProgress(long bytes, long total) {
			super.onProgress(bytes, total);
			int progress = (int) (bytes*100/(double)total);
			Log.d(TAG, String.format("download progress: %d", progress));
			String subtitle = String.format("%d%% of %s",
					progress, file.getName());
			if (notify) {
				notification.setLatestEventInfo(
						UpDownService.this, "Ubuntu One", subtitle, intent);
				notificationManager.notify(downloadNotificationId, notification);
			}
			
			U1Node node = MetaUtilities.getNodeByResourcePath(resourcePath);
			synchronized (UpDownService.this) {
				for (DownloadListenerRef listenerRef : downloadListeners) {
					OnDownloadListener listener = listenerRef.get();
					if (listener != null) {
						listener.onDownloadProgress(node.getKey(), bytes, total);
					}
				}
			}
		}
		
		@Override
		public void onCancel() {
			super.onCancel();
			file.delete();
			MetaUtilities.setStateAndData(resourcePath, null, null);
		}

		@Override
		public void onFinish() {
			super.onFinish();
			resolver.notifyChange(Nodes.CONTENT_URI, null);
			long elapsed = System.currentTimeMillis() - duration;
			Log.i(TAG, String.format("Download took %s", UIUtil.formatTime(elapsed)));
		}
	}
	
	public void notifyRoamingSoAutoUploadDisabled() {
		final String title = "Roaming, Auto Upload disabled";
		final String text = "To enable, change your settings.";
		Notification notification = new NotificationCompat.Builder(this)
				.setOngoing(false)
				.setTicker("Roaming, Auto Upload disabled")
				.setSmallIcon(R.drawable.stat_sys_upload_anim0)
				.setOnlyAlertOnce(true)
				.setAutoCancel(true)
				.build();
		
		final Intent intent = new Intent(
				UpDownService.this, PreferencesActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtra(PreferencesActivity.AUTOUPLOAD_SCREEN, 1);
		final PendingIntent pi = PendingIntent.getActivity(
				getApplicationContext(), REQUEST_AUTOUPLOAD_SCREEN, intent, 0);
		notification.setLatestEventInfo(UpDownService.this, title, text, pi);
		
		notificationManager.notify(R.id.stat_roaming_autoupload_off_id,
				notification);
	}
	
	public void onFailureCallback(U1Failure failure) {
		Throwable cause = failure.getCause();
		if (cause != null) {
			if (cause.getClass() == SocketException.class ||
					cause.getClass() == UnknownHostException.class) {
				onNoNetwork();
				return;
			}
		}
		
		// TODO log failure status codes in GA
		switch (failure.getStatusCode()) {
		case HttpStatus.SC_UNAUTHORIZED:
			onUnauthorizedResponse();
			break;
		case HttpStatus.SC_PAYMENT_REQUIRED:
			onQuotaExceeded();
			break;
		case HttpStatus.SC_INSUFFICIENT_STORAGE:
			onQuotaExceeded();
			break;
		}
	}
	
	public void onNoNetwork() {
		// TODO broadcast UI toast
	}
	
	public void onUnauthorizedResponse() {
		Log.w(TAG, "Received HTTP Unauthorized response.");
		Context context = UbuntuOneFiles.getInstance().getApplicationContext();
		Preferences.invalidateToken(context);
		UbuntuOneFiles.getInstance().setLastAuthStateEvent(new AuthStateEvent(false));
		
		requireReauthentication();
		
		stopSelf();
	}
	
	public void onQuotaExceeded() {
		// Cancel all transfers.
		TransferUtils.setUploadsState(getContentResolver(),
				TransferState.FAILED);
		
		// Cancel retry alarm.
		Alarms.unregisterRetryFailedAlarm();
		
		String title = "Insufficient storage space";
		String text = "Select to buy more storage";
		
		// Notify the user, suggest storage upgrade.
		Notification notification = new NotificationCompat.Builder(this)
				.setOngoing(false)
				.setTicker(title)
				.setSmallIcon(R.drawable.stat_u1_logo)
				.setOnlyAlertOnce(true)
				.setAutoCancel(true)
				.build();
		
		final Intent intent = new Intent(
				UpDownService.this, PreferencesActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtra(PreferencesActivity.PURCHASE_STORAGE_SCREEN, 1);
		final PendingIntent pi = PendingIntent.getActivity(
				getApplicationContext(), REQUEST_PURCHASE_SCREEN, intent, 0);
		notification.setLatestEventInfo(UpDownService.this, title, text, pi);
		
		notificationManager.notify(R.id.stat_quota_exceeded_id, notification);
		hasQuotaExceeded = true;
	}
	
	private synchronized void showFailedTransfersNotification(int failed) {
		String title = "Ubuntu One";
		Resources r = getResources();
		String text = r.getQuantityString(
				R.plurals.failed_to_upload_n_files, failed, failed);
		
		final Intent intent = new Intent(
				UpDownService.this, PreferencesActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtra(PreferencesActivity.SHOW_RETRY_FAILED, 1);
		final PendingIntent pi = PendingIntent.getActivity(
				getApplicationContext(), REQUEST_RETRY_SCREEN, intent, 0);
		
		Notification notification = new NotificationCompat.Builder(this)
				.setOngoing(false)
				.setTicker(title)
				.setSmallIcon(R.drawable.stat_u1_logo)
				.setOnlyAlertOnce(true)
				.setAutoCancel(true)
				.build();
		notification.setLatestEventInfo(this, title, text, pi);
		
		notificationManager.notify(R.id.stat_failed_upload_id, notification);
	}

	private void requireReauthentication() {
		if (isAppVisible) {
			// Show the log-in activity.
			mAuthStateEvent.setIsAuthenticated(false);
			mBus.post(mAuthStateEvent);
			return;
		}
		
		// Post a log-in notification.
		final String title = getString(R.string.please_reauthenticate);
		final String text = getString(R.string.you_have_been_signed_out);

		final Intent intent = new Intent(
				UpDownService.this, LoginActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		final PendingIntent pi = PendingIntent.getActivity(
				getApplicationContext(), 0, intent, 0);

		Notification notification = new NotificationCompat.Builder(this)
				.setOngoing(false)
				.setTicker(title)
				.setSmallIcon(R.drawable.stat_u1_logo)
				.setOnlyAlertOnce(true)
				.setAutoCancel(true)
				.build();
		notification.setLatestEventInfo(this, title, text, pi);

		notificationManager.notify(R.id.stat_please_reauthenticate, notification);
	}
	
	public interface IdleListener {
		public void isIdle();
	}
	
	public class DownloadListenerRef extends WeakReference<OnDownloadListener> {
		public DownloadListenerRef(OnDownloadListener listener) {
			super(listener);
		}
	}
	
	public interface OnDownloadListener {
		public void onDownloadCached(String key, String path);
		public void onDownloadStarted(String key);
		public void onDownloadProgress(String key, long bytes, long total);
		public void onDownloadSuccess(String key, String path);
		public void onDownloadFailure(String key, U1Failure failure);
	}
}
