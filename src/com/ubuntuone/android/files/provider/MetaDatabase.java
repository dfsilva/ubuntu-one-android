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

import com.ubuntuone.android.files.provider.MetaContract.Nodes;
import com.ubuntuone.android.files.provider.MetaContract.NodesColumns;
import com.ubuntuone.android.files.provider.MetaContract.Volumes;
import com.ubuntuone.android.files.util.Log;

public class MetaDatabase extends SQLiteOpenHelper {	
	private static final String TAG = MetaDatabase.class.getSimpleName();
	
	/** Database name found under app_dir/databases */
	private static final String DATABASE_NAME = "u1files.db";
	
	/** Update identifiers go here. */
	private static final int VER_INITIAL = 1;
	private static final int VER_INDEX_ON_NODE_KEY = 2;
	
	/** Current database version. */
	private static final int DATABASE_VERSION = VER_INDEX_ON_NODE_KEY;
	
	interface Tables {
		String VOLUMES = "volumes";
		String NODES = "nodes";
		String VOLUMES_NODES_JOIN = "volumes_nodes";
	}
	
	private interface Qualified {
		String NODES_VOLUME_PATH =
				Tables.NODES + " (" + NodesColumns.NODE_VOLUME_PATH + ")";
		String NODES_PARENT_PATH =
				Tables.NODES + " (" + NodesColumns.NODE_PARENT_PATH + ")";
		String NODES_KEY =
				Tables.NODES + " (" + NodesColumns.NODE_KEY + ")";
	}
	
	public MetaDatabase(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.d(TAG, "onCreate");
		db.execSQL("CREATE TABLE " + Tables.VOLUMES + " ("
				+ Volumes._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
				+ Volumes._COUNT + " INTEGER, "
				+ Volumes.VOLUME_RESOURCE_PATH + " TEXT NOT NULL, "
				+ Volumes.VOLUME_RESOURCE_STATE + " TEXT, "
				+ Volumes.VOLUME_TYPE + " TEXT NOT NULL, "
				+ Volumes.VOLUME_PATH + " TEXT NOT NULL, "
				+ Volumes.VOLUME_GENERATION + " INTEGER, "
				+ Volumes.VOLUME_WHEN_CREATED + " INTEGER, "
				+ Volumes.VOLUME_NODE_PATH + " TEXT, "
				+ Volumes.VOLUME_CONTENT_PATH + " TEXT, "
				+ "UNIQUE (" + Volumes._ID + ") ON CONFLICT REPLACE)");
		
		db.execSQL("CREATE TABLE " + Tables.NODES + " ("
				+ Nodes._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
				+ Nodes._COUNT + " INTEGER, "
				+ Nodes.NODE_RESOURCE_PATH + " TEXT NOT NULL, "
				+ Nodes.NODE_RESOURCE_STATE + " TEXT, "
				+ Nodes.NODE_PARENT_PATH + " TEXT, "
				+ Nodes.NODE_VOLUME_PATH + " TEXT, "
				+ Nodes.NODE_KEY + " TEXT, "
				+ Nodes.NODE_KIND + " TEXT NOT NULL, "
				+ Nodes.NODE_PATH + " TEXT NOT NULL, "
				+ Nodes.NODE_NAME + " TEXT, "
				+ Nodes.NODE_WHEN_CREATED + " INTEGER, "
				+ Nodes.NODE_WHEN_CHANGED + " INTEGER, "
				+ Nodes.NODE_GENERATION + " INTEGER, "
				+ Nodes.NODE_GENERATION_CREATED + " INTEGER, "
				+ Nodes.NODE_CONTENT_PATH + " TEXT, "
				+ Nodes.NODE_IS_LIVE + " INTEGER, "
				+ Nodes.NODE_IS_SYNCED + " INTEGER, "
				
				// Files only:
				+ Nodes.NODE_IS_CACHED + " INTEGER, "
				+ Nodes.NODE_HASH + " TEXT, "
				+ Nodes.NODE_PUBLIC_URL + " TEXT, "
				+ Nodes.NODE_SIZE + " INTEGER, "
				+ Nodes.NODE_MIME + " TEXT, "
				+ Nodes.NODE_DATA + " TEXT, "
				
				// Directories only:
				+ Nodes.NODE_HAS_CHILDREN + " INTEGER, "
				+ "UNIQUE (" + Nodes._ID + ") ON CONFLICT REPLACE)");
		
		db.execSQL("CREATE INDEX "
				+ "nodes_by_volume_path ON " + Qualified.NODES_VOLUME_PATH);
		
		db.execSQL("CREATE INDEX "
				+ "nodes_by_parent_path ON " + Qualified.NODES_PARENT_PATH);
		
		createIndexOnNodeKey(db);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.d(TAG, "onUpgrade");
		Log.i(TAG, "db upgrade from v" + oldVersion + " to v" + newVersion);
		
		int version = oldVersion;
		// Use this switch for cascading database updates, starting at current
		// version and falling through to all future upgrade cases.
		switch (version) {
		case VER_INITIAL:
			// Nothing to do.
			version = VER_INITIAL;
		//$FALL-THROUGH$
		case VER_INDEX_ON_NODE_KEY:
			createIndexOnNodeKey(db);
			version = VER_INDEX_ON_NODE_KEY;
		}
		Log.d(TAG, "upgraded to version " + newVersion);
		if (version != DATABASE_VERSION) {
			Log.w(TAG, "unsuccessful, have to purge data during upgrade!");
			
			db.execSQL("DROP TABLE IF EXISTS " + Tables.VOLUMES);
			db.execSQL("DROP TABLE IF EXISTS " + Tables.NODES);
			onCreate(db);
		}
	}
	
	private void createIndexOnNodeKey(SQLiteDatabase db) {
		db.execSQL("CREATE INDEX nodes_by_key ON " + Qualified.NODES_KEY);
	}
}
