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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/**
 * Convenience utilities related to browser.
 * 
 * @author Michał Karnicki <mkarnicki@gmail.com>
 */
public class BrowserUtilities {

	/**
	 * Opens browser with special flags for no history/recents/etc
	 * 
	 * @param ctx
	 *            context to use
	 * @param url
	 *            url to open
	 */
	public static void openNoHistory(Context ctx, String url) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setData(Uri.parse(url));
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
		intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
		intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
		ctx.startActivity(intent);
	}

	/**
	 * Simply opens browser with a regular intent.
	 * 
	 * @param ctx
	 *            context to use
	 * @param uri
	 *            url top open
	 */
	public static void open(final Context ctx, final String uri) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setData(Uri.parse(uri));
		ctx.startActivity(intent);
	}
	
}
