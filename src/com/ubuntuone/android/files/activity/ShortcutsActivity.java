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

package com.ubuntuone.android.files.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.WindowManager;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;
import com.ubuntuone.android.files.Alarms;
import com.ubuntuone.android.files.Analytics;
import com.ubuntuone.android.files.R;
import com.ubuntuone.android.files.service.AutoUploadService;
import com.ubuntuone.android.files.util.Log;
import com.ubuntuone.android.files.util.TransferUtils;

@SuppressWarnings("deprecation") // TODO Update GA tracker calls.
public class ShortcutsActivity extends Activity {
	private final static String TAG = ShortcutsActivity.class.getSimpleName();

	public static final String ACTION_UPLOAD_MEDIA_NOW =
			"com.ubuntuone.android.files.ACTION_UPLOAD_MEDIA_NOW";
	public static final String ACTION_START =
			"com.ubuntuone.android.files.ACTION_START";
	
	private GoogleAnalyticsTracker mTracker;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
		
		mTracker = GoogleAnalyticsTracker.getInstance();
		mTracker.start(Analytics.U1F_ACCOUNT, this);
		mTracker.trackPageView(TAG);

		final Intent intent = getIntent();
		final String action = intent != null ? intent.getAction() : null;
		
		if (Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
			onCreateShortcut();
		} else if (ACTION_START.equals(action)) {
			Log.i(TAG, "shortcut used: start");
			onStartAction(this);
			finish();
		} else if (ACTION_UPLOAD_MEDIA_NOW.equals(action)) {
			Log.i(TAG, "shortcut used: upload media now");
			onUploadMediaNowAction(this);
			finish();
		}
	}

	/**
	 * Initiate the upload of any pending new media transfers.
	 * 
	 * @param context
	 *            the context to use
	 */
	private void onUploadMediaNowAction(Context context) {
		startService(new Intent(AutoUploadService.ACTION_RESCAN_IMAGES));
	}

	/**
	 * In case of failed transfers, register retry failed alarm.
	 * 
	 * @param context
	 *            the context to use
	 */
	private void onStartAction(Context context) {
		AutoUploadService.startFrom(context);
		final long failedTransfers = TransferUtils.getFailedUploadsCount(
				getContentResolver());
		if (failedTransfers > 0) {
			Alarms.maybeRegisterRetryFailedAlarm();
		}
	}
	
	private void createUploadMediaNowShortcut() {
		final Intent shortcutIntent = new Intent(ACTION_UPLOAD_MEDIA_NOW);
		final String shortcutName = getString(R.string.shortcut_upload_media);
		final Parcelable shortcutIcon =
			Intent.ShortcutIconResource.fromContext(this, R.drawable.launcher);
		
		final Intent intent = new Intent();
		intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
		intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, shortcutName);
		intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, shortcutIcon);
		
		setResult(RESULT_OK, intent);
	}
	
	private void createStartShortcut() {
		final Intent shortcutIntent = new Intent(ACTION_START);
		final String shortcutName = getString(R.string.shortcut_start);
		final Parcelable shortcutIcon =
			Intent.ShortcutIconResource.fromContext(this, R.drawable.launcher);
		
		final Intent intent = new Intent();
		intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
		intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, shortcutName);
		intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, shortcutIcon);
		
		setResult(RESULT_OK, intent);
	}
	
	/**
	 * Possible shortcuts:<br />
	 * <ul>
	 * <li>directory shortcut</li> TODO karni: Shortcut to a directory.
	 * <li>upload media</li>
	 * <li>start</li>
	 * </ul>
	 * <i>Upload media</i> will simply trigger media upload on demand. 
	 * <i>Start</i> will either start background AutoUploadService or register
	 * auto-upload alarm, and register retry failed alarm, if needed.
	 */
	private void onCreateShortcut() {
		final CharSequence[] items = {
				getString(R.string.shortcut_upload_media),
				getString(R.string.shortcut_start)
			};
		
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.shortcut_type);
		builder.setItems(items, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
				switch (item) {
				case 0:
					createUploadMediaNowShortcut();
					break;
				case 1:
					createStartShortcut();
					break;
				default:
					break;
				}
				finish();
			}
		});
		builder.setOnCancelListener(new OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				finish();
			}
		});
		final AlertDialog alert = builder.create();
		alert.setOwnerActivity(this);
		alert.show();
	}

	@Override
	public void onDestroy() {
		mTracker.dispatch();
		mTracker.stop();
		super.onDestroy();
	}
}
