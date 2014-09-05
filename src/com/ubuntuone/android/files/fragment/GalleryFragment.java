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

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.WeakHashMap;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.ubuntuone.android.files.R;
import com.ubuntuone.android.files.activity.GalleryActivity;
import com.ubuntuone.android.files.holder.GalleryViewHolder;
import com.ubuntuone.android.files.provider.MetaUtilities;
import com.ubuntuone.android.files.service.UpDownService.OnDownloadListener;
import com.ubuntuone.android.files.util.BitmapUtilities;
import com.ubuntuone.android.files.util.U1ImageDownloader;
import com.ubuntuone.android.files.util.U1RegularImageDownloader;
import com.ubuntuone.android.files.view.GalleryViewPager;
import com.ubuntuone.android.files.widget.TextViewPlus;
import com.ubuntuone.api.files.model.U1Node;
import com.ubuntuone.api.files.util.U1Failure;

public class GalleryFragment extends Fragment implements OnPageChangeListener,
		OnDownloadListener, OnClickListener
{
	private GalleryActivity activity;

	private Handler mHandler;
	private U1ImageDownloader mDownloader = U1RegularImageDownloader.MEDIUM;

	private GalleryViewPager mViewPager;
	private GalleryAdapter mAdapter;
	private boolean mDelayBackwardPrefetching = true;
	
	private Queue<View> mRecycledViews = new LinkedList<View>();

	public View detailsWrapper;
	public TextViewPlus imageTitle;
	public View shareUrlView;
	
	public OnClickListener onShareClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			onShareLinkClicked();
		}
	};

	private Animation quickFadeIn;
	private Animation fadeIn;
	private Animation fadeOut;

	private WeakHashMap<String, GalleryViewHolder> holders =
			new WeakHashMap<String, GalleryViewHolder>();

	private String mDirectoryResourcePath;
	private String mCurrentKey;
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		this.activity = (GalleryActivity) activity;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mHandler = new Handler();
		
		if (savedInstanceState != null) {
			mDirectoryResourcePath = savedInstanceState
					.getString("mDirectoryResourcePath");
			mCurrentKey = savedInstanceState
					.getString("mCurrentKey");
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View contentView = inflater.inflate(R.layout.fragment_gallery, null);
		mViewPager = (GalleryViewPager) contentView.findViewById(R.id.viewPager);
		mViewPager.setOnPageChangeListener(this);
		
		detailsWrapper = contentView.findViewById(R.id.details_wrapper);
		imageTitle = (TextViewPlus) contentView.findViewById(R.id.image_title);
		shareUrlView = contentView.findViewById(R.id.share_url);
		shareUrlView.setOnClickListener(onShareClickListener);
		
		setupAnimations();
		
		if (mDirectoryResourcePath != null && mCurrentKey != null) {
			showGallery(mDirectoryResourcePath, mCurrentKey);
		}
		return contentView;
	}
	
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putString("mDirectoryResourcePath", mDirectoryResourcePath);
		outState.putString("mCurrentKey", mCurrentKey);
	}
	
	@Override
	public void onDetach() {
		super.onDetach();
		this.activity = null;
	}

	private void setupAnimations() {
		quickFadeIn = AnimationUtils.loadAnimation(activity, R.anim.quick_fade_in);
		fadeIn = AnimationUtils.loadAnimation(activity, android.R.anim.fade_in);
		fadeOut = AnimationUtils.loadAnimation(activity, android.R.anim.fade_out);
	}

	public void showGallery(String directoryResourcePath, String firstKey) {
		this.mDirectoryResourcePath = directoryResourcePath;
		
		ArrayList<U1Node> nodes = MetaUtilities
				.getPhotoNodesFromDirectory(directoryResourcePath);
		
		// Find the first photo position.
		int position = -1;
		int i = 0;
		for (U1Node node : nodes) {
			if (firstKey.equals(node.getKey())) {
				position = i;
				break;
			}
			i++;
		}
		
		if (position == -1) {
			position = 0;
		}
		
		showGallery(nodes, position);
	}
	
	private void showGallery(ArrayList<U1Node> nodes, int position) {
		mAdapter = new GalleryAdapter(activity, nodes);
		mViewPager.setAdapter(mAdapter);
		mViewPager.setCurrentItem(position, false);
		
		this.mCurrentKey = mAdapter.getItem(position).getKey();
	}
	
	private void onShareLinkClicked() {
		U1Node node = mAdapter.getItem(mViewPager.getCurrentItem());
		activity.onFileCreateLinkClicked(node.getResourcePath());
	}
	
	@Override
	public void onDownloadCached(String key, String path) {
		final GalleryViewHolder holder = holders.get(key);
		if (holder != null) {
			WindowManager wm = getActivity().getWindowManager();
			Display display = wm.getDefaultDisplay();
			int width = display.getWidth();
			int height = display.getHeight();
			int maxDimention = Math.max(width, height);
			
			final Bitmap bitmap = BitmapUtilities.decodeFile(
					new File(path), maxDimention);
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					if (holder.previewImage != null) {
						holder.previewImage.setImageDrawable(
								new BitmapDrawable(bitmap));
						holder.progressWrapper.setVisibility(View.GONE);
					}
				}
			});
		}
	}

	@Override
	public void onDownloadStarted(String key) {
		final GalleryViewHolder holder = holders.get(key);
		/*
		Logger l = Logger.getLogger(U1FileAPI.class.getName());
		l.addHandler(new ErrorHandler(holder.errorTextView));
		*/
		if (holder != null) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					if (holder.progressBar != null) {
						holder.progressBar.startAnimation(quickFadeIn);
						holder.progressBar.setVisibility(View.VISIBLE);
					}
				}
			});
		}
	}
	
	@Override
	public void onDownloadProgress(String key, final long bytes, final long total) {
		final GalleryViewHolder holder = holders.get(key);
		if (holder != null) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					int progress = (int) (bytes * 100 / (double) total);
					if (holder.progressBar != null) {
						holder.progressBar.setIndeterminate(false);
						holder.progressBar.setProgress(progress);
					}
				}
			});
		}
	}

	@Override
	public void onDownloadSuccess(String key, String path) {
		final GalleryViewHolder holder = holders.get(key);
		if (holder != null) {
			final Bitmap bitmap = BitmapUtilities.decodeFile(
					new File(path), U1RegularImageDownloader.SIZE_MEDIUM);
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					if (holder.progressWrapper != null &&
							holder.previewImage != null) {
						holder.previewImage.setImageDrawable(
								new BitmapDrawable(bitmap));
						holder.previewImage.startAnimation(quickFadeIn);
						holder.progressWrapper.setVisibility(View.GONE);
						holder.errorTextView.setVisibility(View.GONE);
					}
				}
			});
		}
	}

	@Override
	public void onDownloadFailure(String key, final U1Failure failure) {
		final GalleryViewHolder holder = holders.get(key);
		if (holder != null) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					if (holder.errorTextView != null) {
						holder.errorTextView.setText(R.string.downloading);
						holder.errorTextView.setVisibility(View.VISIBLE);
						holder.errorTextView.startAnimation(quickFadeIn);
					}
				}
			});
		}
	}

	@Override
	public void onClick(View v) {
		if (detailsWrapper.getVisibility() == View.INVISIBLE) {
			final int currentItem = mViewPager.getCurrentItem();
			U1Node node = mAdapter.getItem(currentItem);
			imageTitle.setText(node.getName());
			
			detailsWrapper.setVisibility(View.VISIBLE);
			detailsWrapper.startAnimation(fadeIn);
			detailsWrapper.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (detailsWrapper.getVisibility() == View.VISIBLE &&
							mViewPager.getCurrentItem() == currentItem) {
						detailsWrapper.setVisibility(View.INVISIBLE);
						detailsWrapper.startAnimation(fadeOut);
					}
				}
			}, 5000);
		}
	}

	@Override
	public void onPageScrollStateChanged(int state) {
		if (state == ViewPager.SCROLL_STATE_DRAGGING) {
			if (detailsWrapper.getVisibility() == View.VISIBLE) {
				detailsWrapper.setVisibility(View.INVISIBLE);
				detailsWrapper.startAnimation(fadeOut);
			}
		}
	}

	@Override
	public void onPageScrolled(int position, float positionOffset,
			int positionOffsetPixels) {
		// Unused.
	}

	@Override
	public void onPageSelected(int position) {
		this.mCurrentKey = mAdapter.getItem(position).getKey();
		GalleryViewHolder holder = holders.get(mCurrentKey);
		if (holder != null) {
			Object tag = holder.previewImage.getTag();
			if (tag != null) {
				((Runnable) tag).run();
			}
		}
	}
	
	public class GalleryAdapter extends PagerAdapter {
		private LayoutInflater inflater;
		private List<U1Node> nodes;

		public GalleryAdapter(Context context, List<U1Node> objects) {
			super();
			this.inflater = LayoutInflater.from(context);
			this.nodes = objects;
		}

		public U1Node getItem(int position) {
			if (0 <= position && position < nodes.size()) {
				return nodes.get(position);
			}
			return null;
		}

		@Override
		public int getCount() {
			return nodes.size();
		}
		
		@Override
		public int getItemPosition(Object object) {
			return nodes.indexOf(object);
		}

		@Override
		public Object instantiateItem(ViewGroup pager, int position) {
			View view = mRecycledViews.poll();
			if (view == null) {
				view = inflater.inflate(R.layout.gallery_view, null, false);
			}
			mViewPager.addView(view);
			
			final U1Node node = getItem(position);
			view.setTag(node.getKey());
			
			GalleryViewHolder holder = (GalleryViewHolder) view
					.getTag(R.id.gallery_holder_key);
			if (holder == null) {
				holder = new GalleryViewHolder(view);
				view.setTag(R.id.gallery_holder_key, holder);
			}
			holders.put(node.getKey(), holder);
			
			holder.progressWrapper.setVisibility(View.VISIBLE);
			holder.progressBar.setVisibility(View.GONE);
			holder.progressBar.setProgress(0);
			holder.previewImage.setVisibility(View.VISIBLE);
			holder.errorTextView.setVisibility(View.GONE);
			
			view.setOnClickListener(GalleryFragment.this);
			
			if (node != null && (!mDelayBackwardPrefetching ||
					mViewPager.getCurrentItem() <= position)) {
				mDownloader.getThumbnail(node, GalleryFragment.this);
			} else {
				// Delay image loading.
				holder.previewImage.setTag(new Runnable() {
					@Override
					public void run() {
						mDelayBackwardPrefetching = false;
						mDownloader.getThumbnail(node, GalleryFragment.this);
					}
				});
			}
			return view;
		}
		
		@Override
		public void destroyItem(View pager, int position, Object view) {
			View v = (View) view;
			mViewPager.removeView(v);
			
			String key = (String) v.getTag();
			holders.remove(key);
			v.setTag(null);
			
			mDownloader.cancel(key);
			
			GalleryViewHolder holder =
					(GalleryViewHolder) v.getTag(R.id.gallery_holder_key);
			holder.progressImage.clearAnimation();
			holder.previewImage.clearAnimation();
			BitmapUtilities.recycleImageViewBitmap(holder.previewImage);
			
			mRecycledViews.add(v);
		}

		@Override
		public boolean isViewFromObject(View view, Object object) {
			return view.equals(object);
		}
	}
	
	/*
	private class ErrorHandler extends java.util.logging.Handler {
		private WeakReference<TextView> textViewRef;
		
		public ErrorHandler(TextView textView) {
			super();
			this.textViewRef = new WeakReference<TextView>(textView);
		}

		@Override
		public void close() {
			// Do nothing.
		}

		@Override
		public void flush() {
			// Do nothing.
		}

		@Override
		public void publish(final LogRecord record) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					TextView tv = textViewRef.get();
					if (tv != null) {
						tv.append(record.getMessage() + "\n");
					}
				}
			});
		}		
	}
	*/
}
