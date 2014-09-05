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

package com.ubuntuone.android.files;

import android.accounts.Account;

public class Constants {
	public static final String SSO_SCHEME = "https";
	public static final String SSO_HOST = "login.ubuntu.com";
	
	public static final String U1_METADATA_HOST = "one.ubuntu.com";
	public static final String U1_CONTENT_HOST = "files.one.ubuntu.com";
	
	public static final String ACCOUNT_TYPE = "com.ubuntu";
	public static final String AUTH_TOKEN_TYPE = "ubuntuone";
	public static final String APPLICATION = "Ubuntu One";
	
	/** Key detailing username, equal to {@link Account#name}. */
	public static final String KEY_USERNAME = "sso.username";
	
	/** Key detailing the auth token. */
	public static final String KEY_AUTH_TOKEN = "sso.authToken";
	
	/** Key detailing the auth token type; currently equal to {@link Constants#AUTH_TOKEN_TYPE_U1}. */
	public static final String KEY_AUTHTOKEN_TYPE = "sso.authTokenType";
	
	/** Until email has been validated, the token is kept as user data. */
	public static final String KEY_AUTHTOKEN_HINT = "sso.authTokenHint";
	
	/** Shared preference key where SSO stores app's OAuth access token. */
	public static final String KEY_ACCESS_TOKEN = "access_token";
	
	/** Shared preference key where SSO stores app's OAuth access secret. */
	public static final String KEY_ACCESS_SECRET = "access_secret";
	
	/** Shared preference key where SSO stores colon-delimited OAuth tokens. */
	public static final String KEY_SERIALIZED_TOKEN = "serialized_token";
	
	/** URL against which OAuth should be adjusted before use. */
	public static final String U1_HTP_URL = "http://one.ubuntu.com/api/time";
	
	public static final String U1_SIGNUP_URL = "https://one.ubuntu.com/services/plan/subscribe_basic/";
	
	public static final String U1_SIGNUP_SCHEME = "x-ubuntuone-sso";
	
	public static final String U1_SIGNUP_COMPLETE_URL = U1_SIGNUP_SCHEME + "://signup/complete";
}
