/*
 * Ubuntu One Files - access Ubuntu One cloud storage on Android platform.
 * 
 * Copyright 2011 Canonical Ltd.
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

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.ubuntuone.android.files.Preferences;
import com.ubuntuone.android.files.UbuntuOneFiles;

/**
 * File utilities related to size, MIME types, etc.
 * 
 * @author Michał Karnicki <mkarnicki@gmail.com>
 */
public class FileUtilities {
	public static final String TAG = FileUtilities.class.getSimpleName();
	
	public static String MIME_IMAGE = "image/";
	public static String MIME_IMAGE_ANY = "image/*";
	
	public static String MIME_VIDEO = "video/";
	public static String MIME_VIDEO_ANY = "video/*";
	
	public static String MIME_AUDIO = "audio/";
	public static String MIME_AUDIO_ANY = "audio/*";
	
	public static String MIME_MEDIA = "media/";
	public static String MIME_MEDIA_ANY = "media/*";
	
	public static String MIME_UNKNOWN = "*/*";
	
	public static String MIME_JPG = "image/jpg";
	public static String MIME_JPEG = "image/jpeg";
	
	private static MimeTypeMap mimeMap = MimeTypeMap.getSingleton();
	
	public static String getMime(String filename) {
		int lastIndex = filename.lastIndexOf('.');
		String mime = null;
		if (lastIndex != -1 && (lastIndex + 1 < filename.length())) {
			String extension = filename.substring(lastIndex + 1).toLowerCase();
			mime = mimeMap.getMimeTypeFromExtension(extension);
		}
		if (mime == null)
			mime = "application/octet-stream";
		return mime;
	}
	
	public static final long B = 1;
	// starting 100B display in KiB
	public static final long kibLimit = 100;
	public static final long KiB = 1024*B;
	// starting 100.000B display in MiB
	public static final long mibLimit = 1000*kibLimit;
	public static final long MiB = 1024*KiB;
	// starting 100.000.000B display in GiB
	public static final long gibLimit = 1000*mibLimit;
	public static final long GiB = 1024*MiB;
	
	
	/**
	 * Formatter.formatShortFileSize is not available on Android 1.5
	 * 
	 * @param ctx
	 * @param bytes
	 * @return
	 */
	public static String getHumanReadableSize(final long bytes) {
		String result = null;
		if (bytes >= gibLimit) {
			result = String.format("%.1f GB", bytes/(float)GiB);
		} else if (bytes >= mibLimit) {
			result = String.format("%.1f MB", bytes/(float)MiB);
		} else if (bytes >= kibLimit) {
			result = String.format("%.1f KB", bytes/(float)KiB);
		} else if (bytes >= 0) {
			result = String.format("%d B", bytes);
		} else {
			result = "?";
		}
		return result;
	}

	/**
	 * Gets a file:// uri for a given resource path.<br />
	 * Example resource path: /~/Ubuntu One/foo/bar/baz.txt<br />
	 * Would return: file://<proper base dir>/Ubuntu One/foo/bar/baz.txt
	 * 
	 * @param resourcePath
	 *            the resourcePath to get the {@link Uri} string for
	 * @return a file {@link Uri} for given resourcePath
	 */
	public static String getFileSchemeFromResourcePath(String resourcePath) {
		final String noHeadPath = resourcePath.substring(2);
		return ContentResolver.SCHEME_FILE + "://"
				+ Uri.encode(Preferences.getBaseDirectory() + noHeadPath, "/");
	}

	/**
	 * Gets an absolute file path for a given resource path.<br />
	 * Example resource path: /~/Ubuntu One/foo/bar/baz.txt<br />
	 * Would return: <proper base dir>/Ubuntu One/foo/bar/baz.txt
	 * 
	 * @param resourcePath
	 *            the resourcePath go get the path for
	 * @return an absolute file path for given resourcePath
	 */
	public static String getFilePathFromResourcePath(String resourcePath) {
		return Preferences.getBaseDirectory() + resourcePath.substring(2);
	}

	static boolean mExternalStorageAvailable = false;
	static boolean mExternalStorageWriteable = false;

	private static void updateExternalStorageState() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			mExternalStorageAvailable = mExternalStorageWriteable = true;
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			mExternalStorageAvailable = true;
			mExternalStorageWriteable = false;
		} else {
			mExternalStorageAvailable = mExternalStorageWriteable = false;
		}
	}
	
	public static File getCacheDirectory() {
		updateExternalStorageState();
		if (mExternalStorageWriteable == true) {
			return new File(Environment.getExternalStorageDirectory().getAbsolutePath() +
					"/Android/data/com.ubuntuone.android.files/cache");
		} else {
			Context context = UbuntuOneFiles.getInstance().getApplicationContext();
			return context.getCacheDir();
		}
	}
	
	public static File getThumbCacheDirectory(int thumbSize) {
		return new File(getCacheDirectory(),
				String.format("%s/%d", "thumbs", thumbSize));
	}
	
	public static void clearThumbsCacheDirectory(int thumbSize) {
		File sizedThumbCacheDirectory = getThumbCacheDirectory(thumbSize);
		File[] files = sizedThumbCacheDirectory.listFiles();
		if (files != null) {
			for (File file : files) {
				file.delete();
			}
		}
	}
	
	public static String injectCounterIntoFilename(String filename, int counter) {
		final int index = filename.lastIndexOf('.');
		String result = null;
		if (index == -1) {
			// Just append the counter.
			result = String.format("%s-%d", filename, counter);
		} else {
			// Inject counter.
			final String first = filename.substring(0, index);
			final String second = filename.substring(index, filename.length());
			result = String.format("%s-%d%s", first, counter, second);
		}
		return result;
	}
	
	public static void safeDeleteSilently(String path) {
		try {
			if (!TextUtils.isEmpty(path)) {
				// Legacy check.
				if (path.startsWith(ContentResolver.SCHEME_FILE)) {
					path = path.substring(7); //"file://".length()
				}
				
				File file = new File(path);
				if (!file.exists()) return;
				
				if (file.isDirectory() && isDirectoryEmpty(file)) {
					file.delete();
				} else if (file.isFile()) {
					file.delete();
				}
			}
		} catch (Exception e) {
			// Ignore.
		}
	}
	
	private static boolean isDirectoryEmpty(File dir) {
		File[] children = dir.listFiles();
		if (children == null) {
			return true;
		}
		if (children.length == 0) {
			return true;
		}
		return false;
	}
	
	public static String sanitize(String filename) {
		return filename.replace(":", "_");
	}
}
