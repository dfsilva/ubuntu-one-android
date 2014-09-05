/*
 * Ubuntu One Files - access Ubuntu One cloud storage on Android platform.
 * 
 * Copyright 2012 Canonical Ltd.
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

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;

import com.ubuntuone.android.files.R;
import com.ubuntuone.android.files.fragment.GalleryFragment;
import com.ubuntuone.android.files.provider.MetaUtilities;
import com.ubuntuone.android.files.service.MetaService;
import com.ubuntuone.android.files.service.MetaServiceHelper;
import com.ubuntuone.android.files.service.UpDownService;
import com.ubuntuone.android.files.service.MetaService.Status;
import com.ubuntuone.android.files.service.UpDownService.OnDownloadListener;
import com.ubuntuone.android.files.util.DetachableResultReceiver;
import com.ubuntuone.android.files.util.UIUtil;
import com.ubuntuone.android.files.util.DetachableResultReceiver.Receiver;
import com.ubuntuone.api.files.util.U1Failure;

public class GalleryActivity extends FragmentActivity implements
		OnDownloadListener, Receiver
{
	private Handler mHandler;
	private DetachableResultReceiver mReceiver;
	
	private final int DIALOG_CREATE_PUBLIC_LINK_ID = 1;
	private Dialog mCreatePublicLinkDialog;
	
	private GalleryFragment galleryFragment;

	private ServiceConnection conn;
	private UpDownService boundService;
	private boolean isBound = false;

	private String directoryResourcePath;
	private String firstImageKey;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_gallery);
		
		mHandler = new Handler();
		mReceiver = new DetachableResultReceiver(mHandler);

		galleryFragment = (GalleryFragment) getSupportFragmentManager()
				.findFragmentById(R.id.galleryFragment);

		directoryResourcePath = getIntent().getStringExtra(
				"directoryResourcePath");
		firstImageKey = getIntent().getStringExtra("firstImageKey");

		showGallery(directoryResourcePath, firstImageKey);

		conn = new ServiceConnection() {	
			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				boundService = ((UpDownService.LocalBinder)service).getService();
				boundService.registerDownloadListener(GalleryActivity.this);
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
				boundService = null;
			}
		};
	}

	@Override
	protected void onResume() {
		super.onResume();
		doBindService();
		if (mReceiver != null) {
			mReceiver.setReceiver(this);
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		if (mReceiver != null) {
			mReceiver.detach();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		doUnbindService();
	}

	private void doBindService() {
        bindService(new Intent(GalleryActivity.this, 
                UpDownService.class), conn, Context.BIND_AUTO_CREATE);
        isBound = true;
    }
    
    private void doUnbindService() {
        if (isBound) {
            unbindService(conn);
            isBound = false;
        }
    }

	private void showGallery(String directoryResourcePath, String firstImageKey) {
		galleryFragment.showGallery(directoryResourcePath, firstImageKey);
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog;
		switch (id) {
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
	
	private Dialog buildCreatePublicLinkDialog() {
		final ProgressDialog dialog = new ProgressDialog(this);
		dialog.setMessage(getString(R.string.creating_link));
		dialog.setIndeterminate(true);
		dialog.setCancelable(false);
		return dialog;
	}
	
	@Override
	public void onDownloadCached(String key, String path) {
		galleryFragment.onDownloadCached(key, path);
	}

	@Override
	public void onDownloadStarted(String key) {
		galleryFragment.onDownloadStarted(key);
	}

	@Override
	public void onDownloadProgress(String key, long bytes, long total) {
		galleryFragment.onDownloadProgress(key, bytes, total);
	}
	
	@Override
	public void onDownloadSuccess(String key, String path) {
		if (path.startsWith(ContentResolver.SCHEME_FILE)) {
			path = path.substring(7); // "file://".length()
		}
		galleryFragment.onDownloadSuccess(key, path);
	}

	@Override
	public void onDownloadFailure(String key, U1Failure failure) {
		galleryFragment.onDownloadFailure(key, failure);
	}
	
	public void onFileCreateLinkClicked(String resourcePath) {
		if (MetaUtilities.getPublicUrl(resourcePath) == null) {
			showDialog(DIALOG_CREATE_PUBLIC_LINK_ID);
			MetaServiceHelper.changePublicAccess(
					this, resourcePath, true, mReceiver);
		} else {
			onFileShareLinkClicked(resourcePath);
		}
	}
	
	private void onFileShareLinkClicked(String resourcePath) {
		final String url = MetaUtilities.getPublicUrl(resourcePath);
		if (!TextUtils.isEmpty(url)) {
			UIUtil.shareLink(this, url);
		}
	}

	@Override
	public void onReceiveResult(int resultCode, final Bundle resultData) {
		String resourcePath = resultData != null ?
				resultData.getString(MetaService.EXTRA_RESOURCE_PATH) : null;
		switch (resultCode) {
		case Status.FINISHED:
			if (mCreatePublicLinkDialog != null
					&& mCreatePublicLinkDialog.isShowing()) {
				mCreatePublicLinkDialog.dismiss();
				if (resourcePath != null) {
					onFileShareLinkClicked(resourcePath);
				}
			}
			break;
		case Status.ERROR:
			if (mCreatePublicLinkDialog != null
					&& mCreatePublicLinkDialog.isShowing()) {
				mCreatePublicLinkDialog.dismiss();
				UIUtil.showToast(GalleryActivity.this, "Could not create public link.", true);
			}
			break;
		}
	}
}
