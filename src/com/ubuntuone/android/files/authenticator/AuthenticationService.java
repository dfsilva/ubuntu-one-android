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

package com.ubuntuone.android.files.authenticator;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class AuthenticationService extends Service {
	private static final String TAG = AuthenticationService.class.getSimpleName();
	private Authenticator mAuthenticator;

	@Override
	public void onCreate() {
		Log.v(TAG, "Started Ubuntu Single Sign On service.");
		mAuthenticator = new Authenticator(this);
	}

	@Override
	public void onDestroy() {
		Log.v(TAG, "Stopped Ubuntu Single Sign On service.");
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.v(TAG, "Returning Ubuntu Single Sign On binder for " + intent);
		return mAuthenticator.getIBinder();
	}
}
