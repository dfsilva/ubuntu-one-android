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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

public class AlertDialogFragment extends DialogFragment {
	private int title;
	private int message;
	private OnClickListener onPositiveClickListener;
	private OnClickListener onNegativeClickListener;
	
	public static AlertDialogFragment newInstance(
			int title, int message,
			OnClickListener onPositiveClickListener,
			OnClickListener onNegativeClickListener) {
		AlertDialogFragment f = new AlertDialogFragment(title, message,
				onPositiveClickListener, onNegativeClickListener);
		return f;
	}
	
	public AlertDialogFragment(int title, int message,
			OnClickListener onPositiveClickListener,
			OnClickListener onNegativeClickListener) {
		this.title = title;
		this.message = message;
		this.onPositiveClickListener = onPositiveClickListener;
		this.onNegativeClickListener = onNegativeClickListener;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final AlertDialog.Builder alert = new AlertDialog.Builder(getActivity())
        		.setIcon(android.R.drawable.ic_dialog_alert);
		if (title != -1)
			alert.setTitle(title);
		if (message != -1)
			alert.setMessage(message);
		if (onPositiveClickListener != null)
			alert.setPositiveButton(android.R.string.ok, onPositiveClickListener);
		if (onNegativeClickListener != null)
			alert.setNegativeButton(android.R.string.cancel, onNegativeClickListener);
		return alert.create();
	}
}
