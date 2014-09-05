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

import com.ubuntuone.android.files.provider.TransfersContract.Downloads;
import com.ubuntuone.android.files.provider.TransfersContract.TransferColumns;
import com.ubuntuone.android.files.provider.TransfersContract.TransferPriority;
import com.ubuntuone.android.files.provider.TransfersContract.Uploads;
import com.ubuntuone.android.files.util.Log;

public class TransfersDatabase extends SQLiteOpenHelper {
	private static final String TAG = TransfersDatabase.class.getSimpleName();
	
	private static final String DATABASE_NAME = "u1transfers.db";
	
	private static final int VER_INITIAL = 1;
	
	private static final int DATABASE_VERSION = VER_INITIAL;
	
	interface Tables {
		String UPLOAD = "upload";
		String DOWNLOAD = "download";
	}
	
	private interface Qualified {
		String UPLOAD_PATH = Tables.UPLOAD + " (" + Uploads.PATH + ")";
		String DOWNLOAD_PATH = Tables.DOWNLOAD + " (" + Downloads.PATH + ")";
	}
	
	public TransfersDatabase(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.d(TAG, "onCreate");
		final String transferColumns =
				TransferColumns.STATE + " TEXT NOT NULL, " +
				TransferColumns.WHEN + " INTEGER NOT NULL, " +
				TransferColumns.PRIORITY + " INTEGER DEFAULT " +
						TransferPriority.USER + ", ";
		
		db.execSQL("CREATE TABLE " + Tables.UPLOAD + "(" +
				Uploads._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
				Uploads._COUNT + " INTEGER, " +
				transferColumns +
				Uploads.NAME + " TEXT NOT NULL, " +
				Uploads.PATH + " TEXT NOT NULL, " +
				Uploads.MIME + " TEXT NOT NULL, " +
				Uploads.SIZE + " INTEGER NOT NULL, " +
				Uploads.BYTES_SENT + " INTEGER DEFAULT 0, " +
				Uploads.RESOURCE_PATH + " TEXT NOT NULL, " +
				"UNIQUE (" + Uploads._ID + ") ON CONFLICT REPLACE, " +
				"UNIQUE (" + Uploads.PATH + ") ON CONFLICT IGNORE" +
				")");
		db.execSQL("CREATE INDEX upload_by_path ON " + Qualified.UPLOAD_PATH);
		
		db.execSQL("CREATE TABLE " + Tables.DOWNLOAD + "(" +
				Downloads._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
				Downloads._COUNT + " INTEGER, " +
				transferColumns +
				Downloads.NAME + " TEXT NOT NULL, " +
				Downloads.PATH + " TEXT NOT NULL, " +
				Downloads.SIZE + " INTEGER NOT NULL, " +
				Downloads.CHECKSUM + " TEXT NOT NULL, " +
				Downloads.BYTES_RECEIVED + " INTEGER DEFAULT 0, " +
				Downloads.RESOURCE_PATH + " TEXT NOT NULL, " +
				"UNIQUE (" + Downloads._ID + ") ON CONFLICT REPLACE, " +
				"UNIQUE (" + Downloads.PATH + ") ON CONFLICT IGNORE" +
				")");
		db.execSQL("CREATE INDEX download_by_path ON " + Qualified.DOWNLOAD_PATH);
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
			
			db.execSQL("DROP TABLE IF EXISTS " + Tables.UPLOAD);
			db.execSQL("DROP TABLE IF EXISTS " + Tables.DOWNLOAD);
			onCreate(db);
		}
	}
}
