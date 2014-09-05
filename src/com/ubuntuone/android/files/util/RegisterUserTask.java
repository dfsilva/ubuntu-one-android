package com.ubuntuone.android.files.util;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

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
import com.ubuntuone.api.sso.exceptions.RegistrationException;
import com.ubuntuone.api.sso.model.AccountResponse;
import com.ubuntuone.api.sso.model.AuthenticateResponse;
import com.ubuntuone.api.sso.model.ServerResponse;

public class RegisterUserTask extends AsyncTask<String, Void, Void> {
	private static final String TAG = RegisterUserTask.class.getSimpleName();
	
	private Handler handler;
	
	/** Signal registration result using a callback. */
	private RegisterUserTaskCallback callback;
	
	/** Activity {@link WeakReference} to show progress fragment. */
	private WeakReference<FragmentActivity> activity;
	
	/** {@link DialogFragment} containing a {@link ProgressDialog}. */
	private DialogFragment dialogFragment;
	
	private ServerResponse registerResponse;
	private AuthenticateResponse authenticateResponse;
	private AccountResponse accountResponse;
	
	public RegisterUserTask(FragmentActivity activity,
			RegisterUserTaskCallback callback) {
		this.activity = new WeakReference<FragmentActivity>(activity);
		this.callback = callback;
	}
	
	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		// Post results back to calling thread.
		handler = new Handler();
		
		dialogFragment = ProgressDialogFragment
				.newInstance(-1, R.string.signing_up_header);
		
		if (activity.get() != null) {
			final FragmentManager fragmentManager = activity
					.get().getSupportFragmentManager();
			dialogFragment.show(fragmentManager, "dialog");
		} else {
			cancel(true);
			handler.post(new Runnable() {
				@Override
				public void run() {
					callback.onRegisterUserCancel();
				}
			});
		}
	}

	@Override
	protected Void doInBackground(String... params) {
		if (params.length < 5) {
			throw new RuntimeException("RegisterUserTask requires " +
					"fullname, username, password, captchaId, captchaSolution");
		}
		
		final String fullname = params[0];
		final String username = params[1];
		final String password = params[2];
		final String captchaId = params[3];
		final String captchaSolution = params[4];
		
		try {
			final U1AuthAPI api = new U1AuthAPI(
					UbuntuOneFiles.class.getPackage().getName(),
					UbuntuOneFiles.getApplicationVersion(),
					Constants.SSO_SCHEME, Constants.SSO_HOST,
					HttpClientProvider.getInstance(),
					new BasicAuthorizer(username, password));
			final String redirectUri =
				"https://one.ubuntu.com/oauth/complete-registration/" +
				"?platform=mobile&return_to=" + Constants.U1_SIGNUP_COMPLETE_URL;
			
			registerResponse = api.register(username, password,
					captchaId, captchaSolution, fullname,
					U1AuthAPI.MOBILE, redirectUri);
			if (registerResponse.hasErrors()) {
				handler.post(new Runnable() {
					@Override
					public void run() {
						callback.onRegisterUserRegistrationException(
								registerResponse.getErrors());
					}
				});
				return null;
			}
			
			final String tokenName = Preferences.getApplicationTokenName();
			authenticateResponse = api.authenticate(tokenName);
			if (authenticateResponse.hasErrors()) {
				handler.post(new Runnable() {
					@Override
					public void run() {
						callback.onRegisterUserAuthenticationException(
								new Exception("authentication failed"));
					}
				});
				return null;
			}
			
			final AccountManager am = AccountManager.get(
					UbuntuOneFiles.getInstance());
			Account account = Preferences.getAccount(am);
			
			if (account == null) {
				account = Preferences.addAccount(am, username);
			}
			
			final String oauthData = authenticateResponse.getSerialized();
			api.setAuthorizer(OAuthAuthorizer.getWithTokens(oauthData,
							new PlainTextMessageSigner()));
			accountResponse = api.me();
			
			if (TextUtils.isEmpty(accountResponse.getPreferredEmail())
					|| "null".equals(accountResponse.getPreferredEmail())) {
				am.setUserData(account, Constants.KEY_AUTHTOKEN_HINT, oauthData);
				Log.i(TAG, "Token saved temporarily. Account not yet validated.");
				handler.post(new Runnable() {
					@Override
					public void run() {
						callback.onRegisterUserAccountNotValidated(oauthData);
					}
				});
			} else {
				am.setUserData(account, Constants.KEY_AUTHTOKEN_HINT, oauthData);
				handler.post(new Runnable() {
					@Override
					public void run() {
						callback.onRegisterUserSuccess(oauthData);
					}
				});
			}
		} catch (final RegistrationException e) {
			// This should not happen.
			handler.post(new Runnable() {
				@Override
				public void run() {
					callback.onRegisterUserGenericException(e);
				}
			});
		} catch (final AuthenticationException e) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					callback.onRegisterUserAuthenticationException(e);
				}
			});
		} catch (final AccountException e) {
			// This should not happen.
			handler.post(new Runnable() {
				@Override
				public void run() {
					callback.onRegisterUserGenericException(e);
				}
			});
		} catch (final IOException e) {
			Log.e(TAG, "connectivity problem: " + e.getMessage());
			handler.post(new Runnable() {
				@Override
				public void run() {
					callback.onRegisterUserIOException(
							new Exception("connectivity problem"));
				}
			});
		}
		return null;
	}

	@Override
	protected void onPostExecute(Void result) {
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
		callback.onRegisterUserCancel();
	}

	public interface RegisterUserTaskCallback {
		public void onRegisterUserSuccess(String oauthData);
		
		public void onRegisterUserAccountNotValidated(String oauthData);
		
		public void onRegisterUserCancel();
		
		public void onRegisterUserRegistrationException(ArrayList<ServerResponse.Error> errors);
		
		public void onRegisterUserAuthenticationException(Exception e);
		
		public void onRegisterUserIOException(Exception e);
		
		public void onRegisterUserGenericException(Exception e);
	}
}
