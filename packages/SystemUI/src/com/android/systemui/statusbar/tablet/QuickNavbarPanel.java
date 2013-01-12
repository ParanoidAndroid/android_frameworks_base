/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.systemui.statusbar.tablet;

import com.android.systemui.R;
import com.android.systemui.statusbar.PieControl;
import com.android.systemui.statusbar.PieControl.OnNavButtonPressedListener;
import com.android.systemui.statusbar.BaseStatusBar;

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

/**
 * Needed for takeScreenshot
 */
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.os.IBinder;
import android.os.Messenger;
import android.os.RemoteException;


public class QuickNavbarPanel extends FrameLayout implements StatusBarPanel, OnNavButtonPressedListener {
    private static final boolean DEBUG = true;
    private Handler mHandler;
    boolean mShowing;
    private PieControl mPieControl;
    private int mInjectKeycode;
    private long mDownTime;
    private Context mContext;
    
    ViewGroup mContentFrame;
    Rect mContentArea = new Rect();

    private BaseStatusBar mStatusBar;

    public QuickNavbarPanel(Context context) {
        this(context, null);
    }

    public QuickNavbarPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mPieControl = new PieControl(context);
        mPieControl.setOnNavButtonPressedListener(this);
    }

    public void setBar(BaseStatusBar statusbar) {
        mStatusBar = (BaseStatusBar) statusbar;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mPieControl.onTouchEvent(event);
        //return super.onTouchEvent(event);
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
        mShowing = true;
        mPieControl.attachToContainer(this);
        mPieControl.forceToTop(this);
    }

    /**
     * Whether the panel is showing, or, if it's animating, whether it will be
     * when the animation is done.
     */
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
        }/* else if (buttonName.equals(PieControl.RECENT_BUTTON)) {
            Message peekMsg = mHandler.obtainMessage(BaseStatusBar.MSG_TOGGLE_RECENT_APPS);
            mHandler.sendMessage(peekMsg);
        } else if (buttonName.equals(PieControl.NOTIFICATION_BUTTON)) {
            Message peekMsg = mHandler.obtainMessage(BaseStatusBar.MSG_OPEN_NOTIFICATION_PANEL);
            mHandler.sendMessage(peekMsg);
        } */else if (buttonName.equals(PieControl.SCREENSHOT_BUTTON)) {
            takeScreenshot();
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

    /**
    * functions needed for taking screenhots.  
    * This leverages the built in ICS screenshot functionality 
    */
   final Object mScreenshotLock = new Object();
   ServiceConnection mScreenshotConnection = null;

   final Runnable mScreenshotTimeout = new Runnable() {
       @Override public void run() {
           synchronized (mScreenshotLock) {
               if (mScreenshotConnection != null) {
                   mContext.unbindService(mScreenshotConnection);
                   mScreenshotConnection = null;
               }
           }
       }
   };

   private void takeScreenshot() {
       synchronized (mScreenshotLock) {
           if (mScreenshotConnection != null) {
               return;
           }
           ComponentName cn = new ComponentName("com.android.systemui",
                   "com.android.systemui.screenshot.TakeScreenshotService");
           Intent intent = new Intent();
           intent.setComponent(cn);
           ServiceConnection conn = new ServiceConnection() {
               @Override
               public void onServiceConnected(ComponentName name, IBinder service) {
                   synchronized (mScreenshotLock) {
                       if (mScreenshotConnection != this) {
                           return;
                       }
                       Messenger messenger = new Messenger(service);
                       Message msg = Message.obtain(null, 1);
                       final ServiceConnection myConn = this;
                       Handler h = new Handler(mHandler.getLooper()) {
                           @Override
                           public void handleMessage(Message msg) {
                               synchronized (mScreenshotLock) {
                                   if (mScreenshotConnection == myConn) {
                                       mContext.unbindService(mScreenshotConnection);
                                       mScreenshotConnection = null;
                                       mHandler.removeCallbacks(mScreenshotTimeout);
                                   }
                               }
                           }
                       };
                       msg.replyTo = new Messenger(h);
                       msg.arg1 = msg.arg2 = 0;

                       /* wait for the panel to close */
                       try {
                           Thread.sleep(500); 
                       } catch (InterruptedException ie) {
                       }
                       
                       /* take the screenshot */
                       try {
                           messenger.send(msg);
                       } catch (RemoteException e) {
                       }
                   }
               }
               @Override
               public void onServiceDisconnected(ComponentName name) {}
           };
           if (mContext.bindService(intent, conn, Context.BIND_AUTO_CREATE)) {
               mScreenshotConnection = conn;
               mHandler.postDelayed(mScreenshotTimeout, 10000);
           }
       }
   }
}
