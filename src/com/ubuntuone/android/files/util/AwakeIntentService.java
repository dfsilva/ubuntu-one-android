package com.ubuntuone.android.files.util;

import android.app.IntentService;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;


public abstract class AwakeIntentService extends IntentService {
	private static String TAG = AwakeIntentService.class.getSimpleName();
	
	private WakeLock mWakeLock;
	private WifiLock mWifiLock;

	public AwakeIntentService(String name) {
		super(name);
	}

	@Override
	public void onCreate() {
		acquireWakeLock();
		acquireWifiLock();
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		releaseWifiLock();
		releaseWakeLock();
		super.onDestroy();
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
		} else {
			Log.e(TAG, this.getClass().getSimpleName()
					+ " had mismatched release wake lock call");
		}
	}
	
	private void acquireWifiLock() {
		if (mWifiLock == null) {
			WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
			mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG);
			mWifiLock.setReferenceCounted(false);
		}
		mWifiLock.acquire();
	}
	
	private void releaseWifiLock() {
		if (mWifiLock != null && mWifiLock.isHeld()) {
			mWifiLock.release();
		} else {
			Log.e(TAG, this.getClass().getSimpleName()
					+ " had mismatched release wifi lock call");
		}
	}
	
}
