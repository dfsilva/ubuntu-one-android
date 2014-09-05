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

public class WatchedFoldersContract
{
	interface WatchedFoldersColumns {
		/** Watched folder path. */
		String FOLDER_PATH = "folder_path";
		/** Displayed folder name. */
		String DISPLAY_NAME = "display_name";
		/** Flag indicating whether the folder has already been seen on configuration screen. */
		String IS_NEW = "is_new";
		/** Flag indicating whether upload media from this folder. */
		String AUTO_UPLOAD = "auto_upload";
		/**
		 * Flag indicating whether upload should persist full path to file,
		 * excluding the mount point.
		 */
		String PERSIST_PATH = "persist_path";
		/** Last modified time stamp in seconds of last uploaded file from this folder. */
		String LAST_UPLOADED = "last_uploaded";
	}
	
	public static final String CONTENT_AUTHORITY = 
		"com.ubuntuone.android.files.provider.WatchedFoldersProvider";

	public static final Uri BASE_CONTENT_URI =
		Uri.parse("content://" + CONTENT_AUTHORITY);
	
	public static class WatchedFolders implements BaseColumns, WatchedFoldersColumns {
		
		public static final String DEFAULT_SORT =
				WatchedFoldersColumns.IS_NEW + " DESC, " +
				WatchedFoldersColumns.DISPLAY_NAME + " COLLATE NOCASE ASC";
		
		private static String[] defaultProjection;
		
		public static String[] getDefaultProjection() {
			if (defaultProjection == null) {
				defaultProjection = new String[] {
						_ID,
						_COUNT,
						FOLDER_PATH,
						DISPLAY_NAME,
						IS_NEW,
						AUTO_UPLOAD,
						PERSIST_PATH,
						LAST_UPLOADED
					};
			}
			return defaultProjection;
		}
		
		public static ContentValues values(String path, String name,
				boolean isNew, boolean autoUpload, boolean persistPath,
				long lastUploaded) {
			final ContentValues values = new ContentValues();
			values.put(FOLDER_PATH, path);
			values.put(DISPLAY_NAME, name);
			values.put(IS_NEW, isNew);
			values.put(AUTO_UPLOAD, autoUpload);
			values.put(PERSIST_PATH, persistPath);
			values.put(LAST_UPLOADED, lastUploaded);
			return values;
		}
		
		protected static String getWatchedFolderId(Uri uri) {
			return uri.getPathSegments().get(1);
		}
		
		public static class Images {
			private static final String PATH_WATCHED_IMAGES = "images";
			
			public static final Uri CONTENT_URI =
					BASE_CONTENT_URI.buildUpon().appendEncodedPath(PATH_WATCHED_IMAGES).build();
			
			public static final String CONTENT_TYPE =
					"vnd.android.cursor.dir/vnd.ubuntuone.android.files.watch.images";
			public static final String CONTENT_ITEM_TYPE =
					"vnd.android.cursor.item/vnd.ubuntuone.android.files.watch.images";
			
			public static Uri buildWatchedFolderUri(long id) {
				return buildWatchedFolderUri(String.valueOf(id));
			}
			
			public static Uri buildWatchedFolderUri(String folderId) {
				return CONTENT_URI.buildUpon().appendPath(folderId).build();
			}
		}
		
		public static class Video {
			private static final String PATH_WATCHED_VIDEO = "video";
			
			public static final Uri CONTENT_URI =
					BASE_CONTENT_URI.buildUpon().appendEncodedPath(PATH_WATCHED_VIDEO).build();
			
			public static final String CONTENT_TYPE =
					"vnd.android.cursor.dir/vnd.ubuntuone.android.files.watch.video";
			public static final String CONTENT_ITEM_TYPE =
					"vnd.android.cursor.item/vnd.ubuntuone.android.files.watch.video";
			
			public static Uri buildWatchedFolderUri(long id) {
				return buildWatchedFolderUri(String.valueOf(id));
			}
			
			public static Uri buildWatchedFolderUri(String folderId) {
				return CONTENT_URI.buildUpon().appendPath(folderId).build();
			}
		}
		
		public static class Audio {
			private static final String PATH_WATCHED_AUDIO = "audio";
			
			public static final Uri CONTENT_URI =
					BASE_CONTENT_URI.buildUpon().appendEncodedPath(PATH_WATCHED_AUDIO).build();
			
			public static final String CONTENT_TYPE =
					"vnd.android.cursor.dir/vnd.ubuntuone.android.files.watch.audio";
			public static final String CONTENT_ITEM_TYPE =
					"vnd.android.cursor.item/vnd.ubuntuone.android.files.watch.audio";
			
			public static Uri buildWatchedFolderUri(long id) {
				return buildWatchedFolderUri(String.valueOf(id));
			}
			
			public static Uri buildWatchedFolderUri(String folderId) {
				return CONTENT_URI.buildUpon().appendPath(folderId).build();
			}
		}
	}
	
	private WatchedFoldersContract() {
	}
}
