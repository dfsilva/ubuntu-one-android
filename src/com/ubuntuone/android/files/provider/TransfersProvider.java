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

import com.ubuntuone.android.files.provider.TransfersContract.Downloads;
import com.ubuntuone.android.files.provider.TransfersContract.TransferPriority;
import com.ubuntuone.android.files.provider.TransfersContract.Uploads;
import com.ubuntuone.android.files.provider.TransfersDatabase.Tables;
import com.ubuntuone.android.files.util.Log;
import com.ubuntuone.android.files.util.SelectionBuilder;

/**
 * Provider that stores {@link TransfersContract} data.
 */
public class TransfersProvider extends ContentProvider {
	private static final String TAG = TransfersProvider.class.getSimpleName();
	
	private TransfersDatabase mTransfersDatabase;
	
	private static final UriMatcher sUriMatcher = buildUriMatcher();
	
	public static final int UPLOAD = 100;
	public static final int USER_UPLOAD = 101;
	public static final int AUTO_UPLOAD = 102;
	public static final int UPLOAD_ID = 110;
	
	public static final int DOWNLOAD = 200;
	public static final int DOWNLOAD_ID = 210;
	
	@Override
	public boolean onCreate() {
		final Context context = getContext();
		mTransfersDatabase = new TransfersDatabase(context);
		return true;
	}
	
	@Override
	public ContentProviderResult[] applyBatch(
			ArrayList<ContentProviderOperation> operations)
			throws OperationApplicationException {
		final SQLiteDatabase db = mTransfersDatabase.getWritableDatabase();
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
			final SQLiteDatabase db = mTransfersDatabase.getWritableDatabase();
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
		final SQLiteDatabase db = mTransfersDatabase.getWritableDatabase();
		final int match = sUriMatcher.match(uri);
		switch(match) {
		case UPLOAD:
			long uploadId = db.insertOrThrow(Tables.UPLOAD, null, values);
			return Uploads.buildUploadUri(uploadId);
		case DOWNLOAD:
			long downloadId = db.insertOrThrow(Tables.DOWNLOAD, null, values);
			return Downloads.buildDownloadUri(downloadId);
		default:
			throw new UnsupportedOperationException("Unknown uri: " + uri);
		}
	}
	
	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		Log.v(TAG, "update(uri = " + uri + ", values = " + values.toString()
				+ ")");
		final SQLiteDatabase db = mTransfersDatabase.getWritableDatabase();
		final SelectionBuilder builder = buildSimpleSelection(uri);
		return builder.where(selection, selectionArgs).update(db, values);
	}
	
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		Log.v(TAG, "query(uri = " + uri + ", selection = " + selection
				+ ", selectionArgs = " + Arrays.toString(selectionArgs) + ")");
		final SQLiteDatabase db = mTransfersDatabase.getReadableDatabase();	
		final int match = sUriMatcher.match(uri);
		
		switch (match) {
		case UPLOAD:
			//$FALL-THROUGH$
		case UPLOAD_ID:
			if (sortOrder == null)
				sortOrder = Uploads.DEFAULT_SORT;
			final SelectionBuilder upBuilder = buildExpandedSelection(uri, match);
			return upBuilder.where(selection, selectionArgs)
					.query(db, projection, sortOrder);
		case DOWNLOAD:
			//$FALL-THROUGH$
		case DOWNLOAD_ID:
			if (sortOrder == null)
				sortOrder = Downloads.DEFAULT_SORT;
			final SelectionBuilder downBuilder = buildExpandedSelection(uri, match);
			return downBuilder.where(selection, selectionArgs)
					.query(db, projection, sortOrder);
		default:
			throw new UnsupportedOperationException("Unknown uri: " + uri);
		}
	}
	
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		Log.v(TAG, "delete(uri = " + uri + ")");
		final SQLiteDatabase db = mTransfersDatabase.getWritableDatabase();
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
		case UPLOAD:
			return builder.table(Tables.UPLOAD);
		case USER_UPLOAD:
			return builder.table(Tables.UPLOAD).where(Uploads.PRIORITY + "=?",
					String.valueOf(TransferPriority.USER));
		case AUTO_UPLOAD:
			return builder.table(Tables.UPLOAD).where(Uploads.PRIORITY + "=?",
					String.valueOf(TransferPriority.AUTO));
		case UPLOAD_ID:
			final String uploadId = Uploads.getUploadId(uri);
			return builder.table(Tables.UPLOAD)
					.where(Uploads._ID + "=?", uploadId);
		case DOWNLOAD:
			return builder.table(Tables.DOWNLOAD);
		case DOWNLOAD_ID:
			final String downloadId = Downloads.getDownloadId(uri);
			return builder.table(Tables.DOWNLOAD)
					.where(Downloads._ID + "=?", downloadId);
		default:
			throw new UnsupportedOperationException("Unknown uri: " + uri);
		}
	}
	
	private static UriMatcher buildUriMatcher() {
		final UriMatcher m = new UriMatcher(UriMatcher.NO_MATCH);
		final String authority = TransfersContract.CONTENT_AUTHORITY;
		
		m.addURI(authority, "uploads", UPLOAD);
		m.addURI(authority, "uploads/user", USER_UPLOAD);
		m.addURI(authority, "uplaods/auto", AUTO_UPLOAD);
		m.addURI(authority, "uploads/#", UPLOAD_ID);
		
		m.addURI(authority, "downloads", DOWNLOAD);
		m.addURI(authority, "downloads/#", DOWNLOAD_ID);
		
		return m;
	}
	
	@Override
	public String getType(Uri uri) {
		final int match = sUriMatcher.match(uri);
		switch (match) {
		case UPLOAD:
			//$FALL-THROUGH$
		case USER_UPLOAD:
			//$FALL-THROUGH$
		case AUTO_UPLOAD:
			return Uploads.CONTENT_TYPE;
		case UPLOAD_ID:
			return Uploads.CONTENT_ITEM_TYPE;
		case DOWNLOAD:
			return Downloads.CONTENT_TYPE;
		case DOWNLOAD_ID:
			return Downloads.CONTENT_ITEM_TYPE;
		default:
			throw new UnsupportedOperationException("Unknown uri: " + uri);
		}
	}
	
}
