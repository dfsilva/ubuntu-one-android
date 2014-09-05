package com.ubuntuone.android.files.holder;

import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.ubuntuone.android.files.R;
import com.ubuntuone.android.files.widget.TextViewPlus;

public class GalleryViewHolder
{
	public View progressWrapper;
	public ImageView progressImage;
	public ProgressBar progressBar;
	public ImageView previewImage;
	
	public TextViewPlus errorTextView;
	
	public GalleryViewHolder(View view) {
		progressWrapper = view.findViewById(R.id.progress_wrapper);
		
		progressImage = (ImageView) view.findViewById(R.id.progress_image);
		progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
		previewImage = (ImageView) view.findViewById(R.id.preview_image);
		
		errorTextView = (TextViewPlus) view.findViewById(R.id.error);
	}
}
