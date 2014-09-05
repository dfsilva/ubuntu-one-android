/*
 * Ubuntu One Files - access Ubuntu One cloud storage on Android platform.
 * 
 * Copyright 2011 Canonical Ltd.
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

package com.ubuntuone.android.files.util;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.http.client.HttpClient;

import com.ubuntuone.android.files.Preferences;
import com.ubuntuone.android.files.UbuntuOneFiles;
import com.ubuntuone.android.files.service.UpDownService.OnDownloadListener;
import com.ubuntuone.api.files.U1FileAPI;
import com.ubuntuone.api.files.model.U1Node;
import com.ubuntuone.api.files.util.U1CancelTrigger;
import com.ubuntuone.api.sso.authorizer.OAuthAuthorizer;

/**
 * Fetches images from U1 thumbnailing API.
 */
public abstract class U1ImageDownloader
{
	private static final String TAG = U1ImageDownloader.class.getSimpleName();
	
	private static final String THUMBS_DIR = "thumbs";
	private static final String NO_MEDIA = ".nomedia";
	
	protected final int mSize;
	
	protected U1FileAPI mApi;
	
	protected Executor mExecutor = Executors.newFixedThreadPool(4);
	
	protected Map<String, U1CancelTrigger> mCancelTriggers =
			Collections.synchronizedMap(
					new HashMap<String, U1CancelTrigger>());
	
	private File mCacheDir;
	
	protected U1ImageDownloader(int size) {
		this.mSize = size;
		
		mCacheDir = new File(FileUtilities.getCacheDirectory(),
				String.format("%s/%d", THUMBS_DIR, mSize));
		
		setupCacheDirectory(mCacheDir);
		setupFileApi();
	}
	
	private void setupCacheDirectory(File cacheDir) {
		if (!cacheDir.exists()) {
			// Create cache directory.
			cacheDir.mkdirs();
			// Make MediaScanner ignore the cache directory.
			File noMedia = new File(cacheDir, NO_MEDIA);
			try {
				noMedia.createNewFile();
			} catch (IOException e) {
				// Ignore.
			}
		}
	}
	
	private void setupFileApi() {
		HttpClient httpClient = HttpClientProvider.getInstance();
		OAuthAuthorizer authorizer = null;
		authorizer = Authorizer.getInstance(false);
		
		mApi = new U1FileAPI(UbuntuOneFiles.class.getPackage().getName(),
				Preferences.getSavedVersionName(),
				httpClient, authorizer);
	}
	
	/**
	 * Implementation of this method should call on to
	 * {@link U1ImageDownloader#downloadThumbnailAsync(String, OnDownloadListener)},
	 * and optionally handle download failure.
	 * 
	 * @param node
	 * @param listener
	 */
	public abstract void getThumbnail(U1Node node, OnDownloadListener listener);
	
	protected void downloadThumbnailAsync(final String key,
			final U1CancelTrigger cancelListener,
			final OnDownloadListener listener) {
		
		Runnable downloadRunnable = new Runnable() {
			@Override
			public void run() {
				setupCacheDirectory(mCacheDir);
				downloadThumbnail(key, cancelListener, listener);
			}
		};
		Log.i(TAG, "Scheduling thumb download for " + key);
		mExecutor.execute(downloadRunnable);
	}
	
	protected abstract void downloadThumbnail(final String key,
			final U1CancelTrigger cancelListener,
			final OnDownloadListener listener);
		
	public void cancel(String key) {
		final U1CancelTrigger trigger = mCancelTriggers.get(key);
		if (trigger != null) {
			mCancelTriggers.remove(key);
			new Thread(new Runnable() {
				@Override
				public void run() {
					trigger.onCancel();
				}
			}).start();
		}
	}
	
	protected abstract File getThumbnailFile(String key, int size, boolean isPartial);
	
	protected File getThumbnailFile(boolean isCropped, String key,
			int size, boolean isPartial) {
		File cacheDir = new File(FileUtilities.getCacheDirectory(),
				String.format("%s/%d", THUMBS_DIR, size));
		String filename = null;
		String cropped = isCropped ? "-cropped" : "";
		if (isPartial) {
			filename = String.format("%s%s.jpg.part", key, cropped);
		} else {
			filename = String.format("%s%s.jpg", key, cropped);
		}
		return new File(cacheDir, FileUtilities.sanitize(filename));
	}
	
	public boolean isThumbnailCached(String key) {
		return getThumbnailFile(key, mSize, false).exists();
	}
}
