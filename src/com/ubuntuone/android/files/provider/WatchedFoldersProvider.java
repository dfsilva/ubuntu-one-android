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
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.ubuntuone.android.files.provider.WatchedFoldersContract.WatchedFolders;
import com.ubuntuone.android.files.provider.WatchedFoldersContract.WatchedFolders.Audio;
import com.ubuntuone.android.files.provider.WatchedFoldersContract.WatchedFolders.Images;
import com.ubuntuone.android.files.provider.WatchedFoldersContract.WatchedFolders.Video;
import com.ubuntuone.android.files.provider.WatchedFoldersDatabase.Tables;
import com.ubuntuone.android.files.util.Log;
import com.ubuntuone.android.files.util.SelectionBuilder;

/**
 * Provider that stores {@link WatchedFoldersContract} data.
 */
public class WatchedFoldersProvider extends ContentProvider {
	private static final String TAG = WatchedFoldersProvider.class.getSimpleName();
	
	private WatchedFoldersDatabase mWatchedFoldersDatabase;
	
	private static final UriMatcher sUriMatcher = buildUriMatcher();
	
	public static final int IMAGES = 100;
	public static final int IMAGES_ID = 101;
	
	public static final int VIDEO = 200;
	public static final int VIDEO_ID = 201;
	
	public static final int AUDIO = 300;
	public static final int AUDIO_ID = 301;
	
	@Override
	public boolean onCreate() {
		final Context context = getContext();
		mWatchedFoldersDatabase = new WatchedFoldersDatabase(context);
		return true;
	}
	
	@Override
	public ContentProviderResult[] applyBatch(
			ArrayList<ContentProviderOperation> operations)
			throws OperationApplicationException {
		final SQLiteDatabase db = mWatchedFoldersDatabase.getWritableDatabase();
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
			final SQLiteDatabase db = mWatchedFoldersDatabase.getWritableDatabase();
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
		final SQLiteDatabase db = mWatchedFoldersDatabase.getWritableDatabase();
		final int match = sUriMatcher.match(uri);
		switch(match) {
		case IMAGES:
			long imagesFolderId = db.insertOrThrow(Tables.WATCHED_IMAGE_FOLDERS, null, values);
			return Images.buildWatchedFolderUri(String.valueOf(imagesFolderId));
		case VIDEO:
			long videoFolderId = db.insertOrThrow(Tables.WATCHED_VIDEO_FOLDERS, null, values);
			return Images.buildWatchedFolderUri(String.valueOf(videoFolderId));
		case AUDIO:
			long audioFolderId = db.insertOrThrow(Tables.WATCHED_IMAGE_FOLDERS, null, values);
			return Images.buildWatchedFolderUri(String.valueOf(audioFolderId));
		default:
			throw new UnsupportedOperationException("Unknown uri: " + uri);
		}
	}
	
	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		Log.v(TAG, "update(uri = " + uri + ", selection = " + selection +
				", selectionArgs = " + Arrays.toString(selectionArgs) +
				", values = " + values.toString() +
				")");
		final SQLiteDatabase db = mWatchedFoldersDatabase.getWritableDatabase();
		final SelectionBuilder builder = buildSimpleSelection(uri);
		return builder.where(selection, selectionArgs).update(db, values);
	}
	
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		Log.v(TAG, "query(uri = " + uri + ", selection = " + selection
				+ ", selectionArgs = " + Arrays.toString(selectionArgs) + ")");
		final SQLiteDatabase db = mWatchedFoldersDatabase.getReadableDatabase();	
		final int match = sUriMatcher.match(uri);
		
		switch (match) {
		// This is dead simple, same table types and default sort order.
		// We just want make sure the provided uri is supported.
		case IMAGES:
			//$FALL-THROUGH$
		case IMAGES_ID:
			//$FALL-THROUGH$
		case VIDEO:
			//$FALL-THROUGH$
		case VIDEO_ID:
			//$FALL-THROUGH$
		case AUDIO:
			//$FALL-THROUGH$
		case AUDIO_ID:
			if (sortOrder == null)
				sortOrder = WatchedFolders.DEFAULT_SORT;
			final SelectionBuilder builder = buildExpandedSelection(uri, match);
			return builder.where(selection, selectionArgs)
				.query(db, projection, sortOrder);
			default:
				throw new UnsupportedOperationException("Unknown uri: " + uri);
		}
	}
	
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		Log.v(TAG, "delete(uri = " + uri + ")");
		final SQLiteDatabase db = mWatchedFoldersDatabase.getWritableDatabase();
		final SelectionBuilder builder = buildSimpleSelection(uri);
		return builder.where(selection, selectionArgs).delete(db);
	}
	
	private SelectionBuilder buildSimpleSelection(Uri uri) {
		final int match = sUriMatcher.match(uri);
		return buildExpandedSelection(uri, match);
	}
	
	private SelectionBuilder buildExpandedSelection(Uri uri, int match) {
		final SelectionBuilder builder = new SelectionBuilder();
		switch (match) {
		case IMAGES:
			return builder.table(Tables.WATCHED_IMAGE_FOLDERS);
		case IMAGES_ID:
			final String imagesFolderId = WatchedFolders.getWatchedFolderId(uri);
			return builder.table(Tables.WATCHED_IMAGE_FOLDERS)
					.where(WatchedFolders._ID + "=?", imagesFolderId);
		case VIDEO:
			return builder.table(Tables.WATCHED_VIDEO_FOLDERS);
		case VIDEO_ID:
			final String videoFolderId = WatchedFolders.getWatchedFolderId(uri);
			return builder.table(Tables.WATCHED_VIDEO_FOLDERS)
					.where(WatchedFolders._ID + "=?", videoFolderId);
		case AUDIO:
			return builder.table(Tables.WATCHED_AUDIO_FOLDERS);
		case AUDIO_ID:
			final String audioFolderId = WatchedFolders.getWatchedFolderId(uri);
			return builder.table(Tables.WATCHED_AUDIO_FOLDERS)
					.where(WatchedFolders._ID + "=?", audioFolderId);
			default:
				throw new UnsupportedOperationException("Unknown uri: " + uri);
		}
	}
	
	private static UriMatcher buildUriMatcher() {
		final UriMatcher m = new UriMatcher(UriMatcher.NO_MATCH);
		final String authority = WatchedFoldersContract.CONTENT_AUTHORITY;
		
		m.addURI(authority, "images", IMAGES);
		m.addURI(authority, "images/#", IMAGES_ID);
		
		m.addURI(authority, "video", VIDEO);
		m.addURI(authority, "video/#", VIDEO_ID);
		
		m.addURI(authority, "video", AUDIO);
		m.addURI(authority, "video/#", AUDIO_ID);
		
		return m;
	}
	
	@Override
	public String getType(Uri uri) {
		final int match = sUriMatcher.match(uri);
		switch (match) {
		case IMAGES:
			return Images.CONTENT_TYPE;
		case IMAGES_ID:
			return Images.CONTENT_ITEM_TYPE;
		case VIDEO:
			return Video.CONTENT_TYPE;
		case VIDEO_ID:
			return Video.CONTENT_ITEM_TYPE;
		case AUDIO:
			return Audio.CONTENT_TYPE;
		case AUDIO_ID:
			return Audio.CONTENT_ITEM_TYPE;
		default:
			throw new UnsupportedOperationException("Unknown uri: " + uri);
		}
	}
	
}
