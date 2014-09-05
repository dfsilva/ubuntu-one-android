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

package com.ubuntuone.android.files.fragment;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ubuntuone.android.files.Preferences;
import com.ubuntuone.android.files.R;

public class AutoUploadSetupFragment extends Fragment implements
		OnClickListener, OnCheckedChangeListener {
	private Controller mController;
	
	private ScrollView mContentScroll;
	private RadioGroup mRadioGroup;
	private TextView mMobileNotice;
	private Button mSkipButton;
	private Button mEnableButton;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		final View view = inflater.inflate(
				R.layout.fragment_autoupload_setup, container, false);
		mController = (Controller) getActivity();
		
		mRadioGroup = (RadioGroup) view.findViewById(R.id.auto_upload_mode);
		
		mContentScroll = (ScrollView) view.findViewById(R.id.content_scroll);
		mMobileNotice = (TextView) view.findViewById(R.id.auto_upload_mobile_notice);
		
		mSkipButton = (Button) view.findViewById(R.id.button_skip_auto_upload);
		mSkipButton.setOnClickListener(this);
		
		mEnableButton = (Button) view.findViewById(R.id.button_enable_auto_upload);
		mEnableButton.setOnClickListener(this);
		
		final RadioGroup autoUploadMode =
				(RadioGroup) view.findViewById(R.id.auto_upload_mode);
		autoUploadMode.setOnCheckedChangeListener(this);
		selectInitialAutoUploadMode(autoUploadMode);
		
		return view;
	}
	
	private void selectInitialAutoUploadMode(RadioGroup autoUploadMode) {
		autoUploadMode.check(R.id.auto_upload_on_wifi);
		Preferences.setAutoUploadPhotos(false);
		Preferences.setAutoUploadOnlyOnWiFi(true);
	}

	@Override
	public void onCheckedChanged(RadioGroup group, int checkedId) {
		if (group.getId() == R.id.auto_upload_mode) {
			if (checkedId == R.id.auto_upload_on_both) {
				showMobileNotice();
			} else {
				hideMobileNotice();
			}
		}
	}

	private void showMobileNotice() {
		animateMobileNotice(View.VISIBLE);
		mContentScroll.smoothScrollTo(0, mContentScroll.getBottom());
	}

	private void hideMobileNotice() {
		animateMobileNotice(View.INVISIBLE);
	}

	private void animateMobileNotice(int visibility) {
		if (mMobileNotice.getVisibility() != visibility) {
			mMobileNotice.setAnimation(AnimationUtils.loadAnimation(
					getActivity(),
					visibility == View.VISIBLE ? R.anim.fade_in : R.anim.fade_out));
			mMobileNotice.setEnabled(visibility == View.VISIBLE);
			mMobileNotice.setVisibility(visibility);
		}
	}
	
	public void configurePhotoAutoUploadDisabled() {
		Preferences.setAutoUploadPhotos(false);
		Preferences.setPhotoAutoUploadConfigured(true);
	}
	
	public void configurePhotoAutoUploadEnabledBasedOnSelection(int checkedId) {
		boolean onlyOnWiFi = checkedId == R.id.auto_upload_on_wifi;
		Preferences.setAutoUploadOnlyOnWiFi(onlyOnWiFi);
		Preferences.setAutoUploadPhotos(true);
		Preferences.setPhotoAutoUploadConfigured(true);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.button_skip_auto_upload:
			configurePhotoAutoUploadDisabled();
			if (mController != null) mController.onSetupSkipClicked();
			break;
		case R.id.button_enable_auto_upload:
			int checkedId = mRadioGroup.getCheckedRadioButtonId();
			configurePhotoAutoUploadEnabledBasedOnSelection(checkedId);
			if (mController != null) mController.onSetupDoneClicked();
			break;
		}
	}

	public interface Controller {
		public void onSetupSkipClicked();
		public void onSetupDoneClicked();
	}
}
