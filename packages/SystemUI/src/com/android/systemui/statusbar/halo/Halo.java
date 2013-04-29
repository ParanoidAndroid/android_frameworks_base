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

import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.LinearLayout;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.phone.Ticker;

public class Halo extends LinearLayout implements Ticker.TickerCallback {

	private int id;
    private String appName;

    private Context mContext;
	private LayoutInflater mLayoutInflater;
    private View viewRoot;
    private ImageButton mIcon;
    private TextView mTicker;
    private PackageManager mPm ;
    private Handler mHandler;
    private BaseStatusBar mBar;
    private Notification curNotif;

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
        mHandler = new Handler();
    }

    public void init(BaseStatusBar bar) {
        mBar = bar;
        mBar.mTicker.setUpdateEvent(this);

        // App icon
		mIcon = (ImageButton) findViewById(R.id.app_icon);
        mTicker = (TextView) findViewById(R.id.ticker);

		mIcon.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
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
    }

    public void show(boolean expanded){
        if(expanded) {
        //TODO: pause the activity
        } else {
          Intent appStartIntent = mPm.getLaunchIntentForPackage(appName);
          if (appStartIntent != null) {
                appStartIntent.addFlags(Intent.FLAG_MULTI_WINDOW);
                mContext.startActivity(appStartIntent);
            }
        }
    }

    private void moveToTop(){
        //TODO: Move the imageView to the top of the screen
    }

    public WindowManager.LayoutParams getWMParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
             // WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
             // WindowManager.LayoutParams.MATCH_PARENT,
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
            mIcon.setImageDrawable(new BitmapDrawable(mContext.getResources(), curNotif.largeIcon));
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
                mTicker.setVisibility(View.INVISIBLE);
                mTickerShowing = false;
            }
        }
    };

}
