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

package com.ubuntuone.android.files.fragment;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;
import com.ubuntuone.android.files.Analytics;
import com.ubuntuone.android.files.Preferences;
import com.ubuntuone.android.files.R;
import com.ubuntuone.android.files.provider.WatchedFoldersContract.WatchedFolders;
import com.ubuntuone.android.files.service.AutoUploadService;
import com.ubuntuone.android.files.util.MediaImportUtils;
import com.ubuntuone.android.files.widget.TextViewPlus;

public class AutoUploadCustomizeFragment extends Fragment implements
		OnItemClickListener, OnClickListener
{
	private ListView mFolderList;
	private CursorAdapter mCursorAdapter;
	private TextViewPlus mFolderListFooter;
	private View mFolderListEmpty;
	
	private GoogleAnalyticsTracker mTracker;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		final View view = inflater.inflate(
				R.layout.fragment_autoupload_customize, container, false);
		
		mFolderList = (ListView) view.findViewById(R.id.folder_list);
		mFolderListEmpty = view.findViewById(R.id.folder_list_empty);
		mFolderList.setEmptyView(mFolderListEmpty);
		
		mFolderList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		mFolderList.setOnItemClickListener(this);
		
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				MediaImportUtils.importImageBuckets(getActivity());
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				super.onPostExecute(result);
				mCursorAdapter = getWatchedFoldersAdapter();
				mFolderList.setAdapter(mCursorAdapter);
			}
		}.execute();
		
		mTracker = GoogleAnalyticsTracker.getInstance();
		mTracker.start(Analytics.U1F_ACCOUNT, getActivity());
		
		return view;
	}
	
	private Cursor getWatchedFoldersCursor() {
		final String[] projection = new String[] {
				WatchedFolders._ID,
				WatchedFolders.DISPLAY_NAME,
				WatchedFolders.FOLDER_PATH,
				WatchedFolders.IS_NEW,
				WatchedFolders.AUTO_UPLOAD
		};
		final String selection = "1=1) GROUP BY (" + // ;)
				WatchedFolders.DISPLAY_NAME;
		final String sortOrder = WatchedFolders.IS_NEW + " DESC, " +
				WatchedFolders.DISPLAY_NAME + " ASC";
		
		return getActivity().getContentResolver().query(
				WatchedFolders.Images.CONTENT_URI, projection, selection, null,
				sortOrder);
	}
	
	private CursorAdapter getWatchedFoldersAdapter() {
		final String[] from = new String[] { WatchedFolders.DISPLAY_NAME };
		final int[] to = new int[] { android.R.id.text1 };
		
		return new SimpleCursorAdapter(getActivity(),
				R.layout.list_row_watched_folder,
				getWatchedFoldersCursor(), from, to) {
					@Override
					public void bindView(View view, Context context,
							Cursor cursor) {
						super.bindView(view, context, cursor);
						AutoUploadCustomizeFragment.this
								.bindView(view, context, cursor);
					}
		};
	}
	
	private void bindView(View view, Context context, Cursor cursor) {
		final int checked = cursor.getInt(cursor.getColumnIndex(
				WatchedFolders.AUTO_UPLOAD));
		mFolderList.setItemChecked(cursor.getPosition(),
				checked == 1);
		
		final int isNew = cursor.getInt(cursor.getColumnIndex(
				WatchedFolders.IS_NEW));
		if (isNew == 1) {
			view.setBackgroundResource(R.color.soft_orange);
		} else {
			view.setBackgroundResource(android.R.color.background_light);
		}
	}
	
	public void refreshAdapterCursor() {
		mFolderList.post(new Runnable() {
			@Override
			public void run() {
				final Cursor cursor = getWatchedFoldersCursor();
				mCursorAdapter.changeCursor(cursor);
			}
		});
	}
	
	@Override
	public void onClick(View v) {
		if (v.getId() == mFolderListFooter.getId()) {
			rescanMedia();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		final boolean checked = mFolderList.isItemChecked(position);
		final ContentValues values = new ContentValues(1);
		values.put(WatchedFolders.AUTO_UPLOAD, checked ? 1 : 0);
		values.put(WatchedFolders.LAST_UPLOADED, 0);
		
		Cursor c = (Cursor) mFolderList.getAdapter().getItem(position);
		
		String path = c.getString(c.getColumnIndex(
				WatchedFolders.FOLDER_PATH));
		String pathExpr = path + "%";
		
		String selection = WatchedFolders.FOLDER_PATH + " LIKE ?";
		String[] selectionArgs = new String[] { pathExpr };
		
		if (! checked) {
			mTracker.trackEvent("Settings", "AutoUpload-exclude", path, 1);
		} else {
			mTracker.trackEvent("Settings", "AutoUpload-include", path, 1);
		}
		
		getActivity().getContentResolver().update(
				WatchedFolders.Images.CONTENT_URI, values, selection,
				selectionArgs);
		
		c = getWatchedFoldersCursor();
		((SimpleCursorAdapter) mFolderList.getAdapter()).changeCursor(c);
	}
	
	private void rescanMedia() {
		Intent intent = new Intent(AutoUploadService.ACTION_RESCAN_IMAGES);
		intent.putExtra(AutoUploadService.EXTRA_NEW_ONLY, true);
		getActivity().startService(intent);
	}
	
	@SuppressWarnings("unused")
	private void rescanWithMediaScanner() {
		final String primaryStorage = Environment
				.getExternalStorageDirectory().getAbsolutePath();
		final String secondaryStorage = Preferences
				.getSecondaryStorageDirectory().getAbsolutePath();
		
		Intent primaryStorageIntent = new Intent(Intent.ACTION_MEDIA_MOUNTED,
				Uri.parse("file://" + primaryStorage));
		Intent secondaryStorageIntent = new Intent(Intent.ACTION_MEDIA_MOUNTED,
				Uri.parse("file://" + secondaryStorage));
		
		Activity activity = getActivity();
		activity.sendBroadcast(primaryStorageIntent);
		activity.sendBroadcast(secondaryStorageIntent);
	}
	
	@Override
	public void onStop() {
		rescanMedia();
		
		if (mTracker != null) {
			mTracker.dispatch();
			mTracker.stop();
		}
		super.onStop();
	}
}
