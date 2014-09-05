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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.ubuntuone.android.files.provider.MetaContract.WatchedFoldersColumns;
import com.ubuntuone.android.files.provider.WatchedFoldersContract.WatchedFolders;
import com.ubuntuone.android.files.util.Log;

public class WatchedFoldersDatabase extends SQLiteOpenHelper
{
	private static final String TAG = WatchedFoldersDatabase.class.getSimpleName();
	
	private static final String DATABASE_NAME = "u1watched.db";
	
	private static final int VER_INITIAL = 1;
	
	private static final int DATABASE_VERSION = VER_INITIAL;
	
	interface Tables {
		String WATCHED_IMAGE_FOLDERS = "watched_images";
		String WATCHED_VIDEO_FOLDERS = "watched_video";
		String WATCHED_AUDIO_FOLDERS = "watched_audio";
	}
	
	private interface Qualified {
		String WATCHED_IMAGES_FOLDER_PATH =
				Tables.WATCHED_IMAGE_FOLDERS + " (" + WatchedFoldersColumns.FOLDER_PATH + ")";
		String WATCHED_VIDEO_FOLDER_PATH =
				Tables.WATCHED_VIDEO_FOLDERS + " (" + WatchedFoldersColumns.FOLDER_PATH + ")";
		String WATCHED_AUDIO_FOLDER_PATH =
				Tables.WATCHED_AUDIO_FOLDERS + " (" + WatchedFoldersColumns.FOLDER_PATH + ")";
	}
	
	public WatchedFoldersDatabase(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.d(TAG, "onCreate");
		final String tableBody = "(" +
				WatchedFolders._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
				WatchedFolders._COUNT + " INTEGER, " +
				WatchedFolders.FOLDER_PATH + " TEXT NOT NULL, " +
				WatchedFolders.DISPLAY_NAME + " TEXT NOT NULL, " +
				WatchedFolders.IS_NEW + " INTEGER DEFAULT 1, " +
				WatchedFolders.AUTO_UPLOAD + " INTEGER DEFAULT 1, " +
				WatchedFolders.PERSIST_PATH + " INTEGER DEFAULT 0, " +
				WatchedFolders.LAST_UPLOADED + " INTEGER DEFAULT 0, " +
				"UNIQUE (" + WatchedFolders._ID + ") ON CONFLICT REPLACE, " +
				// Do not insert given path more than once.
				"UNIQUE (" + WatchedFolders.FOLDER_PATH + ") ON CONFLICT IGNORE)";
		
		db.execSQL("CREATE TABLE " + Tables.WATCHED_IMAGE_FOLDERS + tableBody);
		db.execSQL("CREATE INDEX watchedimagefolders_by_path ON "
				+ Qualified.WATCHED_IMAGES_FOLDER_PATH);
		
		db.execSQL("CREATE TABLE " + Tables.WATCHED_VIDEO_FOLDERS + tableBody);
		db.execSQL("CREATE INDEX watchedvideofolders_by_path ON "
				+ Qualified.WATCHED_VIDEO_FOLDER_PATH);
		
		db.execSQL("CREATE TABLE " + Tables.WATCHED_AUDIO_FOLDERS + tableBody);
		db.execSQL("CREATE INDEX watchedaudiofolders_by_path ON "
				+ Qualified.WATCHED_AUDIO_FOLDER_PATH);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.d(TAG, "onUpgrade");
		Log.i(TAG, "db upgrade from v" + oldVersion + " to v" + newVersion);
		
		int version = oldVersion;
		switch (version) {
		case VER_INITIAL:
			// Nothing to do.
			version = VER_INITIAL;
		}
		Log.d(TAG, "upgraded to version " + newVersion);
		if (version != DATABASE_VERSION) {
			Log.w(TAG, "unsuccessful, have to purge data during upgrade!");
			
			db.execSQL("DROP TABLE IF EXISTS " + Tables.WATCHED_IMAGE_FOLDERS);
			db.execSQL("DROP TABLE IF EXISTS " + Tables.WATCHED_VIDEO_FOLDERS);
			db.execSQL("DROP TABLE IF EXISTS " + Tables.WATCHED_AUDIO_FOLDERS);
			onCreate(db);
		}
	}
}
