/*
 * Ubuntu One Files - access Ubuntu One cloud storage on Android platform.
 * 
 * Copyright  2011-2013 Canonical Ltd.
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

package com.ubuntuone.android.files;

import greendroid.app.GDApplication;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.PreferenceManager;

import com.squareup.otto.Bus;
import com.squareup.otto.Produce;
import com.squareup.otto.ThreadEnforcer;
import com.ubuntuone.android.files.activity.FilesActivity;
import com.ubuntuone.android.files.activity.LoginActivity;
import com.ubuntuone.android.files.activity.StorageActivity;
import com.ubuntuone.android.files.event.AuthStateEvent;
import com.ubuntuone.android.files.provider.MetaUtilities;
import com.ubuntuone.android.files.provider.TransfersContract.TransferPriority;
import com.ubuntuone.android.files.service.AutoUploadService;
import com.ubuntuone.android.files.service.UpDownService;
import com.ubuntuone.android.files.util.ConfigUtilities;
import com.ubuntuone.android.files.util.Log;
import com.ubuntuone.android.files.util.MediaScannerHelper;
import com.ubuntuone.android.files.util.TransferUtils;

public final class UbuntuOneFiles extends GDApplication {
	public static final String TAG = UbuntuOneFiles.class.getSimpleName();
	
	private static UbuntuOneFiles sAppInstance;
	
	private Bus mBus = new Bus(ThreadEnforcer.ANY);
	private AuthStateEvent mAuthStateEvent = new AuthStateEvent();
	
	public static Bus getBus() {
		return sAppInstance.mBus;
	}
	
	private MediaScannerHelper mMediaScannerHelper;
	
	public UbuntuOneFiles() {
		super();
		sAppInstance = this;
		setupLogging();
		mBus.register(this);
	}
	
	@Produce
	public AuthStateEvent lastAuthStateEvent() {
		return mAuthStateEvent;
	}
	
	public void setLastAuthStateEvent(AuthStateEvent event) {
		mAuthStateEvent = event;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		Log.i(TAG, "Starting application");
		
		Preferences.reload();
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		
		if (Preferences.isCollectLogsEnabled()) {
			Log.enableCollectingLogs();
		}
		
		mMediaScannerHelper = new MediaScannerHelper(getApplicationContext());

		upgrade();
		
		Thread.setDefaultUncaughtExceptionHandler(
				new UncaughtExceptionHandler(
						Thread.getDefaultUncaughtExceptionHandler()));
		
		if (ConfigUtilities.isExternalStorageMounted()) {
			// Initially, set storage limit equal to available SD storage.
			long limit = Preferences.getLocalStorageLimit();
			if (limit == -1L) {
				long extAvail = StorageActivity.getAvailableExternalStorageSize();
				Preferences.setLocalStorageLimit(extAvail);
			}
		}
		
		MetaUtilities.resetFailedTransfers();
		if (Preferences.hasTokens(this)) {
			if (Preferences.isPhotoUploadEnabled()) {
				AutoUploadService.startFrom(this);
			}
			if (TransferUtils.getQueuedUploadsCount(
					getContentResolver(), TransferPriority.USER) > 0) {
				startService(new Intent(UpDownService.ACTION_UPLOAD));
			}
		}
	}
	
	@Override
	public void onTerminate() {
		mBus.unregister(this);
		Log.i(TAG, "Terminating application");
		super.onTerminate();
	}
	
	private void setupLogging() {
		Log.setLogLevel(android.util.Log.INFO);
	}
	
	/**
	 * This method performs any necessary upgrade steps. Should be called after
	 * {@link Preferences} have been reloaded.
	 */
	private void upgrade() {
		Preferences.updateVersionCode(this);
	}
	
	/**
	 * Gets Ubuntu One Files application instance.
	 * 
	 * @return the app instance
	 */
	public static UbuntuOneFiles getInstance() {
		return sAppInstance;
	}
	
	public static Context getContext() {
		return sAppInstance.getApplicationContext();
	}
	
	public static MediaScannerHelper getMediaScannerHelper() {
		return sAppInstance.mMediaScannerHelper;
	}
	
	public static String getApplicationVersion() {
		final Context ctx = sAppInstance;
		String version = null;
		PackageManager pm = ctx.getPackageManager();
		try {
			PackageInfo info = pm.getPackageInfo(ctx.getPackageName(), 0);
			version = info.versionName;
		} catch (NameNotFoundException e) {
			Log.e(TAG, "can't get app version", e);
			version = "?";
		}
		return version;
	}
	
	@Override
	public Class<?> getHomeActivityClass() {
		// We want the ActionBar to look the same on both of these, thus:
		if (Preferences.hasTokens(this))
			return FilesActivity.class;
		else
			return LoginActivity.class;
	}
	
	public static void showHome(Context context) {
		final Intent intent = new Intent(context,
				sAppInstance.getHomeActivityClass());
		context.startActivity(intent);
	}
	
	private class UncaughtExceptionHandler implements
			Thread.UncaughtExceptionHandler {
		
		private Thread.UncaughtExceptionHandler mDefaultHandler;
		
		public UncaughtExceptionHandler(
				Thread.UncaughtExceptionHandler defaultHandler) {
			mDefaultHandler = defaultHandler;
		}
		
		public void uncaughtException(Thread thread, Throwable ex) {
			// Set the crash flag.
			Preferences.set(Preferences.OOPS_FLAG_KEY);
			
			Log.d(TAG, "=== UNHANDLED === exception", ex);
			Log.disableCollectingLogs();
			
			// Pass it to default uncaught exception handler.
			mDefaultHandler.uncaughtException(thread, ex);
		}
	}
	
}
