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

import java.io.IOException;
import java.io.InputStream;


public abstract class ThrottlingInputStream extends InputStream {

	private static final String TAG = "ThrIS";
	
	private int inputSpeed = 1024; // bytes per second
	
	private int bytesRead = 0;
	
	private int bytesReadTotal = 0;
	
	public ThrottlingInputStream(byte[] buf, int inputSpeed) {
		super();
		this.inputSpeed = inputSpeed;
	}
	
	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, inputSpeed);
	}

	@Override
	public int read(byte[] b, int offset, int length) throws IOException {
		// Check if we should wait before sending next msg fragment.
		if (bytesRead > inputSpeed) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			bytesRead = 0;
			Log.d(TAG, "zeroing bytes read");
		}
		
		// Select proper msg size.
		int size = inputSpeed;
		int read = 0;
		if (inputSpeed > length) {
			size = length;
		}
		
		// Read it and adjust the bytes read numbers.
		read = super.read(b, offset, size);
		bytesRead += read;
		bytesReadTotal += read;
		Log.d(TAG, String.format("read %d bytes", read));
			
		return read;
	}
	
}
