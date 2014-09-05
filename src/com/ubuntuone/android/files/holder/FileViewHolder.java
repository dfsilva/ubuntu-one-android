package com.ubuntuone.android.files.holder;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.ubuntuone.android.files.R;
import com.ubuntuone.api.files.model.U1NodeKind;

public class FileViewHolder
{
	public ImageView icon;
	public TextView itemName;
	public TextView itemTimestamp; // volume created, file changed
	public TextView itemInteger; // fileSize or "..." for non-empty dirs
	
	// For caching query results:
	public String parentResourcePath;
	public String resourcePath;
	public String resourceState;
	public U1NodeKind kind;
	public String filename;
	public boolean isPublic;
	public String mime;
	public String key;
	public String data;
	
	public FileViewHolder(View view) {
		icon = (ImageView) view.findViewById(R.id.icon);
		itemName = (TextView) view.findViewById(R.id.title);
		itemTimestamp = (TextView) view.findViewById(R.id.detail);
		itemInteger = (TextView) view.findViewById(R.id.size);
	}
}
