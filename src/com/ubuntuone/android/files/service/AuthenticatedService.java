/*
 * Ubuntu One Files - access Ubuntu One cloud storage on Android platform.
 * 
 * Copyright 2013 Canonical Ltd.
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

package com.ubuntuone.android.files.service;

import com.ubuntuone.android.files.Preferences;

import android.app.Service;
import android.content.Intent;

/**
 * A {@link Service}, which operates only when user is authenticated and
 * prevents unnecessary service calls when the user is not authenticated.
 */
public abstract class AuthenticatedService extends Service {
	@Override
	public void onCreate() {
		super.onCreate();
		if (Preferences.hasTokens(this)) {
			onCreateAuthenticated();
		} else {
			stopSelf();
			return;
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (Preferences.hasTokens(this)) {
			return onStartCommandAuthenticated(intent, flags, startId);
		} else {
			stopSelf();
			return START_NOT_STICKY;
		}
	}

	@Override
	public void onDestroy() {
		if (Preferences.hasTokens(this)) {
			onDestroyAuthenticated();
		}
		super.onDestroy();
	}

	public abstract void onCreateAuthenticated();

	public abstract void onDestroyAuthenticated();

	public abstract int onStartCommandAuthenticated(Intent intent, int flags, int startId);
}
