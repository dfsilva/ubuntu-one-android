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

import greendroid.widget.PageIndicator;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.ubuntuone.android.files.R;
import com.ubuntuone.android.files.fragment.TutorialPageFragment;

public class TutorialActivity extends FragmentActivity implements
		OnPageChangeListener, OnClickListener {
    private Button signUpNowButton;

    private MyAdapter mAdapter;
    private ViewPager mPager;
    private PageIndicator mPageIndicator;
    
    private int maxPages = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_pager);

        mAdapter = new MyAdapter(getSupportFragmentManager());

        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(mAdapter);
        mPager.setOnPageChangeListener(this);
        
        mPageIndicator = (PageIndicator) findViewById(R.id.indicator);
        mPageIndicator.setDotCount(maxPages);
        mPageIndicator.setDotSpacing(6);
        mPageIndicator.setDotDrawable(
        		getResources().getDrawable(R.drawable.pager_dot));

        signUpNowButton = (Button) findViewById(R.id.sign_up_now);
        
        final Typeface ubuntuB = Typeface.createFromAsset(getAssets(), "Ubuntu-B.ttf");
        signUpNowButton.setTypeface(ubuntuB);
        
        signUpNowButton.setOnClickListener(this);
    }
    
    @Override
	public void onPageScrolled(int arg0, float arg1, int arg2) {
	}

    @Override
	public void onPageScrollStateChanged(int page) {
	}

	@Override
	public void onPageSelected(int page) {
		mPageIndicator.setActiveDot(page);
	}
	
	private void signUp() {
		setResult(RESULT_OK);
		finish();
    }
    
    @Override
	public void onClick(View v) {
    	if (v.getId() == R.id.sign_up_now) {
    		signUp();
    	}
	}

	public class MyAdapter extends FragmentPagerAdapter {
    	
    	public MyAdapter(FragmentManager fm) {
    		super(fm);
    	}

		@Override
		public Fragment getItem(int position) {
			switch (position) {
			case 0:
				return new TutorialPageFragment(R.string.intro_header_1,
	    				R.string.intro_text_1,
	    				R.drawable.slide1);
			case 1:
				return new TutorialPageFragment(R.string.intro_header_2,
	    				R.string.intro_text_2,
	    				R.drawable.slide2);
			case 2:
				return new TutorialPageFragment(R.string.intro_header_3,
	    				R.string.intro_text_3,
	    				R.drawable.slide3);
			case 3:
				return new TutorialPageFragment(R.string.intro_header_4,
	    				R.string.intro_text_4,
	    				R.drawable.slide4);
			default:
				return null;
			}
		}

		@Override
		public int getCount() {
			return maxPages;
		}
    	
    }

    /**
     * Implementation of {@link android.support.v2.view.PagerAdapter} that
     * represents each page as a {@link Fragment} that is persistently
     * kept in the fragment manager as long as the user can return to the page.
     */
    public abstract class FragmentPagerAdapter extends PagerAdapter {

        private final FragmentManager mFragmentManager;
        private FragmentTransaction mCurTransaction = null;

        public FragmentPagerAdapter(FragmentManager fm) {
            mFragmentManager = fm;
        }

        /**
         * Return the Fragment associated with a specified position.
         */
        public abstract Fragment getItem(int position);

        @Override
        public void startUpdate(View container) {
        }

        @Override
        public Object instantiateItem(View container, int position) {
            if (mCurTransaction == null) {
                mCurTransaction = mFragmentManager.beginTransaction();
            }

            // Do we already have this fragment?
            String name = makeFragmentName(container.getId(), position);
            Fragment fragment = mFragmentManager.findFragmentByTag(name);
            if (fragment != null) {
                mCurTransaction.attach(fragment);
            } else {
                fragment = getItem(position);
                mCurTransaction.add(container.getId(), fragment,
                        makeFragmentName(container.getId(), position));
            }

            return fragment;
        }

        @Override
        public void destroyItem(View container, int position, Object object) {
            if (mCurTransaction == null) {
                mCurTransaction = mFragmentManager.beginTransaction();
            }
            mCurTransaction.detach((Fragment)object);
        }

        @Override
        public void finishUpdate(View container) {
            if (mCurTransaction != null) {
                mCurTransaction.commit();
                mCurTransaction = null;
                mFragmentManager.executePendingTransactions();
            }
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return ((Fragment)object).getView() == view;
        }

        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void restoreState(Parcelable state, ClassLoader loader) {
        }

        private String makeFragmentName(int viewId, int index) {
            return "android:switcher:" + viewId + ":" + index;
        }
    }
}
