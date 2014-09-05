/*
 * Ubuntu One Files - access Ubuntu One cloud storage on Android platform.
 * 
 * Copyright (C) 2011 Canonical Ltd.
 * Author: Michał Karnicki <michal.karnicki@canonical.com>
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

package com.ubuntuone.android.files.activity;

import java.io.File;
import java.io.IOException;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceClickListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.ubuntuone.android.files.Preferences;
import com.ubuntuone.android.files.R;
import com.ubuntuone.android.files.provider.MetaContract.Nodes;
import com.ubuntuone.android.files.util.FileUtilities;
import com.ubuntuone.android.files.util.Log;
import com.ubuntuone.android.files.util.UIUtil;
import com.ubuntuone.android.files.Analytics;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;

public class StorageActivity extends PreferenceActivity {
	
	private static final String TAG = StorageActivity.class.getSimpleName();
	
	private static interface State {
		// Used as keys in state Bundle.
		public final String REFRESH_STATS_DIALOG_VISIBLE = "rsd_visible";
		public final String CHECK_MISSING_DIALOG_VISIBLE = "cmd_visible";
		public final String CHECK_MISSING_DIALOG_PROGRESS = "cmd_progress";
		public final String REMOVE_CACHED_DIALOG_VISIBLE = "rcd_visible";
		public final String REMOVE_CACHED_DIALOG_PROGRESS = "rcd_progress";
		public final String LIMIT_STORAGE_DIALOG_VISIBLE = "lsd_visible";
		public final String LIMIT_STORAGE_DIALOG_VALUE = "lsd_value";
	}
	
	private static interface Storage {
		public final String STORAGE_AVAILABLE	= "storage_available";
		public final String STORAGE_USED		= "storage_used";
	}
	
	private static interface Stats {
		public final String FILES_IN_CLOUD		= "files_in_cloud";
		public final String SYNCED_FILES_SIZE	= "synced_files";
		public final String CACHED_FILES_SIZE	= "cached_files";
	}
	
	private static interface Manage {
		public final String REFRESH_STATS 		= "refresh_stats";
		public final String CHECK_MISSING		= "check_missing";
		public final String REMOVE_CACHED		= "remove_cached";
		public final String LIMIT_STORAGE		= "limit_storage";
		public final String UNSELECT_SYNCED		= "unselect_synced";
	}
	
	private final int DIALOG_REFRESH_STATS_ID = 1;
	
	private final int DIALOG_CHECK_MISSING_ID = 2;
	
	private final int DIALOG_REMOVE_CACHED_ID = 3;
	
	private final int DIALOG_LIMIT_STORAGE_ID = 4;
	
	
	private GoogleAnalyticsTracker mTracker;
	
	private Preference mRefreshStats;
	
	private Preference mStorageAvailable;
	
	private Preference mStorageUsed;
	
	
	private Preference mFilesInCloud;
	
	private Preference mSyncedFilesSize;
	
	private Preference mCachedFilesSize;
	
	private ProgressDialog mRefreshStatsDialog;
	
	private Preference mCheckMissing;
	
	private int mCheckMissingProgress;
	
	private ProgressDialog mCheckMissingDialog;
	
	private Preference mRemoveCached;
	
	private ProgressDialog mRemoveCachedDialog;
	
	private int mRemoveCachedProgress;
	
	private Preference mLimitStorage;
	
	private long mLimitStorageValue;
	
	private LimitStorageDialog mLimitStorageDialog;
	
	private Preference mUnselectSynced;
	
	
	private Resources res;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mTracker = GoogleAnalyticsTracker.getInstance();
		mTracker.start(Analytics.U1F_ACCOUNT, this);
		mTracker.trackPageView(TAG);

		addPreferencesFromResource(R.xml.storage);
		res = getResources();
		
		mStorageAvailable = findPreference(Storage.STORAGE_AVAILABLE);
		mStorageUsed = findPreference(Storage.STORAGE_USED);
		
		mFilesInCloud = findPreference(Stats.FILES_IN_CLOUD);
		mSyncedFilesSize = findPreference(Stats.SYNCED_FILES_SIZE);
		mCachedFilesSize = findPreference(Stats.CACHED_FILES_SIZE);
		
		mRefreshStats = findPreference(Manage.REFRESH_STATS);
		mRefreshStats.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				new RefreshStatisticsTask().execute();
				return true;
			}
		});
		
		mCheckMissing = findPreference(Manage.CHECK_MISSING);
		mCheckMissingProgress = 0;
		mCheckMissing.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				new CheckMissingFilesTask().execute();
				return true;
			}
		});
		
		mRemoveCached = findPreference(Manage.REMOVE_CACHED);
		mRemoveCachedProgress = 0;
		mRemoveCached.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				new RemoveCachedFilesTask().execute();
				return true;
			}
		});
		
		mLimitStorage = findPreference(Manage.LIMIT_STORAGE);
		mLimitStorageValue = Preferences.getLocalStorageLimit();
		mLimitStorage.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				showDialog(DIALOG_LIMIT_STORAGE_ID);
				return true;
			}
		});
		
		mUnselectSynced = findPreference(Manage.UNSELECT_SYNCED);
		mUnselectSynced.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				// TODO karni: Auto-generated method stub
				return true;
			}
		});
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		if (mRefreshStatsDialog != null & mRefreshStatsDialog.isShowing())
			outState.putBoolean(State.REFRESH_STATS_DIALOG_VISIBLE, true);
		
		if (mCheckMissingDialog != null && mCheckMissingDialog.isShowing()) {
			outState.putBoolean(State.CHECK_MISSING_DIALOG_VISIBLE, true);
			outState.putInt(State.CHECK_MISSING_DIALOG_PROGRESS,
					mCheckMissingProgress);
		}
		
		if (mRemoveCachedDialog != null && mRemoveCachedDialog.isShowing()) {
			outState.putBoolean(State.REMOVE_CACHED_DIALOG_VISIBLE, true);
			outState.putInt(State.REMOVE_CACHED_DIALOG_PROGRESS,
					mRemoveCachedDialog.getProgress());
		}
		
		if (mLimitStorageDialog != null && mLimitStorageDialog.isShowing()) {
			outState.putBoolean(State.LIMIT_STORAGE_DIALOG_VISIBLE, true);
			outState.putLong(State.LIMIT_STORAGE_DIALOG_VALUE,
					mLimitStorageDialog.getValue());
		}
		
		super.onSaveInstanceState(outState);
	}
	
	@Override
	protected void onRestoreInstanceState(Bundle state) {
		super.onRestoreInstanceState(state);
		if (state.containsKey(State.REFRESH_STATS_DIALOG_VISIBLE))
			showDialog(DIALOG_REFRESH_STATS_ID);
		
		if (state.containsKey(State.CHECK_MISSING_DIALOG_VISIBLE)) {
			mCheckMissingProgress = state
					.getInt(State.CHECK_MISSING_DIALOG_PROGRESS);
			showDialog(DIALOG_CHECK_MISSING_ID);
		}
		
		if (state.containsKey(State.REMOVE_CACHED_DIALOG_VISIBLE)) {
			mRemoveCachedProgress = state
					.getInt(State.REMOVE_CACHED_DIALOG_PROGRESS);
			showDialog(DIALOG_REMOVE_CACHED_ID);
		}
		
		if (state.containsKey(State.LIMIT_STORAGE_DIALOG_VISIBLE)) {
			mLimitStorageValue = state
					.getInt(State.LIMIT_STORAGE_DIALOG_VALUE);			
			showDialog(DIALOG_LIMIT_STORAGE_ID);
		}
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		new RefreshStatisticsTask().execute();
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		ProgressDialog d;
		
		switch (id) {
		case DIALOG_REFRESH_STATS_ID:
			d = new ProgressDialog(StorageActivity.this);
			d.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			d.setMessage(getText(R.string.refreshing_dialog_text));
			mRefreshStatsDialog = d;
			return d;
		
		case DIALOG_CHECK_MISSING_ID:
			d = new ProgressDialog(StorageActivity.this);
			d.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			d.setMessage(getText(R.string.checking_dialog_text));
			d.setProgress(mCheckMissingProgress);
			mCheckMissingDialog = d;
			return d;
			
		case DIALOG_REMOVE_CACHED_ID:
			d = new ProgressDialog(StorageActivity.this);
			d.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			d.setMessage(getText(R.string.removing_dialog_text));
			d.setProgress(mRemoveCachedProgress);
			mRemoveCachedDialog = d;
			return d;
			
		case DIALOG_LIMIT_STORAGE_ID:
			LimitStorageDialog l = new LimitStorageDialog(StorageActivity.this);
			l.setTitle(getText(R.string.limit_storage_dialog_title));
			l.setValue(mLimitStorageValue);
			l.setMessage(getText(R.string.limit_storage_dialog_text));
			l.setButton(getText(R.string.ok), new OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			mLimitStorageDialog = l;
			return l;
			
		default:
			break;
		}
		return super.onCreateDialog(id);
	}
	
	@SuppressWarnings("unused") // XXX We'll use this in last management option.
	private OnPreferenceClickListener whiteHackClick = new OnPreferenceClickListener() {
		public boolean onPreferenceClick(Preference preference) {
			// Hack for ~Theme.light in sub PreferenceScreen
			PreferenceScreen screen = (PreferenceScreen) preference;
			screen.getDialog().getWindow().setBackgroundDrawableResource(
					android.R.color.white);
			return true;
		}
	};
	
	private void updateStatistics() {
		final String fmtFiles = res.getString(R.string.files_count_fmt);
		final String fmtFilesAndSize = res.getString(R.string.files_count_and_size_fmt);
		
		// Storage bytesAvailable.
		final long extAvail = getAvailableExternalStorageSize();
		long extTotal = getTotalExternalStorageSize();
		String extFmt = "%1$s free out of %2$s";
		final String extStr = String.format(extFmt,
				FileUtilities.getHumanReadableSize(extAvail),
				FileUtilities.getHumanReadableSize(extTotal));
		
		// Storage bytesUsed.
		final FileStats usedStats = getLocalFilesStats();
		final String usedStr = String.format(
				(usedStats.count > 0 ? fmtFilesAndSize : fmtFiles),
				usedStats.count,
				FileUtilities.getHumanReadableSize(usedStats.size));
		
		// Total files in cloud.
		final FileStats cloudStats = getCloudStats();
		final String cloudStr = String.format(
				(cloudStats.count > 0 ? fmtFilesAndSize : fmtFiles),
				cloudStats.count,
				FileUtilities.getHumanReadableSize(cloudStats.size))
				+ "XXX server bug";
		
		// Synced files stats.
		final FileStats syncedStats = getSyncedFilesStats();
		final String syncedStr = String.format(
				(syncedStats.count > 0 ? fmtFilesAndSize : fmtFiles),
				syncedStats.count,
				FileUtilities.getHumanReadableSize(syncedStats.size));
		
		// Cached files stats.
		final FileStats cachedStats = getCachedFilesStats();
		final String cachedStr = String.format(
				(cachedStats.count > 0 ? fmtFilesAndSize : fmtFiles),
				cachedStats.count,
				FileUtilities.getHumanReadableSize(cachedStats.size));
		
		runOnUiThread(new Runnable() {
			public void run() {
				mStorageAvailable.setSummary(extStr);
				mStorageUsed.setSummary(usedStr);
				mFilesInCloud.setSummary(cloudStr);
				mSyncedFilesSize.setSummary(syncedStr);
				mCachedFilesSize.setSummary(cachedStr);
			}
		});
	}
	
	private static StatFs u1Stat = null;
	
	static {
		try {
			u1Stat = new StatFs(Environment
					.getExternalStorageDirectory().getCanonicalPath());
		} catch (IOException e) {
			Log.e(TAG, "", e);
		}
	}
	
	public static long getTotalExternalStorageSize() {
		long blockCount = u1Stat.getBlockCount();
		long blockSize = u1Stat.getBlockSize();
		long totalExtStorage = blockCount * blockSize;
		Log.v(TAG, "block count: " + blockCount);
		Log.v(TAG, "block size: " + blockSize);
		Log.v(TAG, "total external storage: " + totalExtStorage);
		return totalExtStorage;
	}
	
	public static long getAvailableExternalStorageSize() {
		long blockAvail = u1Stat.getAvailableBlocks();
		long blockSize = u1Stat.getBlockSize();
		long totalAvailStorage = blockAvail * blockSize;
		Log.v(TAG, "block avail: " + blockAvail);
		Log.v(TAG, "block size: " + blockSize);
		Log.v(TAG, "total available storage: " + totalAvailStorage);
		return totalAvailStorage;
	}
	
	public static final class FileStats {
		public long count;
		
		public long size; // bytes
		
		public FileStats() {
			count = 0;
			size = 0;
		}

		@Override
		public String toString() {
			return String.format("FileStats [count=%s, size=%s]",
					count, FileUtilities.getHumanReadableSize(size));
		}
	}
	
	public static FileStats getCloudStats() {
		FileStats stats = new FileStats();
		
//		Cursor c = MetaDatabase.rawQuery(
//				"SELECT count(_id) AS col_count, sum(file_size) AS col_sum " +
//				"FROM files WHERE file_live = 1", null);
//		if (c.moveToFirst()) {
//			stats.count = c.getLong(c.getColumnIndex("col_count"));
//			stats.size = c.getLong(c.getColumnIndex("col_sum"));
//		}
//		c.close();
		
		return stats;
	}
	
	public static FileStats getLocalFilesStats() {
		FileStats stats = new FileStats();
		
//		Cursor c = MetaDatabase.rawQuery(
//				"SELECT count(_id) AS col_count, sum(file_size) AS col_sum " +
//				"FROM files WHERE file_live = 1 AND file_status=?",
//				new String[]{ String.valueOf(FilesStatus.READY) });
//		if (c.moveToFirst()) {
//			stats.count = c.getLong(c.getColumnIndex("col_count"));
//			stats.size = c.getLong(c.getColumnIndex("col_sum"));
//		}
//		c.close();
		
		return stats;
	}
	
	public static FileStats getSyncedFilesStats() {
		FileStats stats = new FileStats();
		
//		Cursor c = MetaDatabase.rawQuery(
//				"SELECT count(_id) AS col_count, sum(file_size) AS col_sum " +
//				"FROM files WHERE file_synced = 'true' AND file_live = 1", null);
//		if (c.moveToFirst()) {
//			stats.count = c.getLong(c.getColumnIndex("col_count"));
//			stats.size = c.getLong(c.getColumnIndex("col_sum"));
//		}
//		c.close();
		
		return stats;
	}
	
	public static FileStats getCachedFilesStats() {
		FileStats stats = new FileStats();
		
//		Cursor c = MetaDatabase.rawQuery(
//				"SELECT count(_id) AS col_count, sum(file_size) AS col_sum " +
//				"FROM files WHERE file_synced != 'true' AND file_live = 1 " +
//				"AND file_status=?",
//				new String[]{ String.valueOf(FilesStatus.READY) });
//		if (c.moveToFirst()) {
//			stats.count = c.getLong(c.getColumnIndex("col_count"));
//			stats.size = c.getLong(c.getColumnIndex("col_sum"));
//		}
//		c.close();
		
		return stats;
	}
	
	public static void test() {
		Log.d(TAG, "synced: " + getSyncedFilesStats());
		Log.d(TAG, "cached: " + getCachedFilesStats());
		Log.d(TAG, "synced files: ");
		long extAvail = getAvailableExternalStorageSize();
		long extTotal = getTotalExternalStorageSize();
		Log.d(TAG, "external avail: " + extAvail);
		Log.d(TAG, "external total: " + extTotal);
		long limit = Preferences.getLocalStorageLimit();
		Log.d(TAG, "storage limit: " + limit);
	}
	
	private class RefreshStatisticsTask extends AsyncTask<Void, Void, Void> {
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			showDialog(DIALOG_REFRESH_STATS_ID);
		}

		@Override
		protected Void doInBackground(Void... params) {
			updateStatistics();
			return null;
		}
		
		@Override
		protected void onPostExecute(Void result) {
			dismiss();
			super.onPostExecute(result);
		}
		
		private void dismiss() {
			if (mRefreshStatsDialog != null && mRefreshStatsDialog.isShowing())
				mRefreshStatsDialog.dismiss();
		}

		@Override
		protected void onCancelled() {
			dismiss();
			UIUtil.showToast(StorageActivity.this, "Canceled refresh!");
			super.onCancelled();
		}
	}
	
	private class CheckMissingFilesTask extends AsyncTask<Void, Integer, Void> {
		
		private Cursor cursor;
		
		private int mMissingFiles = 0;
		
		private int mBadEntries = 0;
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			showDialog(DIALOG_CHECK_MISSING_ID);
		}

		@Override
		protected Void doInBackground(Void... params) {
//			cursor = MetaDatabase.rawQuery(
//					"SELECT _id, file_data FROM files " +
//					"WHERE file_live = 1 AND file_status=?",
//					new String[]{ String.valueOf(FilesStatus.READY) });
			
			if (cursor.moveToFirst()) {
				Integer count = 0;
				Long id;
				String path;
				count = cursor.getCount();
				int i = 0;
				do {
					i++;
					id = cursor.getLong(cursor.getColumnIndex(Nodes._ID));
					path = cursor.getString(cursor.getColumnIndex(Nodes.NODE_DATA));
					if (path != null) {
						File f = new File(path);
						if (!f.exists()) {
							// File is missing in storage.
							markAsRemote(id);
							mMissingFiles++;
						}
					} else {
						// Path is null, and file is marked as READY, this is wrong.
						markAsRemote(id);
						mBadEntries++;
					}
					publishProgress(i*100/count);
				} while (cursor.moveToNext());
			}
			cursor.close();
			return null;
		}
		
		private void markAsRemote(long id) {
//			MetaDatabase.addToBatch(
//					"UPDATE files SET file_status=? WHERE _id=?",
//					new Object[]{ FilesStatus.REMOTE, id });
		}
		
		@Override
		protected void onProgressUpdate(Integer... values) {
			int progress = values[0];
			if (mCheckMissingDialog != null)
				mCheckMissingDialog.setProgress(progress);
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			dismiss();
			int missing = mMissingFiles + mBadEntries;
			String text = missing == 0 ? "All good!" : String.format(
					"%1$d files were missing", mMissingFiles + mBadEntries);
			UIUtil.showToast(StorageActivity.this, text);
			if (missing > 0)
				new RefreshStatisticsTask().execute();
		}
		
		private void dismiss() {
			if (mCheckMissingDialog != null && mCheckMissingDialog.isShowing())
				dismissDialog(DIALOG_CHECK_MISSING_ID);
		}

		@Override
		protected void onCancelled() {
			if (!cursor.isClosed())
				cursor.close();
			
			dismiss();
			UIUtil.showToast(StorageActivity.this, "Canceled!");
			new RefreshStatisticsTask().execute();
			super.onCancelled();
		}
		
	}
	
	private class RemoveCachedFilesTask extends AsyncTask<Void, Integer, Void> {
		
		private Cursor cursor;
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			showDialog(DIALOG_REMOVE_CACHED_ID);
		}

		@Override
		protected Void doInBackground(Void... params) {
			// Clean up unused files.
//			cursor = MetaDatabase.rawQuery(
//					"SELECT _id, file_data FROM files " +
//					"WHERE file_synced != 'true' AND file_live = 1 " +
//					"AND file_status=?",
//					new String[]{ String.valueOf(FilesStatus.READY) });
			
			if (cursor.moveToFirst()) {
				Integer count = 0;
				String path;
				count = cursor.getCount();
				int i = 0;
				do {
					i++;
					path = cursor.getString(cursor.getColumnIndex(Nodes.NODE_DATA));
					if (path != null) {
						File f = new File(path);
						if (f.exists())
							f.delete();
					}
//					MetaDatabase.addToBatch(
//							"UPDATE files SET file_status=? WHERE _id=?",
//							new Object[]{ FilesStatus.REMOTE, id });
					publishProgress(i*100/count);
				} while (cursor.moveToNext());
			}
			cursor.close();
			
			// Clean up unused meta.
			//MetaDatabase.execSQL("DELETE FROM files WHERE file_live=0");
			
			return null;
		}
		
		@Override
		protected void onProgressUpdate(Integer... values) {
			int progress = values[0];
			if (mRemoveCachedDialog != null)
				mRemoveCachedDialog.setProgress(progress);
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			dismiss();
			new RefreshStatisticsTask().execute();
		}
		
		private void dismiss() {
			if (mRemoveCachedDialog != null && mRemoveCachedDialog.isShowing())
				dismissDialog(DIALOG_REMOVE_CACHED_ID);
		}

		@Override
		protected void onCancelled() {
			if (!cursor.isClosed())
				cursor.close();
			dismiss();
			UIUtil.showToast(StorageActivity.this, "Canceled!");
			new RefreshStatisticsTask().execute();
			super.onCancelled();
		}
		
	}
	
	private class LimitStorageDialog extends AlertDialog implements
			OnSeekBarChangeListener {
		
		private SeekBar seekbar;
		
		private long bytesUsed;
		
		private long bytesLimit;
		
		private long bytesAvailable;

		public LimitStorageDialog(Context context) {
			super(context);
			
			// Check how much we're using.
			final FileStats usedStats = getLocalFilesStats();
			bytesUsed = usedStats.size;
			
			// Check the bytesLimit.
			bytesLimit = mLimitStorageValue;
			
			// And how much we could use.
			bytesAvailable = getAvailableExternalStorageSize();
			
			seekbar = new SeekBar(context);
			seekbar.setMax(100);
			setValue(bytesLimit);
			// secondary progress, can't use less than we're already using
			setUsed(bytesUsed);
			seekbar.setOnSeekBarChangeListener(this);
			
			setView(seekbar, 10, 8, 10, 8);
		}
		
		private int normalize(long value) {
			return (int)(100 * value / (double) bytesAvailable);
		}
		
		private long standarize(int value) {
			return (long)((bytesAvailable * value) / (double) 100);
		}
		
		public void setUsed(long value) {
			seekbar.setSecondaryProgress(normalize(value));
		}
		
		public void setValue(long value) {
			seekbar.setProgress(normalize(value));
		}

		public long getValue() {
			return standarize(seekbar.getProgress());
		}

		public void onProgressChanged(SeekBar seekBar, int progress,
				boolean fromUser) {
			// Unused.
		}

		public void onStartTrackingTouch(SeekBar seekBar) {
			// Unused.
		}

		public void onStopTrackingTouch(SeekBar seekBar) {
			final long oneMB = FileUtilities.MiB;
			// Low is smaller of { bytes used, one megabyte }.
			long low = (bytesUsed > oneMB) ? bytesUsed : oneMB;
			// High is upper limit of storage that can be used by U1F.
			long high = getValue();
			// high == mLimitStorage == should always represent seekbar value
			
			if (high < low) {
				// Readjust the seekbar value.
				high = low;
				setValue(low);
			}
			
			mLimitStorageValue = high;
			Preferences.setLocalStorageLimit(high);
			Log.d(TAG, "limit was set to: " + Preferences.getLocalStorageLimit());
			final String sizeStr = FileUtilities.getHumanReadableSize(high);
			final String text = String.format("Limit set to %1$s", sizeStr);
			UIUtil.showToast(StorageActivity.this, text);
		}
		
	}
	
	public static void showFrom(Context context) {
		final Intent intent = new Intent(context, StorageActivity.class);
		context.startActivity(intent);
	}

	@Override
	public void onDestroy() {
		mTracker.dispatch();
		mTracker.stop();
		super.onDestroy();
	}
	
}
