/*
 * Copyright (C) 2013 ParanoidAndroid
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.systemui.statusbar.pie;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.os.Vibrator;
import android.content.Context;

// +----------------------------------------------------------------------------------+
// | CLASS PieSurface                                                                 |
// +----------------------------------------------------------------------------------+
public class PieSurface {
    private Pie mPie;
    private PieControl mControl;
    private PieStatusPanel mPanel;
    private PiePolicy mPolicy;

    private Context mContext;

    public PieSurface(PieControl control) {
        mControl = control;
        mPie = mControl.mPie;
        mPolicy = mPie.mPolicy;
        mPanel = mPie.mPanel;
        mContext = mControl.mContext;
    }

    public void draw(Canvas canvas) {
    
    }
    
    public boolean touch(MotionEvent event) {
        
        // always re-dispatch event
        return false;
    }
}
