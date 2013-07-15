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
import android.app.KeyguardManager;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.PanelBar;
import com.android.systemui.statusbar.tablet.StatusBarPanel;
import com.android.systemui.statusbar.PieControl.OnNavButtonPressedListener;

public class PieControlPanel extends FrameLayout implements StatusBarPanel, OnNavButtonPressedListener {

    private Handler mHandler;
    private boolean mShowing;
    private boolean mMenuButton;
    private PieControl mPieControl;
    private int mInjectKeycode;
    private long mDownTime;
    private Context mContext;
    private int mOrientation;
    private int mWidth;
    private int mHeight;
    private View mTrigger;
    private WindowManager mWindowManager;
    private Display mDisplay;
    private KeyguardManager mKeyguardManger;
    
    ViewGroup mContentFrame;
    Rect mContentArea = new Rect();

    private BaseStatusBar mStatusBar;

    public PieControlPanel(Context context) {
        this(context, null);
    }

    public PieControlPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mKeyguardManger = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        mDisplay = mWindowManager.getDefaultDisplay();
        mPieControl = new PieControl(context, this);
        mPieControl.setOnNavButtonPressedListener(this);
        mOrientation = Gravity.BOTTOM;
        mMenuButton = false;
    }

    public boolean currentAppUsesMenu() {
        return mMenuButton;
    }

    public void setMenu(boolean state) {
        mMenuButton = state;
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

    public BaseStatusBar getBar() {
        return mStatusBar;
    }

    public void animateCollapsePanels() {
        mPieControl.getPieMenu().getStatusPanel().hidePanels(true);
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

    static private int[] gravityArray = {Gravity.BOTTOM, Gravity.LEFT, Gravity.TOP, Gravity.RIGHT, Gravity.BOTTOM, Gravity.LEFT};
    static public int findGravityOffset(int gravity) {    
        for (int gravityIndex = 1; gravityIndex < gravityArray.length - 2; gravityIndex++) {
            if (gravity == gravityArray[gravityIndex])
                return gravityIndex;
        }
        return 4;
    }

    public void bumpConfiguration() {
        if (Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.PIE_STICK, 1) == 1) {

            // Get original offset
            int gravityIndex = findGravityOffset(convertPieGravitytoGravity(
                    Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.PIE_GRAVITY, 3)));
            
            // Orient Pie to that place
            reorient(gravityArray[gravityIndex], false);

            // Now re-orient it for landscape orientation
            switch(mDisplay.getRotation()) {
                case Surface.ROTATION_270:
                    reorient(gravityArray[gravityIndex + 1], false);
                    break;
                case Surface.ROTATION_90:
                    reorient(gravityArray[gravityIndex - 1], false);
                    break;
            }
        }

        show(false);
        if (mPieControl != null) mPieControl.onConfigurationChanged();
    }

    public void init(Handler h, BaseStatusBar statusbar, View trigger, int orientation) {
        mHandler = h;
        mStatusBar = (BaseStatusBar) statusbar;
        mTrigger = trigger;
        mOrientation = orientation;
        mPieControl.init();
    }

    static public int convertGravitytoPieGravity(int gravity) {
        switch(gravity) {
            case Gravity.LEFT:  return 0;
            case Gravity.TOP:   return 1;
            case Gravity.RIGHT: return 2;
            default:            return 3;
        }
    }

    static public int convertPieGravitytoGravity(int gravity) {
        switch(gravity) {
            case 0:  return Gravity.LEFT;
            case 1:  return Gravity.TOP;
            case 2:  return Gravity.RIGHT;
            default: return Gravity.BOTTOM;
        }
    }

    public void reorient(int orientation, boolean storeSetting) {
        mOrientation = orientation;
        mWindowManager.removeView(mTrigger);
        mWindowManager.addView(mTrigger, BaseStatusBar
                .getPieTriggerLayoutParams(mContext, mOrientation));
        show(mShowing);
        if (storeSetting) {
            int gravityOffset = mOrientation;
            if (Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.PIE_STICK, 1) == 1) {

                gravityOffset = findGravityOffset(mOrientation);
                switch(mDisplay.getRotation()) {
                    case Surface.ROTATION_270:
                        gravityOffset = gravityArray[gravityOffset - 1];
                        break;
                    case Surface.ROTATION_90:
                        gravityOffset = gravityArray[gravityOffset + 1];
                        break;
                    default:
                        gravityOffset = mOrientation;
                        break;
                }
            }
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.PIE_GRAVITY, convertGravitytoPieGravity(gravityOffset));
        }
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
        mPieControl.show(show);
    }

    // verticalPos == -1 -> center PIE
    public void show(int verticalPos) {
        mShowing = true;
        setVisibility(View.VISIBLE);
        Point outSize = new Point(0,0);
        WindowManager windowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getRealSize(outSize);
        mWidth = outSize.x;
        mHeight = outSize.y;
        switch(mOrientation) {
            case Gravity.LEFT:
                mPieControl.setCenter(0, (verticalPos != -1 ? verticalPos : mHeight / 2));
                break;
            case Gravity.TOP:
                mPieControl.setCenter((verticalPos != -1 ? verticalPos : mWidth / 2), 0);
                break;
            case Gravity.RIGHT:
                mPieControl.setCenter(mWidth, (verticalPos != -1 ? verticalPos : mHeight / 2));
                break;
            case Gravity.BOTTOM: 
                mPieControl.setCenter((verticalPos != -1 ? verticalPos : mWidth / 2), mHeight);
                break;
        }
        mPieControl.show(true);
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

    public boolean getKeyguardStatus() {
        return mKeyguardManger.isKeyguardLocked() && mKeyguardManger.isKeyguardSecure();
    }
}
