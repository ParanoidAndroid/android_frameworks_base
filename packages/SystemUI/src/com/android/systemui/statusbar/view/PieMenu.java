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

package com.android.systemui.statusbar.view;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.animation.TimeInterpolator;
import android.database.ContentObserver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.LightingColorFilter;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.ColorUtils;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewManager;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;

import com.android.systemui.R;
import com.android.systemui.statusbar.PieControl;
import com.android.systemui.statusbar.PieControlPanel;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.policy.PiePolicy;

import java.util.ArrayList;
import java.util.List;

public class PieMenu extends FrameLayout {

    private static final int BACKGROUND_COLOR = 0xCC;
    private static final int ANIMATION_IN = 3000;
    private static final int ANIMATION_OUT = 0;

    private static final int COLOR_DEFAULT_BACKGROUND = 0xAAFF005E;
    private static final int COLOR_DEFAULT_SELECT = 0xAADBFF00;
    private static final int COLOR_DEFAULT_BUTTONS = 0xB2FFFFFF;
    private static final int COLOR_DEFAULT_STATUS = 0xFFFFFFFF;
    private static final int COLOR_DEFAULT_BATTERY_JUICE = 0x33b5e5;
    private static final int COLOR_DEFAULT_BATTERY_JUICE_LOW = 0xffbb33;
    private static final int COLOR_DEFAULT_BATTERY_JUICE_CRITICAL = 0xff4444;
    private static final int COLOR_DEFAULT_BATTERY_BACKGROUND = 0xFFFFFF;

    // A view like object that lives off of the pie menu
    public interface PieView {
        public interface OnLayoutListener {
            public void onLayout(int ax, int ay, boolean left);
        }
        public void setLayoutListener(OnLayoutListener l);
        public void layout(int anchorX, int anchorY, boolean onleft, float angle,
                int parentHeight);
        public void draw(Canvas c);
        public boolean onTouchEvent(MotionEvent evt);
    }

    // System
    private WindowManager mWindowManager;
    private Context mContext;
    private PiePolicy mPolicy;
    private Vibrator mVibrator;

    // Geometry
    private Point mCenter = new Point(0, 0);
    private int mRadius;
    private int mRadiusInc;
    private int mSlop;
    private int mTouchOffset;

    private List<PieItem> mItems;
    private PieView mPieView;

    // touch handling
    private PieItem mCurrentItem;
    private PieControlPanel mPanel;

    // Colors
    private ColorUtils.ColorSettingInfo mLastBackgroundColor;
    private ColorUtils.ColorSettingInfo mLastGlowColor;
    private Paint mNormalPaint;
    private Paint mSelectedPaint;
    private Paint mBatteryJuice;
    private Paint mBatteryBackground;
    private Paint mStatusPaint;

    // Animations
    private ValueAnimator mIntoAnimation;
    private ValueAnimator mOutroAnimation;
    private int mBackgroundOpacity = 0;
    private float mTextOffset = 0;
    private int mTextAlpha = 0;
    private float mCharOffset[];
    private int mGlowOffset = 0;
    int mBatteryBackgroundAlpha = 0;
    int mBatteryJuiceAlpha = 0;
    int mBatteryMeter = 0;

    // Flags
    private boolean mPanelActive;
    private boolean mPanelParentChanged;
    private boolean mPerAppColor;
    private boolean mOpen;
    private boolean mStatusAnimate;
    private boolean mGlowColorHelper;
    private int mStatusMode = 2;

    // Layout and UI
    private ViewManager mPanelParent;
    private ScrollView mScrollView;
    private View mContainer;
    private View mContentFrame;

    private Path mStatusPath;
    private float mTextLen = 0;
    private String mStatusText;

    public PieMenu(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public PieMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PieMenu(Context context) {
        super(context);
        init(context);
    }

    private void init(Context ctx) {
        mContext = ctx;
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mPolicy = new PiePolicy(mContext);

        mItems = new ArrayList<PieItem>();
        Resources res = ctx.getResources();
        mRadius = (int) res.getDimension(R.dimen.pie_radius_start);
        mRadiusInc = (int) res.getDimension(R.dimen.pie_radius_increment);
        mSlop = (int) res.getDimension(R.dimen.pie_slop);
        mTouchOffset = (int) res.getDimension(R.dimen.pie_touch_offset);
        setWillNotDraw(false);
        setDrawingCacheEnabled(false);

        mNormalPaint = new Paint();
        mNormalPaint.setAntiAlias(true);
        mNormalPaint.setColor(COLOR_DEFAULT_BACKGROUND);

        mSelectedPaint = new Paint();
        mSelectedPaint.setAntiAlias(true);
        mSelectedPaint.setColor(COLOR_DEFAULT_SELECT);

        mBatteryJuice = new Paint();
        mBatteryJuice.setAntiAlias(true);
        mBatteryJuice.setColor(COLOR_DEFAULT_BATTERY_JUICE);

        mBatteryBackground = new Paint();
        mBatteryBackground.setAntiAlias(true);
        mBatteryBackground.setColor(COLOR_DEFAULT_BATTERY_BACKGROUND);

        mStatusPaint = new Paint();
        mStatusPaint.setColor(COLOR_DEFAULT_STATUS);
        mStatusPaint.setStyle(Paint.Style.FILL);
        mStatusPaint.setTextSize(150);
        mStatusPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));


        // Circle status text
        mCharOffset = new float[25];
        for (int i = 0; i < mCharOffset.length; i++) {
            mCharOffset[i] = 1000;
        }

        mStatusText = mPolicy.getSimpleTime();
        mTextLen = mStatusPaint.measureText(mStatusText, 0, mStatusText.length());
        mPolicy.setOnClockChangedListener(new PiePolicy.OnClockChangedListener() {
            public void onChange(String s) {
                mStatusText = s;
                mTextLen = mStatusPaint.measureText(mStatusText, 0, mStatusText.length());
            }
        });

        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE); 
        mContainer = inflater.inflate(R.layout.pie_notification_panel, null);
        mContentFrame = (View) mContainer.findViewById(R.id.content_frame);
        mScrollView = (ScrollView) mContainer.findViewById(R.id.notification_scroll);
        mScrollView.setOnTouchListener(new OnTouchListener(){

            final int SCROLLING_DISTANCE_TRIGGER = 100;
            float scrollY;
            boolean scrolled;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        scrollY = event.getY();
                        scrolled = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float distance = event.getY() - scrollY;
                        if(Math.abs(distance) > SCROLLING_DISTANCE_TRIGGER) {
                            scrolled = true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if(!scrolled) {
                            hideNotificationsPanel();
                        }
                        break;
                }
                return false;
            }                               
        });

        mLastBackgroundColor = new ColorUtils.ColorSettingInfo();
        mLastGlowColor = new ColorUtils.ColorSettingInfo();

        // Only watch for per app color changes when the setting is in check
        if (ColorUtils.getPerAppColorState(mContext)) {
            setBackgroundColor();
            setGlowColor();

            // Listen for nav bar color changes
            mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.NAV_BAR_COLOR), false, new ContentObserver(new Handler()) {
                    @Override
                    public void onChange(boolean selfChange) {
                        setBackgroundColor();
                    }});

            // Listen for button glow color changes
            mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.NAV_GLOW_COLOR), false, new ContentObserver(new Handler()) {
                    @Override
                    public void onChange(boolean selfChange) {
                        setGlowColor();
                    }});
        }
    }

    public void setPanel(PieControlPanel panel) {
        mPanel = panel;
        mPanelParent = (ViewManager)mPanel.getBar().getNotificationRowLayout().getParent();
        mPanelParentChanged = false;
    }

    public void addItem(PieItem item) {
        // add the item to the pie itself
        mItems.add(item);
    }

    public void removeItem(PieItem item) {
        mItems.remove(item);
    }

    public void clearItems() {
        mItems.clear();
    }

    private boolean onTheTop() {
        return mCenter.y < mSlop;
    }

    public void show(boolean show) {
        mOpen = show;
        if (mOpen) {
            // ensure clean state
            mStatusMode = Settings.System.getInt(mContext.getContentResolver(), Settings.System.PIE_MODE, 2);
            mPerAppColor = Settings.System.getInt(mContext.getContentResolver(), Settings.System.PIE_PAC, 0) == 1;

            if (!mPerAppColor) {
                mNormalPaint.setColor(COLOR_DEFAULT_BACKGROUND);
                mSelectedPaint.setColor(COLOR_DEFAULT_SELECT);
                mStatusPaint.setColor(COLOR_DEFAULT_STATUS);

                // To-do: tint battery perhaps?
                //mBatteryJuice.setColor(COLOR_DEFAULT_BATTERY_JUICE);
                //mBatteryBackground.setColor(COLOR_DEFAULT_BATTERY_BACKGROUND);
            }

            mCurrentItem = null;
            mPieView = null;
            for (PieItem item : mItems) {
                item.setSelected(false);
            }
            layoutPie();
        }
        invalidate();
    }

    public void setCenter(int x, int y) {
        mCenter.y = y;
        mCenter.x = x;
        mStatusPath = new Path();
        mStatusPath.addCircle(mCenter.x, mCenter.y, mRadius+mRadiusInc, Path.Direction.CW);
    }

    private void setBackgroundColor() {
        ColorUtils.ColorSettingInfo colorInfo = ColorUtils.getColorSettingInfo(mContext,
                Settings.System.NAV_BAR_COLOR);
        if (!colorInfo.lastColorString.equals(mLastBackgroundColor.lastColorString) && mPerAppColor) {
            int colorRgb = ColorUtils.extractRGB(colorInfo.lastColor);
            mNormalPaint.setColor(colorRgb | 0xAA000000);
            mLastBackgroundColor = colorInfo;
        }
    }

    private void setGlowColor() {
        ColorUtils.ColorSettingInfo colorInfo = ColorUtils.getColorSettingInfo(mContext,
                Settings.System.NAV_GLOW_COLOR);
        if (!colorInfo.lastColorString.equals(mLastGlowColor.lastColorString) && mPerAppColor) {
            ColorUtils.ColorSettingInfo buttonColorInfo = ColorUtils.getColorSettingInfo(mContext,
                    Settings.System.NAV_BUTTON_COLOR);

            // This helps us to discern when glow has the same color as the button color,
            // in which case we have to counteract in order to prevent both from swallowing each other
            int glowRgb = ColorUtils.extractRGB(colorInfo.lastColor);
            int buttonRgb = ColorUtils.extractRGB(buttonColorInfo.lastColor);
            mGlowColorHelper = glowRgb == buttonRgb;
            mSelectedPaint.setColor(glowRgb | 0xAA000000);
            mStatusPaint.setColor(glowRgb);
            mLastGlowColor = colorInfo;
        }
    }

    public void setDrawingAlpha(Paint paint, float x) {
        paint.setAlpha((int) (x * 255));
    }

    private void layoutPie() {
        float emptyangle = (float) Math.PI / 16;
        int rgap = 2;
        int inner = mRadius + rgap;
        int outer = mRadius + mRadiusInc - rgap;
        int gap = 1;

        int lesserSweepCount = 0;
        for (PieItem item : mItems) {
            if (item.isLesser()) {
                lesserSweepCount += 1;
            }
        }
        float adjustedSweep = lesserSweepCount > 0 ? (((1-0.65f) * lesserSweepCount) / (mItems.size()-lesserSweepCount)) : 0;    
        
        float sweep = 0;
        float angle = 0;
        float total = 0;

        for (PieItem item : mItems) {
            sweep = ((float) (Math.PI - 2 * emptyangle) / mItems.size()) * (item.isLesser() ? 0.65f : 1 + adjustedSweep);
            angle = (emptyangle + sweep / 2 - (float)Math.PI/2);
            item.setPath(makeSlice(getDegrees(0) - gap, getDegrees(sweep) + gap, outer, inner, mCenter));
            View view = item.getView();

            if (view != null) {
                view.measure(view.getLayoutParams().width, view.getLayoutParams().height);
                int w = view.getMeasuredWidth();
                int h = view.getMeasuredHeight();
                int r = inner + (outer - inner) * 2 / 3;
                int x = (int) (r * Math.sin(total + angle));
                int y = (int) (r * Math.cos(total + angle));

                switch(mPanel.getOrientation()) {
                    case Gravity.LEFT:
                        y = mCenter.y - (int) (r * Math.sin(total + angle)) - h / 2;
                        x = (int) (r * Math.cos(total + angle)) - w / 2;
                        break;
                    case Gravity.RIGHT:
                        y = mCenter.y - (int) (Math.PI/2-r * Math.sin(total + angle)) - h / 2;
                        x = mCenter.x - (int) (r * Math.cos(total + angle)) - w / 2;
                        break;
                    case Gravity.TOP:
                        y = y - h / 2;
                        x = mCenter.x - (int)(Math.PI/2-x) - w / 2;
                        break;
                    case Gravity.BOTTOM: 
                        y = mCenter.y - y - h / 2;
                        x = mCenter.x - x - w / 2;
                        break;
                }                
                view.layout(x, y, x + w, y + h);
            }                    
            float itemstart = total + angle - sweep / 2;
            item.setGeometry(itemstart, sweep, inner, outer);
            total += sweep;
        }
    }

    // param angle from 0..PI to Android degrees (clockwise starting at 3
    private float getDegrees(double angle) {
        return (float) (270 - 180 * angle / Math.PI);
    }

    class customAnimatorUpdateListener implements ValueAnimator.AnimatorUpdateListener {
        private int mIndex = 0;
        public customAnimatorUpdateListener(int index) {
            mIndex = index;
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            mCharOffset[mIndex] = (float)((1 - animation.getAnimatedFraction()) * 1000);
            invalidate();
        }
    }

    private void animateIn() {
        // Reset base values
        mBatteryMeter = 0;
        mBatteryBackgroundAlpha = 0;
        mBatteryJuiceAlpha = 0;
        mTextAlpha = 0;
        mBackgroundOpacity = 0;
        mCharOffset = new float[25];
        for (int i = 0; i < mCharOffset.length; i++) {
            mCharOffset[i] = 1000;
        }

        // Decides the color of battery bar, depending on battery level
        final int batteryLevel = mPolicy.getBatteryLevel();
        if(batteryLevel <= PiePolicy.LOW_BATTERY_LEVEL
                && batteryLevel > PiePolicy.CRITICAL_BATTERY_LEVEL) {
            mBatteryJuice.setColor(COLOR_DEFAULT_BATTERY_JUICE_LOW);
        } else if(batteryLevel <= PiePolicy.CRITICAL_BATTERY_LEVEL) {
            mBatteryJuice.setColor(COLOR_DEFAULT_BATTERY_JUICE_CRITICAL);
        } else {
            mBatteryJuice.setColor(COLOR_DEFAULT_BATTERY_JUICE);
        }

        // Background
        mIntoAnimation = ValueAnimator.ofInt(0, 1);
        mIntoAnimation.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mBackgroundOpacity = (int)(animation.getAnimatedFraction() * BACKGROUND_COLOR);
                mBatteryBackgroundAlpha = (int)(animation.getAnimatedFraction() * 0x55);
                mBatteryJuiceAlpha = (int)(animation.getAnimatedFraction() * 0x88);
                invalidate();
            }
        });
        mIntoAnimation.setDuration(ANIMATION_IN);
        mIntoAnimation.setInterpolator(new DecelerateInterpolator());
        mIntoAnimation.start();

        // Background
        ValueAnimator animation = ValueAnimator.ofInt(0, 1);
        animation.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mBatteryMeter = (int)(animation.getAnimatedFraction() * (batteryLevel * 0.9f));
                invalidate();
            }
        });
        animation.setDuration(1500);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.start();

        int textLen = mStatusText.length();
        for(int i = 0; i < textLen; i++) {
            // Text alpha
            if (i == 0) {
                ValueAnimator mTextAlphaAnimation  = ValueAnimator.ofInt(0, 1);
                mTextAlphaAnimation.addUpdateListener(new AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        mTextAlpha = (int)(animation.getAnimatedFraction() * 255);
                        invalidate();
                    }
                });
                mTextAlphaAnimation.setDuration(2000);
                mTextAlphaAnimation.setInterpolator(new AccelerateInterpolator());
                mTextAlphaAnimation.start();
            }

            // Chracters falling into place
            ValueAnimator mTextAnimation = ValueAnimator.ofInt(0, 1);
            mTextAnimation.addUpdateListener(new customAnimatorUpdateListener(i));
            mTextAnimation.setDuration(1000 - 800 / (i + 2));
            mTextAnimation.setInterpolator(new AccelerateInterpolator());
            mTextAnimation.start();
        }
    }

    public void animateOut() {
        mStatusAnimate = false;
        if (mIntoAnimation != null && mIntoAnimation.isRunning()) {
            mIntoAnimation.cancel();
        }

        final int currentAlpha = mTextAlpha;
        final float currentOffset = mTextOffset;
        final int currentOpacity = mBackgroundOpacity;
        mOutroAnimation = ValueAnimator.ofInt(1, 0);
        mOutroAnimation.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mBackgroundOpacity = (int)((1 - animation.getAnimatedFraction()) * currentOpacity);                
                mTextAlpha =  (int)((1 - animation.getAnimatedFraction()) * currentAlpha);
                invalidate();
            }
        });
        mOutroAnimation.setDuration(ANIMATION_OUT);
        mOutroAnimation.setInterpolator(new DecelerateInterpolator());
        mOutroAnimation.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator a) {
                mPanel.show(false);
            }});

        mOutroAnimation.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mOpen) {
            int state;

            // Draw background
            if (mStatusMode != 0) {
                canvas.drawARGB(mBackgroundOpacity, 0, 0, 0);
            }

            // Draw top window glow, indicating the notification tray
            Bitmap mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bottom_divider_glow);
            canvas.drawBitmap(mBitmap, null, new Rect(0,0,getWidth(),3), null);
            Bitmap mBitmap1 = BitmapFactory.decodeResource(getResources(), R.drawable.notify_item_glow_bottom);
            canvas.drawBitmap(mBitmap1, null, new Rect(0,0,getWidth(),mGlowOffset), null);
            canvas.drawBitmap(mBitmap1, null, new Rect(0,0,getWidth(),mGlowOffset), null);

            // draw base menu
            for (PieItem item : mItems) {
                drawItem(canvas, item);
            }

            // Paint status report only if settings allow
            if (mStatusMode != 0) {
                // Draw Battery
                state = canvas.save();
                canvas.rotate(90 + (mCharOffset[1] / 2), mCenter.x, mCenter.y);

                int inner = (int)((mRadius + mRadiusInc - 2) + mTouchOffset * 0.7f);
                int outer = (int)(mRadius + mRadiusInc - 2 + mTouchOffset * 1.7f);

                Path mBatteryPath = makeSlice(mPanel.getDegree() + 13, mPanel.getDegree() + 90 - 2, 
                    inner, outer, mCenter);

                mBatteryBackground.setAlpha(mBatteryBackgroundAlpha);
                canvas.drawPath(mBatteryPath, mBatteryBackground);
                canvas.restoreToCount(state);

                state = canvas.save();
                canvas.rotate(90, mCenter.x, mCenter.y);
                Path mBatteryPath2 = makeSlice(mPanel.getDegree() + 13, mPanel.getDegree() + mBatteryMeter - 2, 
                        inner, outer, mCenter);
                mBatteryJuice.setAlpha(mBatteryJuiceAlpha);
                canvas.drawPath(mBatteryPath2, mBatteryJuice);
                canvas.restoreToCount(state);

                // Draw clock
                if (mStatusPath != null) {
                    mStatusPaint.setColor(COLOR_DEFAULT_STATUS);
                    mStatusPaint.setTextSize(125);
                    mStatusPaint.setAlpha(mTextAlpha);
                    mStatusPaint.setTextScaleX(1.2f);

                    // First measure
                    float totalOffset = 0;
                    float offsets[] = new float[mStatusText.length()];
                    for( int i = 0; i < mStatusText.length(); i++ ) {
                        char character = mStatusText.charAt(i);
                        float measure = mStatusPaint.measureText("" + character); 
                        offsets[i] = measure * (character == '1' || character == ':' ? 0.5f : 0.8f);
                        totalOffset += measure * (character == '1' || character == ':' ? 0.5f : 0.9f);
                    }
                    
                    float fullCircle = 2f * (mRadius+mRadiusInc) * (float)Math.PI;
                    float angle = totalOffset * 360 / fullCircle;
                    float pos = mPanel.getDegree() + (180 - angle);

                    state = canvas.save();
                    canvas.rotate(pos, mCenter.x, mCenter.y);

                    // Then print
                    float lastPos = 0;
                    for(int i = 0; i < mStatusText.length(); i++) {
                        char character = mStatusText.charAt(i);
                        canvas.drawTextOnPath("" + character, mStatusPath, lastPos,
                                -mTouchOffset * 2.3f, mStatusPaint);
                        lastPos += offsets[i];
                    }

                    mStatusPaint.setTextSize(35);
                    String amPm = mPolicy.getAmPm();
                    lastPos -= mStatusPaint.measureText(amPm);
                    canvas.drawTextOnPath(amPm, mStatusPath, lastPos,
                            -mCharOffset[mStatusText.length()-1] - mTouchOffset * 5.8f, mStatusPaint);
                    canvas.restoreToCount(state);

                    // Device status information and date
                    state = canvas.save();
                    pos = mPanel.getDegree() + 180;
                    canvas.rotate(pos, mCenter.x, mCenter.y);
                    mStatusPaint.setTextSize(20);
                    canvas.drawTextOnPath(mPolicy.getNetworkProvider(), mStatusPath, mCharOffset[4], -95, mStatusPaint);
                    canvas.drawTextOnPath(mPolicy.getSimpleDate(), mStatusPath, mCharOffset[4], -70, mStatusPaint);
                    canvas.drawTextOnPath(mPolicy.getBatteryLevelReadable(), mStatusPath, mCharOffset[4], -45, mStatusPaint);
                    canvas.drawTextOnPath(mPolicy.getWifiSsid(), mStatusPath, mCharOffset[4], -20, mStatusPaint);
                    canvas.restoreToCount(state);
                }
            }
        }
    }

    private void drawItem(Canvas canvas, PieItem item) {
        if (item.getView() != null) {
            int state = canvas.save();
            canvas.rotate(getDegrees(item.getStartAngle())
                        + mPanel.getDegree(), mCenter.x, mCenter.y);
            canvas.drawPath(item.getPath(), item.isSelected() ? mSelectedPaint : mNormalPaint);
            canvas.restoreToCount(state);

            state = canvas.save();
            ImageView view = (ImageView)item.getView();
            canvas.translate(view.getX(), view.getY());
            canvas.rotate(getDegrees(item.getStartAngle()
                    + item.getSweep() / 2) + mPanel.getDegree(),
                    view.getWidth() / 2, view.getHeight() / 2);

            view.draw(canvas);
            canvas.restoreToCount(state);
        }
    }

    private Path makeSlice(float start, float end, int outer, int inner, Point center) {
        RectF bb = new RectF(center.x - outer, center.y - outer, center.x + outer, center.y + outer);
        RectF bbi = new RectF(center.x - inner, center.y - inner, center.x + inner, center.y + inner);
        Path path = new Path();
        path.arcTo(bb, start, end - start, true);
        path.arcTo(bbi, end, start - end);
        path.close();
        return path;
    }

    // touch handling for pie
    @Override
    public boolean onTouchEvent(MotionEvent evt) {
        float x = evt.getX();
        float y = evt.getY();
        int orient = mPanel.getOrientation();
        int distance = (int)Math.abs(orient == Gravity.TOP || orient == Gravity.BOTTOM ? y : x);    
        int shadeTreshold = getHeight() - mTouchOffset * 10;
        boolean pieTreshold = distance > mTouchOffset && distance < (int)(mRadius + mRadiusInc) * 2.5f;
        final boolean hapticFeedback = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0;

        int action = evt.getActionMasked();
        if (MotionEvent.ACTION_DOWN == action) {
                // Open panel
                mPanel.show(true);
                animateIn();
        } else if (MotionEvent.ACTION_UP == action) {
            if (mOpen) {
                PieItem item = mCurrentItem;

                // Lets put the notification panel back
                hideNotificationsPanel();

                // Open the notification shade
                if (mPanelActive) {
                    mPanelParent.removeView(mPanel.getBar().getNotificationRowLayout());
                    mScrollView.addView(mPanel.getBar().getNotificationRowLayout());
                    mWindowManager.addView(mContainer, getNotificationsPanelLayoutParams());
                    mPanelParentChanged = true;
                    if(hapticFeedback) mVibrator.vibrate(2);

                    mContentFrame.setBackgroundColor(mBackgroundOpacity);
                    ValueAnimator mAlphaAnimation  = ValueAnimator.ofInt(0, 1);
                    mAlphaAnimation.addUpdateListener(new AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            mScrollView.setX(-(int)((1-animation.getAnimatedFraction()) * getWidth()*1.5));
                            mContentFrame.setBackgroundColor((int)(animation.getAnimatedFraction() * 0xDD) << 24);
                            invalidate();
                        }
                    });
                    mAlphaAnimation.setDuration(1000);
                    mAlphaAnimation.setInterpolator(new DecelerateInterpolator());
                    mAlphaAnimation.start();
                }
      
                // Check for click actions
                if (item != null && item.getView() != null && pieTreshold) {
                    if(hapticFeedback) mVibrator.vibrate(2);
                    item.getView().performClick();
                }
            }

            // Say good bye
            deselect();
            animateOut();
            return true;
        } else if (MotionEvent.ACTION_MOVE == action) {
            int treshold = (int)(getHeight() * 0.6f);
            mGlowOffset = distance > treshold ? distance - treshold : 0;
            
            // Trigger the shade?
            if (!mPanelActive && distance > shadeTreshold) {
                // Give the user a small hint that he's inside the upper touch area
                if(hapticFeedback) mVibrator.vibrate(2);
                mPanelActive = true;           
                return true;
            }

            // Take back shade trigger if user decides to abandon his gesture
            if (distance < shadeTreshold) {
                mPanelActive = false;
            }

            PieItem item = findItem(getPolar(x, y));
            // Check for onEnter separately or'll face constant deselect
            if (item != null && mCurrentItem != item) {
                onEnter(item);
            }
            invalidate();
        }
        // always re-dispatch event
        return false;
    }

    public void hideNotificationsPanel() {
        if(mPanelParentChanged) {
            mScrollView.removeView(mPanel.getBar().getNotificationRowLayout());
            mWindowManager.removeView(mContainer);
            mPanelParent.addView(mPanel.getBar()
                    .getNotificationRowLayout(), getNotificationsPanelLayoutParams());
            mPanelActive = false;
            mPanelParentChanged = false;
        }
    }

    private WindowManager.LayoutParams getNotificationsPanelLayoutParams() {
        return new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
    }

    private void onEnter(PieItem item) {
        // deselect
        if (mCurrentItem != null) {
            mCurrentItem.setSelected(false);
        }
        if (item != null) {
            // clear up stack
            playSoundEffect(SoundEffectConstants.CLICK);
            item.setSelected(true);
            mPieView = null;
            mCurrentItem = item;
        } else {
            mCurrentItem = null;
        }

    }

    private void deselect() {
        if (mCurrentItem != null) {
            mCurrentItem.setSelected(false);
        }
        mCurrentItem = null;
        mPieView = null;
    }

    private float getPolar(double x, double y) {
        PointF size = mPanel.getSize();
        int orient = mPanel.getOrientation();
        switch(orient) {
            case Gravity.TOP:
            case Gravity.BOTTOM:
                x = (size.x / 2 - x) * (orient == Gravity.TOP ? -1 : 1);
                y = orient == Gravity.BOTTOM ? mCenter.y - y : size.y + y;
                break;

            case Gravity.LEFT:
            case Gravity.RIGHT:
                x = orient == Gravity.LEFT ? size.y + x : size.y - x;
                y = (size.y / 2 - y) * (orient == Gravity.RIGHT ? -1 : 1);
                break;
        }
        return -(((float)(Math.acos((orient == Gravity.TOP || orient == Gravity.BOTTOM ? x : y) /
                Math.sqrt(x * x + y * y)) * 180 / Math.PI) - 90) / 10);
    }

    private PieItem findItem(float polar) {
        if (mItems != null) {
            int c = 0;
            for (PieItem item : mItems) {
                if (inside(polar, mTouchOffset, item)) {
                    return item;
                }
            }
        }
        return null;
    }

    private boolean inside(float polar, float offset, PieItem item) {
        return (item.getStartAngle() < polar)
        && (item.getStartAngle() + item.getSweep() > polar);
    }
}
