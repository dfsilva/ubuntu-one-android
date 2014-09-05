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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.ubuntuone.android.files.util.Log;

public class NetworkStatusReceiver extends BroadcastReceiver
{
	private static String TAG = NetworkStatusReceiver.class.getSimpleName();

	boolean isConnected = false;
	boolean isWifi = false;
	boolean isRoaming = true;

	private OnAutoUploadEventListener stateListener;

	public NetworkStatusReceiver(Context context,
			OnAutoUploadEventListener stateListener) {
		this.stateListener = stateListener;
		requestInitialState(context);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
			Log.i(TAG, action);
			onConnectivityAction(context);
		} else {
			Log.w(TAG, "Unhandled broadcast: " + action);
		}
	}

	public void requestInitialState(Context context) {
		Log.d(TAG, "requestInitialState");
		onConnectivityAction(context);
	}

	private void onConnectivityAction(Context context) {
		updateNetworkState(context);
		stateListener.onAutoUploadEventReceived();
	}

	private void updateNetworkState(Context context) {
		Log.d(TAG,  "getNetworkState");
		ConnectivityManager connectivityManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
		isConnected = networkInfo != null &&
				networkInfo.isAvailable() && networkInfo.isConnected();
		if (isConnected) {
			isWifi = networkInfo.getType() == ConnectivityManager.TYPE_WIFI ||
					networkInfo.getType() == ConnectivityManager.TYPE_WIMAX;
			isRoaming = networkInfo.isRoaming();
		} else {
			isWifi = false;
			isRoaming = false;
		}
		Log.i(TAG, String.format(Locale.US,
				"Network state: connected %b, roaming %b, wifi %b",
				isConnected, isRoaming, isWifi));
	}

	public boolean isConnected() {
		return isConnected;
	}

	public boolean isWifi() {
		return isWifi;
	}

	public boolean isRoaming() {
		return isRoaming;
	}
}
