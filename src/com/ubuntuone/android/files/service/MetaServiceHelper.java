/*
 * Ubuntu One Files - access Ubuntu One cloud storage on Android platform.
 * 
 * Copyright 2011-2012 Canonical Ltd.
 *   
 * This file is part of Ubuntu One Files.
 *  
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General public static License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General public static License for more details.
 *  
 * You should have received a copy of the GNU Affero General public static License
 * along with this program.  If not, see http://www.gnu.org/licenses 
 */

package com.ubuntuone.android.files.service;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;

import com.ubuntuone.android.files.provider.MetaContract.Nodes;
import com.ubuntuone.api.files.model.U1NodeKind;

public final class MetaServiceHelper
{
	private MetaServiceHelper() {
	}
	
	public static void getUserInfo(Context context, ResultReceiver receiver) {
		Intent intent = new Intent(MetaService.ACTION_GET_USER);
		intent.putExtra(MetaService.EXTRA_CALLBACK, receiver);
		context.startService(intent);
	}
	
	public static void getVolume(Context context, String resourcePath,
			ResultReceiver receiver) {
		Intent intent = new Intent(MetaService.ACTION_GET_VOLUME);
		intent.putExtra(Nodes.NODE_RESOURCE_PATH, resourcePath);
		intent.putExtra(MetaService.EXTRA_CALLBACK, receiver);
		context.startService(intent);
	}
	
	public static void createVolume(Context context, String resourcePath,
			ResultReceiver receiver) {
		Intent intent = new Intent(MetaService.ACTION_CREATE_VOLUME);
		intent.putExtra(Nodes.NODE_RESOURCE_PATH, resourcePath);
		intent.putExtra(MetaService.EXTRA_CALLBACK, receiver);
		context.startService(intent);
	}
	
	public static void createDirectory(Context context, String resourcePath,
			String newDirName, ResultReceiver receiver) {
		Bundle extras = new Bundle();
		extras.putString(MetaService.EXTRA_KIND, U1NodeKind.DIRECTORY.toString());
		extras.putString(MetaService.EXTRA_NAME, newDirName);
		makeDirectory(context, resourcePath, extras, receiver);
	}
	
	public static void makeDirectory(Context context, String resourcePath,
			Bundle extras, ResultReceiver receiver) {
		Intent intent = new Intent(MetaService.ACTION_MAKE_DIRECTORY);
		intent.putExtra(Nodes.NODE_RESOURCE_PATH, resourcePath);
		intent.putExtras(extras);
		intent.putExtra(MetaService.EXTRA_CALLBACK, receiver);
		context.startService(intent);
	}
	
	public static void prefetchNode(Context context, String resourcePath,
			ResultReceiver receiver) {
		Intent intent = new Intent(MetaService.ACTION_GET_NODE);
		intent.putExtra(Nodes.NODE_RESOURCE_PATH, resourcePath);
		intent.putExtra(MetaService.EXTRA_CALLBACK, receiver);
		context.startService(intent);
	}
	
	public static void getNode(Context context, String resourcePath,
			ResultReceiver receiver) {
		Intent intent = new Intent(MetaService.ACTION_GET_NODE);
		intent.putExtra(Nodes.NODE_RESOURCE_PATH, resourcePath);
		intent.putExtra(MetaService.EXTRA_CALLBACK, receiver);
		context.startService(intent);
	}
	
	private static void updateNode(Context context, String resourcePath,
			Bundle extras, ResultReceiver receiver) {
		Intent intent = new Intent(MetaService.ACTION_UPDATE_NODE);
		intent.putExtra(Nodes.NODE_RESOURCE_PATH, resourcePath);
		intent.putExtras(extras);
		intent.putExtra(MetaService.EXTRA_CALLBACK, receiver);
		context.startService(intent);
	}
	
	public static void deleteNode(Context context, String resourcePath,
			ResultReceiver receiver) {
		Intent intent = new Intent(MetaService.ACTION_DELETE_NODE);
		intent.putExtra(Nodes.NODE_RESOURCE_PATH, resourcePath);
		intent.putExtra(MetaService.EXTRA_CALLBACK, receiver);
		context.startService(intent);
	}
	
	public static void rename(Context context, String resourcePath,
			String newPath, String newName, ResultReceiver receiver) {
		Bundle extras = new Bundle();
		extras.putString(Nodes.NODE_PATH, newPath);
		extras.putString(Nodes.NODE_NAME, newName);
		updateNode(context, resourcePath, extras, receiver);
	}
	
	public static void changePublicAccess(Context context, String resourcePath,
			boolean isPublic, ResultReceiver receiver) {
		Bundle extras = new Bundle();
		extras.putBoolean(Nodes.NODE_IS_PUBLIC, isPublic);
		updateNode(context, resourcePath, extras, receiver);
	}
}
