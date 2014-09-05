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

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.ubuntuone.android.files.Constants;
import com.ubuntuone.android.files.R;
import com.ubuntuone.android.files.activity.LoginActivity;

public class Authenticator extends AbstractAccountAuthenticator {
	private static final String TAG = Authenticator.class.getSimpleName();

	private final Context context;

	public Authenticator(Context context) {
		super(context);
		this.context = context;
	}

	@Override
	public Bundle addAccount(AccountAuthenticatorResponse response,
			String accountType, String authTokenType,
			String[] requiredFeatures, Bundle options)
			throws NetworkErrorException {
		Log.d(TAG, "addAccount()");
		
		final Intent intent = new Intent(context, LoginActivity.class);
		intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE,
				response);
		final Bundle bundle = new Bundle();
		bundle.putParcelable(AccountManager.KEY_INTENT, intent);
		return bundle;
	}

	@Override
	public Bundle confirmCredentials(AccountAuthenticatorResponse response,
			Account account, Bundle options) throws NetworkErrorException {
		Log.d(TAG, "confirmCredentials()");
		throw new UnsupportedOperationException();
	}

	@Override
	public Bundle editProperties(AccountAuthenticatorResponse response,
			String accountType) {
		Log.d(TAG, "editProperties()");
		throw new UnsupportedOperationException();
	}

	@Override
	public Bundle getAuthToken(AccountAuthenticatorResponse response,
			Account account, String authTokenType, Bundle options)
			throws NetworkErrorException {
		final AccountManager am = AccountManager.get(context);
		final String authToken = am.peekAuthToken(account, authTokenType);
		if (authToken != null) {
			// Return the auth token.
			final Bundle result = new Bundle();
			result.putString(AccountManager.KEY_ACCOUNT_TYPE,
					Constants.ACCOUNT_TYPE);
			result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
			result.putString(AccountManager.KEY_AUTHTOKEN, authToken);
			return result;
		}
		
		final String authTokenHint =
				am.getUserData(account, Constants.KEY_AUTHTOKEN_HINT);

		// Either we have no token, or account has not been yet confirmed.
		final Intent intent = new Intent(context, LoginActivity.class);
		intent.putExtra(Constants.KEY_USERNAME, account.name);
		intent.putExtra(Constants.KEY_AUTHTOKEN_TYPE, authTokenType);
		intent.putExtra(Constants.KEY_AUTHTOKEN_HINT, authTokenHint);
		final Bundle bundle = new Bundle();
		bundle.putParcelable(AccountManager.KEY_INTENT, intent);
		return bundle;
	}

	@Override
	public String getAuthTokenLabel(String authTokenType) {
		if (authTokenType.equals(Constants.AUTH_TOKEN_TYPE)) {
			return context.getString(R.string.ubuntuone_token_label);
		} else {
			return authTokenType;
		}
	}

	@Override
	public Bundle hasFeatures(AccountAuthenticatorResponse response,
			Account account, String[] features) throws NetworkErrorException {
		final Bundle result = new Bundle();
		result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
		return result;
	}

	@Override
	public Bundle updateCredentials(AccountAuthenticatorResponse response,
			Account account, String authTokenType, Bundle options)
			throws NetworkErrorException {
		throw new UnsupportedOperationException();
	}
}
