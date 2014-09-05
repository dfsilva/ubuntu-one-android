/*
 * Ubuntu One Files - access Ubuntu One cloud storage on Android platform.
 * 
 * Copyright 2011-2013 Canonical Ltd.
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

import greendroid.app.GDListActivity;
import greendroid.widget.ActionBarItem;
import greendroid.widget.ActionBarItem.Type;
import greendroid.widget.LoaderActionBarItem;
import greendroid.widget.NormalActionBarItem;
import greendroid.widget.QuickActionGrid;
import greendroid.widget.QuickActionWidget;
import greendroid.widget.QuickActionWidget.OnQuickActionClickListener;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Locale;
import java.util.WeakHashMap;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;
import com.squareup.otto.Bus;
import com.squareup.otto.Produce;
import com.squareup.otto.Subscribe;
import com.ubuntuone.android.files.Analytics;
import com.ubuntuone.android.files.Preferences;
import com.ubuntuone.android.files.R;
import com.ubuntuone.android.files.UbuntuOneFiles;
import com.ubuntuone.android.files.event.ActivityStateEvent;
import com.ubuntuone.android.files.event.AuthStateEvent;
import com.ubuntuone.android.files.event.SyncStateEvent;
import com.ubuntuone.android.files.holder.FileViewHolder;
import com.ubuntuone.android.files.provider.MetaContract.Nodes;
import com.ubuntuone.android.files.provider.MetaContract.ResourceState;
import com.ubuntuone.android.files.provider.MetaUtilities;
import com.ubuntuone.android.files.provider.TransfersContract.Downloads;
import com.ubuntuone.android.files.service.AutoUploadService;
import com.ubuntuone.android.files.service.MetaService;
import com.ubuntuone.android.files.service.MetaService.Status;
import com.ubuntuone.android.files.service.MetaServiceHelper;
import com.ubuntuone.android.files.service.UpDownService;
import com.ubuntuone.android.files.service.UpDownService.OnDownloadListener;
import com.ubuntuone.android.files.service.UpDownServiceHelper;
import com.ubuntuone.android.files.util.BitmapUtilities;
import com.ubuntuone.android.files.util.ChangeLogUtils;
import com.ubuntuone.android.files.util.DateUtilities;
import com.ubuntuone.android.files.util.DetachableResultReceiver;
import com.ubuntuone.android.files.util.DetachableResultReceiver.Receiver;
import com.ubuntuone.android.files.util.FileUtilities;
import com.ubuntuone.android.files.util.Log;
import com.ubuntuone.android.files.util.NetworkUtil;
import com.ubuntuone.android.files.util.PathTracker;
import com.ubuntuone.android.files.util.TransferUtils;
import com.ubuntuone.android.files.util.U1CroppedImageDownloader;
import com.ubuntuone.android.files.util.U1ImageDownloader;
import com.ubuntuone.android.files.util.UIUtil;
import com.ubuntuone.android.files.util.UIUtil.BlackQuickAction;
import com.ubuntuone.android.files.util.VersionUtilities;
import com.ubuntuone.api.files.model.U1Node;
import com.ubuntuone.api.files.model.U1NodeKind;
import com.ubuntuone.api.files.util.U1Failure;

@SuppressWarnings("deprecation") // TODO Update GA tracker calls.
public class FilesActivity extends GDListActivity
		implements Receiver, OnDownloadListener
{	
	private static final String TAG = FilesActivity.class.getSimpleName(); 
	
	private static interface ActionBar {
		public static final int REFRESH = 0;
		public static final int UPLOAD = 1;
	}
	
	private static interface QuickAction {
		public static final int UPLOAD_IMAGE = 0;
		public static final int UPLOAD_VIDEO = 1;
		public static final int UPLOAD_AUDIO = 2;
		public static final int UPLOAD_FILE = 3;
		public static final int NEW_FOLDER = 4;
	}
	
	private static final int REQUEST_SIGN_IN = 1;
	private static final int REQUEST_PREFERENCES = 2;
	private static final int REQUEST_ADD_FILE = 3;
	private static final int REQUEST_PREVIEW = 4;
	
	public static final String ACTION_PICK_AUTO_UPLOAD_DIRECTORY =
			"com.ubuntuone.android.files.ACTION_PICK_AUTO_UPLOAD_DIRECTORY";
	
	private final String EXTRA_DIALOG_EXTRAS = "extra_dialog_extras";
	private final String EXTRA_TOP_POSITION = "extra_top_position";
	private final String EXTRA_TOP = "extra_top";
	
	private final int DIALOG_DOWNLOAD_ID = 1;
	private final int DIALOG_RENAME_ID = 2;
	private final int DIALOG_DELETE_ID = 3;
	private final int DIALOG_EMPTY_DIRECTORY_ID = 4;
	private final int DIALOG_NEW_DIRECTORY_ID = 5;
	private final int DIALOG_CHECK_DIRECTORY_SIZE_ID = 7;
	private final int DIALOG_PICK_UPLOAD_DIRECTORY_ID = 8;
	private final int DIALOG_NO_NETWORK = 9;
	private final int DIALOG_CREATE_PUBLIC_LINK_ID = 10;
	
	private Dialog mRenameDialog;
	private Dialog mNewDirectoryDialog;
	private Dialog mCheckDirectorySizeDialog;
	private Dialog mCreatePublicLinkDialog;
	private Bundle mDialogExtras;
	
	private GoogleAnalyticsTracker mTracker;

	private Bus mBus;
	private ActivityStateEvent mActivityStateEvent = new ActivityStateEvent();

	private Handler mHandler;
	private DetachableResultReceiver mReceiver;
	
	private ContentResolver mResolver;
	
	private LoaderActionBarItem mLoaderItem;
	private QuickActionWidget mUploadGrid;
	
	private FileViewHolder mContextViewHolder;
	
	private TextView mEmptyTextView;
	private int mTopPosition, mTop;
	
	private FilesAdapter mAdapter;
	private WeakHashMap<String, FileViewHolder> holders =
			new WeakHashMap<String, FileViewHolder>();
	private U1ImageDownloader mImageDownloader;
	private PathTracker mPathTracker;
	private String currentVolumeResourcePath;
	private boolean isGettingVolume = false;
	
	private boolean mIsPickAutoUploadDirectoryMode = false;
	private boolean mIsPickShareWithDirectoryMode = false;
	private Intent shareIntent = null;
	
	@Override
	public int createLayout() {
		return R.layout.activity_list;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		mBus = UbuntuOneFiles.getBus();
		
		mTracker = GoogleAnalyticsTracker.getInstance();
		mTracker.start(Analytics.U1F_ACCOUNT, this);
		mTracker.trackPageView(TAG);

		mHandler = new Handler();
		mReceiver = new DetachableResultReceiver(mHandler);
		
		mResolver = getContentResolver();
		
		// Override splash screen background.
		getWindow().setBackgroundDrawableResource(
				android.R.drawable.screen_background_light);
		
		setupActionBar();
		
		mEmptyTextView = (TextView) findViewById(android.R.id.empty);
		
		ListView listView = getListView();
		listView.setEmptyView(mEmptyTextView);
		listView.setFastScrollEnabled(true);
		
		registerForContextMenu(getListView());
		
		mPathTracker = new PathTracker();
		
		if (NetworkUtil.getAvailableNetworkType(this) == -1) {
			UIUtil.showToast(this, R.string.toast_working_offline, true);
		}
		
		if (Preferences.hasTokens(this)) {
			mImageDownloader = U1CroppedImageDownloader.SMALL;
			onRefresh(null);
			suggestAutoUploadConfiguration();
		} else {
			final Intent intent = getIntent();
			final String data = (intent != null) ? intent.getDataString() : null;
			if (data != null && data.startsWith("x-ubuntuone-sso")) {
				validate();
			} else {
				signIn();
			}
		}
		
		String action = getIntent() != null ? getIntent().getAction() : null;
		if (Intent.ACTION_SEND.equals(action) ||
				Intent.ACTION_SEND_MULTIPLE.equals(action)) {
			mIsPickShareWithDirectoryMode = true;
			shareIntent = getIntent();
			showDialog(DIALOG_PICK_UPLOAD_DIRECTORY_ID);
		}
	}
	
	private void setupActionBar() {
		final ImageButton homeButton =
				(ImageButton) getActionBar().findViewById(
						R.id.gd_action_bar_home_item);
		homeButton.setImageResource(R.drawable.u1_logo);
		
		addActionBarItem(Type.Refresh);
		mLoaderItem = (LoaderActionBarItem) getActionBar()
				.getItem(ActionBar.REFRESH);
		mLoaderItem.setDrawable(R.drawable.ic_act_action_bar_refresh);

		addActionBarItem(Type.Add);
		NormalActionBarItem uploadItem = (NormalActionBarItem) getActionBar()
				.getItem(ActionBar.UPLOAD);
		uploadItem.setDrawable(R.drawable.ic_act_action_bar_add);
		
		onCreateUploadQuickActionGrid();
		
		getActionBar().setBackgroundResource(R.drawable.action_bar_background);
	}
	
	/*
	 * Sadly, FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET works only for the launcher.
	 * If home button long-pressed and used to get back to activity, a validation
	 * request leftover may be visible. I have no fix for this edge case ATM. 
	 */
	
	private void signIn() {
		final Intent intent = new Intent(LoginActivity.ACTION_SIGN_IN);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
		startActivityForResult(intent, REQUEST_SIGN_IN);
	}
	
	private void validate() {
		final Intent intent = new Intent(LoginActivity.ACTION_VALIDATE);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
		startActivityForResult(intent, REQUEST_SIGN_IN);
	}
	
	private void suggestAutoUploadConfiguration() {
		if (!Preferences.isPhotoUploadConfigured()) {
			startActivity(new Intent(this, AutoUploadSetupActivity.class));
		} else if (Preferences.isPhotoUploadEnabled()) {
			AutoUploadService.startFrom(this);
		}
	}
	
	private void onCreateUploadQuickActionGrid() {
		mUploadGrid = new QuickActionGrid(this);
		mUploadGrid.addQuickAction(new BlackQuickAction(this,
				R.drawable.ic_act_upload_photo, R.string.add_image));
		mUploadGrid.addQuickAction(new BlackQuickAction(this,
				R.drawable.ic_act_upload_video, R.string.add_video));
		mUploadGrid.addQuickAction(new BlackQuickAction(this,
				R.drawable.ic_act_upload_audio, R.string.add_audio));
		mUploadGrid.addQuickAction(new BlackQuickAction(this,
				R.drawable.ic_act_upload_file, R.string.add_file));
		mUploadGrid.addQuickAction(new BlackQuickAction(this,
				R.drawable.ic_act_new_folder, R.string.new_folder));
		mUploadGrid.setOnQuickActionClickListener(mUploadQuickActionListener);
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (mUploadGrid != null && mUploadGrid.isShowing()) {
			mUploadGrid.dismiss();
		}
		onCreateUploadQuickActionGrid();
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		// Save dialog extras.
		outState.putBundle(EXTRA_DIALOG_EXTRAS, mDialogExtras);
		// Save list position.
		final ListView l = getListView();
		int topPosition = l.getFirstVisiblePosition();
		final View topView = l.getChildAt(0);
		final int top = (topView == null) ? 0 : topView.getTop();
		outState.putInt(EXTRA_TOP_POSITION, topPosition);
		outState.putInt(EXTRA_TOP, top);
	}
	
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		// Restore dialog extras.
		mDialogExtras = savedInstanceState.getBundle(EXTRA_DIALOG_EXTRAS);
		// Restore list view position.
		int topPosition = savedInstanceState.getInt(EXTRA_TOP_POSITION);
		int top = savedInstanceState.getInt(EXTRA_TOP);
		getListView().setSelectionFromTop(topPosition, top);
		
		super.onRestoreInstanceState(savedInstanceState);
	}
	
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		String data = intent.getDataString();
		String action = intent.getAction();
		if (data != null && data.startsWith("x-ubuntuone-sso")) {
			validate();
		} else if (ACTION_PICK_AUTO_UPLOAD_DIRECTORY.equals(action)) {
			mIsPickAutoUploadDirectoryMode = true;
			showDialog(DIALOG_PICK_UPLOAD_DIRECTORY_ID);
		}
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		
		mActivityStateEvent.setIsVisible(true);
		mBus.post(mActivityStateEvent);
	}

	@Override
	protected void onResume() {
		super.onResume();
		mBus.register(this);
		
		// This should be moved to onCreate, really.
		ChangeLogUtils.maybeShowChangelog(this);
		
		if (mReceiver != null) {
			mReceiver.setReceiver(this);
		}
		setCursorAdapterInBackground();
	}

	@Subscribe
	public void onSyncStateEvent(final SyncStateEvent event) {
		if (event.isRunning()) {
			awaitWithListEmptyTextView();
			showSpinner();
		} else {
			hideSpinner();
		}
	}
	
	@Produce
	public ActivityStateEvent lastActivityStateEvent() {
		return mActivityStateEvent;
	}
	
	@Subscribe
	public void onAuthStateEvent(final AuthStateEvent event) {
		if (!event.isAuthenticated()) {
			signIn();
		}
	}
	
	/**
	 * Requests appropriate cursor and sets the {@link FilesAdapter} on
	 * a background thread, to avoid ANR due to database operation. Also,
	 * we're not using deprecated .requery() anymore.
	 */
	private void setCursorAdapterInBackground() {
		mHandler.post(new Runnable() {
			public void run() {
				final Cursor cursor = mPathTracker.isAtRoot() ?
						MetaUtilities.getVisibleTopNodesCursor() :
							getFilesCurosor(mPathTracker.getCurrentNode());
				startManagingCursor(cursor);
				// We have a new cursor, set a new list adapter.
				runOnUiThread(new Runnable() {
					public void run() {
						if (mAdapter == null) {
							mAdapter = new FilesAdapter(FilesActivity.this,
									cursor, true);
							setListAdapter(mAdapter);
						} else {
							mAdapter.changeCursor(cursor);
							mAdapter.notifyDataSetChanged();
						}
					}
				});
			}
		});
	}

	@Override
	public void startManagingCursor(Cursor c) {
		if (Build.VERSION.SDK_INT < VersionUtilities.HONEYCOMB) {
			super.startManagingCursor(c);
		}
	}

	@Override
	protected void onPause() {
		if (mReceiver != null) {
			mReceiver.detach();
		}
		mBus.unregister(this);
		super.onPause();
	}
	
	@Override
	protected void onStop() {
		mActivityStateEvent.setIsVisible(false);
		mBus.post(mActivityStateEvent);
		
		super.onStop();
	}

	@Override
	public void onDestroy() {
		if (mTracker != null) {
			mTracker.dispatch();
			mTracker.stop();
		}
		super.onDestroy();
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog;
		switch (id) {
		case DIALOG_DOWNLOAD_ID:
			dialog = buildDownloadDialog(mDialogExtras);
			break;
		case DIALOG_RENAME_ID:
			dialog = mRenameDialog = buildRenameDialog(mDialogExtras);
			break;
		case DIALOG_DELETE_ID:
			dialog = buildDeleteDialog(mDialogExtras);
			break;
		case DIALOG_EMPTY_DIRECTORY_ID:
			dialog = buildEmptyDirDialog(mDialogExtras);
			break;
		case DIALOG_NEW_DIRECTORY_ID:
			dialog = mNewDirectoryDialog = buildNewDirDialog(mDialogExtras);
			break;
		case DIALOG_CHECK_DIRECTORY_SIZE_ID:
			dialog = mCheckDirectorySizeDialog =
					buildCheckDirectorySizeDialog(mDialogExtras);
			break;
		case DIALOG_PICK_UPLOAD_DIRECTORY_ID:
			dialog = buildPickUploadDirectory(mDialogExtras);
			break;
		case DIALOG_NO_NETWORK:
			dialog = buildNoNetworkDialog();
			break;
		case DIALOG_CREATE_PUBLIC_LINK_ID:
			dialog = mCreatePublicLinkDialog =
					buildCreatePublicLinkDialog();
			break;
		default:
			dialog = null;
			break;
		}
		return dialog;
	}
	
	private OnClickListener onClickDismiss = new OnClickListener() {
		
		public void onClick(DialogInterface dialog, int which) {
			dialog.dismiss();
		}
		
	};
	
	private Dialog buildNoNetworkDialog() {
		final AlertDialog dialog = new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(R.string.no_network)
				.setMessage(R.string.network_required)
				.setPositiveButton(R.string.ok, onClickDismiss)
				.create();
		return dialog;
	}
	
	private Dialog buildInsufficientSpaceDialog(long needed, long free) {
		final String message = String.format(
				getString(R.string.no_space_message_fmt),
				FileUtilities.getHumanReadableSize(needed),
				FileUtilities.getHumanReadableSize(free));
		final AlertDialog dialog = new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(R.string.no_space)
				.setMessage(message)
				.setPositiveButton(R.string.ok, new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						removeDialog(DIALOG_DOWNLOAD_ID);
					}
				})
				.create();
		return dialog;
	}
	
	private Dialog buildEmptyDirDialog(final Bundle extras) {
		final String title = extras.getString(MetaService.EXTRA_NAME);
		final AlertDialog dialog = new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setTitle(title)
				.setMessage(R.string.nothing_to_download)
				.setPositiveButton(R.string.ok, new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						removeDialog(DIALOG_EMPTY_DIRECTORY_ID);
					}
				})
				.create();
		return dialog;
	}
	
	private Dialog buildCheckDirectorySizeDialog(final Bundle extras) {
		final ProgressDialog dialog = new ProgressDialog(this);
		dialog.setMessage(getString(R.string.checking_size));
		dialog.setIndeterminate(true);
		dialog.setCancelable(true);
		return dialog;
	}
	
	/**
	 * This dialog applies to downloading directories only.
	 * 
	 * @param extras
	 * @return
	 */
	private Dialog buildDownloadDialog(final Bundle extras) {
		final String resourcePath =
				extras.getString(MetaService.EXTRA_RESOURCE_PATH);
		final long size = extras.getLong(MetaService.EXTRA_SIZE);
		final String sizeText = FileUtilities.getHumanReadableSize(size);
		
		final long free = StorageActivity.getAvailableExternalStorageSize();
		if (free < size) {
			return buildInsufficientSpaceDialog(size, free);
		}
		
		final StringBuilder msgBuilder = new StringBuilder(32);
		msgBuilder.append(String.format(
				getString(R.string.folder_size_fmt), sizeText));
		int network = NetworkUtil.getAvailableNetworkType(this);
		if (network == -1) {
			return buildNoNetworkDialog();
		} else if (network == ConnectivityManager.TYPE_MOBILE) {
			msgBuilder.append(" ");
			msgBuilder.append(getString(R.string.folder_download_on_mobile));
		}
		msgBuilder.append(" ");
		msgBuilder.append(getString(R.string.folder_download_prompt));
		
		final OnClickListener onClick = new OnClickListener() {
			
			private void download() {
				UpDownServiceHelper.download(FilesActivity.this, resourcePath);
			}
			
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case Dialog.BUTTON_POSITIVE:
					download();
					break;
				case Dialog.BUTTON_NEGATIVE:
					// Will dismiss below.
					break;
				default:
					Log.e(TAG, "no such button");
					break;
				}
				removeDialog(DIALOG_DOWNLOAD_ID);
			}
			
		};
		
		final AlertDialog dialog = new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setTitle(R.string.download_folder)
				.setMessage(msgBuilder.toString())
				.setPositiveButton(R.string.yes, onClick)
				.setNegativeButton(R.string.no, onClick)
				.create();
		return dialog;
	}
	
	private Dialog buildCreatePublicLinkDialog() {
		final ProgressDialog dialog = new ProgressDialog(this);
		dialog.setMessage(getString(R.string.creating_link));
		dialog.setIndeterminate(true);
		dialog.setCancelable(false);
		return dialog;
	}
	
	private Dialog buildRenameDialog(final Bundle extras) {
		final String resourcePath =
				extras.getString(MetaService.EXTRA_RESOURCE_PATH);
		final String name = extras.getString(MetaService.EXTRA_NAME);
		final String path = extras.getString(MetaService.EXTRA_PATH);
		Log.d(TAG, "dialog built with PATH: " + path);
		Log.d(TAG, "buildRenameDialog: name is " + name);
		
		final OnClickListener onClick = new OnClickListener() {
			
			private final String reserved = "/";
			
			private void rename(DialogInterface dialog) {
				EditText newNameEdit = (EditText) mRenameDialog
						.findViewById(R.id.dialog_edit_text);
				final String newName = newNameEdit.getText().toString();
				if (TextUtils.isEmpty(newName)) {
					UIUtil.showToast(FilesActivity.this,
							R.string.toast_cant_rename_to_nothing);
					dialog.dismiss();
					return;
				} else if (newName.equals(name)) {
					UIUtil.showToast(FilesActivity.this,
							R.string.toast_no_change);
					return;
				}
				
				for (int i = 0; i < reserved.length(); i++) {
					if (newName.indexOf(reserved.charAt(i)) > -1) {
						final String msg = String.format(
								getString(R.string.toast_file_name_cant_contain),
								reserved.charAt(i));
								
						UIUtil.showToast(FilesActivity.this, msg, true);
						return;
					}
				}
				
				final int cutAt = path.lastIndexOf("/") + 1;
				final String parent = path.substring(0, cutAt); 
				final String newPath = parent.concat(newName);
				MetaServiceHelper.rename(FilesActivity.this, resourcePath,
						newPath, newName, mReceiver);
			}
			
			public void onClick(DialogInterface dialog, int which) {
				
				switch (which) {
				case Dialog.BUTTON_POSITIVE:
					rename(dialog);
					break;
				case Dialog.BUTTON_NEGATIVE:
					// Will dismiss below.
					break;
				default:
					Log.e(TAG, "no such button");
					break;
				}
				removeDialog(DIALOG_RENAME_ID);
			}
			
		};
		
		// TODO karni: Make this a regular text dialog.
		final View dialogView = getLayoutInflater()
				.inflate(R.layout.dialog_edittext,
						(ViewGroup) findViewById(R.id.dialog_root));
		final EditText editText =
			(EditText) dialogView.findViewById(R.id.dialog_edit_text);
		editText.setText(name);
		
		final AlertDialog dialog = new AlertDialog.Builder(this)
				.setView(dialogView)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setTitle(R.string.rename_folder)
				.setPositiveButton(R.string.context_rename, onClick)
				.setNegativeButton(R.string.cancel, onClick)
				.create();
		return dialog;
	}
	
	private Dialog buildDeleteDialog(final Bundle extras) {
		final String resourcePath =
				extras.getString(MetaService.EXTRA_RESOURCE_PATH);
		final String name =
				extras.getString(MetaService.EXTRA_NAME);
		
		final OnClickListener onClick = new OnClickListener() {
			
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case Dialog.BUTTON_POSITIVE:
					MetaServiceHelper.deleteNode(
							FilesActivity.this, resourcePath, mReceiver);
					break;
				case Dialog.BUTTON_NEGATIVE:
					// Will dismiss below.
					break;
				default:
					Log.e(TAG, "no such button");
					break;
				}
				removeDialog(DIALOG_DELETE_ID);
			}
			
		};
		
		final AlertDialog dialog = new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(name)
				.setMessage(R.string.delete_item_prompt)
				.setPositiveButton(R.string.yes, onClick)
				.setNegativeButton(R.string.no, onClick)
				.create();
		return dialog;
	}
	
	private Dialog buildNewDirDialog(final Bundle extras) {
		final String path = mPathTracker.isAtRoot()
				? Preferences.U1_RESOURCE : mPathTracker.getCurrentNode();
		Log.d(TAG, "dialog built with path: " + path);
		
		final OnClickListener onClick = new OnClickListener() {
			
			private final String reserved = "|\\?*<\":>+[]/'";
			
			private void create(DialogInterface dialog) {
				EditText editText = (EditText) mNewDirectoryDialog
						.findViewById(R.id.dialog_edit_text);
				final String newName = editText.getText().toString();
				if (newName.length() == 0) {
					UIUtil.showToast(FilesActivity.this,
							R.string.toast_name_cant_be_nothing);
					dialog.dismiss();
					return;
				}
				
				for (int i = 0; i < reserved.length(); i++) {
					if (newName.indexOf(reserved.charAt(i)) > -1) {
						final String msg = String.format(
								getString(R.string.toast_file_name_cant_contain),
								reserved.charAt(i));
								
						UIUtil.showToast(FilesActivity.this, msg, true);
						return;
					}
				}
				
				final String resourcePath = path + "/" + newName;
				MetaServiceHelper.createDirectory(
						FilesActivity.this, resourcePath, newName, mReceiver);
			}
			
			public void onClick(DialogInterface dialog, int which) {
				
				switch (which) {
				case Dialog.BUTTON_POSITIVE:
					create(dialog);
					break;
				case Dialog.BUTTON_NEGATIVE:
					// Will dismiss below.
					break;
				default:
					Log.e(TAG, "no such button");
					break;
				}
				removeDialog(DIALOG_NEW_DIRECTORY_ID);
			}
			
		};
		
		final View dialogView = getLayoutInflater()
				.inflate(R.layout.dialog_edittext,
						(ViewGroup) findViewById(R.id.dialog_root));
		final TextView textView =
			(TextView) dialogView.findViewById(R.id.dialog_text);
		textView.setText(R.string.create_folder);
		
		final AlertDialog dialog = new AlertDialog.Builder(this)
				.setView(dialogView)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setTitle(R.string.create_folder)
				.setPositiveButton(R.string.create, onClick)
				.setNegativeButton(R.string.cancel, onClick)
				.create();
		return dialog;
	}
	
	private Dialog buildPickUploadDirectory(final Bundle extras) {
		final OnClickListener onClick = new OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		};
		
		final AlertDialog dialog = new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setTitle(R.string.select_upload_directory)
				.setMessage(getText(R.string.select_upload_directory_message))
				.setPositiveButton(R.string.ok, onClick)
				.create();
		return dialog;
	}

	@Override
	public boolean onHandleActionBarItemClick(ActionBarItem item, int position) {
		switch (position) {		
		case ActionBar.REFRESH:
			onActionBarRereshClicked();
			break;
		case ActionBar.UPLOAD:
			if (mPathTracker.isAtRoot()) {
				Toast.makeText(this, "Please select a folder first.",
						Toast.LENGTH_SHORT).show();
			} else {
				onActionBarUploadClicked(item.getItemView());
			}
			break;
		default:
			Log.e(TAG, "unknown action bar action");
			return false;
		}
		return true;
	}
	
	private void onRefresh(String resourcePath) {
		Log.i(TAG, "onRefresh: " + ((resourcePath == null) ? "root" : resourcePath));
		if (resourcePath == null) {
			// Refresh user info and volumes (with deltas)
			MetaServiceHelper.getUserInfo(this, mReceiver);
			
			// Refresh ~/Ubuntu One (first time only)
			resourcePath = Preferences.U1_RESOURCE;
			if (!MetaUtilities.isCached(resourcePath)) {
				Log.i(TAG, "Refreshing ~/Ubuntu One");
				MetaServiceHelper.getNode(this, resourcePath, mReceiver);
			}
			
			// Refresh photos UDF/folder (first time only)
			resourcePath = Preferences.getPhotoUploadResourcePath();
			if (!MetaUtilities.isCached(resourcePath) &&
					Preferences.isPhotoUploadEnabled()) {
				Log.i(TAG, "Refreshing photo auto upload location");
				MetaServiceHelper.getNode(this, resourcePath, mReceiver);
			}
		} else {
			if (!MetaUtilities.isCached(resourcePath)) {
				MetaServiceHelper.getNode(this, resourcePath, mReceiver);
			} else {
				if (!isGettingVolume) {
					isGettingVolume = true;
					MetaServiceHelper.getVolume(this, currentVolumeResourcePath, mReceiver);
				}
			}
		}
	}
	
	private void onActionBarRereshClicked() {
		if (!NetworkUtil.isConnected(this)) {
			if (mLoaderItem != null) {
				mLoaderItem.setLoading(false);
			}
			showDialog(DIALOG_NO_NETWORK);
		} else if (mPathTracker.isAtRoot()) {
			onRefresh(null);
		} else {
			String resourcePath = mPathTracker.getCurrentNode();
			MetaUtilities.setIsCached(resourcePath, false);
			onRefresh(resourcePath);
		}
	}
	
	private void onActionBarUploadClicked(View v) {
		mUploadGrid.show(v);
	}
	
	@SuppressWarnings("unused")
	private void onActionBarSettingsClicked() {
		PreferencesActivity.showFrom(this);
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		menu.clear();
		if (mIsPickAutoUploadDirectoryMode ||
				mIsPickShareWithDirectoryMode) {
			if (!mPathTracker.isAtRoot()) {
				inflater.inflate(R.menu.options_menu_pick_auto_upload, menu);
			} else {
				Toast.makeText(this, "Select a folder first.",
						Toast.LENGTH_SHORT).show();
			}
		} else {
			inflater.inflate(R.menu.options_menu_dashboard, menu);
		}
		return super.onPrepareOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.option_preferences:
			onOptionPreferencesSelected();
			break;
		case R.id.option_upload_here:
			if (mIsPickShareWithDirectoryMode) {
				onOptionShareSelected();
			} else if (mIsPickAutoUploadDirectoryMode) {
				onOptionUploadHereSelected();
			}
			break;
		case R.id.option_cancel:
			if (mIsPickShareWithDirectoryMode) {
				onOptionCancelSelected();
				finish();
			} else {
				onOptionCancelSelected();
			}
			break;
		default:
			return false;
		}
		return true;
	}
	
	private void onOptionPreferencesSelected() {
		final Intent intent = new Intent(this, PreferencesActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
		startActivityForResult(intent, REQUEST_PREFERENCES);
	}
	
	private void onOptionShareSelected() {
		String node = mPathTracker.getCurrentNode();
		String uploadResourcePath = 
				(node == null) ? Preferences.U1_RESOURCE : node;
		
		String action = shareIntent != null ? shareIntent.getAction() : null;
		if (Intent.ACTION_SEND.equals(action)) {
			onSendAction(shareIntent, uploadResourcePath);
		} else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
			onSendMultipleAction(shareIntent, uploadResourcePath);
		}
		shareIntent = null;
		mIsPickShareWithDirectoryMode = false;
		finish();
	}
	
	private void onSendAction(Intent intent, String resourcePath) {
		if (intent.hasExtra(Intent.EXTRA_STREAM)) {
			final Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
			UpDownServiceHelper.upload(this, uri, resourcePath, false);			
		}
	}
	
	private void onSendMultipleAction(Intent intent, String resourcePath) {
		if (intent.hasExtra(Intent.EXTRA_STREAM)) {
			ArrayList<Parcelable> list = intent.getParcelableArrayListExtra(
					Intent.EXTRA_STREAM);
			for (Parcelable item : list) {
				Uri uri = (Uri) item;
				UpDownServiceHelper.upload(this, uri, resourcePath, false);
			}
		}
	}
	
	private void onOptionUploadHereSelected() {
		final String node = mPathTracker.getCurrentNode();
		String uploadDirectory = null;
		if (node == null) {
			uploadDirectory = Preferences.U1_RESOURCE.substring(3);
		} else {
			uploadDirectory = node.substring(3);
		}
		Preferences.setPhotoUploadDirectory(uploadDirectory);
		UIUtil.showToast(this,
				getString(R.string.toast_photos_will_auto_upload_to_fmt, uploadDirectory),
				true);
		mIsPickAutoUploadDirectoryMode = false;
	}
	
	private void onOptionCancelSelected() {
		mIsPickAutoUploadDirectoryMode = false;
		mIsPickShareWithDirectoryMode = false;
	}
	
	public void onReceiveResult(int resultCode, final Bundle resultData) {
		final String lastResourcePath =
				resultData.getString(MetaService.EXTRA_RESOURCE_PATH);
		
		switch (resultCode) {
		case Status.FINISHED:
			setCursorAdapterInBackground();
			
			if (lastResourcePath != null &&
					lastResourcePath.equals(currentVolumeResourcePath)) {
				isGettingVolume = false;
				return;
			}
			
			if (mCheckDirectorySizeDialog != null
					&& mCheckDirectorySizeDialog.isShowing()) {
				final String contextResourcePath =
						mDialogExtras.getString(MetaService.EXTRA_RESOURCE_PATH);
				if (lastResourcePath != null &&
						lastResourcePath.equals(contextResourcePath)) {
					mCheckDirectorySizeDialog.dismiss();
					removeDialog(DIALOG_CHECK_DIRECTORY_SIZE_ID);
					// Calculate the size of FILE entries from the directory.
					final long size =
						MetaUtilities.getDirectorySize(contextResourcePath, false);
					mDialogExtras.putLong(MetaService.EXTRA_SIZE, size);
					if (size == 0L) {
						showDialog(DIALOG_EMPTY_DIRECTORY_ID);
					} else {
						showDialog(DIALOG_DOWNLOAD_ID);
					}
				}
			}
			
			if (mCreatePublicLinkDialog != null
					&& mCreatePublicLinkDialog.isShowing()) {
				mCreatePublicLinkDialog.dismiss();
				final String contextResourcePath =
						mDialogExtras.getString(MetaService.EXTRA_RESOURCE_PATH);
				if (lastResourcePath != null &&
						lastResourcePath.equals(contextResourcePath)) {
					if (MetaUtilities.getPublicUrl(contextResourcePath) != null) {
						onFileShareLinkClicked(mContextViewHolder);
					}
				}
			}
			break;
		case Status.ERROR:
			if (mCheckDirectorySizeDialog != null
					&& mCheckDirectorySizeDialog.isShowing()) {
				mCheckDirectorySizeDialog.dismiss();
				removeDialog(DIALOG_CHECK_DIRECTORY_SIZE_ID);
			}
			
			final String errorMessage = resultData
					.getString(MetaService.EXTRA_ERROR);
			final String extraResourcePath = resultData
					.getString(MetaService.EXTRA_RESOURCE_PATH);
			// XXX This does the job, but in a terrible way. Needs more love.
			if (errorMessage == null || errorMessage.equals("auth failed")
					|| errorMessage.toLowerCase(Locale.US).contains("unauthorized")) {
				signIn();
			} else if (!Preferences.getPhotoUploadResourcePath()
						.equals(extraResourcePath)) {
				runOnUiThread(new Runnable() {
					public void run() {
						hideSpinner();
						
						final String msg = String.format(
								getString(R.string.error_fmt), errorMessage);
						UIUtil.showToast(FilesActivity.this, msg, true);
					}
				});
			}
			
			break;
		default:
			break;
		}
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// Was the Back button pressed?
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			if (!mPathTracker.isAtRoot()) {
				mAdapter.cd(this);
				getListView().setSelectionFromTop(mTopPosition, mTop);
				mTopPosition = mTop = 0;
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}
	
	private OnQuickActionClickListener mUploadQuickActionListener =
		new OnQuickActionClickListener() {
	
		public void onQuickActionClicked(QuickActionWidget widget, int position) {
			switch (position) {
			case QuickAction.UPLOAD_IMAGE:
				onUploadImageClicked();
				break;
			case QuickAction.UPLOAD_VIDEO:
				onUploadVideoClicked();
				break;
			case QuickAction.UPLOAD_AUDIO:
				onUploadAudioClicked();
				break;
			case QuickAction.UPLOAD_FILE:
				onUploadFileClicked();
				break;
			case QuickAction.NEW_FOLDER:
				onNewFolderClicked();
				break;
				
			default:
				Log.e(TAG, "unknown upload quick action");
				break;
			}
		}
		
	};
	
	private void onUploadImageClicked() {
		onUpload("image/*", R.string.pick_image, false);
	}
	
	private void onUploadVideoClicked() {
		onUpload("video/*", R.string.pick_video, false);
	}
	
	private void onUploadAudioClicked() {
		onUpload("audio/*", R.string.pick_audio, false);
	}
	
	private void onUploadFileClicked() {
		onUpload("*/*", R.string.pick_file, true);
	}
	
	private void onNewFolderClicked() {
		showDialog(DIALOG_NEW_DIRECTORY_ID);
	}
	
	private void onUpload(final String type, final int titleResId,
			boolean openable) {
		final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType(type);
		if (openable) {
			intent.addCategory(Intent.CATEGORY_OPENABLE);
		}
		final Intent chooser = Intent.createChooser(intent,
				getString(titleResId));
		startActivityForResult(chooser, REQUEST_ADD_FILE);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode) {
		case REQUEST_SIGN_IN:
			if (resultCode == RESULT_OK) {
				mImageDownloader = U1CroppedImageDownloader.SMALL;
				onRefresh(null);
				suggestAutoUploadConfiguration();
			} else if (resultCode != RESULT_OK){
				finish();
			}
			break;
			
		case REQUEST_PREFERENCES:
			if (resultCode == PreferencesActivity.RESULT_UNLINKED) {
				finish();
			}
			break;
		
		case REQUEST_ADD_FILE:
			if (resultCode == RESULT_OK && data != null) {
				final Uri uri = data.getData();
				String parentResourcePath = mPathTracker.getCurrentNode();
				if (parentResourcePath == null) {
					parentResourcePath = Preferences.U1_RESOURCE;
				}
				Log.d(TAG, "will upload " + uri + " to " + parentResourcePath);
				UpDownServiceHelper.upload(this, uri, parentResourcePath, false);
			} else {
				Log.i(TAG, "no file selected");
				UIUtil.showToast(this, R.string.no_files);
			}			
			break;
		}
	}
	
	private void onDirectoryDownloadClicked(final FileViewHolder holder) {
		final Bundle extras = mDialogExtras = new Bundle();
		extras.putString(MetaService.EXTRA_RESOURCE_PATH, holder.resourcePath);
		extras.putString(MetaService.EXTRA_NAME, holder.filename);
		// TODO karni: make recursive download an option
		showDialog(DIALOG_CHECK_DIRECTORY_SIZE_ID);
		MetaServiceHelper.getNode(this, holder.resourcePath, mReceiver);
	}
	
	private void onDirectoryRenameClicked(final FileViewHolder holder) {
		final String path = MetaUtilities.getStringField(
				holder.resourcePath, Nodes.NODE_PATH);
		final Bundle extras = mDialogExtras = new Bundle();
		extras.putString(Nodes.NODE_RESOURCE_PATH, holder.resourcePath);
		extras.putString(Nodes.NODE_PATH, path);
		extras.putString(Nodes.NODE_NAME, holder.filename);
		showDialog(DIALOG_RENAME_ID);
	}
	
	private void onDirectoryDeleteClicked(final FileViewHolder holder) {
		final Bundle extras = mDialogExtras = new Bundle();
		extras.putString(MetaService.EXTRA_RESOURCE_PATH, holder.resourcePath);
		extras.putString(MetaService.EXTRA_NAME, holder.filename);
		showDialog(DIALOG_DELETE_ID);
	}
	
	private void onFileDownloadClicked(FileViewHolder holder) {
		downloadFile(holder.resourcePath);
	}
	
	private void onFileCancelDownloadClicked(FileViewHolder holder) {
		Intent intent = new Intent(UpDownService.ACTION_CANCEL_DOWNLOAD);
		Uri uri = TransferUtils.getDownloadUriByResourcePath(
				getContentResolver(), holder.resourcePath);
		if (uri != null) {
			intent.setData(uri);
			LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
		} else {
			Log.w(TAG, "onFileCancelDownloadClicked() download uri not found");
		}
	}
	
	private void onFileRenameClicked(final FileViewHolder holder) {
		final String path = MetaUtilities.getStringField(
				holder.resourcePath, Nodes.NODE_PATH);
		final Bundle extras = mDialogExtras = new Bundle();
		extras.putString(Nodes.NODE_RESOURCE_PATH, holder.resourcePath);
		extras.putString(Nodes.NODE_PATH, path);
		extras.putString(Nodes.NODE_NAME, holder.filename);
		showDialog(DIALOG_RENAME_ID);
	}
	
	private void onFileDeleteClicked(final FileViewHolder holder) {
		final Bundle extras = mDialogExtras = new Bundle();
		extras.putString(MetaService.EXTRA_RESOURCE_PATH, holder.resourcePath);
		extras.putString(MetaService.EXTRA_NAME, holder.filename);
		showDialog(DIALOG_DELETE_ID);
	}
	
	private void onFileDeleteFromDeviceClicked(final FileViewHolder holder) {
		String path = FileUtilities.getFilePathFromResourcePath(
				holder.resourcePath);
		FileUtilities.safeDeleteSilently(path);
		MetaUtilities.setStateAndData(holder.resourcePath, null, null);
		MetaUtilities.notifyChange(Nodes.CONTENT_URI);
	}
	
	private void onFileCreateLinkClicked(final FileViewHolder holder) {
		final Bundle extras = mDialogExtras = new Bundle();
		extras.putString(Nodes.NODE_RESOURCE_PATH, holder.resourcePath);
		
		if (MetaUtilities.getPublicUrl(holder.resourcePath) == null) {
			showDialog(DIALOG_CREATE_PUBLIC_LINK_ID);
			MetaServiceHelper.changePublicAccess(
					this, holder.resourcePath, true, mReceiver);
		} else {
			onFileShareLinkClicked(holder);
		}
	}
	
	private void onFileDisableLinkClicked(final FileViewHolder holder) {
		final Bundle extras = mDialogExtras = new Bundle();
		extras.putString(Nodes.NODE_RESOURCE_PATH, holder.resourcePath);
		
		MetaServiceHelper.changePublicAccess(
				this, holder.resourcePath, false, mReceiver);
	}
	
	private void onFileShareLinkClicked(final FileViewHolder holder) {
		final String url = MetaUtilities.getPublicUrl(holder.resourcePath);
		UIUtil.shareLink(this, url);
	}
	
	private void buildDirectoryContextMenu(Menu menu, FileViewHolder viewHolder) {
		menu.add(Menu.NONE, R.id.context_download, 0, R.string.context_download);
		menu.add(Menu.NONE, R.id.context_rename, 10, R.string.context_rename);
		menu.add(Menu.NONE, R.id.context_delete, 11, R.string.context_delete);
	}
	
	private void buildFileContextMenu(Menu menu, FileViewHolder holder) {
		if (ResourceState.STATE_GETTING.equals(holder.resourceState)) {
			menu.add(Menu.NONE, R.id.context_cancel_download, 1,
					R.string.context_cancel_download);
		} else {
			menu.add(Menu.NONE, R.id.context_download, 1,
					R.string.context_download);
		}
		if (holder.isPublic) {
			menu.add(Menu.NONE, R.id.context_share_link, 1,
					R.string.context_share_link);
			menu.add(Menu.NONE, R.id.context_disable_link, 2,
					R.string.context_disable_link);
		} else {
			menu.add(Menu.NONE, R.id.context_create_link, 1,
					R.string.context_create_link);
		}
		menu.add(Menu.NONE, R.id.context_rename, 10, R.string.context_rename);
		menu.add(Menu.NONE, R.id.context_delete, 11, R.string.context_delete);
		if (!TextUtils.isEmpty(holder.data) &&
				!holder.data.startsWith(ContentResolver.SCHEME_CONTENT)) {
			menu.add(Menu.NONE, R.id.context_delete_from_device, 12, 
					R.string.context_delete_from_device);
		}
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		mDialogExtras = null;
		
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		final FileViewHolder holder = mContextViewHolder =
				(FileViewHolder) info.targetView.getTag();
		
		final boolean isVolume = holder.parentResourcePath == null;
		
		if (isVolume) {
			closeContextMenu();
		} else if (U1NodeKind.DIRECTORY == holder.kind) {
			buildDirectoryContextMenu(menu, holder);
		} else if (U1NodeKind.FILE == holder.kind) {
			buildFileContextMenu(menu, holder);
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final AdapterContextMenuInfo info =
				(AdapterContextMenuInfo) item.getMenuInfo();
		final FileViewHolder holder = mContextViewHolder =
				(FileViewHolder) info.targetView.getTag();
		final boolean isDirectory = U1NodeKind.DIRECTORY == holder.kind;
		
		if (isDirectory) {
			switch (item.getItemId()) {
			case R.id.context_download:
				onDirectoryDownloadClicked(holder);
				break;
			case R.id.context_rename:
				onDirectoryRenameClicked(holder);
				break;
			case R.id.context_delete:
				onDirectoryDeleteClicked(holder);
				break;
			}
		} else {
			switch (item.getItemId()) {
			case R.id.context_download:
				onFileDownloadClicked(holder);
				break;
			case R.id.context_cancel_download:
				onFileCancelDownloadClicked(holder);
				break;
			case R.id.context_create_link:
				onFileCreateLinkClicked(holder);
				break;
			case R.id.context_share_link:
				onFileShareLinkClicked(holder);
				break;
			case R.id.context_disable_link:
				onFileDisableLinkClicked(holder);
				break;
			case R.id.context_rename:
				onFileRenameClicked(holder);
				break;
			case R.id.context_delete:
				onFileDeleteClicked(holder);
				break;
			case R.id.context_delete_from_device:
				onFileDeleteFromDeviceClicked(holder);
				break;
			}
		}
		
		return super.onContextItemSelected(item);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		Log.d(TAG, "onListItemClick() " + id);
		
		final FileViewHolder holder = (FileViewHolder) v.getTag();		
		if (MetaUtilities.isDirectory(id)) {
			// This should avoid delayed update of the list-empty label.
			if (NetworkUtil.isConnected(this)) {
				awaitWithListEmptyTextView();
			} else {
				mEmptyTextView.setText(R.string.no_network);
			}
			
			// Save the position of the list, just for the previous screen.
			mTopPosition = l.getFirstVisiblePosition();
			View topView = l.getChildAt(0);
			mTop = (topView == null) ? 0 : topView.getTop();
			// Change to selected directory.
			mAdapter.cd(this, holder.resourcePath);
			// Scroll the list to top when entering a directory.
			getListView().setSelectionFromTop(0, 0);
		} else {
			// Open selected file.
			if ((holder.mime.equals(FileUtilities.MIME_JPG) ||
					holder.mime.equals(FileUtilities.MIME_JPEG)) &&
					!TextUtils.isEmpty(holder.key)) {
				String resourcePath = mPathTracker.getCurrentNode();
				Intent gallery = new Intent(this, GalleryActivity.class);
				gallery.putExtra("directoryResourcePath", resourcePath);
				gallery.putExtra("firstImageKey", holder.key);
				startActivityForResult(gallery, REQUEST_PREVIEW);
			} else if (holder.data != null) {
				onFileClicked(holder.resourcePath, holder.resourceState,
						holder.filename, holder.data);
			} else {
				downloadFile(holder.resourcePath);
			}
		}
	}

	private void showSpinner() {
		mHandler.post(new Runnable() {
			public void run() {
				mEmptyTextView.setText(R.string.loading_files);
				if (mLoaderItem != null)
					mLoaderItem.setLoading(true);
			}
		});
	}
	
	private void hideSpinner() {
		mHandler.post(new Runnable() {
			public void run() {
				mEmptyTextView.setText(R.string.folder_is_empty);
				if (mLoaderItem != null) {
					mLoaderItem.setLoading(false);
				}
			}
		});
	}
	
	private void awaitWithListEmptyTextView() {
		String loadingFiles = getString(R.string.loading_files);
		if (mEmptyTextView != null &&
				mEmptyTextView.getText().equals(loadingFiles)) {
			return;
		}
		mHandler.post(new Runnable() {
			public void run() {
				if (mEmptyTextView != null) {
					mEmptyTextView.setText(R.string.loading_files);
				}
				if (!NetworkUtil.isConnected(FilesActivity.this)) {
					mEmptyTextView.setText(R.string.no_network);
				}
			}
		});
	}
	
	public void downloadFile(final String resourcePath) {
		if (NetworkUtil.isConnected(this)) {
			TransferUtils.dequeueByResourcePath(getContentResolver(),
					Downloads.CONTENT_URI, resourcePath);
			UpDownServiceHelper.download(this, resourcePath);
		} else {
			Toast.makeText(this, "Not connected.", Toast.LENGTH_SHORT).show();
		}
	}
	
	private void onFileClicked(final String resourcePath,
			final String resourceState, final String filename,
			final String data) {
		Log.d(TAG, "onFileClicked() data=" + data);
		
		final boolean isStateIdle = (resourceState == null);
		boolean isDownloading, isFailedDownload;
		isDownloading = isFailedDownload = false;
		if (!isStateIdle) {
			isDownloading =
					ResourceState.STATE_GETTING.equals(resourceState);
			isFailedDownload =
					ResourceState.STATE_GETTING_FAILED.equals(resourceState);
		}
		
		final boolean validTarget = MetaUtilities.isValidUriTarget(data);
		
		Log.d(TAG, String.format("isStateIdle=%s, validTarget=%s",
				String.valueOf(isStateIdle), String.valueOf(validTarget)));
		if (validTarget && isStateIdle) {
			// File exists and is not being downloaded.
			if (data.startsWith(ContentResolver.SCHEME_CONTENT)) {
				Log.d(TAG, "opening file from content uri");
				
				final Intent intent = new Intent(Intent.ACTION_VIEW);
				final Uri uri = Uri.parse(data);
				intent.setDataAndType(uri, FileUtilities.getMime(filename));
				
				final Intent chooser = new Intent(Intent.ACTION_VIEW);
				chooser.putExtra(Intent.EXTRA_INTENT, intent);
				chooser.putExtra(Intent.EXTRA_TITLE,
						getText(R.string.open_with));
				try {
					startActivity(intent);
				} catch (ActivityNotFoundException e) {
					UIUtil.showToast(this, R.string.toast_no_suitable_activity);
				}
			} else {
				Log.d(TAG, "opening file directly");
				File file = null;
				try {
					Log.d(TAG, "opening " + data);
					if (data.startsWith(ContentResolver.SCHEME_FILE)) {
						file = new File(URI.create(data));
					} else {
						file = new File(data);
					}
				} catch (Exception e) {
					Log.e(TAG, "file uri is empty", e);
				}
				if (file != null && file.exists()) {
					Log.d(TAG, "Resource exists, opening: " + file.getAbsolutePath());
					Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setDataAndType(Uri.fromFile(file),
							FileUtilities.getMime(data));
					
					Intent chooser = new Intent(Intent.ACTION_VIEW);
					chooser.putExtra(Intent.EXTRA_INTENT, intent);
					chooser.putExtra(Intent.EXTRA_TITLE,
							getText(R.string.open_with));
					try {
						startActivity(intent);
					} catch (ActivityNotFoundException e) {
						UIUtil.showToast(this, R.string.toast_no_suitable_activity);
					}
				} else {
					Log.d(TAG, "Setting resource as not cached: " + resourcePath);
					MetaUtilities.setIsCached(resourcePath, false);
					MetaUtilities.setStateAndData(resourcePath, null, null);
					UIUtil.showToast(this, R.string.toast_file_not_cached_anymore, false);
					mResolver.notifyChange(Nodes.CONTENT_URI, null);
				}
			}
		} else if (isFailedDownload) {
			// File does not exist or download failed.
			Log.d(TAG, "was: failed download or invalid");
			if (NetworkUtil.isConnected(this)) {
				downloadFile(resourcePath);
			} else {
				UIUtil.showToast(this, R.string.toast_no_network);
			}
		} else if (isStateIdle && !validTarget) {
			// File removed from device, need to download.
			downloadFile(resourcePath);
		} else if (isDownloading) {
			UIUtil.showToast(this, "Please wait while downloading...");
		} else {
			Log.e(TAG, "unhandled state: " + resourceState);
		}
	}
	
	private Cursor getFilesCurosor(final String resourcePath) {
		Cursor filesCursor = null;
		if (resourcePath != null) {
			filesCursor = MetaUtilities
					.getVisibleNodesCursorByParent(resourcePath);
			startManagingCursor(filesCursor);
		}
		return filesCursor;
	}
	
	@Override
	public void onDownloadCached(final String key, String path) {
		final FileViewHolder holder = holders.get(key);
		if (holder != null) {
			final Bitmap bitmap = BitmapUtilities.decodeFile(
					new File(path), U1CroppedImageDownloader.SIZE_SMALL);
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					if (holder.icon != null && key.equals(holder.icon.getTag())) {
						holder.icon.setImageDrawable(new BitmapDrawable(bitmap));
					}
				}
			});
		}
	}
	
	@Override
	public void onDownloadStarted(String key) {
		// Do nothing.
	}

	@Override
	public void onDownloadProgress(String key, long bytes, long total) {
		// Do nothing.
	}

	@Override
	public void onDownloadSuccess(final String key, String path) {
		final FileViewHolder holder = holders.get(key);
		if (holder != null) {
			final Bitmap bitmap = BitmapUtilities.decodeFile(
					new File(path), U1CroppedImageDownloader.SIZE_SMALL);
			holders.remove(key);
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					if (holder.icon != null && key.equals(holder.icon.getTag())) {
						holder.icon.setImageDrawable(new BitmapDrawable(bitmap));
					}
				}
			});
		}
	}

	@Override
	public void onDownloadFailure(String key, U1Failure failure) {
		// Do nothing.
		/*
		if (failure.getStatusCode() == 302) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(FilesActivity.this,
							"302 PLEASE REPORT on #u1-internal", 4000).show();
				}
			});
		}
		*/
	}

	private class FilesAdapter extends CursorAdapter {
		
		private LayoutInflater mInflater;
		
		private final static String PURCHASED_FROM_U1 =
				"Purchased from Ubuntu One";
		private final String PURCHASED_MUSIC =
				getString(R.string.purchased_music_display_name);
		
		public FilesAdapter(Context context, Cursor c, boolean autoRequery) {
			super(context, c, autoRequery);
			mInflater = LayoutInflater.from(context);
		}
		
		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			View view = mInflater.inflate(R.layout.list_file_row, parent, false);
			FileViewHolder holder = new FileViewHolder(view);
			view.setTag(holder);
			return view;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			FileViewHolder holder;
			View view = convertView;
			
			if (view == null) {
				view = newView(FilesActivity.this, null, parent);
			} else {
				holder = (FileViewHolder) view.getTag();
				if (holder != null && !TextUtils.isEmpty(holder.key)) {
					mImageDownloader.cancel(holder.key);
					holders.remove(holder.key);
				}
			}
			
			Cursor cursor = getCursor();
			cursor.moveToPosition(position);
			bindView(view, FilesActivity.this, cursor);
			return view;
		}

		@Override
		public void bindView(View view, final Context context, Cursor cursor) {
			view.setBackgroundResource(R.color.list_item_bg_local);
			
			final FileViewHolder holder = (FileViewHolder) view.getTag();
			
			holder.parentResourcePath = cursor.getString(
					cursor.getColumnIndex(Nodes.NODE_PARENT_PATH));
			holder.resourcePath = cursor.getString(
					cursor.getColumnIndex(Nodes.NODE_RESOURCE_PATH));
			holder.resourceState = cursor.getString(
					cursor.getColumnIndex(Nodes.NODE_RESOURCE_STATE));
			holder.mime = cursor.getString(
					cursor.getColumnIndex(Nodes.NODE_MIME));
			holder.key = cursor.getString(
					cursor.getColumnIndex(Nodes.NODE_KEY));
			holder.data = cursor.getString(
					cursor.getColumnIndex(Nodes.NODE_DATA));
			
			final String url = cursor.getString(cursor
					.getColumnIndex(Nodes.NODE_PUBLIC_URL));
			final boolean isPublic = !(url == null || "".equals(url));
			holder.isPublic = isPublic;
			int fileNameColor = isPublic
					? context.getResources().getColor(R.color.text_blue)
					: context.getResources().getColor(R.color.text_red);
			holder.itemName.setTextColor(fileNameColor);
			
			final boolean isVolume = holder.parentResourcePath == null;
			final boolean isDirectory = MetaUtilities.isDirectory(cursor);
			if (isDirectory) {
				holder.kind = U1NodeKind.DIRECTORY;
				holder.icon.setTag(null);
				holder.icon.setImageResource(R.drawable.ic_folder);
			} else {
				holder.kind = U1NodeKind.FILE;
				String mime =
						cursor.getString(cursor.getColumnIndex(Nodes.NODE_MIME));
				if (TextUtils.isEmpty(mime))
					holder.icon.setImageResource(R.drawable.ic_file);
				else if (holder.key != null &&
						(holder.icon.getTag() == null) ||
						!holder.key.equals(holder.icon.getTag())) {
					if (!isPublic) {
						if (mime.startsWith(FileUtilities.MIME_IMAGE)) {
							getThumbnailForJpegDelayed(mime, holder);
						} else if (mime.startsWith(FileUtilities.MIME_VIDEO))
							holder.icon.setImageResource(R.drawable.ic_video);
						else if (mime.startsWith(FileUtilities.MIME_AUDIO))
							holder.icon.setImageResource(R.drawable.ic_audio);
						else
							holder.icon.setImageResource(R.drawable.ic_file);
					} else {
						if (mime.startsWith(FileUtilities.MIME_IMAGE)) {
							getThumbnailForJpegDelayed(mime, holder);
						} else if (mime.startsWith(FileUtilities.MIME_VIDEO))
							holder.icon.setImageResource(R.drawable.ic_video_published);
						else if (mime.startsWith(FileUtilities.MIME_AUDIO))
							holder.icon.setImageResource(R.drawable.ic_audio_published);
						else
							holder.icon.setImageResource(R.drawable.ic_file_published);
					}
				}
			}
			
			final String filename = cursor.getString(cursor
					.getColumnIndex(Nodes.NODE_NAME));
			holder.filename = filename;
			if (isVolume) {
				if (filename.equals(PURCHASED_FROM_U1))
					holder.itemName.setText(PURCHASED_MUSIC);
				else
					holder.itemName.setText(holder.resourcePath.substring(3));
			} else {
				holder.itemName.setText(filename);
			}
			
			holder.itemInteger.setText("");
			if (!isDirectory) {
				final long size = cursor.getLong(cursor
						.getColumnIndex(Nodes.NODE_SIZE));
				final String sizeText = FileUtilities
						.getHumanReadableSize(size);
				holder.itemInteger.setText(sizeText);
			} else {
				final boolean hasChildren = cursor.getInt(cursor
						.getColumnIndex(Nodes.NODE_HAS_CHILDREN)) == 1;
				if (hasChildren) {
					holder.itemInteger.setText("...");
				}
			}
			
			final String state = cursor.getString(
					cursor.getColumnIndex(Nodes.NODE_RESOURCE_STATE));
			if (!isDirectory && (TextUtils.isEmpty(holder.data))) {
				view.setBackgroundResource(R.color.list_item_bg_remote);
			}

			/*
			 * I have reformatted the code to support viewing last modified
			 * under directories as well, but untill we have deltas, it's not
			 * so trivial (if anything has changed 1 level <i>down</i>, we
			 * should update parent directory WHEN_CHANGE timestamp).
			 */
			
			if (isVolume || isDirectory) {
				holder.itemTimestamp.setVisibility(View.GONE);
			} else {
				holder.itemTimestamp.setVisibility(View.VISIBLE);
				if (state == null) {
					// Display last modification time.
					final long modified = cursor.getLong(
							cursor.getColumnIndex(Nodes.NODE_WHEN_CHANGED));
					if (modified < 1000) {
						// Attempt to update the UI to quickly. This will be fixed
						// by updating cache directly from upload response NodeInfo.
						holder.itemTimestamp.setText("");
					} else {
						final String modifiedText = String.format(
								getString(R.string.last_modified_fmt),
								DateUtilities.getFriendlyDate(context, modified));
						holder.itemTimestamp.setText(modifiedText);
					}
				} else {
					// Provide state feedback.
					if (ResourceState.STATE_GETTING.equals(state)) {
						holder.itemTimestamp.setText(R.string.downloading);
					} else if (ResourceState.STATE_POSTING.equals(state)) {
						holder.itemTimestamp.setText(R.string.uploading);
					} else if (ResourceState.STATE_DELETING.equals(state)) {
						holder.itemTimestamp.setText(R.string.deleting);
					} else if (ResourceState.STATE_GETTING_FAILED.equals(state)) {
						holder.itemTimestamp.setText(R.string.download_failed_tap_resume);
					} else if (ResourceState.STATE_POSTING_FAILED.equals(state)) {
						holder.itemTimestamp.setText(R.string.upload_failed_tap_retry);
					}
				}
			}
		}
		
		private void getThumbnailForJpegDelayed(String mime, final FileViewHolder holder) {
			// Fixed FilesActivity.java:1868 java.lang.NullPointerException in 1.2.4
			// Fixed U1CroppedImageDownloader.java:34 java.lang.NullPointerException in 1.2.4
			if ((mime.equals(FileUtilities.MIME_JPG) ||
					mime.equals(FileUtilities.MIME_JPEG)) &&
					!TextUtils.isEmpty(holder.key)) {
				final String key = holder.key;
				holder.icon.setImageResource(R.drawable.ic_photo);
				holder.icon.setTag(key);
				
				boolean isCached = mImageDownloader.isThumbnailCached(key);
				if (!isCached) {
					holder.icon.setImageResource(R.drawable.ic_photo);
				}
				mHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
						if (key.equals(holder.key)) {
							holders.put(key, holder);
							U1Node node = MetaUtilities.getNodeByKey(holder.key);
							mImageDownloader.getThumbnail(node, FilesActivity.this);
						}
					}
				}, isCached ? 0 : 250);
			} else {
				holder.icon.setImageResource(R.drawable.ic_photo);
			}
		}
		
		/**
		 * cd ..
		 */
		public boolean cd(final Activity activity) {
			if (mPathTracker.isAtRoot()) {
				return false;
			}
			mPathTracker.cd();
			if (mPathTracker.isAtRoot()) {
				final Cursor filesCursor = MetaUtilities
						.getVisibleTopNodesCursor();
				Log.v(TAG, "changing cursor");
				changeCursor(filesCursor);
			} else {
				final String node = mPathTracker.getCurrentNode();
				final Cursor filesCursor = getFilesCurosor(node);
				Log.v(TAG, "changing cursor");
				changeCursor(filesCursor);
			}
			return true;
		}
		
		/**
		 * cd into dir
		 */
		public void cd(final Activity activity, final String resourcePath) {
			if (mPathTracker.isAtRoot()) {
				currentVolumeResourcePath = "/volumes" + resourcePath;
			}
			mPathTracker.cd(resourcePath);
			final Cursor filesCursor = getFilesCurosor(resourcePath);
			changeCursor(filesCursor);
			notifyDataSetChanged();
			
			if (NetworkUtil.isConnected(FilesActivity.this)) {
				onRefresh(resourcePath);
			}
		}
	}
	
	public static void showFrom(Activity activity) {
		final Intent intent = new Intent(activity, FilesActivity.class);
		activity.startActivity(intent);
		activity.overridePendingTransition(
				android.R.anim.fade_in, android.R.anim.fade_out);
	}
}
