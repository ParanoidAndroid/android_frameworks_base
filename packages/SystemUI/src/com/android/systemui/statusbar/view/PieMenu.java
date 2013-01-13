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
import android.database.ContentObserver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.ColorUtils;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.systemui.R;
import com.android.systemui.statusbar.QuickNavbarPanel;

import java.util.ArrayList;
import java.util.List;

public class PieMenu extends FrameLayout {

    private static final int MAX_LEVELS = 5;
    private static final long ANIMATION = 0;

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

    private Drawable mBackground;
    private Paint mNormalPaint;
    private Paint mSelectedPaint;
    private Paint mSubPaint;

    // touch handling
    private PieItem mCurrentItem;

    private boolean mUseBackground;
    private boolean mAnimating;

    private QuickNavbarPanel mPanel;

    private ColorUtils.ColorSettingInfo mLastBackgroundColor;
    private ColorUtils.ColorSettingInfo mLastGlowColor;

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

    private final class ColorObserver extends ContentObserver {
        ColorObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAV_BAR_COLOR), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAV_GLOW_COLOR), false, this);
            setColors();
        }

        @Override
        public void onChange(boolean selfChange) {
            setColors();
        }
    }

    private void init(Context ctx) {
        mContext = ctx;
        mItems = new ArrayList<PieItem>();
        mLevels = 0;
        mCounts = new int[MAX_LEVELS];
        Resources res = ctx.getResources();
        mRadius = (int) res.getDimension(R.dimen.qc_radius_start);
        mRadiusInc = (int) res.getDimension(R.dimen.qc_radius_increment);
        mSlop = (int) res.getDimension(R.dimen.qc_slop);
        mTouchOffset = (int) res.getDimension(R.dimen.qc_touch_offset);
        mOpen = false;
        setWillNotDraw(false);
        setDrawingCacheEnabled(false);
        mCenter = new Point(0, 0);
        mBackground = new ColorDrawable(0x00000000);
        mNormalPaint = new Paint();
        mNormalPaint.setAntiAlias(true);
        mSelectedPaint = new Paint();
        mSelectedPaint.setAntiAlias(true);
        mSubPaint = new Paint();
        mSubPaint.setAntiAlias(true);
        mSubPaint.setColor(0xFF000000);
        mLastBackgroundColor = ColorUtils.getColorSettingInfo(mContext,
                Settings.System.NAV_BAR_COLOR);
        mLastGlowColor = ColorUtils.getColorSettingInfo(mContext,
                Settings.System.NAV_GLOW_COLOR);

        ColorObserver observer = new ColorObserver(new Handler());
        observer.observe();
    }

    public void setPanel(QuickNavbarPanel panel) {
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
            animateOpen();
        }
        invalidate();
    }

    private void animateOpen() {
        ValueAnimator anim = ValueAnimator.ofFloat(0, 1);
        anim.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                for (PieItem item : mItems) {
                    item.setAnimationAngle((1 - animation.getAnimatedFraction()) * (- item.getStart()));
                }
                invalidate();
            }

        });
        anim.setDuration(2 * ANIMATION);
        anim.start();
    }

    public void setCenter(int x, int y) {
        if (y < mSlop) {
            mCenter.y = 0;
        } else {
            mCenter.y = getHeight();
        }
        mCenter.x = x;
    }

    private void setColors() {
        // Only watch for per app color changes when the setting is in check
        if (ColorUtils.getPerAppColorState(mContext)) {
            setBackgroundColor();
            setGlowColor();
        }
    }

    private void setBackgroundColor() {
        ColorUtils.ColorSettingInfo colorInfo = ColorUtils.getColorSettingInfo(mContext,
                Settings.System.NAV_BAR_COLOR);
        int newColor = 0xFF000000;
        if (!colorInfo.lastColorString.equals(mLastBackgroundColor.lastColorString)) {
            if (!colorInfo.isLastColorNull) {
                newColor = colorInfo.lastColor;
            }
            mNormalPaint.setColor(newColor);
            mLastBackgroundColor = colorInfo;
        }
    }

    private void setGlowColor() {
        ColorUtils.ColorSettingInfo colorInfo = ColorUtils.getColorSettingInfo(mContext,
                Settings.System.NAV_GLOW_COLOR);
        int newColor = 0xE033B5E5;
        if (!colorInfo.lastColorString.equals(mLastGlowColor.lastColorString)) {
            if (!colorInfo.isLastColorNull) {
                newColor = colorInfo.lastColor;
            }
            mSelectedPaint.setColor(newColor);
            setDrawingAlpha(mSelectedPaint, 0.7f);
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
                        int y = mCenter.y - (int) (r * Math.cos(angle)) - h / 2;
                        if (onTheTop()) {
                            x = mCenter.x + x - w / 2;
                        } else {
                            x = mCenter.x - x - w / 2;
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

    @Override
    protected void onDraw(Canvas canvas) {
        if (mOpen) {
            int state;
            if (mUseBackground) {
                int w = mBackground.getIntrinsicWidth();
                int h = mBackground.getIntrinsicHeight();
                int left = mCenter.x - w;
                int top = mCenter.y - h / 2;
                mBackground.setBounds(left, top, left + w, top + h);
                state = canvas.save();
                if (onTheTop()) {
                    canvas.scale(-1, 1);
                }
                mBackground.draw(canvas);
                canvas.restoreToCount(state);
            }
            // draw base menu
            PieItem last = mCurrentItem;
            if (mOpenItem != null) {
                last = mOpenItem;
            }
            for (PieItem item : mItems) {
                if (item != last) {
                    drawItem(canvas, item);
                }
            }
            if (last != null) {
                drawItem(canvas, last);
            }
            if (mPieView != null) {
                mPieView.draw(canvas);
            }
        }
    }

    private void drawItem(Canvas canvas, PieItem item) {
        if (item.getView() != null) {
            Paint p = item.isSelected() ? mSelectedPaint : mNormalPaint;
            if (!mItems.contains(item)) {
                p = item.isSelected() ? mSelectedPaint : mSubPaint;
            }
            int state = canvas.save();
            if (onTheTop()) {
                canvas.scale(-1, 1);
            }
            float r = getDegrees(item.getStartAngle()) - 270; // degrees(0)
            canvas.rotate(r, mCenter.x, mCenter.y);
            canvas.drawPath(mPath, p);
            canvas.restoreToCount(state);
            // draw the item view
            View view = item.getView();
            state = canvas.save();
            canvas.translate(view.getX(), view.getY());
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
                mPanel.show(true);
                return true;
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
                if (!handled && (item != null) && (item.getView() != null)) {
                    if ((item == mOpenItem) || !mAnimating) {
                        item.getView().performClick();
                    }
                }
            }
            mPanel.show(false);
            return true;
        } else if (MotionEvent.ACTION_MOVE == action) {
            if (mAnimating) return false;
            boolean handled = false;
            PointF polar = getPolar(x, y);
            if (mPieView != null) {
                handled = mPieView.onTouchEvent(evt);
            }
            if (handled) {
                invalidate();
                return false;
            }
            PieItem item = findItem(polar);
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

    private PointF getPolar(float x, float y) {
        PointF res = new PointF();
        // get angle and radius from x/y
        res.x = (float) Math.PI / 2;
        x = mCenter.x - x;
        if (mCenter.x < mSlop) {
            x = -x;
        }
        y = mCenter.y - y;
        res.y = (float) Math.sqrt(x * x + y * y);
        if (y > 0) {
            res.x = (float) Math.asin(x / res.y);
        } else if (y < 0) {
            res.x = (float) (Math.PI - Math.asin(x / res.y ));
        }
        return res;
    }

    /**
     *
     * @param polar x: angle, y: dist
     * @return the item at angle/dist or null
     */
    private PieItem findItem(PointF polar) {
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

    private boolean inside(PointF polar, float offset, PieItem item) {
        float sweep = polar.x < 0 ? -(item.getSweep()/2) : item.getSweep()/2;
        return (item.getStartAngle() < polar.x + sweep)
        && (item.getStartAngle() + item.getSweep() > polar.x + sweep);
    }

}
