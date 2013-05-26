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
import android.app.INotificationManager;
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
import android.os.ServiceManager;
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
    private int mIconSize, mIconHalfSize;
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
    private static final int SLEEP_DELAY_DAYDREAMING = 3800;
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

    private ValueAnimator mSleepREM = ValueAnimator.ofInt(0, 1);
    private AlphaAnimation mSleepNap = new AlphaAnimation(1, 0.65f);
    private int mAnimationFromX;
    private int mAnimationToX;

    private float mRawX, mRawY;

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
                snapToSide(true,0);
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
        mIconSize = bd.getBitmap().getWidth();
        mIconHalfSize = mIconSize / 2;
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
        mSleepNap.setStartOffset(2000);
        mSleepNap.setDuration(1000);

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
        mHaloEffect.pingMinRadius = mIconHalfSize;
        mHaloEffect.pingMaxRadius = (int)(mIconSize * 1.1f);
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
            mTickerPos.x = mScreenWidth / 2 - mIconHalfSize;
            mTickerPos.y = mScreenHeight / 2 - mIconHalfSize;

            mHandler.postDelayed(new Runnable() {
                public void run() {
                    wakeUp(false);
                    snapToSide(true, 500);
                }}, 1500);
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

    ObjectAnimator mCurrentAnimator;
    private synchronized void wakeUp(boolean pop) {
        wakeUp(pop, 250);
    }

    private synchronized void wakeUp(boolean pop, int duration) {
        // If HALO is hidden, do nothing
        if (mHidden) return;

        unscheduleSleep();

        mContent.setVisibility(View.VISIBLE);

        if (mCurrentAnimator != null) mCurrentAnimator.cancel();
        mCurrentAnimator = ObjectAnimator.ofFloat(mContent, "translationX", 0).setDuration(duration);
        mCurrentAnimator.start();

        // First things first, make the bubble visible
        AlphaAnimation alphaUp = new AlphaAnimation(mContent.getAlpha(), 1);
        alphaUp.setFillAfter(true);
        alphaUp.setDuration(duration);
        mContent.startAnimation(alphaUp);

        // Then pop
        if (pop) {
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    mHaloEffect.causePing(mPaintHoloBlue);
                }}, duration);
            
        }
    }

    private void unscheduleSleep() {
        mHandler.removeCallbacks(Sleep);
        mSleepREM.cancel();
        mSleepNap.cancel();
    }

    Runnable Sleep = new Runnable() {
        public synchronized void run() {
            if (mCurrentAnimator != null) mCurrentAnimator.cancel();
            mCurrentAnimator = ObjectAnimator
                    .ofFloat(mContent, "translationX", mTickerLeft ? -mIconHalfSize : mIconHalfSize)
                    .setDuration(2000);
            mCurrentAnimator.start();
            mContent.startAnimation(mSleepNap);
        }};

    private void scheduleSleep(int daydreaming) {
        unscheduleSleep();

        if (isBeingDragged) return;
        mHandler.postDelayed(Sleep, daydreaming);

        // Hide ticker from sight completely if that's what the user wants
        if (mHideTicker) {
            mSleepREM.setStartDelay(SLEEP_DELAY_REM);
            mSleepREM.start();
        }
    }

    private void snapToSide(boolean sleep, final int daydreaming) {
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
        if (sleep) {
            // Make it fall asleep
            topAnimation.addListener(new Animator.AnimatorListener() {
                @Override public void onAnimationCancel(Animator animation) {}
                @Override public void onAnimationEnd(Animator animation)
                {
                    scheduleSleep(daydreaming);
                }
                @Override public void onAnimationRepeat(Animator animation) {}
                @Override public void onAnimationStart(Animator animation) {}
            });
        }
        topAnimation.setDuration(250);
        topAnimation.setInterpolator(new AccelerateInterpolator());
        topAnimation.start();
    }

    private void updatePosition() {
        try {
            mTickerLeft = (mTickerPos.x + mIconHalfSize < mScreenWidth / 2);
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
            // Probably some animation still looking to move stuff around
        }
    }

    private void updateConstraints() {
        mKillX = mScreenWidth / 2;
        mKillY = mIconHalfSize;

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

        // Frame
		mFrame = (ImageView) findViewById(R.id.frame);
        mFrame.setOnClickListener(mIconClicker);
        mFrame.setOnTouchListener(mIconTouchListener);
        mFrame.setAlpha(0.1f);

        // Number
        mNumber = (TextView) findViewById(R.id.number);
        mNumber.setVisibility(View.GONE);
    }

    OnClickListener mIconClicker = new OnClickListener() {
        @Override
		public void onClick(View v) {
            
        }
    };

    void launchTask(NotificationClicker intent) {
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
            wakeUp(false);
            snapToSide(true, 0);
            if (!isBeingDragged) {
                launchTask(mContentIntent);
            }
            return true;
        }

        @Override
        public void onLongPress(MotionEvent event) {
            if (!isBeingDragged && !mDoubleTap) {
                if (mHapticFeedback) mVibrator.vibrate(25);
                onDoubleTap(event);
            }
        }

        @Override
        public boolean onDoubleTap(MotionEvent event) {
            wakeUp(false);
            if (!mInteractionReversed) {
                mDoubleTap = true;
                mBar.mHaloTaskerActive = true;
                mBar.updateNotificationIcons();
            } else {
                // Move
                isBeingDragged = true;
                mHaloEffect.intro();
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

                    // Snap HALO when it has been dragged or tasked
                    if (isBeingDragged || mDoubleTap) {
                        snapToSide(true, 0);
                    }

                    if (mDoubleTap) {                               
                        if (mTaskIntent != null) {
                            playSoundEffect(SoundEffectConstants.CLICK);
                            launchTask(mTaskIntent);                          
                        }            
                        resetIcons();
                        mBar.mHaloTaskerActive = false;
                        mBar.updateNotificationIcons();
                        mDoubleTap = false;
                    }

                    if (isBeingDragged) mHaloEffect.outro();

                    mHaloEffect.killTicker();
                    boolean oldState = isBeingDragged;
                    isBeingDragged = false;

                    mHaloEffect.invalidate();

                    // Do we erase ourselves?
                    if (overX) {
                        Settings.System.putInt(mContext.getContentResolver(),
                                Settings.System.HALO_ACTIVE, 0);
                        return true;
                    }
                    return oldState;
                case MotionEvent.ACTION_MOVE:
                    mRawX = event.getRawX();
                    mRawY = event.getRawY();
                    float distanceX = mKillX-mRawX;
                    float distanceY = mKillY-mRawY;
                    float distanceToKill = (float)Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));
                    distanceX = initialX-mRawX;
                    distanceY = initialY-mRawY;
                    float initialDistance = (float)Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));

                    if (!mDoubleTap) {
                        // Check kill radius
                        if (distanceToKill < mIconSize) {

                            // Magnetize X
                            mTickerPos.x = (int)mKillX - mIconHalfSize;
                            mTickerPos.y = (int)(mKillY - mIconHalfSize);
                            updatePosition();
                            
                            if (!overX) {
                                if (mHapticFeedback) mVibrator.vibrate(25);
                                mHaloEffect.causePing(mPaintHoloRed);                               
                                overX = true;
                            }

                            return false;
                        } else {                            
                            overX = false;
                        }

                        // Drag
                        if (!isBeingDragged) {
                            if (initialDistance > mIconSize * 0.7f) {            
                                if (mInteractionReversed) {                                
                                    mDoubleTap = true;                  
                                    wakeUp(false);
                                    snapToSide(false, 0);
                                    // Show all available icons for easier browsing while the tasker is in motion
                                    mBar.mHaloTaskerActive = true;
                                    mBar.updateNotificationIcons();
                                } else {
                                    wakeUp(false, 0);
                                    isBeingDragged = true;
                                    mHaloEffect.intro();
                                    if (mHapticFeedback) mVibrator.vibrate(25);
                                }
                                return false;
                            }
                        } else {
                            mTickerPos.x = (int)mRawX - mIconHalfSize;
                            mTickerPos.y = (int)mRawY - mIconHalfSize;
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

                            // Invalidate the effect layer for the markers to show up
                            mHaloEffect.invalidate();

                            int items = mNotificationData.size();

                            // This will be the lenght we are going to use
                            int indexLength = (mScreenWidth - mIconSize * 2) / items;

                            int delta = (int)(mTickerLeft ? mRawX : mScreenWidth - mRawX);

                            int index = -1;
                            if (delta > (int)(mIconSize * 1.5f)) {

                                // Adjust delta
                                delta -= mIconSize * 1.5f;

                                // Calculate index
                                index = mTickerLeft ? (items - delta / indexLength) - 1 : (delta / indexLength);

                                // Watch out for margins!
                                if (index >= items) index = items - 1;
                                if (index < 0) index = 0;
                            }

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
        private int mPingX, mPingY;
        protected int pingMinRadius = 0;
        protected int pingMaxRadius = 0;
        private float mContentAlpha = 0;
        private float mCurContentAlphaUp, mCurContentAlphaDown;
        private View mContentView;
        private RelativeLayout mTickerContent;
        private TextView mTextViewR, mTextViewL;
        private boolean mPingAllowed = true;
        private boolean mSkipThrough = false;

        float mPulseFraction1 = 0;
        float mPulseFraction3 = 0;
        float mXFraction = 0, mXCurFraction = 0;

        private Bitmap mMarkerL, mMarkerR;        
        private Bitmap mXNormal;
        private Bitmap mPulse1;
        private Paint mPulsePaint1 = new Paint();
        private Paint mMarkerPaint = new Paint();
        private ValueAnimator mPulseAnim1 = ValueAnimator.ofInt(0, 1);

        private ValueAnimator tickerUp = ValueAnimator.ofInt(0, 1);
        private ValueAnimator tickerDown = ValueAnimator.ofInt(0, 1);
        private ValueAnimator pingAnim = ValueAnimator.ofInt(0, 1);

        private ValueAnimator introAnim = ValueAnimator.ofInt(0, 1);
        private ValueAnimator outroAnim = ValueAnimator.ofInt(0, 1);

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
                    R.drawable.halo_x);
            mPulse1 = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_pulse1);
            mMarkerR = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_marker_r);
            mMarkerL = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_marker_l);


            mPulsePaint1.setAntiAlias(true);
            mPulsePaint1.setAlpha(0);   
            mMarkerPaint.setAntiAlias(true);

            tickerUp.setInterpolator(new DecelerateInterpolator());
            tickerUp.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mContentAlpha = mCurContentAlphaDown = mCurContentAlphaUp
                            + (1 - mCurContentAlphaUp) * animation.getAnimatedFraction();
                    invalidate();
                }
            });

            tickerDown.setInterpolator(new DecelerateInterpolator());
            tickerDown.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mContentAlpha = mCurContentAlphaDown * (1-animation.getAnimatedFraction());
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
                    mPulsePaint1.setAlpha((int)(50*animation.getAnimatedFraction()));
                    invalidate();
                }
            });

            introAnim.setDuration(PING_TIME / 3);
            introAnim.setInterpolator(new DecelerateInterpolator());
            introAnim.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mXFraction = 1 - animation.getAnimatedFraction();
                    invalidate();
                }
            });

            outroAnim.setDuration(PING_TIME / 3);
            outroAnim.setInterpolator(new DecelerateInterpolator());
            outroAnim.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mXFraction = mXCurFraction + (1-mXCurFraction) * animation.getAnimatedFraction();
                    invalidate();
                }
            });
        }

        @Override
        public void onSizeChanged(int w, int h, int oldw, int oldh) {
            onConfigurationChanged(null);
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

        public void killTicker() {
            tickerDown.cancel();
            tickerUp.cancel();
            mCurContentAlphaDown = mContentAlpha;
            tickerDown.setDuration(250);
            tickerDown.setStartDelay(0);
            tickerDown.start();
        }

        public void ticker(String tickerText, int startDuration, boolean skipThrough) {
            if (tickerText == null || tickerText.isEmpty()) {
                killTicker();
                return;
            }

            tickerDown.cancel();
            tickerUp.cancel();

            mCurContentAlphaUp = mContentAlpha;

            mSkipThrough = skipThrough;

            mTextViewR.setText(tickerText);
            mTextViewL.setText(tickerText);
            createBubble();

            tickerUp.setDuration(startDuration);
            tickerUp.start();
            tickerDown.setDuration(1000);
            tickerDown.setStartDelay(TICKER_HIDE_TIME + startDuration);
            tickerDown.start();
        }

        public void killPing() {
            mPulseAnim1.cancel();
            pingAnim.cancel();
            pingAlpha = 0;
            mPulsePaint1.setAlpha(0);
        }

        public void causePing(Paint paint) {
            if ((!mPingAllowed && paint != mPaintHoloRed) && !mDoubleTap) return;

            mPingX = mTickerPos.x + mIconHalfSize;
            mPingY = mTickerPos.y + mIconHalfSize;

            mPingAllowed = false;
            killPing();

            mPingPaint = paint;

            int c = Color.argb(0xff, Color.red(paint.getColor()), Color.green(paint.getColor()), Color.blue(paint.getColor()));
            mPulsePaint1.setColorFilter(new PorterDuffColorFilter(c, PorterDuff.Mode.SRC_IN));

            mPulseAnim1.start();
            pingAnim.start();

            // prevent ping spam            
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    mPingAllowed = true;
                }}, 3000);
        }

        public void intro() {
            introAnim.cancel();
            outroAnim.cancel();
            mXCurFraction = mXFraction;
            introAnim.start();
        }

        public void outro() {
            introAnim.cancel();
            outroAnim.cancel();
            mXCurFraction = mXFraction;
            outroAnim.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int state;

            if (mPingPaint != null) {
                mPingPaint.setAlpha(pingAlpha);

                canvas.drawCircle(mPingX, mPingY, pingRadius, mPingPaint);

                int w = mPulse1.getWidth() + (int)(mIconSize * mPulseFraction1);
                Rect r = new Rect(mPingX - w / 2, mPingY - w / 2, mPingX + w / 2, mPingY + w / 2);
                canvas.drawBitmap(mPulse1, null, r, mPulsePaint1);
            }

            state = canvas.save();

            int y = mTickerPos.y - mTickerContent.getMeasuredHeight() + (int)(mIconSize * 0.2);

            if (!mSkipThrough && mNumber.getVisibility() != View.VISIBLE) y += (int)(mIconSize * 0.15);

            if (y < 0) y = 0;

            int x = mTickerPos.x + (int)(mIconSize * 0.92f);
            int c = mTickerContent.getMeasuredWidth();
            if (x > mScreenWidth - c) {
                x = mScreenWidth - c;
                if (mTickerPos.x > mScreenWidth - (int)(mIconSize * 1.5f) ) {
                    x = mTickerPos.x - c + (int)(mIconSize * 0.08f);
                }
            }

            state = canvas.save();
            canvas.translate(x, y);
            mTextViewL.setAlpha(mContentAlpha);
            mTextViewR.setAlpha(mContentAlpha);
            mContentView.draw(canvas);
            canvas.restoreToCount(state);

            if (isBeingDragged || outroAnim.isRunning()) {
                int killyPos = (int)(mKillY - mXNormal.getWidth() / 2 - mIconSize * mXFraction);
                mMarkerPaint.setAlpha((int)(255*(1-mXFraction)));
                canvas.drawBitmap(mXNormal, mKillX - mXNormal.getWidth() / 2, killyPos, mMarkerPaint);
            }

            if (y > 0 && mDoubleTap && mNotificationData != null && mNotificationData.size() > 0) {
                int pulseY = mTickerPos.y + mIconHalfSize - mMarkerR.getHeight() / 2;

                int items = mNotificationData.size();
                int indexLength = (mScreenWidth - mIconSize * 2) / items;

                for (int i = 0; i < items; i++) {
                    float pulseX = mTickerLeft ? (mIconSize * 1.5f + indexLength * i)
                            : (mScreenWidth - mIconSize * 1.5f - indexLength * i - mMarkerR.getWidth());
                    boolean markerState = mTickerLeft ? mRawX > pulseX : mRawX < pulseX;
                    mMarkerPaint.setAlpha(markerState ? 255 : 100);
                    canvas.drawBitmap(mTickerLeft ? mMarkerR : mMarkerL, pulseX, pulseY, mMarkerPaint);
                }
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
            mNumber.setVisibility(View.VISIBLE);
            mNumber.setText((n.number < 100) ? String.valueOf(n.number) : "99+");
        } else {
            mNumber.setVisibility(View.GONE);
        }

        // Set text
        mHaloEffect.ticker(text, duration, skipThrough);
    }

    // This is the android ticker callback
    public void updateTicker(StatusBarNotification notification, String text) {

        INotificationManager nm = INotificationManager.Stub.asInterface(
                ServiceManager.getService(Context.NOTIFICATION_SERVICE));
        boolean blacklisted = false; // default off
        try {
            blacklisted = nm.isPackageHaloBlacklisted(notification.pkg);
        } catch (android.os.RemoteException ex) {
            // this does not bode well
        }

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

                    if (!blacklisted) {
                        tick(entry, text, 1000, false);

                        // Wake up and snap
                        mHidden = false;                    
                        wakeUp(!mDoubleTap && mIsNotificationNew);
                        if (!isBeingDragged && !mDoubleTap) snapToSide(true, SLEEP_DELAY_DAYDREAMING);
                    }
                }
                break;
            }
        }
    }
}
