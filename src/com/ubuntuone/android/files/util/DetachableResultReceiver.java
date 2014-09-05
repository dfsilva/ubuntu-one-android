package com.ubuntuone.android.files.util;

import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

public class DetachableResultReceiver extends ResultReceiver {

	private Receiver mReceiver;

	public DetachableResultReceiver(Handler handler) {
		super(handler);
	}

	public void setReceiver(Receiver receiver) {
		mReceiver = receiver;
	}
	
	public void detach() {
		mReceiver = null;
	}

	public static interface Receiver {
		public void onReceiveResult(int resultCode, Bundle resultData);
	}

	@Override
	protected void onReceiveResult(int resultCode, Bundle resultData) {
		if (mReceiver != null) {
			mReceiver.onReceiveResult(resultCode, resultData);
		}
	}

}
