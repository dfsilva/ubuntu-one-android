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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.ubuntuone.android.files.UbuntuOneFiles;
import com.ubuntuone.android.files.activity.PreferencesActivity;
import com.ubuntuone.api.files.U1FileAPI;

/**
 * This class uses the same class name and has same, most important method
 * signatures as the android.util.Log class, so it can be easily deployed
 * just by changing the import. It also contains setup of loggers in related
 * libraries, such as libUbuntuOneFiles.
 */
public final class Log {
	private static String APP_TAG = UbuntuOneFiles.class.getSimpleName();
	
	private static int LOG_LEVEL = android.util.Log.DEBUG;
	
	private static final Logger logger;
	private static final Logger apiLogger;
	
	private static Handler logcatHandler;
	private static FileHandler fileHandler;
	
	static {
		logcatHandler = new LogcatHandler();
		
		logger = Logger.getLogger(UbuntuOneFiles.TAG);
		logger.setLevel(Level.INFO);
		logger.setUseParentHandlers(false);
		
		apiLogger = Logger.getLogger(U1FileAPI.class.getName());
		apiLogger.setLevel(Level.INFO);
		apiLogger.setParent(logger);
		apiLogger.setUseParentHandlers(false);
	}
	
	private Log() {
	}

	/**
	 * Method to set the log level.
	 * 
	 * @param logcatlevel
	 *            the log level to set
	 */
	public static void setLogLevel(int logcatlevel) {
		LOG_LEVEL = logcatlevel;
		switch (logcatlevel) {
		case android.util.Log.ERROR:
			logger.setLevel(Level.SEVERE);
			apiLogger.setLevel(Level.SEVERE);
			break;
		case android.util.Log.WARN:
			logger.setLevel(Level.WARNING);
			apiLogger.setLevel(Level.WARNING);
			break;
		case android.util.Log.INFO:
			logger.setLevel(Level.INFO);
			apiLogger.setLevel(Level.INFO);
			break;
		case android.util.Log.DEBUG:
			logger.setLevel(Level.FINE);
			apiLogger.setLevel(Level.FINE);
			break;
		case android.util.Log.VERBOSE:
			logger.setLevel(Level.FINEST);
			apiLogger.setLevel(Level.FINEST);
			break;
		default:
			break;
		}
	}
	
	public static boolean enableCollectingLogs() {
		try {
			File log = PreferencesActivity.getLogFile();
			int fileSizeLimit = 2 * 1024 * 1024;
			int fileCount = 1;
			fileHandler = new FileHandler(log.getPath(),
					fileSizeLimit, fileCount, false);
			if (fileHandler != null) {
				fileHandler.setFormatter(new LogFormatter());
				// Direct all messages to this file handler.
				logger.addHandler(fileHandler);
				// Forward API log messages to parent handler.
				apiLogger.setUseParentHandlers(true);
				// Set proper log level on all loggers.
				setLogLevel(android.util.Log.DEBUG);
				
				logger.addHandler(logcatHandler);
				logger.info("Enabled collecting logs.");
				return true;
			}
		} catch (IOException e) {
			Log.e("Log", e.getMessage());
			e.printStackTrace();
		}
		return false;
	}
	
	public static void disableCollectingLogs() {
		logger.info("Disabled collecting logs.");
		if (fileHandler != null) {
			logger.removeHandler(fileHandler);
			
			fileHandler.flush();
			fileHandler.close();
			
			apiLogger.setUseParentHandlers(false);
			// Revert to regular log level.
			setLogLevel(android.util.Log.INFO);
		}
		logger.removeHandler(logcatHandler);
	}
	
	public static void wtf(final String tag, final String msg) {
		final String logMessage = format(tag, msg);
		if (LOG_LEVEL <= android.util.Log.ASSERT)
			android.util.Log.e(APP_TAG, logMessage);
		logger.severe(logMessage);
	}
	
	public static void wtf(final String tag, final String msg, final Throwable tr) {
		final String logMessage = format(tag, msg, tr);
		if (LOG_LEVEL <= android.util.Log.ASSERT)
			android.util.Log.e(APP_TAG, logMessage);
		logger.severe(logMessage);
	}
		
	public static void e(final String tag, final String msg) {
		final String logMessage = format(tag, msg);
		if (LOG_LEVEL <= android.util.Log.ERROR)
			android.util.Log.e(APP_TAG, logMessage);
		logger.severe(logMessage);
	}
	
	public static void e(final String tag, final String msg, final Throwable tr) {
		final String logMessage = format(tag, msg, tr);
		if (LOG_LEVEL <= android.util.Log.ERROR)
			android.util.Log.e(APP_TAG, logMessage);
		logger.severe(logMessage);
	}

	public static void w(final String tag, final String msg) {
		final String logMessage = format(tag, msg);
		if (LOG_LEVEL <= android.util.Log.WARN)
			android.util.Log.w(APP_TAG, logMessage);
		logger.warning(logMessage);
	}
	
	public static void w(final String tag, final String msg, final Throwable tr) {
		final String logMessage = format(tag, msg, tr);
		if (LOG_LEVEL <= android.util.Log.WARN)
			android.util.Log.w(APP_TAG, logMessage);
		logger.warning(logMessage);
	}

	public static void i(final String tag, final String msg) {
		final String logMessage = format(tag, msg);
		if (LOG_LEVEL <= android.util.Log.INFO)
			android.util.Log.i(APP_TAG, logMessage);
		logger.info(logMessage);
	}
	
	public static void i(final String tag, final String msg, final Throwable tr) {
		final String logMessage = format(tag, msg, tr);
		if (LOG_LEVEL <= android.util.Log.INFO)
			android.util.Log.i(APP_TAG, logMessage);
		logger.info(logMessage);
	}

	public static void d(final String tag, final String msg) {
		final String logMessage = format(tag, msg);
		if (LOG_LEVEL <= android.util.Log.DEBUG)
			android.util.Log.d(APP_TAG, logMessage);
		logger.fine(logMessage);
	}
	
	public static void d(final String tag, final String msg, final Throwable tr) {
		final String logMessage = format(tag, msg, tr);
		if (LOG_LEVEL <= android.util.Log.DEBUG)
			android.util.Log.d(APP_TAG, logMessage);
		logger.fine(logMessage);
	}

	public static void v(final String tag, final String msg) {
		final String logMessage = format(tag, msg);
		if (LOG_LEVEL <= android.util.Log.VERBOSE)
			android.util.Log.v(APP_TAG, logMessage);
		logger.finer(logMessage);
	}
	
	public static void v(final String tag, final String msg, final Throwable tr) {
		final String logMessage = format(tag, msg, tr);
		if (LOG_LEVEL <= android.util.Log.VERBOSE)
			android.util.Log.v(APP_TAG, logMessage);
		logger.finer(logMessage);
	}
	
	/**
	 * Utility method to get the stack trace from a {@link Throwable}.
	 * 
	 * @param tr
	 *            the {@link Throwable} to get the stack trace of
	 * @return string representing stack trace
	 */
	public static String getStackTraceString(Throwable tr) {
		if (tr == null) {
			return "";
		}
		final StringWriter sw = new StringWriter();
		final PrintWriter pw = new PrintWriter(sw);
		tr.printStackTrace(pw);
		return sw.toString();
	}

	/**
	 * Utility method used to format tag and message.
	 * 
	 * @param tag
	 * @param msg
	 * @return formatted string containing tag and msg
	 */
	private static final String format(String tag, String msg) {
		return String.format("%s: %s", tag, msg);
	}
	
	/**
	 * Utility method used to format tag, message and {@link Throwable} stack trace.
	 * 
	 * @param tag
	 * @param msg
	 * @param tr
	 * @return formatted string containing tag, msg and tr stack trace
	 */
	private static final String format(String tag, String msg, Throwable tr) {
		return String.format("%s: %s\n%s", tag, msg, getStackTraceString(tr));
	}
	
	private static class LogFormatter extends Formatter {
		// format: <level>/<loggername> <sequence no.> <date-time> <message>
		@Override
		public String format(LogRecord r) {
			final String format = "%s/%s %d %s %s\r\n";
			final String date = DateFormat.getDateTimeInstance().format(new Date());
			
			return String.format(format,
					r.getLevel(),
					r.getLoggerName(),
					r.getSequenceNumber(),
					date,
					r.getMessage());
		}
	}
	
	private static class LogcatHandler extends Handler
	{
		@Override
		public void publish(LogRecord r) {
			final Level level = r.getLevel();
			final String name = r.getLoggerName();
			final String msg = r.getMessage();
			
			int intLevel = level.intValue();
			if (intLevel >= Level.SEVERE.intValue()) {
				android.util.Log.e(name, msg);
			} else if (intLevel == Level.WARNING.intValue()) {
				android.util.Log.w(name, msg);
			} else if (intLevel == Level.INFO.intValue()) {
				android.util.Log.i(name, msg);
			} else if (intLevel >= Level.FINE.intValue()) {
				// CONFIG and FINE logging Levels
				android.util.Log.d(name, msg);
			} else if (intLevel <= Level.FINER.intValue()) {
				android.util.Log.v(name, msg);
			}
		}
		
		@Override
		public void close() {
			// Nothing to do.
		}

		@Override
		public void flush() {
			// Nothing to do.
		}
	}
}