/*
 * Copyright (C) 2013 ParanoidAndroid
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.systemui.statusbar.pie;

import android.view.View.OnClickListener;
import android.content.Context;
import android.os.Handler;
import android.util.ColorUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.graphics.drawable.Drawable;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.view.KeyEvent;
import android.content.Intent;
import android.app.ActivityOptions;
import android.os.SystemClock;
import android.hardware.input.InputManager;
import android.os.UserHandle;
import android.content.ActivityNotFoundException;
import android.graphics.Path;
import android.widget.ImageView.ScaleType;
import android.graphics.Rect;
import android.graphics.Point;
import android.view.Gravity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.ViewGroup.LayoutParams;
import android.graphics.RectF;
import android.view.SoundEffectConstants;

import com.android.systemui.R;

// +----------------------------------------------------------------------------------+
// | CLASS PieItem                                                                    |
// +----------------------------------------------------------------------------------+
public class PieItem {

    protected View mView;
    protected float mStart;
    protected float mSweep;
    protected int mInner;
    protected int mOuter;
    protected boolean mSelected;
    protected String mName;
    protected Path mPath;
    protected boolean mLesser;
    protected ColorUtils.ColorSettingInfo mLastColor;

    protected Pie mPie;
    protected PieControl mControl;

    public PieItem(PieControl control) {
        mControl = control;
        mPie = mControl.mPie;
    }

    public void setSelected(boolean s) {
        mSelected = s;
        if (mView != null) mView.setSelected(s);
    }

    public void setGeometry(float st, float sw, int inside, int outside) {
        mStart = st;
        mSweep = sw;
        mInner = inside;
        mOuter = outside;
    }

    public void setIcon(int resId) {
        ((ImageView)mView).setImageResource(resId);
    }

    public void setColor(int color) {
        ImageView imageView = ((ImageView)mView);
        Drawable drawable = imageView.getDrawable();
        drawable.setColorFilter(color, Mode.SRC_ATOP);
        imageView.setImageDrawable(drawable);
    }

    public void draw(Canvas canvas, Paint fill, Paint outline) {
        if (mView != null) {
            int state = canvas.save();
            canvas.rotate(getDegrees(mStart) + mControl.getDegree(), mControl.mCenter.x, mControl.mCenter.y);
            canvas.drawPath(mPath, fill);
            canvas.drawPath(mPath, outline);
            canvas.restoreToCount(state);

            state = canvas.save();
            canvas.translate(mView.getX(), mView.getY());
            canvas.rotate(getDegrees(mStart + mSweep / 2) + mControl.getDegree(),
                    mView.getWidth() / 2, mView.getHeight() / 2);

            mView.draw(canvas);
            canvas.restoreToCount(state);
        }
    }

    public static PieItem makeItem(final PieControl control, int image, String name, boolean lesser) {
        final int mItemSize = (int) control.mContext.getResources().
                getDimension(R.dimen.pie_item_size);

        ImageView view = new ImageView(control.mContext);
        view.setTag(name);
        view.setImageResource(image);
        view.setMinimumWidth(mItemSize);
        view.setMinimumHeight(mItemSize);
        view.setScaleType(ScaleType.CENTER);
        
        LayoutParams lp = new LayoutParams(mItemSize, mItemSize);
        view.setLayoutParams(lp);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String buttonName = (String)v.getTag();
                if (buttonName.equals(control.BACK_BUTTON)) {
                    injectKeyDelayed(control.mHandler, KeyEvent.KEYCODE_BACK);
                } else if (buttonName.equals(control.HOME_BUTTON)) {
                    injectKeyDelayed(control.mHandler, KeyEvent.KEYCODE_HOME);
                } else if (buttonName.equals(control.MENU_BUTTON)) {
                    injectKeyDelayed(control.mHandler, KeyEvent.KEYCODE_MENU);
                } else if (buttonName.equals(control.RECENT_BUTTON)) {
                    control.mPie.mStatusBar.toggleRecentApps();
                } else if (buttonName.equals(control.CLEAR_ALL_BUTTON)) {
                    control.mPie.mStatusBar.clearRecentApps();
                } else if (buttonName.equals(control.SEARCH_BUTTON)) {
                    launchAssistAction(control);
                }
            }
        });
        
        PieItem result = new PieItem(control);
        result.mView = view;
        result.mName = name;
        result.mLesser = lesser;
        return result;
    }

    public static Path makeSlice(float start, float end, int outer, int inner, Point center) {
        return makeSlice(start, end, outer, inner, center, 0, true);
    }

    public static Path makeSlice(float start, float end, int outer, int inner, Point center, float narrow, boolean bothEnds) {
        RectF bb = new RectF(center.x - outer, center.y - outer, center.x + outer, center.y + outer);
        RectF bbi = new RectF(center.x - inner, center.y - inner, center.x + inner, center.y + inner);
        Path path = new Path();
        path.arcTo(bb, start, end - start, true);
        path.arcTo(bbi, end + narrow, start - end - (bothEnds ? narrow : narrow * 2));
        path.close();
        return path;
    }

    private static float getDegrees(double angle) {
        return (float) (270 - 180 * angle / Math.PI);
    }

    public static void layoutPie(PieControl control) {
        Pie pie = control.mPie;
        PiePolicy piePolicy = pie.mPolicy;

        float emptyangle = piePolicy.mEmptyAngle * (float)Math.PI / 180;
        int inner = (int)piePolicy.mInnerPieRadius;
        int outer = (int)piePolicy.mOuterPieRadius;

        int itemCount = control.mItems.size();
        if (!piePolicy.mMenuButton && !piePolicy.mUseMenuAlways) itemCount--;
        if (!piePolicy.mUseSearch) itemCount--;

        int totalCount = 0;
        int lesserSweepCount = 0;
        for (PieItem item : control.mItems) {
            if (canItemDisplay(item)) {
                totalCount++;
                if (item.mLesser) {
                    lesserSweepCount += 1;
                }
            }
        }

        float adjustedSweep = lesserSweepCount > 0 ? (((1-0.65f) * lesserSweepCount) /
                (itemCount-lesserSweepCount)) : 0;    
        float sweep = 0;
        float angle = 0;
        float total = 0;

        int count = 0;
        for (PieItem item : control.mItems) {
            if (!canItemDisplay(item)) continue;

            sweep = ((float) (Math.PI - 2 * emptyangle) / itemCount) * (item.mLesser ? 0.65f : 1 + adjustedSweep);
            angle = (emptyangle + sweep / 2 - (float)Math.PI/2);
            item.mPath = makeSlice(getDegrees(0) - piePolicy.mPieGap, getDegrees(sweep) + piePolicy.mPieGap, outer, inner, control.mCenter,
                    (piePolicy.mPieGap > 0 ? piePolicy.mPieGap + 0.4f : 0), count != 0);
            View view = item.mView;

            if (view != null) {
                view.measure(view.getLayoutParams().width, view.getLayoutParams().height);
                int w = view.getMeasuredWidth();
                int h = view.getMeasuredHeight();
                int r = inner + (outer - inner) * 2 / 3;
                int x = (int) (r * Math.sin(total + angle));
                int y = (int) (r * Math.cos(total + angle));

                switch(pie.mGravity) {
                    case Gravity.LEFT:
                        y = control.mCenter.y - (int) (r * Math.sin(total + angle)) - h / 2;
                        x = (int) (r * Math.cos(total + angle)) - w / 2;
                        break;
                    case Gravity.RIGHT:
                        y = control.mCenter.y - (int) (Math.PI/2-r * Math.sin(total + angle)) - h / 2;
                        x = control.mCenter.x - (int) (r * Math.cos(total + angle)) - w / 2;
                        break;
                    case Gravity.TOP:
                        y = y - h / 2;
                        x = control.mCenter.x - (int)(Math.PI/2-x) - w / 2;
                        break;
                    case Gravity.BOTTOM: 
                        y = control.mCenter.y - y - h / 2;
                        x = control.mCenter.x - x - w / 2;
                        break;
                }                
                view.layout(x, y, x + w, y + h);
            }                    
            float itemstart = total + angle - sweep / 2;
            item.setGeometry(itemstart, sweep, inner, outer);
            total += sweep;
            count++;
        }
    }

    private static void launchAssistAction(PieControl control) {
        if(control.mAssistant != null) {
            try {
                ActivityOptions opts = ActivityOptions.makeCustomAnimation(control.mContext,
                        R.anim.search_launch_enter, R.anim.search_launch_exit);
                control.mAssistant.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                control.mContext.startActivityAsUser(control.mAssistant, opts.toBundle(),
                        new UserHandle(UserHandle.USER_CURRENT));
            } catch (ActivityNotFoundException e) {
            }
        }
    }

    private static void injectKeyDelayed(Handler handler, int keycode){
    	mInjectKeycode = keycode;
        mDownTime = SystemClock.uptimeMillis();
    	handler.removeCallbacks(onInjectKeyDelayed);
      	handler.postDelayed(onInjectKeyDelayed, 100);
    }

    private static int mInjectKeycode;
    private static long mDownTime;
    private final static Runnable onInjectKeyDelayed = new Runnable() {
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

    public static float getPolar(PieControl control, float x, float y) {
        int orientation = control.mPie.mGravity;
        float deltaY = control.mCenter.y - y;
        float deltaX = control.mCenter.x - x;
        float adjustAngle = 0;;
        switch(orientation) {
            case Gravity.TOP:
            case Gravity.LEFT:
                adjustAngle = 90;
                break;
            case Gravity.RIGHT:
                adjustAngle = -90;
                break;
        }
        return (adjustAngle + (float)Math.atan2(orientation == Gravity.TOP ? deltaY : deltaX,
                orientation == Gravity.TOP ? deltaX : deltaY) * 180 / (float)Math.PI)
                * (orientation == Gravity.TOP ? -1 : 1) * (float)Math.PI / 180;
    }

    public static PieItem findItem(PieControl control, float polar) {
        if (control.mItems != null) {
            for (PieItem item : control.mItems) {
                if (!canItemDisplay(item)) continue;
                if (inside(polar, item)) {
                    return item;
                }
            }
        }
        return null;
    }

    public static boolean canItemDisplay(PieItem item) {
        return !(item.mName.equals(PieControl.MENU_BUTTON) && !item.mControl.mMenuButton && !item.mPie.mUseMenuAlways) &&
                !(item.mName.equals(PieControl.SEARCH_BUTTON) && !item.mPie.mUseSearch);
    }

    private static boolean inside(float polar, PieItem item) {
        return (item.mStart < polar)
        && (item.mStart + item.mSweep > polar);
    }

    public static void onEnter(PieItem item) {
        if (item == null || item.mControl.mCurrentItem == item) return;

        PieControl control = item.mControl;
        if (item.mControl.mCurrentItem != null) item.mControl.mCurrentItem.mSelected = false;

        if (item != null) {
            item.mControl.playSoundEffect(SoundEffectConstants.CLICK);
            item.mSelected = true;
            item.mControl.mCurrentItem = item;
        } else {
            item.mControl.mCurrentItem = null;
        }

    }

    public static void deselect(PieControl control) {
        if (control.mCurrentItem != null) {
            control.mCurrentItem.mSelected = false;
        }
        control.mCurrentItem = null;
    }
}
