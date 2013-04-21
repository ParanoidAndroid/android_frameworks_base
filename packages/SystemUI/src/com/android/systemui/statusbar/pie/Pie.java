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
import android.content.res.Resources;
import android.view.WindowManager;
import android.app.KeyguardManager;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.provider.Settings;
import android.database.ContentObserver;
import android.content.ContentResolver;
import android.view.LayoutInflater;
import android.view.ViewGroup.LayoutParams;
import android.view.MotionEvent;
import android.view.Gravity;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.util.ExtendedPropertiesUtils;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;

// +----------------------------------------------------------------------------------+
// | CLASS Pie                                                                        |
// +==================================================================================+
// | This class creates a full PIE whereever it is initiated                          |
// +----------------------------------------------------------------------------------+
public class Pie {

    public static final int DECORATIONS = 4;    

    protected Context mContext;
    protected Resources mResources;
    protected static WindowManager mWindowManager;
    protected static KeyguardManager mKeyguardManager;
    protected static Display mDisplay;
    protected static BaseStatusBar mStatusBar;

    // The following classes constitute PIE
    public PiePolicy mPolicy;
    public View mTrigger;
    public PieControl mControl;
    public PieStatusPanel mPanel;
    public static View[] mDecorations = new View[DECORATIONS];

    public Pie(Context context, BaseStatusBar statusBar) {
        mContext = context;
        mResources = mContext.getResources();
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        mDisplay = mWindowManager.getDefaultDisplay();
        mStatusBar = statusBar;

        // This will take care of all parameters, settings and updates
        mPolicy = new PiePolicy(this);
    }

    public void update() {

        // Clean up present view
        if (mPanel != null) mWindowManager.removeView(mPanel);
        mPanel = null;

        if (mTrigger != null) mWindowManager.removeView(mTrigger);
        mTrigger = null;

        if (mControl != null)  mWindowManager.removeView(mControl);
        mControl = null;

        for (int i = 0; i < DECORATIONS; i++) {
            if (mDecorations[i] != null)  mWindowManager.removeView(mDecorations[i]);
            mDecorations[i] = null;
        }

        if (!show) return;

        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE); 

        // +----------------------------------------------------------------------------------+
        // | Add panel window for toggles and notifications                                   |
        // +----------------------------------------------------------------------------------+
        mPanel = (PieStatusPanel)inflater.inflate(R.layout.pie_status_panel, null);
        mPanel.init(this);
        WindowManager.LayoutParams panelLayout = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        mWindowManager.addView(mPanel, panelLayout);

        // +----------------------------------------------------------------------------------+
        // | Add trigger                                                                      |
        // +----------------------------------------------------------------------------------+
        mTrigger = new View(mContext);
        mTrigger.setBackgroundColor(0x55FF0000);
        mTrigger.setOnTouchListener(new PieTrigger());
        WindowManager.LayoutParams triggerLayout = new WindowManager.LayoutParams(
              (mPolicy.mGravity == Gravity.TOP || mPolicy.mGravity == Gravity.BOTTOM ?
                    ViewGroup.LayoutParams.MATCH_PARENT : (int)(
                    mResources.getDimensionPixelSize(R.dimen.pie_trigger_height) * mPieSize)),
              (mPolicy.mGravity == Gravity.LEFT || mPolicy.mGravity == Gravity.RIGHT ?
                    ViewGroup.LayoutParams.MATCH_PARENT : (int)(
                    mResources.getDimensionPixelSize(R.dimen.pie_trigger_height) * mPieSize)),
              WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                      | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                      | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                      | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
              PixelFormat.TRANSLUCENT);
        triggerLayout.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        triggerLayout.gravity = mPolicy.mGravity;
        mWindowManager.addView(mTrigger, triggerLayout);

        // +----------------------------------------------------------------------------------+
        // | Add decorations (workaround for MDP's inability to deal with fullscreen)         |
        // +----------------------------------------------------------------------------------+
        addMDPDecorations(mContext);

        // +----------------------------------------------------------------------------------+
        // | Add control                                                                      |
        // +----------------------------------------------------------------------------------+
        mControl = (PieControl) View.inflate(mContext, R.layout.pie_control, null);
        mControl.init(this);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.setTitle("Pie");
        lp.windowAnimations = android.R.style.Animation;
        mWindowManager.addView(mControl, lp);
    }

    public static void addMDPDecorations(Context context) {
        for (int i = 0; i < DECORATIONS; i++) {
            mDecorations[i] = new View(context);
            WindowManager.LayoutParams decorLayout = new WindowManager.LayoutParams(
                  (PiePolicy.GRAVITY[i] == Gravity.TOP || PiePolicy.GRAVITY[i] == Gravity.BOTTOM ?
                        ViewGroup.LayoutParams.MATCH_PARENT : 1),
                  (PiePolicy.GRAVITY[i] == Gravity.LEFT || PiePolicy.GRAVITY[i] == Gravity.RIGHT ?
                        ViewGroup.LayoutParams.MATCH_PARENT : 1),
                  WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                          WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                          | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                          | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                          | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                          | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                  PixelFormat.TRANSLUCENT);
            decorLayout.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
            decorLayout.gravity = PiePolicy.GRAVITY[i];
            mWindowManager.addView(mDecorations[i], decorLayout);
        }
    }

    private class PieTrigger implements View.OnTouchListener {
        private int orient;
        private boolean actionDown = false;
        private boolean centerPie = true;
        private float initialX = 0;
        private float initialY = 0;
        int index;

        public PieTrigger() {
            // ...
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            final int action = event.getAction();

            // If PIE is hidden we'll try to wake it up if possible
            if (!mControl.mShow) {
                switch(action) {
                    // First we look for a simple touch
                    case MotionEvent.ACTION_DOWN:
                        // Do not activate PIE while the keyguard is locked
                        if (!mKeyguardManager.isKeyguardLocked()) {
                            centerPie = Settings.System.getInt(mContext.getContentResolver(),
                                    Settings.System.PIE_CENTER, 1) == 1;
                            actionDown = true;
                            initialX = event.getX();
                            initialY = event.getY();
                        }
                        break;
                    // Then we calculate delta and make PIE visible
                    case MotionEvent.ACTION_MOVE:
                        if (actionDown != true) break;
                        float deltaX = Math.abs(event.getX() - initialX);
                        float deltaY = Math.abs(event.getY() - initialY);
                        float distance = mPolicy.mGravity == Gravity.BOTTOM ||
                                mPolicy.mGravity == Gravity.TOP ? deltaY : deltaX;
                        // Swipe up, todo: 10 is not valid - needs actual dp to work right
                        if (distance > 10) {
                            mControl.show(centerPie ? -1 : (int)(mPolicy.mGravity == Gravity.BOTTOM ||
                                mPolicy.mGravity == Gravity.TOP ? initialX : initialY));
                            event.setAction(MotionEvent.ACTION_DOWN);
                            mControl.onTouchEvent(event);
                            actionDown = false;
                        }
                }
            }
            // If PIE is already active, just forward the event
            else return mControl.onTouchEvent(event);

            return false;
        }
    }
}
