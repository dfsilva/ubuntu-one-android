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

package com.ubuntuone.android.files.provider;

import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;

import android.content.ContentValues;
import android.net.Uri;
import android.provider.BaseColumns;

import com.ubuntuone.android.files.provider.MetaDatabase.Tables;
import com.ubuntuone.android.files.util.FileUtilities;
import com.ubuntuone.api.files.model.U1Directory;
import com.ubuntuone.api.files.model.U1File;
import com.ubuntuone.api.files.model.U1Node;
import com.ubuntuone.api.files.model.U1NodeKind;
import com.ubuntuone.api.files.model.U1Volume;

/**
 * Contract class for interacting with {@link MetaProvider}. Unless
 * otherwise noted, all time-based fields are milliseconds since epoch and can
 * be compared against {@link System#currentTimeMillis()}.
 */
public class MetaContract {
	
	/**
	 * String defining current RESTful state of the resource.
	 */
	public static final class ResourceState {
		/** State indicating this resource is being created. */
		public static final String STATE_POSTING = HttpPost.METHOD_NAME;
		/** State indicating this resource is being updated. */
		public static final String STATE_PUTTING = HttpPut.METHOD_NAME;
		/** State indicating this resource is being queried. */
		public static final String STATE_GETTING = HttpGet.METHOD_NAME;
		/** State indicating this resource is being deleted. */
		public static final String STATE_DELETING = HttpDelete.METHOD_NAME;
		
		public static final String FAILED = "_FAILED";
		
		public static final String STATE_POSTING_FAILED =
				HttpPost.METHOD_NAME.concat(FAILED);
		
		public static final String STATE_GETTING_FAILED =
				HttpGet.METHOD_NAME.concat(FAILED);
	}
	
	interface VolumesColumns {
		/** Unique path of this volume resource. */
		String VOLUME_RESOURCE_PATH = "volume_resource_path";
		/** Current RESTful state of this volume resource. See: {@link ResourceState}. */
		String VOLUME_RESOURCE_STATE = "volume_resource_state";
		/** Type describing this volume. One of {'root', 'udf'}. */
		String VOLUME_TYPE = "volume_type";
		/** Path of this volume. */
		String VOLUME_PATH = "volume_path";
		/** Current generation of this volume. */
		String VOLUME_GENERATION = "volume_generation";
		/** Date describing when was this volume created. */
		String VOLUME_WHEN_CREATED = "volume_when_created";
		/** The root path for node resources from this volume. */
		String VOLUME_NODE_PATH = "volume_node_path";
		/** The root path for file node contents from this volume. */
		String VOLUME_CONTENT_PATH = "volume_content_path";
	}
	
	interface NodesColumns {
		/** Unique path of this node resource. */
		String NODE_RESOURCE_PATH = "node_resource_path";
		/** Current RESTful state of this node resource. See: {@link RestStatus} */
		String NODE_RESOURCE_STATE = "node_resource_state";
		/** Path defining parent of this node. */
		String NODE_PARENT_PATH = "node_parent_path";
		/** Path defining volume to which this node belongs. */
		String NODE_VOLUME_PATH = "node_volume_path";
		/** Unique key identifying this block. */
		String NODE_KEY = "node_key";
		/** Kind of this node. One of: {'file', 'directory'}. */
		String NODE_KIND = "node_kind";
		/** Path of this node, relative to the volume. */
		String NODE_PATH = "node_path";
		/** Name of this node. */
		String NODE_NAME = "node_name";
		/** Timestamp of when was this node created. */
		String NODE_WHEN_CREATED = "node_when_created";
		/** Timestamp of when was this node changed. */
		String NODE_WHEN_CHANGED = "node_when_changed";
		/** Current generation of this node. */
		String NODE_GENERATION = "node_generation";
		/** Generation defining when was this node created. */
		String NODE_GENERATION_CREATED = "node_generation_created";
		/** Content path of this node. */
		String NODE_CONTENT_PATH = "node_content_path";
		/** Flag indicating whether this node exists. */
		String NODE_IS_LIVE = "node_is_live";
		/** Flag indicating whether this node should be kept in sync. */ 
		String NODE_IS_SYNCED = "node_is_synced";
		
		// Files only:
		/** Flag indicating whether this file node has been cached before. */
		String NODE_IS_CACHED = "node_is_cached";
		/** Hash of this file node. */
		String NODE_HASH = "node_hash";
		/** Flag indicating whether this node is currently published. */
		String NODE_IS_PUBLIC = "node_is_public";
		/** URL to which this file node has been published to. */ 
		String NODE_PUBLIC_URL = "node_public_url";
		/** Size of this file node. */
		String NODE_SIZE = "node_size";
		/** MIME type of this file node. */
		String NODE_MIME = "node_mime";
		/** Absolute path to cached content of this file node. */
		String NODE_DATA = "node_data";
		
		// Directories only:
		/** Flag indicating whether this directory node has children nodes. */
		String NODE_HAS_CHILDREN = "node_has_children";
	}
	
	interface WatchedFoldersColumns {
		/** Watched folder path. */
		String FOLDER_PATH = "folder_path";
		/** Displayed folder name. */
		String DISPLAY_NAME = "folder_name";
		/** Flag indicating whether upload photos from this folder. */
		String UPLOAD_PHOTOS = "upload_photos";
		/** Flag indicating whether upload video from this folder. */
		String UPLOAD_VIDEO = "upload_video";
		/** Flag indicating whether upload audio from this folder. */
		String UPLOAD_AUDIO = "upload_audio";
	}
	
	public static final String CONTENT_AUTHORITY = 
		"com.ubuntuone.android.files";

	public static final Uri BASE_CONTENT_URI =
		Uri.parse("content://" + CONTENT_AUTHORITY);
	
	private static final String PATH_VOLUMES = "volumes";	
	private static final String PATH_NODES = "nodes";
	private static final String PATH_VOLUMES_NODES_JOIN = "volumes_nodes";
	private static final String PATH_SEARCH = "search";	
	
	/**
	 * {@link Volumes} are entities that group {@link Nodes}. The ~/Ubuntu One
	 * volume, for instance, is a default volume that user starts with. When
	 * subscribing new folders to Ubuntu One service, user creates new volumes.
	 * User Defined Folders (UDFs; volumes created by the user) cannot be nested.
	 */
	public static class Volumes implements BaseColumns, VolumesColumns {
		public static final Uri CONTENT_URI =
			BASE_CONTENT_URI.buildUpon().appendPath(PATH_VOLUMES).build();
		
		public static final String CONTENT_TYPE =
			"vnd.android.cursor.dir/vnd.ubuntuone.android.files.volume";
		public static final String CONTENT_ITEM_TYPE =
			"vnd.android.cursor.item/vnd.ubuntuone.android.files.volume";
		
		/** Default sort order. */
		public static final String DEFAULT_SORT =
				VolumesColumns.VOLUME_TYPE + " ASC, " +
				VolumesColumns.VOLUME_PATH + " COLLATE NOCASE ASC";
		
		/** Type describing root volume. */
		public static final String TYPE_ROOT = "root";
		/** Type describing user defined volume. */
		public static final String TYPE_UDF = "udf";
		
		/** Build {@link Uri} for requested {@link #VOLUME_ID}. */
		public static Uri buildVolumeUri(String volumeId) {
			return CONTENT_URI.buildUpon().appendPath(volumeId).build();
		}
		
		/** Read {@link #VOLUME_ID} from {@link Volumes} {@link Uri}. */
		public static String getVolumeId(Uri uri) {
			return uri.getPathSegments().get(1);
		}
		
		private static String[] defaultProjection = null;
		
		/** Default projection for {@link Volumes} queries. */
		public static String[] getDefaultProjection() {
			if (defaultProjection == null) {
				defaultProjection = new String[] {
						Volumes._ID,
						Volumes._COUNT,
						Volumes.VOLUME_RESOURCE_PATH,
						Volumes.VOLUME_RESOURCE_STATE,
						Volumes.VOLUME_TYPE,
						Volumes.VOLUME_PATH,
						Volumes.VOLUME_GENERATION,
						Volumes.VOLUME_WHEN_CREATED,
						Volumes.VOLUME_NODE_PATH,
						Volumes.VOLUME_CONTENT_PATH
				};
			}
			return defaultProjection;
		}
		
		public static ContentValues valuesFromRepr(U1Volume volume) {
			final ContentValues values = new ContentValues();
			values.put(VolumesColumns.VOLUME_RESOURCE_PATH,
					volume.getResourcePath());
			values.put(VolumesColumns.VOLUME_TYPE,
					volume.getType());
			values.put(VolumesColumns.VOLUME_PATH,
					volume.getPath());
			values.put(VolumesColumns.VOLUME_GENERATION,
					volume.getGeneration());
			values.put(VolumesColumns.VOLUME_WHEN_CREATED,
					volume.getWhenCreated().getTime());
			values.put(VolumesColumns.VOLUME_NODE_PATH,
					volume.getNodePath());
			values.put(VolumesColumns.VOLUME_CONTENT_PATH,
					volume.getContentPath());
			return values;
		}
		
	}
	
	/**
	 * {@link Nodes} are directory and file entities contained within
	 * {@link Volumes} and structured in a well known file system tree.
	 */
	public static class Nodes implements NodesColumns, BaseColumns {
		
		public static final Uri CONTENT_URI =
			BASE_CONTENT_URI.buildUpon().appendPath(PATH_NODES).build();
		
		public static final String CONTENT_TYPE = 
			"vnd.android.cursor.dir/vnd.ubuntuone.android.node";
		
		public static final String CONTENT_ITEM_TYPE = 
			"vnd.android.cursor.item/vnd.ubuntuone.android.node";
		
		/** Alphanumeric sort order. */
		public static final String SORT_ALPHA =
				NodesColumns.NODE_PARENT_PATH + " ASC, " +
				NodesColumns.NODE_NAME + " COLLATE NOCASE ASC";
		/** Folders first sort order. */
		public static final String SORT_FOLDERS_FIRST =
				NodesColumns.NODE_PARENT_PATH + " ASC, " +
				NodesColumns.NODE_KIND + " ASC, " +
				NodesColumns.NODE_NAME + " COLLATE NOCASE ASC";
		/** Default sort order. */
		public static final String DEFAULT_SORT = SORT_FOLDERS_FIRST;
		
		/** Build {@link Uri} for requested {@link #NODE_ID}. */
		public static Uri buildNodeUri(String nodeId) {
			return CONTENT_URI.buildUpon().appendPath(nodeId).build();
		}
		
		/** Build {@link Uri} for requested {@link #NODE_ID}. */
		public static Uri buildNodeUri(Long nodeId) {
			return CONTENT_URI.buildUpon().appendPath(String.valueOf(nodeId))
					.build();
		}
		
		/** Read {@link #NODE_ID} from {@link Nodes} {@link Uri}. */
		public static String getNodeId(Uri uri) {
			return uri.getPathSegments().get(1);
		}
		
		/** Build search {@link Uri} among {@link Nodes} for given query. */
		public static Uri buildSearchUri(String query) {
			return CONTENT_URI.buildUpon().appendPath(PATH_SEARCH).appendPath(
					query).build();
		}
		
		/** Check if this is a search {@link Uri}. */
		public static boolean isSearchUri(Uri uri) {
			return PATH_SEARCH.equals(uri.getPathSegments().get(1));
		}
		
		private static String[] defaultProjection = null;
		
		/** Default projection for {@link Nodes} queries. */
		public static String[] getDefaultProjection() {
			if (defaultProjection == null) {
				defaultProjection = new String[] {
						Nodes._ID,
						Nodes._COUNT,
						Nodes.NODE_RESOURCE_PATH,
						Nodes.NODE_RESOURCE_STATE,
						Nodes.NODE_PARENT_PATH,
						Nodes.NODE_VOLUME_PATH,
						Nodes.NODE_KEY,
						Nodes.NODE_KIND,
						Nodes.NODE_PATH,
						Nodes.NODE_NAME,
						Nodes.NODE_WHEN_CREATED,
						Nodes.NODE_WHEN_CHANGED,
						Nodes.NODE_GENERATION,
						Nodes.NODE_GENERATION_CREATED,
						Nodes.NODE_CONTENT_PATH,
						Nodes.NODE_IS_LIVE,
						Nodes.NODE_IS_SYNCED,
						// Files only:
						Nodes.NODE_IS_CACHED,
						Nodes.NODE_MIME,
						Nodes.NODE_HASH,
						Nodes.NODE_PUBLIC_URL,
						Nodes.NODE_SIZE,
						Nodes.NODE_MIME,
						Nodes.NODE_DATA,
						// Directories only:
						Nodes.NODE_HAS_CHILDREN
				};
			}
			return defaultProjection;
		}
		
		public static ContentValues valuesFromRepr(U1Node node) {
			return valuesFromRepr(node, null);
		}
		
		public static ContentValues valuesFromRepr(U1Node node, String data) {
			final ContentValues values = new ContentValues();
			final String rPath = node.getResourcePath();
			values.put(Nodes.NODE_RESOURCE_PATH, rPath);
			String parentPath = node.getParentPath();
			if (parentPath != null) {
				values.put(Nodes.NODE_PARENT_PATH, parentPath);
			}
			values.put(Nodes.NODE_VOLUME_PATH, node.getVolumePath());
			values.put(Nodes.NODE_KEY, node.getKey());
			values.put(Nodes.NODE_KIND, node.getKind().toString().toLowerCase());
			values.put(Nodes.NODE_PATH, node.getPath());
			final String nodeName = rPath.substring(rPath.lastIndexOf('/')+1);
			values.put(Nodes.NODE_NAME, nodeName);
			values.put(Nodes.NODE_WHEN_CREATED, node.getWhenCreated().getTime());
			values.put(Nodes.NODE_WHEN_CHANGED, node.getWhenChanged().getTime());
			values.put(Nodes.NODE_GENERATION, node.getGeneration());
			values.put(Nodes.NODE_GENERATION_CREATED,
					node.getGenerationCreated());
			values.put(Nodes.NODE_IS_LIVE, true);
			values.put(Nodes.NODE_IS_SYNCED, false);
			
			if (U1NodeKind.FILE == node.getKind()) {
				U1File file = (U1File) node;
				values.put(Nodes.NODE_HASH, file.getHash());
				values.put(Nodes.NODE_CONTENT_PATH, file.getContentPath());
				
				values.put(Nodes.NODE_IS_CACHED, false);
				String nodeMime = FileUtilities.getMime(nodeName);
				values.put(Nodes.NODE_MIME, nodeMime);
				String publicUrl = file.getPublicUrl();
				if (publicUrl != null) {
					values.put(Nodes.NODE_PUBLIC_URL, publicUrl.toString());
				} else {
					values.put(Nodes.NODE_PUBLIC_URL, "");
				}
				values.put(Nodes.NODE_SIZE, file.getSize());
				if (data != null) {
					values.put(Nodes.NODE_DATA, data);
				}
			} else if (U1NodeKind.DIRECTORY == node.getKind()) {
				U1Directory dir = (U1Directory) node;
				values.put(Nodes.NODE_HAS_CHILDREN, dir.getHasChildren());
			}
			return values;
		}
		
		public static ContentValues valuesFromReprDummy(U1Volume volume) {
			final ContentValues values = new ContentValues();
			values.put(Nodes.NODE_RESOURCE_PATH, volume.getNodePath());
			values.put(Nodes.NODE_KIND, U1NodeKind.DIRECTORY.toString());
			values.put(Nodes.NODE_PATH, ""); // we don't care
			values.put(Nodes.NODE_NAME, volume.getPath());
			return values;
		}
	}
	
	public static class VolumesNodesJoin {
		public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon()
				.appendPath(PATH_VOLUMES_NODES_JOIN).build();
		
		public static final String DEFAULT_SORT = Volumes.DEFAULT_SORT
				+ ", " + NodesColumns.NODE_NAME + " COLLATE NOCASE ASC";
		
		private static String[] defaultProjection = null;
		
		/** Default projection for {@link VolumesNodesJoin} queries. */
		public static String[] getDefaultProjection() {
			if (defaultProjection == null) {
				defaultProjection = new String[] {
						Volumes.VOLUME_RESOURCE_PATH,
						Volumes.VOLUME_RESOURCE_STATE,
						Volumes.VOLUME_TYPE,
						Volumes.VOLUME_PATH,
						Volumes.VOLUME_NODE_PATH,
						Tables.NODES + "." + Nodes._ID,
						Nodes.NODE_RESOURCE_PATH,
						Nodes.NODE_RESOURCE_STATE,
						Nodes.NODE_PARENT_PATH,
						Nodes.NODE_VOLUME_PATH,
						Nodes.NODE_KEY,
						Nodes.NODE_KIND,
						Nodes.NODE_PATH,
						Nodes.NODE_NAME,
						Nodes.NODE_WHEN_CHANGED,
						Nodes.NODE_CONTENT_PATH,
						Nodes.NODE_IS_SYNCED,
						// Files only:
						Nodes.NODE_IS_CACHED,
						Nodes.NODE_MIME,
						Nodes.NODE_HASH,
						Nodes.NODE_PUBLIC_URL,
						Nodes.NODE_SIZE,
						Nodes.NODE_MIME,
						Nodes.NODE_DATA,
						// Directories only:
						Nodes.NODE_HAS_CHILDREN
				};
			}
			return defaultProjection;
		}
	}
	
	private MetaContract() {
	}
}
