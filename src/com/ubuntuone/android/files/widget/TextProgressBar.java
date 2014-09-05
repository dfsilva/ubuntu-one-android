package com.ubuntuone.android.files.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.widget.ProgressBar;

public class TextProgressBar extends ProgressBar {
	private String text;
	private Paint textPaint;

	public TextProgressBar(Context context) {
		super(context);
		text = "";
		textPaint = new Paint();
		textPaint.setColor(Color.GRAY);
		setWillNotDraw(false);
	}

	@Override
	protected synchronized void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		final Rect bounds = new Rect();
		textPaint.getTextBounds(text, 0, text.length(), bounds);
		int x = getWidth() / 2 - bounds.centerX();
		int y = getHeight() / 2 - bounds.centerY();
		textPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
		canvas.drawText(text, x, y, textPaint);
	}
	
	public synchronized void setText(String text) {
		this.text = text;
		drawableStateChanged();
	}
	
	public synchronized void setColor(int color) {
		textPaint.setColor(color);
		drawableStateChanged();
	}
}
