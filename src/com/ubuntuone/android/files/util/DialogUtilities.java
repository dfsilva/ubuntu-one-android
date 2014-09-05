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

package com.ubuntuone.android.files.util;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;

/**
 * Convenience utilities related to dialogs.
 * 
 * @author Michał Karnicki <mkarnicki@gmail.com>
 */
public class DialogUtilities {
	
	public static ProgressDialog buildProgressDialog(
			final Context ctx,
			final boolean indeterminate,
			final boolean cancelable,
			final String msg) {
		ProgressDialog pd = new ProgressDialog(ctx);
		pd.setIndeterminate(indeterminate);
		pd.setCancelable(cancelable);
		pd.setMessage(msg);
		return pd;
	}
	
	public static AlertDialog handshakeFailedDialog(final Context ctx) {
		AlertDialog alertDialog = new AlertDialog.Builder(ctx)
				.setTitle("Connect failed")
				.setMessage("Ubuntu One Files is having trouble connecting " +
						"to the servers. Please try again later.")
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setPositiveButton("Okey :(", new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				})
				.create();
		return alertDialog;
	}

}
