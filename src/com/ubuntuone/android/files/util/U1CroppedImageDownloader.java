package com.ubuntuone.android.files.util;

import greendroid.image.ScaleImageProcessor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView.ScaleType;

import com.ubuntuone.android.files.service.UpDownService.OnDownloadListener;
import com.ubuntuone.api.files.model.U1Node;
import com.ubuntuone.api.files.request.U1DownloadListener;
import com.ubuntuone.api.files.util.U1CancelTrigger;
import com.ubuntuone.api.files.util.U1Failure;

public class U1CroppedImageDownloader extends U1ImageDownloader
{
	private static final String TAG = U1CroppedImageDownloader.class.getSimpleName();
	
	public static final int SIZE_SMALL = 192;
	
	public static final U1ImageDownloader SMALL =
			new U1CroppedImageDownloader(SIZE_SMALL);
	
	private U1CroppedImageDownloader(int size) {
		super(size);
	}

	@Override
	public void getThumbnail(U1Node node, OnDownloadListener listener) {
		File imageFile = getThumbnailFile(node.getKey(), mSize, false);
		final String key = node.getKey();
		
		if (imageFile.exists()) {
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
	
	private Bitmap cropBitmap(File partFile, File fullFile) {
		ScaleImageProcessor scaleImageProcessor =
				new ScaleImageProcessor(mSize, mSize, ScaleType.CENTER_CROP);
		
		Bitmap bitmap = BitmapFactory.decodeFile(partFile.getAbsolutePath());
		final Bitmap scaled = scaleImageProcessor.processImage(bitmap);
		FileOutputStream out;
		try {
			out = new FileOutputStream(fullFile.getAbsolutePath());
			scaled.compress(Bitmap.CompressFormat.JPEG, 100, out);
		} catch (FileNotFoundException e) {
			// This will never happen, file has just been created.
		} catch (NullPointerException e) {
			// Protect from --- SkImageDecoder::Factory returned null
		}
		partFile.delete();
		
		return scaled;
	}

	protected void downloadThumbnail(final String key,
			final U1CancelTrigger cancelTrigger,
			final OnDownloadListener listener) {
		final File partFile = getThumbnailFile(key, mSize, true);
		final File fullFile = getThumbnailFile(key, mSize, false);
		
		U1DownloadListener apiListener = new U1DownloadListener()
		{
			@Override
			public void onProgress(long bytes, long total) {
				listener.onDownloadProgress(key, bytes, total);
			}

			@Override
			public void onSuccess() {
				cropBitmap(partFile, fullFile);
				Log.d(TAG, "Downloaded and cropped thumb for " + key);
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
				Log.w(TAG, "Could not download cropped thumb: " + failure);
				if (!cancelTrigger.isCancelled()) {
					listener.onDownloadFailure(key, failure);
				}
			}

			@Override
			public void onFinish() {
				mCancelTriggers.remove(key);
			}
		};
		mApi.downloadThumbnail(key, mSize, partFile.getAbsolutePath(),
				apiListener, cancelTrigger);
	}

	@Override
	protected File getThumbnailFile(String key, int size, boolean isPartial) {
		return getThumbnailFile(true, key, size, isPartial);
	}
}
