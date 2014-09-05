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

import java.io.File;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Environment;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.ubuntuone.android.files.provider.FakeProvider;
import com.ubuntuone.android.files.provider.MetaContract.Nodes;
import com.ubuntuone.android.files.util.FileUtilities;
import com.ubuntuone.android.files.util.StorageInfo;
import com.ubuntuone.api.files.model.U1User;

/**
 * Stores preferences keys and provides utility methods for easier
 * {@link Preference} manipulation.
 */
public final class Preferences {
	
	public static final String DEFAULT_FOLDER = "u1";
	public static final String U1_RESOURCE = "/~/Ubuntu One";
	public static final String U1_PURCHASED_MUSIC = "/~/.ubuntuone/Purchased from Ubuntu One";
	
	public static final String VERSION_CODE_KEY = "version_code";
	public static final String VERSION_NAME_KEY = "version";
	
	public static final String FIRST_RUN_FLAG_KEY = "first_run";
	public static final String WELCOME_MSG_FLAG_KEY = "welcome_msg";
	
	public static final String FOLDER_NAME_KEY = "folder_name";
	
	public static final String USER_ID_KEY = "user_id";
	public static final String USERNAME_KEY = "username";
	public static final String PLAN_KEY = "plan";
	public static final String MAX_BYTES_KEY = "max_bytes";
	public static final String USED_BYTES_KEY = "used_bytes";
	
	public static final String PHOTO_UPLOAD_CONFIGURED_FLAG_KEY = "photo_upload_configured";
	public static final String PHOTO_UPLOAD_ENABLED_KEY = "upload_photos";
	public static final String PHOTO_UPLOAD_ONLY_ON_WIFI = "use_wifi_only";
	public static final String PHOTO_UPLOAD_ONLY_WHEN_CHARGING = "only_when_charging";
	public static final String PHOTO_UPLOAD_ALSO_WHEN_ROAMING = "also_when_roaming";
	public static final String PHOTO_UPLOAD_SHOW_NOTIFICATIONS = "show_auto_upload_notifications";
	public static final String PHOTO_UPLOAD_DIR_KEY = "upload_photos_dir";
	public static final String PHOTO_UPLOAD_INFO = "photo_upload_info";
	public static final String PHOTO_UPLOAD_LAST_TIMESTAMP_KEY = "last_photo_upload";
	
	public static final String AVOID_UPLOAD_DUPS_KEY = "avoid_upload_dups";
	public static final String AUTO_RETRY_FAILED = "auto_retry_failed";
	
	public static final String MANUAL_LIMITS_KEY = "manual_limits";
	public static final String TRANSFER_LIMITS_KEY = "transfer_limits";
	
	public static final String SORT_KEY = "list_sort";
	public static final String SHOW_HIDDEN_KEY = "show_hidden";
	
	public static final String STORAGE_LIMIT_KEY = "storage_limit";
	
	public static final String OOPS_FLAG_KEY = "oops";
	public static final String COLLECT_LOGS_KEY = "collect_logs";
	
	public static final String REQUEST_TOKEN_KEY = "request_token";
	public static final String REQUEST_SECRET_KEY = "request_secret";
	
	public static final String SECONDARY_STORAGE = "secondary_storage";
	
	public static final long DEFAULT_UPLOAD_FREQUENCY = 3600000L;
	
	private static SharedPreferences prefs;
	private static SharedPreferences.Editor editor;
	
	/**
	 * Reloads default preferences and prepares the {@link Editor} for
	 * {@link SharedPreferences} manipulation.
	 */
	public static void reload() {
		final Context context = UbuntuOneFiles.getInstance();
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		editor = prefs.edit();
	}
	
	public static SharedPreferences getSharedPrefs() {
		return prefs;
	}
	
	/**
	 * Creates a proper token name for registration and login.
	 * 
	 * @return An application token name.
	 */
	public static final String getApplicationTokenName() {
		final String model = Build.MODEL.replace('/', ' ');
		return String.format("%s @ %s", "Ubuntu One", model);
	}
	
	/**
	 * Returns an Ubuntu One account of type com.ubuntu, or null if the account
	 * does not exist.
	 * 
	 * @param am
	 * @return
	 */
	public static Account getAccount(AccountManager am) {
		final Account[] accounts = am.getAccountsByType(Constants.ACCOUNT_TYPE);
		return (accounts.length > 0) ? accounts[0] : null;
	}
	
	/**
	 * Adds an Ubuntu One account of type com.ubuntu
	 * 
	 * @param am
	 * @param username
	 * @return Ubuntu One account
	 */
	public static Account addAccount(AccountManager am, String username) {
		final Account account = new Account(username, Constants.ACCOUNT_TYPE);
		// This will return false if the account already exists.
		boolean accountAdded = am.addAccountExplicitly(account, null, null);
		if (accountAdded) {
			disableFakeSync(account, FakeProvider.AUTHORITY);
			disableFakeSync(account, FakeProvider.AUTHORITY2);
		}
		return accountAdded ? account : null;
	}

	private static void disableFakeSync(Account ubuntuAccount, String authority) {
		ContentResolver.cancelSync(ubuntuAccount, authority);
		ContentResolver.setSyncAutomatically(ubuntuAccount, authority, false);
		ContentResolver.setIsSyncable(ubuntuAccount, authority, 0);
	}

	/**
	 * Updates OAuth request token used to retrieve access token.
	 * 
	 * @param token
	 *            OAuth request token
	 * @param secret
	 *            OAuth request token secret
	 */
	public static void updateRequestToken(
			final String token, final String secret) {
		editor.putString(REQUEST_TOKEN_KEY, token);
		editor.putString(REQUEST_SECRET_KEY, secret);
		editor.commit();
	}

	/**
	 * Gets the stored request token.
	 * 
	 * @return OAuth request token, null if not present
	 */
	public static String getRequestToken() {
		return prefs.getString(REQUEST_TOKEN_KEY, null);
	}
	
	/**
	 * Gets the request token secret.
	 * 
	 * @return OAuth request token secret, null if not present
	 */
	public static String getRequestSecret() {
		return prefs.getString(REQUEST_SECRET_KEY, null);
	}
	
	public static void updateSerializedOAuthToken(final String serializedToken) {
		editor.putString(Constants.KEY_SERIALIZED_TOKEN, serializedToken);
		editor.commit();
	}
	
	public static String getSerializedOAuthToken() {
		return prefs.getString(Constants.KEY_SERIALIZED_TOKEN, null);
	}
	
	/**
	 * Account should be present and token possibly cached.
	 * 
	 * @return true, if account is present and token cached
	 */
	public static boolean hasTokens(Context context) {
		final Account ua = getUbuntuOneAccount(context);
		return ua != null && !TextUtils.isEmpty(getSerializedOAuthToken());
	}
	
	/**
	 * Looks up Ubuntu One {@link Account} in {@link AccountManager}.
	 * 
	 * @param context
	 *            the context to use
	 * @return Ubuntu One {@link Account} or null, if one hasn't been found
	 */
	public static Account getUbuntuOneAccount(Context context) {
		final AccountManager am = AccountManager.get(context);
		final Account[] accounts = am.getAccountsByType(Constants.ACCOUNT_TYPE);
		return (accounts.length > 0) ? accounts[0] : null;
	}
	
	/**
	 * Removes the token from preferences and invalidates {@link AccountManager}
	 * cache.
	 * 
	 * @param context
	 */
	public static void invalidateToken(Context context) {
		final AccountManager manager = AccountManager.get(context);
		
		Preferences.updateSerializedOAuthToken(null);
		final Account account = Preferences.getUbuntuOneAccount(context);
		final String authToken =
				manager.peekAuthToken(account, Constants.AUTH_TOKEN_TYPE);
		manager.invalidateAuthToken(Constants.ACCOUNT_TYPE, authToken);
	}
	
	public static void updateAccountInfo(U1User user) {
		editor.putLong(USER_ID_KEY, user.getUserId());
		editor.putString(USERNAME_KEY, user.getVisibleName());
		editor.putString(PLAN_KEY,
				FileUtilities.getHumanReadableSize(user.getMaxBytes()));
		editor.putLong(MAX_BYTES_KEY, user.getMaxBytes());
		editor.putLong(USED_BYTES_KEY, user.getUsedBytes());
		editor.commit();
	}

	/**
	 * Sets the flag with a value of 1 to {@link SharedPreferences}.
	 * 
	 * @param flag
	 *            the flag to set
	 */
	public static void set(final String flag) {
		editor.putString(flag, "1");
		editor.commit();
	}
	
	/**
	 * Clears the given flag.
	 * 
	 * @param flag
	 */
	public static void clear(final String flag) {
		editor.putString(flag, null);
		editor.commit();
	}
	
	public static boolean isSet(final String flag) {
		return prefs.getString(flag, null) != null;
	}
	
	/**
	 * Gets current application version code. Used for upgrading.
	 * 
	 * @param context
	 * @return application version code
	 */
	public static Integer getCurrentVersionCode(Context context) {
		final PackageManager pm = context.getPackageManager();
		Integer versionCode = Integer.valueOf(0);
		try {
			final PackageInfo info = pm.getPackageInfo(
					UbuntuOneFiles.class.getPackage().getName(), 0);
			versionCode = Integer.valueOf(info.versionCode);
		} catch (NameNotFoundException e) {
		}
		return versionCode;
	}
	
	/**
	 * Gets saved application version code.
	 * 
	 * @return saved application version code, 0 if none saved before
	 */
	public static Integer getSavedVersionCode() {
		return Integer.valueOf(prefs.getString(VERSION_CODE_KEY, "0"));
	}
	
	/**
	 * Saves current application version code.
	 * 
	 * @param context
	 */
	public static void updateVersionCode(Context context) {
		final Integer versionCode = getCurrentVersionCode(context);
		editor.putString(VERSION_CODE_KEY, String.valueOf(versionCode));
		editor.commit();
	}
	
	/**
	 * Gets current application version code. Used for notifying users about changes.
	 * 
	 * @param context
	 * @return application version name
	 */
	public static String getCurrentVersionName(Context context) {
		final PackageManager pm = context.getPackageManager();
		String versionName = "";
		try {
			final PackageInfo info = pm.getPackageInfo(
					UbuntuOneFiles.class.getPackage().getName(), 0);
			versionName = info.versionName;
		} catch (NameNotFoundException e) {
		}
		return versionName;
	}
	
	/**
	 * Gets saved application version name.
	 * 
	 * @return saved application version name, empty string if none saved before
	 */
	public static String getSavedVersionName() {
		return prefs.getString(VERSION_NAME_KEY, "");
	}
	
	/**
	 * Saves current application version code.
	 * 
	 * @param context
	 */
	public static void updateVersionName(Context context) {
		final String versionName = getCurrentVersionName(context);
		editor.putString(VERSION_NAME_KEY, versionName);
		editor.commit();
	}
	
	private static String sBaseDirectory;
	
	/**
	 * Gets the base directory to store files.
	 * 
	 * @return absolute path of directory to store files
	 */
	public static String getBaseDirectory() {
		if (sBaseDirectory == null) {
			sBaseDirectory = String.format("%s/%s",
					Environment.getExternalStorageDirectory().toString(),
					Preferences.getString(FOLDER_NAME_KEY, DEFAULT_FOLDER));
		}
		return sBaseDirectory;
	}
	
	public static boolean isPhotoUploadConfigured() {
		return prefs.getBoolean(PHOTO_UPLOAD_CONFIGURED_FLAG_KEY, false);
	}
	
	public static void setPhotoAutoUploadConfigured(boolean configured) {
		putBoolean(PHOTO_UPLOAD_CONFIGURED_FLAG_KEY, configured);
	}
	
	public static boolean isPhotoUploadEnabled() {
		return prefs.getBoolean(PHOTO_UPLOAD_ENABLED_KEY, false);
	}
	
	public static void setAutoUploadPhotos(boolean autoUpload) {
		putBoolean(PHOTO_UPLOAD_ENABLED_KEY, autoUpload);
	}
	
	public static void setAutoUploadOnlyOnWiFi(boolean wifiOnly) {
		putBoolean(PHOTO_UPLOAD_ONLY_ON_WIFI, wifiOnly);
	}
	public static boolean getAutoUploadOnlyOnWiFi() {
		return getBoolean(PHOTO_UPLOAD_ONLY_ON_WIFI, false);
	}
	
	public static void setAutoUploadOnlyWhenCharging(boolean whenCharging) {
		putBoolean(PHOTO_UPLOAD_ONLY_WHEN_CHARGING, whenCharging);
	}
	public static boolean getAutoUploadOnlyWhenCharging() {
		return getBoolean(PHOTO_UPLOAD_ONLY_WHEN_CHARGING, false);
	}
	
	public static void setAutoUploadAlsoWhenRoaming(boolean whenRoaming) {
		putBoolean(PHOTO_UPLOAD_ALSO_WHEN_ROAMING, whenRoaming);
	}
	public static boolean getAutoUploadAlsoWhenRoaming() {
		return getBoolean(PHOTO_UPLOAD_ALSO_WHEN_ROAMING, false);
	}
	
	public static boolean getAutoUploadShowNotifications() {
		return getBoolean(PHOTO_UPLOAD_SHOW_NOTIFICATIONS, false);
	}
	
	private static String getDefaultPhotoUploadDirectory() {
		final String model = Build.MODEL.replace('/', ' ');
		return String.format("Pictures - %s", model);
	}
	
	public static String getPhotoUploadDirectory() {
		return getString(PHOTO_UPLOAD_DIR_KEY,
				getDefaultPhotoUploadDirectory());
	}
	public static void setPhotoUploadDirectory(String directory) {
		putString(PHOTO_UPLOAD_DIR_KEY, directory);
	}
	
	public static long getLastPhotoUploadTimestamp() {
		return getLong(PHOTO_UPLOAD_LAST_TIMESTAMP_KEY, 0L);
	}
	
	public static void updateLastPhotoUploadTimestamp() {
		final long now = System.currentTimeMillis() / 1000;
		putLong(PHOTO_UPLOAD_LAST_TIMESTAMP_KEY, now);
	}
	
	public static String getPhotoUploadResourcePath() {
		return "/~/".concat(getPhotoUploadDirectory());
	}
	
	public static String getSharedFromAndroidResourcePath() {
		return U1_RESOURCE + "/Shared from Android";
	}
	
	public static void setLastPhotoUploadTimestamp(long timestampSeconds) {
		putLong(PHOTO_UPLOAD_LAST_TIMESTAMP_KEY, timestampSeconds);
	}
	
	public static boolean isCollectLogsEnabled() {
		return getBoolean(COLLECT_LOGS_KEY, false);
	}
	
	/**
	 * Gets the preferred file sort order, SQL order by clause.
	 * 
	 * @return the SQL order by clause criterion
	 */
	public static String getFilesSort() {
		return prefs.getString(SORT_KEY, Nodes.SORT_FOLDERS_FIRST);
	}

	/**
	 * Tells if the hidden file/directory/volume items should be visible to the
	 * user.
	 * 
	 * @return true if we should view hidden items, false otherwise
	 */
	public static boolean getShowHidden() {
		return prefs.getBoolean(SHOW_HIDDEN_KEY, true);
	}
	
	/**
	 * Gets the local storage size allowed by the user.
	 */
	public static long getLocalStorageLimit() {
		return prefs.getLong(STORAGE_LIMIT_KEY, -1L);
	}

	/**
	 * Sets the local storage limit allowed by the user.
	 * 
	 * @param limit
	 *            the storage limit to set
	 */
	public static void setLocalStorageLimit(long limit) {
		editor.putLong(STORAGE_LIMIT_KEY, limit);
		editor.commit();
	}
	
	
	public static boolean getIsManualLimits() {
		return getBoolean(MANUAL_LIMITS_KEY, false);
	}
	
	public static void setPhotoUploadEnabled(boolean enabled) {
		putBoolean(Preferences.PHOTO_UPLOAD_ENABLED_KEY, enabled);
	}
	
	public static File getSecondaryStorageDirectory() {
		String path = getString(SECONDARY_STORAGE, null);
		if (path == null) {
			path = StorageInfo.findSecondaryStorageDirectory();
			putString(SECONDARY_STORAGE, path);
		}
		return path != null ? new File(path) : null;
	}
	
	// TODO karni: Implement transfers bandwidth throttling via http
//	public static int getTransferLimits() {
//		return Integer.parseInt(getString(TRANSFER_LIMITS_KEY, "0"));
//	}
	
	public static String getString(final String key, final String def) {
		return prefs.getString(key, def);
	}
	
	public static void putString(final String key, final String value) {
		editor.putString(key, value);
		editor.commit();
	}
	
	public static Integer getInt(final String key, final int def) {
		return prefs.getInt(key, def);
	}
	
	public static void putInt(final String key, final int value) {
		editor.putInt(key, value);
		editor.commit();
	}
	
	public static long getLong(final String key, final long def) {
		return prefs.getLong(key, def);
	}
	
	public static long getLongFromString(final String key, final long def) {
		String value = prefs.getString(key, String.valueOf(def));
		return Long.valueOf(value);
	}
	
	public static void putLong(final String key, final long value) {
		editor.putLong(key, value);
		editor.commit();
	}
	
	public static boolean getBoolean(final String key, final boolean def) {
		return prefs.getBoolean(key, def);
	}
	
	public static void putBoolean(final String key, final boolean value) {
		editor.putBoolean(key, value);
		editor.commit();
	}
	
}
