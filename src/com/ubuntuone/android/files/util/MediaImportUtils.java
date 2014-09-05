/*
 * Ubuntu One Files - access Ubuntu One cloud storage on Android platform.
 * 
 * Copyright 2012 Canonical Ltd.
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

import java.io.File;
import java.util.HashMap;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore.Images;

import com.ubuntuone.android.files.Preferences;
import com.ubuntuone.android.files.provider.WatchedFoldersContract.WatchedFolders;

public class MediaImportUtils
{
	public static final String TAG = MediaImportUtils.class.getSimpleName();
	
	public static final HashMap<String, String> appNames;
	
	static {
		// A few hand picked mappings from app folder name to app name.
		appNames = new HashMap<String, String>();
		appNames.put("CameraZOOM".toLowerCase(), "Camera ZOOM FX");
		appNames.put("Paper Pictures".toLowerCase(), "Paper Camera");
		appNames.put("100VIGNE".toLowerCase(), "Vignette");
		appNames.put("FastBurstCamera".toLowerCase(), "Fast Burst Camera");
		appNames.put("CameraFun".toLowerCase(), "Camera Fun Pro");
		appNames.put("CartoonCamera".toLowerCase(), "Cartoon Camera");
		appNames.put("PixlrOMatic".toLowerCase(), "Pixlr-o-matic");
		appNames.put("FunnyCams".toLowerCase(), "Funny Camera");
		appNames.put("retroCamera".toLowerCase(), "Retro Camera");
		appNames.put("OneManWithCamera".toLowerCase(), "Lomo Camera");
	}
	
	public static void importImageBuckets(Context context) {
		final Uri[] contentUris = new Uri[] {
				Images.Media.EXTERNAL_CONTENT_URI
		};
		final String[] projection = new String[] {
				Images.Media._ID,
				Images.Media.BUCKET_ID,
				Images.Media.DATA
		};
		
		for (Uri uri : contentUris) {
			importImageBucketsForUri(context, uri, projection);
		}
	}
	
	// The good:
	private static final String lcDcim = "dcim";
	private static final String lcPictures = "pictures";
	// and the ugly:
	private static final String lcDownload = "download";
	private static final String lcMusic = "music";
	private static final String lcTemp = "temp";
	
	/**
	 * This method takes a directory path as parameter. It will not validate
	 * if the path .isDirectory(), because it may actually not exist at the time
	 * of performing the check. This is due to the fact that for gallery paths
	 * both internal and external storage counterparts may be considered by the
	 * caller (whilst only one of them may exist at that time).
	 * 
	 * @param folderPath
	 *            The path of a gallery to check.
	 * @return True, if gallery path should be automatically included for auto
	 *         upload, false otherwise.
	 */
	private static boolean isPhotoAutoUploadCandidate(String folderPath) {
		File folder = new File(folderPath);
		File parent = folder.getParentFile();
		if (parent != null) {
			File sdCard = Environment.getExternalStorageDirectory();
			File sdExt = Preferences.getSecondaryStorageDirectory();
			// * Primary external storage folder or
			// * Secondary external storage folder
			if ((sdCard != null && sdCard.equals(parent)) ||
					(sdExt != null && sdExt.equals(parent))) {
				String lcName = folder.getName().toLowerCase();
				return !(lcName.equals(lcDownload) ||
						lcName.equals(lcMusic) ||
						lcName.equals(lcTemp));
			} else {
				// * DCIM or Pictures folder?
				String lcPhotoFolderName = parent.getName().toLowerCase();
				return lcPhotoFolderName.equals(lcDcim) ||
						lcPhotoFolderName.equals(lcPictures);
			}
		}
		return false;
	}
	
	private static void importImageBucketsForUri(Context context, Uri uri,
			String[] projection) {
		String u1Path = Preferences.getBaseDirectory();
		
		Cursor cursor = null;
		try {
			String selection = Images.Media.IS_PRIVATE + "=0 OR " +
					Images.Media.IS_PRIVATE + " IS NULL) GROUP BY (" +
					Images.Media.BUCKET_ID;
			cursor = context.getContentResolver().query(
					uri, projection, selection, null, null);
			
			if (cursor != null && cursor.isBeforeFirst()) {
				while (cursor.moveToNext()) {
					final String filePath = cursor.getString(
							cursor.getColumnIndex(Images.Media.DATA));
					final File file = new File(filePath);
					final File folder = file.getParentFile();
					
					if (folder != null && folder.exists() && folder.isDirectory()) {
						final String folderPath = folder.getAbsolutePath();
						String displayName = folder.getName();
						
						final String lcDisplayName = displayName.toLowerCase();
						if (appNames.containsKey(lcDisplayName)) {
							displayName = appNames.get(lcDisplayName);
						}
						
						if (folderPath.contains(u1Path)) {
							continue;
						}
						
						saveWatchedFolder(context,
								WatchedFolders.Images.CONTENT_URI,
								folderPath, displayName);
					} else {
						Log.e(TAG, "Parent file is null for: " + filePath);
					}
				}
			}
		} finally {
			if (cursor != null) cursor.close();
		}
	}
	
	private static void saveWatchedFolder(Context context, Uri uri,
			String folderPath, String displayName) {
		final String primaryStoragePath =
				Environment.getExternalStorageDirectory().getAbsolutePath();
		final File secondaryStorage =
				Preferences.getSecondaryStorageDirectory();
		final String secondaryStoragePath = secondaryStorage != null ?
				secondaryStorage.getAbsolutePath() : null;
		final ContentResolver contentResolver = context.getContentResolver();
		
		if (secondaryStoragePath != null &&
				folderPath.contains(secondaryStoragePath)) {
			// Edge case of application using secondary storage by default.
			// We first insert primary, then secondary storage entry.
			folderPath = folderPath.replace(
					secondaryStoragePath, primaryStoragePath);
		}
		
		final ContentValues values = new ContentValues(3);
		values.put(WatchedFolders.FOLDER_PATH, folderPath);
		values.put(WatchedFolders.DISPLAY_NAME, displayName);
		if (!isPhotoAutoUploadCandidate(folderPath)) {
			Log.w(TAG, "Not auto uploading by default: " + folderPath);
			values.put(WatchedFolders.AUTO_UPLOAD, 0);
		}
		contentResolver.insert(uri, values);
		
		if (secondaryStoragePath != null) {
			// Also insert same path, but for secondary storage,
			// in case user switches locations.
			final String folderPath2 = folderPath.replace(
					primaryStoragePath, secondaryStoragePath);
			values.remove(WatchedFolders.FOLDER_PATH);
			values.put(WatchedFolders.FOLDER_PATH, folderPath2);
			contentResolver.insert(WatchedFolders.Images.CONTENT_URI, values);
		}
	}
}
