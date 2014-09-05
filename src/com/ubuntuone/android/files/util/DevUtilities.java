package com.ubuntuone.android.files.util;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;

import com.ubuntuone.android.files.provider.TransfersContract.Uploads;

public class DevUtilities {
	private static final String TAG = "dev";
	
	public static void dumpBundleKeys(Intent intent) {
		if (intent == null) {
			Log.d(TAG, "intent is null");
			return;
		}
		dumpBundleKeys(intent.getExtras());
	}
	
	public static void dumpBundleKeys(Bundle bundle) {
		if (bundle == null) {
			Log.d(TAG, "extras bundle is null");
			return;
		}
		Log.d(TAG, "bundle keys:");
		for (String o : bundle.keySet()) {
			Log.d(TAG, o);
		}
	}
	
	public static void dumpTransfers(ContentResolver resolver) {
		String[] projection = new String[] {
				Uploads.RESOURCE_PATH, Uploads.STATE
		};
		Cursor c = resolver.query(Uploads.CONTENT_URI,
				projection, null, null, null);
		if (c != null) {
			try {
				while (c.moveToNext()) {
					Log.i(TAG, "upload: " + c.getString(c.getColumnIndex(
							Uploads.RESOURCE_PATH)) + ", " +
							c.getString(c.getColumnIndex(
									Uploads.STATE)));
				}
			} finally {
				c.close();
			}
		}
		
	}
}
