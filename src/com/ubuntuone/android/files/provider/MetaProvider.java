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

import java.util.ArrayList;
import java.util.Arrays;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.ubuntuone.android.files.provider.MetaContract.Nodes;
import com.ubuntuone.android.files.provider.MetaContract.Volumes;
import com.ubuntuone.android.files.provider.MetaContract.VolumesNodesJoin;
import com.ubuntuone.android.files.provider.MetaDatabase.Tables;
import com.ubuntuone.android.files.util.Log;
import com.ubuntuone.android.files.util.SelectionBuilder;

/**
 * Provider that stores {@link MetaContract} data.
 */
public class MetaProvider extends ContentProvider {
	private static final String TAG = MetaProvider.class.getSimpleName();
	
	private MetaDatabase mMetaDatabase;
	private ContentResolver mContentResolver;
	
	private static final UriMatcher sUriMatcher = buildUriMatcher();
	
	public static final int VOLUMES = 100;
	public static final int VOLUMES_ID = 101;
	
	public static final int NODES = 200;
	public static final int NODES_ID = 201;
	
	public static final int VOLUMES_NODES_JOIN = 300;
	
	@Override
	public boolean onCreate() {
		final Context context = getContext();
		mMetaDatabase = new MetaDatabase(context);
		mContentResolver = context.getContentResolver();
		return true;
	}
	
	@Override
	public ContentProviderResult[] applyBatch(
			ArrayList<ContentProviderOperation> operations)
			throws OperationApplicationException {
		final SQLiteDatabase db = mMetaDatabase.getWritableDatabase();
		db.beginTransaction();
		try {
			final ContentProviderResult[] result = super.applyBatch(operations);
			db.setTransactionSuccessful();
			return result;
		} finally {
			db.endTransaction();
		}
	}
	
	@Override
	public int bulkInsert(Uri uri, ContentValues[] values) {
		synchronized (this) {
			final SQLiteDatabase db = mMetaDatabase.getWritableDatabase();
			db.beginTransaction();
			try {
				super.bulkInsert(uri, values);
				db.setTransactionSuccessful();
				return values.length;
			} finally {
				db.endTransaction();
			}
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		Log.v(TAG, "insert(uri = " + uri + ", values = " + values.toString() + ")");
		final SQLiteDatabase db = mMetaDatabase.getWritableDatabase();
		final int match = sUriMatcher.match(uri);
		switch(match) {
			case NODES:
				long fileId = db.insertOrThrow(Tables.NODES, null, values);
				Uri nodeUri = Nodes.buildNodeUri(String.valueOf(fileId));
				mContentResolver.notifyChange(nodeUri, null);
				return nodeUri;
			case VOLUMES:
				long volumeId = db.insertOrThrow(Tables.VOLUMES, null, values);
				Uri volumeUri = Volumes.buildVolumeUri(String.valueOf(volumeId));
				mContentResolver.notifyChange(volumeUri, null);
				return volumeUri;
			default:
				throw new UnsupportedOperationException("Unknown uri: " + uri);
		}
	}
	
	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		Log.v(TAG, "update(uri = " + uri + ", values = " + values.toString()
				+ ")");
		final SQLiteDatabase db = mMetaDatabase.getWritableDatabase();
		final SelectionBuilder builder = buildSimpleSelection(uri);
		return builder.where(selection, selectionArgs).update(db, values);
	}
	
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		Log.v(TAG, "query(uri = " + uri + ", selection = " + selection
				+ ", selectionArgs = " + Arrays.toString(selectionArgs) + ")");
		final SQLiteDatabase db = mMetaDatabase.getReadableDatabase();	
		final int match = sUriMatcher.match(uri);
		switch (match) {
			case VOLUMES_NODES_JOIN:
				if (selection == null)
					selection = "";
				final SelectionBuilder builder0 = buildSimpleSelection(uri);
				builder0.where(selection, selectionArgs);
				builder0.where(MetaUtilities.getHiddenSelection(),
						(String[]) null);
				return builder0
						.query(db, projection, VolumesNodesJoin.DEFAULT_SORT);
			case NODES:
				//$FALL-THROUGH$
			case NODES_ID:
				if (sortOrder == null)
					sortOrder = Nodes.DEFAULT_SORT;
				final SelectionBuilder builder1 = buildExpandedSelection(uri, match);
				return builder1.where(selection, selectionArgs)
					.query(db, projection, sortOrder);
			case VOLUMES:
				if (sortOrder == null)
					sortOrder = Volumes.DEFAULT_SORT;
				final SelectionBuilder builder3 = buildExpandedSelection(uri, match);
				return builder3.where(selection, selectionArgs)
					.query(db, projection, sortOrder);
			default:
				throw new UnsupportedOperationException("Unknown uri: " + uri);
		}
	}
	
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		Log.v(TAG, "delete(uri = " + uri + ")");
		final SQLiteDatabase db = mMetaDatabase.getWritableDatabase();
		final SelectionBuilder builder = buildSimpleSelection(uri);
		return builder.where(selection, selectionArgs).delete(db);
	}
	
	private SelectionBuilder buildSimpleSelection(Uri uri) {
		final SelectionBuilder builder = new SelectionBuilder();
		final int match = sUriMatcher.match(uri);
		switch (match) {
			case NODES:
				return builder.table(Tables.NODES);
			case NODES_ID:
				final String fileId = Nodes.getNodeId(uri);
				return builder.table(Tables.NODES)
					.where(Nodes._ID + "=?", fileId);
			case VOLUMES:
				return builder.table(Tables.VOLUMES);
			case VOLUMES_ID:
				final String volumeId = Volumes.getVolumeId(uri);
				return builder.table(Tables.VOLUMES)
					.where(Volumes._ID + "=?", volumeId);
			case VOLUMES_NODES_JOIN:
				// Simple selection unused. Use selection clause instead.
				final String VN_JOIN = Tables.VOLUMES + " JOIN " + Tables.NODES
						+ " ON " + Volumes.VOLUME_NODE_PATH
						+ " = " + Nodes.NODE_RESOURCE_PATH;
				return builder.table(VN_JOIN);
			default:
				throw new UnsupportedOperationException("Unknown uri: " + uri);
		}
	}
	
	private SelectionBuilder buildExpandedSelection(Uri uri, int match) {
		final SelectionBuilder builder = new SelectionBuilder();
		switch (match) {
			case NODES:
				return builder.table(Tables.NODES);
			case NODES_ID:
				final String fileId = Nodes.getNodeId(uri);
				return builder.table(Tables.NODES)
					.where(Nodes._ID + "=?", fileId);
			case VOLUMES:
				return builder.table(Tables.VOLUMES);
			case VOLUMES_ID:
				final String volumeId = Volumes.getVolumeId(uri);
				return builder.table(Tables.VOLUMES)
					.where(Volumes._ID + "=?", volumeId);
			default:
				throw new UnsupportedOperationException("Unknown uri: " + uri);
		}
	}

	private static UriMatcher buildUriMatcher() {
		final UriMatcher m = new UriMatcher(UriMatcher.NO_MATCH);
		final String authority = MetaContract.CONTENT_AUTHORITY;
		
		m.addURI(authority, "nodes", NODES);
		m.addURI(authority, "nodes/#", NODES_ID);
		
		m.addURI(authority, "volumes", VOLUMES);
		m.addURI(authority, "volumes/#", VOLUMES_ID);
		
		return m;
	}
	
	@Override
	public String getType(Uri uri) {
		final int match = sUriMatcher.match(uri);
		switch (match) {
		case NODES:
			return MetaUtilities.getStringField(uri, Nodes.NODE_MIME);
		case NODES_ID:
			return Nodes.CONTENT_ITEM_TYPE;
		case VOLUMES:
			return Volumes.CONTENT_TYPE;
		case VOLUMES_ID:
			return Volumes.CONTENT_ITEM_TYPE;
		default:
			throw new UnsupportedOperationException("unknown uri: " + uri);
		}
	}
	
}
