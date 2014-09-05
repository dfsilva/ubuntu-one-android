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

import greendroid.widget.AsyncImageView;
import greendroid.widget.AsyncImageView.OnImageViewLoadListener;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;
import com.ubuntuone.android.files.Analytics;
import com.ubuntuone.android.files.Constants;
import com.ubuntuone.android.files.R;
import com.ubuntuone.android.files.UbuntuOneFiles;
import com.ubuntuone.android.files.activity.TutorialActivity;
import com.ubuntuone.android.files.util.HttpClientProvider;
import com.ubuntuone.android.files.util.RegisterUserTask;
import com.ubuntuone.android.files.util.RegisterUserTask.RegisterUserTaskCallback;
import com.ubuntuone.android.files.util.UIUtil;
import com.ubuntuone.android.files.widget.CheckBoxPlus;
import com.ubuntuone.android.files.widget.EditTextPlus;
import com.ubuntuone.android.files.widget.TextViewPlus;
import com.ubuntuone.api.sso.U1AuthAPI;
import com.ubuntuone.api.sso.model.CaptchaResponse;
import com.ubuntuone.api.sso.model.ServerResponse;

public class SignUpFragment extends Fragment implements
		RegisterUserTaskCallback, OnClickListener {
	private static final String TAG = SignUpFragment.class.getSimpleName();
	
	private SignUpFragmentCallback callback;

	private EditTextPlus fullnameEditText;
	private TextViewPlus emailErrorTextView;
	private EditTextPlus emailEditText;
	private TextViewPlus passwordErrorTextView;
	private EditTextPlus passwordEditText;
	private CheckBoxPlus passwordVisibleCheckBox;

	private View captchaLoading;
	private Button captchaFailed;
	private View captchaReady;
	
	private AsyncImageView captchaImageView;

	private TextViewPlus captchaErrorTextView;
	private EditText captchaEditText;
	
	private Button signupButton;
	
	private GoogleAnalyticsTracker mTracker;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		mTracker = GoogleAnalyticsTracker.getInstance();
		mTracker.start(Analytics.U1F_ACCOUNT, getActivity());
		
		callback = (SignUpFragmentCallback) getActivity();
		
		if (savedInstanceState == null) {
			startActivity(new Intent(getActivity(), TutorialActivity.class));
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		final View content = inflater.inflate(R.layout.fragment_sign_up,
				container, false);
		setupViews(content);
		return content;
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
		fullnameEditText = (EditTextPlus) content.findViewById(R.id.sso_fullname);
		fullnameEditText.requestFocus();
		
		emailErrorTextView = (TextViewPlus) content.findViewById(R.id.email_error);
		emailEditText = (EditTextPlus) content.findViewById(R.id.sso_username);
		
		passwordErrorTextView = (TextViewPlus) content.findViewById(R.id.password_error);
		passwordEditText = (EditTextPlus) content.findViewById(R.id.sso_password);
		
		passwordVisibleCheckBox = (CheckBoxPlus) content.findViewById(R.id.password_toggle);
		passwordVisibleCheckBox.setOnClickListener(this);
		
		captchaErrorTextView = (TextViewPlus) content.findViewById(R.id.captcha_error);
				
		signupButton = (Button) content.findViewById(R.id.sso_signup);
		signupButton.setOnClickListener(onButtonClickedListener);

		final TextView footer = (TextView) content.findViewById(R.id.signup_footer_tos);
		footer.setText(Html.fromHtml(getString(R.string.signup_footer_tos)));
		footer.setLinkTextColor(getResources().getColor(R.color.web_url));
		footer.setMovementMethod(LinkMovementMethod.getInstance());
		
		setupCaptchaImageView(content);
	}
	
	@Override
	public void onClick(View v) {
		if (v.getId() == R.id.password_toggle) {
			if (passwordVisibleCheckBox.isChecked()) {
				passwordEditText.setTransformationMethod(null);
			} else {
				passwordEditText.setTransformationMethod(
						new PasswordTransformationMethod());
			}
			passwordEditText.setSelection(passwordEditText.getText().length());
		}
	}

	private OnClickListener onButtonClickedListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.sso_signup:
				onSignUpButtonClicked();
				break;
			}
		}
	};
	
	private void setupCaptchaImageView(View content) {
		captchaLoading = content.findViewById(R.id.sso_captcha_loading);
		captchaFailed = (Button) content.findViewById(R.id.sso_captcha_failed);
		captchaReady = content.findViewById(R.id.sso_captcha_ready);
		captchaReady.setVisibility(View.GONE);

		captchaImageView = (AsyncImageView) content.findViewById(R.id.sso_captcha_image);
		captchaEditText = (EditText) content.findViewById(R.id.sso_captcha);
		
		captchaImageView.setOnImageViewLoadListener(new OnImageViewLoadListener() {
			
			@Override
			public void onLoadingStarted(AsyncImageView imageView) {
				onCaptchaLoadingStarted();
			}
			
			@Override
			public void onLoadingFailed(AsyncImageView imageView, Throwable throwable) {
				onCaptchaLoadingFailed(throwable);
			}
			
			@Override
			public void onLoadingEnded(AsyncImageView imageView, Bitmap image) {
				onCaptchaLoadingEnded();
			}
		});
		
		captchaImageView.setClickable(true);
		captchaImageView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				getNewCaptchaAsync();
			}
		});
		
		captchaFailed.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onCaptchaLoadingStarted();
				getNewCaptchaAsync();
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();
		getNewCaptchaAsync();
	}
	
	public void getNewCaptchaAsync() {
		new GetNewCaptchaTask(
				new WeakReference<AsyncImageView>(captchaImageView)).execute();
	}

	private void onCaptchaLoadingStarted() {
		captchaLoading.setVisibility(View.VISIBLE);
		captchaFailed.setVisibility(View.GONE);
		// Don't hide captchaReady, captcha should be focused.
	}

	private void onCaptchaLoadingFailed(Throwable t) {
		captchaLoading.setVisibility(View.GONE);
		captchaFailed.setVisibility(View.VISIBLE);
		captchaReady.setVisibility(View.GONE);
		signupButton.setEnabled(false);
	}

	private void onCaptchaLoadingEnded() {
		captchaLoading.setVisibility(View.GONE);
		captchaFailed.setVisibility(View.GONE);
		captchaReady.setVisibility(View.VISIBLE);
		signupButton.setEnabled(true);
	}
	
	private class GetNewCaptchaTask extends AsyncTask<Void, Void, CaptchaResponse> {
		private WeakReference<AsyncImageView> viewRef;
		
		private String errorMessage;
		
		public GetNewCaptchaTask(WeakReference<AsyncImageView> view) {
			this.viewRef = view;
		}
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			if (captchaEditText != null)
				captchaEditText.setText("");
		}

		@Override
		protected CaptchaResponse doInBackground(Void... params) {
			final U1AuthAPI api = new U1AuthAPI(
					UbuntuOneFiles.class.getPackage().getName(),
					UbuntuOneFiles.getApplicationVersion(),
					Constants.SSO_SCHEME, Constants.SSO_HOST,
					HttpClientProvider.getInstance(), null);
			
			try {
				return api.newCaptcha();
			} catch (Exception e) {
				return null;
			}
		}

		@Override
		protected void onPostExecute(CaptchaResponse result) {
			super.onPostExecute(result);
			final AsyncImageView view = this.viewRef.get();
			if (view != null && result != null) {
				captchaImageView.setTag(result.captchaId);
				captchaImageView.setUrl(result.imageUrl);
			} else {
				onCaptchaLoadingFailed(new Throwable(errorMessage));
			}
		}
	}
	
	private void onSignUpButtonClicked() {
		// Dismiss soft input method.
		getActivity().getWindow().setSoftInputMode(
				WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		
		final String fullname = fullnameEditText.getText().toString();
		final String username = emailEditText.getText().toString();
		final String password = passwordEditText.getText().toString();
		final String captchaId = (String) captchaImageView.getTag();
		final String captchaSolution = captchaEditText.getText().toString();
		
		registerUserAsync(fullname, username, password,
				captchaId, captchaSolution, this);
	}
	
	private void registerUserAsync(String fullname, String username,
			String password, String captchaId, String captchaSolution,
			RegisterUserTaskCallback callback) {
		emailErrorTextView.setVisibility(View.GONE);
		passwordErrorTextView.setTextColor(
				getResources().getColor(R.color.text_dark_grey));
		captchaErrorTextView.setVisibility(View.GONE);
		
		new RegisterUserTask(getActivity(), callback)
				.execute(fullname, username, password,
							captchaId, captchaSolution);
	}

	@Override
	public void onRegisterUserSuccess(String oauthData) {
		Log.d(TAG, "onRegisterUserSuccess()");
		
		mTracker.trackEvent("Referee", Analytics.REFERRER, "signup", 1);
		mTracker.dispatch();
		callback.onSignUpComplete();
	}
	
	@Override
	public void onRegisterUserCancel() {
		Log.d(TAG, "onRegisterUserCancel()");
		
		UIUtil.showToast(getActivity(), "Registration canceled.", true);
		getNewCaptchaAsync();
	}
	
	/**
	 * This method will soon go away, as we will not have to validate accounts.
	 */
	@Override
	@Deprecated
	public void onRegisterUserAccountNotValidated(String oauthData) {
		Log.d(TAG, "onRegisterUserAccountNotValidatedException()");
		
		mTracker.trackEvent("Referee", Analytics.REFERRER, "signup", 1);
		mTracker.dispatch();
		callback.onSignUpValidationRequired();
	}

	@Override
	public void onRegisterUserAuthenticationException(Exception e) {
		Log.d(TAG, "onRegisterUserAuthenticationException()");
		
		UIUtil.showToast(getActivity(), "Authentication error: " + e.getMessage(), true);
		getNewCaptchaAsync();
	}

	@Override
	public void onRegisterUserRegistrationException(ArrayList<ServerResponse.Error> errors) {
		Log.d(TAG, "onRegisterUserRegistrationException()");
		
		for (ServerResponse.Error error : errors) {
			String errorMessage = error.getDetails().get(0).toString();
			if ("email".equals(error.getReason())) {
				emailErrorTextView.setVisibility(View.VISIBLE);
				emailErrorTextView.setText(errorMessage);
				emailEditText.requestFocus();
			} else if ("password".equals(error.getReason())) {
				passwordErrorTextView.setTextColor(
						getResources().getColor(R.color.error));
				passwordEditText.requestFocus();
			} else if ("captcha_solution".equals(error.getReason())) {
				captchaErrorTextView.setVisibility(View.VISIBLE);
				captchaErrorTextView.setText(errorMessage);
				captchaEditText.requestFocus();
			} else if ("__all__".equals(error.getReason())) {
				if (errorMessage.equals("Wrong captcha solution.")) {
					// Yes, a hack. We want to show this next to *captcha* field!
					captchaErrorTextView.setVisibility(View.VISIBLE);
					captchaErrorTextView.setText("Wrong words, try again.");
					captchaEditText.requestFocus();
				} else {
					UIUtil.showToast(getActivity(), errorMessage, true);
				}
			}
		}
		getNewCaptchaAsync();
	}
	
	@Override
	public void onRegisterUserIOException(Exception e) {
		Log.d(TAG, "onRegisterUserIOException()");
		
		UIUtil.showToast(getActivity(), R.string.toast_no_network, true);
		getNewCaptchaAsync();
	}
	
	@Override
	public void onRegisterUserGenericException(Exception e) {
		Log.d(TAG, "onRegisterUserGenericException()");
		
		UIUtil.showToast(getActivity(), "Registration error: " + e.getMessage(), true);
		getNewCaptchaAsync();
	}
	
	public interface SignUpFragmentCallback {
		public void onSignUpValidationRequired();
		public void onSignUpComplete();
	}
}
