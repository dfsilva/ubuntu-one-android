/*
 * Ubuntu One Files - access Ubuntu One cloud storage on Android platform.
 * 
 * Copyright (C) 2011 Canonical Ltd.
 * Author: Michał Karnicki <michal.karnicki@canonical.com>
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

import com.ubuntuone.android.files.provider.MetaUtilities;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;

public final class MediaUtilities {
	private static final String TAG = MetaUtilities.class.getSimpleName();
	
	// XXX this should use the _count column instead of c.getCount()
	
	public static int countImages2Sync(Context context, long since) {
		Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
		return countMedia2Sync(context, since, uri);	
	}
	
	public static int countVideos2Sync(Context context, long since) {
		Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
		return countMedia2Sync(context, since, uri);
	}
	
	public static int countAudio2Sync(Context context, long since) {
		Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		return countMedia2Sync(context, since, uri);
	}
	
	private static int countMedia2Sync(Context context, long since, Uri uri) {
		String[] projection = new String[] { MediaColumns._ID,
				MediaColumns.DATE_ADDED };
		String selection = MediaColumns.DATE_ADDED + ">?";
		String[] selectionArgs = new String[] { String.valueOf(since) };
		int count = 0;
		
		final ContentResolver resolver = context.getContentResolver();
		if (resolver != null) {
			final Cursor c = resolver.query(
					uri, projection, selection, selectionArgs, null);
			if (c != null) {
				try {
					count = c.getCount();
				} finally {
					c.close();
				}
			}
		} else {
			Log.w(TAG, "too early to use a resolver! just booted the system?");
		}
		return count;
	}
	
	private MediaUtilities() {
	}

}
