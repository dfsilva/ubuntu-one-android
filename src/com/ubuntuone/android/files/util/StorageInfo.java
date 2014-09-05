/*
 * Ubuntu One Files - access Ubuntu One cloud storage on Android platform.
 * 
 * Copyright (C) 2011 Canonical Ltd.
 * Author: Chad MILLER <chad.miller@canonical.com>
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import android.os.Environment;


/** Utility functions to query and store information about the SDCard "external
 * storage". */
public final class StorageInfo {
	private final static String TAG = StorageInfo.class.getSimpleName();
	private final static String LAST_PUSHED_PHOTO_TIMESTAMP_FILENAME = "photo-upload-timestamp.bin";
	private final static String IO_CHARSET = "US-ASCII";

	public static class StorageNotAvailable extends Exception {
		public static final long serialVersionUID = 0x1L;
		public StorageNotAvailable() { super(); }
		public StorageNotAvailable(String s) { super(s); }
	}

	private StorageInfo() {}

	private static File getDataDirectory()
			throws StorageNotAvailable {
		final File sdcardRoot = Environment.getExternalStorageDirectory();
		final File dataLocation = new File(sdcardRoot,
				"Android/data/com.ubuntuone.android.files/files/config");
		if (! dataLocation.isDirectory()) {
			Log.i(TAG, "Creating config directory for new storage.");
			dataLocation.delete(); // Doesn't throw IOException
			dataLocation.mkdirs();
		}
		if (! dataLocation.isDirectory()) {
			throw new StorageNotAvailable("Making data directory");
		}
		return dataLocation;
	}


	synchronized
	public static long getLastUploadedPhotoTimestamp() throws StorageNotAvailable {
		final File source = new File(getDataDirectory(), LAST_PUSHED_PHOTO_TIMESTAMP_FILENAME);
		Log.d(TAG, "Getting last known upload timestamp.");
		InputStream in = null;
		try {
			byte[] buffer = new byte[13]; // length of str(2**32)+3
			in = new BufferedInputStream(new FileInputStream(source));
			in.read(buffer, 0, 13);
			String value = new String(buffer, IO_CHARSET);
			if (value.equals("")) {
				return 0L; // Error value, if none
			}
			return parseLong(value); // FIXME Chad, this doesn't work Long.valueOf(value);
		} catch (FileNotFoundException e) {
			Log.e(TAG, "Reading from photo timestamp.", e);
			return 0L; // Error value, if none
		} catch (UnsupportedEncodingException e) {
			Log.e(TAG, "Reading from photo timestamp.", e);
			return 0L; // Error value, if none
		} catch (IOException e) {
			Log.e(TAG, "Reading from photo timestamp.");
			return 0L; // Error value, if none
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					Log.e(TAG, "Reading from photo timestamp.", e);
					return 0L; // Error value, if none
				}
			}
		}
	}
	
	private static Long parseLong(String value) {
		Long result = Long.valueOf(0L);
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if ('0' <= c && c <= '9') {
				result = 10*result + (c - '0');
			} else {
				break;
			}
		}
		return result;
	}
	
	public static final String[] knownSecondaryStoragePaths = new String[] {
		"/mnt/sdcard-ext", // Motorola
		"/mnt/sdcard/external_sd", // Samsung
		"/mnt/ext_card", // Samsung
		"/sdcard/_ExternalSD",
		"/mnt/sdcard2"
	};
		
	/**
	 * If the device has a vendor specific secondary storage, this method will
	 * return its path. Returning a non-null path does not imply the storage
	 * is mounted.
	 * 
	 * @return secondary storage path if present, null otherwise
	 */
	public static String findSecondaryStorageDirectory() {
		for (String path : knownSecondaryStoragePaths) {
			File file = new File(path);
			if (file.exists() && file.isDirectory() && file.canWrite()) {
				return file.getAbsolutePath();
			}
		}
		return null;
	}
	
	public static String trimKnownStoragePrefix(String path) {
		return null;
	}
}
