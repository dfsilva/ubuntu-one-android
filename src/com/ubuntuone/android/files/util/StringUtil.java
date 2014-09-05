package com.ubuntuone.android.files.util;

import android.content.ContentResolver;

public class StringUtil {
	private static final int sSuffixLength = "://".length();
	
	private static final int sFileSchemeLength =
			ContentResolver.SCHEME_FILE.length();

	private static final int sContentSchemeLength =
			ContentResolver.SCHEME_CONTENT.length();
	
	public static String trimScheme(String uri) {
		if (uri.regionMatches(0, ContentResolver.SCHEME_FILE,
				0, sFileSchemeLength)) {
			return uri.substring(sFileSchemeLength + sSuffixLength);
		} else if (uri.regionMatches(0, ContentResolver.SCHEME_CONTENT,
				0, sContentSchemeLength)) {
			return uri.substring(sContentSchemeLength + sSuffixLength);
		}
		return uri;
	}
}
