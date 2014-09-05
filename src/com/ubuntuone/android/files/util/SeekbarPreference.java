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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class SeekbarPreference extends Preference implements
		OnSeekBarChangeListener {
	
	// Minimum value limit of the seek bar range. (min of range is 0)
	private int min;
	
	// Maximum value of the seek bar range.
	private int max;
	
	private SeekBar seekbar;
	
	public SeekbarPreference(Context context) {
		super(context);	
	}
	
	public void setLimits(int min, int max) {
		this.min = min;
		this.max = max;
		seekbar.setMax(max);
	}
	
	@Override
	protected View onCreateView(ViewGroup parent) {
		SeekBar seekbar = new SeekBar(getContext());
		seekbar.setMax(max);
		seekbar.setOnSeekBarChangeListener(this);
		parent.addView(seekbar);
		return super.onCreateView(parent);
	}

	@Override
	protected void onSetInitialValue(boolean restorePersistedValue,
			Object defaultValue) {
		if (restorePersistedValue) {
			Integer value = getPersistedInt(max);
			seekbar.setProgress(value);
		} else {
			seekbar.setProgress((Integer)defaultValue);
			if (shouldPersist())
				updatePreference(seekbar.getProgress());
		}
		super.onSetInitialValue(restorePersistedValue, defaultValue);
	}
	
	private void updatePreference(int newValue) {
		SharedPreferences.Editor editor = getEditor();
		editor.putInt(getKey(), newValue);
		editor.commit();
	}
	
	@Override
	protected boolean callChangeListener(Object newValue) {
		Integer progress = (Integer) newValue;
		if (progress < min)
			return false;
		return super.callChangeListener(newValue);
	}

	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
		// Unused.
	}
	
	public void onStartTrackingTouch(SeekBar seekBar) {
		// Unused.
	}

	public void onStopTrackingTouch(SeekBar seekBar) {
		int progress = seekBar.getProgress();
		
		if (!callChangeListener(progress))
			return;
		
		seekBar.setProgress(progress);
		updatePreference(seekBar.getProgress());
		notifyChanged();
	}

}
