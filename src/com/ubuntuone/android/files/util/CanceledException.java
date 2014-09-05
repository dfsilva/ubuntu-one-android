package com.ubuntuone.android.files.util;

public class CanceledException extends Exception {
	private static final long serialVersionUID = 5125833355810279098L;

	public CanceledException() {
		super();
	}

	public CanceledException(Throwable throwable) {
		super(throwable);
	}
}
