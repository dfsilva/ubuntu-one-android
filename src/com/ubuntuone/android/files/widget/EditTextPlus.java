/**
 * @see http://stackoverflow.com/questions/2376250/custom-fonts-and-xml-layouts-android
 */

package com.ubuntuone.android.files.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.EditText;

import com.ubuntuone.android.files.R;

public class EditTextPlus extends EditText {
	private static final String TAG = "EditText";

	public EditTextPlus(Context context) {
		super(context);
		setBackgroundResource(R.drawable.edittext);
	}

	public EditTextPlus(Context context, AttributeSet attrs) {
		super(context, attrs);
		setBackgroundResource(R.drawable.edittext);
		setCustomFont(context, attrs);
	}

	public EditTextPlus(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setBackgroundResource(R.drawable.edittext);
		setCustomFont(context, attrs);
	}

	private void setCustomFont(Context ctx, AttributeSet attrs) {
		TypedArray a = ctx.obtainStyledAttributes(attrs,
				R.styleable.EditTextPlus);
		String customFont = a.getString(R.styleable.EditTextPlus_customFont);
		setCustomFont(ctx, customFont);
		a.recycle();
	}

	public boolean setCustomFont(Context ctx, String asset) {
		Typeface tf = null;
		try {
			tf = Typeface.createFromAsset(ctx.getAssets(), asset);
		} catch (Exception e) {
			Log.e(TAG, "Could not get typeface: " + e.getMessage());
			return false;
		}

		setTypeface(tf);
		return true;
	}

}
