/*
 * Copyright (C) 2013 ParanoidAndroid.
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
import android.content.ContentResolver;
import android.content.res.Configuration;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuffColorFilter;
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
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.TranslateAnimation;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.SoundEffectConstants;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar.NotificationClicker;
import com.android.internal.statusbar.StatusBarNotification;
import com.android.internal.statusbar.StatusBarIcon;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.NotificationData;
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
    private TextView mNumber;
    private PackageManager mPm ;
    private Handler mHandler;
    private BaseStatusBar mBar;    
    private WindowManager mWindowManager;
    private View mRoot;
    private int mIconSize;
    private WindowManager.LayoutParams mTickerPos;
    private boolean isBeingDragged = false;
    private boolean mHapticFeedback;
    private Vibrator mVibrator;
    private LayoutInflater mInflater;
    private HaloEffect mHaloEffect;
    private Display mDisplay;
    private View mContent, mHaloContent;
    private NotificationData.Entry mLastNotification = null;
    private NotificationClicker mContentIntent, mTaskIntent;
    private NotificationData mNotificationData;
    private String mNotificationText = "";
    private GestureDetector mGestureDetector;

    private Paint mPaintHoloBlue = new Paint();
    private Paint mPaintWhite = new Paint();
    private Paint mPaintHoloRed = new Paint();

    public static final String TAG = "HaloLauncher";
    private static final boolean DEBUG = true;
    private static final int TICKER_HIDE_TIME = 2000;
    private static final int SLEEP_DELAY_DAYDREAMING = 5000;
    private static final int SLEEP_DELAY_REM = 10000;

	public boolean mExpanded = false;
    public boolean mSnapped = true;
    public boolean mHidden = false;
    public boolean mFirstStart = true;
    private boolean mInitialized = false;
    private boolean mTickerLeft = true;
    private boolean mNumberLeft = true;
    private boolean mIsNotificationNew = true;

    private boolean mInteractionReversed = true;
    private boolean mHideTicker = false;

    private int mScreenMin, mScreenMax;
    private int mScreenWidth, mScreenHeight;
    private Rect mPopUpRect;

    private int mKillX, mKillY;

    private ValueAnimator mSleepDaydreaming = ValueAnimator.ofInt(0, 1);
    private ValueAnimator mSleepREM = ValueAnimator.ofInt(0, 1);
    private AlphaAnimation mSleepNap = new AlphaAnimation(1, 0.65f);
    private int mAnimationFromX;
    private int mAnimationToX;

    private SettingsObserver mSettingsObserver;

    private final class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HALO_HIDE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HALO_REVERSED), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HAPTIC_FEEDBACK_ENABLED), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            mInteractionReversed =
                    Settings.System.getInt(mContext.getContentResolver(), Settings.System.HALO_REVERSED, 1) == 1;
            mHideTicker =
                    Settings.System.getInt(mContext.getContentResolver(), Settings.System.HALO_HIDE, 0) == 1;
            mHapticFeedback = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0;

            if (!selfChange) {
                wakeUp(true);
                snapToSide(true);
            }
        }
    }

    public Halo(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Halo(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mPm = mContext.getPackageManager();
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mInflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);         
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mDisplay = mWindowManager.getDefaultDisplay();
        mGestureDetector = new GestureDetector(mContext, new GestureListener());
        mHandler = new Handler();
        mRoot = this;

        mSettingsObserver = new SettingsObserver(new Handler());
        mSettingsObserver.observe();
        mSettingsObserver.onChange(true);

        // Init variables
        BitmapDrawable bd = (BitmapDrawable) mContext.getResources().getDrawable(R.drawable.halo_frame);
        mIconSize=bd.getBitmap().getWidth();
        mTickerPos = getWMParams();

        // Init colors
        mPaintHoloBlue.setAntiAlias(true);
        mPaintHoloBlue.setColor(0xff33b5e5);
        mPaintWhite.setAntiAlias(true);
        mPaintWhite.setColor(0xfff0f0f0);
        mPaintHoloRed.setAntiAlias(true);
        mPaintHoloRed.setColor(0xffcc0000);

        // Animations
        mSleepNap.setInterpolator(new DecelerateInterpolator());
        mSleepNap.setFillAfter(true);
        mSleepNap.setDuration(1000);

        mSleepDaydreaming.setDuration(1000);
        mSleepDaydreaming.setInterpolator(new AccelerateInterpolator());
        mSleepDaydreaming.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mTickerPos.x = (int)(mAnimationFromX + mAnimationToX * animation.getAnimatedFraction());
                updatePosition();
            }});
        mSleepDaydreaming.addListener(new Animator.AnimatorListener() {
            @Override public void onAnimationCancel(Animator animation) {}
            @Override public void onAnimationEnd(Animator animation)
            {
                mContent.startAnimation(mSleepNap);
            }
            @Override public void onAnimationRepeat(Animator animation) {}
            @Override public void onAnimationStart(Animator animation) 
            {
                mAnimationFromX = mTickerPos.x;
                final int setBack = mIconSize / 2;
                mAnimationToX = (mAnimationFromX < mScreenWidth / 2) ? -setBack : setBack;
            }});

        mSleepREM.setDuration(1000);
        mSleepREM.setInterpolator(new AccelerateInterpolator());
        mSleepREM.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mTickerPos.x = (int)(mAnimationFromX + mAnimationToX * animation.getAnimatedFraction());
                updatePosition();
            }});
        mSleepREM.addListener(new Animator.AnimatorListener() {
            @Override public void onAnimationCancel(Animator animation) {}
            @Override public void onAnimationEnd(Animator animation) { }
            @Override public void onAnimationRepeat(Animator animation) {}
            @Override public void onAnimationStart(Animator animation) 
            {
                mAnimationFromX = mTickerPos.x;
                mAnimationToX = (mAnimationFromX < mScreenWidth / 2) ? -mIconSize : mIconSize;
            }});

        // Create effect layer
        mHaloEffect = new HaloEffect(mContext);
        mHaloEffect.setLayerType (View.LAYER_TYPE_HARDWARE, null);
        mHaloEffect.pingMinRadius = mIconSize / 2;
        mHaloEffect.pingMaxRadius = mIconSize;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                      | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                      | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                      | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                      | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
              PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.LEFT|Gravity.TOP;
        mWindowManager.addView(mHaloEffect, lp);
    }

    private void initControl() {
        if (mInitialized) return;

        mInitialized = true;

        // Get actual screen size
        mScreenWidth = mHaloEffect.getWidth();
        mScreenHeight = mHaloEffect.getHeight();

        mScreenMin = Math.min(mHaloEffect.getWidth(), mHaloEffect.getHeight());
        mScreenMax = Math.max(mHaloEffect.getWidth(), mHaloEffect.getHeight());

        if (mFirstStart) {
            // let's put halo in sight
            mTickerPos.x = mScreenWidth / 2 - mIconSize / 2;
            mTickerPos.y = mScreenHeight / 2 - mIconSize / 2;

            mHandler.postDelayed(new Runnable() {
                public void run() {
                    wakeUp(false);
                    snapToSide(true, 500);
                }}, 1000);

        }

        // Update dimensions
        unscheduleSleep();
        updateConstraints();

        if (!mFirstStart) {
            snapToSide(true, 500);
        } else {
            // Do the startup animations only once
            mFirstStart = false;
        }
    }

    private void wakeUp(boolean pop) {
        // If HALO is hidden, do nothing
        if (mHidden) return;

        unscheduleSleep();

        mContent.setVisibility(View.VISIBLE);

        // First things first, make the bubble visible
        AlphaAnimation alphaUp = new AlphaAnimation(mContent.getAlpha(), 1);
        alphaUp.setFillAfter(true);
        alphaUp.setDuration(250);
        mContent.startAnimation(alphaUp);

        // Then pop
        if (pop) {
            mHaloEffect.causePing(mPaintHoloBlue);
        }
    }

    private void unscheduleSleep() {
        mSleepREM.cancel();
        mSleepDaydreaming.cancel();
        mSleepNap.cancel();
    }

    private void scheduleSleep(int daydreaming) {
        unscheduleSleep();

        if (isBeingDragged) return;

        mSleepDaydreaming.setStartDelay(daydreaming);
        mSleepDaydreaming.start();

        // Hide ticker from sight completely if that's what the user wants
        if (mHideTicker) {
            mSleepREM.setStartDelay(SLEEP_DELAY_REM);
            mSleepREM.start();
        }
    }

    private void snapToSide(boolean sleep) {
        snapToSide(sleep, SLEEP_DELAY_DAYDREAMING);
    }

    private void snapToSide(boolean sleep, int daydreaming) {
        final int fromX = mTickerPos.x;
        final boolean left = fromX < mScreenWidth / 2;
        final int toX = left ? -fromX : mScreenWidth-fromX-mIconSize;

        ValueAnimator topAnimation = ValueAnimator.ofInt(0, 1);
        topAnimation.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mTickerPos.x = (int)(fromX + toX * animation.getAnimatedFraction());
                updatePosition();
            }
        });
        topAnimation.setDuration(150);
        topAnimation.setInterpolator(new AccelerateInterpolator());
        topAnimation.start();

        // Make it fall asleep soon
        if (sleep) scheduleSleep(daydreaming);
    }

    private void updatePosition() {
        try {
            mTickerLeft = (mTickerPos.x + mIconSize / 2 < mScreenWidth / 2);
            if (mNumberLeft != mTickerLeft) {
                mNumberLeft = mTickerLeft;
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams
                        (RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT );
                params.addRule(mNumberLeft ? RelativeLayout.ALIGN_RIGHT : RelativeLayout.ALIGN_LEFT, mHaloContent.getId());
                mNumber.setLayoutParams(params);
                mHaloEffect.createBubble();
            }

            mWindowManager.updateViewLayout(mRoot, mTickerPos);
            mHaloEffect.invalidate();
        } catch(Exception e) {
            android.util.Log.d("PARANOID", "crash?!" + "  "+ e.getMessage());
            // Probably some animation still looking to move stuff around
        }
    }

    private void updateConstraints() {
        mKillX = mScreenWidth / 2;
        mKillY = mIconSize / 2;

        final int popupWidth;
        final int popupHeight;

        DisplayMetrics metrics = new DisplayMetrics();
        mDisplay.getMetrics(metrics);

        if (metrics.heightPixels > metrics.widthPixels) {
                popupWidth = (int)(metrics.widthPixels * 0.9f);
                popupHeight = (int)(metrics.heightPixels * 0.7f);
            } else {
                popupWidth = (int)(metrics.widthPixels * 0.7f);
                popupHeight = (int)(metrics.heightPixels * 0.8f);
        }

        mPopUpRect = new Rect(
            (mScreenWidth - popupWidth) / 2,
            (mScreenHeight - popupHeight) / 2,
            mScreenWidth - (mScreenWidth - popupWidth) / 2,
            mScreenHeight - (mScreenHeight - popupHeight) / 2);

        if (!mFirstStart) {
            if (mTickerPos.y < 0) mTickerPos.y = 0;
            if (mTickerPos.y > mScreenHeight-mIconSize) mTickerPos.y = mScreenHeight-mIconSize;
            mTickerPos.x = mTickerLeft ? 0 : mScreenWidth-mIconSize;
        }
        mWindowManager.updateViewLayout(mRoot, mTickerPos);
    }

    public void setStatusBar(BaseStatusBar bar) {
        mBar = bar;
        if (mBar.getTicker() != null) mBar.getTicker().setUpdateEvent(this);
        mNotificationData = mBar.getNotificationData();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Content
        mContent = (View) findViewById(R.id.content);
        mHaloContent = (View) findViewById(R.id.halo_content);
        
        // Icon
		mIcon = (ImageView) findViewById(R.id.app_icon);
        mIcon.setOnClickListener(mIconClicker);
        mIcon.setOnTouchListener(mIconTouchListener);

        // Frame
		mFrame = (ImageView) findViewById(R.id.frame);
        mFrame.getDrawable().setColorFilter(null);

        // Number
        mNumber = (TextView) findViewById(R.id.number);
        mNumber.setVisibility(View.GONE);
    }

    OnClickListener mIconClicker = new OnClickListener() {
        @Override
		public void onClick(View v) {
            
        }
    };

    void launchTask(NotificationClicker intent, boolean snap) {
        if (snap) snapToSide(true, 0);
        try {
            ActivityManagerNative.getDefault().resumeAppSwitches();
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
            // ...
        }

        if (intent!= null) {
            intent.onClick(mRoot);
        }
    }

    private boolean mDoubleTap = false;
    class GestureListener extends GestureDetector.SimpleOnGestureListener {
        
        @Override
        public boolean onSingleTapUp (MotionEvent event) {
            playSoundEffect(SoundEffectConstants.CLICK);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2, 
                float velocityX, float velocityY) {
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent event) {
            if (!isBeingDragged) {
                launchTask(mContentIntent, true);
                snapToSide(true);
            }
            return true;
        }

        @Override
        public void onLongPress(MotionEvent event) {
            if (mHapticFeedback && !isBeingDragged && !mDoubleTap) mVibrator.vibrate(25);
            onDoubleTap(event);
        }

        @Override
        public boolean onDoubleTap(MotionEvent event) {

            if (!mInteractionReversed) {
                mDoubleTap = true;
                snapToSide(false);
                // Show all available icons for easier browsing while the tasker is in motion
                mBar.mHaloTaskerActive = true;
                mBar.updateNotificationIcons();
            } else {
                // Move
                isBeingDragged = true;
            }
            return true;
        }
    }

    void resetIcons() {
        final float originalAlpha = mContext.getResources().getFraction(R.dimen.status_bar_icon_drawing_alpha, 1, 1);
        for (int i = 0; i < mNotificationData.size(); i++) {
            NotificationData.Entry entry = mNotificationData.get(i);            
            entry.icon.setAlpha(originalAlpha);
        }
    }

    void setIcon(int index) {
        float originalAlpha = mContext.getResources().getFraction(R.dimen.status_bar_icon_drawing_alpha, 1, 1);
        for (int i = 0; i < mNotificationData.size(); i++) {
            NotificationData.Entry entry = mNotificationData.get(i);
            entry.icon.setAlpha(index == i ? 1f : originalAlpha);
        }
    }

    OnTouchListener mIconTouchListener = new OnTouchListener() {
        private float initialX = 0;
        private float initialY = 0;
        private boolean overX = false;
        private int oldIconIndex = -1;

		@Override
		public boolean onTouch(View v, MotionEvent event) {
            mGestureDetector.onTouchEvent(event);

            final int action = event.getAction();
            switch(action) {
                case MotionEvent.ACTION_DOWN:
                    wakeUp(false);
                    // Watch out here, in reversed mode we can not overwrite the double-tap action down.
                    if (!(mInteractionReversed && isBeingDragged)) {
                        mTaskIntent = null;
                        oldIconIndex = -1;
                        isBeingDragged = false;
                        overX = false;
                        initialX = event.getRawX();
                        initialY = event.getRawY();                        
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (mDoubleTap) {                               
                        if (mTaskIntent != null) {
                            playSoundEffect(SoundEffectConstants.CLICK);
                            launchTask(mTaskIntent, false);                          
                        }            
                        resetIcons();
                        mBar.mHaloTaskerActive = false;
                        mBar.updateNotificationIcons();
                        mDoubleTap = false;
                    }                    

                    boolean oldState = isBeingDragged;
                    isBeingDragged = false;

                    // Do we erase ourselves?
                    if (overX) {
                        Settings.System.putInt(mContext.getContentResolver(),
                                Settings.System.HALO_ACTIVE, 0);
                        return true;
                    }
                    snapToSide(true);
                    return oldState;
                case MotionEvent.ACTION_MOVE:
                    float mX = event.getRawX();
                    float mY = event.getRawY();
                    float distanceX = mKillX-mX;
                    float distanceY = mKillY-mY;
                    float distanceToKill = (float)Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));
                    distanceX = initialX-mX;
                    distanceY = initialY-mY;                    
                    float initialDistance = (float)Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));

                    if (!mDoubleTap) {
                        // Check kill radius
                        if (distanceToKill < mIconSize) {

                            // Magnetize X
                            mTickerPos.x = (int)mKillX - mIconSize / 2;
                            mTickerPos.y = (int)(mKillY - mIconSize * 0.3f);
                            updatePosition();
                            
                            if (!overX) {
                                if (mHapticFeedback) mVibrator.vibrate(25);
                                mHaloEffect.causePing(mPaintHoloRed);                               
                                overX = true;
                                mFrame.getDrawable().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
                            }

                            return false;
                        } else {
                            overX = false;
                            mFrame.getDrawable().setColorFilter(null);
                            mHaloEffect.killPing();
                        }

                        // Drag
                        if (!isBeingDragged) {
                            if (initialDistance > mIconSize * 0.7f) {

                                if (mInteractionReversed) {
                                    mDoubleTap = true;
                                    snapToSide(false);
                                    // Show all available icons for easier browsing while the tasker is in motion
                                    mBar.mHaloTaskerActive = true;
                                    mBar.updateNotificationIcons();
                                } else {
                                    isBeingDragged = true;
                                    if (mHapticFeedback) mVibrator.vibrate(25);
                                }
                                return false;
                            }
                        } else {
                            mTickerPos.x = (int)mX - mIconSize / 2;
                            mTickerPos.y = (int)mY - mIconSize / 2;
                            if (mTickerPos.x < 0) mTickerPos.x = 0;
                            if (mTickerPos.y < 0) mTickerPos.y = 0;
                            if (mTickerPos.x > mScreenWidth-mIconSize) mTickerPos.x = mScreenWidth-mIconSize;
                            if (mTickerPos.y > mScreenHeight-mIconSize) mTickerPos.y = mScreenHeight-mIconSize;
                            updatePosition();
                            return false;
                        }
                    } else {
                        // Switch icons
                        if (mNotificationData != null && mNotificationData.size() > 0) {

                            int items = mNotificationData.size();

                            // This will be the lenght we are going to use
                            int indexLength = (int)(mScreenWidth - mIconSize * 1.5f) / items;
                            // If we have less than 6 notification let's cut it a bit
                            if (items < 6) indexLength = (int)(indexLength * 0.75f);
                            // If we have less than 4 notification let's cut it a bit
                            if (items < 4) indexLength = (int)(indexLength * 0.75f);

                            int totalLength = indexLength * items;

                            // This gets us the actual index
                            int distance = (int)initialDistance;
                            distance = initialDistance <= mIconSize ? 0 : (int)(initialDistance - mIconSize);
                            // We must shorten the totalLength a tiny bit to prevent number wrap ups
                            if (distance >= totalLength) distance = totalLength - 1;
                            // Reverse order depending on which side HALO sits
                            int index = mTickerLeft ? (items - distance / indexLength) - 1 : (distance / indexLength);

                            if (initialDistance <= mIconSize * 0.85f) index = -1;

                            if (index !=oldIconIndex) {
                                oldIconIndex = index;

                                // Make a tiny pop if not so many icons are present
                                if (mHapticFeedback && items < 10) mVibrator.vibrate(1);

                                try {
                                    if (index == -1) {
                                        mTaskIntent = null;
                                        resetIcons();
                                        tick(mLastNotification, mNotificationText, 250, true);

                                        // Ping to notify the user we're back where we started
                                        mHaloEffect.causePing(mPaintHoloBlue);
                                    } else {
                                        setIcon(index);

                                        NotificationData.Entry entry = mNotificationData.get(index);
                                        String text = "";
                                        if (entry.notification.notification.tickerText != null) {
                                            text = entry.notification.notification.tickerText.toString();
                                        }
                                        tick(entry, text, 250, true);

                                        mTaskIntent = entry.floatingIntent;
                                    }
                                } catch (Exception e) {
                                    // IndexOutOfBoundsException
                                }
                            }
                        }
                    }

                    break;
            }
            return false;
        }
    };

    public void cleanUp() {
        // Remove pending tasks, if we can
        unscheduleSleep();
        mHandler.removeCallbacksAndMessages(null);
        // Kill callback
        mBar.getTicker().setUpdateEvent(null);
        // Flag tasker
        mBar.mHaloTaskerActive = false;
        // Kill the effect layer
        if (mHaloEffect != null) mWindowManager.removeView(mHaloEffect);
        // Remove resolver
        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
    }

    class HaloEffect extends FrameLayout {
        private static final int PING_TIME = 1500;
        private static final int PULSE_TIME = 1500;

        private Context mContext;
        private Paint mPingPaint;
        private int pingAlpha = 0;        
        private int pingRadius = 0;
        protected int pingMinRadius = 0;
        protected int pingMaxRadius = 0;
        private float mContentAlpha = 0;
        private float mCurContentAlpha = 0;
        private View mContentView;
        private RelativeLayout mTickerContent;
        private TextView mTextViewR, mTextViewL;
        private boolean mPingAllowed = true;
        private boolean mSkipThrough = false;

        private Bitmap mXNormal;
        private Bitmap mPulse1, mPulse3;
        private Paint mPulsePaint1 = new Paint();
        private Paint mPulsePaint3 = new Paint();
        private ValueAnimator mPulseAnim1 = ValueAnimator.ofInt(0, 1);
        private ValueAnimator mPulseAnim3 = ValueAnimator.ofInt(0, 1);

        private ValueAnimator tickerUp = ValueAnimator.ofInt(0, 1);
        private ValueAnimator tickerDown = ValueAnimator.ofInt(0, 1);
        private ValueAnimator pingAnim = ValueAnimator.ofInt(0, 1);

        public HaloEffect(Context context) {
            super(context);

            mContext = context;
            setWillNotDraw(false);
            setDrawingCacheEnabled(false);

            LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE); 
            mContentView = inflater.inflate(R.layout.halo_content, null);
            mTickerContent = (RelativeLayout) mContentView.findViewById(R.id.ticker);
            mTextViewR = (TextView) mTickerContent.findViewById(R.id.bubble_r);
            mTextViewL = (TextView) mTickerContent.findViewById(R.id.bubble_l);

            mXNormal = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.ic_launcher_clear_normal_holo);
            mPulse1 = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_pulse1);
            mPulse3 = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_pulse3);

            mPulsePaint1.setAntiAlias(true);
            mPulsePaint1.setAlpha(0);
            mPulsePaint3.setAntiAlias(true);
            mPulsePaint3.setAlpha(0);
            //mPulsePaint3.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);

            tickerUp.setInterpolator(new DecelerateInterpolator());
            tickerUp.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mContentAlpha = mCurContentAlpha + (1 - mCurContentAlpha) * animation.getAnimatedFraction();
                    invalidate();
                }
            });

            tickerDown.setDuration(1000);
            tickerDown.setInterpolator(new DecelerateInterpolator());
            tickerDown.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mContentAlpha = 1-animation.getAnimatedFraction();
                    invalidate();
                }
            });

            pingAnim.setDuration(PING_TIME);
            pingAnim.setInterpolator(new DecelerateInterpolator());
            pingAnim.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    pingAlpha = (int)(200 * (1-animation.getAnimatedFraction()));
                    pingRadius = (int)((pingMaxRadius - pingMinRadius) *
                            animation.getAnimatedFraction()) + pingMinRadius;
                    invalidate();
                }
            });

            mPulseAnim1.setDuration((int)(PULSE_TIME / 2));
            mPulseAnim1.setRepeatCount(1);
            mPulseAnim1.setRepeatMode(ValueAnimator.REVERSE);
            mPulseAnim1.setInterpolator(new DecelerateInterpolator());
            mPulseAnim1.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mPulseFraction1 = animation.getAnimatedFraction();
                    mPulsePaint1.setAlpha((int)(150*animation.getAnimatedFraction()));
                    invalidate();
                }
            });

            mPulseAnim3.setDuration((int)(PULSE_TIME / 2));
            mPulseAnim3.setStartDelay(600);
            mPulseAnim3.setRepeatCount(1);
            mPulseAnim3.setRepeatMode(ValueAnimator.REVERSE);
            mPulseAnim3.setInterpolator(new DecelerateInterpolator());
            mPulseAnim3.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mPulseFraction3 = animation.getAnimatedFraction();
                    mPulsePaint3.setAlpha((int)(50*animation.getAnimatedFraction()));
                    invalidate();
                }
            });
        }

        @Override
        public void onConfigurationChanged(Configuration newConfiguration) {
            // This will reset the initialization flag
            mInitialized = false;
            // Generate a new content bubble
            createBubble();
        }

        @Override
        protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
            super.onLayout (changed, left, top, right, bottom);
            // We have our effect-layer, now let's kickstart HALO
            initControl();
        }

        public void createBubble() {
            mTextViewL.setVisibility(mTickerLeft ? View.VISIBLE : View.GONE);
            mTextViewR.setVisibility(mTickerLeft ? View.GONE : View.VISIBLE);

            mContentView.measure(MeasureSpec.getSize(mContentView.getMeasuredWidth()), MeasureSpec.getSize(mContentView.getMeasuredHeight()));
            mContentView.layout(400, 400, 400, 400);
        }

        public void ticker(String tickerText, int startDuration, boolean skipThrough) {
            tickerDown.cancel();
            tickerUp.cancel();

            if (tickerText == null || tickerText.isEmpty()) {
                mCurContentAlpha = mContentAlpha = 0;
                invalidate();
                return;
            }

            mCurContentAlpha = mContentAlpha;

            mSkipThrough = skipThrough;

            mTextViewR.setText(tickerText);
            mTextViewL.setText(tickerText);
            createBubble();

            tickerUp.setDuration(startDuration);
            tickerUp.start();
            tickerDown.setStartDelay(TICKER_HIDE_TIME + startDuration);
            tickerDown.start();
        }

        public void killPing() {
            mPulseAnim1.cancel();
            mPulseAnim3.cancel();
            pingAnim.cancel();
            pingAlpha = 0;
            mPulsePaint1.setAlpha(0);
            mPulsePaint3.setAlpha(0);
        }

        public void causePing(Paint paint) {
            if ((!mPingAllowed && paint != mPaintHoloRed) && !mDoubleTap) return;

            mPingAllowed = false;
            killPing();

            mPingPaint = paint;

            int c = Color.argb(0xff, Color.red(paint.getColor()), Color.green(paint.getColor()), Color.blue(paint.getColor()));
            mPulsePaint1.setColorFilter(new PorterDuffColorFilter(c, PorterDuff.Mode.SRC_IN));
            mPulsePaint3.setColorFilter(new PorterDuffColorFilter(c, PorterDuff.Mode.SRC_IN));

            mPulseAnim1.start();
            mPulseAnim3.start();
            pingAnim.start();

            // prevent ping spam            
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    mPingAllowed = true;
                }}, 3000);
        }

        float mPulseFraction1 = 0;
        float mPulseFraction3 = 0;

        @Override
        protected void onDraw(Canvas canvas) {
            int state;

            if (mPingPaint != null) {
                mPingPaint.setAlpha(pingAlpha);

                int x = mTickerPos.x + mIconSize / 2;
                int y = mTickerPos.y + mIconSize / 2;

                canvas.drawCircle(x, y, pingRadius, mPingPaint);

                int w = mPulse1.getWidth() + (int)(mIconSize * mPulseFraction1);
                Rect r = new Rect(x - w / 2, y - w / 2, x + w / 2, y + w / 2);
                canvas.drawBitmap(mPulse1, null, r, mPulsePaint1);
                
                w = mPulse3.getWidth() + (int)(mIconSize * 0.6f * mPulseFraction3);
                r = new Rect(x - w / 2, y - w / 2, x + w / 2, y + w / 2);
                canvas.drawBitmap(mPulse3, null, r, mPulsePaint3);
            }

            state = canvas.save();

            int y = mTickerPos.y - mTickerContent.getMeasuredHeight() + (int)(mIconSize * 0.15);

            if (!mSkipThrough && mNumber.getVisibility() != View.VISIBLE) y += (int)(mIconSize * 0.15);

            if (y < 0) y = 0;

            int x = mTickerPos.x + (int)(mIconSize * 0.85f);
            int c = mTickerContent.getMeasuredWidth();
            if (x > mScreenWidth - c) {
                x = mScreenWidth - c;
                if (mTickerPos.x > mScreenWidth - (int)(mIconSize * 1.5f) ) {
                    x = mTickerPos.x - c + (int)(mIconSize * 0.15f);
                }
            }

            state = canvas.save();
            canvas.translate(x, y);
            mTextViewL.setAlpha(mContentAlpha);
            mTextViewR.setAlpha(mContentAlpha);
            mContentView.draw(canvas);
            canvas.restoreToCount(state);

            if (isBeingDragged) {
                canvas.drawBitmap(mXNormal, mKillX - mXNormal.getWidth() / 2, mKillY, null);
            }
        }
    }

    public WindowManager.LayoutParams getWMParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.LEFT|Gravity.TOP;
        return lp;
    }

    void tick(NotificationData.Entry entry, String text, int duration, boolean skipThrough) {
        StatusBarNotification notification = entry.notification;
        Notification n = notification.notification;

        // Deal with the intent
        mContentIntent = entry.floatingIntent;

        // set the avatar
        mIcon.setImageDrawable(new BitmapDrawable(mContext.getResources(), entry.roundIcon));

        // Set Number
        if (n.number > 0) {
            // Set number gravity
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams
                    (RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT );
            params.addRule(mNumberLeft ? RelativeLayout.ALIGN_RIGHT : RelativeLayout.ALIGN_LEFT, mHaloContent.getId());
            mNumber.setLayoutParams(params);

            mNumber.setVisibility(View.VISIBLE);
            mNumber.setText((n.number < 100) ? String.valueOf(n.number) : "99+");
        } else {
            mNumber.setVisibility(View.GONE);
        }

        // Wake up and snap
        mHidden = false;
        wakeUp(!mDoubleTap && mIsNotificationNew);
        if (!isBeingDragged && !mDoubleTap) snapToSide(true);

        // Set text
        mHaloEffect.ticker(text, duration, skipThrough);
    }

    // This is the android ticker callback
    public void updateTicker(StatusBarNotification notification, String text) {

        for (int i = 0; i < mNotificationData.size(); i++) {
            NotificationData.Entry entry = mNotificationData.get(i);

            if (entry.notification == notification) {

                // No intent, no tick ...
                if (entry.notification.notification.contentIntent == null) return;

                mIsNotificationNew = true;
                if (mLastNotification != null && notification == mLastNotification.notification) {
                    // Ok, this is the same notification
                    // Let's give it a chance though, if the text has changed we allow it
                    mIsNotificationNew = !mNotificationText.equals(text);
                }

                if (mIsNotificationNew) {
                    mNotificationText = text;
                    mLastNotification = entry;
                    tick(entry, text, 1000, false);
                }
                break;
            }
        }
    }
}
