package com.ubuntuone.android.files.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;

import com.ubuntuone.android.files.provider.WatchedFoldersContract.WatchedFolders;

public class WatchedFolderUtils
{	
	public static void resetUploadTimestamps(ContentResolver resolver, Uri uri) {
		ContentValues values = new ContentValues();
		values.put(WatchedFolders.LAST_UPLOADED, 0);
		resolver.update(uri, values, null, null);
	}
	
	public static void updateUploadTimestamp(ContentResolver resolver,
			String folderPath) {
		ContentValues v = new ContentValues();
		v.put(WatchedFolders.LAST_UPLOADED, TimeUtil.getTimeInSeconds());
		String where = WatchedFolders.FOLDER_PATH + "=?";
		String[] selectionArgs = new String[] { folderPath };
		resolver.update(WatchedFolders.Images.CONTENT_URI,
				v, where, selectionArgs);
	}
}
