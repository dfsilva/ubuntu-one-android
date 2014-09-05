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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;

import com.ubuntuone.android.files.Preferences;
import com.ubuntuone.android.files.R;

public class ChangeLogUtils {
	
	public static void maybeShowChangelog(Context context) {
		final String savedVersion = Preferences.getSavedVersionName();
		final String currentVersion = Preferences.getCurrentVersionName(context);
		
		boolean hasUpgraded = !TextUtils.isEmpty(savedVersion);
		boolean hasVersionChanged = !currentVersion.equals(savedVersion);
		if (hasUpgraded && hasVersionChanged) {
			showChangelog(context);
		}
		Preferences.updateVersionName(context);
	}
	
	public static void showChangelog(Context context) {
		final String currentVersion = Preferences.getCurrentVersionName(context);
		final Dialog dialog = buildChangelogDialog(context, currentVersion);
		dialog.show();
	}
	
	private static Spanned readChangelog(Context context) {
		final InputStream in = context.getResources().openRawResource(R.raw.changelog);
		final BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		
		final StringBuilder builder = new StringBuilder(256);
		try {
			String line = null;
			while ((line = reader.readLine()) != null) {
				builder.append(line);
				builder.append("<br />");
			}
		} catch (IOException e) {
			// Ignore.
		} finally {
			try {
				reader.close();
			} catch (IOException e) {
				// Ignore.
			}
		}
		return Html.fromHtml(builder.toString());
	}
	
	private static AlertDialog buildChangelogDialog(Context context, String version) {
		final Spanned changelog = readChangelog(context);
		final String versionFormat = context.getString(R.string.new_version_fmt);
		
		final AlertDialog dialog = new AlertDialog.Builder(context)
				.setTitle(String.format(versionFormat, version))
				.setMessage(changelog)
				.setPositiveButton(R.string.ok, new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				})
				.create();
		return dialog;
	}
	
}
