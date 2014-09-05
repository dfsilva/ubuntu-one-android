/*
 * Ubuntu One Files - access Ubuntu One cloud storage on Android platform.
 * 
 * Copyright 2011-2013 Canonical Ltd.
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

package com.ubuntuone.android.files.service;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ResultReceiver;

import com.squareup.otto.Bus;
import com.squareup.otto.Produce;
import com.ubuntuone.android.files.Constants;
import com.ubuntuone.android.files.Preferences;
import com.ubuntuone.android.files.UbuntuOneFiles;
import com.ubuntuone.android.files.event.AuthStateEvent;
import com.ubuntuone.android.files.event.SyncStateEvent;
import com.ubuntuone.android.files.provider.MetaContract;
import com.ubuntuone.android.files.provider.MetaContract.Nodes;
import com.ubuntuone.android.files.provider.MetaContract.ResourceState;
import com.ubuntuone.android.files.provider.MetaContract.Volumes;
import com.ubuntuone.android.files.provider.MetaUtilities;
import com.ubuntuone.android.files.util.Authorizer;
import com.ubuntuone.android.files.util.AwakeIntentService;
import com.ubuntuone.android.files.util.FileUtilities;
import com.ubuntuone.android.files.util.HttpClientProvider;
import com.ubuntuone.android.files.util.Log;
import com.ubuntuone.android.files.util.NetworkUtil;
import com.ubuntuone.api.files.U1FileAPI;
import com.ubuntuone.api.files.model.U1File;
import com.ubuntuone.api.files.model.U1Node;
import com.ubuntuone.api.files.model.U1NodeKind;
import com.ubuntuone.api.files.model.U1User;
import com.ubuntuone.api.files.model.U1Volume;
import com.ubuntuone.api.files.request.U1NodeListener;
import com.ubuntuone.api.files.request.U1UserListener;
import com.ubuntuone.api.files.request.U1VolumeListener;
import com.ubuntuone.api.files.util.U1Failure;
import com.ubuntuone.api.sso.authorizer.OAuthAuthorizer;

public class MetaService extends AwakeIntentService
{
	private static final String TAG = MetaService.class.getSimpleName();
	private static final String BASE = "com.ubuntuone.android.files";
	
	public static final String ACTION_GET_USER = BASE + ".ACTION_GET_USER";
	public static final String ACTION_GET_VOLUME = BASE + ".ACTION_GET_VOLUME";
	public static final String ACTION_CREATE_VOLUME = BASE + ".ACTION_CREATE_VOLUME";
	
	public static final String ACTION_MAKE_DIRECTORY = BASE + ".ACTION_MAKE_DIRECTORY";
	public static final String ACTION_GET_NODE = BASE + ".ACTION_GET_NODE";
	public static final String ACTION_UPDATE_NODE = BASE + ".ACTION_UPDATE_NODE";
	public static final String ACTION_DELETE_NODE = BASE + ".ACTION_DELETE_NODE";
	
	/** Auto-upload media request. */
	public static final String ACTION_UPLOAD_MEDIA = BASE + ".ACTION_UPLOAD_MEDIA";
	
	public static final String EXTRA_CALLBACK = "extra_callback";
	public static final String EXTRA_TIMESTAMP = "extra_timestamp";
	public static final String EXTRA_ERROR = "extra_error";
	public static final String EXTRA_RESOURCE_PATH = Nodes.NODE_RESOURCE_PATH;
	public static final String EXTRA_ID = Nodes._ID;
	public static final String EXTRA_PATH = Nodes.NODE_PATH;
	public static final String EXTRA_KIND = Nodes.NODE_KIND;
	public static final String EXTRA_NAME = Nodes.NODE_NAME;
	public static final String EXTRA_SIZE = Nodes.NODE_SIZE;
	public static final String EXTRA_HAS_CHILDREN = Nodes.NODE_HAS_CHILDREN;
	
	public static interface Status {
		public final int PENDING = 1;
		public final int RUNNING = 2;
		public final int PROGRESS = 3;
		public final int FINISHED = 4;
		public final int ERROR = 5;
	}
	
	private Bus mBus;
	private SyncStateEvent mSyncStateEvent = new SyncStateEvent();
	private AuthStateEvent mAuthStateEvent = new AuthStateEvent();
	
	private ContentResolver contentResolver;
	
	private HttpClient httpClient;
	private OAuthAuthorizer authorizer;
	private U1FileAPI api;
	
	public MetaService() {
		super(MetaService.class.getSimpleName());
		mBus = UbuntuOneFiles.getBus();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Produce public SyncStateEvent lastSyncStateEvent() {
		return mSyncStateEvent;
	}
	
	private void sendSyncState(boolean isRunning) {
		mSyncStateEvent.setIsRunning(isRunning);
		mBus.post(mSyncStateEvent);
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		Log.d(TAG, "onCreate()");
		mBus.register(this);
		
		contentResolver = getContentResolver();
		httpClient = HttpClientProvider.getInstance();
		authorizer = Authorizer.getInstance(false);
		
		api = new U1FileAPI(UbuntuOneFiles.class.getPackage().getName(),
				Preferences.getSavedVersionName(),
				Constants.U1_METADATA_HOST, Constants.U1_CONTENT_HOST,
				httpClient, authorizer);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		sendSyncState(true);
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		
		final String action = intent.getAction();
		final String resourcePath =
				intent.getStringExtra(EXTRA_RESOURCE_PATH);
		final ResultReceiver receiver =
				intent.getParcelableExtra(EXTRA_CALLBACK);
		
		if (ACTION_GET_USER.equals(action)) {
			getUser(receiver);
			getVolumes(receiver);
		} else if (ACTION_GET_VOLUME.equals(action)) {
			getVolume(resourcePath, receiver);
		} else if (ACTION_CREATE_VOLUME.equals(action)) {
			createVolume(resourcePath, receiver);
		} else if (ACTION_MAKE_DIRECTORY.equals(action)) {
			makeDirectory(resourcePath, receiver);
		} else if (ACTION_GET_NODE.equals(action)) {
			getNode(resourcePath, receiver, true);
		} else if (ACTION_UPDATE_NODE.equals(action)) {
			if (intent.hasExtra(Nodes.NODE_NAME)) {
				String newPath = intent.getStringExtra(EXTRA_PATH);
				updateNode(resourcePath, newPath, receiver);
			}
			if (intent.hasExtra(Nodes.NODE_IS_PUBLIC)) {
				Boolean isPublic = intent.getBooleanExtra(
						Nodes.NODE_IS_PUBLIC, false);
				updateNode(resourcePath, isPublic, receiver);
			}
		} else if (ACTION_DELETE_NODE.equals(action)) {
			deleteNode(resourcePath, receiver);
		}
		sendSyncState(false);
	}
	
	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy()");
		mBus.unregister(this);
		super.onDestroy();
	}
	
	public void onUbuntuOneFailure(U1Failure failure, ResultReceiver receiver) {
		Log.d(TAG, "onUbuntuOneFailure: " + failure.toString() +
				", status code: " + failure.getStatusCode());
		final Bundle resultData = new Bundle();
		resultData.putString(EXTRA_ERROR, failure.getMessage());
		if (receiver != null) {
			receiver.send(Status.ERROR, resultData);
		}
	}
	
	public void onFailure(U1Failure failure, ResultReceiver receiver) {
		int statusCode = failure.getStatusCode();
		Log.e(TAG, "onFailure: " + failure.toString() + ", HTTP " + statusCode);
		Bundle data;
		switch (statusCode) {
		case HttpStatus.SC_UNAUTHORIZED:
			Log.w(TAG, "Received HTTP Unauthorized response.");
			Context context = UbuntuOneFiles.getInstance().getApplicationContext();
			Preferences.invalidateToken(context);
			UbuntuOneFiles.getInstance().setLastAuthStateEvent(new AuthStateEvent(false));
			
			mAuthStateEvent.setIsAuthenticated(false);
			mBus.post(mAuthStateEvent);
			
			stopSelf();
			break;
		case HttpStatus.SC_NOT_FOUND:
			data = new Bundle();
			data.putString(EXTRA_ERROR, "Resource not found.");
			receiver.send(Status.ERROR, data);
			break;
		case HttpStatus.SC_INTERNAL_SERVER_ERROR:
			data = new Bundle();
			data.putString(EXTRA_ERROR, "Server error. Please try again later.");
			receiver.send(Status.ERROR, data);
			break;

		default:
			// TODO GA?
			break;
		}
	}

	private void getUser(final ResultReceiver receiver) {
		api.getUser(new U1UserListener() {
			@Override
			public void onStart() {
				if (receiver != null)
					receiver.send(Status.RUNNING, Bundle.EMPTY);
			}

			@Override
			public void onSuccess(U1User user) {
				onGetUserSuccess(user);
			}
			
			@Override
			public void onUbuntuOneFailure(U1Failure failure) {
				MetaService.this.onUbuntuOneFailure(failure, receiver);
			}
			
			@Override
			public void onFailure(U1Failure failure) {
				MetaService.this.onFailure(failure, receiver);
			}

			@Override
			public void onFinish() {
				if (receiver != null)
					receiver.send(Status.FINISHED, Bundle.EMPTY);
			}
		});
	}
	
	public void onGetUserSuccess(U1User user) {
		Preferences.updateAccountInfo(user);
	}
	
	public void getVolumes(final ResultReceiver receiver) {
		// Cached volume node paths.
		final Set<String> cachedNodePaths = MetaUtilities.getUserNodePaths();
		// Fresh volume node paths.
		final List<String> volumeNodePaths = new LinkedList<String>();
		
		api.getVolumes(new U1VolumeListener() {
			@Override
			public void onStart() {
				if (receiver != null)
					receiver.send(Status.RUNNING, Bundle.EMPTY);
			}

			@Override
			public void onSuccess(U1Volume volume) {
				onGetVolumeSuccess(volume, receiver);
				volumeNodePaths.add(volume.getNodePath());
			}
			
			@Override
			public void onUbuntuOneFailure(U1Failure failure) {
				MetaService.this.onUbuntuOneFailure(failure, receiver);
			}

			@Override
			public void onFailure(U1Failure failure) {
				MetaService.this.onFailure(failure, receiver);
			}
			
			@Override
			public void onFinish() {
				for (String nodePath : volumeNodePaths) {
					getNode(nodePath, null, false);
					cachedNodePaths.remove(nodePath);
				}
				if (NetworkUtil.isConnected(MetaService.this)) {
					// We are connected, thus left cachedNodePaths are invalid.
					for (String oldNodePath : cachedNodePaths) {
						MetaUtilities.delete(oldNodePath);
					}
				}
				contentResolver.notifyChange(Nodes.CONTENT_URI, null);
				if (receiver != null)
					receiver.send(Status.FINISHED, Bundle.EMPTY);
			}
		});
	}
	
	public void getVolume(final String resourcePath,
			final ResultReceiver receiver) {
		final Bundle data = new Bundle();
		data.putString(EXTRA_RESOURCE_PATH, resourcePath);
		api.getVolume(resourcePath, new U1VolumeListener() {
			@Override
			public void onStart() {
				if (receiver != null)
					receiver.send(Status.RUNNING, data);
			}

			@Override
			public void onSuccess(U1Volume volume) {
				onGetVolumeSuccess(volume, receiver);
			}
			
			@Override
			public void onUbuntuOneFailure(U1Failure failure) {
				MetaService.this.onUbuntuOneFailure(failure, receiver);
			}

			@Override
			public void onFailure(U1Failure failure) {
				MetaService.this.onFailure(failure, receiver);
			}
			
			@Override
			public void onFinish() {
				if (receiver != null)
					receiver.send(Status.FINISHED, data);
			}
		});
	}
	
	private void onGetVolumeSuccess(U1Volume volume, ResultReceiver receiver) {
		final String resourcePath = volume.getResourcePath();
		
		ContentValues values = Volumes.valuesFromRepr(volume);
		String selection = Volumes.VOLUME_RESOURCE_PATH + "=?";
		String[] selectionArgs = new String[] { resourcePath };
		
		long savedGeneration = MetaUtilities.getVolumeGeneration(resourcePath);
		int updated = contentResolver.update(Volumes.CONTENT_URI,
				values, selection, selectionArgs);
		if (updated == 0) {
			contentResolver.insert(Volumes.CONTENT_URI, values);
		} else {
			if (savedGeneration > 0 &&
					savedGeneration < volume.getGeneration()) {
				getVolumeDelta(volume.getResourcePath(), savedGeneration, receiver);
			}
		}
	}
	
	public void getVolumeDelta(String resourcePath, long generation,
			final ResultReceiver receiver) {
		final Bundle data = new Bundle();
		data.putString(EXTRA_RESOURCE_PATH, resourcePath);
		api.getVolumeDelta(resourcePath, generation, new U1NodeListener() {
			@Override
			public void onStart() {
				if (receiver != null)
					receiver.send(Status.RUNNING, data);
			}

			@Override
			public void onSuccess(U1Node node) {
				onGetNodeSuccess(node, false);
			}

			@Override
			public void onUbuntuOneFailure(U1Failure failure) {
				MetaService.this.onUbuntuOneFailure(failure, receiver);
			}
			
			@Override
			public void onFailure(U1Failure failure) {
				MetaService.this.onFailure(failure, receiver);
			}

			@Override
			public void onFinish() {
				if (receiver != null)
					receiver.send(Status.FINISHED, data);
			}
		});
	}
	
	public void getNode(final String resourcePath, final ResultReceiver receiver,
			final boolean getChildren) {
		final Bundle data = new Bundle();
		data.putString(EXTRA_RESOURCE_PATH, resourcePath);
		
		api.getNode(resourcePath, new U1NodeListener() {
			@Override
			public void onStart() {
				if (receiver != null)
					receiver.send(Status.RUNNING, data);
			}

			@Override
			public void onSuccess(U1Node node) {
				onGetNodeSuccess(node, getChildren);
			}
			
			@Override
			public void onUbuntuOneFailure(U1Failure failure) {
				MetaService.this.onUbuntuOneFailure(failure, receiver);
			}
			
			@Override
			public void onFailure(U1Failure failure) {
				MetaService.this.onFailure(failure, receiver);
			}

			@Override
			public void onFinish() {
				if (receiver != null)
					receiver.send(Status.FINISHED, data);
			}
		});
	}
	
	public void onGetNodeSuccess(U1Node node, boolean getChildren) {
		final String resourcePath = node.getResourcePath();
		final boolean isDirectory = node.getKind() == U1NodeKind.DIRECTORY;
		
		String data = null;
		
		String oldHash = MetaUtilities.getStringField(
				resourcePath, Nodes.NODE_HASH);
		if (node.getKind() == U1NodeKind.FILE && oldHash != null) {
			String newHash = ((U1File)node).getHash();
			if (!oldHash.equals(newHash)) {
				String path = FileUtilities.getFilePathFromResourcePath(
						node.getResourcePath());
				FileUtilities.safeDeleteSilently(path);
				data = "";
			}
		}
		
		MetaUtilities.updateNode(getContentResolver(), node, data);
		
		if (node.getIsLive()) {
			if (isDirectory && getChildren) {
				getDirectoryNode(resourcePath, null);
			}
		}
	}
	
	private void createVolume(final String resourcePath,
			final ResultReceiver receiver) {
		api.createVolume(resourcePath, new U1VolumeListener() {
			@Override
			public void onStart() {
				if (receiver != null)
					receiver.send(Status.RUNNING, Bundle.EMPTY);
			}

			@Override
			public void onSuccess(U1Volume volume) {
				onCreateVolumeSuccess(volume);
			}
			
			@Override
			public void onUbuntuOneFailure(U1Failure failure) {
				MetaService.this.onUbuntuOneFailure(failure, receiver);
			}
			
			@Override
			public void onFailure(U1Failure failure) {
				MetaService.this.onFailure(failure, receiver);
			}

			@Override
			public void onFinish() {
				if (receiver != null) {
					Bundle data = new Bundle();
					data.putString(EXTRA_RESOURCE_PATH, resourcePath);
					receiver.send(Status.FINISHED, data);
				}
			}
		});
	}
	
	/**
	 * Volumes need to be inserted manually, since their name on the list is
	 * not simply the last segment of the path, but their full path. Volume
	 * names are not updated when volume root nodes are refreshed.
	 * 
	 * @param volume
	 *            The volume, which has been created.
	 */
	private void onCreateVolumeSuccess(U1Volume volume) {
		final ContentValues values = new ContentValues();
		values.put(Nodes.NODE_RESOURCE_PATH, volume.getNodePath());
		values.put(Nodes.NODE_KIND, U1NodeKind.DIRECTORY.toString());
		values.put(Nodes.NODE_PATH, volume.getPath());
		values.put(Nodes.NODE_NAME, volume.getPath());
		getContentResolver().insert(Nodes.CONTENT_URI, values);
		
		getNode(volume.getNodePath(), null, false);
	}

	private void makeDirectory(final String resourcePath,
			final ResultReceiver receiver) {
		final Bundle data = new Bundle();
		data.putString(EXTRA_RESOURCE_PATH, resourcePath);
		api.makeDirectory(resourcePath, new U1NodeListener() {
			@Override
			public void onStart() {
				if (receiver != null)
					receiver.send(Status.RUNNING, Bundle.EMPTY);
			}

			@Override
			public void onSuccess(U1Node node) {
				onMakeDirectorySuccess(node);
			}
			
			@Override
			public void onUbuntuOneFailure(U1Failure failure) {
				MetaService.this.onUbuntuOneFailure(failure, receiver);
			}
			
			@Override
			public void onFailure(U1Failure failure) {
				MetaService.this.onFailure(failure, receiver);
			}

			@Override
			public void onFinish() {
				if (receiver != null)
					receiver.send(Status.FINISHED, data);
			}
		});
	}
	
	public void onMakeDirectorySuccess(U1Node node) {
		onGetNodeSuccess(node, false);
	}
	
	private void updateNode(final String resourcePath, Boolean isPublic,
			final ResultReceiver receiver) {
		final Bundle data = new Bundle();
		data.putString(EXTRA_RESOURCE_PATH, resourcePath);
		api.setFilePublic(resourcePath, isPublic, new U1NodeListener() {
			@Override
			public void onStart() {
				if (receiver != null)
					receiver.send(Status.RUNNING, Bundle.EMPTY);
			}
			
			@Override
			public void onSuccess(U1Node node) {
				onUpdateNodeAccessSuccess(node);
			}
			
			@Override
			public void onUbuntuOneFailure(U1Failure failure) {
				MetaService.this.onUbuntuOneFailure(failure, receiver);
			}
			
			@Override
			public void onFailure(U1Failure failure) {
				MetaService.this.onFailure(failure, receiver);
			}

			@Override
			public void onFinish() {
				if (receiver != null)
					receiver.send(Status.FINISHED, data);
			}
		});
	}
	
	private void onUpdateNodeAccessSuccess(U1Node node) {
		onGetNodeSuccess(node, false);
	}
	
	private void updateNode(final String resourcePath, final String newPath,
			final ResultReceiver receiver) {
		final Bundle data = new Bundle();
		data.putString(EXTRA_RESOURCE_PATH, resourcePath);
		
		api.moveNode(resourcePath, newPath, new U1NodeListener() {
			@Override
			public void onStart() {
				if (receiver != null)
					receiver.send(Status.RUNNING, data);
			}
			
			@Override
			public void onSuccess(U1Node node) {
				onUpdateNodeNameSuccess(resourcePath, node);
			}
			
			@Override
			public void onUbuntuOneFailure(U1Failure failure) {
				MetaService.this.onUbuntuOneFailure(failure, receiver);
			}
			
			@Override
			public void onFailure(U1Failure failure) {
				MetaService.this.onFailure(failure, receiver);
			}

			@Override
			public void onFinish() {
				if (receiver != null)					
					receiver.send(Status.FINISHED, data);
			}
		});
	}
	
	private void onUpdateNodeNameSuccess(String resourcePath, U1Node node) {
		final String newResourcePath = node.getResourcePath();
		
		// Rename the file, if cached.
		String path = FileUtilities
				.getFilePathFromResourcePath(resourcePath);
		
		// This changes filename in place, mv not supported yet. 
		final File oldFile = new File(path);
		if (oldFile.exists()) {
			String newPath = FileUtilities
					.getFilePathFromResourcePath(newResourcePath);
			
			final File newFile = new File(newPath);
			Log.d(TAG, "Renaming cached file "
					+ oldFile + " to " + newFile);
			oldFile.renameTo(newFile);
			// Update the cache.
			if (newFile.isFile()) {
				final String newFileData = newFile.getAbsolutePath();
				MetaUtilities.updateStringField(resourcePath,
						Nodes.NODE_DATA, newFileData);
			}
		}
		
		MetaUtilities.setIsCached(resourcePath, false);
		onGetNodeSuccess(node, false);
	}

	private void deleteNode(final String resourcePath,
			final ResultReceiver receiver) {
		final Bundle data = new Bundle();
		data.putString(EXTRA_RESOURCE_PATH, resourcePath);
		api.deleteNode(resourcePath, new U1NodeListener() {
			@Override
			public void onStart() {
				if (receiver != null)
					receiver.send(Status.RUNNING, data);
				MetaUtilities.setState(resourcePath, ResourceState.STATE_DELETING);
				MetaUtilities.notifyChange(Nodes.CONTENT_URI);
			}

			@Override
			public void onSuccess(U1Node node) {
				MetaUtilities.delete(resourcePath);
			}
			
			@Override
			public void onUbuntuOneFailure(U1Failure failure) {
				MetaService.this.onUbuntuOneFailure(failure, receiver);
			}
			
			@Override
			public void onFailure(U1Failure failure) {
				MetaService.this.onFailure(failure, receiver);
			}

			@Override
			public void onFinish() {
				MetaUtilities.notifyChange(Nodes.CONTENT_URI);
				if (receiver != null)
					receiver.send(Status.FINISHED, data);
			}
		});
	}
	
	/**
	 * Given parents resource path and {@link ArrayList} of {@link NodeInfo}s of
	 * its children, syncs cached info of these children. Updating children in
	 * one method enables us to make use of database transaction.<br />
	 * <ul>
	 * <li>- inserts if child is new</li>
	 * <li>- updates if child has changed [thus marks is_cached = false]</li>
	 * <li>- deletes if child is missing [dead node]</li>
	 * </ul>
	 * 
	 * @param parentResourcePath
	 *            the resource path of childrens parent
	 * @param children
	 *            {@link NodeInfo}s of the parents children
	 * @throws OperationApplicationException 
	 * @throws RemoteException 
	 */
	public void getDirectoryNode(final String resourcePath,
			final ResultReceiver receiver) {
		Log.i(TAG, "getDirectoryNode()");
		final String[] projection = new String[] {
				Nodes._ID, Nodes.NODE_RESOURCE_PATH,
				Nodes.NODE_GENERATION, Nodes.NODE_DATA
		};
		final String selection = Nodes.NODE_RESOURCE_PATH + "=?";
		
		final Set<Integer> childrenIds =
				MetaUtilities.getChildrenIds(resourcePath);
		
		final ArrayList<ContentProviderOperation> operations =
				new ArrayList<ContentProviderOperation>();
		
		final Bundle data = new Bundle();
		data.putString(EXTRA_RESOURCE_PATH, resourcePath);
		
		api.listDirectory(resourcePath, new U1NodeListener() {
			@Override
			public void onStart() {
				if (receiver != null)
					receiver.send(Status.RUNNING, data);
			}

			@Override
			public void onSuccess(U1Node node) {
				if (node.getKind() == U1NodeKind.FILE &&
						((U1File) node).getSize() == null) {
					// Ignore files with null size.
					return;
				}
				final String[] selectionArgs =
						new String[] { node.getResourcePath() };
				final Cursor c = contentResolver.query(Nodes.CONTENT_URI, projection,
						selection, selectionArgs, null);
				try {
					ContentValues values = Nodes.valuesFromRepr(node);
					if (c.moveToFirst()) {
						final int id = c.getInt(c.getColumnIndex(Nodes._ID));
						// Node is live.
						childrenIds.remove(id);
						
						// Update node.
						final long generation =
								c.getLong(c.getColumnIndex(Nodes.NODE_GENERATION));
						final long newGeneration = node.getGeneration();
						if (generation < newGeneration) {
							Log.v(TAG, "updating child node, new generation");
							values.put(Nodes.NODE_IS_CACHED, false);
							values.put(Nodes.NODE_DATA, "");
							
							String data = c.getString(c.getColumnIndex(Nodes.NODE_DATA));
							FileUtilities.safeDeleteSilently(data);
							
							Uri uri = MetaUtilities.buildNodeUri(id);
							ContentProviderOperation op = ContentProviderOperation
									.newUpdate(uri)
									.withValues(values)
									.build();
							operations.add(op);
							if (operations.size() > 10) {
								try {
									contentResolver.applyBatch(
											MetaContract.CONTENT_AUTHORITY, operations);
									operations.clear();
								} catch (RemoteException e) {
									Log.e(TAG, "Remote exception", e);
								} catch (OperationApplicationException e) {
									MetaUtilities.setIsCached(resourcePath, false);
									return;
								}
								Thread.yield();
							}
						} else {
							Log.v(TAG, "child up to date");
						}
					} else {
						// Insert node.
						Log.v(TAG, "inserting child");
						ContentProviderOperation op = ContentProviderOperation
								.newInsert(Nodes.CONTENT_URI)
								.withValues(values)
								.build();
						operations.add(op);
						if (operations.size() > 10) {
							try {
								contentResolver.applyBatch(
										MetaContract.CONTENT_AUTHORITY, operations);
								operations.clear();
							} catch (RemoteException e) {
								Log.e(TAG, "Remote exception", e);
							} catch (OperationApplicationException e) {
								MetaUtilities.setIsCached(resourcePath, false);
								return;
							}
							Thread.yield();
						}
					}
				} finally {
					c.close();
				}
			}
			
			@Override
			public void onUbuntuOneFailure(U1Failure failure) {
				MetaService.this.onUbuntuOneFailure(failure, receiver);
			}
			
			@Override
			public void onFailure(U1Failure failure) {
				MetaService.this.onFailure(failure, receiver);
			}
			
			@Override
			public void onFinish() {
				if (receiver != null)
					receiver.send(Status.FINISHED, data);
			}
		});
		
		
		// Remove nodes, which ids are left in childrenIds set.
		if (!childrenIds.isEmpty()) {
			Log.v(TAG, "childrenIDs not empty: " + childrenIds.size());
			final Iterator<Integer> it = childrenIds.iterator();
			while (it.hasNext()) {
				int id = it.next();
				Uri uri = MetaUtilities.buildNodeUri(id);
				ContentProviderOperation op = ContentProviderOperation
						.newDelete(uri).build();
				operations.add(op);
			}
		} else {
			Log.v(TAG, "childrenIDs empty");
		}
		
		try {
			long then = System.currentTimeMillis();
			contentResolver.applyBatch(MetaContract.CONTENT_AUTHORITY, operations);
			MetaUtilities.setIsCached(resourcePath, true);
			long now = System.currentTimeMillis();
			Log.d(TAG, "time to update children: " + (now-then));
			contentResolver.notifyChange(Nodes.CONTENT_URI, null); 
		} catch (RemoteException e) {
			Log.e(TAG, "", e);
		} catch (OperationApplicationException e) {
			MetaUtilities.setIsCached(resourcePath, false);
			return;
		}
	}
}
