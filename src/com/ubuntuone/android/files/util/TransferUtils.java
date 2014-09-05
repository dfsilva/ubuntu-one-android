package com.ubuntuone.android.files.util;

import java.io.File;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import com.ubuntuone.android.files.provider.TransfersContract.Downloads;
import com.ubuntuone.android.files.provider.TransfersContract.TransferPriority;
import com.ubuntuone.android.files.provider.TransfersContract.TransferState;
import com.ubuntuone.android.files.provider.TransfersContract.Uploads;

public class TransferUtils
{
	public static Uri queueUpload(ContentResolver resolver, int priority,
			File file, String resourcePath) {
		ContentValues values = Uploads.values(TransferState.QUEUED,
				priority,
				file.getName(),
				file.getAbsolutePath(),
				FileUtilities.getMime(file.getName()),
				file.length(),
				resourcePath);
		return resolver.insert(Uploads.CONTENT_URI, values);
	}
	
	public static boolean isUploadPending(ContentResolver resolver,
			String resourcePath) {
		String[] projection = new String[] { "count(*) AS " + Uploads._COUNT };
		String selection = Uploads.RESOURCE_PATH + "=?";
		String[] selectionArgs = new String[] { resourcePath };
		int count = getIntValue(resolver, Uploads.CONTENT_URI,
				projection, selection, selectionArgs);
		return count > 0;
	}
	
	public static void queueDownload(ContentResolver resolver, int priority,
			String resourcePath, long size, String checksum, File file) {
		if (isDownloadPending(resolver, resourcePath)) {
			// Update download state to QUEUED.
			ContentValues values = new ContentValues();
			values.put(Downloads.STATE, TransferState.QUEUED);
			String where = Downloads.RESOURCE_PATH + "=?";
			String[] selectionArgs = new String[] { resourcePath };
			resolver.update(Downloads.CONTENT_URI, values, where, selectionArgs);
		} else {
			// Queue the download.
			ContentValues values = Downloads.values(TransferState.QUEUED,
					priority,
					file.getName(),
					file.getAbsolutePath(),
					size,
					checksum,
					resourcePath);
			resolver.insert(Downloads.CONTENT_URI, values);
		}
	}
	
	public static boolean isDownloadPending(ContentResolver resolver,
			String resourcePath) {
		String[] projection = new String[] { "count(*) AS " + Downloads._COUNT };
		String selection = Uploads.RESOURCE_PATH + "=?";
		String[] selectionArgs = new String[] { resourcePath };
		int count = getIntValue(resolver, Downloads.CONTENT_URI,
				projection, selection, selectionArgs);
		return count > 0;
	}
	
	public static Uri getDownloadUriByResourcePath(ContentResolver resolver,
			String resourcePath) {
		String[] projection = new String[] {
				Downloads._ID, Downloads.RESOURCE_PATH
		};
		String selection = Downloads.RESOURCE_PATH + "=?";
		String[] selectionArgs = new String[] { resourcePath };
		int id = getIntValue(resolver, Downloads.CONTENT_URI,
				projection, selection, selectionArgs);
		if (id != -1) {
			return Downloads.buildDownloadUri(id);
		} else {
			return null;
		}
	}
	
	public static void clearFailedUploadsState(ContentResolver resolver) {
		ContentValues values = new ContentValues();
		values.put(Uploads.STATE, TransferState.QUEUED);
		resolver.update(Uploads.CONTENT_URI, values, null, null);
	}
	
	public static void dequeue(ContentResolver resolver, Uri uri) {
		resolver.delete(uri, null, null);
	}
	
	public static void dequeueByResourcePath(ContentResolver resolver, Uri uri,
			String resourcePath) {
		String where = Downloads.RESOURCE_PATH + "=?";
		String[] selectionArgs = new String[] { resourcePath };
		resolver.delete(uri, where, selectionArgs);
	}
	
	public static void dequeue(ContentResolver resolver, Uri uri, String state) {
		String selection = Uploads.STATE + "=?";
		String[] selectionArgs = new String[] { state }; 
		resolver.delete(uri, selection, selectionArgs);
	}
	
	public static void cancelFailedUploads(ContentResolver resolver) {
		String where = Uploads.STATE + "=?";
		String[] selectionArgs = new String[] { TransferState.FAILED };
		resolver.delete(Uploads.CONTENT_URI, where, selectionArgs);
	}

	public static int getNonFailedUploadsCount(ContentResolver resolver) {
		String[] projection = new String[] { "count(*) AS " + Uploads._COUNT };
		String selection = Uploads.STATE + "!=?";
		String[] selectionArgs = new String[] { TransferState.FAILED };
		return getIntValue(resolver, Uploads.CONTENT_URI,
				projection, selection, selectionArgs);
	}
	
	public static int getQueuedUploadsCount(ContentResolver resolver, int priority) {
		String[] projection = new String[] { "count(*) AS " + Uploads._COUNT };
		String selection = Uploads.STATE + "=? AND " +
				Uploads.PRIORITY + "=?";
		String[] selectionArgs = new String[] {
			TransferState.QUEUED, String.valueOf(priority)
		};
		return getIntValue(resolver, Uploads.CONTENT_URI,
				projection, selection, selectionArgs);
	}
	
	public static int getNonFailedDownloadsCount(ContentResolver resolver) {
		String[] projection = new String[] { "count(*) AS " + Downloads._COUNT };
		String selection = Downloads.STATE + "!=?";
		String[] selectionArgs = new String[] { TransferState.FAILED };
		return getIntValue(resolver, Downloads.CONTENT_URI,
				projection, selection, selectionArgs);
	}
	
	public static int getFailedUploadsCount(ContentResolver resolver) {
		String[] projection = new String[] { "count(*) AS " + Uploads._COUNT };
		String selection = Uploads.STATE + "=?";
		String[] selectionArgs = new String[] { TransferState.FAILED };
		return getIntValue(resolver, Uploads.CONTENT_URI,
				projection, selection, selectionArgs);
	}
	
	private static int getIntValue(ContentResolver resolver, Uri uri,
			String[] projection, String selection, String[] selectionArgs) {
		final Cursor c = resolver.query(uri, projection,
				selection, selectionArgs, null);
		try {
			if (c != null && c.moveToFirst()) {
				return c.getInt(0);
			}
		} finally {
			if (c != null) c.close();
		}
		return -1;
	}

	public static void updateAutoUploadsState(ContentResolver resolver,
			String fromState, String toState) {
		ContentValues values = new ContentValues();
		values.put(Uploads.STATE, toState);
		String where = Uploads.STATE + "=? AND " +
					Uploads.PRIORITY + "=?";
		String[] selectionArgs = new String[] {
				fromState, String.valueOf(TransferPriority.AUTO)
		};
		resolver.update(Uploads.CONTENT_URI, values, where, selectionArgs);
	}
	
	public static void clearAutoUploads(ContentResolver resolver) {
		String selection = Uploads.PRIORITY + "=?";
		String[] selectionArgs = new String[] {
				String.valueOf(TransferPriority.AUTO)
		};
		resolver.delete(Uploads.CONTENT_URI, selection, selectionArgs);
	}
	
	public static void setUploadsState(ContentResolver resolver,
			String state) {
		ContentValues values = new ContentValues();
		values.put(Uploads.STATE, state);
		resolver.update(Uploads.CONTENT_URI, values, null, null);
	}
}
