package com.ubuntuone.android.files.util;

import java.io.File;

import com.ubuntuone.android.files.UbuntuOneFiles;
import com.ubuntuone.android.files.provider.MetaUtilities;
import com.ubuntuone.android.files.service.UpDownService.OnDownloadListener;
import com.ubuntuone.android.files.service.UpDownServiceHelper;
import com.ubuntuone.api.files.model.U1Node;
import com.ubuntuone.api.files.request.U1DownloadListener;
import com.ubuntuone.api.files.util.U1CancelTrigger;
import com.ubuntuone.api.files.util.U1Failure;

public class U1RegularImageDownloader extends U1ImageDownloader
{
	private static final String TAG = U1RegularImageDownloader.class.getSimpleName();
	
	public static final int SIZE_MEDIUM = 640;
	
	public static final U1ImageDownloader MEDIUM =
			new U1RegularImageDownloader(SIZE_MEDIUM);
	
	private U1RegularImageDownloader(int size) {
		super(size);
	}

	@Override
	public void getThumbnail(U1Node node, OnDownloadListener listener) {
		String fullFilePath = FileUtilities.getFilePathFromResourcePath(
				node.getResourcePath());
		File fullFile = new File(fullFilePath);
		File imageFile = getThumbnailFile(node.getKey(), mSize, false);
		
		final String key = node.getKey();
		
		if (fullFile.exists()) {
			Log.d(TAG, "Using original file to view " + node.getKey());
			listener.onDownloadCached(key, fullFile.getAbsolutePath());
		} else if (imageFile.exists()) {
			Log.d(TAG, "Using thumbnail file to preview " + node.getKey());
			listener.onDownloadCached(key, imageFile.getAbsolutePath());
		} else {
			Log.d(TAG, "Attemtping thumnail fetch.");
			listener.onDownloadStarted(key);
			U1CancelTrigger cancelListener = new U1CancelTrigger();
			mCancelTriggers.put(key, cancelListener);
			downloadThumbnailAsync(key, cancelListener, listener);
		}
	}
	
	private void downloadFileAsync(final String key) {
		Runnable downloadRunnable = new Runnable() {
			@Override
			public void run() {
				U1Node node = MetaUtilities.getNodeByKey(key);
				UpDownServiceHelper.download(
						UbuntuOneFiles.getContext(), node.getResourcePath());
			}
		};
		Log.i(TAG, "Scheduling image download for " + key);
		mExecutor.execute(downloadRunnable);
	}

	protected void downloadThumbnail(final String key,
			final U1CancelTrigger cancelTrigger,
			final OnDownloadListener listener) {
		final File partFile = getThumbnailFile(key, mSize, true);
		final File fullFile = getThumbnailFile(key, mSize, false);
		
		U1DownloadListener apiListener = new U1DownloadListener() {
			@Override
			public void onStart() {
				Log.i(TAG, "Requesting thumb for " + key);
			}
			
			@Override
			public void onProgress(long bytes, long total) {
				listener.onDownloadProgress(key, bytes, total);
			}

			@Override
			public void onSuccess() {
				partFile.renameTo(fullFile);
				listener.onDownloadSuccess(key, fullFile.getAbsolutePath());
			}

			@Override
			public void onFailure(U1Failure failure) {
				Log.w(TAG, "Thumbnail failure: " + failure.getMessage());
				onFileDownloadFailed(failure);
			}

			@Override
			public void onUbuntuOneFailure(U1Failure failure) {
				Log.w(TAG, "Thumbnail U1 failure: " + failure.getMessage());
				onFileDownloadFailed(failure);
			}
			
			private void onFileDownloadFailed(U1Failure failure) {
				if (!cancelTrigger.isCancelled()) {
					listener.onDownloadFailure(key, failure);
					downloadFileAsync(key);
				}
			}

			@Override
			public void onFinish() {
				mCancelTriggers.remove(key);
				Log.i(TAG, "Finished thumb request for " + key);
			}
		};
		mApi.downloadThumbnail(key, mSize, partFile.getAbsolutePath(),
				apiListener, cancelTrigger);
	}

	@Override
	protected File getThumbnailFile(String key, int size, boolean isPartial) {
		return getThumbnailFile(false, key, size, isPartial);
	}
}
