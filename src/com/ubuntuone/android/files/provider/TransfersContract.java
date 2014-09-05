/*
 * Ubuntu One Files - access Ubuntu One cloud storage on Android platform.
 * 
 * Copyright 2011-2012 Canonical Ltd.
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

package com.ubuntuone.android.files.provider;

import android.content.ContentValues;
import android.net.Uri;
import android.provider.BaseColumns;

public class TransfersContract {
	
	public interface TransferState {
		/** Transfer is queued. */
		String QUEUED = "queued";
		/** Waiting for condition, such as connection. */
		String WAITING = "waiting";
		/** Computing SHA1, performing checks, etc */
		String STARTING = "starting";
		/** Transfer is currently paused. */
		String RESUMING = "resuming";
		/** Transfer is resuming. */
		String PAUSED = "paused";
		/** Transfer is running. */
		String RUNNING = "running";
		/** Transfer has run, but failed. */
		String FAILED = "failed";
	}
	
	public interface TransferPriority {
		/** User initiated transfer. User transfers always take precedence. */
		int USER = 0;
		/** Auto initiated transfer. */
		int AUTO = 10;
	}
	
	
	interface TransferColumns extends BaseColumns {
		/** Current transfer state. */
		String STATE = "state";
		/** Timestamp of when the transfer was queued. */
		String WHEN = "when_added";
		/** Transfer priority. */
		String PRIORITY = "priority";
	}
	
	interface UploadColumns extends TransferColumns {
		/** File display name. */
		String NAME = "name";
		/** Absolute source file path. */
		String PATH = "path";
		/** Source file MIME type. */
		String MIME = "mime";
		/** Source file size in bytes. */
		String SIZE = "size";
		/** Bytes sent successfully, for resumable uploads. */
		String BYTES_SENT = "bytes_sent";
		/** U1 destination resource path. */
		String RESOURCE_PATH = "resource_path";
	}
	
	interface DownloadColumns extends TransferColumns {
		/** File display name. */
		String NAME = "name";
		/** Absolute destination file path. */
		String PATH = "path";
		/** Source file size in bytes. */
		String SIZE = "size";
		/** Source file content SHA1 checksum, to verify download. */
		String CHECKSUM = "checksum";
		/** Bytes received successfully, for resumable downloads. */
		String BYTES_RECEIVED = "bytes_received";
		/** U1 source resource path. */
		String RESOURCE_PATH = "resource_path";
	}
	
	public static final String CONTENT_AUTHORITY = 
		"com.ubuntuone.android.files.provider.TransfersProvider";

	public static final Uri BASE_CONTENT_URI =
		Uri.parse("content://" + CONTENT_AUTHORITY);
	
	public static class Uploads implements UploadColumns {
		private static final String PATH_UPLOADS = "uploads";
		private static final String PATH_USER_UPLOADS = "uploads/user";
		private static final String PATH_AUTO_UPLOADS = "uploads/auto";
		
		public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon()
				.appendEncodedPath(PATH_UPLOADS).build();
		public static final Uri USER_CONTENT_URI = BASE_CONTENT_URI.buildUpon()
				.appendEncodedPath(PATH_USER_UPLOADS).build();
		public static final Uri AUTO_CONTENT_URI = BASE_CONTENT_URI.buildUpon()
				.appendEncodedPath(PATH_AUTO_UPLOADS).build();
		public static final String CONTENT_TYPE =
				"vnd.android.cursor.dir/vnd.ubuntuone.android.files.transfer.upload";
		public static final String CONTENT_ITEM_TYPE =
				"vnd.android.cursor.item/vnd.ubuntuone.android.files.transfer.upload";
		
		public static final String DEFAULT_SORT = PRIORITY + " ASC, " + WHEN + " ASC";
		
		public static String[] defaultProjection;
		
		public static String[] getDefaultProjection() {
			if (defaultProjection == null) {
				defaultProjection = new String[] {
						_ID, _COUNT, STATE, WHEN, PRIORITY,
						NAME, PATH, MIME, SIZE, BYTES_SENT,
						RESOURCE_PATH
				};
			}
			return defaultProjection;
		}
		
		public static ContentValues values(String state, int priority,
				String name, String path, String mime,
				long size, String resourcePath) {
			final ContentValues values = new ContentValues();
			values.put(STATE, state);
			values.put(WHEN, System.currentTimeMillis());
			values.put(PRIORITY, priority);
			
			values.put(NAME, name);
			values.put(PATH, path);
			values.put(MIME, mime);
			values.put(SIZE, size);
			values.put(RESOURCE_PATH, resourcePath);
			return values;
		}
		
		protected static String getUploadId(Uri uri) {
			return uri.getPathSegments().get(1);
		}
		
		public static Uri buildUploadUri(String folderId) {
			return CONTENT_URI.buildUpon().appendPath(folderId).build();
		}
		
		public static Uri buildUploadUri(long id) {
			return buildUploadUri(String.valueOf(id));
		}
	}
	
	public static class Downloads implements DownloadColumns {
		private static final String PATH_DOWNLOADS = "downloads";
		
		public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon()
				.appendEncodedPath(PATH_DOWNLOADS).build();
		public static final String CONTENT_TYPE =
				"vnd.android.cursor.dir/vnd.ubuntuone.android.files.transfer.download";
		public static final String CONTENT_ITEM_TYPE =
				"vnd.android.cursor.item/vnd.ubuntuone.android.files.transfer.download";
		
		public static final String DEFAULT_SORT = PRIORITY + " ASC, " + WHEN + " ASC";
		
		public static String[] defaultProjection;
		
		public static String[] getDefaultProjection() {
			if (defaultProjection == null) {
				defaultProjection = new String[] {
						_ID, _COUNT, STATE, WHEN, PRIORITY, NAME, PATH,
						SIZE, CHECKSUM, BYTES_RECEIVED, RESOURCE_PATH
				};
			}
			return defaultProjection;
		}
		
		public static ContentValues values(String state, int priority,
				String name, String path, long size,
				String checksum, String resourcePath) {
			final ContentValues values = new ContentValues();
			values.put(STATE, state);
			values.put(WHEN, System.currentTimeMillis());
			values.put(PRIORITY, priority);
			
			values.put(NAME, name);
			values.put(PATH, path);
			values.put(SIZE, size);
			values.put(CHECKSUM, checksum);
			values.put(BYTES_RECEIVED, 0);
			values.put(RESOURCE_PATH, resourcePath);
			return values;
		}
		
		protected static String getDownloadId(Uri uri) {
			return uri.getPathSegments().get(1);
		}
		
		public static Uri buildDownloadUri(String folderId) {
			return CONTENT_URI.buildUpon().appendPath(folderId).build();
		}
		
		public static Uri buildDownloadUri(long id) {
			return buildDownloadUri(String.valueOf(id));
		}
	}
	
	private TransfersContract() {
	}
}
