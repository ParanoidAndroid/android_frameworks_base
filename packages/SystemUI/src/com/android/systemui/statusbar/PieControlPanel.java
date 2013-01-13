/*
 * Copyright (C) 2010 ParanoidAndroid Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar;


import android.content.Context;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import com.android.systemui.R;
import com.android.systemui.statusbar.tablet.StatusBarPanel;
import com.android.systemui.statusbar.PieControl.OnNavButtonPressedListener;

public class PieControlPanel extends FrameLayout implements StatusBarPanel, OnNavButtonPressedListener {

    private Handler mHandler;
    boolean mShowing;
    private PieControl mPieControl;
    private int mInjectKeycode;
    private long mDownTime;
    private Context mContext;
    
    ViewGroup mContentFrame;
    Rect mContentArea = new Rect();

    private BaseStatusBar mStatusBar;

    public PieControlPanel(Context context) {
        this(context, null);
    }

    public PieControlPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mPieControl = new PieControl(context);
        mPieControl.setOnNavButtonPressedListener(this);
        mPieControl.setPanel(this);
    }

    public void setBar(BaseStatusBar statusbar) {
        mStatusBar = (BaseStatusBar) statusbar;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mPieControl.onTouchEvent(event);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onAttachedToWindow () {
        super.onAttachedToWindow();
    }

    public void setHandler(Handler h) {
        mHandler = h;
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        mContentFrame = (ViewGroup)findViewById(R.id.content_frame);
        setWillNotDraw(false);
        mPieControl.attachToContainer(this);
        mPieControl.forceToTop(this);
        show(false);
    }

    public boolean isShowing() {
        return mShowing;
    }

    public void show(boolean show) {
        mShowing = show;
        setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            mPieControl.setCenter(this.getWidth() / 2, this.getHeight());
        }
        mPieControl.show(show);
    }

    public boolean isInContentArea(int x, int y) {
        mContentArea.left = mContentFrame.getLeft() + mContentFrame.getPaddingLeft();
        mContentArea.top = mContentFrame.getTop() + mContentFrame.getPaddingTop();
        mContentArea.right = mContentFrame.getRight() - mContentFrame.getPaddingRight();
        mContentArea.bottom = mContentFrame.getBottom() - mContentFrame.getPaddingBottom();

        return mContentArea.contains(x, y);
    }

    public void onNavButtonPressed(String buttonName) {
        if (buttonName.equals(PieControl.BACK_BUTTON)) {
            injectKeyDelayed(KeyEvent.KEYCODE_BACK);
        } else if (buttonName.equals(PieControl.HOME_BUTTON)) {
            injectKeyDelayed(KeyEvent.KEYCODE_HOME);
        } else if (buttonName.equals(PieControl.MENU_BUTTON)) {
            injectKeyDelayed(KeyEvent.KEYCODE_MENU);
        } else if (buttonName.equals(PieControl.RECENT_BUTTON)) {
            mStatusBar.toggleRecentApps();
        }
        show(false);
    }

    public void injectKeyDelayed(int keycode){
    	mInjectKeycode = keycode;
        mDownTime = SystemClock.uptimeMillis();
    	mHandler.removeCallbacks(onInjectKeyDelayed);
      	mHandler.postDelayed(onInjectKeyDelayed, 100);
    }

    final Runnable onInjectKeyDelayed = new Runnable() {
    	public void run() {
            final long eventTime = SystemClock.uptimeMillis();
            InputManager.getInstance().injectInputEvent(
                    new KeyEvent(mDownTime, eventTime - 100, KeyEvent.ACTION_DOWN, mInjectKeycode, 0),
                    InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            InputManager.getInstance().injectInputEvent(
                    new KeyEvent(mDownTime, eventTime - 50, KeyEvent.ACTION_UP, mInjectKeycode, 0),
                    InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    	}
    };
}
