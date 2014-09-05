/*
 * Ubuntu SSO for Anpdroid - manage access to Ubuntu SSO from Android 
 * 
 * Copyright (C) 2011 Canonical Ltd.
 * Author: Michał Karnicki <michal.karnicki@canonical.com>
 *   
 * This file is part of Ubuntu SSO for Android
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
import android.util.AttributeSet;
import android.widget.ViewFlipper;

/**
 * This class is workaround for an Android issue. We may need to use it.<br />
 * @see http://daniel-codes.blogspot.com/2010/05/viewflipper-receiver-not-registered.html<br />
 * @see http://code.google.com/p/android/issues/detail?id=6191
 */
public class FixedViewFlipper extends ViewFlipper {

   public FixedViewFlipper(Context context) {
       super(context);
   }

   public FixedViewFlipper(Context context, AttributeSet attrs) {
       super(context, attrs);
   }


   @Override
   protected void onDetachedFromWindow() {
       try {
           super.onDetachedFromWindow();
       }
       catch (IllegalArgumentException e) {
           stopFlipping();
       }
   }
}
