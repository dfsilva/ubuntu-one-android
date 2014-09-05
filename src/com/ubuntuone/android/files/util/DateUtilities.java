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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import android.content.Context;
import android.content.res.Resources;

import com.ubuntuone.android.files.R;

/**
 * Convenience utilities related to dates. 
 * 
 * @author Michał Karnicki <mkarnicki@gmail.com>
 */
public class DateUtilities {
	
	public static final Long MILLIS_IN_SECOND = 1000L;
	public static final Long MILLIS_IN_MINUTE = 60*MILLIS_IN_SECOND;
	public static final Long MILLIS_IN_15_MINUTES = 15*MILLIS_IN_MINUTE;
	public static final Long MILLIS_IN_HALF_HOUR = 2*MILLIS_IN_15_MINUTES;
	public static final Long MILLIS_IN_HOUR = 2*MILLIS_IN_HALF_HOUR;
	public static final Long MILLIS_IN_HALF_DAY = 12*MILLIS_IN_HOUR;
	public static final Long MILLIS_IN_DAY = 2*MILLIS_IN_HALF_DAY;
	public static final Long MILLIS_IN_MONTH = 30*MILLIS_IN_DAY;
	
	private static SimpleDateFormat sDateFormatter;
	private static TimeZone sTimeZone;
	private static Resources sResources;
	static {
		sDateFormatter = new SimpleDateFormat("yyyy-MM-dd");
		//sDateFormatter.setTimeZone(TimeZone.getTimeZone("GMT+02:00"));
		sTimeZone = TimeZone.getDefault();
	}
	
	/**
	 * Returns date from given @param timestamp in friendly format.
	 * 
	 * @param timestamp the dates timestamp
	 * @return friendly formatted date
	 */
	public static String getFriendlyDate(Context context, long timestamp) {
		if (timestamp < 0)
			return "";
		
		if (sResources == null) {
			sResources = context.getResources();
		}
		
		final long now = System.currentTimeMillis();
		final long offset = sTimeZone.getOffset(now);
		// utcNow == now - offset
		final long diff = now - offset - timestamp;
		
		if (diff < MILLIS_IN_MINUTE)
			return "just now";
		if (diff < MILLIS_IN_HOUR) {
			int minutes = (int) (diff / (double) MILLIS_IN_MINUTE);
			final String fmt = sResources.getQuantityString(
					R.plurals.last_modified_minutes, minutes, minutes);
			return String.format(fmt, minutes);
		}
		if (diff < MILLIS_IN_DAY) {
			int hours = (int) (diff / (double) MILLIS_IN_HOUR);
			final String fmt = sResources.getQuantityString(
					R.plurals.last_modified_hours, hours, hours);
			return String.format(fmt, hours);
		}
		if (diff < MILLIS_IN_MONTH) {
			int days = (int) (diff / (double) MILLIS_IN_DAY);
			final String fmt = sResources.getQuantityString(
					R.plurals.last_modified_days, days, days);
			return String.format(fmt, days);
		}
		return sDateFormatter.format(new Date(timestamp));
	}
	
}
