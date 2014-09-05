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

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

import com.ubuntuone.android.files.Preferences;
import com.ubuntuone.android.files.R;
import com.ubuntuone.android.files.fragment.AutoUploadSetupFragment;
import com.ubuntuone.android.files.fragment.ProgressDialogFragment;
import com.ubuntuone.android.files.service.AutoUploadService;
import com.ubuntuone.android.files.util.MediaImportUtils;

public class AutoUploadSetupActivity extends FragmentActivity
		implements AutoUploadSetupFragment.Controller {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_autoupload_setup);
	}
	
	@Override
	public void onSetupSkipClicked() {
		finish();
	}

	@Override
	public void onSetupDoneClicked() {
		showProgressDialog();
		importSourcesAndFinish();
	}
	
	private void showProgressDialog() {
		FragmentManager fm = getSupportFragmentManager();
		FragmentTransaction ft = fm.beginTransaction();
		Fragment prev = fm.findFragmentByTag("dialog");
		if (prev != null) {
			ft.remove(prev);
		}
		ft.addToBackStack(null);
		
		DialogFragment newFragment = new ProgressDialogFragment();
		newFragment.show(ft, "dialog");
	}
	
	private void importSourcesAndFinish() {
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				MediaImportUtils.importImageBuckets(
						getApplicationContext());
				if (Preferences.isPhotoUploadEnabled()) {
					startService(new Intent(
							AutoUploadService.ACTION_RESCAN_IMAGES));
				}
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				super.onPostExecute(result);
				finish();
			}
		}.execute();
	}
}
