package com.ubuntuone.android.files.util;

import java.io.IOException;
import java.lang.ref.WeakReference;

import oauth.signpost.signature.PlainTextMessageSigner;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ProgressDialog;
import android.os.AsyncTask;
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
import com.ubuntuone.android.files.fragment.ProgressDialogFragment;
import com.ubuntuone.api.sso.U1AuthAPI;
import com.ubuntuone.api.sso.authorizer.BasicAuthorizer;
import com.ubuntuone.api.sso.authorizer.OAuthAuthorizer;
import com.ubuntuone.api.sso.exceptions.AccountException;
import com.ubuntuone.api.sso.exceptions.AuthenticationException;
import com.ubuntuone.api.sso.exceptions.TimeDriftException;
import com.ubuntuone.api.sso.model.AccountResponse;
import com.ubuntuone.api.sso.model.AuthenticateResponse;

/**
 * Attempts to authenticate the user. Takes username and password as
 * parameters. In case of success, the result is a serialized oauth token
 * (consumer_key:consumer_secret:token_key:token_secret), which may have not yet
 * been validated. For that, use ValidateTokenTask.
 * 
 * If authentication has succeeded, an account of type com.ubuntu has been
 * created in {@link AccountManager}. In case account not yet validated,
 * the token is stored in user data with "authTokenHint" key.
 * This is to prevent usage of a token for non-validated account.
 */
public class AuthenticateUserTask extends AsyncTask<String, Void, String> {
	private static final String TAG = AuthenticateUserTask.class.getSimpleName();
	
	private Handler handler;
	
	/** Signal authentication result using a callback. */
	private AuthenticateUserTaskCallback callback;
	
	/** Activity {@link WeakReference} to show progress fragment. */
	private WeakReference<FragmentActivity> activity;
	
	/** {@link DialogFragment} containing a {@link ProgressDialog}. */
	private DialogFragment dialogFragment;

	private AuthenticateResponse authenticateResponse;
	private AccountResponse accountResponse;
	
	public AuthenticateUserTask(FragmentActivity activity,
			AuthenticateUserTaskCallback callback) {
		this.activity = new WeakReference<FragmentActivity>(activity);
		this.callback = callback;
	}
	
	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		// Post results back to calling thread.
		handler = new Handler();
		
		dialogFragment = ProgressDialogFragment
				.newInstance(-1, R.string.signing_in);
		
		if (activity.get() != null) {
			final FragmentManager fragmentManager = activity
					.get().getSupportFragmentManager();
			dialogFragment.show(fragmentManager, "dialog");
		} else {
			cancel(true);
			handler.post(new Runnable() {
				@Override
				public void run() {
					callback.onAuthenticateUserCancel();
				}
			});
		}
	}

	@Override
	protected String doInBackground(String... params) {
		if (params.length < 2) {
			throw new RuntimeException("AuthenticateUserTask requires " +
					"username and password arguments.");
		}
		
		final String username = params[0];
		final String password = params[1];
		
		try {
			final U1AuthAPI api = new U1AuthAPI(
					UbuntuOneFiles.class.getPackage().getName(),
					UbuntuOneFiles.getApplicationVersion(),
					Constants.SSO_SCHEME, Constants.SSO_HOST,
					HttpClientProvider.getInstance(),
					new BasicAuthorizer(username, password));
			
			final String tokenName = Preferences.getApplicationTokenName();
			authenticateResponse = api.authenticate(tokenName);
			if (authenticateResponse.hasErrors()) {
				handler.post(new Runnable() {
					@Override
					public void run() {
						callback.onAuthenticateUserAuthenticationException(
								new Exception("authentication failed"));
					}
				});
				return null;
			}
			
			final AccountManager am = AccountManager.get(
					UbuntuOneFiles.getInstance());
			Account account = Preferences.getAccount(am);
			assert account == null;
			account = Preferences.addAccount(am, username);				
			
			final String oauthData = authenticateResponse.getSerialized();
			api.setAuthorizer(OAuthAuthorizer.getWithTokens(oauthData,
							new PlainTextMessageSigner()));
			OAuthAuthorizer.syncTimeWithSSO(HttpClientProvider.getInstance());
			accountResponse = api.me();
			
			final String prefMail = accountResponse.getPreferredEmail();
			if (TextUtils.isEmpty(prefMail) || 
					(prefMail != null && prefMail.equals("null"))) {
				am.setUserData(account, Constants.KEY_AUTHTOKEN_HINT, oauthData);
				Log.i(TAG, "Token saved temporarily. Account not yet validated.");
				handler.post(new Runnable() {
					@Override
					public void run() {
						callback.onAuthenticateUserAccountNotValidated(oauthData);
					}
				});
				return null;
			}
			
			handler.post(new Runnable() {
				@Override
				public void run() {
					callback.onAuthenticateUserSuccess(oauthData);
				}
			});
		} catch (final AuthenticationException e) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					callback.onAuthenticateUserAuthenticationException(e);
				}
			});
		} catch (final AccountException e) {
			// This should not happen.
			handler.post(new Runnable() {
				@Override
				public void run() {
					callback.onAuthenticateUserGenericException(e);
				}
			});
		} catch (final IOException e) {
			Log.e(TAG, "connectivity problem: " + e.getMessage());
			handler.post(new Runnable() {
				@Override
				public void run() {
					callback.onAuthenticateUserIOException(
							new Exception("Fix your device's date and time."));
				}
			});
		} catch (TimeDriftException e) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					Exception e = new Exception("Could not sync time with Ubuntu One. Please try again.");
					callback.onAuthenticateUserGenericException(e);
				}
			});
		}
		return null;
	}

	@Override
	protected void onPostExecute(String result) {
		super.onPostExecute(result);
		if (dialogFragment.isAdded()) {
			dialogFragment.dismiss();
		}
	}
	
	@Override
	protected void onCancelled() {
		super.onCancelled();
		if (dialogFragment.isAdded()) {
			dialogFragment.dismiss();
		}
		
		handler.post(new Runnable() {
			@Override
			public void run() {
				callback.onAuthenticateUserCancel();
			}
		});
	}

	public interface AuthenticateUserTaskCallback {
		public void onAuthenticateUserSuccess(String oauthData);
		
		public void onAuthenticateUserAccountNotValidated(String oauthData);
		
		public void onAuthenticateUserCancel();
		
		public void onAuthenticateUserAuthenticationException(Exception e);
		
		public void onAuthenticateUserIOException(Exception e);
		
		public void onAuthenticateUserGenericException(Exception e);
	}
}
