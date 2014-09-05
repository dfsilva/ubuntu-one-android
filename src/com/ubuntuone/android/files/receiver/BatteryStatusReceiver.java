/*
 * Ubuntu One Files - access Ubuntu One cloud storage on Android platform.
 * 
 * Copyright 2013 Canonical Ltd.
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

package com.ubuntuone.android.files.receiver;

import java.util.Locale;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import com.ubuntuone.android.files.util.Log;

public class BatteryStatusReceiver extends BroadcastReceiver
{
	private final static String TAG = BatteryStatusReceiver.class.getSimpleName();

	boolean lastIsPlugged = false;
	boolean isPlugged = false;
	
	boolean lastIsCharging = false;
	boolean isCharging = false;

	private OnAutoUploadEventListener stateListener;

	public BatteryStatusReceiver(Context context,
			OnAutoUploadEventListener stateListener) {
		this.stateListener = stateListener;
		requestInitialState(context);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
			Log.d(TAG, action);
			onActionBatteryChanged(context);
		} else {
			Log.w(TAG, "Unhandled broadcast: " + action);
		}
	}

	public void requestInitialState(Context context) {
		Log.d(TAG, "requestInitialState");
		onActionBatteryChanged(context);
	}

	public void onActionBatteryChanged(Context context) {
		updateBatteryState(context);
		if (lastIsPlugged != isPlugged || lastIsCharging != isCharging) {
			lastIsPlugged = isPlugged;
			lastIsCharging = isCharging;
			stateListener.onAutoUploadEventReceived();
		}
	}

	public void updateBatteryState(Context context) {
		Log.d(TAG, "getBatteryState");
		IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		Intent intent = context.registerReceiver(null, filter);
		int pluggedFlag = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
		int statusFlag = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
		isPlugged = (pluggedFlag != -1) &&
				(pluggedFlag == BatteryManager.BATTERY_PLUGGED_USB ||
				pluggedFlag == BatteryManager.BATTERY_PLUGGED_AC);
		isCharging = (statusFlag != -1) &&
				(statusFlag == BatteryManager.BATTERY_STATUS_CHARGING ||
				statusFlag == BatteryManager.BATTERY_STATUS_FULL);
		Log.d(TAG, String.format(Locale.US,
				"Battery state: isPlugged %b, isCharging %b",
				isPlugged, isCharging));
	}

	public boolean isCharging() {
		return isPlugged || isCharging;
	}
}
