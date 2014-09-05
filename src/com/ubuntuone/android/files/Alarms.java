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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.ubuntuone.android.files.service.MetaService;
import com.ubuntuone.android.files.service.UpDownService;
import com.ubuntuone.android.files.util.DateUtilities;
import com.ubuntuone.android.files.util.Log;

public final class Alarms {
	private static final String TAG = Alarms.class.getSimpleName();
	
	private static UbuntuOneFiles sAppInstance = UbuntuOneFiles.getInstance();
	
	private static long sLastRetryFailedInterval = 0;
	
	public static void resetRetryFailedInterval() {
		sLastRetryFailedInterval = 0;
	}
	
	public static PendingIntent getMediaUploadPendingIntent(
			final Context context, final int flags) {
		final Intent intent = new Intent(MetaService.ACTION_UPLOAD_MEDIA);
		return PendingIntent.getService(sAppInstance, 0, intent, flags);
	}
	
	/**
	 * Only {@link UpDownService} should call this method.
	 * 
	 * @return
	 */
	public static boolean maybeRegisterRetryFailedAlarm() {
		final boolean retryFailed = Preferences.getBoolean(
				Preferences.AUTO_RETRY_FAILED, false);
		if (retryFailed) {
			final long delay = DateUtilities.MILLIS_IN_MINUTE * 2;
			final long wakeInterval = getNextRetryFailedInterval();
			sLastRetryFailedInterval = wakeInterval; // Save it for laggy exp backoff.
			registerRetryFailedAlarm(delay, wakeInterval);
			return true;
		} else {
			Log.d(TAG, "not registering retry failed alarm because of settings");
			return false;
		}
	}
	
	private static void registerRetryFailedAlarm(
			final long delay, final long wakeInterval) {
		final Intent intent = new Intent(UpDownService.ACTION_RETRY);
		final PendingIntent operation = PendingIntent.getService(
				sAppInstance, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
		
		// Cancel current.
		final AlarmManager am = (AlarmManager) sAppInstance
				.getSystemService(UbuntuOneFiles.ALARM_SERVICE);
		am.cancel(operation);
		
		// Can retry every 15 minutes, but must at least once a wakeInterval.
		Log.i(TAG, "(re)registering retry-failed alarm, wake: " + wakeInterval);
		
		final int sleep = AlarmManager.RTC;
		final int wakeup = AlarmManager.RTC_WAKEUP;
		
		final long withSleepInterval = AlarmManager.INTERVAL_FIFTEEN_MINUTES;
		final long withWakeupInterval = wakeInterval;
		
		long triggerAtTime = System.currentTimeMillis() + delay;
		
		am.setInexactRepeating(sleep, triggerAtTime,
				withSleepInterval, operation);
		am.setInexactRepeating(wakeup, triggerAtTime,
				withWakeupInterval, operation);
	}
	
	private static long getNextRetryFailedInterval() {
		long interval = sLastRetryFailedInterval;
		switch ((int) sLastRetryFailedInterval) {
		case 0:
			interval = AlarmManager.INTERVAL_FIFTEEN_MINUTES;
			break;
		case (int) AlarmManager.INTERVAL_FIFTEEN_MINUTES:
			interval = AlarmManager.INTERVAL_HALF_HOUR;
			break;
		case (int) AlarmManager.INTERVAL_HALF_HOUR:
			interval = AlarmManager.INTERVAL_HOUR;
			break;
		default:
			interval = AlarmManager.INTERVAL_HALF_DAY;
			break;
		}
		return interval;
	}
	
	public static void unregisterRetryFailedAlarm() {
		resetRetryFailedInterval();
		
		final Intent intent = new Intent(UpDownService.ACTION_RETRY);
		final PendingIntent operation =
				PendingIntent.getService(sAppInstance, 0, intent, 0);
		if (operation != null) {
			final AlarmManager am = (AlarmManager) sAppInstance
					.getSystemService(UbuntuOneFiles.ALARM_SERVICE);
			am.cancel(operation);
			Log.i(TAG, "unregistered retry failed alarm");
		} else {
			Log.d(TAG, "no retry failed alarm to unregister");
		}
	}
	
	public static void onMediaMounted() {
		maybeRegisterRetryFailedAlarm();
	}
	
	private Alarms() {
	}

}
