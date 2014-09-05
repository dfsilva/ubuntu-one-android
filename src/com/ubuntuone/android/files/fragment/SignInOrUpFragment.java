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

import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.ubuntuone.android.files.R;

public class SignInOrUpFragment extends Fragment implements OnClickListener {
	public static final int REQUEST_LOGIN = 1;

	private SignInOrUpFragmentCallback callback;
	
	private Button signInButton;
	private Button signUpButton;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		callback = (SignInOrUpFragmentCallback) getActivity();
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		final View content = inflater.inflate(
				R.layout.fragment_sign_in_or_up, container, false);
		
		signInButton = (Button) content.findViewById(R.id.sign_in);
		signUpButton = (Button) content.findViewById(R.id.new_account);
		
		final Typeface ubuntuB = Typeface.createFromAsset(
				getActivity().getAssets(), "Ubuntu-B.ttf");
		
		((TextView) content.findViewById(R.id.u1f)).setTypeface(ubuntuB);
		signInButton.setTypeface(ubuntuB);
		signUpButton.setTypeface(ubuntuB);
		
		signInButton.setOnClickListener(this);
		signUpButton.setOnClickListener(this);
		
		return content;
	}
	
	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.sign_in:
			callback.reqestedSignIn();
			break;
		case R.id.new_account:
			callback.requestedSignUp();
		default:
			break;
		}
	}
	
	public interface SignInOrUpFragmentCallback {
		public void reqestedSignIn();
		public void requestedSignUp();
	}
}
