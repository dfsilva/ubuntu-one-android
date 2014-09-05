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

import android.app.Service;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build.VERSION;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import com.ubuntuone.android.files.util.Log;

/**
 * A {@link Service}, which acquires wake lock for the period of it's life cycle
 * (between onCreate and onDestroy) and optionally allows acquiring Wi-Fi lock.
 */
public abstract class AwakeService extends AuthenticatedService {
	protected String TAG = "AwakeService";
	
	private WakeLock mWakeLock;
	private WifiLock mWifiLock;

	@Override
	public void onCreateAuthenticated() {
		acquireWakeLock();
		Log.d(TAG, "Acquired wake locks.");
	}

	@Override
	public void onDestroyAuthenticated() {
		releaseWakeLock();
		Log.d(TAG, "Released wake locks.");
	}
	
	private void acquireWakeLock() {
		if (mWakeLock == null) {
			PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
			mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
			mWakeLock.setReferenceCounted(false);
		}
		mWakeLock.acquire();
	}
	
	private void releaseWakeLock() {
		if (mWakeLock != null && mWakeLock.isHeld()) {
			mWakeLock.release();
		}
	}
	
	/* XXX We're not ready yet to bump build target sdk to 14, which breaks the
	 * menu button. We're still using GreenDroid, so no fancy ActionBar for
	 * context actions. Thus, we *need* the menu button. Back to target sdk 8.
	 */
	private static final int HONEYCOMB_MR1 = 13;
	private static final int WIFI_MODE_FULL_HIGH_PERF = 3;
	
	protected void acquireWifiLock() {
		if (mWifiLock == null) {
			WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
			int wifiLockMode = WifiManager.WIFI_MODE_FULL;
			if (VERSION.SDK_INT >= HONEYCOMB_MR1) {
				wifiLockMode = WIFI_MODE_FULL_HIGH_PERF;
			}
			mWifiLock = wm.createWifiLock(wifiLockMode, TAG);
			mWifiLock.setReferenceCounted(true);
		}
		mWifiLock.acquire();
	}
	
	protected void releaseWifiLock() {
		if (mWifiLock != null && mWifiLock.isHeld()) {
			mWifiLock.release();
		}
	}
}
