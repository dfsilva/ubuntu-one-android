/*
 * Ubuntu One Files - access Ubuntu One cloud storage on Android platform.
 * 
 * Copyright (C) 2011 Canonical Ltd.
 * Author: Michał Karnicki <michal.karnicki@canonical.com>
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

package com.ubuntuone.android.files;

import greendroid.app.GDActivity;
import greendroid.widget.ActionBar;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.ubuntuone.android.files.activity.LoginActivity;

public class AuthProxy extends GDActivity {
	private static final String TAG = AuthProxy.class.getSimpleName();
	
	/**
	 * In case the OAuth access token is not present, {@link AuthProxy} can
	 * request the {@link SSOLoginActivity} to authenticate first.
	 */
	public static final int REQUEST_AUTHENTICATE = 0;
	
	private TextView mAppNameTextView;
	private Button mAcceptButton;
	private Button mDenyButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		final Intent intent = getIntent();
		if (intent == null || TextUtils.isEmpty(intent.getStringExtra("app_name"))) {
			Log.e(TAG, "android:name of the requesting application not set.");
			Toast.makeText(this, "Ubuntu One: App name not specified!", Toast.LENGTH_SHORT).show();
			
			final Intent data = new Intent();
			data.putExtra("error", "App name not specified.");
			setResult(RESULT_CANCELED, data);
			finish();
			return;
		}
		
		setActionBarContentView(R.layout.activity_authproxy);
		setUpActionBar();
		
		mAppNameTextView = (TextView) findViewById(R.id.app_name);
		mAppNameTextView.setText(intent.getStringExtra("app_name"));
		
		mAcceptButton = (Button) findViewById(R.id.accept_button);
		mAcceptButton.setOnClickListener(mOnAcceptButtonClick);
		
		mDenyButton = (Button) findViewById(R.id.deny_button);
		mDenyButton.setOnClickListener(mOnDenyButtonClick);
		
		if (!Preferences.hasTokens(this)) {
			final Intent loginIntent = new Intent(this, LoginActivity.class);
			startActivityForResult(loginIntent, REQUEST_AUTHENTICATE);
		}
	}
	
	/**
	 * Sets up the background and title and font color of the {@link ActionBar}.
	 */
	private void setUpActionBar() {
		final ActionBar actionBar = getActionBar();
		actionBar.setBackgroundResource(R.drawable.action_bar_background);
		actionBar.setType(ActionBar.Type.Empty);
		actionBar.setTitle("Ubuntu One");
		final TextView actionBarTitle = (TextView) actionBar.findViewById(R.id.gd_action_bar_title);
		actionBarTitle.setTextColor(getResources().getColor(android.R.color.black));
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_AUTHENTICATE) {
			if (resultCode == RESULT_OK) {
				// We now can let the user decide to allow/deny access.
			} else {
				// Something went wrong. Canceled, or error occurred.
				setResult(RESULT_CANCELED, data);
				finish();
			}
		}
	}
	
	private OnClickListener mOnAcceptButtonClick = new OnClickListener() {
		public void onClick(View v) {
			final Intent data = new Intent();
			data.putExtra("oauth_data", Preferences.getSerializedOAuthToken());
			setResult(RESULT_OK, data);
			finish();
		}
	};
	
	private OnClickListener mOnDenyButtonClick = new OnClickListener() {
		public void onClick(View v) {
			final Intent data = new Intent();
			data.putExtra("error", "User denied access.");
			setResult(RESULT_CANCELED, data);
			finish();
		}
	};
}
