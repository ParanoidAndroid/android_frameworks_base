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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.ColorUtils;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.systemui.R;
import com.android.systemui.statusbar.PieControl;
import com.android.systemui.statusbar.PieControlPanel;
import com.android.systemui.statusbar.policy.Clock;

import java.util.ArrayList;
import java.util.List;

public class PieMenu extends FrameLayout {

    private static final int MAX_LEVELS = 5;
    private static final int BACKGROUND_COLOR = 0xCC;
    private static final int ANIMATION_IN = 3000;
    private static final int ANIMATION_OUT = 0;

    public interface PieController {
        /**
         * called before menu opens to customize menu
         * returns if pie state has been changed
         */
        public boolean onOpen();

    }

    /**
     * A view like object that lives off of the pie menu
     */
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

    private Context mContext;

    private Point mCenter;
    private int mRadius;
    private int mRadiusInc;
    private int mSlop;
    private int mTouchOffset;
    private Path mPath;

    private boolean mOpen;
    private PieController mController;

    private List<PieItem> mItems;
    private int mLevels;
    private int[] mCounts;
    private PieView mPieView;

    // sub menus
    private PieItem mOpenItem;

    private Paint mNormalPaint;
    private Paint mSelectedPaint;
    private Paint mSubPaint;
    private Paint mBatteryJuice;
    private Paint mBatteryBackground;

    // touch handling
    private PieItem mCurrentItem;

    private boolean mUseBackground;
    private boolean mAnimating;

    private PieControlPanel mPanel;

    private ColorUtils.ColorSettingInfo mLastBackgroundColor;
    private ColorUtils.ColorSettingInfo mLastGlowColor;
    private boolean mGlowColorHelper;

    private int mBackgroundOpacity;

    private float mTextOffset;
    private int mTextAlpha;
    private float mTextLen;
    private Path mStatusPath;
    private String mStatusText;
    private Paint mStatusPaint;
    private boolean mStatusAnimate;
    private Clock mStatusClock;

    private ValueAnimator mIntoAnimation;
    private ValueAnimator mOutroAnimation;

    private Vibrator vibrator;

    /**
     * @param context
     * @param attrs
     * @param defStyle
     */
    public PieMenu(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    /**
     * @param context
     * @param attrs
     */
    public PieMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    /**
     * @param context
     */
    public PieMenu(Context context) {
        super(context);
        init(context);
    }

    private void init(Context ctx) {
        mContext = ctx;
        vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);

        mItems = new ArrayList<PieItem>();
        mLevels = 0;
        mCounts = new int[MAX_LEVELS];
        Resources res = ctx.getResources();
        mRadius = (int) res.getDimension(R.dimen.pie_radius_start);
        mRadiusInc = (int) res.getDimension(R.dimen.pie_radius_increment);
        mSlop = (int) res.getDimension(R.dimen.pie_slop);
        mTouchOffset = (int) res.getDimension(R.dimen.pie_touch_offset);
        mOpen = false;
        setWillNotDraw(false);
        setDrawingCacheEnabled(false);
        mCenter = new Point(0, 0);

        mNormalPaint = new Paint();
        mNormalPaint.setAntiAlias(true);
        mNormalPaint.setColor(0xAA000000);

        mSelectedPaint = new Paint();
        mSelectedPaint.setAntiAlias(true);
        mSelectedPaint.setColor(0xAA33b5e5);

        mBatteryJuice = new Paint();
        mBatteryJuice.setAntiAlias(true);
        mBatteryJuice.setColor(0x33b5e5);

        mBatteryBackground = new Paint();
        mBatteryBackground.setAntiAlias(true);
        mBatteryBackground.setColor(0xFFFFFF);
        
        mSubPaint = new Paint();
        mSubPaint.setAntiAlias(true);
        mSubPaint.setColor(0xFF000000);

        mUseBackground = true;
        mBackgroundOpacity = 0;
        mGlowColorHelper = false;

        // Circle status text
        mCharOffest = new float[25];
        for (int i = 0; i < mCharOffest.length; i++) {
            mCharOffest[i] = 1000;
        }

        mTextOffset = 0;
        mTextAlpha = 0;
        mTextLen = 0;
        mStatusPaint = new Paint();
        mStatusPaint.setColor(Color.WHITE);
        mStatusPaint.setStyle(Paint.Style.FILL);
        mStatusPaint.setTextSize(150);
        
        mStatusAnimate  = false;
        mStatusClock = new Clock(mContext);
        mStatusClock.startBroadcastReceiver();
        mStatusText = mStatusClock.getSmallTime().toString();
        mTextLen = mStatusPaint.measureText(mStatusText, 0, mStatusText.length());
        mStatusClock.setOnClockChangedListener(new Clock.OnClockChangedListener() {
            public void onChange(CharSequence t) {
                mStatusText = t.toString();
                mTextLen = mStatusPaint.measureText(mStatusText, 0, mStatusText.length());
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
    }

    public void setController(PieController ctl) {
        mController = ctl;
    }

    public void setUseBackground(boolean useBackground) {
        mUseBackground = useBackground;
    }

    public void addItem(PieItem item) {
        // add the item to the pie itself
        mItems.add(item);
        int l = item.getLevel();
        mLevels = Math.max(mLevels, l);
        mCounts[l]++;
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

    /**
     * guaranteed has center set
     * @param show
     */
    public void show(boolean show) {
        mOpen = show;
        if (mOpen) {
            // ensure clean state
            mAnimating = false;
            mCurrentItem = null;
            mOpenItem = null;
            mPieView = null;
            for (PieItem item : mItems) {
                item.setSelected(false);
            }
            if (mController != null) {
                boolean changed = mController.onOpen();
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
        if (!colorInfo.lastColorString.equals(mLastBackgroundColor.lastColorString)) {
            int colorRgb = ColorUtils.extractRGB(colorInfo.lastColor);
            mNormalPaint.setColor(colorRgb | 0xAA000000);
            mLastBackgroundColor = colorInfo;
        }
    }

    private void setGlowColor() {
        ColorUtils.ColorSettingInfo colorInfo = ColorUtils.getColorSettingInfo(mContext,
                Settings.System.NAV_GLOW_COLOR);
        if (!colorInfo.lastColorString.equals(mLastGlowColor.lastColorString)) {
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

        for (int i = 0; i < mLevels; i++) {
            int level = i + 1;
            float sweep = (float) (Math.PI - 2 * emptyangle) / mCounts[level];
            float angle = emptyangle + sweep / 2 - (float)Math.PI/2;
            mPath = makeSlice(getDegrees(0) - gap, getDegrees(sweep) + gap, outer, inner, mCenter);
            for (PieItem item : mItems) {
                if (item.getLevel() == level) {
                    View view = item.getView();
                    if (view != null) {
                        view.measure(view.getLayoutParams().width,
                                view.getLayoutParams().height);
                        int w = view.getMeasuredWidth();
                        int h = view.getMeasuredHeight();
                        int r = inner + (outer - inner) * 2 / 3;
                        int x = (int) (r * Math.sin(angle));
                        int y = (int) (r * Math.cos(angle));

                        switch( mPanel.getOrientation() ) {
                            case Gravity.LEFT:
                                y = mCenter.y - (int) (r * Math.sin(angle)) - h / 2;
                                x = (int) (r * Math.cos(angle)) - w / 2;
                                break;
                            case Gravity.RIGHT:
                                y = mCenter.y - (int) (Math.PI/2-r * Math.sin(angle)) - h / 2;
                                x = mCenter.x - (int) (r * Math.cos(angle)) - w / 2;
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
                    float itemstart = angle - sweep / 2;
                    item.setGeometry(itemstart, sweep, inner, outer);
                    angle += sweep;
                }
            }
            inner += mRadiusInc;
            outer += mRadiusInc;
        }
    }


    /**
     * converts a
     *
     * @param angle from 0..PI to Android degrees (clockwise starting at 3
     *        o'clock)
     * @return skia angle
     */
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
            mCharOffest[mIndex] = (float)((1 - animation.getAnimatedFraction()) * 1000);
            invalidate();
        }
    }

    int mBatteryBackgroundAlpha;
    int mBatteryJuiceAlpha;
    int mBatteryMeter;

    private void animateIn() {

        // Reset base values
        mBatteryMeter = 0;
        mBatteryBackgroundAlpha = 0;
        mBatteryJuiceAlpha = 0;
        mTextAlpha = 0;
        mBackgroundOpacity = 0;
        mCharOffest = new float[25];
        for (int i = 0; i < mCharOffest.length; i++) {
            mCharOffest[i] = 1000;
        }

        // Background
        mIntoAnimation = ValueAnimator.ofInt(0, 1);
        mIntoAnimation.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mBackgroundOpacity = (int)(animation.getAnimatedFraction() * BACKGROUND_COLOR);
                mBatteryBackgroundAlpha = (int)(animation.getAnimatedFraction() * 0x55);
                mBatteryJuiceAlpha = (int)(animation.getAnimatedFraction() * 0x88);
                mBatteryMeter = (int)(animation.getAnimatedFraction() * 75);
                invalidate();
            }
        });
        mIntoAnimation.setDuration(ANIMATION_IN);
        mIntoAnimation.setInterpolator(new DecelerateInterpolator());
        mIntoAnimation.start();

        int textLen = mStatusText.length();
        for( int i = 0; i < textLen; i++ ) {

            // Text alpha
            if ( i == 0 ) {
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

    float mCharOffest[];

    @Override
    protected void onDraw(Canvas canvas) {
        if (mOpen) {
            int state;
            if (mUseBackground) {
                canvas.drawARGB(mBackgroundOpacity, 0, 0, 0);
            }
            // draw base menu
            PieItem last = mCurrentItem;
            if (mOpenItem != null) {
                last = mOpenItem;
            }
            for (PieItem item : mItems) {
                drawItem(canvas, item);
            }


            mStatusPath = new Path();
            mStatusPath.addCircle(mCenter.x, mCenter.y, mRadius+mRadiusInc+mTouchOffset, Path.Direction.CW);

            mStatusPaint = new Paint();
            mStatusPaint.setColor(Color.WHITE);
            mStatusPaint.setStyle(Paint.Style.FILL);
            mStatusPaint.setTextSize(150);
            mStatusPaint.setAlpha(mTextAlpha);
            mStatusPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            mStatusPaint.setTextScaleX(1.2f);
            

            //android.util.Log.d("PARANOID", "sweep="+getDegrees(last.getSweep()*1.5f));
            state = canvas.save();
            canvas.rotate(90 + (mCharOffest[1] / 2), mCenter.x, mCenter.y);
            int inner = mRadius + 2;
            int outer = mRadius + mRadiusInc - 2;
            Path mBatteryPath = makeSlice(mPanel.getDegree() + 13, mPanel.getDegree() + 90 - 2, outer + mTouchOffset * 2, outer + mTouchOffset, mCenter);

            mBatteryBackground.setAlpha(mBatteryBackgroundAlpha);
            canvas.drawPath(mBatteryPath, mBatteryBackground);
            canvas.restoreToCount(state);

            state = canvas.save();
            canvas.rotate(90, mCenter.x, mCenter.y);
            Path mBatteryPath2 = makeSlice(mPanel.getDegree() + 13, mPanel.getDegree() + mBatteryMeter, outer + mTouchOffset * 2, outer + mTouchOffset, mCenter);
            mBatteryJuice.setAlpha(mBatteryJuiceAlpha);
            canvas.drawPath(mBatteryPath2, mBatteryJuice);
            canvas.restoreToCount(state);


            // Time falling into place
            state = canvas.save();
            float pos = mPanel.getDegree() + 120;
            canvas.rotate(pos, mCenter.x, mCenter.y);
            float lastPos = 0;
            for( int i = 0; i < mStatusText.length(); i++ ) {
                char character = mStatusText.charAt(i);
                canvas.drawTextOnPath("" + character, mStatusPath, lastPos, -mCharOffest[i] - 40, mStatusPaint);
                lastPos += mStatusPaint.measureText("" + character) * (character == '1' || character == ':' ? 0.5f : 0.8f);
            }
            mStatusPaint.setTextSize(50);
            lastPos -= mStatusPaint.measureText("PM");
            canvas.drawTextOnPath("PM", mStatusPath, lastPos, -mCharOffest[mStatusText.length()-1] - 160, mStatusPaint);
            canvas.restoreToCount(state);

            // Date circling in
            state = canvas.save();
            pos = mPanel.getDegree() + 180;
            canvas.rotate(pos, mCenter.x, mCenter.y);
            mStatusPaint.setTextSize(20);
            canvas.drawTextOnPath("BASE.DE", mStatusPath, mCharOffest[4], -75, mStatusPaint);
            canvas.drawTextOnPath("WED, 16 JAN 2013", mStatusPath, mCharOffest[4], -50, mStatusPaint);
            canvas.drawTextOnPath("BATTERY AT 75%", mStatusPath, mCharOffest[4], -25, mStatusPaint);
            canvas.drawTextOnPath("WIFI: SITECOM788A2A", mStatusPath, mCharOffest[4], 0, mStatusPaint);
            canvas.restoreToCount(state);

            // floating text
            /*
            state = canvas.save();
            canvas.rotate(mPanel.getDegree(), mCenter.x, mCenter.y);
            mStatusPaint.setAlpha(mTextAlpha);
            canvas.drawTextOnPath(mStatusText, mStatusPath, 0, 0, mStatusPaint);
            canvas.restoreToCount(state);

            if (mStatusAnimate) {
                mTextOffset += .4f;
                invalidate();
            }*/
        }
    }

    private void drawItem(Canvas canvas, PieItem item) {
        if (item.getView() != null) {
            int state = canvas.save();
            canvas.rotate(getDegrees(item.getStartAngle())
                        + mPanel.getDegree(), mCenter.x, mCenter.y);
            canvas.drawPath(mPath, item.isSelected() ? mSelectedPaint : mNormalPaint);
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
        RectF bb =
                new RectF(center.x - outer, center.y - outer, center.x + outer,
                        center.y + outer);
        RectF bbi =
                new RectF(center.x - inner, center.y - inner, center.x + inner,
                        center.y + inner);
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
        int action = evt.getActionMasked();

        if (MotionEvent.ACTION_DOWN == action) {
                // Open panel
                mPanel.show(true);
                animateIn();
        } else if (MotionEvent.ACTION_UP == action) {
            if (mOpen) {
                boolean handled = false;
                if (mPieView != null) {
                    handled = mPieView.onTouchEvent(evt);
                }
                PieItem item = mCurrentItem;
                if (!mAnimating) {
                    deselect();
                }

                int orient = mPanel.getOrientation();
                int distance = (int)Math.abs(orient == Gravity.TOP || orient == Gravity.BOTTOM ? y : x);
                if (!handled && (item != null) && (item.getView() != null) && (distance > mTouchOffset && distance
                        < (int)(mRadius + mRadiusInc) * 2.5f) ) {
                    if ((item == mOpenItem) || !mAnimating) {
                        vibrator.vibrate(5);
                        item.getView().performClick();
                    }
                }
            }

            // Say good bye
            animateOut();
            return true;
        } else if (MotionEvent.ACTION_MOVE == action) {
            if (mAnimating) return false;
            boolean handled = false;
            if (mPieView != null) {
                handled = mPieView.onTouchEvent(evt);
            }
            if (handled) {
                invalidate();
                return false;
            }
            PieItem item = findItem(getPolar(x, y));
            if (item == null) {
            } else if (mCurrentItem != item) {
                onEnter(item);
                invalidate();
            }
        }
        // always re-dispatch event
        return false;
    }

    /**
     * enter a slice for a view
     * updates model only
     * @param item
     */
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
            if ((mCurrentItem != mOpenItem) && mCurrentItem.hasItems()) {
                mOpenItem = item;
            }
        } else {
            mCurrentItem = null;
        }

    }

    private void deselect() {
        if (mCurrentItem != null) {
            mCurrentItem.setSelected(false);
        }
        if (mOpenItem != null) {
            mOpenItem = null;
        }
        mCurrentItem = null;
        mPieView = null;
    }

    private float getPolar(double x, double y) {
        PointF size = mPanel.getSize();
        int orient = mPanel.getOrientation();
        switch( orient ) {
            case Gravity.TOP:
            case Gravity.BOTTOM:
                x = (size.x / 2 - x) * (orient == Gravity.TOP ? -1 : 1);
                y = orient == Gravity.BOTTOM ? mCenter.y - y : size.y + y;
                break;

            case Gravity.LEFT:
            case Gravity.RIGHT:
                x = (size.y + x) * (orient == Gravity.RIGHT ? -1 : 1);
                y = (size.y / 2 - y) * (orient == Gravity.RIGHT ? -1 : 1);
                break;
        }
        return -(((float)(Math.acos((orient == Gravity.TOP || orient == Gravity.BOTTOM ? x : y ) /
                Math.sqrt(x * x + y * y)) * 180 / Math.PI) - 90) / 10);
    }

    /**
     *
     * @param polar x: angle, y: dist
     * @return the item at angle/dist or null
     */
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
