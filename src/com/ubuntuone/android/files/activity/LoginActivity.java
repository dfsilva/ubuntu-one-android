/*
 * Ubuntu One Files - access Ubuntu One cloud storage on Android platform.
 * 
 * Copyright (C) 2011-2013 Canonical Ltd.
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

package com.ubuntuone.android.files.activity;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;

import com.ubuntuone.android.files.Constants;
import com.ubuntuone.android.files.Preferences;
import com.ubuntuone.android.files.R;
import com.ubuntuone.android.files.authenticator.AuthenticatorResultListener;
import com.ubuntuone.android.files.fragment.SignInFragment;
import com.ubuntuone.android.files.fragment.SignInFragment.SignInFragmentCallback;
import com.ubuntuone.android.files.fragment.SignInOrUpFragment;
import com.ubuntuone.android.files.fragment.SignInOrUpFragment.SignInOrUpFragmentCallback;
import com.ubuntuone.android.files.fragment.SignUpFragment;
import com.ubuntuone.android.files.fragment.SignUpFragment.SignUpFragmentCallback;
import com.ubuntuone.android.files.fragment.ValidateFragment;
import com.ubuntuone.android.files.fragment.ValidateFragment.ValidationFragmentCallback;
import com.ubuntuone.android.files.util.UIUtil;

public class LoginActivity extends FragmentActivity implements
		SignInOrUpFragmentCallback,
		SignInFragmentCallback,
		SignUpFragmentCallback,
		ValidationFragmentCallback,
		AuthenticatorResultListener {
	
	private static final String TAG = LoginActivity.class.getSimpleName();
	
	public static final int RESULT_ERROR = RESULT_FIRST_USER;
	
	public static final String ACTION_SIGN_IN = "com.ubuntuone.android.files.ACTION_SIGN_IN";	
	public static final String ACTION_VALIDATE = "com.ubuntuone.android.files.ACTION_VALIDATE";
	
	private static final int FRAGMENT_VALIDATE = 0;
	private static final int FRAGMENT_SIGN_IN_OR_UP = 1;
	private static final int FRAGMENT_SIGN_IN = 2;
	
	/**
	 * {@link AccountAuthenticatorResponse} in case this activity has been
	 * called by {@link AccountManager}.
	 */
	private AccountAuthenticatorResponse mAccountAuthenticatorResponse = null;
	
	/**
	 * The result bundle to return to {@AccountManager}.
	 */
	private Bundle mResultBundle = null;

	/**
	 * @see android.support.v4.app.FragmentActivity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		dismissReauthenticateNotification();
		
		setContentView(R.layout.fragment_content);
		
		final Intent intent = getIntent();
		if (intent == null) {
			Log.e(TAG, "This activity intended to be instantiated via an Intent.");
			finish();
			return;
		}
		
		mAccountAuthenticatorResponse = intent.getParcelableExtra(
				AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);
		if (mAccountAuthenticatorResponse != null) {
			mAccountAuthenticatorResponse.onRequestContinued();
		}
		
		int fragmentToShow = FRAGMENT_SIGN_IN_OR_UP;
		
		final String action = intent.getAction();
		if (ACTION_VALIDATE.equals(action) && savedInstanceState == null) {
			showValidateFragment(true);
			return;
		}
		
		final AccountManager accountManager = AccountManager.get(this);
		final Account account = Preferences.getAccount(accountManager);
		if (account != null && savedInstanceState == null) {
			fragmentToShow = FRAGMENT_SIGN_IN;
			final String hint = accountManager.getUserData(
					account, Constants.KEY_AUTHTOKEN_HINT);
			final String oauthData = accountManager.peekAuthToken(
					account, Constants.AUTH_TOKEN_TYPE);
			if (oauthData == null && hint != null) {
				fragmentToShow = FRAGMENT_VALIDATE;
			} else if (oauthData != null && mAccountAuthenticatorResponse != null) {
				UIUtil.showToast(this, R.string.one_account_only, true);
				finish();
				return;
			}
		}
		
		if (savedInstanceState != null) {
			// Android will re-add the fragment automatically.
			return;
		}
		
		switch (fragmentToShow) {
		case FRAGMENT_VALIDATE:
			showValidateFragment(false);
			break;
		case FRAGMENT_SIGN_IN_OR_UP:
			showSignInOrUpFragment();
			break;
		case FRAGMENT_SIGN_IN:
			showSignInFragment(false);
			break;
			
		default:
			break;
		}
	}
	
	public void dismissReauthenticateNotification() {
		NotificationManager nm = (NotificationManager)
				getApplicationContext().getSystemService(NOTIFICATION_SERVICE);
		nm.cancel(R.id.stat_please_reauthenticate);
	}

	@Override
	public void finish() {
		if (mAccountAuthenticatorResponse != null) {
			if (mResultBundle != null) {
				Log.d(TAG, "Setting authenticator response.");
				mAccountAuthenticatorResponse.onResult(mResultBundle);
			} else {
				Log.d(TAG, "Canceled, not setting authenticator response.");
				mAccountAuthenticatorResponse.onError(
						AccountManager.ERROR_CODE_CANCELED, "canceled");
			}
			mAccountAuthenticatorResponse = null;
		}
		super.finish();
	}

	@Override
	public void setAccountAuthenticatorResult(Bundle result) {
		mResultBundle = result;
	}
	
	@Override
	public void reqestedSignIn() {
		showSignInFragment(true);
	}

	@Override
	public void requestedSignUp() {
		showSignUpFragment(true);
	}

	@Override
	public void onSignUpValidationRequired() {
		showValidateFragment(false);
	}
	
	@Override
	public void onSignUpComplete() {
		showValidateFragment(true);
	}

	@Override
	public void validationComplete() {
		setResult(RESULT_OK);
		finish();
	}

	public void showSignInOrUpFragment() {
		final FragmentManager fragmentManager = getSupportFragmentManager();
		final SignInOrUpFragment signInOrUpFragment = new SignInOrUpFragment();
		
		final FragmentTransaction ft = fragmentManager.beginTransaction();
		ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
				.replace(R.id.content, signInOrUpFragment, "signInOrUp")
				.commit();
	}

	public void showSignInFragment(boolean addToBackStack) {
		final FragmentManager fragmentManager = getSupportFragmentManager();
		final SignInFragment signInFragment = new SignInFragment();
		
		final FragmentTransaction ft = fragmentManager.beginTransaction();
		ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
				.replace(R.id.content, signInFragment, "signIn");
		if (addToBackStack)
			ft.addToBackStack(null);
		ft.commit();
	}

	public void showSignUpFragment(boolean addToBackStack) {
		final FragmentManager fragmentManager = getSupportFragmentManager();
		final SignUpFragment signUpFragment = new SignUpFragment();
		
		final FragmentTransaction ft = fragmentManager.beginTransaction();
		ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
				.replace(R.id.content, signUpFragment, "signUp");
		if (addToBackStack)
			ft.addToBackStack(null);
		ft.commit();
	}

	public void showValidateFragment(boolean postValidation) {
		final FragmentManager fragmentManager = getSupportFragmentManager();
		final ValidateFragment validateFragment = new ValidateFragment();
		if (postValidation) {
			final Bundle args = new Bundle();
			args.putBoolean(ValidateFragment.EXTRA_POST_VALIDATION, postValidation);
			validateFragment.setArguments(args);
		}
		/*
		 * ValidateFragment has two states, pre- and post-validation.
		 * Allow Android to re-instantiate the fragment view to update the UI
		 * based on fragment arguments.
		 */
		final FragmentTransaction ft = fragmentManager.beginTransaction();
		ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
				.replace(R.id.content, validateFragment, "validate")
				.commit();
	}
}
