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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;
import com.ubuntuone.android.files.Alarms;
import com.ubuntuone.android.files.Analytics;
import com.ubuntuone.android.files.Constants;
import com.ubuntuone.android.files.Preferences;
import com.ubuntuone.android.files.R;
import com.ubuntuone.android.files.UbuntuOneFiles;
import com.ubuntuone.android.files.event.AuthStateEvent;
import com.ubuntuone.android.files.service.AutoUploadService;
import com.ubuntuone.android.files.util.AuthenticateUserTask;
import com.ubuntuone.android.files.util.AuthenticateUserTask.AuthenticateUserTaskCallback;
import com.ubuntuone.android.files.util.Log;
import com.ubuntuone.android.files.util.UIUtil;
import com.ubuntuone.android.files.util.ValidateAccountTask;
import com.ubuntuone.android.files.util.ValidateAccountTask.AccountNotValidatedException;
import com.ubuntuone.android.files.util.ValidateAccountTask.ValidateAccountTaskCallback;
import com.ubuntuone.android.files.widget.ButtonPlus;
import com.ubuntuone.android.files.widget.EditTextPlus;

/**
 * Fragment which shows the log in form and lets the user log in.
 */
public class SignInFragment extends Fragment implements
		AuthenticateUserTaskCallback, ValidateAccountTaskCallback {
	private static final String TAG = SignInFragment.class.getSimpleName();
	
	private SignInFragmentCallback callback;
	
	private EditTextPlus usernameEditText;
	private EditTextPlus passwordEditText;
	private ButtonPlus signInButton;
	
	private GoogleAnalyticsTracker mTracker;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		mTracker = GoogleAnalyticsTracker.getInstance();
		mTracker.start(Analytics.U1F_ACCOUNT, getActivity());
		
		callback = (SignInFragmentCallback) getActivity();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		final View content = inflater.inflate(
				R.layout.fragment_sign_in, container, false);	
		setupViews(content);
		return content;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		// If we have a token cached, first check if it is still valid.
		final AccountManager accountManager = AccountManager.get(getActivity());
		final Account account = Preferences.getAccount(accountManager);
		if (account != null) {
			usernameEditText.setText(account.name);
			passwordEditText.requestFocusFromTouch();
			final String oauthData = accountManager.peekAuthToken(
					account, Constants.AUTH_TOKEN_TYPE);
			if (oauthData != null) {
				checkOAuthTokenAsync(oauthData);
			}
		}
	}
	
	@Override
	public void onDestroy() {
		if (mTracker != null) {
			mTracker.dispatch();
			mTracker.stop();
		}
		super.onDestroy();
	}

	private void setupViews(View content) {
		usernameEditText = (EditTextPlus) content.findViewById(R.id.sso_username);
		usernameEditText.requestFocus();
		passwordEditText = (EditTextPlus) content.findViewById(R.id.sso_password);
		signInButton = (ButtonPlus) content.findViewById(R.id.sign_in);
		
		signInButton.setOnClickListener(onButtonClickedListener);
		passwordEditText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					onSignInButtonClicked();
					return true;
				}
				return false;
			}
		});
	}

	private OnClickListener onButtonClickedListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.sign_in:
				onSignInButtonClicked();
				break;
			}
		}
	};

	private void onSignInButtonClicked() {
		Log.d(TAG, "onSignInButtonClicked()");
		
		// Dismiss soft input method.
		getActivity().getWindow().setSoftInputMode(
				WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		
		final String username = usernameEditText.getText().toString();
		final String password = passwordEditText.getText().toString();
		
		final AccountManager accountManager = AccountManager.get(getActivity());
		final Account account = Preferences.getAccount(accountManager);
		// We currently support one account only.
		if (account != null && !account.name.toLowerCase()
				.equals(username.toLowerCase())) {
			final AlertDialogFragment f = AlertDialogFragment.newInstance(
					-1, R.string.one_account_only,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					}, null);
			
			final FragmentManager fm = getActivity().getSupportFragmentManager();
			final FragmentTransaction ft = fm.beginTransaction();
			f.show(ft, "dialog");
		} else {
			authenticateUserAsync(username, password, this);
		}
	}
	
	private void authenticateUserAsync(String username, String password,
			AuthenticateUserTaskCallback callback) {
		new AuthenticateUserTask(getActivity(), callback)
				.execute(username, password);
	}
	
	@SuppressWarnings("unused")
	private void showSignUpInBrowser() {
		final Intent intent = new Intent(Intent.ACTION_VIEW);
 		intent.setData(Uri.parse(Constants.U1_SIGNUP_URL));
 		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
 		startActivity(intent);
	}
	
	private void checkOAuthTokenAsync(final String oauthData) {
		validateAccountAsync(oauthData, this);
	}

	private void validateAccountAsync(String oauthData,
			ValidateAccountTaskCallback callback) {
		new ValidateAccountTask(getActivity(), callback, true)
				.execute(oauthData);
	}
	
	// AuthenticateUserTask callbacks:

	@Override
	public void onAuthenticateUserSuccess(String oauthData) {
		Preferences.updateSerializedOAuthToken(oauthData);
		validateAccountAsync(oauthData, this);
	}
	
	@Override
	public void onAuthenticateUserAccountNotValidated(String oauthData) {
		callback.onSignUpValidationRequired();
	}

	@Override
	public void onAuthenticateUserCancel() {
		Preferences.updateSerializedOAuthToken(null);
		UIUtil.showToast(getActivity(), "Canceled.");
	}

	@Override
	public void onAuthenticateUserAuthenticationException(Exception e) {
		// There must be a better way of doing this, lower level pieces need fixing.
		Activity activity = getActivity();
		String msg = e != null ? e.getMessage() : "unknown error";
		if (activity != null) {
			if (msg != null && msg.toLowerCase().contains("unauthorized")) {
				UIUtil.showToast(getActivity(),	"Wrong credentials.", true);
			} else {
				UIUtil.showToast(getActivity(),
						"Authentication failed: " + e.getMessage(), true);
			}
		}
	}
	
	@Override
	public void onAuthenticateUserIOException(Exception e) {
		Activity activity = getActivity();
		String msg = e != null ? e.getMessage() : "unknown error";
		if (activity != null) {
			UIUtil.showToast(activity,
					"Connectivity problem: " + msg, true);
		}
	}

	@Override
	public void onAuthenticateUserGenericException(Exception e) {
		Preferences.updateSerializedOAuthToken(null);
		Activity activity = getActivity();
		String msg = e != null ? e.getMessage() : "unknown error";
		if (activity != null) {
			UIUtil.showToast(activity,
					"Authentication failed: " + msg, true);
		}
	}
	
	// ValidateAccountTask callbacks:
	
	@Override
	public void onSuccess() {
		mTracker.trackEvent("Referrer", Analytics.REFERRER, "login", 1);
		mTracker.trackEvent("Referee", Analytics.REFERRER, "login", 1);
		
		final Activity activity = getActivity();
		if (activity != null) {
			// In case we have reauthenticated:
			
			// Retry failed uploads.
			Alarms.maybeRegisterRetryFailedAlarm();
			
			// Restart AutoUploadService.
			if (Preferences.isPhotoUploadConfigured() &&
					Preferences.isPhotoUploadEnabled()) {
				AutoUploadService.startFrom(getActivity());
			}
		}
		
		UbuntuOneFiles.getInstance().setLastAuthStateEvent(new AuthStateEvent(true));
		activity.setResult(Activity.RESULT_OK);
		activity.finish();
	}

	@Override
	public void onCancel() {
		UIUtil.showToast(getActivity(), R.string.canceled);
		Preferences.updateSerializedOAuthToken(null);
	}

	@Override
	public void onFailure(Exception e) {
		if (e.getClass() == AccountNotValidatedException.class) {
			callback.onSignUpValidationRequired();
		} else {
			final String oauthData = Preferences.getSerializedOAuthToken();
			Preferences.updateSerializedOAuthToken(null);
			
			final AccountManager am = AccountManager.get(getActivity());
			am.invalidateAuthToken(Constants.ACCOUNT_TYPE, oauthData);
		}
	}
	
	public interface SignInFragmentCallback {
		public void onSignUpValidationRequired();
	}
}
