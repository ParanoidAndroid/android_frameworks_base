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

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.systemui.R;
import com.android.systemui.SearchPanelView;
import com.android.systemui.statusbar.tablet.StatusBarPanel;
import com.android.systemui.statusbar.PieControl.OnNavButtonPressedListener;

public class PieControlPanel extends FrameLayout implements StatusBarPanel, OnNavButtonPressedListener {

    private Handler mHandler;
    boolean mShowing;
    private PieControl mPieControl;
    private int mInjectKeycode;
    private long mDownTime;
    private Context mContext;
    private int mOrientation;
    private int mWidth;
    private int mHeight;
    
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
        mOrientation = Gravity.BOTTOM;
    }

    public void setOrientation(int orientation) {
        mOrientation = orientation;
    }

    public int getOrientation() {
        return mOrientation;
    }

    public int getDegree() {
        switch(mOrientation) {
            case Gravity.LEFT: return 180;
            case Gravity.TOP: return -90;
            case Gravity.RIGHT: return 0;
            case Gravity.BOTTOM: return 90;
        }
        return 0;
    }

    public void setBar(BaseStatusBar statusbar) {
        mStatusBar = (BaseStatusBar) statusbar;
        mPieControl.getPieMenu().setPanel(this);
    }

    public BaseStatusBar getBar() {
        return mStatusBar;
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
        mPieControl.setIsAssistantAvailable(getAssistIntent() != null);
        mPieControl.attachToContainer(this);
        mPieControl.forceToTop(this);
        show(false);
    }

    public boolean isShowing() {
        return mShowing;
    }

    public PointF getSize() {
        return new PointF(mWidth, mHeight);
    }

    public void show(boolean show) {
        mShowing = show;
        setVisibility(show ? View.VISIBLE : View.GONE);

        if (show) {
            Point outSize = new Point(0,0);
            WindowManager windowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
            windowManager.getDefaultDisplay().getRealSize(outSize);
            mWidth = outSize.x;
            mHeight = outSize.y;

            switch(mOrientation) {
                case Gravity.LEFT:
                    mPieControl.setCenter(0, mHeight / 2);
                    break;
                case Gravity.TOP:
                    mPieControl.setCenter(mWidth / 2, 0);
                    break;
                case Gravity.RIGHT:
                    mPieControl.setCenter(mWidth, mHeight / 2);
                    break;
                case Gravity.BOTTOM: 
                    mPieControl.setCenter(mWidth / 2, mHeight);
                    break;
            }
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
        } else if (buttonName.equals(PieControl.CLEAR_ALL_BUTTON)) {
            mStatusBar.clearRecentApps();
        } else if (buttonName.equals(PieControl.SEARCH_BUTTON)) {
            launchAssistAction();
        }
    }

    private Intent getAssistIntent() {
        Intent intent = ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(mContext, UserHandle.USER_CURRENT);
        return intent;
    }

    private void launchAssistAction() {
        Intent intent = getAssistIntent();
        if(intent != null) {
            try {
                ActivityOptions opts = ActivityOptions.makeCustomAnimation(mContext,
                        R.anim.search_launch_enter, R.anim.search_launch_exit);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivityAsUser(intent, opts.toBundle(),
                        new UserHandle(UserHandle.USER_CURRENT));
            } catch (ActivityNotFoundException e) {
                // So bad, so sad...
            }
        }
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
