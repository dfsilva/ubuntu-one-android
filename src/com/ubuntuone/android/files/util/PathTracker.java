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

import java.util.Stack;


public final class PathTracker {
	private static final String TAG = PathTracker.class.getSimpleName();
	
	private Stack<String> mNodePath = null;
		
	public PathTracker() {
		mNodePath = new Stack<String>();
	}
	
	public String getNodePath() {
		return getCurrentNode();
	}
	
	public void setNodePath(Stack<String> nodePath) {
		mNodePath = nodePath;
	}
	
	public boolean isAtRoot() {
		return mNodePath.size() == 0;
	}
	
	/**
	 * @return cd ..
	 */
	public boolean cd() {
		if (mNodePath.size() > 0) {
			mNodePath.pop();
			return true;
		}
		return false;
	}
	
	public void cd(final String node) {
		mNodePath.push(node);
		Log.d(TAG, "Path is: " + getNodePath());
	}

	/**
	 * @return node of currently tracked path
	 */
	public String getCurrentNode() {
		return mNodePath.size() == 0 ? null : mNodePath.peek();
	}
}
