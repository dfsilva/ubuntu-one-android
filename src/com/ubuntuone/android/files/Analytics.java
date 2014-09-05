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

package com.ubuntuone.android.files;

@SuppressWarnings("unused")
public class Analytics {
	public static final String U1F_ACCOUNT = "UA-6230559-2";
	public static final String SSO_ACCOUNT = "UA-6230559-4";
	
	private interface Referrers {
		public String NONE = "none";
		
		public String GOOGLE = "Google"; // Google Android Market
		public String VODAFONE = "Vodafone"; // Vodafone Shop
		public String AMAZON = "Amazon"; // Amazon Appstore for Android
	}
	
	/*
	 * TODO karni: Replace this with pre-compile stage of
	 * - release-android
	 * - release-vodafone
	 * targets. Intentionally left NONE to avoid unintentionally sending one
	 * referrer to different market/appstore and messing up GA numbers.
	 * 
	 * Set this right before releasing the APK, do NOT commit.
	 */ 
	public static final String REFERRER = Referrers.NONE;
}
