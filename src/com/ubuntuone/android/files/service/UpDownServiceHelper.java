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

package com.ubuntuone.android.files.service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore.MediaColumns;

import com.ubuntuone.android.files.provider.MetaContract.Nodes;
import com.ubuntuone.android.files.provider.MetaContract.ResourceState;
import com.ubuntuone.android.files.provider.MetaUtilities;
import com.ubuntuone.android.files.provider.TransfersContract.TransferPriority;
import com.ubuntuone.android.files.provider.TransfersContract.TransferState;
import com.ubuntuone.android.files.provider.TransfersContract.Uploads;
import com.ubuntuone.android.files.util.FileUtilities;
import com.ubuntuone.android.files.util.Log;
import com.ubuntuone.android.files.util.TransferUtils;
import com.ubuntuone.api.files.model.U1NodeKind;

public final class UpDownServiceHelper
{
	private static final String TAG = UpDownServiceHelper.class.getSimpleName();
	
	private UpDownServiceHelper() {
	}
	
	public static void download(Context context, String resourcePath) {
		download(context, resourcePath, false);
	}
	
	public static void download(Context context, String resourcePath,
			boolean isRecursing) {
		ContentResolver resolver = context.getContentResolver();
		
		if (TransferUtils.isDownloadPending(resolver, resourcePath)) {
			Log.i(TAG, "Download already queued.");
			return;
		}
		
		// File being downloaded must be already cached in MetaProvider.
		final String[] projection = new String[] { Nodes.NODE_RESOURCE_PATH,
				Nodes.NODE_KIND, Nodes.NODE_SIZE, Nodes.NODE_HASH
		};
		String kind = null;
		String checksum = null;
		long size = 0;
		File file = null;
		
		Cursor c = MetaUtilities.getNodeCursorByResourcePath(
				resourcePath, projection);
		try {
			if (c != null && c.moveToFirst()) {
				kind = c.getString(c.getColumnIndex(Nodes.NODE_KIND))
						.toUpperCase(Locale.US);
				checksum = c.getString(c.getColumnIndex(Nodes.NODE_HASH));
				size = c.getLong(c.getColumnIndex(Nodes.NODE_SIZE));
			}
		} finally {
			if (c != null) c.close();
		}
		
		if (kind == null) {
			Log.e(TAG, "Unknown file kind in MetaProvider!");
			return;
		}
		kind = kind.toUpperCase(Locale.US);
		
		if (U1NodeKind.FILE == U1NodeKind.valueOf(kind)) {
			String data = FileUtilities.getFileSchemeFromResourcePath(resourcePath);
			file = new File(URI.create(data));
			file.getParentFile().mkdirs();
			
			TransferUtils.queueDownload(resolver, TransferPriority.USER,
					resourcePath, size, checksum, file);
			MetaUtilities.setState(resourcePath, ResourceState.STATE_GETTING);
			MetaUtilities.notifyChange(Nodes.CONTENT_URI);
			if (!isRecursing) {
				context.startService(new Intent(UpDownService.ACTION_DOWNLOAD));
			}
		} else if (U1NodeKind.DIRECTORY == U1NodeKind.valueOf(kind)) {
			final String selection = Nodes.NODE_PARENT_PATH + "=? AND "
					+ Nodes.NODE_KIND + "=?";
			final String[] selectionArgs =
					new String[] { resourcePath, U1NodeKind.FILE.toString() };
			
			c = resolver.query(Nodes.CONTENT_URI, projection,
							selection, selectionArgs, null);
			try {
				if (c != null && c.isBeforeFirst()) {
					while (c.moveToNext()) {
						final String chResourcePath = c.getString(
								c.getColumnIndex(Nodes.NODE_RESOURCE_PATH));
						download(context, chResourcePath, true);
					}
				}
			} finally {
				if(c != null ) c.close();
			}
			context.startService(new Intent(UpDownService.ACTION_DOWNLOAD));
		}
	}
	
	public static void upload(Context context, Uri uri,
			String parentResourcePath, boolean autoTransfer) {
		ContentResolver resolver = context.getContentResolver();
		
		Log.d(TAG, String.format("upload() %s to %s",
				uri.toString(), parentResourcePath));

		FileInfo fileInfo = getFileInfo(context, uri);
		if (fileInfo.path != null) {
			Log.i(TAG, String.format("name=%s, path=%s, size=%d",
					fileInfo.name, fileInfo.path, fileInfo.size));
		} else {
			Log.d(TAG, "Could not get file path for Uri: " + uri);
			return;
		}
		
		int priority = autoTransfer ? TransferPriority.AUTO : TransferPriority.USER;
		String resourcePath =
				String.format("%s/%s", parentResourcePath, fileInfo.name);
		if (TransferUtils.isUploadPending(resolver, resourcePath)) {
			Log.i(TAG, "Upload already queued.");
			return;
		}
		
		// TODO We assume here we either got the path from MediaColumns.DATA or
		// know the path from a File('file://...').

		String data = fileInfo.path;
		if (!MetaUtilities.isValidUriTarget(data)) {
			Log.w(TAG, "Unsupported upload request: " + data);
			return;
		}

		int cachedCount = MetaUtilities.getCount(resourcePath);
		long cachedSize = MetaUtilities.getSize(resourcePath);
		if (fileInfo.size != cachedSize) {
			// Uploading file with same name and different size. Rename?
			if (autoTransfer && cachedCount > 0) {
				// Inject counter to find a new, unused resource path,
				// i.e. IMG001-3.jpg, IMG001-4.jpg
				String filename = null;
				for (int i = 1; cachedCount > 0; i++) {
					filename = FileUtilities.injectCounterIntoFilename(
							fileInfo.name, i);
					resourcePath =
							String.format("%s/%s", parentResourcePath, filename);
					cachedCount = MetaUtilities.getCount(resourcePath);
				}
				Log.d(TAG, "Auto Upload renamed file to: " + filename);
			}
		}
		
		final ContentValues values = Uploads.values(TransferState.QUEUED,
				priority, fileInfo.name, data,
				fileInfo.mime, fileInfo.size, resourcePath);
		context.getContentResolver().insert(Uploads.CONTENT_URI, values);
		context.startService(new Intent(UpDownService.ACTION_UPLOAD));
	}
	
	private static class FileInfo {
		public String name;
		public String path;
		public String mime;
		public Long size;
	}
	
	private static FileInfo getFileInfo(Context context, Uri uri) {
		final FileInfo result = new FileInfo();
		final String scheme = uri.getScheme();
		
		if (ContentResolver.SCHEME_FILE.equals(scheme)) {
			try {
				final File file = new File(URI.create(uri.toString()));
				result.name = file.getName();
				result.path = file.getCanonicalPath();
				result.mime = FileUtilities.getMime(result.name);
				result.size = file.length();
			} catch (IOException e) {
				Log.e(TAG, "Could not get file path for Uri: " + uri);
			}
		} else if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
			final String[] projection = new String[] {
					MediaColumns.DISPLAY_NAME,
					MediaColumns.DATA,
					MediaColumns.MIME_TYPE,
					MediaColumns.SIZE
			};
			final Cursor c = context.getContentResolver()
					.query(uri, projection, null, null, null);
			try {
				if (c != null && c.moveToFirst()) {
					if (c.getColumnIndex(MediaColumns.DATA) != -1) {
						result.name = c.getString(
								c.getColumnIndex(MediaColumns.DISPLAY_NAME));
						result.path = c.getString(
								c.getColumnIndex(MediaColumns.DATA));
						result.mime = c.getString(
								c.getColumnIndex(MediaColumns.MIME_TYPE));
						result.size = c.getLong(
								c.getColumnIndex(MediaColumns.SIZE));
					}
				}
			} finally {
				if(c != null ) c.close();
			}
		} else {
			Log.e(TAG, "Unknown Uri scheme: " + uri.toString());
		}
		return result;
	}
}
