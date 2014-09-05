package com.ubuntuone.android.files.view;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class GalleryViewPager extends ViewPager
{
	private boolean mDisabled;

	public GalleryViewPager(Context context) {
		super(context);
	}

	public GalleryViewPager(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return mDisabled ? false : super.onTouchEvent(event);
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		try {
			if (!mDisabled) {
				return super.onInterceptTouchEvent(event);
			}
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}
		return false;
	}

	public void setPagingDisabled(boolean disabled) {
		this.mDisabled = disabled;
	}
}
