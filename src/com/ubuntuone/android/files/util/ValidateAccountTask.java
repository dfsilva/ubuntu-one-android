package com.ubuntuone.android.files.util;

import java.io.IOException;
import java.lang.ref.WeakReference;

import oauth.signpost.signature.HmacSha1MessageSigner;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.text.TextUtils;
import android.util.Log;

import com.ubuntuone.android.files.Constants;
import com.ubuntuone.android.files.Preferences;
import com.ubuntuone.android.files.R;
import com.ubuntuone.android.files.UbuntuOneFiles;
import com.ubuntuone.android.files.authenticator.AuthenticatorResultListener;
import com.ubuntuone.android.files.fragment.ProgressDialogFragment;
import com.ubuntuone.api.sso.U1AuthAPI;
import com.ubuntuone.api.sso.authorizer.OAuthAuthorizer;
import com.ubuntuone.api.sso.exceptions.AccountException;
import com.ubuntuone.api.sso.exceptions.TimeDriftException;
import com.ubuntuone.api.sso.exceptions.U1PingException;
import com.ubuntuone.api.sso.model.AccountResponse;

/**
 * The {@link ValidateAccountTask} is responsible for:
 * - verifying if the account has been already validated
 * - in case the account is validated, ping U1 to get tokens
 * - in case the account is not validated, inform the user
 */
public class ValidateAccountTask extends AsyncTask<String, Void, Void> {
	private static final String TAG = ValidateAccountTask.class.getSimpleName();
	
	private Handler handler;
	
	private WeakReference<FragmentActivity> activity;
	/** Signal task result using this callback. */
	private ValidateAccountTaskCallback callback;
	/** {@link DialogFragment} containing a {@link ProgressDialog}. */
	private DialogFragment dialogFragment;
	/** Response to me() request containing account information. */
	private AccountResponse accountResponse;
	
	private boolean withDialog = true;
	
	private Exception exception;
	
	public ValidateAccountTask(FragmentActivity activity,
			ValidateAccountTaskCallback callback, boolean withDialog) {
		this.activity = new WeakReference<FragmentActivity>(activity);
		this.callback = callback;
		this.withDialog = withDialog;
	}
	
	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		// Post results back to calling thread.
		handler = new Handler();
		
		if (withDialog) {
			dialogFragment = ProgressDialogFragment.newInstance(
					-1, R.string.signing_in_to_u1);
			
			if (activity.get() != null) {
				final FragmentManager fragmentManager = activity
				.get().getSupportFragmentManager();
				dialogFragment.show(fragmentManager, "dialog");
			} else {
				cancel(true);
				exception = new CanceledException();
			}
		}
	}

	@Override
	protected Void doInBackground(String... params) {
		if (params.length < 1) {
			throw new RuntimeException("ValidateAccountTask requires " +
					"oauthData argument.");
		}
		
		final String oauthData = params[0];
		
		try {
			final U1AuthAPI api = new U1AuthAPI(
					UbuntuOneFiles.class.getPackage().getName(),
					UbuntuOneFiles.getApplicationVersion(),
					Constants.SSO_SCHEME, Constants.SSO_HOST,
					HttpClientProvider.getInstance(), 
					OAuthAuthorizer.getWithTokens(oauthData,
							new HmacSha1MessageSigner()));
			
			OAuthAuthorizer.syncTimeWithSSO(HttpClientProvider.getInstance());
			
			// Save the token in an account in AccountManager.
			if (activity.get() != null) {
				final Activity theActivity = activity.get();
				final AccountManager am = AccountManager.get(theActivity);
				final Account account = Preferences.getAccount(am);
				if (account == null) {
					throw new Exception("Account is missing from " +
							"AccountManager, please log in first.");
				}
				
				// Get general account information.
				accountResponse = api.me();
				
				if (TextUtils.isEmpty(accountResponse.getPreferredEmail())
						|| "null".equals(accountResponse.getPreferredEmail())) {
					am.setUserData(account, Constants.KEY_AUTHTOKEN_HINT, oauthData);
					Log.i(TAG, "Token saved temporarily. Account not yet validated.");
					throw new AccountNotValidatedException();
				}
				
				// UpDown has been fixed to ignore timestamps which are way off.
				// OAuthAuthorizer.syncTimeWithU1(HttpManager.getClient());
				
				int tries = 3;
				while (tries-- > 0) {
					if (isCancelled()) {
						callback.onCancel();
						return null;
					}
					try {
						api.pingUbuntuOne(account.name);
						exception = null;
						break;
					} catch (U1PingException e) {
						Throwable cause = e.getCause();
						if (cause != null)
							Log.w(TAG, "Ping U1 failed.", cause);
						exception = e;
						Log.w(TAG, "Failed to ping U1" +
								((tries > 0) ? ", retrying..." : ", giving up."));
					}
				}
				if (exception != null && exception instanceof U1PingException) {
					// Give up, we failed to copy tokens over to U1.
					throw new U1PingException();
				}
				
				// We are ready to use the token.
				am.setAuthToken(account, Constants.AUTH_TOKEN_TYPE, oauthData);
				am.setUserData(account, Constants.KEY_AUTHTOKEN_HINT, null);
				Preferences.updateSerializedOAuthToken(oauthData);
				
				Authorizer.getInstance(true);
				
				final Bundle result = new Bundle();
				result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
				result.putString(AccountManager.KEY_ACCOUNT_TYPE, Constants.ACCOUNT_TYPE);
				result.putString(AccountManager.KEY_AUTHTOKEN, oauthData);
				((AuthenticatorResultListener) activity.get())
						.setAccountAuthenticatorResult(result);
				Log.i(TAG, "Correctly set the auth token.");
			} else {
				throw new CanceledException();
			}
		} catch (AccountException e) {
			Log.e(TAG, "failed to get account info: " + e.getMessage());
			exception = e;
		} catch (final IOException e) {
			Log.e(TAG, "connectivity problems: " + e.getMessage());
			exception = e;
		} catch (U1PingException e) {
			Log.e(TAG, "U1 ping exception: " + e.getMessage());
			exception = e;
		} catch (AccountNotValidatedException e) {
			Log.w(TAG, "account not validated");
			exception = e;
		} catch (CanceledException e) {
			Log.e(TAG, "canceled exception");
			exception = e;
		} catch (TimeDriftException e) {
			Log.e(TAG, "time drift exception");
			exception = e;
		} catch (Exception e) {
			exception = e;
		}
		return null;
	}
	
	@Override
	protected void onPostExecute(Void result) {
		super.onPostExecute(result);
		// Fixed ValidateAccountTask.java:192 java.lang.IllegalStateException in 1.2.4
		if (isCancelled()) {
			return;
		}
		
		if (withDialog && dialogFragment.isAdded()) {
			dialogFragment.dismiss();
		}
		
		if (exception == null) {
			callback.onSuccess();
		} else {
			if (exception.getClass() == CanceledException.class)
				callback.onCancel();
			else
				callback.onFailure(exception);
		}
	}

	@Override
	protected void onCancelled() {
		super.onCancelled();
		if (withDialog && dialogFragment.isAdded()) {
			dialogFragment.dismiss();
		}
		
		handler.post(new Runnable() {
			@Override
			public void run() {
				callback.onCancel();
			}
		});
	}

	public interface ValidateAccountTaskCallback {
		public void onSuccess();
		
		public void onFailure(Exception e);
		
		public void onCancel();
	}
	
	public class AccountNotValidatedException extends Exception {
		private static final long serialVersionUID = 1286854508279137499L;

		public AccountNotValidatedException() {
			super();
		}

		public AccountNotValidatedException(String detailMessage) {
			super(detailMessage);
		}
	}
}
