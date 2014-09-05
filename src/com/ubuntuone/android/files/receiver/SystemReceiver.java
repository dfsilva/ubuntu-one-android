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

package com.ubuntuone.android.files.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ubuntuone.android.files.Alarms;
import com.ubuntuone.android.files.service.AutoUploadService;
import com.ubuntuone.android.files.util.Log;

public class SystemReceiver extends BroadcastReceiver {
	private final static String TAG = SystemReceiver.class.getSimpleName();

	@Override
	public void onReceive(Context context, Intent intent) {		
		final String action = intent.getAction();
		if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
			Log.i(TAG, action);
			AutoUploadService.onBootComplete(context);
		} else if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
			Log.i(TAG, action);
			Alarms.onMediaMounted();
		} else {
			Log.w(TAG, "Unhandled broadcast: " + action);
		}
	}
}
