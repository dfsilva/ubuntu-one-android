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
import java.util.ArrayList;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;

import com.ubuntuone.android.files.Preferences;
import com.ubuntuone.android.files.provider.TransfersContract.TransferPriority;
import com.ubuntuone.android.files.provider.TransfersProvider;
import com.ubuntuone.android.files.provider.WatchedFoldersContract.WatchedFolders;
import com.ubuntuone.android.files.receiver.BatteryStatusReceiver;
import com.ubuntuone.android.files.receiver.NetworkStatusReceiver;
import com.ubuntuone.android.files.receiver.OnAutoUploadEventListener;
import com.ubuntuone.android.files.util.Log;
import com.ubuntuone.android.files.util.MediaImportUtils;
import com.ubuntuone.android.files.util.TimeUtil;
import com.ubuntuone.android.files.util.TransferUtils;
import com.ubuntuone.android.files.util.WatchedFolderUtils;

/**
 * A {@link Service} responsible for detecting new media, inserting appropriate
 * upload transfers into {@link TransfersProvider} and starting the
 * {@link UpDownService} when there's new media queued for upload and settings
 * allow for auto upload.
 */
public class AutoUploadService extends Service implements
		OnAutoUploadEventListener
{
	private static final String TAG = AutoUploadService.class.getSimpleName();
	
	public static final String ACTION_RESCAN_IMAGES =
			"com.ubuntuone.android.files.autoupload.ACTION_RESCAN_IMAGES";
	
	public static final String EXTRA_NEW_ONLY = "new_only";
	
	private HandlerThread serviceThread;
	private Handler serviceHandler;
	
	private static final int MSG_ON_CHANGE = 1;
		
	private static final Uri IMAGES_CONTENT_URIS[] = {
		MediaStore.Images.Media.EXTERNAL_CONTENT_URI
	};
	
	private ArrayList<MediaObserver> observers =
			new ArrayList<MediaObserver>(IMAGES_CONTENT_URIS.length);
	
	private ConnectivityManager connectivityManager;
	
	private OnSharedPreferenceChangeListener prefsChangeListener;
	private NetworkStatusReceiver networkStatusReceiver;
	private BatteryStatusReceiver batteryStatusReceiver;
	
	@Override
	public void onCreate() {
		Log.i(TAG, "Starting Auto Upload service");
		
		connectivityManager = (ConnectivityManager) getSystemService(
				CONNECTIVITY_SERVICE);
		
		serviceThread = new HandlerThread("AutoUploadThread");
		serviceThread.start();
		serviceHandler = new Handler(serviceThread.getLooper()) {

			@Override
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case MSG_ON_CHANGE:
					Runnable scanTask = (Runnable) msg.obj;
					scanTask.run();
					break;
					
				default:
					super.handleMessage(msg);
					break;
				}
			}
			
		};
		registerContentObservers();
		
		networkStatusReceiver = new NetworkStatusReceiver(this, this);
		registerConnectivityReceiver(networkStatusReceiver);
		batteryStatusReceiver = new BatteryStatusReceiver(this, this);
		registerChargingReceiver(batteryStatusReceiver);
		
		registerOnSharedPreferenceChangeListener();
	}
	
	@Override
	public IBinder onBind(Intent startService) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			String action = intent.getAction();
			if (ACTION_RESCAN_IMAGES.equals(action)) {
				final boolean newOnly = intent
						.getBooleanExtra(EXTRA_NEW_ONLY, false);
				serviceHandler.post(new Runnable() {
					@Override
					public void run() {
						rescanImagesNow(newOnly);
					}
				});
			}
		}
		return Service.START_STICKY;
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "Stopping Auto Upload service");
		unregisterContentObservers();
		serviceThread.getLooper().quit();
		
		unregisterOnSharedPreferenceChangeListener();
		if (networkStatusReceiver != null) {
			getApplicationContext().unregisterReceiver(networkStatusReceiver);
		}
		if (batteryStatusReceiver != null) {
			getApplicationContext().unregisterReceiver(batteryStatusReceiver);
		}
	}

	private void registerContentObservers() {
		for (Uri uri : IMAGES_CONTENT_URIS) {
			MediaObserver observer = new MediaObserver(uri, serviceHandler);
			observers.add(observer);
		}
		
		ContentResolver resolver = getContentResolver();
		for (MediaObserver o : observers) {
			resolver.registerContentObserver(o.uri, true, o);
			Log.d(TAG, "Auto Upload now enabled for " + o.uri);
		}
	}
	
	private void unregisterContentObservers() {
		ContentResolver resolver = getContentResolver();
		for (MediaObserver o : observers) {
			resolver.unregisterContentObserver(o);
			Log.d(TAG, "Auto Upload now disabled for " + o.uri);
		}
		observers.clear();
	}
	
	private void rescanImagesNow(boolean newOnly) {
		ContentResolver resolver = getContentResolver();
		
		if (newOnly == false) {
			WatchedFolderUtils.resetUploadTimestamps(resolver,
					WatchedFolders.Images.CONTENT_URI);
		}
		
		for (Uri uri : IMAGES_CONTENT_URIS) {
			resolver.notifyChange(uri, null);
		}
	}
	
	private class MediaObserver extends ContentObserver {
		private final Uri uri;
		private Runnable scanRunnable;

		public MediaObserver(Uri uri, Handler handler) {
			super(handler);
			this.uri = uri;
			this.scanRunnable = new Runnable() {
				@Override
				public void run() {
					rescanMedia(MediaObserver.this.uri);
				}
			};
		}
		
		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
			synchronized (this) {
				if (! serviceHandler.hasMessages(MSG_ON_CHANGE, scanRunnable)) {
					Log.i(TAG, "Starting auto upload scan of " + uri);
					Message msg = serviceHandler.obtainMessage(
							MSG_ON_CHANGE, scanRunnable);
					serviceHandler.sendMessageDelayed(msg, 1000);
					// Delay: if there are pics taken in same second,
					// we want them all be in database already.
				} else {
					Log.i(TAG, "Scan already scheduled for " + uri);
				}
			}
		}
	}
	
	private void rescanMedia(Uri uri) {
		// TODO This needs work, should be much more lightweight.
		// But it's 3 AM, we're releasing tomorrow, so have to stick with it now.
		MediaImportUtils.importImageBuckets(this);
		if (uri.toString().contains("images")) {
			rescanImages(uri);
		}
	}
	
	private void rescanImages(Uri uri) {
		ContentResolver resolver = getContentResolver();
		String[] projection = new String[] {
				WatchedFolders.FOLDER_PATH,
				WatchedFolders.LAST_UPLOADED,
				WatchedFolders.AUTO_UPLOAD,
				WatchedFolders.PERSIST_PATH
		};
		String selection = WatchedFolders.AUTO_UPLOAD + "=1";
		
		Cursor c = resolver.query(WatchedFolders.Images.CONTENT_URI,
				projection, selection, null, null);
		
		try {
			if (c != null && c.isBeforeFirst()) {
				// Rescan each gallery separately.
				while (c.moveToNext()) {
					String path = c.getString(c.getColumnIndex(
							WatchedFolders.FOLDER_PATH));
					long since = c.getLong(c.getColumnIndex(
							WatchedFolders.LAST_UPLOADED));
					rescanImagesFolder(uri, path, since);
					
					Log.d(TAG, String.format("Timestamping %s with %d",
							path, TimeUtil.getTimeInSeconds()));
					WatchedFolderUtils.updateUploadTimestamp(resolver, path);
				}
			}
		} finally {
			if (c != null) c.close();
		}
		
		// Run uploads.
		startService(new Intent(UpDownService.ACTION_UPLOAD));
	}
	
	private void rescanImagesFolder(Uri uri, String path, long since) {
		ContentResolver resolver = getContentResolver();
		Log.d(TAG, String.format("Rescan of %s since %d", path, since));
		
		String[] projection = new String[] {
				MediaColumns._ID,
				MediaColumns.DATE_ADDED,
				MediaColumns.SIZE,
				MediaColumns.DATA
		};
		String pathExpr = path + "%";
		String selection = MediaColumns.DATA + " LIKE ? AND " +
				MediaColumns.DATE_ADDED + ">=?";
		String[] selectionArgs = new String[] {
				pathExpr, String.valueOf(since)
		}; 
	
		final Cursor c = resolver.query(uri, projection, selection,
				selectionArgs, null);
		try {
			if (c != null && c.isBeforeFirst()) {
				while (c.moveToNext()) {
					// Ignore files of 0 bytes size.
					long size = c.getLong(
							c.getColumnIndex(MediaColumns.SIZE));
					if (size == 0) {
						continue;
					}
					
					String data = c.getString(
							c.getColumnIndex(MediaColumns.DATA));
					
					// Ignore non-existent files (verify MediaStore cache)
					final File file = new File(data);
					if (!file.exists()) {
						Log.w(TAG, "Images MediaStore file not found.");
						continue;
					}
					
					// /DCIM/Camera matches /DCIM, so make sure we
					// only upload to the expected gallery.
					final File gallery = new File(path);
					if (!gallery.equals(file.getParentFile())) {
						continue;
					}
					
					String folderName = new File(path).getName();
					String resourcePath = null;
					if (folderName.toLowerCase().equals("camera") ||
							folderName.toLowerCase().equals("100media")) {
						// Upload to root of UDF.
						resourcePath = String.format("%s/%s",
								Preferences.getPhotoUploadResourcePath(),
								file.getName());
					} else {
						// Upload to a subfolder.
						resourcePath = String.format("%s/%s/%s",
								Preferences.getPhotoUploadResourcePath(),
								new File(path).getName(), // folder name
								file.getName()); // file name
					}
					
					Uri transferUri = TransferUtils.queueUpload(resolver,
							TransferPriority.AUTO, file, resourcePath);
					Log.d(TAG, "Queued upload " + transferUri);
				}
			}
		} finally {
			if (c != null) c.close();
		}
	}

	public static boolean startFrom(Context context) {
		Intent intent = new Intent(context, AutoUploadService.class);
		return context.startService(intent) != null;
	}

	public static boolean stopFrom(Context context) {
		Intent intent = new Intent(context, AutoUploadService.class);
		return context.stopService(intent);
	}
	
	public static void onBootComplete(Context context) {
		boolean isPhotoAutoUploadEnabled = Preferences.getBoolean(
				Preferences.PHOTO_UPLOAD_ENABLED_KEY, false);
		if (isPhotoAutoUploadEnabled) {
			AutoUploadService.startFrom(context);
		}
	}

	@Override
	public void onAutoUploadEventReceived() {
		updateCanAutoUpload();
	}

	public void updateCanAutoUpload() {
		if (networkStatusReceiver == null || batteryStatusReceiver == null)
			return;
		
		if (!Preferences.isPhotoUploadEnabled())
			return;
		
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
		
		if (canAutoUpload) {
			Log.i(TAG, "Settings allow auto-upload.");
			if (TransferUtils.getNonFailedUploadsCount(getContentResolver()) > 0) {
				Log.d(TAG, "Resuming failed uploads");
				startService(new Intent(UpDownService.ACTION_UPLOAD));
			} else {
				Log.d(TAG, "No uploads to resume");
			}
		} else {
			Log.i(TAG, "Settings do *not* allow auto-upload.");
		}
	}
	
	private void registerConnectivityReceiver(BroadcastReceiver receiver) {
		IntentFilter filter = new IntentFilter(
				ConnectivityManager.CONNECTIVITY_ACTION);
		getApplicationContext().registerReceiver(receiver, filter);
	}
	
	private void registerChargingReceiver(BroadcastReceiver receiver) {
		IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		getApplicationContext().registerReceiver(receiver, filter);
	}
	
	private void registerOnSharedPreferenceChangeListener() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		prefsChangeListener = new OnSharedPreferenceChangeListener() {
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
					String key) {
				if (key.equals(Preferences.SECONDARY_STORAGE)) return;
				Log.d(TAG, "onSharedPreferenceChanged() key: " + key);
				updateCanAutoUpload();
			}
		};
		prefs.registerOnSharedPreferenceChangeListener(prefsChangeListener);
	}
	
	private void unregisterOnSharedPreferenceChangeListener() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		prefs.unregisterOnSharedPreferenceChangeListener(prefsChangeListener);
	}
}
