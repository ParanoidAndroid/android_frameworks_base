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
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.os.Vibrator;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.TranslateAnimation;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.SoundEffectConstants;
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
    private ImageView mIcon;
    private ImageView mFrame;
    private ImageView mBackdrop;
    private TextView mTicker;
    private PackageManager mPm ;
    private Handler mHandler;
    private BaseStatusBar mBar;
    private Notification curNotif;
    private WindowManager mWindowManager;
    private View mRoot;
    private int mIconSize;
    private WindowManager.LayoutParams mTickerPos;
    private boolean isBeingDragged = false;
    private boolean mHapticFeedback;
    private Vibrator mVibrator;

    public static final String TAG = "HaloLauncher";
    private static final boolean DEBUG = true;
    private static final int TICKER_HIDE_TIME = 5000;

	public boolean mExpanded = false;
    public boolean mTickerShowing = false;
    public boolean mSnapped = true;

    private int mScreenWidth;
    private int mScreenHeight;
    private Rect mPopUpRect;

    public Halo(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Halo(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mPm = mContext.getPackageManager();
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mHapticFeedback = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0;
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mHandler = new Handler();
        updateConstraints();
    }

    @Override
    public void onConfigurationChanged (Configuration newConfig) {
        updateConstraints();
    }

    private void updateConstraints() {
        Display display = mWindowManager.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);

        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;

        final int popupWidth;
        final int popupHeight;
        if (mScreenHeight > mScreenWidth) {
                popupWidth = (int)(mScreenWidth + 0.9f);
                popupHeight = (int)(mScreenHeight + 0.7f);
            } else {
                popupWidth = (int)(mScreenWidth + 0.7f);
                popupHeight = (int)(mScreenHeight + 0.8f);
        }

        mPopUpRect = new Rect(
            (mScreenWidth - popupWidth) / 2,
            (mScreenHeight - popupHeight) / 2,
            mScreenWidth - (mScreenWidth - popupWidth) / 2,
            mScreenHeight - (mScreenHeight - popupHeight) / 2);
    }

    public void init(BaseStatusBar bar) {
        mBar = bar;
        mBar.mTicker.setUpdateEvent(this);
        mRoot = this;

        mTickerPos = getWMParams();

        mIconSize = mContext.getResources().getDimensionPixelSize(R.dimen.halo_icon_size);

        mFrame = (ImageView) findViewById(R.id.frame);

        Bitmap frame = Bitmap.createBitmap(mIconSize, mIconSize, Bitmap.Config.ARGB_8888);
        Canvas frameCanvas = new Canvas(frame);
        Paint haloPaint = new Paint();
        haloPaint.setAntiAlias(true);
        haloPaint.setColor(0xff33b5e5);
        frameCanvas.drawCircle(mIconSize / 2, mIconSize / 2, (int)mIconSize / 2, haloPaint);

        Bitmap hole = Bitmap.createBitmap(mIconSize, mIconSize, Bitmap.Config.ARGB_8888);
        Canvas holeCanvas = new Canvas(hole);
        holeCanvas.drawARGB(0, 0, 0, 0);
        Paint holePaint = new Paint();
        holePaint.setAntiAlias(true);        
        holeCanvas.drawCircle(mIconSize / 2, mIconSize / 2, (int)((mIconSize / 2) * 0.9f), holePaint);
        holePaint.setXfermode(new PorterDuffXfermode(Mode.SRC_OUT));
        final Rect rect = new Rect(0, 0, mIconSize, mIconSize);
        holeCanvas.drawBitmap(frame, null, rect, holePaint);
        mFrame.setImageDrawable(new BitmapDrawable(mContext.getResources(), hole));

        mBackdrop = (ImageView) findViewById(R.id.backdrop);
        Bitmap backOutput = Bitmap.createBitmap(mIconSize, mIconSize, Bitmap.Config.ARGB_8888);
        Canvas backCanvas = new Canvas(backOutput);
        final Paint backPaint = new Paint();
        backPaint.setAntiAlias(true);
        backPaint.setColor(0xAA000000);
        backCanvas.drawCircle(mIconSize / 2, mIconSize / 2, (int)mIconSize / 2, backPaint);
        mBackdrop.setImageDrawable(new BitmapDrawable(mContext.getResources(), backOutput));

        mTicker = (TextView) findViewById(R.id.ticker);
        mTicker.setVisibility(View.GONE);

		mIcon = (ImageView) findViewById(R.id.app_icon);
		mIcon.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
                if (!isBeingDragged) {
                    moveToTop();
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (mHapticFeedback) mVibrator.vibrate(2);

                    try {
                        ActivityManagerNative.getDefault().resumeAppSwitches();
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
			}
		});

        mIcon.setOnTouchListener(new OnTouchListener() {
            private float initialX = 0;
            private float initialY = 0;

			@Override
			public boolean onTouch(View v, MotionEvent event) {
                final int action = event.getAction();

                switch(action) {
                    case MotionEvent.ACTION_DOWN:
                        isBeingDragged = false;
                        initialX = event.getRawX();
                        initialY = event.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float mX = event.getRawX();
                        float mY = event.getRawY();
                        float distanceX = initialX-mX;
                        float distanceY = initialY-mY;

                        if (!isBeingDragged) {
                            float distance = (float)Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));
                            if (distance > mIconSize) {
                                isBeingDragged = true;
                                if (mHapticFeedback) mVibrator.vibrate(15);
                            }
                        } else {
                            mTickerPos.x = (int)mX - mIconSize / 2;
                            mTickerPos.y = (int)mY - mIconSize / 2;

                            /*if (mTickerPos.x < mIconSize  * 2) mTickerPos.x = 0;
                            if (mTickerPos.x > mScreenWidth - mIconSize * 2) mTickerPos.x = mScreenWidth - mIconSize;
                            if (mTickerPos.y < mIconSize * 2) mTickerPos.y = 0;
                            if (mTickerPos.y > mScreenHeight - mIconSize * 2) mTickerPos.y = mScreenHeight - mIconSize;*/

                            mWindowManager.updateViewLayout(mRoot, mTickerPos);
                            return false;
                        }
                        break;
                }
                return false;
            }
		});
    }

    private void moveToTop(){
        ValueAnimator topAnimation = ValueAnimator.ofInt(0, 1);
        topAnimation.addUpdateListener(new AnimatorUpdateListener() {
            final int fromX = mTickerPos.x;
            final int fromY = mTickerPos.y;

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {

//popUpRect.top
//popUpRect.right

                mTickerPos.x = (int)(fromX + (mScreenWidth-fromX-mIconSize) * animation.getAnimatedFraction());
                mTickerPos.y = (int)(fromY * (1-animation.getAnimatedFraction()));
                mWindowManager.updateViewLayout(mRoot, mTickerPos);
            }
        });
        topAnimation.setDuration(150);
        topAnimation.setInterpolator(new DecelerateInterpolator());
        topAnimation.start();
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
        lp.gravity = Gravity.LEFT|Gravity.TOP;
        return lp;
    }
    
    public void updateTicker(Ticker.Segment segment) {
        curNotif = segment.notification.notification;
        appName = segment.notification.pkg;

        Bitmap output = Bitmap.createBitmap(mIconSize, mIconSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final Paint paint = new Paint();
        Rect rect = new Rect(0, 0, mIconSize, mIconSize);
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        Bitmap input = null;
       
        if (curNotif.largeIcon != null) {
            input = curNotif.largeIcon;
            canvas.drawCircle(mIconSize / 2, mIconSize / 2, mIconSize / 2, paint);
            paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
            canvas.drawBitmap(input, null, rect, paint);
        } else {
            try {
                BitmapDrawable icon = (BitmapDrawable)mPm.getApplicationIcon(appName);
                icon.setFilterBitmap(true);
                input = icon.getBitmap();
                rect = new Rect(
                    (int)(mIconSize * 0.2f), (int)(mIconSize * 0.2f),
                    (int)(mIconSize * 0.8f), (int)(mIconSize * 0.8f));
                canvas.drawBitmap(input, null, rect, paint);
            } catch (Exception e) {
                // NameNotFoundException
            }
        }
        mIcon.setImageDrawable(new BitmapDrawable(mContext.getResources(), output));

        mTickerShowing = true;
        mTicker.setVisibility(View.VISIBLE);
        mTicker.setText(segment.getText().toString());

        AlphaAnimation alphaUp = new AlphaAnimation(0, 1);
        alphaUp.setFillAfter(true);
        alphaUp.setDuration(1000);
        mTicker.startAnimation(alphaUp);

        mHandler.postDelayed(TickerHider, TICKER_HIDE_TIME);
    }

    private Runnable TickerHider = new Runnable() {
        public void run() {
            if (mTickerShowing) {
                AlphaAnimation alphaUp = new AlphaAnimation(1, 0);
                alphaUp.setFillAfter(true);
                alphaUp.setDuration(1000);
                alphaUp.setAnimationListener(new AnimationListener() {
                    public void onAnimationStart(Animation anim) {};
                    public void onAnimationRepeat(Animation anim) {};
                    public void onAnimationEnd(Animation anim)
                    {
                        mTicker.setVisibility(View.GONE);
                        mTickerShowing = false;
                    };
                });
                mTicker.startAnimation(alphaUp);
            }
        }
    };

}
