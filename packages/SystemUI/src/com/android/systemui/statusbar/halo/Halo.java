/*
 * Copyright (C) 2010 The Android Open Source Project
 * This code has been modified. Portions copyright (C) 2012, ParanoidAndroid Project.
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

package com.android.systemui.statusbar.halo;

import android.app.ActivityManagerNative;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.phone.Ticker;

public class Halo extends RelativeLayout implements Ticker.TickerCallback {

	private int id;
    private String appName;

    private Context mContext;
	private LayoutInflater mLayoutInflater;
    private View viewRoot;
    private ImageView mIcon;
    private TextView mTicker;
    private PackageManager mPm ;
    private Handler mHandler;
    private BaseStatusBar mBar;
    private Notification curNotif;
    private LinearLayout content;
    private WindowManager mWindowManager;
    private View mRoot;

    public static final String TAG = "HaloLauncher";
    private static final boolean DEBUG = true;
    private static final int TICKER_HIDE_TIME = 5000;

	public boolean mExpanded = false;
    public boolean mTickerShowing = false;

    public Halo(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Halo(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mPm = mContext.getPackageManager();
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mHandler = new Handler();
    }

    public void init(BaseStatusBar bar) {
        mBar = bar;
        mBar.mTicker.setUpdateEvent(this);
        mRoot = this;

        content = (LinearLayout) findViewById(R.id.content_frame);
		mIcon = (ImageView) findViewById(R.id.app_icon);
        mTicker = (TextView) findViewById(R.id.ticker);
        mTicker.setVisibility(View.GONE);

		mIcon.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
                try {
                    // The intent we are sending is for the application, which
                    // won't have permission to immediately start an activity after
                    // the user switches to home.  We know it is safe to do at this
                    // point, so make sure new activity switches are now allowed.
                    ActivityManagerNative.getDefault().resumeAppSwitches();
                    // Also, notifications can be launched from the lock screen,
                    // so dismiss the lock screen when the activity starts.
                    ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
                } catch (RemoteException e) {
                }

                if (curNotif != null && curNotif.contentIntent != null) {
                    Intent overlay = new Intent();
                    overlay.addFlags(Intent.FLAG_MULTI_WINDOW);
                    try {
                        curNotif.contentIntent.send(mContext, 0, overlay);
                    } catch (PendingIntent.CanceledException e) {
                        // Doesn't want to ...
                    }

                    KeyguardManager kgm =
                            (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
                    if (kgm != null) kgm.exitKeyguardSecurely(null);
                }
			}
		});

        mIcon.setOnTouchListener(new OnTouchListener() {
            private boolean actionDown = false;
            private boolean dragEstablished = false;
            private float initialX = 0;
            private float initialY = 0;

			@Override
			public boolean onTouch(View v, MotionEvent event) {
                final int action = event.getAction();

                switch(action) {
                    case MotionEvent.ACTION_DOWN:
                        actionDown = true;
                        dragEstablished = false;
                        initialX = event.getRawX();
                        initialY = event.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (actionDown != true) break;

                        float mX = event.getRawX();
                        float mY = event.getRawY();
                        float distanceX = initialX-mX;
                        float distanceY = initialY-mY;

                        if (!dragEstablished) {
                            float distance = (float)Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));

                            if (distance > 80) {
                                dragEstablished = true;
                                android.util.Log.d("PARANOID", "est!");
                            }

                        } else {
                            android.util.Log.d("PARANOID", "mX=" + mX + "   mY=" + mY);
                            WindowManager.LayoutParams params = getWMParams();
                            params.x = (int)-distanceX;
                            params.y = (int)-distanceY;
                            mWindowManager.updateViewLayout(mRoot, params);
                            return false;
                        }
                        break;
                }
                return false;
            }
		});
    }

    private void moveToTop(){
        //TODO: Move the imageView to the top of the screen
    }

    public WindowManager.LayoutParams getWMParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        return lp;
    }
    
    public void updateTicker(Ticker.Segment segment) {
        curNotif = segment.notification.notification;
        appName = segment.notification.pkg;

        if (curNotif.largeIcon != null) {

            Bitmap input = curNotif.largeIcon;
            Bitmap output = Bitmap.createBitmap(input.getWidth(),
                    input.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);

            final int color = 0xff424242;
            final Paint paint = new Paint();
            final Rect rect = new Rect(0, 0, input.getWidth(), input.getHeight());

            paint.setAntiAlias(true);
            canvas.drawARGB(0, 0, 0, 0);
            paint.setColor(color);
            canvas.drawCircle(input.getWidth() / 2, input.getHeight() / 2, input.getWidth() / 2, paint);
            paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
            canvas.drawBitmap(input, rect, rect, paint);

            mIcon.setImageDrawable(new BitmapDrawable(mContext.getResources(), output));
        } else {
            try {
                Drawable icon = mPm.getApplicationIcon(appName);
                mIcon.setImageDrawable(icon);
            } catch (NameNotFoundException e) {
                //Who am I
            }
        }

        mTickerShowing = true;
        mTicker.setVisibility(View.VISIBLE);
        mTicker.setText(segment.getText().toString());
        mHandler.postDelayed(TickerHider, TICKER_HIDE_TIME);
    }

    private Runnable TickerHider = new Runnable() {
        public void run() {
            if (mTickerShowing) {
                mTicker.setVisibility(View.GONE);
                mTickerShowing = false;
            }
        }
    };

}
