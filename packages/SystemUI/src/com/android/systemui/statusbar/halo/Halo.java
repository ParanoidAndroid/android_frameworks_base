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

package com.android.systemui.statusbar.halo;

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
import android.graphics.Paint.Style;
import android.graphics.Point;
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
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
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

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.BaseStatusBar.NotificationClicker;
import com.android.systemui.statusbar.phone.Ticker;

public class Halo extends RelativeLayout implements Ticker.TickerCallback {

	private int id;
    private String appName;

    private Context mContext;
	private LayoutInflater mLayoutInflater;
    private ImageView mIcon;
    private ImageView mFrame;
    private ImageView mBackdrop;
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
    private LayoutInflater mInflater;
    private HaloEffect mHaloEffect;
    private Display mDisplay;
    private View mContent;
    private NotificationClicker mContentIntent = null;

    private Paint mPaintHoloBlue = new Paint();
    private Paint mPaintHoloRed = new Paint();

    public static final String TAG = "HaloLauncher";
    private static final boolean DEBUG = true;
    private static final int TICKER_HIDE_TIME = 5000;
    private static final int SLEEP_TIME_YAWNING = 6000;
    private static final int SLEEP_TIME_DAYDREAMING = 12000;
    private static final int SLEEP_TIME_NAP = 20000;

	public boolean mExpanded = false;
    public boolean mSnapped = true;

    private int mScreenMin, mScreenMax;
    private int mScreenWidth, mScreenHeight;
    private Rect mPopUpRect;

    private Bitmap mXActive, mXNormal;
    private int mKillX, mKillY;

    private boolean mInitialized = false;

    public Halo(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Halo(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mPm = mContext.getPackageManager();
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mInflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE); 
        mHapticFeedback = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0;
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mDisplay = mWindowManager.getDefaultDisplay();
        mHandler = new Handler();
        mRoot = this;

        // Init variables
        mIconSize = mContext.getResources().getDimensionPixelSize(R.dimen.halo_icon_size);
        mTickerPos = getWMParams();

        // Init colors
        mPaintHoloBlue.setAntiAlias(true);
        mPaintHoloBlue.setColor(0xff33b5e5);
        mPaintHoloRed.setAntiAlias(true);
        mPaintHoloRed.setColor(0xffcc0000);

        // Bitmaps
        mXActive = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.ic_launcher_clear_active_holo);

        mXNormal = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.ic_launcher_clear_normal_holo);

        // Create effect layer
        mHaloEffect = new HaloEffect(mContext);
        mHaloEffect.rippleMinRadius = mIconSize / 2;
        mHaloEffect.rippleMaxRadius = mIconSize * 2;
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

        // let's put halo in sight
        mTickerPos.x = mScreenWidth / 2 - mIconSize / 2;
        mTickerPos.y = mScreenHeight / 2 - mIconSize / 2;

        // Update dimensions
        updateConstraints();

        // pop a hello ripple
        mHandler.postDelayed(new Runnable() {
            public void run() {
                mHaloEffect.causeRipple(mPaintHoloRed);
            }}, 200);

        // and disappear if empty
        mHandler.postDelayed(new Runnable() {
            public void run() {
                if (curNotif == null) {
                    AlphaAnimation alphaDown = new AlphaAnimation(1, 0);
                    alphaDown.setFillAfter(true);
                    alphaDown.setDuration(1000);
                    mContent.startAnimation(alphaDown);
                }
            }}, 1000);
    }

    private void wakeUp(boolean pop) {
        unscheduleSleep();

        // If bubble is not being dragged snap it
        if (!isBeingDragged) snapToSide(false);

        // First things first, make the bubble visible
        AlphaAnimation alphaUp = new AlphaAnimation(mContent.getAlpha(), 1);
        alphaUp.setFillAfter(true);
        alphaUp.setDuration(250);
        mContent.startAnimation(alphaUp);

        // Then pop
        if (pop) {
            mHaloEffect.causeRipple(mPaintHoloBlue);
        }
    }

    private void unscheduleSleep() {
        mHandler.removeCallbacks(SleepYawning);
        mHandler.removeCallbacks(SleepDaydreaming);
        mHandler.removeCallbacks(SleepNap);
    }

    private void scheduleSleep() {
        unscheduleSleep();
        // Pop HALO aside just a little
        mHandler.postDelayed(SleepYawning, SLEEP_TIME_YAWNING);
        // ...little bit more
        mHandler.postDelayed(SleepDaydreaming, SLEEP_TIME_DAYDREAMING);
        // ...now fade
        mHandler.postDelayed(SleepNap, SLEEP_TIME_NAP);
    }

    private Runnable SleepYawning = new Runnable() {
        public void run() {
            final int fromX = mTickerPos.x;
            final int setBack = (int)(mIconSize * 0.3f);
            final int toX = (fromX < mScreenWidth / 2) ? -setBack : setBack;
            ValueAnimator topAnimation = ValueAnimator.ofInt(0, 1);
            topAnimation.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mTickerPos.x = (int)(fromX + toX * animation.getAnimatedFraction());
                    updatePosition();
                }
            });
            topAnimation.setDuration(1000);
            topAnimation.setInterpolator(new DecelerateInterpolator());
            topAnimation.start();
        }
    };

    private Runnable SleepDaydreaming = new Runnable() {
        public void run() {
            final int fromX = mTickerPos.x;
            final int setBack = (int)(mIconSize * 0.3f);
            final int toX = (fromX < mScreenWidth / 2) ? -setBack : setBack;
            ValueAnimator topAnimation = ValueAnimator.ofInt(0, 1);
            topAnimation.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mTickerPos.x = (int)(fromX + toX * animation.getAnimatedFraction());
                    updatePosition();
                }
            });
            topAnimation.setDuration(1000);
            topAnimation.setInterpolator(new DecelerateInterpolator());
            topAnimation.start();
        }
    };

    private Runnable SleepNap = new Runnable() {
        public void run() {
            AlphaAnimation alphaDown = new AlphaAnimation(mContent.getAlpha(), 0.5f);
            alphaDown.setFillAfter(true);
            alphaDown.setDuration(1000);
            mContent.startAnimation(alphaDown);
        }
    };

    private void moveToTop() {
        // We don't want halo to fly aside here
        unscheduleSleep();

        ValueAnimator topAnimation = ValueAnimator.ofInt(0, 1);
        topAnimation.addUpdateListener(new AnimatorUpdateListener() {

            final int fromX = mTickerPos.x;
            final int fromY = mTickerPos.y;
            final int deltaX = (mPopUpRect.right - mIconSize) - fromX;
            final int deltaY = (mPopUpRect.top - mIconSize) - fromY;

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mTickerPos.x = (int)(fromX + deltaX * animation.getAnimatedFraction());
                mTickerPos.y = (int)(fromY + deltaY * animation.getAnimatedFraction());
                updatePosition();
            }
        });
        topAnimation.setDuration(150);
        topAnimation.setInterpolator(new DecelerateInterpolator());
        topAnimation.start();
    }

    private void snapToSide(boolean sleep) {
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
        topAnimation.setInterpolator(new DecelerateInterpolator());
        topAnimation.start();

        // Make it fall asleep soon
        if (sleep) scheduleSleep();
    }

    private void updatePosition() {
        try {
            mWindowManager.updateViewLayout(mRoot, mTickerPos);
            mHaloEffect.invalidate();
        } catch(Exception e) {
            // Probably some animation still looking to move stuff around
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfiguration) {
        super.onConfigurationChanged(newConfiguration);
        mScreenWidth = newConfiguration.orientation == Configuration.ORIENTATION_PORTRAIT
                ? mScreenMin : mScreenMax;
        mScreenHeight = newConfiguration.orientation == Configuration.ORIENTATION_PORTRAIT
                ? mScreenMax : mScreenMin;
        updateConstraints();
        wakeUp(false);
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

        if (mTickerPos.x < 0) mTickerPos.x = 0;
        if (mTickerPos.y < 0) mTickerPos.y = 0;
        if (mTickerPos.x > mScreenWidth-mIconSize) mTickerPos.x = mScreenWidth-mIconSize;
        if (mTickerPos.y > mScreenHeight-mIconSize) mTickerPos.y = mScreenHeight-mIconSize;
        mWindowManager.updateViewLayout(mRoot, mTickerPos);
    }

    public void setStatusBar(BaseStatusBar bar) {
        mBar = bar;
        mBar.getTicker().setUpdateEvent(this);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Content
        mContent = (View) findViewById(R.id.content);
        
        // Icon
		mIcon = (ImageView) findViewById(R.id.app_icon);
        mIcon.setOnClickListener(mIconClicker);
        mIcon.setOnTouchListener(mIconTouchListener);

        // Frame
        mFrame = (ImageView) findViewById(R.id.frame);
        Bitmap frame = Bitmap.createBitmap(mIconSize, mIconSize, Bitmap.Config.ARGB_8888);
        Canvas frameCanvas = new Canvas(frame);
        frameCanvas.drawCircle(mIconSize / 2, mIconSize / 2, (int)mIconSize / 2, mPaintHoloBlue);
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

        // Background
        mBackdrop = (ImageView) findViewById(R.id.backdrop);
        Bitmap backOutput = Bitmap.createBitmap(mIconSize, mIconSize, Bitmap.Config.ARGB_8888);
        Canvas backCanvas = new Canvas(backOutput);
        final Paint backPaint = new Paint();
        backPaint.setAntiAlias(true);
        backPaint.setColor(0xAA000000);
        backCanvas.drawCircle(mIconSize / 2, mIconSize / 2, (int)mIconSize / 2, backPaint);
        mBackdrop.setImageDrawable(new BitmapDrawable(mContext.getResources(), backOutput));
    }

    OnClickListener mIconClicker = new OnClickListener() {
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
                    // ...
                }

                if (mContentIntent!= null) {
                    mContentIntent.onClick(mRoot);
                }
            }
        }
    };

    OnTouchListener mIconTouchListener = new OnTouchListener() {
        private float initialX = 0;
        private float initialY = 0;
        private boolean overX = false;

		@Override
		public boolean onTouch(View v, MotionEvent event) {
            final int action = event.getAction();
            switch(action) {
                case MotionEvent.ACTION_DOWN:
                    isBeingDragged = false;
                    overX = false;
                    initialX = event.getRawX();
                    initialY = event.getRawY();
                    wakeUp(false);
                    break;
                case MotionEvent.ACTION_UP:
                    isBeingDragged = false;

                    // Do we erase ourselves?
                    if (overX) {
                        Settings.System.putInt(mContext.getContentResolver(),
                                Settings.System.HALO_ACTIVE, 0);
                        return true;
                    }
                    snapToSide(true);
                    break;
                case MotionEvent.ACTION_MOVE:
                    float mX = event.getRawX();
                    float mY = event.getRawY();
                    float distanceX = mKillX-mX;
                    float distanceY = mKillY-mY;
                    float distance = (float)Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));
                    if (distance < mIconSize) {
                        if (!overX) {
                            if (mHapticFeedback) mVibrator.vibrate(25);
                            mHaloEffect.causeRipple(mPaintHoloRed);
                            overX = true;
                        }
                    } else {
                            overX = false;
                    }

                    distanceX = initialX-mX;
                    distanceY = initialY-mY;
                    if (!isBeingDragged) {
                        distance = (float)Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));
                        if (distance > mIconSize) {
                            isBeingDragged = true;
                            if (mHapticFeedback) mVibrator.vibrate(25);
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
                break;
            }
            return false;
        }
    };

    public void cleanUp() {
        // Remove pending tasks, if we can
        mHandler.removeCallbacksAndMessages(null);
        // Kill the effect layer
        if (mHaloEffect != null) mWindowManager.removeView(mHaloEffect);
    }

    class HaloEffect extends FrameLayout {
        private Context mContext;
        private Paint mRipplePaint;
        private int rippleAlpha = 0;
        private int rippleRadius = 0;
        protected int rippleMinRadius = 0;
        protected int rippleMaxRadius = 0;

        private int mContentAlpha = 0;

        private View mContentView;
        private TextView mTextView;

        public HaloEffect(Context context) {
            super(context);

            mContext = context;
            setWillNotDraw(false);
            setDrawingCacheEnabled(false);

            LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE); 
            mContentView = inflater.inflate(R.layout.halo_content, null);
            mTextView = (TextView) mContentView.findViewById(R.id.ticker);
        }

        @Override
        protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
            super.onLayout (changed, left, top, right, bottom);

            // We have our effect-layer, now let's kickstart HALO
            initControl();
        }

        public void ticker(String tickerText) {
            mTextView.setText(tickerText);
            mContentView.measure(MeasureSpec.getSize(mContentView.getMeasuredWidth()), MeasureSpec.getSize(mContentView.getMeasuredHeight()));
            mContentView.layout(400, 400, 400, 400);

            ValueAnimator alphaUp = ValueAnimator.ofInt(0, 1);
            alphaUp.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mContentAlpha = (int)(255 * animation.getAnimatedFraction());
                    invalidate();
                }
            });
            alphaUp.setDuration(1000);
            alphaUp.setInterpolator(new DecelerateInterpolator());
            alphaUp.start();

            mHandler.postDelayed(new Runnable() {
                public void run() {
                        final int currentAlpha = mContentAlpha;
                        ValueAnimator alphaDown = ValueAnimator.ofInt(0, 1);
                        alphaDown.addUpdateListener(new AnimatorUpdateListener() {
                            @Override
                            public void onAnimationUpdate(ValueAnimator animation) {
                                mContentAlpha = (int)(currentAlpha * (1-animation.getAnimatedFraction()));
                                invalidate();
                            }
                        });
                        alphaDown.setDuration(1000);
                        alphaDown.setInterpolator(new DecelerateInterpolator());
                    alphaDown.start();
                }
            }, TICKER_HIDE_TIME);
        }

        public void causeRipple(Paint paint) {
            mRipplePaint = paint;
            ValueAnimator rippleAnim = ValueAnimator.ofInt(0, 1);
            rippleAnim.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    rippleAlpha = (int)(200 * (1-animation.getAnimatedFraction()));
                    rippleRadius = (int)((rippleMaxRadius - rippleMinRadius) *
                            animation.getAnimatedFraction()) + rippleMinRadius;
                    invalidate();
                }
            });
            rippleAnim.setDuration(1500);
            rippleAnim.setInterpolator(new DecelerateInterpolator());
            rippleAnim.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int state;

            if (rippleAlpha > 0) {
                mRipplePaint.setAlpha(rippleAlpha);
                canvas.drawCircle(mTickerPos.x + mIconSize / 2, mTickerPos.y + mIconSize / 2, rippleRadius, mRipplePaint);
            }

            if (mContentAlpha > 0) {
                state = canvas.save();

                int y = mTickerPos.y - mIconSize / 2;
                if (y < 0) y = 0;

                int x = mTickerPos.x + (int)(mIconSize * (y == 0 ? 1 : 0.7f));
                int c = mContentView.getMeasuredWidth();
                if (x > mScreenWidth - c) {
                    x = mScreenWidth - c;
                    if (mTickerPos.x > mScreenWidth - (int)(mIconSize * 1.5f) ) x = mTickerPos.x - c + (int)(mIconSize * (y == 0 ? 0 : 0.3f));
                }

                canvas.translate(x, y);
                mTextView.getBackground().setAlpha(mContentAlpha);
                mTextView.setTextColor(Color.argb(mContentAlpha, 0xff, 0x80, 0x00));
                mContentView.draw(canvas);
                canvas.restoreToCount(state);
            }

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
    
    public void updateTicker(Ticker.Segment segment) {
        mContentIntent = null;

        final PendingIntent contentIntent = segment.notification.notification.contentIntent;
        if (contentIntent == null) return;

        wakeUp(true);

        mContentIntent = mBar.makeClicker(contentIntent,
                segment.notification.pkg, segment.notification.tag, segment.notification.id);
        mContentIntent.makeFloating(true);

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
                Drawable icon = segment.icon;
                if (icon == null) icon = mPm.getApplicationIcon(appName);
                icon.setBounds((int)(mIconSize * 0.25f), (int)(mIconSize * 0.25f),
                        (int)(mIconSize * 0.75f), (int)(mIconSize * 0.75f));            
                icon.draw(canvas);
            } catch (Exception e) {
                // NameNotFoundException
            }
        }
        mIcon.setImageDrawable(new BitmapDrawable(mContext.getResources(), output));

        if (segment != null && segment.getText() != null) {
            mHaloEffect.ticker(segment.getText().toString());
        }
    }
}
