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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.provider.MediaStore.MediaColumns;
import android.text.TextUtils;

import com.ubuntuone.android.files.Preferences;
import com.ubuntuone.android.files.UbuntuOneFiles;
import com.ubuntuone.android.files.provider.MetaContract.Nodes;
import com.ubuntuone.android.files.provider.MetaContract.ResourceState;
import com.ubuntuone.android.files.provider.MetaContract.Volumes;
import com.ubuntuone.android.files.util.FileUtilities;
import com.ubuntuone.android.files.util.Log;
import com.ubuntuone.api.files.model.U1File;
import com.ubuntuone.api.files.model.U1Node;
import com.ubuntuone.api.files.model.U1NodeKind;
import com.ubuntuone.api.files.util.HashUtils;

public final class MetaUtilities
{
	private static final String TAG = MetaUtilities.class.getSimpleName();
	
	private static ContentResolver sResolver;
	
	static {
		sResolver = UbuntuOneFiles.getInstance().getContentResolver();
	}
	
	private MetaUtilities() {
	}
	
	public static void notifyChange(Uri uri) {
		sResolver.notifyChange(uri, null);
	}
	
	final static String sSelection = Nodes.NODE_RESOURCE_PATH + "=?";
	
	public static String getStringField(Uri uri, String columnName) {
		String result = null;
		final String[] projection = new String[] { columnName };
		final Cursor c = sResolver.query(uri, projection, null, null, null);
		try {
			if (c.moveToFirst()) {
				result = c.getString(c.getColumnIndex(columnName));
			}
		} finally {
			c.close();
		}
		return result;
	}
	
	public static String getStringField(String resourcePath, String columnName) {
		String result = null;
		final String[] projection = new String[] { columnName };
		final String[] selectionArgs = new String[] { resourcePath };
		final Cursor c = sResolver.query(Nodes.CONTENT_URI,
				projection, sSelection, selectionArgs, null);
		try {
			if (c.moveToFirst()) {
				result = c.getString(c.getColumnIndex(columnName));
			}
		} finally {
			c.close();
		}
		return result;
	}
	
	public static Cursor getNodeCursorByResourcePath(String resourcePath,
			String[] projection) {
		final String[] selectionArgs = new String[] { resourcePath };
		final Cursor cursor = sResolver.query(Nodes.CONTENT_URI, projection,
				sSelection, selectionArgs, null);
		return cursor;
	}
	
	public static U1Node getNodeByKey(String key) {
		String[] selectionArgs = new String[] { key };
		String selection = Nodes.NODE_KEY + "=?";
		String[] projection = Nodes.getDefaultProjection();
		final Cursor c = sResolver.query(Nodes.CONTENT_URI, projection,
				selection, selectionArgs, null);
		if (c != null) {
			try {
				if (c.moveToFirst()) {
					String resourcePath;
					U1NodeKind kind;
					Boolean isLive = true;
					String path;
					String parentPath;
					String volumePath;
					Date whenCreated;
					Date whenChanged;
					Long generation;
					Long generationCreated;
					String contentPath;
					
					resourcePath = c.getString(c.getColumnIndex(Nodes.NODE_RESOURCE_PATH));
					kind = U1NodeKind.valueOf(c.getString(
							c.getColumnIndex(Nodes.NODE_KIND)).toUpperCase(Locale.US));
					isLive = c.getInt(c.getColumnIndex(Nodes.NODE_IS_LIVE)) != 0;
					path = c.getString(c.getColumnIndex(Nodes.NODE_PATH));
					parentPath = c.getString(c.getColumnIndex(Nodes.NODE_PARENT_PATH));
					volumePath = c.getString(c.getColumnIndex(Nodes.NODE_VOLUME_PATH));
					whenCreated = new Date(c.getLong(c.getColumnIndex(Nodes.NODE_WHEN_CREATED)));
					whenChanged = new Date(c.getLong(c.getColumnIndex(Nodes.NODE_WHEN_CHANGED)));
					generation = c.getLong(c.getColumnIndex(Nodes.NODE_GENERATION));
					generationCreated = c.getLong(c.getColumnIndex(Nodes.NODE_GENERATION_CREATED));
					contentPath = c.getString(c.getColumnIndex(Nodes.NODE_CONTENT_PATH));
					
					return new U1Node(resourcePath, kind, isLive, path, parentPath,
							volumePath, key, whenCreated, whenChanged, generation,
							generationCreated, contentPath);
				} else {
					return null;
				}
			} finally {
				c.close();
			}
		}
		return null; 
	}
	
	public static U1Node getNodeByResourcePath(String resourcePath) {
		String[] selectionArgs = new String[] { resourcePath };
		String selection = Nodes.NODE_RESOURCE_PATH + "=?";
		String[] projection = Nodes.getDefaultProjection();
		final Cursor c = sResolver.query(Nodes.CONTENT_URI, projection,
				selection, selectionArgs, null);
		if (c != null) {
			try {
				if (c.moveToFirst()) {
					String key;
					U1NodeKind kind;
					Boolean isLive = true;
					String path;
					String parentPath;
					String volumePath;
					Date whenCreated;
					Date whenChanged;
					Long generation;
					Long generationCreated;
					String contentPath;
					
					key = c.getString(c.getColumnIndex(Nodes.NODE_KEY));
					kind = U1NodeKind.valueOf(c.getString(
							c.getColumnIndex(Nodes.NODE_KIND)).toUpperCase(Locale.US));
					isLive = c.getInt(c.getColumnIndex(Nodes.NODE_IS_LIVE)) != 0;
					path = c.getString(c.getColumnIndex(Nodes.NODE_PATH));
					parentPath = c.getString(c.getColumnIndex(Nodes.NODE_PARENT_PATH));
					volumePath = c.getString(c.getColumnIndex(Nodes.NODE_VOLUME_PATH));
					whenCreated = new Date(c.getLong(c.getColumnIndex(Nodes.NODE_WHEN_CREATED)));
					whenChanged = new Date(c.getLong(c.getColumnIndex(Nodes.NODE_WHEN_CHANGED)));
					generation = c.getLong(c.getColumnIndex(Nodes.NODE_GENERATION));
					generationCreated = c.getLong(c.getColumnIndex(Nodes.NODE_GENERATION_CREATED));
					contentPath = c.getString(c.getColumnIndex(Nodes.NODE_CONTENT_PATH));
					
					return new U1Node(resourcePath, kind, isLive, path, parentPath,
							volumePath, key, whenCreated, whenChanged, generation,
							generationCreated, contentPath);
				} else {
					return null;
				}
			} finally {
				c.close();
			}
		}
		return null; 
	}
	
	public static Cursor getChildDirectoriesCursorByResourcePath(
			String resourcePath, String[] projection) {
		final String selection = Nodes.NODE_PARENT_PATH + "=?"
				+ " AND " + Nodes.NODE_KIND + "=?";
		final String[] selectionArgs =
				new String[] { resourcePath, U1NodeKind.DIRECTORY.toString() };
		final Cursor cursor = sResolver.query(Nodes.CONTENT_URI, projection,
				selection, selectionArgs, null);
		return cursor;
	}
	
	public static String getPublicUrl(final String resourcePath) {
		final String[] projection = new String[] { Nodes.NODE_PUBLIC_URL };
		final String selection = Nodes.NODE_RESOURCE_PATH + "=?";
		final String[] selectionArgs = new String[] { resourcePath };
		Cursor c = sResolver.query(Nodes.CONTENT_URI,
				projection, selection, selectionArgs, null);
		String url = null;
		try {
			if (c.moveToFirst()) {
				url = c.getString(c.getColumnIndex(Nodes.NODE_PUBLIC_URL));
			}
		} finally {
			c.close();
		}
		return TextUtils.isEmpty(url) ? null : url;
	}
	
	public static long getSize(final String resourcePath) {
		final String[] projection = new String[] { Nodes.NODE_SIZE };
		final String selection = Nodes.NODE_RESOURCE_PATH + "=?";
		final String[] selectionArgs = new String[] { resourcePath };
		Cursor c = sResolver.query(Nodes.CONTENT_URI,
				projection, selection, selectionArgs, null);
		long size = 0L;
		try {
			if (c.moveToFirst()) {
				size = c.getLong(c.getColumnIndex(Nodes.NODE_SIZE));
			}
		} finally {
			c.close();
		}
		return size;
	}
	
	public static int getCount(String resourcePath) {
		final String[] projection = new String[] { Nodes._ID };
		final String[] selectionArgs = new String[] { resourcePath };
		final Cursor c = sResolver.query(Nodes.CONTENT_URI,
				projection, sSelection, selectionArgs, null);
		int count = 0;
		try {
			count = c.getCount();
		} finally {
			c.close();
		}
		return count;
	}
	
	public static Set<Integer> getRootNodeIds() {
		Set<Integer> ids = new TreeSet<Integer>();
		final String[] projection = new String[] { Nodes._ID };
		final String selection = Nodes.NODE_PARENT_PATH + " IS NULL";
		final Cursor c = sResolver.query(Nodes.CONTENT_URI, projection,
				selection, null, null);
		try {
			if (c.moveToFirst()) {
				int id;
				do {
					id = c.getInt(c.getColumnIndex(Nodes._ID));
					ids.add(id);
				} while (c.moveToNext());
			}
		} finally {
			c.close();
		}
		return ids;
	}
	
	public static Set<String> getUserNodePaths() {
		Set<String> userNodePaths = new TreeSet<String>();
		final String[] projection =
				new String[] { Nodes._ID, Nodes.NODE_RESOURCE_PATH };
		final String selection = Nodes.NODE_PARENT_PATH + " IS NULL";
		final Cursor c = sResolver.query(Nodes.CONTENT_URI, projection,
				selection, null, null);
		try {
			if (c.moveToFirst()) {
				String resourcePath;
				do {
					resourcePath = c.getString(c.getColumnIndex(
							Nodes.NODE_RESOURCE_PATH));
					userNodePaths.add(resourcePath);
				} while (c.moveToNext());
			}
		} finally {
			c.close();
		}
		return userNodePaths;
	}
	
	public static Set<Integer> getChildrenIds(String resourcePath) {
		Set<Integer> ids = new TreeSet<Integer>();
		final String[] projection =
				new String[] { Nodes._ID, Nodes.NODE_RESOURCE_STATE };
		final String selection = Nodes.NODE_PARENT_PATH + "=?";
				//+ " AND " + Nodes.NODE_RESOURCE_STATE + " IS NULL"; // FIXME
		final String[] selectionArgs =
				new String[] { resourcePath };
		final Cursor c = sResolver.query(Nodes.CONTENT_URI, projection,
				selection, selectionArgs, null);
		try {
			if (c.moveToFirst()) {
				int id;
				do {
					id = c.getInt(
							c.getColumnIndex(Nodes._ID));
					// We check the state, above SQL is failing to filter out
					// nodes which are in non-null state. No idea why.
					String s = c.getString(
							c.getColumnIndex(Nodes.NODE_RESOURCE_STATE));
					if (s == null) {
						ids.add(id);
					} else {
						Log.d("MetaUtilities", "child state != null, ignoring");
					}
				} while (c.moveToNext());
			}
		} finally {
			c.close();
		}
		return ids;
	}
	
	public static String getHiddenSelection() {
		return Preferences.getShowHidden()
				? null : Nodes.NODE_NAME + " NOT LIKE '.%' AND "
						+ Nodes.NODE_NAME + " NOT LIKE '~/.%' ";
	}
	
	public static Cursor getVisibleVolumesCursor() {
		final Cursor c = sResolver.query(Volumes.CONTENT_URI,
				Volumes.getDefaultProjection(), null, null, null);
		c.setNotificationUri(sResolver, Volumes.CONTENT_URI);
		return c;
	}
	
	public static Cursor getVisibleTopNodesCursor() {
		// XXX android.database.CursorIndexOutOfBoundsException: Index -1 requested, with a size of 1
		final String showHidden = Preferences.getShowHidden() ? "" :
			" AND " + getHiddenSelection();
		final String[] projection = Nodes.getDefaultProjection();
		
		String selection = Nodes.NODE_RESOURCE_PATH + "=?" + showHidden;
		String[] selectionArgs = new String[] { Preferences.U1_RESOURCE };
		final Cursor ubuntuOne = sResolver.query(Nodes.CONTENT_URI, projection,
				selection, selectionArgs, null);
		
		selection = Nodes.NODE_RESOURCE_PATH + "=?" + showHidden;
		selectionArgs = new String[] { Preferences.U1_PURCHASED_MUSIC };
		final Cursor purchasedMusic = sResolver.query(Nodes.CONTENT_URI, projection,
				selection, selectionArgs, null);
		
		selection = Nodes.NODE_PARENT_PATH + " IS NULL " +
				"AND " + Nodes.NODE_RESOURCE_PATH + "!=? " +
				"AND " + Nodes.NODE_RESOURCE_PATH + "!=? " +
				showHidden;
		selectionArgs = new String[] {
				Preferences.U1_RESOURCE, Preferences.U1_PURCHASED_MUSIC
		};
		final Cursor cloudFolders = sResolver.query(Nodes.CONTENT_URI, projection,
				selection, selectionArgs, null);
		
		final MergeCursor cursor = new MergeCursor(new Cursor[] {
				ubuntuOne, purchasedMusic, cloudFolders
		});
		cursor.setNotificationUri(sResolver, Nodes.CONTENT_URI);
		
		return cursor;
	}
	
	public static Cursor getVisibleNodesCursorByParent(String parentPath) {
		final String showHidden = Preferences.getShowHidden() ? "" :
				" AND " + getHiddenSelection();
		
		final String[] projection = Nodes.getDefaultProjection();
		final String selection = Nodes.NODE_PARENT_PATH + "=? "
				+ showHidden;
		final String[] selectionArgs = new String[] { parentPath };
		
		final Cursor cursor = sResolver.query(Nodes.CONTENT_URI, projection,
				selection, selectionArgs, null);
		cursor.setNotificationUri(sResolver, Nodes.CONTENT_URI);
		
		return cursor;
	}

	/**
	 * Calculates directory content size, recursively if necessary.
	 * 
	 * @param resourcePath
	 *            the directory resource path to calculate size of
	 * @param recursive
	 *            the flag indicating recursive calculation
	 * @return the resorucePath defined directory size
	 */
	public static long getDirectorySize(final String resourcePath,
			final boolean recursive) {
		final String[] projection = new String[] { Nodes.NODE_RESOURCE_PATH,
				Nodes.NODE_KIND, Nodes.NODE_SIZE };
		final String selection = Nodes.NODE_PARENT_PATH + "=?";
		final String[] selectionArgs =
				new String[] { resourcePath };
		final Cursor c = sResolver.query(Nodes.CONTENT_URI, projection,
				selection, selectionArgs, null);
		U1NodeKind kind;
		long size = 0L;
		try {
			if (c.moveToFirst()) {
				do {
					kind = U1NodeKind.valueOf(c.getString(
							c.getColumnIndex(Nodes.NODE_KIND)).toUpperCase(Locale.US));
					if (U1NodeKind.FILE == kind) {
						size += c.getLong(c.getColumnIndex(Nodes.NODE_SIZE));
					} else if (U1NodeKind.DIRECTORY == kind && recursive) {
						final String subDirResourcePath = c.getString(c
								.getColumnIndex(Nodes.NODE_RESOURCE_PATH));
						size += getDirectorySize(subDirResourcePath, true);
					}
				} while (c.moveToNext());
			}
		} finally {
			c.close();
		}
		return size;
	}
	
	public static Cursor getFailedTransfers() {
		final String[] projection =	new String[] {
				Nodes.NODE_RESOURCE_PATH, Nodes.NODE_RESOURCE_STATE,
				Nodes.NODE_PARENT_PATH, Nodes.NODE_DATA };
		final String selection = Nodes.NODE_RESOURCE_STATE
				+ " LIKE '%" + ResourceState.FAILED + "'";
		final String sortOrder = Nodes.NODE_RESOURCE_STATE + " ASC";
		return sResolver.query(Nodes.CONTENT_URI, projection, selection,
				null, sortOrder);
	}
	
	public static int resetFailedTransfers() {
		int failed = 0;
		failed += resetFailedTransfers(HttpPost.METHOD_NAME);
		failed += resetFailedTransfers(HttpGet.METHOD_NAME);
		return failed;
	}
	
	public static int resetFailedTransfers(String method) {
		String ongoing;
		String failed;
		if (HttpPost.METHOD_NAME.equals(method)) {
			ongoing = ResourceState.STATE_POSTING;
			failed = ResourceState.STATE_POSTING_FAILED;
		} else if (HttpGet.METHOD_NAME.equals(method)) {
			ongoing = ResourceState.STATE_GETTING;
			failed = ResourceState.STATE_GETTING_FAILED;
		} else {
			Log.e(TAG, "Bad method name: " + method);
			return 0;
		}
		
		final ContentValues values = new ContentValues(1);
		values.put(Nodes.NODE_RESOURCE_STATE, failed);
		final String where = Nodes.NODE_RESOURCE_STATE + "=?";
		final String[] selectionArgs = new String[] { ongoing };
		return sResolver.update(Nodes.CONTENT_URI, values, where, selectionArgs);
	}
	
	public static void cancelFailedTransfers() {
		ContentValues values;
		final String where = Nodes.NODE_RESOURCE_STATE + "=?";
		String[] selectionArgs;
		String clearState = null;
		
		// Reset uploads.
		selectionArgs = new String[] { ResourceState.STATE_POSTING_FAILED };
		sResolver.delete(Nodes.CONTENT_URI, where, selectionArgs);
		
		// Reset downloads.
		selectionArgs = new String[] { ResourceState.STATE_GETTING_FAILED };
		values = new ContentValues(1);
		values.put(Nodes.NODE_RESOURCE_STATE, clearState);
		sResolver.update(Nodes.CONTENT_URI, values, where, selectionArgs);
	}
	
	public static void updateLongField(String resourcePath, String column, Long value) {
		final String[] selectionArgs = new String[] { resourcePath };
		final ContentValues values = new ContentValues();
		values.put(column, value);
		sResolver.update(Nodes.CONTENT_URI, values, sSelection, selectionArgs);
	}
	
	public static void updateStringField(String resourcePath, String column, String value) {
		final String[] selectionArgs = new String[] { resourcePath };
		final ContentValues values = new ContentValues();
		values.put(column, value);
		sResolver.update(Nodes.CONTENT_URI, values, sSelection, selectionArgs);
	}
	
	public static void setState(final String resourcePath, final String state) {
		final ContentValues values = new ContentValues();
		values.put(Nodes.NODE_RESOURCE_STATE, state);
		final String where = Nodes.NODE_RESOURCE_PATH + "=?";
		final String[] selectionArgs = new String[] { resourcePath };
		sResolver.update(Nodes.CONTENT_URI, values, where, selectionArgs);
	}
	
	public static void setStateAndData(final String resourcePath,
			final String state, final String data) {
		final String[] selectionArgs = new String[] { resourcePath };
		final ContentValues values = new ContentValues();
		values.put(Nodes.NODE_RESOURCE_STATE, state);
		values.put(Nodes.NODE_DATA, data);
		sResolver.update(Nodes.CONTENT_URI, values, sSelection, selectionArgs);
	}
	
	public static void setIsCached(String resourcePath, boolean isCached) {
		ContentValues values = new ContentValues(2);
		values.put(Nodes.NODE_RESOURCE_PATH, resourcePath);
		values.put(Nodes.NODE_IS_CACHED, isCached);
		String where = Nodes.NODE_RESOURCE_PATH + "=?";
		String[] selectionArgs = new String[] { resourcePath };
		sResolver.update(Nodes.CONTENT_URI, values, where, selectionArgs);
		sResolver.notifyChange(Nodes.CONTENT_URI, null);
	}
	
	public static boolean isCached(String resourcePath) {
		String[] projection = new String[] { Nodes.NODE_IS_CACHED };
		String selection = Nodes.NODE_RESOURCE_PATH + "=?";
		String[] selectionArgs = new String[] { resourcePath } ;
		Cursor c = sResolver.query(Nodes.CONTENT_URI,
				projection, selection, selectionArgs, null);
		if (c != null) {
			try {
				if (c.moveToFirst()) {
					return c.getInt(c.getColumnIndex(Nodes.NODE_IS_CACHED)) != 0;
				}
			} finally {
				c.close();
			}
		}
		return false;
	}
	
	public static void delete(String resourcePath) {
		Log.d(TAG, "Delete " + resourcePath);
		String kindString = MetaUtilities.getStringField(
				resourcePath, Nodes.NODE_KIND);
		if (kindString == null) { // Consistency check.
			Log.e(TAG, "Node has null kind!");
			return;
		}
		U1NodeKind kind = U1NodeKind.valueOf(kindString.toUpperCase(Locale.US));
		
		if (kind == U1NodeKind.FILE) {
			deleteFile(resourcePath);
		} else {
			deleteDirectory(resourcePath);
		}
	}
	
	public static void deleteFile(String fileResPath) {
		deleteFileContent(fileResPath);
		deleteNodeMetadata(fileResPath);
	}
	
	private static void deleteFileContent(String fileResPath) {
		String path = FileUtilities.getFilePathFromResourcePath(fileResPath);
		FileUtilities.safeDeleteSilently(path);
	}
	
	public static void deleteDirectory(String dirResPath) {
		deleteDirectoryContent(dirResPath);
		deleteNodeMetadata(dirResPath);
	}
	
	private static void deleteDirectoryContent(String dirResPath) {
		String resPathFmt = dirResPath + "/%";
		String[] projection =
				new String[] { Nodes.NODE_RESOURCE_PATH, Nodes.NODE_DATA };
		String selection = Nodes.NODE_RESOURCE_PATH + " LIKE ?";
		String[] selectionArgs = new String[] { resPathFmt };
		
		Cursor c = null;
		try {
			c = sResolver.query(Nodes.CONTENT_URI, projection,
					selection, selectionArgs, null);
			if (c != null && c.moveToFirst()) {
				do {
					String resPath = c.getString(c.getColumnIndex(
							Nodes.NODE_RESOURCE_PATH));
					delete(resPath);
				} while (c.moveToNext());
			}
		} finally {
			if (c != null) c.close();
		}
		
		String path = FileUtilities.getFilePathFromResourcePath(dirResPath);
		FileUtilities.safeDeleteSilently(path);
	}
	
	public static void deleteNodeMetadata(String resourcePath) {
		String where = Nodes.NODE_RESOURCE_PATH + "=?";
		String[] selectionArgs = new String[] { resourcePath };
		sResolver.delete(Nodes.CONTENT_URI, where, selectionArgs);
	}
	
	public static Uri buildNodeUri(int id) {
		return Nodes.CONTENT_URI.buildUpon()
				.appendPath(String.valueOf(id)).build();
	}
	
	public static boolean isValidUri(Uri uri) {
		try {
			final InputStream in = sResolver.openInputStream(uri);
			in.close();
		} catch (FileNotFoundException e) {
			return false;
		} catch (IOException e) {
			return false;
		}
		return true;
	}
	
	public static boolean isValidUriTarget(String data) {
		if (data == null) {
			Log.d(TAG, "uri is null");
			return false;
		} else if (data.startsWith(ContentResolver.SCHEME_CONTENT)) {
			Log.d(TAG, "checking content uri");
			return isValidUri(Uri.parse(data));
		} else if (data.startsWith(ContentResolver.SCHEME_FILE)) {
			try {
				Log.d(TAG, "checking file uri");
				return new File(URI.create(data)).exists();
			} catch (IllegalArgumentException e) {
				// We have received wrong uri. Failed upload shall be cleaned up.
				return false;
			}
		} else if (new File(data).exists()) {
			return true;
		} else {
			Log.e(TAG, "unknown uri: " + data);
		}
		return false;
	}
	
	public static boolean isDirectory(long id) {
		String[] projection = new String[] { Nodes.NODE_KIND };
		String selection = Nodes._ID + "=?";
		String[] selectionArgs = new String[] { String.valueOf(id) };
		final Cursor c = sResolver.query(Nodes.CONTENT_URI, projection,
				selection, selectionArgs, null);
		String type = null;
		try {
			if (c.moveToFirst()) {
				type = c.getString(c.getColumnIndex(Nodes.NODE_KIND));
			}
		} finally {
			c.close();
		}
		return (type != null) ? U1NodeKind.DIRECTORY ==
				U1NodeKind.valueOf(type.toUpperCase(Locale.US)) : false;
	}
	
	public static boolean isDirectory(Cursor c) {
		final String type = c.getString(c.getColumnIndex(Nodes.NODE_KIND));
		return U1NodeKind.DIRECTORY == U1NodeKind.valueOf(type.toUpperCase(Locale.US));
	}

	/**
	 * For a given file {@link Uri} string, we check if its hash has a
	 * corresponding entry in the {@link MetaProvider}, telling thus whether the
	 * file from under given {@link Uri} string has been already uploaded.
	 * 
	 * @param uriString
	 *            the uri string which content we are checking
	 * @return resourcePath if content under uri has been already uploaded,
	 *         null otherwise
	 */
	public static String isUploaded(String uriString) {
		File file = null;
		String fileHash = null;
		
		if (uriString.startsWith(ContentResolver.SCHEME_CONTENT)) {
			final String[] projection = new String[] { MediaColumns.DATA };
			final Cursor c = sResolver.query(Uri.parse(uriString),
					projection, null, null, null);
			try {
				if (c.moveToFirst()) {
					String data = c.getString(c.getColumnIndex(MediaColumns.DATA));
					file = new File(data);
				} else {
					return null;
				}
			} finally {
				c.close();
			}
		} else if (uriString.startsWith(ContentResolver.SCHEME_FILE)) {
			final URI fileURI = URI.create(Uri.encode(uriString, ":/"));
			file = new File(fileURI);
		} else {
			Log.e(TAG, "Tried to check malformed uri string: " + uriString);
			return null;
		}
		
		try {
			if (file != null && file.exists()) {
				fileHash = HashUtils.getSha1(file);
				Log.d(TAG, String.format("Computed hash: '%s'", fileHash));
			} else {
				throw new FileNotFoundException("isUploaded()");
			}
		} catch (Exception e) {
			Log.e(TAG, "Can't compute file hash!", e);
			return null;
		}
		
		final String[] projection =
				new String[] { Nodes.NODE_RESOURCE_PATH };
		final String selection = Nodes.NODE_HASH + "=?";
		final String[] selectionArgs = new String[] { fileHash };
		final Cursor c = sResolver.query(Nodes.CONTENT_URI, projection,
				selection, selectionArgs, null);
		String resourcePath = null;
		try {
			if (c.moveToFirst()) {
				resourcePath = c.getString(c.getColumnIndex(
						Nodes.NODE_RESOURCE_PATH));
				Log.d(TAG, "Corresponding file hash found: " + resourcePath);
			} else {
				Log.d(TAG, "Corresponding file hash not found.");
			}
		} finally {
			c.close();
		}
		return resourcePath;
	}
	
	public static void updateNode(ContentResolver contentResolver,
			U1Node node, String data) {
		final String resourcePath = node.getResourcePath();
		final boolean isDirectory = node.getKind() == U1NodeKind.DIRECTORY;
		
		if (!isDirectory && ((U1File) node).getSize() == null) {
			// Ignore files with null size.
			return;
		}
		
		ContentValues values = Nodes.valuesFromRepr(node, data);
		String selection = Nodes.NODE_KEY + "=?";
		String[] selectionArgs = new String[] { node.getKey() };
		if (node.getIsLive()) {
			int updated = contentResolver.update(Nodes.CONTENT_URI,
					values, selection, selectionArgs);
			if (updated == 0) {
				boolean isNonEmptyFile =
						!isDirectory && ((U1File) node).getSize() != null;
				if (isDirectory || isNonEmptyFile) {
					contentResolver.insert(Nodes.CONTENT_URI, values);
				}
			}
		} else {
			if (node.getKind() == U1NodeKind.FILE) {
				FileUtilities.safeDeleteSilently(FileUtilities
						.getFilePathFromResourcePath(resourcePath));
			}
			MetaUtilities.deleteNodeMetadata(resourcePath);
		}
		MetaUtilities.notifyChange(Nodes.CONTENT_URI);
	}
	
	public static long getVolumeGeneration(String resourcePath) {
		String[] projection = new String[] { Volumes.VOLUME_GENERATION };
		String selection = Volumes.VOLUME_RESOURCE_PATH + "=?";
		String[] selectionArgs = new String[] { resourcePath };
		Cursor c = sResolver.query(Volumes.CONTENT_URI,
				projection, selection, selectionArgs, null);
		if (c != null) {
			try {
				if (c.moveToFirst()) {
					return c.getLong(c.getColumnIndex(
							Volumes.VOLUME_GENERATION));
				}
			} finally {
				c.close();
			}
		}
		return 0;
	}
	
	public static ArrayList<U1Node> getPhotoNodesFromDirectory(String directoryResourcePath) {
		String[] projection = Nodes.getDefaultProjection();
		String selection = Nodes.NODE_PARENT_PATH + "=? AND " +
					Nodes.NODE_MIME + "=?";
		String[] selectionArgs = new String[] { directoryResourcePath, "image/jpeg" };
		
		Cursor c = sResolver.query(Nodes.CONTENT_URI, projection, selection,
				selectionArgs, null);
		
		ArrayList<U1Node> photoNodes = null;
		if (c != null) {
			photoNodes = new ArrayList<U1Node>();
			try {
				if (c.moveToFirst()) {
					do {
						String resourcePath;
						String key;
						U1NodeKind kind;
						Boolean isLive = true;
						String path;
						String parentPath;
						String volumePath;
						Date whenCreated;
						Date whenChanged;
						Long generation;
						Long generationCreated;
						String contentPath;
						
						resourcePath = c.getString(c.getColumnIndex(Nodes.NODE_RESOURCE_PATH));
						key = c.getString(c.getColumnIndex(Nodes.NODE_KEY));
						kind = U1NodeKind.valueOf(c.getString(
								c.getColumnIndex(Nodes.NODE_KIND)).toUpperCase(Locale.US));
						isLive = c.getInt(c.getColumnIndex(Nodes.NODE_IS_LIVE)) != 0;
						path = c.getString(c.getColumnIndex(Nodes.NODE_PATH));
						parentPath = c.getString(c.getColumnIndex(Nodes.NODE_PARENT_PATH));
						volumePath = c.getString(c.getColumnIndex(Nodes.NODE_VOLUME_PATH));
						whenCreated = new Date(c.getLong(c.getColumnIndex(Nodes.NODE_WHEN_CREATED)));
						whenChanged = new Date(c.getLong(c.getColumnIndex(Nodes.NODE_WHEN_CHANGED)));
						generation = c.getLong(c.getColumnIndex(Nodes.NODE_GENERATION));
						generationCreated = c.getLong(c.getColumnIndex(Nodes.NODE_GENERATION_CREATED));
						contentPath = c.getString(c.getColumnIndex(Nodes.NODE_CONTENT_PATH));
						
						U1Node node = new U1Node(resourcePath, kind, isLive, path, parentPath,
								volumePath, key, whenCreated, whenChanged, generation,
								generationCreated, contentPath);
						photoNodes.add(node);
					} while (c.moveToNext());
				}
			} finally {
				c.close();
			}
		}
		return photoNodes;
	}
}
