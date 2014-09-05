/*
 * Ubuntu One Files - access Ubuntu One cloud storage on Android platform.
 * 
 * Copyright (C) 2011 Canonical Ltd.
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

package com.ubuntuone.android.files.util;

import java.util.LinkedList;
import java.util.List;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;

public class MediaScannerHelper
{
	private static final String TAG = MediaScannerHelper.class.getSimpleName();
	
	private MediaScannerConnection mediaScannerConnection;
	
	private MediaScannerConnectionClient mediaScannerConnectionClient;
	
	private final List<String> filePaths = new LinkedList<String>();
	
	public MediaScannerHelper(Context context) {
		mediaScannerConnectionClient = new MediaScannerConnectionClient() {
			
			@Override
			public void onMediaScannerConnected() {
				Log.d(TAG, "Connected to MediaScanner.");
				scanNext();
			}
			
			@Override
			public void onScanCompleted(String path, Uri uri) {
				Log.i(TAG, "Scan completed: " + path);
				scanNext();
			}
		};
		
		mediaScannerConnection = new MediaScannerConnection(context,
				mediaScannerConnectionClient);
	}
	
	public void scanFile(String path) {
		synchronized (filePaths) {
			filePaths.add(path);
			if (! mediaScannerConnection.isConnected()) {
				Log.d(TAG, "Connecting to MediaScanner...");
				mediaScannerConnection.connect();
			}
		}
	}
	
	public void scanNext() {
		if (filePaths.size() == 0) {
			Log.d(TAG, "Disconnecting from MediaScanner.");
			mediaScannerConnection.disconnect();
		} else {
			synchronized (filePaths) {
				final String path = filePaths.get(0);
				filePaths.remove(0);
				mediaScannerConnection.scanFile(path, "*/*");
			}
		}
	}
}
