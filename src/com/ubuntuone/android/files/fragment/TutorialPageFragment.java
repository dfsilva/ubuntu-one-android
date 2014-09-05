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

package com.ubuntuone.android.files.fragment;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.ubuntuone.android.files.R;
import com.ubuntuone.android.files.widget.TextViewPlus;

public class TutorialPageFragment extends Fragment {
	private int headerResId;
	private int textResId;
	private int imageResId;
	
	public TutorialPageFragment() {
		super();
	}
		
	public TutorialPageFragment(int headerResId, int textResId, int imageResId) {
		this.headerResId = headerResId;
		this.textResId = textResId;
		this.imageResId = imageResId;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		final View content = inflater.inflate(R.layout.tutorial_page, null);
		final ImageView image = (ImageView)
				content.findViewById(R.id.tutorial_page_image);
		final TextViewPlus header =	(TextViewPlus)
				content.findViewById(R.id.tutorial_page_header);
		final TextViewPlus body = (TextViewPlus)
				content.findViewById(R.id.tutorial_page_body);
		
		image.setImageResource(imageResId);
		header.setText(headerResId);
		body.setText(textResId);
		
		return content;
	}
}
