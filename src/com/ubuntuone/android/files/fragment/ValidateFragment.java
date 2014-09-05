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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;
import com.ubuntuone.android.files.Analytics;
import com.ubuntuone.android.files.Constants;
import com.ubuntuone.android.files.Preferences;
import com.ubuntuone.android.files.R;
import com.ubuntuone.android.files.util.ValidateAccountTask;
import com.ubuntuone.android.files.util.ValidateAccountTask.AccountNotValidatedException;
import com.ubuntuone.android.files.util.ValidateAccountTask.ValidateAccountTaskCallback;
import com.ubuntuone.android.files.widget.TextViewPlus;

public class ValidateFragment extends Fragment implements
		ValidateAccountTaskCallback {
	private static final String TAG = ValidateFragment.class.getSimpleName();
	
	public static final String EXTRA_POST_VALIDATION = "postValidation";
	
	private ValidationFragmentCallback callback;
	
	private TextViewPlus validationHeader;
	private TextViewPlus validationMessage;
	
	private Handler handler;
	
	private String oauthData;
	
	private boolean canceled = false;
	
	private GoogleAnalyticsTracker mTracker;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		mTracker = GoogleAnalyticsTracker.getInstance();
		mTracker.start(Analytics.U1F_ACCOUNT, getActivity());
		
		callback = (ValidationFragmentCallback) getActivity();
		
		handler = new Handler();
		
		final AccountManager am = AccountManager.get(getActivity());
		final Account account = Preferences.getAccount(am);
		
		final String hint = am.getUserData(account, Constants.KEY_AUTHTOKEN_HINT);
		
		if (hint != null) {
			oauthData = hint;
			handler.post(new Runnable() {
				@Override
				public void run() {
					validate();
				}
			});
		} else {
			Log.e(TAG, "ValidateFragment used with no OAuth hint!");
			getActivity().finish();
		}
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		final View content = inflater.inflate(
				R.layout.fragment_validate, container, false);
		validationHeader = (TextViewPlus) content.findViewById(R.id.validation_header);
		validationMessage = (TextViewPlus) content.findViewById(R.id.validation_message);
		
		final Bundle args = getArguments();
		if (args != null && args.containsKey(EXTRA_POST_VALIDATION)) {
			validationHeader.setText(R.string.almost_there);
			validationMessage.setText(R.string.please_wait);
		}
		return content;
	}

	public void validate() {
		Log.i(TAG, "Checking if account validated...");
		
		validateAccountAsync(getActivity(), this, false);
	}
	
	public void validateAccountAsync(Activity activity,
			ValidateAccountTaskCallback callback, boolean withDialog) {
		new ValidateAccountTask(getActivity(), callback, withDialog)
				.execute(oauthData);
	}
	
	@Override
	public void onSuccess() {
		Log.d(TAG, "onSuccess()");
		
		mTracker.trackEvent("Referrer", Analytics.REFERRER, "validate", 1);
		mTracker.trackEvent("Referee", Analytics.REFERRER, "validate", 1);
		mTracker.dispatch();
		
		if (callback != null) {
			callback.validationComplete();
		}
	}

	@Override
	public void onCancel() {
		// Not used.
	}

	@Override
	public void onFailure(Exception e) {
		Log.d(TAG, "onFailure() " + e.getMessage());
		
		if (e.getClass() == AccountNotValidatedException.class) {
			if (!canceled) {
				handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						validate();
					}
				}, 4000);
			}
		}
	}

	@Override
	public void onStop() {
		super.onStop();
		canceled = true;
	}
	
	@Override
	public void onDestroy() {
		if (mTracker != null) {
			mTracker.dispatch();
			mTracker.stop();
		}
		super.onDestroy();
	}
	
	public interface ValidationFragmentCallback {
		public void validationComplete();
	}
}
