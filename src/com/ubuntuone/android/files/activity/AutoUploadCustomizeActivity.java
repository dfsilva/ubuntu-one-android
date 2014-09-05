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

package com.ubuntuone.android.files.activity;

import android.content.ContentValues;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import com.ubuntuone.android.files.R;
import com.ubuntuone.android.files.provider.WatchedFoldersContract.WatchedFolders;

public class AutoUploadCustomizeActivity extends FragmentActivity
{
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_autoupload_customize);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		// Mark all folders as seen by the user.
		final ContentValues values = new ContentValues(1);
		values.put(WatchedFolders.IS_NEW, 0);
		getContentResolver().update(WatchedFolders.Images.CONTENT_URI, values,
				null, null);
	}
}
