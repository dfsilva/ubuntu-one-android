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

import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.ubuntuone.android.files.R;

public class ProgressDialogFragment extends DialogFragment {
	private int title = -1;
	private int message = R.string.please_wait;
	
	public static ProgressDialogFragment newInstance(int title, int message) {
		return new ProgressDialogFragment(title, message);
	}
	
	public ProgressDialogFragment() {
		// Required no-args constructor.
	}
	
	public ProgressDialogFragment(int title, int message) {
		this.title = title;
		this.message = message;
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final ProgressDialog dialog = new ProgressDialog(getActivity());
		if (savedInstanceState != null) {
			title = savedInstanceState.getInt("title", -1);
			message = savedInstanceState.getInt("message", -1);
		}
		if (title != -1)
			dialog.setTitle(title);
		if (message != -1)
			dialog.setMessage(getString(message));
		setCancelable(false);
		return dialog;
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		state.putInt("title", title);
		state.putInt("message", message);
	}
}
